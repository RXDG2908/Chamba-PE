# Cierre del Sprint 2 · 28/10/2026

Guía para la Sprint Review y la Retrospectiva. La preparó Renzo León (Scrum Master); el estado se actualizó el 29/10 con lo que se unió a `main` la noche del 28/10.

## 1. Estado al cierre

**15 de 23 h terminadas** (65 %). La noche del 28/10, entre las 22:26 y las 23:59, entraron a `main` #13, #15, #17, #18, #11 y #21. HU-8 (7 h) salió del sprint por decisión del PO, así que el compromiso quedó en 23 h. `main` queda con 145 pruebas OK (3 fallos esperados por el impedimento 16), y lint y build OK.

| Tarea | Responsable | Horas | Dónde está | Estado |
|:---:|---|:---:|---|---|
| T5.1 API de autenticación con roles | Renzo | 3 | [#13](https://github.com/RXDG2908/Chamba-PE/pull/13) | ✅ Unido a `main` el 28/10 |
| T6.1 Endpoint de disponibilidad | Renzo | 2 | [#15](https://github.com/RXDG2908/Chamba-PE/pull/15) | ✅ Unido a `main` el 28/10 |
| T5.2 Pantallas de registro e inicio de sesión | Renzo | 3 | [#17](https://github.com/RXDG2908/Chamba-PE/pull/17) | ✅ Unido a `main` el 28/10 |
| T6.2 Interruptor de disponibilidad | Renzo | 2 | [#18](https://github.com/RXDG2908/Chamba-PE/pull/18) | ✅ Unido a `main` el 28/10 |
| T7.1 Consulta por coordenadas | Luis | 2 | [#14](https://github.com/RXDG2908/Chamba-PE/pull/14) | 👀 En revisión, sin revisor asignado |
| T7.2 Mapa con marcadores | Luis | 3 | [#16](https://github.com/RXDG2908/Chamba-PE/pull/16) | 👀 En revisión |
| T8.1 Notificaciones push (FCM) | Luis | 3 | — | ➡️ Sprint 3 |
| T8.2 Recepción y aceptar/rechazar | Luis | 2 | — | ➡️ Sprint 3 |
| T9.1 Modelo y API de revisión | David | 2 | [#11](https://github.com/RXDG2908/Chamba-PE/pull/11) | ✅ Unido a `main` el 28/10 |
| T9.2 Pantalla de aprobar o rechazar | David | 2 | [#11](https://github.com/RXDG2908/Chamba-PE/pull/11) | ✅ Unido a `main` el 28/10 |
| T5.3 Pruebas de autenticación y rol | David | 1 | [#21](https://github.com/RXDG2908/Chamba-PE/pull/21) | ✅ Unido a `main` el 28/10 |
| T6.3 Pruebas de disponibilidad | David | 1 | [#20](https://github.com/RXDG2908/Chamba-PE/pull/20) | 👀 En revisión; choca con `main` |
| T7.3 Pruebas del radio | David | 2 | [#23](https://github.com/RXDG2908/Chamba-PE/pull/23) | 👀 En revisión; choca con `main` |
| T8.3 Pruebas de entrega (< 5 s) | David | 2 | [#22](https://github.com/RXDG2908/Chamba-PE/pull/22) | ➡️ Sprint 3 (base parcial); depende de T8.1 |

**Por historia:** HU-5, HU-6 y HU-9 quedan ✅ completas en `main`. A HU-7 le faltan unir #14 y #16 (Luis) y #23 (pruebas). HU-8 pasa completa al Sprint 3.

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
| El contrato de T5.1 se escribió el primer día y los demás trabajaron contra él sin esperar (David integró T5.1 en T9.1 y T5.3). | **Todo se revisó y se unió el último día, el 28/10 en la noche**: el #13 esperó 9 días su revisión y el burndown estuvo en 30 h hasta el cierre. | Revisar cada PR en máximo **1 día hábil**; si no se puede, otro integrante lo revisa. |
| Las pruebas cruzadas encontraron problemas a tiempo (cambio a `EsAdministrador`, la carga de certificados sin sesión). | El sprint empezó tarde: el 15/10 y el 16/10 pasaron sin horas y no quedó registrada la decisión de recorte del Daily del 23/10. | Planificar el último día del sprint anterior (29/10 para el Sprint 3) y hacer el recorte en el Daily de la mitad del sprint. |
| Todo junto funciona: 116 de 116 pruebas OK con los PRs unidos. | Los PRs encadenados (#13 → #15 → #17 → #18) acumulan riesgo si el primero no se revisa. | Activar la protección de `main` con 1 aprobación (impedimento 14) y unir apenas haya aprobación. |
| | HU-8 (notificaciones) no avanzó: no hubo rama de T8.1 ni T8.2. | Avisar en el Daily cuando una tarea no arranca, para repartirla. |

## 5. Transición al Sprint 3 · actualización del 29/10/2026

Esta sección complementa el cierre histórico de las secciones 1–4; no cambia sus resultados. El cierre permanece en **15 de 23 h terminadas (65 %)**. La unidad es horas, no «15 de 23 tareas».

### Cambios posteriores al cierre

- #20 (T6.3, 1 h) y #23 (T7.3, 2 h) se integraron el 29/10. Estas 3 h se contabilizan una sola vez al inicio de S3; ya no están pendientes aunque la tabla histórica las muestre en revisión al cierre.
- #14 (T7.1) y #16 (T7.2) siguen abiertos según la consulta del 29/10. David asume revisión y Luis los ajustes/integración; sus estimaciones originales de 5 h no se vuelven a añadir.
- #22 permanece abierto como base parcial T8.3: las pruebas de bandeja no prueban entrega FCM.
- HU-8 cuenta ahora con implementación y evidencia local conservadas, sin commit/PR. Hay recepción real y un ensayo grabado ≈1,3 s, pendiente de repetición y validación cruzada. La frase «No hay T8.1 ni T8.2» de la demo describe el corte histórico, no este avance posterior. HU-8 sigue abierta.

### Acuerdo de Planning aprobado

El Scrum Master comunica que David (PO) y Renzo aprobaron la propuesta. Sprint 3: **29/10–11/11/2026, 30 h**. Sprint Goal: cerrar HU-7/HU-8, corregir permisos/propiedad de certificados y avanzar HU-19/HU-21.

| Integrante | Trabajo asignado | Reserva | Total |
|---|---:|---:|---:|
| Luis | 7 h | 3 h | 10 h |
| David | 8 h (incluye 3 h ya integradas el 29/10) | 2 h | 10 h |
| Renzo | 9 h | 1 h | 10 h |
| **Total** | **24 h** | **6 h** | **30 h** |

HU-13 y HU-20 se postergan del compromiso inicial, sin trasladarlas automáticamente al Sprint 4. Las reservas no son entregables ni horas terminadas. Se mantienen separadas las estimaciones históricas y el esfuerzo restante aprobado.

El backlog detallado, criterios, dependencias, PR y burndown están en [SPRINTS.md](../SPRINTS.md#plan-aprobado-del-sprint-3); el registro diario y los bloqueos, en [AVANCES.md](../AVANCES.md#10-seguimiento-del-sprint-3--291011112026). La preparación documental no realizó merge, cambio de rama, commit, push ni PR.
