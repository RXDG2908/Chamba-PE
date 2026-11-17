from django.contrib.auth import get_user_model
from django.test import Client, TestCase
from django.urls import reverse
from rest_framework.response import Response
from rest_framework.test import APIRequestFactory, force_authenticate
from rest_framework.views import APIView

from prestadores.models import Prestador, Rubro

from .models import Perfil
from .permisos import EsAdministrador, EsCliente, EsPrestador

User = get_user_model()


def crear_prestador(username='prestador1', dni='12345678'):
    usuario = User.objects.create_user(username=username, password='Clave-segura-123')
    prestador = Prestador.objects.create(
        usuario=usuario, nombres='Juan', apellidos='Pérez', dni=dni,
        latitud='-12.046374', longitud='-77.042793', radio_cobertura_km=5,
    )
    prestador.rubros.set([Rubro.objects.get(nombre='Gasfitería')])
    return usuario


class RegistroClienteTests(TestCase):
    def setUp(self):
        self.url = reverse('registro_cliente')
        self.datos = {
            'username': 'ana.perez', 'email': 'ana@correo.pe', 'password': 'UnaClaveSegura2026',
            'nombres': 'Ana', 'apellidos': 'Pérez',
        }

    def post(self, **cambios):
        return self.client.post(self.url, {**self.datos, **cambios}, content_type='application/json')

    def test_registro_valido_crea_cliente_con_sesion(self):
        r = self.post()
        self.assertEqual(r.status_code, 201)
        self.assertEqual(r.json()['rol'], 'cliente')
        self.assertIsNone(r.json()['prestador_id'])
        self.assertNotIn('password', r.json())
        self.assertEqual(User.objects.get().perfil.rol, Perfil.Rol.CLIENTE)
        # Queda con la sesión iniciada.
        self.assertEqual(self.client.get(reverse('usuario_actual')).status_code, 200)

    def test_correo_invalido(self):
        r = self.post(email='no-es-correo')
        self.assertEqual(r.status_code, 400)
        self.assertIn('email', r.json())

    def test_contrasena_debil(self):
        r = self.post(password='123')
        self.assertEqual(r.status_code, 400)
        self.assertIn('password', r.json())

    def test_campos_obligatorios(self):
        r = self.client.post(self.url, {}, content_type='application/json')
        self.assertEqual(r.status_code, 400)
        for campo in ['username', 'email', 'password', 'nombres', 'apellidos']:
            self.assertIn(campo, r.json())

    def test_correo_repetido_sin_importar_mayusculas(self):
        self.assertEqual(self.post().status_code, 201)
        self.client.logout()
        r = self.post(username='otra', email='ANA@correo.pe')
        self.assertEqual(r.status_code, 400)
        self.assertEqual(r.json()['email'], ['Ya existe una cuenta con ese correo.'])

    def test_usuario_repetido(self):
        self.assertEqual(self.post().status_code, 201)
        self.client.logout()
        r = self.post(username='ANA.PEREZ', email='otra@correo.pe')
        self.assertEqual(r.status_code, 400)
        self.assertIn('username', r.json())

    def test_correo_de_un_prestador_tambien_cuenta(self):
        usuario = crear_prestador()
        usuario.email = 'ana@correo.pe'
        usuario.save()
        self.assertEqual(self.post().status_code, 400)


class LoginTests(TestCase):
    def setUp(self):
        self.usuario = User.objects.create_user(
            username='ana.perez', email='ana@correo.pe', password='UnaClaveSegura2026',
            first_name='Ana', last_name='Pérez',
        )

    def login(self, username, password='UnaClaveSegura2026'):
        return self.client.post(
            reverse('login'), {'username': username, 'password': password},
            content_type='application/json',
        )

    def test_login_con_usuario(self):
        r = self.login('ana.perez')
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()['username'], 'ana.perez')
        self.assertEqual(r.json()['nombres'], 'Ana')

    def test_login_con_correo(self):
        r = self.login('ANA@correo.pe')
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()['id'], self.usuario.id)

    def test_contrasena_incorrecta(self):
        r = self.login('ana.perez', 'otra-clave')
        self.assertEqual(r.status_code, 400)
        self.assertEqual(r.json(), {'detail': 'Usuario o contraseña incorrectos.'})
        self.assertEqual(self.client.get(reverse('usuario_actual')).status_code, 403)

    def test_usuario_inexistente_da_el_mismo_mensaje(self):
        r = self.login('nadie@correo.pe')
        self.assertEqual(r.json(), {'detail': 'Usuario o contraseña incorrectos.'})

    def test_cuenta_desactivada(self):
        self.usuario.is_active = False
        self.usuario.save()
        self.assertEqual(self.login('ana.perez').status_code, 400)

    def test_logout_cierra_la_sesion(self):
        self.login('ana.perez')
        self.assertEqual(self.client.post(reverse('logout')).status_code, 204)
        self.assertEqual(self.client.get(reverse('usuario_actual')).status_code, 403)

    def test_logout_sin_sesion(self):
        self.assertEqual(self.client.post(reverse('logout')).status_code, 204)


class CsrfTests(TestCase):
    """El cliente de pruebas normal no revisa CSRF; aquí sí."""

    def setUp(self):
        self.client = Client(enforce_csrf_checks=True)
        User.objects.create_user(username='ana.perez', password='UnaClaveSegura2026')
        self.datos = {'username': 'ana.perez', 'password': 'UnaClaveSegura2026'}

    def test_login_sin_token_se_rechaza(self):
        r = self.client.post(reverse('login'), self.datos, content_type='application/json')
        self.assertEqual(r.status_code, 403)

    def test_login_con_token_de_la_cookie(self):
        self.client.get(reverse('csrf'))
        token = self.client.cookies['csrftoken'].value
        r = self.client.post(
            reverse('login'), self.datos, content_type='application/json',
            HTTP_X_CSRFTOKEN=token,
        )
        self.assertEqual(r.status_code, 200)


class UsuarioActualTests(TestCase):
    def test_sin_sesion_da_403(self):
        self.assertEqual(self.client.get(reverse('usuario_actual')).status_code, 403)

    def test_prestador_ve_su_id_y_nombre(self):
        usuario = crear_prestador()
        self.client.force_login(usuario)
        datos = self.client.get(reverse('usuario_actual')).json()
        self.assertEqual(datos['rol'], 'prestador')
        self.assertEqual(datos['prestador_id'], usuario.prestador.id)
        self.assertEqual((datos['nombres'], datos['apellidos']), ('Juan', 'Pérez'))

    def test_superusuario_es_administrador(self):
        admin = User.objects.create_superuser('admin', 'admin@correo.pe', 'Clave-segura-123')
        self.assertEqual(admin.perfil.rol, Perfil.Rol.ADMINISTRADOR)
        self.client.force_login(admin)
        self.assertEqual(self.client.get(reverse('usuario_actual')).json()['rol'], 'administrador')


class RegistroPrestadorAsignaRolTests(TestCase):
    def test_registro_de_prestador_queda_con_rol_prestador(self):
        rubro = Rubro.objects.get(nombre='Gasfitería')
        r = self.client.post(reverse('registro_prestador'), {
            'username': 'juan', 'email': 'juan@correo.pe', 'password': 'Clave-segura-123',
            'nombres': 'Juan', 'apellidos': 'Pérez', 'dni': '12345678',
            'rubros': [rubro.id], 'latitud': '-12.046374', 'longitud': '-77.042793',
            'radio_cobertura_km': 5,
        }, content_type='application/json')
        self.assertEqual(r.status_code, 201)
        self.assertEqual(User.objects.get(username='juan').perfil.rol, Perfil.Rol.PRESTADOR)


class VistaDePrueba(APIView):
    def get(self, request):
        return Response({})


class PermisosTests(TestCase):
    """Cada clase de permiso deja pasar solo a su rol."""

    def setUp(self):
        self.factory = APIRequestFactory()
        self.cliente = User.objects.create_user(username='cliente')
        self.prestador = crear_prestador()
        self.admin = User.objects.create_superuser('admin', 'admin@correo.pe', 'Clave-segura-123')
        # Cuenta de staff sin perfil, como las creadas antes de la app usuarios.
        staff = User.objects.create_user(username='staff', is_staff=True)
        staff.perfil.delete()
        self.staff_sin_perfil = User.objects.get(pk=staff.pk)

    def estado(self, permiso, usuario):
        vista = VistaDePrueba.as_view(permission_classes=[permiso])
        peticion = self.factory.get('/')
        if usuario:
            force_authenticate(peticion, user=usuario)
        return vista(peticion).status_code

    def test_es_cliente(self):
        self.assertEqual(self.estado(EsCliente, self.cliente), 200)
        self.assertEqual(self.estado(EsCliente, self.prestador), 403)
        self.assertEqual(self.estado(EsCliente, self.admin), 403)
        self.assertEqual(self.estado(EsCliente, None), 403)

    def test_es_prestador(self):
        self.assertEqual(self.estado(EsPrestador, self.prestador), 200)
        self.assertEqual(self.estado(EsPrestador, self.cliente), 403)
        self.assertEqual(self.estado(EsPrestador, self.admin), 403)
        self.assertEqual(self.estado(EsPrestador, None), 403)

    def test_es_administrador(self):
        self.assertEqual(self.estado(EsAdministrador, self.admin), 200)
        self.assertEqual(self.estado(EsAdministrador, self.staff_sin_perfil), 200)
        self.assertEqual(self.estado(EsAdministrador, self.cliente), 403)
        self.assertEqual(self.estado(EsAdministrador, self.prestador), 403)
        self.assertEqual(self.estado(EsAdministrador, None), 403)


class DisponibilidadTests(TestCase):
    """T6.1: el prestador activa o desactiva su disponibilidad."""

    def setUp(self):
        self.url = reverse('disponibilidad')
        self.usuario = crear_prestador()
        self.prestador = self.usuario.prestador
        # Verificado para que la búsqueda dependa solo de la disponibilidad.
        self.prestador.estado = Prestador.Estado.VERIFICADO
        self.prestador.save()
        self.client.force_login(self.usuario)

    def cambiar(self, valor):
        return self.client.patch(self.url, {'disponible': valor}, content_type='application/json')

    def buscar(self):
        rubro = self.prestador.rubros.get()
        return self.client.get(reverse('buscar_prestadores'), {
            'rubro': rubro.id, 'latitud': '-12.046374', 'longitud': '-77.042793',
        }).json()

    def test_consultar_disponibilidad(self):
        r = self.client.get(self.url)
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json(), {'disponible': False, 'estado': 'verificado'})

    def test_activar_y_desactivar(self):
        r = self.cambiar(True)
        self.assertEqual(r.status_code, 200)
        self.assertTrue(r.json()['disponible'])
        self.prestador.refresh_from_db()
        self.assertTrue(self.prestador.disponible)

        self.assertFalse(self.cambiar(False).json()['disponible'])
        self.prestador.refresh_from_db()
        self.assertFalse(self.prestador.disponible)

    def test_inactivo_no_aparece_en_la_busqueda(self):
        self.cambiar(True)
        self.assertEqual([p['id'] for p in self.buscar()], [self.prestador.id])
        self.cambiar(False)
        self.assertEqual(self.buscar(), [])

    def test_valor_obligatorio_y_booleano(self):
        r = self.client.patch(self.url, {}, content_type='application/json')
        self.assertEqual(r.status_code, 400)
        self.assertIn('disponible', r.json())
        self.assertEqual(self.cambiar('quizas').status_code, 400)

    def test_solo_el_prestador_puede_cambiarla(self):
        self.client.logout()
        self.assertEqual(self.cambiar(True).status_code, 403)
        self.client.force_login(User.objects.create_user(username='cliente'))
        self.assertEqual(self.cambiar(True).status_code, 403)
        self.prestador.refresh_from_db()
        self.assertFalse(self.prestador.disponible)

    def test_exige_csrf(self):
        cliente = Client(enforce_csrf_checks=True)
        cliente.force_login(self.usuario)
        r = cliente.patch(self.url, {'disponible': True}, content_type='application/json')
        self.assertEqual(r.status_code, 403)
