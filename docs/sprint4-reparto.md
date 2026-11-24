# Sprint 4 · Reparto de tareas y pendientes — Chamba PE

**Fechas:** 12/11 – 25/11/2026 · **Preparado por:** Renzo León (Scrum Master) el 16/11/2026 · **Estado:** 🟡 propuesta del Scrum Master, se confirma en el Daily del 17/11.

Estado comprobado el 16/11 contra `origin/main` (`3a373b5`) y los PR de GitHub. Planificación general en [SPRINTS.md](../SPRINTS.md#sprint-4--1211--25112026); reglas en [PLAN_EQUIPO.md](../PLAN_EQUIPO.md).

---

## 1. Resumen

| Dato | Valor |
|---|---|
| Trabajo nuevo del Sprint 4 (HU-10, HU-11, HU-12) | 26 h · **ninguna tarea empezada** (sin ramas ni código) |
| Pendiente que pasa del Sprint 3 | 4 h (T19.4 y T8.3) + unir HU-21 (PR #38, ya construido) |
| Total a comprometer | **30 h = capacidad completa (3 × 10 h), sin holgura** |
| Días hábiles que quedan | 8 (16/11 – 25/11): **1,25 h diarias por integrante** |

> ⚠️ **Arranque tardío, como en el Sprint 2:** el 12/11 y el 13/11 pasaron sin Planning y sin horas. Las 4 h de holgura del Sprint 4 se usan en lo que quedó del Sprint 3. Si en el Daily del 20/11 quedan más de 15 h, el Product Owner decide qué sale (candidata: T10.3, pantalla de la recomendación).

**Fuera del compromiso (no caben):** HU-13 (4 h), HU-20 (11 h) y la app Android (PR #39, sin estimar). Lo decide el PO; ver [decisiones](#5-decisiones-que-necesita-el-product-owner).

---

## 2. Pendientes de sprints anteriores

| Qué | De quién | Horas | Situación al 16/11 | Qué falta |
|---|---|:---:|---|---|
| **HU-21** · T21.1 y T21.2 | Luis (revisa David) | 2 | Construido en el [PR #38](https://github.com/RXDG2908/Chamba-PE/pull/38), abierto desde el 12/11, sin conflictos y **sin revisión** | Revisión de David y unir a `main`. El PO define el texto del consentimiento (`demo-dni-v2`), la retención y quién corre `procesar_eliminaciones_dni` |
| **T19.4** · pruebas de HU-19 con muestras de DNI | Luis | 2 | **Sin iniciar:** sin rama ni PR desde el 29/10 | Todo. Se propone pasarla a David (ver reparto) |
| **T8.3** · pruebas de entrega del push (< 5 s) | David | 2 | Ya no está bloqueada: HU-8 entró a `main` el 12/11 (#37). El [PR #22](https://github.com/RXDG2908/Chamba-PE/pull/22) (base parcial) tiene **conflictos** | Configurar FCM real, medir la entrega y dejar la evidencia; actualizar o cerrar el #22 |
| **HU-8** · T8.1 y T8.2 | Luis | — | En `main` con el [PR #37](https://github.com/RXDG2908/Chamba-PE/pull/37), que **unió el propio autor sin revisión** | Queda abierta hasta T8.3. Registrar en AVANCES y SPRINTS |
| [PR #34](https://github.com/RXDG2908/Chamba-PE/pull/34) · nombre en `/yo` según el rol | David | — | Abierto desde el 05/11, con **conflictos**, sin revisión | Traer `main`, resolver y pedir revisión a Renzo (dueño de `usuarios`) |
| Guías de navegación | David | — | `docs/GUIA_NAVEGACION.md` y `docs/GUIA_T6.3.md` siguen con el registro en `/` (cambió con el #32) | Actualizarlas |
| HU-19 · pruebas reales | Renzo | — | Sin probar la cámara en un celular ni EasyOCR con una imagen real | Se hace junto con T19.4 |
| Proceso | Renzo (SM) | — | `main` **sigue sin protección** (impedimento 14); retrospectivas de los Sprints 2 y 3 vacías; burndown y cierre del Sprint 3 sin registrar; TABLERO sin actualizar desde el 04/11 | Ver tareas del Scrum Master |
| AVANCES y SPRINTS | Luis | — | Sin entradas de Luis en la bitácora desde el 15/10; el plan del Sprint 3 aún dice HU-8 «local/parcial» y HU-21 «sin iniciar» | Registrar #33, #37 y #38 |

---

## 3. Reparto del Sprint 4

Cada uno construye una historia completa y tiene backend; las pruebas las hace otro integrante.

### Renzo León — 10 h · HU-10 (app `ia`, nueva)

| Tarea | Descripción | Tipo | Horas |
|:---:|---|:---:|:---:|
| T10.1 | Definir la fórmula de puntaje (distancia, calificación y disponibilidad) | Backend | 2 |
| T10.2 | Servicio de recomendación y ranking | Backend | 5 |
| T10.3 | Mostrar el prestador recomendado con motivo y tiempo estimado | Frontend | 3 |
| | **Subtotal** | | **10** |

### Luis Abad — 10 h · HU-11 (app `servicios`)

| Tarea | Descripción | Tipo | Horas |
|:---:|---|:---:|:---:|
| T11.1 | Modelo de estados (Confirmado → En camino → En servicio → Finalizado) y tiempo real (WebSocket) | Backend | 4 |
| T11.2 | Pantalla de seguimiento en tiempo real (web responsive) | Frontend | 5 |
| T12.3 | Pruebas de HU-12: solo se califica con el servicio Finalizado | Pruebas | 1 |
| | **Subtotal** | | **10** |

### David Valcarcel — 10 h · HU-12 y pruebas cruzadas

| Tarea | Descripción | Tipo | Horas |
|:---:|---|:---:|:---:|
| T12.1 | API para registrar calificaciones y actualizar el promedio del prestador | Backend | 1 |
| T12.2 | Pantalla de calificación (1 a 5 estrellas y comentario) | Frontend | 2 |
| T10.4 | Pruebas de la recomendación con datos de ejemplo | Pruebas | 1 |
| T11.3 | Pruebas de transición de estados | Pruebas | 2 |
| T8.3 | Pruebas de entrega del push (pasa del Sprint 3) | Pruebas | 2 |
| T19.4 | Pruebas de HU-19 con muestras ficticias de DNI (pasa del Sprint 3; **antes de Luis**) | Pruebas | 2 |
| | **Subtotal** | | **10** |

> **Cambio propuesto:** T19.4 pasa de Luis a David. Motivo: lleva 2 semanas sin iniciar y Luis tiene la historia más pesada y riesgosa del sprint (HU-11, WebSocket). Si Luis ya tiene avance local, lo dice en el Daily del 17/11 y se reacomoda.

### Carga y pruebas cruzadas

| Integrante | Backend | Frontend | Pruebas | Total |
|---|:---:|:---:|:---:|:---:|
| Renzo León | 7 h | 3 h | — | **10 h** |
| Luis Abad | 4 h | 5 h | 1 h | **10 h** |
| David Valcarcel | 1 h | 2 h | 7 h | **10 h** |
| **Equipo** | **12 h** | **10 h** | **8 h** | **30 h** |

| Historia | Construye | Prueba | Revisa el PR |
|:---:|---|---|---|
| HU-10 | Renzo | David (T10.4) | David |
| HU-11 | Luis | David (T11.3) | David |
| HU-12 | David | Luis (T12.3) | Luis (dueño de `servicios`) |
| HU-19 | Renzo | David (T19.4) | — |
| HU-8 | Luis | David (T8.3) | — |
| HU-21 (#38) | Luis | — | David |

### Tareas del Scrum Master (Renzo, sin horas del sprint)

- Activar la protección de `main`: solo por PR con 1 aprobación (impedimento 14).
- Cerrar el Sprint 3: burndown, Review y retrospectiva; completar la retrospectiva del Sprint 2.
- Actualizar SPRINTS y TABLERO cuando el equipo confirme este reparto.
- Revisar el PR #34 de David.

---

## 4. Orden de trabajo

Dependencias: **T11.1 → T12.1** (la calificación necesita el estado Finalizado) · **T12.1 → T10.2** (el puntaje usa el promedio de calificaciones) · **T11.1 → T11.2** · cada prueba va después del PR de su historia.

| Momento | Renzo | Luis | David |
|---|---|---|---|
| **16/11 – 17/11** · bloqueantes | T10.1 fórmula escrita en `docs/` (con «sin calificaciones» = valor neutro) · proteger `main` | **Contrato de estados y del WebSocket en `docs/` (17/11)** · inicio de T11.1 | **Revisar y unir el #38** · T8.3 (ya tiene el push en `main`) · arreglar el #34 |
| **18/11 – 20/11** | T10.2 servicio de recomendación | T11.1 (PR a más tardar el 19/11) · T11.2 | T12.1 y T12.2 · T19.4 con muestras ficticias |
| **23/11 – 25/11** | T10.3 pantalla · correcciones | T11.2 · T12.3 | T10.4 · T11.3 · evidencia de T8.3 |

**Acuerdos para el Daily del 17/11:**

1. **Estados de HU-11:** nombres exactos y quién cambia cada uno. Luis lo deja escrito antes de codificar; David y Renzo trabajan contra ese contrato sin esperar el código.
2. **WebSocket:** Django Channels obliga a pasar a ASGI y agregar librerías. El PR de T11.1 actualiza `requirements.txt` y quien revisa instala desde cero. Si el 19/11 no funciona, plan B: consulta periódica cada 5 s (como hoy en T4.2) y se registra como deuda.
3. **Quién califica:** la `Solicitud` guarda nombre y teléfono, no la cuenta del cliente. David y Luis acuerdan cómo se identifica al cliente que califica.
4. **Nadie une su propio PR.** Revisión en máximo 1 día.

**Cierre (25/11):** Review con demo de recomendación → solicitud → seguimiento hasta Finalizado → calificación, y Retrospectiva final.

### Burndown (30 h)

| Día | Fecha | Ideal (h) | Real (h) |
|:---:|:---:|:---:|:---:|
| Inicio | 12/11 | 30 | 30 |
| 1 | 12/11 (jue) | 27 | 30 |
| 2 | 13/11 (vie) | 24 | 30 |
| 3 | 16/11 (lun) | 21 | 30 |
| 4 | 17/11 (mar) | 18 | |
| 5 | 18/11 (mié) | 15 | |
| 6 | 19/11 (jue) | 12 | |
| 7 | 20/11 (vie) | 9 | |
| 8 | 23/11 (lun) | 6 | |
| 9 | 24/11 (mar) | 3 | |
| 10 | 25/11 (mié) | 0 | |

---

## 5. Decisiones que necesita el Product Owner

| # | Decisión | Propuesta del Scrum Master |
|:---:|---|---|
| 1 | HU-13 (4 h) y HU-20 (11 h), postergadas del Sprint 3 | No caben: pasan al backlog futuro |
| 2 | App Android ([PR #39](https://github.com/RXDG2908/Chamba-PE/pull/39)): no está en el Sprint 4 ni tiene horas; cambia `usuarios` y `prestadores` y se aparta del plan de Luis (usa Django + Firebase, no Spring) | Fuera del compromiso. **16/11:** Renzo lo unió a `main` para no frenar el trabajo, con revisión automática y sin la de otro integrante; David y Luis lo revisan sobre `main` ([detalle](android-reparto.md)) |
| 3 | HU-21: texto del consentimiento, retención y responsable de atender las eliminaciones | Definirlo al revisar el #38 |
| 4 | Validador `eldni` (hoy siempre responde «no disponible») | Dejarlo apagado: todo va a revisión manual |
| 5 | Horas del PR #32 (menú por rol y diseño) como uso de reserva del Sprint 3 | Registrarlas contra la reserva de David (2 h); David informa las horas reales |
| 6 | Pasar T19.4 de Luis a David | Aprobar |

---

## 6. Mensajes para el equipo

### Para el grupo

> Equipo, ya vamos en el día 3 del Sprint 4 (12/11 – 25/11) y todavía no hay nada empezado de HU-10, HU-11 ni HU-12. Armé el reparto en `docs/sprint4-reparto.md`: son 30 h, 10 h cada uno, sin holgura, porque también entran T19.4 y T8.3 que quedaron del Sprint 3. Nos quedan 8 días hábiles, más o menos 1 h 15 min diaria cada uno.
>
> Renzo: HU-10 (recomendación). Luis: HU-11 (seguimiento en tiempo real) y la prueba de HU-12. David: HU-12 (calificación) y las pruebas cruzadas.
>
> Mañana 17/11 en el Daily lo confirmamos y cerramos el contrato de estados de HU-11, que bloquea a los demás. Dos reglas: nadie une su propio PR y AVANCES.md se actualiza cada día. Lo primero también va por mí: hoy uní el #39 y el #40 sin revisión para ganar tiempo; desde mañana, con `main` protegido, ya no se puede. Si algo no les cuadra, avisen hoy.

### Para Luis

> Luis, te cuento lo tuyo:
>
> **Del Sprint 3**
> - HU-21 (PR #38) está completo pero sin revisión; ya le pedí a David que lo revise para unirlo.
> - HU-8 entró a `main` con el #37, pero lo uniste tú mismo sin revisión. Para los siguientes, esperemos la aprobación de otro.
> - T19.4 no tiene rama ni PR desde el 29/10. Propongo pasársela a David para que te concentres en HU-11. Si ya tienes algo avanzado, dímelo y lo vemos.
> - Falta que registres en AVANCES.md y SPRINTS.md lo de los PR #33, #37 y #38 (no hay entradas tuyas desde el 15/10).
>
> **Sprint 4 (10 h)**
> - T11.1 modelo de estados + WebSocket (4 h): es la tarea que bloquea a David y a mí. Necesito el contrato de estados escrito en `docs/` el 17/11 y el PR a más tardar el 19/11.
> - T11.2 pantalla de seguimiento (5 h).
> - T12.3 prueba de la calificación de David (1 h).
>
> Si Channels se complica, el 19/11 pasamos al plan B (consulta cada 5 s) y lo dejamos como deuda.

### Para David

> David, te cuento lo tuyo:
>
> **Del Sprint 3**
> - T8.3 ya no está bloqueada: el push de HU-8 está en `main` desde el 12/11. Falta configurar FCM real, medir los < 5 s y dejar la evidencia. El PR #22 tiene conflictos: actualízalo o ciérralo.
> - El PR #38 (HU-21 de Luis) espera tu revisión desde el 12/11. Es lo primero, por favor.
> - Tu PR #34 lleva abierto desde el 05/11 y tiene conflictos; trae `main` y te lo reviso.
> - Faltan actualizar `GUIA_NAVEGACION.md` y `GUIA_T6.3.md` (el registro ya no está en `/`).
>
> **Sprint 4 (10 h)**
> - T12.1 y T12.2: API y pantalla de calificación (3 h).
> - Pruebas cruzadas: T10.4 (1 h), T11.3 (2 h), T8.3 (2 h) y T19.4 (2 h), que propongo pasarte de Luis.
>
> **Como PO necesito que decidas** (sección 5 del documento): HU-13 y HU-20 al backlog, qué hacemos con la app Android (PR #39), el texto y la retención del consentimiento de HU-21, y las horas del PR #32.
