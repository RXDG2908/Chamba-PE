# Guía de navegación — comprobar las tareas hechas hasta hoy

Recorrido paso a paso para comprobar, en el navegador y en la terminal, cada tarea terminada del Sprint 1 y la T7.3 del Sprint 2. Cada paso indica la tarea que comprueba y el resultado esperado; marca la casilla cuando lo veas.

Estado al 22/10/2026: rama `feature/t7.3-pruebas-radio`, `python manage.py test` con **70 de 70 OK**.

| Paso | Qué se comprueba | Tareas |
|:---:|---|---|
| [0](#0-preparación) | Levantar Django y React | — |
| [1](#1-pruebas-automáticas) | Suite completa en verde | T2.3, T4.3, T7.3 y el resto |
| [2](#2-registro-del-prestador) | Registro con validaciones y estado pendiente | T1.1, T1.2, T1.3 |
| [3](#3-carga-de-certificados) | Carga de PDF/JPG ≤ 5 MB asociada al prestador | T2.1, T2.2 |
| [4](#4-dejar-al-prestador-listo-para-recibir-solicitudes) | Disponibilidad y acceso del prestador (admin) | preparación |
| [5](#5-búsqueda-y-orden-por-distancia) | Búsqueda por rubro, cobertura y orden | T3.1, T3.2, T3.3 |
| [6](#6-tiempo-de-respuesta--3-s) | Búsqueda en menos de 3 s con 500 prestadores | T3.4 |
| [7](#7-solicitud-aceptar-y-rechazar) | Enviar, aceptar y rechazar una solicitud | T4.1, T4.2 |
| [8](#8-radio-de-cobertura-a-mano) | 3 km entra, 5 km (borde) entra, 5,5 km no | T7.3 |

---

## 0. Preparación

Dos terminales, ambas desde la raíz del proyecto.

**Terminal 1: Django (puerto 8000)**

```powershell
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python manage.py migrate
python manage.py createsuperuser   # solo si aún no tienes uno (ya existe "admin")
python manage.py runserver
```

**Terminal 2: React + Vite (puerto 5173)**

```powershell
cd frontend
npm install
npm run dev
```

> La app de React está en `frontend/`. El `src/` de la raíz es una copia antigua y no se usa.

- [ ] http://127.0.0.1:8000/admin/ abre el admin de Django.
- [ ] http://localhost:5173/ muestra el menú **Registro de prestador · Resultados · Cargar certificado**.

---

## 1. Pruebas automáticas

```powershell
python manage.py test
```

- [ ] `Ran 70 tests` y `OK`.

Solo las de la T7.3, con el nombre de cada prueba:

```powershell
python manage.py test servicios.test_radio_cobertura -v 2
```

- [ ] 12 pruebas `ok`: 6 de `PrecisionHaversineTests` (Lima, Tumbes y Tacna) y 6 de `RadioCoberturaBusquedaTests` (3, 5 y 5,5 km en 8 rumbos).

---

## 2. Registro del prestador

**T1.1 · T1.2 · T1.3** · http://localhost:5173/ → **Registro de prestador**

1. Intenta enviar el formulario vacío.
   - [ ] Aparecen errores; no se crea nada.
2. Llena todo **menos el DNI** (o con 7 dígitos), luego **sin rubros**.
   - [ ] Cada caso muestra un error y no se registra.
3. Registra un prestador válido. Anota el usuario y la contraseña, porque los usarás en el paso 7:
   - Usuario `prestador_guia`, un correo y una contraseña segura.
   - DNI de 8 dígitos que no exista (ej. `45678912`).
   - Rubro **Gasfitería**.
   - En el mapa, haz clic en el centro de Lima y deja el radio en **5 km**.
   - [ ] Pantalla «¡Registro enviado!» con el perfil **Pendiente de verificación** y el **ID** del prestador. Anota el ID.
4. Comprueba en el admin: http://127.0.0.1:8000/admin/prestadores/prestador/
   - [ ] El prestador aparece con estado *Pendiente de verificación* y **disponible desmarcado**.

---

## 3. Carga de certificados

**T2.1 · T2.2** · desde «¡Registro enviado!» pulsa **Cargar un certificado** (o el menú **Cargar certificado**).

1. Verifica que el ID del prestador ya está escrito.
2. Sube un **PDF** de menos de 5 MB.
   - [ ] Mensaje de éxito con el nombre del archivo y un enlace que lo abre (lo sirve Django por `/media/`).
3. Prueba los rechazos:
   - [ ] Un `.png` o `.docx` → rechazado por formato.
   - [ ] Un archivo de texto renombrado a `.pdf` → rechazado (se revisa el contenido real, no solo la extensión).
   - [ ] Un PDF de más de 5 MB → rechazado por tamaño.
   - [ ] ID de prestador que no existe (ej. `99999`) → error «no existe».
4. Admin → **Certificacion prestadors**.
   - [ ] El certificado aparece vinculado al prestador del paso 2.

---

## 4. Dejar al prestador listo para recibir solicitudes

Todavía no hay inicio de sesión (llega con T5.1) ni interruptor de disponibilidad (T6.2), así que estos dos ajustes se hacen en el admin.

1. Admin → **Prestadors** → tu prestador → marca **Disponible** → *Save*.
2. Admin → **Users** → `prestador_guia` → marca **Staff status** → *Save*. Así puede iniciar sesión en Django para responder solicitudes en el paso 7. No le des *Superuser status*.

Prestador de demostración para el paso 8 (Gasfitería, centro exacto en Lima, radio 5 km, disponible):

```powershell
python manage.py preparar_busqueda_local
```

- [ ] Muestra `Creado` (o `Reutilizado, sin modificaciones`) y el ID del prestador **Demo Gasfitero ficticio**.

---

## 5. Búsqueda y orden por distancia

**T3.1 · T3.2 · T3.3** · http://localhost:5173/ → **Resultados**

1. Rubro **Gasfitería**. En Latitud y Longitud escribe `-12.046374` y `-77.042793`, pulsa **Usar coordenadas** y luego **Buscar**.
   - [ ] Aparecen tarjetas con nombre, rubros y distancia en km. *Demo Gasfitero ficticio* sale a **0 km**.
   - [ ] Las tarjetas van de la más cercana a la más lejana (T3.3).
2. Cambia el rubro a uno que no ofrezca nadie cerca (ej. **Soldadura**) y busca.
   - [ ] Mensaje de «sin resultados»; las tarjetas anteriores desaparecen.
3. Detén Django (Ctrl+C en la terminal 1) y pulsa **Buscar**.
   - [ ] Mensaje de error de comunicación. Vuelve a ejecutar `python manage.py runserver`, pulsa **Buscar** y los resultados vuelven sin recargar la página.
4. Desmarca **Disponible** de un prestador en el admin y repite la búsqueda.
   - [ ] Ya no aparece. Vuelve a marcarlo.
5. La API directa: http://127.0.0.1:8000/api/prestadores/buscar/?rubro=1&latitud=-12.046374&longitud=-77.042793
   - [ ] JSON ordenado por `distancia_km`. Sin `rubro`, la respuesta es 400 con el mensaje en español.

---

## 6. Tiempo de respuesta < 3 s

**T3.4** · en una terminal con el entorno activado:

```powershell
python manage.py medir_busqueda --salida $env:TEMP\medicion.txt
```

> `--salida` escribe el informe fuera del repo para no cambiar `evidencia_rendimiento.txt`.

- [ ] Crea 500 prestadores ficticios, mide la búsqueda, los borra y muestra tiempos en milisegundos (del orden de decenas de ms, muy por debajo de 3000 ms).
- [ ] La lista de prestadores del admin queda igual que antes.

---

## 7. Solicitud, aceptar y rechazar

**T4.1 · T4.2**

**Como cliente** (http://localhost:5173/ → **Resultados**):

1. Busca Gasfitería cerca de tu prestador del paso 2. En su tarjeta abre **Solicitar este servicio**.
2. Prueba los datos inválidos:
   - [ ] Nombre con números (`Luis2`) → error, no se envía.
   - [ ] Teléfono con letras, 8 o 10 dígitos → error, no se envía.
   - [ ] Descripción con solo espacios → error.
3. Envía con datos válidos (ej. `Ángel Muñoz`, `987654321`, «Fuga en el baño»).
   - [ ] La tarjeta muestra **Pendiente de respuesta**. Anota el número de solicitud. **Deja esta pestaña abierta.**

**Como prestador**, en otra pestaña:

4. http://127.0.0.1:8000/admin/ → *Log out* si estás como admin → inicia sesión con `prestador_guia`.
5. Bandeja: http://127.0.0.1:8000/api/servicios/solicitudes/recibidas/
   - [ ] Aparece la solicitud del cliente con estado `pendiente`.
6. Abre http://127.0.0.1:8000/api/servicios/solicitudes/ID/aceptar/ (cambia `ID` por el número de la solicitud). La página dice *405 Method Not Allowed* porque espera un POST: baja hasta el formulario y pulsa **POST** con el contenido vacío.
   - [ ] Respuesta `200 OK` con `"estado": "aceptada"`.
   - [ ] En la pestaña del cliente, en unos 5 s y sin recargar, el estado cambia a **Aceptada**.
7. Pulsa **POST** otra vez.
   - [ ] `409 Conflict`: «La solicitud ya fue aceptada.» Solo se responde una vez.
8. Envía una segunda solicitud desde React y repite el paso 6 con `.../ID/rechazar/`.
   - [ ] El cliente ve **Rechazada**.
9. Inicia sesión como `admin` y abre `.../ID/aceptar/` de una solicitud pendiente.
   - [ ] `403`: solo el prestador dueño puede responder.

---

## 8. Radio de cobertura a mano

**T7.3** · usa el prestador *Demo Gasfitero ficticio* del paso 4 (centro `-12.046374, -77.042793`, radio 5 km). Todos los puntos están **al norte** del centro, con la misma longitud `-77.042793`.

En **Resultados**, rubro **Gasfitería**: escribe la latitud, pulsa **Usar coordenadas** y luego **Buscar**.

| Latitud del cliente | Distancia real al centro | ¿Aparece *Demo Gasfitero ficticio*? |
|:---:|:---:|:---:|
| `-12.019394` | 3,000 km | ✅ Sí, a 3 km |
| `-12.001408` | 4,999998 km (borde) | ✅ Sí, a 5 km |
| `-12.001407` | 5,000109 km (11 cm afuera) | ❌ No |
| `-11.996911` | 5,500 km | ❌ No |

- [ ] Los cuatro casos dan el resultado de la tabla. Entre la segunda y la tercera fila solo hay 0,000001° (unos 11 cm): el borde es inclusivo y exacto.

Opcional, al este (latitud `-12.046370` en las tres):

| Longitud | Distancia | ¿Aparece? |
|:---:|:---:|:---:|
| `-76.996815` | 4,99994 km | ✅ Sí |
| `-76.992217` | 5,49996 km | ❌ No |

> Otros prestadores del rubro pueden aparecer si te cubren con su propio radio; fíjate solo en *Demo Gasfitero ficticio*.

---

## Lo que esta guía todavía no cubre

| Tarea | Motivo |
|---|---|
| T5.1, T5.2, T5.3 (inicio de sesión y roles) | Siguen en ramas sin unir a `main`; por eso el prestador entra por el admin. |
| T6.1, T6.2, T6.3 (disponibilidad) | Sin iniciar; la disponibilidad se cambia en el admin. |
| T7.1, T7.2 (mapa con marcadores) | En el PR 14 de Luis, sin unir. |
| T8.x (notificaciones) | Sin iniciar. |
| T9.1, T9.2 (revisión del administrador) | T9.1 en la rama `feature/t9.1-revision-documentos`, sin unir. |
