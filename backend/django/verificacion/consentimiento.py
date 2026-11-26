"""Consentimiento explícito por finalidad: DNI (HU-21) y comparación facial (HU-20), por separado."""
from django.db import transaction
from django.utils import timezone

from .models import Consentimiento, SolicitudEliminacionDni, ControlDatosDni
from .coordinacion import bloquear_usuario
from .textos_consentimiento import (
    FINALIDAD_DNI, FINALIDAD_FACIAL, TEXTO_DNI, TEXTO_FACIAL, VERSION_DNI, VERSION_FACIAL,
)

# Versión y texto vigentes de cada finalidad.
VIGENTES = {FINALIDAD_DNI: (VERSION_DNI, TEXTO_DNI), FINALIDAD_FACIAL: (VERSION_FACIAL, TEXTO_FACIAL)}


def aceptacion_vigente(usuario_id, finalidad=FINALIDAD_DNI):
    return Consentimiento.objects.filter(
        usuario_id=usuario_id, finalidad=finalidad, version_texto=VIGENTES[finalidad][0],
        revocado_en__isnull=True, aceptado_en__lte=timezone.now(),
    ).first()


def tiene_consentimiento(prestador, finalidad=FINALIDAD_DNI):
    usuario_id = getattr(prestador, 'usuario_id', None)
    return bool(usuario_id and aceptacion_vigente(usuario_id, finalidad))


def estado_consentimiento(usuario, finalidad=FINALIDAD_DNI):
    version, texto = VIGENTES[finalidad]
    vigente = aceptacion_vigente(usuario.pk, finalidad)
    ultimo = vigente or Consentimiento.objects.filter(
        usuario=usuario, finalidad=finalidad,
    ).first()
    estado = 'ausente'
    if vigente:
        estado = 'vigente'
    elif ultimo:
        estado = 'revocado' if ultimo.revocado_en else 'desactualizado'
    respuesta = {
        'finalidad': finalidad,
        'version_texto': version,
        'texto': texto,
        'provisional': True,
        'vigente': bool(vigente),
        'estado': estado,
        'aceptado_en': ultimo.aceptado_en if ultimo else None,
        'revocado_en': ultimo.revocado_en if ultimo else None,
    }
    if finalidad == FINALIDAD_DNI:
        respuesta['eliminacion_pendiente'] = SolicitudEliminacionDni.objects.filter(
            prestador__usuario=usuario).exclude(estado='atendida').exists()
    return respuesta


@transaction.atomic
def aceptar_consentimiento(usuario, finalidad=FINALIDAD_DNI):
    # Primera escritura compartida con captura/eliminación; también reserva en SQLite.
    bloquear_usuario(usuario.pk)
    version, texto = VIGENTES[finalidad]
    _, creado = Consentimiento.objects.get_or_create(
        usuario=usuario, finalidad=finalidad, version_texto=version,
        revocado_en=None, defaults={'texto_aceptado': texto},
    )
    return creado


@transaction.atomic
def revocar_consentimiento(usuario, finalidad=FINALIDAD_DNI):
    bloquear_usuario(usuario.pk)
    modificados = Consentimiento.objects.filter(
        usuario=usuario, finalidad=finalidad, revocado_en__isnull=True,
    ).update(revocado_en=timezone.now())
    if modificados:
        from django.db.models import F
        ControlDatosDni.objects.filter(prestador__usuario=usuario).update(generacion=F('generacion') + 1)
