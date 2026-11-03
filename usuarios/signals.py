from django.conf import settings
from django.db.models.signals import post_save
from django.dispatch import receiver

from prestadores.models import Prestador

from .models import Perfil


@receiver(post_save, sender=settings.AUTH_USER_MODEL)
def crear_perfil(sender, instance, created, **kwargs):
    # createsuperuser crea la cuenta con is_staff, así que queda como administrador.
    if created:
        rol = Perfil.Rol.ADMINISTRADOR if instance.is_staff else Perfil.Rol.CLIENTE
        Perfil.objects.get_or_create(usuario=instance, defaults={'rol': rol})


@receiver(post_save, sender=Prestador)
def marcar_prestador(sender, instance, created, **kwargs):
    if created:
        perfil, _ = Perfil.objects.update_or_create(
            usuario=instance.usuario, defaults={'rol': Perfil.Rol.PRESTADOR},
        )
        # Para que la misma instancia del usuario ya vea el rol nuevo.
        instance.usuario.perfil = perfil
