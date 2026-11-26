import os
import uuid

from django.conf import settings
from django.core.files.storage import FileSystemStorage
from django.db import models
from django.utils import timezone

from prestadores.models import Prestador


class AlmacenPrivado(FileSystemStorage):
    """Guarda fuera de MEDIA_ROOT: ninguna ruta pública sirve las fotos del DNI.

    Lee RAIZ_PRIVADA en cada uso, para que las pruebas puedan cambiarla.
    """

    @property
    def base_location(self):
        return settings.RAIZ_PRIVADA

    @property
    def location(self):
        return os.path.abspath(self.base_location)


class IntentoDni(models.Model):
    class Resultado(models.TextChoices):
        VALIDADO = 'validado', 'DNI validado'
        REVISION_MANUAL = 'revision_manual', 'En revisión manual'

    prestador = models.ForeignKey(Prestador, on_delete=models.CASCADE, related_name='intentos_dni')
    resultado = models.CharField(max_length=20, choices=Resultado.choices)
    motivos = models.JSONField(default=list, blank=True)
    # Lo leído de la MRZ (vacío si no se pudo leer).
    dni_leido = models.CharField(max_length=8, blank=True)
    apellidos_leidos = models.CharField(max_length=100, blank=True)
    nombres_leidos = models.CharField(max_length=100, blank=True)
    fecha_nacimiento = models.DateField(null=True, blank=True)
    fecha_vencimiento = models.DateField(null=True, blank=True)
    mrz_valida = models.BooleanField(default=False)
    # Validador externo (T19.3): solo su nombre y si los nombres coincidieron.
    fuente = models.CharField(max_length=30, blank=True)
    estado_fuente = models.CharField(max_length=20, default='no_disponible')
    anverso = models.FileField(upload_to='dni/', storage=AlmacenPrivado(), blank=True)
    reverso = models.FileField(upload_to='dni/', storage=AlmacenPrivado(), blank=True)
    foto_titular = models.FileField(upload_to='dni/', storage=AlmacenPrivado(), blank=True)
    creado = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-creado', '-id']

    def __str__(self):
        return f'Intento de DNI de {self.prestador} - {self.resultado}'

    def eliminar_datos_biometricos(self):
        """Borra las fotos y lo leído del DNI; solo quedan resultado, motivos y fecha.

        La usa la solicitud de eliminación de HU-21. No lanza errores por un archivo que no
        se pudo borrar: devuelve la lista de los campos que fallaron ([] si todo salió bien).
        Esos archivos conservan su nombre para poder repetir la llamada.
        """
        fallidos = []
        for nombre in ('anverso', 'reverso', 'foto_titular'):
            campo = getattr(self, nombre)
            if not campo:
                continue
            try:
                campo.delete(save=False)
            except Exception:
                fallidos.append(nombre)
        self.dni_leido = self.apellidos_leidos = self.nombres_leidos = ''
        self.fecha_nacimiento = self.fecha_vencimiento = None
        self.mrz_valida = False
        self.fuente = ''
        self.estado_fuente = 'no_disponible'
        self.save()
        return fallidos


class Consentimiento(models.Model):
    usuario = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE,
                                related_name='consentimientos')
    finalidad = models.CharField(max_length=60)
    version_texto = models.CharField(max_length=40)
    texto_aceptado = models.TextField()
    aceptado_en = models.DateTimeField(default=timezone.now, editable=False)
    revocado_en = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ['-aceptado_en', '-id']
        constraints = [
            models.UniqueConstraint(
                fields=['usuario', 'finalidad', 'version_texto'],
                condition=models.Q(revocado_en__isnull=True),
                name='consentimiento_vigente_unico',
            ),
            models.CheckConstraint(
                condition=(models.Q(revocado_en__isnull=True)
                           | models.Q(revocado_en__gte=models.F('aceptado_en'))),
                name='consentimiento_revocacion_posterior',
            ),
        ]


class ControlDatosDni(models.Model):
    prestador = models.OneToOneField(Prestador, on_delete=models.PROTECT)
    generacion = models.PositiveBigIntegerField(default=0)


class SolicitudEliminacionDni(models.Model):
    class Estado(models.TextChoices):
        PENDIENTE = 'pendiente', 'Pendiente'
        PROCESANDO = 'procesando', 'Procesando'
        PARCIAL = 'parcial', 'Parcial'
        FALLIDA = 'fallida', 'Fallida'
        ATENDIDA = 'atendida', 'Atendida'

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    prestador = models.ForeignKey(Prestador, on_delete=models.PROTECT, related_name='eliminaciones_dni')
    clave_idempotencia = models.UUIDField()
    version_alcance = models.CharField(max_length=40)
    alcance = models.JSONField()
    estado = models.CharField(max_length=12, choices=Estado.choices, default=Estado.PENDIENTE)
    creada = models.DateTimeField(default=timezone.now, editable=False)
    actualizada = models.DateTimeField(default=timezone.now)
    atendida_en = models.DateTimeField(null=True, blank=True)
    ejecuciones = models.PositiveIntegerField(default=0)
    token_ejecucion = models.UUIDField(null=True, editable=False)
    codigo_error = models.CharField(max_length=32, blank=True)
    pendientes = models.PositiveIntegerField(default=0)

    class Meta:
        ordering = ['-creada', '-id']
        constraints = [
            models.UniqueConstraint(fields=['prestador', 'clave_idempotencia'], name='eliminacion_clave_propia_unica'),
            models.UniqueConstraint(fields=['prestador'], condition=~models.Q(estado='atendida'),
                                    name='eliminacion_abierta_unica'),
            models.CheckConstraint(condition=models.Q(estado__in=['pendiente', 'procesando', 'parcial', 'fallida', 'atendida']),
                                   name='eliminacion_estado_valido'),
            models.CheckConstraint(condition=(models.Q(estado='atendida', atendida_en__isnull=False)
                                               | (~models.Q(estado='atendida') & models.Q(atendida_en__isnull=True))),
                                   name='eliminacion_fecha_coherente'),
        ]


class EventoEliminacionDni(models.Model):
    solicitud = models.ForeignKey(SolicitudEliminacionDni, on_delete=models.PROTECT, related_name='eventos')
    fecha = models.DateTimeField(default=timezone.now, editable=False)
    anterior = models.CharField(max_length=12, blank=True)
    estado = models.CharField(max_length=12, choices=SolicitudEliminacionDni.Estado.choices)
    ejecucion = models.PositiveIntegerField(default=0)
    codigo = models.CharField(max_length=32, blank=True)
    pendientes = models.PositiveIntegerField(default=0)

    class Meta:
        ordering = ['fecha', 'id']


class ArchivoEliminacionDni(models.Model):
    """Inventario técnico privado, nunca parte del serializador o auditoría pública."""
    solicitud = models.ForeignKey(SolicitudEliminacionDni, on_delete=models.PROTECT, related_name='archivos')
    intento = models.ForeignKey(IntentoDni, null=True, on_delete=models.SET_NULL)
    campo = models.CharField(max_length=20, blank=True)
    nombre_privado = models.CharField(max_length=500)
    verificado = models.BooleanField(default=False)

    class Meta:
        constraints = [models.UniqueConstraint(fields=['solicitud', 'nombre_privado'], name='inventario_archivo_unico')]


class IntentoFacial(models.Model):
    """HU-20: selfie en vivo comparada con la foto del titular recortada del DNI (HU-19).

    La decisión automática solo se registra: no cambia `Prestador.estado`. En la zona gris
    (revisión manual) el administrador ve las dos fotos lado a lado y decide.
    """

    class Decision(models.TextChoices):
        VERIFICADO = 'verificado', 'Verificado'
        REVISION_MANUAL = 'revision_manual', 'En revisión manual'
        RECHAZADO = 'rechazado', 'Rechazado'

    class Revision(models.TextChoices):
        APROBADO = 'aprobado', 'Aprobado por el administrador'
        RECHAZADO = 'rechazado', 'Rechazado por el administrador'

    prestador = models.ForeignKey(Prestador, on_delete=models.CASCADE, related_name='intentos_faciales')
    # La foto del DNI con la que se comparó; si HU-21 la borra, el intento queda sin ella.
    intento_dni = models.ForeignKey(IntentoDni, on_delete=models.SET_NULL, null=True, related_name='intentos_faciales')
    selfie = models.FileField(upload_to='facial/', storage=AlmacenPrivado(), blank=True)
    parpadeo = models.BooleanField(default=False)
    # Similitud coseno de SFace (-1 a 1); null si no se pudo comparar (sin rostro, sin modelo).
    similitud = models.FloatField(null=True, blank=True)
    decision = models.CharField(max_length=20, choices=Decision.choices)
    motivos = models.JSONField(default=list, blank=True)
    comparador = models.CharField(max_length=40, blank=True)
    revision = models.CharField(max_length=10, choices=Revision.choices, blank=True)
    revisado_por = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True,
                                     related_name='revisiones_faciales')
    revisado_en = models.DateTimeField(null=True, blank=True)
    creado = models.DateTimeField(default=timezone.now, editable=False)

    class Meta:
        ordering = ['-creado', '-id']
        constraints = [
            models.CheckConstraint(condition=models.Q(decision__in=['verificado', 'revision_manual', 'rechazado']),
                                   name='facial_decision_valida'),
            models.CheckConstraint(condition=(models.Q(revision='', revisado_en__isnull=True)
                                               | (~models.Q(revision='') & models.Q(revisado_en__isnull=False))),
                                   name='facial_revision_con_fecha'),
        ]

    def __str__(self):
        return f'Intento facial de {self.prestador} - {self.decision}'

    @property
    def porcentaje(self):
        """Similitud en 0-100 para mostrar; la coseno negativa cuenta como 0."""
        if self.similitud is None:
            return None
        return round(max(0.0, min(1.0, self.similitud)) * 100, 1)
