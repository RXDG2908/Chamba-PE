from django.db import OperationalError
from rest_framework import serializers
from rest_framework.response import Response
from rest_framework.views import APIView

from usuarios.permisos import EsPrestador

from .consentimiento import (
    aceptar_consentimiento, estado_consentimiento, revocar_consentimiento,
)
from .serializers import AceptacionConsentimientoSerializer


class ConsentimientoView(APIView):
    permission_classes = [EsPrestador]

    def get(self, request):
        return Response(estado_consentimiento(request.user))

    def post(self, request):
        datos = AceptacionConsentimientoSerializer(data=request.data)
        datos.is_valid(raise_exception=True)
        try:
            creado = aceptar_consentimiento(request.user)
        except OperationalError:
            # El servicio atomic ya salió con rollback antes de responder.
            return Response({'detail': 'No pudimos guardar el consentimiento. Inténtalo de nuevo.'}, status=503)
        return Response(estado_consentimiento(request.user), status=201 if creado else 200)


class RevocarConsentimientoView(APIView):
    permission_classes = [EsPrestador]

    def post(self, request):
        if request.data:
            raise serializers.ValidationError({'detail': 'La revocación no recibe campos.'})
        try:
            revocar_consentimiento(request.user)
        except OperationalError:
            return Response({'detail': 'No pudimos guardar el consentimiento. Inténtalo de nuevo.'}, status=503)
        return Response(estado_consentimiento(request.user))
