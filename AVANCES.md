# Avances del grupo — Chamba PE

> **Regla del equipo:** este archivo se actualiza **siempre**, al terminar cada tarea o al cierre del día de trabajo, antes de dar la tarea por terminada. Ver [PLAN_EQUIPO.md](PLAN_EQUIPO.md#3-reglas-de-reparto).
> Sirve de base para el cuadro de avances y el informe del Sprint Review.

**Última actualización:** 29/10/2026 · **Sprint 1:** ✅ cerrado el 14/10/2026 (01/10 – 14/10) · **Sprint 2:** ✅ cerrado el 28/10/2026 con 15 de 23 h (HU-8 pasa al Sprint 3; T6.3 y T7.3 se unieron el 29/10) · **Meta:** el prestador se registra y el cliente lo encuentra y le envía una solicitud.


> **Actualización de Planning S3 (29/10):** acuerdo aprobado de David y Renzo, comunicado por el Scrum Master: 24 h de trabajo + 6 h de reserva, incluyendo las 3 h integradas de #20/#23. HU-8 sigue parcial con avance local; HU-13/HU-20 se postergan del compromiso inicial. [Seguimiento](#10-seguimiento-del-sprint-3--291011112026). Este acuerdo reemplaza la propuesta de sobrecarga de 35 h, conservada en el historial.

Estados: ✅ terminada · 🟡 en curso / parcial · ⚪ sin iniciar · 🔴 bloqueada

---

## 1. Resumen del Sprint 1 (cerrado)

| Indicador | Valor |
|---|---|
| Horas comprometidas | 30 h |
| Horas terminadas (tareas ✅) | 26 h (87 %) |
| Horas pendientes al cierre | 4 h |
| Tareas terminadas | 11 de 13 |
| Tareas que pasan al Sprint 2 | 2 (T2.2 y T3.4, de David). Terminadas el 18/10 con el PR 9 |
| Historias terminadas | 2 de 4 (HU-1 y HU-4). A HU-2 le faltaba T2.2 y a HU-3, T3.4; ambas se completaron el 18/10 (Sprint 2) |
| Estado de `main` al 15/10 | 49 de 49 pruebas OK, sin migraciones pendientes, lint y build OK; flujo registrar → buscar → solicitar → aceptar o rechazar probado en el navegador |

> Las horas solo cuentan como terminadas cuando la tarea cumple la [definición de terminado](PLAN_EQUIPO.md#8-definición-de-terminado). T2.3 y T4.3 las confirmó Luis el 15/10; el PR 7 se unió el 15/10 a las 00:43 y se cuenta en el cierre.

El detalle de cada verificación está en la bitácora (sección 6) y en los informes [docs/pruebas-T2.3.md](docs/pruebas-T2.3.md), [docs/pruebas-T4.3.md](docs/pruebas-T4.3.md) y [frontend/README.md](frontend/README.md).

## 2. Cuadro de avance por tarea

| Tarea | HU | Descripción | Responsable | Horas | Estado | Rama / PR | Fecha | Notas |
|:---:|:---:|---|---|:---:|:---:|---|:---:|---|
| T1.1 | HU-1 | Modelo de datos del prestador | Renzo León | 2 | ✅ | PR 1 unido a `main` | 05/10 | App `prestadores`: `Rubro` y `Prestador` (DNI, rubros, cobertura centro + radio km, estado "Pendiente de verificación"). Migración 0001. |
| T1.2 | HU-1 | API de registro de prestadores | Renzo León | 3 | ✅ | `main` | 05/10 | `POST /api/prestadores/registro/` crea usuario y prestador; exige DNI (8 dígitos, único), ≥ 1 rubro y cobertura; queda "Pendiente de verificación". `GET /api/prestadores/rubros/`. 7 pruebas pasan. Se agregó Django REST Framework (`requirements.txt`). |
| T1.3 | HU-1 | Formulario de registro (web y móvil) | Renzo León | 2 | ✅ | `main` | 05/10 | Formulario React (`frontend/src/RegistroPrestador.jsx`): datos, cuenta, rubros desde la API y mapa Leaflet con círculo de cobertura y radio. Valida en el cliente y muestra los errores del servidor. Diseño responsive (sirve en navegador móvil); la app móvil nativa sigue por definir. Probado de punta a punta contra la API. |
| T4.1 | HU-4 | Flujo de solicitud: envío y aceptación/rechazo | Renzo León | 3 | ✅ | PR 4 unido a `main` | 12/10 | Implementada en `servicios`: modelo `Solicitud` (Pendiente → Aceptada / Rechazada) y migración 0001. `POST /api/servicios/solicitudes/` valida que el prestador esté disponible, ofrezca el rubro y cubra la ubicación del cliente. El prestador ve su bandeja (`GET .../recibidas/`) y acepta o rechaza (`POST .../<id>/aceptar/` y `.../rechazar/`), solo si es el dueño y si sigue Pendiente. El cliente ve el cambio en `GET .../<id>/`. 12 pruebas nuevas; `prestadores` y `servicios`: 30 OK. |
| T3.1 | HU-3 | Búsqueda por rubro, radio y disponibilidad | Luis Abad | 3 | ✅ | PR 3 unido a `main` | 12/10 | Implementada y verificada: `GET /api/prestadores/buscar/`, parámetros validados en español, Haversine reutilizable, cobertura inclusiva y datos públicos. 11 pruebas nuevas; suite completa: 18 OK, repetida tras incorporar `ae51360` del equipo. Luis confirmó HTTP 200 vacío y positivo. T3.3 agregó el orden ascendente; hay una sola ruta, en `prestadores/urls.py`. [Documentación](docs/API_BUSQUEDA.md). |
| T3.2 | HU-3 | Pantalla de resultados con lista de prestadores | Luis Abad | 4 | ✅ | PR 3 unido a `main`; comprobaciones en `pruebas-cierre-sprint1` | 14/10 | Implementada con rubros API, mapa/coordenadas, tarjetas y cancelación; conserva orden recibido. Sobre `7e671cd`: asistente ejecutó lint/build OK, 11 tests búsqueda OK y HTTP positivo/vacío/error/recuperación. Luis comprobó móvil simulado/escritorio sin cortes, Demo a 0 km, vacío al cambiar a Electricidad, error con Django detenido y recuperación sin recargar. Network: rubro 1 anterior cancelado, rubro 2 final 200, sin resultados antiguos. Incorporado después `e20c533`, sin repetir pruebas. Pendientes cambios rápidos de ubicación y otros casos específicos; [evidencia y pasos](frontend/README.md). Estado ✅ conservado del cuadro de Renzo; no se suman horas. |
| T2.3 | HU-1/2 | Pruebas de registro y carga | Luis Abad | 1 | ✅ | PR 7 unido a `main` | 15/10 | Pruebas de registro y carga en `main`, incluida la asociación del certificado y el rechazo sin prestador. Luis confirmó el cierre el 15/10. [Evidencia](docs/pruebas-T2.3.md). |
| T4.3 | HU-4 | Pruebas buscar → solicitar → aceptar | Luis Abad | 2 | ✅ | PR 7 unido a `main` | 15/10 | Flujo probado desde React: envío, Aceptada, Rechazada, validaciones sin crear solicitudes y vista en escritorio y móvil. Luis confirmó el cierre el 15/10. [Evidencia](docs/pruebas-T4.3.md). |
| T2.1 | HU-2 | API de carga de certificaciones (PDF/JPG ≤ 5 MB) | David Valcarcel | 2 | ✅ | PR 6 unido a `main`; reforzada en PR 9 | 14/10 | Exige `prestador` (400 si falta), guarda la certificación vinculada al prestador una sola vez mediante el `FileField` y responde con su nombre y URL. Revisado por Luis. PR 9 (18/10): `prestador` no numérico da 400 en vez de 500; extensión, MIME y primeros bytes (`%PDF-` / `FF D8 FF`) deben coincidir, así un TXT renombrado a `.pdf` se rechaza. |
| T2.2 | HU-2 | Componente de carga de archivos | David Valcarcel | 2 | ✅ | PR 9 unido a `main` | 18/10 | Pasó del Sprint 1. Ruta `/certificaciones` con enlace «Cargar certificado» en el menú (React Router); el registro exitoso muestra el ID y enlaza a la carga. Envía el `prestador`, usa su `UploadCertificado.css` y Vite redirige `/media/` a Django para abrir el archivo subido. |
| T3.3 | HU-3 | Ordenar resultados por distancia | David Valcarcel | 2 | ✅ | PR 6 unido a `main` | 14/10 | `GET /api/prestadores/buscar/` ordena por distancia ascendente, con prueba. Se quitó la ruta duplicada en `servicios`. El endpoint aparte `certificaciones/prestadores/cercanos/` sigue existiendo y ya no se usa. |
| T3.4 | HU-3 | Datos de prueba y tiempo de respuesta (< 3 s) | David Valcarcel | 2 | ✅ | PR 9 unido a `main` | 18/10 | Pasó del Sprint 1. `python manage.py medir_busqueda` crea 500 prestadores ficticios (se descartan al terminar), mide la búsqueda real `GET /api/prestadores/buscar/` con la cabecera `X-Execution-Time-Ms` y escribe `evidencia_rendimiento.txt` (máx. 32,5 ms). 3 pruebas nuevas. Se retiraron los `test_reporte.py` que hacían fallar `manage.py test`. |
| T9.1 | HU-9 | Modelo y API en Django para revisar documentos | David Valcarcel | 2 | ✅ | PR 11 unido a `main` (28/10) | 21/10 | Sprint 2. App `administracion`: modelo `RevisionDocumento` (pendiente / aprobado / rechazado, comentario, revisor y fecha), migraciones 0001 y 0002 (crea revisiones para las certificaciones ya subidas). Cada certificación nueva entra sola como pendiente. `/api/administracion/revisiones/`: listar (`?estado=`), consultar, crear y `PATCH` para aprobar o rechazar; solo administradores. Rechazar exige comentario (400). 16 pruebas nuevas; suite completa: 74 OK. 21/10: usa `EsAdministrador` de T5.1 en vez de `IsAdminUser` (entra el rol `administrador` o `is_staff`), según la revisión de Renzo; 2 pruebas nuevas. Con T5.1 integrada: 108 de 108 OK. Se une después del PR 13. |
| T9.2 | HU-9 | Pantalla web (React) para aprobar o rechazar | David Valcarcel | 2 | ✅ | PR 11 unido a `main` (28/10) | 19/10 | Ruta `/administracion/revisiones` («Revisión de documentos» en el menú): lista los pendientes con prestador, DNI, tipo y formato, y enlace al archivo por `/media/`. «Aprobar» envía el `PATCH` directo; «Rechazar» pide un motivo obligatorio. La tarjeta muestra el resultado sin recargar. Al decidir, el prestador queda Verificado si tiene al menos un documento aprobado (Rechazado si todos se rechazaron). La búsqueda y el envío de solicitudes ahora exigen Verificado. Sesión: login de `/admin/` por el proxy de Vite. 9 pruebas nuevas; suite: 83 OK, ejecutor T4.3 OK, lint y build OK. Probado por HTTP a través del proxy (login, lista, archivo, CSRF, aprobar); falta revisarlo en el navegador. |
| T5.1 | HU-5 | API de autenticación con roles | Renzo León | 3 | ✅ | PR 13 unido a `main` (28/10) | 28/10 | App `usuarios`: `Perfil` con rol, rutas `csrf`, `registro`, `login`, `logout` y `yo`, permisos `EsCliente`, `EsPrestador` y `EsAdministrador`. Contrato en `docs/API_AUTENTICACION.md`. |
| T6.1 | HU-6 | Endpoint para activar y desactivar la disponibilidad | Renzo León | 2 | ✅ | PR 15 unido a `main` (28/10) | 28/10 | `GET` y `PATCH /api/usuarios/disponibilidad/`, solo para el prestador. |
| T5.2 | HU-5 | Pantallas de registro e inicio de sesión | Renzo León | 3 | ✅ | PR 17 unido a `main` (28/10) | 28/10 | `/crear-cuenta`, `/ingresar` con usuario o correo, sesión en la cabecera y cierre de sesión. |
| T6.2 | HU-6 | Interruptor de disponibilidad | Renzo León | 2 | ✅ | PR 18 unido a `main` (28/10) | 28/10 | `/disponibilidad` solo para el prestador, con aviso si no está verificado. |
| T5.3 | HU-5 | Pruebas de autenticación y acceso por rol | David Valcarcel | 1 | ✅ | PR 21 unido a `main` (28/10) | 21/10 | `usuarios/test_acceso_por_rol.py`: 31 pruebas con una cuenta por rol (cliente, prestador, administrador `is_staff` y superusuario) que inicia sesión por `/api/usuarios/login/`. Cubre el rol en `login` y `yo`; `/api/administracion/revisiones/` (admin por `is_staff` o por rol 200/201, cliente, prestador y anónimo 403); bandeja y aceptar/rechazar solo para el prestador dueño (otro prestador 404); búsqueda, rubros y envío de solicitudes públicos; y sin sesión válida (sin credenciales, cookie inventada, Basic, token, sesión cerrada, cuenta desactivada, sin rol de administrador, sin CSRF) 403, nunca 401, porque la API solo usa la sesión de Django. 3 pruebas `expectedFailure` documentan el impedimento 16 (carga de certificados sin sesión). Suite: 139 OK (3 fallas esperadas). Se une después del PR 13 (T5.1) y del PR 11. |
| T6.3 | HU-6 | Pruebas: solo los prestadores activos aparecen en la búsqueda | David Valcarcel | 1 | ✅ | PR 20 unido a `main` (29/10, inicio del Sprint 3) | 29/10 | `servicios/test_disponibilidad_busqueda.py`: 9 pruebas. Las 6 combinaciones de estado × disponibilidad (solo Verificado + Activo aparece) y el flujo por los endpoints reales: registro → certificado → aprobación del admin → `PATCH /api/usuarios/disponibilidad/`. Activo sin verificar, verificado inactivo y rechazado activo quedan fuera; al pasar a Inactivo desaparece en la búsqueda siguiente y al volver a Activo reaparece sin otra aprobación; inactivo no recibe solicitudes. Prueba de mutación: sin el filtro de disponibilidad o sin el de verificado fallan 5 pruebas en cada caso. [Guía manual](docs/GUIA_T6.3.md). 29/10: integra `main`; suite 154 OK (3 fallas esperadas). Unido a `main` el 29/10. |
| T7.3 | HU-7 | Pruebas de precisión del radio de cobertura | David Valcarcel | 2 | ✅ | PR 23 unido a `main` (29/10, inicio del Sprint 3) | 21/10 | `servicios/test_radio_cobertura.py`: 12 pruebas. Los puntos se generan con la fórmula de destino sobre la esfera (independiente de Haversine) en Lima, Tumbes y Tacna y en 8 rumbos. Haversine: devuelve la distancia generada con error < 1e-9 km de 1 m a 100 km, 1° de latitud = 111,195 km, 1° de longitud se acorta con la latitud, coincide con la ley de cosenos entre 6 ciudades y 6 decimales en BD mueven el punto < 0,1 m. Búsqueda: prestador con radio 5 km incluido a 3 km y en el borde exacto de 5 km, excluido a 5,5 km; 1 mm decide en el borde; a 5,05 km al este u oeste queda fuera en las tres ciudades; radios 1 y 100 km; cada prestador usa su radio. Prueba de mutación: una distancia plana (111,32 km por grado) hace fallar 227 casos y un borde exclusivo (`<`), 5. No depende de T7.1 (PR 14). 22/10: verificada de nuevo, 70 de 70 OK; comprobación manual en el paso 8 de la [guía de navegación](docs/GUIA_NAVEGACION.md). |
| T19.2 | HU-19 | OCR del DNI: leer número y nombres y recortar la foto | Renzo León | 4 | ✅ | PR #28 (unido 12/11) | 03/11 | App `verificacion`: `POST`/`GET /api/verificacion/dni/` y fotos solo para el administrador. Lee la MRZ del reverso (ICAO 9303) con sus 4 dígitos de control, corrige letras confundidas por números y vuelve NXX a Ñ; compara el DNI leído con el del prestador y la vigencia; recorta la foto del titular; máximo 5 intentos por día. Fotos en `privado/`, fuera de `/media/`. El OCR (EasyOCR) va detrás de la interfaz `LectorDni`; las 22 pruebas usan un lector falso. Suite completa: 188 OK (3 fallas esperadas). **Falta:** probar EasyOCR con un DNI de muestra (no está instalado en el equipo) y calibrar la zona de recorte de la foto. |
| T19.3 | HU-19 | Validador intercambiable y paso a revisión manual | Renzo León | 2 | ✅ | PR #28 (unido 12/11) | 03/11 | `verificacion/validadores.py`: `simulado` (por defecto, con tabla fija de DNIs ficticios) y `eldni` elegidos con `VALIDADOR_DNI`. Compara apellidos y nombres sin tildes, mayúsculas ni Ñ/NXX; nada de parecidos. Fuente caída o DNI desconocido → `fuente_no_disponible`; nombres distintos → `nombres_distintos`; ambos van a revisión manual. Solo `validado` si todo coincide, y aun así no cambia `Prestador.estado`. Si el DNI no es el del prestador no se consulta la fuente. 12 pruebas nuevas; suite 200 OK (3 fallas esperadas). **Falta:** `eldni` todavía no consulta nada (ver decisiones técnicas); el PO decide si se activa. |
| T19.1 | HU-19 | Pantalla de escaneo del DNI con la cámara | Renzo León | 3 | ✅ | PR #28 (unido 12/11) | 03/11 | `frontend/src/VerificarDni.jsx`, ruta `/verificar-dni` solo para prestadores y enlace «Verificar mi DNI». Cámara trasera con `getUserMedia` y marco guía; foto del frente y del reverso con vista previa y «Repetir». Sin cámara o permiso usa `<input capture="environment">`. Muestra «Validando…», el resultado con lo leído y los motivos en palabras simples, y al volver a entrar el último intento. Lint y build OK. Probado en el navegador: fallback de archivo, envío de las dos fotos, error del servidor y vista del resultado en revisión manual (con un intento de prueba, ya borrado). **Falta:** probar la cámara real en un celular y el envío con OCR real (EasyOCR no está instalado; sin él el servidor responde 500). |
| T4.2 | HU-4 | Pantallas de solicitud y cambio de estado | David Valcarcel | 2 | ✅ | PR 6 unido a `main` | 14/10 | Cada tarjeta de resultados permite solicitar el servicio (`POST /api/servicios/solicitudes/`) y consulta el estado cada 5 s hasta Aceptada o Rechazada. PR 7 agregó validaciones de nombre, teléfono de 9 dígitos y descripción. Probado en el navegador el 15/10. |

## 3. Avance por historia de usuario

| HU | Historia | SP | Horas | Tareas | Estado | Pruebas a cargo |
|:---:|---|:---:|:---:|---|:---:|---|
| HU-1 | Registro del prestador | 3 | 7 h | T1.1 ✅ · T1.2 ✅ · T1.3 ✅ | ✅ 7 de 7 h | Luis (T2.3) |
| HU-2 | Carga de certificaciones | 2 | 5 h | T2.1 ✅ · T2.2 ✅ · T2.3 ✅ | ✅ 5 de 5 h (T2.2 el 18/10) | Luis (T2.3) |
| HU-3 | Búsqueda de prestadores | 5 | 11 h | T3.1 ✅ · T3.2 ✅ · T3.3 ✅ · T3.4 ✅ | ✅ 11 de 11 h (T3.4 el 18/10) | David (T3.4) |
| HU-4 | Solicitud y aceptación | 3 | 7 h | T4.1 ✅ · T4.2 ✅ · T4.3 ✅ | ✅ 7 de 7 h | Luis (T4.3) |

## 4. Avance por integrante

| Integrante | Rol | Horas plan | Horas terminadas | Tareas terminadas | Tareas en curso |
|---|---|:---:|:---:|:---:|---|
| Renzo León | Scrum Master / Desarrollador | 10 h | 10 h | T1.1, T1.2, T1.3, T4.1 | — |
| Luis Abad | Development Team | 10 h | 10 h | T3.1, T3.2, T2.3, T4.3 | — |
| David Valcarcel | Product Owner / Desarrollador | 10 h | 6 h | T2.1, T3.3, T4.2 | — (T2.2 y T3.4 terminadas el 18/10, en el Sprint 2) |
| **Equipo** | | **30 h** | **26 h** | **11** | **0** |

## 5. Burndown (horas pendientes)

| Día | Fecha | Ideal (h) | Real (h) |
|:---:|:---:|:---:|:---:|
| Inicio | 01/10 | 30 | 30 |
| 1 | 01/10 | 27 | 30 |
| 2 | 02/10 | 24 | 30 |
| 3 | 05/10 | 21 | 23 |
| 4 | 06/10 | 18 | 23 |
| 5 | 07/10 | 15 | 23 |
| 6 | 08/10 | 12 | 23 |
| 7 | 09/10 | 9 | 23 |
| 8 | 12/10 | 6 | 13 |
| 9 | 13/10 | 3 | 13 |
| 10 | 14/10 | 0 | 4 |

## 6. Bitácora de avances

Una línea por entrega, la más reciente arriba.

| Fecha | Quién | Qué se hizo | Tarea |
|:---:|---|---|:---:|
| 12/11 | Renzo León | Unió a `main` el PR #27 (contrato) y el #28 (T19.1–T19.3): integró `main` en la rama, resolvió `App.jsx` (menú por rol + enlace «Verificar mi DNI» del prestador), AVANCES y `.gitignore`. 219 pruebas Django OK, lint y build OK. Las 9 h de HU-19 de Renzo quedan integradas; falta T19.4 (Luis) y probar la cámara real en un celular. | T19.1–T19.3 |
| 03/11 | Renzo León | Correcciones a las observaciones de Luis sobre los PR #27 y #28: la imagen se abre completa con Pillow (un JPEG roto o cortado da `400`); PNG y JPG se guardan y sirven con su extensión y tipo reales; si falla el OCR, el recorte o la fuente el intento queda en revisión manual (`lector_no_disponible`) en vez de `500`; `consentimiento.py` con `tiene_consentimiento` (devuelve `True` hasta T21.1) y `IntentoDni.eliminar_datos_biometricos()` para HU-21; la pantalla muestra un mensaje claro por cada error. El contrato se pasó también al #27. 6 pruebas nuevas; suite 206 OK (3 fallas esperadas), lint y build OK. | T19.1–T19.3 |
| 04/11 | Renzo León | Segunda ronda de revisión (Luis y Codex): una respuesta mal formada de la fuente (no es un objeto, sin `estado`, sin apellidos o nombres, o con tipos raros) ya no da error y pasa a revisión manual; `eliminar_datos_biometricos()` no lanza error si un archivo no se borra y devuelve los campos fallidos para que T21.2 los registre; la pantalla avisa si falla la carga del último intento y da un mensaje cuando el 403/429 viene sin detalle. 2 pruebas nuevas; suite 208 OK (3 fallas esperadas). | T19.1–T19.3 |
| 03/11 | Renzo León | T19.1 en la misma rama: pantalla `/verificar-dni` (cámara, vista previa, resultado). Lint y build OK; recorrido en el navegador con una cuenta temporal que se borró después. La cámara no se pudo probar en el panel del navegador (la bloquea); solo el camino sin cámara. Con T19.1, T19.2 y T19.3 las 9 h de Renzo en HU-19 quedan listas para revisión. | T19.1 |
| 03/11 | Renzo León | T19.3 en la misma rama: validadores `simulado` y `eldni`, comparación de nombres normalizada y motivos `fuente_no_disponible` / `nombres_distintos`. `servicio.analizar` ya no devuelve siempre revisión manual: con el DNI correcto y nombres iguales el intento queda `validado`. Suite 200 OK. | T19.3 |
| 03/11 | Renzo León | T19.2 en la rama `feature/t19.2-ocr-dni` (sale de la del contrato, PR #27): app `verificacion` con el modelo `IntentoDni`, `mrz.py` (dígitos de control, fechas, NXX → Ñ), `lector.py` (EasyOCR detrás de `LectorDni`), `servicio.py` (DNI distinto, vencido, sin foto) y la API. 22 pruebas nuevas; suite 188 OK. `requirements.txt` suma `easyocr` y `Pillow`. No se instaló EasyOCR ni se probó con una imagen real: queda pendiente con una muestra ficticia (Luis, T19.4). | T19.2 |
| 29/10 | Renzo León | Contrato de HU-19 en `docs/API_VERIFICACION_DNI.md`: app `verificacion`, `POST`/`GET /api/verificacion/dni/`, lectura de la MRZ del reverso (ICAO 9303, dígitos de control), comparación de nombres normalizada, validador intercambiable (`simulado` por defecto, `eldni` opcional) y revisión manual ante cualquier falla, sin cambiar `Prestador.estado`. Dependencias con HU-21 (consentimiento), T19.4 (muestras ficticias) y HU-9. Falta implementar T19.1, T19.2 y T19.3. | T19.1–T19.3 |
| 05/11 | David Valcarcel | PR #32 (menú por rol y diseño), revisión de Renzo: se integró `main` (#29 y #33; conflicto solo en AVANCES, `App.jsx` se une solo). Los rubros de la portada abren la búsqueda con ese rubro elegido y `/registro-prestador` manda al inicio si ya hay sesión. Se comprobó en el navegador que la cabecera fija queda encima del mapa, sus controles y popups al hacer scroll (el mapa tiene su propio `z-index: 0`). **Pendientes:** actualizar `docs/GUIA_NAVEGACION.md` y `docs/GUIA_T6.3.md` (el registro ya no está en `/`), y que el Scrum Master o el PO aprueben las horas como uso de reserva. | Frontend |
| 05/11 | David Valcarcel | Renzo aprobó el #29 (173/173 OK, lint OK). Se trajo `main` a la rama (conflicto solo en esta bitácora, se conservaron las filas de ambos lados) y se une: **S3-CERT-16 ✅ y el impedimento 16 queda cerrado** (2 h). También queda cerrada **S3-MAPA-R ✅** (1 h): la revisión de #14/#16 terminó con su integración el 04/11 y el redondeo a 3 decimales (#31) que decidió el PO. | S3-CERT-16/S3-MAPA-R |
| 05/11 | David Valcarcel | S3-CERT-16 (#29), revisión de Renzo: con JSON `{"prestador": 5}` (número) la validación del ID lanzaba `TypeError` y la API respondía 500. Ahora el ID se valida como texto: número propio sigue (400 si falta el archivo), de otro prestador 403, decimal/booleano/lista 400. 1 prueba nueva; suite 173 OK sin fallas esperadas. | S3-CERT-16 |
| 03/11 | David Valcarcel | Sprint 3: revisó #14 y #16 sobre `main` (168 OK, mapa probado en el navegador; observaciones por publicar), corrigió el impedimento 16 en `feature/s3-cert-16-carga-certificados` (172 OK, sin `expectedFailure`) y dejó T8.3 bloqueada hasta que Luis publique la rama de HU-8. Detalle en la sección 10. | S3-MAPA-R/S3-CERT-16/T8.3 |
| 04/11 | David Valcarcel | Menú y rutas según el rol, y nuevo diseño del frontend (decisión del PO), en `feature/menu-por-rol-y-diseno`. Portada nueva en `/` con botones por rol, cómo funciona y rubros reales; el registro de prestador pasa a `/registro-prestador`. Cada rol ve solo lo suyo: visitante (buscar, ofrecer servicios, sesión), cliente (buscar), prestador (disponibilidad, certificado) y administrador (revisión de documentos); `/certificaciones` y `/administracion/revisiones` pasan a rutas privadas y el admin entra directo a su panel. Tema visual común en `Tema.css`, cabecera fija y pie. Probado en el navegador con los 4 roles y a 375 px sin desbordes; lint, build y 170 pruebas Django OK. Sin horas del Sprint 3 asignadas: se registra como uso de reserva si el equipo lo aprueba. | Frontend |
| 04/11 | Renzo León | Limpieza de ramas del Sprint 2: se unieron a `main` los PR #14 y #16 (mapa de HU-7, de Luis), #25 (guía de exposición) y #12 (documento Word). Antes de unir, #16 se probó con `main` integrado: 168 pruebas OK (3 fallas esperadas), lint y build OK; la revisión automática dejó 7 observaciones no bloqueantes (la más importante: la búsqueda ahora entrega las coordenadas exactas del prestador, a decidir con el PO). Los conflictos de AVANCES de #25 y #12 se resolvieron conservando las filas de ambos lados. | Cierre S2 |
| 18/10 | Renzo León | Documento Word con la descripción del proyecto (problema, casos de uso, verificación del prestador, IA, arquitectura, API y plan de sprints): [docs/Chamba_PE_Descripcion_del_Proyecto.docx](docs/Chamba_PE_Descripcion_del_Proyecto.docx). | — |
| 29/10 | Renzo León | Guía de exposición y pruebas en vivo de los Sprints 1 y 2 en `docs/exposicion-renzo-sprint1-2.md`: resumen de T1.1–T1.3, T4.1, T5.1, T5.2, T6.1 y T6.2, checklist, guion de la demo, pruebas automáticas (10, 13 y 29 OK), plan B y preguntas probables. Se probó que aceptar una solicitud desde la API navegable funciona con el proxy de Vite. | Exposición |
| 29/10 | David Valcarcel | Unió a `main` el PR 20 (T6.3) y el PR 23 (T7.3), al inicio del Sprint 3; al unir T7.3 chocaron AVANCES y TABLERO y se conservaron las filas de ambas tareas. El Sprint 2 sigue cerrado con 15 de 23 h; estas 3 h cuentan como terminadas el 29/10. Antes de unir verificó las 6 tareas de David juntas (`main` + T6.3 + T7.3 + T8.3): `administracion` 25, T5.3 31 (3 fallas esperadas), T7.3 12, T6.3 9 y T8.3 7 pruebas OK; suite 173 OK, lint y build OK. Recorrido en la app (Django + React, base de prueba aparte): el admin aprueba el certificado en `/administracion/revisiones` y el prestador queda Verificado; aparece a 0, 3 y 4,9 km y no a 5,5 ni 8 km (radio 5 km); con el interruptor de `/disponibilidad` desaparece y reaparece en la búsqueda; la solicitud llega a la bandeja en 66 ms y el cliente la ve aceptada; cliente, prestador y anónimo reciben 403 donde corresponde. Quedan abiertos #14, #16 (Luis) y #22 (base parcial de T8.3). | T6.3/T7.3 |
| 29/10 | David Valcarcel | T6.3: se integró `main` (cierre del Sprint 2) en `feature/t6.3-pruebas-disponibilidad` para el PR 20. `core/settings.py` y `App.jsx` se unieron solos; en AVANCES, SPRINTS y TABLERO se tomó la versión de `main` y se conservaron la fila de T6.3 y la entrada del 22/10. Suite: 154 pruebas OK (3 fallas esperadas del impedimento 16), sin migraciones pendientes. | T6.3 |
| 29/10 | David Valcarcel | T7.3: se integró `main` (cierre del Sprint 2) en `feature/t7.3-pruebas-radio` para el PR 23. Solo chocaron AVANCES, SPRINTS y TABLERO: se tomó la versión de `main` y se conservaron la fila de T7.3 y sus entradas del 21/10 y 22/10. Las 12 pruebas del radio siguen pasando con el filtro de prestador Verificado. Suite: 157 pruebas OK (3 fallas esperadas del impedimento 16), sin migraciones pendientes. | T7.3 |
| 29/10 | Renzo León | Cierre del Sprint 2 actualizado: el 28/10 en la noche David aprobó y unió #13, #15, #17 y #18, y unió #11 y #21. Sprint 2: 15 de 23 h terminadas (HU-5, HU-6 y HU-9 completas). Quedan sin unir #14, #16, #20 y #23 (8 h) y HU-8 pasa al Sprint 3. Se unió el PR 24 (decisión del PO) a `cierre-sprint-2` y se integró `main`. En `main`: 145 pruebas OK, lint y build OK. Burndown del 28/10: 15 h. | Cierre |
| 28/10 | David Valcarcel | **Decisión del PO:** HU-8 (T8.1, T8.2 y T8.3, 7 h) pasa completa al Sprint 3 porque T8.1 no se empezó; la prueba de la bandeja de T8.3 queda como base parcial. El Sprint 2 queda en 23 h comprometidas. Abrió hacia `main` los PRs de pruebas de T5.3, T6.3 y T7.3 y el de la base parcial de T8.3; T5.3 y T6.3 se unen después de #11, #13 y #15. Actualizó TABLERO (11 en revisión, 3 al Sprint 3) y SPRINTS (pendientes y capacidad del Sprint 3). | Cierre |
| 28/10 | Renzo León | Preparó el cierre del Sprint 2 en `docs/sprint2-review.md`: estado de las 14 tareas, guion de la demo, decisiones para el PO y propuesta de retrospectiva. Probó la demo en una rama local que une #18, #11 y #16: 116 de 116 pruebas OK, lint y build OK, y por API el flujo prestador verificado → búsqueda → solicitud → aceptada. Burndown del 28/10: 30 h. | Cierre |
| 28/10 | Renzo León | T6.2 en la rama `feature/t6.2-interruptor-disponibilidad` (sale de T5.2): pantalla `/disponibilidad` con interruptor Activo/Inactivo, solo para el prestador; sin sesión manda a iniciar sesión y vuelve después, con otro rol regresa al inicio. Avisa si el perfil aún no está verificado. Al iniciar sesión, el prestador entra directo a su disponibilidad. Probado en el navegador (escritorio y 375 px): activar, desactivar y que aparezca o no en la búsqueda. Lint y build OK. | T6.2 |
| 28/10 | Renzo León | Burndown del 21/10 al 27/10: 30 h pendientes cada día, porque ningún PR del Sprint 2 entró a `main` (#11, #13, #14 y #15 siguen abiertos sin revisión). | Burndown |
| 28/10 | Renzo León | T5.2 en la rama `feature/t5.2-login-registro` (sale de T6.1): pantallas React de crear cuenta de cliente (`/crear-cuenta`) e iniciar sesión con usuario o correo (`/ingresar`), sesión en la cabecera con el nombre y el rol, y cierre de sesión. `api.js` envía `X-CSRFToken` en cada cambio. Probado en el navegador: registro, cierre, inicio con correo, contraseña incorrecta y sesión al recargar. Lint y build OK. | T5.2 |
| 28/10 | Renzo León | T5.1: agrega `CSRF_TRUSTED_ORIGINS` para el origen de Vite. Al probar T5.2 en el navegador, el proxy de Vite reescribe el Host y Django rechazaba el `Origin` con 403 en registro e inicio de sesión. Es el mismo bloque del PR 11, así que no choca. | T5.1 |
| 22/10 | David Valcarcel | T6.3 en `feature/t6.3-pruebas-disponibilidad`, que sale de T9.1 (PR 11) e integra T6.1 (PR 15); solo hubo conflictos en AVANCES y TABLERO. 9 pruebas en `servicios/test_disponibilidad_busqueda.py` y [guía de comprobación manual](docs/GUIA_T6.3.md). Suite: 123 de 123 OK, sin migraciones pendientes. Falta el PR. | T6.3 |
| 22/10 | David Valcarcel | Verificó T7.3 de nuevo (`python manage.py test`: 70 de 70 OK) y escribió la [guía de navegación](docs/GUIA_NAVEGACION.md) para comprobar en el navegador cada tarea terminada (T1.1 a T4.3 y T7.3), con coordenadas a 3, 5 y 5,5 km del prestador demo. Sin horas nuevas: T7.3 sigue esperando el PR. | T7.3 |
| 21/10 | David Valcarcel | T7.3 en `feature/t7.3-pruebas-radio`: 12 pruebas de precisión del radio (Haversine contra la fórmula de destino y la ley de cosenos; búsqueda a 3, 5 y 5,5 km en 8 rumbos y 3 latitudes; borde de 1 mm; radios 1 y 100 km). Sobre `main`: 70 de 70 OK. Integrada con T5.1, el PR 11 y T5.3: 151 OK (3 fallas esperadas del impedimento 16). Falta el PR. | T7.3 |
| 21/10 | David Valcarcel | T5.3: se trajo el PR 11 actualizado (`main`, T5.1 y `EsAdministrador`). La prueba «quitar `is_staff` quita el acceso» pasa a «quitar el rol y `is_staff`», porque con `EsAdministrador` el rol del perfil también da acceso; nueva prueba de un administrador por rol que entra por el login. 139 de 139 OK (3 fallas esperadas del impedimento 16). | T5.3 |
| 21/10 | David Valcarcel | PR 11 (T9.1 y T9.2) actualizado con las observaciones de Renzo: integra `main` (PR 10, se conservó la plantilla nueva del Sprint 2 y los impedimentos de T9 pasan a ser el 18 y el 19) y T5.1 (PR 13; choque solo en `INSTALLED_APPS` y `urlpatterns`), y la revisión usa `EsAdministrador`. 2 pruebas nuevas (rol `administrador` sin `is_staff` entra; prestador 403). 108 de 108 OK, sin migraciones pendientes. | T9.1/T9.2 |
| 21/10 | Renzo León | T6.1 en la rama `feature/t6.1-disponibilidad` (sale de T5.1): `GET` y `PATCH /api/usuarios/disponibilidad/` solo para el prestador con sesión; un inactivo no aparece en la búsqueda. 6 pruebas nuevas; 87 de 87 OK. PR 15 abierto, revisa David; se une después del PR 13. | T6.1 |
| 21/10 | Renzo León | Burndown del 20/10: 30 h pendientes (ideal 18). Ninguna tarea entró a `main`: T5.1 (PR 13) espera la revisión de David, y T9.1 (PR 11) y T7.1 (PR 14) siguen abiertos. Revisó el PR 11 y le dejó observaciones (choques con `main` y con T5.1, cambiar a `EsAdministrador`). | Burndown |
| 20/10 | Renzo León | Unió el PR 10 (planificación del Sprint 2) a `main` e integró `main` en la rama de T5.1. Burndown del 19/10: 30 h pendientes (ideal 21), porque T5.1 sigue en revisión y ninguna tarea entró a `main`. | Planning/T5.1 |
| 20/10 | David Valcarcel | T5.3 en `feature/t5.3-pruebas-acceso-rol`: se unió la rama de T5.1 (conflictos solo en `settings`, `urls` y AVANCES, se conservaron ambos lados) y se agregaron 30 pruebas de acceso por rol con login real. 136 de 136 OK (3 fallas esperadas del impedimento 16), sin migraciones pendientes. Prueba de mutación: con `IsAuthenticated` en vez de `IsAdminUser` en las revisiones fallan 11 pruebas. Falta el PR, después del de T5.1. | T5.3 |
| 19/10 | Renzo León | T5.1 implementada en la rama `feature/t5.1-autenticacion`: app `usuarios` con `Perfil` (rol), rutas `csrf`, `registro`, `login` (usuario o correo), `logout` y `yo`, permisos `EsCliente`, `EsPrestador` y `EsAdministrador`, migración que da perfil a las cuentas existentes, correo único también en el registro del prestador y bandeja de solicitudes con `EsPrestador`. 23 pruebas nuevas; 81 de 81 OK. PR 13 abierto, revisa David. | T5.1 |
| 19/10 | Renzo León | Contrato de T5.1 en `docs/API_AUTENTICACION.md`: rutas `/api/usuarios/` (csrf, registro, login, logout, yo), sesión de Django con `X-CSRFToken` por el proxy de Vite, rol en `usuarios.Perfil` y permisos `EsCliente`, `EsPrestador` y `EsAdministrador`. Falta implementar la API (PR el 20/10). | T5.1 |
| 19/10 | David Valcarcel | T9.2: pantalla `RevisionDocumentos.jsx` para aprobar o rechazar con motivo, regla de prestador Verificado al decidir y filtro de verificados en la búsqueda y en las solicitudes (se actualizaron los datos de prueba de `servicios`, `medir_busqueda`, `preparar_busqueda_local` y el ejecutor T4.3). Proxy de Vite para `/admin/` y `/static/` y `CSRF_TRUSTED_ORIGINS`. 83 de 83 pruebas OK, ejecutor T4.3 OK, lint y build OK; flujo login → lista → aprobar comprobado por HTTP a través del proxy. Falta la revisión en el navegador y el PR. | T9.2 |
| 18/10 | David Valcarcel | T9.1 en `feature/t9.1-revision-documentos`: app `administracion` con el modelo `RevisionDocumento`, sus migraciones y la API `/api/administracion/revisiones/` (listar, consultar, crear, aprobar o rechazar; solo administradores). 16 pruebas nuevas, 74 de 74 OK y sin migraciones pendientes. Falta abrir el PR. | T9.1 |
| 18/10 | Renzo León | Sprint Planning del Sprint 2 en SPRINTS.md: Renzo HU-5 y HU-6 (`usuarios`), Luis HU-7 y HU-8 (`servicios`), David HU-9 (`administracion`) y las pruebas cruzadas; 10 h cada uno. T5.1 va primero (PR el 20/10) porque bloquea T6.1, T5.2, T9.1 y T8.1. | Planning |
| 18/10 | Renzo León | Revisó y unió el PR 9 de David a `main`: 58 de 58 pruebas OK, sin migraciones pendientes, lint y build OK. Cierra T2.2 y T3.4, que pasaron del Sprint 1. | T2.1/T2.2/T3.4 |
| 17/10 | David Valcarcel | PR 9: carga de certificados en la navegación (`/certificaciones`) con el prestador y proxy `/media/`; validación de `prestador` y de la firma real del archivo; `manage.py medir_busqueda` mide la búsqueda real (< 3000 ms). 8 pruebas nuevas. | T2.1/T2.2/T3.4 |
| 15/10 | Luis Abad | Confirmó el cierre de T2.3 y T4.3: probó envío, aceptación y rechazo, validaciones y la vista en escritorio y móvil. Sprint 1 queda en 26 de 30 h. | T2.3/T4.3 |
| 15/10 | Renzo León | Cierre del Sprint 1: verificó `main` con una instalación limpia (49 de 49 pruebas, ejecutor T4.3 con 32 OK, migraciones al día, lint y build OK) y en el navegador (solicitud con teléfono inválido rechazada, solicitud válida creada y Rechazada mostrada sola; móvil sin scroll horizontal). Actualizó AVANCES, SPRINTS, TABLERO y README con 23 de 30 h y el borrador de la retrospectiva. | Cierre |
| 15/10 | Luis Abad | PR 7 unido a `main`: validaciones de nombre, teléfono de 9 dígitos y descripción en React y Django (6 pruebas nuevas) y tarjeta de prestador corregida. Probó en escritorio y a 360/390 px. | T3.2/T4.3 |
| 15/10 | Luis / asistente | Luis reportó tarjeta correcta en escritorio y 360/390 px, nombre con números y teléfono con letras/8/10 dígitos sin crear solicitudes, y datos válidos con tilde y números en descripción que crean pendiente. Asistente documentó únicamente esos resultados en un commit nuevo; sin repetir tests, sumar horas o cerrar Sprint 1, T2.2 ni T3.4. | T3.2/T4.3 |
| 15/10 | Luis / asistente | Luis reportó 43 pruebas OK y aceptación/rechazo desde React con respuesta por API sobre f5565c0. Asistente corrigió tarjeta y validaciones, ejecutó 49 pruebas OK y lint/build OK; documentó evidencia y pendientes sin nuevas horas ni cierre. Commits de código fc756df y baa41e5; revisión visual posterior pendiente. | T3.2/T2.3/T4.3 |
| 14/10 | David Valcarcel | PR 6 revisado por Luis y unido a `main`: certificado vinculado al prestador, orden por distancia en la búsqueda, pantalla de solicitud, guardado único del archivo, migración regenerada con Django 6.1 y ruta duplicada eliminada. | T2.1/T3.3/T4.2 |
| 14/10 | Asistente | Inspeccionó procesos Python y el puerto 8000 (sin servidor activo); identificó `core/urls.py` cargado desde la raíz actual, eliminó únicamente `core/__pycache__` y `servicios/__pycache__` y verificó sin generar bytecode que `/api/servicios/prestadores/buscar/` resuelve a `BuscarPrestadoresView`. Sin horas ni cambios de burndown, datos, commit o PR. | T3.3 |
| 14/10 | Asistente | Verificó el include principal y la resolución de `/api/servicios/prestadores/buscar/` a `BuscarPrestadoresView`; agregó regresiones para la URL exacta, alias anterior y orden ascendente por distancia (11 pruebas de búsqueda OK) y actualizó la documentación. Sin cambios de datos, horas, commit ni PR; T3.3 continúa parcial hasta revisión e integración. | T3.3 |
| 14/10 | Luis Abad | Registró sus pruebas manuales sobre `7e671cd`: móvil simulado/escritorio sin cortes, Demo Gasfitero ficticio a 0 km en Gasfitería/Lima, cambio a Electricidad con tarjeta anterior retirada y mensaje vacío; Django detenido con error de comunicación y reiniciado con recuperación al pulsar Buscar sin recargar. Búsquedas Gasfitería→Electricidad: Network con solicitudes rubro 1 canceladas y rubro 2 final 200, estado vacío sin reaparecer Demo. Evidencia manual separada de las pruebas del asistente y de Renzo; no repetida en `e20c533`. | T3.2 |
| 14/10 | Luis Abad / asistente | Conservó la comprobación automatizada previa en `7e671cd`: 11 pruebas búsqueda OK, lint/build frontend OK; proxy 200 positivo/vacío, 400 latitud inválida, 502→200 al detener/reiniciar Django. Instalación local de django-cors-headers 4.9.0 en venv existente; ficticio id 2 reutilizado dos veces, SHA-256 BD sin cambios. Resguardó los tres cambios en stash e incorporó origin/main `e20c533` por fast-forward; combinó documentación sin perder avances de Renzo y mantuvo su `django-cors-headers>=4.4`, retirando el pin redundante. Sin nuevas pruebas sobre main, migraciones, cambios a BD, horas nuevas, commits de trabajo, push ni PR. | T3.2 |
| 14/10 | Renzo León | Incorporó a `hu-4-solicitud` el commit de David del 13/10 y agregó `django-cors-headers` a `requirements.txt`, que faltaba y hacía fallar `manage.py` en `main`. Pruebas: 40 (39 OK y la falla intencional de T2.3), ejecutor T4.3 con 32 OK, lint y build OK. | T4.1 |
| 13/10 | David Valcarcel | Subió a `main` (sin PR) la configuración CORS y del proxy para la carga de certificaciones; corrigió el `err` sin usar del lint. | T4.2 |
| 12/10 | Renzo León | Revisó y probó la búsqueda de Luis combinada con la T4.1: 40 pruebas (39 OK y la falla intencional de T2.3), ejecutor T4.3 con 32 OK, build OK y prueba en navegador de registro, búsqueda, flujo de solicitud por el proxy y vista móvil. Unió el PR 3 y el PR 4 a `main`. | T3.1/T3.2/T4.1 |
| 12/10 | Renzo León | T4.1 en la rama `hu-4-solicitud` (creada desde `hu-3-busqueda` con `main` incorporado): modelo `Solicitud` y migración, envío con validación de disponibilidad, rubro y cobertura, bandeja del prestador, aceptar/rechazar solo por el dueño y solo una vez (409 si ya se respondió) y consulta del estado para el cliente. 12 pruebas nuevas; `prestadores` y `servicios`: 30 OK. AVANCES actualizado con lo subido por David entre el 05/10 y el 10/10. | T4.1 |
| 12/10 | Luis Abad | Ejecutó T4.3 en copia temporal de HEAD con API de Renzo `eb39859`: 32 pruebas OK (30 existentes + 2 nuevas encadenando búsqueda/envío/aceptación o rechazo/consulta cliente), Basic Auth real y BD en memoria. Bandeja y cambios persistidos correctos; segunda respuesta 409. Copia eliminada; rama, HEAD, T2.3, BD y media conservados. Añadido ejecutor reproducible y [evidencia](docs/pruebas-T4.3.md). React pendiente por T4.2 e integración T4.1; sin commits, push, fusiones ni horas nuevas. | T4.3 |
| 12/10 | Luis Abad | Revisión tras merge existente 5d2e1db: búsqueda 11 tests OK; suite ampliada 28, 27 OK/1 FAIL por asociación HU-2. Build OK; lint falla en UploadCertificado de David. Añadidos casos T2.3 con BD/media temporales y documentación de manuales previas, incidencias y pendientes. Sin horas nuevas ni tareas completas. [Informe](docs/pruebas-T2.3.md). | T3.1/T3.2/T2.3 |
| 09/10 | David Valcarcel | Subió a `main` la pantalla de envío de certificación con estado (`frontend/src/UploadCertificado.jsx`). | T4.2 |
| 07/10 | David Valcarcel | Script de medición del tiempo de respuesta y `evidencia_rendimiento.txt`. | T3.4 |
| 06/10 | David Valcarcel | Endpoint `prestadores/cercanos/` que ordena por distancia (Haversine) en `certificaciones`. | T3.3 |
| 06/10 | Luis Abad | Preparación para revisión de T3.1/T3.2 en `hu-3-busqueda`: incorporado por avance rápido `ae51360` del equipo sin modificar sus archivos. Suite backend repetida: 18 OK en base de pruebas; lint y build repetidos: código 0. Registrada confirmación manual de Luis de HTTP 200 vacío/positivo y tarjeta ficticia a 0 km. Documentación revisada; commit pendiente de aprobación del mensaje y PR pendiente. HU-3 sigue parcial, sin sumar horas terminadas. | T3.1/T3.2 |
| 06/10 | Luis Abad | Conectó Resultados a Django: selector de rubros, mapa Leaflet y coordenadas exactas, botón Buscar, tarjetas con datos de la API sin calificaciones, estados en español y cancelación de búsquedas anteriores. Conserva registro y navegación, orden del backend, cambios T3.1 y base local. Lint y build en `frontend/`: código 0. Consultas vía proxy Vite: 200 y ficticio a 0 km. Pendientes comprobaciones visuales documentadas y PR; HU-3 parcial, T3.3/T3.4 de David pendientes. Sin commit ni push. | T3.2 |
| 06/10 | Luis Abad | Prueba positiva local de T3.1: comando idempotente `preparar_busqueda_local` creó un único prestador ficticio (id 2), Gasfitería (id 1), disponible, centro -12.046374/-77.042793 y radio 5 km. Segunda ejecución reutilizó el registro sin cambios. Consulta HTTP al servidor local: 200, prestador ficticio a 0.0 km. Sin alterar registros previos ni filtros; no completa T3.4 de David. | T3.1 |
| 06/10 | Luis Abad | Implementó T3.1 en `servicios`, reutilizando `Prestador` y `Rubro`: validación en español, filtros de rubro/disponibilidad/cobertura, distancia Haversine y salida pública. Ejecutó `.\venv\Scripts\python.exe manage.py test --noinput`: 18 pruebas OK (7 existentes + 11 nuevas), con base de pruebas creada y destruida. Documentó endpoint y ejemplos. Sin cambios al frontend, commit ni push; pendiente de PR. T3.2 y HU-3 siguen parciales. | T3.1 |
| 06/10 | Renzo León | Maquetas de las 21 historias de usuario (`docs/renders/historias-usuario.html`) para la presentación; README corregido (tenía un conflicto de merge sin resolver) y ampliado con backlog, estructura y cómo ejecutar. | — |
| 05/10 | David Valcarcel | Componente de carga de archivos con estado "Pendiente de verificación" (`src/UploadCertificado.jsx`). | T2.2 |
| 05/10 | Renzo León | Formulario de registro con rubros y mapa de cobertura (Leaflet); proxy `/api` a Django en Vite. | T1.3 |
| 05/10 | Renzo León | Migración 0002 con 10 rubros iniciales (Gasfitería, Electricidad, Carpintería, etc.). | T1.1 |
| 05/10 | Renzo León | API de registro: `POST /api/prestadores/registro/` y `GET /api/prestadores/rubros/`, con validaciones y 7 pruebas. | T1.2 |
| 05/10 | Renzo León | PR 1 unido a `main`. Modelo `Prestador`/`Rubro` con cobertura y estado; migración y admin; app registrada en `settings`. | T1.1 |
| 04/10 | Luis Abad | Documentó el avance del frontend en `frontend/README.md`. | T3.2 |
| 04/10 | Luis Abad | Frontend React + Vite con el listado de prestadores en tarjetas. | T3.2 |
| 03/10 | David Valcarcel | Resolvió conflicto en `.gitignore`. | — |
| 03/10 | David Valcarcel | API `upload/` de certificaciones: valida PDF/JPG y 5 MB. | T2.1 |
| 01/10 | Renzo León | Planificación del Sprint 1, reparto de tareas y documentación (README, PLAN_EQUIPO, SPRINTS, Lab 07). | — |
| 01/10 | Renzo León | Contexto inicial del proyecto y Django como backend. | — |

## 7. Impedimentos y riesgos

| # | Fecha | Impedimento / riesgo | Afecta a | Responsable | Estado |
|:---:|:---:|---|---|---|:---:|
| 13 | 14/10 | Se detectó que la migración anterior de certificaciones declaraba `id` como `AutoField`, mientras el modelo usaba `BigAutoField`. Resuelto al regenerar la migración limpia. | Migraciones de certificaciones | Equipo | ✅ |
| 12 | 14/10 | Luis cubrió visuales móvil simulado/escritorio, error/recuperación de búsqueda y concurrencia por rubro sobre `7e671cd`. Quedan cambios rápidos de ubicación, salida/retorno durante espera, reintento de rubros, validaciones visibles, regresión de registro y teléfono físico. No hay repetición sobre `e20c533`; el asistente no dispone de navegador. [Detalle](frontend/README.md). | T3.2, seguimiento de comprobaciones | Luis | 🟡 |
| 1 | 05/10 | T1.1 llegaba con 3 días de retraso y bloqueaba la búsqueda y las certificaciones. Resuelto: PR 1 unido a `main` el 05/10. | HU-2, HU-3 | Renzo | ✅ |
| 2 | 05/10 | La carga de certificaciones no se asociaba al prestador. Resuelto: PR 6 unido a `main` el 14/10. | HU-2, T2.3 | David (T2.1) | ✅ |
| 3 | 06/10 | T3.1 y T3.2 esperaban revisión. Resuelto: PR 3 revisado, probado y unido a `main` el 12/10. | HU-3 | Luis | ✅ |
| 4 | 05/10 | T4.1 dependía de HU-3. Resuelto: PR 4 unido a `main` el 12/10, después del PR 3. | HU-4 | Renzo | ✅ |
| 5 | 05/10 | App móvil "por definir" en el stack: el formulario es web responsive, no una app nativa. | HU-1 | Equipo | ⚪ |
| 6 | 12/10 | T2.2, T3.3, T3.4 y T4.2 se subieron directo a `main`, sin PR. T3.3 y T4.2 se rehicieron y entraron por el PR 6; T2.2 y T3.4, por el PR 9 el 18/10. | HU-2, HU-3 | David | ✅ |
| 7 | 12/10 | `certificaciones/test_reporte.py` no es una prueba de Django: al correr `manage.py test` da error (falta `requests`) y reescribe `evidencia_rendimiento.txt`. Resuelto en el PR 9: se retiró y la medición pasó a `manage.py medir_busqueda` con pruebas. | Todas | David | ✅ |
| 8 | 12/10 | El lint fallaba por `err` sin usar en `UploadCertificado.jsx`. Resuelto por David el 13/10. El CSS ya existe y el componente está en la navegación (`/certificaciones`) desde el PR 9. | HU-2 | David (T2.2) | ✅ |
| 9 | 12/10 | El flujo desde React dependía de la pantalla de T4.2. Resuelto: PR 6 la unió a `main` y se probó en el navegador. | HU-4, T4.3 | David (T4.2) | ✅ |
| 10 | 12/10 | Riesgo de cerrar el sprint sin HU-2, HU-3 ni HU-4. Se cerró con 26 de 30 h; pasan 4 h al Sprint 2 (T2.2 y T3.4). | Sprint 1 | Equipo | ✅ |
| 11 | 14/10 | El commit de CORS del 13/10 agregó `corsheaders` a la configuración sin sumarlo a `requirements.txt`: con una instalación nueva, `main` no arranca. Corregido en el PR 4. Además, la vista de carga pone cabeceras CORS a mano aunque ya se usa `django-cors-headers`, y el proxy de Vite ya evita CORS en desarrollo. | Todas | David / Renzo | 🟡 |
| 15 | 17/10 | Cambiar el estado de una solicitud desde el admin de Django deja `respondida` vacía y permite saltarse la regla de una sola respuesta. | T4.1/T4.2 | David | 🟡 |
| 16 | 18/10 | La carga de certificados no pide sesión: cualquiera puede subir un archivo con el ID de otro prestador. Se corrige con la autenticación de HU-5. Desde el 20/10, 3 pruebas `expectedFailure` de T5.3 (cliente, anónimo y otro prestador deberían recibir 403) lo documentan; al corregir la vista pasan y hay que quitarles el decorador. Hoy no se corrige porque el formulario de carga (T2.2) aún no tiene inicio de sesión (T5.2). 03/11: corregido en `feature/s3-cert-16-carga-certificados` (S3-CERT-16), en revisión de Renzo; se cierra al unirse a `main`. 05/11: cerrado con #29, aprobado por Renzo. | HU-2, HU-5 | Equipo | ✅ |
| 17 | 18/10 | El Sprint 2 se planificó el 18/10: el 15/10 y el 16/10 pasaron sin horas. Quedan 8 días hábiles para 30 h (1,25 h diarias por integrante). Si en el Daily del 23/10 quedan más de 15 h, el Product Owner decide qué pasa al Sprint 3. | Sprint 2 | Equipo | 🟡 |
| 18 | 18/10 | La API de revisión (T9.1) exige un administrador, pero aún no hay pantalla de inicio de sesión (T5.2). Desde el 21/10 usa `EsAdministrador` (T5.1), así que también entra el rol `administrador` por `/api/usuarios/login/`. Mientras tanto, la pantalla de T9.2 solo funciona con la sesión del admin de Django (`/admin/`). Mitigado el 19/10: el login de `/admin/` pasa por el proxy de Vite y la pantalla enlaza a él; con HU-5 se reemplaza por el login propio. | T9.2, HU-5 | David / Equipo | 🟡 |
| 19 | 19/10 | Desde T9.2, la búsqueda solo muestra prestadores Verificado. Los prestadores que hoy están en Pendiente dejan de aparecer hasta que un administrador apruebe uno de sus documentos. Hay que avisar al equipo antes de las pruebas manuales. | HU-3, HU-4 | David | 🟡 |
| 20 | 28/10 | T8.1 (push con FCM) no se empezó y el sprint cerró el 28/10: el criterio de HU-8 «la notificación llega al móvil en < 5 s» queda sin cumplir ni probar. Decisión del PO: HU-8 completa pasa al Sprint 3. | HU-8, T8.1, T8.2, T8.3 | Luis / David | ➡️ Sprint 3 |
| 21 | 28/10 | Con HU-8, el Sprint 3 suma 35 h para 30 h de capacidad. Se ajusta en el Sprint Planning del 29/10 (propuesta: HU-13 al Sprint 4). | Sprint 3 | Equipo | 🟡 |
| 14 | 15/10 | El PR 4 y el PR 7 se unieron sin revisión de otro integrante. Acción: activar la protección de `main` en GitHub para exigir 1 aprobación. | Proceso | Renzo | 🟡 |

## 8. Decisiones técnicas

| Fecha | Decisión | Motivo |
|:---:|---|---|
| 03/11 | `eldni` queda registrado como validador pero responde siempre «no disponible». | eldni.com no tiene API: es un formulario con token y publicidad, y sus datos no son oficiales de RENIEC. Sacar los datos de ese HTML es frágil y puede ir contra sus condiciones de uso; lo decide el PO. Mientras tanto, todo caso va a revisión manual, que es lo seguro. |
| 03/11 | Las fotos del DNI se guardan en `RAIZ_PRIVADA` (carpeta `privado/` junto a `manage.py`), no dentro de `MEDIA_ROOT`. | `urls.py` publica `/media/` completo cuando `DEBUG` está activo; una subcarpeta `media/privado/` habría quedado servida sin sesión. Solo el administrador las pide por la API. |
| 19/10 | El prestador queda Verificado si tiene al menos un documento aprobado; sigue Pendiente mientras haya documentos por revisar y pasa a Rechazado si todos se rechazaron. La búsqueda y el envío de solicitudes exigen Verificado. | Criterio de HU-9: «al aprobar, el estado cambia a Verificado y el prestador ya puede recibir solicitudes». |
| 19/10 | Hasta HU-5, el panel usa la sesión del admin de Django: Vite redirige `/admin/` y `/static/` y Django confía en el origen de Vite para el CSRF. | Sin inicio de sesión propio, es la forma de exigir `is_staff` sin guardar contraseñas en el frontend. |
| 18/10 | La revisión de documentos vive en una tabla aparte (`administracion.RevisionDocumento`, 1:1 con la certificación), no en campos nuevos de `CertificacionPrestador`. Al decidir, se copia el estado a la certificación. | La app `administracion` es de David (HU-9, HU-13) y guarda quién revisó y cuándo sin tocar el modelo de HU-2. |
| 12/10 | La `Solicitud` guarda nombre y teléfono del cliente en vez de enlazar a una cuenta. | Todavía no hay inicio de sesión de clientes (HU-5, Sprint 2); entonces se agrega la relación con el usuario. |
| 12/10 | Aceptar, rechazar y la bandeja exigen sesión del prestador dueño; enviar y consultar el estado no piden sesión. | Solo el prestador puede responder sus solicitudes; el cliente aún no tiene cuenta. La consulta del cliente no muestra su teléfono. |
| 12/10 | La solicitud solo se responde una vez; responder de nuevo devuelve 409. | Evita que un prestador cambie de Aceptada a Rechazada después de confirmar. |
| 14/10 | La búsqueda se expone solo en `/api/prestadores/buscar/`; se elimina la ruta duplicada desde `servicios`. | Una única ruta evita mantener dos URLs para la misma vista. |
| 06/10 | Resultados usa el proxy `/api` existente y Leaflet; cancela búsquedas al cambiar filtros, repetir consulta o salir de la pantalla y consume el orden recibido del backend. | Evita datos desactualizados y permite que T3.3 ordene los resultados sin lógica duplicada en el frontend. |
| 06/10 | Búsqueda en `servicios` con Haversine reutilizable y radio inclusivo; respuesta pública sin calificaciones. | Respeta T3.1 y la organización del backend; T3.3 añade orden ascendente y T3.4 debe medir la búsqueda. Sin migraciones ni cambios a registros locales. |
| 05/10 | Mapa con Leaflet + OpenStreetMap; en desarrollo el frontend usa un proxy `/api` hacia Django (puerto 8000). | Sin claves ni costo; evita configurar CORS. |
| 05/10 | Rubros iniciales cargados con una migración de datos. | Todos los entornos arrancan con los mismos rubros. |
| 01/10 | Backend en Django + Django REST Framework. | Definido en el plan del equipo. |
| 05/10 | Se agrega Django REST Framework; el registro crea el usuario y el prestador en una sola operación. | La API necesita un usuario (relación 1:1); el login con roles llega en HU-5. |
| 05/10 | Cobertura del prestador como centro (latitud, longitud) + radio en km, sin PostGIS. | Más simple; basta para la búsqueda por radio y por distancia. |

## 9. Retrospectiva del Sprint 1

> Borrador del Scrum Master con los datos del sprint; se confirma o ajusta con el equipo.

| ¿Qué salió bien? | ¿Qué mejorar? | Acciones para el Sprint 2 |
|---|---|---|
| HU-1 completa en `main` con PR revisado y pruebas. | Arranque lento: 0 h terminadas los dos primeros días y T1.1, que bloqueaba al resto, llegó con 3 días de retraso. | Las tareas que bloquean a otros (modelos y APIs) se hacen en los primeros 2 días. |
| Búsqueda, API de solicitudes y pantalla de solicitud funcionan de punta a punta y se probaron en el navegador. | Commits directo a `main` sin PR: rompieron `main` (faltaba `django-cors-headers`) y duplicaron lógica. | Activar la protección de `main` en GitHub: solo se une por PR con 1 aprobación. |
| Las pruebas cruzadas funcionaron: Luis probó la API de T4.1 y su prueba de T2.3 detectó el certificado sin vincular. | T4.2 se hizo primero como pantalla de certificados por no leer los criterios de aceptación. | Antes de empezar, leer los criterios en SPRINTS.md y consultar dudas en el Daily. |
| Al cierre, `main` quedó con 49 de 49 pruebas OK, lint y build sin errores. | Integración tardía: casi todo se unió entre el 12/10 y el 14/10, por eso el burndown no bajó una semana. | PR pequeños: abrirlos apenas la tarea funcione y revisarlos en máximo 1 día. |
| | PR 4 y PR 7 se unieron sin revisión de otro integrante. | Si un PR agrega una librería, también actualiza `requirements.txt`; quien revisa instala desde cero. |
| | AVANCES.md no se actualizó a diario y los informes de pruebas salieron muy largos. | AVANCES.md al cierre de cada día, en pocas líneas; el detalle va en `docs/`. |

## 10. Seguimiento del Sprint 3 · 29/10–11/11/2026

**Plan aprobado por David y Renzo, comunicado por el Scrum Master el 29/10.** Objetivo: cierre de HU-7/HU-8, corrección de certificados y avance de HU-19/HU-21. [Backlog, criterios, dependencias y burndown](SPRINTS.md#plan-aprobado-del-sprint-3).

| Responsable | Trabajo S3 asignado | Ya integrado en S3 | Pendiente al formalizar | Reserva sin consumo registrado | Capacidad |
|---|---:|---:|---:|---:|---:|
| Luis | 7 h | 0 h | 7 h | 3 h | 10 h |
| David | 8 h | 3 h | 5 h | 2 h | 10 h |
| Renzo | 9 h | 0 h | 9 h | 1 h | 10 h |
| **Total** | **24 h** | **3 h** | **21 h** | **6 h** | **30 h** |

Las 3 h de David son T6.3 (#20, 1 h) y T7.3 (#23, 2 h), integradas el 29/10 según origin/main `f515837`. No cambian el cierre de S2: **15 de 23 h**, no una cuenta de tareas. No volver a sumar las estimaciones originales de mapa/HU-8 a las horas restantes del nuevo plan. Las cifras de planificación no acreditan horas reales consumidas.

| Referencia | Responsable | Horas S3 pendientes iniciales | Estado / próximo paso |
|---|---|---:|---|
| T6.3 + T7.3 | David | 0 | ✅ Integradas #20/#23; 3 h ya incluidas en su asignación S3 |
| S3-MAPA-R | David | 1 | Revisar #14/#16 y regresiones sobre main |
| S3-MAPA-I | Luis | 1 | Ajustes/integración de T7.1/T7.2, sin duplicar su desarrollo original |
| T8.1 + T8.2 | Luis | 2 | 🟡 Código local conservado; completar escenarios y revisión, sin commit/PR |
| T8.3 | David | 2 | 🟡 #22 base parcial de bandeja; repetir push real y validar evidencia |
| S3-CERT-16 | David | 2 | Pendiente sesión, rol, propiedad y CSRF de carga; pruebas y expectedFailure |
| T19.1 / T19.2 / T19.3 | Renzo | 3 + 4 + 2 | Sin iniciar; contrato cámara/OCR/validador y alternativa manual |
| T19.4 | Luis | 2 | Prueba cruzada de HU-19 |
| T21.1 / T21.2 | Luis | 1 + 1 | Consentimiento y eliminación; revisión de David |

**HU-8 continúa abierta.** Se conserva toda la evidencia local: pruebas automatizadas previas (127 Django y 3 worker simuladas), recepción real, aceptación/rechazo, primer/segundo plano y pestaña cerrada con Chrome abierto, logout y cambio a administrador. Un ensayo grabado #17 dio ≈1,3 s (margen visual orientativo ±0,1 s); no sustituye repetición/validación de David ni demuestra una garantía general. Las pruebas locales anteriores tampoco equivalen a ejecutar la suite sobre el main remoto más reciente. Detalle en la guía local HU8_NOTIFICACIONES.md (todavía no publicada).

**HU-13 (4 h) y HU-20 (11 h) quedan postergadas fuera del compromiso inicial S3**, por acuerdo aprobado. Sin asignación automática a S4 y sin restar sus horas de la reserva. La propuesta anterior de 35 h queda sustituida por este plan de 24 + 6.

### Registro diario de avances, bloqueos y completadas

| Fecha | Referencia / responsable | Avance / bloqueo | Evidencia / condición de cierre | Próximo paso |
|---|---|---|---|---|
| 29/10 | T6.3 / David | ✅ Integrada, 1 h de referencia | #20 | No replanificar implementación |
| 29/10 | T7.3 / David | ✅ Integrada, 2 h de referencia | #23 | Regresión conjunta al revisar mapa |
| 29/10 | Planning / Scrum Master | Acuerdo de David y Renzo formalizado; 21 h pendientes + 6 h reserva | SPRINTS.md; sin horas nuevas consumidas por documentarlo | Iniciar revisión y contratos |
| 29/10 | HU-8 / Luis y David | Local/parcial; medición y escenarios documentados | Guía HU-8; falta validación cruzada e integración | Completar matriz y revisión |
| 03/11 | S3-MAPA-R / David | Revisión de #14 y luego #16 unidos sobre `main` `15d8dc9`: 168 pruebas OK (3 fallas esperadas), lint y build OK; mapa probado en el navegador con marcadores coincidentes, encuadre, popups y teclado. Observaciones: ambos chocan con #26 en AVANCES/SPRINTS; las coordenadas públicas tienen 6 decimales; «Sin calificaciones» espera decisión del PO; #16 suma ~2 MB de evidencias y una ruta local en `resultados.json` | Comentarios por publicar en #14 y #16 | Luis ajusta e integra (S3-MAPA-I), primero #14 |
| 03/11 | S3-CERT-16 / David | 🟡 Corrección en `feature/s3-cert-16-carga-certificados`: la carga exige sesión de prestador y CSRF, y el documento va al perfil de la sesión (otro perfil, cliente, admin o anónimo: 403). Se mantienen formatos, 5 MB y firma. `/certificaciones` pasa a ruta privada sin campo de ID. 6 pruebas nuevas y se quitan los 3 `expectedFailure` de T5.3: 172 OK sin fallas esperadas; lint, build y prueba en el navegador OK | PR por abrir; revisa Renzo | Abrir PR y pedir revisión a Renzo |
| 03/11 | T8.3 / David | 🔴 Bloqueada: el push de HU-8 (`feature/hu8-notificaciones`) sigue solo en la máquina de Luis, sin commit ni PR | Sin rama publicada no hay push real que validar | Luis publica la rama; David valida y repite medidas |
| 04/11 | Menú por rol y diseño / David | 🟡 PR #32 (`feature/menu-por-rol-y-diseno`), con `main` integrado el 05/11 | 4 roles probados en el navegador; 170 pruebas OK | Commit, PR y revisión; unir después del #29 (ambos tocan rutas de `App.jsx`) |
| 05/11 | S3-MAPA-R / David | ✅ Revisión cerrada | #14 y #16 unidos el 04/11; #31 redondea coordenadas a 3 decimales | — |
| 05/11 | S3-CERT-16 / David | ✅ Unida a `main` con #29; impedimento 16 cerrado | Aprobado por Renzo; 173 OK sin `expectedFailure` | Burndown: lo actualiza el Scrum Master |
| 05/11 | Menú por rol y diseño / David | 🟡 PR #32 revisado por Renzo; observaciones atendidas | Cabecera vs. mapa comprobada en el navegador; rubro preseleccionado y guardia del registro | Unir #32; actualizar las guías de navegación |
| Por registrar | Tarea / responsable | Avance o bloqueo | PR / prueba / evidencia | Acción, responsable y fecha |

### Bloqueos y decisiones vigentes

- David: impedimento 16 de certificados; Renzo revisa cambios de prestadores/permisos.
- David/Luis: #14 y #16 abiertos; revisar #14 antes de #16, conservar funcionalidades al preparar integración futura.
- David/Luis: #22 no acredita entrega FCM; HU-8 requiere evidencia real y revisión.
- Renzo: calidad OCR y disponibilidad/contrato de fuente externa; revisión manual ante fallo.
- Luis/PO David: acordar contrato y atención de eliminación/consentimiento, y tratamiento de «Sin calificaciones» en HU-7.
- Scrum Master: verificar protección de main/revisión; registrar consumo de reserva por persona. El impedimento de capacidad de 35 h se resuelve documentalmente con el recorte aprobado, sin dar por resueltos los riesgos técnicos.

**Corte inicial del burndown S3:** 24 h asignadas, 3 h integradas el 29/10, 21 h pendientes, 6 h de reserva sin consumo registrado. Los cortes siguientes y el uso de reserva se anotan en SPRINTS.md sin reemplazar los resultados históricos de S2. Al completar una tarea, actualizar estado, evidencia, pendiente y saldo de reserva; no convertir una prueba local en cierre de historia.

## Preparación T7.1 / PR #14 · 02/11/2026

Se conserva íntegro el documento de main f515837 y, abajo, el historial particular de T7.1 como evidencia fechada. El código del PR devuelve coordenadas numéricas de solo lectura y no incluye marcadores, CSS de mapa ni Playwright. No se requiere una corrección funcional adicional de T7.1 según la revisión integrada.

La prueba de integración con main se realizó en review/hu7-t71-t72: 165 Django pasan y 3 expectedFailure heredadas. Este worktree parte de la cabecera remota #14 (5ddaa14) y todavía NO contiene la sincronización de código con main. El contrato de destino usa los filtros actuales de main, que deberán conservarse al sincronizar después de autorización. T7.1 sigue pendiente de revisión e integración; 0 h adicionales terminadas, sin cambios de cierre S2 ni planificación S3.

### Historial particular conservado (afirmaciones del corte original)

## 10. Sprint 2 — avance local de T7.1 (19/10/2026)

| Tarea | Responsable | Horas plan | Estado | Rama / PR |
|---|---|---|---|---|
| T7.1 — consulta por coordenadas | Luis Abad | 2 h | ✅ Unido a `main` el 04/11 (PR #14) | `main` (PR #14) |

- **Implementado:** misma búsqueda pública, ahora con latitud/longitud numéricas del centro de cobertura; conserva tarjetas, Haversine, filtros, radios propios, orden y cabecera de tiempo. Contrato en [docs/API_BUSQUEDA.md](docs/API_BUSQUEDA.md).
- **Probado:** Python de `venv`, `manage.py test --noinput`: 60 pruebas OK en 2.574 s, sin problemas en el system check; dos pruebas nuevas y contrato público actualizado. Datos exclusivamente en la base de pruebas; sin modificar registros locales. Sin prueba visual del frontend.
- **Evidencia manual reportada por Luis (19/10):** búsqueda HTTP 200, prestador ficticio id `2`, latitud `-12.046374`, longitud `-77.042793`, `distancia_km: 0.0` y cabecera `X-Execution-Time-Ms: 16.782`. Confirma la respuesta para ese caso; no valida el filtro Verificado pendiente.
- **Integración/revisión pendiente:** David revisa el PR y ejecuta T7.3; T9.1 todavía debe actualizar el estado del prestador e integrar el filtro de verificados en búsqueda/solicitudes. Renzo debe confirmar continuidad de consulta pública al integrar roles/disponibilidad. Sin soporte real de calificaciones: propuesta de promedio `null`, cantidad `0` y texto «Sin calificaciones», aún fuera de la respuesta.
- **Bitácora:** 19/10, Luis/asistente: verificados rama limpia y HEAD `8198a83`; fetch remoto completado con certificados de Windows; revisadas planificación y ramas T5.1/T9.1 sin fusionar. Preparados código, pruebas y documentación para revisión local.
- **Horas y burndown:** 2 h planificadas para T7.1; horas reales no informadas por Luis. Se suman **0 h terminadas** hasta revisión e integración; no se descuenta T7.1 del burndown ni se infiere el avance de otros integrantes.
- **Alcance:** T7.2, T7.3, HU-7 y Sprint 2 siguen pendientes; sin notificaciones ni HU-12 implementadas.

## Actualización autorizada de T7.1 / PR #14 · 02/11/2026

- Cabecera remota comprobada sin cambios: 5ddaa14. Se conserva su historial y se incorpora main f515837 por merge normal, sin rebase ni force push. No hubo conflictos de código ni conflictos documentales residuales: las resoluciones previamente preparadas se combinaron automáticamente.
- Registros de main y avances históricos preservados; cierre S2 sigue en 15 de 23 h. No cambia la planificación de S3.
- Pruebas de esta combinación real: 168 Django en 23,807 s (165 aprobadas y 3 expectedFailure conocidas del impedimento 16), system check sin problemas y makemigrations --check --dry-run sin cambios. git diff --check correcto.
- Diff contra main limitado a coordenadas/pruebas T7.1 y sus documentos; sin marcadores, frontend, Playwright ni cambios HU-8/Firebase. Actualización autorizada únicamente de la rama original de #14; David sigue pendiente de revisar y no se declara HU-7 terminada.


## Corrección local HU-7 tras decisión del PO · 04/11/2026

David (PO) comunica revisión de #14/#16 y pruebas aprobadas. Decide mantener «Sin calificaciones» hasta implementar HU-12 y redondear exclusivamente las coordenadas públicas de búsqueda a un máximo de 3 decimales. Esta decisión sustituye la precisión pública y la decisión pendiente de las notas anteriores, conservadas como historia.

- T7.1 / #14: corrección preparada en `prepare/hu7-t71-pr14`, cabecera remota `36de951`; main `15d8dc9` ya incorporado. Solo serialización de salida, contrato y pruebas; coordenadas almacenadas, distancia, orden y cobertura conservan precisión original.
- T7.2 / #16: cabecera remota `e149076`, descendiente de #14. Validación del frontend contra el backend corregido de #14, sin copiar commits. Mantener el texto aprobado no requiere cambiar el producto.
- Estado: pendiente de revisión de esta corrección, sin commit, push, merge a main ni cierre. Sin nuevas horas terminadas ni consumo de reserva registrado; cierre S2 15/23 h y planificación S3 24 h + 6 h intactos. No se modifican HU-8, Firebase, HU-19, HU-21 ni credenciales.
- Validación final: `manage.py test --noinput`, 170 pruebas en 34,094 s: 167 aprobadas y 3 expectedFailure heredadas de certificados; system check sin problemas. `makemigrations --check --dry-run`: sin cambios. Lint y build correctos en ambos worktrees (#14 y #16); `git diff --check` correcto en ambos.
- Playwright final, secuencial y sin reintentos: 16/16 T7.2 en 20,8 s (API/teselas simuladas); 14/14 integraciones en 40,7 s (Django corregido de #14 y teselas OSM reales, SQLite ficticia temporal). Se verifica salida redondeada, proyección de marcadores, puntos compartidos, encuadre, filtros, tarjetas, teclado, responsive 320/390/1280 y solicitud ficticia. El frontend de #16 no requiere cambios funcionales. «Sin calificaciones» conserva su comportamiento.
- Incidencias durante la preparación: primera ejecución Django falló por un dato nuevo de prueba con radio fraccionario; corregido a radio entero de 1 km sin cambiar el modelo. Las primeras suites Playwright concurrentes compartieron `test-results` y una integración falló al cerrar la traza (ENOENT); se interrumpieron tras completar los casos al quedar procesos pendientes. Otra ejecución restringida de T7.2 registró 14 casos aprobados y 2 fallidos (sin marcadores / selector no encontrado al arrancar), con fixtures simulados originales, y también quedó pendiente de cierre. Se repitieron las suites completas secuencialmente, con salidas separadas y permisos de procesos para Chromium/servidores: ambas terminaron con código 0. No se alteraron timeouts, aserciones del mapa ni reintentos para aprobar. No se detectaron regresiones del producto en las ejecuciones finales; se conservan las incidencias sin atribuir una causa interna no demostrada.
- Evidencias nuevas archivadas localmente en el worktree T7.2 bajo `.hu7-local/redondeo-2026-11-04/evidencias/`; los artefactos históricos versionados se conservan. Las salidas separadas de navegador están en `frontend/.hu7-local/`. No sustituyen validación en teléfono físico ni otros navegadores.
- Procedimiento propuesto, pendiente de autorización: crear una sola vez el commit de corrección en #14 y actualizar su rama remota mediante push normal. Después incorporar esa misma rama en #16 mediante merge normal (sin cherry-pick, sin copiar el backend y sin rebase/force push), junto con el ajuste local de expectativas de integración. Tras integrar #14 en main conservando su historial, sincronizar #16 con main y repetir las pruebas/diff: el backend de #14 debe quedar fuera del diff de #16 contra main. No se ejecuta ahora ninguno de estos pasos de publicación o integración.

### Publicación autorizada de la corrección · 04/11/2026

El usuario revisó la preparación y autorizó actualizar los PR existentes, sin integrar a main. Fetch confirma #14 `36de951`, #16 `e149076` y main `15d8dc9`, sin cambios en esas cabeceras. GitHub informa ambos PR abiertos y sin conflictos. Cambios revisados: cuatro archivos de T7.1 y únicamente la expectativa de integración de T7.2; sin archivos ajenos.

Revalidación previa de #14: 170 Django en 24,952 s (167 aprobadas y 3 expectedFailure heredadas), system check correcto; lint y build correctos. Se autoriza un solo commit de la corrección en #14 y su incorporación mediante merge normal en #16, preservando el mismo commit y sin cherry-pick/squash. La nota nueva se ubica antes de la última sección histórica compartida para conservar intactos los registros posteriores de T7.2 al fusionar. Sin cambios de estado de HU-7, horas, burndown ni planificación.

## Preparaci?n local tras revisi?n de David ? 03/11/2026

Se incorpora main 15d8dc9 en la preparaci?n de #14 mediante merge normal, conservando ?ntegra la planificaci?n aprobada por #26 y el historial de T7.1. Cierre S2: 15 de 23 h; S3: 24 h de trabajo y 6 h de reserva. Las coordenadas p?blicas conservan su comportamiento y precisi?n actuales; cualquier cambio queda pendiente del PO. HU-7 sigue abierta, sin horas nuevas terminadas. Esta preparaci?n no publica ni actualiza el PR.


## Preparación T7.2 / PR #16 · 02/11/2026

Se conserva íntegro el documento de main f515837. Este worktree parte de #16 (485edb4), que ya contiene #14 (5ddaa14). Se trasladan únicamente las dos correcciones de mapa, su suite Chromium y documentación; servicios/serializers.py y servicios/tests.py no reciben otra copia de T7.1.

Prueba integrada previa en review/hu7-t71-t72: 165 Django pasan, 3 expectedFailure heredadas, 16 Chromium con API/teselas simuladas, lint/build OK. Esta evidencia se conserva diferenciada de la prueba sobre la cabecera antigua. La sincronización de código con main sigue pendiente de autorización; documentos resueltos no equivalen a un merge de historial. T7.2/HU-7 no terminadas, sin horas adicionales descontadas ni cambios de cierre S2 o planificación S3.


## 11. Sprint 2 — T7.2, implementación y pruebas manuales (27/10/2026)

- **Luis/asistente:** mapa existente con un marcador por prestador en sus coordenadas, cliente azul y prestadores verdes; ventanas con nombre, rubros, distancia y «Sin calificaciones». Coordenadas coincidentes permiten consultar a todos en la misma ventana. Se conservan tarjetas y solicitudes, cancelación y limpieza al cambiar filtros, carga, errores y ausencia de resultados. Encuadre automático al cambiar ubicación/resultados y botón para recuperarlo después de explorar.
- **Validación ejecutada:** `npm run lint` y `npm run build` en `frontend/`, ambos OK. Se restauraron las dependencias del lockfile con `npm ci --ignore-scripts` y certificados del sistema; sin cambios de versiones ni bibliotecas nuevas. El primer build falló por `react-router-dom` ausente en la instalación local; el build posterior pasó.
- **Pruebas manuales realizadas por Luis en Chrome:** Gasfitería muestra al ficticio Demo Gasfitero en mapa y tarjeta; ventana con nombre, rubro, distancia y «Sin calificaciones»; cerrar/abrir no cambia coordenadas; cambiar rubro elimina marcador y tarjeta; rubro sin coincidencias muestra el aviso correspondiente. A 390 px, mapa y ventana correctos; a 320 × 800, tarjeta, campos y botón de solicitud sin cortes. Las variaciones de distancia entre capturas se deben al cambio manual de ubicación por Luis.
- **Pruebas pendientes:** coordenadas compartidas, envío y validaciones de solicitudes, encuadre/exploración, limpieza al cambiar ubicación, cancelación con respuestas tardías y estados de carga/error; no se presentan como aprobadas. La automatización anterior fue bloqueada por Computer Use; las pruebas visuales registradas son de Luis. No se alteraron registros locales durante esta actualización. Detalle en [docs/pruebas-T7.2.md](docs/pruebas-T7.2.md).
- **Estado y dependencias:** entrega autorizada en dos commits y un único PR de T7.2; pendiente de revisión e integración. Consulta pública a GitHub el 27/10: PR #14 abierto y sin integrar. La rama hereda T7.1 (`5c30dfb`, `5ddaa14`) y el merge `e42ae77`; los cambios nuevos T7.2 son JSX/CSS y documentación. David debe integrar el filtro Verificado en T9.1 y ejecutar T7.3. Sin Firebase/T8.1/T8.2 ni calificaciones inventadas. HU-7 y Sprint 2 siguen abiertos.
- **Horas y burndown:** horas reales no informadas; 0 h adicionales terminadas y sin descuento del burndown hasta revisión e integración. Se conserva el reparto del equipo.

## Transferencia selectiva local T7.2 · 02/11/2026

- Rama prepare/hu7-t72-pr16: incorporados encuadres sin animación y suite de 14 integraciones desde el worktree de validación, preservando payload/accesibilidad y pruebas previas. Sin cambios backend/T7.1 ni copia de documentos sobre los registros existentes.
- Pruebas nuevas en destino: 16 Chromium simuladas aprobadas y 14 integraciones aprobadas. Las integraciones combinan este frontend con el backend compatible del worktree de validación; la base antigua de #16 no se sincronizó ni se declara integrada. SQLite ficticia nueva en destino. Lint/build/diff-check correctos. [Detalle y limitaciones](docs/pruebas-T7.2.md).
- Estado sin cierre: pendiente revisión de David, sincronización de dependencias, repetición de pruebas sobre la combinación final y validación física/humana. No se modifican PR #14/#16 ni se publica. Horas reales no informadas; 0 h nuevas terminadas, burndown y cierre histórico intactos.

## Sincronización local T7.2 con T7.1 · 02/11/2026

- Tras verificar cabeceras remotas #14 8c887ae y #16 485edb4, se conserva el historial de #16 y se integra localmente #14 mediante merge normal; sin publicación. Conflictos documentales resueltos preservando registros de main, actualización de T7.1 e historial particular de T7.2. Sin conflictos de código.
- Se mantienen login, disponibilidad, filtros, coordenadas y mapa corregido. Pruebas sobre el backend propio del worktree pendientes de esta ejecución; las 14 aprobadas anteriores usaron backend externo compatible.
- HU-7/T7.2 sin cierre, horas reales no informadas y 0 h nuevas terminadas; no se altera el cierre histórico 15/23 h ni la planificación S3.

**Detención por fallo nuevo (02/11):** Django sobre backend propio: 168 pruebas en 25,426 s, 165 aprobadas y 3 expectedFailure conocidas; system check correcto. Suite T7.2: 15 aprobadas y 1 fallida, «cambiar rubro o coordenadas retira tarjetas y marcadores anteriores» (mapa.spec.mjs:81): esperaba dos tarjetas y obtuvo cero tras la primera búsqueda. Causa no confirmada. Se detiene el trabajo conforme a la instrucción del usuario; no se reintenta ni corrige para ocultar el fallo. Las 14 integraciones, lint y build independiente no se ejecutaron después de esta detención; el build previo de la suite sí completó. Merge pendiente de commit, documentos resueltos en índice, cabecera entrante 8c887ae; único commit nuevo 23831c2. Sin publicación. Otros cinco worktrees, HU-8/Firebase y base principal verificados intactos.

## Depuración y validación del merge local T7.2 · 03/11/2026

- El fallo 0 tarjetas/carga se reprodujo de forma intermitente: 8/10 aprobadas y 2 fallidas con page.route. Traza: respuesta CSRF simulada entregada por fulfill pero no recibida por Chromium; búsqueda sin evento de emisión. Causa localizada en interceptación/entrega del entorno de prueba, no en datos recibidos/render; defecto interno del navegador no determinado.
- Corrección exclusiva de prueba: interceptar API/teselas por contexto aislado y adjuntar eventos de red. Sin cambios de producto, tiempos ni aserciones. 10/10 repeticiones y 16/16 suite completa aprobadas; 14/14 integración con backend propio, sin HU7_TEST_BACKEND_ROOT. Django: 168, 165 aprobadas + 3 expectedFailure conocidas; lint/build/diff-check correctos. [Detalle](docs/pruebas-T7.2.md).
- Merge local autorizado de #14 8c887ae sobre T7.2 23831c2, documentos resueltos preservando main y ambos historiales; sin publicación. Backend de #14 intacto. HU-8/Firebase y demás worktrees verificados sin cambios.
- HU-7/T7.2 sin cierre; pendiente revisión y validación física/humana. Horas reales no informadas, 0 h nuevas terminadas; cierre S2 15/23 h y planificación S3 sin cambios.


## Preparaci?n de correcciones T7.2 tras #26 ? 03/11/2026

Merge local del #14 preparado (`36de951`) conservando el historial de #16 y toda la planificaci?n de main `15d8dc9`. Conflictos AVANCES/SPRINTS resueltos sin quitar registros. Django 168 (165 aprobadas y tres expectedFailure conocidas), Playwright 16/16 e integraci?n real propia 14/14; lint/build correctos. Evidencias p?blicas saneadas; informes locales de integridad archivados en carpeta ignorada. [Informe y l?mites](docs/preparacion-correcciones-HU7.md). Precisi?n de coordenadas y ?Sin calificaciones? sin cambios, pendientes del PO. Sin push ni actualizaci?n de PR, sin cierre de HU-7 ni horas nuevas terminadas.

## Preparación del cierre técnico HU-7 · 04/11/2026

El usuario autoriza preparar y validar la corrección pendiente, sin commit, push, merge ni PR. Se crea el worktree independiente `Chamba-PE-hu7-cierre-tecnico`, rama `fix/hu7-cierre-tecnico`, desde `origin/main 1d80cb7`. La base ya contiene #14, #16 y #31, incluido el redondeo público y la decisión del PO; no se vuelven a integrar. Las notas anteriores se conservan como historia. Esta preparación no acredita horas nuevas terminadas, consumo de reserva ni cierre funcional adicional; no modifica el historial S2 ni la planificación S3.

### Cambios preparados

- `frontend/tests/hu7-local/integracion.spec.mjs`: compara las coordenadas públicas con el fixture redondeado a tres decimales y comprueba su precisión máxima. Se conservan tipos, campos, distancias, cobertura, filtros y todos los escenarios existentes.
- `frontend/playwright.config.mjs`: mantiene Chromium/canal `chromium`, aserciones y timeout de 30 s; ejecuta un worker para reproducibilidad en este entorno, conserva trazas ante fallos y separa la salida en `test-results/hu7`.
- `frontend/playwright.hu7-local.config.mjs`: únicamente separa la salida en `test-results/hu7-local`; mantiene Chromium, un worker, timeout de 45 s, Django propio y OSM real.
- Sin cambios al producto, serializer, modelos, cálculos internos, coordenadas almacenadas ni «Sin calificaciones». Sin cambios de dependencias. No se eliminan pruebas, aserciones ni escenarios; no se agregan retries, sleeps o ampliaciones de timeout.

### Investigación y resultados conservados

| Experimento | Resultado | Conclusión |
|---|---|---|
| Configuración inicial, dos workers, dos casos repetidos tres veces | 5/6 aprobados, un fallo, 38,469 s | Intermitencia reproducida en el worktree nuevo |
| Chromium directo sin proxy, mismos dos workers | 10/10 casos dirigidos y 16/16 suite completa; serie ampliada posterior 18/20, dos fallos en 46,666 s | Experimento descartado: no demuestra solución; flag retirado |
| Headless shell, dos workers | 20/20 casos dirigidos; suite completa posterior 15/16, un fallo en 39,057 s | Experimento descartado como solución por sí solo; canal original restaurado |
| Headless shell, un worker, tres suites completas | 48/48, 42,648 s | Control diagnóstico de ejecución serial |
| Configuración FINAL: canal Chromium original, un worker, tres suites completas | **48/48, 52,265 s**, sin fallos, omisiones ni retries | Los 16 escenarios pasan tres veces, con cancelación y respuestas tardías conservadas |

Las trazas muestran bloqueos de arranque/entrega de red y, en un caso, búsqueda cancelada sin respuesta posterior observable. La evidencia es compatible con contención en la automatización paralela; el componente interno exacto no está identificado. La mitigación comprobada es ejecutar serialmente, sin presentar como resuelto el fallo de transporte bajo dos workers. Se sigue la recomendación de [Playwright para estabilidad y reproducibilidad en CI](https://playwright.dev/docs/ci). El paralelismo se puede volver a investigar mediante controles explícitos; los fallos anteriores no se sustituyen por las ejecuciones aprobadas.

### Verificación final sobre la rama preparada

| Verificación | Resultado |
|---|---|
| Django, `manage.py test --noinput` | 170 ejecutadas en 27,158 s: 167 aprobadas y 3 expectedFailure heredadas de certificados; system check correcto |
| `makemigrations --check --dry-run` | Sin cambios |
| Playwright T7.2 | 48/48, tres ejecuciones completas de los 16 escenarios, canal original y un worker |
| 14 integraciones, configuración final | **14/14, 26,574 s**, cero retries; backend del PROPIO worktree, sin HU7_TEST_BACKEND_ROOT, SQLite ficticia temporal y teselas OSM HTTPS reales |
| Lint y build | Correctos; build final 253 ms |
| `git diff --check` | Correcto |

Las integraciones comprueban precisión pública, proyección de marcadores, cobertura, rubros, disponibilidad, estados, lista/mapa, vacío, errores de red, cancelación, sesión/CSRF, solicitud ficticia, puntos compartidos, encuadre, teclado y responsive 320/390/1280. Las llamadas de sesión/disponibilidad son pruebas de regresión ya existentes: no se modifica HU-8, Firebase, HU-19, HU-21 ni credenciales.

Los resultados y evidencias nuevos se archivan localmente en `.hu7-local/cierre-tecnico/` y `frontend/.hu7-local/`; las capturas y registros históricos versionados se conservan. Se guardan todos los fallos, JSON y trazas de los experimentos descartados, incluido el fallo de la suite completa en headless shell. Las carpetas de pruebas/diagnóstico están ignoradas y fuera del diff propuesto. Los siete worktrees anteriores, incluida la carpeta original, conservan HEAD, estado y hashes de sus archivos locales respecto del manifiesto inicial.

**Estado para revisión:** sí hace falta una corrección adicional de pruebas/configuración: main aún compara fixtures de seis decimales en la integración pese a que #31 ya redondea la API. La propuesta se limita a esa expectativa, la ejecución reproducible y esta evidencia. No requiere otra implementación del mapa o del backend. Quedan pendientes teléfono físico, GPS/permisos, otros navegadores y accesibilidad humana, además de la revisión final de David. La ejecución paralela no se declara estabilizada y las muestras seriales no garantizan ausencia universal de intermitencias. Sin commit, push, merge ni PR; se detiene para revisión del usuario.

### Publicación autorizada para revisión de David · 04/11/2026

El usuario revisó esta preparación y autorizó un commit, push normal de `fix/hu7-cierre-tecnico` y un PR nuevo hacia main, sin integrarlo ni cerrar HU-7. Fetch previo confirma `origin/main 1d80cb7`, idéntico a la base validada. Se revisa el diff completo, limitado a los cuatro archivos anteriores; no se incluyen temporales, trazas, rutas personales, credenciales ni evidencias privadas.

Se comprueban los tres `expectedFailure` de `usuarios.test_acceso_por_rol.CargaDeCertificacionPorRolTests`: anónimo, cliente y prestador que intenta subir certificaciones para otro prestador. Los decoradores, las aserciones y el código de carga ya están en main y no cambian aquí. La verificación aislada previa a publicar ejecuta cuatro pruebas en 0,082 s: una aprobada y esos mismos tres fallos esperados, system check correcto. No son regresiones introducidas por HU-7 ni se ocultan o eliminan.

Se conservan los resultados completos y limitaciones arriba registrados: 170 Django (167 aprobadas y tres fallos esperados), 48/48 T7.2, 14/14 integraciones reales, lint/build y diff-check correctos. El PR documentará la mitigación serial, los fallos paralelos conservados y las validaciones manuales pendientes. La autorización no cambia el estado de HU-7, el historial S2, la planificación S3 ni los otros worktrees; la integración queda pendiente de revisión de David.
