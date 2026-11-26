from django.db import transaction
from django.contrib.auth import authenticate, get_user_model, login, logout
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_protect, ensure_csrf_cookie
from rest_framework import status
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from .permisos import EsPrestador
from .serializers import (
    DisponibilidadSerializer, LoginSerializer, RegistroClienteSerializer, UsuarioActualSerializer,
)

User = get_user_model()


def cuenta_sancionada(username, password):
    """Mensaje de la sanción vigente si las credenciales son correctas; si no, None."""
    from administracion.models import sancion_vigente
    from administracion.sanciones import mensaje_para_la_cuenta

    cuenta = User.objects.filter(username=username, is_active=True).first()
    if cuenta is None or not cuenta.check_password(password):
        return None
    sancion = sancion_vigente(cuenta.pk)
    return mensaje_para_la_cuenta(sancion) if sancion else None


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
        from servicios.models import TokenFCM
        TokenFCM.objects.filter(sesion=request.session.session_key).delete()
        login(request._request, usuario, backend='administracion.sanciones.BackendConSanciones')
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
            sancionada = cuenta_sancionada(username, serializer.validated_data['password'])
            if sancionada:
                # HU-13: con la contraseña correcta se explica por qué no entra.
                return Response({'detail': sancionada}, status=status.HTTP_403_FORBIDDEN)
            return Response(
                {'detail': 'Usuario o contraseña incorrectos.'},
                status=status.HTTP_400_BAD_REQUEST,
            )
        from servicios.models import TokenFCM
        TokenFCM.objects.filter(sesion=request.session.session_key).delete()
        login(request._request, usuario)
        return Response(UsuarioActualSerializer(usuario).data)


@method_decorator(csrf_protect, name='dispatch')
class LogoutView(APIView):
    permission_classes = [AllowAny]
    authentication_classes = []

    def post(self, request):
        from servicios.models import TokenFCM
        TokenFCM.objects.filter(sesion=request.session.session_key).delete()
        logout(request._request)
        return Response(status=status.HTTP_204_NO_CONTENT)


class UsuarioActualView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        return Response(UsuarioActualSerializer(request.user).data)


class DisponibilidadView(APIView):
    """T6.1: el prestador consulta y cambia su disponibilidad (Activo o Inactivo)."""
    permission_classes = [EsPrestador]

    def get(self, request):
        return Response(DisponibilidadSerializer(request.user.prestador).data)

    def patch(self, request):
        prestador = request.user.prestador
        serializer = DisponibilidadSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        prestador.disponible = serializer.validated_data['disponible']
        prestador.save(update_fields=['disponible'])
        return Response(DisponibilidadSerializer(prestador).data)


class RegistroClienteMovilView(APIView):
    """Cuenta de cliente para la app móvil (POST /api/usuarios/registro-movil/).

    La app inicia sesión con Firebase; para usar la API (solicitudes, seguimiento y
    calificación, igual que la web) necesita una cuenta de Django vinculada a ese UID.
    Es idempotente: si el UID ya está vinculado a un cliente, devuelve sus datos (200).
    Nunca vincula una cuenta existente por correo (eso lo hace un administrador) ni
    convierte a un prestador en cliente.
    """
    permission_classes = [AllowAny]
    authentication_classes = []

    def post(self, request):
        from . import autenticacion
        from .models import Perfil

        token = autenticacion.token_de(request)
        datos = autenticacion.datos_del_token(token) if token else None
        if datos is None:
            return Response({'detail': 'El token no es válido o venció.'}, status=status.HTTP_403_FORBIDDEN)
        uid = datos['sub']
        perfil = Perfil.objects.select_related('usuario').filter(firebase_uid=uid).first()
        if perfil is not None:
            if perfil.rol != Perfil.Rol.CLIENTE:
                return Response({'detail': 'Esta cuenta de la app no es de cliente.'}, status=status.HTTP_409_CONFLICT)
            return Response(UsuarioActualSerializer(perfil.usuario).data)
        correo = (datos.get('email') or '').strip()
        if correo and User.objects.filter(email__iexact=correo).exists():
            return Response(
                {'email': ['Ya existe una cuenta con ese correo. Pide a administración que la vincule con la app.']},
                status=status.HTTP_400_BAD_REQUEST,
            )
        nombre = str(request.data.get('nombre') or datos.get('name') or '').strip()[:150]
        with transaction.atomic():
            # Sin contraseña utilizable: solo se entra con el token de Firebase.
            usuario = User.objects.create_user(username=f'fb-{uid}'[:150], email=correo, first_name=nombre)
            Perfil.objects.filter(usuario=usuario).update(rol=Perfil.Rol.CLIENTE, firebase_uid=uid)
        return Response(UsuarioActualSerializer(usuario).data, status=status.HTTP_201_CREATED)
