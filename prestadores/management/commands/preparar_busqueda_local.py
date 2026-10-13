from decimal import Decimal

from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from prestadores.models import Prestador, Rubro


class Command(BaseCommand):
    help = 'Prepara un único prestador ficticio para probar T3.1 localmente, sin actualizar registros existentes.'

    @transaction.atomic
    def handle(self, *args, **options):
        rubro = Rubro.objects.filter(nombre='Gasfitería').first()
        if rubro is None:
            raise CommandError('No existe Gasfitería. No se modificó ningún registro.')

        username = 'demo_t31_gasfiteria_local'
        email = 'demo-t31@example.invalid'
        dni = '00000000'  # Dato ficticio, exclusivamente para esta prueba local.
        datos = {
            'nombres': 'Demo', 'apellidos': 'Gasfitero ficticio', 'dni': dni,
            'latitud': Decimal('-12.046374'), 'longitud': Decimal('-77.042793'),
            'radio_cobertura_km': 5, 'disponible': True,
        }
        usuario = get_user_model().objects.filter(username=username).first()
        if usuario is not None:
            prestador = Prestador.objects.filter(usuario=usuario).first()
            if (
                usuario.email != email or usuario.has_usable_password()
                or prestador is None
                or any(getattr(prestador, campo) != valor for campo, valor in datos.items())
                or set(prestador.rubros.values_list('id', flat=True)) != {rubro.id}
            ):
                raise CommandError('El identificador de prueba ya está ocupado o fue modificado. No se alteró ningún registro.')
            accion = 'Reutilizado, sin modificaciones'
        else:
            if Prestador.objects.filter(dni=dni).exists():
                raise CommandError('El DNI ficticio ya está ocupado. No se alteró ningún registro.')
            usuario = get_user_model().objects.create_user(username=username, email=email, password=None)
            prestador = Prestador(usuario=usuario, **datos)
            prestador.full_clean()
            prestador.save()
            prestador.rubros.add(rubro)
            accion = 'Creado'

        self.stdout.write(self.style.SUCCESS(f'{accion}: prestador id={prestador.id}, rubro Gasfitería id={rubro.id}.'))
        self.stdout.write(
            f'/api/prestadores/buscar/?rubro={rubro.id}&latitud=-12.046374&longitud=-77.042793'
        )
