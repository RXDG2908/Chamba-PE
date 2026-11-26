import sys
from comun import AQUI, PROYECTO, preparar_django
preparar_django(AQUI / sys.argv[1])
import google.auth.jwt as gjwt
import usuarios.autenticacion as autenticacion
publica = (AQUI / 'publica.pem').read_text()
def verificar_token(token):
    # Misma comprobación que verify_firebase_token, con la clave de prueba en vez de las de Google.
    try:
        datos = gjwt.decode(token, certs={'k1': publica}, audience=PROYECTO)
        if datos.get('iss') != f'https://securetoken.google.com/{PROYECTO}' or not datos.get('sub'):
            return None
        return datos['sub']
    except Exception:
        return None
def datos_del_token(token):
    # Lo que usan los registros de la app (correo y nombre del token).
    try:
        datos = gjwt.decode(token, certs={'k1': publica}, audience=PROYECTO)
        if datos.get('iss') != f'https://securetoken.google.com/{PROYECTO}' or not datos.get('sub'):
            return None
        return datos
    except Exception:
        return None
autenticacion.verificar_token = verificar_token
autenticacion.datos_del_token = datos_del_token
from django.core.management import call_command
call_command('runserver', f'127.0.0.1:{sys.argv[2]}', use_reloader=False)
