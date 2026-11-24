# Exposición de Renzo León · Sprints 1 y 2

Guía para presentar mi parte de Chamba PE y hacer las pruebas en vivo. Tiempo estimado: **15 minutos** (5 de exposición, 7 de demo y 3 de pruebas automáticas), más preguntas.

---

## 1. Quién soy y qué me tocó

- **Rol:** Scrum Master y desarrollador.
- **Apps de Django a mi cargo:** `prestadores` (perfil, rubros y cobertura) y `usuarios` (cuentas, roles y disponibilidad).
- **Como Scrum Master:** planificación de los sprints, burndown diario, revisión de PRs y cierre de cada sprint.

| Sprint | Historia | Tarea | Qué hice | Horas | PR |
|:---:|:---:|:---:|---|:---:|:---:|
| 1 | HU-1 | T1.1 | Modelo de datos del prestador | 2 | [#1](https://github.com/RXDG2908/Chamba-PE/pull/1) |
| 1 | HU-1 | T1.2 | API de registro de prestadores | 3 | [#2](https://github.com/RXDG2908/Chamba-PE/pull/2) |
| 1 | HU-1 | T1.3 | Formulario de registro con mapa de cobertura | 2 | [#2](https://github.com/RXDG2908/Chamba-PE/pull/2) |
| 1 | HU-4 | T4.1 | Flujo de solicitud: envío, aceptar y rechazar | 3 | [#4](https://github.com/RXDG2908/Chamba-PE/pull/4) |
| 2 | HU-5 | T5.1 | API de autenticación con roles | 3 | [#13](https://github.com/RXDG2908/Chamba-PE/pull/13) |
| 2 | HU-6 | T6.1 | Endpoint de disponibilidad | 2 | [#15](https://github.com/RXDG2908/Chamba-PE/pull/15) |
| 2 | HU-5 | T5.2 | Pantallas de registro e inicio de sesión | 3 | [#17](https://github.com/RXDG2908/Chamba-PE/pull/17) |
| 2 | HU-6 | T6.2 | Interruptor de disponibilidad | 2 | [#18](https://github.com/RXDG2908/Chamba-PE/pull/18) |
| | | | **Total** | **20 h** | |

**Resultado:** mis 20 h están terminadas y en `main`. En el Sprint 1 el equipo cerró 26 de 30 h, y en el Sprint 2, 15 de 23 h, con mis 10 h completas.

---

## 2. Sprint 1 · El prestador se registra y recibe solicitudes

**Meta del sprint:** el prestador se registra y el cliente lo encuentra y le envía una solicitud.

### HU-1 · Registro del prestador (T1.1, T1.2 y T1.3)

- **Modelo `Prestador`:** nombres, apellidos, DNI de 8 dígitos y único, teléfono, uno o más rubros y la **zona de cobertura** (un círculo: centro con latitud y longitud, más un radio de 1 a 100 km).
- **Estado inicial:** todo prestador nace **"Pendiente de verificación"**. No puede marcarse como verificado desde el registro; eso lo hace el administrador (HU-9, Sprint 2).
- **API `POST /api/prestadores/registro/`:** crea en un solo paso la cuenta de Django y el perfil. Valida en español el DNI, los rubros, la cobertura y la contraseña.
- **Formulario en React:** datos personales, cuenta, rubros que llegan de la API y un **mapa Leaflet** donde el prestador toca su zona y ajusta el radio.
- **10 rubros iniciales** cargados con una migración (Gasfitería, Electricidad, Carpintería y otros).

### HU-4 · Solicitud y aceptación (T4.1)

- **Modelo `Solicitud`:** Pendiente → Aceptada o Rechazada.
- **El cliente envía** la solicitud con su contacto, la descripción y la ubicación (`POST /api/servicios/solicitudes/`).
- **El prestador ve su bandeja** y responde **una sola vez**: si intenta responder otra vez, la API devuelve **409 Conflict**.
- Solo el prestador dueño de la solicitud puede verla y responderla.

---

## 3. Sprint 2 · Cuentas, roles y disponibilidad

**Meta del sprint:** cuentas, disponibilidad, mapa y notificaciones; prestadores verificados por administración.

### HU-5 · Registro e inicio de sesión (T5.1 y T5.2)

- **Lo primero fue un contrato:** el 19/10 dejé escrito en `docs/API_AUTENTICACION.md` qué rutas iba a tener la API. Así Luis y David trabajaron contra el contrato sin esperar a que yo terminara.
- **Tres roles:** cliente, prestador y administrador, guardados en un `Perfil` junto a cada cuenta.
- **Rutas en `/api/usuarios/`:** `registro`, `login` (con usuario **o** correo), `logout` y `yo`, que dice quién tiene la sesión.
- **Seguridad:** sesión de Django con protección **CSRF**. Sin sesión, las rutas privadas responden **403**.
- **Permisos reutilizables:** `EsCliente`, `EsPrestador` y `EsAdministrador`. David los usa en su panel de revisión.
- **Pantallas en React:** crear cuenta de cliente, iniciar sesión y la cabecera con el nombre, el rol y "Cerrar sesión".

### HU-6 · Disponibilidad del prestador (T6.1 y T6.2)

- **API `GET` y `PATCH /api/usuarios/disponibilidad/`:** solo para el prestador con sesión.
- **Pantalla "Mi disponibilidad":** interruptor Activo/Inactivo. Si el perfil todavía no está verificado, la pantalla lo avisa.
- **Regla del negocio:** un prestador **inactivo no aparece en la búsqueda ni recibe solicitudes**.

### Dos problemas que encontré probando en el navegador

1. **Error 403 al registrarse desde React.** El proxy de Vite cambia el *Host* y Django rechazaba el *Origin* `localhost:5173`. Lo resolví con `CSRF_TRUSTED_ORIGINS`.
2. **El prestador entraba a la pantalla equivocada.** Después de iniciar sesión, la ruta de login redirigía sola a Resultados antes de ir a "Mi disponibilidad". Lo corregí haciendo que cada pantalla decida a dónde ir cuando ya hay sesión.

> 💡 **Mensaje para la exposición:** las pruebas automáticas pasaban, pero estos dos errores solo aparecieron al probar en el navegador. Por eso hago las dos cosas.

---

## 4. Antes de exponer (checklist)

Hacerlo **10 minutos antes**, desde la carpeta `Chamba-PE`, en PowerShell.

1. **Traer la última versión e instalar dependencias:**

   ```powershell
   git checkout main
   git pull
   venv\Scripts\python.exe -m pip install -r requirements.txt
   npm --prefix frontend install
   ```

2. **Base de datos limpia para la demo** (respalda la actual):

   ```powershell
   Copy-Item db.sqlite3 db.respaldo.sqlite3
   Remove-Item db.sqlite3
   venv\Scripts\python.exe manage.py migrate
   ```

3. **Crear el administrador de la demo** (pide una contraseña; anótala):

   ```powershell
   venv\Scripts\python.exe manage.py createsuperuser --username admin.demo --email admin.demo@ejemplo.test
   ```

4. **Levantar los dos servidores**, cada uno en su terminal:

   ```powershell
   venv\Scripts\python.exe manage.py runserver
   ```

   ```powershell
   npm --prefix frontend run dev
   ```

5. Abrir **http://localhost:5173**, con la ventana del navegador maximizada y el zoom al 110 %.
6. Tener a la mano un **PDF cualquiera** para el certificado y una **ventana de incógnito** para el segundo usuario.

**Datos de prueba sugeridos** (son ficticios):

| Cuenta | Usuario | Correo | DNI | Rubro |
|---|---|---|---|---|
| Prestador | `pedro.demo` | `pedro.demo@ejemplo.test` | `87654321` | Gasfitería |
| Cliente | `ana.demo` | `ana.demo@ejemplo.test` | — | — |

Usa la misma contraseña de prueba para los dos, de 8 caracteres o más (por ejemplo, una que termine en `-2026`).

---

## 5. Demo en vivo (unos 7 minutos)

Ventana normal = **prestador** · Ventana de incógnito = **cliente** · El admin entra en la ventana normal cuando haga falta.

### Parte A · Sprint 1: registro del prestador (HU-1)

| # | Acción | Qué debe verse |
|:---:|---|---|
| A1 | En **Registro de prestador**, presionar **Registrarme** con todo vacío | Errores en español en cada campo |
| A2 | Escribir el DNI `123` | "El DNI debe tener 8 dígitos." |
| A3 | Llenar los datos de Pedro, elegir **Gasfitería**, tocar el mapa en Lima y mover el radio a 5 km | El círculo de cobertura se dibuja y cambia de tamaño |
| A4 | Presionar **Registrarme** | "¡Registro enviado!" con el perfil **Pendiente de verificación** |

> 🗣️ "El prestador nunca puede verificarse solo: el estado lo cambia el administrador."

### Parte B · Sprint 2: inicio de sesión y disponibilidad (HU-5 y HU-6)

| # | Acción | Qué debe verse |
|:---:|---|---|
| B1 | **Iniciar sesión** con el correo de Pedro y una contraseña **incorrecta** | "Usuario o contraseña incorrectos." |
| B2 | Iniciar sesión con la contraseña correcta | Entra directo a **Mi disponibilidad**; la cabecera dice "Hola, Pedro · Prestador" |
| B3 | Señalar el aviso amarillo | "Tu perfil está pendiente de verificación…" |
| B4 | Activar el interruptor | Cambia a **Activo**: "Quedas disponible, pero aún no recibes solicitudes." |
| B5 | Ir a **Cargar certificado** y subir el PDF con el ID que mostró el registro | "¡Certificado enviado!" |

### Parte C · El administrador verifica (HU-9, de David; enlaza mi trabajo)

| # | Acción | Qué debe verse |
|:---:|---|---|
| C1 | **Cerrar sesión** e iniciar sesión con `admin.demo` | La cabecera dice "Administrador" |
| C2 | **Revisión de documentos** → **Aprobar** el certificado de Pedro | Pedro pasa a **Verificado** |

### Parte D · El cliente busca y solicita (HU-5, HU-6 y HU-4)

| # | Acción | Qué debe verse |
|:---:|---|---|
| D1 | En **incógnito**: **Crear cuenta** de Ana, primero con todo vacío y luego completa | Validaciones; al crearla queda con la sesión iniciada como **Cliente** |
| D2 | Escribir `/disponibilidad` en la barra de direcciones | Lo devuelve al inicio: esa pantalla es solo para prestadores |
| D3 | **Resultados** → Gasfitería → tocar el mapa en Lima → **Buscar** | Aparece **Pedro** con su distancia |
| D4 | En la ventana normal: Pedro inicia sesión y **desactiva** el interruptor | Inactivo |
| D5 | En incógnito, **Buscar** de nuevo | Pedro **ya no aparece** |
| D6 | Pedro vuelve a **activar** el interruptor. Ana busca otra vez y, en la tarjeta ("Solicitar este servicio"), llena su nombre, un teléfono de 9 dígitos y la descripción, y presiona **Enviar solicitud** | La solicitud queda **Pendiente** |

### Parte E · El prestador responde (HU-4, T4.1)

| # | Acción | Qué debe verse |
|:---:|---|---|
| E1 | En la ventana de Pedro, abrir `http://localhost:5173/api/servicios/solicitudes/recibidas/` | La API navegable de Django REST lista la solicitud de Ana con su **id** |
| E2 | Abrir `http://localhost:5173/api/servicios/solicitudes/<id>/aceptar/` y presionar **POST** | Responde `"estado": "aceptada"` |
| E3 | Presionar **POST** otra vez | **409**: "La solicitud ya fue aceptada." |
| E4 | Volver a la ventana de Ana | Su tarjeta cambia sola a **Aceptada** (se actualiza cada 5 s) |

> 🗣️ "La bandeja del prestador todavía no tiene pantalla propia. Por eso la muestro desde la API navegable, que usa la misma sesión del login."

---

## 6. Pruebas automáticas en vivo (unos 3 minutos)

Con los servidores encendidos, en una tercera terminal:

| Comando | Qué prueba | Resultado esperado |
|---|---|---|
| `venv\Scripts\python.exe manage.py test prestadores` | HU-1: registro válido, DNI inválido o duplicado, sin rubros, sin cobertura, rubros y que no se pueda aprobar desde el registro | **10 pruebas OK** |
| `venv\Scripts\python.exe manage.py test servicios.tests.SolicitudTests` | HU-4: envío, bandeja, aceptar, rechazar, responder dos veces (409) y permisos | **13 pruebas OK** |
| `venv\Scripts\python.exe manage.py test usuarios.tests` | HU-5 y HU-6: registro, login con usuario y correo, CSRF, `yo/`, roles y disponibilidad | **29 pruebas OK** (unos 40 s) |
| `venv\Scripts\python.exe manage.py test` | Todo el proyecto | **145 pruebas OK** (3 "fallos esperados") |

> 🗣️ "Los 3 fallos esperados son de David: marcan que la carga de certificados todavía no pide sesión (impedimento 16). Cuando se corrija, esas pruebas avisarán."

Si sobra tiempo, abrir `usuarios/tests.py` y mostrar una prueba corta, por ejemplo la que comprueba que un prestador inactivo no aparece en la búsqueda (`test_inactivo_no_aparece_en_la_busqueda`).

---

## 7. Plan B si algo falla

| Problema | Qué hacer |
|---|---|
| "No pudimos comunicarnos con el servidor" | Django no está encendido: revisar la terminal de `runserver` |
| Error 403 al registrarse o iniciar sesión | Recargar la página con F5: así se pide de nuevo la cookie CSRF |
| Pedro no aparece en la búsqueda | Revisar tres cosas: que esté **Verificado** (parte C), **Activo** (B4) y que el punto del mapa esté dentro de sus 5 km |
| No hay certificado para aprobar | Atajo de la demo (en el producto lo hace el admin): `venv\Scripts\python.exe manage.py shell -c "from prestadores.models import Prestador; Prestador.objects.filter(dni='87654321').update(estado='verificado')"` |
| El DNI o el correo ya existen | Usar otro DNI u otro correo, o repetir el paso 2 del checklist |
| Falla todo el navegador | Mostrar solo las pruebas automáticas (sección 6) y el contrato `docs/API_AUTENTICACION.md` |

---

## 8. Preguntas que pueden hacer

| Pregunta | Respuesta corta |
|---|---|
| ¿Por qué sesión de Django y no JWT? | React y Django están en el mismo origen gracias al proxy de Vite, así que la cookie de sesión es lo más simple y seguro. Con CSRF, el navegador no puede mandar peticiones falsas en nombre del usuario. |
| ¿Dónde se guarda el rol? | En `usuarios.Perfil`, uno por cuenta. Se asigna solo: registrarse como cliente da rol cliente, crear un `Prestador` da rol prestador y `createsuperuser` da administrador. |
| ¿Qué pasa con las cuentas que existían antes? | Una migración les dio perfil según lo que eran: prestador, administrador (`is_staff`) o cliente. |
| ¿Por qué el login acepta usuario o correo? | Es más cómodo para el usuario. Además, el correo ahora es único, también en el registro del prestador. |
| ¿Por qué el error de login no dice qué dato falló? | Para no revelar si un correo está registrado. |
| ¿Por qué el prestador no aparece aunque esté activo? | También tiene que estar **Verificado** por el administrador (HU-9). Las dos condiciones se exigen en la búsqueda y en las solicitudes. |
| ¿Cómo se trabajó en equipo? | Una rama y un PR por tarea, revisados por otro integrante, y pruebas cruzadas (David probó mi HU-5 y HU-6). AVANCES, SPRINTS y TABLERO se actualizan en cada entrega. |
| ¿Qué salió mal en el Sprint 2? | Las revisiones se acumularon y todo se unió el último día. Para el Sprint 3 acordamos revisar cada PR en máximo 1 día hábil. |

---

## 9. Después de la exposición

Para volver a tus datos de antes:

```powershell
Remove-Item db.sqlite3
Rename-Item db.respaldo.sqlite3 db.sqlite3
```
