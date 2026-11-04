from django.core.files.base import ContentFile
from django.http import FileResponse, Http404
from django.utils import timezone
from rest_framework.response import Response
from rest_framework.views import APIView

from usuarios.permisos import EsAdministrador, EsPrestador

from . import servicio
from .lector import obtener_lector
from .models import IntentoDni

MAX_IMAGEN = 5 * 1024 * 1024  # 5 MB
INTENTOS_POR_DIA = 5
FIRMAS = (b'\xff\xd8\xff', b'\x89PNG\r\n\x1a\n')  # JPG y PNG
FOTOS = ('anverso', 'reverso', 'titular')


def error_de_imagen(archivo):
    """Texto del error si la imagen falta o no es un JPG o PNG de hasta 5 MB."""
    if archivo is None:
        return 'Este campo es obligatorio.'
    if archivo.size > MAX_IMAGEN:
        return 'La imagen excede el límite de 5 MB.'
    cabecera = archivo.read(8)
    archivo.seek(0)
    if not cabecera.startswith(FIRMAS):
        return 'Solo se permiten imágenes JPG o PNG.'
    return None


def a_json(intento):
    return {
        'id': intento.id,
        'resultado': intento.resultado,
        'motivos': intento.motivos,
        'lectura': {
            'dni': intento.dni_leido,
            'apellidos': intento.apellidos_leidos,
            'nombres': intento.nombres_leidos,
            'fecha_nacimiento': intento.fecha_nacimiento,
            'fecha_vencimiento': intento.fecha_vencimiento,
            'mrz_valida': intento.mrz_valida,
        },
        'fuente': {'nombre': intento.fuente, 'estado': intento.estado_fuente},
        'tiene_foto': bool(intento.foto_titular),
        'creado': intento.creado,
    }


class IntentoDniView(APIView):
    def get_permissions(self):
        if self.request.method == 'GET' and 'prestador' in self.request.query_params:
            return [EsAdministrador()]
        return [EsPrestador()]

    def post(self, request):
        prestador = request.user.prestador
        errores = {}
        for campo in ('anverso', 'reverso'):
            error = error_de_imagen(request.FILES.get(campo))
            if error:
                errores[campo] = [error]
        if errores:
            return Response(errores, status=400)

        hoy = timezone.localdate()
        if prestador.intentos_dni.filter(creado__date=hoy).count() >= INTENTOS_POR_DIA:
            return Response(
                {'detail': f'Llegaste al máximo de {INTENTOS_POR_DIA} intentos por día. Prueba mañana.'},
                status=429,
            )

        anverso = request.FILES['anverso'].read()
        reverso = request.FILES['reverso'].read()
        lectura, foto, motivos, fuente = servicio.analizar(anverso, reverso, prestador, obtener_lector())

        intento = IntentoDni(
            prestador=prestador,
            resultado=(IntentoDni.Resultado.REVISION_MANUAL if servicio.hay_revision_manual(motivos)
                       else IntentoDni.Resultado.VALIDADO),
            motivos=motivos,
            fuente=fuente['nombre'],
            estado_fuente=fuente['estado'],
        )
        if lectura:
            intento.dni_leido = lectura.dni
            intento.apellidos_leidos = lectura.apellidos
            intento.nombres_leidos = lectura.nombres
            intento.fecha_nacimiento = lectura.fecha_nacimiento
            intento.fecha_vencimiento = lectura.fecha_vencimiento
            intento.mrz_valida = lectura.mrz_valida
        intento.anverso.save('anverso.jpg', ContentFile(anverso), save=False)
        intento.reverso.save('reverso.jpg', ContentFile(reverso), save=False)
        if foto:
            intento.foto_titular.save('titular.jpg', ContentFile(foto), save=False)
        intento.save()
        return Response(a_json(intento), status=201)

    def get(self, request):
        if 'prestador' in request.query_params:
            try:
                id_prestador = int(request.query_params['prestador'])
            except ValueError:
                return Response({'prestador': ['Debe ser un número entero.']}, status=400)
            intentos = IntentoDni.objects.filter(prestador_id=id_prestador)
            return Response([a_json(i) for i in intentos])

        intento = request.user.prestador.intentos_dni.first()
        if intento is None:
            return Response({'detail': 'Todavía no verificaste tu DNI.'}, status=404)
        return Response(a_json(intento))


class FotoIntentoDni(APIView):
    """Las fotos del DNI no tienen URL pública: solo el administrador las pide aquí."""
    permission_classes = [EsAdministrador]

    def get(self, request, intento_id, tipo):
        if tipo not in FOTOS:
            raise Http404
        try:
            intento = IntentoDni.objects.get(id=intento_id)
        except IntentoDni.DoesNotExist:
            raise Http404
        archivo = intento.foto_titular if tipo == 'titular' else getattr(intento, tipo)
        if not archivo:
            raise Http404
        return FileResponse(archivo.open('rb'), content_type='image/jpeg')
