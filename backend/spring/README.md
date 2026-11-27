# Back End de usuario — Spring Boot

Responsable: **David Valcarcel** · Revisa: Renzo León

Parte **Usuario** de la arquitectura (ver la sección *Arquitectura* del [README](../../README.md)):
la app móvil (`app-movil/`) y la web de usuario (`web-portal/`) consumen este backend,
que comparte la base de datos con el back end de administración (`backend/django/`).

> **Estado (26/11/2026):** responden todas las rutas de usuario (rubros, búsqueda, solicitudes, bandeja del
> prestador, seguimiento, disponibilidad, registro desde la app, calificaciones y el token de avisos de la web), iguales a Django.
> **La app Android y la web de usuario ya las consumen** como pide el diagrama: la app con el token de Firebase
> (`apiBaseUrl` en el puerto 8080) y la web con la misma sesión de Django (el proxy de Vite manda esas rutas a
> Spring; el resto sigue en Django).

## Cómo ejecutarlo

Necesitas **Java 21** (`java -version`). Maven no hace falta: `mvnw` lo descarga la primera vez.

```powershell
cd backend/spring
.\mvnw spring-boot:run
```

Queda en `http://localhost:8080` (otro puerto: variable `PORT`). Por defecto usa la misma
`backend/django/db.sqlite3` de Django, así que primero corre las migraciones de Django.

**Para desarrollar con la web**, arranca Spring con `DJANGO_DEBUG=true` (igual que Django): así los dos usan la
misma clave de desarrollo y Spring reconoce la sesión con la que inicias en la web. Sin Spring abierto, la
búsqueda, las solicitudes y las calificaciones de la web fallan (o arranca Vite con
`CHAMBA_API_USUARIO=http://127.0.0.1:8000` para mandar todo a Django).

```powershell
$env:DJANGO_DEBUG = "true"; .\mvnw spring-boot:run
```
 Puedes tener Django
(8000) y Spring (8080) abiertos a la vez sobre la misma base: los dos esperan hasta 5 s si el otro está
escribiendo (`busy_timeout`).

Pruebas (usan una base temporal con el esquema de Django, no tocan la tuya):

```powershell
.\mvnw test
```

| Variable | Para qué |
|---|---|
| `PORT` | Puerto (por defecto 8080) |
| `SPRING_DATASOURCE_URL` | Otra base, en formato JDBC (`jdbc:sqlite:...?busy_timeout=5000` o `jdbc:postgresql://host:5432/base`). No se usa `DATABASE_URL` porque los hostings la llenan con `postgres://usuario:clave@...`, que JDBC no entiende |
| `DJANGO_SECRET_KEY` | **La misma** que usa Django. Con ella Spring lee la sesión de la web (cookie `sessionid`) y firma el código de seguimiento (HU-11) igual que `signing.Signer`; sin ella la web entra como visitante y no hay código de seguimiento |
| `DJANGO_DEBUG=true` | Como en Django: sin `DJANGO_SECRET_KEY` usa la misma clave de desarrollo local. Solo para desarrollar |
| `CSRF_TRUSTED_ORIGINS` | Orígenes de la web aceptados en POST/PATCH con sesión, separados por comas (por defecto `http://localhost:5173,http://127.0.0.1:5173`, como Django) |
| `FIREBASE_AUTH_PROJECT_ID` | ID del proyecto de Firebase, el mismo que en Django. Sin él, cualquier `Authorization: Bearer` da 403 «El acceso con token no está disponible.» |
| `FIREBASE_CLAVES_URL` | De dónde leer las claves públicas de los tokens. Por defecto las de Google; solo se cambia para pruebas locales |
| `FCM_ENABLED=true` + `FIREBASE_PROJECT_ID` + `GOOGLE_APPLICATION_CREDENTIALS` | Aviso push al prestador cuando llega una solicitud, con la misma cuenta de servicio que Django. Apagado por defecto. La ruta del JSON se pone en la variable, nunca el archivo en el repo |
| `SPRING_PROFILES_ACTIVE=postgres` + `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Base PostgreSQL compartida, cuando exista. Sin la URL el arranque falla (no abre el SQLite por error) |

La ruta del SQLite por defecto es relativa a la carpeta desde donde se arranca. Al iniciar, Spring
escribe en el registro la ruta completa que abrió (`Base de datos SQLite: ...`, con `(NO EXISTE)` si falta).

## Organización del código

`src/main/java/pe/tecsup/chambape/`

- `usuarios/`: `FiltroFirebase` decide quién llama, en el orden de DRF: primero la sesión de la web
  (`SesionDjango` lee `django_session`, comprueba la firma, el backend y el hash de la contraseña; `CsrfDjango`
  exige el CSRF en las escrituras) y luego `Authorization: Bearer` (valida el token con `VerificadorFirebase` y
  busca la cuenta por `firebase_uid` con `Cuentas`). Una cuenta suspendida o bloqueada por HU-13 recibe el mismo
  403 que en Django. Además `DisponibilidadController` y `RegistroMovilController`/`RegistroMovil` (alta de
  cliente y de prestador desde la app, con las mismas filas que `create_user` y las señales de Django).
- `calificaciones/`: `CalificacionesController` y `CalificacionesService` (HU-12: «Mis servicios», una
  calificación por solicitud Finalizada y el promedio del prestador en la misma transacción).
- `servicios/`: `SolicitudesController`, `SolicitudesService` (validación campo por campo y reglas en el mismo
  orden que el serializer de Django), `Solicitudes` (SQL con `JdbcTemplate`: guarda fechas y decimales con el
  formato de Django), `CodigoSeguimiento`, `Notificador` (FCM HTTP v1, se envía al confirmar la transacción) y
  `NotificacionesController` (HU-8: el token FCM del navegador del prestador, con la clave de su sesión de Django).
- `ia/`: `RecomendacionController` y `RecomendacionService` (HU-10): toma los prestadores de `BusquedaService`,
  cuenta sus servicios en curso y calcula puntaje, orden, tiempo estimado y motivo en el mismo orden de
  operaciones que Python (redondeos con `round()` mitad al par y `:.1f` sobre el valor binario exacto).
- `prestadores/`: entidades `Prestador` y `Rubro` (solo lectura, `@Immutable`), repositorios,
  `BusquedaService` (filtros, distancia Haversine, orden y redondeo igual que Django) y `PrestadoresController`.
- `comun/`: lo que hace que Spring responda como Django:
  - `ManejadorErrores` y `ErroresDelServidor`: errores con la forma de DRF, `{"campo": ["mensaje"]}` al validar y
    `{"detail": "..."}` en lo demás (405 con cabecera `Allow`, 404, 500 sin detalles internos, y los que
    rechaza Tomcat), en vez de la página de error de Spring Boot.
  - `ConsultaComoDjango`: lee la URL como `QueryDict` (`+` es espacio, un `%` mal formado se deja tal cual,
    bytes que no son UTF-8 se cambian por «�», gana el último valor repetido).
  - `NumerosComoPython`: lee números igual que `int()`/`float()` de Python (`"1.0"`, `"1_0"`, `" 1 "` y
    dígitos de otras escrituras como `"١"` valen; `"1d"` y `"0x10"` no). Los largos se cuentan en caracteres.
  - `ComoDrf`, `PythonRepr`, `CuerpoComoDrf`, `FechasDjango`, `RespuestaError`: campos (`DecimalField`,
    `CharField`, `BooleanField`, claves foráneas) con los mismos mensajes de DRF, cuerpo JSON o formulario como
    `request.data`, y fechas como las guarda y las muestra Django.
  - `ConfiguracionWeb`: CORS como `CORS_ALLOW_ALL_ORIGINS` de Django (sin cookies entre sitios; las escrituras
    con sesión las protege el CSRF).
  - `ClaveDjango`: la SECRET_KEY de Django resuelta igual que en `settings.py`.
  - `AvisoBaseDeDatos`: registra al arrancar qué base se abrió.

## Alcance acordado

Mismas rutas y respuestas JSON que la API actual de Django, para que la app y la web
solo cambien la dirección del servidor:

| Ruta | Uso | Estado |
|---|---|:---:|
| `GET /api/prestadores/rubros/` | Lista de rubros | ✅ |
| `GET /api/prestadores/buscar/` | Búsqueda: solo prestadores Verificados y Activos, dentro de su radio, coordenadas públicas con 3 decimales ([contrato](../../docs/API_BUSQUEDA.md)) | ✅ |
| `POST /api/servicios/solicitudes/` · `GET /api/servicios/solicitudes/{id}/` | Enviar una solicitud (con o sin cuenta) y consultar su estado con la cuenta o con `?codigo=` | ✅ |
| `GET /api/servicios/solicitudes/recibidas/` · `.../recibidas/{id}/` · `POST .../{id}/aceptar/` · `.../rechazar/` · `.../avanzar/` | Bandeja del prestador, respuesta y seguimiento (HU-4, HU-11) | ✅ |
| `GET` · `PATCH /api/usuarios/disponibilidad/` | Disponibilidad del prestador | ✅ |
| `POST /api/usuarios/registro-movil/` · `POST /api/prestadores/registro-movil/` | Alta de cliente y de prestador desde la app, con el token de Firebase | ✅ 26/11 |
| `GET /api/calificaciones/mis-servicios/` · `POST /api/calificaciones/` | «Mis servicios» del cliente y calificar (HU-12) | ✅ 26/11 |
| `GET /api/ia/recomendacion/` | Prestador recomendado y hasta 3 alternativas con puntaje, motivo y tiempo estimado (HU-10), con la misma fórmula y textos que `ia/recomendacion.py` ([contrato](../../docs/API_RECOMENDACION.md)). Pública, como la búsqueda; hoy la usa la web | ✅ 27/11 |
| `POST` · `DELETE /api/servicios/notificaciones/token/` | El prestador registra o quita el token FCM de su navegador (HU-8). Queda ligado a la sesión de Django con la que se registró, como en Django | ✅ 26/11 |
| `GET /api/usuarios/yo/` | Rol y prestador de la cuenta: la app lee de aquí su perfil con el token (antes, Firestore). Los 403 de la app traen `codigo` (`cuenta_no_vinculada`, `cuenta_sancionada`) | ✅ 26/11 |

Con estas, Spring tiene las 13 rutas que usa la app Android y la del token de avisos de la web. Todas aceptan el token de la app **y** la sesión de la
web.

Quedan en Django (Back End de administración y cuentas): el registro e inicio de sesión de la web
(`/api/usuarios/login/`, `logout/`, `csrf/`, `yo/`, `registro/` y `/api/prestadores/registro/`), la verificación de
identidad (HU-19 a HU-21), los certificados y todo `administracion/`. El proxy de Vite (`web-portal/vite.config.js`) envía a Spring solo
las rutas de la tabla.

La búsqueda incluye también `calificacion_promedio` (`null` sin calificaciones) y `calificaciones_total`
de HU-12; los recalcula quien guarda la calificación (Django o Spring, con la misma fórmula).

### Comparación con Django

`tools/paridad.py` pide lo mismo a Django y a Spring abiertos sobre la misma base y compara estado y JSON
(solo biblioteca estándar, no escribe nada):

```powershell
python backend/spring/tools/paridad.py --django http://127.0.0.1:8000 --spring http://127.0.0.1:8080
```

Los números se comparan como números: una distancia puede diferir en el último decimal (1 o 2 ulp, por el
orden de las operaciones de coma flotante), y eso se cuenta aparte como «iguales numéricamente», no como
«idénticas». El 25/11: 68 respuestas (rubros y 67 búsquedas, incluidas URLs con `%` mal formado, `| { } [ ]`,
dígitos y espacios Unicode y consultas de 12 KB), 68 idénticas. A igual distancia los dos ordenan por `id`.

`tools/paridad_solicitudes/correr.py` prueba las rutas que escriben. Arma una base con datos ficticios, la copia
dos veces y levanta Django sobre una y Spring sobre la otra, con la misma `DJANGO_SECRET_KEY` y una clave RSA de
prueba que reemplaza a las de Google (en los dos). Repite el mismo escenario en ambos (crear con y sin cuenta,
JSON y formulario, cada error de validación, prestador sin verificar, no disponible, sancionado o lejos, tokens
vencidos, de otro proyecto o sin vincular, aceptar, rechazar, avanzar hasta Finalizada, conflictos,
disponibilidad, registro desde la app con cada error de campo, calificaciones y su promedio, el token FCM de la web
(registrar, repetir, cada error del token, quitar y quitar uno ajeno) y la web con sesión:
CSRF ausente, mal formado, incorrecto u otro origen, sesión con otra contraseña, cuenta sancionada y sesión antes
que token) y al final abre otro Django sobre la base que escribió Spring para comprobar que la lee igual
(`PARIDAD_VER=1` muestra cada respuesta):

```powershell
python backend/spring/tools/paridad_solicitudes/correr.py
```

El 26/11 (con HU-8 y HU-10): 291 de 291 respuestas idénticas (las fechas se comparan por formato) y 14 de 14 en la lectura cruzada
(la última compara las filas de `servicios_tokenfcm` que dejó cada uno, porque esa tabla no tiene ruta de lectura).
Además se probó en el navegador: con la sesión iniciada en Django, la web buscó, envió una solicitud, vio «Mis
servicios» y el prestador aceptó y avanzó, todo por Spring (ninguna de esas rutas apareció en el registro de Django).

Diferencias conocidas con Django: no acepta `multipart/form-data` (la app y la web mandan JSON); el texto de un
JSON mal formado es otro (el estado 400 es el mismo); en el registro de prestador por formulario, `rubros` solo
toma el último valor (la app manda JSON); con una sesión cuyo hash ya no coincide Django la borra y Spring solo la
ignora (Spring nunca escribe sesiones); en HTTPS sin `Origin` el chequeo del Referer es más simple que el de Django
(los navegadores actuales siempre envían `Origin` en POST).

`backend/django/core/test_esquema_spring.py` falla si una migración de Django cambia una columna de la copia
del esquema que usan las pruebas de Spring (`src/test/resources/esquema-django.sql`).

El inicio de sesión de la app usa Firebase Authentication: Spring Boot valida el token (firma, vencimiento,
proyecto y emisor) y busca al usuario por `firebase_uid` (campo de `usuarios.Perfil`), igual que Django.

## Reglas de la base de datos compartida

- **Django es el dueño de las tablas:** las crea y modifica con sus migraciones.
- **Spring Boot no crea ni cambia tablas** (`spring.jpa.hibernate.ddl-auto=none`): solo escribe filas en
  `servicios_solicitud`, `servicios_cambioestado`, `calificaciones_calificacion`, `prestadores_prestador`
  (disponibilidad, promedio y altas desde la app), `prestadores_prestador_rubros`, `auth_user` y
  `usuarios_perfil` (altas desde la app), y borra tokens FCM que ya no sirven. Lee `django_session`, nunca la
  escribe. Con PostgreSQL se pasará a `validate` cuando probemos contra la base real; antes hay que revisar los
  tipos (Django usa `smallint` e `integer` con CHECK donde SQLite no distingue) y dar a Spring un usuario con
  permisos solo sobre esas tablas.
- La conexión se configura con variables de entorno; nunca se versionan contraseñas.
