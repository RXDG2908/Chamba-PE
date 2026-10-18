"""T9.1: modelo y API de revisión de documentos."""
from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from rest_framework.test import APIClient

from certificaciones.models import CertificacionPrestador
from prestadores.models import Prestador

from .models import RevisionDocumento


class RevisionDocumentoTests(TestCase):
    def setUp(self):
        Usuario = get_user_model()
        self.admin = Usuario.objects.create_user(username='admin_revisor', is_staff=True)
        self.no_admin = Usuario.objects.create_user(username='usuario_comun')
        self.prestador = Prestador.objects.create(
            usuario=Usuario.objects.create_user(username='prestador_revision'),
            nombres='Ana', apellidos='Prueba', dni='55667788',
            latitud='-12.046374', longitud='-77.042793', radio_cobertura_km=5,
        )
        self.certificacion = CertificacionPrestador.objects.create(prestador=self.prestador)
        self.revision = self.certificacion.revision
        self.client = APIClient()
        self.client.force_authenticate(self.admin)
        self.url_lista = reverse('revision-documento-list')
        self.url_detalle = reverse('revision-documento-detail', args=[self.revision.id])

    # --- Modelo ---

    def test_certificacion_nueva_crea_revision_pendiente(self):
        self.assertEqual(self.revision.estado, RevisionDocumento.Estado.PENDIENTE)
        self.assertIsNone(self.revision.revisor)
        self.assertIsNone(self.revision.fecha_revision)

    def test_editar_certificacion_no_duplica_revision(self):
        self.certificacion.estado = 'Pendiente'
        self.certificacion.save()
        self.assertEqual(RevisionDocumento.objects.count(), 1)

    # --- Seguridad ---

    def test_sin_autenticar_devuelve_403(self):
        respuesta = APIClient().get(self.url_lista)
        self.assertEqual(respuesta.status_code, 403)

    def test_usuario_no_admin_devuelve_403(self):
        cliente = APIClient()
        cliente.force_authenticate(self.no_admin)
        self.assertEqual(cliente.get(self.url_lista).status_code, 403)
        respuesta = cliente.patch(self.url_detalle, {'estado': 'aprobado'}, format='json')
        self.assertEqual(respuesta.status_code, 403)

    # --- Listar y consultar ---

    def test_listar_devuelve_200_con_datos_del_prestador(self):
        respuesta = self.client.get(self.url_lista)
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(len(respuesta.data), 1)
        self.assertEqual(respuesta.data[0]['prestador']['dni'], '55667788')
        self.assertEqual(respuesta.data[0]['estado'], 'pendiente')

    def test_listar_filtra_por_estado(self):
        otra = CertificacionPrestador.objects.create(prestador=self.prestador)
        RevisionDocumento.objects.filter(documento=otra).update(estado='aprobado')
        respuesta = self.client.get(self.url_lista, {'estado': 'pendiente'})
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual([r['id'] for r in respuesta.data], [self.revision.id])

    def test_consultar_detalle_devuelve_200(self):
        respuesta = self.client.get(self.url_detalle)
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta.data['documento'], self.certificacion.id)

    def test_consultar_inexistente_devuelve_404(self):
        url = reverse('revision-documento-detail', args=[9999])
        self.assertEqual(self.client.get(url).status_code, 404)

    # --- Crear ---

    def test_crear_revision_para_documento_sin_revision_devuelve_201(self):
        RevisionDocumento.objects.all().delete()
        respuesta = self.client.post(self.url_lista, {'documento': self.certificacion.id}, format='json')
        self.assertEqual(respuesta.status_code, 201)
        self.assertEqual(respuesta.data['estado'], 'pendiente')

    def test_crear_revision_duplicada_devuelve_400(self):
        respuesta = self.client.post(self.url_lista, {'documento': self.certificacion.id}, format='json')
        self.assertEqual(respuesta.status_code, 400)

    def test_crear_ignora_estado_enviado_por_el_cliente(self):
        RevisionDocumento.objects.all().delete()
        respuesta = self.client.post(
            self.url_lista, {'documento': self.certificacion.id, 'estado': 'aprobado'}, format='json',
        )
        self.assertEqual(respuesta.status_code, 201)
        self.assertEqual(respuesta.data['estado'], 'pendiente')

    # --- Aprobar / rechazar ---

    def test_aprobar_devuelve_200_y_registra_revisor_y_fecha(self):
        respuesta = self.client.patch(self.url_detalle, {'estado': 'aprobado'}, format='json')
        self.assertEqual(respuesta.status_code, 200)
        self.revision.refresh_from_db()
        self.certificacion.refresh_from_db()
        self.assertEqual(self.revision.estado, 'aprobado')
        self.assertEqual(self.revision.revisor, self.admin)
        self.assertIsNotNone(self.revision.fecha_revision)
        self.assertEqual(self.certificacion.estado, 'Aprobado')
        self.assertEqual(respuesta.data['revisor'], 'admin_revisor')

    def test_rechazar_con_comentario_devuelve_200(self):
        respuesta = self.client.patch(
            self.url_detalle, {'estado': 'rechazado', 'comentario': 'Documento ilegible'}, format='json',
        )
        self.assertEqual(respuesta.status_code, 200)
        self.revision.refresh_from_db()
        self.assertEqual(self.revision.estado, 'rechazado')
        self.assertEqual(self.revision.comentario, 'Documento ilegible')

    def test_rechazar_sin_comentario_devuelve_400(self):
        respuesta = self.client.patch(
            self.url_detalle, {'estado': 'rechazado', 'comentario': '   '}, format='json',
        )
        self.assertEqual(respuesta.status_code, 400)
        self.assertIn('comentario', respuesta.data)
        self.revision.refresh_from_db()
        self.assertEqual(self.revision.estado, 'pendiente')

    def test_estado_invalido_devuelve_400(self):
        for estado in ['pendiente', 'borrado', '']:
            respuesta = self.client.patch(self.url_detalle, {'estado': estado}, format='json')
            self.assertEqual(respuesta.status_code, 400, estado)

    def test_put_y_delete_no_estan_permitidos(self):
        self.assertEqual(self.client.put(self.url_detalle, {}, format='json').status_code, 405)
        self.assertEqual(self.client.delete(self.url_detalle).status_code, 405)
