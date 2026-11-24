# Pruebas de T19.4 · HU-19 con DNIs de muestra · 23/11/2026

> Prueba cruzada: la hace David, no quien construyó HU-19 (Renzo). Todos los DNIs son **ficticios** (`docs/muestras-dni/`); no se usaron documentos ni fotos de personas reales.

Pruebas en `backend/django/verificacion/test_muestras_dni.py`, sobre `main` con el OCR real (#45).

## Resultado

| Verificación | Resultado |
|---|---|
| `verificacion` con EasyOCR real | 117/117 OK (11 nuevas de T19.4) |
| Django completo | 361/361 OK |

| Muestra | Lo que se leyó | Resultado |
|---|---|---|
| `1-valido` | 12345678 · PEREZ GOMEZ · JUAN CARLOS, con foto del titular | ✅ Validado, coincide con la fuente |
| `2-otro-dni` | 87654321 (no es el DNI del prestador) | ✅ Revisión manual por `dni_distinto`; no consulta la fuente |
| `3-borroso` | Nada | ✅ Revisión manual por `mrz_ilegible` |

## Coincidencia de nombres (MRZ contra la fuente)

| Caso | Resultado |
|---|---|
| La fuente trae tildes, minúsculas o espacios de más | Coincide |
| Ñ (en la MRZ viaja como `NXX`) y diéresis (Argüello) | Coincide |
| El DNI tiene un solo nombre y la fuente dos | Revisión manual |
| Apellidos en otro orden | Revisión manual |
| El OCR confunde una letra del nombre (G0MEZ) | Revisión manual (en los nombres no se adivina) |
| Nombre largo que el DNI corta a 30 caracteres | Revisión manual (ver hallazgo 2) |

## Fotos imperfectas (la muestra válida como saldría de un celular)

| Variante | Tiempo de lectura | Resultado |
|---|:---:|---|
| Original (1800 × 900) | 6,3 s | ✅ Lee bien |
| JPEG muy comprimida (calidad 25) | 6,1 s | ✅ Lee bien |
| Desenfoque leve | 6,2 s | ✅ Lee bien |
| Inclinada 3° | 6,6 s | Revisión manual (`mrz_invalida`) |
| Inclinada 8° | 8,3 s | Revisión manual (`mrz_ilegible`) |
| A la mitad de tamaño (900 × 450) | 2,3 s | Revisión manual (`mrz_invalida`) |
| A un tercio | 1,3 s | Revisión manual (`mrz_invalida`) |
| Oscura (40 % de brillo) | 6,1 s | Revisión manual (`mrz_invalida`) |
| Al revés (180°) | 6,6 s | Revisión manual (`mrz_ilegible`) |

En **ningún** caso se validó con datos mal leídos: o lee bien o manda a revisión. La prueba `test_fotos_imperfectas_...` deja eso fijo con 4 de estas variantes. Tiempos en CPU, sin GPU, con el modelo ya cargado.

## Hallazgos (para Renzo, dueño de HU-19)

1. **El OCR es frágil con fotos de celular.** Basta una inclinación de 3°, una foto pequeña u oscura para que no lea la MRZ. Es seguro (va a revisión manual), pero en la práctica muchos prestadores terminarán en revisión manual. Mejoras posibles: enderezar la imagen, subir el contraste y ampliar fotos pequeñas antes del OCR, o pedir en la pantalla que la foto salga recta y con luz.
2. **Nombres largos siempre van a revisión.** La línea de nombres del DNI mide 30 caracteres y corta los nombres largos («RODRIGUEZ DE LA CRUZ MARIA FE…»), así que nunca coinciden exactamente con la fuente. Se podría aceptar que la fuente empiece igual que lo leído cuando la línea está llena.
3. **No se comparan los nombres que registró el prestador.** Solo se compara el número de DNI con el registrado y los nombres del DNI con la fuente. Un prestador registrado como «Juan Carlos Pérez Gómez» que sube su propio DNI con otro nombre queda «validado». Como la verificación nunca aprueba sola, el administrador debe mirar el nombre al aprobar; conviene mostrar los dos nombres lado a lado en su panel o agregar el motivo `nombre_registrado_distinto`.
4. Cada lectura tarda unos **6 s** en CPU (más la primera carga del modelo). La pantalla ya muestra «Validando…».

## Cómo correrlas

```powershell
cd backend/django
python manage.py test verificacion.test_muestras_dni
```

Las 4 pruebas con el OCR real necesitan EasyOCR (`requirements.txt`) y sus modelos en `C:\Users\<usuario>\.EasyOCR\model` (`craft_mlt_25k.pth` y `latin_g2.pth`, ~95 MB). Si no están, **se saltan** con un aviso, así nunca se descargan durante las pruebas. Para descargarlos una vez:

```powershell
python -c "import easyocr; easyocr.Reader(['es'], gpu=False)"
```
