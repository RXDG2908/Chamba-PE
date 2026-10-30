# Escaneo y validación del DNI — contrato de HU-19

Lo escribió Renzo León el 29/10/2026, al inicio del Sprint 3, antes de escribir el código. Cubre:

- **T19.1** pantalla de escaneo con la cámara;
- **T19.2** lectura del DNI con OCR;
- **T19.3** validador intercambiable y paso a revisión manual.

Luis prepara T19.4 (pruebas cruzadas) contra este documento, sin esperar el código. Si algo cambia al implementar, se actualiza aquí en el mismo PR.

## Resumen

| Tema | Acuerdo |
|---|---|
| App de Django | `verificacion` (HU-19 y HU-21; HU-20 cuando se replanifique). En [PLAN_EQUIPO.md](../PLAN_EQUIPO.md#5-organización-del-backend) figura "por asignar": Renzo la crea con HU-19 y Luis agrega HU-21 ahí mismo |
| Rutas | Bajo `/api/verificacion/dni/` |
| Quién la usa | Solo el prestador con sesión (`EsPrestador`). El administrador consulta los resultados (`EsAdministrador`) |
| Qué se envía | Dos fotos del DNI: **anverso** (el frente) y **reverso** |
| De dónde salen los datos | Principalmente de la **MRZ del reverso** (las 3 líneas con `<<<`), que traen dígitos de control estándar. El anverso se usa para recortar la foto del titular |
| Qué valida | Que la MRZ sea correcta, que el DNI leído sea el del prestador registrado y que los nombres coincidan con la fuente externa |
| Resultado | `validado` o `revision_manual`. **Nunca aprueba al prestador por sí solo**: `Prestador.estado` no cambia en el Sprint 3 |
| Fuente externa | Validador **intercambiable**: simulado en desarrollo y pruebas, y eldni.com (u otra fuente) solo si se activa en la configuración |
| Datos personales | Fotos privadas, sin URL pública, y se pueden eliminar con HU-21 (Ley 29733) |

## 1. Flujo

1. El prestador inicia sesión y entra a **Verificar mi DNI** (`/verificar-dni`).
2. Toma con la cámara la foto del **anverso** y luego la del **reverso**. Puede repetir cada foto antes de enviar.
3. React envía las dos imágenes a `POST /api/verificacion/dni/`.
4. Django ejecuta los pasos de la sección 3 y guarda un **intento** con su resultado.
5. La pantalla muestra lo que se leyó y el resultado: **DNI validado** o **En revisión manual**, con el motivo.
6. El administrador ve el intento junto a los certificados (panel de HU-9) y decide como hasta ahora.

## 2. Por qué la MRZ y no el frente

El criterio de HU-19 pide que el número pase la validación del dígito verificador.

- **La MRZ del reverso** sigue el estándar público **ICAO 9303 (formato TD1)**. Cada campo trae su propio dígito de control (pesos 7, 3, 1, módulo 10), así que la lectura se puede comprobar sin depender de fórmulas no publicadas.
- **El código de verificación del anverso** (el carácter después del guion, junto al número) no tiene una especificación oficial que hayamos podido confirmar. Por eso **no se usa para aprobar**: si se lee, solo se muestra como dato.

En el DNI peruano la Ñ aparece en la MRZ como `NXX` (por ejemplo, `NUNXXEZ` = NUÑEZ). Al leer, se convierte de vuelta. T19.4 confirma este punto con las muestras.

## 3. Pasos del backend

| # | Paso | Si falla |
|:---:|---|---|
| 1 | Comprobar las imágenes: JPG o PNG, 5 MB como máximo cada una, las dos presentes | `400` por campo; no se guarda nada |
| 2 | **OCR del reverso**: ubicar las 3 líneas de la MRZ y leerlas | Resultado `revision_manual`, motivo `mrz_ilegible` |
| 3 | **Dígitos de control** de la MRZ: número de documento, fecha de nacimiento, vencimiento y control compuesto | `revision_manual`, motivo `mrz_invalida` |
| 4 | **Número**: el DNI de la MRZ (8 dígitos) debe ser igual a `Prestador.dni` | `revision_manual`, motivo `dni_distinto` |
| 5 | **Vigencia**: la fecha de vencimiento no debe haber pasado | `revision_manual`, motivo `dni_vencido` |
| 6 | **OCR del anverso**: recortar la foto del titular (se guarda para HU-20) | Se sigue sin foto; motivo informativo `sin_foto` |
| 7 | **Fuente externa**: consultar los nombres del DNI con el validador configurado | `revision_manual`, motivo `fuente_no_disponible` |
| 8 | **Comparar nombres**: los de la MRZ contra los de la fuente, normalizados | `revision_manual`, motivo `nombres_distintos` |
| 9 | Si todo pasó | Resultado `validado` |

**Comparación de nombres (paso 8):** se pasa a mayúsculas, se quitan las tildes y la diéresis, se convierten `NXX` y `Ñ` a `N` y se dejan los espacios simples. Coinciden si los apellidos y los nombres son iguales después de normalizar. No se aceptan coincidencias "parecidas": ante la duda, revisión manual.

**OCR (T19.2):** se usa **EasyOCR**, como dice [PLAN_EQUIPO.md](../PLAN_EQUIPO.md#4-stack-tecnológico), detrás de una interfaz `LectorDni` para poder cambiarlo. Si la instalación de EasyOCR resulta muy pesada, se evalúa Tesseract **sin cambiar este contrato**.

## 4. Rutas

Todas responden JSON, con errores en español por campo, como el resto de la API. Exigen sesión y `X-CSRFToken` ([API_AUTENTICACION.md](API_AUTENTICACION.md)).

### `POST /api/verificacion/dni/` · prestador

Envío `multipart/form-data`:

| Campo | Regla |
|---|---|
| `anverso` | Imagen JPG o PNG, 5 MB como máximo |
| `reverso` | Imagen JPG o PNG, 5 MB como máximo |

Respuesta `201 Created`:

```json
{
  "id": 7,
  "resultado": "validado",
  "motivos": [],
  "lectura": {
    "dni": "12345678",
    "apellidos": "PEREZ GOMEZ",
    "nombres": "JUAN CARLOS",
    "fecha_nacimiento": "1995-04-12",
    "fecha_vencimiento": "2031-04-12",
    "mrz_valida": true
  },
  "fuente": {"nombre": "simulada", "estado": "coincide"},
  "tiene_foto": true,
  "creado": "2026-11-03T15:20:00Z"
}
```

Con `"resultado": "revision_manual"`, `motivos` lista los códigos de la sección 3; por ejemplo, `["fuente_no_disponible"]`. Los valores de `fuente.estado` son `coincide`, `no_coincide` y `no_disponible`.

| Respuesta | Cuándo |
|---|---|
| `400` | Faltan imágenes, el formato no es válido o pesan más de 5 MB |
| `403` | Sin sesión, con otro rol o sin consentimiento (HU-21) |
| `429` | Más de **5 intentos en el día** para el mismo prestador |

### `GET /api/verificacion/dni/` · prestador

Devuelve su **último intento** con el mismo cuerpo, o `404` si nunca intentó. React lo usa para mostrar el estado al volver a la pantalla.

### `GET /api/verificacion/dni/?prestador=<id>` · administrador

Lista los intentos de un prestador, del más reciente al más antiguo, para el panel de HU-9. Las fotos **no** van como URL pública. El administrador las ve en `GET /api/verificacion/dni/<id>/foto/<anverso|reverso|titular>/`, que solo responde con `EsAdministrador`.

## 5. Validador intercambiable (T19.3)

La lógica de los pasos 7 y 8 no sabe de dónde vienen los datos. Llama a una sola función:

```text
consultar(dni) → {"estado": "ok", "apellidos": "...", "nombres": "..."}
               → {"estado": "no_disponible"}
```

| Validador | Cuándo se usa |
|---|---|
| `simulado` | **Por defecto**, en desarrollo y en todas las pruebas. Responde con una tabla fija de DNIs de prueba, sin internet |
| `eldni` | Solo si `VALIDADOR_DNI=eldni` está en las variables de entorno. Consulta eldni.com |
| Futuro oficial | Un servicio con convenio, si el proyecto lo consigue; basta con agregar otro validador |

Sobre **eldni.com**: la propia página dice que sus datos vienen de fuentes públicas y no son oficiales de RENIEC (ver [Lab 07](Lab07_CPS_ChambaYa.md)). Por eso:

- tiene un tiempo de espera máximo de **5 s**;
- cualquier error, demora o cambio en la página da `no_disponible` y el caso pasa a **revisión manual**;
- el PO decide si se activa en la demo.

## 6. Datos que se guardan

Modelo `verificacion.IntentoDni`:

| Campo | Contenido |
|---|---|
| `prestador` | FK a `Prestador` |
| `resultado` | `validado` o `revision_manual` |
| `motivos` | Lista de códigos de la sección 3 |
| `dni_leido`, `apellidos_leidos`, `nombres_leidos`, `fecha_nacimiento`, `fecha_vencimiento` | Lo leído de la MRZ |
| `fuente`, `estado_fuente` | Validador usado y su respuesta |
| `anverso`, `reverso`, `foto_titular` | Archivos en una carpeta **privada** (`MEDIA_ROOT/privado/dni/`), fuera de `/media/` público |
| `creado` | Fecha y hora |

- **No se guarda** la respuesta completa de la fuente externa, solo el estado y si coincidió.
- La solicitud de eliminación de HU-21 (Luis) borra los archivos y los datos leídos, y deja solo `resultado`, `motivos` y `creado` como trazabilidad.

## 7. Pantalla (T19.1)

- Ruta `/verificar-dni`, protegida con `RutaPrivada` para el rol prestador, más un enlace en "Mi disponibilidad".
- **Cámara** con `getUserMedia`, con la cámara trasera en el celular y un marco guía con la forma del DNI. Se toma una foto para cada lado, con vista previa y botón **Repetir**.
- **Sin cámara o sin permiso:** aparece `<input type="file" accept="image/*" capture="environment">`, que en el celular abre la cámara igualmente.
- Consejos visibles: buena luz, sin reflejos y el DNI completo dentro del marco.
- Al enviar se muestra **Validando…**, y después el resultado con los datos leídos y, si hubo revisión manual, el motivo en palabras simples.
- Responsive: la misma pantalla sirve en el celular y en la PC.

## 8. Dependencias con el equipo

| Con | Qué | Cuándo |
|---|---|---|
| **Luis · HU-21** | Función `tiene_consentimiento(prestador)` en `verificacion`. `POST /dni/` responde `403` sin consentimiento. Si T21.1 no está en `main` cuando se una HU-19, la comprobación se activa en el PR de T21.1 | Antes de integrar |
| **Luis · T19.4** | Las muestras de DNI son **ficticias** (espécimen o imágenes generadas). **No se suben DNIs reales al repositorio**. Los DNIs de prueba se agregan a la tabla del validador `simulado` | Desde hoy |
| **David · HU-9** | Mostrar el último intento (resultado, motivos y fotos) junto a los certificados en el panel de revisión | Al tener T19.2 |
| **HU-20** (postergada) | Usará `foto_titular` para la comparación facial | Cuando se replanifique |

## 9. Pruebas que acompañan los PRs

- **T19.2:** dígitos de control de la MRZ (válida, cada campo alterado, `NXX` → Ñ) y lectura con imágenes de muestra.
- **T19.3:** cada fila de la sección 3 con el validador `simulado`, incluido `no_disponible` → revisión manual, y que `Prestador.estado` **no cambie**.
- **API:** permisos (`403` sin sesión, con otro rol o sin consentimiento), `400` por imágenes, `429` por intentos y CSRF.
- **T19.1:** lint y build, y la prueba en el navegador en un celular y en la PC.

Las pruebas cruzadas con muestras son de Luis (T19.4).
