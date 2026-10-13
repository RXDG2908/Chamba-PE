# Avances del grupo — Chamba PE

> **Regla del equipo:** este archivo se actualiza **siempre**, al terminar cada tarea o al cierre del día de trabajo, antes de dar la tarea por terminada. Ver [PLAN_EQUIPO.md](PLAN_EQUIPO.md#3-reglas-de-reparto).
> Sirve de base para el cuadro de avances y el informe del Sprint Review.

**Última actualización:** 12/10/2026 · **Sprint actual:** 1 (01/10 – 14/10/2026) · **Meta:** el prestador se registra y el cliente lo encuentra y le envía una solicitud.

Estados: ✅ terminada · 🟡 en curso / parcial · ⚪ sin iniciar · 🔴 bloqueada

---

## 1. Resumen del Sprint 1

| Indicador | Valor |
|---|---|
| Horas comprometidas | 30 h |
| Horas terminadas (tareas ✅) | 7 h |
| Horas pendientes | 23 h |
| Ideal de horas pendientes al 12/10 (día 8) | 6 h |
| Tareas terminadas | 3 de 13 |
| Tareas en curso | 8 (T2.1, T2.2, T3.1, T3.2, T3.3, T3.4, T4.1, T4.2) |
| Historias terminadas | 1 de 4 (HU-1; falta la prueba de Luis, T2.3) |
| Estado general | 🔴 Atrasado: casi todo está avanzado, pero solo HU-1 entró a `main` por PR revisado |

> Las horas solo cuentan como terminadas cuando la tarea cumple la [definición de terminado](PLAN_EQUIPO.md#8-definición-de-terminado).

## 2. Cuadro de avance por tarea

| Tarea | HU | Descripción | Responsable | Horas | Estado | Rama / PR | Fecha | Notas |
|:---:|:---:|---|---|:---:|:---:|---|:---:|---|
| T1.1 | HU-1 | Modelo de datos del prestador | Renzo León | 2 | ✅ | PR 1 unido a `main` | 05/10 | App `prestadores`: `Rubro` y `Prestador` (DNI, rubros, cobertura centro + radio km, estado "Pendiente de verificación"). Migración 0001. |
| T1.2 | HU-1 | API de registro de prestadores | Renzo León | 3 | ✅ | `main` | 05/10 | `POST /api/prestadores/registro/` crea usuario y prestador; exige DNI (8 dígitos, único), ≥ 1 rubro y cobertura; queda "Pendiente de verificación". `GET /api/prestadores/rubros/`. 7 pruebas pasan. Se agregó Django REST Framework (`requirements.txt`). |
| T1.3 | HU-1 | Formulario de registro (web y móvil) | Renzo León | 2 | ✅ | `main` | 05/10 | Formulario React (`frontend/src/RegistroPrestador.jsx`): datos, cuenta, rubros desde la API y mapa Leaflet con círculo de cobertura y radio. Valida en el cliente y muestra los errores del servidor. Diseño responsive (sirve en navegador móvil); la app móvil nativa sigue por definir. Probado de punta a punta contra la API. |
| T4.1 | HU-4 | Flujo de solicitud: envío y aceptación/rechazo | Renzo León | 3 | 🟡 | `hu-4-solicitud`; pendiente de PR (se une después del PR 3 de HU-3) | 12/10 | Implementada en `servicios`: modelo `Solicitud` (Pendiente → Aceptada / Rechazada) y migración 0001. `POST /api/servicios/solicitudes/` valida que el prestador esté disponible, ofrezca el rubro y cubra la ubicación del cliente. El prestador ve su bandeja (`GET .../recibidas/`) y acepta o rechaza (`POST .../<id>/aceptar/` y `.../rechazar/`), solo si es el dueño y si sigue Pendiente. El cliente ve el cambio en `GET .../<id>/`. 12 pruebas nuevas; `prestadores` y `servicios`: 30 OK. |
| T3.1 | HU-3 | Búsqueda por rubro, radio y disponibilidad | Luis Abad | 3 | 🟡 | `hu-3-busqueda`; pendiente de revisión e integración por PR | 06/10 | Implementada y verificada: `GET /api/prestadores/buscar/`, parámetros validados en español, Haversine reutilizable, cobertura inclusiva y datos públicos. 11 pruebas nuevas; suite completa: 18 OK, repetida tras incorporar `ae51360` del equipo. Luis confirmó HTTP 200 vacío y positivo. Sin ordenar por distancia (T3.3). [Documentación](docs/API_BUSQUEDA.md). Pendiente de revisión e integración por PR para cumplir la definición de terminado. |
| T3.2 | HU-3 | Pantalla de resultados con lista de prestadores | Luis Abad | 4 | 🟡 | `hu-3-busqueda`; base previa `50a030c`, `3eceea7`; pendiente de revisión e integración por PR | 06/10 | Implementada: rubros desde API, ubicación Leaflet/coordenadas, Buscar por proxy Vite, tarjetas con datos reales sin calificaciones y estados en español. Cancela peticiones anteriores y conserva el orden de la API. Lint y build código 0, repetidos tras incorporar cambios del equipo. Proxy HTTP 200; Luis confirmó la tarjeta Demo Gasfitero ficticio, Gasfitería, 0 km. Pendientes revisión visual móvil/errores/concurrencia y PR; [pasos y comprobaciones pendientes](frontend/README.md). |
| T2.3 | HU-1/2 | Pruebas de registro y carga | Luis Abad | 1 | ⚪ | — | — | Requiere T1.2 y T2.1. |
| T4.3 | HU-4 | Pruebas buscar → solicitar → aceptar | Luis Abad | 2 | ⚪ | — | — | Requiere el flujo completo. |
| T2.1 | HU-2 | API de carga de certificaciones (PDF/JPG ≤ 5 MB) | David Valcarcel | 2 | 🟡 | `main` (`7533a5e`) | 03/10 | Valida tamaño y formato y devuelve estado "Pendiente de verificación". **Falta asociar la certificación al `Prestador`** (criterio de HU-2); se podrá con el modelo de T1.1. |
| T2.2 | HU-2 | Componente de carga de archivos | David Valcarcel | 2 | 🟡 | `main` (directo, sin PR) | 05/10 | Componente en `src/UploadCertificado.jsx` (carpeta `src/` de la raíz, no en `frontend/`). Falta integrarlo a la app de `frontend/` y pasar por PR. |
| T3.3 | HU-3 | Ordenar resultados por distancia | David Valcarcel | 2 | 🟡 | `main` (`ae51360`, directo, sin PR) | 06/10 | Endpoint aparte `GET /api/certificaciones/prestadores/cercanos/` que ordena por distancia, pero sobre un modelo propio (`CertificacionPrestador`), no sobre `Prestador`. Falta aplicar el orden en la búsqueda de T3.1 (`/api/prestadores/buscar/`). |
| T3.4 | HU-3 | Datos de prueba y tiempo de respuesta (< 3 s) | David Valcarcel | 2 | 🟡 | `main` (`960c3f6`, directo, sin PR) | 07/10 | Script de medición y `evidencia_rendimiento.txt`, medido sobre el endpoint de T3.3, no sobre la búsqueda real. `certificaciones/test_reporte.py` necesita `requests` y el servidor encendido, por eso `manage.py test` marca 1 error. |
| T4.2 | HU-4 | Pantallas de solicitud y cambio de estado | David Valcarcel | 2 | 🟡 | `main` (`1483eab`, directo, sin PR) | 09/10 | El commit agrega una pantalla de carga de certificaciones (`frontend/src/UploadCertificado.jsx`), no la de solicitud de servicio. Falta la pantalla que use la API de T4.1 (`/api/servicios/solicitudes/`). |

## 3. Avance por historia de usuario

| HU | Historia | SP | Horas | Tareas | Estado | Pruebas a cargo |
|:---:|---|:---:|:---:|---|:---:|---|
| HU-1 | Registro del prestador | 3 | 7 h | T1.1 ✅ · T1.2 ✅ · T1.3 ✅ | ✅ 7 de 7 h | Luis (T2.3) |
| HU-2 | Carga de certificaciones | 2 | 5 h | T2.1 🟡 · T2.2 🟡 · T2.3 ⚪ | 🟡 | Luis (T2.3) |
| HU-3 | Búsqueda de prestadores | 5 | 11 h | T3.1 🟡 (PR 3) · T3.2 🟡 (PR 3) · T3.3 🟡 · T3.4 🟡 | 🟡 | David (T3.4) |
| HU-4 | Solicitud y aceptación | 3 | 7 h | T4.1 🟡 (`hu-4-solicitud`) · T4.2 🟡 · T4.3 ⚪ | 🟡 | Luis (T4.3) |

## 4. Avance por integrante

| Integrante | Rol | Horas plan | Horas terminadas | Tareas terminadas | Tareas en curso |
|---|---|:---:|:---:|:---:|---|
| Renzo León | Scrum Master / Desarrollador | 10 h | 7 h | T1.1, T1.2, T1.3 | T4.1 (pendiente de PR) |
| Luis Abad | Development Team | 10 h | 0 h | — | T3.1 (pendiente de PR), T3.2 (pendientes revisión visual y PR) |
| David Valcarcel | Product Owner / Desarrollador | 10 h | 0 h | — | T2.1, T2.2, T3.3, T3.4, T4.2 (subidas a `main` sin PR; falta integrarlas) |
| **Equipo** | | **30 h** | **7 h** | **3** | **8** |

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
| 8 | 12/10 | 6 | 23 |
| 9 | 13/10 | 3 | |
| 10 | 14/10 | 0 | |

## 6. Bitácora de avances

Una línea por entrega, la más reciente arriba.

| Fecha | Quién | Qué se hizo | Tarea |
|:---:|---|---|:---:|
| 12/10 | Renzo León | T4.1 en la rama `hu-4-solicitud` (creada desde `hu-3-busqueda` con `main` incorporado): modelo `Solicitud` y migración, envío con validación de disponibilidad, rubro y cobertura, bandeja del prestador, aceptar/rechazar solo por el dueño y solo una vez (409 si ya se respondió) y consulta del estado para el cliente. 12 pruebas nuevas; `prestadores` y `servicios`: 30 OK. AVANCES actualizado con lo subido por David entre el 05/10 y el 10/10. | T4.1 |
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
| 1 | 05/10 | T1.1 llegaba con 3 días de retraso y bloqueaba la búsqueda y las certificaciones. Resuelto: PR 1 unido a `main` el 05/10. | HU-2, HU-3 | Renzo | ✅ |
| 2 | 05/10 | La carga de certificaciones no está asociada a un prestador (criterio de HU-2). | HU-2 | David | 🟡 |
| 3 | 06/10 | T3.1 y la conexión de T3.2 ya están implementadas localmente; faltan comprobaciones visuales de T3.2 y revisión/integración por PR. La pantalla ya consulta datos de la API. | HU-3 | Luis | 🟡 |
| 4 | 05/10 | T4.1 dependía de HU-3. Implementada el 12/10 sobre `hu-3-busqueda`; su PR debe unirse después del PR 3. | HU-4 | Renzo | 🟡 |
| 5 | 05/10 | App móvil "por definir" en el stack: el formulario es web responsive, no una app nativa. | HU-1 | Equipo | ⚪ |
| 6 | 12/10 | T2.2, T3.3, T3.4 y T4.2 se subieron directo a `main`, sin PR ni revisión, y T3.3/T3.4 usan un modelo aparte (`CertificacionPrestador`) en vez de `Prestador`. Hay que integrarlas a la búsqueda real antes de la Review. | HU-2, HU-3, HU-4 | David | 🔴 |
| 7 | 12/10 | `certificaciones/test_reporte.py` no es una prueba de Django: al correr `manage.py test` da error (falta `requests`) y reescribe `evidencia_rendimiento.txt`. Conviene moverlo fuera de la app o convertirlo en prueba. | Todas | David | 🟡 |
| 8 | 12/10 | Quedan 2 días: HU-2, HU-3 y HU-4 no cumplen todavía la definición de terminado. Prioridad: revisar y unir el PR 3, luego T4.1, y que David conecte T3.3 y T4.2 a las APIs reales. | Sprint 1 | Equipo | 🔴 |

## 8. Decisiones técnicas

| Fecha | Decisión | Motivo |
|:---:|---|---|
| 12/10 | La `Solicitud` guarda nombre y teléfono del cliente en vez de enlazar a una cuenta. | Todavía no hay inicio de sesión de clientes (HU-5, Sprint 2); entonces se agrega la relación con el usuario. |
| 12/10 | Aceptar, rechazar y la bandeja exigen sesión del prestador dueño; enviar y consultar el estado no piden sesión. | Solo el prestador puede responder sus solicitudes; el cliente aún no tiene cuenta. La consulta del cliente no muestra su teléfono. |
| 12/10 | La solicitud solo se responde una vez; responder de nuevo devuelve 409. | Evita que un prestador cambie de Aceptada a Rechazada después de confirmar. |
| 06/10 | Resultados usa el proxy `/api` existente y Leaflet; cancela búsquedas al cambiar filtros, repetir consulta o salir de la pantalla y conserva el orden recibido. | Evita datos desactualizados sin asumir el ordenamiento T3.3 de David; no altera el registro ni los datos locales. |
| 06/10 | Búsqueda en `servicios` con Haversine reutilizable y radio inclusivo; respuesta pública sin calificaciones ni orden por distancia. | Respeta T3.1 y la organización del backend; T3.3 y T3.4 quedan a cargo de David. Sin migraciones ni cambios a registros locales. |
| 05/10 | Mapa con Leaflet + OpenStreetMap; en desarrollo el frontend usa un proxy `/api` hacia Django (puerto 8000). | Sin claves ni costo; evita configurar CORS. |
| 05/10 | Rubros iniciales cargados con una migración de datos. | Todos los entornos arrancan con los mismos rubros. |
| 01/10 | Backend en Django + Django REST Framework. | Definido en el plan del equipo. |
| 05/10 | Se agrega Django REST Framework; el registro crea el usuario y el prestador en una sola operación. | La API necesita un usuario (relación 1:1); el login con roles llega en HU-5. |
| 05/10 | Cobertura del prestador como centro (latitud, longitud) + radio en km, sin PostGIS. | Más simple; basta para la búsqueda por radio y por distancia. |

## 9. Retrospectiva del Sprint 1 (se llena el 14/10)

| ¿Qué salió bien? | ¿Qué mejorar? | Acciones para el Sprint 2 |
|---|---|---|
| | | |
