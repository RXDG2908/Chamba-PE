from collections.abc import Mapping

from rest_framework import serializers

from .textos_consentimiento import VERSION_DNI


class AceptacionConsentimientoSerializer(serializers.Serializer):
    acepto = serializers.BooleanField()
    version_texto = serializers.CharField(max_length=40)

    def to_internal_value(self, data):
        if isinstance(data, Mapping):
            desconocidos = set(data) - set(self.fields)
            if desconocidos:
                raise serializers.ValidationError({
                    campo: ['Este campo no se puede enviar.'] for campo in sorted(desconocidos)
                })
        return super().to_internal_value(data)

    def validate_acepto(self, value):
        if self.initial_data.get('acepto') is not True:
            raise serializers.ValidationError('Debes aceptar explícitamente el texto mostrado.')
        return value

    def validate_version_texto(self, value):
        if value != VERSION_DNI:
            raise serializers.ValidationError(
                'El texto cambió. Consulta la versión vigente antes de aceptar.'
            )
        return value
