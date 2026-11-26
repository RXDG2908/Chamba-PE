"""Descarga los modelos ONNX de OpenCV para HU-20 y comprueba su huella SHA-256."""
import hashlib
import urllib.request
from pathlib import Path

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError

from verificacion.facial import MODELOS


def huella(ruta):
    h = hashlib.sha256()
    with open(ruta, 'rb') as archivo:
        for bloque in iter(lambda: archivo.read(1 << 20), b''):
            h.update(bloque)
    return h.hexdigest()


class Command(BaseCommand):
    help = 'Descarga los modelos faciales (YuNet y SFace de opencv_zoo) a MODELOS_FACIALES.'

    def handle(self, *args, **opciones):
        carpeta = Path(settings.MODELOS_FACIALES)
        carpeta.mkdir(parents=True, exist_ok=True)
        for nombre, url, esperada in MODELOS.values():
            destino = carpeta / nombre
            if destino.is_file() and huella(destino) == esperada:
                self.stdout.write(f'{nombre}: ya está.')
                continue
            temporal = destino.with_suffix('.descarga')
            self.stdout.write(f'Descargando {nombre}…')
            urllib.request.urlretrieve(url, temporal)
            obtenida = huella(temporal)
            if obtenida != esperada:
                temporal.unlink(missing_ok=True)
                raise CommandError(f'{nombre}: la huella no coincide ({obtenida}); no se usa.')
            temporal.replace(destino)
            self.stdout.write(self.style.SUCCESS(f'{nombre}: listo.'))
