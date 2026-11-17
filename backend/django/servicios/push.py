"""FCM data-only: aceptación por Firebase no significa entrega al navegador."""
import logging
from django.conf import settings
from django.contrib.sessions.models import Session
from django.utils import timezone
from .models import Solicitud, TokenFCM

logger = logging.getLogger(__name__)


def enviar_fcm(token, datos):
    import firebase_admin
    from firebase_admin import messaging
    try:
        app = firebase_admin.get_app('chamba-push')
    except ValueError:
        app = firebase_admin.initialize_app(options={
            'projectId': settings.FIREBASE_PROJECT_ID,
            'httpTimeout': 5,
        }, name='chamba-push')
    return messaging.send(messaging.Message(
        token=token, data=datos,
        webpush=messaging.WebpushConfig(headers={'TTL': '300', 'Urgency': 'high'}),
    ), app=app)


def notificar_solicitud(pk):
    if not settings.FCM_ENABLED:
        return
    try:
        solicitud = Solicitud.objects.select_related('prestador').get(pk=pk)
        tokens = TokenFCM.objects.filter(usuario_id=solicitud.prestador.usuario_id)
        sesiones = Session.objects.filter(expire_date__gt=timezone.now())
        for dispositivo in tokens:
            sesion = sesiones.filter(session_key=dispositivo.sesion).first()
            if not sesion or sesion.get_decoded().get('_auth_user_id') != str(dispositivo.usuario_id):
                dispositivo.delete()
                continue
            try:
                enviar_fcm(dispositivo.token, {
                    'solicitud_id': str(pk), 'usuario_id': str(dispositivo.usuario_id),
                })
                logger.info('FCM aceptó envío de solicitud %s; entrega no confirmada', pk)
            except Exception as exc:
                from firebase_admin.messaging import UnregisteredError
                # No registrar el token ni el texto del error (podría contener credenciales).
                if isinstance(exc, UnregisteredError):
                    dispositivo.delete()
                logger.warning('FCM falló para solicitud %s (%s)', pk, type(exc).__name__)
    except Exception as exc:
        # Un fallo posterior al commit nunca convierte un alta persistida en un 500.
        logger.warning('No se pudo notificar solicitud %s (%s)', pk, type(exc).__name__)
