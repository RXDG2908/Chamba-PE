from decimal import ROUND_HALF_UP, Decimal

from django.db import IntegrityError, transaction
from django.db.models import Avg, Count

from prestadores.models import Prestador
from servicios.models import Solicitud

from .models import Calificacion


class NoCalificable(Exception):
    """La solicitud existe y es del cliente, pero hoy no se puede calificar."""


def actualizar_promedio(prestador_id):
    """Recalcula el promedio y el total del prestador desde sus calificaciones guardadas."""
    resumen = Calificacion.objects.filter(prestador_id=prestador_id).aggregate(
        promedio=Avg('estrellas'), total=Count('id'),
    )
    promedio = resumen['promedio']
    if promedio is not None:
        promedio = Decimal(str(promedio)).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
    Prestador.objects.filter(pk=prestador_id).update(
        calificacion_promedio=promedio, calificaciones_total=resumen['total'],
    )


def calificar(cliente, solicitud, estrellas, comentario):
    """Guarda la calificación y actualiza el promedio en la misma transacción."""
    with transaction.atomic():
        # Se vuelve a leer bloqueada para que dos envíos simultáneos no pasen ambos.
        solicitud = Solicitud.objects.select_for_update().get(pk=solicitud.pk)
        if solicitud.estado != Solicitud.Estado.FINALIZADA:
            raise NoCalificable('Solo puedes calificar un servicio Finalizado.')
        if Calificacion.objects.filter(solicitud=solicitud).exists():
            raise NoCalificable('Ya calificaste este servicio.')
        try:
            with transaction.atomic():
                calificacion = Calificacion.objects.create(
                    solicitud=solicitud, prestador_id=solicitud.prestador_id, cliente=cliente,
                    estrellas=estrellas, comentario=comentario,
                )
        except IntegrityError as error:
            raise NoCalificable('Ya calificaste este servicio.') from error
        actualizar_promedio(solicitud.prestador_id)
    return calificacion
