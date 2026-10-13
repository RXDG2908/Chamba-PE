from math import degrees, pi
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import SimpleTestCase, TestCase
from django.urls import reverse

from prestadores.models import Prestador, Rubro

from .distancias import RADIO_TIERRA_KM, distancia_km


class DistanciaTests(SimpleTestCase):
    def test_distancia_conocida_en_ecuador(self):
        self.assertAlmostEqual(distancia_km(0, 0, 0, 1), pi * RADIO_TIERRA_KM / 180)

    def test_mismo_punto_y_antipodas(self):
        self.assertEqual(distancia_km(-12, -77, -12, -77), 0)
        self.assertAlmostEqual(distancia_km(0, 0, 0, 180), pi * RADIO_TIERRA_KM)


class BusquedaPrestadoresTests(TestCase):
    def setUp(self):
        self.rubro = Rubro.objects.create(nombre='Rubro de prueba')
        self.otro_rubro = Rubro.objects.create(nombre='Otro rubro de prueba')
        self.url = reverse('buscar_prestadores')
        self.parametros = {'rubro': self.rubro.id, 'latitud': 0, 'longitud': 0}

    def crear_prestador(self, rubros=None, **cambios):
        numero = Prestador.objects.count() + 1
        usuario = get_user_model().objects.create_user(username=f'prueba{numero}')
        datos = {
            'usuario': usuario, 'dni': f'{numero:08d}', 'nombres': 'Ana',
            'apellidos': 'Pérez', 'latitud': 0, 'longitud': 0,
            'radio_cobertura_km': 5, 'disponible': True,
        }
        prestador = Prestador.objects.create(**{**datos, **cambios})
        prestador.rubros.set(rubros if rubros is not None else [self.rubro])
        return prestador

    def buscar(self, **cambios):
        return self.client.get(self.url, {**self.parametros, **cambios})

    def test_filtra_rubro_disponibilidad_y_cobertura(self):
        incluido = self.crear_prestador(rubros=[self.rubro, self.otro_rubro])
        self.crear_prestador(rubros=[self.otro_rubro])
        self.crear_prestador(disponible=False)
        self.crear_prestador(longitud=1)
        respuesta = self.buscar()
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual([p['id'] for p in respuesta.json()], [incluido.id])

    def test_respuesta_solo_contiene_datos_publicos(self):
        self.crear_prestador()
        resultado = self.buscar().json()[0]
        self.assertEqual(set(resultado), {'id', 'nombres', 'apellidos', 'rubros', 'distancia_km'})
        self.assertEqual(resultado['rubros'], [{'id': self.rubro.id, 'nombre': self.rubro.nombre}])
        self.assertEqual(resultado['distancia_km'], 0)

    def test_radio_de_cada_prestador(self):
        self.crear_prestador(radio_cobertura_km=1)
        incluido = self.crear_prestador(radio_cobertura_km=5)
        respuesta = self.buscar(longitud=degrees(3 / RADIO_TIERRA_KM))
        self.assertEqual([p['id'] for p in respuesta.json()], [incluido.id])
        self.assertAlmostEqual(respuesta.json()[0]['distancia_km'], 3)

    def test_limite_inclusivo_y_puntos_a_ambos_lados(self):
        prestador = self.crear_prestador()
        for distancia, esperado in [(4.999, True), (5, True), (5.001, False)]:
            with self.subTest(distancia=distancia):
                respuesta = self.buscar(longitud=degrees(distancia / RADIO_TIERRA_KM))
                self.assertEqual([p['id'] for p in respuesta.json()], [prestador.id] if esperado else [])

    def test_comparacion_no_redondea_distancia(self):
        self.crear_prestador()
        with patch('servicios.views.distancia_km', return_value=5.000001):
            self.assertEqual(self.buscar().json(), [])

    def test_parametros_faltantes(self):
        respuesta = self.client.get(self.url)
        self.assertEqual(respuesta.status_code, 400)
        self.assertEqual(set(respuesta.json()), {'rubro', 'latitud', 'longitud'})
        for campo in self.parametros:
            with self.subTest(campo=campo):
                respuesta = self.client.get(self.url, {k: v for k, v in self.parametros.items() if k != campo})
                self.assertEqual(respuesta.status_code, 400)
                self.assertIn('obligatorio', respuesta.json()[campo][0])

    def test_parametros_invalidos(self):
        casos = {
            'rubro': ['', 'texto', '1.5', '0', '-1', '999999', '9' * 30],
            'latitud': ['', 'texto', 'NaN', 'Infinity', '-Infinity', '90.001', '-90.001'],
            'longitud': ['', 'texto', 'NaN', 'Infinity', '-Infinity', '180.001', '-180.001'],
        }
        for campo, valores in casos.items():
            for valor in valores:
                with self.subTest(campo=campo, valor=valor):
                    respuesta = self.buscar(**{campo: valor})
                    self.assertEqual(respuesta.status_code, 400)
                    self.assertIn(campo, respuesta.json())
        self.assertEqual(self.buscar(latitud=91).json()['latitud'], ['La coordenada debe estar entre -90 y 90 grados.'])
        self.assertEqual(self.buscar(rubro=999999).json()['rubro'], ['No existe un rubro con ese ID.'])

    def test_extremos_de_coordenadas_validos(self):
        for latitud, longitud in [(-90, -180), (90, 180)]:
            with self.subTest(latitud=latitud, longitud=longitud):
                self.assertEqual(self.buscar(latitud=latitud, longitud=longitud).status_code, 200)

    def test_busqueda_sin_resultados(self):
        respuesta = self.buscar()
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta.json(), [])
