# Sprint 1 · 01/10 – 14/10/2026

| Story points | Horas | Capacidad | Integrantes |
|:---:|:---:|:---:|:---:|
| 13 SP | 30 h | 30 h | 3 × 10 h |

**Meta del sprint (MVP):** el prestador se registra y el cliente lo encuentra y le envía una solicitud.

---

## 1. Historias comprometidas

| HU | Historia | Prioridad | SP | Horas |
|:---:|---|:---:|:---:|:---:|
| HU-1 | Como **prestador**, quiero registrarme indicando mis datos, rubros y área de cobertura, a fin de que los clientes cercanos puedan encontrarme. | 20 | 3 | 7 h |
| HU-2 | Como **prestador**, quiero cargar mis certificaciones (PDF o JPG), a fin de demostrar mi experiencia y generar confianza. | 19 | 2 | 5 h |
| HU-3 | Como **cliente**, quiero buscar prestadores disponibles cerca de mí por tipo de servicio, a fin de resolver una necesidad urgente sin agendar. | 18 | 5 | 11 h |
| HU-4 | Como **cliente**, quiero enviar una solicitud al prestador elegido y que él pueda aceptarla o rechazarla, a fin de contratar el servicio en pocos minutos. | 17 | 3 | 7 h |
| | **Total** | | **13** | **30 h** |

### Criterios de aceptación

| HU | Criterios |
|:---:|---|
| HU-1 | • No se completa el registro sin DNI, al menos un rubro y un área de cobertura.<br>• Al guardar, el perfil queda en estado **"Pendiente de verificación"**. |
| HU-2 | • Acepta certificaciones en PDF o JPG de hasta **5 MB** y rechaza otros formatos o tamaños.<br>• Las certificaciones quedan asociadas al perfil del prestador. |
| HU-3 | • Solo se muestran prestadores **disponibles**, del rubro elegido y con cobertura sobre la ubicación del cliente.<br>• Los resultados aparecen en **menos de 3 segundos**, ordenados por distancia. |
| HU-4 | • El prestador recibe la solicitud y puede aceptarla o rechazarla.<br>• El cliente ve el cambio de estado de su solicitud. |

---

## 2. Reparto por integrante

Cada integrante tiene **10 h** y al menos una tarea de **backend en Django**.

### Renzo León — 10 h

| Tarea | Descripción | Tipo | Horas |
|:---:|---|:---:|:---:|
| T1.1 | Diseñar el modelo de datos del prestador (datos, rubros y cobertura) | Backend | 2 |
| T1.2 | Implementar la API de registro de prestadores (Django) | Backend | 3 |
| T1.3 | Maquetar el formulario de registro (web y móvil) con rubros y mapa de cobertura | Frontend | 2 |
| T4.1 | Implementar el flujo de solicitud: envío y aceptación o rechazo del prestador | Backend | 3 |
| | **Subtotal** | | **10 h** |

### Luis Abad — 10 h

| Tarea | Descripción | Tipo | Horas |
|:---:|---|:---:|:---:|
| T3.1 | Implementar la búsqueda por rubro, radio de cobertura y disponibilidad | Backend | 3 |
| T3.2 | Pantalla de resultados con lista de prestadores | Frontend | 4 |
| T2.3 | Pruebas funcionales del registro y la carga (campos obligatorios y límites) | Pruebas | 1 |
| T4.3 | Pruebas del flujo buscar → solicitar → aceptar | Pruebas | 2 |
| | **Subtotal** | | **10 h** |

### David Valcarcel — 10 h

| Tarea | Descripción | Tipo | Horas |
|:---:|---|:---:|:---:|
| T2.1 | Implementar la API de carga de certificaciones (PDF/JPG hasta 5 MB) | Backend | 2 |
| T2.2 | Componente de carga de archivos y estado "Pendiente de verificación" | Frontend | 2 |
| T3.3 | Ordenar los resultados por distancia | Backend | 2 |
| T3.4 | Datos de prueba de prestadores y verificación del tiempo de respuesta (< 3 s) | Pruebas | 2 |
| T4.2 | Pantallas de envío de solicitud y cambio de estado | Frontend | 2 |
| | **Subtotal** | | **10 h** |

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
| HU-1 | Renzo | Luis (dentro de T2.3: campos obligatorios del registro) |
| HU-2 | David | Luis (T2.3) |
| HU-3 | Luis y David | David (T3.4: datos de prueba y tiempo de respuesta) |
| HU-4 | Renzo y David | Luis (T4.3) |

---

## 3. Orden de trabajo

Dependencias del Lab 07: **HU-1 → HU-2 y HU-3**, **HU-3 → HU-4**. En cada historia la API va primero; las pantallas se maquetan en paralelo con datos de prueba (mocks).

| Momento | Renzo | Luis | David |
|---|---|---|---|
| **Días 1–2** (01/10 – 02/10) | **T1.1 modelo del prestador** (bloquea al resto) | Maquetar T3.2 con mocks | Maquetar T2.2 y T4.2 con mocks |
| **Semana 1** (05/10 – 09/10) | T1.2 API de registro · T1.3 formulario | T3.1 búsqueda · T3.2 resultados | T2.1 API de certificaciones · T2.2 carga |
| **Semana 2** (12/10 – 14/10) | T4.1 flujo de solicitud | T2.3 y T4.3 pruebas | T3.3 orden por distancia · T3.4 datos y rendimiento · T4.2 conectar a la API |

**Cierre:** 14/10/2026, Sprint Review (demo del flujo registrar → buscar → solicitar → aceptar) y Retrospectiva.

---

## 4. Seguimiento (burndown)

Horas pendientes al final de cada día hábil. La línea ideal baja 3 h por día. Se actualiza en cada Daily Standup.

| Día | Fecha | Ideal (h) | Real (h) |
|:---:|:---:|:---:|:---:|
| Inicio | 01/10 | 30 | 30 |
| 1 | 01/10 (jue) | 27 | |
| 2 | 02/10 (vie) | 24 | |
| 3 | 05/10 (lun) | 21 | |
| 4 | 06/10 (mar) | 18 | |
| 5 | 07/10 (mié) | 15 | |
| 6 | 08/10 (jue) | 12 | |
| 7 | 09/10 (vie) | 9 | |
| 8 | 12/10 (lun) | 6 | |
| 9 | 13/10 (mar) | 3 | |
| 10 | 14/10 (mié) | 0 | |

---

## 5. Checklist del sprint

**Renzo**
- [ ] T1.1 Modelo del prestador
- [ ] T1.2 API de registro de prestadores
- [ ] T1.3 Formulario de registro
- [ ] T4.1 Flujo de solicitud

**Luis**
- [ ] T3.1 Búsqueda por rubro, radio y disponibilidad
- [ ] T3.2 Pantalla de resultados
- [ ] T2.3 Pruebas de registro y carga
- [ ] T4.3 Pruebas del flujo completo

**David**
- [ ] T2.1 API de certificaciones
- [ ] T2.2 Componente de carga
- [ ] T3.3 Orden por distancia
- [ ] T3.4 Datos de prueba y tiempo de respuesta
- [ ] T4.2 Pantallas de solicitud

**Definición de terminado:** el código está en `main` mediante Pull Request revisado, cumple los criterios de aceptación de su historia y sus pruebas pasan.
