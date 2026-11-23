"""T21.2: archivos sintéticos y almacenamiento privado temporal; nunca datos reales."""
import json
import uuid
from datetime import date
from io import StringIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import mock

from django.contrib.auth import get_user_model
from django.core.files.base import ContentFile
from django.core.files.uploadedfile import SimpleUploadedFile
from django.core.management import call_command
from django.db import DatabaseError, IntegrityError, OperationalError, connection, transaction
from django.test import TestCase, override_settings
from django.test.utils import CaptureQueriesContext
from django.urls import reverse
from rest_framework.test import APIClient

from prestadores.models import Prestador
from .consentimiento import aceptar_consentimiento, revocar_consentimiento
from .eliminacion import (
    CapturaBloqueada, ESTADO, iniciar_captura, persistir_captura,
    procesar_eliminacion, reintentar_eliminacion, solicitar_eliminacion,
    _inventariar, _borrar_y_verificar,
)
from .models import AlmacenPrivado, Consentimiento, IntentoDni, SolicitudEliminacionDni
from .textos_consentimiento import TEXTO_DNI_V1, VERSION_DNI
from .tests import JPG
from .vistas_eliminacion import solicitud_json


@override_settings(PASSWORD_HASHERS=['django.contrib.auth.hashers.MD5PasswordHasher'])
class EliminacionTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        user = get_user_model()
        cls.a = Prestador.objects.create(usuario=user.objects.create_user(username='borrado_ficticio_a'),
            dni='12345678', nombres='Persona', apellidos='Ficticia', latitud=0, longitud=0, radio_cobertura_km=5)
        cls.b = Prestador.objects.create(usuario=user.objects.create_user(username='borrado_ficticio_b'),
            dni='87654321', nombres='Otra', apellidos='Ficticia', latitud=0, longitud=0, radio_cobertura_km=5)
        cls.cliente = user.objects.create_user(username='borrado_cliente')
        cls.admin = user.objects.create_user(username='borrado_admin', is_staff=True)
        aceptar_consentimiento(cls.a.usuario)
        aceptar_consentimiento(cls.b.usuario)

    def setUp(self):
        self.temp = TemporaryDirectory(prefix='hu21-eliminacion-')
        self.addCleanup(self.temp.cleanup)
        cfg = override_settings(RAIZ_PRIVADA=self.temp.name, MEDIA_ROOT=self.temp.name + '/media')
        cfg.enable()
        self.addCleanup(cfg.disable)

    def sesion(self, usuario=None, csrf=True):
        c = APIClient(enforce_csrf_checks=True)
        if usuario:
            c.force_login(usuario)
            c.get(reverse('csrf'))
            if csrf:
                c.credentials(HTTP_X_CSRFTOKEN=c.cookies['csrftoken'].value)
        return c

    def intento(self, prestador=None):
        i = IntentoDni(prestador=prestador or self.a, resultado='validado', motivos=[],
            dni_leido='12345678', nombres_leidos='FICTICIO', apellidos_leidos='PRUEBA',
            fecha_nacimiento=date(2000, 1, 1), fecha_vencimiento=date(2030, 1, 1),
            mrz_valida=True, fuente='simulado', estado_fuente='coincide')
        for campo, nombre in [('anverso', 'anverso.jpg'), ('reverso', 'reverso.jpg'), ('foto_titular', 'titular.jpg')]:
            getattr(i, campo).save(nombre, ContentFile(b'archivo sintetico'), save=False)
        i.save()
        return i

    def pedir(self, prestador=None, clave=None):
        return solicitar_eliminacion(prestador or self.a, clave or uuid.uuid4())[0]

    def test_completa_varios_intentos_conserva_cuenta_dni_y_otra_persona(self):
        intentos = [self.intento(), self.intento()]
        ajeno = self.intento(self.b)
        rutas = [getattr(i, c).path for i in intentos for c in ('anverso', 'reverso', 'foto_titular')]
        original = IntentoDni.eliminar_datos_biometricos
        with mock.patch.object(IntentoDni, 'eliminar_datos_biometricos', autospec=True, side_effect=original) as borrar:
            s = procesar_eliminacion(self.pedir().pk)
        self.assertEqual(borrar.call_count, 2)
        self.assertEqual(s.estado, ESTADO.ATENDIDA)
        self.assertIsNotNone(s.atendida_en)
        self.assertFalse(any(Path(r).exists() for r in rutas))
        for i in intentos:
            i.refresh_from_db()
            self.assertEqual((i.dni_leido, i.nombres_leidos, i.apellidos_leidos, i.fuente), ('', '', '', ''))
            self.assertIsNone(i.fecha_nacimiento)
            self.assertIsNone(i.fecha_vencimiento)
            self.assertFalse(i.mrz_valida)
            self.assertEqual(i.resultado, 'validado')
        self.a.refresh_from_db()
        self.assertEqual(self.a.dni, '12345678')
        self.assertTrue(get_user_model().objects.filter(pk=self.a.usuario_id).exists())
        self.assertTrue(Path(ajeno.anverso.path).exists())
        self.assertEqual(list(s.eventos.values_list('estado', flat=True)), ['pendiente', 'procesando', 'atendida'])

    def test_parcial_reintento_conserva_referencias_e_historial(self):
        i = self.intento()
        nombre = i.reverso.name
        original = AlmacenPrivado.delete
        def borrar(storage, key):
            if key == nombre:
                raise PermissionError('DETALLE-PRIVADO-NO-AUDITAR')
            return original(storage, key)
        s = self.pedir()
        with mock.patch.object(AlmacenPrivado, 'delete', borrar):
            s = procesar_eliminacion(s.pk)
        self.assertEqual(s.estado, ESTADO.PARCIAL)
        self.assertIsNone(s.atendida_en)
        self.assertTrue(s.archivos.filter(nombre_privado=nombre, verificado=False).exists())
        i.refresh_from_db()
        self.assertEqual(i.reverso.name, nombre)
        self.assertEqual(reintentar_eliminacion(self.a, s.pk).pk, s.pk)
        s = procesar_eliminacion(s.pk)
        self.assertEqual(s.estado, ESTADO.ATENDIDA)
        self.assertEqual(s.ejecuciones, 2)
        self.assertEqual(s.eventos.count(), 6)
        self.assertFalse(AlmacenPrivado().exists(nombre))
        self.assertNotIn('DETALLE-PRIVADO', json.dumps(solicitud_json(s), default=str))

    def test_borrado_silencioso_no_es_atendido_y_se_recupera(self):
        i = self.intento()
        rutas = [i.anverso.name, i.reverso.name, i.foto_titular.name]
        s = self.pedir()
        with mock.patch.object(AlmacenPrivado, 'delete', return_value=None):
            s = procesar_eliminacion(s.pk)
        self.assertEqual(s.estado, ESTADO.PARCIAL)
        self.assertTrue(all(AlmacenPrivado().exists(n) for n in rutas))
        self.assertEqual(s.archivos.count(), 3)
        reintentar_eliminacion(self.a, s.pk)
        self.assertEqual(procesar_eliminacion(s.pk).estado, ESTADO.ATENDIDA)
        self.assertFalse(any(AlmacenPrivado().exists(n) for n in rutas))

    def test_fallo_inventario_preserva_todo_y_reintento_recupera(self):
        i = self.intento()
        s = self.pedir()
        with mock.patch.object(AlmacenPrivado, 'listdir', side_effect=PermissionError('PRIVADO')):
            s = procesar_eliminacion(s.pk)
        self.assertEqual(s.estado, ESTADO.FALLIDA)
        self.assertEqual(s.codigo_error, 'inventario')
        self.assertTrue(Path(i.anverso.path).exists())
        reintentar_eliminacion(self.a, s.pk)
        self.assertEqual(procesar_eliminacion(s.pk).estado, ESTADO.ATENDIDA)

    def test_fallo_exists_no_acredita_borrado(self):
        self.intento()
        s = self.pedir()
        with mock.patch.object(AlmacenPrivado, 'exists', side_effect=OSError('PRIVADO')):
            s = procesar_eliminacion(s.pk)
        self.assertIn(s.estado, [ESTADO.PARCIAL, ESTADO.FALLIDA])
        self.assertIsNone(s.atendida_en)
        reintentar_eliminacion(self.a, s.pk)
        self.assertEqual(procesar_eliminacion(s.pk).estado, ESTADO.ATENDIDA)

    def test_fallo_bd_tras_borrar_conserva_inventario_y_recupera(self):
        self.intento()
        s = self.pedir()
        with mock.patch.object(IntentoDni, 'save', side_effect=DatabaseError('PRIVADO')):
            s = procesar_eliminacion(s.pk)
        self.assertEqual(s.estado, ESTADO.PARCIAL)
        self.assertEqual(s.codigo_error, 'persistencia')
        self.assertEqual(s.archivos.count(), 3)
        reintentar_eliminacion(self.a, s.pk)
        self.assertEqual(procesar_eliminacion(s.pk).estado, ESTADO.ATENDIDA)

    def test_recupera_procesador_interrumpido_sin_perder_historial(self):
        self.intento()
        s = self.pedir()
        with mock.patch('verificacion.eliminacion._borrar_y_verificar', side_effect=KeyboardInterrupt):
            with self.assertRaises(KeyboardInterrupt):
                procesar_eliminacion(s.pk)
        s.refresh_from_db()
        token = s.token_ejecucion
        self.assertEqual(s.estado, ESTADO.PROCESANDO)
        self.assertEqual(procesar_eliminacion(s.pk).token_ejecucion, token)
        s = procesar_eliminacion(s.pk, recuperar=True)
        self.assertEqual(s.estado, ESTADO.ATENDIDA)
        self.assertEqual(s.ejecuciones, 2)
        self.assertNotEqual(s.token_ejecucion, token)
        self.assertIn('recuperacion', s.eventos.values_list('codigo', flat=True))

    def test_idempotencia_repetida_y_nueva_solicitud_tras_atender(self):
        c = self.sesion(self.a.usuario)
        payload = {'clave_idempotencia': str(uuid.uuid4()), 'confirmo': True}
        url = reverse('eliminaciones_dni')
        primera = c.post(url, payload, format='json')
        segunda = c.post(url, payload, format='json')
        self.assertEqual((primera.status_code, segunda.status_code), (202, 200))
        self.assertEqual(primera.json()['id'], segunda.json()['id'])
        distinta = c.post(url, {**payload, 'clave_idempotencia': str(uuid.uuid4())}, format='json')
        self.assertEqual(distinta.status_code, 409)
        self.assertEqual(SolicitudEliminacionDni.objects.count(), 1)
        procesar_eliminacion(primera.json()['id'])
        self.assertEqual(c.post(url, payload, format='json').json()['estado'], 'atendida')
        tercera = c.post(url, {**payload, 'clave_idempotencia': str(uuid.uuid4())}, format='json')
        self.assertEqual(tercera.status_code, 202)

    def test_reintento_repetido_no_duplica_ejecuciones(self):
        s = self.pedir()
        s.estado = ESTADO.FALLIDA
        s.save()
        reintentar_eliminacion(self.a, s.pk)
        reintentar_eliminacion(self.a, s.pk)
        self.assertEqual(s.eventos.filter(codigo='reintento').count(), 1)
        s = procesar_eliminacion(s.pk)
        self.assertEqual(s.ejecuciones, 1)
        self.assertEqual(procesar_eliminacion(s.pk).ejecuciones, 1)

    def test_constraints_impiden_doble_abierta_y_estado_invalido(self):
        s = self.pedir()
        with self.assertRaises(IntegrityError), transaction.atomic():
            SolicitudEliminacionDni.objects.create(prestador=self.a, clave_idempotencia=uuid.uuid4(), alcance=[], version_alcance='v1')
        with self.assertRaises(IntegrityError), transaction.atomic():
            SolicitudEliminacionDni.objects.filter(pk=s.pk).update(estado='inventado')
        with self.assertRaises(IntegrityError), transaction.atomic():
            SolicitudEliminacionDni.objects.filter(pk=s.pk).update(estado='atendida')

    def test_no_exige_consentimiento_para_pedir_borrado(self):
        revocar_consentimiento(self.a.usuario)
        self.assertEqual(self.pedir().estado, ESTADO.PENDIENTE)

    def test_no_admite_propietario_alcance_estado_ni_confirmacion_implicita(self):
        c = self.sesion(self.a.usuario)
        base = {'clave_idempotencia': str(uuid.uuid4()), 'confirmo': True}
        for cambios in [{'prestador': self.b.pk}, {'estado': 'atendida'}, {'alcance': []}, {'confirmo': 'true'}, {'clave_idempotencia': 'invalida'}]:
            with self.subTest(cambios=cambios):
                self.assertEqual(c.post(reverse('eliminaciones_dni'), {**base, **cambios}, format='json').status_code, 400)
        self.assertEqual(SolicitudEliminacionDni.objects.count(), 0)

    def test_roles_sesion_y_csrf_sin_efectos(self):
        url = reverse('eliminaciones_dni')
        body = {'clave_idempotencia': str(uuid.uuid4()), 'confirmo': True}
        for usuario in [None, self.cliente, self.admin]:
            c = self.sesion(usuario)
            self.assertEqual(c.get(url).status_code, 403)
            self.assertEqual(c.post(url, body, format='json').status_code, 403)
        self.assertEqual(self.sesion(self.a.usuario, csrf=False).post(url, body, format='json').status_code, 403)
        self.assertEqual(SolicitudEliminacionDni.objects.count(), 0)

    def test_aislamiento_historial_y_reintento_csrf(self):
        s = self.pedir()
        detalle = reverse('eliminacion_dni', args=[s.pk])
        retry = reverse('reintentar_eliminacion_dni', args=[s.pk])
        ajeno = self.sesion(self.b.usuario)
        self.assertEqual(ajeno.get(detalle).status_code, 404)
        self.assertEqual(ajeno.post(retry).status_code, 404)
        self.assertEqual(ajeno.get(reverse('eliminaciones_dni')).json()['solicitudes'], [])
        self.assertEqual(self.sesion(self.a.usuario, csrf=False).get(detalle).status_code, 200)
        self.assertEqual(self.sesion(self.a.usuario, csrf=False).post(retry).status_code, 403)

    def test_auditoria_no_contiene_imagenes_datos_leidos_rutas_ni_excepciones(self):
        i = self.intento()
        nombres = [i.anverso.name, i.reverso.name, i.foto_titular.name]
        s = self.pedir()
        with mock.patch.object(AlmacenPrivado, 'delete', side_effect=PermissionError('DATOS-SENSIBLES-EXCEPCION')):
            s = procesar_eliminacion(s.pk)
        texto = json.dumps(solicitud_json(s), default=str)
        for dato in ['12345678', 'FICTICIO', 'PRUEBA', self.temp.name, 'DATOS-SENSIBLES', *nombres]:
            self.assertNotIn(dato, texto)
        self.assertTrue(s.eventos.exists())

    def test_bloquea_captura_en_todos_los_estados_sin_resolver_antes_de_ocr(self):
        s = self.pedir()
        c = self.sesion(self.a.usuario)
        for estado in ['pendiente', 'procesando', 'parcial', 'fallida']:
            s.estado = estado
            s.save()
            with mock.patch('verificacion.views.error_de_imagen') as imagen, mock.patch('verificacion.views.obtener_lector') as lector:
                self.assertEqual(c.post(reverse('verificacion_dni'), {}, format='multipart').status_code, 409)
                imagen.assert_not_called()
                lector.assert_not_called()

    def test_generacion_invalida_captura_tardia_aun_despues_de_atendida(self):
        generacion = iniciar_captura(self.a)
        s = procesar_eliminacion(self.pedir().pk)
        self.assertEqual(s.estado, ESTADO.ATENDIDA)
        guardar = mock.Mock()
        with self.assertRaises(CapturaBloqueada):
            persistir_captura(self.a, generacion, guardar)
        guardar.assert_not_called()

    def test_reaceptar_no_desactiva_barrera_y_revocar_invalida_generacion(self):
        generacion = iniciar_captura(self.a)
        revocar_consentimiento(self.a.usuario)
        aceptar_consentimiento(self.a.usuario)
        with self.assertRaises(CapturaBloqueada):
            persistir_captura(self.a, generacion, mock.Mock())
        self.pedir()
        aceptar_consentimiento(self.a.usuario)
        with self.assertRaises(CapturaBloqueada):
            iniciar_captura(self.a)

    def test_eliminacion_durante_ocr_impide_escritura_tardia_en_endpoint(self):
        def analizar(*args):
            procesar_eliminacion(self.pedir().pk)
            return None, None, [], {'nombre': '', 'estado': 'no_disponible'}
        c = self.sesion(self.a.usuario)
        with mock.patch('verificacion.views.servicio.analizar', side_effect=analizar):
            r = c.post(reverse('verificacion_dni'), {
                'anverso': SimpleUploadedFile('ficticio.jpg', JPG, content_type='image/jpeg'),
                'reverso': SimpleUploadedFile('ficticio.jpg', JPG, content_type='image/jpeg'),
            }, format='multipart')
        self.assertEqual(r.status_code, 409)
        self.assertFalse(IntentoDni.objects.exists())
        self.assertFalse(any(Path(self.temp.name).rglob('*.jpg')))

    def test_journal_recupera_archivos_de_captura_cuyo_save_fallo(self):
        gen = iniciar_captura(self.a)
        nombre = f'dni/hu21/{self.a.pk}/{uuid.uuid4().hex}/anverso.jpg'
        def guardar():
            AlmacenPrivado().save(nombre, ContentFile(b'ficticio'))
            raise DatabaseError('simulado')
        with self.assertRaises(DatabaseError):
            persistir_captura(self.a, gen, guardar)
        self.assertTrue(AlmacenPrivado().exists(nombre))
        s = procesar_eliminacion(self.pedir().pk)
        self.assertEqual(s.estado, ESTADO.ATENDIDA)
        self.assertFalse(AlmacenPrivado().exists(nombre))
        self.assertEqual(s.archivos.count(), 1)

    def test_referencia_compartida_o_fuera_de_raiz_no_borra_datos_ajenos(self):
        ajeno = self.intento(self.b)
        propio = self.intento()
        propio.anverso.name = ajeno.anverso.name
        propio.save()
        s = procesar_eliminacion(self.pedir().pk)
        self.assertEqual(s.estado, ESTADO.FALLIDA)
        self.assertTrue(Path(ajeno.anverso.path).exists())

    def test_comando_atiende_pendiente_y_no_reprocesa_atendida(self):
        self.intento()
        s = self.pedir()
        salida = StringIO()
        call_command('procesar_eliminaciones_dni', solicitud=str(s.pk), stdout=salida)
        s.refresh_from_db()
        self.assertEqual(s.estado, ESTADO.ATENDIDA)
        self.assertIn('atendida', salida.getvalue())
        call_command('procesar_eliminaciones_dni', stdout=salida)
        s.refresh_from_db()
        self.assertEqual(s.ejecuciones, 1)

    def test_api_base_ocupada_devuelve_error_recuperable_sin_filtrar_detalles(self):
        c = self.sesion(self.a.usuario)
        with mock.patch('verificacion.vistas_eliminacion.solicitar_eliminacion', side_effect=OperationalError('DETALLE-PRIVADO')):
            r = c.post(reverse('eliminaciones_dni'), {'clave_idempotencia': str(uuid.uuid4()), 'confirmo': True}, format='json')
        self.assertEqual(r.status_code, 503)
        self.assertNotIn('DETALLE-PRIVADO', str(r.json()))
        self.assertFalse(SolicitudEliminacionDni.objects.exists())
        s = self.pedir()
        with mock.patch('verificacion.vistas_eliminacion.reintentar_eliminacion', side_effect=OperationalError('DETALLE-PRIVADO')):
            r = c.post(reverse('reintentar_eliminacion_dni', args=[s.pk]))
        self.assertEqual(r.status_code, 503)
        s.refresh_from_db()
        self.assertEqual(s.estado, ESTADO.PENDIENTE)
        self.assertEqual(s.eventos.count(), 1)

    def test_version_v1_se_conserva_sin_aceptacion_automatica_de_v2(self):
        revocar_consentimiento(self.a.usuario)
        antigua = Consentimiento.objects.create(usuario=self.a.usuario, finalidad='verificacion_dni',
            version_texto='demo-dni-v1', texto_aceptado=TEXTO_DNI_V1)
        fecha = antigua.aceptado_en
        c = self.sesion(self.a.usuario)
        estado = c.get(reverse('consentimiento')).json()
        self.assertFalse(estado['vigente'])
        self.assertEqual(estado['estado'], 'desactualizado')
        self.assertEqual(estado['version_texto'], VERSION_DNI)
        self.assertEqual(c.post(reverse('consentimiento'), {'acepto': True, 'version_texto': 'demo-dni-v1'}, format='json').status_code, 400)
        self.assertEqual(c.post(reverse('consentimiento'), {'acepto': True, 'version_texto': VERSION_DNI}, format='json').status_code, 201)
        antigua.refresh_from_db()
        self.assertEqual(antigua.texto_aceptado, TEXTO_DNI_V1)
        self.assertEqual(antigua.aceptado_en, fecha)
        self.assertIsNone(antigua.revocado_en)

    def test_fases_reservan_por_escritura_antes_de_cualquier_lectura(self):
        s = self.pedir()
        s.estado, s.token_ejecucion = ESTADO.PROCESANDO, uuid.uuid4()
        s.save()
        # Sin select_related: el acceso implícito a solicitud.prestador recrearía la carrera.
        s = SolicitudEliminacionDni.objects.get(pk=s.pk)
        for fase in [_inventariar, _borrar_y_verificar]:
            with CaptureQueriesContext(connection) as consultas:
                fase(s, s.token_ejecucion, self.a)
            decisiones = [q['sql'] for q in consultas if q['sql'].lstrip().upper().startswith(('SELECT', 'UPDATE', 'INSERT', 'DELETE'))]
            self.assertTrue(decisiones[0].lstrip().upper().startswith('UPDATE '), decisiones)
            self.assertIn(get_user_model()._meta.db_table, decisiones[0])

    def test_journal_no_borra_referencia_de_otro_propietario(self):
        nombre = f'dni/hu21/{self.a.pk}/{uuid.uuid4().hex}/anverso.jpg'
        AlmacenPrivado().save(nombre, ContentFile(b'ficticio'))
        ajeno = IntentoDni.objects.create(prestador=self.b, resultado='revision_manual', anverso=nombre)
        s = procesar_eliminacion(self.pedir().pk)
        self.assertEqual(s.estado, ESTADO.FALLIDA)
        self.assertTrue(AlmacenPrivado().exists(nombre))
        ajeno.refresh_from_db()
        self.assertEqual(ajeno.anverso.name, nombre)
