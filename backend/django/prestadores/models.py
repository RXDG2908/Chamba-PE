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
    class Tipo(models.TextChoices):
        INDEPENDIENTE = 'independiente', 'Independiente'
        EMPRESA = 'empresa', 'Empresa'

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
    # HU-12: se recalculan cada vez que un cliente califica; sin calificaciones queda vacío.
    calificacion_promedio = models.DecimalField(max_digits=3, decimal_places=2, null=True, blank=True)
    calificaciones_total = models.PositiveIntegerField(default=0)
    # Perfil: se presenta con su nombre (independiente) o con el de su empresa. Con valor por
    # defecto en la base porque Spring inserta prestadores sin estas columnas (registro de la app).
    tipo = models.CharField(
        max_length=13, choices=Tipo.choices, default=Tipo.INDEPENDIENTE, db_default=Tipo.INDEPENDIENTE,
    )
    nombre_comercial = models.CharField(max_length=120, blank=True, default='', db_default='')
    ruc = models.CharField(
        'RUC', max_length=11, blank=True, default='', db_default='',
        validators=[RegexValidator(r'^(10|15|17|20)\d{9}$', 'El RUC tiene 11 dígitos y empieza con 10, 15, 17 o 20.')],
    )
    descripcion = models.CharField('sobre mí', max_length=500, blank=True, default='', db_default='')

    class Meta:
        verbose_name_plural = 'prestadores'

    def __str__(self):
        return f'{self.nombres} {self.apellidos} ({self.dni})'

    @property
    def nombre_publico(self):
        """Nombre que ven los clientes: el de su empresa o el suyo."""
        if self.tipo == self.Tipo.EMPRESA and self.nombre_comercial:
            return self.nombre_comercial
        return f'{self.nombres} {self.apellidos}'.strip()
