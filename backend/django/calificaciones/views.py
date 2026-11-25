from django.shortcuts import get_object_or_404
from rest_framework import generics, status
from rest_framework.response import Response
from rest_framework.views import APIView

from servicios.models import Solicitud
from usuarios.permisos import EsCliente

from .serializers import CalificacionSerializer, CalificarSerializer, MiServicioSerializer
from .servicio import NoCalificable, calificar


class MisServiciosView(generics.ListAPIView):
    """Solicitudes que el cliente envió con su cuenta, con su calificación si ya la dio."""
    serializer_class = MiServicioSerializer
    permission_classes = [EsCliente]
    pagination_class = None

    def get_queryset(self):
        return (
            Solicitud.objects.filter(cliente=self.request.user)
            .select_related('prestador', 'rubro', 'calificacion')
            .prefetch_related('historial')
        )


class CalificarView(APIView):
    """El cliente califica una solicitud suya ya Finalizada, una sola vez (HU-12)."""
    permission_classes = [EsCliente]

    def post(self, request):
        datos = CalificarSerializer(data=request.data)
        datos.is_valid(raise_exception=True)
        datos = datos.validated_data
        # Una solicitud de otro cliente, o anónima, se responde igual que una inexistente.
        solicitud = get_object_or_404(Solicitud, pk=datos['solicitud'], cliente=request.user)
        try:
            calificacion = calificar(
                request.user, solicitud, datos['estrellas'], datos['comentario'].strip(),
            )
        except NoCalificable as motivo:
            return Response({'detail': str(motivo)}, status=status.HTTP_409_CONFLICT)
        prestador = calificacion.prestador
        prestador.refresh_from_db(fields=['calificacion_promedio', 'calificaciones_total'])
        return Response({
            'solicitud': solicitud.pk,
            'calificacion': CalificacionSerializer(calificacion).data,
            'prestador': {
                'id': prestador.pk,
                'calificacion_promedio': float(prestador.calificacion_promedio),
                'calificaciones_total': prestador.calificaciones_total,
            },
        }, status=status.HTTP_201_CREATED)
