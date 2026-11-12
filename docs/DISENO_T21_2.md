# Diseño de T21.2: eliminación de datos de verificación del DNI

Fecha: 05/11/2026. Este documento conserva el diseño previo sobre PR #28 `5f510c7`. **Actualización:** T21.2 está implementada localmente para revisión, sin publicación; el [contrato actual](API_ELIMINACION_DNI.md) detalla la realización, las decisiones provisionales y sus límites. El historial por ejecución se registra en eventos y contador/token de la solicitud, sin una tabla separada de ejecuciones. Este documento no autoriza commit, publicación ni integración.

## 1. Alcance acordado

Versión propuesta del alcance: `verificacion_dni-v1`, fijada por el servidor y guardada como copia inmutable en cada solicitud.

Eliminar todos los intentos de verificación del prestador dentro del alcance: archivos `anverso`, `reverso`, `foto_titular`; `dni_leido`, `apellidos_leidos`, `nombres_leidos`, `fecha_nacimiento`, `fecha_vencimiento`; reiniciar `mrz_valida`, `fuente` y `estado_fuente` conforme al método de Renzo. No limitarse al último intento.

Conservar `IntentoDni` con propietario, resultado, motivos codificados y fecha como trazabilidad mínima. No copiar los datos leídos ni las imágenes al historial de la solicitud. El alcance no elimina la cuenta, el DNI/nombre/contacto registrados en `Prestador`, certificados, solicitudes de servicios o datos de otras personas. No promete borrado de copias de seguridad, fuentes externas o archivos históricos huérfanos cuya propiedad no esté identificada; esos casos requieren inventario y alcance específico antes de afirmar una eliminación global.

El consentimiento `verificacion_dni` sigue separado de reconocimiento/comparación facial. No se habilita HU-20 ni se crea consentimiento facial. Solicitar eliminación no altera automáticamente la aceptación histórica: revocar sigue siendo una operación explícita de T21.1. Mientras exista una eliminación sin resolver, ni siquiera una nueva aceptación permite capturar. Una vez atendida, un nuevo procesamiento requiere consentimiento vigente de la finalidad/versión correspondientes. Si cambia el texto mostrado de consentimiento, se crea otra versión sin reescribir aceptaciones anteriores; aprobación del PO antes de uso real.

## 2. Persistencia propuesta

Los nombres son de diseño; no se crean modelos o migraciones en esta etapa.

- `SolicitudEliminacionDni`: UUID, propietario obtenido de sesión, alcance y versión, clave de idempotencia, estado, fechas de creación/actualización/atención, contador de ejecuciones y referencia a la ejecución vigente.
- `ElementoEliminacionDni`: inventario duradero de intento/campo y referencias privadas de almacenamiento necesarias para comprobar y reintentar. Se captura ANTES de llamar al borrado. Las referencias no aparecen en serializadores, auditoría pública ni mensajes de error. No guarda bytes de imágenes o valores OCR.
- `EjecucionEliminacionDni` y eventos de transición: número, token de ejecución, tiempos, estado anterior/nuevo, conteos y códigos de error permitidos. Historial acumulativo, sin excepciones crudas, DNI, nombres, MRZ, fotografías ni rutas del equipo. Un fallo no reemplaza el historial previo.
- `ControlDatosDni`: una fila única por propietario para coordinar captura, consentimiento y eliminación; generación de procesamiento, eliminación activa y reserva/token de persistencia. La escritura condicional de esta fila es la barrera, no una consulta meramente informativa.

Restricciones: UUID/idempotencia únicos por propietario; como máximo una solicitud sin resolver por propietario y alcance; estados admitidos mediante constraint; coherencia de fechas. Se debe conservar el propietario del registro de auditoría; una futura eliminación de cuenta/anonimización exige una política aparte y no se resuelve con CASCADE automático.

Una solicitud repetida con la misma clave devuelve la misma solicitud, incluso tras atenderla. Otra clave mientras haya una abierta devuelve la abierta sin duplicar trabajo; si se intenta reutilizar una clave con otro alcance, se rechaza. Una petición explícita posterior a una atendida puede registrar una nueva eliminación para datos capturados después: nunca se reutiliza ni se reescribe el historial atendido.

## 3. Estados y reintentos

| Estado | Significado | Capturas |
|---|---|---|
| pendiente | Solicitud registrada y todavía no ejecutada | Bloqueadas |
| procesando | Ejecución adquirida mediante actualización condicional | Bloqueadas |
| parcial | Hubo eliminación, pero quedan elementos o comprobaciones pendientes | Bloqueadas |
| fallida | No se pudo iniciar, continuar o comprobar con fiabilidad; diagnóstico preservado | Bloqueadas |
| atendida | Todo el alcance está verificado y no puede aparecer una escritura anterior tardía | Requieren consentimiento vigente |

Transiciones: pendiente → procesando → atendida/parcial/fallida; parcial/fallida → pendiente ante reintento autorizado. Repetir una llamada cuando ya está pendiente/procesando no crea otra ejecución. Una ejecución interrumpida se recupera conservando su registro y adquiriendo un nuevo token; un token antiguo no puede confirmar resultados sobre la ejecución nueva. El contador aumenta al adquirir una ejecución, no por doble clic.

## 4. API y pantalla propuestas

- `POST /api/verificacion/eliminaciones/`: registra de forma duradera y devuelve 202 con identificador/estado. El cuerpo solo admite clave de idempotencia y confirmación/versionado que finalmente se aprueben; no propietario, estado, archivos, rutas ni campos arbitrarios. Una repetición identificada devuelve el mismo recurso sin efectos duplicados.
- `GET /api/verificacion/eliminaciones/`: historial propio, paginado y sin datos sensibles.
- `GET /api/verificacion/eliminaciones/<uuid>/`: estado propio, fechas, conteos y categorías de elementos pendientes.
- `POST /api/verificacion/eliminaciones/<uuid>/reintentar/`: CSRF, propietario y transición permitida; mismo UUID y nuevo registro de ejecución cuando el procesador adquiera el trabajo. No permite cambios de alcance.

Usar SessionAuthentication y EsPrestador existentes. Derivar siempre el prestador desde `request.user`; campos extra se rechazan. Solicitudes ajenas devuelven 404; anónimo, cliente y administrador no adquieren permisos por enviar un identificador. Las escrituras requieren CSRF real; no usar csrf_exempt, endpoints públicos ni GET con efectos de borrado.

La pantalla React mostrará alcance y exclusiones antes de confirmar, ID/fecha/estado, progreso verificable, pendientes y reintento cuando proceda. Confirmar/cancelar, doble clic, errores de sesión/red, teclado y 320/390/1280 px. No mostrará fotos, datos leídos, rutas privadas ni mensajes de excepción. `VerificarDni` comprobará la barrera antes de montar cámara/selector y antes del envío; el backend sigue siendo la autoridad. Un estado desconocido mantiene el bloqueo.

## 5. Servicio de eliminación y comprobación final

1. Registrar la solicitud y activar la barrera en una transacción corta, antes de responder éxito. Nunca borrar primero y registrar después.
2. Un procesador reclama el trabajo con un UPDATE condicional y un token único. Se propone un comando Django recuperable, ejecutable manualmente y por un proceso programado definido por el equipo; no se incorpora Celery u otro servicio por defecto. Tener un endpoint sin procesador no satisface «se atiende».
3. Inventariar todos los intentos propios y sus referencias originales; confirmar ese inventario duradero antes del primer borrado.
4. Llamar a `intento.eliminar_datos_biometricos()` actualizado por Renzo. No copiar su bucle ni crear una segunda implementación de borrado normal de IntentoDni. Registrar los campos que devuelve como pendientes; también capturar errores de BD, inventario y verificación con códigos sanitizados.
5. Recargar cada intento, comprobar todos los derivados y verificar con su almacenamiento que las referencias inventariadas ya no existen. Archivo previamente ausente es éxito idempotente. Una excepción de exists, un borrado que no tuvo efecto o una referencia residual no son éxito.
6. Bajo la barrera del propietario, comprobar que no quedan intentos/operaciones del alcance sin verificar y que el token/generación siguen vigentes. Solo entonces marcar atendida y liberar el bloqueo en la misma decisión transaccional. `[]` por sí solo NO acredita ese estado.

El almacenamiento y la BD no se revierten juntos. Si se borró un archivo y falló el save, el siguiente intento vuelve a comprobarlo: no intenta reconstruir información eliminada. El manifiesto evita perder su referencia cuando el campo se limpia. Si la reserva o el registro de resultado fallan, la solicitud permanece recuperable y bloqueada; no se informa atención exitosa al usuario.

## 6. Concurrencia en SQLite

`select_for_update()` no es una garantía de exclusión en SQLite. Se usará una fila de coordinación por propietario y escrituras condicionales dentro de atomic; la primera operación decisoria debe adquirir la reserva mediante DML, no depender de una lectura previa sin protección. Las constraints impiden duplicados y se manejan IntegrityError/SQLITE_BUSY sin continuar con un estado supuesto. No se guarda nada si no se adquirió la reserva. Los bloqueos de memoria del proceso no bastan para varios workers.

La solicitud incrementa/invalida la generación de capturas anteriores. El POST de HU-19 comprueba la barrera antes de OCR/archivos y vuelve a comprobar consentimiento, barrera y generación JUSTO ANTES de persistir archivos/IntentoDni. Ese tramo de escritura comparte la reserva del propietario; OCR y consultas de fuentes no mantienen una transacción larga.

- Si la captura termina de persistir primero, la eliminación posterior la incluye en el inventario.
- Si la eliminación se registra primero, una captura antigua no puede guardar sus resultados después y debe liberar sus temporales.
- Una aceptación o revocación concurrente usa la misma coordinación y no desactiva la barrera de borrado.

Debe contemplarse un journal privado por operación de escritura para referencias creadas y limpieza ante fallos de persistencia. Un archivo creado cuyo IntentoDni no llegó a guardarse no puede quedar invisible para la comprobación. Este inventario técnico es separado de la auditoría y no contiene imágenes ni datos OCR; ante propiedad o limpieza no verificable, la solicitud no pasa a atendida. No se recorre ni se elimina indiscriminadamente RAIZ_PRIVADA.

Pruebas con SQLite en archivo temporal, conexiones/procesos independientes y barreras/eventos explícitos, sin sleeps para forzar orden: dos solicitudes simultáneas, dos procesadores, reintentos duplicados, captura frente a solicitud/finalización, revocación/aceptación concurrentes, lock ocupado, caída tras borrar y antes de actualizar BD, y recuperación de ejecución interrumpida. Debe demostrarse un solo trabajo vigente, ninguna captura tardía persistida y ninguna solicitud falsamente atendida. Las pruebas ordinarias en memoria no prueban esta exclusión multiproceso.

Referencia técnica: [Django, select_for_update y SQLite](https://docs.djangoproject.com/en/6.1/ref/models/querysets/#select-for-update).

## 7. Plan de pruebas

| Caso | Django / almacenamiento | Playwright |
|---|---|---|
| Completa | Todos los intentos propios y derivados eliminados; exists falso; cuenta y DNI registrado intactos | Confirmar, ver estado y alcance; sin promesa de borrar la cuenta |
| Parcial | Un campo devuelve fallo; se conserva referencia; nunca atendida | Pendientes legibles y botón de reintento |
| Error | Borrado, save, exists o worker fallan; historial y bloqueo conservados | Mensaje sanitizado y recuperación de consulta |
| Reintento | Mismo UUID, nueva ejecución, progreso conservado y archivo ya ausente aceptado | Doble clic no duplica trabajo |
| Repetida | Idempotencia y constraint, también con dos conexiones | Reutiliza la solicitud y no pierde estado tras recargar |
| Propietario | Consulta/reintento ajenos 404; fotos y datos de otro prestador intactos | No muestra solicitud ajena |
| Sesión/CSRF | APIClient con CSRF exigido, sesión real; rechazo sin efectos | Sesión vencida, error CSRF y red sin anunciar éxito |
| Auditoría | Historial persistente sin imágenes, OCR, nombres, DNI ni rutas/errores crudos | La respuesta/pantalla no revela referencias privadas |
| T21.1/HU-19 | Los 65 casos específicos actuales más regresiones de permiso y generación | Se conservan todos los escenarios de consentimiento y captura |
| Concurrencia | Pruebas independientes en SQLite real temporal, reinicio y fencing del token | Dos pestañas y estado de eliminación bloquean nuevos envíos |

Playwright simulado no sustituye la autenticación/CSRF reales ni la prueba de filesystem. Añadir una integración con backend propio, almacenamiento privado temporal e imágenes generadas. No usar DNI, nombres ni fotografías reales.

## 8. Archivos y etapas futuras

Después de revisión/autorización:

1. Congelar contrato de alcance, idempotencia, estados, respuestas y procesador. Revisar estimación: incluye bastante más que un endpoint nominal; no se cambia la planificación unilateralmente.
2. Añadir modelos/constraints y migración posterior a 0002; pruebas de invariantes y de concurrencia SQLite antes de conectar la UI.
3. Servicio, inventario privado, recuperación y comando de procesamiento; pruebas de fallos de almacenamiento/BD y comprobación final. Reutilizar el método de Renzo.
4. Vistas/serializers/urls y coordinación mínima con `views.py` y `consentimiento.py`; demostrar ausencia de escrituras tardías.
5. Pantalla y estilos de eliminación, rutas/enlaces y bloqueo de `VerificarDni`; adaptar el texto vigente solo con una nueva versión cuando corresponda.
6. Ejecutar suites específicas y completa, migraciones en entorno aislado, Playwright, integración real, lint/build/diff-check; revisión de alcance y privacidad. Mantener pendientes teléfono físico, cámara/OCR reales y accesibilidad humana donde no haya evidencia.

Archivos previstos: `verificacion/models.py`, nueva migración, nuevos servicio/vistas/tests de eliminación y comando; `serializers.py`, `urls.py`, `views.py` y coordinación de consentimiento; nueva pantalla/CSS, `App.jsx`, enlaces y `VerificarDni.jsx`; tests HU-21; contratos HU-19/HU-21, README y AVANCES. No tocar HU-7, HU-8, Firebase o ramas de Renzo.

**Corte histórico:** los puntos 2–5 estaban pendientes al terminar la reconciliación. La implementación local posterior se documenta en el contrato y pruebas de T21.2; no se publica ni se declara aprobada por este diseño.
