"""Pruebas locales con transporte simulado; no acreditan entrega FCM."""
from unittest.mock import patch
from django.contrib.auth import get_user_model
from django.db import transaction
from django.test import Client, TestCase, override_settings
from django.urls import reverse
from .models import Solicitud, TokenFCM
from . import tests as pruebas_solicitud


@override_settings(FCM_ENABLED=True)
class PushTests(TestCase):
    setUp = pruebas_solicitud.SolicitudTests.setUp
    crear_prestador = pruebas_solicitud.SolicitudTests.crear_prestador
    enviar = pruebas_solicitud.SolicitudTests.enviar

    def registrar(self, prestador=None, token='token-de-navegador-123456789'):
        self.client.force_login((prestador or self.prestador).usuario)
        r = self.client.post(reverse('token_fcm'), {'token': token, 'usuario': 999}, content_type='application/json')
        self.assertEqual(r.status_code, 200)
        return token

    def test_registro_sesion_y_baja_solo_propietario(self):
        self.assertEqual(self.client.post(reverse('token_fcm')).status_code, 403)
        self.client.force_login(get_user_model().objects.create_user('cliente_push'))
        self.assertEqual(self.client.post(reverse('token_fcm')).status_code, 403)
        token = self.registrar()
        self.registrar()  # idempotencia
        self.assertEqual(TokenFCM.objects.get().usuario, self.prestador.usuario)
        otro = self.crear_prestador('otro_push')
        self.client.force_login(otro.usuario)
        self.client.delete(reverse('token_fcm'), {'token': token}, content_type='application/json')
        self.assertEqual(TokenFCM.objects.count(), 1)
        self.client.force_login(self.prestador.usuario)
        self.client.delete(reverse('token_fcm'), {'token': token}, content_type='application/json')
        self.assertFalse(TokenFCM.objects.exists())

    def test_csrf_exigido(self):
        cliente = Client(enforce_csrf_checks=True)
        cliente.force_login(self.prestador.usuario)
        for metodo in [cliente.post, cliente.delete]:
            self.assertEqual(metodo(reverse('token_fcm'), {'token': 'x' * 30}, content_type='application/json').status_code, 403)

    @patch('servicios.push.enviar_fcm')
    def test_destinatario_y_contenido_sin_datos_personales(self, enviar):
        token = self.registrar()
        sesion_destinatario = self.client
        self.client = Client()
        self.registrar(self.crear_prestador('vecino_push'), 'otro-token-1234567890123456')
        self.client = sesion_destinatario
        with self.captureOnCommitCallbacks(execute=True):
            r = self.enviar()
        self.assertEqual(r.status_code, 201)
        enviar.assert_called_once_with(token, {
            'solicitud_id': str(r.json()['id']), 'usuario_id': str(self.prestador.usuario_id),
        })
        self.assertNotIn('entregada', r.json())

    @patch('servicios.push.enviar_fcm')
    def test_no_envia_antes_del_commit_ni_si_rollback(self, enviar):
        self.registrar()
        with self.captureOnCommitCallbacks(execute=True):
            with transaction.atomic():
                self.enviar()
                enviar.assert_not_called()
                transaction.set_rollback(True)
        enviar.assert_not_called()
        self.assertFalse(Solicitud.objects.exists())

    @patch('servicios.push.enviar_fcm', side_effect=RuntimeError('fallo simulado'))
    def test_fallo_transporte_no_pierde_solicitud(self, enviar):
        self.registrar()
        with self.captureOnCommitCallbacks(execute=True):
            r = self.enviar()
        self.assertEqual(r.status_code, 201)
        self.assertTrue(Solicitud.objects.filter(pk=r.json()['id']).exists())
        self.assertEqual(TokenFCM.objects.count(), 1)

    @patch('servicios.push.enviar_fcm')
    def test_validaciones_no_notifican(self, enviar):
        self.registrar()
        with self.captureOnCommitCallbacks(execute=True):
            for cambios in [{'cliente_telefono': '123'}, {'latitud': 40}, {'rubro': self.otro_rubro.pk}]:
                self.assertEqual(self.enviar(**cambios).status_code, 400)
            for campo, valor in [('disponible', False), ('estado', 'pendiente')]:
                setattr(self.prestador, campo, valor)
                self.prestador.save()
                self.assertEqual(self.enviar().status_code, 400)
                self.prestador.disponible = True
        enviar.assert_not_called()

    @patch('servicios.push.enviar_fcm')
    def test_logout_revoca_y_sesion_vencida_no_envia(self, enviar):
        self.registrar()
        self.client.post(reverse('logout'))
        self.assertFalse(TokenFCM.objects.exists())
        self.registrar()
        TokenFCM.objects.update(sesion='sesion_inexistente')
        with self.captureOnCommitCallbacks(execute=True):
            self.enviar()
        enviar.assert_not_called()
        self.assertFalse(TokenFCM.objects.exists())

    def test_detalle_privado_y_respuesta(self):
        pk = self.enviar().json()['id']
        ruta = reverse('solicitud_privada', args=[pk])
        self.assertEqual(self.client.get(ruta).status_code, 403)
        otro = self.crear_prestador('otro_detalle')
        self.client.force_login(otro.usuario)
        self.assertEqual(self.client.get(ruta).status_code, 404)
        self.client.force_login(self.prestador.usuario)
        self.assertEqual(self.client.get(ruta).status_code, 200)
        self.assertEqual(self.client.post(reverse('aceptar_solicitud', args=[pk])).status_code, 200)
        self.assertEqual(self.client.post(reverse('rechazar_solicitud', args=[pk])).status_code, 409)
        self.assertEqual(self.client.get(ruta).json()['estado'], 'aceptada')

    @patch('servicios.push.enviar_fcm')
    def test_token_no_registrado_se_elimina(self, enviar):
        from firebase_admin.messaging import UnregisteredError
        enviar.side_effect = UnregisteredError('simulado')
        self.registrar()
        with self.captureOnCommitCallbacks(execute=True):
            self.enviar()
        self.assertFalse(TokenFCM.objects.exists())

    def test_cambio_de_cuenta_revoca_token_anterior(self):
        self.registrar()
        otro = self.crear_prestador('otra_cuenta')
        otro.usuario.set_password('UnaClavePrueba2026!')
        otro.usuario.save()
        r = self.client.post(reverse('login'), {
            'username': otro.usuario.username, 'password': 'UnaClavePrueba2026!',
        }, content_type='application/json')
        self.assertEqual(r.status_code, 200)
        self.assertFalse(TokenFCM.objects.exists())

    @override_settings(FIREBASE_PROJECT_ID='proyecto-prueba')
    @patch('firebase_admin.messaging.send', return_value='id-simulado')
    @patch('firebase_admin.get_app')
    def test_adaptador_sdk_sin_payload_visible_automatico(self, app, enviar):
        from .push import enviar_fcm
        enviar_fcm('token-prueba', {'solicitud_id': '3', 'usuario_id': '4'})
        mensaje = enviar.call_args.args[0]
        self.assertIsNone(mensaje.notification)
        self.assertEqual(mensaje.data, {'solicitud_id': '3', 'usuario_id': '4'})
        self.assertEqual(mensaje.webpush.headers['TTL'], '300')

    @override_settings(FCM_ENABLED=False)
    @patch('servicios.push.enviar_fcm')
    def test_fcm_desactivado_no_envia_y_conserva_solicitud(self, enviar):
        self.registrar()
        with self.captureOnCommitCallbacks(execute=True):
            r = self.enviar()
        self.assertEqual(r.status_code, 201)
        self.assertTrue(Solicitud.objects.filter(pk=r.json()['id']).exists())
        enviar.assert_not_called()

    def test_token_invalido_no_se_registra(self):
        self.client.force_login(self.prestador.usuario)
        for token in ['', 'corto', 'x' * 2049, 'espacio no permitido 123456789']:
            r = self.client.post(reverse('token_fcm'), {'token': token}, content_type='application/json')
            self.assertEqual(r.status_code, 400)
        self.assertFalse(TokenFCM.objects.exists())

    @patch('servicios.push.enviar_fcm')
    def test_sesion_real_expirada_no_envia(self, enviar):
        from datetime import timedelta
        from django.contrib.sessions.models import Session
        from django.utils import timezone
        self.registrar()
        Session.objects.filter(session_key=self.client.session.session_key).update(
            expire_date=timezone.now() - timedelta(seconds=1))
        with self.captureOnCommitCallbacks(execute=True):
            self.enviar()
        enviar.assert_not_called()
        self.assertFalse(TokenFCM.objects.exists())
