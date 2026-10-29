# Cierre del Sprint 2 · 28/10/2026

Guía para la Sprint Review y la Retrospectiva. La preparó Renzo León (Scrum Master) con el estado de GitHub al 28/10.

## 1. Estado al cierre

**Ninguna tarea del Sprint 2 está en `main`.** Por la [definición de terminado](../PLAN_EQUIPO.md#8-definición-de-terminado) quedan **0 de 30 h terminadas**, aunque casi todo el código ya existe en PRs abiertos y funciona junto (sección 2).

| Tarea | Responsable | Horas | Dónde está | Estado |
|:---:|---|:---:|---|---|
| T5.1 API de autenticación con roles | Renzo | 3 | [#13](https://github.com/RXDG2908/Chamba-PE/pull/13) | 👀 En revisión desde el 19/10, sin revisar |
| T6.1 Endpoint de disponibilidad | Renzo | 2 | [#15](https://github.com/RXDG2908/Chamba-PE/pull/15) | 👀 En revisión, sale de #13 |
| T5.2 Pantallas de registro e inicio de sesión | Renzo | 3 | [#17](https://github.com/RXDG2908/Chamba-PE/pull/17) | 👀 En revisión, sale de #15 |
| T6.2 Interruptor de disponibilidad | Renzo | 2 | [#18](https://github.com/RXDG2908/Chamba-PE/pull/18) | 👀 En revisión, sale de #17 |
| T7.1 Consulta por coordenadas | Luis | 2 | [#14](https://github.com/RXDG2908/Chamba-PE/pull/14) | 👀 En revisión, sin revisor asignado |
| T7.2 Mapa con marcadores | Luis | 3 | [#16](https://github.com/RXDG2908/Chamba-PE/pull/16) | 👀 En revisión |
| T8.1 Notificaciones push (FCM) | Luis | 3 | — | ☐ Sin rama |
| T8.2 Recepción y aceptar/rechazar | Luis | 2 | — | ☐ Sin rama |
| T9.1 Modelo y API de revisión | David | 2 | [#11](https://github.com/RXDG2908/Chamba-PE/pull/11) | 👀 En revisión, ya atendió las observaciones |
| T9.2 Pantalla de aprobar o rechazar | David | 2 | [#11](https://github.com/RXDG2908/Chamba-PE/pull/11) | 👀 En revisión, va en el mismo PR que T9.1 |
| T5.3 Pruebas de autenticación y rol | David | 1 | rama `feature/t5.3-pruebas-acceso-rol` | 🟡 Sin PR |
| T6.3 Pruebas de disponibilidad | David | 1 | rama `feature/t6.3-pruebas-disponibilidad` | 🟡 Sin PR |
| T7.3 Pruebas del radio | David | 2 | rama `feature/t7.3-pruebas-radio` | 🟡 Sin PR |
| T8.3 Pruebas de entrega (< 5 s) | David | 2 | rama `feature/t8.3-pruebas-notificaciones` | 🟡 Sin PR; depende de T8.1 |

**Por historia:** HU-5, HU-6 y HU-9 tienen todo el código listo en PRs y solo les falta la revisión. HU-7 tiene backend y mapa en PRs. HU-8, las notificaciones, no tiene ni T8.1 ni T8.2.

## 2. Demo de la Review

Se probó el 28/10 en una rama local que une los PRs #18 (trae #13, #15 y #17), #11 y #16:

- **116 de 116 pruebas OK**, sin migraciones pendientes, y lint y build del frontend sin errores.
- Los únicos choques entre PRs son de documentos (AVANCES, TABLERO y API_BUSQUEDA) y un menú en `App.jsx` (se dejan los dos enlaces).

### Preparar la demo

```bash
git fetch origin
git checkout -b demo-sprint-2 origin/feature/t6.2-interruptor-disponibilidad
git merge origin/feature/t9.1-revision-documentos origin/feature/t7.2-mapa-marcadores
python manage.py migrate
python manage.py createsuperuser
```

Si `git merge` se detiene por choques: en `App.jsx` se dejan las dos versiones del menú y de las rutas; en los `.md` da igual cuál se deje, porque la rama de demo no se sube. Después se levantan Django y Vite (`npm run dev` en `frontend/`).

### Guion (unos 8 minutos)

| # | Quién | Paso | Qué se muestra | HU |
|:---:|---|---|---|:---:|
| 1 | Cliente | **Crear cuenta** en `/crear-cuenta` | Validaciones con el formulario vacío; al registrarse queda con la sesión y la cabecera dice "Cliente" | HU-5 |
| 2 | Cliente | **Cerrar sesión** e **Iniciar sesión** con el correo | Error "Usuario o contraseña incorrectos." con una contraseña mala | HU-5 |
| 3 | Prestador | **Registro de prestador** y luego iniciar sesión | Entra directo a **Mi disponibilidad**, con el aviso "pendiente de verificación" | HU-5, HU-6 |
| 4 | Prestador | **Cargar certificado** (PDF) y activar el interruptor | Aunque está Activo, todavía no aparece en la búsqueda porque no está verificado | HU-6, HU-9 |
| 5 | Admin | Iniciar sesión e ir a **Revisión de documentos** | Rechazar sin motivo da error; al aprobar, el prestador pasa a "Verificado" | HU-9 |
| 6 | Cliente | **Resultados**: rubro y ubicación en el mapa | Ahora el prestador aparece con su marcador y la distancia | HU-7 |
| 7 | Prestador | Desactivar el interruptor y volver a buscar | Desaparece de la búsqueda | HU-6 |
| 8 | Cliente → prestador | Enviar una solicitud y aceptarla | La solicitud pasa de Pendiente a Aceptada (HU-4, ya en `main`) | HU-4 |

El 28/10 se probaron por API los pasos 3 a 8 con la rama de demo, y todos dieron el resultado esperado. Los pasos 1 y 2 se probaron en el navegador con T5.2.

**Lo que no se puede mostrar:** la notificación push al prestador (HU-8). No hay T8.1 ni T8.2.

## 3. Decisiones para el Product Owner (David)

1. ✅ **Decidido por el PO el 28/10. Qué pasa al Sprint 3:** las tareas con PR solo esperan revisión, así que se unen el 29/10 y cuentan al inicio del Sprint 3. T8.1, T8.2 y T8.3 (7 h, HU-8) pasan completas al Sprint 3.
2. **Orden de unión:** #13 → #15 → #17 → #18 → #11 → #14 → #16. Después, los PRs de pruebas de David (T5.3, T6.3 y T7.3, abiertos el 28/10) y la base parcial de T8.3. Al unir un PR de la cadena, se cambia la base del siguiente a `main` **antes** de borrar su rama.
3. **Revisión de PRs:** quién revisa #14 y #16 de Luis.

## 4. Retrospectiva (propuesta para discutir)

Son puntos que salen de los hechos del sprint. El equipo los confirma o cambia en la reunión, y lo acordado se copia en [SPRINTS.md](../SPRINTS.md#retrospectiva).

| ¿Qué salió bien? | ¿Qué mejorar? | Acciones para el Sprint 3 |
|---|---|---|
| El contrato de T5.1 se escribió el primer día y los demás trabajaron contra él sin esperar (David integró T5.1 en T9.1 y T5.3). | **Ningún PR entró a `main` en todo el sprint**: el #13 esperó 9 días su revisión y el burndown no bajó nunca de 30 h. | Revisar cada PR en máximo **1 día hábil**; si no se puede, otro integrante lo revisa. |
| Las pruebas cruzadas encontraron problemas a tiempo (cambio a `EsAdministrador`, la carga de certificados sin sesión). | El sprint empezó tarde: el 15/10 y el 16/10 pasaron sin horas y no quedó registrada la decisión de recorte del Daily del 23/10. | Planificar el último día del sprint anterior (29/10 para el Sprint 3) y hacer el recorte en el Daily de la mitad del sprint. |
| Todo junto funciona: 116 de 116 pruebas OK con los PRs unidos. | Los PRs encadenados (#13 → #15 → #17 → #18) acumulan riesgo si el primero no se revisa. | Activar la protección de `main` con 1 aprobación (impedimento 14) y unir apenas haya aprobación. |
| | HU-8 (notificaciones) no avanzó: no hubo rama de T8.1 ni T8.2. | Avisar en el Daily cuando una tarea no arranca, para repartirla. |
