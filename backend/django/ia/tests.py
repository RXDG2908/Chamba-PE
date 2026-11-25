from decimal import Decimal

from django.contrib.auth import get_user_model
from django.test import SimpleTestCase, TestCase
from django.urls import reverse

from prestadores.models import Prestador, Rubro
from servicios.models import Solicitud

from . import recomendacion as r

CLIENTE = {'latitud': -12.046374, 'longitud': -77.042793}
# 1 km al norte del cliente en grados de latitud.
KM = 1 / 111.195


class FormulaTests(SimpleTestCase):
    def test_factor_distancia_baja_en_linea_recta_hasta_10_km(self):
        self.assertEqual(r.factor_distancia(0), 1)
        self.assertAlmostEqual(r.factor_distancia(2.5), 0.75)
        self.assertEqual(r.factor_distancia(10), 0)
        self.assertEqual(r.factor_distancia(25), 0)

    def test_sin_calificaciones_vale_la_previa(self):
        self.assertEqual(r.calificacion_ajustada(None, 0), r.CALIFICACION_PREVIA)
        self.assertAlmostEqual(r.factor_calificacion(None, 0), (3.5 - 1) / 4)

    def test_una_sola_opinion_de_5_no_gana_a_veinte_de_4_8(self):
        self.assertLess(r.calificacion_ajustada(Decimal('5.00'), 1), r.calificacion_ajustada(Decimal('4.80'), 20))

    def test_disponibilidad_baja_con_cada_servicio_en_curso(self):
        self.assertEqual([r.factor_disponibilidad(n) for n in (0, 1, 3)], [1, 0.5, 0.25])

    def test_tiempo_estimado(self):
        self.assertEqual(r.minutos_estimados(0, 0), 10)
        self.assertEqual(r.minutos_estimados(5, 0), 25)  # 10 de preparación + 15 de trayecto
        self.assertEqual(r.minutos_estimados(5, 2), 85)  # + 30 por cada servicio en curso


class RecomendacionApiTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.rubro = Rubro.objects.create(nombre='Gasfitería HU-10')
        cls.otro_rubro = Rubro.objects.create(nombre='Pintura HU-10')

    def prestador(self, nombre, km=0.0, promedio=None, total=0, rubro=None, **cambios):
        datos = {
            'usuario': get_user_model().objects.create_user(username=f'hu10_{nombre}'),
            'nombres': nombre, 'apellidos': 'Prueba', 'dni': f'{Prestador.objects.count() + 10000001}',
            'latitud': round(CLIENTE['latitud'] + km * KM, 6), 'longitud': CLIENTE['longitud'],
            'radio_cobertura_km': 15, 'disponible': True, 'estado': Prestador.Estado.VERIFICADO,
            'calificacion_promedio': promedio, 'calificaciones_total': total, **cambios,
        }
        prestador = Prestador.objects.create(**datos)
        prestador.rubros.set([rubro or self.rubro])
        return prestador

    def en_curso(self, prestador, cantidad, estado=Solicitud.Estado.EN_CAMINO):
        for _ in range(cantidad):
            Solicitud.objects.create(
                prestador=prestador, rubro=self.rubro, cliente_nombre='Cliente', cliente_telefono='987654321',
                descripcion='Trabajo', estado=estado, **CLIENTE,
            )

    def pedir(self, **cambios):
        return self.client.get(reverse('recomendacion'), {'rubro': self.rubro.id, **CLIENTE, **cambios})

    def test_recomienda_al_mejor_puntaje_con_motivo_y_tiempo(self):
        self.prestador('Cerca', km=0.5)
        mejor = self.prestador('Valorado', km=2, promedio=Decimal('4.90'), total=25)
        respuesta = self.pedir()
        self.assertEqual(respuesta.status_code, 200)
        recomendado = respuesta.json()['recomendado']
        self.assertEqual(recomendado['id'], mejor.id)
        self.assertEqual(recomendado['tiempo_estimado_min'], 16)
        self.assertEqual(recomendado['servicios_en_curso'], 0)
        self.assertEqual(recomendado['motivo'],
                         'Está a 2,0 km de ti, tiene ★ 4,9 con 25 calificaciones y no tiene servicios en curso.')
        self.assertEqual([a['nombres'] for a in respuesta.json()['alternativas']], ['Cerca'])
        # Mismos datos públicos que la búsqueda, sin DNI ni teléfono.
        self.assertNotIn('dni', recomendado)
        self.assertNotIn('telefono', recomendado)

    def test_con_calificaciones_parecidas_gana_el_mas_cercano(self):
        cerca = self.prestador('Cerca', km=1, promedio=Decimal('4.50'), total=10)
        self.prestador('Lejos', km=6, promedio=Decimal('4.60'), total=10)
        recomendado = self.pedir().json()['recomendado']
        self.assertEqual(recomendado['id'], cerca.id)
        self.assertTrue(recomendado['motivo'].startswith('Es el más cercano (a 1,0 km de ti)'))

    def test_los_servicios_en_curso_bajan_el_puntaje_y_alargan_el_tiempo(self):
        ocupado = self.prestador('Ocupado', km=1)
        libre = self.prestador('Libre', km=1.5)
        self.en_curso(ocupado, 2)
        self.en_curso(ocupado, 1, Solicitud.Estado.FINALIZADA)  # los terminados no cuentan
        datos = self.pedir().json()
        self.assertEqual(datos['recomendado']['id'], libre.id)
        alternativa = datos['alternativas'][0]
        self.assertEqual(alternativa['servicios_en_curso'], 2)
        self.assertEqual(alternativa['tiempo_estimado_min'], 73)
        self.assertIn('tiene 2 servicios en curso', alternativa['motivo'])

    def test_solo_considera_los_que_aparecen_en_la_busqueda(self):
        self.prestador('Inactivo', disponible=False, promedio=Decimal('5.00'), total=50)
        self.prestador('Pendiente', estado=Prestador.Estado.PENDIENTE)
        self.prestador('OtroRubro', rubro=self.otro_rubro)
        self.prestador('FueraDeRadio', km=8, radio_cobertura_km=5)
        valido = self.prestador('Valido', km=3)
        datos = self.pedir().json()
        self.assertEqual(datos['recomendado']['id'], valido.id)
        self.assertEqual(datos['alternativas'], [])

    def test_sin_prestadores_devuelve_null(self):
        self.assertEqual(self.pedir().json(), {'recomendado': None, 'alternativas': []})

    def test_maximo_tres_alternativas_en_orden(self):
        for n in range(6):
            self.prestador(f'P{n}', km=n + 0.5)
        datos = self.pedir().json()
        puntajes = [datos['recomendado']['puntaje']] + [a['puntaje'] for a in datos['alternativas']]
        self.assertEqual(len(datos['alternativas']), 3)
        self.assertEqual(puntajes, sorted(puntajes, reverse=True))

    def test_empate_exacto_lo_resuelve_el_id(self):
        primero = self.prestador('Uno', km=1)
        self.prestador('Dos', km=1)
        self.assertEqual(self.pedir().json()['recomendado']['id'], primero.id)

    def test_valida_igual_que_la_busqueda(self):
        respuesta = self.client.get(reverse('recomendacion'), {'latitud': 0})
        self.assertEqual(respuesta.status_code, 400)
        self.assertEqual(set(respuesta.json()), {'rubro', 'longitud'})
