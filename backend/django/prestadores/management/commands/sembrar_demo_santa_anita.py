"""Prestadores ficticios alrededor de TECSUP (Santa Anita) para probar la app y la web.

Uso, solo en desarrollo (DJANGO_DEBUG=true):

    python manage.py sembrar_demo_santa_anita

Crea o actualiza unos 30 prestadores verificados y disponibles a 0,3–4 km de TECSUP, de todos los
rubros y con nombres de personas («Joel Rivas») y de marcas («ElecTools»), varios con calificaciones.
También agrega rubros que la app agrupa en sus secciones (Cámaras de seguridad, Aire acondicionado…).
Se puede correr varias veces: los datos de demostración se reconocen por el usuario `demo_sa_*` y
nunca toca a otros prestadores.
"""
from datetime import timedelta
from decimal import Decimal

from django.conf import settings
from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.utils import timezone

from calificaciones.models import Calificacion
from calificaciones.servicio import actualizar_promedio
from prestadores.models import Prestador, Rubro
from servicios.models import Solicitud

# TECSUP Lima, Av. Cascanueces 2221, Santa Anita.
TECSUP = (Decimal('-12.044500'), Decimal('-76.952500'))
PREFIJO = 'demo_sa_'

# Rubros que la app muestra en sus secciones y que la base inicial no trae.
RUBROS_NUEVOS = [
    'Cámaras de seguridad', 'Aire acondicionado', 'Fumigación', 'Redes e internet',
    'Mudanzas', 'Vidriería', 'Electrodomésticos', 'Techos y drywall',
]

# (usuario, nombres, apellidos, rubros, desplazamiento en km (norte, este), radio, estrellas de sus reseñas)
PRESTADORES = [
    ('joel', 'Joel', 'Rivas Paredes', ['Electricidad'], (0.4, 0.3), 8, [5, 5, 4]),
    ('electools', 'ElecTools', 'Servicios eléctricos', ['Electricidad', 'Redes e internet'], (-0.8, 1.1), 15, [5, 4, 5, 5]),
    ('voltaje24', 'Voltaje 24', 'Electricistas', ['Electricidad'], (1.6, -0.7), 10, [4, 3]),
    ('rosa', 'Rosa', 'Quispe Mamani', ['Gasfitería'], (-0.3, -0.5), 6, [5, 5, 5]),
    ('gasfiexpress', 'GasfiExpress', 'Atención 24 h', ['Gasfitería'], (0.9, 1.4), 12, [4, 4, 5]),
    ('aquafix', 'AquaFix', 'Perú', ['Gasfitería', 'Electrodomésticos'], (-1.9, 0.6), 10, []),
    ('pepe', 'José «Pepe»', 'Huamán Ccori', ['Cerrajería'], (0.2, -1.0), 8, [5, 4]),
    ('llavesya', 'Llaves Ya', 'Cerrajería', ['Cerrajería', 'Vidriería'], (-1.2, -1.3), 15, [4]),
    ('carmen', 'Carmen', 'Flores Ríos', ['Limpieza'], (0.6, 0.9), 8, [5, 5, 4, 5]),
    ('cleango', 'Clean&Go', 'Limpieza profesional', ['Limpieza', 'Fumigación'], (-0.5, 1.8), 20, [4, 5]),
    ('brillo', 'Brillo Total', 'Oficinas', ['Limpieza'], (2.3, 0.4), 15, []),
    ('arcoiris', 'Pinturas Arcoíris', 'Acabados', ['Pintura'], (1.1, -1.6), 12, [5, 4, 4]),
    ('miguel', 'Miguel', 'Torres Salas', ['Pintura', 'Techos y drywall'], (-0.9, -0.2), 8, [4]),
    ('maderas', 'Maderas Santa Anita', 'Carpintería', ['Carpintería'], (0.1, 0.7), 10, [5, 5]),
    ('luis', 'Luis', 'Mendoza Vargas', ['Carpintería'], (-2.4, -0.9), 10, [3, 4]),
    ('obrafina', 'Obra Fina', 'Construcciones', ['Albañilería', 'Techos y drywall'], (1.8, 1.9), 20, [4, 5]),
    ('wilmer', 'Wilmer', 'Choque Apaza', ['Albañilería'], (-0.4, 0.9), 8, []),
    ('verdeyard', 'Verde Jardín', 'Paisajismo', ['Jardinería'], (2.9, -1.1), 15, [5]),
    ('flor', 'Flor', 'Gutiérrez León', ['Jardinería', 'Limpieza'], (-1.5, 1.6), 10, [5, 4]),
    ('metalsur', 'MetalSur', 'Soldadura', ['Soldadura'], (-3.1, 0.2), 20, [4, 4]),
    ('raul', 'Raúl', 'Chávez Poma', ['Soldadura', 'Vidriería'], (0.7, -2.2), 10, []),
    ('automec', 'AutoMec', 'Santa Anita', ['Mecánica'], (1.3, 0.1), 10, [4, 5, 3]),
    ('frank', 'Frank', 'Salazar Díaz', ['Mecánica'], (-0.6, -1.9), 8, [5]),
    ('vigilacam', 'VigilaCam', 'Seguridad', ['Cámaras de seguridad', 'Redes e internet'], (0.3, 2.4), 25, [5, 4]),
    ('friotec', 'FrioTec', 'Climatización', ['Aire acondicionado', 'Electrodomésticos'], (-2.0, 2.1), 25, [4, 5]),
    ('fumiplus', 'FumiPlus', 'Control de plagas', ['Fumigación'], (2.0, -2.4), 20, [5]),
    ('netfix', 'NetFix', 'Redes', ['Redes e internet'], (-1.0, 0.0), 15, []),
    ('mudanzas', 'Mudanzas Rápidas', 'Lima Este', ['Mudanzas'], (3.4, 1.0), 30, [4, 4]),
    ('cristal', 'Cristal Glass', 'Vidriería', ['Vidriería'], (0.8, -0.4), 10, [5]),
    ('electrohogar', 'ElectroHogar', 'Reparaciones', ['Electrodomésticos'], (-0.2, -2.8), 15, [3, 4]),
]

COMENTARIOS = {
    5: 'Llegó a la hora y dejó todo limpio. Muy recomendable.',
    4: 'Buen trabajo, aunque demoró un poco más de lo previsto.',
    3: 'Cumplió, pero tuve que explicarle dos veces lo que necesitaba.',
}

KM_POR_GRADO = Decimal('111.195')


def _punto(norte_km, este_km):
    lat = TECSUP[0] + Decimal(str(norte_km)) / KM_POR_GRADO
    # A 12° de latitud, un grado de longitud mide ~108,8 km.
    lon = TECSUP[1] + Decimal(str(este_km)) / Decimal('108.76')
    return lat.quantize(Decimal('0.000001')), lon.quantize(Decimal('0.000001'))


class Command(BaseCommand):
    help = 'Crea o actualiza prestadores ficticios cerca de TECSUP Santa Anita (solo desarrollo).'

    def add_arguments(self, parser):
        parser.add_argument('--quitar', action='store_true', help='Borra los datos de demostración y sale.')

    @transaction.atomic
    def handle(self, *args, **options):
        if not settings.DEBUG:
            raise CommandError('Solo para desarrollo: arranca con DJANGO_DEBUG=true. No se modificó nada.')
        Usuario = get_user_model()
        if options['quitar']:
            borrados, _ = Usuario.objects.filter(username__startswith=PREFIJO).delete()
            self.stdout.write(self.style.SUCCESS(f'Datos de demostración borrados ({borrados} filas).'))
            return

        for nombre in RUBROS_NUEVOS:
            Rubro.objects.get_or_create(nombre=nombre)
        rubros = {r.nombre: r for r in Rubro.objects.all()}

        cliente, _ = Usuario.objects.get_or_create(
            username=f'{PREFIJO}cliente_resenas',
            defaults={'email': 'cliente-resenas@example.invalid', 'first_name': 'Clientes', 'last_name': 'de prueba'},
        )
        if cliente.has_usable_password():
            cliente.set_unusable_password()
            cliente.save(update_fields=['password'])

        creados = actualizados = 0
        for indice, (clave, nombres, apellidos, nombres_rubros, (norte, este), radio, estrellas) in enumerate(PRESTADORES):
            faltan = [n for n in nombres_rubros if n not in rubros]
            if faltan:
                raise CommandError(f'Faltan los rubros {faltan}. No se modificó nada.')
            usuario, nuevo = Usuario.objects.get_or_create(
                username=f'{PREFIJO}{clave}', defaults={'email': f'{clave}@example.invalid'},
            )
            if nuevo:
                usuario.set_unusable_password()
                usuario.save(update_fields=['password'])
            lat, lon = _punto(norte, este)
            dni = f'79{indice:06d}'  # ficticio
            otro = Prestador.objects.filter(dni=dni).exclude(usuario=usuario).first()
            if otro is not None:
                raise CommandError(f'El DNI ficticio {dni} ya es de otro prestador. No se modificó nada.')
            prestador, nuevo_prestador = Prestador.objects.update_or_create(
                usuario=usuario,
                defaults={
                    'nombres': nombres, 'apellidos': apellidos, 'dni': dni, 'telefono': f'9{indice:08d}',
                    'latitud': lat, 'longitud': lon, 'radio_cobertura_km': radio,
                    'disponible': True, 'estado': Prestador.Estado.VERIFICADO,
                },
            )
            prestador.rubros.set([rubros[n] for n in nombres_rubros])
            creados += nuevo_prestador
            actualizados += not nuevo_prestador

            # Reseñas: servicios ya finalizados con su calificación (no cuentan como «en curso»).
            Solicitud.objects.filter(prestador=prestador, cliente=cliente).delete()
            for numero, valor in enumerate(estrellas):
                solicitud = Solicitud.objects.create(
                    prestador=prestador, rubro=rubros[nombres_rubros[0]], cliente=cliente,
                    cliente_nombre='Cliente de prueba', cliente_telefono='999999999',
                    descripcion='Servicio de demostración.', latitud=TECSUP[0], longitud=TECSUP[1],
                    estado=Solicitud.Estado.FINALIZADA, respondida=timezone.now() - timedelta(days=numero + 1),
                )
                Calificacion.objects.create(
                    solicitud=solicitud, prestador=prestador, cliente=cliente,
                    estrellas=valor, comentario=COMENTARIOS[valor],
                )
            # El promedio y el total que muestran la búsqueda y la app (HU-12).
            actualizar_promedio(prestador.id)

        self.stdout.write(self.style.SUCCESS(
            f'Listo: {creados} prestadores nuevos y {actualizados} actualizados cerca de TECSUP Santa Anita.'
        ))
