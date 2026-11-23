"""Fuentes de donde se consultan los nombres de un DNI (T19.3).

Todas responden igual, así que la verificación no sabe de dónde vienen los datos:

    consultar(dni) → {"estado": "ok", "apellidos": "...", "nombres": "..."}
                   → {"estado": "no_disponible"}
"""
import re
import unicodedata

from django.conf import settings
from django.core.exceptions import ImproperlyConfigured

NO_DISPONIBLE = {'estado': 'no_disponible'}

# DNIs ficticios para desarrollo y pruebas; T19.4 agrega los de las muestras.
TABLA_SIMULADA = {
    '12345678': ('PEREZ GOMEZ', 'JUAN CARLOS'),
    '87654321': ('NUÑEZ RIOS', 'MARIA'),
}


class ValidadorSimulado:
    nombre = 'simulada'

    def consultar(self, dni):
        if dni not in TABLA_SIMULADA:
            return NO_DISPONIBLE
        apellidos, nombres = TABLA_SIMULADA[dni]
        return {'estado': 'ok', 'apellidos': apellidos, 'nombres': nombres}


class ValidadorEldni:
    """Reservado para eldni.com; todavía no consulta nada.

    La página no ofrece una API: es un formulario con token y publicidad, y sus datos
    no son oficiales de RENIEC. Hasta que el PO decida cómo usarla, responde
    "no disponible" y el caso pasa a revisión manual, que es lo seguro.
    """
    nombre = 'eldni'

    def consultar(self, dni):
        return NO_DISPONIBLE


VALIDADORES = {'simulado': ValidadorSimulado, 'eldni': ValidadorEldni}


def obtener_validador():
    nombre = getattr(settings, 'VALIDADOR_DNI', 'simulado')
    if nombre not in VALIDADORES:
        raise ImproperlyConfigured(
            f'VALIDADOR_DNI={nombre!r} no existe. Opciones: {", ".join(VALIDADORES)}.'
        )
    return VALIDADORES[nombre]()


def normalizar(texto):
    """Mayúsculas, sin tildes ni diéresis, Ñ y NXX como N, y espacios simples."""
    texto = texto.upper().replace('NXX', 'N')
    texto = unicodedata.normalize('NFD', texto)
    texto = ''.join(c for c in texto if not unicodedata.combining(c))
    return re.sub(r'\s+', ' ', texto).strip()


def nombres_coinciden(lectura, datos):
    """Apellidos y nombres iguales tras normalizar; nada de "parecidos"."""
    return (normalizar(lectura.apellidos) == normalizar(datos['apellidos'])
            and normalizar(lectura.nombres) == normalizar(datos['nombres']))
