"""Datos raros en el borde de la API (auditoría «harden» del 26/11): nombres, usuarios y teléfonos."""
from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse

from prestadores.models import Prestador, Rubro

User = get_user_model()


class NombresRarosClienteTests(TestCase):
    def setUp(self):
        self.url = reverse('registro_cliente')
        self.datos = {
            'username': 'ana.perez', 'email': 'ana@correo.pe', 'password': 'UnaClaveSegura2026',
            'nombres': 'Ana', 'apellidos': 'Pérez',
        }

    def post(self, **cambios):
        return self.client.post(self.url, {**self.datos, **cambios}, content_type='application/json')

    def test_acepta_nombres_de_cualquier_idioma(self):
        for i, (nombres, apellidos) in enumerate([
            ('María José', "O'Connor-Ñúñez"), ('محمد', 'العبدالله'), ('王小明', '李'), ('A.', 'B'),
        ]):
            with self.subTest(nombres=nombres):
                r = self.post(username=f'cliente{i}', email=f'c{i}@correo.pe', nombres=nombres, apellidos=apellidos)
                self.assertEqual(r.status_code, 201, r.content)

    def test_normaliza_espacios_y_tildes_combinadas(self):
        r = self.post(nombres='  Ana   Lucía ', apellidos='Peña')
        self.assertEqual(r.status_code, 201)
        usuario = User.objects.get()
        self.assertEqual(usuario.first_name, 'Ana Lucía')
        self.assertEqual(usuario.last_name, 'Peña')  # una sola letra «ñ» (NFC)

    def test_rechaza_nombres_sin_letras_o_con_simbolos(self):
        for valor in ['👷🏽‍♀️', 'Rosa 🔧', '12345', 'Ana\tMaría', 'Ana\nMaría', '​', '<b>Ana</b>', '   ']:
            with self.subTest(valor=repr(valor)):
                r = self.post(nombres=valor)
                self.assertEqual(r.status_code, 400)
                self.assertIn('nombres', r.json())
        self.assertFalse(User.objects.exists())

    def test_rechaza_usuarios_con_espacios_o_emoji(self):
        for valor in ['ana perez', 'ana😀', 'ana/perez']:
            with self.subTest(valor=valor):
                r = self.post(username=valor)
                self.assertEqual(r.status_code, 400)
                self.assertIn('username', r.json())


class DatosRarosPrestadorTests(TestCase):
    def setUp(self):
        self.rubro = Rubro.objects.get(nombre='Gasfitería')
        self.url = reverse('registro_prestador')
        self.datos = {
            'username': 'juan', 'email': 'juan@example.com', 'password': 'Clave-segura-123',
            'nombres': 'Juan', 'apellidos': 'Pérez', 'dni': '12345678',
            'rubros': [self.rubro.id], 'latitud': '-12.046374', 'longitud': '-77.042793',
            'radio_cobertura_km': 5,
        }

    def post(self, **cambios):
        return self.client.post(self.url, {**self.datos, **cambios}, content_type='application/json')

    def test_telefono_con_prefijo_y_espacios_se_guarda_en_9_digitos(self):
        for i, valor in enumerate(['+51 987 654 321', '987-654-321', '51987654321']):
            with self.subTest(valor=valor):
                r = self.post(username=f'juan{i}', email=f'j{i}@example.com', dni=f'1234567{i}', telefono=valor)
                self.assertEqual(r.status_code, 201, r.content)
                self.assertEqual(Prestador.objects.get(dni=f'1234567{i}').telefono, '987654321')

    def test_telefono_vacio_sigue_siendo_opcional(self):
        self.assertEqual(self.post(telefono='').status_code, 201)

    def test_rechaza_telefonos_que_no_se_pueden_marcar(self):
        for valor in ['abc', '12345', '+51 (01) 987-654-321', '9876543210123', '<script>']:
            with self.subTest(valor=valor):
                r = self.post(telefono=valor)
                self.assertEqual(r.status_code, 400)
                self.assertIn('telefono', r.json())

    def test_rechaza_nombres_y_apellidos_raros(self):
        r = self.post(nombres='Z̷̢a̵̛l̶͝g̸̕o̷', apellidos='✨🏠')
        self.assertEqual(r.status_code, 400)
        self.assertEqual(set(r.json()) & {'nombres', 'apellidos'}, {'nombres', 'apellidos'})
        self.assertFalse(Prestador.objects.exists())

    def test_nombre_en_el_limite_del_modelo(self):
        self.assertEqual(self.post(nombres='A' * 100).status_code, 201)
        r = self.post(username='otro', email='o@example.com', dni='87654321', nombres='A' * 101)
        self.assertEqual(r.status_code, 400)
