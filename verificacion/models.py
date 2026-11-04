import os

from django.conf import settings
from django.core.files.storage import FileSystemStorage
from django.db import models

from prestadores.models import Prestador


class AlmacenPrivado(FileSystemStorage):
    """Guarda fuera de MEDIA_ROOT: ninguna ruta pública sirve las fotos del DNI.

    Lee RAIZ_PRIVADA en cada uso, para que las pruebas puedan cambiarla.
    """

    @property
    def base_location(self):
        return settings.RAIZ_PRIVADA

    @property
    def location(self):
        return os.path.abspath(self.base_location)


class IntentoDni(models.Model):
    class Resultado(models.TextChoices):
        VALIDADO = 'validado', 'DNI validado'
        REVISION_MANUAL = 'revision_manual', 'En revisión manual'

    prestador = models.ForeignKey(Prestador, on_delete=models.CASCADE, related_name='intentos_dni')
    resultado = models.CharField(max_length=20, choices=Resultado.choices)
    motivos = models.JSONField(default=list, blank=True)
    # Lo leído de la MRZ (vacío si no se pudo leer).
    dni_leido = models.CharField(max_length=8, blank=True)
    apellidos_leidos = models.CharField(max_length=100, blank=True)
    nombres_leidos = models.CharField(max_length=100, blank=True)
    fecha_nacimiento = models.DateField(null=True, blank=True)
    fecha_vencimiento = models.DateField(null=True, blank=True)
    mrz_valida = models.BooleanField(default=False)
    # Validador externo (T19.3): solo su nombre y si los nombres coincidieron.
    fuente = models.CharField(max_length=30, blank=True)
    estado_fuente = models.CharField(max_length=20, default='no_disponible')
    anverso = models.FileField(upload_to='dni/', storage=AlmacenPrivado(), blank=True)
    reverso = models.FileField(upload_to='dni/', storage=AlmacenPrivado(), blank=True)
    foto_titular = models.FileField(upload_to='dni/', storage=AlmacenPrivado(), blank=True)
    creado = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-creado', '-id']

    def __str__(self):
        return f'Intento de DNI de {self.prestador} - {self.resultado}'
