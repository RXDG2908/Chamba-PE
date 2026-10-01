# Chamba PE

**Servicios bajo demanda en tiempo real:** conecta a clientes con prestadores verificados (gasfiteros, electricistas, costureros, primeros auxilios y más) que están cerca y disponibles ahora, al estilo de un delivery.

Proyecto Integrador — Diseño y Desarrollo de Software · IV ciclo · 2026-2

## ¿Qué problema resuelve?

Una tubería rota, un corto circuito o un vestido descosido horas antes de un evento necesitan una solución inmediata. Llamar, buscar en redes o pedir referencias es lento y no garantiza disponibilidad. Chamba PE permite localizar, contratar y recibir al prestador más cercano en minutos.

## Funcionalidades principales

- Solicitud de servicio en tiempo real, sin agendamiento previo
- Geolocalización y mapa de prestadores dentro del radio de cobertura
- Perfiles verificados (DNI y certificaciones) con calificaciones y reseñas
- Seguimiento del servicio: Confirmado → En camino → En servicio → Finalizado
- Notificaciones push al prestador
- **IA:** recomendación del prestador más adecuado y chatbot que clasifica el problema en el oficio correcto
- Panel de administración: verificación, moderación, rubros, comisiones, parámetros del matching y métricas

## Categorías del MVP

Costura · Primeros auxilios · Gasfitería · Electricidad · Limpieza de emergencia · Mudanza y carga liviana · Reparaciones del hogar

## Arquitectura propuesta

| Capa | Tecnología |
|---|---|
| Frontend web | React (aplicación responsiva y panel de administración) |
| App móvil | Android / iOS |
| Backend | API REST con Django |
| Tiempo real | WebSocket |
| Notificaciones | Firebase Cloud Messaging |
| IA | Módulo de recomendación y clasificación integrado al backend |

## Planificación (Scrum)

18 historias de usuario · 51 story points · 4 sprints de 2 semanas · 30 h por sprint

| Sprint | Fechas | Historias | Meta |
|---|---|---|---|
| 1 | 17/09 – 30/09/2026 | HU-1 a HU-4 | MVP: el prestador se registra y el cliente lo encuentra y le envía una solicitud |
| 2 | 01/10 – 14/10/2026 | HU-5 a HU-9 | Cuentas, disponibilidad, mapa, notificaciones y verificación de prestadores |
| 3 | 15/10 – 28/10/2026 | HU-10 a HU-13 | Recomendación con IA, seguimiento en tiempo real, calificación y moderación |
| 4 | 29/10 – 11/11/2026 | HU-14 a HU-18 | Prestadores alternativos, chatbot con IA y administración del negocio |

Detalle completo de historias, criterios de aceptación, tareas y estimaciones en [`docs/Lab07_CPS_ChambaYa.md`](docs/Lab07_CPS_ChambaYa.md). Visión del producto en [`docs/CHAMBA_PE.md`](docs/CHAMBA_PE.md).

## Equipo

| Integrante | Rol |
|---|---|
| Renzo León | Scrum Master / Desarrollador |
| Luis Abad | Development Team |
| David Valcarcel | Product Owner / Desarrollador |
