"""Impedimento 15: el admin de Django no puede saltarse las reglas de las solicitudes."""
from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone

from calificaciones.models import Calificacion
from prestadores.models import Prestador, Rubro

from .models import CambioEstado, Solicitud


class AdminDeSolicitudesTests(TestCase):
    def setUp(self):
        User = get_user_model()
        self.rubro = Rubro.objects.create(nombre='Prueba admin')
        self.ana = self.prestador('ana_adm', '20000001')
        self.beto = self.prestador('beto_adm', '20000002')
        self.cliente = User.objects.create_user('cliente_adm')
        self.solicitud = Solicitud.objects.create(
            prestador=self.ana, rubro=self.rubro, cliente=self.cliente, cliente_nombre='Lucía',
            cliente_telefono='987654321', descripcion='Fuga', latitud='-12.046374', longitud='-77.042793',
            estado=Solicitud.Estado.ACEPTADA, respondida=timezone.now(),
        )
        CambioEstado.objects.create(solicitud=self.solicitud, estado=Solicitud.Estado.ACEPTADA, fecha=timezone.now())
        self.client.force_login(User.objects.create_superuser('super_adm', 'super@example.invalid', None))

    def prestador(self, usuario, dni):
        p = Prestador.objects.create(
            usuario=get_user_model().objects.create_user(usuario), nombres=usuario, apellidos='Prueba', dni=dni,
            latitud='-12.046374', longitud='-77.042793', radio_cobertura_km=5, estado='verificado',
        )
        p.rubros.add(self.rubro)
        return p

    def url(self, accion, *args):
        return reverse(f'admin:servicios_solicitud_{accion}', args=args)

    def test_lista_y_detalle_se_pueden_consultar(self):
        self.assertContains(self.client.get(self.url('changelist')), 'Lucía')
        detalle = self.client.get(self.url('change', self.solicitud.pk))
        self.assertEqual(detalle.status_code, 200)
        self.assertNotContains(detalle, 'name="_save"')  # sin botón de guardar
        self.assertNotContains(detalle, 'name="prestador"')

    def test_no_se_edita_ni_el_estado_ni_el_prestador(self):
        respuesta = self.client.post(self.url('change', self.solicitud.pk), {
            'prestador': self.beto.pk, 'rubro': self.rubro.pk, 'cliente_nombre': 'Otro',
            'cliente_telefono': '900000000', 'descripcion': 'x', 'latitud': '0', 'longitud': '0',
            'estado': 'pendiente', 'respondida': '',
            'historial-TOTAL_FORMS': '0', 'historial-INITIAL_FORMS': '0',
        })
        self.assertEqual(respuesta.status_code, 403)
        self.solicitud.refresh_from_db()
        self.assertEqual(
            (self.solicitud.prestador_id, self.solicitud.estado, self.solicitud.cliente_nombre),
            (self.ana.pk, 'aceptada', 'Lucía'),
        )
        self.assertIsNotNone(self.solicitud.respondida)
        self.assertEqual(self.solicitud.historial.count(), 1)

    def test_no_se_crean_solicitudes_sin_pasar_por_la_api(self):
        self.assertEqual(self.client.get(self.url('add')).status_code, 403)
        antes = Solicitud.objects.count()
        respuesta = self.client.post(self.url('add'), {
            'prestador': self.beto.pk, 'rubro': self.rubro.pk, 'cliente_nombre': 'X', 'cliente_telefono': '900000000',
            'descripcion': 'x', 'latitud': '0', 'longitud': '0',
            'historial-TOTAL_FORMS': '0', 'historial-INITIAL_FORMS': '0',
        })
        self.assertEqual(respuesta.status_code, 403)
        self.assertEqual(Solicitud.objects.count(), antes)

    def test_borrar_quita_la_solicitud_y_recalcula_el_promedio(self):
        self.solicitud.estado = Solicitud.Estado.FINALIZADA
        self.solicitud.save(update_fields=['estado'])
        Calificacion.objects.create(solicitud=self.solicitud, prestador=self.ana, cliente=self.cliente, estrellas=5)
        Prestador.objects.filter(pk=self.ana.pk).update(calificacion_promedio=5, calificaciones_total=1)
        respuesta = self.client.post(self.url('delete', self.solicitud.pk), {'post': 'yes'})
        self.assertRedirects(respuesta, reverse('admin:index'))  # sin permiso de editar, Django vuelve al inicio
        self.assertFalse(Solicitud.objects.filter(pk=self.solicitud.pk).exists())
        self.ana.refresh_from_db()
        self.assertEqual((self.ana.calificacion_promedio, self.ana.calificaciones_total), (None, 0))
