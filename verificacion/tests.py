"""
T19.2: lectura de la MRZ del DNI y API de intentos de verificación.

El lector (EasyOCR) se reemplaza por uno falso que devuelve las líneas de la MRZ,
así las pruebas no dependen de instalar el OCR ni de fotos de DNIs reales.
"""
import os
from datetime import date
from tempfile import TemporaryDirectory
from unittest import mock

from django.conf import settings
from django.contrib.auth import get_user_model
from django.core.exceptions import ImproperlyConfigured
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase, override_settings
from django.urls import reverse
from rest_framework.test import APIClient

from prestadores.models import Prestador, Rubro

from .lector import LectorDni
from .models import IntentoDni
from .mrz import ErrorMrz, digito_control, interpretar
from .validadores import (
    NO_DISPONIBLE, ValidadorEldni, ValidadorSimulado, nombres_coinciden,
    normalizar, obtener_validador,
)

User = get_user_model()
CLAVE = 'Clave-segura-2026'
JPG = b'\xff\xd8\xff\xe0' + b'0' * 100


def hacer_mrz(dni='12345678', nacimiento='950412', vencimiento='310412',
              apellidos='PEREZ<GOMEZ', nombres='JUAN<CARLOS'):
    """Arma las 3 líneas de una MRZ TD1 con sus dígitos de control correctos."""
    numero = dni + '<'
    l1 = 'IDPER' + numero + str(digito_control(numero)) + '<' * 15
    cuerpo = (nacimiento + str(digito_control(nacimiento)) + 'M'
              + vencimiento + str(digito_control(vencimiento)) + 'PER' + '<' * 11)
    total = digito_control(l1[5:30] + cuerpo[0:7] + cuerpo[8:15] + cuerpo[18:29])
    l2 = cuerpo + str(total)
    l3 = (apellidos + '<<' + nombres).ljust(30, '<')
    return [l1, l2, l3]


class LectorFalso(LectorDni):
    def __init__(self, lineas, foto=b'foto'):
        self.lineas = lineas
        self.foto = foto

    def leer_textos(self, imagen):
        return self.lineas

    def recortar_titular(self, imagen):
        return self.foto


class DigitoControlTests(TestCase):
    def test_ejemplos_del_estandar_icao(self):
        # Ejemplo público del documento ICAO 9303 para el formato TD1.
        self.assertEqual(digito_control('D23145890'), 7)
        self.assertEqual(digito_control('740812'), 2)
        self.assertEqual(digito_control('120415'), 9)

    def test_el_relleno_vale_cero(self):
        self.assertEqual(digito_control('12345678<'), digito_control('12345678'))


class InterpretarMrzTests(TestCase):
    def test_lee_una_mrz_correcta(self):
        lectura = interpretar(hacer_mrz())
        self.assertEqual(lectura.dni, '12345678')
        self.assertEqual(lectura.apellidos, 'PEREZ GOMEZ')
        self.assertEqual(lectura.nombres, 'JUAN CARLOS')
        self.assertEqual(lectura.fecha_nacimiento, date(1995, 4, 12))
        self.assertEqual(lectura.fecha_vencimiento, date(2031, 4, 12))

    def test_la_nxx_vuelve_a_ser_enie(self):
        lectura = interpretar(hacer_mrz(apellidos='NUNXXEZ<RIOS', nombres='MARIA'))
        self.assertEqual(lectura.apellidos, 'NUÑEZ RIOS')

    def test_tolera_espacios_y_confusion_de_letras_en_los_numeros(self):
        lineas = hacer_mrz()
        lineas[0] = lineas[0].replace('12345678', '1234567B')  # B en lugar de 8
        lineas[1] = lineas[1].replace('950412', '95O412')  # O en lugar de 0
        self.assertEqual(interpretar(lineas).dni, '12345678')

    def test_sin_las_tres_lineas_es_ilegible(self):
        for textos in ([], ['TEXTO CUALQUIERA'], hacer_mrz()[:2]):
            with self.assertRaises(ErrorMrz) as error:
                interpretar(textos)
            self.assertEqual(error.exception.motivo, 'mrz_ilegible')

    def test_cada_campo_alterado_rompe_su_digito_de_control(self):
        base = hacer_mrz()
        cambios = {
            'numero de documento': (0, 5, '2'),
            'nacimiento': (1, 0, '8'),
            'vencimiento': (1, 8, '4'),
            'control compuesto': (1, 29, '0' if base[1][29] != '0' else '1'),
            'relleno de la linea 1': (0, 21, 'B'),
        }
        for nombre, (linea, posicion, nuevo) in cambios.items():
            lineas = list(base)
            texto = lineas[linea]
            lineas[linea] = texto[:posicion] + nuevo + texto[posicion + 1:]
            with self.subTest(nombre):
                with self.assertRaises(ErrorMrz) as error:
                    interpretar(lineas)
                self.assertEqual(error.exception.motivo, 'mrz_invalida')

    def test_fecha_imposible_es_invalida(self):
        with self.assertRaises(ErrorMrz) as error:
            interpretar(hacer_mrz(nacimiento='951332'))
        self.assertEqual(error.exception.motivo, 'mrz_invalida')


class ValidadorTests(TestCase):
    def test_normalizar_ignora_tildes_mayusculas_y_espacios(self):
        self.assertEqual(normalizar('  núñez   Ríos '), 'NUNEZ RIOS')
        self.assertEqual(normalizar('NUNXXEZ'), 'NUNEZ')

    def test_nombres_coinciden_sin_importar_tildes_ni_mayusculas(self):
        lectura = interpretar(hacer_mrz(apellidos='NUNXXEZ<RIOS', nombres='MARIA'))
        self.assertTrue(nombres_coinciden(lectura, {'apellidos': 'Núñez Ríos', 'nombres': 'maría'}))

    def test_un_nombre_parecido_no_coincide(self):
        lectura = interpretar(hacer_mrz())
        for apellidos, nombres in (('PEREZ GOMES', 'JUAN CARLOS'), ('PEREZ GOMEZ', 'JUAN'),
                                   ('GOMEZ PEREZ', 'JUAN CARLOS')):
            with self.subTest(apellidos=apellidos, nombres=nombres):
                self.assertFalse(nombres_coinciden(lectura, {'apellidos': apellidos, 'nombres': nombres}))

    def test_simulado_responde_con_la_tabla_y_sin_internet(self):
        respuesta = ValidadorSimulado().consultar('12345678')
        self.assertEqual(respuesta, {'estado': 'ok', 'apellidos': 'PEREZ GOMEZ', 'nombres': 'JUAN CARLOS'})
        self.assertEqual(ValidadorSimulado().consultar('00000000'), NO_DISPONIBLE)

    def test_eldni_todavia_no_consulta_y_pasa_a_revision(self):
        self.assertEqual(ValidadorEldni().consultar('12345678'), NO_DISPONIBLE)

    def test_el_validador_se_elige_en_la_configuracion(self):
        self.assertIsInstance(obtener_validador(), ValidadorSimulado)
        with override_settings(VALIDADOR_DNI='eldni'):
            self.assertIsInstance(obtener_validador(), ValidadorEldni)
        with override_settings(VALIDADOR_DNI='inventado'):
            with self.assertRaises(ImproperlyConfigured):
                obtener_validador()


@override_settings(PASSWORD_HASHERS=['django.contrib.auth.hashers.MD5PasswordHasher'])
class ApiDniTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.rubro = Rubro.objects.create(nombre='Rubro T19.2')
        cls.prestador = cls.crear_prestador('prestador_t192', '12345678')
        cls.otro = cls.crear_prestador('otro_t192', '87654321')
        cls.cliente = User.objects.create_user(username='cliente_t192', password=CLAVE)
        cls.admin = User.objects.create_user(username='admin_t192', password=CLAVE, is_staff=True)

    @classmethod
    def crear_prestador(cls, username, dni):
        prestador = Prestador.objects.create(
            usuario=User.objects.create_user(username=username, password=CLAVE),
            nombres='Juan Carlos', apellidos='Pérez Gómez', dni=dni, latitud=0, longitud=0,
            radio_cobertura_km=5,
        )
        prestador.rubros.set([cls.rubro])
        return prestador

    def setUp(self):
        carpeta = TemporaryDirectory()
        self.addCleanup(carpeta.cleanup)
        configuracion = override_settings(MEDIA_ROOT=carpeta.name + '/media', RAIZ_PRIVADA=carpeta.name + '/privado')
        configuracion.enable()
        self.addCleanup(configuracion.disable)

    def sesion(self, usuario):
        """Cliente que exige CSRF, con la sesión abierta y el token en la cabecera."""
        cliente = APIClient(enforce_csrf_checks=True)
        if usuario is not None:
            cliente.force_login(usuario)
            cliente.get(reverse('csrf'))
            cliente.credentials(HTTP_X_CSRFTOKEN=cliente.cookies['csrftoken'].value)
        return cliente

    def enviar(self, cliente, lineas=None, foto=b'foto', **archivos):
        datos = {
            'anverso': SimpleUploadedFile('a.jpg', JPG, content_type='image/jpeg'),
            'reverso': SimpleUploadedFile('r.jpg', JPG, content_type='image/jpeg'),
        }
        datos.update(archivos)
        datos = {k: v for k, v in datos.items() if v is not None}
        with mock.patch('verificacion.views.obtener_lector',
                        return_value=LectorFalso(hacer_mrz() if lineas is None else lineas, foto)):
            return cliente.post(reverse('verificacion_dni'), datos, format='multipart')

    def test_mrz_correcta_guarda_la_lectura_y_la_foto(self):
        respuesta = self.enviar(self.sesion(self.prestador.usuario))
        self.assertEqual(respuesta.status_code, 201, respuesta.content)
        cuerpo = respuesta.json()
        self.assertEqual(cuerpo['lectura']['dni'], '12345678')
        self.assertEqual(cuerpo['lectura']['apellidos'], 'PEREZ GOMEZ')
        self.assertTrue(cuerpo['lectura']['mrz_valida'])
        self.assertTrue(cuerpo['tiene_foto'])
        self.assertEqual(cuerpo['resultado'], 'validado')
        self.assertEqual(cuerpo['motivos'], [])
        self.assertEqual(cuerpo['fuente'], {'nombre': 'simulada', 'estado': 'coincide'})
        self.assertEqual(IntentoDni.objects.count(), 1)

    def test_no_cambia_el_estado_del_prestador(self):
        self.enviar(self.sesion(self.prestador.usuario))
        self.prestador.refresh_from_db()
        self.assertEqual(self.prestador.estado, Prestador.Estado.PENDIENTE)

    def test_mrz_ilegible(self):
        cuerpo = self.enviar(self.sesion(self.prestador.usuario), lineas=['BORROSO']).json()
        self.assertIn('mrz_ilegible', cuerpo['motivos'])
        self.assertEqual(cuerpo['lectura']['dni'], '')

    def test_mrz_invalida(self):
        lineas = hacer_mrz()
        lineas[1] = lineas[1][:29] + ('0' if lineas[1][29] != '0' else '1')
        cuerpo = self.enviar(self.sesion(self.prestador.usuario), lineas=lineas).json()
        self.assertIn('mrz_invalida', cuerpo['motivos'])

    def test_dni_distinto_al_registrado(self):
        cuerpo = self.enviar(self.sesion(self.prestador.usuario), lineas=hacer_mrz(dni='11111111')).json()
        self.assertIn('dni_distinto', cuerpo['motivos'])
        self.assertEqual(cuerpo['resultado'], 'revision_manual')

    def test_dni_vencido(self):
        cuerpo = self.enviar(self.sesion(self.prestador.usuario), lineas=hacer_mrz(vencimiento='200412')).json()
        self.assertIn('dni_vencido', cuerpo['motivos'])

    def test_sin_foto_es_solo_informativo(self):
        cuerpo = self.enviar(self.sesion(self.prestador.usuario), foto=None).json()
        self.assertIn('sin_foto', cuerpo['motivos'])
        self.assertFalse(cuerpo['tiene_foto'])

    def test_imagenes_obligatorias_y_validas(self):
        cliente = self.sesion(self.prestador.usuario)
        r = self.enviar(cliente, anverso=None)
        self.assertEqual(r.status_code, 400)
        self.assertIn('anverso', r.json())
        r = self.enviar(cliente, reverso=SimpleUploadedFile('r.txt', b'texto', content_type='text/plain'))
        self.assertIn('reverso', r.json())
        grande = SimpleUploadedFile('r.jpg', JPG + b'0' * (5 * 1024 * 1024), content_type='image/jpeg')
        self.assertIn('reverso', self.enviar(cliente, reverso=grande).json())
        self.assertEqual(IntentoDni.objects.count(), 0)

    def test_permisos(self):
        anonimo = self.sesion(None)
        self.assertEqual(self.enviar(anonimo).status_code, 403)
        self.assertEqual(self.enviar(self.sesion(self.cliente)).status_code, 403)
        self.assertEqual(self.enviar(self.sesion(self.admin)).status_code, 403)
        self.assertEqual(IntentoDni.objects.count(), 0)

    def test_exige_csrf(self):
        cliente = self.sesion(self.prestador.usuario)
        cliente.credentials()  # sin la cabecera X-CSRFToken
        self.assertEqual(self.enviar(cliente).status_code, 403)

    def test_maximo_cinco_intentos_por_dia(self):
        cliente = self.sesion(self.prestador.usuario)
        for _ in range(5):
            self.assertEqual(self.enviar(cliente).status_code, 201)
        self.assertEqual(self.enviar(cliente).status_code, 429)
        # Otro prestador no se ve afectado.
        self.assertEqual(self.enviar(self.sesion(self.otro.usuario)).status_code, 201)

    def test_el_prestador_ve_su_ultimo_intento(self):
        cliente = self.sesion(self.prestador.usuario)
        self.assertEqual(cliente.get(reverse('verificacion_dni')).status_code, 404)
        self.enviar(cliente, lineas=['BORROSO'])
        ultimo = self.enviar(cliente).json()
        self.assertEqual(cliente.get(reverse('verificacion_dni')).json()['id'], ultimo['id'])

    def test_el_administrador_lista_los_intentos_de_un_prestador(self):
        self.enviar(self.sesion(self.prestador.usuario))
        self.enviar(self.sesion(self.otro.usuario))
        url = f"{reverse('verificacion_dni')}?prestador={self.prestador.id}"
        respuesta = self.sesion(self.admin).get(url)
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(len(respuesta.json()), 1)
        self.assertEqual(self.sesion(self.prestador.usuario).get(url).status_code, 403)

    def test_las_fotos_no_tienen_url_publica_y_solo_las_ve_el_administrador(self):
        intento_id = self.enviar(self.sesion(self.prestador.usuario)).json()['id']
        intento = IntentoDni.objects.get(id=intento_id)
        # Las fotos quedan fuera de MEDIA_ROOT, así que /media/ no las sirve.
        self.assertTrue(intento.anverso.path.startswith(os.path.abspath(settings.RAIZ_PRIVADA)))
        self.assertFalse(intento.anverso.path.startswith(os.path.abspath(settings.MEDIA_ROOT)))
        for tipo in ('anverso', 'reverso', 'titular'):
            url = reverse('verificacion_dni_foto', args=[intento_id, tipo])
            respuesta = self.sesion(self.admin).get(url)
            respuesta.close()  # suelta el archivo, si no Windows no deja borrar la carpeta temporal
            self.assertEqual(respuesta.status_code, 200, tipo)
            self.assertEqual(self.sesion(self.prestador.usuario).get(url).status_code, 403, tipo)
            self.assertEqual(self.sesion(None).get(url).status_code, 403, tipo)
        url = reverse('verificacion_dni_foto', args=[intento_id, 'otra'])
        self.assertEqual(self.sesion(self.admin).get(url).status_code, 404)

    def test_la_fuente_no_responde_pasa_a_revision_manual(self):
        with override_settings(VALIDADOR_DNI='eldni'):
            cuerpo = self.enviar(self.sesion(self.prestador.usuario)).json()
        self.assertEqual(cuerpo['resultado'], 'revision_manual')
        self.assertEqual(cuerpo['motivos'], ['fuente_no_disponible'])
        self.assertEqual(cuerpo['fuente'], {'nombre': 'eldni', 'estado': 'no_disponible'})

    def test_dni_que_la_fuente_no_conoce_pasa_a_revision_manual(self):
        self.prestador.dni = '55555555'
        self.prestador.save()
        cuerpo = self.enviar(self.sesion(self.prestador.usuario), lineas=hacer_mrz(dni='55555555')).json()
        self.assertEqual(cuerpo['resultado'], 'revision_manual')
        self.assertEqual(cuerpo['motivos'], ['fuente_no_disponible'])

    def test_nombres_distintos_a_la_fuente_pasan_a_revision_manual(self):
        lineas = hacer_mrz(apellidos='PEREZ<GOMES')
        cuerpo = self.enviar(self.sesion(self.prestador.usuario), lineas=lineas).json()
        self.assertEqual(cuerpo['resultado'], 'revision_manual')
        self.assertEqual(cuerpo['motivos'], ['nombres_distintos'])
        self.assertEqual(cuerpo['fuente']['estado'], 'no_coincide')

    def test_la_enie_de_la_mrz_coincide_con_la_fuente(self):
        lineas = hacer_mrz(dni='87654321', apellidos='NUNXXEZ<RIOS', nombres='MARIA')
        cuerpo = self.enviar(self.sesion(self.otro.usuario), lineas=lineas).json()
        self.assertEqual(cuerpo['resultado'], 'validado')

    def test_nunca_aprueba_al_prestador_por_si_solo(self):
        cuerpo = self.enviar(self.sesion(self.prestador.usuario)).json()
        self.assertEqual(cuerpo['resultado'], 'validado')
        self.prestador.refresh_from_db()
        self.assertEqual(self.prestador.estado, Prestador.Estado.PENDIENTE)

    def test_con_dni_distinto_no_se_consulta_la_fuente(self):
        with mock.patch('verificacion.servicio.obtener_validador') as validador:
            cuerpo = self.enviar(self.sesion(self.prestador.usuario), lineas=hacer_mrz(dni='11111111')).json()
        validador.assert_not_called()
        self.assertEqual(cuerpo['motivos'], ['dni_distinto'])
