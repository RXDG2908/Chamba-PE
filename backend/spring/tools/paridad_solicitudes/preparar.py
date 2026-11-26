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
datos = {'ana': ana.pk, 'lejos': lejos.pk, 'sanc': sanc.pk, 'gas': gas.pk, 'otro_rubro': otro.pk}
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
