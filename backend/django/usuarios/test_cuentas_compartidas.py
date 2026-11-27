"""Una cuenta para la web y la app (login-firebase y vincular). El token de Firebase se simula."""
from datetime import timedelta
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import TestCase, override_settings
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from usuarios.models import Perfil

User = get_user_model()
DATOS = 'usuarios.autenticacion.datos_del_token'


def app(uid):
    cliente = APIClient(enforce_csrf_checks=True)
    cliente.credentials(HTTP_AUTHORIZATION=f'Bearer token-de-{uid}')
    return cliente


@override_settings(FIREBASE_AUTH_PROJECT_ID='chamba-pruebas')
class VincularCuentaTests(TestCase):
    def setUp(self):
        cache.clear()
        self.web = User.objects.create_user('lucia', 'lucia@example.invalid', 'clave-de-la-web-1', first_name='Lucía')
        Perfil.objects.filter(usuario=self.web).update(rol=Perfil.Rol.CLIENTE)

    def vincular(self, clave='clave-de-la-web-1', uid='uid-lucia', email='Lucia@example.invalid'):
        with patch(DATOS, return_value={'sub': uid, 'email': email}):
            return app(uid).post(reverse('vincular_cuenta'), {'password': clave}, format='json')

    def test_con_la_contrasena_de_la_web_vincula_y_la_app_ya_entra(self):
        respuesta = self.vincular()
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta.json()['username'], 'lucia')
        self.assertEqual(Perfil.objects.get(usuario=self.web).firebase_uid, 'uid-lucia')
        # Con el UID vinculado, la app ya usa la API con su token.
        with patch('usuarios.autenticacion.verificar_token', return_value='uid-lucia'):
            self.assertEqual(app('uid-lucia').get(reverse('usuario_actual')).status_code, 200)

    def test_el_correo_solo_no_basta(self):
        respuesta = self.vincular(clave='otra-clave')
        self.assertEqual(respuesta.status_code, 400)
        self.assertIsNone(Perfil.objects.get(usuario=self.web).firebase_uid)

    def test_un_correo_sin_cuenta_da_el_mismo_mensaje(self):
        sin_cuenta = self.vincular(email='nadie@example.invalid')
        clave_mala = self.vincular(clave='otra-clave')
        self.assertEqual(sin_cuenta.status_code, 400)
        self.assertEqual(sin_cuenta.json(), clave_mala.json())

    def test_diez_fallos_por_hora_bloquean_ese_correo(self):
        for _ in range(10):
            self.assertEqual(self.vincular(clave='mala').status_code, 400)
        # Ni la contraseña correcta pasa mientras dure el bloqueo.
        self.assertEqual(self.vincular().status_code, 429)
        self.assertIsNone(Perfil.objects.get(usuario=self.web).firebase_uid)

    def test_no_vincula_dos_veces_ni_una_cuenta_ya_vinculada(self):
        self.assertEqual(self.vincular().status_code, 200)
        self.assertEqual(self.vincular().status_code, 409)  # el mismo UID otra vez
        self.assertEqual(self.vincular(uid='uid-otro').status_code, 409)  # otra cuenta de Firebase
        self.assertEqual(Perfil.objects.get(usuario=self.web).firebase_uid, 'uid-lucia')

    def test_una_cuenta_de_administracion_no_se_vincula(self):
        Perfil.objects.filter(usuario=self.web).update(rol=Perfil.Rol.ADMINISTRADOR)
        self.assertEqual(self.vincular().status_code, 409)

    def test_una_cuenta_sancionada_no_se_vincula(self):
        from administracion.models import SancionCuenta
        admin = User.objects.create_user('admin', 'admin@example.invalid', 'x', is_staff=True)
        SancionCuenta.objects.create(usuario=self.web, tipo='suspension', motivo='Prueba de sanción vigente.',
                                     hasta=timezone.now() + timedelta(days=3), creada_por=admin)
        respuesta = self.vincular()
        self.assertEqual(respuesta.status_code, 403)
        self.assertEqual(respuesta.json()['codigo'], 'cuenta_sancionada')

    def test_sin_token_valido_no_hace_nada(self):
        with patch(DATOS, return_value=None):
            respuesta = app('x').post(reverse('vincular_cuenta'), {'password': 'clave-de-la-web-1'}, format='json')
        self.assertEqual(respuesta.status_code, 403)


@override_settings(FIREBASE_AUTH_PROJECT_ID='chamba-pruebas')
class LoginFirebaseTests(TestCase):
    def setUp(self):
        self.usuario = User.objects.create_user('fb-uid-mica', 'mica@example.invalid', first_name='Micaela')
        Perfil.objects.filter(usuario=self.usuario).update(rol=Perfil.Rol.CLIENTE, firebase_uid='uid-mica')
        self.web = APIClient(enforce_csrf_checks=True)
        self.web.get(reverse('csrf'))
        self.csrf = self.web.cookies['csrftoken'].value

    def entrar(self, uid='uid-mica', con_csrf=True):
        cabeceras = {'HTTP_AUTHORIZATION': f'Bearer token-de-{uid}'}
        if con_csrf:
            cabeceras['HTTP_X_CSRFTOKEN'] = self.csrf
        with patch(DATOS, return_value={'sub': uid, 'email': 'mica@example.invalid'}):
            return self.web.post(reverse('login_firebase'), {}, format='json', **cabeceras)

    def test_la_cuenta_de_la_app_entra_a_la_web_con_sesion(self):
        respuesta = self.entrar()
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta.json()['nombres'], 'Micaela')
        # Ya con sesión de Django (sin token), como cualquier usuario de la web.
        self.web.credentials()
        self.assertEqual(self.web.get(reverse('usuario_actual')).json()['username'], 'fb-uid-mica')

    def test_sin_vincular_o_sin_csrf_no_entra(self):
        respuesta = self.entrar(uid='uid-desconocido')
        self.assertEqual(respuesta.status_code, 403)
        self.assertEqual(respuesta.json()['codigo'], 'cuenta_no_vinculada')
        self.assertEqual(self.entrar(con_csrf=False).status_code, 403)
