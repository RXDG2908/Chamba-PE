from django.core.validators import MaxValueValidator, MinValueValidator
from django.db import models
from django.conf import settings

from prestadores.models import Prestador, Rubro


class TokenFCM(models.Model):
    usuario = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    token = models.CharField(max_length=2048, unique=True)
    sesion = models.CharField(max_length=40)
    actualizado = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f'Dispositivo {self.pk}'


class Solicitud(models.Model):
    class Estado(models.TextChoices):
        PENDIENTE = 'pendiente', 'Pendiente'
        # HU-11: «aceptada» se muestra como «Confirmado», la primera etapa del seguimiento.
        ACEPTADA = 'aceptada', 'Confirmado'
        RECHAZADA = 'rechazada', 'Rechazada'
        EN_CAMINO = 'en_camino', 'En camino'
        EN_SERVICIO = 'en_servicio', 'En servicio'
        FINALIZADA = 'finalizada', 'Finalizada'

    prestador = models.ForeignKey(Prestador, on_delete=models.CASCADE, related_name='solicitudes')
    rubro = models.ForeignKey(Rubro, on_delete=models.PROTECT, related_name='solicitudes')
    # Cuenta del cliente si envió la solicitud con sesión (HU-12: solo él puede calificarla).
    cliente = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='solicitudes_enviadas',
    )
    # Datos de contacto del cliente, con o sin cuenta.
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
    estado = models.CharField(max_length=20, choices=Estado.choices, default=Estado.PENDIENTE)
    creada = models.DateTimeField(auto_now_add=True)
    respondida = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ['-creada']
        verbose_name_plural = 'solicitudes'

    def __str__(self):
        return f'Solicitud {self.id} a {self.prestador} ({self.get_estado_display()})'

    # HU-11: el prestador asignado avanza el servicio solo en este orden, sin saltar ni retroceder.
    # «rechazada» y «finalizada» son finales; «pendiente» se responde con aceptar/rechazar (HU-4).
    SEGUIMIENTO = {
        Estado.ACEPTADA: Estado.EN_CAMINO,
        Estado.EN_CAMINO: Estado.EN_SERVICIO,
        Estado.EN_SERVICIO: Estado.FINALIZADA,
    }

    @property
    def siguiente_estado(self):
        """Estado al que el prestador puede pasar la solicitud ahora, o None si no puede avanzarla."""
        return self.SEGUIMIENTO.get(self.estado)


class CambioEstado(models.Model):
    """Historial de la solicitud (HU-11): cada cambio de estado con su fecha y hora."""
    solicitud = models.ForeignKey(Solicitud, on_delete=models.CASCADE, related_name='historial')
    estado = models.CharField(max_length=20, choices=Solicitud.Estado.choices)
    fecha = models.DateTimeField()

    class Meta:
        ordering = ['fecha', 'id']
        verbose_name = 'cambio de estado'
        verbose_name_plural = 'cambios de estado'

    def __str__(self):
        return f'Solicitud {self.solicitud_id}: {self.get_estado_display()}'


class Mensaje(models.Model):
    """Chat de la solicitud: el cliente y el prestador coordinan desde que se acepta."""
    solicitud = models.ForeignKey(Solicitud, on_delete=models.CASCADE, related_name='mensajes')
    autor = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='mensajes_enviados')
    texto = models.CharField(max_length=500)
    enviado = models.DateTimeField()

    # Se escribe en Confirmado, En camino y En servicio; al finalizar queda solo para leer.
    ESTADOS_ACTIVOS = (Solicitud.Estado.ACEPTADA, Solicitud.Estado.EN_CAMINO, Solicitud.Estado.EN_SERVICIO)
    ESTADOS_CON_CHAT = ESTADOS_ACTIVOS + (Solicitud.Estado.FINALIZADA,)

    class Meta:
        ordering = ['enviado', 'id']

    def __str__(self):
        return f'Mensaje {self.pk} de la solicitud {self.solicitud_id}'
