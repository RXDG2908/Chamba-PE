from rest_framework import serializers

from servicios.models import Solicitud

from .models import Calificacion


class EnteroEstricto(serializers.IntegerField):
    """Solo acepta enteros JSON: rechaza "5", 4.5, true y listas."""

    def to_internal_value(self, data):
        if isinstance(data, bool) or not isinstance(data, int):
            self.fail('invalid')
        return super().to_internal_value(data)


class CalificarSerializer(serializers.Serializer):
    solicitud = EnteroEstricto(min_value=1, error_messages={
        'invalid': 'Indica el número de la solicitud.',
        'required': 'Indica qué solicitud calificas.',
    })
    estrellas = EnteroEstricto(min_value=1, max_value=5, error_messages={
        'invalid': 'Elige de 1 a 5 estrellas.',
        'required': 'Elige de 1 a 5 estrellas.',
        'min_value': 'Elige de 1 a 5 estrellas.',
        'max_value': 'Elige de 1 a 5 estrellas.',
    })
    comentario = serializers.CharField(
        max_length=500, required=False, allow_blank=True, default='',
        error_messages={'max_length': 'El comentario admite hasta 500 caracteres.'},
    )


class CalificacionSerializer(serializers.ModelSerializer):
    class Meta:
        model = Calificacion
        fields = ['estrellas', 'comentario', 'creada']


class MiServicioSerializer(serializers.ModelSerializer):
    """Lo que el cliente ve de cada solicitud que envió con su cuenta."""
    prestador = serializers.SerializerMethodField()
    rubro = serializers.StringRelatedField()
    estado_display = serializers.CharField(source='get_estado_display', read_only=True)
    calificacion = serializers.SerializerMethodField()
    puede_calificar = serializers.SerializerMethodField()

    class Meta:
        model = Solicitud
        fields = [
            'id', 'prestador', 'rubro', 'descripcion', 'estado', 'estado_display',
            'creada', 'calificacion', 'puede_calificar',
        ]

    def get_prestador(self, solicitud):
        prestador = solicitud.prestador
        return {'id': prestador.id, 'nombre': f'{prestador.nombres} {prestador.apellidos}'}

    def _calificacion(self, solicitud):
        return getattr(solicitud, 'calificacion', None)

    def get_calificacion(self, solicitud):
        calificacion = self._calificacion(solicitud)
        return CalificacionSerializer(calificacion).data if calificacion else None

    def get_puede_calificar(self, solicitud):
        return solicitud.estado == Solicitud.Estado.FINALIZADA and self._calificacion(solicitud) is None
