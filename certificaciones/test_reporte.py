import time
import requests

url = "http://127.0.0.1:8000/api/certificaciones/prestadores/cercanos/?lat=-12.0464&lon=-77.0428"

print("Realizando petición al endpoint de cercanía...")
inicio = time.time()
response = requests.get(url)
fin = time.time()

tiempo_ms = (fin - inicio) * 1000
tiempo_s = fin - inicio

reporte = f"""=== REPORTE DE VERIFICACIÓN DE RENDIMIENTO (T3.3) ===
- Endpoint consultado: {url}
- Código de Estado HTTP: {response.status_code}
- Total de registros devueltos: {len(response.json())}
- Tiempo de respuesta: {tiempo_ms:.2f} ms ({tiempo_s:.4f} segundos)
- Resultado: {'APROBADO (< 3 segundos)' if tiempo_s < 3.0 else 'REPROBADO'}
===================================================
"""

print(reporte)

# Guardar la evidencia en un archivo de texto para GitHub
with open("evidencia_rendimiento.txt", "w", encoding="utf-8") as f:
    f.write(reporte)

print("¡Evidencia guardada exitosamente en 'evidencia_rendimiento.txt'!")