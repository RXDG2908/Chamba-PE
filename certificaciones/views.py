from django.shortcuts import render
import os
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.core.files.storage import FileSystemStorage

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