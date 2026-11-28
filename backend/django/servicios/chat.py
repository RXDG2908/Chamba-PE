"""Chat de la solicitud: al aceptarla, el cliente y el prestador coordinan por mensajes.

GET  /api/servicios/solicitudes/<id>/chat/  → la conversación (la web y la app la consultan cada 5 s).
POST /api/servicios/solicitudes/<id>/chat/  → {"texto": "..."} envía un mensaje.

Solo entran las dos partes: el cliente dueño y el prestador asignado; para cualquier otro es 404,
como si no existiera. Hay chat desde Confirmado; se escribe hasta En servicio y al quedar
Finalizada queda solo para leer. Pendiente y Rechazada no tienen chat (409).
"""
from django.db.models import Q
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import serializers, status
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import Mensaje, Solicitud

E = Solicitud.Estado

SIN_CHAT = {
    E.PENDIENTE: 'El chat se abre cuando el prestador acepta la solicitud.',
    E.RECHAZADA: 'La solicitud fue rechazada y no tiene chat.',
}
SOLO_LECTURA = 'El servicio ya finalizó; el chat queda solo para leer.'


class NuevoMensajeSerializer(serializers.Serializer):
    texto = serializers.CharField(max_length=500, trim_whitespace=False, error_messages={
        'blank': 'Escribe un mensaje.',
    })

    def validate_texto(self, valor):
        if not valor.strip():
            raise serializers.ValidationError('Escribe un mensaje; no uses solo espacios.')
        return valor


def mensaje_json(mensaje, solicitud):
    return {
        'id': mensaje.id,
        'de': 'prestador' if mensaje.autor_id == solicitud.prestador.usuario_id else 'cliente',
        'texto': mensaje.texto,
        'enviado': serializers.DateTimeField().to_representation(mensaje.enviado),
    }


class ChatSolicitudView(APIView):
    permission_classes = [IsAuthenticated]

    def solicitud(self, request, pk):
        usuario = request.user
        partes = Solicitud.objects.select_related('prestador').filter(
            Q(cliente=usuario) | Q(prestador__usuario=usuario))
        return get_object_or_404(partes, pk=pk)

    def get(self, request, pk):
        solicitud = self.solicitud(request, pk)
        if solicitud.estado not in Mensaje.ESTADOS_CON_CHAT:
            return self.sin_chat(solicitud)
        activo = solicitud.estado in Mensaje.ESTADOS_ACTIVOS
        soy_prestador = solicitud.prestador.usuario_id == request.user.id
        if soy_prestador:
            # Como en la bandeja: el prestador siempre tiene el contacto del cliente.
            contraparte = {'nombre': solicitud.cliente_nombre, 'telefono': solicitud.cliente_telefono}
        else:
            # El cliente ve el teléfono del prestador solo mientras el chat está activo.
            contraparte = {
                'nombre': solicitud.prestador.nombre_publico,
                'telefono': (solicitud.prestador.telefono or None) if activo else None,
            }
        respuesta = Response({
            'solicitud': solicitud.id,
            'estado': solicitud.estado,
            'estado_display': solicitud.get_estado_display(),
            'activo': activo,
            'yo': 'prestador' if soy_prestador else 'cliente',
            'contraparte': contraparte,
            'mensajes': [mensaje_json(m, solicitud) for m in solicitud.mensajes.all()],
        })
        respuesta['Cache-Control'] = 'private, no-store'
        return respuesta

    def post(self, request, pk):
        solicitud = self.solicitud(request, pk)
        if solicitud.estado not in Mensaje.ESTADOS_CON_CHAT:
            return self.sin_chat(solicitud)
        if solicitud.estado not in Mensaje.ESTADOS_ACTIVOS:
            return Response({'chat': SOLO_LECTURA}, status=status.HTTP_409_CONFLICT)
        datos = NuevoMensajeSerializer(data=request.data)
        datos.is_valid(raise_exception=True)
        mensaje = Mensaje.objects.create(
            solicitud=solicitud, autor=request.user, texto=datos.validated_data['texto'], enviado=timezone.now(),
        )
        return Response(mensaje_json(mensaje, solicitud), status=status.HTTP_201_CREATED)

    @staticmethod
    def sin_chat(solicitud):
        return Response({'chat': SIN_CHAT[solicitud.estado]}, status=status.HTTP_409_CONFLICT)
