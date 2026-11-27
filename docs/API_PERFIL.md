# Perfil del cliente y del prestador

Cada cuenta (cliente o prestador) edita su perfil en la web, en **Mi perfil** (`/perfil`, en el menú y al tocar su nombre en la cabecera). Las rutas son de Django y aceptan la sesión de la web o el token de Firebase de la app (`Authorization: Bearer …`).

| Ruta | Método | Qué hace |
|---|---|---|
| `/api/usuarios/perfil/` | `GET` | Datos del perfil |
| `/api/usuarios/perfil/` | `PATCH` | Cambia solo los campos enviados |
| `/api/usuarios/perfil/foto/` | `POST` (multipart, campo `foto`) | Sube o reemplaza la foto |
| `/api/usuarios/perfil/foto/` | `DELETE` | Quita la foto |
| `/api/usuarios/perfil/contrasena/` | `POST` `{actual, nueva}` | Cambia la contraseña sin cerrar la sesión |

Sin sesión: `403`. Los errores de validación llegan por campo (`{"campo": ["mensaje"]}`), como el resto de la API.

## Campos

**Todas las cuentas:** `nombres`, `apellidos`, `email` (único), `telefono` (9 dígitos; acepta `+51`, espacios y guiones y se guarda sin ellos). Solo lectura: `username`, `rol`, `foto`, `tiene_contrasena`, `cuenta_app`.

**Solo el prestador:**

| Campo | Regla |
|---|---|
| `tipo` | `independiente` (se presenta con su nombre) o `empresa` |
| `nombre_comercial` | Obligatorio si `tipo` es `empresa`; hasta 120 caracteres |
| `ruc` | Opcional; 11 dígitos que empiezan con 10, 15, 17 o 20 |
| `descripcion` | «Sobre ti o tu empresa», hasta 500 caracteres |
| `rubros` | Lista de IDs; al menos uno |
| `radio_cobertura_km`, `latitud`, `longitud` | Zona de cobertura (radio de 1 a 100 km; las coordenadas se redondean a 6 decimales) |

Solo lectura del prestador: `nombre_publico` (el nombre comercial si es empresa; si no, nombres y apellidos), `estado`, `nombres_editables` y `dni` (solo los 3 últimos dígitos: `•••••678`).

**Nombre verificado:** los nombres y apellidos del prestador se comparan con su DNI (HU-19). Mientras no está verificado puede corregirlos; ya verificado, cambiarlos devuelve `400` con el mensaje de escribir a administración. Enviar el mismo nombre no cuenta como cambio. Ser empresa no cambia esto: la persona sigue verificada y el nombre comercial es solo cómo la ven los clientes.

## Foto

JPG, PNG o WEBP de hasta 5 MB. El servidor la abre con Pillow (un archivo que no es imagen da `400`), la gira según su EXIF, la recorta cuadrada al centro, la reduce a 512 × 512 px y la guarda como JPEG **sin metadatos** (se descarta el GPS que traen las fotos del celular). Se guarda en `MEDIA_ROOT/fotos/` con un nombre nuevo en cada cambio, y la anterior se borra.

`foto` es la ruta `/media/fotos/…` (o `null`), igual en `/perfil/`, `/yo/` y la búsqueda.

## Dónde se ve

- **Búsqueda y recomendación (HU-3, HU-7, HU-10):** cada prestador trae `tipo`, `nombre_publico`, `descripcion` y `foto`. Las tarjetas muestran la foto (o las iniciales), el nombre público, la insignia «Empresa» y la descripción. Django y Spring devuelven lo mismo y en el mismo orden.
- **Insignia «Nuevo»:** la búsqueda y la recomendación traen `nuevo` (`true` durante los primeros 30 días desde que el prestador se registró, `DIAS_NUEVO` en `prestadores/models.py` y en `BusquedaService` de Spring). La web y la app muestran «Nuevo» en la tarjeta, en «Recomendado para ti», en el detalle y en el mapa, para que los clientes sepan que aún no tiene historial. En la demo, «Nuevo» debe salir **solo** en los prestadores que se registran en vivo. Justo antes de empezar, en la base que se usará:

  ```powershell
  $env:DJANGO_DEBUG='true'; python manage.py sembrar_demo_santa_anita   # si faltan los de relleno
  $env:DJANGO_DEBUG='true'; python manage.py preparar_demo_nuevos       # nadie queda como «Nuevo»
  ```

  `preparar_demo_nuevos` pasa a hace un año la fecha de registro de los prestadores que hoy serían «Nuevo» (cuentas de prueba creadas este mes incluidas); los que se registren después sí lo son. Solo corre con `DJANGO_DEBUG`.
- **App Android:** muestra el nombre público y las insignias «Nuevo» y «Empresa» (caché Room en versión 3).
- **Cabecera de la web:** foto y nombre (vía `/api/usuarios/yo/`, que suma `foto` y `nombre_publico` en Django y Spring).

## Base de datos

Migraciones `usuarios.0004_perfil_editable` (`foto`, `telefono`) y `prestadores.0004_perfil_editable` (`tipo`, `nombre_comercial`, `ruc`, `descripcion`). Las columnas de texto tienen valor por defecto **en la base** (`db_default`) y `foto` admite nulo, porque Spring inserta prestadores y perfiles (registro desde la app) sin conocerlas. La copia del esquema de Spring (`backend/spring/src/test/resources/esquema-django.sql`) ya las incluye.

## Pendiente

- **App Android:** el perfil de la app sigue siendo de solo lectura. Editarlo desde la app necesita estas mismas rutas en Spring (la app usa Spring por defecto), incluida la subida de la foto a la carpeta de Django.
