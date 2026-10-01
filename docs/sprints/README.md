# Sprints — Chamba PE

Planificación Scrum del proyecto: 18 historias de usuario, 51 story points, 4 sprints de 2 semanas, 30 h por sprint (10 h por integrante).

El detalle original de historias, criterios de aceptación y estimaciones está en [`../Lab07_CPS_ChambaYa.md`](../Lab07_CPS_ChambaYa.md).

## Calendario

> Las fechas se recorrieron 2 semanas respecto del Lab 07 porque el Sprint 1 empezó el 01/10/2026.

| Sprint | Fechas | Historias | SP | Horas | Documento |
|:---:|:---:|:---:|:---:|:---:|---|
| 1 | 01/10 – 14/10/2026 | HU-1 a HU-4 | 13 | 30 h | [sprint-1.md](sprint-1.md) |
| 2 | 15/10 – 28/10/2026 | HU-5 a HU-9 | 13 | 30 h | _por planificar_ |
| 3 | 29/10 – 11/11/2026 | HU-10 a HU-13 | 14 | 30 h | _por planificar_ |
| 4 | 12/11 – 25/11/2026 | HU-14 a HU-18 | 11 | 30 h | _por planificar_ |

## Equipo

| Integrante | GitHub | Rol Scrum |
|---|---|---|
| Renzo León | [@RXDG2908](https://github.com/RXDG2908) | Scrum Master / Desarrollador |
| Luis Abad | [@Itssmann](https://github.com/Itssmann) | Development Team |
| David Valcarcel | [@Davish-pxl](https://github.com/Davish-pxl) | Product Owner / Desarrollador |

## Reglas de reparto

1. **Historias completas por persona.** Cada integrante construye modelo, API en Django y pantalla de sus historias, así los tres trabajan en el backend en todos los sprints.
2. **10 h por integrante por sprint** (1 h diaria × 5 días × 2 semanas). No se compromete más trabajo del que cabe.
3. **Pruebas cruzadas.** Las tareas de pruebas las hace un integrante distinto de quien construyó la historia.
4. **Apps de Django con dueño** para no pisarse el código:

   | App | Dueño principal | Contenido |
   |---|---|---|
   | `prestadores` | Renzo | Perfil, rubros, cobertura, certificaciones |
   | `servicios` | Luis | Búsqueda, mapa, solicitudes, notificaciones, seguimiento |
   | `usuarios` | Renzo | Cuentas, login con roles, disponibilidad |
   | `administracion` | David | Verificación, moderación, rubros, parámetros, métricas |

   Si una tarea toca la app de otro, se coordina con el dueño y él revisa el Pull Request.

## Flujo de trabajo en Git

1. Una rama por tarea o historia: `hu-1-registro-prestador`, `hu-3-busqueda`, etc.
2. Commits pequeños que mencionen la tarea: `T1.2 API de registro de prestadores`.
3. Pull Request hacia `main`; lo revisa el compañero responsable de las pruebas de esa historia.
4. Nadie sube directo a `main`.
