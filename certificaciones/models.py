from django.db import models

class CertificacionPrestador(models.Model):

    latitud = models.FloatField(default=0.0, help_text="Latitud del prestador")
    longitud = models.FloatField(default=0.0, help_text="Longitud del prestador")