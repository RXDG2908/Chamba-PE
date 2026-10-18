from django.utils import timezone
from rest_framework import serializers

from .models import RevisionDocumento


class RevisionDocumentoSerializer(serializers.ModelSerializer):
    prestador = serializers.SerializerMethodField()
    archivo_url = serializers.SerializerMethodField()
    revisor = serializers.StringRelatedField()

    class Meta:
        model = RevisionDocumento
        fields = [
            'id', 'documento', 'prestador', 'archivo_url', 'estado', 'comentario',
            'revisor', 'fecha_revision', 'creado',
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
        }

    def get_archivo_url(self, obj):
        archivo = obj.documento.archivo
        if not archivo:
            return None
        request = self.context.get('request')
        return request.build_absolute_uri(archivo.url) if request else archivo.url


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
        return instance
