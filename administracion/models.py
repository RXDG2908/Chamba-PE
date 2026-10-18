from django.conf import settings
from django.db import models

from certificaciones.models import CertificacionPrestador


class RevisionDocumento(models.Model):
    """T9.1: revisión manual de un documento subido por un prestador."""

    class Estado(models.TextChoices):
        PENDIENTE = 'pendiente', 'Pendiente'
        APROBADO = 'aprobado', 'Aprobado'
        RECHAZADO = 'rechazado', 'Rechazado'

    documento = models.OneToOneField(
        CertificacionPrestador, on_delete=models.CASCADE, related_name='revision',
    )
    estado = models.CharField(max_length=10, choices=Estado.choices, default=Estado.PENDIENTE)
    comentario = models.TextField(blank=True)
    revisor = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL,
        null=True, blank=True, related_name='revisiones_documentos',
    )
    fecha_revision = models.DateTimeField(null=True, blank=True)
    creado = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-creado', '-id']
        verbose_name = 'revisión de documento'
        verbose_name_plural = 'revisiones de documentos'

    def __str__(self):
        return f'Revisión de {self.documento} - {self.get_estado_display()}'
