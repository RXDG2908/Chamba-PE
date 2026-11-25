# API de recomendación — HU-10

> **HU-10.** Como **cliente**, quiero que se me recomiende el prestador más adecuado, a fin de contratar más rápido.
> Código: `backend/django/ia/` (fórmula en `recomendacion.py`). Pantalla: «Recomendado para ti» en la búsqueda (`/buscar`).

## Endpoint

`GET /api/ia/recomendacion/?rubro=<id>&latitud=<grados>&longitud=<grados>`

- Público, igual que la búsqueda, y con **los mismos parámetros y mensajes de error** (400).
- Solo considera a los prestadores que también devuelve `GET /api/prestadores/buscar/`: verificados, disponibles, del rubro y con cobertura sobre el cliente.

```json
{
  "recomendado": {
    "id": 7, "nombres": "Rosa", "apellidos": "Quispe", "rubros": [{"id": 1, "nombre": "Gasfitería"}],
    "latitud": -12.028, "longitud": -77.043, "distancia_km": 2.0,
    "calificacion_promedio": 4.9, "calificaciones_total": 25,
    "puntaje": 87.8,
    "tiempo_estimado_min": 16,
    "servicios_en_curso": 0,
    "motivo": "Está a 2,0 km de ti, tiene ★ 4,9 con 25 calificaciones y no tiene servicios en curso."
  },
  "alternativas": [ { "...": "mismos campos, hasta 3, de mayor a menor puntaje" } ]
}
```

Si nadie cubre la ubicación: `{"recomendado": null, "alternativas": []}`.

## Fórmula del puntaje (T10.1)

Puntaje de 0 a 100 = **50 % cercanía + 35 % calificación + 15 % disponibilidad**.

| Factor | Cálculo (0 a 1) | Por qué |
|---|---|---|
| Cercanía | `max(0, 1 − distancia_km / 10)` | Llega antes. A 10 km o más ya no suma. |
| Calificación | Promedio bayesiano `(promedio × n + 3,5 × 3) / (n + 3)`, llevado de 1–5 a 0–1 | Un 5★ con una sola opinión no gana a un 4,8★ con veinte. Sin calificaciones vale 3,5★. |
| Disponibilidad | `1 / (1 + servicios en curso)` | Todos están «Activos» (filtro de HU-6); este factor premia al que además no tiene servicios confirmados, en camino o en servicio. |

Empates: más cercano primero y luego el ID más bajo.

**Tiempo estimado de llegada** = 10 min de preparación + trayecto a 20 km/h (tráfico urbano) + 30 min por cada servicio en curso. Es orientativo y se muestra como «≈ N min».

**Motivo**: frase en palabras simples con la distancia (dice «el más cercano» cuando lo es), la calificación y los servicios en curso.

Los pesos y constantes están al inicio de `ia/recomendacion.py`; HU-17 (backlog futuro) los volvería configurables.

## Ejemplos

| Caso | Resultado |
|---|---|
| A a 0,5 km sin calificaciones · B a 2 km con ★ 4,9 (25) | Gana B (87,8 contra 84,4) |
| A a 1 km con ★ 4,5 (10) · B a 6 km con ★ 4,6 (10) | Gana A, «Es el más cercano» |
| A a 1 km con 2 servicios en curso · B a 1,5 km libre | Gana B; A queda como alternativa con ≈ 73 min |

## Pruebas

`backend/django/ia/tests.py`: 13 pruebas (fórmula, orden, filtros de la búsqueda, empates, alternativas, validación y textos). T10.4 (pruebas cruzadas con datos de ejemplo) le toca a David.
