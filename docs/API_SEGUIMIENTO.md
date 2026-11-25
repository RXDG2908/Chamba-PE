# Seguimiento del servicio (HU-11)

> **Historia:** como cliente, quiero ver en qué etapa va mi servicio (Confirmado, En camino, En servicio, Finalizado), a fin de saber cuándo llega el prestador.

Implementación de T11.1 (backend) y T11.2 (pantallas), Luis Abad, 24/11/2026. App `backend/django/servicios/`; pantallas `web-portal/src/SolicitudesRecibidas.jsx` (prestador), `MisServicios.jsx` y `SolicitudServicio.jsx` (cliente) con la línea de etapas de `Seguimiento.jsx`.

## Acuerdos con David (PO), 23/11

| Tema | Decisión |
|---|---|
| Estados | Se reutiliza `Solicitud.estado`, sin duplicarlo. `aceptada` se muestra como **Confirmado**; se agregan `en_camino` y `en_servicio`; el final es `finalizada` (el que habilita la calificación de HU-12). |
| Orden | `aceptada → en_camino → en_servicio → finalizada`. Solo avanza un paso, sin saltar ni retroceder. Si no, **409** (igual que aceptar/rechazar). |
| Quién | Solo el **prestador asignado** a la solicitud. Otro prestador recibe **404**. |
| Finales | `rechazada` y `finalizada` no cambian más. `pendiente` se responde con aceptar/rechazar (HU-4). |
| Columna | `estado` pasa de `max_length=10` a **20** (`servicios.0004`): `en_servicio` tiene 11 caracteres y PostgreSQL rechazaba el valor. |
| Tiempo real | Consulta automática **cada 5 segundos** mientras el servicio sigue en curso (decisión del 23/11 por el tiempo del sprint; no hace falta Channels ni otro servidor). Se puede cambiar a WebSocket después sin tocar el contrato. |
| Admin | Se retira el `list_editable` temporal de HU-12: en `/admin/` el estado y su historial son de solo lectura, para que nadie se salte etapas. |

## Cambios en modelos (migración `servicios.0004_seguimiento_estados`)

- `Solicitud.estado`: nuevas opciones `en_camino` y `en_servicio`, etiqueta «Confirmado» para `aceptada`, `max_length=20`.
- `CambioEstado` (nuevo): historial con `solicitud`, `estado` y `fecha`. Se crea uno al aceptar, al rechazar y en cada avance, en la misma transacción del cambio.

## Endpoint nuevo

### `POST /api/servicios/solicitudes/<id>/avanzar/`

Sesión con rol **prestador** y cabecera `X-CSRFToken`. El cuerpo dice a qué estado pasa, para que un doble clic no avance dos etapas:

```json
{ "estado": "en_camino" }
```

| Respuesta | Cuándo |
|---|---|
| `200` | Avanzó. Devuelve la solicitud como en la bandeja, con `estado_display`, `siguiente_estado` e `historial`. |
| `400` | Falta `estado` o no es un estado válido. |
| `403` | Sin sesión, sin CSRF o sin rol prestador. |
| `404` | La solicitud no existe o es de otro prestador. |
| `409` | No es el siguiente paso (salto, retroceso, `pendiente`, estado final o ya avanzó en otra pestaña). Devuelve `estado` (mensaje) y `estado_actual`. |

El cambio es condicional (`UPDATE ... WHERE estado = <actual>`): si dos pestañas pulsan a la vez, solo una avanza y la otra recibe 409.

## Cambios en respuestas existentes

- Bandeja del prestador (`/api/servicios/solicitudes/recibidas/` y `.../recibidas/<id>/`): suma `estado_display`, `siguiente_estado` (o `null`) e `historial`.
- Consulta del cliente (`GET /api/servicios/solicitudes/<id>/`): suma `historial` con `estado`, `estado_display` y `fecha`.
- «Mis servicios» (HU-12) ya muestra `estado_display`, así que «En camino» y «En servicio» aparecen sin cambiar `calificaciones`. Desde T11.3 también recibe el `historial`, para mostrar la hora de cada etapa.

## Pruebas

- Django: `python manage.py test servicios.test_seguimiento` (17): flujo completo, historial, sin saltos ni retrocesos, doble paso, `pendiente`, finales, 404 de otro prestador, 403, 400, longitud de la columna, bandeja sin consultas de más e integración con HU-12 (solo se califica después de que el prestador finaliza).
- Navegador: `npm run test:hu11` (5, con la API simulada): botones del prestador en orden, línea de etapas, 409 que recarga, rechazada sin botones y el cliente que ve el avance solo y deja de consultar al finalizar.
- De punta a punta (T11.3): `npm run test:hu11-local` (4, navegador y Django reales, sin nada simulado): flujo completo hasta calificar, reglas de transición, avances simultáneos y calificar solo al final. Detalle en [pruebas-T11.3.md](pruebas-T11.3.md).

## Cómo probarla a mano

1. Con sesión de cliente, busca un prestador y envíale una solicitud.
2. Con la sesión del prestador, en `/solicitudes` pulsa **Aceptar**, luego **Voy en camino**, **Empecé el servicio** y **Finalizar servicio**.
3. En otra ventana con la sesión del cliente, `/mis-servicios` cambia de etapa sola en unos segundos; al llegar a Finalizada aparece el formulario de calificación (HU-12).
