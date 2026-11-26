"""HU-13: suspensión y bloqueo de cuentas.

Criterios de aceptación:
- El administrador suspende o bloquea una cuenta indicando el motivo.
- Una cuenta bloqueada no puede iniciar sesión ni enviar o recibir solicitudes.
Las cuentas inician sesión por /api/usuarios/login/ (no force_login), como en la web.
"""
from datetime import timedelta
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.db import IntegrityError, transaction
from django.test import TestCase, override_settings
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from prestadores.models import Prestador, Rubro
from servicios.models import Solicitud, TokenFCM
from usuarios.models import Perfil

from .models import SancionCuenta

User = get_user_model()
CLAVE = 'clave-de-prueba-HU13'
CENTRO = ('-12.046374', '-77.042793')


def cuenta(usuario, rol, **extra):
    user = User.objects.create_user(usuario, f'{usuario}@example.invalid', CLAVE, **extra)
    Perfil.objects.update_or_create(usuario=user, defaults={'rol': rol})
    return user


class BaseSanciones(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.rubro = Rubro.objects.create(nombre='Rubro HU-13')
        cls.admin = cuenta('admin_hu13', Perfil.Rol.ADMINISTRADOR)
        cls.cliente = cuenta('cliente_hu13', Perfil.Rol.CLIENTE, first_name='Carla', last_name='Cliente')
        cls.usuario_prestador = cuenta('prestador_hu13', Perfil.Rol.PRESTADOR)
        cls.prestador = Prestador.objects.create(
            usuario=cls.usuario_prestador, nombres='Pedro', apellidos='Prestador', dni='13131313',
            latitud=CENTRO[0], longitud=CENTRO[1], radio_cobertura_km=5,
            disponible=True, estado=Prestador.Estado.VERIFICADO,
        )
        cls.prestador.rubros.add(cls.rubro)

    def sesion(self, usuario):
        """Cliente HTTP con CSRF que inicia sesión por la API real; devuelve (http, respuesta)."""
        http = APIClient(enforce_csrf_checks=True)
        http.get(reverse('csrf'))
        http.credentials(HTTP_X_CSRFTOKEN=http.cookies['csrftoken'].value)
        respuesta = http.post(reverse('login'), {'username': usuario, 'password': CLAVE}, format='json')
        # Al iniciar sesión Django cambia el token CSRF: se usa el nuevo, como hace la web.
        http.credentials(HTTP_X_CSRFTOKEN=http.cookies['csrftoken'].value)
        return http, respuesta

    def administrador(self):
        http, respuesta = self.sesion('admin_hu13')
        self.assertEqual(respuesta.status_code, 200, respuesta.content)
        return http

    def sancionar(self, usuario, **datos):
        return self.administrador().post(reverse('cuenta_sancionar', args=[usuario.pk]), datos, format='json')

    def solicitud(self, http):
        return http.post(reverse('crear_solicitud'), {
            'prestador': self.prestador.pk, 'rubro': self.rubro.pk, 'cliente_nombre': 'Carla Cliente',
            'cliente_telefono': '900000000', 'descripcion': 'Fuga en el baño.',
            'latitud': CENTRO[0], 'longitud': CENTRO[1],
        }, format='json')


class SancionarTests(BaseSanciones):
    def test_suspender_con_motivo_y_dias(self):
        antes = timezone.now()
        respuesta = self.sancionar(self.cliente, tipo='suspension', dias=7, motivo='Insultos al prestador en el chat.')
        self.assertEqual(respuesta.status_code, 201, respuesta.content)
        cuerpo = respuesta.json()
        self.assertEqual(cuerpo['estado'], 'suspendida')
        self.assertEqual(cuerpo['sancion']['motivo'], 'Insultos al prestador en el chat.')
        self.assertEqual(cuerpo['sancion']['creada_por'], 'admin_hu13')
        sancion = SancionCuenta.objects.get()
        self.assertAlmostEqual(sancion.hasta, antes + timedelta(days=7), delta=timedelta(minutes=1))

    def test_bloquear_sin_fecha_de_fin(self):
        respuesta = self.sancionar(self.cliente, tipo='bloqueo', motivo='Solicitudes falsas reiteradas.')
        self.assertEqual(respuesta.status_code, 201, respuesta.content)
        self.assertEqual(respuesta.json()['estado'], 'bloqueada')
        self.assertIsNone(SancionCuenta.objects.get().hasta)

    def test_validaciones(self):
        casos = [
            ({'tipo': 'suspension', 'motivo': 'Motivo suficientemente largo.'}, 'dias'),
            ({'tipo': 'bloqueo', 'dias': 3, 'motivo': 'Motivo suficientemente largo.'}, 'dias'),
            ({'tipo': 'suspension', 'dias': 0, 'motivo': 'Motivo suficientemente largo.'}, 'dias'),
            ({'tipo': 'suspension', 'dias': 366, 'motivo': 'Motivo suficientemente largo.'}, 'dias'),
            ({'tipo': 'bloqueo', 'motivo': '   corto  '}, 'motivo'),
            ({'tipo': 'bloqueo'}, 'motivo'),
            ({'tipo': 'expulsion', 'motivo': 'Motivo suficientemente largo.'}, 'tipo'),
        ]
        http = self.administrador()
        for datos, campo in casos:
            with self.subTest(datos=datos):
                respuesta = http.post(reverse('cuenta_sancionar', args=[self.cliente.pk]), datos, format='json')
                self.assertEqual(respuesta.status_code, 400)
                self.assertIn(campo, respuesta.json())
        self.assertFalse(SancionCuenta.objects.exists())

    def test_no_se_sanciona_dos_veces_a_la_vez(self):
        self.assertEqual(self.sancionar(self.cliente, tipo='bloqueo', motivo='Primera sanción vigente.').status_code, 201)
        respuesta = self.sancionar(self.cliente, tipo='suspension', dias=2, motivo='Segunda sanción encima.')
        self.assertEqual(respuesta.status_code, 409)
        self.assertEqual(SancionCuenta.objects.count(), 1)

    def test_la_base_impide_dos_sanciones_abiertas(self):
        # Respaldo si dos administradores sancionan a la vez.
        SancionCuenta.objects.create(usuario=self.cliente, tipo='bloqueo', motivo='Primera.')
        with self.assertRaises(IntegrityError), transaction.atomic():
            SancionCuenta.objects.create(usuario=self.cliente, tipo='bloqueo', motivo='Segunda.')

    def test_cuentas_de_administracion_no_se_sancionan(self):
        staff = User.objects.create_user('staff_hu13', password=CLAVE, is_staff=True)
        for objetivo in (self.admin, staff):
            with self.subTest(objetivo=objetivo.username):
                respuesta = self.sancionar(objetivo, tipo='bloqueo', motivo='No debería poder hacerse.')
                self.assertEqual(respuesta.status_code, 400)
        self.assertFalse(SancionCuenta.objects.exists())

    def test_solo_administradores(self):
        url = reverse('cuenta_sancionar', args=[self.cliente.pk])
        datos = {'tipo': 'bloqueo', 'motivo': 'Intento sin permiso.'}
        anonimo = APIClient()
        self.assertEqual(anonimo.post(url, datos, format='json').status_code, 403)
        self.assertEqual(anonimo.get(reverse('cuentas')).status_code, 403)
        for usuario in ('cliente_hu13', 'prestador_hu13'):
            with self.subTest(usuario=usuario):
                http, _ = self.sesion(usuario)
                self.assertEqual(http.post(url, datos, format='json').status_code, 403)
                self.assertEqual(http.get(reverse('cuentas')).status_code, 403)
        self.assertFalse(SancionCuenta.objects.exists())


class EfectosDeLaSancionTests(BaseSanciones):
    def test_cuenta_bloqueada_no_inicia_sesion_y_ve_el_motivo(self):
        self.sancionar(self.cliente, tipo='bloqueo', motivo='Solicitudes falsas reiteradas.')
        _, respuesta = self.sesion('cliente_hu13')
        self.assertEqual(respuesta.status_code, 403)
        self.assertEqual(respuesta.json()['detail'], 'Tu cuenta está bloqueada. Motivo: Solicitudes falsas reiteradas.')

    def test_tambien_por_correo(self):
        self.sancionar(self.cliente, tipo='bloqueo', motivo='Solicitudes falsas reiteradas.')
        _, respuesta = self.sesion('cliente_hu13@example.invalid')
        self.assertEqual(respuesta.status_code, 403)

    def test_suspension_muestra_la_fecha_de_fin_en_hora_de_lima(self):
        self.sancionar(self.cliente, tipo='suspension', dias=3, motivo='Lenguaje ofensivo en la solicitud.')
        _, respuesta = self.sesion('cliente_hu13')
        self.assertEqual(respuesta.status_code, 403)
        self.assertRegex(respuesta.json()['detail'],
                         r'^Tu cuenta está suspendida hasta el \d{2}/\d{2}/\d{4} a las \d{2}:\d{2}\. '
                         r'Motivo: Lenguaje ofensivo en la solicitud\.$')

    def test_con_contrasena_incorrecta_no_revela_la_sancion(self):
        self.sancionar(self.cliente, tipo='bloqueo', motivo='Solicitudes falsas reiteradas.')
        http = APIClient()
        respuesta = http.post(reverse('login'), {'username': 'cliente_hu13', 'password': 'otra'}, format='json')
        self.assertEqual(respuesta.status_code, 400)
        self.assertNotIn('bloqueada', respuesta.content.decode())

    def test_la_sesion_abierta_deja_de_valer(self):
        http, respuesta = self.sesion('cliente_hu13')
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(http.get(reverse('usuario_actual')).status_code, 200)
        self.sancionar(self.cliente, tipo='suspension', dias=1, motivo='Suspensión durante la sesión.')
        self.assertEqual(http.get(reverse('usuario_actual')).status_code, 403)

    def test_cliente_sancionado_no_envia_solicitudes(self):
        http, _ = self.sesion('cliente_hu13')
        self.sancionar(self.cliente, tipo='bloqueo', motivo='Solicitudes falsas reiteradas.')
        respuesta = self.solicitud(http)
        self.assertEqual(respuesta.status_code, 403)
        self.assertIn('bloqueada', respuesta.json()['detail'])
        self.assertFalse(Solicitud.objects.exists())

    def test_prestador_sancionado_no_recibe_solicitudes_ni_sale_en_la_busqueda(self):
        TokenFCM.objects.create(usuario=self.usuario_prestador, token='token-hu13', sesion='s')
        buscar = {'rubro': self.rubro.pk, 'latitud': CENTRO[0], 'longitud': CENTRO[1]}
        anonimo = APIClient()
        self.assertEqual(len(anonimo.get(reverse('buscar_prestadores'), buscar).json()), 1)

        self.sancionar(self.usuario_prestador, tipo='bloqueo', motivo='Cobros fuera de la plataforma.')
        self.prestador.refresh_from_db()
        self.assertFalse(self.prestador.disponible)
        self.assertEqual(anonimo.get(reverse('buscar_prestadores'), buscar).json(), [])
        self.assertFalse(TokenFCM.objects.filter(usuario=self.usuario_prestador).exists())

        respuesta = self.solicitud(anonimo)
        self.assertEqual(respuesta.status_code, 400)
        self.assertEqual(respuesta.json()['prestador'], ['El prestador no está disponible.'])
        _, ingreso = self.sesion('prestador_hu13')
        self.assertEqual(ingreso.status_code, 403)

    def test_aunque_se_reactive_a_mano_la_disponibilidad_no_recibe(self):
        self.sancionar(self.usuario_prestador, tipo='suspension', dias=5, motivo='Cobros fuera de la plataforma.')
        Prestador.objects.filter(pk=self.prestador.pk).update(disponible=True)  # p. ej. desde el admin
        respuesta = self.solicitud(APIClient())
        self.assertEqual(respuesta.status_code, 400)

    @override_settings(FIREBASE_AUTH_PROJECT_ID='proyecto-de-prueba')
    def test_app_movil_con_token_tambien_se_rechaza(self):
        Perfil.objects.filter(usuario=self.usuario_prestador).update(firebase_uid='uid-hu13')
        movil = APIClient()
        movil.credentials(HTTP_AUTHORIZATION='Bearer token-de-prueba')
        url = reverse('disponibilidad')
        with patch('usuarios.autenticacion.verificar_token', return_value='uid-hu13'):
            self.assertEqual(movil.get(url).status_code, 200)
            self.sancionar(self.usuario_prestador, tipo='bloqueo', motivo='Cobros fuera de la plataforma.')
            respuesta = movil.get(url)
        self.assertEqual(respuesta.status_code, 403)
        self.assertIn('bloqueada', respuesta.json()['detail'])

    def test_suspension_vencida_ya_no_impide_entrar(self):
        SancionCuenta.objects.create(usuario=self.cliente, tipo='suspension', motivo='Ya cumplida.',
                                     hasta=timezone.now() - timedelta(minutes=1))
        _, respuesta = self.sesion('cliente_hu13')
        self.assertEqual(respuesta.status_code, 200)
        # Y se puede volver a sancionar: la vencida se cierra en su fecha de fin.
        self.assertEqual(self.sancionar(self.cliente, tipo='bloqueo', motivo='Reincidencia comprobada.').status_code, 201)
        vencida = SancionCuenta.objects.get(motivo='Ya cumplida.')
        self.assertEqual(vencida.levantada, vencida.hasta)


class ReactivarYListarTests(BaseSanciones):
    def test_reactivar_devuelve_el_acceso_y_queda_en_el_historial(self):
        self.sancionar(self.cliente, tipo='bloqueo', motivo='Solicitudes falsas reiteradas.')
        http = self.administrador()
        url = reverse('cuenta_reactivar', args=[self.cliente.pk])
        self.assertEqual(http.post(url, {'motivo': '  '}, format='json').status_code, 400)
        respuesta = http.post(url, {'motivo': 'Apelación aceptada.'}, format='json')
        self.assertEqual(respuesta.status_code, 200, respuesta.content)
        self.assertEqual(respuesta.json()['estado'], 'activa')
        self.assertEqual(http.post(url, {'motivo': 'Otra vez.'}, format='json').status_code, 409)

        _, ingreso = self.sesion('cliente_hu13')
        self.assertEqual(ingreso.status_code, 200)
        detalle = http.get(reverse('cuenta_detalle', args=[self.cliente.pk])).json()
        self.assertEqual(len(detalle['historial']), 1)
        self.assertEqual(detalle['historial'][0]['motivo_levantamiento'], 'Apelación aceptada.')
        self.assertEqual(detalle['historial'][0]['levantada_por'], 'admin_hu13')
        self.assertFalse(detalle['historial'][0]['vigente'])

    def test_al_reactivar_el_prestador_vuelve_a_activarse_el_mismo(self):
        self.sancionar(self.usuario_prestador, tipo='suspension', dias=2, motivo='Cobros fuera de la plataforma.')
        self.administrador().post(reverse('cuenta_reactivar', args=[self.usuario_prestador.pk]),
                                  {'motivo': 'Aclarado con el cliente.'}, format='json')
        self.prestador.refresh_from_db()
        self.assertFalse(self.prestador.disponible)
        http, ingreso = self.sesion('prestador_hu13')
        self.assertEqual(ingreso.status_code, 200)
        self.assertEqual(http.patch(reverse('disponibilidad'), {'disponible': True}, format='json').status_code, 200)

    def test_lista_filtra_por_estado_y_texto_sin_cuentas_de_administracion(self):
        self.sancionar(self.cliente, tipo='suspension', dias=1, motivo='Lenguaje ofensivo en la solicitud.')
        http = self.administrador()
        todas = http.get(reverse('cuentas')).json()
        self.assertEqual([c['usuario'] for c in todas], ['cliente_hu13', 'prestador_hu13'])
        prestador = todas[1]
        self.assertEqual((prestador['nombre'], prestador['rol'], prestador['dni']), ('Pedro Prestador', 'prestador', '13131313'))
        suspendidas = http.get(reverse('cuentas'), {'estado': 'suspendida'}).json()
        self.assertEqual([c['usuario'] for c in suspendidas], ['cliente_hu13'])
        self.assertEqual(http.get(reverse('cuentas'), {'estado': 'activa', 'q': 'pedro'}).json()[0]['usuario'],
                         'prestador_hu13')
        self.assertEqual(http.get(reverse('cuentas'), {'q': '13131313'}).json()[0]['usuario'], 'prestador_hu13')
        self.assertEqual(http.get(reverse('cuentas'), {'estado': 'rara'}).status_code, 400)

    def test_la_lista_no_hace_una_consulta_por_cuenta(self):
        from django.db import connection
        from django.test.utils import CaptureQueriesContext
        http = self.administrador()
        with CaptureQueriesContext(connection) as con_dos:
            self.assertEqual(len(http.get(reverse('cuentas')).json()), 2)
        for i in range(5):
            cuenta(f'extra_{i}', Perfil.Rol.CLIENTE)
        self.sancionar(self.cliente, tipo='bloqueo', motivo='Para que haya una sanción vigente.')
        with CaptureQueriesContext(connection) as con_siete:
            self.assertEqual(len(http.get(reverse('cuentas')).json()), 7)
        self.assertEqual(len(con_dos), len(con_siete))
