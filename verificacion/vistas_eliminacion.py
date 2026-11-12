from django.db import DatabaseError
from django.shortcuts import get_object_or_404
from rest_framework import serializers
from rest_framework.authentication import SessionAuthentication
from rest_framework.response import Response
from rest_framework.views import APIView

from usuarios.permisos import EsPrestador
from .eliminacion import SolicitudAbierta, reintentar_eliminacion, solicitar_eliminacion
from .models import SolicitudEliminacionDni


class PeticionEliminacionSerializer(serializers.Serializer):
    clave_idempotencia = serializers.UUIDField()
    confirmo = serializers.BooleanField()

    def to_internal_value(self, data):
        if not isinstance(data, dict) or set(data) != {'clave_idempotencia', 'confirmo'}:
            raise serializers.ValidationError({'detail': 'Envía únicamente clave_idempotencia y confirmo.'})
        return super().to_internal_value(data)

    def validate_confirmo(self, value):
        if self.initial_data.get('confirmo') is not True:
            raise serializers.ValidationError('Debes confirmar explícitamente la eliminación.')
        return value


def solicitud_json(solicitud):
    return {
        'id': str(solicitud.pk), 'estado': solicitud.estado,
        'version_alcance': solicitud.version_alcance, 'alcance': solicitud.alcance,
        'creada': solicitud.creada, 'actualizada': solicitud.actualizada,
        'atendida_en': solicitud.atendida_en, 'ejecuciones': solicitud.ejecuciones,
        'codigo_error': solicitud.codigo_error, 'pendientes': solicitud.pendientes,
        'eventos': list(solicitud.eventos.values(
            'fecha', 'anterior', 'estado', 'ejecucion', 'codigo', 'pendientes')),
    }


class EliminacionesDniView(APIView):
    authentication_classes = [SessionAuthentication]
    permission_classes = [EsPrestador]

    def get(self, request):
        propias = SolicitudEliminacionDni.objects.filter(prestador=request.user.prestador)
        return Response({
            'bloqueada': propias.exclude(estado='atendida').exists(),
            'solicitudes': [solicitud_json(s) for s in propias[:50]],
        })

    def post(self, request):
        datos = PeticionEliminacionSerializer(data=request.data)
        datos.is_valid(raise_exception=True)
        try:
            solicitud, creada = solicitar_eliminacion(request.user.prestador, datos.validated_data['clave_idempotencia'])
        except SolicitudAbierta as e:
            return Response({'detail': 'Ya tienes una solicitud sin resolver.',
                             'solicitud': solicitud_json(e.solicitud)}, status=409)
        except DatabaseError:
            return Response({'detail': 'No pudimos registrar la solicitud. Repite con la misma clave.'}, status=503)
        return Response(solicitud_json(solicitud), status=202 if creada else 200)


class EliminacionDniDetalleView(APIView):
    authentication_classes = [SessionAuthentication]
    permission_classes = [EsPrestador]

    def get(self, request, identificador):
        solicitud = get_object_or_404(SolicitudEliminacionDni, id=identificador, prestador=request.user.prestador)
        return Response(solicitud_json(solicitud))


class ReintentarEliminacionDniView(APIView):
    authentication_classes = [SessionAuthentication]
    permission_classes = [EsPrestador]

    def post(self, request, identificador):
        if request.data:
            raise serializers.ValidationError({'detail': 'El reintento no recibe campos.'})
        get_object_or_404(SolicitudEliminacionDni, id=identificador, prestador=request.user.prestador)
        try:
            solicitud = reintentar_eliminacion(request.user.prestador, identificador)
        except DatabaseError:
            return Response({'detail': 'No pudimos registrar el reintento. Puedes repetirlo.'}, status=503)
        return Response(solicitud_json(solicitud), status=200 if solicitud.estado == 'atendida' else 202)
