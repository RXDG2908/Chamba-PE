"""T21.1: consentimiento real y bloqueo previo de HU-19, con datos ficticios."""
from datetime import timedelta
from tempfile import TemporaryDirectory
from unittest import mock

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.db import IntegrityError, OperationalError, transaction
from django.db.models.query import QuerySet
from django.test import TestCase, override_settings
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from prestadores.models import Prestador, Rubro
from .consentimiento import tiene_consentimiento
from .models import Consentimiento, ControlDatosDni, IntentoDni
from .tests import JPG, LectorFalso, hacer_mrz
from .textos_consentimiento import FINALIDAD_DNI, TEXTO_DNI, VERSION_DNI

User = get_user_model()


@override_settings(PASSWORD_HASHERS=['django.contrib.auth.hashers.MD5PasswordHasher'])
class ConsentimientoTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.rubro = Rubro.objects.create(nombre='Rubro ficticio HU21')
        cls.prestador = cls.crear_prestador('hu21_ficticio_a', '12345678')
        cls.otro = cls.crear_prestador('hu21_ficticio_b', '87654321')
        cls.cliente = User.objects.create_user(username='hu21_cliente', password='SoloPruebas!')
        cls.admin = User.objects.create_user(username='hu21_admin', password='SoloPruebas!', is_staff=True)

    @classmethod
    def crear_prestador(cls, nombre, dni):
        user = User.objects.create_user(username=nombre, password='SoloPruebas!')
        prestador = Prestador.objects.create(usuario=user, nombres='Persona', apellidos='Ficticia',
            dni=dni, latitud=0, longitud=0, radio_cobertura_km=5)
        prestador.rubros.add(cls.rubro)
        return prestador

    def setUp(self):
        temporal = TemporaryDirectory(prefix='hu21-test-')
        self.addCleanup(temporal.cleanup)
        configuracion = override_settings(
            MEDIA_ROOT=temporal.name + '/media', RAIZ_PRIVADA=temporal.name + '/privado',
        )
        configuracion.enable()
        self.addCleanup(configuracion.disable)

    def sesion(self, usuario=None, csrf=True):
        cliente = APIClient(enforce_csrf_checks=True)
        if usuario:
            cliente.force_login(usuario)
            cliente.get(reverse('csrf'))
            if csrf:
                cliente.credentials(HTTP_X_CSRFTOKEN=cliente.cookies['csrftoken'].value)
        return cliente

    def aceptar(self, cliente=None, **datos):
        cliente = cliente or self.sesion(self.prestador.usuario)
        return cliente.post(reverse('consentimiento'),
            {'acepto': True, 'version_texto': VERSION_DNI, **datos}, format='json')

    def registro(self, prestador=None, **campos):
        return Consentimiento.objects.create(
            usuario=(prestador or self.prestador).usuario,
            **{'finalidad': FINALIDAD_DNI, 'version_texto': VERSION_DNI,
               'texto_aceptado': TEXTO_DNI, **campos},
        )

    def enviar_dni(self, cliente):
        return cliente.post(reverse('verificacion_dni'), {
            'anverso': SimpleUploadedFile('frente.jpg', JPG, content_type='image/jpeg'),
            'reverso': SimpleUploadedFile('reverso.jpg', JPG, content_type='image/jpeg'),
        }, format='multipart')

    def test_ausencia_bloquea_por_defecto(self):
        self.assertFalse(tiene_consentimiento(self.prestador))
        self.assertFalse(tiene_consentimiento(None))
        r = self.sesion(self.prestador.usuario).get(reverse('consentimiento'))
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()['estado'], 'ausente')
        self.assertFalse(r.json()['vigente'])

    def test_aceptacion_registra_propietario_texto_version_y_fecha_servidor(self):
        antes = timezone.now()
        r = self.aceptar()
        self.assertEqual(r.status_code, 201, r.content)
        registro = Consentimiento.objects.get()
        self.assertEqual(registro.usuario_id, self.prestador.usuario_id)
        self.assertEqual((registro.finalidad, registro.version_texto, registro.texto_aceptado),
                         (FINALIDAD_DNI, VERSION_DNI, TEXTO_DNI))
        self.assertGreaterEqual(registro.aceptado_en, antes)
        self.assertIsNone(registro.revocado_en)
        self.assertTrue(r.json()['vigente'])
        self.assertTrue(r.json()['provisional'])
        self.assertTrue(tiene_consentimiento(self.prestador))

    def test_aceptacion_repetida_no_duplica_ni_cambia_fecha(self):
        self.assertEqual(self.aceptar().status_code, 201)
        fecha = Consentimiento.objects.get().aceptado_en
        self.assertEqual(self.aceptar().status_code, 200)
        self.assertEqual(Consentimiento.objects.count(), 1)
        self.assertEqual(Consentimiento.objects.get().aceptado_en, fecha)

    def test_requiere_booleano_true_explicito(self):
        for valor in (False, None, 'true', 1):
            with self.subTest(valor=valor):
                self.assertEqual(self.aceptar(acepto=valor).status_code, 400)
        self.assertEqual(Consentimiento.objects.count(), 0)

    def test_faltan_campos_obligatorios(self):
        c = self.sesion(self.prestador.usuario)
        for datos in ({}, {'acepto': True}, {'version_texto': VERSION_DNI}):
            self.assertEqual(c.post(reverse('consentimiento'), datos, format='json').status_code, 400)

    def test_version_anterior_no_se_acepta(self):
        r = self.aceptar(version_texto='demo-anterior')
        self.assertEqual(r.status_code, 400)
        self.assertIn('version_texto', r.json())
        self.assertEqual(Consentimiento.objects.count(), 0)

    def test_registro_antiguo_no_habilita_dni(self):
        self.registro(version_texto='demo-anterior')
        self.assertFalse(tiene_consentimiento(self.prestador))
        c = self.sesion(self.prestador.usuario)
        self.assertEqual(c.get(reverse('consentimiento')).json()['estado'], 'desactualizado')
        self.assertEqual(self.enviar_dni(c).status_code, 403)

    def test_otra_finalidad_no_habilita_dni(self):
        self.registro(finalidad='otra_finalidad')
        self.assertFalse(tiene_consentimiento(self.prestador))

    def test_otro_usuario_no_habilita_dni_propio(self):
        self.registro(self.otro)
        self.assertFalse(tiene_consentimiento(self.prestador))
        self.assertTrue(tiene_consentimiento(self.otro))
        r = self.sesion(self.prestador.usuario).get(
            reverse('consentimiento') + '?usuario=' + str(self.otro.usuario_id))
        self.assertFalse(r.json()['vigente'])

    def test_no_permite_manipular_propietario_texto_finalidad_ni_fechas(self):
        ataques = {
            'usuario': self.otro.usuario_id, 'usuario_id': self.otro.usuario_id,
            'prestador': self.otro.pk, 'texto_aceptado': 'Otro texto',
            'texto': 'Otro texto', 'finalidad': 'facial',
            'aceptado_en': '2000-01-01T00:00:00Z', 'revocado_en': None,
        }
        for campo, valor in ataques.items():
            with self.subTest(campo=campo):
                self.assertEqual(self.aceptar(**{campo: valor}).status_code, 400)
        self.assertEqual(Consentimiento.objects.count(), 0)

    def test_revocacion_bloquea_y_conserva_historial(self):
        self.aceptar()
        c = self.sesion(self.prestador.usuario)
        r = c.post(reverse('revocar_consentimiento'), format='json')
        self.assertEqual(r.status_code, 200)
        self.assertFalse(r.json()['vigente'])
        self.assertEqual(r.json()['estado'], 'revocado')
        self.assertFalse(tiene_consentimiento(self.prestador))
        registro = Consentimiento.objects.get()
        self.assertIsNotNone(registro.revocado_en)
        self.assertEqual(registro.texto_aceptado, TEXTO_DNI)
        self.assertEqual(self.enviar_dni(c).status_code, 403)

    def test_revocar_repetidamente_no_cambia_fecha(self):
        self.aceptar()
        c = self.sesion(self.prestador.usuario)
        c.post(reverse('revocar_consentimiento'))
        fecha = Consentimiento.objects.get().revocado_en
        self.assertEqual(c.post(reverse('revocar_consentimiento')).status_code, 200)
        self.assertEqual(Consentimiento.objects.get().revocado_en, fecha)

    def test_revocar_sin_aceptacion_es_idempotente(self):
        r = self.sesion(self.prestador.usuario).post(reverse('revocar_consentimiento'))
        self.assertEqual(r.status_code, 200)
        self.assertFalse(r.json()['vigente'])
        self.assertEqual(Consentimiento.objects.count(), 0)

    def test_nueva_aceptacion_tras_revocar_crea_otro_registro(self):
        self.aceptar()
        primero = Consentimiento.objects.get().pk
        self.sesion(self.prestador.usuario).post(reverse('revocar_consentimiento'))
        self.assertEqual(self.aceptar().status_code, 201)
        self.assertEqual(Consentimiento.objects.count(), 2)
        self.assertIsNotNone(Consentimiento.objects.get(pk=primero).revocado_en)
        self.assertTrue(tiene_consentimiento(self.prestador))

    def test_revocar_no_permite_indicar_otro_propietario(self):
        self.registro(self.otro)
        c = self.sesion(self.prestador.usuario)
        self.assertEqual(c.post(reverse('revocar_consentimiento'),
            {'usuario': self.otro.usuario_id}, format='json').status_code, 400)
        self.assertTrue(tiene_consentimiento(self.otro))
        c.post(reverse('revocar_consentimiento'))
        self.assertTrue(tiene_consentimiento(self.otro))

    def test_roles_no_autorizados(self):
        for user in (None, self.cliente, self.admin):
            c = self.sesion(user)
            with self.subTest(usuario=user):
                self.assertEqual(c.get(reverse('consentimiento')).status_code, 403)
                self.assertEqual(self.aceptar(c).status_code, 403)
                self.assertEqual(c.post(reverse('revocar_consentimiento')).status_code, 403)

    def test_csrf_obligatorio_en_aceptacion_y_revocacion(self):
        c = self.sesion(self.prestador.usuario, csrf=False)
        self.assertEqual(self.aceptar(c).status_code, 403)
        self.assertEqual(c.post(reverse('revocar_consentimiento')).status_code, 403)
        self.assertEqual(c.get(reverse('consentimiento')).status_code, 200)
        self.assertEqual(Consentimiento.objects.count(), 0)

    def test_sin_consentimiento_no_valida_imagenes_ni_llama_servicios(self):
        with mock.patch('verificacion.views.error_de_imagen') as imagen, \
             mock.patch('verificacion.views.obtener_lector') as lector, \
             mock.patch('verificacion.servicio.obtener_validador') as fuente, \
             mock.patch('verificacion.models.AlmacenPrivado._save') as guardar:
            self.assertEqual(self.enviar_dni(self.sesion(self.prestador.usuario)).status_code, 403)
        for llamada in (imagen, lector, fuente, guardar):
            llamada.assert_not_called()
        self.assertEqual(IntentoDni.objects.count(), 0)

    def test_con_consentimiento_pasa_por_el_flujo_original_de_hu19(self):
        self.aceptar()
        with mock.patch('verificacion.views.obtener_lector', return_value=LectorFalso(hacer_mrz())):
            r = self.enviar_dni(self.sesion(self.prestador.usuario))
        self.assertEqual(r.status_code, 201, r.content)
        self.assertEqual(r.json()['resultado'], 'validado')
        self.prestador.refresh_from_db()
        self.assertEqual(self.prestador.estado, Prestador.Estado.PENDIENTE)

    def test_revocar_no_elimina_datos_dni_t212_no_implementada(self):
        self.aceptar()
        c = self.sesion(self.prestador.usuario)
        with mock.patch('verificacion.views.obtener_lector', return_value=LectorFalso(hacer_mrz())):
            self.enviar_dni(c)
        intento = IntentoDni.objects.get()
        ruta = intento.anverso.path
        c.post(reverse('revocar_consentimiento'))
        from pathlib import Path
        self.assertTrue(Path(ruta).exists())
        self.assertEqual(IntentoDni.objects.get().dni_leido, self.prestador.dni)

    def test_bd_impide_duplicado_activo(self):
        self.registro()
        with self.assertRaises(IntegrityError), transaction.atomic():
            self.registro()

    def test_bd_impide_revocacion_anterior_a_aceptacion(self):
        with self.assertRaises(IntegrityError), transaction.atomic():
            self.registro(revocado_en=timezone.now() - timedelta(days=1))

    def test_get_no_escribe_y_muestra_texto_provisional(self):
        r = self.sesion(self.prestador.usuario).get(reverse('consentimiento'))
        self.assertEqual(r.json()['texto'], TEXTO_DNI)
        self.assertIn('provisional', r.json()['texto'])
        self.assertEqual(Consentimiento.objects.count(), 0)

    def comprobar_ocupado(self, respuesta):
        self.assertEqual(respuesta.status_code, 503)
        self.assertEqual(respuesta['Content-Type'], 'application/json')
        self.assertEqual(respuesta.json(), {
            'detail': 'No pudimos guardar el consentimiento. Inténtalo de nuevo.',
        })
        self.assertNotIn('SQLITE-PRIVADO', respuesta.content.decode())

    def test_reserva_ocupada_al_aceptar_es_recuperable_e_idempotente(self):
        c = self.sesion(self.prestador.usuario)
        with mock.patch('verificacion.consentimiento.bloquear_usuario',
                        side_effect=OperationalError('SQLITE-PRIVADO')):
            self.comprobar_ocupado(self.aceptar(c))
        self.assertFalse(Consentimiento.objects.exists())
        self.assertEqual(self.aceptar(c).status_code, 201)
        aceptacion = Consentimiento.objects.get()
        self.assertEqual(self.aceptar(c).status_code, 200)
        self.assertEqual(Consentimiento.objects.count(), 1)
        self.assertEqual(Consentimiento.objects.get().aceptado_en, aceptacion.aceptado_en)

    def test_reserva_ocupada_al_revocar_conserva_estado_y_permite_reintento(self):
        aceptacion = self.registro()
        control = ControlDatosDni.objects.create(prestador=self.prestador, generacion=4)
        c = self.sesion(self.prestador.usuario)
        url = reverse('revocar_consentimiento')
        with mock.patch('verificacion.consentimiento.bloquear_usuario',
                        side_effect=OperationalError('SQLITE-PRIVADO')):
            self.comprobar_ocupado(c.post(url))
        aceptacion.refresh_from_db()
        control.refresh_from_db()
        self.assertIsNone(aceptacion.revocado_en)
        self.assertEqual(control.generacion, 4)
        self.assertTrue(tiene_consentimiento(self.prestador))
        self.assertEqual(c.post(url).status_code, 200)
        aceptacion.refresh_from_db()
        revocado_en = aceptacion.revocado_en
        self.assertIsNotNone(revocado_en)
        self.assertEqual(c.post(url).status_code, 200)
        aceptacion.refresh_from_db()
        control.refresh_from_db()
        self.assertEqual(aceptacion.revocado_en, revocado_en)
        self.assertEqual(control.generacion, 5)

    def test_fallo_despues_de_crear_aceptacion_revierte_la_escritura(self):
        c = self.sesion(self.prestador.usuario)
        crear = Consentimiento.objects.get_or_create

        def crear_y_fallar(*args, **kwargs):
            crear(*args, **kwargs)
            raise OperationalError('SQLITE-PRIVADO')

        with mock.patch('verificacion.consentimiento.Consentimiento.objects.get_or_create',
                        side_effect=crear_y_fallar):
            self.comprobar_ocupado(self.aceptar(c))
        self.assertFalse(Consentimiento.objects.exists())
        self.assertEqual(self.aceptar(c).status_code, 201)

    def test_fallo_despues_de_revocar_revierte_consentimiento_y_generacion(self):
        aceptacion = self.registro()
        control = ControlDatosDni.objects.create(prestador=self.prestador, generacion=8)
        c = self.sesion(self.prestador.usuario)
        actualizar = QuerySet.update

        def actualizar_y_fallar(queryset, **campos):
            resultado = actualizar(queryset, **campos)
            if queryset.model is ControlDatosDni and 'generacion' in campos:
                raise OperationalError('SQLITE-PRIVADO')
            return resultado

        with mock.patch.object(QuerySet, 'update', actualizar_y_fallar):
            self.comprobar_ocupado(c.post(reverse('revocar_consentimiento')))
        aceptacion.refresh_from_db()
        control.refresh_from_db()
        self.assertIsNone(aceptacion.revocado_en)
        self.assertEqual(control.generacion, 8)
        self.assertEqual(c.post(reverse('revocar_consentimiento')).status_code, 200)
        control.refresh_from_db()
        self.assertEqual(control.generacion, 9)

    def test_permisos_y_csrf_se_comprueban_antes_de_reservar(self):
        for c in [self.sesion(), self.sesion(self.cliente), self.sesion(self.admin),
                  self.sesion(self.prestador.usuario, csrf=False)]:
            with mock.patch('verificacion.consentimiento.bloquear_usuario') as reservar:
                self.assertEqual(self.aceptar(c).status_code, 403)
                self.assertEqual(c.post(reverse('revocar_consentimiento')).status_code, 403)
                reservar.assert_not_called()
