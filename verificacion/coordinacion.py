"""La primera escritura reserva al propietario también en SQLite; sin SELECT FOR UPDATE."""
from django.contrib.auth import get_user_model
from django.db.models import F

from .models import ControlDatosDni


def bloquear_usuario(usuario_id):
    # Debe llamarse dentro de atomic y antes de leer decisiones. No cambia el valor.
    get_user_model().objects.filter(pk=usuario_id).update(last_login=F('last_login'))


def control_propietario(prestador):
    bloquear_usuario(prestador.usuario_id)
    return ControlDatosDni.objects.get_or_create(prestador=prestador)[0]
