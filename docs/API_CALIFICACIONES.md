# Calificación del servicio (HU-12)

> **Historia:** como cliente, quiero calificar al prestador (1 a 5 estrellas y comentario) al finalizar el servicio, a fin de ayudar a otros usuarios a decidir.
>
> **Criterios:** solo se puede calificar cuando el servicio está Finalizado; la calificación queda guardada y actualiza el promedio del prestador.

Implementación de T12.1 (API) y T12.2 (pantalla), David Valcarcel, 23/11/2026. App de Django `backend/django/calificaciones/`; pantalla `web-portal/src/MisServicios.jsx` en `/mis-servicios`.

## Decisiones del PO (23/11)

| Tema | Decisión |
|---|---|
| Estado Finalizado | Se agrega solo el valor `finalizada` a `Solicitud.Estado`. Los estados intermedios de HU-11 (en camino, en servicio) y quién marca cada uno los define Luis en T11.1. Mientras no exista esa pantalla, administración puede marcar `Finalizada` desde `/admin/` (lista de solicitudes, columna Estado). |
| Quién califica | El **cliente con sesión iniciada**. Si envía la solicitud con su sesión, la solicitud guarda su cuenta (`Solicitud.cliente`) y solo él puede calificarla. Las solicitudes enviadas sin cuenta se siguen aceptando, pero no se califican. |

## Cambios en modelos

- `servicios.Solicitud`: nuevo `cliente` (FK a la cuenta, opcional) y el estado `finalizada` (migración `servicios.0003`).
- `prestadores.Prestador`: `calificacion_promedio` (2 decimales, `null` sin calificaciones) y `calificaciones_total` (migración `prestadores.0003`). Se recalculan en la misma transacción en que se guarda cada calificación.
- `calificaciones.Calificacion`: solicitud (única: una calificación por servicio), prestador, cliente, estrellas (1 a 5, también como restricción de la base) y comentario (hasta 500 caracteres, opcional).

## Endpoints

Todos exigen sesión con rol **cliente** (403 si no) y, en `POST`, la cabecera `X-CSRFToken`.

### `POST /api/calificaciones/`

```json
{ "solicitud": 4, "estrellas": 4, "comentario": "Dejó todo impecable y llegó puntual." }
```

| Respuesta | Cuándo |
|---|---|
| `201` | Guardada. Devuelve la calificación y el promedio nuevo del prestador. |
| `400` | `solicitud` o `estrellas` no son enteros JSON (se rechazan `"5"`, `4.5`, `true`), estrellas fuera de 1 a 5 o comentario de más de 500 caracteres. |
| `403` | Sin sesión, sin CSRF o con rol prestador o administrador. |
| `404` | La solicitud no existe, es de otro cliente o se envió sin cuenta (no se revela cuál). |
| `409` | `Solo puedes calificar un servicio Finalizado.` o `Ya calificaste este servicio.` |

```json
{
  "solicitud": 4,
  "calificacion": { "estrellas": 4, "comentario": "Dejó todo impecable y llegó puntual.", "creada": "2026-11-23T14:45:10Z" },
  "prestador": { "id": 3, "calificacion_promedio": 4.0, "calificaciones_total": 1 }
}
```

### `GET /api/calificaciones/mis-servicios/`

Solicitudes que el cliente envió con su cuenta, de la más reciente a la más antigua. No incluye el teléfono del cliente.

```json
[{
  "id": 4, "prestador": { "id": 3, "nombre": "Marta Vargas" }, "rubro": "Limpieza",
  "descripcion": "Limpieza de la sala.", "estado": "finalizada", "estado_display": "Finalizada",
  "creada": "2026-11-23T14:40:00Z", "calificacion": null, "puede_calificar": true
}]
```

### Búsqueda (`GET /api/prestadores/buscar/`)

Cada prestador incluye ahora `calificacion_promedio` (número o `null`) y `calificaciones_total`. La web muestra «★ 4.0 · 1 calificación» o «Sin calificaciones» en la tarjeta y en el mapa. **T10.2 (Renzo)** puede leer estos dos campos para el puntaje; `null` significa «sin calificaciones» (valor neutro según la fórmula de T10.1).

## Cómo probarlo

1. Inicia sesión como **cliente** y envía una solicitud desde **Buscar servicios**.
2. Como administrador, en `/admin/` → Solicitudes, cambia su estado a **Finalizada** y guarda.
3. Como el cliente, entra a **Mis servicios**, elige las estrellas, escribe un comentario y envía.
4. Vuelve a buscar ese rubro: la tarjeta del prestador muestra el promedio.

Pruebas automáticas (dentro de `backend/django/` y `web-portal/`):

```powershell
python manage.py test calificaciones
```

```powershell
npm run test:hu12
```

## Para T12.3 (Luis)

Las pruebas de `calificaciones/tests.py` (22) ya cubren las reglas del servidor; las de `web-portal/tests/hu12/` (10) cubren la pantalla con la API simulada. T12.3 queda como **prueba cruzada**: repetir el flujo de «Cómo probarlo» con la transición real a Finalizada de HU-11 cuando exista.

## Límites

- No hay pantalla pública con los comentarios: se guardan y se ven en `/admin/`, y la búsqueda muestra solo promedio y total.
- El cliente no puede editar ni borrar su calificación. Si administración borra una desde `/admin/`, el promedio se recalcula.
