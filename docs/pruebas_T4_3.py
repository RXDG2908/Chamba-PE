r"""Reproduce T4.3 in an isolated archive; never merges or uses local DB/media.

Run from the repository root: .\venv\Scripts\python.exe docs/pruebas_T4_3.py
"""
import ast
from hashlib import sha256
from io import BytesIO
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
SEARCH_REF = '5d2e1dbc0e6ad2362307800e37f0ad9bb5592e5a'
REQUEST_REF = 'eb39859273a265b3e8907ccf6af0099138bc3ac1'
REQUEST_FILES = (
    'core/settings.py', 'core/urls.py', 'servicios/admin.py', 'servicios/apps.py',
    'servicios/migrations/0001_initial.py', 'servicios/migrations/__init__.py',
    'servicios/models.py', 'servicios/serializers.py', 'servicios/tests.py',
    'servicios/urls.py', 'servicios/views.py',
)


FLOW_TESTS = r'''
import base64
import json

from django.contrib.auth import get_user_model
from django.test import Client, TestCase
from django.urls import reverse

from prestadores.models import Prestador, Rubro
from servicios.models import Solicitud


class FlujoT43Tests(TestCase):
    def setUp(self):
        self.rubro = Rubro.objects.create(nombre='Rubro T4.3 aislado')
        self.usuario = get_user_model().objects.create_user(
            username='prestador-t43', password='Clave-pruebas-T43-123')
        self.prestador = Prestador.objects.create(
            usuario=self.usuario, nombres='Prestador', apellidos='Prueba T43',
            dni='77777777', latitud=0, longitud=0, radio_cobertura_km=5,
            disponible=True)
        self.prestador.rubros.set([self.rubro])
        self.cliente = Client()
        self.proveedor = Client()
        token = base64.b64encode(b'prestador-t43:Clave-pruebas-T43-123').decode()
        self.auth = {'HTTP_AUTHORIZATION': f'Basic {token}'}
        self.evidencia = []

    def registrar(self, etapa, respuesta):
        self.evidencia.append({'etapa': etapa, 'http': respuesta.status_code,
                              'respuesta': respuesta.json()})

    def flujo(self, accion, estado, etiqueta):
        busqueda = self.cliente.get(reverse('buscar_prestadores'), {
            'rubro': self.rubro.pk, 'latitud': '0.010000', 'longitud': '0.000000'})
        self.assertEqual(busqueda.status_code, 200)
        self.assertEqual(len(busqueda.json()), 1)
        candidato = busqueda.json()[0]
        self.assertEqual(candidato['id'], self.prestador.pk)
        self.assertIn(self.rubro.pk, [r['id'] for r in candidato['rubros']])
        self.registrar('buscar', busqueda)

        envio = self.cliente.post(reverse('crear_solicitud'), {
            'prestador': candidato['id'], 'rubro': self.rubro.pk,
            'cliente_nombre': 'Cliente T43', 'cliente_telefono': '999000111',
            'descripcion': f'Solicitud de prueba para {accion}',
            'latitud': '0.010000', 'longitud': '0.000000',
        }, content_type='application/json')
        self.assertEqual(envio.status_code, 201)
        self.assertEqual(envio.json()['estado'], 'pendiente')
        solicitud_id = envio.json()['id']
        solicitud = Solicitud.objects.get(pk=solicitud_id)
        self.assertEqual(solicitud.prestador_id, candidato['id'])
        self.assertEqual(solicitud.rubro_id, self.rubro.pk)
        self.assertIsNone(solicitud.respondida)
        self.registrar('solicitar', envio)

        detalle_url = reverse('detalle_solicitud', args=[solicitud_id])
        pendiente = self.cliente.get(detalle_url)
        self.assertEqual(pendiente.status_code, 200)
        self.assertEqual(pendiente.json()['estado'], 'pendiente')
        self.assertIsNone(pendiente.json()['respondida'])
        self.registrar('consulta_cliente_pendiente', pendiente)

        bandeja = self.proveedor.get(reverse('solicitudes_recibidas'), **self.auth)
        self.assertEqual(bandeja.status_code, 200)
        self.assertEqual([s['id'] for s in bandeja.json()], [solicitud_id])
        self.assertEqual(bandeja.json()[0]['estado'], 'pendiente')
        self.assertEqual(bandeja.json()[0]['cliente_telefono'], '999000111')
        self.registrar('bandeja_prestador', bandeja)

        respuesta = self.proveedor.post(
            reverse(f'{accion}_solicitud', args=[solicitud_id]), **self.auth)
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta.json()['estado'], estado)
        self.assertIsNotNone(respuesta.json()['respondida'])
        self.registrar(accion, respuesta)

        final = self.cliente.get(detalle_url)
        self.assertEqual(final.status_code, 200)
        self.assertEqual(final.json()['id'], solicitud_id)
        self.assertEqual(final.json()['estado'], estado)
        self.assertEqual(final.json()['estado_display'], etiqueta)
        self.assertEqual(final.json()['respondida'], respuesta.json()['respondida'])
        self.assertNotIn('cliente_telefono', final.json())
        self.assertNotIn('cliente_nombre', final.json())
        solicitud.refresh_from_db()
        self.assertEqual(solicitud.estado, estado)
        self.assertIsNotNone(solicitud.respondida)
        self.registrar('consulta_cliente_final', final)

        contraria = 'rechazar' if accion == 'aceptar' else 'aceptar'
        repetida = self.proveedor.post(
            reverse(f'{contraria}_solicitud', args=[solicitud_id]), **self.auth)
        self.assertEqual(repetida.status_code, 409)
        solicitud.refresh_from_db()
        self.assertEqual(solicitud.estado, estado)
        self.registrar('segunda_respuesta_bloqueada', repetida)
        print('EVIDENCIA_T43 ' + json.dumps({'caso': accion, 'pasos': self.evidencia},
                                           ensure_ascii=True), flush=True)

    def test_buscar_solicitar_aceptar_y_consultar(self):
        self.flujo('aceptar', 'aceptada', 'Aceptada')

    def test_buscar_solicitar_rechazar_y_consultar(self):
        self.flujo('rechazar', 'rechazada', 'Rechazada')
'''


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def snapshot():
    archivos = [ROOT / 'prestadores/tests.py', ROOT / 'certificaciones/tests.py',
                ROOT / 'docs/pruebas-T2.3.md', ROOT / 'db.sqlite3']
    media = ROOT / 'media'
    if media.exists():
        archivos.extend(p for p in media.rglob('*') if p.is_file())
    return {str(p.relative_to(ROOT)): sha256(p.read_bytes()).hexdigest()
            for p in archivos if p.exists()}


def shared_definitions(ref, filename):
    tree = ast.parse(git('show', f'{ref}:{filename}').decode('utf-8'))
    return {n.name: ast.dump(n, include_attributes=False) for n in tree.body
            if isinstance(n, (ast.ClassDef, ast.FunctionDef))}


def main():
    # Ensure the overlay preserves every existing search definition and test.
    for filename in ('servicios/views.py', 'servicios/serializers.py', 'servicios/tests.py'):
        before = shared_definitions(SEARCH_REF, filename)
        after = shared_definitions(REQUEST_REF, filename)
        for name, definition in before.items():
            if after.get(name) != definition:
                raise RuntimeError(f'Search definition differs: {filename}:{name}')
    for filename in ('servicios/distancias.py', 'prestadores/urls.py',
                     'frontend/src/ResultadosPrestadores.jsx'):
        if git('show', f'{SEARCH_REF}:{filename}') != git('show', f'{REQUEST_REF}:{filename}'):
            raise RuntimeError(f'Shared file differs: {filename}')

    original_head = git('rev-parse', 'HEAD')
    original_branch = git('branch', '--show-current')
    original_status = git('status', '--porcelain')
    protected = snapshot()
    with TemporaryDirectory(prefix='chambape-t43-') as directory:
        isolated = Path(directory).resolve()
        print(f'ISOLATED_COPY {isolated}', flush=True)
        with ZipFile(BytesIO(git('archive', '--format=zip', SEARCH_REF))) as archive:
            for member in archive.infolist():
                target = (isolated / member.filename).resolve()
                if not target.is_relative_to(isolated):
                    raise RuntimeError('Archive path escapes isolated directory')
            archive.extractall(isolated)
        for filename in REQUEST_FILES:
            target = isolated / filename
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(git('show', f'{REQUEST_REF}:{filename}'))
        (isolated / 't43_flow_tests.py').write_text(FLOW_TESTS, encoding='utf-8')
        # Force all databases and storage into the disposable copy.
        (isolated / 't43_settings.py').write_text(
            'from core.settings import *\n'
            "DATABASES = {'default': {'ENGINE': 'django.db.backends.sqlite3', "
            "'NAME': ':memory:', 'TEST': {'NAME': ':memory:'}}}\n"
            "MEDIA_ROOT = BASE_DIR / 't43_media'\n", encoding='utf-8')
        result = subprocess.run([
            sys.executable, 'manage.py', 'test', 'prestadores.tests', 'servicios.tests',
            't43_flow_tests', '--settings=t43_settings', '--noinput', '--verbosity=2',
        ], cwd=isolated)
    assert git('rev-parse', 'HEAD') == original_head, 'HEAD changed'
    assert git('branch', '--show-current') == original_branch, 'Branch changed'
    assert git('status', '--porcelain') == original_status, 'Working tree status changed'
    assert snapshot() == protected, 'T2.3/local DB/media changed'
    print('PRESERVATION_OK: branch, HEAD, status, T2.3, local DB/media; temporary copy removed',
          flush=True)
    return result.returncode


if __name__ == '__main__':
    sys.exit(main())
