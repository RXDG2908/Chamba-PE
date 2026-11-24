from decimal import ROUND_HALF_UP, Decimal

from django.db import IntegrityError, transaction
from django.db.models import Avg, Count

from prestadores.models import Prestador
from servicios.models import Solicitud

from .models import Calificacion


class NoCalificable(Exception):
    """La solicitud existe y es del cliente, pero hoy no se puede calificar."""


def actualizar_promedio(prestador_id):
    """Recalcula el promedio y el total del prestador desde sus calificaciones guardadas.

    Bloquea la fila del prestador hasta el fin de la transacción: si dos clientes califican
    a la vez a un mismo prestador (solicitudes distintas), el segundo espera y su conteo ya
    incluye la calificación del primero. Sin el bloqueo, en PostgreSQL cada uno contaría
    sin ver la del otro y el último en guardar dejaría el total corto. (SQLite no tiene
    FOR UPDATE, pero allí las escrituras ya van de una en una.)
    """
    with transaction.atomic():
        bloqueado = Prestador.objects.select_for_update().filter(pk=prestador_id).values_list('pk', flat=True)
        if not list(bloqueado):
            return  # El prestador ya no existe: no hay promedio que guardar.
        _guardar_promedio(prestador_id)


def _guardar_promedio(prestador_id):
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
