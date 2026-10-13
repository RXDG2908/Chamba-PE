from math import asin, cos, radians, sin, sqrt


RADIO_TIERRA_KM = 6371.0088


def distancia_km(latitud_origen, longitud_origen, latitud_destino, longitud_destino):
    """Distancia geográfica Haversine en km; acepta float o Decimal."""
    lat1, lon1, lat2, lon2 = map(
        lambda valor: radians(float(valor)),
        (latitud_origen, longitud_origen, latitud_destino, longitud_destino),
    )
    a = sin((lat2 - lat1) / 2) ** 2 + cos(lat1) * cos(lat2) * sin((lon2 - lon1) / 2) ** 2
    # Evita errores de dominio por redondeo en puntos antípodas.
    return 2 * RADIO_TIERRA_KM * asin(sqrt(max(0.0, min(1.0, a))))
