from django.conf import settings
from django.db import models


class Perfil(models.Model):
    class Rol(models.TextChoices):
        CLIENTE = 'cliente', 'Cliente'
        PRESTADOR = 'prestador', 'Prestador'
        ADMINISTRADOR = 'administrador', 'Administrador'

    usuario = models.OneToOneField(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='perfil',
    )
    rol = models.CharField(max_length=13, choices=Rol.choices, default=Rol.CLIENTE)

    class Meta:
        verbose_name_plural = 'perfiles'

    def __str__(self):
        return f'{self.usuario.username} ({self.get_rol_display()})'


def rol_de(usuario):
    """Rol de la cuenta; un is_staff sin perfil cuenta como administrador."""
    perfil = getattr(usuario, 'perfil', None)
    if perfil is not None:
        return perfil.rol
    if usuario.is_staff:
        return Perfil.Rol.ADMINISTRADOR
    return None
