# Búsqueda de prestadores — T3.1 (Luis Abad)

## Endpoint

`GET /api/prestadores/buscar/`

Consulta pública, sin autenticación. Requiere los tres parámetros:

| Parámetro | Valor |
|---|---|
| `rubro` | ID entero positivo de un rubro existente; consultar `GET /api/prestadores/rubros/` |
| `latitud` | Coordenada numérica finita del cliente, entre -90 y 90 grados inclusive |
| `longitud` | Coordenada numérica finita del cliente, entre -180 y 180 grados inclusive |

Ejemplo (reemplazar `1` por el ID del rubro elegido):

```http
GET /api/prestadores/buscar/?rubro=1&latitud=-12.046374&longitud=-77.042793
```

Respuesta `200 OK`, ejemplo ilustrativo del formato, sin crear registros:

```json
[
  {
    "id": 7,
    "nombres": "Ana",
    "apellidos": "Pérez",
    "rubros": [{"id": 1, "nombre": "Gasfitería"}],
    "distancia_km": 2.345678
  }
]
```

Devuelve todos los rubros de cada prestador. Los únicos campos de salida son
`id`, `nombres`, `apellidos`, `rubros` y `distancia_km`; no incluye DNI,
teléfono, datos de cuenta, contraseñas ni calificaciones.
Sin coincidencias devuelve `200 OK` con `[]`, sin paginación.

Los errores devuelven `400 Bad Request` por parámetro, en español. Ejemplo:

```json
{"latitud": ["La coordenada debe estar entre -90 y 90 grados."]}
```

Un ID de rubro inexistente también devuelve 400; un rubro existente sin
prestadores que cumplan los filtros devuelve una lista vacía.

## Cálculo y alcance

La búsqueda reside en `servicios`, conforme a la organización del backend
en `PLAN_EQUIPO.md`, y reutiliza los modelos de `prestadores` sin modificarlos.
Primero filtra en la base de datos por rubro y `disponible=True`.
Después calcula la distancia geográfica entre el centro de cobertura del
prestador y el cliente mediante Haversine, usando un radio terrestre medio
de 6371.0088 km. No es una distancia de ruta ni un tiempo de llegada.

Solo incluye al prestador si la distancia es menor o igual a su propio
`radio_cobertura_km`. Compara sin redondear y admite una tolerancia absoluta
de 0.000000001 km (un micrómetro) para el error numérico en el límite.
`servicios.distancias.distancia_km` queda disponible para reutilizar en T3.3.
Los rubros se precargan con `prefetch_related`.

No hay ordenamiento por distancia ni garantía de orden; T3.3 corresponde a
David. Tampoco se añade un filtro por estado de verificación: T3.1 aplica
los filtros solicitados de rubro, disponibilidad y cobertura.
T3.2 ya está conectada a esta API en Resultados, con selector de rubros y
ubicación Leaflet; véase [cómo probar el frontend](../frontend/README.md).
Luis confirmó manualmente la tarjeta ficticia a 0 km y respuestas HTTP 200
vacía y positiva. Quedan pendientes la revisión visual completa y el PR.
HU-3 continúa parcial y la verificación de rendimiento de T3.4 está pendiente.
El avance de David `ae51360` usa un endpoint separado en `certificaciones`;
se conserva en la base de la rama y aún debe integrarse con esta búsqueda.

## Probar en Windows

Preparación opcional de una coincidencia ficticia en la base **local**:

```powershell
.\venv\Scripts\python.exe manage.py preparar_busqueda_local
```

Crea un único prestador `Demo Gasfitero ficticio`, con Gasfitería,
disponible, centro -12.046374/-77.042793 y radio 5 km. La cuenta tiene
contraseña inutilizable y el DNI es ficticio. Repetir el comando reutiliza
el mismo registro sin cambios; si sus identificadores están ocupados o el
registro fue modificado, aborta sin actualizar ni borrar registros.
Esto es una prueba positiva local de T3.1, no los datos ni las pruebas de
rendimiento de T3.4. Verificado el 06/10: primera ejecución creó id 2,
segunda ejecución lo reutilizó; consulta HTTP real al servidor local con
rubro 1 devolvió 200 y el prestador a 0.0 km.

1. Desde la raíz del repositorio, ejecutar el backend (si ya está corriendo,
   usar ese servidor):

   ```powershell
   .\venv\Scripts\python.exe manage.py runserver
   ```

2. En otra terminal, consultar los rubros:

   ```powershell
   Invoke-RestMethod 'http://127.0.0.1:8000/api/prestadores/rubros/'
   ```

3. Elegir un ID y consultar la ubicación del cliente:

   ```powershell
   Invoke-RestMethod 'http://127.0.0.1:8000/api/prestadores/buscar/?rubro=1&latitud=-12.046374&longitud=-77.042793' | ConvertTo-Json -Depth 5
   ```

   También se puede abrir esa URL en el navegador. Un registro nuevo tiene
   `disponible=False` por defecto, así que no aparece. Para obtener una
   coincidencia manual debe existir un prestador disponible del rubro y con
   cobertura en esa ubicación. Esta implementación no cambia registros locales.

4. Comprobar errores abriendo `/api/prestadores/buscar/` sin parámetros y
   repitiendo la consulta con `latitud=91`: ambos devuelven 400.

5. Ejecutar la suite automática, que crea sus datos exclusivamente en la
   base de pruebas y la destruye al terminar:

   ```powershell
   .\venv\Scripts\python.exe manage.py test --noinput
   ```

Verificado el 06/10/2026: **18 pruebas, OK**, comprobación de Django sin
problemas. Incluye 7 pruebas existentes de registro y 11 nuevas de distancia
y búsqueda: filtros combinados, distintos radios, límite y puntos a ambos
lados, comparación sin redondear, campos públicos, parámetros faltantes e
inválidos, extremos válidos y ausencia de resultados.
