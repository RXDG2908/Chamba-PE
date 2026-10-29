# Tablero del Sprint — Chamba PE

## Sprint 2 · 15/10 – 28/10/2026 (cerrado)

**Actualizado:** 29/10/2026 · **Meta:** cuentas, disponibilidad, mapa y notificaciones; prestadores verificados por administración. Reparto y orden en [SPRINTS.md](SPRINTS.md#sprint-2--1510--28102026).

Resumen: **7 terminadas · 4 en revisión · 0 en curso · 0 por hacer · 3 pasan al Sprint 3** · 15 de 23 h terminadas (HU-8, 7 h, salió del sprint por decisión del PO).

> 29/10 (inicio del Sprint 3): T6.3 (#20) y T7.3 (#23) se unieron a `main` y pasan a Terminado; quedan en revisión T7.1 (#14) y T7.2 (#16).

> 🟣 **Decisión del Product Owner (David, 28/10):** HU-8 (T8.1, T8.2 y T8.3, 7 h) pasa completa al Sprint 3, porque T8.1 no se empezó. La prueba de la bandeja de T8.3 queda como base parcial.

### 👀 En revisión (2) · pasan al inicio del Sprint 3

| Tarjeta | HU | Responsable | Horas | PR | Nota |
|---|:---:|---|:---:|:---:|---|
| **T7.1** Consulta por coordenadas dentro del radio | HU-7 | Luis Abad | 2 | [#14](https://github.com/RXDG2908/Chamba-PE/pull/14) | Falta asignar quién revisa |
| **T7.2** Mapa con marcadores | HU-7 | Luis Abad | 3 | [#16](https://github.com/RXDG2908/Chamba-PE/pull/16) | Incluye T7.1; se une después del #14 |

### ➡️ Pasan al Sprint 3 (3)

| Tarjeta | HU | Responsable | Horas | Nota |
|---|:---:|---|:---:|---|
| **T8.1** Notificaciones push (FCM) | HU-8 | Luis Abad | 3 | No se empezó en el Sprint 2 |
| **T8.2** Recepción y aceptar/rechazar desde la notificación | HU-8 | Luis Abad | 2 | Depende de T8.1 |
| **T8.3** Pruebas de entrega (< 5 s) | HU-8 | David Valcarcel | 2 | Base parcial en [#22](https://github.com/RXDG2908/Chamba-PE/pull/22): 7 pruebas de la bandeja. Falta la prueba del push |

### ✅ Terminado (9)

| Tarjeta | HU | Responsable | Horas | PR | Nota |
|---|:---:|---|:---:|:---:|---|
| **T5.1** API de autenticación con roles | HU-5 | Renzo León | 3 | [#13](https://github.com/RXDG2908/Chamba-PE/pull/13) | Unido el 28/10. [Contrato](docs/API_AUTENTICACION.md) |
| **T6.1** Endpoint de disponibilidad | HU-6 | Renzo León | 2 | [#15](https://github.com/RXDG2908/Chamba-PE/pull/15) | Unido el 28/10 |
| **T5.2** Pantallas de registro e inicio de sesión | HU-5 | Renzo León | 3 | [#17](https://github.com/RXDG2908/Chamba-PE/pull/17) | Unido el 28/10 |
| **T6.2** Interruptor de disponibilidad | HU-6 | Renzo León | 2 | [#18](https://github.com/RXDG2908/Chamba-PE/pull/18) | Unido el 28/10 |
| **T9.1** Modelo y API de revisión de documentos | HU-9 | David Valcarcel | 2 | [#11](https://github.com/RXDG2908/Chamba-PE/pull/11) | Unido el 28/10; usa `EsAdministrador` |
| **T9.2** Pantalla de aprobar o rechazar | HU-9 | David Valcarcel | 2 | [#11](https://github.com/RXDG2908/Chamba-PE/pull/11) | Unido el 28/10, junto con T9.1 |
| **T5.3** Pruebas de autenticación y rol | HU-5 | David Valcarcel | 1 | [#21](https://github.com/RXDG2908/Chamba-PE/pull/21) | Unido el 28/10; 3 fallos esperados por el impedimento 16 |
| **T6.3** Pruebas: solo activos en la búsqueda | HU-6 | David Valcarcel | 1 | [#20](https://github.com/RXDG2908/Chamba-PE/pull/20) | Unido el 29/10 (inicio del Sprint 3). [Guía manual](docs/GUIA_T6.3.md) |
| **T7.3** Pruebas del radio de cobertura | HU-7 | David Valcarcel | 2 | [#23](https://github.com/RXDG2908/Chamba-PE/pull/23) | Unido el 29/10 (inicio del Sprint 3). [Guía de navegación](docs/GUIA_NAVEGACION.md) |

---

## Sprint 1 · 01/10 – 14/10/2026 (cerrado)

Tablero estilo Trello del Sprint 1. Cada tarjeta es una tarea; al avanzar, se mueve a la columna siguiente. El detalle de cada tarea está en [AVANCES.md](AVANCES.md) y la planificación en [SPRINTS.md](SPRINTS.md).

**Actualizado:** 18/10/2026 · **Sprint 1:** ✅ cerrado el 14/10/2026 · **Meta:** el prestador se registra y el cliente lo encuentra y le envía una solicitud.

| Columna | Cuándo pasa una tarjeta ahí |
|---|---|
| 📋 Por hacer | Está planificada pero nadie la empezó |
| 🔨 En curso | Hay código avanzado, pero falta terminarla o integrarla |
| 👀 En revisión | Está completa y espera la revisión del PR |
| ✅ Terminado | Está en `main` por un PR revisado y cumple los criterios de aceptación |

Resumen: **13 terminadas · 0 en revisión · 0 en curso · 0 por hacer** · 26 de 30 h terminadas dentro del sprint. T2.2 y T3.4 pasaron al Sprint 2 y se terminaron el 18/10 con el PR 9.

---

### 📋 Por hacer (0)

Sin tarjetas.

### 🔨 En curso (0)

Sin tarjetas.

### 👀 En revisión (0)

Sin PR abiertos: del #1 al #9 están unidos a `main`.

### ✅ Terminado (13)

| Tarjeta | HU | Responsable | Horas | PR | Nota |
|---|:---:|---|:---:|:---:|---|
| **T1.1** Modelo de datos del prestador | HU-1 | Renzo León | 2 | [#1](https://github.com/RXDG2908/Chamba-PE/pull/1) | `Rubro` y `Prestador` con cobertura y estado |
| **T1.2** API de registro de prestadores | HU-1 | Renzo León | 3 | [#2](https://github.com/RXDG2908/Chamba-PE/pull/2) | Registro con validaciones; 7 pruebas |
| **T1.3** Formulario de registro | HU-1 | Renzo León | 2 | [#2](https://github.com/RXDG2908/Chamba-PE/pull/2) | Formulario React con rubros y mapa de cobertura |
| **T3.1** Búsqueda por rubro, radio y disponibilidad | HU-3 | Luis Abad | 3 | [#3](https://github.com/RXDG2908/Chamba-PE/pull/3) | `GET /api/prestadores/buscar/`; 11 pruebas |
| **T3.2** Pantalla de resultados | HU-3 | Luis Abad | 4 | [#3](https://github.com/RXDG2908/Chamba-PE/pull/3) | Rubros, mapa Leaflet y tarjetas con datos reales |
| **T4.1** Flujo de solicitud: envío y aceptación o rechazo | HU-4 | Renzo León | 3 | [#4](https://github.com/RXDG2908/Chamba-PE/pull/4) | API de solicitudes; 12 pruebas |
| **T2.1** API de carga de certificaciones | HU-2 | David Valcarcel | 2 | [#6](https://github.com/RXDG2908/Chamba-PE/pull/6) | Certificado vinculado al prestador; 400 si falta |
| **T3.3** Ordenar resultados por distancia | HU-3 | David Valcarcel | 2 | [#6](https://github.com/RXDG2908/Chamba-PE/pull/6) | Orden ascendente en la búsqueda real |
| **T4.2** Pantallas de solicitud y cambio de estado | HU-4 | David Valcarcel | 2 | [#6](https://github.com/RXDG2908/Chamba-PE/pull/6) | Solicitar desde la tarjeta y ver Aceptada o Rechazada; validaciones en [#7](https://github.com/RXDG2908/Chamba-PE/pull/7) |
| **T2.3** Pruebas de registro y carga | HU-1/2 | Luis Abad | 1 | [#7](https://github.com/RXDG2908/Chamba-PE/pull/7) | Registro y carga, incluida la asociación del certificado. [Informe](docs/pruebas-T2.3.md) |
| **T4.3** Pruebas buscar → solicitar → aceptar | HU-4 | Luis Abad | 2 | [#7](https://github.com/RXDG2908/Chamba-PE/pull/7) | Envío, Aceptada, Rechazada y validaciones desde React. [Informe](docs/pruebas-T4.3.md) |
| **T2.2** Componente de carga de archivos | HU-2 | David Valcarcel | 2 | [#9](https://github.com/RXDG2908/Chamba-PE/pull/9) | Pasó del Sprint 1; ruta `/certificaciones` en el menú y envío del prestador (18/10) |
| **T3.4** Datos de prueba y respuesta < 3 s | HU-3 | David Valcarcel | 2 | [#9](https://github.com/RXDG2908/Chamba-PE/pull/9) | Pasó del Sprint 1; `manage.py medir_busqueda` sobre la búsqueda real, máx. 32,5 ms (18/10) |

#### Otros entregables hechos (sin horas del sprint)

| Fecha | Qué | Quién |
|:---:|---|---|
| 01/10 | Planificación del Sprint 1, reparto de tareas y documentación (README, PLAN_EQUIPO, SPRINTS) | Renzo León |
| 01/10 | Lab 07 actualizado con la verificación del prestador y exportado a Word | Renzo León |
| 05/10 | 10 rubros iniciales cargados con una migración | Renzo León |
| 06/10 | Maquetas de las 21 historias de usuario (`docs/renders/historias-usuario.html`) | Renzo León |
| 06/10 | Documentación de la API de búsqueda (`docs/API_BUSQUEDA.md`) | Luis Abad |
| 12/10 | AVANCES actualizado con el estado real de todas las tareas | Renzo León |
| 12/10 | Informes de pruebas T2.3 y T4.3 (`docs/pruebas-T2.3.md`, `docs/pruebas-T4.3.md`) | Luis Abad |
| 12/10 | Revisión y prueba combinada de los PR 3 y 4 (backend, frontend y navegador) | Renzo León |
| 14/10 | Pruebas manuales de búsqueda en móvil y escritorio (PR 5) | Luis Abad |
| 15/10 | Validaciones de nombre, teléfono y descripción, y tarjeta corregida (PR 7) | Luis Abad |
| 15/10 | Verificación final de `main` y cierre del Sprint 1 | Renzo León |

---

### Estado por historia

| HU | Historia | Por hacer | En curso | En revisión | Terminado |
|:---:|---|:---:|:---:|:---:|:---:|
| HU-1 | Registro del prestador | — | — | — | T1.1 · T1.2 · T1.3 |
| HU-2 | Carga de certificaciones | — | — | — | T2.1 · T2.2 · T2.3 |
| HU-3 | Búsqueda de prestadores | — | — | — | T3.1 · T3.2 · T3.3 · T3.4 |
| HU-4 | Solicitud y aceptación | — | — | — | T4.1 · T4.2 · T4.3 |

## Próximos sprints (backlog)

| Sprint | Fechas | Historias |
|:---:|:---:|---|
| 3 | 29/10 – 11/11 | HU-19 Escaneo del DNI · HU-20 Reconocimiento facial · HU-21 Consentimiento biométrico · HU-13 Suspensión y bloqueo |
| 4 | 12/11 – 25/11 | HU-10 Recomendación con IA · HU-11 Seguimiento en tiempo real · HU-12 Calificación |
| Futuro | — | HU-14 a HU-18 |
