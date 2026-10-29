# Sprints — Chamba PE

16 historias en alcance · 51 story points · 114 h · 4 sprints de 2 semanas · capacidad de 30 h por sprint (10 h por integrante).

Reglas de reparto y flujo de trabajo en [PLAN_EQUIPO.md](PLAN_EQUIPO.md). Detalle original de historias y criterios en [docs/Lab07_CPS_ChambaYa.md](docs/Lab07_CPS_ChambaYa.md).

## Calendario

| Sprint | Fechas | Historias | SP | Horas | Estado |
|:---:|:---:|:---:|:---:|:---:|:---:|
| [1](#sprint-1--0110--14102026) | 01/10 – 14/10/2026 | HU-1 a HU-4 | 13 | 30 h | ✅ Cerrado (26 de 30 h) |
| [2](#sprint-2--1510--28102026) | 15/10 – 28/10/2026 | HU-5 a HU-9 | 13 | 30 h | 🟢 En curso |
| [3](#sprint-3--2910--11112026) | 29/10 – 11/11/2026 | HU-19, HU-20, HU-21, HU-13 | 13 | 28 h | ⚪ Por planificar |
| [4](#sprint-4--1211--25112026) | 12/11 – 25/11/2026 | HU-10, HU-11, HU-12 | 12 | 26 h | ⚪ Por planificar |
| [—](#backlog-futuro) | — | HU-14 a HU-18 | 11 | 30 h | 📦 Backlog futuro |

### Cambios respecto al Lab 07

- Las fechas se recorrieron 2 semanas porque el Sprint 1 empezó el 01/10/2026.
- Se priorizó la **seguridad del prestador**: se agregan HU-19 (escaneo y validación del DNI), HU-20 (reconocimiento facial) y HU-21 (consentimiento biométrico) en el Sprint 3.
- HU-10, HU-11 y HU-12 pasan al Sprint 4; HU-14 a HU-18 salen al [backlog futuro](#backlog-futuro).
- Los Sprints 3 y 4 dejan holgura (2 h y 4 h) por el riesgo técnico de la verificación facial.

---

# Sprint 1 · 01/10 – 14/10/2026

**Meta (MVP):** el prestador se registra y el cliente lo encuentra y le envía una solicitud.

| Story points | Horas | Capacidad |
|:---:|:---:|:---:|
| 13 SP | 30 h | 3 × 10 h |

## Historias comprometidas

| HU | Historia | SP | Horas | Criterios de aceptación |
|:---:|---|:---:|:---:|---|
| HU-1 | Como **prestador**, quiero registrarme indicando mis datos, rubros y área de cobertura, a fin de que los clientes cercanos puedan encontrarme. | 3 | 7 h | • No se completa sin DNI, al menos un rubro y un área de cobertura.<br>• Al guardar, el perfil queda "Pendiente de verificación". |
| HU-2 | Como **prestador**, quiero cargar mis certificaciones (PDF o JPG), a fin de demostrar mi experiencia. | 2 | 5 h | • Acepta PDF o JPG de hasta 5 MB y rechaza otros formatos o tamaños.<br>• Las certificaciones quedan asociadas al perfil. |
| HU-3 | Como **cliente**, quiero buscar prestadores disponibles cerca de mí por tipo de servicio, a fin de resolver una necesidad urgente. | 5 | 11 h | • Solo prestadores disponibles, del rubro elegido y con cobertura sobre el cliente.<br>• Resultados en menos de 3 s, ordenados por distancia. |
| HU-4 | Como **cliente**, quiero enviar una solicitud al prestador y que él pueda aceptarla o rechazarla, a fin de contratar en pocos minutos. | 3 | 7 h | • El prestador recibe la solicitud y la acepta o rechaza.<br>• El cliente ve el cambio de estado. |

## Reparto de tareas

✅ terminada (en `main` por PR revisado) · 🟡 en curso o pendiente de PR · ☐ sin iniciar. Detalle en [AVANCES.md](AVANCES.md).

### Renzo León — 10 h

| Tarea | Descripción | Tipo | Horas | ✔ |
|:---:|---|:---:|:---:|:---:|
| T1.1 | Diseñar el modelo de datos del prestador (datos, rubros y cobertura) | Backend | 2 | ✅ |
| T1.2 | Implementar la API de registro de prestadores (Django) | Backend | 3 | ✅ |
| T1.3 | Maquetar el formulario de registro (web y móvil) con rubros y mapa de cobertura | Frontend | 2 | ✅ |
| T4.1 | Implementar el flujo de solicitud: envío y aceptación o rechazo | Backend | 3 | ✅ |
| | **Subtotal** | | **10** | |

### Luis Abad — 10 h

| Tarea | Descripción | Tipo | Horas | ✔ |
|:---:|---|:---:|:---:|:---:|
| T3.1 | Implementar la búsqueda por rubro, radio de cobertura y disponibilidad | Backend | 3 | ✅ |
| T3.2 | Pantalla de resultados con lista de prestadores | Frontend | 4 | ✅ |
| T2.3 | Pruebas funcionales del registro y la carga (campos obligatorios y límites) | Pruebas | 1 | ✅ |
| T4.3 | Pruebas del flujo buscar → solicitar → aceptar | Pruebas | 2 | ✅ |
| | **Subtotal** | | **10** | |

### David Valcarcel — 10 h

| Tarea | Descripción | Tipo | Horas | ✔ |
|:---:|---|:---:|:---:|:---:|
| T2.1 | Implementar la API de carga de certificaciones (PDF/JPG hasta 5 MB) | Backend | 2 | ✅ |
| T2.2 | Componente de carga de archivos y estado "Pendiente de verificación" | Frontend | 2 | ✅ 18/10 |
| T3.3 | Ordenar los resultados por distancia | Backend | 2 | ✅ |
| T3.4 | Datos de prueba de prestadores y verificación del tiempo de respuesta (< 3 s) | Pruebas | 2 | ✅ 18/10 |
| T4.2 | Pantallas de envío de solicitud y cambio de estado | Frontend | 2 | ✅ |
| | **Subtotal** | | **10** | |

### Resumen de carga

| Integrante | Backend | Frontend | Pruebas | Total |
|---|:---:|:---:|:---:|:---:|
| Renzo León | 8 h | 2 h | — | **10 h** |
| Luis Abad | 3 h | 4 h | 3 h | **10 h** |
| David Valcarcel | 4 h | 4 h | 2 h | **10 h** |
| **Equipo** | **15 h** | **10 h** | **5 h** | **30 h** |

### Pruebas cruzadas

| Historia | Construye | Prueba |
|:---:|---|---|
| HU-1 | Renzo | Luis (T2.3) |
| HU-2 | David | Luis (T2.3) |
| HU-3 | Luis y David | David (T3.4) |
| HU-4 | Renzo y David | Luis (T4.3) |

## Orden de trabajo

Dependencias: **HU-1 → HU-2 y HU-3** · **HU-3 → HU-4**. La API va primero; las pantallas se maquetan en paralelo con datos de prueba (mocks).

| Momento | Renzo | Luis | David |
|---|---|---|---|
| **01/10 – 02/10** | **T1.1 modelo del prestador** (bloquea al resto) | Maquetar T3.2 con mocks | Maquetar T2.2 y T4.2 con mocks |
| **05/10 – 09/10** | T1.2 API de registro · T1.3 formulario | T3.1 búsqueda · T3.2 resultados | T2.1 API de certificaciones · T2.2 carga |
| **12/10 – 14/10** | T4.1 flujo de solicitud | T2.3 y T4.3 pruebas | T3.3 orden por distancia · T3.4 datos y rendimiento · T4.2 conectar a la API |

**Cierre (14/10):** Sprint Review con demo del flujo registrar → buscar → solicitar → aceptar, y Retrospectiva.

## Burndown

Horas pendientes al final de cada día. La línea ideal baja 3 h por día.

| Día | Fecha | Ideal (h) | Real (h) |
|:---:|:---:|:---:|:---:|
| Inicio | 01/10 | 30 | 30 |
| 1 | 01/10 (jue) | 27 | 30 |
| 2 | 02/10 (vie) | 24 | 30 |
| 3 | 05/10 (lun) | 21 | 23 |
| 4 | 06/10 (mar) | 18 | 23 |
| 5 | 07/10 (mié) | 15 | 23 |
| 6 | 08/10 (jue) | 12 | 23 |
| 7 | 09/10 (vie) | 9 | 23 |
| 8 | 12/10 (lun) | 6 | 13 |
| 9 | 13/10 (mar) | 3 | 13 |
| 10 | 14/10 (mié) | 0 | 4 |

## Retrospectiva

> Borrador del Scrum Master con los datos del sprint; se confirma o ajusta con el equipo.

| ¿Qué salió bien? | ¿Qué mejorar? | Acciones para el Sprint 2 |
|---|---|---|
| HU-1 completa en `main` con PR revisado y pruebas. | Arranque lento: 0 h terminadas los dos primeros días y T1.1, que bloqueaba al resto, llegó con 3 días de retraso. | Las tareas que bloquean a otros (modelos y APIs) se hacen en los primeros 2 días. |
| Búsqueda, API de solicitudes y pantalla de solicitud funcionan de punta a punta y se probaron en el navegador. | Commits directo a `main` sin PR: rompieron `main` (faltaba `django-cors-headers`) y duplicaron lógica. | Activar la protección de `main` en GitHub: solo se une por PR con 1 aprobación. |
| Las pruebas cruzadas funcionaron: Luis probó la API de T4.1 y su prueba de T2.3 detectó el certificado sin vincular. | T4.2 se hizo primero como pantalla de certificados por no leer los criterios de aceptación. | Antes de empezar, leer los criterios en SPRINTS.md y consultar dudas en el Daily. |
| Al cierre, `main` quedó con 49 de 49 pruebas OK, lint y build sin errores. | Integración tardía: casi todo se unió entre el 12/10 y el 14/10, por eso el burndown no bajó una semana. | PR pequeños: abrirlos apenas la tarea funcione y revisarlos en máximo 1 día. |
| | PR 4 y PR 7 se unieron sin revisión de otro integrante. | Si un PR agrega una librería, también actualiza `requirements.txt`; quien revisa instala desde cero. |
| | AVANCES.md no se actualizó a diario y los informes de pruebas salieron muy largos. | AVANCES.md al cierre de cada día, en pocas líneas; el detalle va en `docs/`. |

---

# Sprint 2 · 15/10 – 28/10/2026

**Meta:** Cuentas, disponibilidad, mapa y notificaciones; prestadores verificados por administración.

| Story points | Horas | Capacidad |
|:---:|:---:|:---:|
| 13 SP | 30 h | 3 × 10 h |

> ⚠️ **Arranque tardío:** el reparto se armó el 18/10 y el 15/10 y el 16/10 pasaron sin horas. Quedan 8 días hábiles (19/10 – 28/10) para 30 h, unas **1,25 h diarias por integrante**. Si en el Daily del 23/10 quedan más de 15 h, el Product Owner decide qué tarea pasa al Sprint 3 (candidatas: T8.2 y T8.3, notificaciones).

## Pendientes que pasan del Sprint 1

| Tarea | Descripción | Responsable | Horas plan | Estado | Resultado |
|:---:|---|---|:---:|:---:|---|
| T2.2 | Componente de carga de certificados | David Valcarcel | 2 | ✅ | PR 9 unido el 18/10: ruta `/certificaciones` en el menú, envía el prestador y usa su CSS |
| T3.4 | Tiempo de respuesta < 3 s | David Valcarcel | 2 | ✅ | PR 9 unido el 18/10: `manage.py medir_busqueda` mide `/api/prestadores/buscar/` (máx. 32,5 ms) |

## Historias comprometidas

| HU | Historia | SP | Horas | Criterios de aceptación |
|:---:|---|:---:|:---:|---|
| HU-5 | Como **cliente o prestador**, quiero registrarme e iniciar sesión de forma segura, a fin de acceder a las funciones de mi perfil. | 3 | 7 h | • El registro valida correo y contraseña y asigna el rol (cliente o prestador).<br>• Sin sesión iniciada no se accede a las funciones privadas. |
| HU-6 | Como **prestador**, quiero activar o desactivar mi disponibilidad en tiempo real, a fin de recibir solicitudes solo cuando esté listo para trabajar. | 2 | 5 h | • El prestador cambia su estado entre Activo e Inactivo desde la app.<br>• Un prestador inactivo no aparece en las búsquedas ni recibe solicitudes. |
| HU-7 | Como **cliente**, quiero ver en un mapa a los prestadores dentro del radio de cobertura, a fin de saber qué tan cerca están de mi ubicación. | 3 | 7 h | • El mapa muestra solo prestadores dentro del radio de cobertura de la ubicación del cliente.<br>• Al tocar un marcador se ve nombre, rubro, calificación y distancia. |
| HU-8 | Como **prestador**, quiero recibir una notificación inmediata en mi móvil cuando me llegue una solicitud, a fin de aceptarla o rechazarla en pocos segundos. | 3 | 7 h | • La notificación llega en menos de 5 s tras enviarse la solicitud.<br>• Desde la notificación el prestador puede aceptar o rechazar. |
| HU-9 | Como **administrador**, quiero validar la identidad y las certificaciones de los prestadores antes de activarlos, a fin de garantizar la seguridad de los usuarios. | 2 | 4 h | • El administrador ve el DNI y las certificaciones, y puede aprobarlas o rechazarlas indicando un motivo.<br>• Al aprobar, el estado cambia a "Verificado" y el prestador ya puede recibir solicitudes. |

## Reparto de tareas

Cada uno construye las historias de la app de Django de la que es dueño ([PLAN_EQUIPO.md §5](PLAN_EQUIPO.md#5-organización-del-backend)). David, que tiene la historia más chica, hace las pruebas cruzadas de las demás.

✅ terminada (en `main` por PR revisado) · 🟡 en curso o pendiente de PR · ☐ sin iniciar. Detalle en [AVANCES.md](AVANCES.md).

### Renzo León — 10 h · app `usuarios`

| Tarea | Descripción | Tipo | Horas | ✔ |
|:---:|---|:---:|:---:|:---:|
| T5.1 | API de autenticación con roles (cliente, prestador, administrador) | Backend | 3 | 🟡 |
| T6.1 | Endpoint para activar y desactivar la disponibilidad | Backend | 2 | 🟡 |
| T5.2 | Pantallas de registro e inicio de sesión (móvil y web) | Frontend | 3 | 🟡 |
| T6.2 | Interruptor de disponibilidad en la app móvil | Frontend | 2 | 🟡 |
| | **Subtotal** | | **10** | |

### Luis Abad — 10 h · app `servicios`

| Tarea | Descripción | Tipo | Horas | ✔ |
|:---:|---|:---:|:---:|:---:|
| T7.1 | Consulta de prestadores por coordenadas dentro del radio | Backend | 2 | ☐ |
| T8.1 | Integrar notificaciones push (Firebase Cloud Messaging) | Backend | 3 | ☐ |
| T7.2 | Integrar el mapa con marcadores (móvil y web) | Frontend | 3 | ☐ |
| T8.2 | Recepción de la notificación y pantalla aceptar/rechazar | Frontend | 2 | ☐ |
| | **Subtotal** | | **10** | |

### David Valcarcel — 10 h · app `administracion`

| Tarea | Descripción | Tipo | Horas | ✔ |
|:---:|---|:---:|:---:|:---:|
| T9.1 | Modelo y API en Django para revisar documentos | Backend | 2 | ☐ |
| T9.2 | Pantalla web (React) para aprobar o rechazar | Frontend | 2 | ☐ |
| T5.3 | Pruebas de autenticación y acceso por rol | Pruebas | 1 | ☐ |
| T7.3 | Pruebas de precisión del radio de cobertura | Pruebas | 2 | ☐ |
| T6.3 | Pruebas: solo los prestadores activos aparecen en la búsqueda | Pruebas | 1 | ☐ |
| T8.3 | Pruebas de entrega de notificaciones | Pruebas | 2 | ☐ |
| | **Subtotal** | | **10** | |

### Resumen de carga

| Integrante | Backend | Frontend | Pruebas | Total |
|---|:---:|:---:|:---:|:---:|
| Renzo León | 5 h | 5 h | — | **10 h** |
| Luis Abad | 5 h | 5 h | — | **10 h** |
| David Valcarcel | 2 h | 2 h | 6 h | **10 h** |
| **Equipo** | **12 h** | **12 h** | **6 h** | **30 h** |

### Pruebas cruzadas y revisión de PR

| Historia | Construye | Prueba | Revisa el PR |
|:---:|---|---|---|
| HU-5 | Renzo | David (T5.3) | David |
| HU-6 | Renzo | David (T6.3) | David |
| HU-7 | Luis | David (T7.3) | David |
| HU-8 | Luis | David (T8.3) | David |
| HU-9 | David | Luis (prueba manual antes de la Review, sin horas) | Renzo (dueño de `prestadores`, donde vive el estado del prestador) |

## Orden de trabajo

Dependencias: **T5.1 → T6.1, T5.2, T9.1 (permisos por rol) y T8.1 (token del dispositivo por usuario)** · **T6.1 → T6.2** · **T7.1 → T7.2** · **T8.1 → T8.2** · cada prueba va después del PR de su historia.

Lo que bloquea va en los primeros dos días. **T5.1** es la única tarea de la que dependen los otros dos, así que su PR se abre a más tardar el 20/10; mientras tanto, Luis y David avanzan en lo que no depende de ella.

| Momento | Renzo | Luis | David |
|---|---|---|---|
| **19/10 – 20/10** · bloqueantes | **T5.1 API de autenticación con roles** (PR el 20/10) | **T7.1 consulta por coordenadas** · crear el proyecto de Firebase y hacer un envío de prueba (inicio de T8.1) | **T9.1 modelo y API de revisión**, con `IsAdminUser` hasta que entre el rol de T5.1 |
| **21/10 – 23/10** | T6.1 disponibilidad · T5.2 pantallas de registro e inicio de sesión | T8.1 notificación al crear la solicitud · T7.2 mapa con marcadores | T9.2 pantalla de aprobar o rechazar · T5.3 pruebas de autenticación · T7.3 pruebas del radio |
| **26/10 – 28/10** | T6.2 interruptor de disponibilidad | T8.2 recepción y aceptar/rechazar desde la notificación | T6.3 pruebas de disponibilidad · T8.3 pruebas de entrega (< 5 s) |

**Acuerdos para el Daily del 19/10, antes de escribir código**, para que nadie se bloquee:

1. **Contrato de T5.1:** rutas de registro, inicio y cierre de sesión y "quién soy"; cómo viaja la sesión desde React (sesión de Django + CSRF por el proxy de Vite, como hoy); dónde se guarda el rol. Renzo lo deja escrito en `docs/` el 19/10.
2. **"Móvil" = web responsive:** la app nativa sigue por definir ([impedimento 5](AVANCES.md#7-impedimentos-y-riesgos)), así que T5.2, T6.2 y T7.2 se hacen en React responsive y T8.2 usa notificaciones web de FCM (service worker).
3. **HU-9 cambia la búsqueda y las solicitudes:** solo un prestador "Verificado" aparece y recibe solicitudes. David agrega ese filtro en T9.1 y Luis revisa el cambio en `servicios`.
4. **Credenciales de Firebase fuera del repositorio** (variables de entorno); en `main` solo va un archivo de ejemplo.

**Cierre (28/10):** Sprint Review con demo de iniciar sesión → activar disponibilidad → ver prestadores en el mapa → enviar solicitud → notificación al prestador → aceptar, y del administrador aprobando a un prestador. Luego, Retrospectiva.

## Burndown

Horas pendientes al final de cada día. La línea ideal baja 3 h por día.

| Día | Fecha | Ideal (h) | Real (h) |
|:---:|:---:|:---:|:---:|
| Inicio | 15/10 | 30 | 30 |
| 1 | 15/10 (jue) | 27 | 30 |
| 2 | 16/10 (vie) | 24 | 30 |
| 3 | 19/10 (lun) | 21 | 30 |
| 4 | 20/10 (mar) | 18 | 30 |
| 5 | 21/10 (mié) | 15 | 30 |
| 6 | 22/10 (jue) | 12 | 30 |
| 7 | 23/10 (vie) | 9 | 30 |
| 8 | 26/10 (lun) | 6 | 30 |
| 9 | 27/10 (mar) | 3 | 30 |
| 10 | 28/10 (mié) | 0 | |

## Retrospectiva

| ¿Qué salió bien? | ¿Qué mejorar? | Acciones para el Sprint 3 |
|---|---|---|
| | | |

---


# Sprint 3 · 29/10 – 11/11/2026

> ⚪ **Plantilla: por planificar en el Sprint Planning.** Los responsables se asignan en la reunión.

**Meta:** Verificación de identidad del prestador (escaneo del DNI + reconocimiento facial) y moderación de cuentas.

| Story points | Horas | Capacidad | Holgura |
|:---:|:---:|:---:|:---:|
| 13 SP | 28 h | 3 × 10 h | 2 h |

> ⚠️ **Sprint de mayor riesgo técnico.** HU-19 y HU-20 dependen de la calidad del OCR, de la calibración del umbral de similitud facial y de que eldni.com esté disponible. Por eso se dejan **2 h de holgura** y el Sprint 4 sirve de margen si hay que ajustar algo. Requiere terminados HU-1 (modelo del prestador) y HU-9 (panel de revisión manual).

## Flujo de verificación del prestador

1. **Escanear el DNI** con la cámara → el OCR extrae número y nombres y recorta la foto del DNI.
2. **Consultar eldni.com** con el número → los nombres deben coincidir con los leídos.
3. **Selfie en vivo** (solo cámara, sin galería) con **parpadeo** como prueba de vida.
4. **Comparar** el rostro del DNI con la selfie → porcentaje de similitud.
5. **Decidir:**

| Resultado | Acción |
|---|---|
| Similitud alta y nombres coinciden | ✅ Verificado automáticamente |
| Zona gris o eldni.com no responde | 🟡 Revisión manual del administrador (HU-9) |
| Similitud baja | ❌ Rechazado, puede reintentar |

## Historias y tareas

| HU | Tarea | Descripción | Tipo | Horas | Responsable |
|:---:|:---:|---|:---:|:---:|:---:|
| HU-19 · Escaneo y validación del DNI | T19.1 | Pantalla de escaneo del DNI con la cámara (móvil) | Frontend | 3 | |
| | T19.2 | OCR del DNI: extraer número y nombres y recortar la foto | Backend | 4 | |
| | T19.3 | Consulta a eldni.com con validador intercambiable y paso a revisión manual si falla | Backend | 2 | |
| | T19.4 | Pruebas con DNIs de muestra (lectura y coincidencia de nombres) | Pruebas | 2 | |
| HU-20 · Reconocimiento facial | T20.1 | Selfie en vivo con detección de parpadeo (MediaPipe) | Frontend | 3 | |
| | T20.2 | Servicio de comparación facial DNI vs selfie (DeepFace) | Backend | 4 | |
| | T20.3 | Reglas de decisión (verificado / revisión / rechazado) e integración con el panel de HU-9 | Backend | 2 | |
| | T20.4 | Calibrar el umbral de similitud con fotos del equipo | Pruebas | 2 | |
| HU-21 · Consentimiento biométrico | T21.1 | Pantalla de consentimiento y registro de la aceptación | Frontend | 1 | |
| | T21.2 | Endpoint para solicitar la eliminación de los datos biométricos | Backend | 1 | |
| HU-13 · Suspensión y bloqueo | T13.1 | API en Django para suspender y bloquear cuentas | Backend | 2 | |
| | T13.2 | Pantalla web (React) de gestión de cuentas reportadas | Frontend | 2 | |
| | | **Total** | | **28** | |

### Criterios de aceptación de las historias nuevas

| HU | Historia | Criterios |
|:---:|---|---|
| HU-19 | Como **prestador**, quiero escanear mi DNI para que mis datos se lean y validen solos, a fin de registrarme sin escribirlos. | • El número leído pasa la validación del dígito verificador.<br>• Los nombres consultados en eldni.com coinciden con los del DNI (sin importar tildes ni mayúsculas).<br>• Si la fuente no responde o no coinciden, pasa a revisión manual; nunca se aprueba solo. |
| HU-20 | Como **prestador**, quiero tomarme una selfie en vivo que se compare con la foto de mi DNI, a fin de demostrar que soy el titular. | • Solo se acepta foto desde la cámara y tras detectar un parpadeo.<br>• Se guarda el porcentaje de similitud y la decisión con fecha y hora.<br>• En zona gris, el administrador ve ambas fotos lado a lado. |
| HU-21 | Como **prestador**, quiero aceptar el uso de mis datos biométricos y poder pedir que se borren, a fin de que se respete mi privacidad (Ley 29733). | • Sin consentimiento no se inicia la verificación facial.<br>• La solicitud de eliminación queda registrada y se atiende. |

## Carga por integrante

| Integrante | Backend | Frontend | Pruebas | Total |
|---|:---:|:---:|:---:|:---:|
| | | | | |
| | | | | |
| | | | | |

## Burndown

| Día | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Ideal (h)** | 25.2 | 22.4 | 19.6 | 16.8 | 14 | 11.2 | 8.4 | 5.6 | 2.8 | 0 |
| **Real (h)** | | | | | | | | | | |

## Retrospectiva

| ¿Qué salió bien? | ¿Qué mejorar? | Acciones para el Sprint 4 |
|---|---|---|
| | | |

---

# Sprint 4 · 12/11 – 25/11/2026

> ⚪ **Plantilla: por planificar en el Sprint Planning.** Las tareas y horas vienen del Lab 07; los responsables se asignan en la reunión.

**Meta:** Recomendación con IA, seguimiento en tiempo real y calificación del servicio.

| Story points | Horas | Capacidad | Holgura |
|:---:|:---:|:---:|:---:|
| 12 SP | 26 h | 3 × 10 h | 4 h |

> La holgura de 4 h sirve para terminar ajustes de la verificación del Sprint 3 si hiciera falta.

## Historias y tareas

| HU | Tarea | Descripción | Tipo | Horas | Responsable |
|:---:|:---:|---|:---:|:---:|:---:|
| HU-10 · Recomendación con IA | T10.1 | Definir la fórmula de puntaje (distancia, calificación y disponibilidad) | Backend | 2 | |
| | T10.2 | Implementar el servicio de recomendación y ranking | Backend | 5 | |
| | T10.3 | Mostrar el prestador recomendado con motivo y tiempo estimado | Frontend | 3 | |
| | T10.4 | Pruebas de la recomendación con datos de ejemplo | Pruebas | 1 | |
| HU-11 · Seguimiento en tiempo real | T11.1 | Modelo de estados y comunicación en tiempo real (WebSocket) | Backend | 4 | |
| | T11.2 | Pantalla de seguimiento en tiempo real (móvil y web) | Frontend | 5 | |
| | T11.3 | Pruebas de transición de estados | Pruebas | 2 | |
| HU-12 · Calificación del servicio | T12.1 | API para registrar calificaciones | Backend | 1 | |
| | T12.2 | Pantalla de calificación (1 a 5 estrellas y comentario) | Frontend | 2 | |
| | T12.3 | Pruebas: solo se califica con el servicio Finalizado | Pruebas | 1 | |
| | | **Total** | | **26** | |

## Carga por integrante

| Integrante | Backend | Frontend | Pruebas | Total |
|---|:---:|:---:|:---:|:---:|
| | | | | |
| | | | | |
| | | | | |

## Burndown

| Día | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Ideal (h)** | 23.4 | 20.8 | 18.2 | 15.6 | 13 | 10.4 | 7.8 | 5.2 | 2.6 | 0 |
| **Real (h)** | | | | | | | | | | |

## Retrospectiva

| ¿Qué salió bien? | ¿Qué mejorar? | Acciones finales |
|---|---|---|
| | | |

---

# Backlog futuro

Historias del Lab 07 que salen del alcance de los 4 sprints para dar prioridad a la seguridad del prestador. Se retoman si sobra capacidad o en una ampliación posterior.

| HU | Historia | SP | Horas | Motivo |
|:---:|---|:---:|:---:|---|
| HU-14 | Prestadores alternativos y reasignación | 2 | 5 | Depende de HU-10; mejora la experiencia pero no es crítica |
| HU-15 | Chatbot clasificador de oficios (IA) | 3 | 8 | La IA del proyecto ya se cubre con HU-10 y el reconocimiento facial (HU-20) |
| HU-16 | Gestión de rubros, categorías y comisiones | 2 | 5 | Los rubros del MVP pueden cargarse con datos iniciales |
| HU-17 | Configuración de parámetros del matching | 2 | 6 | Los parámetros pueden quedar fijos en la configuración |
| HU-18 | Dashboard de métricas operativas | 2 | 6 | Administrativo; no afecta al usuario final |
