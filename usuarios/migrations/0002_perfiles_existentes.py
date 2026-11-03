from django.conf import settings
from django.db import migrations


def asignar_perfiles(apps, schema_editor):
    """Da perfil a las cuentas creadas antes de esta app."""
    User = apps.get_model(*settings.AUTH_USER_MODEL.split('.'))
    Perfil = apps.get_model('usuarios', 'Perfil')
    Prestador = apps.get_model('prestadores', 'Prestador')
    con_prestador = set(Prestador.objects.values_list('usuario_id', flat=True))
    for usuario in User.objects.filter(perfil__isnull=True):
        if usuario.id in con_prestador:
            rol = 'prestador'
        elif usuario.is_staff:
            rol = 'administrador'
        else:
            rol = 'cliente'
        Perfil.objects.create(usuario=usuario, rol=rol)


class Migration(migrations.Migration):

    dependencies = [
        ('usuarios', '0001_initial'),
        ('prestadores', '0001_initial'),
    ]

    operations = [
        migrations.RunPython(asignar_perfiles, migrations.RunPython.noop),
    ]
