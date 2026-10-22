"""
T7.3: pruebas de precisión del radio de cobertura.

Los puntos de prueba se generan con la fórmula de destino sobre la esfera (punto a
una distancia y rumbo dados), que es independiente de Haversine: si `distancia_km`
devuelve la misma distancia con la que se generó el punto, el cálculo es correcto.
Se prueba en Lima, Tumbes y Tacna porque un grado de longitud mide distinto según
la latitud, y en ocho rumbos (N, NE, E, SE, S, SO, O, NO).
"""
from decimal import Decimal
from itertools import count
from math import acos, asin, atan2, cos, degrees, pi, radians, sin

from django.contrib.auth import get_user_model
from django.test import SimpleTestCase, TestCase
from django.urls import reverse

from prestadores.models import Prestador, Rubro

from .distancias import RADIO_TIERRA_KM, distancia_km

# Centros con 6 decimales, la misma precisión con la que se guardan en Prestador.
LIMA = (Decimal('-12.046374'), Decimal('-77.042793'))
TUMBES = (Decimal('-3.566900'), Decimal('-80.451500'))
TACNA = (Decimal('-18.006600'), Decimal('-70.246300'))
CIUDADES = {'Lima': LIMA, 'Tumbes': TUMBES, 'Tacna': TACNA}
RUMBOS = range(0, 360, 45)
UN_MILIMETRO_KM = 1e-6


def destino(latitud, longitud, distancia, rumbo):
    """Punto a `distancia` km de (latitud, longitud) con el rumbo dado en grados."""
    fi1, lambda1, theta = radians(float(latitud)), radians(float(longitud)), radians(rumbo)
    delta = distancia / RADIO_TIERRA_KM
    fi2 = asin(sin(fi1) * cos(delta) + cos(fi1) * sin(delta) * cos(theta))
    lambda2 = lambda1 + atan2(
        sin(theta) * sin(delta) * cos(fi1), cos(delta) - sin(fi1) * sin(fi2),
    )
    return degrees(fi2), degrees(lambda2)


def ley_de_cosenos_km(lat1, lon1, lat2, lon2):
    """Segundo método para comparar; pierde precisión en distancias de metros."""
    fi1, fi2 = radians(lat1), radians(lat2)
    coseno = sin(fi1) * sin(fi2) + cos(fi1) * cos(fi2) * cos(radians(lon2 - lon1))
    return RADIO_TIERRA_KM * acos(min(1.0, coseno))


class PrecisionHaversineTests(SimpleTestCase):
    def test_distancia_al_punto_generado_en_ocho_rumbos_y_tres_latitudes(self):
        for ciudad, centro in CIUDADES.items():
            for rumbo in RUMBOS:
                for distancia in (0.001, 1, 3, 5, 5.5, 50, 100):
                    with self.subTest(ciudad=ciudad, rumbo=rumbo, km=distancia):
                        punto = destino(*centro, distancia, rumbo)
                        self.assertAlmostEqual(
                            distancia_km(*centro, *punto), distancia, delta=1e-9,
                        )

    def test_un_grado_de_latitud_mide_lo_mismo_en_todo_el_peru(self):
        esperado = pi * RADIO_TIERRA_KM / 180  # 111,195 km
        for ciudad, (latitud, longitud) in CIUDADES.items():
            with self.subTest(ciudad=ciudad):
                self.assertAlmostEqual(
                    distancia_km(latitud, longitud, latitud + 1, longitud), esperado, places=9,
                )

    def test_un_grado_de_longitud_se_acorta_con_la_latitud(self):
        # Sobre un paralelo y en tramos cortos, la distancia es R·cos(latitud)·Δλ.
        delta = 0.01
        for ciudad, (latitud, longitud) in CIUDADES.items():
            with self.subTest(ciudad=ciudad):
                esperado = RADIO_TIERRA_KM * cos(radians(latitud)) * radians(delta)
                medido = distancia_km(latitud, longitud, latitud, longitud + Decimal(delta))
                self.assertAlmostEqual(medido, esperado, delta=1e-7)
        # En Tacna un grado de longitud es ~5 km más corto que en Tumbes.
        en_tumbes = distancia_km(*TUMBES, TUMBES[0], TUMBES[1] + 1)
        en_tacna = distancia_km(*TACNA, TACNA[0], TACNA[1] + 1)
        self.assertGreater(en_tumbes - en_tacna, 5)

    def test_coincide_con_la_ley_de_cosenos_entre_ciudades(self):
        ciudades = {
            'Lima': (-12.046374, -77.042793), 'Arequipa': (-16.398866, -71.536961),
            'Cusco': (-13.531950, -71.967463), 'Trujillo': (-8.111990, -79.028690),
            'Iquitos': (-3.749120, -73.253830), 'Tumbes': (-3.566900, -80.451500),
        }
        nombres = list(ciudades)
        for i, origen in enumerate(nombres):
            for llegada in nombres[i + 1:]:
                with self.subTest(origen=origen, llegada=llegada):
                    a, b = ciudades[origen], ciudades[llegada]
                    self.assertAlmostEqual(
                        distancia_km(*a, *b), ley_de_cosenos_km(*a, *b), delta=1e-6,
                    )

    def test_simetrica_y_acepta_decimal_o_float(self):
        punto = destino(*LIMA, 5, 60)
        ida = distancia_km(*LIMA, *punto)
        self.assertAlmostEqual(ida, distancia_km(*punto, *LIMA), delta=1e-12)
        self.assertEqual(ida, distancia_km(float(LIMA[0]), float(LIMA[1]), *punto))

    def test_seis_decimales_bastan_para_el_borde(self):
        # Prestador.latitud/longitud guardan 6 decimales: el redondeo mueve el punto
        # menos de 0,1 m, muy por debajo de cualquier radio de cobertura.
        for rumbo in RUMBOS:
            with self.subTest(rumbo=rumbo):
                latitud, longitud = destino(*LIMA, 5, rumbo)
                redondeado = (round(latitud, 6), round(longitud, 6))
                self.assertLess(abs(distancia_km(*LIMA, *redondeado) - 5), 0.0001)


class RadioCoberturaBusquedaTests(TestCase):
    """La búsqueda real filtra por el radio de cada prestador con el borde inclusivo."""

    def setUp(self):
        self.rubro = Rubro.objects.create(nombre='Rubro T7.3')
        self.url = reverse('buscar_prestadores')
        self.numeros = count(1)

    def crear_prestador(self, centro=LIMA, radio=5):
        numero = next(self.numeros)
        prestador = Prestador.objects.create(
            usuario=get_user_model().objects.create_user(username=f'radio{numero}'),
            nombres='Ana', apellidos='Pérez', dni=f'7300{numero:04d}',
            latitud=centro[0], longitud=centro[1], radio_cobertura_km=radio,
            disponible=True, estado=Prestador.Estado.VERIFICADO,
        )
        prestador.rubros.set([self.rubro])
        return prestador

    def buscar_desde(self, punto):
        # repr() conserva todos los dígitos del float: el cliente queda a la distancia exacta.
        respuesta = self.client.get(self.url, {
            'rubro': self.rubro.id, 'latitud': repr(punto[0]), 'longitud': repr(punto[1]),
        })
        self.assertEqual(respuesta.status_code, 200)
        return respuesta.json()

    def test_dentro_borde_y_fuera_del_radio_en_ocho_rumbos(self):
        prestador = self.crear_prestador()
        for distancia, incluido in ((3, True), (5, True), (5.5, False)):
            for rumbo in RUMBOS:
                with self.subTest(km=distancia, rumbo=rumbo):
                    resultado = self.buscar_desde(destino(*LIMA, distancia, rumbo))
                    if incluido:
                        self.assertEqual([p['id'] for p in resultado], [prestador.id])
                        self.assertAlmostEqual(resultado[0]['distancia_km'], distancia, delta=1e-9)
                    else:
                        self.assertEqual(resultado, [])

    def test_un_milimetro_decide_en_el_borde(self):
        prestador = self.crear_prestador()
        for rumbo in RUMBOS:
            with self.subTest(rumbo=rumbo):
                adentro = self.buscar_desde(destino(*LIMA, 5 - UN_MILIMETRO_KM, rumbo))
                self.assertEqual([p['id'] for p in adentro], [prestador.id])
                afuera = self.buscar_desde(destino(*LIMA, 5 + UN_MILIMETRO_KM, rumbo))
                self.assertEqual(afuera, [])

    def test_la_latitud_no_se_ignora_al_medir_la_longitud(self):
        # Una aproximación plana con 111,32 km por grado en ambos ejes vería a 5,05 km
        # al este como 4,94 km en Lima (4,80 km en Tacna) y lo incluiría por error.
        for ciudad, centro in CIUDADES.items():
            with self.subTest(ciudad=ciudad):
                Prestador.objects.all().delete()
                prestador = self.crear_prestador(centro)
                for rumbo in (90, 270):
                    self.assertEqual(self.buscar_desde(destino(*centro, 5.05, rumbo)), [])
                    resultado = self.buscar_desde(destino(*centro, 4.95, rumbo))
                    self.assertEqual([p['id'] for p in resultado], [prestador.id])

    def test_varios_prestadores_alrededor_del_cliente(self):
        # El cliente queda exactamente a 5 km del prestador del borde.
        borde = self.crear_prestador(LIMA)
        cliente = destino(*LIMA, 5, 30)
        redondear = lambda punto: tuple(Decimal(f'{c:.6f}') for c in punto)
        dentro = self.crear_prestador(redondear(destino(*cliente, 3, 200)))
        fuera = self.crear_prestador(redondear(destino(*cliente, 5.5, 120)))
        resultado = self.buscar_desde(cliente)
        self.assertEqual([p['id'] for p in resultado], [dentro.id, borde.id])
        self.assertAlmostEqual(resultado[0]['distancia_km'], 3, delta=0.0001)
        self.assertAlmostEqual(resultado[1]['distancia_km'], 5, delta=1e-9)
        self.assertNotIn(fuera.id, [p['id'] for p in resultado])

    def test_radios_minimo_y_maximo_permitidos(self):
        # El modelo admite radios de 1 a 100 km.
        for radio in (1, 100):
            with self.subTest(radio=radio):
                Prestador.objects.all().delete()
                prestador = self.crear_prestador(radio=radio)
                for rumbo in (0, 135, 270):
                    resultado = self.buscar_desde(destino(*LIMA, radio, rumbo))
                    self.assertEqual([p['id'] for p in resultado], [prestador.id])
                    self.assertEqual(self.buscar_desde(destino(*LIMA, radio * 1.001, rumbo)), [])

    def test_cada_prestador_usa_su_propio_radio(self):
        corto = self.crear_prestador(radio=2)
        largo = self.crear_prestador(radio=10)
        resultado = self.buscar_desde(destino(*LIMA, 7, 315))
        self.assertEqual([p['id'] for p in resultado], [largo.id])
        self.assertNotIn(corto.id, [p['id'] for p in resultado])
