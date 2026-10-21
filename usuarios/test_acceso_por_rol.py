"""
T5.3: pruebas de autenticación y acceso por rol.

Recorre las rutas reales de la API con una cuenta de cada rol (cliente, prestador,
administrador con is_staff y superusuario) que inicia sesión por /api/usuarios/login/.
La API solo usa la sesión de Django (SessionAuthentication), que no envía la cabecera
WWW-Authenticate: por eso DRF responde 403, nunca 401, cuando faltan o no sirven
las credenciales.
"""
from base64 import b64encode
from tempfile import TemporaryDirectory
from unittest import expectedFailure

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase, override_settings
from django.urls import reverse
from rest_framework.test import APIClient

from administracion.models import RevisionDocumento
from certificaciones.models import CertificacionPrestador
from certificaciones.tests import pdf_valido
from prestadores.models import Prestador, Rubro
from servicios.models import Solicitud

User = get_user_model()
CLAVE = 'Clave-segura-2026'


@override_settings(PASSWORD_HASHERS=['django.contrib.auth.hashers.MD5PasswordHasher'])
class AccesoPorRolBase(TestCase):
    """Una cuenta por rol, un prestador verificado y otro con un documento en revisión."""

    @classmethod
    def setUpTestData(cls):
        cls.rubro = Rubro.objects.create(nombre='Rubro T5.3')
        cls.cliente = User.objects.create_user(username='cliente_t53', password=CLAVE)
        cls.admin = User.objects.create_user(username='admin_t53', password=CLAVE, is_staff=True)
        cls.superusuario = User.objects.create_superuser(
            username='super_t53', password=CLAVE, email='super@chamba.pe',
        )
        cls.prestador = cls.crear_prestador('prestador_t53', '53000001')
        cls.otro_prestador = cls.crear_prestador('otro_prestador_t53', '53000002')
        # Al subir el documento, otro_prestador vuelve a Pendiente hasta que lo aprueben.
        cls.certificacion = CertificacionPrestador.objects.create(prestador=cls.otro_prestador)

    @classmethod
    def crear_prestador(cls, username, dni):
        prestador = Prestador.objects.create(
            usuario=User.objects.create_user(username=username, password=CLAVE),
            nombres='Ana', apellidos='Pérez', dni=dni, latitud=0, longitud=0,
            radio_cobertura_km=5, disponible=True, estado=Prestador.Estado.VERIFICADO,
        )
        prestador.rubros.set([cls.rubro])
        return prestador

    def sesion(self, usuario):
        """Cliente HTTP con la sesión abierta por el login real de T5.1."""
        cliente = APIClient()
        if usuario is not None:
            r = cliente.post(
                reverse('login'), {'username': usuario.username, 'password': CLAVE}, format='json',
            )
            self.assertEqual(r.status_code, 200, r.content)
        return cliente

    def cuentas(self):
        return {
            'anonimo': None,
            'cliente': self.cliente,
            'prestador': self.prestador.usuario,
            'administrador': self.admin,
            'superusuario': self.superusuario,
        }

    def nueva_solicitud(self, prestador=None):
        return Solicitud.objects.create(
            prestador=prestador or self.prestador, rubro=self.rubro,
            cliente_nombre='Carlos', cliente_telefono='999888777',
            descripcion='Fuga en el baño.', latitud=0, longitud=0,
        )


class AutenticacionYRolTests(AccesoPorRolBase):
    """T5.1 de punta a punta: el login y /yo/ devuelven el rol de cada cuenta."""

    def test_login_y_yo_devuelven_el_rol_de_cada_cuenta(self):
        esperado = {
            'cliente': 'cliente', 'prestador': 'prestador',
            'administrador': 'administrador', 'superusuario': 'administrador',
        }
        for nombre, rol in esperado.items():
            with self.subTest(cuenta=nombre):
                usuario = self.cuentas()[nombre]
                cliente = APIClient()
                r = cliente.post(
                    reverse('login'), {'username': usuario.username, 'password': CLAVE},
                    format='json',
                )
                self.assertEqual(r.status_code, 200)
                self.assertEqual(r.json()['rol'], rol)
                yo = cliente.get(reverse('usuario_actual'))
                self.assertEqual(yo.status_code, 200)
                self.assertEqual(yo.json()['rol'], rol)

    def test_solo_el_prestador_recibe_su_prestador_id(self):
        yo = self.sesion(self.prestador.usuario).get(reverse('usuario_actual')).json()
        self.assertEqual(yo['prestador_id'], self.prestador.id)
        yo = self.sesion(self.cliente).get(reverse('usuario_actual')).json()
        self.assertIsNone(yo['prestador_id'])

    def test_cliente_registrado_por_la_api_queda_como_cliente(self):
        cliente = APIClient()
        r = cliente.post(reverse('registro_cliente'), {
            'username': 'nuevo_t53', 'email': 'nuevo@chamba.pe', 'password': CLAVE,
            'nombres': 'Rosa', 'apellidos': 'Quispe',
        }, format='json')
        self.assertEqual(r.status_code, 201)
        self.assertEqual(cliente.get(reverse('usuario_actual')).json()['rol'], 'cliente')

    def test_contrasena_incorrecta_no_abre_sesion(self):
        cliente = APIClient()
        r = cliente.post(
            reverse('login'), {'username': self.admin.username, 'password': 'otra'}, format='json',
        )
        self.assertEqual(r.status_code, 400)
        self.assertEqual(cliente.get(reverse('usuario_actual')).status_code, 403)


class RutasDeAdministracionTests(AccesoPorRolBase):
    """/api/administracion/revisiones/: solo administradores (is_staff o superusuario)."""

    def setUp(self):
        self.revision = self.certificacion.revision
        self.url_lista = reverse('revision-documento-list')
        self.url_detalle = reverse('revision-documento-detail', args=[self.revision.id])

    def test_administradores_listan_y_consultan(self):
        for nombre in ('administrador', 'superusuario'):
            with self.subTest(cuenta=nombre):
                cliente = self.sesion(self.cuentas()[nombre])
                self.assertEqual(cliente.get(self.url_lista).status_code, 200)
                self.assertEqual(cliente.get(self.url_detalle).status_code, 200)

    def test_administrador_crea_revision(self):
        RevisionDocumento.objects.all().delete()
        r = self.sesion(self.admin).post(
            self.url_lista, {'documento': self.certificacion.id}, format='json',
        )
        self.assertEqual(r.status_code, 201)

    def test_administrador_aprueba_y_queda_como_revisor(self):
        r = self.sesion(self.admin).patch(self.url_detalle, {'estado': 'aprobado'}, format='json')
        self.assertEqual(r.status_code, 200)
        self.revision.refresh_from_db()
        self.assertEqual(self.revision.revisor, self.admin)

    def test_superusuario_rechaza_con_comentario(self):
        r = self.sesion(self.superusuario).patch(
            self.url_detalle, {'estado': 'rechazado', 'comentario': 'Ilegible'}, format='json',
        )
        self.assertEqual(r.status_code, 200)

    def test_cliente_prestador_y_anonimo_reciben_403_en_todo(self):
        RevisionDocumento.objects.all().delete()
        revision = RevisionDocumento.objects.create(documento=self.certificacion)
        url_detalle = reverse('revision-documento-detail', args=[revision.id])
        peticiones = {
            'listar': lambda c: c.get(self.url_lista),
            'consultar': lambda c: c.get(url_detalle),
            'crear': lambda c: c.post(self.url_lista, {'documento': self.certificacion.id}, format='json'),
            'aprobar': lambda c: c.patch(url_detalle, {'estado': 'aprobado'}, format='json'),
        }
        for nombre in ('anonimo', 'cliente', 'prestador'):
            cliente = self.sesion(self.cuentas()[nombre])
            for accion, peticion in peticiones.items():
                with self.subTest(cuenta=nombre, accion=accion):
                    self.assertEqual(peticion(cliente).status_code, 403)
        revision.refresh_from_db()
        self.assertEqual(revision.estado, RevisionDocumento.Estado.PENDIENTE)
        self.assertEqual(RevisionDocumento.objects.count(), 1)

    def test_el_prestador_no_puede_aprobar_su_propio_documento(self):
        r = self.sesion(self.otro_prestador.usuario).patch(
            self.url_detalle, {'estado': 'aprobado'}, format='json',
        )
        self.assertEqual(r.status_code, 403)
        self.revision.refresh_from_db()
        self.assertEqual(self.revision.estado, RevisionDocumento.Estado.PENDIENTE)


class RutasDePrestadorTests(AccesoPorRolBase):
    """Bandeja y respuesta de solicitudes: solo el prestador (EsPrestador)."""

    def test_bandeja_solo_para_prestadores(self):
        self.nueva_solicitud()
        esperado = {
            'anonimo': 403, 'cliente': 403, 'prestador': 200,
            'administrador': 403, 'superusuario': 403,
        }
        for nombre, codigo in esperado.items():
            with self.subTest(cuenta=nombre):
                r = self.sesion(self.cuentas()[nombre]).get(reverse('solicitudes_recibidas'))
                self.assertEqual(r.status_code, codigo)

    def test_bandeja_muestra_solo_las_solicitudes_propias(self):
        propia = self.nueva_solicitud()
        self.nueva_solicitud(self.otro_prestador)
        r = self.sesion(self.prestador.usuario).get(reverse('solicitudes_recibidas'))
        self.assertEqual([s['id'] for s in r.json()], [propia.id])

    def test_aceptar_y_rechazar_no_lo_hacen_otros_roles(self):
        solicitud = self.nueva_solicitud()
        for accion in ('aceptar', 'rechazar'):
            url = reverse(f'{accion}_solicitud', args=[solicitud.id])
            for nombre in ('anonimo', 'cliente', 'administrador', 'superusuario'):
                with self.subTest(accion=accion, cuenta=nombre):
                    self.assertEqual(self.sesion(self.cuentas()[nombre]).post(url).status_code, 403)
        solicitud.refresh_from_db()
        self.assertEqual(solicitud.estado, Solicitud.Estado.PENDIENTE)

    def test_otro_prestador_no_ve_la_solicitud_ajena(self):
        solicitud = self.nueva_solicitud()
        r = self.sesion(self.otro_prestador.usuario).post(
            reverse('aceptar_solicitud', args=[solicitud.id]),
        )
        self.assertEqual(r.status_code, 404)

    def test_el_prestador_duenio_acepta(self):
        solicitud = self.nueva_solicitud()
        r = self.sesion(self.prestador.usuario).post(
            reverse('aceptar_solicitud', args=[solicitud.id]),
        )
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()['estado'], Solicitud.Estado.ACEPTADA)


class CargaDeCertificacionPorRolTests(AccesoPorRolBase):
    """
    POST /api/certificaciones/upload/ todavía no pide sesión: recibe el ID del prestador
    en el formulario. Las pruebas marcadas con expectedFailure describen lo que debería
    pasar con HU-5 y hoy fallan; cuando la vista exija la sesión del prestador dueño,
    pasarán y unittest las marcará como «unexpected success» para quitarles el decorador.
    """

    def setUp(self):
        directorio = TemporaryDirectory(prefix='chambape-t53-')
        self.addCleanup(directorio.cleanup)
        configuracion = self.settings(MEDIA_ROOT=directorio.name)
        configuracion.enable()
        self.addCleanup(configuracion.disable)

    def subir(self, usuario, prestador):
        archivo = SimpleUploadedFile('certificado.pdf', pdf_valido(), content_type='application/pdf')
        return self.sesion(usuario).post(
            reverse('upload_certification'), {'file': archivo, 'prestador': prestador.id},
            format='multipart',
        )

    def test_el_prestador_sube_su_certificacion(self):
        self.assertEqual(self.subir(self.prestador.usuario, self.prestador).status_code, 201)

    @expectedFailure
    def test_cliente_no_puede_subir_certificaciones(self):
        self.assertEqual(self.subir(self.cliente, self.prestador).status_code, 403)

    @expectedFailure
    def test_anonimo_no_puede_subir_certificaciones(self):
        self.assertEqual(self.subir(None, self.prestador).status_code, 403)

    @expectedFailure
    def test_prestador_no_sube_a_nombre_de_otro(self):
        self.assertEqual(self.subir(self.otro_prestador.usuario, self.prestador).status_code, 403)


class RutasPublicasTests(AccesoPorRolBase):
    """Búsqueda, rubros, registro y envío de solicitudes no piden sesión."""

    def test_busqueda_y_rubros_responden_200_a_cualquier_rol(self):
        parametros = {'rubro': self.rubro.id, 'latitud': '0', 'longitud': '0'}
        for nombre, usuario in self.cuentas().items():
            with self.subTest(cuenta=nombre):
                cliente = self.sesion(usuario)
                r = cliente.get(reverse('buscar_prestadores'), parametros)
                self.assertEqual(r.status_code, 200)
                self.assertEqual([p['id'] for p in r.json()], [self.prestador.id])
                self.assertEqual(cliente.get(reverse('rubros')).status_code, 200)

    def test_anonimo_y_cliente_envian_solicitudes(self):
        datos = {
            'prestador': self.prestador.id, 'rubro': self.rubro.id,
            'cliente_nombre': 'Carlos', 'cliente_telefono': '999888777',
            'descripcion': 'Se rompió la tubería.', 'latitud': '0.010000', 'longitud': '0.000000',
        }
        for nombre in ('anonimo', 'cliente'):
            with self.subTest(cuenta=nombre):
                r = self.sesion(self.cuentas()[nombre]).post(
                    reverse('crear_solicitud'), datos, format='json',
                )
                self.assertEqual(r.status_code, 201)
                # El cliente sigue el estado sin sesión (T4.2).
                detalle = APIClient().get(reverse('detalle_solicitud', args=[r.json()['id']]))
                self.assertEqual(detalle.status_code, 200)

    def test_rutas_publicas_ignoran_una_sesion_invalida(self):
        cliente = APIClient()
        cliente.cookies['sessionid'] = 'sesion-que-no-existe'
        r = cliente.get(reverse('buscar_prestadores'), {'rubro': self.rubro.id, 'latitud': '0', 'longitud': '0'})
        self.assertEqual(r.status_code, 200)


class SinCredencialesValidasTests(AccesoPorRolBase):
    """Sin sesión válida, toda ruta privada responde 403 (la API no usa 401)."""

    def rutas_privadas(self):
        solicitud = self.nueva_solicitud()
        return {
            'yo': ('get', reverse('usuario_actual')),
            'bandeja': ('get', reverse('solicitudes_recibidas')),
            'aceptar': ('post', reverse('aceptar_solicitud', args=[solicitud.id])),
            'revisiones': ('get', reverse('revision-documento-list')),
        }

    def verificar_403(self, cliente, **cabeceras):
        for nombre, (metodo, url) in self.rutas_privadas().items():
            with self.subTest(ruta=nombre):
                r = getattr(cliente, metodo)(url, **cabeceras)
                self.assertEqual(r.status_code, 403)
                self.assertNotIn('WWW-Authenticate', r)

    def test_sin_credenciales(self):
        self.verificar_403(APIClient())

    def test_cookie_de_sesion_inventada(self):
        cliente = APIClient()
        cliente.cookies['sessionid'] = 'sesion-que-no-existe'
        self.verificar_403(cliente)

    def test_basic_auth_no_se_acepta_aunque_la_clave_sea_correcta(self):
        credenciales = b64encode(f'{self.admin.username}:{CLAVE}'.encode()).decode()
        self.verificar_403(APIClient(), HTTP_AUTHORIZATION=f'Basic {credenciales}')

    def test_token_inventado(self):
        self.verificar_403(APIClient(), HTTP_AUTHORIZATION='Token 0123456789abcdef')

    def test_sesion_cerrada_ya_no_sirve(self):
        cliente = self.sesion(self.admin)
        self.assertEqual(cliente.get(reverse('revision-documento-list')).status_code, 200)
        self.assertEqual(cliente.post(reverse('logout')).status_code, 204)
        self.verificar_403(cliente)

    def test_cuenta_desactivada_pierde_el_acceso(self):
        cliente = self.sesion(self.admin)
        User.objects.filter(pk=self.admin.pk).update(is_active=False)
        self.assertEqual(cliente.get(reverse('revision-documento-list')).status_code, 403)

    def test_quitar_is_staff_quita_el_acceso_de_administrador(self):
        cliente = self.sesion(self.admin)
        User.objects.filter(pk=self.admin.pk).update(is_staff=False)
        self.assertEqual(cliente.get(reverse('revision-documento-list')).status_code, 403)

    def test_sesion_valida_sin_token_csrf_no_modifica_datos(self):
        cliente = APIClient(enforce_csrf_checks=True)
        cliente.force_login(self.admin)
        url = reverse('revision-documento-detail', args=[self.certificacion.revision.id])
        r = cliente.patch(url, {'estado': 'aprobado'}, format='json')
        self.assertEqual(r.status_code, 403)
        self.assertIn('CSRF', r.json()['detail'])
        # Con el token de la cookie, la misma sesión sí puede.
        cliente.get(reverse('csrf'))
        r = cliente.patch(
            url, {'estado': 'aprobado'}, format='json',
            HTTP_X_CSRFTOKEN=cliente.cookies['csrftoken'].value,
        )
        self.assertEqual(r.status_code, 200)
