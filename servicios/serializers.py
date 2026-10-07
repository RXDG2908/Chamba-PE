from math import isfinite

from rest_framework import serializers

from prestadores.models import Prestador, Rubro
from prestadores.serializers import RubroSerializer


class CoordenadaField(serializers.FloatField):
    def __init__(self, limite, **kwargs):
        self.limite = limite
        super().__init__(error_messages={
            'required': 'Este parámetro es obligatorio.',
            'invalid': 'Ingresa una coordenada numérica válida.',
            'null': 'Este parámetro es obligatorio.',
        }, **kwargs)

    def to_internal_value(self, data):
        valor = super().to_internal_value(data)
        if not isfinite(valor):
            raise serializers.ValidationError('Ingresa una coordenada numérica finita.')
        if not -self.limite <= valor <= self.limite:
            raise serializers.ValidationError(
                f'La coordenada debe estar entre {-self.limite} y {self.limite} grados.'
            )
        return valor


class BusquedaPrestadorSerializer(serializers.Serializer):
    rubro = serializers.IntegerField(min_value=1, error_messages={
        'required': 'El parámetro rubro es obligatorio.',
        'invalid': 'El rubro debe ser un ID entero válido.',
        'min_value': 'El rubro debe ser un ID entero positivo.',
        'null': 'El parámetro rubro es obligatorio.',
        'max_string_length': 'El ID del rubro es demasiado largo.',
    })
    latitud = CoordenadaField(limite=90)
    longitud = CoordenadaField(limite=180)

    def validate_rubro(self, value):
        # Evita desbordamientos al consultar IDs demasiado grandes en SQLite.
        if value > 9223372036854775807 or not Rubro.objects.filter(pk=value).exists():
            raise serializers.ValidationError('No existe un rubro con ese ID.')
        return value


class PrestadorBusquedaSerializer(serializers.ModelSerializer):
    rubros = RubroSerializer(many=True, read_only=True)
    distancia_km = serializers.FloatField(read_only=True)

    class Meta:
        model = Prestador
        fields = ['id', 'nombres', 'apellidos', 'rubros', 'distancia_km']
