from django.db.models.signals import post_save
from django.dispatch import receiver

from certificaciones.models import CertificacionPrestador

from .models import RevisionDocumento


@receiver(post_save, sender=CertificacionPrestador)
def crear_revision_pendiente(sender, instance, created, **kwargs):
    """Todo documento nuevo entra a la cola de revisión como pendiente."""
    if created:
        RevisionDocumento.objects.get_or_create(documento=instance)
