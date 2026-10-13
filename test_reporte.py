import time
import urllib.request
import json

url = "http://127.0.0.1:8000/api/certificaciones/prestadores/cercanos/?lat=-12.0464&lon=-77.0428"

print("Realizando petición al endpoint de cercanía...")
inicio = time.time()
try:
    with urllib.request.urlopen(url) as response:
        status_code = response.getcode()
        body = response.read().decode('utf-8')
        data = json.loads(body)
        total_registros = len(data)
except Exception as e:
    status_code = "Error"
    total_registros = 0
    print(f"Error en la petición: {e}")

fin = time.time()

tiempo_ms = (fin - inicio) * 1000
tiempo_s = fin - inicio

reporte = f"""=== REPORTE DE VERIFICACIÓN DE RENDIMIENTO (T3.3) ===
- Endpoint consultado: {url}
- Código de Estado HTTP: {status_code}
- Total de registros devueltos: {total_registros}
- Tiempo de respuesta: {tiempo_ms:.2f} ms ({tiempo_s:.4f} segundos)
- Resultado: {'APROBADO (< 3 segundos)' if isinstance(tiempo_s, float) and tiempo_s < 3.0 else 'REPROBADO'}
===================================================
"""

print(reporte)

with open("evidencia_rendimiento.txt", "w", encoding="utf-8") as f:
    f.write(reporte)

print("¡Evidencia guardada exitosamente en 'evidencia_rendimiento.txt'!")
