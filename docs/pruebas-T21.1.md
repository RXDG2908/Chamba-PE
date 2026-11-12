# Validación local T21.1 · 03/11/2026

Rama feature/hu21-consentimiento-eliminacion, worktree independiente, base a6147de. Sin commit, push, merge ni PR. T21.2 no implementada.

| Comprobación | Resultado |
|---|---|
| T21.1 y regresiones HU-19 | 63/63 aprobadas (23 nuevas + 40 HU-19), 1,288 s |
| Suite Django completa | 229 ejecutadas: 226 aprobadas y 3 expectedFailure conocidas de certificados, 27,094 s |
| Migración 0002 | Aplicada, revertida y reaplicada en SQLite en memoria; IntentoDni conservado |
| Migraciones pendientes / system check | Sin cambios pendientes; sin problemas |
| ESLint | Correcto |
| Build de producción | Correcto, ejecutado por las suites Playwright |
| Playwright | 15/15 aprobadas en 18,7 s; API y cámara simuladas, sin reintentos |
| Capturas responsive adicionales | 3/3 aprobadas en 11,2 s; 320, 390 y 1280 px; artefactos ignorados |
| Git | diff --check correcto; sin cambios preparados en índice |

Las duraciones anteriores corresponden a las primeras ejecuciones completas. Cualquier repetición posterior motivada por una corrección se informa por separado en la entrega.

## Cobertura

Consentimiento válido, ausente, revocado, de versión anterior y de otra finalidad; aceptación repetida y nueva aceptación tras revocar; historial y restricciones de BD; propietario de la sesión y campos manipulados; roles y CSRF; rechazo del DNI antes de imágenes/OCR/almacenamiento/fuente; aceptación real habilita el flujo existente; revocar no borra fotos.

Navegación antes de cámara, confirmación/cancelación de revocación, casilla desmarcada, cambio de versión, errores de consulta, revocación entre captura y envío, 403 definitivo del backend y responsive sin desbordamiento.

## Aislamiento y límites

SQLite en memoria, media temporal, usuarios ficticios e imágenes generadas. Sin base principal, Firebase, credenciales ni documentos reales. No se instaló EasyOCR: el OCR se sustituye por el lector falso existente en las regresiones.

Las pruebas de navegador interceptan la API; la autenticación, CSRF y persistencia reales se verifican en Django. No equivalen a una prueba manual de teléfono, lector de pantalla u OCR real. El texto es provisional para demostración, no legal definitivo.

No se retiraron expectedFailure de certificados ni se modificaron aserciones para ocultar fallos. Solo se añadieron aceptaciones reales a los fixtures normales de HU-19 para respetar el requisito nuevo de consentimiento.

## Comprobación final tras ajustar el texto provisional

- T21.1 + HU-19: 63/63 aprobadas, 1,939 s; sin migraciones pendientes.
- ESLint correcto; build completado; Playwright 15/15 aprobadas, 25,9 s, cero reintentos.
- Capturas de 320 y 1280 px inspeccionadas visualmente; la prueba de 390 px también aprobada.
- Índice vacío; HEAD a6147de sin nuevos commits. Otros worktrees preservados por comprobación de hashes, cabeceras y estado. Sin base de trabajo ni .env en el worktree HU-21.

## Reconciliación controlada con #28 · 05/11/2026

Los cortes anteriores se conservan como historia. HEAD actual: `5f510c74a7b5772ab285a461dfab2f1295b9eb8d`, incorporado por fast-forward desde a6147de, sin crear commits. Rama `feature/hu21-consentimiento-eliminacion`; T21.1 permanece sin commit y T21.2 solo está diseñada.

Resguardo previo: 164 archivos completos, incluidos los 11 untracked, patch binario, índice original, cabecera y manifiesto SHA-256. Está bajo `.hu21-local/reconciliacion-20261104/`, ignorado. Las evidencias anteriores no se sustituyeron; las nuevas usan una subcarpeta propia. El identificador 20261104 del resguardo se conserva; este registro corresponde al 05/11.

Conflictos resueltos: README (ambos enlaces), contrato de verificación (consentimiento real y nuevo retorno del borrado), models (método de Renzo y modelo Consentimiento) y VerificarDni (guardas locales y manejo de errores de Renzo). En la pantalla se conserva la decisión de T21.1 de bloquear cámara ante fallo de consulta, con mensaje recuperable, en lugar de adoptar el fallback de captura del frontend remoto sin esa guarda.

| Comprobación | Resultado nuevo |
|---|---|
| T21.1 + HU-19 | 65/65 aprobadas en 1,992 s: 63 anteriores + 2 nuevas de Renzo |
| Conservación de aserciones | AST de los 63 métodos originales idéntico al resguardo; método de eliminación idéntico al de #28 |
| Django completo | 231 en 26,045 s: 228 aprobadas y 3 expectedFailure heredadas de certificados; system check correcto |
| Migraciones | Sin cambios de esquema pendientes; 0002 aplicada, revertida y reaplicada en memoria; sin aceptaciones automáticas |
| Playwright | 16/16 en 18,322 s: 15 escenarios conservados + error de red; dos workers, canal Chromium original, cero retries/omisiones |
| Lint / build | Correctos; build 569 ms |
| git diff --check | Correcto, sin marcadores ni archivos sin resolver |

El caso nuevo comprueba que el error de red al consultar un intento se informa en español y no abre cámara ni selector. No se cambió ninguna expectativa previa para forzar resultados. No hubo fallos nuevos de pruebas en esta reconciliación; los cuatro conflictos textuales quedan documentados y resueltos. Los logs y JSON se conservan en el resguardo ignorado.

Se mantienen los límites originales: API/cámara simuladas en Playwright, almacenamiento temporal y OCR falso en Django, sin documentos reales, sin promesa sobre cámara/OCR reales o concurrencia SQLite multiproceso. No se aplicaron cambios de configuración de HU-7. El [diseño T21.2](DISENO_T21_2.md) especifica las pruebas de concurrencia futuras, todavía no ejecutadas ni implementadas.

El listado anterior de archivos T21.1 se conserva. Se añaden a la entrega documental `docs/DISENO_T21_2.md` y la entrada de `AVANCES.md`; los demás cambios de esta etapa reconcilian fuentes existentes y agregan el caso de red a la suite HU-21 ya incluida. El estado final permanece sin stage, sin conflictos y sin commits propios.

## Compatibilidad durante T21.2 · 05/11/2026

Los resultados anteriores corresponden a sus cortes históricos. La implementación local posterior mantiene los 65 métodos específicos y sus aserciones intactos, y los 16 escenarios Playwright de T21.1. El texto provisional vigente pasa a demo-dni-v2, sin reescribir ni aceptar automáticamente registros v1; una prueba nueva comprueba ese historial y la aceptación explícita.

La barrera de eliminación se suma al consentimiento, sin convertirlo en una autorización facial ni borrar datos al revocar. La comprobación de permiso/generación antes de persistir también protege una revocación/reaceptación concurrente. Los nuevos archivos se guardan en un espacio privado por propietario/operación para recuperar escrituras fallidas; se mantienen el almacenamiento privado y los endpoints de HU-19.

La ejecución paralela mostró un timeout de navegación anterior a las aserciones: se conserva el fallo y se valida la configuración serial sin ampliar tiempos/retries. Ver los [resultados T21.2](pruebas-T21.2.md): 264 Django, con 261 aprobadas y tres fallos esperados heredados; 33/33 Playwright finales y contraste serial 66/66. No se modifican las pruebas anteriores para forzar resultados.

## Archivos de la entrega

Creados:
- verificacion/textos_consentimiento.py
- verificacion/serializers.py
- verificacion/vistas_consentimiento.py
- verificacion/test_consentimiento.py
- verificacion/migrations/0002_consentimiento.py
- frontend/src/Consentimiento.jsx
- frontend/src/Consentimiento.css
- frontend/playwright.hu21.config.mjs
- frontend/tests/hu21/consentimiento.spec.mjs
- docs/API_CONSENTIMIENTO.md
- docs/pruebas-T21.1.md

Modificados:
- verificacion/models.py
- verificacion/consentimiento.py
- verificacion/urls.py
- verificacion/tests.py
- frontend/src/App.jsx
- frontend/src/VerificarDni.jsx
- frontend/package.json
- frontend/package-lock.json
- docs/API_VERIFICACION_DNI.md
- README.md
- .gitignore
