# Tablero del Sprint — Chamba PE

Tablero estilo Trello del Sprint 1. Cada tarjeta es una tarea; al avanzar, se mueve a la columna siguiente. El detalle de cada tarea está en [AVANCES.md](AVANCES.md) y la planificación en [SPRINTS.md](SPRINTS.md).

**Actualizado:** 12/10/2026 · **Sprint 1:** 01/10 – 14/10/2026 · **Meta:** el prestador se registra y el cliente lo encuentra y le envía una solicitud.

| Columna | Cuándo pasa una tarjeta ahí |
|---|---|
| 📋 Por hacer | Está planificada pero nadie la empezó |
| 🔨 En curso | Hay código avanzado, pero falta terminarla o integrarla |
| 👀 En revisión | Está completa y espera la revisión del PR |
| ✅ Terminado | Está en `main` por un PR revisado y cumple los criterios de aceptación |

Resumen: **3 terminadas · 3 en revisión · 5 en curso · 2 por hacer** · 7 de 30 h terminadas.

---

## 📋 Por hacer (2)

| Tarjeta | HU | Responsable | Horas | Nota |
|---|:---:|---|:---:|---|
| **T2.3** Pruebas de registro y carga | HU-1/2 | Luis Abad | 1 | Espera que el certificado quede vinculado al prestador (T2.1) |
| **T4.3** Pruebas buscar → solicitar → aceptar | HU-4 | Luis Abad | 2 | Espera los PR #3 y #4 y la pantalla de T4.2 |

## 🔨 En curso (5)

| Tarjeta | HU | Responsable | Horas | Nota |
|---|:---:|---|:---:|---|
| **T2.1** API de carga de certificaciones | HU-2 | David Valcarcel | 2 | Valida PDF/JPG y 5 MB; falta vincular el certificado al prestador |
| **T2.2** Componente de carga de archivos | HU-2 | David Valcarcel | 2 | Está en `src/` de la raíz; falta llevarlo a `frontend/` y corregir el lint (`err` sin usar) |
| **T3.3** Ordenar resultados por distancia | HU-3 | David Valcarcel | 2 | Hecho en un endpoint aparte; falta aplicarlo en la búsqueda de Luis |
| **T3.4** Datos de prueba y respuesta < 3 s | HU-3 | David Valcarcel | 2 | Medido sobre el endpoint aparte; falta medir la búsqueda real |
| **T4.2** Pantallas de solicitud y cambio de estado | HU-4 | David Valcarcel | 2 | El commit subió una pantalla de certificados; falta la de solicitud conectada a la API de T4.1 |

## 👀 En revisión (3)

| Tarjeta | HU | Responsable | Horas | PR | Nota |
|---|:---:|---|:---:|:---:|---|
| **T3.1** Búsqueda por rubro, radio y disponibilidad | HU-3 | Luis Abad | 3 | [#3](https://github.com/RXDG2908/Chamba-PE/pull/3) | `GET /api/prestadores/buscar/`; 11 pruebas. Revisa Renzo |
| **T3.2** Pantalla de resultados | HU-3 | Luis Abad | 4 | [#3](https://github.com/RXDG2908/Chamba-PE/pull/3) | Rubros, mapa Leaflet y tarjetas con datos reales |
| **T4.1** Flujo de solicitud: envío y aceptación o rechazo | HU-4 | Renzo León | 3 | [#4](https://github.com/RXDG2908/Chamba-PE/pull/4) | API de solicitudes; 12 pruebas. Revisa Luis; se une después del #3 |

## ✅ Terminado (3)

| Tarjeta | HU | Responsable | Horas | PR | Nota |
|---|:---:|---|:---:|:---:|---|
| **T1.1** Modelo de datos del prestador | HU-1 | Renzo León | 2 | [#1](https://github.com/RXDG2908/Chamba-PE/pull/1) | `Rubro` y `Prestador` con cobertura y estado |
| **T1.2** API de registro de prestadores | HU-1 | Renzo León | 3 | [#2](https://github.com/RXDG2908/Chamba-PE/pull/2) | Registro con validaciones; 7 pruebas |
| **T1.3** Formulario de registro | HU-1 | Renzo León | 2 | [#2](https://github.com/RXDG2908/Chamba-PE/pull/2) | Formulario React con rubros y mapa de cobertura |

### Otros entregables hechos (sin horas del sprint)

| Fecha | Qué | Quién |
|:---:|---|---|
| 01/10 | Planificación del Sprint 1, reparto de tareas y documentación (README, PLAN_EQUIPO, SPRINTS) | Renzo León |
| 01/10 | Lab 07 actualizado con la verificación del prestador y exportado a Word | Renzo León |
| 05/10 | 10 rubros iniciales cargados con una migración | Renzo León |
| 06/10 | Maquetas de las 21 historias de usuario (`docs/renders/historias-usuario.html`) | Renzo León |
| 06/10 | Documentación de la API de búsqueda (`docs/API_BUSQUEDA.md`) | Luis Abad |
| 12/10 | AVANCES actualizado con el estado real de todas las tareas | Renzo León |

---

## Estado por historia

| HU | Historia | Por hacer | En curso | En revisión | Terminado |
|:---:|---|:---:|:---:|:---:|:---:|
| HU-1 | Registro del prestador | — | — | — | T1.1 · T1.2 · T1.3 |
| HU-2 | Carga de certificaciones | T2.3 | T2.1 · T2.2 | — | — |
| HU-3 | Búsqueda de prestadores | — | T3.3 · T3.4 | T3.1 · T3.2 | — |
| HU-4 | Solicitud y aceptación | T4.3 | T4.2 | T4.1 | — |

## Próximos sprints (backlog)

| Sprint | Fechas | Historias |
|:---:|:---:|---|
| 2 | 15/10 – 28/10 | HU-5 Registro e inicio de sesión · HU-6 Disponibilidad · HU-7 Mapa · HU-8 Notificaciones · HU-9 Validación por el administrador |
| 3 | 29/10 – 11/11 | HU-19 Escaneo del DNI · HU-20 Reconocimiento facial · HU-21 Consentimiento biométrico · HU-13 Suspensión y bloqueo |
| 4 | 12/11 – 25/11 | HU-10 Recomendación con IA · HU-11 Seguimiento en tiempo real · HU-12 Calificación |
| Futuro | — | HU-14 a HU-18 |
