"""Pruebas de HU-11 (T11.1): el prestador asignado avanza el servicio en orden y queda el historial."""
from django.contrib.auth import get_user_model
from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext
from django.urls import reverse

from prestadores.models import Prestador, Rubro

from .models import CambioEstado, Solicitud

E = Solicitud.Estado


class SeguimientoTests(TestCase):
    def setUp(self):
        self.rubro = Rubro.objects.create(nombre='Gasfitería de prueba')
        self.prestador = self.crear_prestador('ana')
        self.solicitud = self.crear_solicitud()

    def crear_prestador(self, username):
        usuario = get_user_model().objects.create_user(username=username)
        prestador = Prestador.objects.create(
            usuario=usuario, dni=f'{Prestador.objects.count() + 1:08d}', nombres='Ana', apellidos='Pérez',
            latitud=0, longitud=0, radio_cobertura_km=5, disponible=True, estado=Prestador.Estado.VERIFICADO,
        )
        prestador.rubros.set([self.rubro])
        return prestador

    def crear_solicitud(self, estado=E.PENDIENTE, prestador=None):
        return Solicitud.objects.create(
            prestador=prestador or self.prestador, rubro=self.rubro, cliente_nombre='Carlos',
            cliente_telefono='999888777', descripcion='Tubería rota.', latitud=0, longitud=0, estado=estado,
        )

    def aceptar(self, solicitud=None):
        self.client.force_login(self.prestador.usuario)
        return self.client.post(reverse('aceptar_solicitud', args=[(solicitud or self.solicitud).id]))

    def avanzar(self, estado, solicitud=None, usuario=None):
        self.client.force_login(usuario or self.prestador.usuario)
        return self.client.post(
            reverse('avanzar_solicitud', args=[(solicitud or self.solicitud).id]),
            {'estado': estado}, content_type='application/json',
        )

    def estado_guardado(self):
        self.solicitud.refresh_from_db()
        return self.solicitud.estado

    def test_ruta(self):
        self.assertEqual(reverse('avanzar_solicitud', args=[7]), '/api/servicios/solicitudes/7/avanzar/')

    def test_flujo_completo_en_orden(self):
        self.assertEqual(self.aceptar().status_code, 200)
        for estado in (E.EN_CAMINO, E.EN_SERVICIO, E.FINALIZADA):
            r = self.avanzar(estado)
            self.assertEqual(r.status_code, 200, estado)
            self.assertEqual(r.json()['estado'], estado)
            self.assertEqual(self.estado_guardado(), estado)
        self.assertIsNone(r.json()['siguiente_estado'])
        self.assertEqual(r.json()['estado_display'], 'Finalizada')

    def test_historial_con_cada_etapa_en_orden(self):
        self.aceptar()
        self.avanzar(E.EN_CAMINO)
        self.avanzar(E.EN_SERVICIO)
        self.assertEqual(list(self.solicitud.historial.values_list('estado', flat=True)),
                         [E.ACEPTADA, E.EN_CAMINO, E.EN_SERVICIO])
        # El cliente lo ve en la consulta de su solicitud, con la etiqueta «Confirmado».
        self.client.logout()
        datos = self.client.get(reverse('detalle_solicitud', args=[self.solicitud.id])).json()
        self.assertEqual(datos['estado_display'], 'En servicio')
        self.assertEqual([h['estado_display'] for h in datos['historial']],
                         ['Confirmado', 'En camino', 'En servicio'])
        self.assertNotIn('cliente_telefono', datos)

    def test_rechazar_tambien_queda_en_el_historial(self):
        self.client.force_login(self.prestador.usuario)
        self.client.post(reverse('rechazar_solicitud', args=[self.solicitud.id]))
        self.assertEqual(list(self.solicitud.historial.values_list('estado', flat=True)), [E.RECHAZADA])

    def test_no_salta_etapas(self):
        self.aceptar()
        for estado in (E.EN_SERVICIO, E.FINALIZADA):
            r = self.avanzar(estado)
            self.assertEqual(r.status_code, 409, estado)
            self.assertEqual(r.json()['estado_actual'], E.ACEPTADA)
        self.assertEqual(self.estado_guardado(), E.ACEPTADA)
        self.assertEqual(self.solicitud.historial.count(), 1)

    def test_no_retrocede(self):
        self.aceptar()
        self.avanzar(E.EN_CAMINO)
        self.avanzar(E.EN_SERVICIO)
        for estado in (E.EN_CAMINO, E.ACEPTADA, E.PENDIENTE):
            self.assertEqual(self.avanzar(estado).status_code, 409, estado)
        self.assertEqual(self.estado_guardado(), E.EN_SERVICIO)

    def test_el_mismo_paso_dos_veces_da_409(self):
        # Doble clic o dos pestañas: el segundo intento no avanza otra etapa.
        self.aceptar()
        self.assertEqual(self.avanzar(E.EN_CAMINO).status_code, 200)
        r = self.avanzar(E.EN_CAMINO)
        self.assertEqual(r.status_code, 409)
        self.assertIn('solo puede pasar a «En servicio»', r.json()['estado'])
        self.assertEqual(self.solicitud.historial.filter(estado=E.EN_CAMINO).count(), 1)

    def test_pendiente_no_se_avanza_sin_aceptar(self):
        r = self.avanzar(E.EN_CAMINO)
        self.assertEqual(r.status_code, 409)
        self.assertEqual(r.json()['estado'], 'Primero acepta la solicitud para empezar el seguimiento.')
        self.assertEqual(self.estado_guardado(), E.PENDIENTE)

    def test_rechazada_y_finalizada_son_finales(self):
        for final in (E.RECHAZADA, E.FINALIZADA):
            solicitud = self.crear_solicitud(final)
            for estado in E.values:
                r = self.avanzar(estado, solicitud)
                self.assertEqual(r.status_code, 409, (final, estado))
            solicitud.refresh_from_db()
            self.assertEqual(solicitud.estado, final)
        self.assertFalse(CambioEstado.objects.exists())

    def test_otro_prestador_recibe_404(self):
        self.aceptar()
        otro = self.crear_prestador('beto')
        self.assertEqual(self.avanzar(E.EN_CAMINO, usuario=otro.usuario).status_code, 404)
        self.assertEqual(self.estado_guardado(), E.ACEPTADA)

    def test_solicitud_inexistente_da_404(self):
        self.client.force_login(self.prestador.usuario)
        r = self.client.post(reverse('avanzar_solicitud', args=[999999]), {'estado': E.EN_CAMINO},
                             content_type='application/json')
        self.assertEqual(r.status_code, 404)

    def test_sin_sesion_o_sin_rol_de_prestador_da_403(self):
        self.aceptar()
        self.client.logout()
        url = reverse('avanzar_solicitud', args=[self.solicitud.id])
        self.assertEqual(self.client.post(url, {'estado': E.EN_CAMINO}, content_type='application/json')
                         .status_code, 403)
        cliente = get_user_model().objects.create_user('carlos')
        self.client.force_login(cliente)
        self.assertEqual(self.client.post(url, {'estado': E.EN_CAMINO}, content_type='application/json')
                         .status_code, 403)
        self.assertEqual(self.estado_guardado(), E.ACEPTADA)

    def test_estado_invalido_o_ausente_da_400(self):
        self.aceptar()
        for cuerpo in ({}, {'estado': 'volando'}, {'estado': ''}, {'estado': None}, {'estado': ['en_camino']}):
            self.client.force_login(self.prestador.usuario)
            r = self.client.post(reverse('avanzar_solicitud', args=[self.solicitud.id]), cuerpo,
                                 content_type='application/json')
            self.assertEqual(r.status_code, 400, cuerpo)
        self.assertEqual(self.estado_guardado(), E.ACEPTADA)

    def test_en_servicio_cabe_en_la_columna(self):
        # max_length=20 (servicios.0004): «en_servicio» tiene 11 caracteres y en PostgreSQL no cabía en 10.
        self.assertGreaterEqual(Solicitud._meta.get_field('estado').max_length, len(E.EN_SERVICIO))
        self.assertGreaterEqual(CambioEstado._meta.get_field('estado').max_length, len(E.EN_SERVICIO))

    def test_bandeja_del_prestador_indica_el_siguiente_paso(self):
        self.aceptar()
        datos = self.client.get(reverse('solicitudes_recibidas')).json()[0]
        self.assertEqual((datos['estado_display'], datos['siguiente_estado']), ('Confirmado', E.EN_CAMINO))
        self.assertEqual(len(datos['historial']), 1)

    def test_bandeja_no_hace_una_consulta_por_solicitud(self):
        for _ in range(5):
            self.aceptar(self.crear_solicitud())
        with CaptureQueriesContext(connection) as consultas:
            self.assertEqual(len(self.client.get(reverse('solicitudes_recibidas')).json()), 6)
        con_pocas = len(consultas)
        self.aceptar(self.crear_solicitud())
        with CaptureQueriesContext(connection) as consultas:
            self.client.get(reverse('solicitudes_recibidas'))
        self.assertEqual(len(consultas), con_pocas)

    def test_finalizada_por_el_prestador_habilita_la_calificacion(self):
        # Integración con HU-12 (T12.3): el cliente califica después de que el prestador finaliza.
        cliente = get_user_model().objects.create_user('carlos')
        self.solicitud.cliente = cliente
        self.solicitud.save()
        self.aceptar()
        self.avanzar(E.EN_CAMINO)
        self.client.force_login(cliente)
        r = self.client.post(reverse('calificar'), {'solicitud': self.solicitud.id, 'estrellas': 5},
                             content_type='application/json')
        self.assertEqual(r.status_code, 409)
        self.avanzar(E.EN_SERVICIO)
        self.avanzar(E.FINALIZADA)
        self.client.force_login(cliente)
        r = self.client.post(reverse('calificar'), {'solicitud': self.solicitud.id, 'estrellas': 5},
                             content_type='application/json')
        self.assertEqual(r.status_code, 201)
