from math import isfinite
import re
import unicodedata

from rest_framework import serializers

from prestadores.models import Prestador, Rubro
from prestadores.serializers import RubroSerializer

from .distancias import distancia_km
from .models import Solicitud


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


class SolicitudSerializer(serializers.ModelSerializer):
    """Envío de una solicitud por el cliente (HU-4)."""

    class Meta:
        model = Solicitud
        fields = [
            'id', 'prestador', 'rubro', 'cliente_nombre', 'cliente_telefono',
            'descripcion', 'latitud', 'longitud', 'estado', 'creada',
        ]
        read_only_fields = ['id', 'estado', 'creada']

    cliente_nombre = serializers.CharField(max_length=100, trim_whitespace=False, error_messages={
        'blank': 'Ingresa un nombre con al menos una letra.',
    })
    cliente_telefono = serializers.CharField(trim_whitespace=False, error_messages={
        'blank': 'Ingresa exactamente 9 d\u00edgitos del 0 al 9, sin +51 ni espacios.',
    })
    descripcion = serializers.CharField(max_length=500, trim_whitespace=False, error_messages={
        'blank': 'Describe el trabajo; no lo dejes en blanco.',
    })

    def validate_cliente_telefono(self, valor):
        if not re.fullmatch(r'[0-9]{9}', valor):
            raise serializers.ValidationError('Ingresa exactamente 9 dígitos del 0 al 9, sin +51 ni espacios.')
        return valor

    def validate_cliente_nombre(self, valor):
        nombre = unicodedata.normalize('NFC', valor)
        if not any(c.isalpha() for c in nombre) or not all(
            c.isalpha() or c in " -'’" for c in nombre
        ):
            raise serializers.ValidationError(
                'Ingresa un nombre con al menos una letra; solo letras, espacios, guiones y apóstrofes.'
            )
        return nombre

    def validate_descripcion(self, valor):
        if not valor.strip():
            raise serializers.ValidationError('Describe el trabajo; no uses solo espacios.')
        return valor

    def validate(self, datos):
        prestador = datos['prestador']
        if not prestador.disponible:
            raise serializers.ValidationError({'prestador': 'El prestador no está disponible.'})
        if not prestador.rubros.filter(pk=datos['rubro'].pk).exists():
            raise serializers.ValidationError({'rubro': 'El prestador no ofrece ese rubro.'})
        distancia = distancia_km(
            prestador.latitud, prestador.longitud, datos['latitud'], datos['longitud'],
        )
        # Mismo límite inclusivo que la búsqueda (HU-3).
        if distancia > prestador.radio_cobertura_km + 1e-9:
            raise serializers.ValidationError(
                {'prestador': 'Tu ubicación está fuera del área de cobertura del prestador.'}
            )
        return datos


class EstadoSolicitudSerializer(serializers.ModelSerializer):
    """Lo que ve el cliente al consultar su solicitud; sin datos de contacto."""
    prestador_nombres = serializers.CharField(source='prestador.nombres', read_only=True)
    prestador_apellidos = serializers.CharField(source='prestador.apellidos', read_only=True)
    rubro = serializers.StringRelatedField()
    estado_display = serializers.CharField(source='get_estado_display', read_only=True)

    class Meta:
        model = Solicitud
        fields = [
            'id', 'prestador', 'prestador_nombres', 'prestador_apellidos', 'rubro',
            'estado', 'estado_display', 'creada', 'respondida',
        ]


class SolicitudRecibidaSerializer(serializers.ModelSerializer):
    """Lo que ve el prestador: incluye el contacto del cliente para atenderlo."""
    rubro = serializers.StringRelatedField()

    class Meta:
        model = Solicitud
        fields = [
            'id', 'rubro', 'cliente_nombre', 'cliente_telefono', 'descripcion',
            'latitud', 'longitud', 'estado', 'creada', 'respondida',
        ]
