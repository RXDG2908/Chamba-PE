"""HU-13: cómo se aplica una suspensión o un bloqueo en toda la API.

El punto único es `BackendConSanciones`: Django lo usa al iniciar sesión y en cada petición con
sesión, así que una cuenta sancionada no entra y las sesiones que ya tenía abiertas dejan de valer
en la siguiente petición. La app móvil (token de Firebase) pregunta lo mismo con `sancion_vigente`.
"""
from zoneinfo import ZoneInfo

from django.contrib.auth.backends import ModelBackend
from django.db import IntegrityError, transaction
from django.db.models import F
from django.utils import timezone

from prestadores.models import Prestador

from .models import SancionCuenta, sancion_vigente

LIMA = ZoneInfo('America/Lima')


class BackendConSanciones(ModelBackend):
    """ModelBackend que además rechaza las cuentas con una sanción vigente."""

    def user_can_authenticate(self, user):
        return super().user_can_authenticate(user) and sancion_vigente(user.pk) is None


def mensaje_para_la_cuenta(sancion):
    """Lo que ve la persona sancionada al intentar entrar."""
    if sancion.tipo == SancionCuenta.Tipo.BLOQUEO:
        return f'Tu cuenta está bloqueada. Motivo: {sancion.motivo}'
    hasta = timezone.localtime(sancion.hasta, LIMA).strftime('%d/%m/%Y a las %H:%M')
    return f'Tu cuenta está suspendida hasta el {hasta}. Motivo: {sancion.motivo}'


class SancionAbierta(Exception):
    """La cuenta ya tiene una sanción vigente."""


@transaction.atomic
def sancionar(usuario, tipo, motivo, hasta, administrador):
    ahora = timezone.now()
    # Una suspensión que ya venció sigue «abierta» en la base: se cierra en su fecha de fin.
    SancionCuenta.objects.filter(usuario=usuario, levantada__isnull=True, hasta__lte=ahora).update(levantada=F('hasta'))
    try:
        with transaction.atomic():
            sancion = SancionCuenta.objects.create(
                usuario=usuario, tipo=tipo, motivo=motivo, hasta=hasta, creada=ahora, creada_por=administrador,
            )
    except IntegrityError:
        raise SancionAbierta
    # Un prestador sancionado deja de estar disponible: no sale en la búsqueda (Django y Spring)
    # ni recibe solicitudes. Al volver, él mismo vuelve a activarse.
    Prestador.objects.filter(usuario=usuario).update(disponible=False)
    from servicios.models import TokenFCM
    TokenFCM.objects.filter(usuario=usuario).delete()  # sin notificaciones en sus dispositivos
    return sancion


@transaction.atomic
def reactivar(usuario, motivo, administrador):
    """Levanta la sanción abierta; devuelve la sanción levantada o None si no había."""
    sancion = SancionCuenta.objects.select_for_update().filter(usuario=usuario, levantada__isnull=True).first()
    if sancion is None or not sancion.vigente():
        return None
    sancion.levantada = timezone.now()
    sancion.levantada_por = administrador
    sancion.motivo_levantamiento = motivo
    sancion.save(update_fields=['levantada', 'levantada_por', 'motivo_levantamiento'])
    return sancion
