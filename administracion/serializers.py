import os

from django.utils import timezone
from rest_framework import serializers

from .models import RevisionDocumento, actualizar_estado_prestador

FORMATOS = {'.pdf': 'PDF', '.jpg': 'JPG', '.jpeg': 'JPG'}


class RevisionDocumentoSerializer(serializers.ModelSerializer):
    prestador = serializers.SerializerMethodField()
    tipo_documento = serializers.SerializerMethodField()
    formato = serializers.SerializerMethodField()
    archivo_url = serializers.SerializerMethodField()
    revisor = serializers.StringRelatedField()

    class Meta:
        model = RevisionDocumento
        fields = [
            'id', 'documento', 'prestador', 'tipo_documento', 'formato', 'archivo_url',
            'estado', 'comentario', 'revisor', 'fecha_revision', 'creado',
        ]
        read_only_fields = ['estado', 'comentario', 'revisor', 'fecha_revision', 'creado']

    def get_prestador(self, obj):
        prestador = obj.documento.prestador
        if prestador is None:
            return None
        return {
            'id': prestador.id,
            'nombre': f'{prestador.nombres} {prestador.apellidos}',
            'dni': prestador.dni,
            'estado': prestador.estado,
            'estado_display': prestador.get_estado_display(),
        }

    def get_tipo_documento(self, obj):
        # Por ahora el único documento que se sube es la certificación (HU-2).
        return 'Certificación'

    def get_formato(self, obj):
        archivo = obj.documento.archivo
        if not archivo:
            return None
        return FORMATOS.get(os.path.splitext(archivo.name)[1].lower())

    def get_archivo_url(self, obj):
        # Ruta relativa (/media/...): funciona desde Django y desde el proxy de Vite.
        archivo = obj.documento.archivo
        return archivo.url if archivo else None


class DecisionRevisionSerializer(serializers.ModelSerializer):
    """Aprueba o rechaza un documento; el rechazo exige un comentario."""

    estado = serializers.ChoiceField(
        choices=[RevisionDocumento.Estado.APROBADO, RevisionDocumento.Estado.RECHAZADO],
    )

    class Meta:
        model = RevisionDocumento
        fields = ['estado', 'comentario']
        extra_kwargs = {'comentario': {'required': False}}

    def validate(self, attrs):
        comentario = attrs.get('comentario', '').strip()
        if attrs['estado'] == RevisionDocumento.Estado.RECHAZADO and not comentario:
            raise serializers.ValidationError({'comentario': 'Indica el motivo del rechazo.'})
        attrs['comentario'] = comentario
        return attrs

    def update(self, instance, validated_data):
        instance.estado = validated_data['estado']
        instance.comentario = validated_data['comentario']
        instance.revisor = self.context['request'].user
        instance.fecha_revision = timezone.now()
        instance.save()
        # Mantiene el estado visible de la certificación alineado con la revisión.
        instance.documento.estado = instance.get_estado_display()
        instance.documento.save(update_fields=['estado'])
        actualizar_estado_prestador(instance.documento.prestador)
        return instance
