from django.db import migrations


def crear_revisiones_pendientes(apps, schema_editor):
    """Las certificaciones subidas antes de T9.1 también entran a la cola."""
    CertificacionPrestador = apps.get_model('certificaciones', 'CertificacionPrestador')
    RevisionDocumento = apps.get_model('administracion', 'RevisionDocumento')
    sin_revision = CertificacionPrestador.objects.filter(revision__isnull=True)
    RevisionDocumento.objects.bulk_create(
        RevisionDocumento(documento=certificacion) for certificacion in sin_revision
    )


class Migration(migrations.Migration):

    dependencies = [
        ('administracion', '0001_initial'),
    ]

    operations = [
        migrations.RunPython(crear_revisiones_pendientes, migrations.RunPython.noop),
    ]
