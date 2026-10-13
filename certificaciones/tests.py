"""T2.3: contrato de carga con BD de pruebas y almacenamiento temporal."""
from base64 import b64decode
from pathlib import Path
from tempfile import TemporaryDirectory

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase
from django.urls import reverse

from .models import CertificacionPrestador


def pdf_valido(tamano=None):
    objetos = [b'<< /Type /Catalog /Pages 2 0 R >>',
               b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
               b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] >>']
    contenido = b'%PDF-1.4\n'
    offsets = []
    for numero, objeto in enumerate(objetos, 1):
        offsets.append(len(contenido))
        contenido += f'{numero} 0 obj\n'.encode() + objeto + b'\nendobj\n'
    inicio_xref = len(contenido)
    contenido += b'xref\n0 4\n0000000000 65535 f \n'
    for offset in offsets:
        contenido += f'{offset:010d} 00000 n \n'.encode()
    contenido += b'trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n'
    contenido += str(inicio_xref).encode() + b'\n%%EOF\n'
    # Relleno antes de EOF, sin alterar los offsets de objetos/xref.
    if tamano is not None:
        contenido = contenido[:-6] + b' ' * (tamano - len(contenido)) + contenido[-6:]
    return contenido


# JPEG real de 1x1 generado con System.Drawing, sin dependencia adicional.
JPG_VALIDO = b64decode(
    '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD8qqKKKAP/2Q=='
)


class CargaCertificacionTests(TestCase):
    def setUp(self):
        directorio = TemporaryDirectory(prefix='chambape-t23-')
        self.addCleanup(directorio.cleanup)
        self.directorio = Path(directorio.name)
        configuracion = self.settings(MEDIA_ROOT=directorio.name)
        configuracion.enable()
        self.addCleanup(configuracion.disable)
        self.url = reverse('upload_certification')

    def cargar(self, nombre='certificado.pdf', contenido=None, mime='application/pdf', **datos):
        if contenido is None:
            contenido = pdf_valido()
        archivo = SimpleUploadedFile(nombre, contenido, content_type=mime)
        return self.client.post(self.url, {'file': archivo, **datos})

    def verificar_guardado(self, respuesta, contenido):
        self.assertEqual(respuesta.status_code, 201)
        self.assertEqual(respuesta.json()['status'], 'Pendiente de verificación')
        ruta = self.directorio / respuesta.json()['file_name']
        self.assertEqual(ruta.read_bytes(), contenido)

    def test_pdf_aceptado_y_guardado(self):
        contenido = pdf_valido()
        self.verificar_guardado(self.cargar(contenido=contenido), contenido)

    def test_jpg_aceptado_y_guardado(self):
        contenido = JPG_VALIDO
        self.verificar_guardado(self.cargar('certificado.jpg', contenido, 'image/jpeg'), contenido)

    def test_archivo_ausente(self):
        respuesta = self.client.post(self.url, {})
        self.assertEqual(respuesta.status_code, 400)
        self.assertIn('error', respuesta.json())
        self.assertEqual(list(self.directorio.iterdir()), [])

    def test_formato_no_permitido(self):
        for nombre, mime in [('archivo.png', 'image/png'), ('archivo.exe', 'application/pdf'),
                             ('archivo.pdf', 'text/plain')]:
            with self.subTest(nombre=nombre, mime=mime):
                respuesta = self.cargar(nombre, b'contenido', mime)
                self.assertEqual(respuesta.status_code, 400)
                self.assertIn('error', respuesta.json())
                self.assertEqual(list(self.directorio.iterdir()), [])

    def test_exactamente_5_mb(self):
        contenido = pdf_valido(5 * 1024 * 1024)
        self.assertEqual(len(contenido), 5242880)
        self.verificar_guardado(self.cargar(contenido=contenido), contenido)

    def test_supera_5_mb_por_un_byte(self):
        respuesta = self.cargar(contenido=pdf_valido(5 * 1024 * 1024 + 1))
        self.assertEqual(respuesta.status_code, 400)
        self.assertIn('5 MB', respuesta.json()['error'])
        self.assertEqual(list(self.directorio.iterdir()), [])

    def test_certificacion_asociada_al_prestador(self):
        from prestadores.models import Prestador, Rubro
        rubro = Rubro.objects.get(nombre='Gasfitería')
        respuesta = self.client.post(reverse('registro_prestador'), {
            'username': 'certificado', 'email': 'certificado@example.com',
            'password': 'Clave-segura-123', 'nombres': 'Ana', 'apellidos': 'Prueba',
            'dni': '87654321', 'rubros': [rubro.pk], 'latitud': '-12.046374',
            'longitud': '-77.042793', 'radio_cobertura_km': 5,
        }, content_type='application/json')
        self.assertEqual(respuesta.status_code, 201)
        prestador = Prestador.objects.get(pk=respuesta.json()['id'])
        carga = self.cargar(prestador=prestador.pk)
        self.assertEqual(carga.status_code, 201)
        self.assertEqual(CertificacionPrestador.objects.count(), 1,
                         'HU-2 incumplida: la carga no persiste una certificación del perfil.')
        certificacion = CertificacionPrestador.objects.get()
        self.assertEqual(getattr(certificacion, 'prestador_id', None), prestador.pk,
                         'HU-2 incumplida: la certificación no está asociada al prestador.')
