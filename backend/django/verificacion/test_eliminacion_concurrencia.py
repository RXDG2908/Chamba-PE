"""Concurrencia real: procesos/conexiones independientes y SQLite en archivo temporal."""
import multiprocessing
import uuid
from pathlib import Path
from tempfile import TemporaryDirectory

from django.test import SimpleTestCase


def _proceso(base, accion, salida, identificador=None, clave=None, barrera=None, listo=None, continuar=None):
    import os
    os.environ['DJANGO_SETTINGS_MODULE'] = 'core.settings'
    import django
    from django.conf import settings
    settings.DATABASES['default']['NAME'] = str(Path(base) / 'pruebas.sqlite3')
    settings.DATABASES['default']['OPTIONS'] = {'timeout': 0 if accion == 'sin_espera' else 5}
    settings.RAIZ_PRIVADA = str(Path(base) / 'privado')
    settings.PASSWORD_HASHERS = ['django.contrib.auth.hashers.MD5PasswordHasher']
    django.setup()
    from django.db import connections, transaction
    from django.core.management import call_command
    from django.contrib.auth import get_user_model
    from prestadores.models import Prestador
    from verificacion.coordinacion import control_propietario
    from verificacion.consentimiento import aceptar_consentimiento
    from verificacion.models import IntentoDni, SolicitudEliminacionDni
    from verificacion.eliminacion import (
        CapturaBloqueada, SolicitudAbierta, iniciar_captura, persistir_captura,
        procesar_eliminacion, solicitar_eliminacion, reintentar_eliminacion,
    )
    try:
        if accion == 'preparar':
            call_command('migrate', verbosity=0, interactive=False)
            user = get_user_model().objects.create_user(username='concurrencia_ficticia')
            p = Prestador.objects.create(usuario=user, dni='12345678', nombres='Persona',
                apellidos='Ficticia', latitud=0, longitud=0, radio_cobertura_km=5)
            aceptar_consentimiento(user)
            salida.put({'propietario': p.pk})
            return
        p = Prestador.objects.get(pk=1)
        if accion in ('capturar', 'tardia'):
            generacion = iniciar_captura(p)
        if listo is not None and accion == 'tardia':
            listo.set()
            if not continuar.wait(20):
                raise TimeoutError
        if barrera is not None:
            barrera.wait(timeout=20)
        if accion == 'preparar_fallida':
            s, _ = solicitar_eliminacion(p, uuid.UUID(clave))
            s.estado = 'fallida'
            s.save()
            salida.put({'id': str(s.pk)})
        elif accion == 'reintentar':
            s = reintentar_eliminacion(p, identificador)
            salida.put({'id': str(s.pk), 'estado': s.estado})
        elif accion in ('solicitar', 'sin_espera'):
            try:
                s, creada = solicitar_eliminacion(p, uuid.UUID(clave))
            except SolicitudAbierta as e:
                s, creada = e.solicitud, False
            salida.put({'id': str(s.pk), 'creada': creada})
        elif accion == 'procesar':
            s = procesar_eliminacion(identificador)
            salida.put({'estado': s.estado})
        elif accion in ('capturar', 'tardia'):
            try:
                persistir_captura(p, generacion, lambda: IntentoDni.objects.create(
                    prestador=p, resultado='validado', dni_leido='12345678'))
                salida.put({'capturada': True})
            except CapturaBloqueada:
                salida.put({'bloqueada': True})
        elif accion == 'reserva':
            with transaction.atomic():
                control_propietario(p)
                listo.set()
                if not continuar.wait(20):
                    raise TimeoutError
            salida.put({'liberada': True})
        elif accion == 'estado':
            solicitudes = list(SolicitudEliminacionDni.objects.all())
            salida.put({'cantidad': len(solicitudes),
                'estados': [s.estado for s in solicitudes],
                'ejecuciones': [s.ejecuciones for s in solicitudes],
                'transiciones_procesando': sum(s.eventos.filter(estado='procesando').count() for s in solicitudes),
                'transiciones_reintento': sum(s.eventos.filter(codigo='reintento').count() for s in solicitudes),
                'codigos': [s.codigo_error for s in solicitudes],
                'datos_residuales': IntentoDni.objects.exclude(dni_leido='').count(),
                'intentos': IntentoDni.objects.count()})
    except Exception as e:
        salida.put({'error': type(e).__name__})
    finally:
        connections.close_all()


class ConcurrenciaEliminacionTests(SimpleTestCase):
    def setUp(self):
        self.ctx = multiprocessing.get_context('spawn')
        self.temp = TemporaryDirectory(prefix='hu21-sqlite-procesos-')
        self.addCleanup(self.temp.cleanup)
        self.assertEqual(self.uno('preparar'), {'propietario': 1})

    def iniciar(self, accion, **kwargs):
        q = self.ctx.Queue()
        p = self.ctx.Process(target=_proceso, args=(self.temp.name, accion, q), kwargs=kwargs)
        p.start()
        self.addCleanup(self.limpiar, p)
        return p, q

    @staticmethod
    def limpiar(p):
        if p.is_alive():
            p.terminate()
        p.join(5)

    def resultado(self, trabajo, error=False):
        p, q = trabajo
        dato = q.get(timeout=30)
        p.join(10)
        self.assertFalse(p.is_alive())
        self.assertEqual(p.exitcode, 0)
        if not error:
            self.assertNotIn('error', dato, dato)
        return dato

    def uno(self, accion, **kwargs):
        return self.resultado(self.iniciar(accion, **kwargs))

    def competir(self, accion, parametros):
        barrera = self.ctx.Barrier(2)
        trabajos = [self.iniciar(accion, barrera=barrera, **p) for p in parametros]
        return [self.resultado(t) for t in trabajos]

    def test_misma_clave_simultanea_crea_una_sola_solicitud(self):
        clave = str(uuid.uuid4())
        resultados = self.competir('solicitar', [{'clave': clave}, {'clave': clave}])
        self.assertEqual(resultados[0]['id'], resultados[1]['id'])
        self.assertEqual(sum(r['creada'] for r in resultados), 1)
        self.assertEqual(self.uno('estado')['cantidad'], 1)

    def test_claves_distintas_simultaneas_no_duplican_abiertas(self):
        resultados = self.competir('solicitar', [{'clave': str(uuid.uuid4())}, {'clave': str(uuid.uuid4())}])
        self.assertEqual(resultados[0]['id'], resultados[1]['id'])
        self.assertEqual(self.uno('estado')['cantidad'], 1)

    def test_dos_procesadores_no_duplican_ejecucion(self):
        s = self.uno('solicitar', clave=str(uuid.uuid4()))
        self.competir('procesar', [{'identificador': s['id']}, {'identificador': s['id']}])
        estado = self.uno('estado')
        self.assertEqual(estado['estados'], ['atendida'], estado)
        self.assertEqual(estado['ejecuciones'], [1])
        self.assertEqual(estado['transiciones_procesando'], 1)

    def test_captura_tardia_en_otro_proceso_no_recrea_datos(self):
        listo, continuar = self.ctx.Event(), self.ctx.Event()
        captura = self.iniciar('tardia', listo=listo, continuar=continuar)
        self.assertTrue(listo.wait(20))
        try:
            s = self.uno('solicitar', clave=str(uuid.uuid4()))
            self.assertEqual(self.uno('procesar', identificador=s['id'])['estado'], 'atendida')
        finally:
            continuar.set()
        self.assertEqual(self.resultado(captura), {'bloqueada': True})
        self.assertEqual(self.uno('estado')['intentos'], 0)

    def test_captura_y_solicitud_simultaneas_no_dejan_datos_tras_atender(self):
        barrera = self.ctx.Barrier(2)
        captura = self.iniciar('capturar', barrera=barrera)
        solicitud = self.iniciar('solicitar', barrera=barrera, clave=str(uuid.uuid4()))
        self.resultado(captura)
        s = self.resultado(solicitud)
        self.assertEqual(self.uno('procesar', identificador=s['id'])['estado'], 'atendida')
        self.assertEqual(self.uno('estado')['datos_residuales'], 0)

    def test_sqlite_ocupado_no_omite_reserva_ni_crea_solicitud(self):
        listo, continuar = self.ctx.Event(), self.ctx.Event()
        reserva = self.iniciar('reserva', listo=listo, continuar=continuar)
        self.assertTrue(listo.wait(20))
        try:
            dato = self.resultado(self.iniciar('sin_espera', clave=str(uuid.uuid4())), error=True)
            self.assertEqual(dato, {'error': 'OperationalError'})
        finally:
            continuar.set()
        self.resultado(reserva)
        self.assertEqual(self.uno('estado')['cantidad'], 0)
        self.assertTrue(self.uno('solicitar', clave=str(uuid.uuid4()))['creada'])

    def test_reintentos_simultaneos_conservan_una_transicion_y_una_ejecucion(self):
        s = self.uno('preparar_fallida', clave=str(uuid.uuid4()))
        self.competir('reintentar', [{'identificador': s['id']}, {'identificador': s['id']}])
        self.assertEqual(self.uno('estado')['transiciones_reintento'], 1)
        self.assertEqual(self.uno('procesar', identificador=s['id'])['estado'], 'atendida')
        self.assertEqual(self.uno('estado')['ejecuciones'], [1])
