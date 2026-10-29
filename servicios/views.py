import time
from math import isclose

from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import generics, status
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from prestadores.models import Prestador
from usuarios.permisos import EsPrestador

from .distancias import distancia_km
from .models import Solicitud
from .serializers import (
    BusquedaPrestadorSerializer, EstadoSolicitudSerializer, PrestadorBusquedaSerializer,
    SolicitudRecibidaSerializer, SolicitudSerializer,
)


class BuscarPrestadoresView(APIView):
    permission_classes = [AllowAny]
    authentication_classes = []

    def get(self, request):
        inicio = time.perf_counter()
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
        
        # --- TAREA T3.3: ORDENAR POR DISTANCIA (del más cercano al más lejano) ---
        resultados.sort(key=lambda x: x.distancia_km)

        respuesta = Response(PrestadorBusquedaSerializer(resultados, many=True).data)
        respuesta['X-Execution-Time-Ms'] = f'{(time.perf_counter() - inicio) * 1000:.3f}'
        return respuesta


class CrearSolicitudView(generics.CreateAPIView):
    """El cliente envía una solicitud a un prestador; queda Pendiente."""
    serializer_class = SolicitudSerializer
    permission_classes = [AllowAny]
    authentication_classes = []


class DetalleSolicitudView(generics.RetrieveAPIView):
    """El cliente consulta su solicitud para ver el cambio de estado."""
    queryset = Solicitud.objects.select_related('prestador', 'rubro')
    serializer_class = EstadoSolicitudSerializer
    permission_classes = [AllowAny]
    authentication_classes = []


class SolicitudesRecibidasView(generics.ListAPIView):
    """Bandeja del prestador con las solicitudes que le enviaron."""
    serializer_class = SolicitudRecibidaSerializer
    permission_classes = [EsPrestador]
    pagination_class = None

    def get_queryset(self):
        return self.request.user.prestador.solicitudes.select_related('rubro')


class ResponderSolicitudView(APIView):
    """Base para aceptar o rechazar; solo el prestador dueño y solo si sigue Pendiente."""
    permission_classes = [EsPrestador]
    nuevo_estado = None

    def post(self, request, pk):
        solicitud = get_object_or_404(Solicitud, pk=pk, prestador=request.user.prestador)
        if solicitud.estado != Solicitud.Estado.PENDIENTE:
            return Response(
                {'estado': f'La solicitud ya fue {solicitud.get_estado_display().lower()}.'},
                status=status.HTTP_409_CONFLICT,
            )
        solicitud.estado = self.nuevo_estado
        solicitud.respondida = timezone.now()
        solicitud.save(update_fields=['estado', 'respondida'])
        return Response(SolicitudRecibidaSerializer(solicitud).data)


class AceptarSolicitudView(ResponderSolicitudView):
    nuevo_estado = Solicitud.Estado.ACEPTADA


class RechazarSolicitudView(ResponderSolicitudView):
    nuevo_estado = Solicitud.Estado.RECHAZADA