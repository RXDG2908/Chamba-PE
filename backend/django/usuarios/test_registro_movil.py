"""App móvil con la API de Django: cuenta de cliente vinculada a Firebase y flujo completo de una solicitud.

El token de Firebase se simula (las pruebas no salen a internet): `datos_del_token` y
`verificar_token` devuelven el UID como lo haría Google con un token válido.
"""
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import TestCase, override_settings
from django.urls import reverse
from rest_framework.test import APIClient

from prestadores.models import Prestador, Rubro
from usuarios.models import Perfil

User = get_user_model()
DATOS = 'usuarios.autenticacion.datos_del_token'
VERIFICAR = 'usuarios.autenticacion.verificar_token'
LIMA = {'latitud': '-12.046374', 'longitud': '-77.042793'}


def app(uid):
    """Cliente HTTP que se identifica como la app: token Bearer y sin sesión ni CSRF."""
    cliente = APIClient(enforce_csrf_checks=True)
    cliente.credentials(HTTP_AUTHORIZATION=f'Bearer token-de-{uid}')
    return cliente


@override_settings(FIREBASE_AUTH_PROJECT_ID='chamba-pruebas')
class RegistroClienteMovilTests(TestCase):
    def registrar(self, uid='uid-carla', email='carla@example.invalid', nombre='Carla'):
        with patch(DATOS, return_value={'sub': uid, 'email': email}):
            return app(uid).post(reverse('registro_cliente_movil'), {'nombre': nombre}, format='json')

    def test_crea_un_cliente_vinculado_al_uid(self):
        respuesta = self.registrar()
        self.assertEqual(respuesta.status_code, 201)
        self.assertEqual(respuesta.json()['rol'], 'cliente')
        perfil = Perfil.objects.get(firebase_uid='uid-carla')
        self.assertEqual((perfil.rol, perfil.usuario.email, perfil.usuario.first_name),
                         ('cliente', 'carla@example.invalid', 'Carla'))
        self.assertFalse(perfil.usuario.has_usable_password())

    def test_es_idempotente(self):
        self.registrar()
        segunda = self.registrar()
        self.assertEqual(segunda.status_code, 200)
        self.assertEqual(Perfil.objects.filter(firebase_uid='uid-carla').count(), 1)

    def test_no_convierte_a_un_prestador_en_cliente(self):
        usuario = User.objects.create_user('pres')
        Perfil.objects.filter(usuario=usuario).update(rol=Perfil.Rol.PRESTADOR, firebase_uid='uid-pres')
        self.assertEqual(self.registrar(uid='uid-pres').status_code, 409)
        self.assertEqual(Perfil.objects.get(usuario=usuario).rol, Perfil.Rol.PRESTADOR)

    def test_no_vincula_una_cuenta_existente_por_correo(self):
        User.objects.create_user('web', email='carla@example.invalid')
        respuesta = self.registrar()
        self.assertEqual(respuesta.status_code, 400)
        self.assertIn('email', respuesta.json())
        self.assertFalse(Perfil.objects.filter(firebase_uid='uid-carla').exists())

    def test_token_invalido(self):
        with patch(DATOS, return_value=None):
            respuesta = app('x').post(reverse('registro_cliente_movil'), {}, format='json')
        self.assertEqual(respuesta.status_code, 403)
        self.assertEqual(APIClient().post(reverse('registro_cliente_movil')).status_code, 403)


@override_settings(FIREBASE_AUTH_PROJECT_ID='chamba-pruebas')
class FlujoAppConDjangoTests(TestCase):
    """Lo que hace la app: pedir, responder, avanzar, seguir y calificar, todo por la API."""

    def setUp(self):
        self.rubro = Rubro.objects.create(nombre='Gasfitería app')
        usuario = User.objects.create_user('rosa_app')
        Perfil.objects.filter(usuario=usuario).update(rol=Perfil.Rol.PRESTADOR, firebase_uid='uid-rosa')
        self.prestador = Prestador.objects.create(
            usuario=usuario, nombres='Rosa', apellidos='Quispe', dni='70000001', radio_cobertura_km=5,
            disponible=True, estado=Prestador.Estado.VERIFICADO, **LIMA,
        )
        self.prestador.rubros.set([self.rubro])
        with patch(DATOS, return_value={'sub': 'uid-carla', 'email': 'carla@example.invalid'}):
            app('uid-carla').post(reverse('registro_cliente_movil'), {'nombre': 'Carla'}, format='json')

    def como(self, uid, metodo, url, datos=None):
        with patch(VERIFICAR, return_value=uid):
            return getattr(app(uid), metodo)(url, datos, format='json')

    def test_flujo_completo(self):
        creada = self.como('uid-carla', 'post', reverse('crear_solicitud'), {
            'prestador': self.prestador.id, 'rubro': self.rubro.id, 'cliente_nombre': 'Carla Huamán',
            'cliente_telefono': '987654321', 'descripcion': 'Fuga en la cocina.', **LIMA,
        })
        self.assertEqual(creada.status_code, 201, creada.content)
        solicitud = creada.json()['id']

        # El prestador la ve en su bandeja (la misma de la web) y la acepta y la avanza.
        recibidas = self.como('uid-rosa', 'get', reverse('solicitudes_recibidas')).json()
        self.assertEqual([s['id'] for s in recibidas], [solicitud])
        self.assertEqual(self.como('uid-rosa', 'post', reverse('aceptar_solicitud', args=[solicitud])).status_code, 200)
        for estado in ('en_camino', 'en_servicio', 'finalizada'):
            r = self.como('uid-rosa', 'post', reverse('avanzar_solicitud', args=[solicitud]), {'estado': estado})
            self.assertEqual(r.status_code, 200, r.content)

        # El cliente la sigue en «Mis servicios» con su historial y la califica.
        servicios = self.como('uid-carla', 'get', reverse('mis_servicios')).json()
        self.assertEqual(servicios[0]['estado'], 'finalizada')
        self.assertEqual([h['estado'] for h in servicios[0]['historial']], ['aceptada', 'en_camino', 'en_servicio', 'finalizada'])
        self.assertTrue(servicios[0]['puede_calificar'])
        calificada = self.como('uid-carla', 'post', reverse('calificar'), {'solicitud': solicitud, 'estrellas': 5, 'comentario': 'Puntual.'})
        self.assertEqual(calificada.status_code, 201, calificada.content)
        self.prestador.refresh_from_db()
        self.assertEqual((float(self.prestador.calificacion_promedio), self.prestador.calificaciones_total), (5.0, 1))

    def test_sin_token_la_solicitud_queda_sin_cliente(self):
        r = APIClient().post(reverse('crear_solicitud'), {
            'prestador': self.prestador.id, 'rubro': self.rubro.id, 'cliente_nombre': 'Anónimo',
            'cliente_telefono': '987654321', 'descripcion': 'x', **LIMA,
        }, format='json')
        self.assertEqual(r.status_code, 201)
        self.assertEqual(self.como('uid-carla', 'get', reverse('mis_servicios')).json(), [])
