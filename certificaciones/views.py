from django.shortcuts import render
import os
import math
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.core.files.storage import FileSystemStorage
from rest_framework.decorators import api_view
from rest_framework.response import Response

MAX_FILE_SIZE = 5 * 1024 * 1024  # 5 MB en bytes
ALLOWED_EXTENSIONS = ['.pdf', '.jpg', '.jpeg']
ALLOWED_MIME_TYPES = ['application/pdf', 'image/jpeg']

@csrf_exempt
def upload_certification(request):
    # 1. Validar que sea método POST
    if request.method != 'POST':
        return JsonResponse({'error': 'Método no permitido. Usa POST.'}, status=405)

    # Capturar el archivo de la petición
    uploaded_file = request.FILES.get('file') or request.FILES.get('certificacion')
    
    if not uploaded_file:
        return JsonResponse({'error': 'No se adjuntó ningún archivo.'}, status=400)

    # 2. Validar que el archivo pese menos de 5 MB
    if uploaded_file.size > MAX_FILE_SIZE:
        return JsonResponse({'error': 'El archivo excede el límite permitido de 5 MB.'}, status=400)

    # 3. Validar que la extensión y tipo sean estrictamente PDF o JPG
    ext = os.path.splitext(uploaded_file.name)[1].lower()
    if ext not in ALLOWED_EXTENSIONS or uploaded_file.content_type not in ALLOWED_MIME_TYPES:
        return JsonResponse({'error': 'Formato no válido. Solo se permiten archivos PDF o JPG.'}, status=400)

    # 4. Guardar el archivo y devolver respuesta de éxito en JSON
    fs = FileSystemStorage()
    filename = fs.save(uploaded_file.name, uploaded_file)
    file_url = fs.url(filename)

    return JsonResponse({
        'message': 'Certificación cargada exitosamente',
        'file_name': filename,
        'file_url': file_url,
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

    # Corrección aplicada: Importación desde la app actual
    from .models import CertificacionPrestador as Prestador 
    from .serializers import CertificacionSerializer as PrestadorSerializer

    prestadores = Prestador.objects.all()
    prestadores_con_distancia = []

    for prestador in prestadores:
        # Calcular la distancia en kilómetros respecto a la posición del usuario
        dist = calcular_distancia(user_lat, user_lon, prestador.latitud, prestador.longitud)
        prestador.distancia_km = round(dist, 2)
        prestadores_con_distancia.append(prestador)

    # Ordenar los resultados de menor a mayor distancia (más cercano primero)
    prestadores_con_distancia.sort(key=lambda x: x.distancia_km)

    serializer = PrestadorSerializer(prestadores_con_distancia, many=True)
    
    # Inyectar el campo de distancia calculada en el JSON de respuesta
    data = serializer.data
    for i, prestador in enumerate(prestadores_con_distancia):
        data[i]['distancia_km'] = prestador.distancia_km

    return JsonResponse(data, safe=False)