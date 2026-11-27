"""Perfil editable de clientes y prestadores (/api/usuarios/perfil/)."""
import shutil
import tempfile
from io import BytesIO

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings
from PIL import Image
from rest_framework.test import APITestCase

from prestadores.models import Prestador, Rubro

User = get_user_model()


def imagen(formato='PNG', tamano=(800, 600), modo='RGBA', nombre='foto.png'):
    salida = BytesIO()
    Image.new(modo, tamano, (200, 40, 40, 255) if modo == 'RGBA' else (200, 40, 40)).save(salida, formato)
    return SimpleUploadedFile(nombre, salida.getvalue(), content_type=f'image/{formato.lower()}')


class PerfilBase(APITestCase):
    def setUp(self):
        self.media = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.media, ignore_errors=True)
        ajuste = override_settings(MEDIA_ROOT=self.media)
        ajuste.enable()
        self.addCleanup(ajuste.disable)

    def crear_prestador(self, estado=Prestador.Estado.PENDIENTE, username='ana'):
        usuario = User.objects.create_user(username, f'{username}@correo.pe', 'clave-segura-123')
        prestador = Prestador.objects.create(
            usuario=usuario, nombres='Ana', apellidos='Quispe', dni='12345678' if username == 'ana' else '87654321',
            latitud=-12.05, longitud=-77.04, radio_cobertura_km=5, estado=estado, disponible=True,
        )
        prestador.rubros.set([Rubro.objects.first()])
        return usuario, prestador


class PerfilClienteTests(PerfilBase):
    def setUp(self):
        super().setUp()
        self.cliente = User.objects.create_user('luis', 'luis@correo.pe', 'clave-segura-123',
                                                first_name='Luis', last_name='Rojas')
        self.client.force_authenticate(self.cliente)

    def test_lee_su_perfil_sin_campos_de_prestador(self):
        datos = self.client.get('/api/usuarios/perfil/').json()
        self.assertEqual(datos['nombres'], 'Luis')
        self.assertEqual(datos['rol'], 'cliente')
        self.assertIsNone(datos['foto'])
        self.assertTrue(datos['tiene_contrasena'])
        self.assertNotIn('tipo', datos)

    def test_cambia_nombre_telefono_y_correo(self):
        respuesta = self.client.patch('/api/usuarios/perfil/', {
            'nombres': '  José  Luis ', 'apellidos': 'Rojas', 'telefono': '+51 987 654 321', 'email': 'nuevo@correo.pe',
        }, format='json')
        self.assertEqual(respuesta.status_code, 200, respuesta.content)
        self.cliente.refresh_from_db()
        self.assertEqual(self.cliente.first_name, 'José Luis')
        self.assertEqual(self.cliente.email, 'nuevo@correo.pe')
        self.assertEqual(self.cliente.perfil.telefono, '987654321')

    def test_rechaza_datos_invalidos_y_correo_ajeno(self):
        User.objects.create_user('otra', 'otra@correo.pe', 'x')
        respuesta = self.client.patch('/api/usuarios/perfil/', {
            'nombres': 'L1s', 'telefono': '123', 'email': 'OTRA@correo.pe',
        }, format='json')
        self.assertEqual(respuesta.status_code, 400)
        self.assertEqual(set(respuesta.json()), {'nombres', 'telefono', 'email'})

    def test_un_cliente_no_cambia_campos_de_prestador(self):
        self.client.patch('/api/usuarios/perfil/', {'tipo': 'empresa', 'nombre_comercial': 'X'}, format='json')
        self.assertFalse(Prestador.objects.exists())

    def test_sube_la_foto_como_jpeg_cuadrado_y_la_quita(self):
        respuesta = self.client.post('/api/usuarios/perfil/foto/', {'foto': imagen()}, format='multipart')
        self.assertEqual(respuesta.status_code, 200, respuesta.content)
        self.cliente.perfil.refresh_from_db()
        with Image.open(self.cliente.perfil.foto.path) as foto:
            self.assertEqual((foto.format, foto.size), ('JPEG', (512, 512)))
        self.assertTrue(respuesta.json()['foto'].endswith('.jpg'))
        self.assertTrue(self.client.get('/api/usuarios/yo/').json()['foto'].startswith('/media/fotos/'))

        anterior = self.cliente.perfil.foto.path
        self.client.post('/api/usuarios/perfil/foto/', {'foto': imagen('JPEG', modo='RGB', nombre='b.jpg')},
                         format='multipart')
        self.cliente.perfil.refresh_from_db()
        self.assertNotEqual(self.cliente.perfil.foto.path, anterior)
        self.assertFalse(self.cliente.perfil.foto.storage.exists(anterior))

        self.assertEqual(self.client.delete('/api/usuarios/perfil/foto/').status_code, 204)
        self.cliente.perfil.refresh_from_db()
        self.assertFalse(self.cliente.perfil.foto)

    def test_rechaza_archivos_que_no_son_imagen_o_formatos_no_permitidos(self):
        texto = SimpleUploadedFile('foto.jpg', b'no soy una imagen', content_type='image/jpeg')
        self.assertEqual(self.client.post('/api/usuarios/perfil/foto/', {'foto': texto}, format='multipart').status_code, 400)
        gif = imagen('GIF', modo='RGB', nombre='a.gif')
        respuesta = self.client.post('/api/usuarios/perfil/foto/', {'foto': gif}, format='multipart')
        self.assertEqual(respuesta.status_code, 400)
        self.assertIn('JPG', respuesta.json()['foto'][0])
        self.assertEqual(self.client.post('/api/usuarios/perfil/foto/', {}, format='multipart').status_code, 400)

    def test_cambia_la_contrasena_y_sigue_con_sesion(self):
        self.client.force_authenticate(None)
        self.client.login(username='luis', password='clave-segura-123')
        malo = self.client.post('/api/usuarios/perfil/contrasena/', {'actual': 'otra', 'nueva': 'Nueva-clave-456'}, format='json')
        self.assertEqual(malo.status_code, 400)
        debil = self.client.post('/api/usuarios/perfil/contrasena/', {'actual': 'clave-segura-123', 'nueva': '123'}, format='json')
        self.assertIn('nueva', debil.json())
        bien = self.client.post('/api/usuarios/perfil/contrasena/', {'actual': 'clave-segura-123', 'nueva': 'Nueva-clave-456'}, format='json')
        self.assertEqual(bien.status_code, 204)
        self.assertEqual(self.client.get('/api/usuarios/perfil/').status_code, 200)
        self.cliente.refresh_from_db()
        self.assertTrue(self.cliente.check_password('Nueva-clave-456'))

    def test_cuenta_de_la_app_sin_contrasena(self):
        self.cliente.set_unusable_password()
        self.cliente.save()
        respuesta = self.client.post('/api/usuarios/perfil/contrasena/', {'actual': 'a', 'nueva': 'b'}, format='json')
        self.assertEqual(respuesta.status_code, 400)
        self.assertFalse(self.client.get('/api/usuarios/perfil/').json()['tiene_contrasena'])

    def test_sin_sesion_no_hay_perfil(self):
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get('/api/usuarios/perfil/').status_code, 403)
        self.assertEqual(self.client.patch('/api/usuarios/perfil/', {}, format='json').status_code, 403)


class PerfilPrestadorTests(PerfilBase):
    def test_se_presenta_como_empresa_con_ruc(self):
        usuario, prestador = self.crear_prestador()
        self.client.force_authenticate(usuario)
        respuesta = self.client.patch('/api/usuarios/perfil/', {
            'tipo': 'empresa', 'nombre_comercial': ' Gasfitería  Ana SAC ', 'ruc': '20123456789',
            'descripcion': 'Atendemos todo Lima.', 'radio_cobertura_km': 12, 'latitud': -12.1, 'longitud': -77.0312345,
        }, format='json')
        self.assertEqual(respuesta.status_code, 200, respuesta.content)
        self.assertEqual(respuesta.json()['nombre_publico'], 'Gasfitería Ana SAC')
        self.assertEqual(respuesta.json()['dni'], '•••••678')
        prestador.refresh_from_db()
        self.assertEqual((prestador.tipo, prestador.ruc, prestador.radio_cobertura_km), ('empresa', '20123456789', 12))
        self.assertEqual(respuesta.json()['longitud'], -77.031234)
        self.assertEqual(self.client.patch('/api/usuarios/perfil/', {'latitud': 95}, format='json').status_code, 400)
        self.assertEqual(self.client.get('/api/usuarios/yo/').json()['nombre_publico'], 'Gasfitería Ana SAC')

    def test_empresa_sin_nombre_y_ruc_invalido(self):
        usuario, _ = self.crear_prestador()
        self.client.force_authenticate(usuario)
        respuesta = self.client.patch('/api/usuarios/perfil/', {'tipo': 'empresa', 'ruc': '12345'}, format='json')
        self.assertEqual(respuesta.status_code, 400)
        self.assertIn('ruc', respuesta.json())
        respuesta = self.client.patch('/api/usuarios/perfil/', {'tipo': 'empresa'}, format='json')
        self.assertIn('nombre_comercial', respuesta.json())

    def test_vuelve_a_independiente_y_muestra_su_nombre(self):
        usuario, prestador = self.crear_prestador()
        prestador.tipo, prestador.nombre_comercial = 'empresa', 'Ana SAC'
        prestador.save()
        self.client.force_authenticate(usuario)
        datos = self.client.patch('/api/usuarios/perfil/', {'tipo': 'independiente'}, format='json').json()
        self.assertEqual(datos['nombre_publico'], 'Ana Quispe')

    def test_cambia_su_nombre_mientras_no_esta_verificado(self):
        usuario, prestador = self.crear_prestador()
        self.client.force_authenticate(usuario)
        respuesta = self.client.patch('/api/usuarios/perfil/', {'nombres': 'Ana María'}, format='json')
        self.assertEqual(respuesta.status_code, 200)
        prestador.refresh_from_db()
        usuario.refresh_from_db()
        self.assertEqual((prestador.nombres, usuario.first_name), ('Ana María', 'Ana María'))

    def test_verificado_no_cambia_el_nombre_del_dni(self):
        usuario, prestador = self.crear_prestador(Prestador.Estado.VERIFICADO)
        self.client.force_authenticate(usuario)
        self.assertFalse(self.client.get('/api/usuarios/perfil/').json()['nombres_editables'])
        respuesta = self.client.patch('/api/usuarios/perfil/', {'nombres': 'Otra'}, format='json')
        self.assertEqual(respuesta.status_code, 400)
        # Enviar el mismo nombre (el formulario manda todo) no es un cambio.
        self.assertEqual(self.client.patch('/api/usuarios/perfil/', {'nombres': 'Ana', 'descripcion': 'Hola'},
                                           format='json').status_code, 200)

    def test_cambia_rubros_pero_no_los_deja_vacios(self):
        usuario, prestador = self.crear_prestador()
        self.client.force_authenticate(usuario)
        rubros = list(Rubro.objects.values_list('id', flat=True)[:2])
        self.assertEqual(self.client.patch('/api/usuarios/perfil/', {'rubros': rubros}, format='json').status_code, 200)
        self.assertEqual(set(prestador.rubros.values_list('id', flat=True)), set(rubros))
        self.assertEqual(self.client.patch('/api/usuarios/perfil/', {'rubros': []}, format='json').status_code, 400)

    def test_la_busqueda_muestra_nombre_publico_y_foto(self):
        usuario, prestador = self.crear_prestador(Prestador.Estado.VERIFICADO)
        prestador.tipo, prestador.nombre_comercial = 'empresa', 'Ana SAC'
        prestador.save()
        self.client.force_authenticate(usuario)
        self.client.post('/api/usuarios/perfil/foto/', {'foto': imagen()}, format='multipart')
        self.client.force_authenticate(None)
        rubro = prestador.rubros.first().id
        resultado = self.client.get('/api/prestadores/buscar/', {'rubro': rubro, 'latitud': -12.05, 'longitud': -77.04}).json()
        self.assertEqual(resultado[0]['nombre_publico'], 'Ana SAC')
        self.assertEqual(resultado[0]['tipo'], 'empresa')
        self.assertTrue(resultado[0]['foto'].startswith('/media/fotos/'))
