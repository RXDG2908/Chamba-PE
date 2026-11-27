import time

from django.http import Http404
from django.shortcuts import get_object_or_404
from django.utils import timezone
from django.db import transaction
from rest_framework import generics, status
from rest_framework.exceptions import PermissionDenied
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from usuarios.permisos import EsCliente, EsPrestador

from .busqueda import prestadores_en_cobertura
from .codigo_seguimiento import puede_ver
from .models import CambioEstado, Solicitud
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
        resultados = prestadores_en_cobertura(datos['rubro'], datos['latitud'], datos['longitud'])
        respuesta = Response(PrestadorBusquedaSerializer(resultados, many=True).data)
        respuesta['X-Execution-Time-Ms'] = f'{(time.perf_counter() - inicio) * 1000:.3f}'
        return respuesta


class CrearSolicitudView(generics.CreateAPIView):
    """El cliente envía una solicitud a un prestador; queda Pendiente.

    Solo un cliente con sesión (web) o token de la app: un visitante sin cuenta, un prestador o un
    administrador reciben 403, como el resto de rutas privadas. La solicitud queda a su nombre.
    """
    serializer_class = SolicitudSerializer
    permission_classes = [EsCliente]

    def check_permissions(self, request):
        # HU-13: la sesión de una cuenta sancionada ya no autentica, pero se reconoce por su id
        # en la sesión: se explica el motivo en vez del mensaje genérico de «sin sesión».
        from django.contrib.auth import SESSION_KEY, get_user_model
        from administracion.models import sancion_vigente
        from administracion.sanciones import mensaje_para_la_cuenta
        id_en_sesion = request.session.get(SESSION_KEY) if hasattr(request, 'session') else None
        if id_en_sesion and not request.user.is_authenticated:
            sancion = sancion_vigente(get_user_model()._meta.pk.to_python(id_en_sesion))
            if sancion is not None:
                raise PermissionDenied(mensaje_para_la_cuenta(sancion))
        super().check_permissions(request)

    def perform_create(self, serializer):
        from .push import notificar_solicitud
        with transaction.atomic():
            solicitud = serializer.save(cliente=self.request.user)
            transaction.on_commit(lambda: notificar_solicitud(solicitud.pk))


class DetalleSolicitudView(generics.RetrieveAPIView):
    """El cliente consulta su solicitud para ver el cambio de estado.

    Solo la ven el cliente dueño (con su sesión o, si la envió sin cuenta, con su código de
    seguimiento) y el prestador asignado. Para cualquier otro es 404, como si no existiera.
    """
    queryset = Solicitud.objects.select_related('prestador', 'rubro').prefetch_related('historial')
    serializer_class = EstadoSolicitudSerializer
    permission_classes = [AllowAny]

    def get_object(self):
        solicitud = super().get_object()
        if not puede_ver(solicitud, self.request):
            raise Http404
        return solicitud

    def finalize_response(self, request, response, *args, **kwargs):
        response = super().finalize_response(request, response, *args, **kwargs)
        response['Cache-Control'] = 'private, no-store'
        return response


class SolicitudesRecibidasView(generics.ListAPIView):
    """Bandeja del prestador con las solicitudes que le enviaron."""
    serializer_class = SolicitudRecibidaSerializer
    permission_classes = [EsPrestador]
    pagination_class = None

    def get_queryset(self):
        return self.request.user.prestador.solicitudes.select_related('rubro').prefetch_related('historial')


class ResponderSolicitudView(APIView):
    """Base para aceptar o rechazar; solo el prestador dueño y solo si sigue Pendiente."""
    permission_classes = [EsPrestador]
    nuevo_estado = None

    def post(self, request, pk):
        solicitud = get_object_or_404(Solicitud, pk=pk, prestador=request.user.prestador)
        respondida = timezone.now()
        with transaction.atomic():
            actualizadas = Solicitud.objects.filter(pk=solicitud.pk, estado=Solicitud.Estado.PENDIENTE).update(
                estado=self.nuevo_estado, respondida=respondida,
            )
            if actualizadas:
                CambioEstado.objects.create(solicitud=solicitud, estado=self.nuevo_estado, fecha=respondida)
        if not actualizadas:
            solicitud.refresh_from_db()
            return Response(
                {'estado': f'La solicitud ya fue {solicitud.get_estado_display().lower()}.'},
                status=status.HTTP_409_CONFLICT,
            )
        solicitud.refresh_from_db()
        return Response(SolicitudRecibidaSerializer(solicitud).data)


class AceptarSolicitudView(ResponderSolicitudView):
    nuevo_estado = Solicitud.Estado.ACEPTADA


class RechazarSolicitudView(ResponderSolicitudView):
    nuevo_estado = Solicitud.Estado.RECHAZADA

class AvanzarSolicitudView(APIView):
    """HU-11: el prestador asignado avanza el servicio un paso (Confirmado → En camino → En servicio → Finalizada).

    El cuerpo indica a qué estado pasa ({"estado": "en_camino"}); si no es el siguiente en el orden
    (salto, retroceso, estado final o un doble clic que ya avanzó), responde 409 sin cambiar nada.
    Otro prestador recibe 404, igual que al aceptar o rechazar.
    """
    permission_classes = [EsPrestador]

    def post(self, request, pk):
        solicitud = get_object_or_404(Solicitud, pk=pk, prestador=request.user.prestador)
        destino = request.data.get('estado') if hasattr(request.data, 'get') else None
        if destino not in Solicitud.Estado.values:
            return Response({'estado': 'Indica un estado válido.'}, status=status.HTTP_400_BAD_REQUEST)
        actual = solicitud.estado
        if Solicitud.SEGUIMIENTO.get(actual) != destino:
            return self.conflicto(solicitud)
        ahora = timezone.now()
        with transaction.atomic():
            # Solo cambia si nadie la movió mientras tanto (dos pestañas o doble clic).
            actualizadas = Solicitud.objects.filter(pk=solicitud.pk, estado=actual).update(estado=destino)
            if actualizadas:
                CambioEstado.objects.create(solicitud=solicitud, estado=destino, fecha=ahora)
        solicitud.refresh_from_db()
        if not actualizadas:
            return self.conflicto(solicitud)
        return Response(SolicitudRecibidaSerializer(solicitud).data)

    @staticmethod
    def conflicto(solicitud):
        siguiente = solicitud.siguiente_estado
        if solicitud.estado == Solicitud.Estado.PENDIENTE:
            detalle = 'Primero acepta la solicitud para empezar el seguimiento.'
        elif siguiente:
            detalle = (f'La solicitud está «{solicitud.get_estado_display()}»; '
                       f'solo puede pasar a «{Solicitud.Estado(siguiente).label}».')
        else:
            detalle = f'La solicitud está «{solicitud.get_estado_display()}» y ya no se puede avanzar.'
        return Response({'estado': detalle, 'estado_actual': solicitud.estado}, status=status.HTTP_409_CONFLICT)
