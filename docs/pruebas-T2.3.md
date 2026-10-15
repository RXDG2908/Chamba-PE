# Actualización T2.3 — 15/10/2026

Esta actualización prevalece sobre los bloqueos históricos descritos debajo.
Base `main`: `f5565c0` (PR #6 integrado, remoto comprobado). Rama de trabajo:
`pruebas-validacion-sprint1`. Código probado: `baa41e5`, con distribución de
tarjetas en `fc756df`. README sin cambios: las instrucciones de uso se conservan.

| Caso | Resultado | Observación |
|---|---|---|
| Suite de Luis sobre f5565c0 | 43 OK, reportado por Luis | Instaló requirements y ejecutó migrate; no repetido por el asistente sobre esa base sin cambios |
| Suite del asistente sobre el código de baa41e5 | 49 OK | `python manage.py test prestadores.tests servicios.tests certificaciones.tests --noinput`; última ejecución aproximadamente 2,3 s, salida 0 |
| Registro y carga API | OK dentro de la suite | Incluye asociación de certificación y rechazo sin prestador; la falla histórica quedó corregida en main |
| Frontend, asistente | lint y build OK | En `frontend/`: `npm.cmd run lint` y `npm.cmd run build`; 63 módulos. No demuestra presentación visual ni carga desde UI |
| Incidencias de solicitud y tarjeta | Correcciones implementadas | Detalle y comprobaciones pendientes en [T4.3](pruebas-T4.3.md) |

Se usaron BD de pruebas de Django y media temporal de los tests de carga.
El asistente no ejecutó migrate en la BD local, no recuperó stashes, no cambió
la contraseña del usuario demo y no ejecutó scripts de rendimiento.
T2.3 continúa parcial por comprobaciones UI y revisión pendientes. T2.2 y T3.4
de David siguen pendientes; Sprint 1 no se declara terminado.

---

# Evidencia histórica T2.3 y revisión de integración — 12/10/2026

Responsable de pruebas: Luis Abad. Rama `hu-3-busqueda`, HEAD `5d2e1db`.
Se leyeron PLAN_EQUIPO.md, SPRINTS.md y AVANCES.md; no se encontró AGENTS.md
en el repositorio ni en sus directorios ascendentes. Estado inicial limpio.
El commit `02cb494` y `origin/main` hasta `1483eab` ya son ancestros de HEAD;
no se repitió el merge. Rama inicialmente adelantada 3 commits respecto a origin.
No se hicieron commits, push, cambios de rama ni fusiones.

## Ejecución automatizada real

Desde la raíz, en Windows PowerShell:

```powershell
.\venv\Scripts\python.exe manage.py test prestadores.tests servicios.tests certificaciones.tests --noinput
```

Resultado final: **28 pruebas, 27 OK y 1 FAIL**, en 2.045 s, código de salida
del runner 1. Repetición final tras revisar los archivos: mismo resultado, 2.578 s.
System check sin problemas. Antes de añadir casos: 18 pruebas OK.
Se añadieron 3 métodos de registro con subcasos y 7 de carga.
La BD SQLite de pruebas en memoria se creó y destruyó; cada carga utiliza
`TemporaryDirectory` y `override_settings(MEDIA_ROOT=...)`, con limpieza posterior.
No se ejecutaron migraciones sobre la BD local ni se usó su carpeta media.

Se seleccionan módulos explícitos porque `test_reporte.py` y
`certificaciones/test_reporte.py` ejecutan HTTP contra localhost y escriben
`evidencia_rendimiento.txt` al importarse. Son scripts de David, no pruebas
aisladas. No se ejecutaron ni modificaron; el descubrimiento genérico queda pendiente.

| Caso | Resultado automatizado |
|---|---|
| Registro válido | 201, estado `pendiente`, rubro asociado, contraseña omitida |
| Intento de registrar estado `verificado` | 201, persiste pendiente y etiqueta Pendiente de verificación |
| Campos obligatorios ausentes y vacíos | 400 para username, email, password, nombres, apellidos, DNI, rubros, latitud, longitud y radio; no crea usuario/perfil |
| DNI inválido / duplicado | 400; se mantienen las pruebas existentes |
| Rubros vacíos | 400, error del campo rubros |
| Cobertura ausente | 400, cada coordenada y radio ausentes |
| Cobertura inválida | 400, coordenadas fuera de rango, radio 0/-1/101, texto y null |
| PDF válido / JPG válido | 201, estado pendiente y bytes guardados iguales al original |
| Archivo ausente | 400, sin archivos guardados |
| Formato no permitido | 400 para PNG, extensión EXE con MIME PDF y PDF con MIME text/plain; sin archivos guardados |
| Exactamente 5 MB (5 × 1024 × 1024 = 5.242.880 bytes) | 201, archivo guardado íntegro |
| 5 MB + 1 byte (5.242.881 bytes) | 400, mensaje de límite y sin archivo guardado |
| Asociación al prestador (HU-2) | **FAIL**: HTTP 201 pero 0 registros CertificacionPrestador |

El PDF de prueba incluye catálogo, página y tabla xref; el JPG es una imagen
real de 1×1 generada con System.Drawing e incorporada como base64. Los archivos
de tamaño límite son PDFs con relleno antes de EOF. La API comprueba extensión
y MIME; estas pruebas no certifican validación del contenido por el servidor.

## Incumplimientos y reproducción

> **Actualización 14/10/2026:** los resultados siguientes documentan una
> ejecución anterior y están supersedidos por las correcciones de
> `feature/sprint-1-correcciones`. La suite actualizada pasó 43 pruebas,
> incluyendo asociación al prestador y HTTP 400 si falta; la carga guarda el
> archivo mediante el modelo. Ver [AVANCES.md](../AVANCES.md) para el cierre.

**David — T2.1, asociación HU-2 (bloquea cierre de T2.3).**
Ejecutar de forma aislada, sin registros locales:

```powershell
.\venv\Scripts\python.exe manage.py test certificaciones.tests.CargaCertificacionTests.test_certificacion_asociada_al_prestador --noinput
```

La prueba registra un prestador por API, carga un PDF con `prestador=<id>` y
exige un registro persistido enlazado al perfil. Resultado: 201 y archivo temporal
guardado, pero `CertificacionPrestador.objects.count()` es 0 (esperado 1).
`upload_certification` ignora el prestador y solo utiliza FileSystemStorage;
el modelo contiene latitud/longitud, sin relación al perfil ni campo de archivo.
La prueba queda fallando, sin skip ni expectedFailure. El nombre `prestador`
es el contrato propuesto para reproducir; David debe definir el contrato definitivo
o usar la identidad autenticada, cumpliendo siempre la asociación persistida.

**David — integración frontend, T2.2/T4.2.** Desde `frontend/`:

```powershell
npm.cmd run lint
npm.cmd run build
```

Lint: **FAIL**, `UploadCertificado.jsx:63`, variable `err` sin usar
(`no-unused-vars`). Build: **OK**, Vite genera producción (62 módulos).
El componente importa `UploadCertificado.css`, archivo ausente, pero no está
importado ni accesible desde App.jsx; por eso el build actual no detecta esa
dependencia. Pendiente de David corregir lint, aportar estilos y conectar su
componente. No se modificó código de sus funcionalidades.

**David — T3.3/T3.4, integración pendiente con búsqueda HU-3.**
Inspección: ordenamiento y medición apuntan a
`/api/certificaciones/prestadores/cercanos/` y a CertificacionPrestador,
no a `/api/prestadores/buscar/` ni al modelo Prestador usado por React.
Reproducción por lectura de `certificaciones/views.py` y ambos `test_reporte.py`.
Las pruebas de búsqueda siguen pasando, pero esto no demuestra orden por distancia
ni tiempo <3 s para el endpoint de HU-3. David debe verificar/integrar sus tareas;
no se implementaron ni se dieron por terminadas en esta revisión.

## Pruebas manuales previas declaradas por Luis

No se repitieron ni se cuentan como automatizadas de esta sesión:

| Caso | Observación de Luis |
|---|---|
| Registro válido | “Registro enviado”, pendiente de verificación |
| DNI duplicado | Error mostrado en inglés |
| Sin rubros | “Elige al menos un rubro” |
| Nombres vacíos | “Ingresa tus nombres” |
| Búsqueda vacía | HTTP 200 y lista vacía |
| Búsqueda positiva | React mostró “Demo Gasfitero ficticio”, Gasfitería, 0 km |

El idioma del error de DNI queda pendiente de revisión de Renzo (T1.2);
`LANGUAGE_CODE='en-us'` y el validador de unicidad usa mensajes por defecto.
No afecta el resultado automatizado de rechazo con HTTP 400.

## T3.1/T3.2: verificación y navegador pendiente

Los 11 tests de servicios pasan: filtros de rubro/disponibilidad/cobertura,
distancia, límite inclusivo, datos públicos, extremos/errores de parámetros y
lista vacía. No hubo cambios nuevos a servicios ni a ResultadosPrestadores;
la pantalla sigue consultando rubros y búsqueda reales, manteniendo orden recibido,
mensajes de carga/vacío/error y cancelación de peticiones anteriores.

No hay herramienta de navegador disponible y Playwright/Puppeteer no están
instalados. No se verificaron visualmente esta sesión. Pendientes manuales:

1. Con Django y Vite, abrir Resultados, elegir Gasfitería y ubicación; verificar
   las tarjetas y la lista vacía con datos de prueba, sin alterar registros reales.
2. Desconectar backend: comprobar error de carga de rubros, botón Reintentar y
   recuperación; desconectarlo después de cargar rubros para comprobar error de búsqueda.
3. Sin rubro/ubicación y coordenadas fuera de rango: comprobar mensajes en español.
4. Con red lenta, repetir Buscar/cambiar filtros/salir de pantalla: ninguna
   respuesta vieja debe sustituir los resultados recientes.
5. A 360 y 390 px: comprobar mapa, controles, tarjetas, textos y navegación sin
   desbordamiento horizontal; repetir a tamaño escritorio.
6. Completar la carga de archivos desde UI cuando David conecte el componente,
   incluido estado pendiente y pertenencia al perfil.

## T4.3: revisión de origin/hu-4-solicitud sin integración

Se inspeccionó la referencia local remota, HEAD `eb39859`, solo con git show/log/diff.
No se actualizó por fetch en esta sesión ni se ejecutaron sus pruebas; el README
de esa rama declara 30 aprobadas por su autor, lo cual no es evidencia de ejecución aquí.

Existe Solicitud con prestador, rubro, contacto, descripción, coordenadas y estados.
La rama registra servicios y su migración/API. Endpoints existentes:

| Etapa | Implementación observada |
|---|---|
| Buscar | Búsqueda HU-3 incorporada en esa rama |
| Solicitar | POST /api/servicios/solicitudes/, valida disponibilidad/rubro/cobertura |
| Recibir | GET /api/servicios/solicitudes/recibidas/, sesión del prestador |
| Aceptar/rechazar | POST .../<id>/aceptar/ o rechazar/, propietario y pendiente, segunda respuesta 409 |
| Ver estado cliente | GET .../<id>/, incluye estado y fecha de respuesta |

Hay 12 tests de Solicitud, con aceptación/rechazo, permisos y rechazo de segundo
cambio. Se podría avanzar T4.3 de API en una copia aislada de esa rama con BD
temporal, añadiendo caso que use el ID devuelto por búsqueda antes de enviar.
No hay prueba existente que encadene búsqueda → envío → respuesta de extremo a extremo.

**Bloqueos del flujo completo:** T4.1 de Renzo existe en rama remota pero aún no
en la rama actual/main revisado. T4.2 de David (`1483eab`) añade un cargador de
certificaciones; no existe botón Solicitar en las tarjetas, formulario de servicio,
bandeja aceptar/rechazar ni consulta/actualización del estado en React.
Hace falta conectar esas pantallas a los endpoints reales y disponer de sesión
de prestador (para API puede usarse Basic Auth con cuenta de prueba; no exige
implementar HU-5 ahora). No se confunde carga de certificación con solicitud de servicio.

## PR y estado de cierre

No se pudo consultar el PR hu-3-busqueda → main: gh no está disponible;
GitHub/API pública no accesibles desde las herramientas usadas (fallos de acceso/red).
No se conoce su existencia, enlace ni estado. Verificar en GitHub autenticado
filtrando base main y head hu-3-busqueda. No se creó ningún PR ni se fusionó ninguno.

T2.3 sigue parcial por asociación HU-2 y verificación UI pendiente.
T3.1/T3.2 siguen pendientes de revisión/PR y comprobaciones visuales;
HU-3 no completa por integración/verificación T3.3/T3.4. T4.3 no completa.
No se añadieron horas trabajadas o terminadas ni se recalculó el burndown.
