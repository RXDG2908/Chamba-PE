from django.conf import settings
from django.db import models
from django.utils.crypto import get_random_string


def ruta_foto(perfil, nombre):
    # Nombre nuevo en cada cambio (la caché del navegador no muestra la foto anterior). La foto
    # ya llega procesada a JPEG.
    return f'fotos/usuario-{perfil.usuario_id}-{get_random_string(8)}.jpg'


class Perfil(models.Model):
    class Rol(models.TextChoices):
        CLIENTE = 'cliente', 'Cliente'
        PRESTADOR = 'prestador', 'Prestador'
        ADMINISTRADOR = 'administrador', 'Administrador'

    usuario = models.OneToOneField(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='perfil',
    )
    rol = models.CharField(max_length=13, choices=Rol.choices, default=Rol.CLIENTE)
    # Cuenta de Firebase de la app móvil. La vincula un administrador; vacío = sin acceso móvil.
    firebase_uid = models.CharField(
        'UID de Firebase', max_length=128, unique=True, null=True, blank=True,
        help_text='UID de la cuenta de Firebase Authentication con la que esta persona usa la app móvil.',
    )
    # Perfil editable (cliente y prestador). Con valor por defecto en la base: Spring inserta
    # perfiles sin estas columnas (registro desde la app).
    foto = models.ImageField('foto de perfil', upload_to=ruta_foto, null=True, blank=True)
    telefono = models.CharField(max_length=20, blank=True, default='', db_default='')

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
