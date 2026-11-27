from datetime import timedelta
from io import StringIO

from django.contrib.auth import get_user_model
from django.core.management import call_command
from django.core.management.base import CommandError
from django.test import TestCase, override_settings
from django.utils import timezone

from prestadores.models import Prestador

User = get_user_model()


def prestador(username, dni):
    usuario = User.objects.create_user(username)
    return Prestador.objects.create(
        usuario=usuario, nombres='Ana', apellidos='Quispe', dni=dni,
        latitud=-12.05, longitud=-77.04, radio_cobertura_km=5,
    )


class PrepararDemoNuevosTests(TestCase):
    @override_settings(DEBUG=True)
    def test_quita_nuevo_a_los_actuales_y_no_a_los_que_se_registran_despues(self):
        reciente = prestador('ana', '11111111')
        antiguo = prestador('beto', '22222222')
        hace_dos_anios = timezone.now() - timedelta(days=730)
        Prestador.objects.filter(pk=antiguo.pk).update(creado=hace_dos_anios)

        call_command('preparar_demo_nuevos', stdout=StringIO())

        reciente.refresh_from_db()
        antiguo.refresh_from_db()
        self.assertFalse(reciente.es_nuevo)
        self.assertEqual(antiguo.creado, hace_dos_anios)  # al que ya no era nuevo no se le toca
        # El que se registra en vivo, después del comando, sí es «Nuevo».
        self.assertTrue(prestador('carla', '33333333').es_nuevo)

    @override_settings(DEBUG=False)
    def test_fuera_de_desarrollo_no_hace_nada(self):
        reciente = prestador('ana', '11111111')
        with self.assertRaises(CommandError):
            call_command('preparar_demo_nuevos', stdout=StringIO())
        reciente.refresh_from_db()
        self.assertTrue(reciente.es_nuevo)
