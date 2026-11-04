"""Pasos 2 a 8 del contrato: lee la MRZ, la compara con el prestador y con la fuente externa."""
from datetime import date

from .mrz import ErrorMrz, interpretar
from .validadores import nombres_coinciden, obtener_validador


def analizar(anverso, reverso, prestador, lector):
    """Devuelve (lectura o None, foto del titular o None, motivos, fuente).

    fuente es {"nombre": ..., "estado": coincide | no_coincide | no_disponible}.
    """
    motivos = []
    lectura = None
    fuente = {'nombre': '', 'estado': 'no_disponible'}
    try:
        lectura = interpretar(lector.leer_textos(reverso))
    except ErrorMrz as error:
        motivos.append(error.motivo)
    except Exception:
        # El OCR no está instalado o falló: no es culpa del prestador, lo revisa un administrador.
        motivos.append('lector_no_disponible')

    if lectura:
        if lectura.dni != prestador.dni:
            motivos.append('dni_distinto')
        if lectura.fecha_vencimiento < date.today():
            motivos.append('dni_vencido')

    try:
        foto = lector.recortar_titular(anverso)
    except Exception:
        foto = None
    if foto is None:
        motivos.append('sin_foto')

    # Solo se consulta si hay un DNI válido y es el del prestador: si no, ya va a revisión.
    if lectura and 'dni_distinto' not in motivos:
        validador = obtener_validador()
        try:
            datos = validador.consultar(lectura.dni)
        except Exception:
            datos = {'estado': 'no_disponible'}
        fuente['nombre'] = validador.nombre
        if datos['estado'] != 'ok':
            motivos.append('fuente_no_disponible')
        elif nombres_coinciden(lectura, datos):
            fuente['estado'] = 'coincide'
        else:
            fuente['estado'] = 'no_coincide'
            motivos.append('nombres_distintos')

    return lectura, foto, motivos, fuente


def hay_revision_manual(motivos):
    """Todo motivo salvo el informativo 'sin_foto' manda el caso a revisión."""
    return any(m != 'sin_foto' for m in motivos)
