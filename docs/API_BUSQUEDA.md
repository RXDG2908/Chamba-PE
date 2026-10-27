# Búsqueda de prestadores — T3.1, T3.3 y T7.1

## Endpoint y parámetros

`GET /api/prestadores/buscar/` es una consulta pública, sin autenticación.
Se reutiliza para tarjetas y para preparar los marcadores de T7.2; no hay un endpoint nuevo.

| Parámetro obligatorio | Contrato |
|---|---|
| `rubro` | ID entero positivo de un rubro existente en `GET /api/prestadores/rubros/` |
| `latitud` | Coordenada numérica finita del cliente, entre -90 y 90 inclusive |
| `longitud` | Coordenada numérica finita del cliente, entre -180 y 180 inclusive |

No se requiere un radio del cliente: se respeta el `radio_cobertura_km` de cada prestador.

Ejemplo con Gasfitería (ID 1, confirmado mediante consulta de solo lectura a los rubros locales el 19/10/2026):

```http
GET /api/prestadores/buscar/?rubro=1&latitud=-12.05&longitud=-77.04
```

Respuesta `200 OK` ilustrativa (no crea registros):

```json
[
  {
    "id": 7,
    "nombres": "Ana",
    "apellidos": "Pérez",
    "rubros": [{"id": 1, "nombre": "Gasfitería"}],
    "latitud": -12.046374,
    "longitud": -77.042793,
    "distancia_km": 0.504792
  }
]
```

La distancia del ejemplo está aproximada para su lectura. La API devuelve el cálculo sin redondear.
Los únicos campos de cada resultado son `id`, `nombres`, `apellidos`, `rubros`,
`latitud`, `longitud` y `distancia_km`. Los rubros contienen únicamente `id` y `nombre`.
Las coordenadas se devuelven como **números JSON**, aunque el modelo las almacena
como Decimal con seis decimales. Representan el centro de cobertura registrado
del prestador, no su ubicación en tiempo real ni las coordenadas del cliente.

Se conservan los campos y tipos consumidos por las tarjetas actuales; las dos
coordenadas son campos adicionales. No se exponen DNI, teléfono, correo,
cuentas, documentos ni estado administrativo. Sin coincidencias: `200 OK` con
`[]`, sin paginación. Las respuestas 200 conservan `X-Execution-Time-Ms`
(milisegundos de ejecución de la vista, incluida la serialización; no mide la red).

Parámetros faltantes o inválidos, incluidos rubros inexistentes, devuelven
`400 Bad Request` con mensajes por campo en español. Ejemplo:

```json
{"latitud": ["La coordenada debe estar entre -90 y 90 grados."]}
```

Un rubro existente sin coincidencias devuelve una lista vacía, no un error.

## Distancia, filtros y dependencias

`servicios.views.BuscarPrestadoresView` filtra por `disponible=True` y rubro,
precarga todos los rubros y reutiliza `servicios.distancias.distancia_km`.
Calcula Haversine con radio terrestre medio de 6371.0088 km, no distancia de
ruta ni tiempo de llegada. Incluye al prestador cuando la distancia al cliente
es menor o igual a su propio radio; compara sin redondear, con tolerancia
absoluta de 1e-9 km en el límite. Ordena por distancia ascendente.

**Verificación pendiente de integración (David, T9.1):** existe
`Prestador.estado` con `pendiente`, `verificado` y `rechazado`, pero la búsqueda
actual todavía no filtra por él. La planificación de `origin/planning-sprint-2`
asigna ese filtro y el de solicitudes a David. Tras actualizar las referencias
el 19/10, su rama `origin/feature/t9.1-revision-documentos` incorpora revisión de
certificaciones, pero todavía no actualiza `Prestador.estado` al aprobar ni
aplica los filtros en búsqueda/solicitudes. T7.1 no duplica ese trabajo.
Por tanto, esta respuesta aún puede incluir prestadores pendientes o rechazados
si cumplen disponibilidad, rubro y cobertura; **HU-7 no está completa**.
Coordinar con David la transición de estado, el filtro y sus pruebas de integración.

**Calificación pendiente (HU-12):** no hay modelo ni cálculo real de calificaciones.
El contrato actual omite ese campo. Propuesta para acordar con T7.2/HU-12:
`calificacion_promedio: null` y `cantidad_calificaciones: 0` cuando no haya
reseñas; mostrar «Sin calificaciones». Estos campos son una propuesta, **no
forman parte de la respuesta implementada**. Hasta acordar e integrar el soporte,
T7.2 puede mostrar ese texto cuando el campo está ausente; nunca interpretar
su ausencia como cero estrellas ni inventar puntuaciones.

La rama `origin/feature/t5.1-autenticacion` de Renzo incorpora roles y sesiones,
pero conserva la búsqueda pública. T7.1 no necesita fusionarla. Confirmar ese
contrato al integrar T5.1/T6.1. Los marcadores (T7.2), las pruebas cruzadas de
precisión (David, T7.3), las notificaciones y HU-12 quedan fuera de la entrega original T7.1.

## Probar en Windows sin modificar registros locales

```powershell
.\venv\Scripts\python.exe manage.py runserver
```

En otra terminal, confirmar primero el rubro y luego consultar:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8000/api/prestadores/rubros/'
Invoke-WebRequest 'http://127.0.0.1:8000/api/prestadores/buscar/?rubro=1&latitud=-12.05&longitud=-77.04'
```

Gasfitería tiene ID 1 en la base local consultada; en otro entorno usar el ID
que devuelva el listado. `Invoke-WebRequest` permite revisar tanto JSON como
cabecera de tiempo. Puede devolver `[]` si no hay prestadores elegibles; no se
necesita alterar registros para validar esa respuesta. Sin parámetros o con
`latitud=91`, debe devolver 400. Las tarjetas existentes siguen en Resultados;
la entrega original T7.1 no dibuja marcadores. T7.2 los añade en la misma pantalla;
ver [comprobaciones del mapa](pruebas-T7.2.md).

```powershell
.\venv\Scripts\python.exe manage.py test --noinput
```

Verificado el 19/10/2026: **60 pruebas OK**, en 2.574 s, comprobación de Django
sin problemas. Base de pruebas separada, creada y destruida por Django; no se
modificaron registros locales. Incluye dos pruebas nuevas de coordenadas reales
del prestador, compatibilidad con tarjetas, cabecera de tiempo y combinación de
filtros/orden/cobertura, más las regresiones existentes de validación, radios
individuales, límite inclusivo, comparación sin redondeo, privacidad y rendimiento
(< 3000 ms para el escenario de prueba). No se ejecutaron pruebas visuales del
frontend ni se validó la integración de ramas todavía separadas.

Evidencia manual reportada por Luis el 19/10/2026: HTTP 200 con el prestador
ficticio id `2`, latitud `-12.046374`, longitud `-77.042793`,
`distancia_km: 0.0` y `X-Execution-Time-Ms: 16.782`. Es una comprobación
puntual de la respuesta; el filtro Verificado sigue pendiente de integración
y T7.1 sigue pendiente de revisión/PR. HU-7 no se declara completa.
