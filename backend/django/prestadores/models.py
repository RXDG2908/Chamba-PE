from django.conf import settings
from django.core.validators import MaxValueValidator, MinValueValidator, RegexValidator
from django.db import models


class Rubro(models.Model):
    nombre = models.CharField(max_length=60, unique=True)

    class Meta:
        ordering = ['nombre']

    def __str__(self):
        return self.nombre


class Prestador(models.Model):
    class Estado(models.TextChoices):
        PENDIENTE = 'pendiente', 'Pendiente de verificación'
        VERIFICADO = 'verificado', 'Verificado'
        RECHAZADO = 'rechazado', 'Rechazado'

    usuario = models.OneToOneField(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='prestador',
    )
    nombres = models.CharField(max_length=100)
    apellidos = models.CharField(max_length=100)
    dni = models.CharField(
        max_length=8, unique=True,
        validators=[RegexValidator(r'^\d{8}$', 'El DNI debe tener 8 dígitos.')],
    )
    telefono = models.CharField(max_length=20, blank=True)
    rubros = models.ManyToManyField(Rubro, related_name='prestadores')
    # Área de cobertura: círculo con centro (latitud, longitud) y radio en km.
    latitud = models.DecimalField(
        max_digits=9, decimal_places=6,
        validators=[MinValueValidator(-90), MaxValueValidator(90)],
    )
    longitud = models.DecimalField(
        max_digits=9, decimal_places=6,
        validators=[MinValueValidator(-180), MaxValueValidator(180)],
    )
    radio_cobertura_km = models.PositiveSmallIntegerField(
        validators=[MinValueValidator(1), MaxValueValidator(100)],
    )
    disponible = models.BooleanField(default=False)
    estado = models.CharField(max_length=12, choices=Estado.choices, default=Estado.PENDIENTE)
    creado = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name_plural = 'prestadores'

    def __str__(self):
        return f'{self.nombres} {self.apellidos} ({self.dni})'
