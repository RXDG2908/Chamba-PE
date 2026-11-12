from django.shortcuts import get_object_or_404
from rest_framework import serializers
from rest_framework.response import Response
from rest_framework.views import APIView
from usuarios.permisos import EsPrestador
from .models import Solicitud, TokenFCM
from .serializers import SolicitudRecibidaSerializer


class TokenSerializer(serializers.Serializer):
    token = serializers.RegexField(r'^[A-Za-z0-9_:\-]+$', min_length=20, max_length=2048)


class TokenFCMView(APIView):
    permission_classes = [EsPrestador]

    def post(self, request):
        serializer = TokenSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        TokenFCM.objects.update_or_create(token=serializer.validated_data['token'], defaults={
            'usuario': request.user, 'sesion': request.session.session_key,
        })
        return Response({'registrado': True})

    def delete(self, request):
        serializer = TokenSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        TokenFCM.objects.filter(usuario=request.user, token=serializer.validated_data['token']).delete()
        return Response(status=204)


class SolicitudPrivadaView(APIView):
    permission_classes = [EsPrestador]

    def get(self, request, pk):
        solicitud = get_object_or_404(Solicitud, pk=pk, prestador=request.user.prestador)
        return Response(SolicitudRecibidaSerializer(solicitud).data)
