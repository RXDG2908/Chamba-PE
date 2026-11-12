# Consentimiento para verificación del DNI · T21.1

Implementación local de HU-21 sobre HU-19, base a6147de (#28, que contiene #27 actualizado).
Texto de demostración provisional, pendiente de aprobación del PO: no acredita un consentimiento legal definitivo.

## Alcance

La finalidad verificacion_dni autoriza el tratamiento descrito de imágenes y datos del DNI para su verificación y revisión administrativa. No autoriza reconocimiento facial ni comparación biométrica futura. El modelo Consentimiento no guarda DNI, imágenes ni datos biométricos.

Sin una aceptación propia, de la finalidad y versión vigentes, y no revocada, POST /api/verificacion/dni/ devuelve 403 antes de validar imágenes, obtener lector, almacenar archivos o consultar fuentes. El enganche existente de HU-19 llama a tiene_consentimiento(prestador); no se duplica el flujo de OCR.

La revocación bloquea nuevos procesamientos. No elimina archivos ni datos leídos: la eliminación se solicita por separado mediante [T21.2](API_ELIMINACION_DNI.md). El método de borrado de IntentoDni y sus límites no se modifican.

## Autenticación y propietario

Sesión Django y EsPrestador. Anónimo, cliente y administrador reciben 403. Las escrituras usan cookie csrftoken y cabecera X-CSRFToken. Los GET no requieren token CSRF.

El usuario se obtiene exclusivamente de request.user. Propietario, finalidad, texto aceptado y fechas son controlados por el servidor. Los campos ajenos al esquema de aceptación se rechazan con 400.

## Modelo y versión

Consentimiento: usuario, finalidad, version_texto, texto_aceptado, aceptado_en y revocado_en.
La copia del texto permite conservar lo aceptado aunque se publique una versión posterior.
El texto y la versión vigentes están en verificacion/textos_consentimiento.py. Toda modificación de un texto ya publicado exige una nueva versión; no se reescriben aceptaciones anteriores.

Existe una restricción de unicidad para aceptaciones no revocadas por usuario/finalidad/versión, y otra que impide revocaciones anteriores a la aceptación. Los servicios usan transacciones y bloqueo del propietario cuando el motor lo soporta. No se promete una prueba de concurrencia multiproceso en SQLite.

Migración 0002_consentimiento: depende de 0001_initial y del usuario configurable. No acepta automáticamente a usuarios existentes: no hay migración de aceptación masiva.

## Endpoints

### GET /api/verificacion/consentimiento/

200: finalidad, version_texto, texto, provisional, vigente, estado, aceptado_en, revocado_en y eliminacion_pendiente.
Estados: ausente, vigente, revocado y desactualizado. La consulta no crea registros y nunca devuelve aceptaciones ajenas.

### POST /api/verificacion/consentimiento/

JSON:

```json
{"acepto": true, "version_texto": "demo-dni-v2"}
```

201 al crear aceptación; 200 al repetir una aceptación vigente, sin cambiar su fecha ni duplicarla.
Solo se admite el booleano JSON true. Una versión antigua, falta de campos o campos extra producen 400.
La respuesta tiene el mismo esquema del GET.

Después de una revocación, aceptar de nuevo crea otro registro y mantiene el anterior revocado.

### POST /api/verificacion/consentimiento/revocar/

Sin campos en el cuerpo. 200 con el estado actualizado. Repetir la operación es idempotente y no cambia fechas históricas. Se revocan las aceptaciones activas del propietario para esta finalidad, sin tocar otros usuarios ni otras finalidades.

No es un endpoint de eliminación de datos.

En ambos POST, un `OperationalError` al reservar o escribir devuelve **503 JSON** con
`{"detail": "No pudimos guardar el consentimiento. Inténtalo de nuevo."}`. La excepción
se captura después del rollback del servicio transaccional: no se devuelve HTML ni
información interna. Se puede repetir la misma operación sin duplicar aceptaciones
ni cambiar fechas de una revocación ya registrada. Permisos, sesión y CSRF se
comprueban antes de reservar. Corrección local del 07/11/2026, pendiente de revisión.

## Interfaz

/consentimiento usa RutaPrivada para prestadores. Muestra finalidad, texto, versión y advertencia provisional. La casilla comienza desmarcada y el botón se habilita al aceptarla. El cambio se confirma visualmente. La revocación exige confirmación y puede cancelarse.

/verificar-dni consulta la aceptación antes de montar cámara o selector de archivos, y vuelve a comprobarla antes del envío. Solo permite continuar con `vigente === true` y `eliminacion_pendiente === false`, ambos booleanos explícitos. Sin aceptación dirige a /consentimiento; con eliminación pendiente mantiene el bloqueo. Campos ausentes, nulos o de tipos incorrectos y errores de consulta bloquean captura/envío. Si la comprobación falla después de capturar, libera las previsualizaciones y exige reconsultar antes de capturar otra vez. El backend conserva la decisión definitiva. Las previsualizaciones también se liberan al desmontar la pantalla.

## Validación y reproducción

Pruebas backend: verificacion.test_consentimiento y verificacion.tests, con SQLite en memoria y media privada temporal. Los casos normales de HU-19 incorporan una aceptación real de fixture; sus aserciones originales se conservan. Los casos negativos de HU-21 no crean esa aceptación.

Frontend: npm ci; npm run lint; npm run build; npm run test:hu21.
Playwright usa el puerto 5191, backend simulado por contexto aislado, imágenes sintéticas y cero reintentos. Artefactos bajo .hu21-local/, ignorado.

No ejecutar migrate contra la base de trabajo como parte de la revisión aislada. La migración ya se verificó mediante aplicación, reversión y reaplicación en memoria.

Ver [resultados de validación](pruebas-T21.1.md).

## Pendientes

Aprobación del PO del texto/finalidad antes de un uso real; revisión de T21.1/T21.2; operación del procesador, validación física de cámara y accesibilidad humana. Los problemas heredados de certificados y las observaciones pendientes del adaptador de HU-19 no se corrigen en esta tarea.

## Reconciliación con #28 · 05/11/2026

La referencia a a6147de al inicio describe la preparación original, conservada. El worktree HU-21 se actualizó mediante fast-forward a `5f510c7`, última cabecera verificada de #28, y se reaplicaron los cambios locales desde un resguardo verificado. No se creó un commit ni se publicó.

Se conserva el método actualizado de Renzo: `eliminar_datos_biometricos()` devuelve campos de archivo fallidos, mantiene sus referencias y permite reintentos. T21.1 no lo duplica ni implementa su orquestación. El frontend combina la corrección de mensajes/limpieza de errores con los controles locales de consentimiento, cancelación y liberación de previsualizaciones. Un error al consultar el intento anterior se informa y mantiene bloqueada la cámara; el 404 permite continuar tras verificar consentimiento. No se reincorpora una consulta de intentos sin pasar por ese control.

Las 63 pruebas específicas anteriores conservan sus aserciones y pasan junto con las dos nuevas de Renzo: 65/65. Playwright conserva sus 15 escenarios y añade un caso de error de red: 16/16. [Resultados completos](pruebas-T21.1.md).

El alcance y la coordinación de eliminación quedan especificados en el [diseño T21.2](DISENO_T21_2.md). Solo es un diseño: no existen aún solicitudes persistentes, endpoints, procesador ni barrera de eliminación. La revocación de T21.1 sigue sin borrar fotos.

## Implementación local T21.2 · 05/11/2026

El párrafo anterior conserva el corte de diseño previo. Ahora existen solicitudes, atención recuperable e interfaz de eliminación en la rama local, sin publicación: [contrato actual](API_ELIMINACION_DNI.md).

La versión provisional vigente pasa a demo-dni-v2 para describir la eliminación disponible; el texto v1 se conserva como referencia y en cada aceptación histórica, sin reescribirla. Una aceptación v1 queda desactualizada y requiere aceptación explícita de v2 para procesar de nuevo. La eliminación no exige esa aceptación ni la cambia automáticamente. No se autoriza reconocimiento facial.

El estado del consentimiento agrega `eliminacion_pendiente`. Una solicitud pendiente/procesando/parcial/fallida bloquea captura y persistencia aunque el consentimiento sea vigente. La aceptación/revocación usan una primera escritura del propietario compatible con SQLite; revocar invalida la generación de una captura anterior. El backend vuelve a comprobar permiso y generación antes de guardar archivos/IntentoDni. No se considera select_for_update una garantía de exclusión en SQLite.
