"""Django real para T11.3 (HU-11 de punta a punta): base SQLite temporal y datos ficticios.

Lo arranca `npm run test:hu11-local` (playwright.hu11-local.config.mjs). La base se borra al
terminar y la contraseña de las cuentas ficticias se genera en cada corrida: se deja solo en
.hu11-local/fixtures.json (ignorado por git) para que la lea la prueba.
"""
import json
import os
from pathlib import Path
import secrets
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]   # backend/django
REPO = ROOT.parents[1]
sys.path.insert(0, str(ROOT))
os.environ['DJANGO_SETTINGS_MODULE'] = 'core.settings'
# Sin Firebase ni credenciales del entorno del usuario.
os.environ.pop('GOOGLE_APPLICATION_CREDENTIALS', None)
os.environ['FCM_ENABLED'] = 'false'

import django  # noqa: E402
from django.conf import settings  # noqa: E402
from django.core.management import call_command  # noqa: E402

PUERTO_DJANGO = 8021
ORIGEN_WEB = 'http://127.0.0.1:5195'
CENTRO = (-12.046374, -77.042793)


def main():
    espacio = REPO / '.hu11-local'
    espacio.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='datos-', dir=espacio) as temporal:
        settings.DATABASES['default']['NAME'] = Path(temporal) / 'pruebas.sqlite3'
        settings.MEDIA_ROOT = str(Path(temporal) / 'media')
        settings.ALLOWED_HOSTS = ['127.0.0.1', 'localhost']
        settings.CSRF_TRUSTED_ORIGINS = [ORIGEN_WEB]
        settings.PASSWORD_HASHERS = ['django.contrib.auth.hashers.MD5PasswordHasher']
        settings.SECRET_KEY = secrets.token_urlsafe(48)
        settings.CORS_ALLOW_ALL_ORIGINS = False
        django.setup()

        from django.contrib.auth import get_user_model
        from prestadores.models import Prestador, Rubro
        from usuarios.models import Perfil

        call_command('migrate', verbosity=0, interactive=False)
        assert not Prestador.objects.exists(), 'La base temporal debe empezar vacía'
        clave = secrets.token_urlsafe(16)
        User = get_user_model()
        rubro = Rubro.objects.get(nombre='Gasfitería')

        def cuenta(usuario, rol):
            user = User.objects.create_user(usuario, f'{usuario}@example.invalid', clave)
            Perfil.objects.update_or_create(usuario=user, defaults={'rol': rol})
            return user

        def prestador(usuario, nombres, dni, latitud):
            p = Prestador.objects.create(
                usuario=cuenta(usuario, 'prestador'), nombres=nombres, apellidos='Ficticia',
                dni=dni, telefono='000000000', latitud=f'{latitud:.6f}', longitud=f'{CENTRO[1]:.6f}',
                radio_cobertura_km=5, disponible=True, estado=Prestador.Estado.VERIFICADO)
            p.rubros.add(rubro)
            return p

        ana = prestador('hu11_prestadora', 'Ana', '91100001', CENTRO[0])
        beto = prestador('hu11_otro_prestador', 'Beto', '91100002', CENTRO[0] + 0.5)  # lejos: no sale en la búsqueda
        cuenta('hu11_cliente', 'cliente')
        cuenta('hu11_otro_cliente', 'cliente')

        datos = {
            'clave': clave, 'centro': {'latitud': CENTRO[0], 'longitud': CENTRO[1]}, 'rubro': rubro.pk,
            'prestadora': {'id': ana.pk, 'usuario': 'hu11_prestadora'},
            'otro_prestador': {'id': beto.pk, 'usuario': 'hu11_otro_prestador'},
            'cliente': 'hu11_cliente', 'otro_cliente': 'hu11_otro_cliente',
        }
        (espacio / 'fixtures.json').write_text(json.dumps(datos, indent=2), encoding='utf-8')
        print('HU11: Django real con SQLite temporal y cuentas ficticias.', flush=True)
        call_command('runserver', f'127.0.0.1:{PUERTO_DJANGO}', use_reloader=False, verbosity=1)


if __name__ == '__main__':
    main()
