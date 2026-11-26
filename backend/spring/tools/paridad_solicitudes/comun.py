"""Piezas comunes de la paridad de solicitudes. Todo lo generado va a PARIDAD_TRABAJO (o una carpeta temporal)."""
import os, sys, tempfile
from pathlib import Path
SCRIPTS = Path(__file__).resolve().parent
REPO = SCRIPTS.parents[3]
DJANGO = REPO / 'backend' / 'django'
AQUI = Path(os.environ.setdefault('PARIDAD_TRABAJO', tempfile.mkdtemp(prefix='paridad-solicitudes-')))
CLAVE_DJANGO = 'clave-solo-para-la-paridad-local'
PROYECTO = 'proyecto-paridad'

def preparar_django(base):
    sys.path.insert(0, str(DJANGO))
    os.environ['DJANGO_SETTINGS_MODULE'] = 'core.settings'
    os.environ['DJANGO_DEBUG'] = 'true'
    os.environ['FCM_ENABLED'] = 'false'
    os.environ['FIREBASE_AUTH_PROJECT_ID'] = PROYECTO
    os.environ.pop('GOOGLE_APPLICATION_CREDENTIALS', None)
    import django
    from django.conf import settings
    settings.DATABASES['default']['NAME'] = base
    settings.SECRET_KEY = CLAVE_DJANGO
    settings.FIREBASE_AUTH_PROJECT_ID = PROYECTO
    settings.ALLOWED_HOSTS = ['127.0.0.1', 'localhost']
    settings.PASSWORD_HASHERS = ['django.contrib.auth.hashers.MD5PasswordHasher']
    django.setup()
