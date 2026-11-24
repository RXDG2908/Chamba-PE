# Back End de usuario — Spring Boot

Responsable: **David Valcarcel** · Revisa: Renzo León

Parte **Usuario** de la arquitectura (ver la sección *Arquitectura* del [README](../../README.md)):
la app móvil (`app-movil/`) y la web de usuario (`web-portal/`) consumen este backend,
que comparte la base de datos con el back end de administración (`backend/django/`).

> **Estado (23/11/2026):** ya responden `rubros` y `buscar`, iguales a Django. Faltan solicitudes,
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
| `DATABASE_URL` | Otra base. Si es SQLite, deja `?open_mode=1` al final (solo lectura) |
| `SPRING_PROFILES_ACTIVE=postgres` + `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | Base PostgreSQL compartida, cuando exista |

## Organización del código

`src/main/java/pe/tecsup/chambape/`

- `prestadores/`: entidades `Prestador` y `Rubro` (solo lectura, `@Immutable`), repositorios,
  `BusquedaService` (filtros, distancia Haversine, orden y redondeo igual que Django) y `PrestadoresController`.
- `comun/`: errores de validación con el mismo formato que DRF (`{"campo": ["mensaje"]}`) y
  `NumerosComoPython`, que lee números igual que Python (`"1.0"`, `"1_0"` y `" 1 "` valen; `"1d"` y `"0x10"` no).

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

Comparado con Django real sobre la misma base (23/11 y, ya con HU-12, 24/11): 59 consultas (rubros,
filtros, distancias, redondeo, calificaciones y todos los mensajes de error), sin diferencias.

El inicio de sesión de la app usa Firebase Authentication: Spring Boot valida el token y
busca al usuario por `firebase_uid` (campo de `usuarios.Perfil`).

## Reglas de la base de datos compartida

- **Django es el dueño de las tablas:** las crea y modifica con sus migraciones.
- **Spring Boot no crea ni cambia tablas** (`spring.jpa.hibernate.ddl-auto=none`). Con SQLite además
  abre la base en solo lectura. Con PostgreSQL se pasará a `validate` cuando probemos contra la base real.
- La conexión se configura con variables de entorno; nunca se versionan contraseñas.
