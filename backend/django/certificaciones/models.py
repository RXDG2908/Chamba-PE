from django.db import models
from prestadores.models import Prestador  # Importamos el modelo Prestador

class CertificacionPrestador(models.Model):
    # Relación obligatoria para la tarea T2.1
    prestador = models.ForeignKey(Prestador, on_delete=models.CASCADE, related_name='certificaciones', null=True, blank=True)
    
    # Archivo de la certificación
    archivo = models.FileField(upload_to='certificaciones/', blank=True, null=True)
    
    # Estado de la certificación
    estado = models.CharField(max_length=50, default='Pendiente')
    
    # Campos geográficos previos si los necesitas
    latitud = models.FloatField(default=0.0, help_text="Latitud del prestador")
    longitud = models.FloatField(default=0.0, help_text="Longitud del prestador")

    def __str__(self):
        return f"Certificación de {self.prestador} - {self.estado}"