"""T20.4: mide la similitud entre fotos de las mismas y de distintas personas.

Uso: python manage.py calibrar_umbral_facial <carpeta>

La carpeta tiene una subcarpeta por persona (con su consentimiento) y en cada una sus fotos
JPG o PNG: por ejemplo la foto del DNI recortada y dos o tres selfies. Compara todos los
pares y muestra cuántos pares de la misma persona y de personas distintas quedarían
verificados, en revisión o rechazados con los umbrales actuales, y propone otros. No guarda
nada: las fotos no salen de la carpeta.
"""
from itertools import combinations
from pathlib import Path

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError

from verificacion import facial


def clasificar(similitud, verificado, rechazo):
    if similitud >= verificado:
        return 'verificado'
    return 'revision' if similitud >= rechazo else 'rechazado'


class Command(BaseCommand):
    help = 'Calibra UMBRAL_FACIAL_VERIFICADO y UMBRAL_FACIAL_RECHAZO con fotos del equipo.'

    def add_arguments(self, parser):
        parser.add_argument('carpeta')

    def handle(self, *args, carpeta, **opciones):
        raiz = Path(carpeta)
        personas = {d.name: sorted(p for p in d.iterdir() if p.suffix.lower() in ('.jpg', '.jpeg', '.png'))
                    for d in sorted(raiz.iterdir()) if d.is_dir()} if raiz.is_dir() else {}
        personas = {k: v for k, v in personas.items() if v}
        if len(personas) < 2:
            raise CommandError('Hacen falta al menos 2 personas, cada una en su subcarpeta con fotos.')
        comparador = facial.obtener_comparador()
        if isinstance(comparador, facial.ComparadorNoDisponible):
            raise CommandError('Faltan los modelos: python manage.py descargar_modelos_faciales')
        fotos = [(persona, ruta) for persona, rutas in personas.items() for ruta in rutas]
        mismas, distintas, sin_rostro = [], [], 0
        for (pa, a), (pb, b) in combinations(fotos, 2):
            resultado = comparador.comparar(a.read_bytes(), b.read_bytes())
            if resultado.similitud is None:
                sin_rostro += 1
                continue
            (mismas if pa == pb else distintas).append(resultado.similitud)
        verificado, rechazo = settings.UMBRAL_FACIAL_VERIFICADO, settings.UMBRAL_FACIAL_RECHAZO
        self.stdout.write(f'{len(personas)} personas, {len(fotos)} fotos; {sin_rostro} pares sin rostro detectado.')
        for titulo, valores in (('Misma persona', mismas), ('Personas distintas', distintas)):
            if not valores:
                self.stdout.write(f'{titulo}: sin pares.')
                continue
            conteo = {c: sum(clasificar(v, verificado, rechazo) == c for v in valores)
                      for c in ('verificado', 'revision', 'rechazado')}
            self.stdout.write(f'{titulo}: {len(valores)} pares, similitud mín {min(valores):.3f} · '
                              f'máx {max(valores):.3f} → {conteo}')
        if mismas and distintas:
            # Ningún impostor verificado: el umbral de verificación queda por encima del impostor más parecido.
            propuesto_verificado = round(max(max(distintas) + 0.05, verificado), 3)
            # Ninguna persona real rechazada: el de rechazo queda por debajo de su par más bajo.
            propuesto_rechazo = round(min(min(mismas) - 0.05, rechazo), 3)
            self.stdout.write(self.style.SUCCESS(
                f'Propuesta: UMBRAL_FACIAL_VERIFICADO={propuesto_verificado} '
                f'UMBRAL_FACIAL_RECHAZO={propuesto_rechazo} (actual {verificado} / {rechazo}).'))
