"""Búsqueda de prestadores (HU-3, HU-7 y HU-9), compartida por la búsqueda y la recomendación (HU-10)."""
from math import isclose

from prestadores.models import Prestador

from .distancias import distancia_km


def prestadores_en_cobertura(rubro, latitud, longitud):
    """Prestadores verificados, disponibles y del rubro cuya cobertura incluye el punto.

    Cada uno lleva `distancia_km` y la lista sale ordenada del más cercano al más lejano.
    """
    candidatos = Prestador.objects.filter(
        disponible=True, estado=Prestador.Estado.VERIFICADO, rubros__id=rubro,
    ).prefetch_related('rubros').order_by('id')  # a igual distancia, por id (Spring hace lo mismo)
    resultados = []
    for prestador in candidatos:
        distancia = distancia_km(prestador.latitud, prestador.longitud, latitud, longitud)
        radio = prestador.radio_cobertura_km
        # El límite es inclusivo; tolerancia de 1 micrómetro para redondeo flotante.
        if distancia <= radio or isclose(distancia, radio, rel_tol=0, abs_tol=1e-9):
            prestador.distancia_km = distancia
            resultados.append(prestador)
    # T3.3: del más cercano al más lejano.
    resultados.sort(key=lambda prestador: prestador.distancia_km)
    return resultados
