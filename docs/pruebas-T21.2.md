# Validación local T21.2 · 05/11/2026

Worktree Chamba-PE-hu21-preparacion, rama feature/hu21-consentimiento-eliminacion, HEAD `5f510c7`. Conserva T21.1 reconciliada y #28; sin commit, push, merge ni PR en esta etapa. [Contrato implementado](API_ELIMINACION_DNI.md).

## Resultado final

| Verificación | Resultado |
|---|---|
| Django completo | **264 ejecutadas en 87,534 s: 261 aprobadas y 3 expectedFailure heredadas de certificados**; system check correcto |
| Grupo verificacion dentro de la suite | 98 casos: 65 anteriores intactos, 26 de eliminación/compatibilidad y 7 SQLite multiproceso |
| Concurrencia | Solicitudes simultáneas con claves iguales/distintas, dos procesadores, dos reintentos, captura simultánea, captura tardía después de atender y SQLite ocupado |
| Playwright final | **33/33 en 39,015 s**, canal Chromium original, un worker, cero retries y omisiones |
| Control serial ampliado | 66/66 en 68,367 s, dos suites completas sin retries; resultado conservado por separado |
| ESLint / build | Correctos; build final 495 ms |
| Migración 0003 | Sin cambios de esquema pendientes; aplicada, revertida y reaplicada únicamente en memoria vacía; sin aceptaciones/solicitudes automáticas |
| git diff --check | Correcto |

Las siete pruebas de concurrencia usan procesos y conexiones independientes contra SQLite en archivo temporal, sincronizados mediante barreras/eventos. No se presentan pruebas de memoria o mocks como garantía multiproceso. No se usa select_for_update como mecanismo suficiente.

## Cobertura de eliminación

- Todos los intentos propios, archivos y derivados; cuenta/DNI registrado y otro prestador intactos.
- Uso comprobado del método de Renzo, que conserva su AST original; sin segunda implementación de limpieza de IntentoDni.
- Borrado parcial y conservación de referencias; reintento con mismo UUID e historial.
- Almacenamiento que devuelve sin borrar, fallo de exists/listdir, fallo de save después de borrar y recuperación de ejecución interrumpida.
- Journal de escrituras cuyo save hizo rollback; referencias fuera de raíz o compartidas no se borran, incluso en ese journal.
- Idempotencia, constraints de solicitud abierta y fecha/estado, dos procesadores/reintentos concurrentes y tokens sustituidos.
- Propietario exclusivamente de sesión, roles, CSRF real exigido, campos manipulados, recursos ajenos 404 y DB ocupada 503 sin información sensible.
- Bloqueo antes de imágenes/OCR para los cuatro estados sin resolver y nueva comprobación antes de guardar; invalidación de generaciones incluso después de atender o revocar/reaceptar.
- Auditoría sin imágenes, datos leídos, rutas ni excepciones crudas; inventario técnico privado fuera de respuestas.
- Consentimiento v1 conservado, sin aceptación automática de v2; aceptación explícita de la nueva versión provisional.
- Los 42 métodos de prueba HU-19 y 23 de T21.1 conservan sus aserciones originales, comprobadas por AST. Los tres expectedFailure de certificados no cambian.

Playwright mantiene los 16 escenarios T21.1 y añade 17 de eliminación: confirmación/CSRF, 202 sin falso éxito, respuesta perdida y clave conservada, parcial/fallida/reintento, consulta atendida, conflicto, errores, bloqueo antes y después de capturar, teclado, roles y responsive 320/390/1280. API/cámara simuladas, imágenes sintéticas; autenticación, CSRF, persistencia y filesystem reales se verifican en Django. Se inspeccionaron capturas de 320 y 1280 px y se corrigió el espaciado de enlaces al pie.

## Fallos encontrados, sin ocultarlos

1. **Playwright paralelo:** primera ejecución 32/33 en 45,707 s. El caso anterior de aceptación agotó 30 s en page.goto('/consentimiento'), antes de sus aserciones; HTML HTTP 200 en 49,511 ms, CSS en 20.145,679 ms y script incompleto. Se conserva error-context, captura, trace.zip y JSON. La configuración HU-21 pasa a un worker tras el contraste serial completo 66/66 y la verificación final 33/33; no se aumentaron timeouts ni se eliminaron pruebas. No se declara resuelto el transporte paralelo del entorno.
2. **Carrera SQLite real:** una ejecución intermedia de 262 tests falló en «dos procesadores»: una solicitud quedó fallida en vez de atendida. Se encontró acceso a la FK perezosa solicitud.prestador tras refresh_from_db DENTRO de atomic, antes de la reserva de escritura. Esa lectura previa permitía colisión de actualización en SQLite. Se resolvió el propietario antes de entrar a cada fase y se añadió una regresión de primer SQL UPDATE. La expectativa original de una sola ejecución atendida se mantiene. Pasaron luego 32 casos específicos de eliminación/concurrencia en 61,304 s, la suite completa de 263 y la entrega final de 264 tras ampliar la protección de referencias ajenas. El log fallido permanece conservado.

No hubo otros fallos nuevos en la entrega final. Los fallos de almacenamiento/BD inyectados por pruebas son escenarios controlados; los tres expectedFailure existentes siguen siendo defectos heredados, no se reclasifican como aprobados.

## Evidencias y resguardos

Todo está bajo `.hu21-local/implementacion-t212-20261105/`, ignorado:

- originales/ y manifest.json: 166 archivos fuente previos a T21.2 con SHA-256. No reemplazan el resguardo de reconciliación de 164 archivos, que también se conserva.
- django-inicial.log, concurrencia-inicial.log y demás ejecuciones preliminares.
- django-validacion-final.log: incluye el fallo real de dos procesadores. **No borrar ni confundir con la entrega final.**
- django-correccion-concurrencia.log y django-cierre.log: controles posteriores a la corrección.
- django-entrega.log: resultado Django final de 264 pruebas.
- playwright-inicial.json y playwright-inicial/: fallo paralelo y sus artefactos.
- playwright-serial.json y playwright-serial/: contraste serial.
- playwright-final.json y playwright-final/: resultado y capturas de la configuración final.
- qa-*.png y qa-final-*.png: inspección visual antes/después; capturas previas conservadas.

La salida por defecto nueva usa `.hu21-local/playwright-hu21/` para no sustituir las evidencias originales de T21.1. No se añaden logs, bases temporales, imágenes ni trazas al diff fuente. El listado de archivos de esta etapa y la comprobación de otros worktrees se guardan en el mismo resguardo privado.

## Archivos de esta implementación

Nuevos:

- verificacion/coordinacion.py, eliminacion.py y vistas_eliminacion.py.
- verificacion/migrations/0003_eliminacion_dni.py.
- verificacion/management/__init__.py, management/commands/__init__.py y management/commands/procesar_eliminaciones_dni.py.
- verificacion/test_eliminacion.py y test_eliminacion_concurrencia.py.
- frontend/src/EliminarDatos.jsx y EliminarDatos.css.
- frontend/tests/hu21/eliminacion.spec.mjs.
- docs/API_ELIMINACION_DNI.md y docs/pruebas-T21.2.md.

Actualizados sobre la preparación reconciliada:

- verificacion/models.py, consentimiento.py, textos_consentimiento.py, urls.py y views.py.
- frontend/src/App.jsx, Consentimiento.jsx y VerificarDni.jsx; frontend/playwright.hu21.config.mjs.
- README.md, AVANCES.md, docs/API_CONSENTIMIENTO.md, DISENO_T21_2.md y pruebas-T21.1.md.

Los otros archivos locales anteriores de T21.1 permanecen sin perder contenido; su estado sin commit sigue incluido en git status. No se reintroducen commits ni se modifican las ramas de Renzo.

## Límites para revisión

No se instaló un scheduler: un operador debe ejecutar el procesador; sin ello, pendiente no equivale a atendida. Texto demo v2, retención del historial y política operativa pendientes del PO. No se validaron cámara/OCR reales, teléfono físico, lector de pantalla u otros motores de navegador. Playwright usa backend simulado; no se afirma una integración de navegador contra Django real. Las pruebas de filesystem/CSRF/sesión y SQLite multiproceso sí son reales y aisladas.

No se usaron documentos personales reales ni una base de trabajo; las migraciones solo se probaron en bases temporales o memoria. No se promete eliminación forense de backups ni de huérfanos históricos sin propiedad identificada. La muestra serial aprobada no garantiza estabilidad universal ni repara la ejecución paralela.

Estado: implementación local para revisión, sin publicación y sin declarar HU-21 terminada.

## Correcciones autorizadas de la revisión técnica · 07/11/2026

Alcance: exclusivamente los tres defectos reproducidos. Rama
`feature/hu21-consentimiento-eliminacion`, HEAD `5f510c7`, sin integrar main.
Resguardo previo de los 180 archivos fuente, incluidos todos los untracked, en
`.hu21-local/correcciones-t212-20261107-5735ac17e9004e3b8e97ebe169648d6e/originales/`
con manifiesto SHA-256. Los respaldos y evidencias anteriores permanecen intactos.

### Cambios y regresiones

1. `VerificarDni.jsx`: exige booleanos explícitos de vigencia y eliminación antes
   de capturar/enviar. Estados incompletos, nulos o de tipos incorrectos no permiten
   continuar. Un fallo de comprobación después de capturar libera las imágenes y
   obliga a reconsultar. Se mantienen permisos, mensajes, revocación, barrera y
   controles del backend de HU-19/T21.1. Hay 16 regresiones nuevas: siete respuestas
   inválidas al abrir y antes de enviar, y fallos HTTP/red previos al envío.
2. `EliminarDatos.jsx`: generación de consultas, cancelación e invalidación al
   iniciar POST, comprobación de vigencia también después de recibir/parsear el
   cuerpo, y exclusión de escrituras locales simultáneas. Se añadieron cinco casos
   de respuesta antigua frente a un POST en cada estado, y uno de error antiguo.
   La prueba retiene un cuerpo ya recibido: demuestra que cancelar fetch por sí
   solo no basta. Usa señales y cuadros de renderizado, sin sleeps ni retries.
3. `vistas_consentimiento.py`: captura `OperationalError` fuera del servicio
   `atomic` y responde 503 JSON sanitizado. Cinco pruebas nuevas verifican fallo
   de reserva al aceptar/revocar, recuperación/idempotencia, rollback tras escrituras
   parciales y comprobación de rol/CSRF antes de reservar.

Los mocks normales pasan a `demo-dni-v2` y agregan el booleano de eliminación.
Se conserva la aserción de versión enviada, ahora contra la versión vigente, y
todas las aserciones funcionales anteriores. Las pruebas que comprueban historia
v1 y nueva aceptación explícita siguen usando v1 intencionalmente.

### Resultados de esta etapa

| Verificación | Resultado |
|---|---|
| Regresiones antes de corregir | Dos Playwright representativas fallan; dos reservas Django producen los errores originales. Logs/trazas conservados |
| Consentimiento específico | 28/28, incluidos cinco casos nuevos |
| Django completo | **269 en 109,843 s: 266 aprobadas y tres expectedFailure heredadas**, sin regresiones nuevas |
| SQLite multiproceso | **7/7**, dentro de Django completo, sin modificar sus pruebas |
| Playwright HU-21 | **55/55 en 62,786 s**, 33 anteriores y 22 nuevas; un worker, cero retries, omisiones y flaky |
| ESLint | Código 0 |
| Build | Código 0, 456 ms; salida aislada nueva |
| Migraciones | `makemigrations --check --dry-run`: sin cambios; 0003 aplicada por la suite sobre BD de pruebas |
| Django system check | Sin incidencias |
| Integridad | Método de Renzo y métodos de pruebas previos conservados; comparación SHA-256 de fuentes y respaldos, ver `integridad.json` |

Las tres expectedFailure son carga por anónimo, cliente y otro prestador en la
base antigua. No se modificaron ni se presentan como aprobaciones normales.
Main remoto ya contiene #29; su eventual integración exigirá revalidarlas.

La ejecución usa el Python del venv existente y settings aislados con base en
memoria; los siete procesos SQLite crean sus propios archivos temporales. No
se usa la base ni el almacenamiento de trabajo. Playwright utiliza Chromium,
API/cámara simuladas y un servidor propio cerrado al finalizar. La configuración
de validación solo cambia rutas de artefactos y gestión del servidor: conserva
worker, timeouts, retries y pruebas. Los logs red/green, JSON, trazas y builds
quedan en el nuevo resguardo ignorado; no sustituyen evidencias anteriores.

### Remoto, alcance y límites

Consulta REST de GitHub: main `ecd633438719b88bba13fe54407dd7c2f3bcc177`;
#27 y #28 abiertos, cabecera #28 igual a HEAD. Comparación main/base: 35 commits
exclusivos de main y nueve de esta base, sin contar cambios locales. Main incluye
#29/#32/#33; siguen pendientes la preparación de integración y revisión de menú,
rutas, dependencias y documentos. No se hizo fetch, merge ni actualización de refs.

Los tres defectos quedan corregidos y sus regresiones pasan. No se declara HU-21
integrada ni lista para uso real: PO/equipo deben definir quién ejecuta
`procesar_eliminaciones_dni`, frecuencia, seguimiento y recuperación. Puede
mantenerse manual con responsabilidad explícita; no se instaló un scheduler.
También quedan pendientes texto provisional, retención, navegador contra Django
real, teléfono físico, cámara/OCR reales y accesibilidad humana. No se garantiza
borrado forense ni eliminación de backups o huérfanos históricos sin propietario.

Archivos editados en esta etapa: los dos componentes React, las dos suites
Playwright HU-21, `verificacion/vistas_consentimiento.py`,
`verificacion/test_consentimiento.py`, `docs/API_CONSENTIMIENTO.md`,
`docs/API_ELIMINACION_DNI.md`, aclaración de estado en
`docs/API_VERIFICACION_DNI.md`, este informe y `AVANCES.md`.
No se modifican modelos, migraciones, método de Renzo, HU-7/HU-8/Firebase,
Android/Spring ni funciones ajenas. Sin commit, push, merge, rebase o PR.
