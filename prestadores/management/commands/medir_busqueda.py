import time
from decimal import Decimal
from pathlib import Path

from django.conf import settings
from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.test import RequestFactory
from django.urls import resolve
from django.utils import timezone

from prestadores.models import Prestador, Rubro

RUTA = '/api/prestadores/buscar/'
LIMITE_MS = 3000
CENTRO = (Decimal('-12.046374'), Decimal('-77.042793'))
PASO = Decimal('0.004')  # unos 450 m entre puntos de la grilla de prueba


class Command(BaseCommand):
    help = (
        'T3.4: mide GET /api/prestadores/buscar/ con prestadores ficticios y valida que '
        'X-Execution-Time-Ms sea menor a 3000 ms. Los datos se descartan al terminar.'
    )

    def add_arguments(self, parser):
        parser.add_argument('--prestadores', type=int, default=500)
        parser.add_argument('--repeticiones', type=int, default=5)
        parser.add_argument('--salida', default=str(Path(settings.BASE_DIR) / 'evidencia_rendimiento.txt'))

    def handle(self, *args, **opciones):
        cantidad, repeticiones = opciones['prestadores'], opciones['repeticiones']
        if cantidad < 1 or repeticiones < 1:
            raise CommandError('--prestadores y --repeticiones deben ser mayores que 0.')

        with transaction.atomic():
            rubro = self.crear_datos(cantidad)
            parametros = {'rubro': rubro.id, 'latitud': CENTRO[0], 'longitud': CENTRO[1]}
            mediciones = [self.medir(parametros) for _ in range(repeticiones)]
            # Los prestadores ficticios no deben quedar en la base de datos.
            transaction.set_rollback(True)

        tiempos = [ms for ms, _ in mediciones]
        resultados = mediciones[0][1]
        aprobado = max(tiempos) < LIMITE_MS
        consulta = '&'.join(f'{clave}={valor}' for clave, valor in parametros.items())
        reporte = (
            '=== REPORTE DE RENDIMIENTO DE BÚSQUEDA (T3.4) ===\n'
            f'- Fecha: {timezone.localtime():%d/%m/%Y %H:%M %Z}\n'
            f'- Endpoint: GET {RUTA}?{consulta}\n'
            f'- Datos de prueba: {cantidad} prestadores ficticios (descartados al terminar)\n'
            f'- Resultados devueltos: {resultados} (HTTP 200)\n'
            f'- Repeticiones: {repeticiones}\n'
            f'- X-Execution-Time-Ms: mín {min(tiempos):.3f} · máx {max(tiempos):.3f} · '
            f'promedio {sum(tiempos) / len(tiempos):.3f}\n'
            f'- Límite: < {LIMITE_MS} ms (3 segundos)\n'
            f'- Resultado: {"APROBADO" if aprobado else "REPROBADO"}\n'
            '==================================================\n'
        )
        Path(opciones['salida']).write_text(reporte, encoding='utf-8')
        self.stdout.write(reporte)
        if not aprobado:
            raise CommandError(f'La búsqueda tardó {max(tiempos):.3f} ms; el límite es {LIMITE_MS} ms.')
        self.stdout.write(self.style.SUCCESS(f'Evidencia guardada en {opciones["salida"]}.'))

    def crear_datos(self, cantidad):
        """Grilla de prestadores alrededor del centro; solo una parte cubre el punto buscado."""
        rubro = Rubro.objects.create(nombre=f'Rendimiento T3.4 {time.time_ns()}')
        Usuario = get_user_model()
        usuarios = Usuario.objects.bulk_create([
            Usuario(username=f'rendimiento_t34_{rubro.id}_{i}', password='!') for i in range(cantidad)
        ])
        ocupados = set(Prestador.objects.values_list('dni', flat=True))
        dnis = (dni for dni in (f'{n:08d}' for n in range(99_999_999, 0, -1)) if dni not in ocupados)
        prestadores = Prestador.objects.bulk_create([
            Prestador(
                usuario=usuario, dni=next(dnis), nombres='Prueba', apellidos=f'Rendimiento {i}',
                latitud=CENTRO[0] + (i % 20 - 10) * PASO,
                longitud=CENTRO[1] + (i // 20 % 20 - 10) * PASO,
                radio_cobertura_km=5, disponible=True, estado=Prestador.Estado.VERIFICADO,
            )
            for i, usuario in enumerate(usuarios)
        ])
        Prestador.rubros.through.objects.bulk_create([
            Prestador.rubros.through(prestador_id=prestador.id, rubro_id=rubro.id) for prestador in prestadores
        ])
        return rubro

    def medir(self, parametros):
        """Llama a la vista registrada en la ruta real y lee el tiempo que ella misma reporta."""
        respuesta = resolve(RUTA).func(RequestFactory().get(RUTA, parametros))
        respuesta.render()
        if respuesta.status_code != 200:
            raise CommandError(f'La búsqueda respondió HTTP {respuesta.status_code}: {respuesta.content!r}')
        if 'X-Execution-Time-Ms' not in respuesta:
            raise CommandError('La búsqueda no devolvió la cabecera X-Execution-Time-Ms.')
        return float(respuesta['X-Execution-Time-Ms']), len(respuesta.data)
