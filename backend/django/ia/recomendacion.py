"""HU-10: recomendación del prestador (T10.1 fórmula y T10.2 ranking).

Puntaje de 0 a 100 con tres factores, solo sobre los prestadores que ya pasan la búsqueda
(verificados, disponibles, del rubro y con cobertura sobre el cliente):

* Cercanía (50 %): 1 a 0 km y baja en línea recta hasta 0 a DISTANCIA_REFERENCIA_KM o más.
* Calificación (35 %): promedio bayesiano de 1 a 5 estrellas llevado a 0–1. Con pocas
  calificaciones se acerca a CALIFICACION_PREVIA, así un 5★ con una sola opinión no gana
  a un 4,8★ con veinte. Sin calificaciones vale lo mismo que la previa.
* Disponibilidad real (15 %): 1 si no tiene servicios en curso (confirmados, en camino o en
  servicio) y baja con cada uno: 1 / (1 + servicios en curso).

Empates: más cerca primero y luego el ID más bajo, para que el resultado sea estable.
Detalle y ejemplos en docs/API_RECOMENDACION.md.
"""
from dataclasses import dataclass

from django.db.models import Count

from servicios.busqueda import prestadores_en_cobertura
from servicios.models import Solicitud

PESO_DISTANCIA = 0.50
PESO_CALIFICACION = 0.35
PESO_DISPONIBILIDAD = 0.15
DISTANCIA_REFERENCIA_KM = 10.0
CALIFICACION_PREVIA = 3.5  # estrellas que se suponen sin datos
PESO_PREVIA = 3  # cuántas calificaciones «vale» la previa
# Tiempo estimado de llegada: preparación + trayecto urbano + espera por servicios en curso.
MINUTOS_PREPARACION = 10
VELOCIDAD_KM_H = 20.0
MINUTOS_POR_SERVICIO_EN_CURSO = 30

ESTADOS_EN_CURSO = (
    Solicitud.Estado.ACEPTADA, Solicitud.Estado.EN_CAMINO, Solicitud.Estado.EN_SERVICIO,
)


@dataclass(frozen=True)
class Puntaje:
    total: float
    distancia: float
    calificacion: float
    disponibilidad: float
    en_curso: int
    minutos_estimados: int


def factor_distancia(distancia_km):
    return max(0.0, 1.0 - distancia_km / DISTANCIA_REFERENCIA_KM)


def calificacion_ajustada(promedio, total):
    """Promedio bayesiano en estrellas (1 a 5)."""
    total = total or 0
    suma = float(promedio) * total if promedio is not None and total else 0.0
    return (suma + CALIFICACION_PREVIA * PESO_PREVIA) / (total + PESO_PREVIA)


def factor_calificacion(promedio, total):
    return (calificacion_ajustada(promedio, total) - 1) / 4


def factor_disponibilidad(en_curso):
    return 1 / (1 + en_curso)


def minutos_estimados(distancia_km, en_curso):
    trayecto = distancia_km / VELOCIDAD_KM_H * 60
    return round(MINUTOS_PREPARACION + trayecto + MINUTOS_POR_SERVICIO_EN_CURSO * en_curso)


def puntuar(prestador, en_curso):
    distancia = factor_distancia(prestador.distancia_km)
    calificacion = factor_calificacion(prestador.calificacion_promedio, prestador.calificaciones_total)
    disponibilidad = factor_disponibilidad(en_curso)
    total = 100 * (PESO_DISTANCIA * distancia + PESO_CALIFICACION * calificacion
                   + PESO_DISPONIBILIDAD * disponibilidad)
    return Puntaje(
        total=round(total, 1), distancia=distancia, calificacion=calificacion,
        disponibilidad=disponibilidad, en_curso=en_curso,
        minutos_estimados=minutos_estimados(prestador.distancia_km, en_curso),
    )


def _formato_km(km):
    return f'{km:.1f}'.replace('.', ',')


def motivo(prestador, puntaje, es_el_mas_cercano):
    """Explicación corta, en palabras simples, de por qué se recomienda."""
    partes = []
    distancia = f'a {_formato_km(prestador.distancia_km)} km de ti'
    partes.append(f'Es el más cercano ({distancia})' if es_el_mas_cercano else f'Está {distancia}')
    if prestador.calificaciones_total:
        promedio = f'{float(prestador.calificacion_promedio):.1f}'.replace('.', ',')
        opiniones = 'calificación' if prestador.calificaciones_total == 1 else 'calificaciones'
        partes.append(f'tiene ★ {promedio} con {prestador.calificaciones_total} {opiniones}')
    else:
        partes.append('aún no tiene calificaciones')
    if puntaje.en_curso == 0:
        partes.append('no tiene servicios en curso')
    else:
        servicios = 'servicio' if puntaje.en_curso == 1 else 'servicios'
        partes.append(f'tiene {puntaje.en_curso} {servicios} en curso')
    texto = ', '.join(partes[:-1]) + ' y ' + partes[-1]
    return texto[0].upper() + texto[1:] + '.'


def ranking(rubro, latitud, longitud):
    """Prestadores de la búsqueda ordenados por puntaje: [(prestador, puntaje), ...]."""
    prestadores = prestadores_en_cobertura(rubro, latitud, longitud)
    if not prestadores:
        return []
    en_curso = dict(
        Solicitud.objects.filter(prestador__in=prestadores, estado__in=ESTADOS_EN_CURSO)
        .values('prestador').annotate(cantidad=Count('id'))
        .values_list('prestador', 'cantidad')
    )
    puntuados = [(p, puntuar(p, en_curso.get(p.id, 0))) for p in prestadores]
    puntuados.sort(key=lambda par: (-par[1].total, par[0].distancia_km, par[0].id))
    return puntuados
