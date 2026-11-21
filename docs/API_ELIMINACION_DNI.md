# Eliminación de datos de verificación del DNI · T21.2

Implementación **local para revisión**, 05/11/2026, sobre #28 `5f510c7` y T21.1 reconciliada. Sin commit, push, PR ni integración. No constituye aprobación legal del texto provisional ni cierre de HU-21.

## Alcance conservador

Cada solicitud guarda la copia del alcance `verificacion_dni-v1`. Comprende TODOS los intentos del propietario: fotografías anverso/reverso, recorte del titular, DNI/nombres/apellidos leídos, fechas leídas, indicador MRZ y metadatos de fuente. Se usa el método actualizado de Renzo `IntentoDni.eliminar_datos_biometricos()`, sin modificarlo ni duplicar su limpieza de datos.

Se conservan cuenta, DNI/nombres/contacto registrados del prestador, certificados, servicios y consentimiento histórico. IntentoDni conserva propietario, resultado, motivos codificados y fecha. No se borran datos de otro prestador. No se promete eliminación en copias de seguridad, fuentes externas o archivos huérfanos históricos sin propiedad identificable. No se recorre indiscriminadamente la raíz privada.

Los archivos nuevos se guardan bajo `dni/hu21/<propietario>/<operación-UUID>/`. Ese directorio privado permite recuperar archivos de una escritura que falló antes de guardar IntentoDni. El procesador inventaría únicamente ese espacio propio y las referencias de los intentos del propietario. Los huérfanos identificados se limpian mediante el almacenamiento; el borrado normal de los intentos sigue delegado al método de Renzo. Las referencias compartidas entre prestadores o fuera de la raíz se rechazan sin borrarlas.

Eliminar y revocar son operaciones distintas. No se revoca automáticamente ni se habilita reconocimiento facial. Durante una eliminación sin resolver se bloquea la captura aunque el consentimiento siga vigente o se acepte de nuevo. Después de atender, una captura nueva requiere la versión vigente del consentimiento. El texto provisional pasa a `demo-dni-v2` para describir la eliminación disponible: las aceptaciones v1 conservan su texto y quedan desactualizadas, sin aceptación masiva.

## Persistencia y privacidad

- SolicitudEliminacionDni: UUID, propietario de sesión, idempotencia, alcance, estado, fechas, ejecuciones, token de procesamiento, código sanitizado y pendientes.
- EventoEliminacionDni: transiciones acumulativas, fecha, número de ejecución, código y conteo. No imágenes, MRZ, datos leídos, DNI, nombres ni excepciones crudas.
- ArchivoEliminacionDni: inventario técnico PRIVADO, con referencias para comprobar y reintentar. No contiene imágenes; no se incluye en las respuestas ni el historial público. Es distinto de la auditoría.
- ControlDatosDni: generación por propietario para invalidar escrituras tardías.

El inventario se confirma en BD ANTES de borrar archivos. Si falla el save después del borrado, se conserva el manifiesto: una nueva ejecución comprueba lo que realmente queda. Un almacenamiento que retorna sin borrar no produce atención exitosa. El método de Renzo puede devolver `[]`, pero el procesador recarga los intentos y comprueba las referencias físicas y los derivados antes de atender.

## Estados

| Estado | Significado | Capturas |
|---|---|---|
| pendiente | Registro confirmado, todavía pendiente del procesador | Bloqueadas |
| procesando | Ejecución adquirida y registrada | Bloqueadas |
| parcial | Hay progreso, pero archivos, datos o comprobaciones pendientes | Bloqueadas |
| fallida | No pudo completarse el inventario/procesamiento/verificación | Bloqueadas |
| atendida | Todos los elementos y datos del alcance verificados | Requieren consentimiento vigente |

Parcial/fallida → pendiente mediante reintento explícito. El UUID y el historial no cambian. Repetir el reintento en pendiente/procesando no agrega otra ejecución; repetir una atendida no vuelve a borrarla. El contador aumenta cuando un procesador adquiere trabajo. `pendientes` cuenta comprobaciones, no necesariamente archivos distintos.

## Autenticación y endpoints

SessionAuthentication explícita, EsPrestador y CSRF en todas las escrituras. El prestador se deriva de request.user; nunca del cuerpo. Anónimo/cliente/administrador: 403. Recurso ajeno: 404. No se exige consentimiento para pedir borrar datos propios. No se aceptan estados, rutas, propietarios, alcance o fechas enviados por el usuario.

### GET /api/verificacion/eliminaciones/

200 con `bloqueada` y `solicitudes`: últimas 50 solicitudes propias. Cada solicitud devuelve UUID, estado, alcance/versionado, fechas, número de ejecuciones, código sanitizado, pendientes y todos sus eventos. La consulta no borra ni procesa. Los registros anteriores permanecen en BD; el detalle se consulta por UUID.

### POST /api/verificacion/eliminaciones/

```json
{"clave_idempotencia": "00000000-0000-4000-8000-000000000021", "confirmo": true}
```

La confirmación debe ser el booleano JSON true y la clave un UUID. Solo se admiten esos campos.

- 202: solicitud registrada de forma duradera; NO significa eliminación terminada.
- 200: misma clave del mismo propietario; devuelve la misma solicitud incluso tras atenderla.
- 409: otra clave mientras existe una sin resolver; devuelve `solicitud` con el recurso propio existente, sin duplicarlo.
- 400: esquema, UUID o confirmación inválidos.
- 503: no se pudo adquirir/guardar la reserva; repetir con la misma clave es seguro. Nunca continuar sin reserva.

Una nueva clave después de atender crea una nueva solicitud explícita; no modifica la anterior.

### GET /api/verificacion/eliminaciones/<UUID>/

200: detalle propio con historial. 404: inexistente o ajeno. Sin token CSRF en consultas de solo lectura.

### POST /api/verificacion/eliminaciones/<UUID>/reintentar/

Cuerpo vacío. Parcial/fallida se reencola como pendiente, conservando el mismo UUID e historial. 202 para trabajo sin resolver; 200 si ya estaba atendida. Sin cambios repetidos por doble clic. 400 por campos, 403 por sesión/rol/CSRF, 404 por propietario y 503 recuperable por BD ocupada.

## Atención y recuperación

La pantalla `/eliminar-datos` registra, consulta y reintenta; nunca presenta un 202 como atención completa. La consulta es explícita mediante «Consultar estado». No se instala un scheduler, cola externa, servicio de fondo ni notificación Firebase.

Después de revisar/aplicar las migraciones en un entorno autorizado, un operador atiende solicitudes mediante:

```text
python manage.py procesar_eliminaciones_dni
python manage.py procesar_eliminaciones_dni --solicitud <UUID>
```

Solo se adquieren pendientes. Para parcial/fallida, primero se solicita el reintento desde la API/pantalla. Para una ejecución interrumpida que quedó procesando:

```text
python manage.py procesar_eliminaciones_dni --solicitud <UUID> --recuperar
```

La recuperación registra otra ejecución y otro token. Un procesador antiguo no puede confirmar o borrar con el token sustituido. Si no puede guardarse el resultado de una ejecución, la solicitud permanece recuperable y bloqueada; no se anuncia éxito. El comando imprime únicamente UUID, estado, conteo y código, sin rutas ni datos personales.

**Limitación operativa:** aún debe acordarse quién ejecutará/programará la atención y con qué frecuencia. Sin ejecutar el procesador, las solicitudes quedan pendientes y las capturas bloqueadas. Esta etapa no programó tareas ni migró una base real.

## Exclusión y SQLite

La primera operación decisoria de cada transacción escribe sin cambiar el valor de last_login del propietario, antes de leer/crear el control. Es una reserva real de escritura en SQLite y bloqueo de esa fila en motores compatibles; no depende de select_for_update ni de un mutex en memoria. Las constraints garantizan una clave propia única y como máximo una solicitud sin resolver por propietario. SQLITE_BUSY no se interpreta como permiso para continuar: la API responde 503 y permite repetir.

La relación con el prestador se resuelve fuera de las transacciones del procesador. Acceder a una FK perezosa después de refresh_from_db y antes de reservar introducía una lectura previa incompatible con esa garantía; se corrigió y existe una regresión que comprueba que el primer SQL de ambas fases sea UPDATE, además de las pruebas entre procesos.

Registrar una eliminación incrementa la generación. OCR se ejecuta fuera de una transacción larga; antes de guardar archivos/IntentoDni se adquiere de nuevo la reserva y se comprueban barrera, generación, consentimiento y cuota diaria. Si la captura persistió primero, entra al inventario posterior. Si la eliminación ganó primero, no se guarda una captura antigua, incluso si la solicitud ya pasó a atendida. Revocar el consentimiento también invalida generaciones anteriores; reaceptar no revive una captura vieja.

La atención utiliza la misma reserva y tokens para evitar dos procesadores/reintentos concurrentes. La BD y el filesystem no son una sola transacción: el inventario duradero y el directorio privado propio permiten comprobar y recuperar esos fallos. Un dato o archivo cuya eliminación no se puede acreditar impide atender.

## Decisiones provisionales y límites

**Corrección local del 07/11/2026:** la pantalla invalida y cancela lecturas anteriores
al comenzar un POST. Cada consulta comprueba su generación antes de actualizar datos,
errores o estado de carga; un cuerpo ya recibido tampoco puede reemplazar el resultado
de una escritura posterior. Las escrituras simultáneas se rechazan localmente y la
consulta se deshabilita durante una escritura. Se conserva el resultado registrado
en cualquiera de los cinco estados. El backend mantiene sus constraints e idempotencia.

**Decisión operativa previa a declarar lista la integración:** PO/equipo deben acordar
atención manual o programada, responsable, frecuencia de revisión, seguimiento de
pendientes/parciales/fallidas y procedimiento de recuperación de ejecuciones
interrumpidas con `--recuperar`. Puede mantenerse el comando manual si se asigna
esa operación explícitamente; no se requiere instalar un scheduler por defecto.
La corrección no configura ni ejecuta el procesador sobre datos reales.

- Texto demo v2, política de retención del historial y operación del procesador pendientes de revisión del PO; no se presentan como aprobación legal.
- No se borra la cuenta, el DNI registrado, consentimiento histórico ni datos de otras finalidades; eliminación facial futura requiere alcance y consentimiento separados.
- Lista inicial limitada a 50 solicitudes; detalle e historial de cada UUID persisten. No hay paginación UI de registros más antiguos en esta etapa.
- No cubre erasure forense, backups externos ni huérfanos históricos sin propietario; no marca como completa una comprobación de su inventario que haya fallado.
- Pruebas con usuarios, imágenes y documentos sintéticos, sin OCR/identidad real. Ver [resultados y evidencias](pruebas-T21.2.md).
