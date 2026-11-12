from django.shortcuts import render
import os
import math
import re
from django.http import JsonResponse
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from usuarios.permisos import EsPrestador
from .models import CertificacionPrestador

MAX_FILE_SIZE = 5 * 1024 * 1024  # 5 MB en bytes
# Extensión permitida → (tipo MIME esperado, firma de los primeros bytes).
FORMATOS_PERMITIDOS = {
    '.pdf': ('application/pdf', b'%PDF-'),
    '.jpg': ('image/jpeg', b'\xff\xd8\xff'),
    '.jpeg': ('image/jpeg', b'\xff\xd8\xff'),
}
PRESTADOR_ID = re.compile(r'[1-9]\d*')


def contenido_coincide(archivo, firma):
    """Compara la firma real del archivo sin depender de lo que declara el cliente."""
    archivo.seek(0)
    cabecera = archivo.read(len(firma))
    archivo.seek(0)
    return cabecera == firma

@api_view(['POST'])
@permission_classes([EsPrestador])
def upload_certification(request):
    """Carga de certificados (HU-2). S3-CERT-16: exige la sesión del prestador, con su CSRF,
    y el documento siempre queda en su propio perfil, nunca en el que diga el formulario."""
    prestador_asociado = request.user.prestador

    # El campo prestador es opcional; si llega, tiene que ser el del usuario con sesión.
    # Con JSON puede llegar como número o lista: se valida como texto para responder 400 y no 500.
    prestador_id = request.data.get('prestador') or request.data.get('prestador_id')
    if prestador_id:
        prestador_id = str(prestador_id)
        if not PRESTADOR_ID.fullmatch(prestador_id):
            return Response({'error': 'El prestador debe ser un número entero positivo.'}, status=400)
        if int(prestador_id) != prestador_asociado.pk:
            return Response({'error': 'Solo puedes subir certificados a tu propio perfil.'}, status=403)

    # Capturar el archivo de la petición
    uploaded_file = request.FILES.get('file') or request.FILES.get('certificacion')
    if not uploaded_file:
        return Response({'error': 'No se adjuntó ningún archivo.'}, status=400)

    # 1. Validar que el archivo pese menos de 5 MB
    if uploaded_file.size > MAX_FILE_SIZE:
        return Response({'error': 'El archivo excede el límite permitido de 5 MB.'}, status=400)

    # 2. Validar que extensión y tipo MIME correspondan al mismo formato (PDF o JPG)
    ext = os.path.splitext(uploaded_file.name)[1].lower()
    mime_esperado, firma = FORMATOS_PERMITIDOS.get(ext, (None, None))
    if uploaded_file.content_type != mime_esperado:
        return Response({'error': 'Formato no válido. Solo se permiten archivos PDF o JPG.'}, status=400)

    # 3. Validar el contenido real (magic bytes): un .txt renombrado a .pdf no pasa
    if not contenido_coincide(uploaded_file, firma):
        return Response({'error': 'El contenido del archivo no corresponde a un PDF o JPG válido.'}, status=400)

    # Tarea T2.1: Crear y persistir la certificación vinculada al prestador correcto
    cert = CertificacionPrestador.objects.create(
        prestador=prestador_asociado,
        archivo=uploaded_file,
        estado='Pendiente'
    )

    return Response({
        'message': 'Certificación cargada y vinculada exitosamente',
        'file_name': cert.archivo.name,
        'file_url': cert.archivo.url,
        'status': 'Pendiente de verificación'
    }, status=201)


# --- FÓRMULA DE HAVERSINE PARA LA TAREA T3.3 ---
def calcular_distancia(lat1, lon1, lat2, lon2):
    R = 6371.0  # Radio de la Tierra en kilómetros
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c

@api_view(['GET'])
def listar_prestadores_por_distancia(request):
    try:
        # Capturar la ubicación enviada por el usuario en la URL (ej: ?lat=-12.04&lon=-77.04)
        user_lat = float(request.GET.get('lat'))
        user_lon = float(request.GET.get('lon'))
    except (TypeError, ValueError):
        return Response({"error": "Debe proporcionar una latitud y longitud válidas."}, status=400)

    from prestadores.models import Prestador 
    from .serializers import CertificacionSerializer as PrestadorSerializer

    prestadores = Prestador.objects.all()
    prestadores_con_distancia = []

    for prestador in prestadores:
        # Calcular la distancia en kilómetros respecto a la posición del usuario
        dist = calcular_distancia(user_lat, user_lon, prestador.latitud, prestador.longitud)
        prestador.distancia_km = round(dist, 2)
        prestadores_con_distancia.append(prestador)

    # Ordenar los resultados de menor a mayor distancia (más cercano primero) - Tarea T3.3
    prestadores_con_distancia.sort(key=lambda x: x.distancia_km)

    serializer = PrestadorSerializer(prestadores_con_distancia, many=True)
    
    # Inyectar el campo de distancia calculada en el JSON de respuesta
    data = serializer.data
    for i, prestador in enumerate(prestadores_con_distancia):
        data[i]['distancia_km'] = prestador.distancia_km

    return JsonResponse(data, safe=False)