from io import StringIO

from django.core.management import call_command
from django.core.management.base import CommandError
from django.test import TestCase, override_settings

from prestadores.models import Prestador, Rubro


@override_settings(DEBUG=True)
class SembrarDemoSantaAnitaTests(TestCase):
    def sembrar(self, *args):
        call_command('sembrar_demo_santa_anita', *args, stdout=StringIO())

    def test_crea_prestadores_verificados_con_rubros_y_calificaciones(self):
        self.sembrar()
        demo = Prestador.objects.filter(usuario__username__startswith='demo_sa_')
        self.assertEqual(demo.count(), 30)
        self.assertFalse(demo.exclude(estado=Prestador.Estado.VERIFICADO).exists())
        self.assertFalse(demo.filter(disponible=False).exists())
        self.assertTrue(Rubro.objects.filter(nombre='Cámaras de seguridad').exists())
        joel = demo.get(usuario__username='demo_sa_joel')
        self.assertEqual(joel.calificaciones_total, 3)
        self.assertEqual(str(joel.calificacion_promedio), '4.67')
        # Todos cerca de TECSUP: a menos de ~4 km en cada eje.
        for p in demo:
            self.assertLess(abs(float(p.latitud) + 12.0445), 0.04)
            self.assertLess(abs(float(p.longitud) + 76.9525), 0.04)

    def test_se_puede_correr_varias_veces_sin_duplicar(self):
        self.sembrar()
        self.sembrar()
        demo = Prestador.objects.filter(usuario__username__startswith='demo_sa_')
        self.assertEqual(demo.count(), 30)
        self.assertEqual(demo.get(usuario__username='demo_sa_joel').calificaciones_total, 3)

    def test_quitar_borra_solo_los_datos_de_demostracion(self):
        self.sembrar()
        self.sembrar('--quitar')
        self.assertFalse(Prestador.objects.filter(usuario__username__startswith='demo_sa_').exists())

    @override_settings(DEBUG=False)
    def test_fuera_de_desarrollo_no_hace_nada(self):
        with self.assertRaises(CommandError):
            self.sembrar()
        self.assertFalse(Prestador.objects.exists())
