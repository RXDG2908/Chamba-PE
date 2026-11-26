"""Mismo escenario de solicitudes contra Django (base a) y Spring (base b); luego Django lee la base b.

Uso, desde la raíz del repo, con Spring ya compilado (mvnw package) y el venv de Django:

    python backend/spring/tools/paridad_solicitudes/correr.py

Arma una base nueva con datos ficticios, una clave RSA de prueba (sus JWKS se sirven en local,
en lugar de las claves de Google) y levanta los dos servidores con la misma DJANGO_SECRET_KEY.
No toca db.sqlite3 ni usa credenciales reales.
"""
import http.client, json, os, re, shutil, subprocess, sys, time, threading
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from functools import partial
from comun import AQUI, CLAVE_DJANGO, PROYECTO, SCRIPTS
import google.auth.crypt as gcrypt, google.auth.jwt as gjwt

PY = sys.executable
JAR = next((SCRIPTS.parents[1] / 'target').glob('chambape-*.jar'))
subprocess.run([PY, '-W', 'ignore', str(SCRIPTS / 'preparar.py')], check=True)
PD, PS, PJ, PD2 = 8041, 8042, 8043, 8044
datos = json.loads((AQUI / 'datos.json').read_text())

firmante = gcrypt.RSASigner.from_string((AQUI / 'privada.pem').read_text(), key_id='k1')
def token(uid, aud=PROYECTO, venc=3600):
    ahora = int(time.time())
    return gjwt.encode(firmante, {'iss': f'https://securetoken.google.com/{aud}', 'aud': aud, 'sub': uid,
                                  'iat': ahora - 30, 'exp': ahora + venc, 'auth_time': ahora - 30}).decode()

class SoloJwks(SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass
srv = ThreadingHTTPServer(('127.0.0.1', PJ), partial(SoloJwks, directory=str(AQUI)))
threading.Thread(target=srv.serve_forever, daemon=True).start()

env = dict(os.environ, DJANGO_DEBUG='true')
logs = []
def django(base, puerto):
    f = open(AQUI / f'django-{puerto}.log', 'w'); logs.append(f)
    return subprocess.Popen([PY, '-W', 'ignore', str(SCRIPTS / 'django_server.py'), base, str(puerto)], cwd=AQUI, env=env, stdout=f, stderr=f)
procesos = [django('a.sqlite3', PD)]
f = open(AQUI / 'spring.log', 'w'); logs.append(f)
procesos.append(subprocess.Popen(['java', '-jar', JAR], cwd=AQUI, stdout=f, stderr=f, env=dict(os.environ,
    PORT=str(PS), SPRING_DATASOURCE_URL=f'jdbc:sqlite:{(AQUI / "b.sqlite3").as_posix()}?busy_timeout=5000',
    DJANGO_SECRET_KEY=CLAVE_DJANGO, FIREBASE_AUTH_PROJECT_ID=PROYECTO,
    FIREBASE_CLAVES_URL=f'http://127.0.0.1:{PJ}/jwks.json', FCM_ENABLED='false')))

def pedir(puerto, metodo, ruta, cuerpo=None, tipo='application/json', tok=None):
    con = http.client.HTTPConnection('127.0.0.1', puerto, timeout=30)
    cab = {}
    if tok: cab['Authorization'] = 'Bearer ' + tok
    if cuerpo is not None:
        cab['Content-Type'] = tipo
        cuerpo = cuerpo if isinstance(cuerpo, (bytes, str)) else json.dumps(cuerpo)
    con.request(metodo, ruta, body=cuerpo, headers=cab)
    r = con.getresponse(); txt = r.read().decode()
    try: j = json.loads(txt)
    except ValueError: j = {'_texto': txt[:100]}
    return r.status, j, r.getheader('Cache-Control')

def esperar(puerto):
    for _ in range(120):
        try: pedir(puerto, 'GET', '/api/prestadores/rubros/'); return
        except OSError: time.sleep(1)
    raise SystemExit(f'no levantó {puerto}')

FECHA = re.compile(r'^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?Z$')
def normal(x):
    if isinstance(x, dict): return {k: normal(v) for k, v in x.items()}
    if isinstance(x, list): return [normal(v) for v in x]
    if isinstance(x, str) and FECHA.match(x): return '<fecha%s>' % ('.us' if '.' in x else '')
    return x

try:
    esperar(PD); esperar(PS)
    A, G = datos['ana'], datos['gas']
    base = {'prestador': A, 'rubro': G, 'cliente_nombre': 'María José', 'cliente_telefono': '987654321',
            'descripcion': 'Fuga en el baño', 'latitud': '-12.046374', 'longitud': '-77.042793'}
    T = {k: token(v) for k, v in {'ana': 'uid-ana', 'dino': 'uid-dino', 'cli': 'uid-cliente', 'otro': 'uid-otro',
         'sanc': 'uid-sanc', 'clis': 'uid-cli-susp', 'nadie': 'uid-desconocido'}.items()}
    T['venc'] = token('uid-ana', venc=-300); T['proy'] = token('uid-ana', aud='otro-proyecto')
    S = '/api/servicios/solicitudes/'
    pasos = [
        ('POST', S, base, None), ('POST', S, dict(base, latitud=-12.0463741), None),
        ('POST', S, dict(base, cliente_nombre='Ana', descripcion='Con cuenta'), T['cli']),
        ('POST', S, {}, None), ('POST', S, [1], None), ('POST', S, 'x', None),
        ('POST', S, dict(base, prestador='abc', rubro=None), None), ('POST', S, dict(base, prestador=999), None),
        ('POST', S, dict(base, prestador=2), None), ('POST', S, dict(base, prestador=3), None),
        ('POST', S, dict(base, prestador=datos['sanc']), None), ('POST', S, dict(base, rubro=datos['otro_rubro']), None),
        ('POST', S, dict(base, prestador=datos['lejos']), None),
        ('POST', S, dict(base, cliente_telefono='+51987654', cliente_nombre='123', descripcion='   '), None),
        ('POST', S, dict(base, cliente_nombre='', cliente_telefono='', descripcion=''), None),
        ('POST', S, dict(base, cliente_nombre='a' * 101, descripcion='d' * 501), None),
        ('POST', S, dict(base, latitud='1234.5', longitud=True), None), ('POST', S, dict(base, latitud='nan'), None),
        ('POST', S, dict(base, cliente_nombre=5, descripcion=['x']), None),
        ('POST', S, 'prestador=%d&rubro=%d&cliente_nombre=Luz&cliente_telefono=912345678&descripcion=Caño&latitud=-12.046374&longitud=-77.042793' % (A, G), 'form'),
        ('POST', S, base, 'clis'), ('POST', S, base, 'venc'), ('POST', S, base, 'proy'), ('POST', S, base, 'nadie'),
        ('GET', S + '1/', None, None), ('GET', S + '1/?codigo=malo', None, None), ('GET', S + '999/', None, None),
        ('GET', S + '3/', None, T['cli']), ('GET', S + '3/', None, T['otro']), ('GET', S + '1/', None, T['ana']),
        ('GET', S + 'recibidas/', None, None), ('GET', S + 'recibidas/', None, T['cli']),
        ('GET', S + 'recibidas/', None, T['sanc']), ('GET', S + 'recibidas/', None, T['ana']),
        ('GET', S + 'recibidas/1/', None, T['ana']), ('GET', S + 'recibidas/1/', None, T['dino']),
        ('POST', S + '1/avanzar/', {}, T['ana']), ('POST', S + '1/aceptar/', None, T['dino']),
        ('POST', S + '1/aceptar/', None, T['ana']), ('POST', S + '1/aceptar/', None, T['ana']),
        ('POST', S + '1/rechazar/', None, T['ana']), ('POST', S + '2/rechazar/', None, T['ana']),
        ('POST', S + '2/avanzar/', {'estado': 'en_camino'}, T['ana']),
        ('POST', S + '1/avanzar/', {}, T['ana']), ('POST', S + '1/avanzar/', {'estado': 'finalizada'}, T['ana']),
        ('POST', S + '1/avanzar/', {'estado': 'en_camino'}, T['ana']), ('POST', S + '1/avanzar/', {'estado': 'en_camino'}, T['ana']),
        ('POST', S + '1/avanzar/', 'estado=en_servicio', T['ana']), ('POST', S + '1/avanzar/', {'estado': 'finalizada'}, T['ana']),
        ('POST', S + '1/avanzar/', {'estado': 'finalizada'}, T['ana']), ('POST', S + '999/avanzar/', [1], T['ana']),
        ('GET', S + '1/', None, T['ana']), ('GET', S + 'recibidas/', None, T['ana']),
        ('GET', '/api/usuarios/disponibilidad/', None, T['ana']), ('GET', '/api/usuarios/disponibilidad/', None, T['cli']),
        ('PATCH', '/api/usuarios/disponibilidad/', {'disponible': 'off'}, T['ana']),
        ('PATCH', '/api/usuarios/disponibilidad/', {'disponible': 2}, T['ana']),
        ('PATCH', '/api/usuarios/disponibilidad/', {}, T['ana']),
        ('PATCH', '/api/usuarios/disponibilidad/', {'disponible': None}, T['ana']),
        ('POST', S, base, None),
        ('PATCH', '/api/usuarios/disponibilidad/', {'disponible': True}, T['ana']),
        ('POST', S, base, None),
    ]
    iguales, distintos, codigos = 0, [], {}
    for metodo, ruta, cuerpo, tok in pasos:
        tipo = 'application/json'
        if tok == 'form' or (isinstance(cuerpo, str) and '=' in cuerpo):
            tipo = 'application/x-www-form-urlencoded'
        if tok in ('form',): tok = None
        if tok in T: tok = T[tok]
        if isinstance(cuerpo, str) and cuerpo == 'x': cuerpo = '"x"'
        rd = pedir(PD, metodo, ruta, cuerpo, tipo, tok); rs = pedir(PS, metodo, ruta, cuerpo, tipo, tok)
        if isinstance(rd[1], dict) and 'codigo_seguimiento' in rd[1]:
            codigos[rd[1]['id']] = rd[1]['codigo_seguimiento']
        a, b = (rd[0], normal(rd[1]), rd[2] if 'private' in str(rd[2]) else None), (rs[0], normal(rs[1]), rs[2] if 'private' in str(rs[2]) else None)
        if a == b: iguales += 1
        else: distintos.append((metodo, ruta[:60], a, b))
    # El código de seguimiento sirve en los dos.
    for i, c in codigos.items():
        r1, r2 = pedir(PD, 'GET', f'{S}{i}/?codigo={c}'), pedir(PS, 'GET', f'{S}{i}/?codigo={c}')
        if (r1[0], normal(r1[1])) == (r2[0], normal(r2[1])) and r1[0] == 200: iguales += 1
        else: distintos.append(('GET código', i, r1[:2], r2[:2]))
    print(f'Django vs Spring: {iguales} iguales, {len(distintos)} distintos de {len(pasos) + len(codigos)}')
    for d in distintos: print('  DISTINTO', json.dumps(d, ensure_ascii=False)[:900])

    # Lectura cruzada: un Django sobre la base que escribió Spring ve lo mismo que el Django original.
    procesos.append(django('b.sqlite3', PD2)); esperar(PD2)
    cruz = [('GET', S + 'recibidas/', T['ana'])] + [('GET', f'{S}{i}/', T['ana']) for i in codigos] + \
           [('GET', '/api/usuarios/disponibilidad/', T['ana'])]
    malos = 0
    for metodo, ruta, tok in cruz:
        r1, r2 = pedir(PD, metodo, ruta, tok=tok), pedir(PD2, metodo, ruta, tok=tok)
        if (r1[0], normal(r1[1])) != (r2[0], normal(r2[1])):
            malos += 1; print('  CRUCE DISTINTO', ruta, json.dumps([r1[:2], r2[:2]], ensure_ascii=False)[:900])
    print(f'Lectura cruzada (Django lee lo que escribió Spring): {len(cruz) - malos}/{len(cruz)} iguales')
    sys.exit(1 if distintos or malos else 0)
finally:
    for p in procesos: p.terminate()
    srv.shutdown()
