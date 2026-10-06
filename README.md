# Chamba PE

**Servicios bajo demanda en tiempo real:** conecta a clientes con prestadores verificados (gasfiteros, electricistas, costureros, primeros auxilios y más) que están cerca y disponibles ahora, al estilo de un delivery.

Proyecto Integrador — Diseño y Desarrollo de Software · IV ciclo · Tecsup 2026-2

> 📱 **Maquetas de las 21 historias de usuario:** [docs/renders/historias-usuario.html](docs/renders/historias-usuario.html)
> Descarga el repositorio y abre el archivo en el navegador. Tiene filtro por sprint y un modo presentación (← → para avanzar, Esc para salir).

| 📄 Documento | Contenido |
|---|---|
| **README.md** (este archivo) | Presentación del proyecto |
| [docs/renders/historias-usuario.html](docs/renders/historias-usuario.html) | Maqueta de pantalla de cada historia de usuario con sus criterios de aceptación |
| [PLAN_EQUIPO.md](PLAN_EQUIPO.md) | Integrantes, capacidad, reglas de reparto, stack, Git y ceremonias Scrum |
| [SPRINTS.md](SPRINTS.md) | Sprint 1 planificado y plantillas de los Sprints 2 a 4 |
| [AVANCES.md](AVANCES.md) | Estado de cada tarea, bitácora, impedimentos y burndown |
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
- Panel de administración: revisión manual de prestadores, suspensión y bloqueo de cuentas (rubros, comisiones y métricas en una fase posterior)

## Seguridad: verificación del prestador

La confianza es lo más importante: el cliente deja entrar a un desconocido a su casa u oficina. Por eso todo prestador pasa por una verificación de identidad antes de poder trabajar:

1. **Acepta el uso de sus datos biométricos** (Ley 29733 de Protección de Datos Personales).
2. **Escanea su DNI** → la app lee el número y los nombres automáticamente.
3. **Se valida el DNI** consultando la fuente pública eldni.com.
4. **Se toma una selfie en vivo** con prueba de vida (parpadeo).
5. **Reconocimiento facial:** se compara la selfie con la foto del DNI.
6. Si todo coincide, obtiene la insignia **"Identidad verificada"**; si hay dudas, lo revisa un administrador.

## Inteligencia Artificial

- **Reconocimiento facial:** confirma que el prestador es el titular del DNI.
- **Recomendación:** sugiere al prestador más adecuado según distancia, calificación, disponibilidad y tipo de servicio.
- **Tiempo estimado de llegada:** la recomendación indica cuánto tardará el prestador.
- **Chatbot clasificador** _(fase posterior)_: el cliente describe su problema en texto libre y la IA lo asigna al oficio correcto.

## Público objetivo

- **Clientes:** personas, familias, oficinas, hoteles y negocios que necesitan resolver algo puntual y rápido.
- **Prestadores:** trabajadores independientes, técnicos certificados y pequeñas empresas de servicios que buscan ingresos diarios con clientes cercanos.

## Categorías del MVP

Costura · Primeros auxilios · Gasfitería · Electricidad · Limpieza de emergencia · Mudanza y carga liviana · Reparaciones del hogar

---

## Product Backlog

**21 historias de usuario · 62 story points.** 16 historias (51 SP) están comprometidas en 4 sprints; las otras 5 (11 SP) quedan en el backlog futuro. Ver cada pantalla en las [maquetas](docs/renders/historias-usuario.html) y el detalle en [docs/Lab07_CPS_ChambaYa.md](docs/Lab07_CPS_ChambaYa.md).

| Sprint | Fechas | Historias | SP | Meta |
|:---:|:---:|---|:---:|---|
| 1 | 01/10 – 14/10/2026 | HU-1 Registro del prestador · HU-2 Certificaciones · HU-3 Búsqueda · HU-4 Solicitud y aceptación | 13 | MVP: el prestador se registra y el cliente lo encuentra y le envía una solicitud |
| 2 | 15/10 – 28/10/2026 | HU-5 Registro e inicio de sesión · HU-6 Disponibilidad · HU-7 Mapa · HU-8 Notificaciones · HU-9 Validación por el administrador | 13 | Cuentas, disponibilidad, mapa, notificaciones y verificación de prestadores |
| 3 | 29/10 – 11/11/2026 | HU-19 Escaneo del DNI · HU-20 Selfie y reconocimiento facial · HU-21 Consentimiento biométrico · HU-13 Suspensión y bloqueo | 13 | Verificación de identidad del prestador y moderación |
| 4 | 12/11 – 25/11/2026 | HU-10 Recomendación con IA · HU-11 Seguimiento en tiempo real · HU-12 Calificación | 12 | Recomendación con IA, seguimiento en tiempo real y calificación |
| Futuro | — | HU-14 Alternativos · HU-15 Chatbot · HU-16 Rubros y comisiones · HU-17 Parámetros del matching · HU-18 Métricas | 11 | Se planifican cuando haya capacidad |

Capacidad: 30 h por sprint (3 integrantes × 10 h) · 114 h planificadas. Detalle de tareas en [SPRINTS.md](SPRINTS.md) y avance diario en [AVANCES.md](AVANCES.md).

---

## Arquitectura

| Capa | Tecnología |
|---|---|
| Backend | API REST con Django + Django REST Framework |
| Frontend web | React + Vite (aplicación responsiva y panel de administración) |
| App móvil | _por definir_ |
| Tiempo real | WebSocket (Django Channels) |
| Notificaciones | Firebase Cloud Messaging |
| Verificación de identidad | OCR (EasyOCR), consulta a eldni.com, reconocimiento facial (DeepFace), prueba de vida (MediaPipe) |
| IA | Módulo de recomendación y clasificación integrado al backend |

## Estructura del repositorio

```
Chamba-PE/
├── core/               Configuración de Django (settings, urls)
├── prestadores/        App Django: prestador, rubros, cobertura y API de registro (HU-1)
├── certificaciones/    App Django: carga de certificaciones PDF/JPG ≤ 5 MB (HU-2)
├── frontend/           Frontend React + Vite: listado de prestadores (HU-3) y formulario de registro (HU-1)
├── src/                Componente React de carga de certificaciones (HU-2)
├── docs/               Visión del producto, Lab 07 y maquetas (docs/renders/)
├── manage.py
├── requirements.txt    Dependencias de Python
└── package.json
```

## Cómo ejecutarlo

**Backend (Django)** — requiere Python 3.10 o superior:

```bash
python -m venv venv
```

```bash
venv\Scripts\activate
```

```bash
pip install -r requirements.txt
```

```bash
python manage.py migrate
```

```bash
python manage.py runserver
```

La API queda en `http://127.0.0.1:8000/` y el panel de Django en `/admin/`.

| Endpoint | Uso |
|---|---|
| `POST /api/prestadores/registro/` | Registro del prestador (HU-1) |
| `GET /api/prestadores/rubros/` | Lista de rubros |
| `/api/certificaciones/` | Carga de certificaciones (HU-2) |

Pruebas del backend:

```bash
python manage.py test
```

**Frontend (React + Vite)** — requiere Node.js 20 o superior. Dentro de `frontend/` (con Django corriendo; Vite redirige `/api` al puerto 8000):

```bash
npm install
```

```bash
npm run dev
```

## Flujo de trabajo

Una rama por historia (`hu-1-registro-prestador`), commits que nombran la tarea (`T1.2 API de registro de prestadores`) y Pull Request hacia `main` revisado por quien hace las pruebas de esa historia. Al terminar cada tarea se actualiza [AVANCES.md](AVANCES.md). Reglas completas en [PLAN_EQUIPO.md](PLAN_EQUIPO.md).

## Entregables

| Entregable | Semana |
|---|:---:|
| Monografía — entrega final | 14 |
| Aplicación del proyecto integrador | 16 |
| Exposición del proyecto | 16 |

## Equipo

| Integrante | GitHub | Rol |
|---|---|---|
| Renzo León | [@RXDG2908](https://github.com/RXDG2908) | Scrum Master / Desarrollador |
| Luis Abad | [@Itssmann](https://github.com/Itssmann) | Development Team |
| David Valcarcel | [@Davish-pxl](https://github.com/Davish-pxl) | Product Owner / Desarrollador |

Organización completa en [PLAN_EQUIPO.md](PLAN_EQUIPO.md).
