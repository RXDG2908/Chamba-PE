# Pruebas de T10.4 · HU-10 con datos de ejemplo · 24/11/2026

> Prueba cruzada: la hace David, no quien construyó HU-10 (Renzo). Todos los datos son ficticios.

Las 13 pruebas de Renzo (`ia/tests.py`) ya cubren la fórmula y los casos básicos: cerca sin calificaciones contra valorado, ocupado contra libre, 5★ con una opinión contra 4,8★ con veinte, y sin prestadores. T10.4 agrega lo que faltaba, en `backend/django/ia/test_cruzadas.py` (7 pruebas).

## 1. HU-10 junto al resto del sistema (con las API reales)

| Prueba | Resultado |
|---|---|
| **HU-11:** el cliente pide un servicio a Ana (1 km) y ella lo avanza por la API | Pendiente no la ocupa (sigue recomendada). Al aceptarla, «En camino» y «En servicio» cuenta 1 servicio en curso y se recomienda a Beto (1,5 km), con el tiempo estimado de Ana +30 min. Al finalizar vuelve a ser la recomendada. Una solicitud rechazada no la ocupa. |
| **HU-12:** el cliente califica a Beto (2 km) con 5★ por la API | Con 0 y 1 calificaciones gana Ana (1 km, sin calificaciones); con 2, gana Beto. El motivo dice «tiene ★ 5,0 con 2 calificaciones». |
| **HU-6:** Ana se desactiva por la API de disponibilidad | Sale de la recomendación y de las alternativas. |
| Primer ejemplo del documento | Da exactamente los puntajes que dice: 87,8 contra 84,4. |

## 2. Muestra grande de datos ficticios

300 prestadores al azar (semilla fija, siempre los mismos): posición a ±13 km, radio de 1 a 15 km, 90 % activos, 85 % verificados, 60 % con calificaciones (1 a 5★, 1 a 40 opiniones), dos rubros y de 0 a 3 solicitudes en cualquier estado. Se consulta desde 5 puntos y con los 2 rubros.

| Comprobación | Resultado |
|---|---|
| El recomendado y las alternativas coinciden con la fórmula de `docs/API_RECOMENDACION.md`, **calculada aparte en la prueba** (sin usar `ia/recomendacion.py`): orden, puntaje, servicios en curso y tiempo estimado | ✅ En todas las búsquedas con resultados |
| Solo recomienda a quien sale en la búsqueda; si la búsqueda está vacía, `recomendado: null` | ✅ |
| Puntaje entre 0 y 100, como mucho 3 alternativas, motivo con mayúscula y punto final | ✅ |
| Solo datos públicos (sin DNI, teléfono ni estado) | ✅ |
| Mismas consultas a la base con muchos o pocos resultados (sin una consulta por prestador) | ✅ |
| Responde en menos de 3 s (criterio de HU-3, la recomendación sale en la misma pantalla) | ✅ |

## Resultado

| Verificación | Resultado |
|---|---|
| `ia` (13 de Renzo + 7 de T10.4) | 20/20 |
| Django completo | 399/399 |
| Playwright HU-7 · HU-11 · HU-12 · HU-8 · HU-21 | 16/16 · 5/5 · 10/10 · 11/11 · 55/55 |
| Playwright con Django real: HU-7 · HU-11 (T11.3) | 14/14 · 4/4 (HU-7 estaba en 3/14, ver hallazgo 1) |
| Lint y build | OK |

## Hallazgos

1. **La insignia «✨ Recomendado» rompía la prueba de HU-7 con Django real (11 de 14 fallaban).** Iba dentro del título de la tarjeta (`<h4>`), así que el título pasaba a ser «Ana Pérez ✨ Recomendado»: los lectores de pantalla la leían como parte del nombre y la prueba que compara los nombres fallaba. **Corregido en esta rama:** la insignia va debajo del título. HU-7 vuelve a 14/14.
2. Menor: el orden usa el puntaje ya redondeado a un decimal, así que dos prestadores a menos de 0,05 puntos cuentan como empate y gana el más cercano. En la muestra no cambió ningún resultado; si se quiere, se puede anotar en el documento.
3. Menor: la respuesta es pública y dice cuántos servicios en curso tiene cada prestador. No es un dato personal, pero cualquiera puede ver qué tan ocupado está.
4. No hay prueba de navegador del recuadro «Recomendado para ti»: las suites de Playwright cargan la pantalla con la recomendación activa, pero no la revisan.

## Cómo correrlas

```powershell
cd backend/django
python manage.py test ia
```
