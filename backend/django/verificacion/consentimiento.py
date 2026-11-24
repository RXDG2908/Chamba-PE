"""Consentimiento explícito para DNI; no habilita reconocimiento facial."""
from django.db import transaction
from django.utils import timezone

from .models import Consentimiento, SolicitudEliminacionDni, ControlDatosDni
from .coordinacion import bloquear_usuario
from .textos_consentimiento import FINALIDAD_DNI, TEXTO_DNI, VERSION_DNI


def aceptacion_vigente(usuario_id):
    return Consentimiento.objects.filter(
        usuario_id=usuario_id, finalidad=FINALIDAD_DNI, version_texto=VERSION_DNI,
        revocado_en__isnull=True, aceptado_en__lte=timezone.now(),
    ).first()


def tiene_consentimiento(prestador):
    usuario_id = getattr(prestador, 'usuario_id', None)
    return bool(usuario_id and aceptacion_vigente(usuario_id))


def estado_consentimiento(usuario):
    vigente = aceptacion_vigente(usuario.pk)
    ultimo = vigente or Consentimiento.objects.filter(
        usuario=usuario, finalidad=FINALIDAD_DNI,
    ).first()
    estado = 'ausente'
    if vigente:
        estado = 'vigente'
    elif ultimo:
        estado = 'revocado' if ultimo.revocado_en else 'desactualizado'
    return {
        'finalidad': FINALIDAD_DNI,
        'version_texto': VERSION_DNI,
        'texto': TEXTO_DNI,
        'provisional': True,
        'vigente': bool(vigente),
        'estado': estado,
        'aceptado_en': ultimo.aceptado_en if ultimo else None,
        'revocado_en': ultimo.revocado_en if ultimo else None,
        'eliminacion_pendiente': SolicitudEliminacionDni.objects.filter(
            prestador__usuario=usuario).exclude(estado='atendida').exists(),
    }


@transaction.atomic
def aceptar_consentimiento(usuario):
    # Primera escritura compartida con captura/eliminación; también reserva en SQLite.
    bloquear_usuario(usuario.pk)
    _, creado = Consentimiento.objects.get_or_create(
        usuario=usuario, finalidad=FINALIDAD_DNI, version_texto=VERSION_DNI,
        revocado_en=None, defaults={'texto_aceptado': TEXTO_DNI},
    )
    return creado


@transaction.atomic
def revocar_consentimiento(usuario):
    bloquear_usuario(usuario.pk)
    modificados = Consentimiento.objects.filter(
        usuario=usuario, finalidad=FINALIDAD_DNI, revocado_en__isnull=True,
    ).update(revocado_en=timezone.now())
    if modificados:
        from django.db.models import F
        ControlDatosDni.objects.filter(prestador__usuario=usuario).update(generacion=F('generacion') + 1)
