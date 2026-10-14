from django.shortcuts import render
import os
import math
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.core.files.storage import FileSystemStorage
from rest_framework.decorators import api_view
from rest_framework.response import Response

# Importar el modelo Prestador para asociar la certificación (T2.1)
from prestadores.models import Prestador
from .models import CertificacionPrestador

MAX_FILE_SIZE = 5 * 1024 * 1024  # 5 MB en bytes
ALLOWED_EXTENSIONS = ['.pdf', '.jpg', '.jpeg']
ALLOWED_MIME_TYPES = ['application/pdf', 'image/jpeg']

@csrf_exempt
def upload_certification(request):
    # 1. Responder a la petición previa del navegador (Preflight OPTIONS)
    if request.method == 'OPTIONS':
        res = JsonResponse({})
        res["Access-Control-Allow-Origin"] = "*"
        res["Access-Control-Allow-Methods"] = "POST, OPTIONS"
        res["Access-Control-Allow-Headers"] = "*"
        return res

    # 2. Validar que sea método POST
    if request.method != 'POST':
        res = JsonResponse({'error': 'Método no permitido. Usa POST.'}, status=405)
        res["Access-Control-Allow-Origin"] = "*"
        res["Access-Control-Allow-Headers"] = "*"
        return res

    # Capturar el archivo de la petición
    uploaded_file = request.FILES.get('file') or request.FILES.get('certificacion')
    
    if not uploaded_file:
        res = JsonResponse({'error': 'No se adjuntó ningún archivo.'}, status=400)
        res["Access-Control-Allow-Origin"] = "*"
        res["Access-Control-Allow-Headers"] = "*"
        return res

    # 3. Validar que el archivo pese menos de 5 MB
    if uploaded_file.size > MAX_FILE_SIZE:
        res = JsonResponse({'error': 'El archivo excede el límite permitido de 5 MB.'}, status=400)
        res["Access-Control-Allow-Origin"] = "*"
        res["Access-Control-Allow-Headers"] = "*"
        return res

    # 4. Validar que la extensión y tipo sean estrictamente PDF o JPG
    ext = os.path.splitext(uploaded_file.name)[1].lower()
    if ext not in ALLOWED_EXTENSIONS or uploaded_file.content_type not in ALLOWED_MIME_TYPES:
        res = JsonResponse({'error': 'Formato no válido. Solo se permiten archivos PDF o JPG.'}, status=400)
        res["Access-Control-Allow-Origin"] = "*"
        res["Access-Control-Allow-Headers"] = "*"
        return res

    # Tarea T2.1: Obtener o asociar el prestador desde la petición
    prestador_id = request.POST.get('prestador') or request.POST.get('prestador_id')
    
    if prestador_id:
        try:
            prestador_asociado = Prestador.objects.get(id=prestador_id)
        except Prestador.DoesNotExist:
            return JsonResponse({'error': 'El prestador especificado no existe.'}, status=404)
    else:
        # Si no se envía prestador, tomamos el primero o creamos uno por defecto con un usuario válido para las pruebas
        prestador_asociado = Prestador.objects.first()
        if not prestador_asociado:
            from django.contrib.auth.models import User
            usuario_default, _ = User.objects.get_or_create(
                username="test_prestador_default",
                defaults={"email": "test@example.com"}
            )
            prestador_asociado = Prestador.objects.create(
                usuario=usuario_default,
                nombres="Prestador",
                apellidos="Prueba",
                dni="00000000",
                telefono="999999999",
                latitud=-12.046374,
                longitud=-77.042793,
                radio_cobertura_km=5.0,
                disponible=True
            )

    # 5. Guardar físicamente el archivo
    fs = FileSystemStorage()
    filename = fs.save(uploaded_file.name, uploaded_file)
    file_url = fs.url(filename)

    # Tarea T2.1: Crear y persistir la certificación vinculada al prestador correcto
    CertificacionPrestador.objects.create(
        prestador=prestador_asociado,
        archivo=uploaded_file,
        estado='Pendiente'
    )

    res = JsonResponse({
        'message': 'Certificación cargada y vinculada exitosamente',
        'file_name': filename,
        'file_url': file_url,
        'status': 'Pendiente de verificación'
    }, status=201)

    # Inyectar las cabeceras CORS obligatorias en la respuesta de éxito
    res["Access-Control-Allow-Origin"] = "*"
    res["Access-Control-Allow-Methods"] = "POST, OPTIONS"
    res["Access-Control-Allow-Headers"] = "*"
    return res


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