"""Registro de prestador desde la app móvil (token de Firebase)."""
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import TestCase, override_settings
from rest_framework.test import APIClient

from usuarios.models import Perfil

from .models import Prestador, Rubro

URL = '/api/prestadores/registro-movil/'
DATOS_TOKEN = 'usuarios.autenticacion.datos_del_token'


@override_settings(FIREBASE_AUTH_PROJECT_ID='proyecto-de-prueba')
class RegistroPrestadorMovilTests(TestCase):
    def setUp(self):
        self.rubro = Rubro.objects.first() or Rubro.objects.create(nombre='Gasfitería')
        self.http = APIClient(enforce_csrf_checks=True)
        self.http.credentials(HTTP_AUTHORIZATION='Bearer token-de-prueba')
        self.datos = {
            'nombres': 'Ana', 'apellidos': 'Pérez', 'dni': '12345678', 'telefono': '999888777',
            'rubros': [self.rubro.id], 'latitud': '-12.046374', 'longitud': '-77.042793',
            'radio_cobertura_km': 5,
        }
        self.token = {'sub': 'uid-nuevo', 'email': 'ana@example.invalid'}

    def registrar(self, datos=None, token='usar-el-de-setup'):
        token = self.token if token == 'usar-el-de-setup' else token
        with patch(DATOS_TOKEN, return_value=token):
            return self.http.post(URL, datos or self.datos, format='json')

    def test_crea_prestador_pendiente_vinculado_al_uid(self):
        respuesta = self.registrar()
        self.assertEqual(respuesta.status_code, 201, respuesta.content)
        self.assertEqual(respuesta.json()['estado'], 'pendiente')

        prestador = Prestador.objects.get(dni='12345678')
        self.assertFalse(prestador.disponible)
        self.assertEqual(list(prestador.rubros.all()), [self.rubro])
        usuario = prestador.usuario
        self.assertEqual(usuario.email, 'ana@example.invalid')
        self.assertFalse(usuario.has_usable_password())
        self.assertEqual(usuario.perfil.rol, Perfil.Rol.PRESTADOR)
        self.assertEqual(usuario.perfil.firebase_uid, 'uid-nuevo')

    def test_despues_de_registrarse_el_token_da_acceso_a_su_disponibilidad(self):
        self.registrar()
        with patch('usuarios.autenticacion.verificar_token', return_value='uid-nuevo'):
            respuesta = self.http.get('/api/usuarios/disponibilidad/')
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta.json(), {'disponible': False, 'estado': 'pendiente'})

    def test_sin_token_o_con_token_invalido_no_crea_nada(self):
        self.assertEqual(self.registrar(token=None).status_code, 403)
        self.http.credentials()
        with patch(DATOS_TOKEN) as datos_del_token:
            self.assertEqual(self.http.post(URL, self.datos, format='json').status_code, 403)
        datos_del_token.assert_not_called()
        self.assertFalse(Prestador.objects.exists())

    def test_una_cuenta_ya_registrada_no_se_registra_dos_veces(self):
        self.assertEqual(self.registrar().status_code, 201)
        respuesta = self.registrar({**self.datos, 'dni': '87654321'})
        self.assertEqual(respuesta.status_code, 409)
        self.assertEqual(Prestador.objects.count(), 1)

    def test_no_se_apropia_de_una_cuenta_web_con_el_mismo_correo(self):
        get_user_model().objects.create_user('ana_web', 'ana@example.invalid', 'clave-segura-123')
        respuesta = self.registrar()
        self.assertEqual(respuesta.status_code, 400)
        self.assertIn('email', respuesta.json())
        self.assertFalse(Prestador.objects.exists())
        self.assertFalse(Perfil.objects.filter(firebase_uid='uid-nuevo').exists())

    def test_valida_los_datos_como_el_registro_web(self):
        respuesta = self.registrar({**self.datos, 'dni': '123', 'rubros': [], 'latitud': '95'})
        self.assertEqual(respuesta.status_code, 400)
        self.assertEqual(set(respuesta.json()), {'dni', 'rubros', 'latitud'})
        # Un dato inválido no deja una cuenta a medias.
        self.assertFalse(get_user_model().objects.filter(username='fb-uid-nuevo').exists())

    def test_dni_repetido_se_rechaza(self):
        self.registrar()
        respuesta = self.registrar(token={'sub': 'uid-otro', 'email': 'otro@example.invalid'})
        self.assertEqual(respuesta.status_code, 400)
        self.assertEqual(respuesta.json(), {'dni': ['Ya existe un prestador con ese DNI.']})

    def test_no_permite_fijar_estado_ni_disponibilidad(self):
        self.registrar({**self.datos, 'estado': 'verificado', 'disponible': True})
        prestador = Prestador.objects.get()
        self.assertEqual(prestador.estado, Prestador.Estado.PENDIENTE)
        self.assertFalse(prestador.disponible)
