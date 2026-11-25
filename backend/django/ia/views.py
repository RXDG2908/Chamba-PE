from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from servicios.serializers import BusquedaPrestadorSerializer, PrestadorBusquedaSerializer

from .recomendacion import motivo, ranking

MAXIMO_ALTERNATIVAS = 3


def _detalle(prestador, puntaje, es_el_mas_cercano):
    return {
        **PrestadorBusquedaSerializer(prestador).data,
        'puntaje': puntaje.total,
        'tiempo_estimado_min': puntaje.minutos_estimados,
        'servicios_en_curso': puntaje.en_curso,
        'motivo': motivo(prestador, puntaje, es_el_mas_cercano),
    }


class RecomendacionView(APIView):
    """HU-10: GET /api/ia/recomendacion/?rubro=&latitud=&longitud= (mismos parámetros que la búsqueda).

    Devuelve el prestador recomendado (o null si no hay ninguno con cobertura) y hasta tres
    alternativas, cada uno con su puntaje, motivo y tiempo estimado de llegada.
    """
    permission_classes = [AllowAny]
    authentication_classes = []

    def get(self, request):
        parametros = BusquedaPrestadorSerializer(data=request.query_params)
        parametros.is_valid(raise_exception=True)
        datos = parametros.validated_data
        puntuados = ranking(datos['rubro'], datos['latitud'], datos['longitud'])
        if not puntuados:
            return Response({'recomendado': None, 'alternativas': []})
        mas_cercano = min(p.distancia_km for p, _ in puntuados)
        detalles = [_detalle(p, s, p.distancia_km == mas_cercano) for p, s in puntuados]
        return Response({'recomendado': detalles[0], 'alternativas': detalles[1:1 + MAXIMO_ALTERNATIVAS]})
