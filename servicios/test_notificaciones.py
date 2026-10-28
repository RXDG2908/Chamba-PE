"""
T8.3: pruebas de entrega del aviso de una solicitud nueva al prestador (HU-8).

Mientras no exista el envío push de T8.1, el aviso que recibe el prestador es la
solicitud en su bandeja (`GET /api/servicios/solicitudes/recibidas/`). Estas pruebas
miden ese canal: que la solicitud llegue al prestador correcto, en menos de 5 s
desde que el cliente la envía y con los datos que necesita para responder. Cuando
entre T8.1 se agregan aquí las pruebas del envío push con el mismo límite.
"""
import time
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.test import Client, TestCase
from django.urls import reverse
from django.utils import timezone
from django.utils.dateparse import parse_datetime

from prestadores.models import Prestador, Rubro

from .models import Solicitud

LIMITE_ENTREGA_S = 5
CENTRO = {'latitud': '-12.046374', 'longitud': '-77.042793'}


class EntregaAvisoSolicitudTests(TestCase):
    def setUp(self):
        self.rubro = Rubro.objects.get(nombre='Gasfitería')
        self.prestador = self.crear_prestador('prestador_t83')
        self.cliente = Client()
        self.sesion_prestador = Client()
        self.sesion_prestador.force_login(self.prestador.usuario)
        self.datos = {
            'prestador': self.prestador.id, 'rubro': self.rubro.id,
            'cliente_nombre': 'María-José Ñahui', 'cliente_telefono': '987654321',
            'descripcion': 'Fuga de agua bajo el lavadero, piso 2.',
            'latitud': '-12.019394', 'longitud': '-77.042793',  # a 3 km del prestador
        }

    def crear_prestador(self, username, **cambios):
        datos = {
            'usuario': get_user_model().objects.create_user(username=username),
            'nombres': 'Rosa', 'apellidos': 'Quispe', 'dni': f'8300{Prestador.objects.count() + 1:04d}',
            'radio_cobertura_km': 5, 'disponible': True, 'estado': Prestador.Estado.VERIFICADO,
            **CENTRO,
        }
        prestador = Prestador.objects.create(**{**datos, **cambios})
        prestador.rubros.set([self.rubro])
        return prestador

    def enviar(self, **cambios):
        return self.cliente.post(
            reverse('crear_solicitud'), {**self.datos, **cambios}, content_type='application/json',
        )

    def bandeja(self, sesion=None):
        respuesta = (sesion or self.sesion_prestador).get(reverse('solicitudes_recibidas'))
        self.assertEqual(respuesta.status_code, 200)
        return respuesta.json()

    def medir_entrega(self):
        """Segundos desde que el cliente envía hasta que la solicitud está en la bandeja."""
        inicio = time.perf_counter()
        respuesta = self.enviar()
        self.assertEqual(respuesta.status_code, 201, respuesta.content)
        solicitud_id = respuesta.json()['id']
        self.assertIn(solicitud_id, [s['id'] for s in self.bandeja()])
        return time.perf_counter() - inicio

    def test_la_solicitud_llega_en_la_consulta_siguiente(self):
        self.assertEqual(self.bandeja(), [])
        solicitud_id = self.enviar().json()['id']
        # La entrega es síncrona: no hace falta esperar ni reintentar.
        self.assertEqual([s['id'] for s in self.bandeja()], [solicitud_id])

    def test_entrega_menor_a_5_s(self):
        tiempos = [self.medir_entrega() for _ in range(10)]
        self.assertLess(max(tiempos), LIMITE_ENTREGA_S)

    def test_entrega_menor_a_5_s_con_bandeja_cargada(self):
        # 300 solicitudes previas del prestador y 20 prestadores más con las suyas.
        otros = [self.crear_prestador(f'otro_t83_{n}') for n in range(20)]
        Solicitud.objects.bulk_create([
            Solicitud(
                prestador=prestador, rubro=self.rubro, cliente_nombre='Cliente',
                cliente_telefono='900000000', descripcion='Trabajo anterior', **CENTRO,
            )
            for prestador in [self.prestador] * 300 + otros * 15
        ])
        tiempos = [self.medir_entrega() for _ in range(5)]
        self.assertLess(max(tiempos), LIMITE_ENTREGA_S)

    def test_la_hora_registrada_es_la_del_envio(self):
        antes = timezone.now()
        self.enviar()
        despues = timezone.now()
        creada = parse_datetime(self.bandeja()[0]['creada'])
        self.assertLessEqual(antes, creada)
        self.assertLessEqual(creada, despues)
        self.assertLess(despues - antes, timedelta(seconds=LIMITE_ENTREGA_S))

    def test_el_aviso_trae_los_datos_del_cliente_rubro_y_servicio(self):
        solicitud_id = self.enviar().json()['id']
        aviso = self.bandeja()[0]
        self.assertEqual(aviso['id'], solicitud_id)
        self.assertEqual(aviso['cliente_nombre'], 'María-José Ñahui')
        self.assertEqual(aviso['cliente_telefono'], '987654321')
        self.assertEqual(aviso['rubro'], 'Gasfitería')
        self.assertEqual(aviso['descripcion'], 'Fuga de agua bajo el lavadero, piso 2.')
        self.assertEqual((aviso['latitud'], aviso['longitud']), ('-12.019394', '-77.042793'))
        self.assertEqual(aviso['estado'], 'pendiente')
        self.assertIsNone(aviso['respondida'])

    def test_solo_la_recibe_el_prestador_elegido(self):
        otro = self.crear_prestador('vecino_t83')
        sesion_otro = Client()
        sesion_otro.force_login(otro.usuario)
        self.enviar()
        self.assertEqual(len(self.bandeja()), 1)
        self.assertEqual(self.bandeja(sesion_otro), [])

    def test_una_solicitud_rechazada_no_genera_aviso(self):
        casos = {
            'datos inválidos': {'cliente_telefono': '12345'},
            'fuera de cobertura': {'latitud': '-11.996911'},  # a 5,5 km
        }
        for caso, cambios in casos.items():
            with self.subTest(caso=caso):
                self.assertEqual(self.enviar(**cambios).status_code, 400)
        self.prestador.disponible = False
        self.prestador.save()
        self.assertEqual(self.enviar().status_code, 400)
        self.assertEqual(self.bandeja(), [])
