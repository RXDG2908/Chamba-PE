from django.core.management.base import BaseCommand, CommandError

from verificacion.eliminacion import procesar_eliminacion
from verificacion.models import SolicitudEliminacionDni


class Command(BaseCommand):
    help = 'Atiende solicitudes DNI pendientes; usa --recuperar para ejecuciones interrumpidas.'

    def add_arguments(self, parser):
        parser.add_argument('--solicitud', type=str)
        parser.add_argument('--recuperar', action='store_true')

    def handle(self, *args, **options):
        solicitudes = SolicitudEliminacionDni.objects.filter(estado='pendiente')
        if options['recuperar']:
            solicitudes = SolicitudEliminacionDni.objects.filter(estado__in=['pendiente', 'procesando'])
        if options['solicitud']:
            try:
                solicitudes = solicitudes.filter(pk=options['solicitud'])
            except Exception:
                raise CommandError('Identificador de solicitud inválido.') from None
        for identificador in list(solicitudes.values_list('id', flat=True)):
            try:
                s = procesar_eliminacion(identificador, recuperar=options['recuperar'])
            except Exception:
                raise CommandError('No se pudo adquirir o guardar la ejecución; el trabajo permanece recuperable.') from None
            self.stdout.write(f'{s.id}: {s.estado}; pendientes={s.pendientes}; codigo={s.codigo_error or "ninguno"}')
