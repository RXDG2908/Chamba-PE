"""Servidor Django exclusivo de validación HU-7 con datos ficticios y BD temporal."""
import json
import math
import os
from pathlib import Path
import secrets
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
# Permite probar el frontend preparado contra la dependencia backend sin fusionarla.
BACKEND_ROOT = Path(os.environ.get('HU7_TEST_BACKEND_ROOT', ROOT)).resolve()
sys.path.insert(0, str(BACKEND_ROOT))
os.environ['DJANGO_SETTINGS_MODULE'] = 'core.settings'
# Esta ejecución no usa Firebase ni credenciales del entorno del usuario.
os.environ.pop('GOOGLE_APPLICATION_CREDENTIALS', None)
os.environ['FCM_ENABLED'] = 'false'
import django
from django.conf import settings
from django.core.management import call_command

def main():
    espacio = ROOT / '.hu7-local'
    espacio.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='datos-', dir=espacio) as temporal:
        settings.DATABASES['default']['NAME'] = Path(temporal) / 'pruebas.sqlite3'
        settings.MEDIA_ROOT = str(Path(temporal) / 'media')
        settings.ALLOWED_HOSTS = ['127.0.0.1', 'localhost', 'testserver']
        settings.CSRF_TRUSTED_ORIGINS = ['http://127.0.0.1:5189']
        settings.PASSWORD_HASHERS = ['django.contrib.auth.hashers.MD5PasswordHasher']
        settings.SECRET_KEY = secrets.token_urlsafe(48)
        settings.CORS_ALLOW_ALL_ORIGINS = False
        django.setup()
        from django.contrib.auth import get_user_model
        from prestadores.models import Prestador, Rubro
        call_command('migrate', verbosity=0, interactive=False)
        assert not Prestador.objects.exists(), 'La base aislada debe comenzar vacía'
        rubros = {n: Rubro.objects.get(nombre=n) for n in ['Gasfitería', 'Electricidad']}
        lat, lng = -12.046374, -77.042793
        specs = [
            ('Centro', lat, lng, 5, True, 'verificado', 'Gasfitería'),
            ('Noreste', lat + .015, lng + .015, 5, True, 'verificado', 'Gasfitería'),
            ('Fuera', lat + math.degrees(6 / 6371.0088), lng, 1, True, 'verificado', 'Gasfitería'),
            ('Inactivo', lat, lng, 5, False, 'verificado', 'Gasfitería'),
            ('Pendiente', lat, lng, 5, True, 'pendiente', 'Gasfitería'),
            ('Electricidad', lat, lng, 5, True, 'verificado', 'Electricidad'),
            ('Compartido', lat, lng, 5, True, 'verificado', 'Gasfitería'),
        ]
        fixtures = {'centro': {'latitud': lat, 'longitud': lng},
                    'rubros': {n: r.pk for n, r in rubros.items()}, 'prestadores': {}}
        for numero, (nombre, plat, plng, radio, disponible, estado, rubro) in enumerate(specs, 1):
            user = get_user_model().objects.create_user(
                username=f'hu7_ficticio_{numero}', password='SoloPruebasHU7!2026',
                email=f'hu7-{numero}@example.invalid')
            p = Prestador.objects.create(usuario=user, nombres='Ficticio', apellidos=nombre,
                dni=f'{numero:08d}', telefono='000000000',
                latitud=f'{plat:.6f}', longitud=f'{plng:.6f}',
                radio_cobertura_km=radio, disponible=disponible, estado=estado)
            p.rubros.add(rubros[rubro])
            fixtures['prestadores'][nombre] = {'id': p.pk, 'username': user.username,
                'latitud': float(p.latitud), 'longitud': float(p.longitud)}
        destino = ROOT / 'docs/evidencias/hu7-local/fixtures.json'
        destino.parent.mkdir(parents=True, exist_ok=True)
        destino.write_text(json.dumps(fixtures, ensure_ascii=False, indent=2), encoding='utf-8')
        print('HU7: Django aislado, 7 prestadores ficticios y SQLite temporal dentro del worktree.', flush=True)
        call_command('runserver', '127.0.0.1:8019', use_reloader=False, verbosity=1)

if __name__ == '__main__':
    main()
