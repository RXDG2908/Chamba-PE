from django.conf import settings
from django.db import models
from django.utils import timezone

from certificaciones.models import CertificacionPrestador
from prestadores.models import Prestador


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


def actualizar_estado_prestador(prestador):
    """
    T9.2: el prestador queda Verificado solo si tiene al menos un documento aprobado.
    Con documentos por revisar sigue Pendiente; si todos fueron rechazados, Rechazado.
    """
    if prestador is None:
        return
    estados = set(
        RevisionDocumento.objects.filter(documento__prestador=prestador).values_list('estado', flat=True)
    )
    if RevisionDocumento.Estado.APROBADO in estados:
        nuevo = Prestador.Estado.VERIFICADO
    elif RevisionDocumento.Estado.PENDIENTE in estados or not estados:
        nuevo = Prestador.Estado.PENDIENTE
    else:
        nuevo = Prestador.Estado.RECHAZADO
    # Se compara en la base: la instancia recibida puede tener un estado desactualizado.
    Prestador.objects.filter(pk=prestador.pk).exclude(estado=nuevo).update(estado=nuevo)
    prestador.estado = nuevo


class SancionCuenta(models.Model):
    """HU-13: suspensión (con fecha de fin) o bloqueo (sin fin) de una cuenta, con su motivo.

    Mientras está vigente la cuenta no puede iniciar sesión (web, admin ni app), sus sesiones
    abiertas dejan de valer y, si es prestador, no aparece en la búsqueda ni recibe solicitudes.
    Nunca se borra: al reactivar la cuenta se marca como levantada y queda en el historial.
    """

    class Tipo(models.TextChoices):
        SUSPENSION = 'suspension', 'Suspensión'
        BLOQUEO = 'bloqueo', 'Bloqueo'

    usuario = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='sanciones')
    tipo = models.CharField(max_length=10, choices=Tipo.choices)
    motivo = models.TextField()
    # Solo la suspensión tiene fin; el bloqueo dura hasta que un administrador lo levante.
    hasta = models.DateTimeField(null=True, blank=True)
    creada = models.DateTimeField(default=timezone.now)
    creada_por = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='sanciones_aplicadas',
    )
    levantada = models.DateTimeField(null=True, blank=True)
    levantada_por = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True, related_name='+',
    )
    motivo_levantamiento = models.TextField(blank=True)

    class Meta:
        ordering = ['-creada', '-id']
        verbose_name = 'sanción de cuenta'
        verbose_name_plural = 'sanciones de cuentas'
        constraints = [
            # Una sola sanción abierta por cuenta, aunque dos administradores actúen a la vez.
            models.UniqueConstraint(
                fields=['usuario'], condition=models.Q(levantada__isnull=True),
                name='una_sancion_abierta_por_cuenta',
            ),
            models.CheckConstraint(
                condition=models.Q(tipo='bloqueo', hasta__isnull=True)
                | models.Q(tipo='suspension', hasta__isnull=False),
                name='solo_la_suspension_tiene_fin',
            ),
        ]

    def __str__(self):
        return f'{self.get_tipo_display()} de {self.usuario}'

    def vigente(self, ahora=None):
        ahora = ahora or timezone.now()
        return self.levantada is None and (self.hasta is None or self.hasta > ahora)


def sanciones_vigentes(ahora=None):
    ahora = ahora or timezone.now()
    return SancionCuenta.objects.filter(levantada__isnull=True).filter(
        models.Q(hasta__isnull=True) | models.Q(hasta__gt=ahora)
    )


def sancion_vigente(usuario_id):
    """La sanción que hoy impide usar la cuenta, o None."""
    return sanciones_vigentes().filter(usuario_id=usuario_id).first()
