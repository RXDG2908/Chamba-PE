"""Base con datos ficticios, clave RSA de prueba y JWKS para Django y Spring."""
import base64, json, shutil
from comun import AQUI, preparar_django
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization
base = AQUI / 'base.sqlite3'
base.unlink(missing_ok=True)
preparar_django(base)
from django.core.management import call_command
call_command('migrate', verbosity=0)
from django.contrib.auth import get_user_model
from django.utils import timezone
from datetime import timedelta
from prestadores.models import Prestador, Rubro
from usuarios.models import Perfil
from administracion.models import SancionCuenta
User = get_user_model()
gas = Rubro.objects.get(nombre='Gasfitería'); otro = Rubro.objects.exclude(pk=gas.pk).first()
def cuenta(u, rol, uid):
    user = User.objects.create_user(u, f'{u}@example.invalid', None)
    Perfil.objects.update_or_create(usuario=user, defaults={'rol': rol, 'firebase_uid': uid})
    return user
def prestador(u, uid, nombre, dni, lat, estado='verificado', disponible=True):
    p = Prestador.objects.create(usuario=cuenta(u, 'prestador', uid), nombres=nombre, apellidos='Ficticia', dni=dni,
        latitud=f'{lat:.6f}', longitud='-77.042793', radio_cobertura_km=5, disponible=disponible, estado=estado)
    p.rubros.add(gas); return p
ana = prestador('ana', 'uid-ana', 'Ana', '10000001', -12.046374)
prestador('beto', 'uid-beto', 'Beto', '10000002', -12.046374, estado='pendiente')
prestador('carla', 'uid-carla', 'Carla', '10000003', -12.046374, disponible=False)
lejos = prestador('dino', 'uid-dino', 'Dino', '10000004', -11.5)
sanc = prestador('sancionado', 'uid-sanc', 'Saúl', '10000005', -12.046374)
SancionCuenta.objects.create(usuario=sanc.usuario, tipo='bloqueo', motivo='Cobros fuera de la plataforma.')
cuenta('cliente', 'cliente', 'uid-cliente'); cuenta('otro_cliente', 'cliente', 'uid-otro')
cli_s = cuenta('cliente_suspendido', 'cliente', 'uid-cli-susp')
SancionCuenta.objects.create(usuario=cli_s, tipo='suspension', motivo='Lenguaje ofensivo.', hasta=timezone.now() + timedelta(days=3))
# HU-10: rubro propio para la recomendación, con distancias, calificaciones y empates distintos.
# Van al final para no mover los ids que usan los casos anteriores.
from decimal import Decimal
reco = Rubro.objects.create(nombre='Paridad HU-10')
KM = 1 / 111.195  # grados de latitud por km
def recomendable(u, nombre, km, promedio=None, total=0, radio=15, **cambios):
    p = Prestador.objects.create(usuario=cuenta(u, 'prestador', f'uid-{u}'), nombres=nombre, apellidos='Ficticia',
        dni=f'{10000100 + Prestador.objects.count():08d}', latitud=f'{-12.046374 + km * KM:.6f}', longitud='-77.042793',
        radio_cobertura_km=radio, calificacion_promedio=None if promedio is None else Decimal(promedio),
        calificaciones_total=total, **{'disponible': True, 'estado': 'verificado', **cambios})
    p.rubros.add(reco); return p
r_cerca = recomendable('r_cerca', 'Rita', 0.5)
recomendable('r_valorado', 'Raúl', 2, '4.90', 25)
r_ocupado = recomendable('r_ocupado', 'Rocío', 1, '4.50', 10)
recomendable('r_uno', 'Rubén', 1)
recomendable('r_dos', 'Ruth', 1)
recomendable('r_lejos', 'Rosa', 6, '4.60', 1)
recomendable('r_corto', 'Rolando', 3, '5.00', 3, radio=2)
recomendable('r_inactivo', 'Remo', 0.1, '5.00', 50, disponible=False)
recomendable('r_pendiente', 'Reina', 0.1, estado='pendiente')
# Cuenta compartida web/app: cuentas de la web con contraseña y sin Firebase (POST /api/usuarios/vincular/).
for u, rol in (('web_lucia', 'cliente'), ('web_bloq', 'cliente'), ('web_admin', 'administrador'), ('web_susp', 'cliente')):
    w = User.objects.create_user(u, f'{u}@example.invalid', 'clave-de-la-web-1')
    Perfil.objects.update_or_create(usuario=w, defaults={'rol': rol, 'firebase_uid': None})
SancionCuenta.objects.create(usuario=User.objects.get(username='web_susp'), tipo='suspension', motivo='Prueba de sanción.',
                             hasta=timezone.now() + timedelta(days=3))
# Sesiones de la web (las mismas en las dos bases) y un token CSRF, como tras iniciar sesión en Django.
from django.contrib.sessions.backends.db import SessionStore
from django.middleware.csrf import _get_new_csrf_string, _mask_cipher_secret
def sesion(usuario, hash_malo=False):
    s = SessionStore()
    s['_auth_user_id'] = str(usuario.pk)
    s['_auth_user_backend'] = 'administracion.sanciones.BackendConSanciones'
    s['_auth_user_hash'] = 'x' if hash_malo else usuario.get_session_auth_hash()
    s.create(); return s.session_key
cli = User.objects.get(username='cliente')
secreto = _get_new_csrf_string()
sesiones = {'cli': sesion(cli), 'ana': sesion(ana.usuario), 'cli_hash_malo': sesion(cli, hash_malo=True),
            'cli_susp': sesion(cli_s), 'sanc': sesion(sanc.usuario)}
datos = {'reco': reco.pk, 'r_cerca': r_cerca.pk, 'r_ocupado': r_ocupado.pk, 'ana': ana.pk, 'lejos': lejos.pk, 'sanc': sanc.pk, 'gas': gas.pk, 'otro_rubro': otro.pk,
         'sesiones': sesiones, 'csrf': secreto, 'csrf_enmascarado': _mask_cipher_secret(secreto)}
(AQUI / 'datos.json').write_text(json.dumps(datos))
clave = rsa.generate_private_key(public_exponent=65537, key_size=2048)
(AQUI / 'privada.pem').write_bytes(clave.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
(AQUI / 'publica.pem').write_bytes(clave.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo))
num = clave.public_key().public_numbers()
b = lambda n: base64.urlsafe_b64encode(n.to_bytes((n.bit_length() + 7) // 8, 'big')).rstrip(b'=').decode()
(AQUI / 'jwks.json').write_text(json.dumps({'keys': [{'kty': 'RSA', 'kid': 'k1', 'use': 'sig', 'alg': 'RS256', 'n': b(num.n), 'e': b(num.e)}]}))
for copia in ('a', 'b'):
    shutil.copy(base, AQUI / f'{copia}.sqlite3')
print('listo', datos)
