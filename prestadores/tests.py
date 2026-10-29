from io import StringIO

from django.contrib.auth import get_user_model
from django.core.management import call_command
from django.core.management.base import CommandError
from django.test import TestCase, override_settings
from django.urls import reverse

from .models import Prestador, Rubro


class RegistroPrestadorTests(TestCase):
    def setUp(self):
        self.rubro = Rubro.objects.get(nombre='Gasfitería')  # cargado por la migración 0002
        self.url = reverse('registro_prestador')
        self.datos = {
            'username': 'juan', 'email': 'juan@example.com', 'password': 'Clave-segura-123',
            'nombres': 'Juan', 'apellidos': 'Pérez', 'dni': '12345678',
            'rubros': [self.rubro.id], 'latitud': '-12.046374', 'longitud': '-77.042793',
            'radio_cobertura_km': 5,
        }

    def post(self, **cambios):
        datos = {**self.datos, **cambios}
        return self.client.post(self.url, datos, content_type='application/json')

    def test_registro_valido_queda_pendiente(self):
        r = self.post()
        self.assertEqual(r.status_code, 201)
        self.assertEqual(r.json()['estado'], 'pendiente')
        self.assertNotIn('password', r.json())
        self.assertEqual(Prestador.objects.get().rubros.count(), 1)

    def test_sin_dni_se_rechaza(self):
        datos = {k: v for k, v in self.datos.items() if k != 'dni'}
        r = self.client.post(self.url, datos, content_type='application/json')
        self.assertEqual(r.status_code, 400)
        self.assertIn('dni', r.json())

    def test_dni_invalido(self):
        self.assertEqual(self.post(dni='123').status_code, 400)

    def test_sin_rubro_se_rechaza(self):
        r = self.post(rubros=[])
        self.assertEqual(r.status_code, 400)
        self.assertIn('rubros', r.json())

    def test_sin_cobertura_se_rechaza(self):
        datos = {k: v for k, v in self.datos.items() if k != 'radio_cobertura_km'}
        r = self.client.post(self.url, datos, content_type='application/json')
        self.assertEqual(r.status_code, 400)
        self.assertIn('radio_cobertura_km', r.json())

    def test_dni_duplicado(self):
        self.assertEqual(self.post().status_code, 201)
        self.assertEqual(self.post(username='otro').status_code, 400)

    def test_lista_de_rubros(self):
        r = self.client.get(reverse('rubros'))
        self.assertEqual(len(r.json()), Rubro.objects.count())
        self.assertIn({'id': self.rubro.id, 'nombre': 'Gasfitería'}, r.json())


    def test_campos_obligatorios_ausentes_y_vacios(self):
        for campo in self.datos:
            for ausente in (True, False):
                with self.subTest(campo=campo, ausente=ausente):
                    datos = self.datos.copy()
                    if ausente:
                        datos.pop(campo)
                    else:
                        datos[campo] = [] if campo == 'rubros' else ''
                    r = self.client.post(self.url, datos, content_type='application/json')
                    self.assertEqual(r.status_code, 400)
                    self.assertIn(campo, r.json())
                    self.assertEqual(Prestador.objects.count(), 0)
                    self.assertEqual(get_user_model().objects.count(), 0)

    def test_cobertura_invalida(self):
        casos = {'latitud': [-90.000001, 90.000001, 'texto', None],
                 'longitud': [-180.000001, 180.000001, 'texto', None],
                 'radio_cobertura_km': [0, -1, 101, 'texto', None]}
        for campo, valores in casos.items():
            for valor in valores:
                with self.subTest(campo=campo, valor=valor):
                    r = self.post(**{campo: valor})
                    self.assertEqual(r.status_code, 400)
                    self.assertIn(campo, r.json())
                    self.assertFalse(Prestador.objects.exists())

    def test_estado_no_puede_aprobarse_desde_registro(self):
        r = self.post(estado='verificado')
        self.assertEqual(r.status_code, 201)
        prestador = Prestador.objects.get()
        self.assertEqual(prestador.estado, Prestador.Estado.PENDIENTE)
        self.assertEqual(prestador.get_estado_display(), 'Pendiente de verificación')


@override_settings(DEBUG=True)
class PrepararDemoTests(TestCase):
    def preparar(self):
        call_command('preparar_demo', stdout=StringIO())

    def test_crea_las_tres_cuentas_con_su_rol(self):
        self.preparar()
        roles = {u.username: u.perfil.rol for u in get_user_model().objects.all()}
        self.assertEqual(roles, {'admin.demo': 'administrador', 'ana.cliente': 'cliente',
                                 'jorge.respaldo': 'prestador'})
        self.assertTrue(self.client.login(username='jorge.respaldo', password='PrestadorChamba2026'))

    def test_el_prestador_de_respaldo_sale_en_la_busqueda(self):
        self.preparar()
        rubro = Rubro.objects.get(nombre='Gasfitería')
        r = self.client.get(reverse('buscar_prestadores'),
                            {'rubro': rubro.id, 'latitud': '-12.0464', 'longitud': '-77.0428'})
        self.assertEqual([p['nombres'] for p in r.json()], ['Jorge'])

    def test_se_puede_correr_dos_veces(self):
        self.preparar()
        self.preparar()
        self.assertEqual(get_user_model().objects.count(), 3)
        self.assertEqual(Prestador.objects.count(), 1)

    @override_settings(DEBUG=False)
    def test_no_corre_fuera_de_desarrollo(self):
        with self.assertRaises(CommandError):
            self.preparar()
        self.assertFalse(get_user_model().objects.exists())
