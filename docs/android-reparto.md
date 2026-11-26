# App Android · Qué falta y quién lo hace — Chamba PE

**Preparado por:** Renzo León (Scrum Master) el 16/11/2026 · **Estado:** 🟡 propuesta, se confirma con el equipo en el Daily del 17/11.

Compara el [plan Android](PLAN_DESARROLLO_ANDROID.md) (21 tarjetas `AND-*`, de Luis) con lo que hay en `app-movil/` (antes `mobile/android/`) en el [PR #39](https://github.com/RXDG2908/Chamba-PE/pull/39). Detalle técnico de la app en su [README](../app-movil/README.md).

> **Actualización del 16/11 (tarde):** el PR #39 **ya está unido a `main`**. Lo unió Renzo para no frenar el trabajo, después de una revisión automática (250 pruebas de Django OK, 19 pruebas de Android OK, lint sin errores, sin secretos en el diff) y **sin revisión de Luis ni David**. La revisión de la parte A sigue pendiente, ahora sobre `main`: lo que encuentren entra como PR de corrección. La revisión automática dejó tres observaciones, ninguna bloqueante:
>
> 1. `registro-movil` guarda el correo del token aunque Firebase no lo haya verificado: alguien podría ocupar el correo de otra persona (no le da acceso a su cuenta).
> 2. Dos registros simultáneos con la misma cuenta responden 500 en vez de 409.
> 3. Cada petición con token descarga las claves públicas de Google; conviene guardarlas en caché.

---

## 1. Resumen

| Dato | Valor |
|---|---|
| Tarjetas del plan construidas | **11 de 21**, todas por Renzo; en `main` desde el 16/11 (PR #39), **sin revisión de otro integrante** |
| Tarjetas parciales | 3: AND-02, AND-14 y AND-17 |
| Tarjetas sin iniciar | 7: AND-08, AND-15, AND-16 y AND-18 a AND-21 |
| Trabajo nuevo que el plan no tenía | 4 tarjetas: AND-22 a AND-25 |
| Horas estimadas de lo que falta | **≈ 29 h** (estimación del Scrum Master, por confirmar) |
| Capacidad en el Sprint 4 | **0 h**: está completo con 30 h ([reparto](sprint4-reparto.md), en el [PR #40](https://github.com/RXDG2908/Chamba-PE/pull/40)) |

> ⚠️ **Android no cabe en el Sprint 4.** Solo entra la parte A (revisar y unir lo ya hecho, sin horas de sprint). La parte B queda en el backlog hasta que el PO decida. Si el curso de Programación en Móviles pide una entrega antes del 25/11, David tiene que sacar algo del Sprint 4 para darle lugar.

**Dos cosas se hicieron distinto al plan** y el equipo todavía no las aprobó:

1. La app usa la **API Django + Firebase** (Auth y Firestore), no el backend Spring.
2. Las **solicitudes de la app viven en Firestore**, separadas de las de la web (Django).

---

## 2. Estado de las tarjetas del plan

✅ construida (en `main`, falta la revisión de otro integrante) · 🟡 parcial · ☐ sin iniciar

| ID | Tarjeta | Plan decía | Estado | Nota |
|:---:|---|:---:|:---:|---|
| AND-01 | Proyecto Kotlin/Compose y versiones | Renzo | ✅ | Compila, pruebas y lint OK |
| AND-02 | Tema Material 3 y componentes comunes | David | 🟡 | 26/11: tema de marca claro/oscuro (`Tema.kt`), asistente por pasos (`Asistente.kt`), iconos Material, icono adaptable de la app y arranque animado. Falta revisar texto ampliado y pantallas anchas |
| AND-03 | Navigation Compose | Luis | ✅ | Pestañas por rol |
| AND-04 | Retrofit, DTO y mappers | David | ✅ | Contra Django |
| AND-05 | Repository de búsqueda | Renzo | ✅ | Con caché en Room |
| AND-06 | `BusquedaViewModel` y UiState | Luis | ✅ | |
| AND-07 | Filtros y lista de búsqueda | David | ✅ | |
| AND-08 | Pruebas de Repository y contrato HTTP | Renzo | ☐ | No hay pruebas de repositorios ni de mappers |
| AND-09 | Pruebas de ViewModel | Luis | ✅ | 19 pruebas (búsqueda, disponibilidad y registro) |
| AND-10 | Identidad y sesión | Renzo | ✅ | Con Firebase Auth, no Spring |
| AND-11 | Pantalla de acceso y navegación por rol | Luis | ✅ | |
| AND-12 | Crear y consultar solicitud | David | ✅ | En Firestore; incluye la bandeja del prestador (aceptar/rechazar) |
| AND-13 | Disponibilidad del prestador | Luis | ✅ | Contra Django con token de Firebase |
| AND-14 | Mapa y ubicación | David | 🟡 | Mapa hecho (osmdroid); «Usar mi ubicación» sin probar con GPS real |
| AND-15 | Cliente FCM y token del dispositivo | Renzo | ☐ | Hoy solo hay una notificación local |
| AND-16 | Pruebas Compose y de navegación | David | ☐ | No existe `androidTest` |
| AND-17 | Integración en emulador y **teléfono físico** | Luis | 🟡 | Emulador hecho; teléfono físico no |
| AND-18 | Verificar recepción de notificaciones | Renzo | ☐ | Depende de AND-15 |
| AND-19 | Consentimiento del DNI | David | ☐ | Depende del PR #38 (HU-21) |
| AND-20 | Eliminación de datos del DNI | Luis | ☐ | Depende del PR #38 (HU-21) |
| AND-21 | Captura del DNI | Renzo | ☐ | Depende de AND-19 |

Además, fuera del plan, ya está hecho el **registro de prestador** con mapa de cobertura (HU-1 en móvil).

---

## 3. Reparto de lo que falta

Renzo construyó casi todo, así que **las pruebas y la revisión pasan a Luis y David** (nadie prueba lo suyo), y lo nuevo se reparte entre los tres. Las horas son una estimación para discutir.

### Parte A · Revisión del PR #39, ya unido (ahora, sin horas de sprint)

| Qué | Responsable | Detalle |
|---|---|---|
| Revisar el PR #39 — app Android | **David** | El plan ya decía «David revisa a Renzo». Compilar, correr las pruebas e instalar en emulador |
| Revisar el PR #39 — backend | **Luis** | `usuarios/autenticacion.py` (token de Firebase), migración `usuarios.0003` y `POST /api/prestadores/registro-movil/`. Instalar desde cero (`requirements.txt` cambió) |
| Decidir el enfoque | **Equipo** (David como PO) | Django + Firebase en vez de Spring, y solicitudes en Firestore. Ver [decisiones](#5-decisiones-del-equipo) |
| Atender las observaciones y actualizar el plan | **Renzo** | Plan Android a v0.2 con lo que se decida; quitar «App móvil: por definir» de `PLAN_EQUIPO.md` |
| Dar acceso al proyecto Firebase | **Renzo** | `chamba-pe-tecsup` está en la cuenta de Renzo: agregar a Luis y David. El `google-services.json` ya está configurado (26/11, con el MCP de Firebase) y las huellas del certificado de depuración, registradas |

### Parte B · Tarjetas pendientes (backlog, por comprometer)

#### Luis Abad — ≈ 10 h

| ID | Tarea | Tipo | Horas | Depende de |
|:---:|---|:---:|:---:|---|
| AND-17 | Probar en **teléfono físico**: búsqueda, GPS real, solicitud y bandeja; dejar evidencia | Pruebas | 2 | #39 revisado |
| AND-08 | Pruebas de repositorios, mappers y contrato HTTP con servidor simulado | Pruebas | 3 | — |
| AND-23 | ✅ 26/11 (Renzo). Solicitudes de la app por la API Django (`servicios`) en vez de Firestore, para que web y móvil vean las mismas | Backend + móvil | 3 | Decisión 2 |
| AND-20 | Solicitud, historial y reintento de eliminación de datos del DNI | Móvil | 2 | #38 unido; AND-19 |

#### David Valcarcel — ≈ 10 h

| ID | Tarea | Tipo | Horas | Depende de |
|:---:|---|:---:|:---:|---|
| AND-16 | Pruebas Compose y de navegación del recorrido de búsqueda | Pruebas | 3 | — |
| AND-02 | Cerrar el tema: oscuro, texto ampliado y pantalla ancha | Móvil | 2 | — |
| AND-22 | **Nueva.** Carga de certificados PDF/JPG desde la app (HU-2 en móvil) | Móvil | 3 | Token de Firebase aceptado en la carga |
| AND-19 | Consentimiento del DNI y revocación | Móvil | 2 | #38 unido |

#### Renzo León — ≈ 9 h

| ID | Tarea | Tipo | Horas | Depende de |
|:---:|---|:---:|:---:|---|
| AND-15 | Cliente FCM y registro del token del dispositivo en Django | Backend + móvil | 3 | Acordar con Luis el emisor de HU-8 |
| AND-18 | Verificar recepción real: primer y segundo plano, permiso denegado, cierre de sesión | Pruebas | 2 | AND-15 |
| AND-21 | Captura del DNI (frente y reverso) y resultado de HU-19 | Móvil | 3 | AND-19 |
| AND-24 | ✅ 26/11 (Renzo), resuelta de otra forma: la app ya no usa Firestore; el rol y el ID de prestador salen de `GET /api/usuarios/yo/`, sin vínculo manual | Backend + móvil | 1 | AND-23 |

> AND-18 la construye y la verifica Renzo; para respetar las pruebas cruzadas, **David repite la matriz** en su teléfono.

#### Después del Sprint 4

| ID | Tarea | Responsable | Depende de |
|:---:|---|---|---|
| AND-25 | **Nueva.** Recomendación, seguimiento y calificación en la app (HU-10, HU-11 y HU-12) | Quien construye cada historia en la web | Que cierre el Sprint 4; se estima entonces |

### Carga

| Integrante | Construir | Pruebas | Total estimado |
|---|:---:|:---:|:---:|
| Luis Abad | 5 h | 5 h | **≈ 10 h** |
| David Valcarcel | 7 h | 3 h | **≈ 10 h** |
| Renzo León | 7 h | 2 h | **≈ 9 h** |
| **Equipo** | **19 h** | **10 h** | **≈ 29 h** |

---

## 4. Orden de trabajo

1. **Revisar y decidir** (parte A): el PR #39 se unió sin esa revisión, así que es lo primero.
2. **Probar lo que ya existe:** AND-17 (teléfono físico), AND-08 y AND-16. No dependen de nada más.
3. **Unificar solicitudes** (AND-23) antes de las notificaciones: AND-15 necesita saber quién emite el aviso.
4. **Privacidad en orden:** PR #38 → AND-19 → AND-20 → AND-21. No se captura un DNI sin consentimiento.
5. **AND-25** al cerrar el Sprint 4.

Reglas del [plan](PLAN_DESARROLLO_ANDROID.md#11-git-colaboración-y-conservación-de-avances): una rama por tarjeta (`feature/android/and-08-...`), cambios en `gradle/libs.versions.toml` avisados al equipo, y nada de `google-services.json` ni firmas en el repositorio.

---

## 5. Decisiones del equipo

| # | Decisión | Propuesta del Scrum Master |
|:---:|---|---|
| 1 | ¿API Django + Firebase Auth, o el backend Spring del plan? | Django + Firebase: ya funciona y Spring no existe. El plan pasa a v0.2 |
| 2 | ¿Solicitudes en Firestore o en Django? | Django (AND-23): una sola fuente para web y móvil |
| 3 | ¿Android entra en algún sprint? | Parte A ahora; parte B al backlog. Si el curso de Móviles exige fecha, el PO ajusta el Sprint 4 |
| 4 | ¿Quién administra el proyecto Firebase? | Renzo, con Luis y David como editores |
| 5 | Checklist de aprobación del plan (sección 13), aún sin firmar | Firmarlo en el Daily del 17/11 con estas decisiones |

---

## 6. Mensajes para el equipo

### Para el grupo

> Equipo, sobre la app Android: ya está la primera versión en `main`, con el PR #39 (ingreso, registro de cliente y prestador, búsqueda con mapa, favoritos, solicitudes, bandeja y disponibilidad). Hice 11 de las 21 tarjetas del plan de Luis, pero la uní hoy sin que nadie más la revise, para no frenar el trabajo, y me aparté del plan en dos cosas: usa Django + Firebase en vez de Spring, y las solicitudes están en Firestore.
>
> En `docs/android-reparto.md` dejé qué falta y una propuesta de reparto (unas 29 h, 10 cada uno). No cabe en el Sprint 4, así que por ahora solo pido que la revisen sobre `main` y que decidamos el enfoque mañana en el Daily. Si el curso de Móviles tiene fecha de entrega antes del 25/11, avisen para reordenar.

### Para Luis

> Luis, de Android te pido, sin horas de sprint por ahora: revisar la parte de **backend** del PR #39 (`usuarios/autenticacion.py`, la migración `usuarios.0003` y `registro-movil`). Ya está en `main`, pero toca `usuarios` y `prestadores` y quiero otro par de ojos; lo que encuentres lo corrijo en un PR aparte.
>
> Tu plan decía Spring y yo usé Django + Firebase; mañana lo decidimos juntos y actualizo el plan a v0.2 con lo que quede.
>
> Para después te propongo: AND-17 (probar en tu teléfono), AND-08 (pruebas de repositorios), AND-23 (pasar las solicitudes de Firestore a tu API de `servicios`) y AND-20 (eliminación de datos). Te paso el acceso a Firebase y el `google-services.json`.

### Para David

> David, de Android te pido, sin horas de sprint por ahora: revisar la **app** del PR #39, que ya está en `main` (compilar, pruebas, emulador). El plan ya te ponía como revisor mío.
>
> Como PO necesito que decidas mañana: Django + Firebase o Spring, solicitudes en Firestore o en Django, y si Android entra en algún sprint (hoy el Sprint 4 está en 30 h, sin espacio).
>
> Para después te propongo: AND-16 (pruebas Compose), cerrar el tema visual (AND-02), AND-22 (certificados desde la app) y AND-19 (consentimiento, cuando se una el #38). Te paso el acceso a Firebase y el `google-services.json`.
