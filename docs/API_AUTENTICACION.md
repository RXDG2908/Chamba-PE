# Autenticación con roles — contrato de T5.1

Acuerdo del Daily del 19/10/2026 para HU-5. Lo implementa Renzo León en la app
`usuarios` (T5.1). Luis y David pueden trabajar contra este contrato desde hoy,
aunque el PR todavía no esté unido. Este documento solo fija **lo que la API
promete**; si algo cambia al implementar, se actualiza aquí en el mismo PR.

## Resumen

| Tema | Acuerdo |
|---|---|
| App de Django | `usuarios` (dueño: Renzo) |
| Rutas | Bajo `/api/usuarios/` |
| Sesión | Sesión de Django (cookie `sessionid`), sin tokens ni JWT |
| CSRF | Cookie `csrftoken` y cabecera `X-CSRFToken` en todo `POST`, `PUT`, `PATCH` y `DELETE` |
| Desde React | Mismo origen gracias al proxy `/api` de Vite, como hoy; no hace falta `credentials: 'include'` |
| Rol | Campo `rol` en el modelo `usuarios.Perfil`, uno por cuenta |
| Roles | `cliente`, `prestador`, `administrador` |
| Permisos | `EsCliente`, `EsPrestador` y `EsAdministrador` en `usuarios/permisos.py` |
| Sin sesión | `403 Forbidden` en las rutas privadas |

## Dónde se guarda el rol

Se usa el `User` de Django sin cambiarlo y se agrega un perfil:

```text
usuarios.Perfil
├── usuario   OneToOne con User (related_name='perfil')
└── rol       'cliente' | 'prestador' | 'administrador'
```

- **Cliente:** se crea con `POST /api/usuarios/registro/`.
- **Prestador:** se sigue creando con el registro que ya existe,
  `POST /api/prestadores/registro/` (T1.2). T5.1 solo le agrega el perfil con
  rol `prestador`. El formulario de registro del prestador no cambia.
- **Administrador:** no se registra por la API. Se crea con
  `python manage.py createsuperuser`, y ese comando le asigna el rol
  `administrador`. Un usuario con `is_staff` y sin perfil también cuenta como
  administrador, para no romper las cuentas que ya existen.
- Una migración de datos asigna perfil a las cuentas que ya están en la base:
  `prestador` si tienen `Prestador`, `administrador` si son `is_staff` y
  `cliente` en los demás casos.

## Rutas

Todas responden JSON y los mensajes de error van en español, por campo, con el
mismo formato que el resto de la API:

```json
{"email": ["Ya existe una cuenta con ese correo."]}
```

### `GET /api/usuarios/csrf/`

Deja la cookie `csrftoken` en el navegador. React la llama una vez al cargar la
aplicación, antes de cualquier `POST`.

Respuesta `200 OK`: `{}`

### `POST /api/usuarios/registro/`

Registra un **cliente** e inicia su sesión de inmediato.

| Campo | Regla |
|---|---|
| `username` | Obligatorio, máximo 150 caracteres, único sin importar mayúsculas |
| `email` | Obligatorio, correo válido, único sin importar mayúsculas |
| `password` | Obligatorio, pasa los validadores de contraseña de Django |
| `nombres` | Obligatorio, máximo 100 caracteres |
| `apellidos` | Obligatorio, máximo 100 caracteres |

```json
{
  "username": "ana.perez",
  "email": "ana@correo.pe",
  "password": "UnaClaveSegura2026",
  "nombres": "Ana",
  "apellidos": "Pérez"
}
```

Respuesta `201 Created`: el mismo cuerpo de `GET /api/usuarios/yo/`.
Errores: `400` por campo.

> El correo también pasa a ser único en `POST /api/prestadores/registro/`, para
> que una persona no tenga dos cuentas con el mismo correo.

### `POST /api/usuarios/login/`

| Campo | Regla |
|---|---|
| `username` | Nombre de usuario **o** correo |
| `password` | Contraseña |

Respuesta `200 OK`: el mismo cuerpo de `GET /api/usuarios/yo/`.

Credenciales incorrectas o cuenta desactivada: `400` con un único mensaje, sin
decir cuál de los dos datos falló:

```json
{"detail": "Usuario o contraseña incorrectos."}
```

### `POST /api/usuarios/logout/`

Cierra la sesión. Respuesta `204 No Content`. Sin sesión también responde `204`.

### `GET /api/usuarios/yo/`

Devuelve quién tiene la sesión iniciada. Es la ruta que usa React para saber
qué menú mostrar y si debe mandar al inicio de sesión.

```json
{
  "id": 12,
  "username": "ana.perez",
  "email": "ana@correo.pe",
  "nombres": "Ana",
  "apellidos": "Pérez",
  "rol": "cliente",
  "prestador_id": null
}
```

- `prestador_id` es el ID del `Prestador` cuando el rol es `prestador`, y
  `null` en los demás roles.
- En un prestador, `nombres` y `apellidos` salen de su `Prestador`; en los
  demás, del `User`.
- Sin sesión: `403`.

### `GET` y `PATCH /api/usuarios/disponibilidad/` (T6.1, HU-6)

Solo para el prestador con sesión (`EsPrestador`). `GET` devuelve su estado
actual y `PATCH` lo cambia entre Activo (`true`) e Inactivo (`false`).

```json
{"disponible": true}
```

Respuesta `200 OK`:

```json
{"disponible": true, "estado": "verificado"}
```

- `estado` es el de la verificación (HU-9), solo de lectura.
- Un prestador inactivo no aparece en `GET /api/prestadores/buscar/` y no
  recibe solicitudes; ambas cosas ya filtran por `disponible`.
- Sin `disponible` o con un valor que no es booleano: `400`. Sin sesión, con
  otro rol o sin `X-CSRFToken`: `403`.

## Permisos para las demás apps

`usuarios/permisos.py` expone tres clases de DRF. Cada una exige sesión iniciada
y el rol indicado; si falta la sesión o el rol no corresponde, la respuesta es
`403`.

```python
from usuarios.permisos import EsAdministrador

class RevisionDocumentosView(APIView):
    permission_classes = [EsAdministrador]
```

| Clase | Deja pasar a |
|---|---|
| `EsCliente` | rol `cliente` |
| `EsPrestador` | rol `prestador` (y la cuenta tiene `Prestador`) |
| `EsAdministrador` | rol `administrador` o `is_staff` |

En `core/settings.py` la autenticación de DRF queda solo con sesión:

```python
REST_FRAMEWORK = {
    'DEFAULT_AUTHENTICATION_CLASSES': [
        'rest_framework.authentication.SessionAuthentication',
    ],
}
```

Así, sin sesión la respuesta es `403` (y no `401` con la ventana de usuario y
contraseña del navegador). Las rutas públicas de hoy siguen igual porque
declaran `AllowAny`.

## Cómo llamar a la API desde React

```js
function leerCookie(nombre) {
  const fila = document.cookie.split('; ').find((c) => c.startsWith(`${nombre}=`))
  return fila ? decodeURIComponent(fila.split('=')[1]) : ''
}

// Al cargar la app
await fetch('/api/usuarios/csrf/')

// En cada POST, PUT, PATCH o DELETE
await fetch('/api/usuarios/login/', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-CSRFToken': leerCookie('csrftoken'),
  },
  body: JSON.stringify({ username, password }),
})
```

Después de iniciar o cerrar sesión, Django cambia el `csrftoken`; por eso se lee
la cookie en cada petición y no se guarda en una variable.

## Qué cambia en lo que ya existe

| Ruta | Cambio | Quién |
|---|---|---|
| `POST /api/prestadores/registro/` | Crea el perfil con rol `prestador`; correo único | Renzo, en T5.1 |
| `GET /api/servicios/solicitudes/recibidas/`, aceptar y rechazar | Pasan de `IsAuthenticated` a `EsPrestador` | Renzo, en T5.1 (las escribió en T4.1) |
| Revisión de documentos (T9.1) | Usa `EsAdministrador` en vez de `IsAdminUser` (hecho en el PR 11, 21/10) | David |
| `POST /api/certificaciones/upload/` | Pedir `EsPrestador` y tomar el prestador de la sesión (impedimento 16) | Se acuerda con David |
| `POST /api/servicios/solicitudes/` | Enlazar la solicitud a la cuenta del cliente | Se acuerda con Luis |

Las búsquedas y la lista de rubros siguen públicas.

## Pruebas que acompañan el PR de T5.1

Las pruebas cruzadas de acceso por rol son de David (T5.3). El PR de T5.1 trae
sus propias pruebas unitarias de: registro válido e inválido, correo repetido,
inicio de sesión con usuario y con correo, credenciales incorrectas, cierre de
sesión, `yo/` con y sin sesión, y que cada clase de permiso deja pasar solo a
su rol.
