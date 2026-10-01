# Chamba PE

**Servicios bajo demanda en tiempo real:** conecta a clientes con prestadores verificados (gasfiteros, electricistas, costureros, primeros auxilios y más) que están cerca y disponibles ahora, al estilo de un delivery.

Proyecto Integrador — Diseño y Desarrollo de Software · IV ciclo · Tecsup 2026-2

| 📄 Documento | Contenido |
|---|---|
| **README.md** (este archivo) | Presentación del proyecto |
| [PLAN_EQUIPO.md](PLAN_EQUIPO.md) | Integrantes, capacidad, reglas de reparto, stack, Git y ceremonias Scrum |
| [SPRINTS.md](SPRINTS.md) | Sprint 1 planificado y plantillas de los Sprints 2 a 4 |
| [docs/](docs/) | Documentos fuente: visión del producto y Lab 07 (historias, criterios y estimaciones) |

---

## ¿Qué problema resuelve?

En el día a día surgen imprevistos que necesitan solución inmediata pero no justifican una búsqueda larga ni un agendamiento formal. Llamar, buscar en redes o pedir referencias es lento y no garantiza disponibilidad.

Chamba PE conecta a quien tiene el problema con quien puede resolverlo, **al instante y cerca**: el cliente localiza, contrata y recibe al prestador en el menor tiempo posible.

## Casos de uso

| Situación | Solución con Chamba PE |
|---|---|
| 👗 Un vestido se descose horas antes de un evento | Un costurero cercano llega con sus herramientas y lo arregla en el acto |
| 🩹 Un niño se raspa la rodilla en casa | Un técnico en primeros auxilios certificado llega con su botiquín |
| 🚰 Se rompe una tubería en la oficina | Un gasfitero disponible en la zona va directo al lugar |
| ⚡ Un corto circuito deja sin luz el departamento | Un electricista verificado está en camino en minutos |

## Funcionalidades principales

- Solicitud de servicio en tiempo real, sin agendamiento previo
- Geolocalización y mapa de prestadores dentro del radio de cobertura
- Perfiles verificados (DNI y certificaciones) con calificaciones y reseñas
- Seguimiento del servicio: Confirmado → En camino → En servicio → Finalizado
- Notificaciones push al prestador
- Historial de servicios y módulo de pagos
- Panel de administración: verificación, moderación, rubros, comisiones, parámetros del matching y métricas

## Inteligencia Artificial

- **Recomendación:** sugiere al prestador más adecuado según distancia, calificación, disponibilidad y tipo de servicio.
- **Chatbot clasificador:** el cliente describe su problema en texto libre y la IA lo asigna al oficio correcto.
- **Prioridades:** estima tiempos de llegada y ordena la atención en momentos de alta demanda.

## Público objetivo

- **Clientes:** personas, familias, oficinas, hoteles y negocios que necesitan resolver algo puntual y rápido.
- **Prestadores:** trabajadores independientes, técnicos certificados y pequeñas empresas de servicios que buscan ingresos diarios con clientes cercanos.

## Categorías del MVP

Costura · Primeros auxilios · Gasfitería · Electricidad · Limpieza de emergencia · Mudanza y carga liviana · Reparaciones del hogar

## Arquitectura

| Capa | Tecnología |
|---|---|
| Backend | API REST con Django + Django REST Framework |
| Frontend web | React (aplicación responsiva y panel de administración) |
| App móvil | Android / iOS |
| Tiempo real | WebSocket |
| Notificaciones | Firebase Cloud Messaging |
| IA | Módulo de recomendación y clasificación integrado al backend |

## Planificación

18 historias de usuario · 51 story points · 4 sprints de 2 semanas · 30 h por sprint

| Sprint | Fechas | Meta |
|:---:|:---:|---|
| 1 | 01/10 – 14/10/2026 | MVP: el prestador se registra y el cliente lo encuentra y le envía una solicitud |
| 2 | 15/10 – 28/10/2026 | Cuentas, disponibilidad, mapa, notificaciones y verificación de prestadores |
| 3 | 29/10 – 11/11/2026 | Recomendación con IA, seguimiento en tiempo real, calificación y moderación |
| 4 | 12/11 – 25/11/2026 | Prestadores alternativos, chatbot con IA y administración del negocio |

Detalle de tareas y horas en [SPRINTS.md](SPRINTS.md).

## Entregables

| Entregable | Semana |
|---|:---:|
| Monografía — entrega final | 14 |
| Aplicación del proyecto integrador | 16 |
| Exposición del proyecto | 16 |

## Equipo

| Integrante | Rol |
|---|---|
| Renzo León | Scrum Master / Desarrollador |
| Luis Abad | Development Team |
| David Valcarcel | Product Owner / Desarrollador |

Organización completa en [PLAN_EQUIPO.md](PLAN_EQUIPO.md).
