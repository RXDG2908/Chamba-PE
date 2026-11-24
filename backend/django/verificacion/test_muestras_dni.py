"""
T19.4: pruebas cruzadas de HU-19 con DNIs de muestra (lectura y coincidencia de nombres).

Las hace David, no quien construyó HU-19 (Renzo). Dos partes:

1. Coincidencia de nombres: casos que se dan con DNIs reales (tildes, un solo nombre,
   nombres largos que la MRZ corta, letras confundidas por el OCR), pasando por la API
   completa con un lector falso. Corren siempre.
2. Las muestras ficticias de docs/muestras-dni/ leídas con EasyOCR de verdad. Solo corren
   si los modelos de EasyOCR ya están descargados en la PC (así nunca se descargan durante
   las pruebas); si no, se saltan con el motivo.

Todos los datos son ficticios: no se usan DNIs ni fotos de personas reales.
"""
import io
import os
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import mock, skipUnless

from django.conf import settings
from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase, override_settings
from django.urls import reverse
from PIL import Image, ImageEnhance
from rest_framework.test import APIClient

from prestadores.models import Prestador, Rubro

from .models import Consentimiento, IntentoDni
from .tests import CLAVE, JPG, LectorFalso, hacer_mrz
from .textos_consentimiento import FINALIDAD_DNI, TEXTO_DNI, VERSION_DNI

User = get_user_model()

MUESTRAS = Path(settings.BASE_DIR).parent.parent / 'docs' / 'muestras-dni'
MODELOS_EASYOCR = Path(os.environ.get('EASYOCR_MODULE_PATH', Path.home() / '.EasyOCR')) / 'model'
# Detector de texto y reconocedor del alfabeto latino que usa Reader(['es']).
OCR_LISTO = all((MODELOS_EASYOCR / archivo).exists() for archivo in ('craft_mlt_25k.pth', 'latin_g2.pth'))
try:
    import easyocr  # noqa: F401
except ImportError:
    OCR_LISTO = False


class BaseMuestras(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.rubro = Rubro.objects.create(nombre='Rubro T19.4')
        cls.prestador = Prestador.objects.create(
            usuario=User.objects.create_user(username='prestador_t194', password=CLAVE),
            nombres='Juan Carlos', apellidos='Pérez Gómez', dni='12345678',
            latitud=0, longitud=0, radio_cobertura_km=5,
        )
        cls.prestador.rubros.set([cls.rubro])
        Consentimiento.objects.create(usuario=cls.prestador.usuario, finalidad=FINALIDAD_DNI,
                                      version_texto=VERSION_DNI, texto_aceptado=TEXTO_DNI)

    def setUp(self):
        carpeta = TemporaryDirectory()
        self.addCleanup(carpeta.cleanup)
        configuracion = override_settings(MEDIA_ROOT=carpeta.name + '/media',
                                          RAIZ_PRIVADA=carpeta.name + '/privado', VALIDADOR_DNI='simulado')
        configuracion.enable()
        self.addCleanup(configuracion.disable)
        self.cliente = APIClient(enforce_csrf_checks=True)
        self.cliente.force_login(self.prestador.usuario)
        self.cliente.get(reverse('csrf'))
        self.cliente.credentials(HTTP_X_CSRFTOKEN=self.cliente.cookies['csrftoken'].value)

    def enviar(self, anverso=JPG, reverso=JPG):
        datos = {
            'anverso': SimpleUploadedFile('anverso.jpg', anverso, content_type='image/jpeg'),
            'reverso': SimpleUploadedFile('reverso.jpg', reverso, content_type='image/jpeg'),
        }
        respuesta = self.cliente.post(reverse('verificacion_dni'), datos, format='multipart')
        self.assertEqual(respuesta.status_code, 201, respuesta.content)
        return respuesta.json()


class CoincidenciaNombresCruzadaTests(BaseMuestras):
    """La MRZ comparada con la fuente (tabla simulada) en casos que se dan con DNIs reales."""

    def verificar(self, apellidos_mrz, nombres_mrz, fuente, cortar_mrz=False):
        lineas = hacer_mrz(apellidos=apellidos_mrz, nombres=nombres_mrz)
        if cortar_mrz:
            lineas[2] = lineas[2][:30]  # la línea 3 del DNI mide 30: los nombres largos se cortan
        tabla = {'12345678': fuente}
        with mock.patch.dict('verificacion.validadores.TABLA_SIMULADA', tabla, clear=True), \
                mock.patch('verificacion.views.obtener_lector', return_value=LectorFalso(lineas)):
            return self.enviar()

    def assertCoincide(self, cuerpo):
        self.assertEqual((cuerpo['resultado'], cuerpo['fuente']['estado'], cuerpo['motivos']),
                         ('validado', 'coincide', []))

    def assertRevisionPorNombres(self, cuerpo):
        self.assertEqual((cuerpo['resultado'], cuerpo['fuente']['estado'], cuerpo['motivos']),
                         ('revision_manual', 'no_coincide', ['nombres_distintos']))

    def test_fuente_con_tildes_minusculas_y_espacios_de_mas(self):
        self.assertCoincide(self.verificar('PEREZ<GOMEZ', 'JUAN<CARLOS', ('  Pérez   Gómez ', 'juan carlos')))

    def test_enie_y_dieresis_de_la_fuente(self):
        self.assertCoincide(self.verificar('NUNXXEZ<ARGUELLO', 'JOSE', ('Núñez Argüello', 'José')))

    def test_un_solo_nombre_en_el_dni_y_dos_en_la_fuente_va_a_revision(self):
        self.assertRevisionPorNombres(self.verificar('PEREZ<GOMEZ', 'JUAN', ('PEREZ GOMEZ', 'JUAN CARLOS')))

    def test_apellidos_en_otro_orden_va_a_revision(self):
        self.assertRevisionPorNombres(self.verificar('GOMEZ<PEREZ', 'JUAN<CARLOS', ('PEREZ GOMEZ', 'JUAN CARLOS')))

    def test_letra_confundida_por_el_ocr_en_el_nombre_va_a_revision(self):
        # En los números la O se corrige a 0; en los nombres no se adivina: lo ve un administrador.
        self.assertRevisionPorNombres(self.verificar('PEREZ<G0MEZ', 'JUAN<CARLOS', ('PEREZ GOMEZ', 'JUAN CARLOS')))

    def test_nombre_largo_cortado_en_la_mrz_va_a_revision(self):
        # Hallazgo de T19.4: «RODRIGUEZ<DE<LA<CRUZ<<MARIA<FERNANDA» mide 36 y el DNI lo corta a 30,
        # así que nunca coincide con la fuente. Es seguro (lo revisa un administrador), pero esos
        # prestadores siempre irán a revisión manual.
        cuerpo = self.verificar('RODRIGUEZ<DE<LA<CRUZ', 'MARIA<FERNANDA',
                                ('RODRIGUEZ DE LA CRUZ', 'MARIA FERNANDA'), cortar_mrz=True)
        self.assertEqual(cuerpo['lectura']['nombres'], 'MARIA FE')
        self.assertRevisionPorNombres(cuerpo)

    def test_los_nombres_registrados_por_el_prestador_no_se_comparan(self):
        # Hallazgo de T19.4: el prestador se registró como «Juan Carlos Pérez Gómez», pero solo se
        # comparan el número de DNI y la MRZ contra la fuente. Un DNI suyo con otro nombre se valida;
        # el administrador debe mirar el nombre al aprobar (la verificación nunca aprueba sola).
        cuerpo = self.verificar('TORRES<VEGA', 'ANA', ('TORRES VEGA', 'ANA'))
        self.assertCoincide(cuerpo)
        self.prestador.refresh_from_db()
        self.assertNotEqual(self.prestador.estado, Prestador.Estado.VERIFICADO)


@skipUnless(OCR_LISTO, 'EasyOCR o sus modelos no están en esta PC (ver docs/pruebas-T19.4.md).')
class MuestrasConOcrRealTests(BaseMuestras):
    """Las 3 muestras ficticias de docs/muestras-dni/ leídas con EasyOCR de verdad."""

    def muestra(self, nombre):
        return (MUESTRAS / f'{nombre}-anverso.jpg').read_bytes(), (MUESTRAS / f'{nombre}-reverso.jpg').read_bytes()

    def test_dni_valido_se_lee_y_coincide(self):
        anverso, reverso = self.muestra('1-valido')
        cuerpo = self.enviar(anverso, reverso)
        self.assertEqual(cuerpo['lectura']['dni'], '12345678')
        self.assertEqual(cuerpo['lectura']['apellidos'], 'PEREZ GOMEZ')
        self.assertEqual(cuerpo['lectura']['nombres'], 'JUAN CARLOS')
        self.assertTrue(cuerpo['tiene_foto'])
        self.assertEqual((cuerpo['resultado'], cuerpo['motivos'], cuerpo['fuente']['estado']),
                         ('validado', [], 'coincide'))

    def test_dni_de_otra_persona_va_a_revision(self):
        anverso, reverso = self.muestra('2-otro-dni')
        cuerpo = self.enviar(anverso, reverso)
        self.assertEqual(cuerpo['lectura']['dni'], '87654321')
        self.assertEqual(cuerpo['resultado'], 'revision_manual')
        self.assertIn('dni_distinto', cuerpo['motivos'])
        # Con otro DNI ni se consulta la fuente.
        self.assertEqual(cuerpo['fuente']['estado'], 'no_disponible')

    def test_dni_borroso_va_a_revision_sin_lectura(self):
        anverso, reverso = self.muestra('3-borroso')
        cuerpo = self.enviar(anverso, reverso)
        self.assertEqual(cuerpo['resultado'], 'revision_manual')
        self.assertIn('mrz_ilegible', cuerpo['motivos'])
        self.assertEqual(IntentoDni.objects.get().dni_leido, '')

    def test_fotos_imperfectas_se_leen_bien_o_van_a_revision_nunca_con_datos_errados(self):
        # Como saldría la muestra válida con un celular. El 23/11 solo la JPEG muy comprimida se
        # leyó; las demás fueron a revisión manual (ver docs/pruebas-T19.4.md). Lo que no puede
        # pasar es que se valide con un DNI o nombres mal leídos.
        anverso, reverso = self.muestra('1-valido')
        original = Image.open(io.BytesIO(reverso)).convert('RGB')
        ancho, alto = original.size
        variantes = {
            'inclinada 3 grados': original.rotate(3, expand=True, fillcolor='white'),
            'mitad de tamaño': original.resize((ancho // 2, alto // 2)),
            'oscura': ImageEnhance.Brightness(original).enhance(0.4),
            'JPEG calidad 25': original,
        }
        for nombre, foto in variantes.items():
            with self.subTest(nombre):
                IntentoDni.objects.all().delete()  # máximo 5 intentos por día
                salida = io.BytesIO()
                foto.save(salida, 'JPEG', quality=25 if 'JPEG' in nombre else 90)
                cuerpo = self.enviar(anverso, salida.getvalue())
                if cuerpo['resultado'] == 'validado':
                    self.assertEqual((cuerpo['lectura']['dni'], cuerpo['lectura']['apellidos'],
                                      cuerpo['lectura']['nombres']), ('12345678', 'PEREZ GOMEZ', 'JUAN CARLOS'))
                else:
                    self.assertEqual(cuerpo['resultado'], 'revision_manual')
                    self.assertTrue({'mrz_ilegible', 'mrz_invalida'} & set(cuerpo['motivos']), cuerpo['motivos'])
