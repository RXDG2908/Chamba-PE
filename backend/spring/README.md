# Back End de usuario — Spring Boot

Responsable: **David Valcarcel** · Revisa: Renzo León

Parte **Usuario** de la arquitectura (ver la sección *Arquitectura* del [README](../../README.md)):
la app móvil (`app-movil/`) y la web de usuario (`web-portal/`) consumen este backend,
que comparte la base de datos con el back end de administración (`backend/django/`).

> **Estado:** en construcción. Esta carpeta se creó con la estructura del 17/11/2026 para que
> el código de Spring Boot no choque con el de Django, Android ni React.

## Alcance acordado

Mismas rutas y respuestas JSON que la API actual de Django, para que la app y la web
solo cambien la dirección del servidor:

| Ruta | Uso |
|---|---|
| `GET /api/prestadores/rubros/` | Lista de rubros |
| `GET /api/prestadores/buscar/` | Búsqueda: solo prestadores Verificados y Activos, dentro de su radio, coordenadas públicas con 3 decimales ([contrato](../../docs/API_BUSQUEDA.md)) |
| `POST /api/servicios/solicitudes/` · `GET /api/servicios/solicitudes/{id}/` | Enviar una solicitud y consultar su estado |
| `GET /api/servicios/solicitudes/recibidas/` · `POST .../{id}/aceptar/` · `.../rechazar/` | Bandeja del prestador y respuesta |
| `GET` · `PATCH /api/usuarios/disponibilidad/` | Disponibilidad del prestador |

El inicio de sesión de la app usa Firebase Authentication: Spring Boot valida el token y
busca al usuario por `firebase_uid` (campo de `usuarios.Perfil`).

## Reglas de la base de datos compartida

- **Django es el dueño de las tablas:** las crea y modifica con sus migraciones.
- **Spring Boot no crea ni cambia tablas** (`spring.jpa.hibernate.ddl-auto=validate`).
- La conexión se configura con variables de entorno; nunca se versionan contraseñas.
