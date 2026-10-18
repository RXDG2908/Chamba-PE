from io import StringIO
from math import degrees, pi
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.management import CommandError, call_command
from django.test import SimpleTestCase, TestCase
from django.urls import reverse

from prestadores.management.commands import medir_busqueda
from prestadores.models import Prestador, Rubro

from .distancias import RADIO_TIERRA_KM, distancia_km
from .models import Solicitud


class DistanciaTests(SimpleTestCase):
    def test_distancia_conocida_en_ecuador(self):
        self.assertAlmostEqual(distancia_km(0, 0, 0, 1), pi * RADIO_TIERRA_KM / 180)

    def test_mismo_punto_y_antipodas(self):
        self.assertEqual(distancia_km(-12, -77, -12, -77), 0)
        self.assertAlmostEqual(distancia_km(0, 0, 0, 180), pi * RADIO_TIERRA_KM)


class BusquedaPrestadoresTests(TestCase):
    def setUp(self):
        self.rubro = Rubro.objects.create(nombre='Rubro de prueba')
        self.otro_rubro = Rubro.objects.create(nombre='Otro rubro de prueba')
        self.url = reverse('buscar_prestadores')
        self.assertEqual(self.url, '/api/prestadores/buscar/')
        self.parametros = {'rubro': self.rubro.id, 'latitud': 0, 'longitud': 0}

    def crear_prestador(self, rubros=None, **cambios):
        numero = Prestador.objects.count() + 1
        usuario = get_user_model().objects.create_user(username=f'prueba{numero}')
        datos = {
            'usuario': usuario, 'dni': f'{numero:08d}', 'nombres': 'Ana',
            'apellidos': 'Pérez', 'latitud': 0, 'longitud': 0,
            'radio_cobertura_km': 5, 'disponible': True,
        }
        prestador = Prestador.objects.create(**{**datos, **cambios})
        prestador.rubros.set(rubros if rubros is not None else [self.rubro])
        return prestador

    def buscar(self, **cambios):
        return self.client.get(self.url, {**self.parametros, **cambios})

    def test_ruta_de_busqueda_unica(self):
        respuesta = self.client.get(self.url)
        self.assertEqual(respuesta.status_code, 400)
        respuesta_duplicada = self.client.get('/api/servicios/prestadores/buscar/')
        self.assertEqual(respuesta_duplicada.status_code, 404)

    def test_filtra_rubro_disponibilidad_y_cobertura(self):
        incluido = self.crear_prestador(rubros=[self.rubro, self.otro_rubro])
        self.crear_prestador(rubros=[self.otro_rubro])
        self.crear_prestador(disponible=False)
        self.crear_prestador(longitud=1)
        respuesta = self.buscar()
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual([p['id'] for p in respuesta.json()], [incluido.id])

    def test_respuesta_solo_contiene_datos_publicos(self):
        self.crear_prestador()
        resultado = self.buscar().json()[0]
        self.assertEqual(set(resultado), {'id', 'nombres', 'apellidos', 'rubros', 'distancia_km'})
        self.assertEqual(resultado['rubros'], [{'id': self.rubro.id, 'nombre': self.rubro.nombre}])
        self.assertEqual(resultado['distancia_km'], 0)

    def test_radio_de_cada_prestador(self):
        self.crear_prestador(radio_cobertura_km=1)
        incluido = self.crear_prestador(radio_cobertura_km=5)
        respuesta = self.buscar(longitud=degrees(3 / RADIO_TIERRA_KM))
        self.assertEqual([p['id'] for p in respuesta.json()], [incluido.id])
        self.assertAlmostEqual(respuesta.json()[0]['distancia_km'], 3)

    def test_ordenados_por_distancia_de_menor_a_mayor(self):
        lejano = self.crear_prestador(longitud=degrees(3 / RADIO_TIERRA_KM))
        cercano = self.crear_prestador(longitud=degrees(1 / RADIO_TIERRA_KM))

        respuesta = self.buscar()

        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual([p['id'] for p in respuesta.json()], [cercano.id, lejano.id])

    def test_limite_inclusivo_y_puntos_a_ambos_lados(self):
        prestador = self.crear_prestador()
        for distancia, esperado in [(4.999, True), (5, True), (5.001, False)]:
            with self.subTest(distancia=distancia):
                respuesta = self.buscar(longitud=degrees(distancia / RADIO_TIERRA_KM))
                self.assertEqual([p['id'] for p in respuesta.json()], [prestador.id] if esperado else [])

    def test_comparacion_no_redondea_distancia(self):
        self.crear_prestador()
        with patch('servicios.views.distancia_km', return_value=5.000001):
            self.assertEqual(self.buscar().json(), [])

    def test_parametros_faltantes(self):
        respuesta = self.client.get(self.url)
        self.assertEqual(respuesta.status_code, 400)
        self.assertEqual(set(respuesta.json()), {'rubro', 'latitud', 'longitud'})
        for campo in self.parametros:
            with self.subTest(campo=campo):
                respuesta = self.client.get(self.url, {k: v for k, v in self.parametros.items() if k != campo})
                self.assertEqual(respuesta.status_code, 400)
                self.assertIn('obligatorio', respuesta.json()[campo][0])

    def test_parametros_invalidos(self):
        casos = {
            'rubro': ['', 'texto', '1.5', '0', '-1', '999999', '9' * 30],
            'latitud': ['', 'texto', 'NaN', 'Infinity', '-Infinity', '90.001', '-90.001'],
            'longitud': ['', 'texto', 'NaN', 'Infinity', '-Infinity', '180.001', '-180.001'],
        }
        for campo, valores in casos.items():
            for valor in valores:
                with self.subTest(campo=campo, valor=valor):
                    respuesta = self.buscar(**{campo: valor})
                    self.assertEqual(respuesta.status_code, 400)
                    self.assertIn(campo, respuesta.json())
        self.assertEqual(self.buscar(latitud=91).json()['latitud'], ['La coordenada debe estar entre -90 y 90 grados.'])
        self.assertEqual(self.buscar(rubro=999999).json()['rubro'], ['No existe un rubro con ese ID.'])

    def test_extremos_de_coordenadas_validos(self):
        for latitud, longitud in [(-90, -180), (90, 180)]:
            with self.subTest(latitud=latitud, longitud=longitud):
                self.assertEqual(self.buscar(latitud=latitud, longitud=longitud).status_code, 200)

    def test_busqueda_sin_resultados(self):
        respuesta = self.buscar()
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta.json(), [])

    def test_busqueda_expone_tiempo_sin_cambiar_el_json(self):
        respuesta = self.buscar()
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta.json(), [])
        self.assertGreaterEqual(float(respuesta['X-Execution-Time-Ms']), 0)


class RendimientoBusquedaTests(TestCase):
    """T3.4: la búsqueda real responde en menos de 3 s según X-Execution-Time-Ms."""

    def test_busqueda_con_datos_de_prueba_menor_a_3_s(self):
        rubro = medir_busqueda.Command().crear_datos(300)
        respuesta = self.client.get('/api/prestadores/buscar/', {
            'rubro': rubro.id, 'latitud': medir_busqueda.CENTRO[0], 'longitud': medir_busqueda.CENTRO[1],
        })
        self.assertEqual(respuesta.status_code, 200)
        distancias = [p['distancia_km'] for p in respuesta.json()]
        self.assertTrue(0 < len(distancias) < 300)
        self.assertEqual(distancias, sorted(distancias))
        self.assertLess(float(respuesta['X-Execution-Time-Ms']), medir_busqueda.LIMITE_MS)

    def test_comando_registra_evidencia_y_descarta_datos(self):
        rubros = Rubro.objects.count()
        with TemporaryDirectory() as carpeta:
            salida = Path(carpeta) / 'evidencia.txt'
            call_command('medir_busqueda', prestadores=50, repeticiones=2, salida=salida, stdout=StringIO())
            reporte = salida.read_text(encoding='utf-8')
        self.assertIn('GET /api/prestadores/buscar/?rubro=', reporte)
        self.assertIn('X-Execution-Time-Ms: mín', reporte)
        self.assertIn('Resultado: APROBADO', reporte)
        self.assertFalse(Prestador.objects.exists())
        self.assertEqual(Rubro.objects.count(), rubros)
        self.assertFalse(get_user_model().objects.exists())

    def test_comando_falla_si_supera_el_limite(self):
        with TemporaryDirectory() as carpeta, patch.object(medir_busqueda, 'LIMITE_MS', 0):
            salida = Path(carpeta) / 'evidencia.txt'
            with self.assertRaisesMessage(CommandError, 'el límite es 0 ms'):
                call_command('medir_busqueda', prestadores=5, repeticiones=1, salida=salida, stdout=StringIO())
            self.assertIn('Resultado: REPROBADO', salida.read_text(encoding='utf-8'))


class SolicitudTests(TestCase):
    def setUp(self):
        self.rubro = Rubro.objects.create(nombre='Rubro de prueba')
        self.otro_rubro = Rubro.objects.create(nombre='Otro rubro de prueba')
        self.prestador = self.crear_prestador('ana')
        self.datos = {
            'prestador': self.prestador.id, 'rubro': self.rubro.id,
            'cliente_nombre': 'Carlos', 'cliente_telefono': '999888777',
            'descripcion': 'Se rompió la tubería del baño.',
            'latitud': '0.010000', 'longitud': '0.000000',
        }

    def crear_prestador(self, username, **cambios):
        usuario = get_user_model().objects.create_user(username=username)
        datos = {
            'usuario': usuario, 'dni': f'{Prestador.objects.count() + 1:08d}', 'nombres': 'Ana',
            'apellidos': 'Pérez', 'latitud': 0, 'longitud': 0,
            'radio_cobertura_km': 5, 'disponible': True,
        }
        prestador = Prestador.objects.create(**{**datos, **cambios})
        prestador.rubros.set([self.rubro])
        return prestador

    def enviar(self, **cambios):
        return self.client.post(
            reverse('crear_solicitud'), {**self.datos, **cambios}, content_type='application/json',
        )

    def responder(self, accion, solicitud_id, usuario=None):
        self.client.force_login(usuario or self.prestador.usuario)
        return self.client.post(reverse(f'{accion}_solicitud', args=[solicitud_id]))

    def test_envio_valido_queda_pendiente(self):
        r = self.enviar()
        self.assertEqual(r.status_code, 201)
        self.assertEqual(r.json()['estado'], 'pendiente')
        self.assertEqual(self.prestador.solicitudes.count(), 1)

    def test_campos_obligatorios(self):
        r = self.client.post(reverse('crear_solicitud'), {}, content_type='application/json')
        self.assertEqual(r.status_code, 400)
        for campo in ['prestador', 'rubro', 'cliente_nombre', 'cliente_telefono', 'descripcion']:
            self.assertIn(campo, r.json())

    def test_prestador_no_disponible(self):
        self.prestador.disponible = False
        self.prestador.save()
        r = self.enviar()
        self.assertEqual(r.status_code, 400)
        self.assertIn('prestador', r.json())

    def test_rubro_que_no_ofrece(self):
        r = self.enviar(rubro=self.otro_rubro.id)
        self.assertEqual(r.status_code, 400)
        self.assertIn('rubro', r.json())

    def test_cliente_fuera_de_cobertura(self):
        r = self.enviar(latitud='1.000000')  # unos 111 km, fuera del radio de 5 km
        self.assertEqual(r.status_code, 400)
        self.assertIn('prestador', r.json())

    def test_prestador_ve_sus_solicitudes(self):
        self.enviar()
        otro = self.crear_prestador('beto')
        self.enviar(prestador=otro.id)
        self.client.force_login(self.prestador.usuario)
        r = self.client.get(reverse('solicitudes_recibidas'))
        self.assertEqual(r.status_code, 200)
        self.assertEqual(len(r.json()), 1)
        self.assertEqual(r.json()[0]['cliente_telefono'], '999888777')

    def test_bandeja_requiere_sesion_de_prestador(self):
        self.assertEqual(self.client.get(reverse('solicitudes_recibidas')).status_code, 403)
        self.client.force_login(get_user_model().objects.create_user(username='cliente'))
        self.assertEqual(self.client.get(reverse('solicitudes_recibidas')).status_code, 403)

    def test_aceptar_y_el_cliente_ve_el_cambio(self):
        solicitud_id = self.enviar().json()['id']
        r = self.responder('aceptar', solicitud_id)
        self.assertEqual(r.status_code, 200)
        self.client.logout()
        r = self.client.get(reverse('detalle_solicitud', args=[solicitud_id]))
        self.assertEqual(r.json()['estado'], 'aceptada')
        self.assertIsNotNone(r.json()['respondida'])
        self.assertNotIn('cliente_telefono', r.json())

    def test_rechazar(self):
        solicitud_id = self.enviar().json()['id']
        self.assertEqual(self.responder('rechazar', solicitud_id).status_code, 200)
        r = self.client.get(reverse('detalle_solicitud', args=[solicitud_id]))
        self.assertEqual(r.json()['estado'], 'rechazada')

    def test_no_se_responde_dos_veces(self):
        solicitud_id = self.enviar().json()['id']
        self.responder('aceptar', solicitud_id)
        r = self.responder('rechazar', solicitud_id)
        self.assertEqual(r.status_code, 409)
        self.assertEqual(Solicitud.objects.get().estado, 'aceptada')

    def test_otro_prestador_no_puede_responder(self):
        solicitud_id = self.enviar().json()['id']
        otro = self.crear_prestador('beto')
        self.assertEqual(self.responder('aceptar', solicitud_id, otro.usuario).status_code, 404)
        self.assertEqual(Solicitud.objects.get().estado, 'pendiente')

    def test_responder_sin_sesion(self):
        solicitud_id = self.enviar().json()['id']
        r = self.client.post(reverse('aceptar_solicitud', args=[solicitud_id]))
        self.assertEqual(r.status_code, 403)


class ValidacionSolicitudTests(TestCase):
    setUp = SolicitudTests.setUp
    crear_prestador = SolicitudTests.crear_prestador
    enviar = SolicitudTests.enviar
    """Regresiones del contrato de contacto; cada rechazo debe ser atómico."""

    def test_telefonos_validos(self):
        for telefono in ['987654321', '012345678']:
            with self.subTest(telefono=telefono):
                respuesta = self.enviar(cliente_telefono=telefono)
                self.assertEqual(respuesta.status_code, 201)
                self.assertEqual(Solicitud.objects.get(pk=respuesta.json()['id']).cliente_telefono, telefono)

    def comprobar_rechazos(self, campo, valores):
        for valor in valores:
            with self.subTest(campo=campo, valor=valor):
                antes = Solicitud.objects.count()
                respuesta = self.enviar(**{campo: valor})
                self.assertEqual(respuesta.status_code, 400)
                self.assertIn(campo, respuesta.json())
                self.assertEqual(Solicitud.objects.count(), antes)

    def test_telefonos_invalidos_no_guardan(self):
        self.comprobar_rechazos('cliente_telefono', [
            '', '   ', '12345678', '1234567890', '9' * 21, 'abcdefghi',
            '98765a321', '+51987654321', '987 65432', ' 987654321',
            '987654321 ', '９８７６５４３２１', '٩٨٧٦٥٤٣٢١', '98765432\n',
        ])

    def test_nombres_unicode_validos(self):
        for nombre in ["María-José O'Neill", 'Ángel Muñoz', '李 明', 'D’Arcy', 'Jose\u0301 Pérez']:
            with self.subTest(nombre=nombre):
                self.assertEqual(self.enviar(cliente_nombre=nombre).status_code, 201)

    def test_nombres_invalidos_no_guardan(self):
        self.comprobar_rechazos('cliente_nombre', [
            '', '   ', '123', 'Luis2', " -'’ ", 'Ana@', 'Ana\tMaría', 'a' * 101,
        ])

    def test_descripcion_numeros_puntuacion_y_limite(self):
        for descripcion in ['reparar 2 tuberías en el piso 3', 'Revisar: caño #2, ¿fuga?', 'a' * 500]:
            with self.subTest(descripcion=descripcion):
                self.assertEqual(self.enviar(descripcion=descripcion).status_code, 201)

    def test_descripciones_invalidas_no_guardan(self):
        self.comprobar_rechazos('descripcion', ['', '   ', '\t\n', 'a' * 501])
