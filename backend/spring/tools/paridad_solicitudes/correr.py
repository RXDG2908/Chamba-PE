"""Mismo escenario (solicitudes, registro de la app y calificaciones) contra Django (base a) y Spring (base b);
luego Django lee la base b.

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
def token(uid, aud=PROYECTO, venc=3600, **extra):
    ahora = int(time.time())
    return gjwt.encode(firmante, {'iss': f'https://securetoken.google.com/{aud}', 'aud': aud, 'sub': uid,
                                  'iat': ahora - 30, 'exp': ahora + venc, 'auth_time': ahora - 30, **extra}).decode()

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

def pedir(puerto, metodo, ruta, cuerpo=None, tipo='application/json', tok=None, extra=None):
    con = http.client.HTTPConnection('127.0.0.1', puerto, timeout=30)
    cab = dict(extra or {})
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
    RE, PUNTO = '/api/ia/recomendacion/', 'latitud=-12.046374&longitud=-77.042793'
    pasos = [
        ('GET', f'{RE}?rubro={G}&{PUNTO}', None, None),
        # Sin cuenta o con una cuenta que no es de cliente: 403 en los dos (bug del 27/11).
        ('POST', S, base, None), ('POST', S, {}, None), ('POST', S, base, T['ana']),
        ('POST', S, base, 'cli'), ('POST', S, dict(base, latitud=-12.0463741), 'cli'),
        ('POST', S, dict(base, cliente_nombre='Ana', descripcion='Con cuenta'), T['cli']),
        ('POST', S, {}, 'cli'), ('POST', S, [1], 'cli'), ('POST', S, 'x', 'cli'),
        ('POST', S, dict(base, prestador='abc', rubro=None), 'cli'), ('POST', S, dict(base, prestador=999), 'cli'),
        ('POST', S, dict(base, prestador=2), 'cli'), ('POST', S, dict(base, prestador=3), 'cli'),
        ('POST', S, dict(base, prestador=datos['sanc']), 'cli'), ('POST', S, dict(base, rubro=datos['otro_rubro']), 'cli'),
        ('POST', S, dict(base, prestador=datos['lejos']), 'cli'),
        ('POST', S, dict(base, cliente_telefono='+51987654', cliente_nombre='123', descripcion='   '), 'cli'),
        ('POST', S, dict(base, cliente_nombre='', cliente_telefono='', descripcion=''), 'cli'),
        ('POST', S, dict(base, cliente_nombre='a' * 101, descripcion='d' * 501), 'cli'),
        ('POST', S, dict(base, latitud='1234.5', longitud=True), 'cli'), ('POST', S, dict(base, latitud='nan'), 'cli'),
        ('POST', S, dict(base, cliente_nombre=5, descripcion=['x']), 'cli'),
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
        ('GET', f'{RE}?rubro={G}&{PUNTO}', None, None),
        ('POST', S, base, 'cli'),
        ('PATCH', '/api/usuarios/disponibilidad/', {'disponible': True}, T['ana']),
        ('POST', S, base, 'cli'),
    ]
    iguales, distintos, codigos = 0, [], {}
    for metodo, ruta, cuerpo, tok in pasos:
        tipo = 'application/json'
        if tok == 'form' or (isinstance(cuerpo, str) and '=' in cuerpo):
            tipo = 'application/x-www-form-urlencoded'
        if tok in ('form',): tok = 'cli'
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
    total = len(pasos) + len(codigos)

    # Registro de la app y calificaciones (HU-12): los ids salen de la respuesta de Django.
    def paso(metodo, ruta, cuerpo=None, tok=None, tipo='application/json', extra=None, solo_estado=False):
        # solo_estado: un 500 de Django es una página (HTML o texto), no JSON; basta el código.
        nonlocal_estado['total'] += 1
        rd = pedir(PD, metodo, ruta, cuerpo, tipo, tok, extra); rs = pedir(PS, metodo, ruta, cuerpo, tipo, tok, extra)
        if os.environ.get('PARIDAD_VER'):
            print(' ', rd[0], metodo, ruta[:40], json.dumps(rd[1], ensure_ascii=False)[:150])
        if solo_estado and rd[0] == rs[0] or (rd[0], normal(rd[1])) == (rs[0], normal(rs[1])): nonlocal_estado['iguales'] += 1
        else: distintos.append((metodo, ruta[:60], json.dumps(cuerpo, ensure_ascii=False)[:80] if cuerpo is not None else None, rd[:2], rs[:2]))
        return rd
    nonlocal_estado = {'total': 0, 'iguales': 0}
    web_sin_csrf = lambda quien: {'Cookie': f'sessionid={datos["sesiones"][quien]}', 'Origin': 'http://localhost:5173'}
    RC, RP, C = '/api/usuarios/registro-movil/', '/api/prestadores/registro-movil/', '/api/calificaciones/'
    nuevo = lambda uid, **extra: token(uid, **extra)
    paso('POST', RC, {'nombre': 'X'})
    paso('POST', RC, {'nombre': 'X'}, 'Bearer')
    paso('POST', RC, {'nombre': 'X'}, T['venc'])
    t1 = nuevo('uid-nuevo1', email='Nuevo.Uno@Example.COM', name='Nombre Token')
    paso('POST', RC, {'nombre': '  Luz Nueva  '}, t1); paso('POST', RC, {'nombre': 'Otro'}, t1)
    paso('POST', RC, {}, T['ana'])
    paso('POST', RC, {}, nuevo('uid-nuevo2', email='CLIENTE@EXAMPLE.INVALID'))
    paso('POST', RC, {}, nuevo('uid-nuevo3', name='  Solo Token  '))
    paso('POST', RC, {'nombre': 5}, nuevo('uid-nuevo4'))
    paso('POST', RC, {'nombre': '', 'x': 1}, nuevo('uid-nuevo5', email='  sin.nombre@EJEMPLO.pe '))
    paso('POST', RC, 'nombre=Formulario', nuevo('uid-nuevo6'), 'application/x-www-form-urlencoded')
    paso('POST', RC, None, nuevo('uid-nuevo7', name='N' * 160))
    paso('GET', '/api/calificaciones/mis-servicios/', None, t1)
    # Una cuenta para la web y la app: vincular la cuenta de Firebase con la de la web, con su contraseña.
    V = '/api/usuarios/vincular/'
    paso('POST', V, {'password': 'x'}); paso('POST', V, {'password': 'x'}, T['venc'])
    paso('POST', V, {'password': 'x'}, T['ana'])  # ya vinculada
    tl = nuevo('uid-lucia', email='Web_Lucia@Example.invalid')
    for cuerpo in ({}, [1], 'null', {'password': 5}, {'password': ''}, {'password': 'mala'}):
        paso('POST', V, cuerpo, tl)
    paso('POST', V, {'password': 'clave-de-la-web-1'}, nuevo('uid-sin-correo'))
    paso('POST', V, {'password': 'clave-de-la-web-1'}, nuevo('uid-nadie', email='nadie@example.invalid'))
    paso('POST', V, {'password': 'clave-de-la-web-1'}, nuevo('uid-adm', email='web_admin@example.invalid'))
    paso('POST', V, {'password': 'clave-de-la-web-1'}, nuevo('uid-susp', email='web_susp@example.invalid'))
    paso('POST', V, 'password=clave-de-la-web-1', tl, 'application/x-www-form-urlencoded')  # vincula
    paso('POST', V, {'password': 'clave-de-la-web-1'}, tl)  # la misma otra vez
    paso('POST', V, {'password': 'clave-de-la-web-1'}, nuevo('uid-lucia-2', email='web_lucia@example.invalid'))
    paso('GET', '/api/usuarios/yo/', None, tl)  # ya entra con su token
    tb = nuevo('uid-bloq', email='web_bloq@example.invalid')
    for _ in range(10):
        paso('POST', V, {'password': 'mala'}, tb)
    paso('POST', V, {'password': 'clave-de-la-web-1'}, tb)  # 429: diez fallos en la hora
    paso('POST', RP, {}); paso('POST', RP, {}, T['cli'])
    paso('POST', RP, {}, nuevo('uid-p0', email='ana@example.invalid'))
    tp = nuevo('uid-p1', email='Pedro@Ejemplo.PE')
    for cuerpo in ({}, [1], 'null', {'nombres': '  ', 'apellidos': 'a' * 101, 'dni': '123456789', 'telefono': 'x' * 21,
                   'rubros': [], 'latitud': '100', 'longitud': 'abc', 'radio_cobertura_km': 0},
                   {'nombres': 5, 'apellidos': True, 'dni': '1234567a', 'telefono': None, 'rubros': '1', 'latitud': '-12.1234567',
                    'longitud': -77.0, 'radio_cobertura_km': '5.5'},
                   {'nombres': 'Ana\u0000', 'apellidos': ['x'], 'dni': '10000001', 'rubros': [999], 'latitud': None,
                    'radio_cobertura_km': 101},
                   {'nombres': 'A', 'apellidos': 'B', 'dni': ' 1234 ', 'rubros': [True], 'latitud': 0, 'longitud': 0,
                    'radio_cobertura_km': True},
                   {'nombres': 'A', 'apellidos': 'B', 'dni': '12345678', 'rubros': [None], 'latitud': 0, 'longitud': 0,
                    'radio_cobertura_km': 1e3},
                   {'nombres': 'A', 'apellidos': 'B', 'dni': '12345678', 'rubros': [datos['gas'], 'x'], 'latitud': 0,
                    'longitud': 0, 'radio_cobertura_km': [5]}):
        paso('POST', RP, cuerpo, tp)
    valido = {'nombres': ' Pedro ', 'apellidos': 'Pérez Ñol', 'dni': '20000001', 'telefono': ' 987654321 ',
              'rubros': [datos['otro_rubro'], datos['gas']], 'latitud': '-12.05', 'longitud': -77.04, 'radio_cobertura_km': '10'}
    paso('POST', RP, valido, tp); paso('POST', RP, valido, tp)
    paso('POST', RP, dict(valido, dni='20000001'), nuevo('uid-p2'))
    tp3 = nuevo('uid-p3')
    paso('POST', RP, {k: v for k, v in dict(valido, dni='\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668', radio_cobertura_km=5.0).items() if k != 'telefono'}, tp3)
    for tok in (t1, tp, tp3):
        paso('GET', '/api/usuarios/disponibilidad/', None, tok)

    # Tres servicios del cliente, finalizados, para calificar y mover el promedio de Ana.
    finalizadas = []
    for n in range(3):
        sid = paso('POST', S, dict(base, descripcion=f'Para calificar {n}'), T['cli'])[1]['id']
        paso('POST', f'{S}{sid}/aceptar/', None, T['ana'])
        for etapa in ('en_camino', 'en_servicio', 'finalizada'):
            paso('POST', f'{S}{sid}/avanzar/', {'estado': etapa}, T['ana'])
        finalizadas.append(sid)
    pendiente = paso('POST', S, dict(base, descripcion='Sin terminar'), T['cli'])[1]['id']
    paso('POST', C, {'solicitud': pendiente, 'estrellas': 5}, T['cli'])
    MS = C + 'mis-servicios/'
    paso('GET', MS); paso('GET', MS, None, T['ana']); paso('GET', MS, None, T['cli']); paso('POST', MS, {}, T['cli'])
    paso('POST', C, {'solicitud': finalizadas[0], 'estrellas': 4}); paso('POST', C, {'solicitud': finalizadas[0], 'estrellas': 4}, T['ana'])
    for cuerpo in ({}, [1], {'solicitud': '5', 'estrellas': 4.5}, {'solicitud': 0, 'estrellas': 0},
                   {'solicitud': True, 'estrellas': 6, 'comentario': 'x' * 501}, {'solicitud': None, 'estrellas': None, 'comentario': None},
                   {'solicitud': finalizadas[0], 'estrellas': [4], 'comentario': 5}, {'solicitud': 3, 'estrellas': 5},
                   {'solicitud': 1, 'estrellas': 5}, {'solicitud': 999999, 'estrellas': 5}, {'solicitud': 10 ** 30, 'estrellas': 5}):
        paso('POST', C, cuerpo, T['cli'])
    paso('POST', C, 'solicitud=%d&estrellas=5' % finalizadas[0], T['cli'], 'application/x-www-form-urlencoded')
    paso('POST', C, {'solicitud': finalizadas[0], 'estrellas': 4}, T['otro'])
    paso('POST', C, {'solicitud': finalizadas[0], 'estrellas': 4, 'comentario': '  Muy bien, puntual  '}, T['cli'])
    paso('POST', C, {'solicitud': finalizadas[0], 'estrellas': 5}, T['cli'])
    paso('POST', C, {'solicitud': finalizadas[1], 'estrellas': 5}, T['cli'])
    paso('POST', C, {'solicitud': finalizadas[2], 'estrellas': 4, 'comentario': '   '}, T['cli'])
    paso('GET', MS, None, T['cli'])
    paso('GET', '/api/prestadores/buscar/?rubro=%d&latitud=-12.046374&longitud=-77.042793' % G)

    # HU-10: recomendación con IA. Pública como la búsqueda, con su misma validación.
    R = datos['reco']
    reco = lambda consulta, **k: paso('GET', f'{RE}?{consulta}', **k)
    for consulta in ('', 'rubro=abc&latitud=0&longitud=0', 'rubro=0&latitud=0&longitud=0', 'rubro=999&latitud=0&longitud=0',
                     'rubro=1.0&latitud=91&longitud=x', 'rubro=1&latitud=nan&longitud=inf', 'latitud=0',
                     'rubro=%ZZ&latitud=1%&longitud=%2', 'rubro=' + '9' * 1001 + '&latitud=0&longitud=0',
                     'rubro=1&latitud=' + '1' * 5000 + '&longitud=0', 'rubro=1&latitud=-0&longitud=Infinity',
                     f'rubro={R}&rubro=abc&{PUNTO}', f'rubro=abc&rubro={R}&{PUNTO}',
                     f'rubro=%20{R}%20&latitud=%C2%A0-12.046374%E3%80%80&longitud=-77.042793',
                     f'rubro={R}.0&latitud=-12.046374&longitud=-77.042793&x=%FF'):
        reco(consulta)
    for lat, lon in ((-12.046374, -77.042793), (-12.0464, -77.0428), (-12.03, -77.042793), (-12.12, -77.03),
                     (-12.0, -77.1), (0, 0), (-90, 180)):
        reco(f'rubro={R}&latitud={lat}&longitud={lon}'); reco(f'rubro={G}&latitud={lat}&longitud={lon}')
    # No autentica (authentication_classes = []): ni un token vencido ni una sesión sancionada la bloquean.
    for tok, extra in ((T['venc'], None), (T['ana'], None), ('Bearer', None), (None, web_sin_csrf('cli_susp')),
                       (None, {'Cookie': 'sessionid=inventada123'})):
        reco(f'rubro={R}&{PUNTO}', tok=tok, extra=extra)
    for metodo in ('POST', 'PUT', 'PATCH', 'DELETE'):
        paso(metodo, f'{RE}?rubro={R}&{PUNTO}', {})
    paso('HEAD', f'{RE}?rubro={R}&{PUNTO}')
    # Los servicios en curso los escribe cada servidor y bajan el puntaje de Rocío (pendiente y finalizada no cuentan).
    TR = token('uid-r_ocupado')
    nueva = lambda: paso('POST', S, dict(base, prestador=datos['r_ocupado'], rubro=R, descripcion='Para HU-10'), T['cli'])[1]['id']
    s1 = nueva(); reco(f'rubro={R}&{PUNTO}')
    paso('POST', f'{S}{s1}/aceptar/', None, TR); reco(f'rubro={R}&{PUNTO}')
    s2 = nueva(); paso('POST', f'{S}{s2}/aceptar/', None, TR); paso('POST', f'{S}{s2}/avanzar/', {'estado': 'en_camino'}, TR)
    reco(f'rubro={R}&{PUNTO}')
    for etapa in ('en_camino', 'en_servicio'):
        paso('POST', f'{S}{s1}/avanzar/', {'estado': etapa}, TR)
    s3 = nueva(); paso('POST', f'{S}{s3}/aceptar/', None, TR); reco(f'rubro={R}&{PUNTO}')
    paso('POST', f'{S}{s1}/avanzar/', {'estado': 'finalizada'}, TR); s4 = nueva(); paso('POST', f'{S}{s4}/rechazar/', None, TR)
    reco(f'rubro={R}&{PUNTO}'); reco(f'rubro={R}&latitud=-12.03&longitud=-77.042793')
    # Rita se desconecta: sale de la recomendación.
    paso('PATCH', '/api/usuarios/disponibilidad/', {'disponible': False}, token('uid-r_cerca')); reco(f'rubro={R}&{PUNTO}')

    # La web con la sesión de Django: SessionAuthentication va antes que el token y exige CSRF.
    SES, CSRF, MASC = datos['sesiones'], datos['csrf'], datos['csrf_enmascarado']
    WEB = 'http://localhost:5173'
    def web(quien, csrf=True, token=MASC, origen=WEB, cookie_csrf=CSRF):
        cab = {'Cookie': f'sessionid={SES[quien]}' + (f'; csrftoken={cookie_csrf}' if cookie_csrf else '')}
        if csrf: cab['X-CSRFToken'] = token
        if origen: cab['Origin'] = origen
        return cab
    paso('GET', MS, None, None, extra=web('cli', csrf=False, cookie_csrf=None))
    paso('POST', C, {}, None, extra=web('cli', csrf=False, cookie_csrf=None))
    paso('POST', C, {}, None, extra=web('cli', csrf=False))
    paso('POST', C, {}, None, extra=web('cli', token='corto'))
    paso('POST', C, {}, None, extra=web('cli', token='a' * 31 + '!'))
    paso('POST', C, {}, None, extra=web('cli', token='b' * 64))
    paso('POST', C, {}, None, extra=web('cli', cookie_csrf='malo'))
    paso('POST', C, {}, None, extra=web('cli', origen='http://otro-sitio.example'))
    paso('POST', C, {}, None, extra=web('cli'))
    paso('POST', C, {}, None, extra=web('cli', token=CSRF))
    paso('POST', C, {}, None, extra=web('cli', origen=None))
    sid = paso('POST', S, dict(base, descripcion='Desde la web'), None, extra=web('cli'))[1]['id']
    paso('POST', S, dict(base, descripcion='Sin CSRF'), None, extra=web('cli', csrf=False))
    paso('GET', f'{S}{sid}/', None, None, extra=web('cli', csrf=False))
    paso('GET', MS, None, None, extra=web('cli', csrf=False))
    paso('GET', S + 'recibidas/', None, None, extra=web('ana', csrf=False))
    paso('POST', f'{S}{sid}/aceptar/', None, None, extra=web('ana'))
    paso('PATCH', '/api/usuarios/disponibilidad/', {'disponible': True}, None, extra=web('ana'))
    paso('PATCH', '/api/usuarios/disponibilidad/', {'disponible': True}, None, extra=web('ana', csrf=False))
    # La sesión gana sobre el token; una sesión que no autentica deja pasar al token.
    paso('GET', MS, None, T['ana'], extra=web('cli', csrf=False))
    paso('GET', MS, None, None, extra=web('cli_hash_malo', csrf=False))
    paso('GET', MS, None, T['cli'], extra=web('cli_hash_malo', csrf=False))
    paso('GET', MS, None, None, extra={'Cookie': 'sessionid=inventada123'})
    # HU-13: sesión de una cuenta sancionada.
    paso('POST', S, base, None, extra=web('cli_susp', csrf=False))
    paso('GET', MS, None, None, extra=web('cli_susp', csrf=False))
    paso('GET', S + 'recibidas/', None, None, extra=web('sanc', csrf=False))
    # 26/11: perfil de la app (GET /yo con token), 403 con codigo y reglas de nombre y teléfono.
    for tok in (t1, tp, T['ana'], T['cli'], T['nadie'], T['sanc'], T['clis'], T['venc'], None):
        paso('GET', '/api/usuarios/yo/', None, tok)
    tp4 = nuevo('uid-p4')
    for nombres, telefono in (('Rosa 🔧', 'abc'), ('Ana	María', '12345'), ('​', '+51 (01) 987-654-321'),
                              ('  Ana   Peña ', '+51 987 654 321'), ("O'Connor-Ñúñez.", '51987654321')):
        paso('POST', RP, dict(valido, dni='20000004', nombres=nombres, apellidos=nombres, telefono=telefono), tp4)
    paso('POST', S, dict(base, cliente_nombre='  Ana   María. '), T['cli'])
    paso('POST', S, dict(base, cliente_nombre='Ana	María'), T['cli'])
    # HU-8: token FCM de la web del prestador (POST registra, DELETE quita). Va con la sesión de Django.
    NT = '/api/servicios/notificaciones/token/'
    FCM = 'fcm-de-prueba_' + 'Ab9:-' * 30
    paso('POST', NT, {'token': FCM}); paso('POST', NT, {'token': FCM}, T['cli']); paso('POST', NT, {'token': FCM}, T['sanc'])
    paso('POST', NT, {'token': FCM}, None, extra=web('cli')); paso('POST', NT, {'token': FCM}, None, extra=web('ana', csrf=False))
    paso('GET', NT, None, None, extra=web('ana', csrf=False)); paso('GET', NT, None, None, extra=web('cli', csrf=False))
    for cuerpo in ({}, [1], 'null', '"x"', {'token': ''}, {'token': '   '}, {'token': None}, {'token': True}, {'token': ['x']},
                   {'token': {'a': 1}}, {'token': 'corto'}, {'token': 'a' * 19 + '!'}, {'token': 'a' * 2049},
                   {'token': 'a' * 2049 + '/'}, {'token': 'a' * 30 + '\u0000'}, {'token': 'ñ' * 25}, {'token': 'a' * 20 + ' b'},
                   {'token': 'a' * 20 + '\n'}, {'token': 1.5}, {'otro': FCM}):
        paso('POST', NT, cuerpo, None, extra=web('ana'))
    paso('POST', NT, {'token': FCM}, None, extra=web('ana'))
    paso('POST', NT, {'token': FCM}, None, extra=web('ana'))  # mismo token: se actualiza, no se duplica
    paso('POST', NT, {'token': '  ' + FCM[:40] + '\t\n'}, None, extra=web('ana'))  # se registra sin los espacios
    paso('POST', NT, {'token': 12345678901234567890123}, None, extra=web('ana'))  # DRF acepta números como texto
    paso('POST', NT, {'token': 'a' * 2048}, None, extra=web('ana'))
    paso('POST', NT, 'token=' + 'formulario-' * 3, None, 'application/x-www-form-urlencoded', extra=web('ana'))
    # Con el token de la app no hay sesión de Django: session_key queda en None y la base lo rechaza (500 en los dos).
    paso('POST', NT, {'token': 'sin-sesion-' * 3}, T['ana'], solo_estado=True)
    paso('POST', NT, {'token': 'sesion-inventada-' * 2}, T['ana'], extra={'Cookie': 'sessionid=inventada123'}, solo_estado=True)
    paso('POST', NT, {'token': 'sesion-hash-malo-' * 2}, T['ana'], extra=web('cli_hash_malo', csrf=False), solo_estado=True)
    paso('POST', NT, {'token': 'sesion-sancionada-' * 2}, T['ana'], extra=web('cli_susp', csrf=False))  # la sesión sigue
    for cuerpo in ({}, [1], {'token': 'corto'}):
        paso('DELETE', NT, cuerpo, None, extra=web('ana'))
    paso('DELETE', NT, {'token': FCM}); paso('DELETE', NT, {'token': FCM}, None, extra=web('ana', csrf=False))
    paso('DELETE', NT, {'token': FCM}, None, extra=web('cli'))
    paso('DELETE', NT, {'token': 'formulario-' * 3}, T['cli'])
    paso('DELETE', NT, {'token': FCM}, None, extra=web('ana'))
    paso('DELETE', NT, {'token': FCM}, None, extra=web('ana'))  # ya no está: también 204
    paso('DELETE', NT, {'token': 'a' * 2048}, T['dino'])  # de otro prestador: no lo borra
    paso('PATCH', NT, {'token': FCM}, None, extra=web('ana'))
    iguales += nonlocal_estado['iguales']; total += nonlocal_estado['total']
    print(f'Django vs Spring: {iguales} iguales, {len(distintos)} distintos de {total}')
    for d in distintos: print('  DISTINTO', json.dumps(d, ensure_ascii=False)[:900])

    # Lectura cruzada: un Django sobre la base que escribió Spring ve lo mismo que el Django original.
    procesos.append(django('b.sqlite3', PD2)); esperar(PD2)
    cruz = [('GET', S + 'recibidas/', T['ana'])] + [('GET', f'{S}{i}/', T['ana']) for i in codigos] + \
           [('GET', '/api/usuarios/disponibilidad/', T['ana']), ('GET', MS, T['cli'])] + \
           [('GET', '/api/usuarios/yo/', tok) for tok in (t1, tp, tp3)] + [('GET', '/api/usuarios/disponibilidad/', tp)] + \
           [('GET', f'{RE}?rubro={r}&{PUNTO}', None) for r in (R, G)]
    malos = 0
    for metodo, ruta, tok in cruz:
        r1, r2 = pedir(PD, metodo, ruta, tok=tok), pedir(PD2, metodo, ruta, tok=tok)
        if (r1[0], normal(r1[1])) != (r2[0], normal(r2[1])):
            malos += 1; print('  CRUCE DISTINTO', ruta, json.dumps([r1[:2], r2[:2]], ensure_ascii=False)[:900])
    # Los tokens FCM no tienen ruta de lectura: se comparan las filas que dejaron Django (a) y Spring (b).
    import sqlite3
    def tokens(base):
        with sqlite3.connect(AQUI / base) as con:
            return con.execute('select usuario_id, token, sesion from servicios_tokenfcm order by token').fetchall()
    if tokens('a.sqlite3') != tokens('b.sqlite3'):
        malos += 1; print('  TOKENS FCM DISTINTOS', tokens('a.sqlite3'), tokens('b.sqlite3'))
    cruz.append('tokens FCM')
    print(f'Lectura cruzada (Django lee lo que escribió Spring): {len(cruz) - malos}/{len(cruz)} iguales')
    sys.exit(1 if distintos or malos else 0)
finally:
    for p in procesos: p.terminate()
    srv.shutdown()
