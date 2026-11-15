"""Autenticación de la app móvil: token de Firebase en lugar de sesión de Django.

La app Android inicia sesión con Firebase Authentication y envía su token de identidad
en la cabecera `Authorization: Bearer <token>`. Aquí se verifica la firma del token y se
busca la cuenta de Django vinculada a ese UID (`Perfil.firebase_uid`).

El vínculo lo registra un administrador: tener una cuenta de Firebase no da acceso a
ninguna cuenta de Django por sí solo, ni siquiera con el mismo correo.
"""
import logging

from django.conf import settings
from rest_framework.authentication import BaseAuthentication, get_authorization_header
from rest_framework.exceptions import AuthenticationFailed

from .models import Perfil

logger = logging.getLogger(__name__)


def verificar_token(token):
    """Devuelve el UID de Firebase del token, o None si no es válido para este proyecto.

    Comprueba la firma con las claves públicas de Google, la caducidad, que el token sea
    de nuestro proyecto (audiencia) y que lo haya emitido Firebase Authentication. No
    necesita credenciales de servicio.
    """
    from google.auth.transport import requests as transporte
    from google.oauth2 import id_token

    proyecto = settings.FIREBASE_AUTH_PROJECT_ID
    try:
        datos = id_token.verify_firebase_token(token, transporte.Request(), audience=proyecto)
        if not datos or datos.get('iss') != f'https://securetoken.google.com/{proyecto}':
            raise ValueError('emisor inesperado')
        return datos.get('sub') or None
    except Exception as error:  # vencido, mal firmado, de otro proyecto o sin red para validar
        # Se registra el motivo, nunca el token.
        logger.warning('Token de Firebase rechazado: %s: %s', type(error).__name__, error)
        return None


class AutenticacionFirebase(BaseAuthentication):
    """Acepta `Authorization: Bearer <token de Firebase>` de una cuenta vinculada."""

    def authenticate(self, request):
        partes = get_authorization_header(request).split()
        if not partes or partes[0].lower() != b'bearer':
            return None  # sin token: deciden las demás autenticaciones (sesión)
        if len(partes) != 2 or not settings.FIREBASE_AUTH_PROJECT_ID:
            raise AuthenticationFailed('El acceso con token no está disponible.')

        try:
            token = partes[1].decode('ascii')
        except UnicodeError:
            raise AuthenticationFailed('El token no es válido.')
        uid = verificar_token(token)
        if not uid:
            raise AuthenticationFailed('El token no es válido o venció.')

        perfil = Perfil.objects.select_related('usuario').filter(firebase_uid=uid).first()
        if perfil is None or not perfil.usuario.is_active:
            raise AuthenticationFailed('Esta cuenta de la app aún no está vinculada.')
        return (perfil.usuario, None)
