from rest_framework import serializers
from .models import CertificacionPrestador

class CertificacionSerializer(serializers.ModelSerializer):
    class Meta:
        model = CertificacionPrestador
        fields = '__all__'