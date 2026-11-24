from django.conf import settings
from django.core.validators import MaxValueValidator, MinValueValidator
from django.db import models
from django.db.models import Q

from prestadores.models import Prestador
from servicios.models import Solicitud


class Calificacion(models.Model):
    """Lo que el cliente opina del servicio terminado (HU-12): 1 a 5 estrellas y un comentario."""
    # OneToOne: cada solicitud se califica una sola vez.
    solicitud = models.OneToOneField(Solicitud, on_delete=models.CASCADE, related_name='calificacion')
    prestador = models.ForeignKey(Prestador, on_delete=models.CASCADE, related_name='calificaciones')
    cliente = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='calificaciones',
    )
    estrellas = models.PositiveSmallIntegerField(validators=[MinValueValidator(1), MaxValueValidator(5)])
    comentario = models.CharField(max_length=500, blank=True)
    creada = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-creada']
        verbose_name = 'calificación'
        verbose_name_plural = 'calificaciones'
        constraints = [
            models.CheckConstraint(condition=Q(estrellas__gte=1, estrellas__lte=5), name='estrellas_de_1_a_5'),
        ]

    def __str__(self):
        return f'{self.estrellas}★ a {self.prestador} (solicitud {self.solicitud_id})'
