> Actualización: el informe original se conserva como evidencia histórica. La revisión y los resultados vigentes del 02/11 están en [Revisión aislada HU-7](#revisión-aislada-hu-7--02112026).

# T7.2 — mapa de prestadores

Implementación del 27/10/2026 en `feature/t7.2-mapa-marcadores`, con pruebas manuales reportadas por Luis y pendiente de revisión e integración. HU-7 y Sprint 2 siguen abiertos.

## Comportamiento

Se reutiliza `GET /api/prestadores/buscar/` y el mapa Leaflet de Resultados.
Cada resultado conserva su tarjeta y formulario, y tiene un marcador en su centro
de cobertura registrado (no es seguimiento en tiempo real). Azul identifica al
cliente; verde y número identifican al prestador. Un `+` indica coordenadas
compartidas: cualquier marcador de ese punto abre una ventana con todos sus
prestadores, desplazable si hace falta. También funciona si coinciden con el cliente.

Las ventanas muestran nombre, todos los rubros, distancia en km y «Sin
calificaciones», porque el contrato actual no incluye puntuaciones reales.
Los textos se renderizan con React, sin interpolar datos del servidor en HTML.
Pulsar marcadores, ventanas o el botón de encuadre no selecciona otra ubicación.
Al cambiar rubro o ubicación, se cancela la petición y se limpian tarjetas y
marcadores. Una respuesta cancelada no puede reemplazar los resultados nuevos.
Coordenadas inválidas del servidor producen un error, sin resultados parciales.

El mapa encuadra cliente/resultados al cambiar esos datos; explorar o abrir
ventanas no vuelve a encuadrarlo. «Ver ubicación y resultados» recupera el encuadre.
En celular las coordenadas se apilan y la ventana limita su ancho al espacio disponible.

## Validación ejecutada

- `cd frontend; npm run lint`: OK.
- `cd frontend; npm run build`: OK (Vite, 73 módulos).
- Dependencias existentes restauradas desde el lockfile; sin bibliotecas nuevas.
- No se modificaron registros locales ni se enviaron solicitudes de prueba.

La automatización visual fue bloqueada por Computer Use al no poder determinar
con confianza la URL activa de Chrome. Las comprobaciones visuales siguientes
fueron realizadas manualmente por **Luis en Chrome**, según su reporte; no se
atribuyen a la automatización ni a lint/build.

## Pruebas manuales realizadas por Luis

| Caso | Resultado reportado |
|---|---|
| Buscar Gasfitería | El prestador ficticio Demo Gasfitero aparece en el mapa y en su tarjeta. |
| Abrir el marcador | La ventana muestra nombre, rubro, distancia y «Sin calificaciones». |
| Cerrar y volver a abrir la ventana | Las coordenadas del cliente se conservan. |
| Cambiar de rubro | Se eliminan el marcador y la tarjeta anteriores. |
| Buscar un rubro sin coincidencias | Aparece «No encontramos prestadores». |
| Vista de 390 px de ancho | Mapa y ventana del marcador se ven correctamente. |
| Vista de 320 × 800 px | Tarjeta, campos y botón de solicitud se ven sin cortes. |

Luis aclaró que las variaciones de distancia entre capturas corresponden a
cambios manuales de su ubicación; no son evidencia de un cambio involuntario
causado por los marcadores. No se ha probado el envío de solicitudes.

## Abrir y ampliar la comprobación manual

Desde la raíz, en una terminal:

```powershell
.\venv\Scripts\python.exe manage.py runserver
```

En otra:

```powershell
cd frontend
npm run dev -- --host 127.0.0.1
```

Abrir `http://127.0.0.1:5173/resultados` (usar el puerto que indique Vite si cambia).
Las teselas de OpenStreetMap necesitan conexión a Internet.

La lista siguiente es una guía de comprobación ampliada; solo los casos de la
tabla anterior se consideran ejecutados. Permanecen pendientes las coordenadas
compartidas (también con el cliente), encuadre/exploración, limpieza al editar o
seleccionar ubicación, cancelación con respuestas tardías, carga y errores de
red/coordenadas del servidor, validaciones y envío de solicitudes, navegación
completa por teclado y comprobación global de desbordamiento horizontal.

1. Elegir un rubro y una ubicación cubierta por prestadores existentes. Buscar:
   deben coincidir cantidad de tarjetas y marcadores. Abrir un marcador y comprobar
   nombre, rubros, km y «Sin calificaciones»; la ubicación y las tarjetas no cambian.
2. Si ya existen prestadores con coordenadas iguales, abrir el punto con `+` y
   consultar todos, desplazando la ventana. No crear ni editar registros para esta
   prueba sin acordarlo antes; si no hay coincidencias, dejar el caso pendiente.
3. Mover/zoom del mapa; debe conservar la exploración hasta pulsar «Ver ubicación
   y resultados». Pulsar ese botón y comprobar que no se limpien los resultados.
4. Cambiar rubro, editar coordenadas o tocar un punto libre: desaparecen tarjetas y
   marcadores anteriores. En una conexión lenta, iniciar una búsqueda y cambiar
   inmediatamente rubro/ubicación: la respuesta anterior no debe reaparecer.
5. Buscar fuera de cobertura: mensaje sin resultados, sin marcadores de prestadores.
   Simular desconexión en DevTools y repetir: error recuperable, sin datos anteriores.
6. Revisar el formulario de solicitud en las tarjetas y sus validaciones sin enviarlo.
   El envío real queda pendiente: crea un registro y debe probarse con datos acordados.
7. En DevTools, probar anchos de 320 y 390 px y escritorio. Revisar mapa, ventana,
   formulario, tarjetas y navegación: sin barra horizontal; botones utilizables.

## Pendientes

Consulta pública a GitHub el 27/10/2026: PR [#14](https://github.com/RXDG2908/Chamba-PE/pull/14)
de T7.1 abierto, sin integrar (`state: open`, `merged: false`). La rama T7.2
hereda los commits `5c30dfb` (coordenadas de la API), `5ddaa14` (documentación
T7.1) y el merge `e42ae77` con main. Los cambios nuevos de T7.2 son JSX/CSS
para marcadores y ventanas, más documentación y pruebas manuales; no añaden
otro endpoint ni implementan el filtro administrativo.

Filtro Verificado en búsqueda/solicitudes a cargo de
David (T9.1), sin filtro adicional en el frontend; T7.3 y pruebas de integración.
Confirmar continuidad del contrato al integrar roles/disponibilidad. HU-12 deberá
definir el contrato de calificaciones antes de mostrar puntuaciones. Sin Firebase.

La entrega se separa en dos commits: implementación JSX/CSS y documentación
Markdown con pruebas manuales y dependencias. Su publicación no cierra HU-7 ni
Sprint 2; siguen pendientes revisión, integración y las comprobaciones indicadas.

## Revisión aislada HU-7 · 02/11/2026

### Base, alcance y PR

Worktree independiente `Chamba-PE-hu7-revision`, rama `review/hu7-t71-t72`,
creado desde `origin/main f515837c6453d1d2a8a17e742c9ff9323f0ab94e`.
Se trasladaron únicamente los cambios funcionales de #14/#16 y estas pruebas;
no se hizo merge, commit, push ni PR. La carpeta original de HU-8 no se cambió
de rama ni se utilizó su base de datos/configuración Firebase en las pruebas.

Consulta GitHub del 02/11: ambos PR siguen abiertos, sin merge, comentarios de
conversación, comentarios de revisión ni revisiones registradas.
- #14: `feature/t7.1-mapa-prestadores`, `5ddaa14`. Archivos: AVANCES.md,
  SPRINTS.md, docs/API_BUSQUEDA.md, servicios/serializers.py y servicios/tests.py.
- #16: `feature/t7.2-mapa-marcadores`, `485edb4`. Incluye los cinco anteriores
  más TABLERO.md, docs/pruebas-T7.2.md, frontend/src/App.css y
  frontend/src/ResultadosPrestadores.jsx.
- #14 es ancestro de #16. Orden de integración recomendado: #14, después #16.
  Actualizar/revisar el diff de #16 después de incorporar #14 para no duplicarlo.
- GitHub respondió mergeable desconocido. El cálculo local de conflictos, sin
  modificar ramas, detectó en #14: SPRINTS.md y docs/API_BUSQUEDA.md; en #16:
  SPRINTS.md, TABLERO.md y docs/API_BUSQUEDA.md. No se detectaron conflictos
  funcionales en servicios/serializers.py ni servicios/tests.py.
- Conservar siempre el cierre histórico y los registros actuales de main al
  resolver documentación. La planificación de Sprint 3 no se modifica aquí.

### Qué está implementado y qué se corrigió

**T7.1:** dos campos FloatField de solo lectura devuelven latitud/longitud como
números JSON del centro de cobertura del prestador. Se conserva el contrato de
tarjetas, la búsqueda pública y la cabecera X-Execution-Time-Ms. Se mantienen los
filtros integrados de main: Verificado, Activo, rubro y cobertura inclusiva,
ordenados por distancia sin redondear. Los parámetros ausentes, texto, NaN,
infinito y fuera de rango ya se validan con 400. No se cambian rutas ni modelos.

**T7.2:** se conservan marcadores, popups con nombre/rubros/distancia y «Sin
calificaciones», puntos compartidos, encuadre y formularios de solicitud.
La suite reprodujo y permitió corregir:
1. Datos incompletos del prestador con coordenadas válidas podían romper React
   al renderizar nombres/rubros. Se valida el contrato antes de usar los mismos
   datos en mapa y lista; también se rechazan IDs duplicados. Error recuperable
   en pantalla, sin mantener resultados anteriores.
2. El mapa no tenía una región con nombre accesible. Ahora tiene el nombre
   «Mapa de prestadores y ubicación»; se comprobó abrir un marcador con teclado.

Los avisos iniciales de límites responsive correspondían a medir durante la
animación de autoencuadre. Se ajustó el test para esperar el resultado estable;
no se alteraron estilos por ese falso positivo. La suite final utiliza build
de producción y preview, evitando las recargas de optimización/HMR de Vite.

### Pruebas automatizadas ejecutadas

| Verificación | Resultado | Alcance / límite |
|---|---|---|
| Django, manage.py test --noinput | 168 ejecutadas: 165 pasan y 3 fallos esperados heredados | Base de pruebas temporal; búsqueda/coordenadas, roles/login, disponibilidad, solicitudes, revisión y certificados |
| makemigrations --check --dry-run | Sin cambios | No se crearon migraciones |
| npm run lint | OK | Frontend del worktree |
| npm run build | OK | Build de producción, también generado por la suite |
| npm run test:hu7 | 16 pruebas Chromium OK | API y teselas simuladas, sin Django real ni datos del usuario |
| git diff --check | OK | Sin marcadores de conflicto ni problemas de espacios |

Las tres expectedFailure documentan el impedimento 16 de certificados, ya
existente en main y fuera de esta revisión de HU-7. No se presentan como tres
correcciones ni como una suite sin deuda conocida.

Las 16 pruebas de navegador cubren: posición relativa norte/este y coincidencia
de personas entre lista/mapa; apertura y clic del popup sin cambiar ubicación;
dos prestadores coincidentes con el cliente; limpieza al cambiar rubro/coordenadas;
carga, cancelación y respuesta tardía; vacío, error HTTP/red y recuperación;
cuatro clases de coordenadas inválidas del servidor; payload incompleto;
teclado y región accesible; 320/390/1280 px sin overflow y popup dentro de pantalla;
selección de otro punto; envío desde tarjeta con ID/rubro/coordenadas correctos.
El envío de solicitud en estas pruebas también está simulado: no crea registros.

Dependencias: se añade solo @playwright/test como desarrollo (más sus dos
dependencias), script test:hu7 y configuración. No cambia ninguna versión de las
dependencias existentes de main ni se añade biblioteca de ejecución al producto.
npm audit de la instalación exacta reportó 8 alertas (6 moderadas y 2 altas) en
dependencias heredadas; no se aplicó una actualización general fuera de alcance.

### Reproducir en el worktree

Backend con un entorno Python que tenga requirements.txt instalado:

```powershell
python manage.py test --noinput
python manage.py makemigrations --check --dry-run
```

Frontend:

```powershell
cd frontend
npm ci
npx playwright install chromium
npm run lint
npm run test:hu7
```

La configuración reserva 127.0.0.1:5187 con strictPort y no reutiliza servidores
ajenos. Construye y sirve el frontend del worktree; Playwright cierra su servidor
al terminar. Los resultados y capturas de fallo quedan ignorados por Git.

### Pendientes antes de integrar o declarar terminadas

- Revisión cruzada de David y autorización para publicar las correcciones en las
  ramas de los PR existentes; esta rama de revisión no los actualiza.
- Resolver los conflictos documentales conservando main, sin sobrescribir el
  cierre de S2 ni la planificación de S3. #14 antes de #16.
- Validación manual conjunta con Django real y teselas OSM reales, encuadre tras
  explorar el mapa, teclado/lector de pantalla y navegador de teléfono físico.
  Los tests automatizados responsive no son una prueba en un dispositivo físico.
- PO debe confirmar el tratamiento «Sin calificaciones»: no hay modelo real de
  reseñas de HU-12 y no se inventan valores para cumplir el criterio de HU-7.
- Repetir las regresiones pertinentes después de publicar/actualizar las ramas
  y comprobar el diff final contra main antes de cualquier integración futura.

T7.1/T7.2 quedan técnicamente revisadas y con cambios locales preparados; no se
marcan terminadas ni se descuentan horas por esta revisión. HU-7 continúa abierta.

## Preparación separada para actualizar el PR #16 · 02/11/2026

Worktree Chamba-PE-hu7-t72-preparacion; rama prepare/hu7-t72-pr16,
basada en la cabecera remota 485edb4. El backend heredado de #14 permanece
sin cambios adicionales; no se vuelven a aplicar ni a copiar sus commits.

La actualización pendiente de T7.2 se limita a:
- frontend/src/ResultadosPrestadores.jsx: las dos correcciones verificadas
  (contrato de datos antes de render y región accesible).
- frontend/.gitignore, package.json, package-lock.json, playwright.config.mjs
  y tests/hu7/mapa.spec.mjs: suite reproducible y artefactos ignorados.
- AVANCES.md, SPRINTS.md, TABLERO.md y docs/API_BUSQUEDA.md: resoluciones
  documentales basadas en main conservando su historial, más esta evidencia.

frontend/src/App.css ya contiene los estilos del mapa en el PR #16 y no necesita
un segundo cambio. servicios/serializers.py y servicios/tests.py pertenecen a
T7.1 y no se modifican en esta actualización.

Conflictos documentales identificados: SPRINTS.md, TABLERO.md y API_BUSQUEDA.
El contenido resuelto se preparó, pero el historial aún NO se sincronizó con
main: no se ha ejecutado merge. La planificación de S3 y el cierre de S2 se
conservan desde main, con notas nuevas de HU-7 separadas.

### Publicación futura, sujeta a autorización

1. Autorizar commit local de los archivos enumerados, sin incluir otro código.
2. Esperar la revisión/integración de #14 en main por el equipo.
3. Autorizar fetch y merge normal de ese main en esta rama. No cherry-pick de
   los commits de #14, no rebase ni force push. Resolver los documentos
   conservando también cualquier actualización posterior de main.
4. Repetir pruebas con main ya sincronizado y verificar el diff de #16: el
   backend de #14 debe desaparecer del diff porque ya estará en la base.
5. Autorizar push normal: `git push origin HEAD:feature/t7.2-mapa-marcadores`.
   Se actualiza #16; no se abre otro PR.
6. Revisión de David y decisión del equipo sobre integración. No declarar
   terminado por tests automatizados si quedan criterios/manuales pendientes.

### Validación manual conjunta aún pendiente

Usar una BD aislada con prestadores ficticios verificados y activos, otro inactivo,
otro no verificado y rubros distintos. Con Django real comprobar búsqueda,
marcadores/lista y envío de solicitudes; repetir cambios rápidos y errores de red.
Comprobar ubicación/encuadre al mover/zoom y recuperarlo, varios prestadores en
la misma coordenada y coincidiendo con el cliente, y navegación por teclado.

En teléfono físico: origen HTTPS confiable, cámara/geolocalización si se usa,
gestos de mapa, popup, formularios y orientación sin desbordamiento; un viewport
Chromium de 320/390 px no sustituye esta comprobación. No usar credenciales ni
base de HU-8. Las pruebas actuales de navegador simulan API y teselas; verificar
también las teselas OSM reales. Acordar con PO «Sin calificaciones» mientras
HU-12 no exista. T7.2/HU-7 continúan pendientes de cierre.

### Validación de esta cabecera preparada

02/11/2026: 60 pruebas Django de la cabecera antigua T7.2 aprobadas. Las 168 pruebas sobre main corresponden al worktree integrado conservado, no a una sincronización ya realizada en esta preparación.

En esta preparación separada también se ejecutó npm ci --offline, lint y la suite de build/preview: 16 pruebas Chromium aprobadas. APIs/teselas y envío de solicitudes simulados, sin datos ni credenciales de HU-8. El resultado de audit sin conexión no se usa para declarar ausencia de vulnerabilidades.

### Incorporación selectiva de correcciones verificadas · 02/11/2026

En prepare/hu7-t72-pr16 se incorporaron únicamente las dos opciones animate:false del encuadre de resultados/recuperación y su comentario, desde test/hu7-validacion-local. Se conserva el autopan de ventanas, la validación del payload y la región accesible, además de las 16 pruebas previas. No se copió backend/T7.1, documentación de Sprint 3, diagnósticos históricos con esperas ni configuración Firebase.

Se agregaron la suite de 14 integraciones (sin sleeps ni reencuadre manual para aprobar el recorrido inicial), los configs Vite/Playwright exclusivos, el generador Django aislado, el script test:hu7-local y la exclusión .hu7-local/. Los archivos existentes no se sustituyeron con versiones de la documentación del origen: solo se agregaron esta sección y una entrada a AVANCES. Los documentos ya preparados y sus registros históricos se conservaron.

**Resultados nuevos en destino:** 16/16 Chromium con APIs y teselas simuladas, 22,4 s; 14/14 integraciones, 27,8 s, API Django y teselas OSM reales, cobertura/ubicación/listado/marcadores, filtros, vacío, cancelación, red, sesión/CSRF/disponibilidad, solicitud ficticia y ventanas compartidas/responsive a 320/390/1280. Lint, build y diff-check correctos. [JSON completo de integración](evidencias/hu7-local/resultados.json), [resumen de ambas suites](evidencias/hu7-local/transferencia-resumen.json).

**Límite importante de integración:** este worktree conserva la cabecera antigua 485edb4 de #16; su backend no contiene usuarios/sesión/disponibilidad ni todas las dependencias actuales. Las 14 integraciones ejecutaron el FRONTEND preparado aquí contra el BACKEND compatible del worktree Chamba-PE-hu7-validacion-local (base 8c887ae). El generador importa ese backend en un proceso separado; antes de django.setup sustituye la base y media por rutas temporales del DESTINO. No ejecuta escrituras en la base principal ni en el worktree de origen. Por tanto, no se afirma que las 14 pasen sobre el backend antiguo autónomo de #16. Esa combinación debe sincronizarse y repetirse tras la revisión/integración de dependencias.

Para reproducir: definir HU7_TEST_PYTHON con la ruta a un python.exe con requirements instalado; definir HU7_TEST_BACKEND_ROOT con la carpeta del backend compatible (actualmente el worktree de validación); desde frontend ejecutar npm run test:hu7-local -- integracion.spec.mjs. Con dependencias integradas podrá omitirse HU7_TEST_BACKEND_ROOT y usar el backend del propio worktree. Puertos 8019/5189, reuseExistingServer:false, base nueva con siete prestadores ficticios por ejecución. La suite propia se ejecuta con npm run test:hu7 y puerto 5187. No copiar .env ni claves.

Pendiente teléfono físico, tacto/orientación/GPS, Safari/iOS/Firefox, lector de pantalla, red móvil física y revisión de David. Sigue pendiente sincronización normal de #16 con main/T7.1 autorizada por separado, repetir pruebas sobre esa combinación y autorizar commit/push. No se publica ahora, no se modifica #14 ni se declara HU-7/T7.2 terminada.
