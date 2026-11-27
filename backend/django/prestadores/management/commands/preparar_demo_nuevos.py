"""Deja a todos los prestadores que ya existen sin la insignia «Nuevo», antes de una demo.

Uso, solo en desarrollo (DJANGO_DEBUG=true):

    python manage.py preparar_demo_nuevos

«Nuevo» se calcula con la fecha de registro (los primeros DIAS_NUEVO días). En la demo esa insignia
debe salir solo en los prestadores que se registran en vivo, así que este comando pasa la fecha de
registro de todos los actuales a hace un año. Se corre justo antes de la demo; quien se registre
después sale como «Nuevo». Se puede correr varias veces.
"""
from datetime import timedelta

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from django.utils import timezone

from prestadores.models import DIAS_NUEVO, Prestador


class Command(BaseCommand):
    help = 'Quita la insignia «Nuevo» a todos los prestadores actuales (solo en desarrollo).'

    def handle(self, *args, **options):
        if not settings.DEBUG:
            raise CommandError('Solo en desarrollo: define DJANGO_DEBUG=true. No se modificó nada.')
        limite = timezone.now() - timedelta(days=DIAS_NUEVO)
        # Solo los que hoy serían «Nuevo»; a los demás no se les toca la fecha.
        cambiados = Prestador.objects.filter(creado__gt=limite).update(
            creado=timezone.now() - timedelta(days=365),
        )
        self.stdout.write(self.style.SUCCESS(
            f'Listo: {cambiados} prestadores dejaron de ser «Nuevo». '
            'Solo saldrán como «Nuevo» los que se registren desde ahora.'
        ))
