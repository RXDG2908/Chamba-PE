from django.contrib.auth import get_user_model
from rest_framework import generics, status
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from usuarios import autenticacion
from usuarios.models import Perfil

from .models import Rubro
from .serializers import (
    RegistroPrestadorMovilSerializer, RegistroPrestadorSerializer, RubroSerializer,
)


class RegistroPrestadorView(generics.CreateAPIView):
    serializer_class = RegistroPrestadorSerializer
    permission_classes = [AllowAny]
    authentication_classes = []



class RegistroPrestadorMovilView(APIView):
    """Registro de prestador desde la app móvil.

    Quien llama demuestra con su token que es dueño de una cuenta de Firebase todavía
    sin registrar; se le crea una cuenta de Django nueva vinculada a ese UID. Nunca se
    vincula a una cuenta existente: eso solo lo hace un administrador.
    """
    permission_classes = [AllowAny]
    authentication_classes = []

    def post(self, request):
        token = autenticacion.token_de(request)
        datos = autenticacion.datos_del_token(token) if token else None
        if datos is None:
            return Response({'detail': 'El token no es válido o venció.'}, status=status.HTTP_403_FORBIDDEN)

        uid = datos['sub']
        correo = datos.get('email') or ''
        if Perfil.objects.filter(firebase_uid=uid).exists():
            return Response({'detail': 'Esta cuenta de la app ya está registrada.'}, status=status.HTTP_409_CONFLICT)
        if correo and get_user_model().objects.filter(email__iexact=correo).exists():
            return Response(
                {'email': ['Ya existe una cuenta con ese correo. Pide a administración que la vincule con la app.']},
                status=status.HTTP_400_BAD_REQUEST,
            )

        serializer = RegistroPrestadorMovilSerializer(
            data=request.data, context={'firebase_uid': uid, 'email': correo},
        )
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response(serializer.data, status=status.HTTP_201_CREATED)


class RubroListView(generics.ListAPIView):
    queryset = Rubro.objects.all()
    serializer_class = RubroSerializer
    permission_classes = [AllowAny]
    pagination_class = None
