"""Validadores compartidos de datos personales (skill request-validation: lista blanca en el borde).

Los usan el registro de clientes, el de prestadores (web y app) y la solicitud de servicio, para que
un mismo dato tenga la misma regla en todas partes. Se valida y se normaliza; nunca se «arregla» en
silencio algo inválido.
"""
import re
import unicodedata

from rest_framework import serializers

MENSAJE_NOMBRE = 'Ingresa un nombre con al menos una letra; solo letras, espacios, guiones y apóstrofes.'
MENSAJE_TELEFONO = 'Ingresa un teléfono de 9 dígitos (puede empezar con +51).'
_PERMITIDOS_EN_NOMBRE = set(" -'’.")


def nombre_de_persona(valor):
    """Nombres y apellidos: letras de cualquier idioma, espacios, guiones, apóstrofes y puntos.

    Rechaza dígitos, emoji, símbolos, tabuladores, saltos de línea y caracteres invisibles. Normaliza a
    NFC (así «ñ» escrita como «n» + tilde combinada es una sola letra), quita los espacios de los
    extremos y junta los repetidos.
    """
    nombre = re.sub(' {2,}', ' ', unicodedata.normalize('NFC', valor).strip(' '))
    if not any(c.isalpha() for c in nombre) or not all(c.isalpha() or c in _PERMITIDOS_EN_NOMBRE for c in nombre):
        raise serializers.ValidationError(MENSAJE_NOMBRE)
    return nombre


def telefono_peruano(valor):
    """Teléfono del prestador: 9 dígitos, con +51, espacios o guiones opcionales; se guarda sin ellos.

    Vacío se permite (el campo es opcional en el modelo).
    """
    if not valor.strip():
        return ''
    digitos = re.sub(r'[\s-]', '', valor)
    digitos = re.sub(r'^\+?51(?=\d{9}$)', '', digitos)
    if not re.fullmatch(r'\d{9}', digitos):
        raise serializers.ValidationError(MENSAJE_TELEFONO)
    return digitos
