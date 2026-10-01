# Plan de Equipo — Chamba PE

Cómo se organiza el equipo, cómo se reparten las horas y cómo se trabaja en el código.

---

## 1. Integrantes

| Integrante | GitHub | Rol Scrum | Responsabilidad principal |
|---|---|---|---|
| Renzo León | [@RXDG2908](https://github.com/RXDG2908) | Scrum Master / Desarrollador | Vela por el proceso Scrum, facilita las reuniones y actualiza el burndown |
| Luis Abad | [@Itssmann](https://github.com/Itssmann) | Development Team | Desarrollo y pruebas |
| David Valcarcel | [@Davish-pxl](https://github.com/Davish-pxl) | Product Owner / Desarrollador | Prioriza el backlog y define los criterios de aceptación |

---

## 2. Capacidad del equipo

| Concepto | Valor |
|---|---|
| Duración del sprint | 2 semanas |
| Dedicación por integrante | 1 h diaria × 5 días = 5 h por semana |
| Horas por integrante por sprint | **10 h** |
| Capacidad del equipo por sprint | 3 × 10 h = **30 h** |
| Total del proyecto | 4 sprints × 30 h = **120 h** · 51 story points |

---

## 3. Reglas de reparto

1. **Historias completas por persona.** Cada integrante construye el modelo, la API en Django y la pantalla de sus historias. Así los tres trabajan en el backend en todos los sprints, no solo uno.
2. **10 h por integrante en cada sprint.** No se compromete más trabajo del que cabe; si algo no entra, pasa al siguiente sprint.
3. **Pruebas cruzadas.** Las tareas de pruebas las hace un integrante distinto de quien construyó la historia. Así todos conocen el código de los demás.
4. **Cada uno tiene backend.** En cada sprint, cada integrante tiene al menos una tarea de backend en Django.

---

## 4. Stack tecnológico

| Capa | Tecnología |
|---|---|
| Backend | Django + Django REST Framework |
| Frontend web y panel de administración | React |
| App móvil | _por definir_ |
| Tiempo real | WebSocket (Django Channels) |
| Notificaciones | Firebase Cloud Messaging |
| IA | Módulo de recomendación y clasificación integrado al backend |

---

## 5. Organización del backend

Cada app de Django tiene un dueño que revisa los cambios que otros hagan en ella.

| App | Dueño | Contenido | Historias |
|---|---|---|---|
| `prestadores` | Renzo | Perfil, rubros, cobertura, certificaciones | HU-1, HU-2 |
| `usuarios` | Renzo | Cuentas, login con roles, disponibilidad | HU-5, HU-6 |
| `servicios` | Luis | Búsqueda, mapa, solicitudes, notificaciones, seguimiento, calificación | HU-3, HU-4, HU-7, HU-8, HU-11, HU-12, HU-14 |
| `administracion` | David | Verificación, moderación, rubros, comisiones, parámetros, métricas | HU-9, HU-13, HU-16, HU-17, HU-18 |
| `ia` | _por asignar_ | Recomendación y chatbot clasificador | HU-10, HU-15 |

---

## 6. Flujo de trabajo en Git

1. **Una rama por historia:** `hu-1-registro-prestador`, `hu-3-busqueda`, etc.
2. **Commits pequeños que nombren la tarea:** `T1.2 API de registro de prestadores`.
3. **Pull Request hacia `main`**, revisado por el compañero encargado de las pruebas de esa historia.
4. **Nadie sube directo a `main`.**

---

## 7. Ceremonias Scrum

| Ceremonia | Cuándo | Duración | Resultado |
|---|---|---|---|
| Sprint Planning | Primer día del sprint | máx. 4 h (2 h por semana de sprint) | Historias comprometidas, tareas y responsables, con Planning Poker |
| Daily Standup | Cada día de trabajo | 15 min | ¿Qué hice? ¿Qué haré? ¿Qué me bloquea? · se actualiza el burndown |
| Sprint Review | Último día del sprint | 1 h | Demo de lo terminado al Product Owner |
| Retrospectiva | Después de la Review | 30 min | Qué mejorar en el siguiente sprint |

---

## 8. Definición de terminado

Una tarea está terminada cuando:

- [ ] El código está en `main` mediante un Pull Request revisado.
- [ ] Cumple los criterios de aceptación de su historia.
- [ ] Sus pruebas pasan.
- [ ] La tarea está marcada en [SPRINTS.md](SPRINTS.md).
