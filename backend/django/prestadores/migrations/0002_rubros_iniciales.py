from django.db import migrations

RUBROS = [
    'Gasfitería', 'Electricidad', 'Carpintería', 'Pintura', 'Albañilería',
    'Cerrajería', 'Limpieza', 'Jardinería', 'Soldadura', 'Mecánica',
]


def cargar_rubros(apps, schema_editor):
    Rubro = apps.get_model('prestadores', 'Rubro')
    for nombre in RUBROS:
        Rubro.objects.get_or_create(nombre=nombre)


def quitar_rubros(apps, schema_editor):
    apps.get_model('prestadores', 'Rubro').objects.filter(nombre__in=RUBROS).delete()


class Migration(migrations.Migration):
    dependencies = [('prestadores', '0001_initial')]
    operations = [migrations.RunPython(cargar_rubros, quitar_rubros)]
