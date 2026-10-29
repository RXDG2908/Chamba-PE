# Avances del grupo — Chamba PE

> **Regla del equipo:** este archivo se actualiza **siempre**, al terminar cada tarea o al cierre del día de trabajo, antes de dar la tarea por terminada. Ver [PLAN_EQUIPO.md](PLAN_EQUIPO.md#3-reglas-de-reparto).
> Sirve de base para el cuadro de avances y el informe del Sprint Review.

**Última actualización:** 21/10/2026 · **Sprint 1:** ✅ cerrado el 14/10/2026 (01/10 – 14/10) · **Sprint 2:** 🟢 en curso (15/10 – 28/10) · **Meta:** el prestador se registra y el cliente lo encuentra y le envía una solicitud.

Estados: ✅ terminada · 🟡 en curso / parcial · ⚪ sin iniciar · 🔴 bloqueada

---

## 1. Resumen del Sprint 1 (cerrado)

| Indicador | Valor |
|---|---|
| Horas comprometidas | 30 h |
| Horas terminadas (tareas ✅) | 26 h (87 %) |
| Horas pendientes al cierre | 4 h |
| Tareas terminadas | 11 de 13 |
| Tareas que pasan al Sprint 2 | 2 (T2.2 y T3.4, de David). Terminadas el 18/10 con el PR 9 |
| Historias terminadas | 2 de 4 (HU-1 y HU-4). A HU-2 le faltaba T2.2 y a HU-3, T3.4; ambas se completaron el 18/10 (Sprint 2) |
| Estado de `main` al 15/10 | 49 de 49 pruebas OK, sin migraciones pendientes, lint y build OK; flujo registrar → buscar → solicitar → aceptar o rechazar probado en el navegador |

> Las horas solo cuentan como terminadas cuando la tarea cumple la [definición de terminado](PLAN_EQUIPO.md#8-definición-de-terminado). T2.3 y T4.3 las confirmó Luis el 15/10; el PR 7 se unió el 15/10 a las 00:43 y se cuenta en el cierre.

El detalle de cada verificación está en la bitácora (sección 6) y en los informes [docs/pruebas-T2.3.md](docs/pruebas-T2.3.md), [docs/pruebas-T4.3.md](docs/pruebas-T4.3.md) y [frontend/README.md](frontend/README.md).

## 2. Cuadro de avance por tarea

| Tarea | HU | Descripción | Responsable | Horas | Estado | Rama / PR | Fecha | Notas |
|:---:|:---:|---|---|:---:|:---:|---|:---:|---|
| T1.1 | HU-1 | Modelo de datos del prestador | Renzo León | 2 | ✅ | PR 1 unido a `main` | 05/10 | App `prestadores`: `Rubro` y `Prestador` (DNI, rubros, cobertura centro + radio km, estado "Pendiente de verificación"). Migración 0001. |
| T1.2 | HU-1 | API de registro de prestadores | Renzo León | 3 | ✅ | `main` | 05/10 | `POST /api/prestadores/registro/` crea usuario y prestador; exige DNI (8 dígitos, único), ≥ 1 rubro y cobertura; queda "Pendiente de verificación". `GET /api/prestadores/rubros/`. 7 pruebas pasan. Se agregó Django REST Framework (`requirements.txt`). |
| T1.3 | HU-1 | Formulario de registro (web y móvil) | Renzo León | 2 | ✅ | `main` | 05/10 | Formulario React (`frontend/src/RegistroPrestador.jsx`): datos, cuenta, rubros desde la API y mapa Leaflet con círculo de cobertura y radio. Valida en el cliente y muestra los errores del servidor. Diseño responsive (sirve en navegador móvil); la app móvil nativa sigue por definir. Probado de punta a punta contra la API. |
| T4.1 | HU-4 | Flujo de solicitud: envío y aceptación/rechazo | Renzo León | 3 | ✅ | PR 4 unido a `main` | 12/10 | Implementada en `servicios`: modelo `Solicitud` (Pendiente → Aceptada / Rechazada) y migración 0001. `POST /api/servicios/solicitudes/` valida que el prestador esté disponible, ofrezca el rubro y cubra la ubicación del cliente. El prestador ve su bandeja (`GET .../recibidas/`) y acepta o rechaza (`POST .../<id>/aceptar/` y `.../rechazar/`), solo si es el dueño y si sigue Pendiente. El cliente ve el cambio en `GET .../<id>/`. 12 pruebas nuevas; `prestadores` y `servicios`: 30 OK. |
| T3.1 | HU-3 | Búsqueda por rubro, radio y disponibilidad | Luis Abad | 3 | ✅ | PR 3 unido a `main` | 12/10 | Implementada y verificada: `GET /api/prestadores/buscar/`, parámetros validados en español, Haversine reutilizable, cobertura inclusiva y datos públicos. 11 pruebas nuevas; suite completa: 18 OK, repetida tras incorporar `ae51360` del equipo. Luis confirmó HTTP 200 vacío y positivo. T3.3 agregó el orden ascendente; hay una sola ruta, en `prestadores/urls.py`. [Documentación](docs/API_BUSQUEDA.md). |
| T3.2 | HU-3 | Pantalla de resultados con lista de prestadores | Luis Abad | 4 | ✅ | PR 3 unido a `main`; comprobaciones en `pruebas-cierre-sprint1` | 14/10 | Implementada con rubros API, mapa/coordenadas, tarjetas y cancelación; conserva orden recibido. Sobre `7e671cd`: asistente ejecutó lint/build OK, 11 tests búsqueda OK y HTTP positivo/vacío/error/recuperación. Luis comprobó móvil simulado/escritorio sin cortes, Demo a 0 km, vacío al cambiar a Electricidad, error con Django detenido y recuperación sin recargar. Network: rubro 1 anterior cancelado, rubro 2 final 200, sin resultados antiguos. Incorporado después `e20c533`, sin repetir pruebas. Pendientes cambios rápidos de ubicación y otros casos específicos; [evidencia y pasos](frontend/README.md). Estado ✅ conservado del cuadro de Renzo; no se suman horas. |
| T2.3 | HU-1/2 | Pruebas de registro y carga | Luis Abad | 1 | ✅ | PR 7 unido a `main` | 15/10 | Pruebas de registro y carga en `main`, incluida la asociación del certificado y el rechazo sin prestador. Luis confirmó el cierre el 15/10. [Evidencia](docs/pruebas-T2.3.md). |
| T4.3 | HU-4 | Pruebas buscar → solicitar → aceptar | Luis Abad | 2 | ✅ | PR 7 unido a `main` | 15/10 | Flujo probado desde React: envío, Aceptada, Rechazada, validaciones sin crear solicitudes y vista en escritorio y móvil. Luis confirmó el cierre el 15/10. [Evidencia](docs/pruebas-T4.3.md). |
| T2.1 | HU-2 | API de carga de certificaciones (PDF/JPG ≤ 5 MB) | David Valcarcel | 2 | ✅ | PR 6 unido a `main`; reforzada en PR 9 | 14/10 | Exige `prestador` (400 si falta), guarda la certificación vinculada al prestador una sola vez mediante el `FileField` y responde con su nombre y URL. Revisado por Luis. PR 9 (18/10): `prestador` no numérico da 400 en vez de 500; extensión, MIME y primeros bytes (`%PDF-` / `FF D8 FF`) deben coincidir, así un TXT renombrado a `.pdf` se rechaza. |
| T2.2 | HU-2 | Componente de carga de archivos | David Valcarcel | 2 | ✅ | PR 9 unido a `main` | 18/10 | Pasó del Sprint 1. Ruta `/certificaciones` con enlace «Cargar certificado» en el menú (React Router); el registro exitoso muestra el ID y enlaza a la carga. Envía el `prestador`, usa su `UploadCertificado.css` y Vite redirige `/media/` a Django para abrir el archivo subido. |
| T3.3 | HU-3 | Ordenar resultados por distancia | David Valcarcel | 2 | ✅ | PR 6 unido a `main` | 14/10 | `GET /api/prestadores/buscar/` ordena por distancia ascendente, con prueba. Se quitó la ruta duplicada en `servicios`. El endpoint aparte `certificaciones/prestadores/cercanos/` sigue existiendo y ya no se usa. |
| T3.4 | HU-3 | Datos de prueba y tiempo de respuesta (< 3 s) | David Valcarcel | 2 | ✅ | PR 9 unido a `main` | 18/10 | Pasó del Sprint 1. `python manage.py medir_busqueda` crea 500 prestadores ficticios (se descartan al terminar), mide la búsqueda real `GET /api/prestadores/buscar/` con la cabecera `X-Execution-Time-Ms` y escribe `evidencia_rendimiento.txt` (máx. 32,5 ms). 3 pruebas nuevas. Se retiraron los `test_reporte.py` que hacían fallar `manage.py test`. |
| T9.1 | HU-9 | Modelo y API en Django para revisar documentos | David Valcarcel | 2 | 🟡 | PR 11 abierto (`feature/t9.1-revision-documentos`, con `main` y T5.1 integrados) | 21/10 | Sprint 2. App `administracion`: modelo `RevisionDocumento` (pendiente / aprobado / rechazado, comentario, revisor y fecha), migraciones 0001 y 0002 (crea revisiones para las certificaciones ya subidas). Cada certificación nueva entra sola como pendiente. `/api/administracion/revisiones/`: listar (`?estado=`), consultar, crear y `PATCH` para aprobar o rechazar; solo administradores. Rechazar exige comentario (400). 16 pruebas nuevas; suite completa: 74 OK. 21/10: usa `EsAdministrador` de T5.1 en vez de `IsAdminUser` (entra el rol `administrador` o `is_staff`), según la revisión de Renzo; 2 pruebas nuevas. Con T5.1 integrada: 108 de 108 OK. Se une después del PR 13. |
| T9.2 | HU-9 | Pantalla web (React) para aprobar o rechazar | David Valcarcel | 2 | 🟡 | PR 11 abierto (va junto con T9.1) | 19/10 | Ruta `/administracion/revisiones` («Revisión de documentos» en el menú): lista los pendientes con prestador, DNI, tipo y formato, y enlace al archivo por `/media/`. «Aprobar» envía el `PATCH` directo; «Rechazar» pide un motivo obligatorio. La tarjeta muestra el resultado sin recargar. Al decidir, el prestador queda Verificado si tiene al menos un documento aprobado (Rechazado si todos se rechazaron). La búsqueda y el envío de solicitudes ahora exigen Verificado. Sesión: login de `/admin/` por el proxy de Vite. 9 pruebas nuevas; suite: 83 OK, ejecutor T4.3 OK, lint y build OK. Probado por HTTP a través del proxy (login, lista, archivo, CSRF, aprobar); falta revisarlo en el navegador. |
| T5.3 | HU-5 | Pruebas de autenticación y acceso por rol | David Valcarcel | 1 | 🟡 | `feature/t5.3-pruebas-acceso-rol` (incluye T5.1 y el PR 11 actualizado), PR pendiente | 21/10 | `usuarios/test_acceso_por_rol.py`: 31 pruebas con una cuenta por rol (cliente, prestador, administrador `is_staff` y superusuario) que inicia sesión por `/api/usuarios/login/`. Cubre el rol en `login` y `yo`; `/api/administracion/revisiones/` (admin por `is_staff` o por rol 200/201, cliente, prestador y anónimo 403); bandeja y aceptar/rechazar solo para el prestador dueño (otro prestador 404); búsqueda, rubros y envío de solicitudes públicos; y sin sesión válida (sin credenciales, cookie inventada, Basic, token, sesión cerrada, cuenta desactivada, sin rol de administrador, sin CSRF) 403, nunca 401, porque la API solo usa la sesión de Django. 3 pruebas `expectedFailure` documentan el impedimento 16 (carga de certificados sin sesión). Suite: 139 OK (3 fallas esperadas). Se une después del PR 13 (T5.1) y del PR 11. |
| T4.2 | HU-4 | Pantallas de solicitud y cambio de estado | David Valcarcel | 2 | ✅ | PR 6 unido a `main` | 14/10 | Cada tarjeta de resultados permite solicitar el servicio (`POST /api/servicios/solicitudes/`) y consulta el estado cada 5 s hasta Aceptada o Rechazada. PR 7 agregó validaciones de nombre, teléfono de 9 dígitos y descripción. Probado en el navegador el 15/10. |

## 3. Avance por historia de usuario

| HU | Historia | SP | Horas | Tareas | Estado | Pruebas a cargo |
|:---:|---|:---:|:---:|---|:---:|---|
| HU-1 | Registro del prestador | 3 | 7 h | T1.1 ✅ · T1.2 ✅ · T1.3 ✅ | ✅ 7 de 7 h | Luis (T2.3) |
| HU-2 | Carga de certificaciones | 2 | 5 h | T2.1 ✅ · T2.2 ✅ · T2.3 ✅ | ✅ 5 de 5 h (T2.2 el 18/10) | Luis (T2.3) |
| HU-3 | Búsqueda de prestadores | 5 | 11 h | T3.1 ✅ · T3.2 ✅ · T3.3 ✅ · T3.4 ✅ | ✅ 11 de 11 h (T3.4 el 18/10) | David (T3.4) |
| HU-4 | Solicitud y aceptación | 3 | 7 h | T4.1 ✅ · T4.2 ✅ · T4.3 ✅ | ✅ 7 de 7 h | Luis (T4.3) |

## 4. Avance por integrante

| Integrante | Rol | Horas plan | Horas terminadas | Tareas terminadas | Tareas en curso |
|---|---|:---:|:---:|:---:|---|
| Renzo León | Scrum Master / Desarrollador | 10 h | 10 h | T1.1, T1.2, T1.3, T4.1 | — |
| Luis Abad | Development Team | 10 h | 10 h | T3.1, T3.2, T2.3, T4.3 | — |
| David Valcarcel | Product Owner / Desarrollador | 10 h | 6 h | T2.1, T3.3, T4.2 | — (T2.2 y T3.4 terminadas el 18/10, en el Sprint 2) |
| **Equipo** | | **30 h** | **26 h** | **11** | **0** |

## 5. Burndown (horas pendientes)

| Día | Fecha | Ideal (h) | Real (h) |
|:---:|:---:|:---:|:---:|
| Inicio | 01/10 | 30 | 30 |
| 1 | 01/10 | 27 | 30 |
| 2 | 02/10 | 24 | 30 |
| 3 | 05/10 | 21 | 23 |
| 4 | 06/10 | 18 | 23 |
| 5 | 07/10 | 15 | 23 |
| 6 | 08/10 | 12 | 23 |
| 7 | 09/10 | 9 | 23 |
| 8 | 12/10 | 6 | 13 |
| 9 | 13/10 | 3 | 13 |
| 10 | 14/10 | 0 | 4 |

## 6. Bitácora de avances

Una línea por entrega, la más reciente arriba.

| Fecha | Quién | Qué se hizo | Tarea |
|:---:|---|---|:---:|

| 21/10 | David Valcarcel | T5.3: se trajo el PR 11 actualizado (`main`, T5.1 y `EsAdministrador`). La prueba «quitar `is_staff` quita el acceso» pasa a «quitar el rol y `is_staff`», porque con `EsAdministrador` el rol del perfil también da acceso; nueva prueba de un administrador por rol que entra por el login. 139 de 139 OK (3 fallas esperadas del impedimento 16). | T5.3 |
| 21/10 | David Valcarcel | PR 11 (T9.1 y T9.2) actualizado con las observaciones de Renzo: integra `main` (PR 10, se conservó la plantilla nueva del Sprint 2 y los impedimentos de T9 pasan a ser el 18 y el 19) y T5.1 (PR 13; choque solo en `INSTALLED_APPS` y `urlpatterns`), y la revisión usa `EsAdministrador`. 2 pruebas nuevas (rol `administrador` sin `is_staff` entra; prestador 403). 108 de 108 OK, sin migraciones pendientes. | T9.1/T9.2 |

| 28/10 | Renzo León | T6.2 en la rama `feature/t6.2-interruptor-disponibilidad` (sale de T5.2): pantalla `/disponibilidad` con interruptor Activo/Inactivo, solo para el prestador; sin sesión manda a iniciar sesión y vuelve después, con otro rol regresa al inicio. Avisa si el perfil aún no está verificado. Al iniciar sesión, el prestador entra directo a su disponibilidad. Probado en el navegador (escritorio y 375 px): activar, desactivar y que aparezca o no en la búsqueda. Lint y build OK. | T6.2 |
| 28/10 | Renzo León | Burndown del 21/10 al 27/10: 30 h pendientes cada día, porque ningún PR del Sprint 2 entró a `main` (#11, #13, #14 y #15 siguen abiertos sin revisión). | Burndown |
| 28/10 | Renzo León | T5.2 en la rama `feature/t5.2-login-registro` (sale de T6.1): pantallas React de crear cuenta de cliente (`/crear-cuenta`) e iniciar sesión con usuario o correo (`/ingresar`), sesión en la cabecera con el nombre y el rol, y cierre de sesión. `api.js` envía `X-CSRFToken` en cada cambio. Probado en el navegador: registro, cierre, inicio con correo, contraseña incorrecta y sesión al recargar. Lint y build OK. | T5.2 |
| 28/10 | Renzo León | T5.1: agrega `CSRF_TRUSTED_ORIGINS` para el origen de Vite. Al probar T5.2 en el navegador, el proxy de Vite reescribe el Host y Django rechazaba el `Origin` con 403 en registro e inicio de sesión. Es el mismo bloque del PR 11, así que no choca. | T5.1 |
| 21/10 | Renzo León | T6.1 en la rama `feature/t6.1-disponibilidad` (sale de T5.1): `GET` y `PATCH /api/usuarios/disponibilidad/` solo para el prestador con sesión; un inactivo no aparece en la búsqueda. 6 pruebas nuevas; 87 de 87 OK. PR 15 abierto, revisa David; se une después del PR 13. | T6.1 |

| 21/10 | Renzo León | Burndown del 20/10: 30 h pendientes (ideal 18). Ninguna tarea entró a `main`: T5.1 (PR 13) espera la revisión de David, y T9.1 (PR 11) y T7.1 (PR 14) siguen abiertos. Revisó el PR 11 y le dejó observaciones (choques con `main` y con T5.1, cambiar a `EsAdministrador`). | Burndown |
| 20/10 | Renzo León | Unió el PR 10 (planificación del Sprint 2) a `main` e integró `main` en la rama de T5.1. Burndown del 19/10: 30 h pendientes (ideal 21), porque T5.1 sigue en revisión y ninguna tarea entró a `main`. | Planning/T5.1 |
| 20/10 | David Valcarcel | T5.3 en `feature/t5.3-pruebas-acceso-rol`: se unió la rama de T5.1 (conflictos solo en `settings`, `urls` y AVANCES, se conservaron ambos lados) y se agregaron 30 pruebas de acceso por rol con login real. 136 de 136 OK (3 fallas esperadas del impedimento 16), sin migraciones pendientes. Prueba de mutación: con `IsAuthenticated` en vez de `IsAdminUser` en las revisiones fallan 11 pruebas. Falta el PR, después del de T5.1. | T5.3 |
| 19/10 | Renzo León | T5.1 implementada en la rama `feature/t5.1-autenticacion`: app `usuarios` con `Perfil` (rol), rutas `csrf`, `registro`, `login` (usuario o correo), `logout` y `yo`, permisos `EsCliente`, `EsPrestador` y `EsAdministrador`, migración que da perfil a las cuentas existentes, correo único también en el registro del prestador y bandeja de solicitudes con `EsPrestador`. 23 pruebas nuevas; 81 de 81 OK. PR 13 abierto, revisa David. | T5.1 |
| 19/10 | Renzo León | Contrato de T5.1 en `docs/API_AUTENTICACION.md`: rutas `/api/usuarios/` (csrf, registro, login, logout, yo), sesión de Django con `X-CSRFToken` por el proxy de Vite, rol en `usuarios.Perfil` y permisos `EsCliente`, `EsPrestador` y `EsAdministrador`. Falta implementar la API (PR el 20/10). | T5.1 |
| 19/10 | David Valcarcel | T9.2: pantalla `RevisionDocumentos.jsx` para aprobar o rechazar con motivo, regla de prestador Verificado al decidir y filtro de verificados en la búsqueda y en las solicitudes (se actualizaron los datos de prueba de `servicios`, `medir_busqueda`, `preparar_busqueda_local` y el ejecutor T4.3). Proxy de Vite para `/admin/` y `/static/` y `CSRF_TRUSTED_ORIGINS`. 83 de 83 pruebas OK, ejecutor T4.3 OK, lint y build OK; flujo login → lista → aprobar comprobado por HTTP a través del proxy. Falta la revisión en el navegador y el PR. | T9.2 |
| 18/10 | David Valcarcel | T9.1 en `feature/t9.1-revision-documentos`: app `administracion` con el modelo `RevisionDocumento`, sus migraciones y la API `/api/administracion/revisiones/` (listar, consultar, crear, aprobar o rechazar; solo administradores). 16 pruebas nuevas, 74 de 74 OK y sin migraciones pendientes. Falta abrir el PR. | T9.1 |
| 18/10 | Renzo León | Sprint Planning del Sprint 2 en SPRINTS.md: Renzo HU-5 y HU-6 (`usuarios`), Luis HU-7 y HU-8 (`servicios`), David HU-9 (`administracion`) y las pruebas cruzadas; 10 h cada uno. T5.1 va primero (PR el 20/10) porque bloquea T6.1, T5.2, T9.1 y T8.1. | Planning |
| 18/10 | Renzo León | Revisó y unió el PR 9 de David a `main`: 58 de 58 pruebas OK, sin migraciones pendientes, lint y build OK. Cierra T2.2 y T3.4, que pasaron del Sprint 1. | T2.1/T2.2/T3.4 |
| 17/10 | David Valcarcel | PR 9: carga de certificados en la navegación (`/certificaciones`) con el prestador y proxy `/media/`; validación de `prestador` y de la firma real del archivo; `manage.py medir_busqueda` mide la búsqueda real (< 3000 ms). 8 pruebas nuevas. | T2.1/T2.2/T3.4 |
| 15/10 | Luis Abad | Confirmó el cierre de T2.3 y T4.3: probó envío, aceptación y rechazo, validaciones y la vista en escritorio y móvil. Sprint 1 queda en 26 de 30 h. | T2.3/T4.3 |
| 15/10 | Renzo León | Cierre del Sprint 1: verificó `main` con una instalación limpia (49 de 49 pruebas, ejecutor T4.3 con 32 OK, migraciones al día, lint y build OK) y en el navegador (solicitud con teléfono inválido rechazada, solicitud válida creada y Rechazada mostrada sola; móvil sin scroll horizontal). Actualizó AVANCES, SPRINTS, TABLERO y README con 23 de 30 h y el borrador de la retrospectiva. | Cierre |
| 15/10 | Luis Abad | PR 7 unido a `main`: validaciones de nombre, teléfono de 9 dígitos y descripción en React y Django (6 pruebas nuevas) y tarjeta de prestador corregida. Probó en escritorio y a 360/390 px. | T3.2/T4.3 |
| 14/10 | David Valcarcel | PR 6 revisado por Luis y unido a `main`: certificado vinculado al prestador, orden por distancia en la búsqueda, pantalla de solicitud, guardado único del archivo, migración regenerada con Django 6.1 y ruta duplicada eliminada. | T2.1/T3.3/T4.2 |
| 15/10 | Luis / asistente | Luis reportó tarjeta correcta en escritorio y 360/390 px, nombre con números y teléfono con letras/8/10 dígitos sin crear solicitudes, y datos válidos con tilde y números en descripción que crean pendiente. Asistente documentó únicamente esos resultados en un commit nuevo; sin repetir tests, sumar horas o cerrar Sprint 1, T2.2 ni T3.4. | T3.2/T4.3 |
| 15/10 | Luis / asistente | Luis reportó 43 pruebas OK y aceptación/rechazo desde React con respuesta por API sobre f5565c0. Asistente corrigió tarjeta y validaciones, ejecutó 49 pruebas OK y lint/build OK; documentó evidencia y pendientes sin nuevas horas ni cierre. Commits de código fc756df y baa41e5; revisión visual posterior pendiente. | T3.2/T2.3/T4.3 |
| 14/10 | Asistente | Inspeccionó procesos Python y el puerto 8000 (sin servidor activo); identificó `core/urls.py` cargado desde la raíz actual, eliminó únicamente `core/__pycache__` y `servicios/__pycache__` y verificó sin generar bytecode que `/api/servicios/prestadores/buscar/` resuelve a `BuscarPrestadoresView`. Sin horas ni cambios de burndown, datos, commit o PR. | T3.3 |
| 14/10 | Asistente | Verificó el include principal y la resolución de `/api/servicios/prestadores/buscar/` a `BuscarPrestadoresView`; agregó regresiones para la URL exacta, alias anterior y orden ascendente por distancia (11 pruebas de búsqueda OK) y actualizó la documentación. Sin cambios de datos, horas, commit ni PR; T3.3 continúa parcial hasta revisión e integración. | T3.3 |
| 14/10 | Luis Abad | Registró sus pruebas manuales sobre `7e671cd`: móvil simulado/escritorio sin cortes, Demo Gasfitero ficticio a 0 km en Gasfitería/Lima, cambio a Electricidad con tarjeta anterior retirada y mensaje vacío; Django detenido con error de comunicación y reiniciado con recuperación al pulsar Buscar sin recargar. Búsquedas Gasfitería→Electricidad: Network con solicitudes rubro 1 canceladas y rubro 2 final 200, estado vacío sin reaparecer Demo. Evidencia manual separada de las pruebas del asistente y de Renzo; no repetida en `e20c533`. | T3.2 |
| 14/10 | Luis Abad / asistente | Conservó la comprobación automatizada previa en `7e671cd`: 11 pruebas búsqueda OK, lint/build frontend OK; proxy 200 positivo/vacío, 400 latitud inválida, 502→200 al detener/reiniciar Django. Instalación local de django-cors-headers 4.9.0 en venv existente; ficticio id 2 reutilizado dos veces, SHA-256 BD sin cambios. Resguardó los tres cambios en stash e incorporó origin/main `e20c533` por fast-forward; combinó documentación sin perder avances de Renzo y mantuvo su `django-cors-headers>=4.4`, retirando el pin redundante. Sin nuevas pruebas sobre main, migraciones, cambios a BD, horas nuevas, commits de trabajo, push ni PR. | T3.2 |
| 14/10 | Renzo León | Incorporó a `hu-4-solicitud` el commit de David del 13/10 y agregó `django-cors-headers` a `requirements.txt`, que faltaba y hacía fallar `manage.py` en `main`. Pruebas: 40 (39 OK y la falla intencional de T2.3), ejecutor T4.3 con 32 OK, lint y build OK. | T4.1 |
| 13/10 | David Valcarcel | Subió a `main` (sin PR) la configuración CORS y del proxy para la carga de certificaciones; corrigió el `err` sin usar del lint. | T4.2 |
| 12/10 | Renzo León | Revisó y probó la búsqueda de Luis combinada con la T4.1: 40 pruebas (39 OK y la falla intencional de T2.3), ejecutor T4.3 con 32 OK, build OK y prueba en navegador de registro, búsqueda, flujo de solicitud por el proxy y vista móvil. Unió el PR 3 y el PR 4 a `main`. | T3.1/T3.2/T4.1 |
| 12/10 | Renzo León | T4.1 en la rama `hu-4-solicitud` (creada desde `hu-3-busqueda` con `main` incorporado): modelo `Solicitud` y migración, envío con validación de disponibilidad, rubro y cobertura, bandeja del prestador, aceptar/rechazar solo por el dueño y solo una vez (409 si ya se respondió) y consulta del estado para el cliente. 12 pruebas nuevas; `prestadores` y `servicios`: 30 OK. AVANCES actualizado con lo subido por David entre el 05/10 y el 10/10. | T4.1 |
| 12/10 | Luis Abad | Ejecutó T4.3 en copia temporal de HEAD con API de Renzo `eb39859`: 32 pruebas OK (30 existentes + 2 nuevas encadenando búsqueda/envío/aceptación o rechazo/consulta cliente), Basic Auth real y BD en memoria. Bandeja y cambios persistidos correctos; segunda respuesta 409. Copia eliminada; rama, HEAD, T2.3, BD y media conservados. Añadido ejecutor reproducible y [evidencia](docs/pruebas-T4.3.md). React pendiente por T4.2 e integración T4.1; sin commits, push, fusiones ni horas nuevas. | T4.3 |
| 12/10 | Luis Abad | Revisión tras merge existente 5d2e1db: búsqueda 11 tests OK; suite ampliada 28, 27 OK/1 FAIL por asociación HU-2. Build OK; lint falla en UploadCertificado de David. Añadidos casos T2.3 con BD/media temporales y documentación de manuales previas, incidencias y pendientes. Sin horas nuevas ni tareas completas. [Informe](docs/pruebas-T2.3.md). | T3.1/T3.2/T2.3 |
| 09/10 | David Valcarcel | Subió a `main` la pantalla de envío de certificación con estado (`frontend/src/UploadCertificado.jsx`). | T4.2 |
| 07/10 | David Valcarcel | Script de medición del tiempo de respuesta y `evidencia_rendimiento.txt`. | T3.4 |
| 06/10 | David Valcarcel | Endpoint `prestadores/cercanos/` que ordena por distancia (Haversine) en `certificaciones`. | T3.3 |
| 05/10 | David Valcarcel | Componente de carga de archivos con estado "Pendiente de verificación" (`src/UploadCertificado.jsx`). | T2.2 |
| 06/10 | Luis Abad | Preparación para revisión de T3.1/T3.2 en `hu-3-busqueda`: incorporado por avance rápido `ae51360` del equipo sin modificar sus archivos. Suite backend repetida: 18 OK en base de pruebas; lint y build repetidos: código 0. Registrada confirmación manual de Luis de HTTP 200 vacío/positivo y tarjeta ficticia a 0 km. Documentación revisada; commit pendiente de aprobación del mensaje y PR pendiente. HU-3 sigue parcial, sin sumar horas terminadas. | T3.1/T3.2 |
| 06/10 | Luis Abad | Conectó Resultados a Django: selector de rubros, mapa Leaflet y coordenadas exactas, botón Buscar, tarjetas con datos de la API sin calificaciones, estados en español y cancelación de búsquedas anteriores. Conserva registro y navegación, orden del backend, cambios T3.1 y base local. Lint y build en `frontend/`: código 0. Consultas vía proxy Vite: 200 y ficticio a 0 km. Pendientes comprobaciones visuales documentadas y PR; HU-3 parcial, T3.3/T3.4 de David pendientes. Sin commit ni push. | T3.2 |
| 06/10 | Luis Abad | Prueba positiva local de T3.1: comando idempotente `preparar_busqueda_local` creó un único prestador ficticio (id 2), Gasfitería (id 1), disponible, centro -12.046374/-77.042793 y radio 5 km. Segunda ejecución reutilizó el registro sin cambios. Consulta HTTP al servidor local: 200, prestador ficticio a 0.0 km. Sin alterar registros previos ni filtros; no completa T3.4 de David. | T3.1 |
| 06/10 | Luis Abad | Implementó T3.1 en `servicios`, reutilizando `Prestador` y `Rubro`: validación en español, filtros de rubro/disponibilidad/cobertura, distancia Haversine y salida pública. Ejecutó `.\venv\Scripts\python.exe manage.py test --noinput`: 18 pruebas OK (7 existentes + 11 nuevas), con base de pruebas creada y destruida. Documentó endpoint y ejemplos. Sin cambios al frontend, commit ni push; pendiente de PR. T3.2 y HU-3 siguen parciales. | T3.1 |
| 06/10 | Renzo León | Maquetas de las 21 historias de usuario (`docs/renders/historias-usuario.html`) para la presentación; README corregido (tenía un conflicto de merge sin resolver) y ampliado con backlog, estructura y cómo ejecutar. | — |
| 05/10 | Renzo León | Formulario de registro con rubros y mapa de cobertura (Leaflet); proxy `/api` a Django en Vite. | T1.3 |
| 05/10 | Renzo León | Migración 0002 con 10 rubros iniciales (Gasfitería, Electricidad, Carpintería, etc.). | T1.1 |
| 05/10 | Renzo León | API de registro: `POST /api/prestadores/registro/` y `GET /api/prestadores/rubros/`, con validaciones y 7 pruebas. | T1.2 |
| 05/10 | Renzo León | PR 1 unido a `main`. Modelo `Prestador`/`Rubro` con cobertura y estado; migración y admin; app registrada en `settings`. | T1.1 |
| 04/10 | Luis Abad | Documentó el avance del frontend en `frontend/README.md`. | T3.2 |
| 04/10 | Luis Abad | Frontend React + Vite con el listado de prestadores en tarjetas. | T3.2 |
| 03/10 | David Valcarcel | Resolvió conflicto en `.gitignore`. | — |
| 03/10 | David Valcarcel | API `upload/` de certificaciones: valida PDF/JPG y 5 MB. | T2.1 |
| 01/10 | Renzo León | Planificación del Sprint 1, reparto de tareas y documentación (README, PLAN_EQUIPO, SPRINTS, Lab 07). | — |
| 01/10 | Renzo León | Contexto inicial del proyecto y Django como backend. | — |

## 7. Impedimentos y riesgos

| # | Fecha | Impedimento / riesgo | Afecta a | Responsable | Estado |
|:---:|:---:|---|---|---|:---:|
| 13 | 14/10 | Se detectó que la migración anterior de certificaciones declaraba `id` como `AutoField`, mientras el modelo usaba `BigAutoField`. Resuelto al regenerar la migración limpia. | Migraciones de certificaciones | Equipo | ✅ |
| 12 | 14/10 | Luis cubrió visuales móvil simulado/escritorio, error/recuperación de búsqueda y concurrencia por rubro sobre `7e671cd`. Quedan cambios rápidos de ubicación, salida/retorno durante espera, reintento de rubros, validaciones visibles, regresión de registro y teléfono físico. No hay repetición sobre `e20c533`; el asistente no dispone de navegador. [Detalle](frontend/README.md). | T3.2, seguimiento de comprobaciones | Luis | 🟡 |
| 1 | 05/10 | T1.1 llegaba con 3 días de retraso y bloqueaba la búsqueda y las certificaciones. Resuelto: PR 1 unido a `main` el 05/10. | HU-2, HU-3 | Renzo | ✅ |
| 2 | 05/10 | La carga de certificaciones no se asociaba al prestador. Resuelto: PR 6 unido a `main` el 14/10. | HU-2, T2.3 | David (T2.1) | ✅ |
| 3 | 06/10 | T3.1 y T3.2 esperaban revisión. Resuelto: PR 3 revisado, probado y unido a `main` el 12/10. | HU-3 | Luis | ✅ |
| 4 | 05/10 | T4.1 dependía de HU-3. Resuelto: PR 4 unido a `main` el 12/10, después del PR 3. | HU-4 | Renzo | ✅ |
| 5 | 05/10 | App móvil "por definir" en el stack: el formulario es web responsive, no una app nativa. | HU-1 | Equipo | ⚪ |
| 6 | 12/10 | T2.2, T3.3, T3.4 y T4.2 se subieron directo a `main`, sin PR. T3.3 y T4.2 se rehicieron y entraron por el PR 6; T2.2 y T3.4, por el PR 9 el 18/10. | HU-2, HU-3 | David | ✅ |
| 7 | 12/10 | `certificaciones/test_reporte.py` no es una prueba de Django: al correr `manage.py test` da error (falta `requests`) y reescribe `evidencia_rendimiento.txt`. Resuelto en el PR 9: se retiró y la medición pasó a `manage.py medir_busqueda` con pruebas. | Todas | David | ✅ |
| 8 | 12/10 | El lint fallaba por `err` sin usar en `UploadCertificado.jsx`. Resuelto por David el 13/10. El CSS ya existe y el componente está en la navegación (`/certificaciones`) desde el PR 9. | HU-2 | David (T2.2) | ✅ |
| 9 | 12/10 | El flujo desde React dependía de la pantalla de T4.2. Resuelto: PR 6 la unió a `main` y se probó en el navegador. | HU-4, T4.3 | David (T4.2) | ✅ |
| 10 | 12/10 | Riesgo de cerrar el sprint sin HU-2, HU-3 ni HU-4. Se cerró con 26 de 30 h; pasan 4 h al Sprint 2 (T2.2 y T3.4). | Sprint 1 | Equipo | ✅ |
| 11 | 14/10 | El commit de CORS del 13/10 agregó `corsheaders` a la configuración sin sumarlo a `requirements.txt`: con una instalación nueva, `main` no arranca. Corregido en el PR 4. Además, la vista de carga pone cabeceras CORS a mano aunque ya se usa `django-cors-headers`, y el proxy de Vite ya evita CORS en desarrollo. | Todas | David / Renzo | 🟡 |
| 15 | 17/10 | Cambiar el estado de una solicitud desde el admin de Django deja `respondida` vacía y permite saltarse la regla de una sola respuesta. | T4.1/T4.2 | David | 🟡 |
| 16 | 18/10 | La carga de certificados no pide sesión: cualquiera puede subir un archivo con el ID de otro prestador. Se corrige con la autenticación de HU-5. Desde el 20/10, 3 pruebas `expectedFailure` de T5.3 (cliente, anónimo y otro prestador deberían recibir 403) lo documentan; al corregir la vista pasan y hay que quitarles el decorador. Hoy no se corrige porque el formulario de carga (T2.2) aún no tiene inicio de sesión (T5.2). | HU-2, HU-5 | Equipo | 🟡 |
| 17 | 18/10 | El Sprint 2 se planificó el 18/10: el 15/10 y el 16/10 pasaron sin horas. Quedan 8 días hábiles para 30 h (1,25 h diarias por integrante). Si en el Daily del 23/10 quedan más de 15 h, el Product Owner decide qué pasa al Sprint 3. | Sprint 2 | Equipo | 🟡 |
| 18 | 18/10 | La API de revisión (T9.1) exige un administrador, pero aún no hay pantalla de inicio de sesión (T5.2). Desde el 21/10 usa `EsAdministrador` (T5.1), así que también entra el rol `administrador` por `/api/usuarios/login/`. Mientras tanto, la pantalla de T9.2 solo funciona con la sesión del admin de Django (`/admin/`). Mitigado el 19/10: el login de `/admin/` pasa por el proxy de Vite y la pantalla enlaza a él; con HU-5 se reemplaza por el login propio. | T9.2, HU-5 | David / Equipo | 🟡 |
| 19 | 19/10 | Desde T9.2, la búsqueda solo muestra prestadores Verificado. Los prestadores que hoy están en Pendiente dejan de aparecer hasta que un administrador apruebe uno de sus documentos. Hay que avisar al equipo antes de las pruebas manuales. | HU-3, HU-4 | David | 🟡 |
| 14 | 15/10 | El PR 4 y el PR 7 se unieron sin revisión de otro integrante. Acción: activar la protección de `main` en GitHub para exigir 1 aprobación. | Proceso | Renzo | 🟡 |

## 8. Decisiones técnicas

| Fecha | Decisión | Motivo |
|:---:|---|---|
| 19/10 | El prestador queda Verificado si tiene al menos un documento aprobado; sigue Pendiente mientras haya documentos por revisar y pasa a Rechazado si todos se rechazaron. La búsqueda y el envío de solicitudes exigen Verificado. | Criterio de HU-9: «al aprobar, el estado cambia a Verificado y el prestador ya puede recibir solicitudes». |
| 19/10 | Hasta HU-5, el panel usa la sesión del admin de Django: Vite redirige `/admin/` y `/static/` y Django confía en el origen de Vite para el CSRF. | Sin inicio de sesión propio, es la forma de exigir `is_staff` sin guardar contraseñas en el frontend. |
| 18/10 | La revisión de documentos vive en una tabla aparte (`administracion.RevisionDocumento`, 1:1 con la certificación), no en campos nuevos de `CertificacionPrestador`. Al decidir, se copia el estado a la certificación. | La app `administracion` es de David (HU-9, HU-13) y guarda quién revisó y cuándo sin tocar el modelo de HU-2. |
| 12/10 | La `Solicitud` guarda nombre y teléfono del cliente en vez de enlazar a una cuenta. | Todavía no hay inicio de sesión de clientes (HU-5, Sprint 2); entonces se agrega la relación con el usuario. |
| 12/10 | Aceptar, rechazar y la bandeja exigen sesión del prestador dueño; enviar y consultar el estado no piden sesión. | Solo el prestador puede responder sus solicitudes; el cliente aún no tiene cuenta. La consulta del cliente no muestra su teléfono. |
| 12/10 | La solicitud solo se responde una vez; responder de nuevo devuelve 409. | Evita que un prestador cambie de Aceptada a Rechazada después de confirmar. |
| 14/10 | La búsqueda se expone solo en `/api/prestadores/buscar/`; se elimina la ruta duplicada desde `servicios`. | Una única ruta evita mantener dos URLs para la misma vista. |
| 06/10 | Resultados usa el proxy `/api` existente y Leaflet; cancela búsquedas al cambiar filtros, repetir consulta o salir de la pantalla y consume el orden recibido del backend. | Evita datos desactualizados y permite que T3.3 ordene los resultados sin lógica duplicada en el frontend. |
| 06/10 | Búsqueda en `servicios` con Haversine reutilizable y radio inclusivo; respuesta pública sin calificaciones. | Respeta T3.1 y la organización del backend; T3.3 añade orden ascendente y T3.4 debe medir la búsqueda. Sin migraciones ni cambios a registros locales. |
| 05/10 | Mapa con Leaflet + OpenStreetMap; en desarrollo el frontend usa un proxy `/api` hacia Django (puerto 8000). | Sin claves ni costo; evita configurar CORS. |
| 05/10 | Rubros iniciales cargados con una migración de datos. | Todos los entornos arrancan con los mismos rubros. |
| 01/10 | Backend en Django + Django REST Framework. | Definido en el plan del equipo. |
| 05/10 | Se agrega Django REST Framework; el registro crea el usuario y el prestador en una sola operación. | La API necesita un usuario (relación 1:1); el login con roles llega en HU-5. |
| 05/10 | Cobertura del prestador como centro (latitud, longitud) + radio en km, sin PostGIS. | Más simple; basta para la búsqueda por radio y por distancia. |

## 9. Retrospectiva del Sprint 1

> Borrador del Scrum Master con los datos del sprint; se confirma o ajusta con el equipo.

| ¿Qué salió bien? | ¿Qué mejorar? | Acciones para el Sprint 2 |
|---|---|---|
| HU-1 completa en `main` con PR revisado y pruebas. | Arranque lento: 0 h terminadas los dos primeros días y T1.1, que bloqueaba al resto, llegó con 3 días de retraso. | Las tareas que bloquean a otros (modelos y APIs) se hacen en los primeros 2 días. |
| Búsqueda, API de solicitudes y pantalla de solicitud funcionan de punta a punta y se probaron en el navegador. | Commits directo a `main` sin PR: rompieron `main` (faltaba `django-cors-headers`) y duplicaron lógica. | Activar la protección de `main` en GitHub: solo se une por PR con 1 aprobación. |
| Las pruebas cruzadas funcionaron: Luis probó la API de T4.1 y su prueba de T2.3 detectó el certificado sin vincular. | T4.2 se hizo primero como pantalla de certificados por no leer los criterios de aceptación. | Antes de empezar, leer los criterios en SPRINTS.md y consultar dudas en el Daily. |
| Al cierre, `main` quedó con 49 de 49 pruebas OK, lint y build sin errores. | Integración tardía: casi todo se unió entre el 12/10 y el 14/10, por eso el burndown no bajó una semana. | PR pequeños: abrirlos apenas la tarea funcione y revisarlos en máximo 1 día. |
| | PR 4 y PR 7 se unieron sin revisión de otro integrante. | Si un PR agrega una librería, también actualiza `requirements.txt`; quien revisa instala desde cero. |
| | AVANCES.md no se actualizó a diario y los informes de pruebas salieron muy largos. | AVANCES.md al cierre de cada día, en pocas líneas; el detalle va en `docs/`. |
