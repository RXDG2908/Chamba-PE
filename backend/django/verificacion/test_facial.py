"""HU-20: selfie en vivo, comparación con la foto del DNI, reglas y revisión del administrador."""
import io
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import mock

from django.conf import settings
from django.contrib.auth import get_user_model
from django.core.files.base import ContentFile
from django.core.files.uploadedfile import SimpleUploadedFile
from django.core.management import CommandError, call_command
from django.test import SimpleTestCase, TestCase, override_settings
from django.urls import reverse
from PIL import Image
from rest_framework.test import APIClient

from prestadores.models import Prestador, Rubro

from . import facial
from .models import Consentimiento, IntentoDni, IntentoFacial, SolicitudEliminacionDni
from .tests import JPG
from .textos_consentimiento import FINALIDAD_DNI, FINALIDAD_FACIAL, TEXTO_FACIAL, VERSION_FACIAL

User = get_user_model()


class ComparadorFalso(facial.ComparadorFacial):
    nombre = 'falso'

    def __init__(self, similitud=0.8, motivos=()):
        self.similitud, self.motivos, self.llamadas = similitud, list(motivos), []

    def comparar(self, foto_dni, selfie):
        self.llamadas.append((foto_dni, selfie))
        return facial.Comparacion(self.similitud, list(self.motivos))


@override_settings(PASSWORD_HASHERS=['django.contrib.auth.hashers.MD5PasswordHasher'],
                   UMBRAL_FACIAL_VERIFICADO=0.45, UMBRAL_FACIAL_RECHAZO=0.25)
class IntentoFacialTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.rubro = Rubro.objects.create(nombre='Rubro ficticio HU20')
        cls.prestador = cls.crear_prestador('hu20_ficticio_a', '12345678')
        cls.otro = cls.crear_prestador('hu20_ficticio_b', '87654321')
        cls.admin = User.objects.create_user(username='hu20_admin', is_staff=True)
        cls.cliente = User.objects.create_user(username='hu20_cliente')

    @classmethod
    def crear_prestador(cls, nombre, dni):
        usuario = User.objects.create_user(username=nombre)
        prestador = Prestador.objects.create(usuario=usuario, nombres='Persona', apellidos='Ficticia',
                                             dni=dni, latitud=0, longitud=0, radio_cobertura_km=5)
        prestador.rubros.add(cls.rubro)
        return prestador

    def setUp(self):
        temporal = TemporaryDirectory(prefix='hu20-test-')
        self.addCleanup(temporal.cleanup)
        self.raiz = Path(temporal.name) / 'privado'
        configuracion = override_settings(RAIZ_PRIVADA=str(self.raiz), MEDIA_ROOT=temporal.name + '/media')
        configuracion.enable()
        self.addCleanup(configuracion.disable)
        self.comparador = ComparadorFalso()
        parche = mock.patch.object(facial, 'obtener_comparador', side_effect=lambda: self.comparador)
        parche.start()
        self.addCleanup(parche.stop)

    def sesion(self, usuario):
        cliente = APIClient(enforce_csrf_checks=True)
        cliente.force_login(usuario)
        cliente.get(reverse('csrf'))
        cliente.credentials(HTTP_X_CSRFTOKEN=cliente.cookies['csrftoken'].value)
        return cliente

    def consentir(self, prestador=None):
        Consentimiento.objects.create(usuario=(prestador or self.prestador).usuario, finalidad=FINALIDAD_FACIAL,
                                      version_texto=VERSION_FACIAL, texto_aceptado=TEXTO_FACIAL)

    def dni(self, prestador=None, resultado=IntentoDni.Resultado.VALIDADO, foto=b'foto-dni'):
        intento = IntentoDni(prestador=prestador or self.prestador, resultado=resultado)
        if foto:
            intento.foto_titular.save('hu21/x/titular.jpg', ContentFile(foto), save=False)
        intento.save()
        return intento

    def enviar(self, cliente=None, parpadeo='true', selfie=JPG):
        datos = {'parpadeo': parpadeo}
        if selfie is not None:
            datos['selfie'] = SimpleUploadedFile('selfie.jpg', selfie, content_type='image/jpeg')
        return (cliente or self.sesion(self.prestador.usuario)).post(reverse('verificacion_facial'), datos)

    def preparado(self, **dni):
        self.consentir()
        return self.dni(**dni)

    # --- Consentimiento separado (HU-21) ---

    def test_consentimiento_facial_aparte_del_dni(self):
        cliente = self.sesion(self.prestador.usuario)
        estado = cliente.get(reverse('consentimiento_facial')).json()
        self.assertEqual((estado['finalidad'], estado['estado'], estado['version_texto']),
                         (FINALIDAD_FACIAL, 'ausente', VERSION_FACIAL))
        self.assertNotIn('eliminacion_pendiente', estado)
        self.assertEqual(cliente.post(reverse('consentimiento_facial'),
                                      {'acepto': True, 'version_texto': 'demo-dni-v3'}, format='json').status_code, 400)
        r = cliente.post(reverse('consentimiento_facial'), {'acepto': True, 'version_texto': VERSION_FACIAL},
                         format='json')
        self.assertEqual((r.status_code, r.json()['estado']), (201, 'vigente'))
        # Aceptar lo facial no acepta el DNI.
        self.assertEqual(cliente.get(reverse('consentimiento')).json()['estado'], 'ausente')
        self.assertFalse(Consentimiento.objects.filter(finalidad=FINALIDAD_DNI).exists())
        self.assertEqual(cliente.post(reverse('revocar_consentimiento_facial')).json()['estado'], 'revocado')

    def test_sin_consentimiento_facial_no_se_compara(self):
        self.dni()
        Consentimiento.objects.create(usuario=self.prestador.usuario, finalidad=FINALIDAD_DNI,
                                      version_texto='demo-dni-v3', texto_aceptado='x')
        r = self.enviar()
        self.assertEqual(r.status_code, 403)
        self.assertEqual(self.comparador.llamadas, [])
        self.assertFalse(IntentoFacial.objects.exists())

    # --- Prueba de vida y foto del DNI ---

    def test_sin_parpadeo_o_sin_selfie_valida_es_400(self):
        self.preparado()
        r = self.enviar(parpadeo='false')
        self.assertEqual(list(r.json()), ['parpadeo'])
        r = self.enviar(selfie=None, parpadeo='')
        self.assertEqual(sorted(r.json()), ['parpadeo', 'selfie'])
        self.assertIn('JPG o PNG', self.enviar(selfie=b'%PDF-1.4 no es una foto').json()['selfie'][0])
        self.assertEqual(self.comparador.llamadas, [])

    def test_sin_foto_del_titular_pide_verificar_el_dni(self):
        self.consentir()
        self.assertEqual(self.enviar().status_code, 409)
        self.dni(foto=None)
        r = self.enviar()
        self.assertEqual(r.status_code, 409)
        self.assertIn('verifica tu DNI', r.json()['detail'])

    # --- Reglas de decisión (T20.3) ---

    def test_reglas_de_decision(self):
        self.preparado()
        casos = [
            (0.80, [], 'verificado', []),
            (0.45, [], 'verificado', []),
            (0.44, [], 'revision_manual', ['zona_gris']),
            (0.25, [], 'revision_manual', ['zona_gris']),
            (0.24, [], 'rechazado', ['similitud_baja']),
            (None, ['comparador_no_disponible'], 'revision_manual', ['comparador_no_disponible']),
            (None, ['sin_rostro_dni'], 'revision_manual', ['sin_rostro_dni']),
            (None, ['sin_rostro_selfie'], 'rechazado', ['sin_rostro_selfie']),
        ]
        for similitud, motivos, decision, esperados in casos:
            with self.subTest(similitud=similitud, motivos=motivos):
                IntentoFacial.objects.all().delete()
                self.comparador = ComparadorFalso(similitud, motivos)
                r = self.enviar()
                self.assertEqual(r.status_code, 201)
                self.assertEqual((r.json()['decision'], r.json()['motivos']), (decision, esperados))

    def test_guarda_porcentaje_decision_fecha_y_selfie_privada(self):
        intento_dni = self.preparado()
        self.comparador = ComparadorFalso(0.6234)
        datos = self.enviar().json()
        self.assertEqual((datos['similitud'], datos['porcentaje'], datos['parpadeo']), (0.6234, 62.3, True))
        self.assertTrue(datos['creado'])
        intento = IntentoFacial.objects.get()
        self.assertEqual((intento.intento_dni, intento.comparador), (intento_dni, 'falso'))
        # Se comparó la foto del titular del DNI con la selfie enviada.
        self.assertEqual(self.comparador.llamadas, [(b'foto-dni', JPG)])
        ruta = Path(intento.selfie.path)
        self.assertTrue(ruta.is_file())
        self.assertTrue(ruta.is_relative_to(self.raiz))
        self.assertIn(f'facial/{self.prestador.pk}/', intento.selfie.name)

    def test_con_dni_en_revision_no_queda_verificado(self):
        self.preparado(resultado=IntentoDni.Resultado.REVISION_MANUAL)
        datos = self.enviar().json()
        self.assertEqual((datos['decision'], datos['motivos']), ('revision_manual', ['dni_en_revision']))

    def test_la_decision_no_cambia_el_estado_del_prestador(self):
        self.preparado()
        self.enviar()
        self.prestador.refresh_from_db()
        self.assertEqual(self.prestador.estado, Prestador.Estado.PENDIENTE)

    def test_maximo_cinco_intentos_por_dia(self):
        self.preparado()
        for _ in range(5):
            self.assertEqual(self.enviar().status_code, 201)
        self.assertEqual(self.enviar().status_code, 429)

    def test_eliminacion_del_dni_pendiente_bloquea(self):
        self.preparado()
        SolicitudEliminacionDni.objects.create(prestador=self.prestador, clave_idempotencia='00000000-0000-4000-8000-000000000020',
                                               version_alcance='verificacion_dni-v1', alcance=[])
        self.assertEqual(self.enviar().status_code, 409)

    def test_revocar_durante_la_comparacion_no_guarda(self):
        self.preparado()
        cliente = self.sesion(self.prestador.usuario)

        class RevocaAlComparar(ComparadorFalso):
            def comparar(inner, foto_dni, selfie):
                cliente.post(reverse('revocar_consentimiento_facial'))
                return super().comparar(foto_dni, selfie)

        self.comparador = RevocaAlComparar()
        self.assertEqual(self.enviar(cliente).status_code, 409)
        self.assertFalse(IntentoFacial.objects.exists())
        self.assertEqual(list(self.raiz.rglob('selfie.*')), [])

    # --- Permisos y panel del administrador ---

    def test_permisos(self):
        self.preparado()
        self.enviar()
        intento = IntentoFacial.objects.get()
        for usuario in (self.cliente, self.admin):
            self.assertEqual(self.enviar(self.sesion(usuario)).status_code, 403)
        prestador = self.sesion(self.prestador.usuario)
        self.assertEqual(prestador.get(reverse('verificacion_facial') + '?decision=revision_manual').status_code, 403)
        self.assertEqual(prestador.get(reverse('verificacion_facial_foto', args=[intento.id, 'selfie'])).status_code, 403)
        self.assertEqual(prestador.patch(reverse('revision_facial', args=[intento.id]), {'revision': 'aprobado'},
                                         format='json').status_code, 403)
        self.assertEqual(self.sesion(self.otro.usuario).get(reverse('verificacion_facial')).status_code, 404)
        self.assertEqual(APIClient().get(reverse('verificacion_facial')).status_code, 403)

    def test_zona_gris_el_administrador_ve_las_dos_fotos_y_decide(self):
        self.preparado()
        self.comparador = ComparadorFalso(0.35)
        self.enviar()
        intento = IntentoFacial.objects.get()
        admin = self.sesion(self.admin)
        cola = admin.get(reverse('verificacion_facial') + '?decision=revision_manual').json()
        self.assertEqual([(i['id'], i['porcentaje'], i['prestador']['dni']) for i in cola], [(intento.id, 35.0, '12345678')])
        self.assertTrue(cola[0]['tiene_selfie'] and cola[0]['tiene_foto_dni'])
        for tipo, contenido in (('selfie', JPG), ('dni', b'foto-dni')):
            foto = admin.get(reverse('verificacion_facial_foto', args=[intento.id, tipo]))
            self.assertEqual((foto.status_code, b''.join(foto.streaming_content)), (200, contenido))
            self.assertEqual(foto['Cache-Control'], 'private, no-store')
        self.assertEqual(admin.get(reverse('verificacion_facial_foto', args=[intento.id, 'otra'])).status_code, 404)
        self.assertEqual(admin.patch(reverse('revision_facial', args=[intento.id]), {'revision': 'quizas'},
                                     format='json').status_code, 400)
        r = admin.patch(reverse('revision_facial', args=[intento.id]), {'revision': 'aprobado'}, format='json')
        self.assertEqual((r.status_code, r.json()['revision']), (200, 'aprobado'))
        intento.refresh_from_db()
        self.assertEqual(intento.revisado_por, self.admin)
        self.assertEqual(admin.patch(reverse('revision_facial', args=[intento.id]), {'revision': 'rechazado'},
                                     format='json').status_code, 409)
        # Ya revisado: sale de la cola.
        self.assertEqual(admin.get(reverse('verificacion_facial') + '?decision=revision_manual').json(), [])
        self.assertEqual(admin.get(reverse('verificacion_facial') + '?decision=otra').status_code, 400)

    # --- Borrado de selfies (HU-21) ---

    def test_borrar_selfies_conserva_porcentaje_y_decision(self):
        self.preparado()
        self.comparador = ComparadorFalso(0.35)
        self.enviar()
        self.enviar()
        rutas = [Path(i.selfie.path) for i in IntentoFacial.objects.all()]
        cliente = self.sesion(self.prestador.usuario)
        self.assertEqual(cliente.post(reverse('eliminar_selfies'), {}, format='json').status_code, 400)
        r = cliente.post(reverse('eliminar_selfies'), {'confirmo': True}, format='json')
        self.assertEqual(r.json(), {'borradas': 2, 'pendientes': 0})
        self.assertFalse(any(ruta.exists() for ruta in rutas))
        self.assertEqual(set(IntentoFacial.objects.values_list('selfie', 'similitud', 'decision')),
                         {('', 0.35, 'revision_manual')})
        # Sin selfie no hay nada que mostrar al administrador.
        self.assertEqual(self.sesion(self.admin).get(reverse('verificacion_facial') + '?decision=revision_manual').json(), [])
        self.assertEqual(cliente.post(reverse('eliminar_selfies'), {'confirmo': True}, format='json').json(),
                         {'borradas': 0, 'pendientes': 0})

    def test_borrar_mientras_se_compara_no_deja_una_selfie_nueva(self):
        self.preparado()
        cliente = self.sesion(self.prestador.usuario)

        class BorraAlComparar(ComparadorFalso):
            def comparar(inner, foto_dni, selfie):
                cliente.post(reverse('eliminar_selfies'), {'confirmo': True}, format='json')
                return super().comparar(foto_dni, selfie)

        self.comparador = BorraAlComparar()
        self.assertEqual(self.enviar(cliente).status_code, 409)
        self.assertEqual(list(self.raiz.rglob('selfie.*')), [])

    def test_si_hu21_borra_la_foto_del_dni_el_administrador_no_la_ve(self):
        intento_dni = self.preparado()
        self.comparador = ComparadorFalso(0.35)
        self.enviar()
        intento = IntentoFacial.objects.get()
        intento_dni.eliminar_datos_biometricos()
        admin = self.sesion(self.admin)
        self.assertFalse(admin.get(reverse('verificacion_facial') + '?decision=revision_manual').json()[0]['tiene_foto_dni'])
        self.assertEqual(admin.get(reverse('verificacion_facial_foto', args=[intento.id, 'dni'])).status_code, 404)


def jpg(color, tamano=(200, 200)):
    salida = io.BytesIO()
    Image.new('RGB', tamano, color).save(salida, 'JPEG')
    return salida.getvalue()


class ComparadorOpenCVTests(SimpleTestCase):
    """Con los modelos reales (si se descargaron): sin rostro nunca se aprueba."""

    def setUp(self):
        if not facial.modelos_presentes():
            self.skipTest('Sin modelos faciales: python manage.py descargar_modelos_faciales')
        self.comparador = facial.obtener_comparador()

    def test_carga_los_modelos(self):
        self.assertIsInstance(self.comparador, facial.ComparadorOpenCV)

    def test_imagen_sin_rostro_o_danada(self):
        gris = jpg('gray')
        self.assertEqual(self.comparador.comparar(gris, gris).motivos, [facial.SIN_ROSTRO_SELFIE])
        self.assertEqual(self.comparador.comparar(gris, b'no es imagen').motivos, [facial.SIN_ROSTRO_SELFIE])

    def test_muestra_de_dni_sin_rostro_real(self):
        muestra = Path(settings.BASE_DIR).parent.parent / 'docs' / 'muestras-dni' / '1-valido-anverso.jpg'
        resultado = self.comparador.comparar(muestra.read_bytes(), muestra.read_bytes())
        self.assertIsNone(resultado.similitud)


class ModelosYCalibracionTests(SimpleTestCase):
    def test_sin_modelos_queda_en_revision_manual(self):
        with TemporaryDirectory() as vacia, override_settings(MODELOS_FACIALES=vacia):
            comparador = facial.obtener_comparador()
        self.assertIsInstance(comparador, facial.ComparadorNoDisponible)
        self.assertEqual(comparador.comparar(b'', b'').motivos, [facial.COMPARADOR_NO_DISPONIBLE])

    def test_descarga_rechaza_una_huella_distinta(self):
        def falsa(url, destino):
            Path(destino).write_bytes(b'no es el modelo')

        with TemporaryDirectory() as carpeta, override_settings(MODELOS_FACIALES=carpeta), \
                mock.patch('urllib.request.urlretrieve', side_effect=falsa):
            with self.assertRaisesMessage(CommandError, 'la huella no coincide'):
                call_command('descargar_modelos_faciales', stdout=io.StringIO())
            self.assertEqual(list(Path(carpeta).iterdir()), [])

    def test_calibrar_pide_dos_personas(self):
        with TemporaryDirectory() as carpeta:
            (Path(carpeta) / 'ana').mkdir()
            (Path(carpeta) / 'ana' / 'a.jpg').write_bytes(jpg('red'))
            with self.assertRaisesMessage(CommandError, 'al menos 2 personas'):
                call_command('calibrar_umbral_facial', carpeta, stdout=io.StringIO())

    def test_calibrar_propone_umbrales(self):
        similitudes = iter([0.7, 0.2, 0.3, 0.1, 0.15, 0.65])

        class Fijo(facial.ComparadorFacial):
            def comparar(self, a, b):
                return facial.Comparacion(next(similitudes))

        with TemporaryDirectory() as carpeta, mock.patch.object(facial, 'obtener_comparador', return_value=Fijo()), \
                override_settings(UMBRAL_FACIAL_VERIFICADO=0.45, UMBRAL_FACIAL_RECHAZO=0.25):
            for persona in ('ana', 'beto'):
                (Path(carpeta) / persona).mkdir()
                for n in (1, 2):
                    (Path(carpeta) / persona / f'{n}.jpg').write_bytes(jpg('red'))
            salida = io.StringIO()
            call_command('calibrar_umbral_facial', carpeta, stdout=salida)
        texto = salida.getvalue()
        # Pares: ana1-ana2 0.7, ana1-beto1 0.2, ana1-beto2 0.3, ana2-beto1 0.1, ana2-beto2 0.15, beto1-beto2 0.65.
        self.assertIn('Misma persona: 2 pares', texto)
        self.assertIn('Personas distintas: 4 pares', texto)
        self.assertIn('UMBRAL_FACIAL_VERIFICADO=0.45 UMBRAL_FACIAL_RECHAZO=0.25', texto)
