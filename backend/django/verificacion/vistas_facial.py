"""HU-20: selfie en vivo, comparación con la foto del DNI y revisión del administrador."""
import uuid

from django.core.files.base import ContentFile
from django.db import DatabaseError, transaction
from django.db.models import F
from django.http import FileResponse, Http404
from django.utils import timezone
from rest_framework.response import Response
from rest_framework.views import APIView

from usuarios.permisos import EsAdministrador, EsPrestador

from . import facial
from .consentimiento import tiene_consentimiento
from .coordinacion import control_propietario
from .eliminacion import eliminacion_pendiente
from .models import ControlDatosDni, IntentoFacial
from .textos_consentimiento import FINALIDAD_FACIAL
from .views import INTENTOS_POR_DIA, error_de_imagen, extension_de

VERDADEROS = (True, 'true', 'True', '1', 'on')
SIN_PARPADEO = 'Falta la prueba de vida: parpadea frente a la cámara antes de tomar la selfie.'
SIN_CONSENTIMIENTO = 'Antes de comparar tu rostro debes aceptar el uso de tu selfie.'
SIN_FOTO_DNI = 'Primero verifica tu DNI con una foto del frente donde se vea tu rostro.'
ELIMINACION_DNI = 'Tienes una eliminación de datos del DNI pendiente de resolver.'


class CapturaInvalidada(Exception):
    pass


def a_json(intento):
    return {
        'id': intento.id,
        'decision': intento.decision,
        'motivos': intento.motivos,
        'similitud': intento.similitud,
        'porcentaje': intento.porcentaje,
        'parpadeo': intento.parpadeo,
        'revision': intento.revision,
        'revisado_en': intento.revisado_en,
        'tiene_selfie': bool(intento.selfie),
        'tiene_foto_dni': bool(intento.intento_dni_id and intento.intento_dni.foto_titular),
        'creado': intento.creado,
    }


def foto_dni_de(prestador):
    """El último intento de DNI con foto del titular, o None."""
    return prestador.intentos_dni.exclude(foto_titular='').first()


class IntentoFacialView(APIView):
    def get_permissions(self):
        if self.request.method == 'GET' and 'decision' in self.request.query_params:
            return [EsAdministrador()]
        return [EsPrestador()]

    def post(self, request):
        prestador = request.user.prestador
        if eliminacion_pendiente(prestador):
            return Response({'detail': ELIMINACION_DNI}, status=409)
        if not tiene_consentimiento(prestador, FINALIDAD_FACIAL):
            return Response({'detail': SIN_CONSENTIMIENTO}, status=403)
        errores = {}
        error = error_de_imagen(request.FILES.get('selfie'))
        if error:
            errores['selfie'] = [error]
        if request.data.get('parpadeo') not in VERDADEROS:
            errores['parpadeo'] = [SIN_PARPADEO]
        if errores:
            return Response(errores, status=400)
        intento_dni = foto_dni_de(prestador)
        if intento_dni is None:
            return Response({'detail': SIN_FOTO_DNI}, status=409)
        if prestador.intentos_faciales.filter(creado__date=timezone.localdate()).count() >= INTENTOS_POR_DIA:
            return Response(
                {'detail': f'Llegaste al máximo de {INTENTOS_POR_DIA} intentos por día. Prueba mañana.'}, status=429)
        try:
            generacion = control_inicial(prestador)
        except DatabaseError:
            return Response({'detail': 'No pudimos reservar la comparación. Inténtalo de nuevo.'}, status=503)

        selfie = request.FILES['selfie'].read()
        with intento_dni.foto_titular.open('rb') as archivo:
            foto_dni = archivo.read()
        comparador = facial.obtener_comparador()
        # La comparación (lenta) va fuera de la transacción, como el OCR de HU-19.
        comparacion = comparador.comparar(foto_dni, selfie)
        decision, motivos = facial.decidir(comparacion, intento_dni)
        intento = IntentoFacial(
            prestador=prestador, intento_dni=intento_dni, parpadeo=True, similitud=comparacion.similitud,
            decision=decision, motivos=motivos, comparador=comparador.nombre,
        )
        try:
            persistir(prestador, generacion, intento, selfie)
        except CapturaInvalidada:
            return Response({'detail': 'La comparación se canceló: cambió tu consentimiento o pediste borrar '
                                       'tus datos. Inténtalo de nuevo.'}, status=409)
        except Exception:
            return Response({'detail': 'No pudimos guardar la comparación. Inténtalo de nuevo.'}, status=503)
        return Response(a_json(intento), status=201)

    def get(self, request):
        if 'decision' in request.query_params:
            # Cola del administrador (zona gris por defecto), con las selfies que aún existen.
            decision = request.query_params['decision']
            if decision not in IntentoFacial.Decision.values:
                return Response({'decision': ['Valor no válido.']}, status=400)
            intentos = (IntentoFacial.objects.filter(decision=decision, revision='')
                        .exclude(selfie='').select_related('prestador', 'intento_dni'))
            return Response([
                {**a_json(i), 'prestador': {
                    'id': i.prestador_id,
                    'nombre': f'{i.prestador.nombres} {i.prestador.apellidos}'.strip(),
                    'dni': i.prestador.dni,
                }}
                for i in intentos
            ])
        intento = request.user.prestador.intentos_faciales.select_related('intento_dni').first()
        if intento is None:
            return Response({'detail': 'Todavía no comparaste tu rostro.'}, status=404)
        return Response(a_json(intento))


@transaction.atomic
def control_inicial(prestador):
    return control_propietario(prestador).generacion


@transaction.atomic
def persistir(prestador, generacion, intento, selfie):
    """Guarda solo si nada cambió mientras se comparaba (revocación, borrado, otro intento)."""
    control = control_propietario(prestador)
    if (control.generacion != generacion or eliminacion_pendiente(prestador)
            or not tiene_consentimiento(prestador, FINALIDAD_FACIAL)):
        raise CapturaInvalidada
    if prestador.intentos_faciales.filter(creado__date=timezone.localdate()).count() >= INTENTOS_POR_DIA:
        raise CapturaInvalidada
    # El campo ya agrega «facial/» (upload_to); repetirlo dejaba las selfies en facial/facial/.
    carpeta = f'{prestador.pk}/{uuid.uuid4().hex}/'
    intento.selfie.save(carpeta + f'selfie.{extension_de(selfie)}', ContentFile(selfie), save=False)
    try:
        intento.save()
    except Exception:
        intento.selfie.delete(save=False)
        raise


class FotoIntentoFacial(APIView):
    """Las dos fotos para compararlas lado a lado; solo el administrador."""
    permission_classes = [EsAdministrador]

    def get(self, request, intento_id, tipo):
        try:
            intento = IntentoFacial.objects.select_related('intento_dni').get(id=intento_id)
        except IntentoFacial.DoesNotExist:
            raise Http404
        if tipo == 'selfie':
            archivo = intento.selfie
        elif tipo == 'dni':
            archivo = intento.intento_dni.foto_titular if intento.intento_dni_id else None
        else:
            raise Http404
        if not archivo:
            raise Http404
        extension = archivo.name.rsplit('.', 1)[-1].lower()
        respuesta = FileResponse(archivo.open('rb'), content_type='image/png' if extension == 'png' else 'image/jpeg')
        respuesta['Cache-Control'] = 'private, no-store'
        return respuesta


class RevisionFacialView(APIView):
    """El administrador decide un intento en zona gris: aprobado o rechazado."""
    permission_classes = [EsAdministrador]

    def patch(self, request, intento_id):
        revision = request.data.get('revision')
        if revision not in IntentoFacial.Revision.values:
            return Response({'revision': ['Elige aprobado o rechazado.']}, status=400)
        if not IntentoFacial.objects.filter(id=intento_id).exists():
            raise Http404
        # Actualización condicional: si dos administradores deciden a la vez, gana uno solo.
        cambiados = IntentoFacial.objects.filter(id=intento_id, revision='').update(
            revision=revision, revisado_por=request.user, revisado_en=timezone.now())
        intento = IntentoFacial.objects.select_related('intento_dni').get(id=intento_id)
        if not cambiados:
            return Response({'detail': 'Este intento ya fue revisado.', **a_json(intento)}, status=409)
        return Response(a_json(intento))


class EliminarSelfiesView(APIView):
    """HU-21 para HU-20: borra todas las selfies propias; quedan porcentaje, decisión y fecha."""
    permission_classes = [EsPrestador]

    def post(self, request):
        if request.data.get('confirmo') is not True:
            return Response({'confirmo': ['Confirma que quieres borrar tus selfies.']}, status=400)
        prestador = request.user.prestador
        with transaction.atomic():
            control_propietario(prestador)
            # Invalida una comparación en curso: no podrá guardar una selfie nueva después del borrado.
            ControlDatosDni.objects.filter(prestador=prestador).update(generacion=F('generacion') + 1)
            intentos = list(prestador.intentos_faciales.exclude(selfie=''))
        borradas, pendientes = 0, 0
        for intento in intentos:
            try:
                intento.selfie.delete(save=False)
            except Exception:
                pendientes += 1
                continue
            IntentoFacial.objects.filter(pk=intento.pk).update(selfie='')
            borradas += 1
        if pendientes:
            return Response({'detail': 'No pudimos borrar todas tus selfies. Inténtalo de nuevo.',
                             'borradas': borradas, 'pendientes': pendientes}, status=503)
        return Response({'borradas': borradas, 'pendientes': 0})
