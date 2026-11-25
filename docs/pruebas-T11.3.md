# Pruebas de T11.3 · transición de estados de HU-11 · 23/11/2026

> Prueba cruzada: la hace David, no quien construyó HU-11 (Luis). Las pruebas de Luis cubren las reglas en Django y las pantallas con una API simulada; T11.3 junta las dos partes: **navegador real + Django real**, sin nada simulado.

## Qué se probó

Suite nueva `web-portal/tests/hu11-local/transiciones.spec.mjs`. Levanta un Django real (`backend/django/tools/hu11_backend_local.py`) con una base SQLite temporal que se borra al terminar, cuentas ficticias y una contraseña nueva en cada corrida.

| Prueba | Qué comprueba |
|---|---|
| Flujo completo en el navegador | El cliente busca y envía la solicitud desde la tarjeta. La prestadora, en otra sesión, la acepta y pulsa **Voy en camino**, **Empecé el servicio** y **Finalizar servicio**. El cliente, **sin recargar**, ve que terminó y la hora de las 4 etapas. En **Mis servicios** también ve las horas, califica con 5 estrellas y la búsqueda muestra «★ 5.0 · 1 calificación». |
| Reglas de transición | Contra el servidor real: avanzar sin aceptar → 409; saltar una etapa → 409 con `estado_actual`; estado inexistente → 400; otro prestador → 404; el cliente → 403; sin CSRF → 403; repetir o retroceder → 409; Finalizada y Rechazada son finales; el historial sale completo, en orden y con horas crecientes. |
| Tres avances a la vez | Tres pedidos simultáneos a «En camino»: uno responde 200, dos 409, y el historial no se duplica. |
| Calificar solo al final | En «En camino» y «En servicio» calificar da 409; ya Finalizada, otro cliente recibe 404, el dueño califica (201) y una segunda vez da 409. |

## Resultado

| Verificación | Resultado |
|---|---|
| `npm run test:hu11-local` (nueva) | 4/4 |
| Playwright HU-11 · HU-12 · HU-7 · HU-8 · HU-21 | 5/5 · 10/10 · 16/16 · 11/11 · 55/55 |
| Playwright HU-7 con Django real (`test:hu7-local`) | 14/14 (estaba en 13/14, ver hallazgo 2) |
| Django | 368/368 |
| Lint, build y worker push | OK |

## Hallazgos (corregidos en esta misma rama)

1. **«Mis servicios» no mostraba la hora de cada etapa.** La línea de etapas pedía el `historial`, pero `/api/calificaciones/mis-servicios/` no lo enviaba (en la prueba de Luis la API simulada sí lo traía). Ahora lo envía, sin una consulta extra por cada servicio. Prueba nueva en `calificaciones/tests.py`.
2. **La prueba de HU-7 con Django real fallaba desde HU-12.** Comprobaba que la búsqueda devolviera exactamente 7 campos, y HU-12 (#42) agregó `calificacion_promedio` y `calificaciones_total`. Como esa prueba solo se corre a mano, nadie lo notó. Ahora espera los 9 campos (sin calificaciones: `null` y `0`).

## Cómo correrla

```powershell
cd web-portal
$env:HU11_TEST_PYTHON = "..\.venv\Scripts\python.exe"
npm run test:hu11-local
```

Necesita el entorno de Python con `requirements.txt` (como `test:hu7-local`, que también acepta `HU7_TEST_PYTHON`). Usa los puertos 8021 (Django) y 5195 (web); la base y el build de prueba quedan en `.hu11-local/`, que git ignora.
