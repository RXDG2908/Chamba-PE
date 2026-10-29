from decimal import Decimal

from django.conf import settings
from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from prestadores.models import Prestador, Rubro

# Cuentas ficticias para la demo local del Sprint 1 (ver docs/demo-sprint1.md).
ADMIN = ('admin.demo', 'admin@demo.pe', 'AdminChamba2026')
CLIENTE = ('ana.cliente', 'ana@demo.pe', 'ClienteChamba2026')
PRESTADOR = ('jorge.respaldo', 'jorge@demo.pe', 'PrestadorChamba2026')


class Command(BaseCommand):
    help = 'Crea las cuentas de la demo local: administrador, cliente y un prestador verificado y activo.'

    @transaction.atomic
    def handle(self, *args, **options):
        if not settings.DEBUG:
            raise CommandError('Solo se usa en desarrollo (DEBUG = True). No se creó ninguna cuenta.')
        rubro = Rubro.objects.filter(nombre='Gasfitería').first()
        if rubro is None:
            raise CommandError('No existe Gasfitería. Corre primero python manage.py migrate.')

        User = get_user_model()
        if not User.objects.filter(username=ADMIN[0]).exists():
            User.objects.create_superuser(*ADMIN)
        if not User.objects.filter(username=CLIENTE[0]).exists():
            User.objects.create_user(*CLIENTE, first_name='Ana', last_name='Pérez')
        if not User.objects.filter(username=PRESTADOR[0]).exists():
            usuario = User.objects.create_user(*PRESTADOR)
            # Plaza San Martín (Lima); ya verificado y activo para que salga en la búsqueda.
            prestador = Prestador.objects.create(
                usuario=usuario, nombres='Jorge', apellidos='Ramírez', dni='70000001',
                latitud=Decimal('-12.051800'), longitud=Decimal('-77.034700'),
                radio_cobertura_km=10, disponible=True, estado=Prestador.Estado.VERIFICADO,
            )
            prestador.rubros.add(rubro)

        for username, _, password in (ADMIN, CLIENTE, PRESTADOR):
            usuario = User.objects.get(username=username)
            self.stdout.write(f'{usuario.perfil.rol:<14} {username:<15} {password}')
        self.stdout.write(self.style.SUCCESS('Cuentas de la demo listas.'))
