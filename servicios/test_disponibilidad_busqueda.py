"""
T6.3: solo los prestadores activos (y verificados) aparecen en la búsqueda.

Un prestador aparece en `/api/prestadores/buscar/` solo si está a la vez Verificado
(documento aprobado por el administrador, T9.1) y Activo (`disponible`, T6.1). Las
pruebas de flujo cambian ambas condiciones por los endpoints reales, igual que lo
harían el panel de revisión y el interruptor de disponibilidad.
"""
import shutil
import tempfile
from itertools import count, product

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import Client, TestCase, override_settings
from django.urls import reverse

from administracion.models import RevisionDocumento
from prestadores.models import Prestador, Rubro

CENTRO = {'latitud': '-12.046374', 'longitud': '-77.042793'}
CLAVE = 'Clave-segura-123'
PDF = b'%PDF-1.4\n%prueba T6.3\n'


class CombinacionesEstadoDisponibilidadTests(TestCase):
    """Las dos condiciones se exigen juntas; ninguna basta sola."""

    def setUp(self):
        self.rubro = Rubro.objects.get(nombre='Gasfitería')
        self.numeros = count(1)

    def crear_prestador(self, estado, disponible):
        numero = next(self.numeros)
        prestador = Prestador.objects.create(
            usuario=get_user_model().objects.create_user(username=f'combinacion{numero}'),
            nombres='Ana', apellidos='Pérez', dni=f'6300{numero:04d}',
            radio_cobertura_km=5, estado=estado, disponible=disponible, **CENTRO,
        )
        prestador.rubros.set([self.rubro])
        return prestador

    def buscar(self):
        respuesta = self.client.get(reverse('buscar_prestadores'), {'rubro': self.rubro.id, **CENTRO})
        self.assertEqual(respuesta.status_code, 200)
        return [p['id'] for p in respuesta.json()]

    def test_solo_verificado_y_activo_aparece(self):
        for estado, disponible in product(Prestador.Estado.values, (True, False)):
            with self.subTest(estado=estado, disponible=disponible):
                Prestador.objects.all().delete()
                prestador = self.crear_prestador(estado, disponible)
                incluido = estado == Prestador.Estado.VERIFICADO and disponible
                self.assertEqual(self.buscar(), [prestador.id] if incluido else [])

    def test_las_seis_combinaciones_juntas(self):
        prestadores = {
            (estado, disponible): self.crear_prestador(estado, disponible)
            for estado, disponible in product(Prestador.Estado.values, (True, False))
        }
        esperado = prestadores[(Prestador.Estado.VERIFICADO, True)]
        self.assertEqual(self.buscar(), [esperado.id])

    def test_desactivar_a_uno_no_afecta_a_los_demas(self):
        primero = self.crear_prestador(Prestador.Estado.VERIFICADO, True)
        segundo = self.crear_prestador(Prestador.Estado.VERIFICADO, True)
        self.assertEqual(sorted(self.buscar()), [primero.id, segundo.id])
        Prestador.objects.filter(pk=primero.pk).update(disponible=False)
        self.assertEqual(self.buscar(), [segundo.id])


class FlujoVerificacionYDisponibilidadTests(TestCase):
    """Registro → certificado → aprobación → Activo/Inactivo, todo por la API."""

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.media = tempfile.mkdtemp()
        cls.ajustes = override_settings(MEDIA_ROOT=cls.media)
        cls.ajustes.enable()

    @classmethod
    def tearDownClass(cls):
        cls.ajustes.disable()
        shutil.rmtree(cls.media, ignore_errors=True)
        super().tearDownClass()

    def setUp(self):
        self.rubro = Rubro.objects.get(nombre='Gasfitería')
        self.cliente = Client()
        self.administrador = Client()
        self.administrador.force_login(
            get_user_model().objects.create_user(username='admin_t63', is_staff=True),
        )
        self.prestador = self.registrar()
        self.sesion_prestador = Client()
        self.assertTrue(self.sesion_prestador.login(username='prestador_t63', password=CLAVE))

    def registrar(self):
        respuesta = self.cliente.post(reverse('registro_prestador'), {
            'username': 'prestador_t63', 'email': 'prestador_t63@example.com', 'password': CLAVE,
            'nombres': 'Rosa', 'apellidos': 'Quispe', 'dni': '63636363', 'telefono': '987654321',
            'rubros': [self.rubro.id], 'radio_cobertura_km': 5, **CENTRO,
        }, content_type='application/json')
        self.assertEqual(respuesta.status_code, 201, respuesta.content)
        return Prestador.objects.get(pk=respuesta.json()['id'])

    def subir_certificado(self):
        archivo = SimpleUploadedFile('certificado.pdf', PDF, content_type='application/pdf')
        respuesta = self.cliente.post(
            reverse('upload_certification'), {'prestador': self.prestador.id, 'file': archivo},
        )
        self.assertEqual(respuesta.status_code, 201, respuesta.content)
        return RevisionDocumento.objects.filter(documento__prestador=self.prestador).latest('id')

    def decidir(self, revision, estado, comentario=''):
        respuesta = self.administrador.patch(
            reverse('revision-documento-detail', args=[revision.id]),
            {'estado': estado, 'comentario': comentario}, content_type='application/json',
        )
        self.assertEqual(respuesta.status_code, 200, respuesta.content)

    def cambiar_disponibilidad(self, valor):
        respuesta = self.sesion_prestador.patch(
            reverse('disponibilidad'), {'disponible': valor}, content_type='application/json',
        )
        self.assertEqual(respuesta.status_code, 200, respuesta.content)
        self.assertEqual(respuesta.json()['disponible'], valor)

    def aparece(self):
        respuesta = self.cliente.get(reverse('buscar_prestadores'), {'rubro': self.rubro.id, **CENTRO})
        self.assertEqual(respuesta.status_code, 200)
        return self.prestador.id in [p['id'] for p in respuesta.json()]

    def estado(self):
        self.prestador.refresh_from_db()
        return self.prestador.estado

    def test_recien_registrado_no_aparece(self):
        self.assertEqual(self.estado(), Prestador.Estado.PENDIENTE)
        self.assertFalse(self.prestador.disponible)
        self.assertFalse(self.aparece())

    def test_activo_pero_sin_verificar_no_aparece(self):
        self.cambiar_disponibilidad(True)
        self.assertFalse(self.aparece())
        # Con el documento subido sigue Pendiente hasta que el administrador decida.
        self.subir_certificado()
        self.assertEqual(self.estado(), Prestador.Estado.PENDIENTE)
        self.assertFalse(self.aparece())

    def test_verificado_pero_inactivo_no_aparece(self):
        self.decidir(self.subir_certificado(), 'aprobado')
        self.assertEqual(self.estado(), Prestador.Estado.VERIFICADO)
        self.assertFalse(self.aparece())

    def test_aparece_al_cumplir_ambas_y_desaparece_al_ponerse_inactivo(self):
        self.decidir(self.subir_certificado(), 'aprobado')
        self.cambiar_disponibilidad(True)
        self.assertTrue(self.aparece())
        # La búsqueda siguiente ya no lo trae: no hay caché ni espera.
        self.cambiar_disponibilidad(False)
        self.assertFalse(self.aparece())
        # Desactivarse no le quita la verificación: al volver a Activo reaparece.
        self.assertEqual(self.estado(), Prestador.Estado.VERIFICADO)
        self.cambiar_disponibilidad(True)
        self.assertTrue(self.aparece())

    def test_activo_con_documento_rechazado_no_aparece(self):
        self.cambiar_disponibilidad(True)
        self.decidir(self.subir_certificado(), 'rechazado', 'Documento ilegible.')
        self.assertEqual(self.estado(), Prestador.Estado.RECHAZADO)
        self.assertFalse(self.aparece())
        # Sube otro documento: vuelve a Pendiente y aparece solo cuando se aprueba.
        nueva = self.subir_certificado()
        self.assertEqual(self.estado(), Prestador.Estado.PENDIENTE)
        self.assertFalse(self.aparece())
        self.decidir(nueva, 'aprobado')
        self.assertTrue(self.aparece())

    def test_inactivo_tampoco_recibe_solicitudes(self):
        self.decidir(self.subir_certificado(), 'aprobado')
        self.cambiar_disponibilidad(False)
        respuesta = self.cliente.post(reverse('crear_solicitud'), {
            'prestador': self.prestador.id, 'rubro': self.rubro.id, 'cliente_nombre': 'Ana',
            'cliente_telefono': '912345678', 'descripcion': 'Fuga en el baño', **CENTRO,
        }, content_type='application/json')
        self.assertEqual(respuesta.status_code, 400)
        self.assertFalse(self.prestador.solicitudes.exists())
