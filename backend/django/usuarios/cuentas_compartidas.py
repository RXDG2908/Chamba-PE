"""Una sola cuenta para la web y la app: el mismo correo y la misma contraseña en los dos.

- La app entra con Firebase; la web, con la sesión de Django. Una cuenta creada en la app no tiene
  contraseña de Django y una creada en la web no tiene cuenta de Firebase.
- `POST /api/usuarios/login-firebase/` (web): quien escribió su correo y contraseña de la app en
  la web y Firebase los aceptó, manda su token y recibe la sesión de Django de su cuenta vinculada.
- `POST /api/usuarios/vincular/` (app, también en Spring): quien tiene cuenta de la web crea su
  cuenta de Firebase con el mismo correo y contraseña y, con su token y la contraseña de la web,
  la vincula. Se exige la contraseña correcta de esa cuenta: el correo solo no basta (en Firebase
  cualquiera puede crear una cuenta con un correo ajeno), así que nadie se apodera de una cuenta.

Revisado con la skill `api-security` (OWASP API2): la contraseña se compara con el hash de Django,
los intentos fallidos por correo tienen límite (10 por hora) y los mensajes no dicen si el correo
existe.
"""
from django.contrib.auth import get_user_model, login
from django.core.cache import cache
from django.db import transaction
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_protect
from rest_framework import status
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from . import autenticacion
from .models import Perfil
from .serializers import UsuarioActualSerializer

User = get_user_model()

INTENTOS_MAXIMOS = 10
VENTANA_SEGUNDOS = 60 * 60
NO_COINCIDE = 'El correo o la contraseña no coinciden con una cuenta de la web.'
DEMASIADOS = 'Demasiados intentos con este correo. Inténtalo de nuevo en una hora.'


def _token_valido(request):
    token = autenticacion.token_de(request)
    return autenticacion.datos_del_token(token) if token else None


def _mensaje_sancion(usuario_id):
    from administracion.models import sancion_vigente
    from administracion.sanciones import mensaje_para_la_cuenta
    sancion = sancion_vigente(usuario_id)
    return mensaje_para_la_cuenta(sancion) if sancion else None


def _clave_intentos(correo):
    return f'vincular:{correo.strip().lower()}'


def intentos_fallidos(correo):
    return cache.get(_clave_intentos(correo), 0)


def _sumar_fallo(correo):
    clave = _clave_intentos(correo)
    if cache.add(clave, 1, VENTANA_SEGUNDOS):
        return
    try:
        cache.incr(clave)
    except ValueError:  # venció entre add e incr
        cache.add(clave, 1, VENTANA_SEGUNDOS)


@method_decorator(csrf_protect, name='dispatch')
class LoginFirebaseView(APIView):
    """Web: token de Firebase de una cuenta vinculada → sesión de Django (como `login/`)."""
    permission_classes = [AllowAny]
    authentication_classes = []

    def post(self, request):
        datos = _token_valido(request)
        if datos is None:
            return Response({'detail': 'El token no es válido o venció.'}, status=status.HTTP_403_FORBIDDEN)
        perfil = Perfil.objects.select_related('usuario').filter(firebase_uid=datos['sub']).first()
        if perfil is None or not perfil.usuario.is_active:
            return Response(
                {'detail': 'Esta cuenta de la app aún no está vinculada.', 'codigo': 'cuenta_no_vinculada'},
                status=status.HTTP_403_FORBIDDEN,
            )
        mensaje = _mensaje_sancion(perfil.usuario_id)
        if mensaje:
            return Response({'detail': mensaje, 'codigo': 'cuenta_sancionada'}, status=status.HTTP_403_FORBIDDEN)
        from servicios.models import TokenFCM
        TokenFCM.objects.filter(sesion=request.session.session_key).delete()
        login(request._request, perfil.usuario, backend='administracion.sanciones.BackendConSanciones')
        return Response(UsuarioActualSerializer(perfil.usuario).data)


class VincularCuentaView(APIView):
    """App: vincula la cuenta de Firebase con la cuenta de la web del mismo correo, con su contraseña."""
    permission_classes = [AllowAny]
    authentication_classes = []

    def post(self, request):
        datos = _token_valido(request)
        if datos is None:
            return Response({'detail': 'El token no es válido o venció.'}, status=status.HTTP_403_FORBIDDEN)
        uid = datos['sub']
        if Perfil.objects.filter(firebase_uid=uid).exists():
            return Response({'detail': 'Esta cuenta de la app ya está vinculada.'}, status=status.HTTP_409_CONFLICT)
        correo = str(datos.get('email') or '').strip()
        clave = request.data.get('password') if isinstance(request.data, dict) else None
        if not correo or not isinstance(clave, str) or not clave:
            return Response({'detail': NO_COINCIDE}, status=status.HTTP_400_BAD_REQUEST)
        if intentos_fallidos(correo) >= INTENTOS_MAXIMOS:
            return Response({'detail': DEMASIADOS}, status=status.HTTP_429_TOO_MANY_REQUESTS)

        usuario = User.objects.filter(email__iexact=correo, is_active=True).order_by('id').first()
        if usuario is None or not usuario.check_password(clave):
            _sumar_fallo(correo)
            return Response({'detail': NO_COINCIDE}, status=status.HTTP_400_BAD_REQUEST)

        with transaction.atomic():
            perfil = Perfil.objects.select_for_update().filter(usuario=usuario).first()
            if perfil is None or perfil.rol not in (Perfil.Rol.CLIENTE, Perfil.Rol.PRESTADOR):
                return Response({'detail': 'Esta cuenta no se usa en la app.'}, status=status.HTTP_409_CONFLICT)
            if perfil.firebase_uid:
                return Response(
                    {'detail': 'Esa cuenta de la web ya está vinculada con otra cuenta de la app.'},
                    status=status.HTTP_409_CONFLICT,
                )
            mensaje = _mensaje_sancion(usuario.pk)
            if mensaje:
                return Response({'detail': mensaje, 'codigo': 'cuenta_sancionada'}, status=status.HTTP_403_FORBIDDEN)
            perfil.firebase_uid = uid
            perfil.save(update_fields=['firebase_uid'])
        cache.delete(_clave_intentos(correo))
        return Response(UsuarioActualSerializer(usuario).data)
