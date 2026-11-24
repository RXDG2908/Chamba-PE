"""Mantiene al día el promedio del prestador cuando una calificación desaparece.

Django envía post_delete por cada calificación borrada, también cuando se van en
cascada (al eliminar la solicitud, el cliente o su usuario) o desde el Admin, y lo
hace dentro de la misma transacción del borrado.
"""
from django.db.models.signals import post_delete
from django.dispatch import receiver

from .models import Calificacion
from .servicio import actualizar_promedio


@receiver(post_delete, sender=Calificacion, dispatch_uid='calificaciones_recalcular_al_eliminar')
def recalcular_al_eliminar(sender, instance, **kwargs):
    actualizar_promedio(instance.prestador_id)
