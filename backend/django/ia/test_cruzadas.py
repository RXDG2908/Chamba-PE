"""
T10.4: pruebas cruzadas de HU-10 con datos de ejemplo (las hace David; HU-10 es de Renzo).

Las 13 pruebas de ia/tests.py ya cubren la fórmula y los casos básicos (cerca sin
calificaciones contra valorado, ocupado contra libre, 5★ con una opinión contra 4,8★ con
veinte, sin prestadores). Aquí se prueba lo que falta:

1. HU-10 junto al resto del sistema: el puntaje cambia cuando el servicio avanza por la API
   de HU-11, cuando el cliente califica por la API de HU-12 y cuando el prestador se
   desactiva por la de HU-6.
2. Una muestra grande de datos ficticios: la respuesta coincide con la fórmula tal como la
   escribe docs/API_RECOMENDACION.md (calculada aquí aparte, sin usar ia/recomendacion.py),
   solo recomienda a quien sale en la búsqueda, no hace una consulta por prestador y
   responde en menos de 3 s.
"""
import random
import time
from decimal import Decimal

from django.contrib.auth import get_user_model
from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext
from django.urls import reverse

from calificaciones.models import Calificacion
from prestadores.models import Prestador, Rubro
from servicios.models import Solicitud

User = get_user_model()
CLIENTE = {'latitud': -12.046374, 'longitud': -77.042793}
KM = 1 / 111.195  # 1 km de latitud en grados
CAMPOS_PUBLICOS = {
    'id', 'nombres', 'apellidos', 'rubros', 'latitud', 'longitud', 'distancia_km',
    'calificacion_promedio', 'calificaciones_total', 'tipo', 'nombre_publico', 'descripcion', 'foto', 'nuevo',
    'puntaje', 'tiempo_estimado_min', 'servicios_en_curso', 'motivo',
}
EN_CURSO = ('aceptada', 'en_camino', 'en_servicio')


def puntaje_segun_documento(distancia_km, promedio, total, en_curso):
    """La fórmula de docs/API_RECOMENDACION.md, escrita aquí de nuevo y sin redondear."""
    cercania = max(0.0, 1 - distancia_km / 10)
    suma = (promedio or 0) * total
    estrellas = (suma + 3.5 * 3) / (total + 3)
    calificacion = (estrellas - 1) / 4
    disponibilidad = 1 / (1 + en_curso)
    return 100 * (0.50 * cercania + 0.35 * calificacion + 0.15 * disponibilidad)


def minutos_segun_documento(distancia_km, en_curso):
    return round(10 + distancia_km / 20 * 60 + 30 * en_curso)


class Base(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.rubro = Rubro.objects.create(nombre='Gasfitería T10.4')

    def prestador(self, nombre, km, **cambios):
        datos = {
            'usuario': User.objects.create_user(username=f't104_{nombre}'),
            'nombres': nombre, 'apellidos': 'Muestra', 'dni': f'{Prestador.objects.count() + 20000001}',
            'latitud': round(CLIENTE['latitud'] + km * KM, 6), 'longitud': CLIENTE['longitud'],
            'radio_cobertura_km': 15, 'disponible': True, 'estado': Prestador.Estado.VERIFICADO, **cambios,
        }
        p = Prestador.objects.create(**datos)
        p.rubros.set([self.rubro])
        return p

    def recomendacion(self):
        r = self.client.get(reverse('recomendacion'), {'rubro': self.rubro.id, **CLIENTE})
        self.assertEqual(r.status_code, 200, r.content)
        return r.json()

    def recomendado(self):
        return self.recomendacion()['recomendado']


class ConLasOtrasHistoriasTests(Base):
    """El puntaje responde a lo que pasa en HU-11, HU-12 y HU-6, usando sus API reales."""

    def setUp(self):
        self.cliente = User.objects.create_user(username='t104_cliente')
        self.ana = self.prestador('Ana', 1)      # más cerca
        self.beto = self.prestador('Beto', 1.5)

    def como(self, usuario):
        self.client.force_login(usuario)
        return self.client

    def pedir_servicio(self, prestador):
        r = self.como(self.cliente).post(reverse('crear_solicitud'), {
            'prestador': prestador.id, 'rubro': self.rubro.id, 'cliente_nombre': 'Cliente Muestra',
            'cliente_telefono': '900000000', 'descripcion': 'Prueba T10.4',
            'latitud': f"{CLIENTE['latitud']:.6f}", 'longitud': f"{CLIENTE['longitud']:.6f}",
        }, content_type='application/json')
        self.assertEqual(r.status_code, 201, r.content)
        self.client.logout()
        return r.json()['id']

    def accion(self, prestador, id, accion, **cuerpo):
        r = self.como(prestador.usuario).post(
            reverse(f'{accion}_solicitud', args=[id]), cuerpo, content_type='application/json')
        self.assertEqual(r.status_code, 200, r.content)
        self.client.logout()

    def test_el_seguimiento_de_hu11_cambia_quien_esta_libre(self):
        id = self.pedir_servicio(self.ana)
        # Pendiente todavía no la ocupa.
        self.assertEqual((self.recomendado()['id'], self.recomendado()['servicios_en_curso']), (self.ana.id, 0))
        self.accion(self.ana, id, 'aceptar')
        for siguiente in (None, 'en_camino', 'en_servicio'):
            if siguiente:
                self.accion(self.ana, id, 'avanzar', estado=siguiente)
            datos = self.recomendacion()
            self.assertEqual(datos['recomendado']['id'], self.beto.id, siguiente)
            self.assertEqual(datos['alternativas'][0]['servicios_en_curso'], 1)
            self.assertEqual(datos['alternativas'][0]['tiempo_estimado_min'], minutos_segun_documento(
                datos['alternativas'][0]['distancia_km'], 1))
        # Al finalizar vuelve a estar libre y vuelve a ser la recomendada.
        self.accion(self.ana, id, 'avanzar', estado='finalizada')
        self.assertEqual((self.recomendado()['id'], self.recomendado()['servicios_en_curso']), (self.ana.id, 0))
        # Una solicitud rechazada tampoco la ocupa.
        self.accion(self.ana, self.pedir_servicio(self.ana), 'rechazar')
        self.assertEqual(self.recomendado()['id'], self.ana.id)

    def test_las_calificaciones_de_hu12_cambian_al_recomendado(self):
        # Beto (2 km) necesita 2 calificaciones de 5★ para pasar a Ana (1 km, sin calificaciones):
        # con una sola, el promedio bayesiano todavía no le alcanza.
        self.beto.latitud = round(CLIENTE['latitud'] + 2 * KM, 6)
        self.beto.save()
        esperado = [self.ana.id, self.ana.id, self.beto.id]  # con 0, 1 y 2 calificaciones
        for cantidad in range(3):
            if cantidad:
                solicitud = Solicitud.objects.create(
                    prestador=self.beto, rubro=self.rubro, cliente=self.cliente, cliente_nombre='Cliente',
                    cliente_telefono='900000000', descripcion='Terminado', estado=Solicitud.Estado.FINALIZADA,
                    **CLIENTE)
                r = self.como(self.cliente).post(reverse('calificar'), {'solicitud': solicitud.id, 'estrellas': 5},
                                                  content_type='application/json')
                self.assertEqual(r.status_code, 201, r.content)
                self.client.logout()
            self.assertEqual(self.recomendado()['id'], esperado[cantidad], f'{cantidad} calificaciones')
        self.assertIn('tiene ★ 5,0 con 2 calificaciones', self.recomendado()['motivo'])

    def test_desactivarse_por_hu6_lo_saca_de_la_recomendacion(self):
        self.assertEqual(self.recomendado()['id'], self.ana.id)
        r = self.como(self.ana.usuario).patch(reverse('disponibilidad'), {'disponible': False},
                                              content_type='application/json')
        self.assertEqual(r.status_code, 200, r.content)
        self.client.logout()
        datos = self.recomendacion()
        self.assertEqual(datos['recomendado']['id'], self.beto.id)
        self.assertNotIn(self.ana.id, [a['id'] for a in datos['alternativas']])

    def test_el_primer_ejemplo_del_documento_da_los_puntajes_que_dice(self):
        Prestador.objects.all().delete()
        self.prestador('A', 0.5)
        b = self.prestador('B', 2, calificacion_promedio=Decimal('4.90'), calificaciones_total=25)
        datos = self.recomendacion()
        self.assertEqual((datos['recomendado']['id'], datos['recomendado']['puntaje']), (b.id, 87.8))
        self.assertEqual(datos['alternativas'][0]['puntaje'], 84.4)


class MuestraGrandeTests(Base):
    """300 prestadores ficticios con posiciones, calificaciones y servicios en curso al azar (semilla fija)."""

    @classmethod
    def setUpTestData(cls):
        super().setUpTestData()
        cls.otro_rubro = Rubro.objects.create(nombre='Electricidad T10.4')
        azar = random.Random(2026)
        cliente = User.objects.create_user(username='t104_muestra')
        usuarios = User.objects.bulk_create([User(username=f't104_m{n}') for n in range(300)])
        prestadores = []
        for n, usuario in enumerate(usuarios):
            con_notas = azar.random() < 0.6
            prestadores.append(Prestador(
                usuario=usuario, nombres=f'M{n}', apellidos='Muestra', dni=f'{30000000 + n}',
                latitud=round(CLIENTE['latitud'] + azar.uniform(-0.12, 0.12), 6),
                longitud=round(CLIENTE['longitud'] + azar.uniform(-0.12, 0.12), 6),
                radio_cobertura_km=azar.randint(1, 15), disponible=azar.random() < 0.9,
                estado=Prestador.Estado.VERIFICADO if azar.random() < 0.85 else Prestador.Estado.PENDIENTE,
                calificacion_promedio=Decimal(f'{azar.uniform(1, 5):.2f}') if con_notas else None,
                calificaciones_total=azar.randint(1, 40) if con_notas else 0,
            ))
        prestadores = Prestador.objects.bulk_create(prestadores)
        Relacion = Prestador.rubros.through
        Relacion.objects.bulk_create([
            Relacion(prestador_id=p.id, rubro_id=(cls.rubro if azar.random() < 0.7 else cls.otro_rubro).id)
            for p in prestadores])
        estados = ['pendiente', 'aceptada', 'en_camino', 'en_servicio', 'finalizada', 'rechazada']
        Solicitud.objects.bulk_create([
            Solicitud(prestador=p, rubro=cls.rubro, cliente=cliente, cliente_nombre='Cliente',
                      cliente_telefono='900000000', descripcion='Muestra', estado=azar.choice(estados), **CLIENTE)
            for p in prestadores for _ in range(azar.randint(0, 3))])
        # Puntos de búsqueda: el centro y cuatro alrededor, algunos con pocos resultados.
        cls.puntos = [CLIENTE] + [
            {'latitud': round(CLIENTE['latitud'] + dy, 6), 'longitud': round(CLIENTE['longitud'] + dx, 6)}
            for dy, dx in ((0.1, 0.1), (-0.1, 0.05), (0.03, -0.11), (0.2, 0.2))]

    def en_curso(self, prestador_id):
        return Solicitud.objects.filter(prestador_id=prestador_id, estado__in=EN_CURSO).count()

    def consultar(self, ruta, rubro, punto):
        return self.client.get(reverse(ruta), {'rubro': rubro.id, **punto})

    def test_coincide_con_la_formula_del_documento_en_cada_busqueda(self):
        revisadas = 0
        for rubro in (self.rubro, self.otro_rubro):
            for punto in self.puntos:
                with self.subTest(rubro=rubro.nombre, punto=punto):
                    busqueda = self.consultar('buscar_prestadores', rubro, punto).json()
                    datos = self.consultar('recomendacion', rubro, punto).json()
                    if not busqueda:
                        self.assertEqual(datos, {'recomendado': None, 'alternativas': []})
                        continue
                    revisadas += 1
                    # Lo que se espera según el documento, para todos los de la búsqueda.
                    esperados = []
                    for p in busqueda:
                        en_curso = self.en_curso(p['id'])
                        puntaje = puntaje_segun_documento(
                            p['distancia_km'], p['calificacion_promedio'], p['calificaciones_total'], en_curso)
                        esperados.append((round(puntaje, 1), p['distancia_km'], p['id'], en_curso))
                    esperados.sort(key=lambda e: (-e[0], e[1], e[2]))
                    lista = [datos['recomendado']] + datos['alternativas']
                    self.assertEqual([p['id'] for p in lista], [e[2] for e in esperados[:4]])
                    for p, (puntaje, distancia, _, en_curso) in zip(lista, esperados):
                        self.assertEqual(set(p), CAMPOS_PUBLICOS)  # sin DNI, teléfono ni estado
                        self.assertEqual(p['puntaje'], puntaje)
                        self.assertTrue(0 <= p['puntaje'] <= 100)
                        self.assertEqual(p['servicios_en_curso'], en_curso)
                        self.assertEqual(p['tiempo_estimado_min'], minutos_segun_documento(distancia, en_curso))
                        self.assertTrue(p['motivo'].endswith('.') and p['motivo'][0].isupper())
                    ids_busqueda = {p['id'] for p in busqueda}
                    self.assertTrue({p['id'] for p in lista} <= ids_busqueda)
                    self.assertLessEqual(len(datos['alternativas']), 3)
        self.assertGreaterEqual(revisadas, 6)  # la muestra sí produce resultados en varios puntos

    def test_no_hace_una_consulta_por_prestador(self):
        conteos = {}
        for punto in self.puntos:
            cantidad = len(self.consultar('buscar_prestadores', self.rubro, punto).json())
            if cantidad:  # sin resultados se ahorra la consulta de servicios en curso
                with CaptureQueriesContext(connection) as consultas:
                    self.consultar('recomendacion', self.rubro, punto)
                conteos[cantidad] = len(consultas)
        self.assertGreaterEqual(len(conteos), 2)  # puntos con distinta cantidad de resultados
        self.assertEqual(len(set(conteos.values())), 1, conteos)  # muchas o pocas, mismas consultas

    def test_responde_en_menos_de_3_segundos(self):
        # HU-3: la búsqueda responde en menos de 3 s; la recomendación sale en la misma pantalla.
        inicio = time.perf_counter()
        respuesta = self.consultar('recomendacion', self.rubro, CLIENTE)
        self.assertEqual(respuesta.status_code, 200)
        self.assertLess(time.perf_counter() - inicio, 3.0)
