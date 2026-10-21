from django.contrib.auth import authenticate, get_user_model, login, logout
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_protect, ensure_csrf_cookie
from rest_framework import status
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from .serializers import LoginSerializer, RegistroClienteSerializer, UsuarioActualSerializer

User = get_user_model()


@method_decorator(ensure_csrf_cookie, name='dispatch')
class CsrfView(APIView):
    """Deja la cookie csrftoken; React la llama al cargar la app."""
    permission_classes = [AllowAny]
    authentication_classes = []

    def get(self, request):
        return Response({})


# Registro e inicio de sesión no tienen sesión todavía, así que el CSRF se exige aparte.
@method_decorator(csrf_protect, name='dispatch')
class RegistroClienteView(APIView):
    permission_classes = [AllowAny]
    authentication_classes = []

    def post(self, request):
        serializer = RegistroClienteSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        usuario = serializer.save()
        login(request._request, usuario, backend='django.contrib.auth.backends.ModelBackend')
        return Response(UsuarioActualSerializer(usuario).data, status=status.HTTP_201_CREATED)


@method_decorator(csrf_protect, name='dispatch')
class LoginView(APIView):
    permission_classes = [AllowAny]
    authentication_classes = []

    def post(self, request):
        serializer = LoginSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        username = serializer.validated_data['username']
        # Se acepta el nombre de usuario o el correo.
        if '@' in username:
            cuenta = User.objects.filter(email__iexact=username).first()
            if cuenta:
                username = cuenta.username
        usuario = authenticate(
            request._request, username=username,
            password=serializer.validated_data['password'],
        )
        if usuario is None:
            return Response(
                {'detail': 'Usuario o contraseña incorrectos.'},
                status=status.HTTP_400_BAD_REQUEST,
            )
        login(request._request, usuario)
        return Response(UsuarioActualSerializer(usuario).data)


@method_decorator(csrf_protect, name='dispatch')
class LogoutView(APIView):
    permission_classes = [AllowAny]
    authentication_classes = []

    def post(self, request):
        logout(request._request)
        return Response(status=status.HTTP_204_NO_CONTENT)


class UsuarioActualView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        return Response(UsuarioActualSerializer(request.user).data)
