"""Pruebas de HU-12: solo el cliente dueño califica, una vez, y solo con el servicio Finalizado."""
from decimal import Decimal

from django.contrib.auth import get_user_model
from django.test import Client, TestCase
from django.urls import reverse

from prestadores.models import Prestador, Rubro
from servicios.models import Solicitud

from .models import Calificacion
from .servicio import actualizar_promedio


class BaseCalificacion(TestCase):
    def setUp(self):
        self.rubro = Rubro.objects.create(nombre='Gasfitería de prueba')
        self.prestador = self.crear_prestador('ana')
        self.cliente = get_user_model().objects.create_user('carlos')

    def crear_prestador(self, username):
        usuario = get_user_model().objects.create_user(username=username)
        prestador = Prestador.objects.create(
            usuario=usuario, dni=f'{Prestador.objects.count() + 1:08d}', nombres='Ana', apellidos='Pérez',
            latitud=0, longitud=0, radio_cobertura_km=5, disponible=True, estado=Prestador.Estado.VERIFICADO,
        )
        prestador.rubros.set([self.rubro])
        return prestador

    def crear_solicitud(self, estado=Solicitud.Estado.FINALIZADA, cliente=None, prestador=None):
        return Solicitud.objects.create(
            prestador=prestador or self.prestador, rubro=self.rubro, cliente=cliente or self.cliente,
            cliente_nombre='Carlos', cliente_telefono='999888777', descripcion='Tubería rota.',
            latitud=0, longitud=0, estado=estado,
        )

    def calificar(self, solicitud_id, estrellas=5, comentario='Muy puntual.', usuario=None):
        self.client.force_login(usuario or self.cliente)
        return self.client.post(
            reverse('calificar'),
            {'solicitud': solicitud_id, 'estrellas': estrellas, 'comentario': comentario},
            content_type='application/json',
        )


class CalificarTests(BaseCalificacion):
    def test_califica_servicio_finalizado_y_actualiza_promedio(self):
        solicitud = self.crear_solicitud()
        r = self.calificar(solicitud.id, estrellas=4)
        self.assertEqual(r.status_code, 201)
        self.assertEqual(r.json()['calificacion']['estrellas'], 4)
        self.assertEqual(r.json()['prestador'], {
            'id': self.prestador.id, 'calificacion_promedio': 4.0, 'calificaciones_total': 1,
        })
        self.prestador.refresh_from_db()
        self.assertEqual(self.prestador.calificacion_promedio, Decimal('4.00'))
        self.assertEqual(self.prestador.calificaciones_total, 1)
        guardada = Calificacion.objects.get()
        self.assertEqual((guardada.cliente, guardada.prestador, guardada.comentario),
                         (self.cliente, self.prestador, 'Muy puntual.'))

    def test_promedio_con_varias_calificaciones_redondea_a_dos_decimales(self):
        for estrellas in (5, 4, 4):
            self.assertEqual(self.calificar(self.crear_solicitud().id, estrellas).status_code, 201)
        self.prestador.refresh_from_db()
        self.assertEqual(self.prestador.calificacion_promedio, Decimal('4.33'))
        self.assertEqual(self.prestador.calificaciones_total, 3)

    def test_solo_con_servicio_finalizado(self):
        for estado in (Solicitud.Estado.PENDIENTE, Solicitud.Estado.ACEPTADA, Solicitud.Estado.RECHAZADA):
            r = self.calificar(self.crear_solicitud(estado).id)
            self.assertEqual(r.status_code, 409, estado)
            self.assertEqual(r.json()['detail'], 'Solo puedes calificar un servicio Finalizado.')
        self.assertFalse(Calificacion.objects.exists())
        self.prestador.refresh_from_db()
        self.assertIsNone(self.prestador.calificacion_promedio)
        self.assertEqual(self.prestador.calificaciones_total, 0)

    def test_una_sola_vez(self):
        solicitud = self.crear_solicitud()
        self.assertEqual(self.calificar(solicitud.id, 5).status_code, 201)
        r = self.calificar(solicitud.id, 1)
        self.assertEqual(r.status_code, 409)
        self.assertEqual(r.json()['detail'], 'Ya calificaste este servicio.')
        self.prestador.refresh_from_db()
        self.assertEqual((self.prestador.calificacion_promedio, self.prestador.calificaciones_total),
                         (Decimal('5.00'), 1))

    def test_comentario_opcional_y_recortado(self):
        r = self.calificar(self.crear_solicitud().id, comentario='   ')
        self.assertEqual(r.status_code, 201)
        self.assertEqual(Calificacion.objects.get().comentario, '')
        self.client.force_login(self.cliente)
        r = self.client.post(reverse('calificar'), {'solicitud': self.crear_solicitud().id, 'estrellas': 3},
                             content_type='application/json')
        self.assertEqual(r.status_code, 201)

    def test_estrellas_y_comentario_invalidos(self):
        solicitud = self.crear_solicitud()
        for estrellas in (0, 6, -1, '5', 4.5, True, None, [5]):
            r = self.calificar(solicitud.id, estrellas=estrellas)
            self.assertEqual(r.status_code, 400, estrellas)
            self.assertIn('estrellas', r.json())
        r = self.calificar(solicitud.id, comentario='x' * 501)
        self.assertEqual(r.status_code, 400)
        self.assertIn('comentario', r.json())
        self.assertFalse(Calificacion.objects.exists())

    def test_solicitud_invalida(self):
        for valor in ('1', 0, True, None):
            r = self.calificar(valor)
            self.assertEqual(r.status_code, 400, valor)
            self.assertIn('solicitud', r.json())
        self.assertEqual(self.calificar(999).status_code, 404)


class PermisosCalificacionTests(BaseCalificacion):
    def test_sin_sesion_403(self):
        solicitud = self.crear_solicitud()
        r = self.client.post(reverse('calificar'), {'solicitud': solicitud.id, 'estrellas': 5},
                             content_type='application/json')
        self.assertEqual(r.status_code, 403)
        self.assertEqual(self.client.get(reverse('mis_servicios')).status_code, 403)

    def test_prestador_y_administrador_no_califican(self):
        solicitud = self.crear_solicitud()
        admin = get_user_model().objects.create_user('admin_hu12', is_staff=True)
        for usuario in (self.prestador.usuario, admin):
            self.assertEqual(self.calificar(solicitud.id, usuario=usuario).status_code, 403)
        self.assertFalse(Calificacion.objects.exists())

    def test_otro_cliente_recibe_404(self):
        solicitud = self.crear_solicitud()
        otro = get_user_model().objects.create_user('intruso')
        self.assertEqual(self.calificar(solicitud.id, usuario=otro).status_code, 404)
        self.assertFalse(Calificacion.objects.exists())

    def test_solicitud_anonima_no_se_califica(self):
        solicitud = self.crear_solicitud()
        Solicitud.objects.filter(pk=solicitud.pk).update(cliente=None)
        self.assertEqual(self.calificar(solicitud.id).status_code, 404)

    def test_csrf_exigido(self):
        solicitud = self.crear_solicitud()
        navegador = Client(enforce_csrf_checks=True)
        navegador.force_login(self.cliente)
        r = navegador.post(reverse('calificar'), {'solicitud': solicitud.id, 'estrellas': 5},
                           content_type='application/json')
        self.assertEqual(r.status_code, 403)
        self.assertFalse(Calificacion.objects.exists())


class MisServiciosTests(BaseCalificacion):
    def test_lista_solo_las_del_cliente_con_su_estado(self):
        finalizada = self.crear_solicitud()
        pendiente = self.crear_solicitud(Solicitud.Estado.PENDIENTE)
        self.crear_solicitud(cliente=get_user_model().objects.create_user('otro_cliente'))
        self.client.force_login(self.cliente)
        r = self.client.get(reverse('mis_servicios'))
        self.assertEqual(r.status_code, 200)
        por_id = {s['id']: s for s in r.json()}
        self.assertEqual(set(por_id), {finalizada.id, pendiente.id})
        self.assertTrue(por_id[finalizada.id]['puede_calificar'])
        self.assertFalse(por_id[pendiente.id]['puede_calificar'])
        self.assertEqual(por_id[finalizada.id]['prestador'], {'id': self.prestador.id, 'nombre': 'Ana Pérez'})
        self.assertEqual(por_id[finalizada.id]['estado_display'], 'Finalizada')
        self.assertNotIn('cliente_telefono', por_id[finalizada.id])

    def test_muestra_la_calificacion_dada(self):
        solicitud = self.crear_solicitud()
        self.calificar(solicitud.id, 3, 'Llegó tarde.')
        r = self.client.get(reverse('mis_servicios'))
        servicio = r.json()[0]
        self.assertFalse(servicio['puede_calificar'])
        self.assertEqual(servicio['calificacion']['estrellas'], 3)
        self.assertEqual(servicio['calificacion']['comentario'], 'Llegó tarde.')

    def test_prestador_no_ve_mis_servicios(self):
        self.client.force_login(self.prestador.usuario)
        self.assertEqual(self.client.get(reverse('mis_servicios')).status_code, 403)


class SolicitudConCuentaTests(BaseCalificacion):
    def datos(self):
        return {
            'prestador': self.prestador.id, 'rubro': self.rubro.id, 'cliente_nombre': 'Carlos',
            'cliente_telefono': '999888777', 'descripcion': 'Tubería rota.',
            'latitud': '0.010000', 'longitud': '0.000000',
        }

    def test_solicitud_con_sesion_de_cliente_queda_a_su_nombre(self):
        self.client.force_login(self.cliente)
        r = self.client.post(reverse('crear_solicitud'), self.datos(), content_type='application/json')
        self.assertEqual(r.status_code, 201)
        self.assertEqual(Solicitud.objects.get().cliente, self.cliente)

    def test_visitante_sigue_pudiendo_enviar_sin_cuenta(self):
        r = Client(enforce_csrf_checks=True).post(
            reverse('crear_solicitud'), self.datos(), content_type='application/json',
        )
        self.assertEqual(r.status_code, 201)
        self.assertIsNone(Solicitud.objects.get().cliente)

    def test_prestador_con_sesion_no_queda_como_cliente(self):
        otro = self.crear_prestador('beto')
        self.client.force_login(otro.usuario)
        r = self.client.post(reverse('crear_solicitud'), self.datos(), content_type='application/json')
        self.assertEqual(r.status_code, 201)
        self.assertIsNone(Solicitud.objects.get().cliente)

    def test_cliente_con_sesion_necesita_csrf(self):
        navegador = Client(enforce_csrf_checks=True)
        navegador.force_login(self.cliente)
        r = navegador.post(reverse('crear_solicitud'), self.datos(), content_type='application/json')
        self.assertEqual(r.status_code, 403)
        self.assertFalse(Solicitud.objects.exists())


class BusquedaMuestraPromedioTests(BaseCalificacion):
    def buscar(self):
        return self.client.get(reverse('buscar_prestadores'), {
            'rubro': self.rubro.id, 'latitud': '0.0', 'longitud': '0.0',
        }).json()[0]

    def test_sin_calificaciones_el_promedio_es_nulo(self):
        resultado = self.buscar()
        self.assertIsNone(resultado['calificacion_promedio'])
        self.assertEqual(resultado['calificaciones_total'], 0)

    def test_con_calificaciones_muestra_promedio_y_total(self):
        self.calificar(self.crear_solicitud().id, 5)
        self.calificar(self.crear_solicitud().id, 4)
        self.client.logout()
        resultado = self.buscar()
        self.assertEqual(resultado['calificacion_promedio'], 4.5)
        self.assertEqual(resultado['calificaciones_total'], 2)


class PromedioTests(BaseCalificacion):
    def test_recalcular_sin_calificaciones_lo_deja_vacio(self):
        solicitud = self.crear_solicitud()
        self.calificar(solicitud.id, 2)
        Calificacion.objects.all().delete()
        actualizar_promedio(self.prestador.id)
        self.prestador.refresh_from_db()
        self.assertIsNone(self.prestador.calificacion_promedio)
        self.assertEqual(self.prestador.calificaciones_total, 0)
