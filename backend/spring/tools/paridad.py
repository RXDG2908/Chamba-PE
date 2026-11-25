"""Compara la API de búsqueda de Django y de Spring sobre la misma base.

Uso (con los dos servidores abiertos sobre la misma db.sqlite3):

    python backend/spring/tools/paridad.py --django http://127.0.0.1:8000 --spring http://127.0.0.1:8080

Compara estado HTTP y JSON de cada consulta. Los números se comparan como números: las
distancias pueden diferir en el último decimal (1 o 2 ulp, por el orden de las operaciones de
coma flotante en Java y Python), así que se cuentan aparte como «iguales numéricamente».
Solo usa la biblioteca estándar y no escribe nada en la base.
"""
import argparse
import http.client
import json
import math
import sys
from urllib.parse import urlsplit

# Diferencia relativa máxima aceptada entre floats (unos pocos ulp de un double).
TOLERANCIA = 1e-12

CONSULTAS_FIJAS = [
    '', 'rubro=abc&latitud=0&longitud=0', 'rubro=0&latitud=0&longitud=0', 'rubro=999&latitud=0&longitud=0',
    'rubro=1.0&latitud=91&longitud=x', 'rubro=1&latitud=nan&longitud=inf', 'rubro=1&latitud=1_0&longitud=1e1',
    'rubro=' + '9' * 1001 + '&latitud=0&longitud=0', 'rubro=1&latitud=' + '1' * 5000 + '&longitud=0',
    'rubro=2&rubro=1&latitud=-12.0464&longitud=-77.0428', 'rubro=1&latitud=1e400&longitud=0',
    'rubro=1&latitud=-0&longitud=Infinity', 'rubro=%201%20&latitud=%20-12.0464%20&longitud=-77.0428',
    'rubro=1_0&latitud=1_0.5&longitud=0', 'rubro=1e0&latitud=0&longitud=0', 'rubro=-1&latitud=0&longitud=0',
    'rubro=1&latitud=.5&longitud=5.', 'rubro=1&latitud=-90&longitud=180', 'rubro=1&latitud=0x1&longitud=1f',
    # Lo que Tomcat rechazaría y Django acepta: % mal formado, | { } [ ], bytes que no son UTF-8.
    'rubro=%ZZ&latitud=1%&longitud=%2', 'rubro={1}&latitud=[0]&longitud=|0|', 'rubro=1&latitud=0&longitud=0&x=%FF',
    'rubro=+1+&latitud=-12.0464&longitud=-77.0428',
    # Dígitos y espacios Unicode (١ = 1 arábigo, U+00A0, U+3000, U+200B) y largo en caracteres.
    'rubro=%D9%A1&latitud=%C2%A0-12.0464%E3%80%80&longitud=-77.0428',
    'rubro=1&latitud=-12.0464%E2%80%8B&longitud=-77.0428',
    'rubro=' + '%F0%9F%98%80' * 1000 + '&latitud=0&longitud=0',
    'rubro=' + '%F0%9F%98%80' * 1001 + '&latitud=0&longitud=0',
]
PUNTOS = [(-12.0464, -77.0428), (-12.12, -77.03), (-11.99, -77.06), (0, 0)]


def pedir(base, ruta):
    """GET con la ruta tal cual (sin volver a codificarla). Devuelve (estado, json, cabeceras)."""
    partes = urlsplit(base)
    conexion = http.client.HTTPConnection(partes.hostname, partes.port, timeout=20)
    try:
        conexion.putrequest('GET', ruta, skip_accept_encoding=True)
        conexion.endheaders()
        respuesta = conexion.getresponse()
        cuerpo = respuesta.read().decode('utf-8')
        try:
            datos = json.loads(cuerpo)
        except ValueError:
            datos = {'_no_json': cuerpo[:120]}
        return respuesta.status, datos, dict(respuesta.getheaders())
    finally:
        conexion.close()


def comparar(a, b):
    """'igual', 'numerico' (floats a pocos ulp) o 'distinto'."""
    if type(a) is not type(b) and not (isinstance(a, (int, float)) and isinstance(b, (int, float))):
        return 'distinto'
    if isinstance(a, dict):
        if a.keys() != b.keys():
            return 'distinto'
        return peor(comparar(a[k], b[k]) for k in a)
    if isinstance(a, list):
        if len(a) != len(b):
            return 'distinto'
        return peor(comparar(x, y) for x, y in zip(a, b))
    if isinstance(a, float) or isinstance(b, float):
        if a == b:
            return 'igual'
        return 'numerico' if math.isclose(a, b, rel_tol=TOLERANCIA, abs_tol=1e-15) else 'distinto'
    return 'igual' if a == b else 'distinto'


def peor(resultados):
    orden = {'igual': 0, 'numerico': 1, 'distinto': 2}
    return max(resultados, key=orden.get, default='igual')


def main():
    argumentos = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    argumentos.add_argument('--django', default='http://127.0.0.1:8000')
    argumentos.add_argument('--spring', default='http://127.0.0.1:8080')
    opciones = argumentos.parse_args()

    estado_d, rubros_d, _ = pedir(opciones.django, '/api/prestadores/rubros/')
    estado_s, rubros_s, _ = pedir(opciones.spring, '/api/prestadores/rubros/')
    consultas = list(CONSULTAS_FIJAS)
    for rubro in rubros_d if estado_d == 200 else []:
        for latitud, longitud in PUNTOS:
            consultas.append(f'rubro={rubro["id"]}&latitud={latitud}&longitud={longitud}')

    conteo = {'igual': 0, 'numerico': 0, 'distinto': 0}
    conteo[comparar([estado_d, rubros_d], [estado_s, rubros_s])] += 1
    prestadores = 0
    for consulta in consultas:
        ruta = '/api/prestadores/buscar/?' + consulta
        estado_d, datos_d, _ = pedir(opciones.django, ruta)
        estado_s, datos_s, cabeceras_s = pedir(opciones.spring, ruta)
        resultado = comparar([estado_d, datos_d], [estado_s, datos_s])
        if estado_s == 200 and 'X-Execution-Time-Ms' not in cabeceras_s:
            resultado = 'distinto'
        conteo[resultado] += 1
        if isinstance(datos_d, list):
            prestadores += len(datos_d)
        if resultado == 'distinto':
            print(f'DISTINTO {consulta[:80]}\n  Django {estado_d}: {json.dumps(datos_d)[:200]}'
                  f'\n  Spring {estado_s}: {json.dumps(datos_s)[:200]}')

    print(f'Respuestas comparadas: {len(consultas) + 1} (rubros + {len(consultas)} búsquedas, '
          f'{prestadores} prestadores en total)')
    print(f'  idénticas: {conteo["igual"]}')
    print(f'  iguales numéricamente (floats a menos de {TOLERANCIA:g} relativo): {conteo["numerico"]}')
    print(f'  distintas: {conteo["distinto"]}')
    return 1 if conteo['distinto'] else 0


if __name__ == '__main__':
    sys.exit(main())
