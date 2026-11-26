"""El esquema que usan las pruebas de Spring sigue igual al que crean las migraciones de Django.

Spring nunca crea tablas: lee las de Django. Sus pruebas usan una copia del esquema
(backend/spring/src/test/resources/esquema-django.sql). Si una migración cambia una columna que
Spring lee, esta prueba falla y avisa que hay que actualizar la copia y la entidad de Spring.
"""
import sqlite3
from pathlib import Path
from unittest import skipUnless

from django.conf import settings
from django.db import connection
from django.test import TestCase

ESQUEMA_SPRING = Path(settings.BASE_DIR).parent / 'spring' / 'src' / 'test' / 'resources' / 'esquema-django.sql'


def columnas(cursor, tabla):
    """{columna: (tipo, not null, primary key)} según SQLite."""
    cursor.execute(f'PRAGMA table_info("{tabla}")')
    return {fila[1]: (fila[2].lower(), bool(fila[3]), bool(fila[5])) for fila in cursor.fetchall()}


@skipUnless(connection.vendor == 'sqlite', 'La copia del esquema de Spring es de SQLite.')
class EsquemaSpringTests(TestCase):
    def test_columnas_que_lee_spring_existen_con_el_mismo_tipo(self):
        copia = sqlite3.connect(':memory:')
        self.addCleanup(copia.close)
        copia.executescript(ESQUEMA_SPRING.read_text(encoding='utf-8'))
        tablas = [fila[0] for fila in copia.execute(
            "select name from sqlite_master where type = 'table' and name not like 'sqlite_%'")]
        self.assertEqual(set(tablas), {
            'auth_user', 'prestadores_rubro', 'prestadores_prestador', 'prestadores_prestador_rubros',
            'usuarios_perfil', 'servicios_solicitud', 'servicios_cambioestado', 'servicios_tokenfcm',
            'django_session', 'administracion_sancioncuenta', 'calificaciones_calificacion',
        })

        with connection.cursor() as cursor:
            for tabla in tablas:
                reales = columnas(cursor, tabla)
                self.assertTrue(reales, f'La tabla {tabla} ya no existe en Django.')
                for columna, definicion in columnas(copia.cursor(), tabla).items():
                    with self.subTest(tabla=tabla, columna=columna):
                        self.assertIn(columna, reales, f'{tabla}.{columna} ya no existe en Django.')
                        self.assertEqual(definicion, reales[columna],
                                         f'{tabla}.{columna} cambió en Django: actualiza esquema-django.sql y Spring.')

                # Una columna nueva obligatoria y sin valor por defecto rompe los INSERT de Spring
                # (solicitudes, historial) y los de sus pruebas: también debe estar en la copia.
                cursor.execute(f'PRAGMA table_info("{tabla}")')
                obligatorias = {f[1] for f in cursor.fetchall() if f[3] and f[4] is None and not f[5]}
                self.assertLessEqual(obligatorias, set(columnas(copia.cursor(), tabla)))
