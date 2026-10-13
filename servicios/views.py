from math import isclose

from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from prestadores.models import Prestador

from .distancias import distancia_km
from .serializers import BusquedaPrestadorSerializer, PrestadorBusquedaSerializer


class BuscarPrestadoresView(APIView):
    permission_classes = [AllowAny]
    authentication_classes = []

    def get(self, request):
        parametros = BusquedaPrestadorSerializer(data=request.query_params)
        parametros.is_valid(raise_exception=True)
        datos = parametros.validated_data
        candidatos = Prestador.objects.filter(
            disponible=True, rubros__id=datos['rubro'],
        ).prefetch_related('rubros')
        resultados = []
        for prestador in candidatos:
            distancia = distancia_km(
                prestador.latitud, prestador.longitud, datos['latitud'], datos['longitud'],
            )
            radio = prestador.radio_cobertura_km
            # El límite es inclusivo; tolerancia de 1 micrómetro para redondeo flotante.
            if distancia <= radio or isclose(distancia, radio, rel_tol=0, abs_tol=1e-9):
                prestador.distancia_km = distancia
                resultados.append(prestador)
        # T3.3 incorporará el ordenamiento; aquí solo se filtra y calcula distancia.
        return Response(PrestadorBusquedaSerializer(resultados, many=True).data)
