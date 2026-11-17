"""Acceso de la app móvil con token de Firebase (Perfil.firebase_uid)."""
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import TestCase, override_settings
from rest_framework.test import APIClient

from prestadores.models import Prestador, Rubro

from .models import Perfil

URL = '/api/usuarios/disponibilidad/'
VERIFICAR = 'usuarios.autenticacion.verificar_token'


@override_settings(FIREBASE_AUTH_PROJECT_ID='proyecto-de-prueba')
class AutenticacionFirebaseTests(TestCase):
    def setUp(self):
        User = get_user_model()
        self.usuario = User.objects.create_user('prestador_movil', 'movil@example.invalid', 'clave-segura-123')
        self.prestador = Prestador.objects.create(
            usuario=self.usuario, nombres='Ana', apellidos='Pérez', dni='12345678',
            latitud='-12.046374', longitud='-77.042793', radio_cobertura_km=5,
            disponible=False, estado=Prestador.Estado.VERIFICADO,
        )
        self.prestador.rubros.add(Rubro.objects.first() or Rubro.objects.create(nombre='Gasfitería'))
        Perfil.objects.update_or_create(
            usuario=self.usuario, defaults={'rol': Perfil.Rol.PRESTADOR, 'firebase_uid': 'uid-ana'},
        )
        # enforce_csrf_checks: el token no depende de cookies, así que no necesita CSRF.
        self.cliente_http = APIClient(enforce_csrf_checks=True)

    def con_token(self):
        self.cliente_http.credentials(HTTP_AUTHORIZATION='Bearer token-de-prueba')

    def test_token_de_cuenta_vinculada_consulta_y_cambia_disponibilidad(self):
        self.con_token()
        with patch(VERIFICAR, return_value='uid-ana') as verificar:
            respuesta = self.cliente_http.get(URL)
            self.assertEqual(respuesta.status_code, 200)
            self.assertEqual(respuesta.json(), {'disponible': False, 'estado': 'verificado'})

            respuesta = self.cliente_http.patch(URL, {'disponible': True}, format='json')
            self.assertEqual(respuesta.status_code, 200)
            verificar.assert_called_with('token-de-prueba')
        self.prestador.refresh_from_db()
        self.assertTrue(self.prestador.disponible)

    def test_token_invalido_se_rechaza(self):
        self.con_token()
        with patch(VERIFICAR, return_value=None):
            self.assertEqual(self.cliente_http.get(URL).status_code, 403)

    def test_cuenta_de_firebase_sin_vincular_se_rechaza(self):
        self.con_token()
        with patch(VERIFICAR, return_value='uid-de-otra-persona'):
            respuesta = self.cliente_http.patch(URL, {'disponible': True}, format='json')
        self.assertEqual(respuesta.status_code, 403)
        self.prestador.refresh_from_db()
        self.assertFalse(self.prestador.disponible)

    def test_cuenta_desactivada_se_rechaza(self):
        self.usuario.is_active = False
        self.usuario.save()
        self.con_token()
        with patch(VERIFICAR, return_value='uid-ana'):
            self.assertEqual(self.cliente_http.get(URL).status_code, 403)

    def test_sin_token_sigue_exigiendo_sesion(self):
        with patch(VERIFICAR) as verificar:
            self.assertEqual(self.cliente_http.get(URL).status_code, 403)
        verificar.assert_not_called()

    def test_el_rol_se_sigue_comprobando(self):
        Perfil.objects.filter(usuario=self.usuario).update(rol=Perfil.Rol.CLIENTE)
        self.con_token()
        with patch(VERIFICAR, return_value='uid-ana'):
            self.assertEqual(self.cliente_http.get(URL).status_code, 403)

    @override_settings(FIREBASE_AUTH_PROJECT_ID='')
    def test_sin_proyecto_configurado_no_se_acepta_ningun_token(self):
        self.con_token()
        with patch(VERIFICAR, return_value='uid-ana') as verificar:
            self.assertEqual(self.cliente_http.get(URL).status_code, 403)
        verificar.assert_not_called()

    def test_un_uid_no_puede_vincularse_a_dos_cuentas(self):
        from django.db import IntegrityError, transaction

        otro = get_user_model().objects.create_user('otra_cuenta', 'otra@example.invalid', 'clave-segura-123')
        with self.assertRaises(IntegrityError), transaction.atomic():
            Perfil.objects.update_or_create(usuario=otro, defaults={'firebase_uid': 'uid-ana'})
