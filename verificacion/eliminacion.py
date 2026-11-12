"""Solicitudes duraderas, barrera de captura e inventario privado de archivos DNI."""
import uuid
from pathlib import Path, PurePosixPath

from django.conf import settings
from django.db import DatabaseError, transaction
from django.db.models import Q
from django.utils import timezone

from .coordinacion import control_propietario
from .models import (
    AlmacenPrivado, ArchivoEliminacionDni, EventoEliminacionDni,
    IntentoDni, SolicitudEliminacionDni,
)

ESTADO = SolicitudEliminacionDni.Estado
CAMPOS = ('anverso', 'reverso', 'foto_titular')
VERSION_ALCANCE = 'verificacion_dni-v1'
ALCANCE = ['fotografias_dni', 'fotografia_titular', 'datos_leidos', 'fechas_leidas', 'indicadores_y_fuente']
CODIGOS = {'', 'solicitada', 'reintento', 'recuperacion', 'almacenamiento', 'inventario', 'persistencia', 'verificacion'}


class CapturaBloqueada(Exception):
    pass


class ConsentimientoRequerido(Exception):
    pass


class LimiteIntentos(Exception):
    pass


class SolicitudAbierta(Exception):
    def __init__(self, solicitud):
        self.solicitud = solicitud


def eliminacion_pendiente(prestador):
    return SolicitudEliminacionDni.objects.filter(prestador=prestador).exclude(estado=ESTADO.ATENDIDA).exists()


def _comprobar_captura(prestador, control, generacion=None):
    from .consentimiento import tiene_consentimiento
    if eliminacion_pendiente(prestador) or (generacion is not None and generacion != control.generacion):
        raise CapturaBloqueada
    if not tiene_consentimiento(prestador):
        raise ConsentimientoRequerido


@transaction.atomic
def iniciar_captura(prestador):
    control = control_propietario(prestador)
    _comprobar_captura(prestador, control)
    return control.generacion


@transaction.atomic
def persistir_captura(prestador, generacion, guardar):
    control = control_propietario(prestador)
    _comprobar_captura(prestador, control, generacion)
    if prestador.intentos_dni.filter(creado__date=timezone.localdate()).count() >= 5:
        raise LimiteIntentos
    # Mantiene la reserva durante archivos + save. OCR se ejecuta ANTES de entrar aquí.
    # Los archivos nuevos usan un directorio propio recuperable si el save hace rollback.
    return guardar()


def _transicion(solicitud, estado, codigo='', pendientes=0):
    if codigo not in CODIGOS:
        raise ValueError('Código no permitido')
    anterior = solicitud.estado
    solicitud.estado = estado
    solicitud.actualizada = timezone.now()
    solicitud.atendida_en = solicitud.actualizada if estado == ESTADO.ATENDIDA else None
    solicitud.codigo_error = codigo if estado in (ESTADO.PARCIAL, ESTADO.FALLIDA) else ''
    solicitud.pendientes = pendientes
    solicitud.save()
    EventoEliminacionDni.objects.create(
        solicitud=solicitud, anterior=anterior, estado=estado, codigo=codigo,
        ejecucion=solicitud.ejecuciones, pendientes=pendientes,
    )


@transaction.atomic
def solicitar_eliminacion(prestador, clave):
    control = control_propietario(prestador)
    repetida = SolicitudEliminacionDni.objects.filter(prestador=prestador, clave_idempotencia=clave).first()
    if repetida:
        return repetida, False
    abierta = SolicitudEliminacionDni.objects.filter(prestador=prestador).exclude(estado=ESTADO.ATENDIDA).first()
    if abierta:
        raise SolicitudAbierta(abierta)
    solicitud = SolicitudEliminacionDni.objects.create(
        prestador=prestador, clave_idempotencia=clave, version_alcance=VERSION_ALCANCE, alcance=list(ALCANCE),
    )
    control.generacion += 1
    control.save(update_fields=['generacion'])
    EventoEliminacionDni.objects.create(solicitud=solicitud, estado=ESTADO.PENDIENTE, codigo='solicitada')
    return solicitud, True


@transaction.atomic
def reintentar_eliminacion(prestador, identificador):
    control_propietario(prestador)
    solicitud = SolicitudEliminacionDni.objects.get(id=identificador, prestador=prestador)
    if solicitud.estado in (ESTADO.PARCIAL, ESTADO.FALLIDA):
        _transicion(solicitud, ESTADO.PENDIENTE, 'reintento', solicitud.pendientes)
    return solicitud


def _nombre_seguro(nombre):
    p = PurePosixPath(nombre)
    if not nombre or p.is_absolute() or '..' in p.parts or '\\' in nombre:
        raise ValueError('Referencia privada inválida')
    destino = Path(AlmacenPrivado().path(nombre)).resolve()
    if not destino.is_relative_to(Path(settings.RAIZ_PRIVADA).resolve()):
        raise ValueError('Referencia privada fuera del almacén')


def _archivos_propios(prestador):
    """Journal por propietario de cargas nuevas, incluidas escrituras sin fila tras rollback."""
    storage = AlmacenPrivado()
    prefijo = f'dni/hu21/{prestador.pk}/'
    encontrados = []

    def recorrer(carpeta, nivel=0):
        if nivel > 1:
            raise ValueError('Estructura privada no inventariable')
        _nombre_seguro(carpeta)
        if not Path(storage.path(carpeta)).resolve().is_relative_to(Path(storage.path(prefijo)).resolve()):
            raise ValueError('Directorio fuera del propietario')
        try:
            directorios, archivos = storage.listdir(carpeta)
        except FileNotFoundError:
            return
        for nombre in archivos:
            if '/' in nombre or '\\' in nombre or nombre in ('.', '..'):
                raise ValueError('Nombre inválido')
            clave = carpeta + nombre
            _nombre_seguro(clave)
            if not Path(storage.path(clave)).resolve().is_relative_to(Path(storage.path(prefijo)).resolve()):
                raise ValueError('Archivo fuera del propietario')
            encontrados.append(clave)
        for nombre in directorios:
            # Las operaciones de captura generan exclusivamente UUID hexadecimales.
            if len(nombre) != 32 or any(c not in '0123456789abcdef' for c in nombre):
                raise ValueError('Directorio no inventariable')
            recorrer(carpeta + nombre + '/', nivel + 1)

    recorrer(prefijo)
    return encontrados


def _referencia_ajena(nombre, prestador):
    return IntentoDni.objects.exclude(prestador=prestador).filter(
        Q(anverso=nombre) | Q(reverso=nombre) | Q(foto_titular=nombre),
    ).exists()


@transaction.atomic
def _inventariar(solicitud, token, prestador):
    # El propietario se resuelve FUERA de atomic: una FK perezosa sería una lectura previa.
    control_propietario(prestador)
    actual = SolicitudEliminacionDni.objects.get(pk=solicitud.pk)
    if actual.token_ejecucion != token or actual.estado != ESTADO.PROCESANDO:
        return False
    for intento in IntentoDni.objects.filter(prestador=prestador):
        for campo in CAMPOS:
            nombre = getattr(intento, campo).name
            if not nombre:
                continue
            _nombre_seguro(nombre)
            if _referencia_ajena(nombre, prestador):
                raise ValueError('Referencia compartida entre propietarios')
            ArchivoEliminacionDni.objects.get_or_create(
                solicitud=solicitud, nombre_privado=nombre, defaults={'intento': intento, 'campo': campo},
            )
    for nombre in _archivos_propios(prestador):
        if _referencia_ajena(nombre, prestador):
            raise ValueError('Journal referenciado por otro propietario')
        ArchivoEliminacionDni.objects.get_or_create(solicitud=solicitud, nombre_privado=nombre)
    return True


def _derivados_vacios(intento):
    return (not any(getattr(intento, c) for c in ('dni_leido', 'apellidos_leidos', 'nombres_leidos', 'fuente'))
            and intento.fecha_nacimiento is None and intento.fecha_vencimiento is None
            and not intento.mrz_valida and intento.estado_fuente == 'no_disponible')


@transaction.atomic
def _borrar_y_verificar(solicitud, token, prestador):
    control_propietario(prestador)
    actual = SolicitudEliminacionDni.objects.get(pk=solicitud.pk)
    if actual.token_ejecucion != token or actual.estado != ESTADO.PROCESANDO:
        return actual
    storage = AlmacenPrivado()
    error = ''
    progreso = False
    for intento in IntentoDni.objects.filter(prestador=prestador):
        antes = not _derivados_vacios(intento)
        try:
            # Savepoint: un error de BD no invalida el registro exterior del resultado.
            with transaction.atomic():
                for item in actual.archivos.filter(intento=intento):
                    if not getattr(intento, item.campo) and storage.exists(item.nombre_privado):
                        setattr(intento, item.campo, item.nombre_privado)
                if intento.eliminar_datos_biometricos():
                    error = 'almacenamiento'
            intento.refresh_from_db()
            progreso = progreso or (antes and _derivados_vacios(intento))
        except DatabaseError:
            error = 'persistencia'
        except Exception:
            error = 'verificacion'
    pendientes = 0
    for item in actual.archivos.all():
        try:
            # Solo huérfanos del journal propio: el borrado normal sigue siendo de Renzo.
            if item.intento_id is None and storage.exists(item.nombre_privado):
                storage.delete(item.nombre_privado)
            item.verificado = not storage.exists(item.nombre_privado)
        except Exception:
            item.verificado = False
            error = 'almacenamiento'
        item.save(update_fields=['verificado'])
        progreso = progreso or item.verificado
        pendientes += int(not item.verificado)
    for intento in IntentoDni.objects.filter(prestador=prestador):
        if not _derivados_vacios(intento) or any(getattr(intento, c) for c in CAMPOS):
            pendientes += 1
    conocidos = set(actual.archivos.values_list('nombre_privado', flat=True))
    try:
        extras = set(_archivos_propios(prestador)) - conocidos
        pendientes += len(extras)
    except Exception:
        pendientes += 1
        error = 'inventario'
    if pendientes or error:
        _transicion(actual, ESTADO.PARCIAL if progreso else ESTADO.FALLIDA,
                    error or 'verificacion', max(1, pendientes))
    else:
        _transicion(actual, ESTADO.ATENDIDA)
    return actual


def procesar_eliminacion(identificador, recuperar=False):
    solicitud = SolicitudEliminacionDni.objects.select_related('prestador').get(pk=identificador)
    prestador = solicitud.prestador
    with transaction.atomic():
        control_propietario(prestador)
        solicitud.refresh_from_db()
        if solicitud.estado != ESTADO.PENDIENTE and not (recuperar and solicitud.estado == ESTADO.PROCESANDO):
            return solicitud
        token = uuid.uuid4()
        solicitud.ejecuciones += 1
        solicitud.token_ejecucion = token
        _transicion(solicitud, ESTADO.PROCESANDO, 'recuperacion' if recuperar else '')
    inventariado = False
    try:
        # Confirmar el inventario en BD ANTES de modificar el filesystem.
        if _inventariar(solicitud, token, prestador):
            inventariado = True
            return _borrar_y_verificar(solicitud, token, prestador)
        solicitud.refresh_from_db()
        return solicitud
    except Exception as error:
        codigo = 'persistencia' if isinstance(error, DatabaseError) else ('verificacion' if inventariado else 'inventario')
        with transaction.atomic():
            control_propietario(prestador)
            solicitud.refresh_from_db()
            if solicitud.token_ejecucion == token and solicitud.estado == ESTADO.PROCESANDO:
                _transicion(solicitud, ESTADO.FALLIDA, codigo, max(1, solicitud.pendientes))
        return solicitud
