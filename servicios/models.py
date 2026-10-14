from django.core.validators import MaxValueValidator, MinValueValidator
from django.db import models

from prestadores.models import Prestador, Rubro


class Solicitud(models.Model):
    class Estado(models.TextChoices):
        PENDIENTE = 'pendiente', 'Pendiente'
        ACEPTADA = 'aceptada', 'Aceptada'
        RECHAZADA = 'rechazada', 'Rechazada'

    prestador = models.ForeignKey(Prestador, on_delete=models.CASCADE, related_name='solicitudes')
    rubro = models.ForeignKey(Rubro, on_delete=models.PROTECT, related_name='solicitudes')
    # Datos de contacto del cliente; con el inicio de sesión (HU-5) se enlazará a su cuenta.
    cliente_nombre = models.CharField(max_length=100)
    cliente_telefono = models.CharField(max_length=20)
    descripcion = models.CharField(max_length=500)
    # Ubicación del cliente, donde se presta el servicio.
    latitud = models.DecimalField(
        max_digits=9, decimal_places=6,
        validators=[MinValueValidator(-90), MaxValueValidator(90)],
    )
    longitud = models.DecimalField(
        max_digits=9, decimal_places=6,
        validators=[MinValueValidator(-180), MaxValueValidator(180)],
    )
    estado = models.CharField(max_length=10, choices=Estado.choices, default=Estado.PENDIENTE)
    creada = models.DateTimeField(auto_now_add=True)
    respondida = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ['-creada']
        verbose_name_plural = 'solicitudes'

    def __str__(self):
        return f'Solicitud {self.id} a {self.prestador} ({self.get_estado_display()})'
