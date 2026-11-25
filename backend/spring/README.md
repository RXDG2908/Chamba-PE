# Back End de usuario — Spring Boot

Responsable: **David Valcarcel** · Revisa: Renzo León

Parte **Usuario** de la arquitectura (ver la sección *Arquitectura* del [README](../../README.md)):
la app móvil (`app-movil/`) y la web de usuario (`web-portal/`) consumen este backend,
que comparte la base de datos con el back end de administración (`backend/django/`).

> **Estado (25/11/2026):** ya responden `rubros` y `buscar`, iguales a Django. Faltan solicitudes,
> disponibilidad y el inicio de sesión con Firebase (ver la tabla de abajo).

## Cómo ejecutarlo

Necesitas **Java 21** (`java -version`). Maven no hace falta: `mvnw` lo descarga la primera vez.

```powershell
cd backend/spring
.\mvnw spring-boot:run
```

Queda en `http://localhost:8080` (otro puerto: variable `PORT`). Por defecto lee la misma
`backend/django/db.sqlite3` de Django **en solo lectura**, así que primero corre las migraciones de Django.
Puedes tener Django (8000) y Spring (8080) abiertos a la vez sobre la misma base.

Pruebas (usan una base temporal con el esquema de Django, no tocan la tuya):

```powershell
.\mvnw test
```

| Variable | Para qué |
|---|---|
| `PORT` | Puerto (por defecto 8080) |
| `SPRING_DATASOURCE_URL` | Otra base, en formato JDBC (`jdbc:sqlite:...` o `jdbc:postgresql://host:5432/base`). Si es SQLite, deja `?open_mode=1` al final (solo lectura). No se usa `DATABASE_URL` porque los hostings la llenan con `postgres://usuario:clave@...`, que JDBC no entiende |
| `SPRING_PROFILES_ACTIVE=postgres` + `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Base PostgreSQL compartida, cuando exista. Sin la URL el arranque falla (no abre el SQLite por error) |

La ruta del SQLite por defecto es relativa a la carpeta desde donde se arranca. Al iniciar, Spring
escribe en el registro la ruta completa que abrió (`Base de datos SQLite: ...`, con `(NO EXISTE)` si falta).

## Organización del código

`src/main/java/pe/tecsup/chambape/`

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
  - `ConfiguracionWeb`: CORS abierto para GET, como `CORS_ALLOW_ALL_ORIGINS` de Django.
  - `AvisoBaseDeDatos`: registra al arrancar qué base se abrió.

## Alcance acordado

Mismas rutas y respuestas JSON que la API actual de Django, para que la app y la web
solo cambien la dirección del servidor:

| Ruta | Uso | Estado |
|---|---|:---:|
| `GET /api/prestadores/rubros/` | Lista de rubros | ✅ |
| `GET /api/prestadores/buscar/` | Búsqueda: solo prestadores Verificados y Activos, dentro de su radio, coordenadas públicas con 3 decimales ([contrato](../../docs/API_BUSQUEDA.md)) | ✅ |
| `POST /api/servicios/solicitudes/` · `GET /api/servicios/solicitudes/{id}/` | Enviar una solicitud y consultar su estado | Pendiente |
| `GET /api/servicios/solicitudes/recibidas/` · `POST .../{id}/aceptar/` · `.../rechazar/` | Bandeja del prestador y respuesta | Pendiente |
| `GET` · `PATCH /api/usuarios/disponibilidad/` | Disponibilidad del prestador | Pendiente |

La búsqueda incluye también `calificacion_promedio` (`null` sin calificaciones) y `calificaciones_total`
de HU-12; Spring solo los lee, los mantiene Django.

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

`backend/django/core/test_esquema_spring.py` falla si una migración de Django cambia una columna de la copia
del esquema que usan las pruebas de Spring (`src/test/resources/esquema-django.sql`).

El inicio de sesión de la app usa Firebase Authentication: Spring Boot valida el token y
busca al usuario por `firebase_uid` (campo de `usuarios.Perfil`).

## Reglas de la base de datos compartida

- **Django es el dueño de las tablas:** las crea y modifica con sus migraciones.
- **Spring Boot no crea ni cambia tablas** (`spring.jpa.hibernate.ddl-auto=none`). Con SQLite además
  abre la base en solo lectura. Con PostgreSQL se pasará a `validate` cuando probemos contra la base real;
  antes hay que revisar los tipos (Django usa `smallint` e `integer` con CHECK donde SQLite no distingue) y
  dar a Spring un usuario de PostgreSQL con solo `SELECT`, porque ahí el pool no está en solo lectura.
- La conexión se configura con variables de entorno; nunca se versionan contraseñas.
