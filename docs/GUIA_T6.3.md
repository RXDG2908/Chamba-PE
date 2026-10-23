# T6.3 — Guía de comprobación manual: solo los prestadores activos y verificados aparecen en la búsqueda

Recorrido de punta a punta en el navegador (React + Django) para comprobar que un prestador aparece en **Resultados** solo cuando está a la vez **Verificado** (T9.1/T9.2) y **Activo** (T6.1), y que desaparece en cuanto se pone Inactivo.

Rama: `feature/t6.3-pruebas-disponibilidad`, que integra T9.1 + T9.2 (PR 11, ya con T5.1) y T6.1 (PR 15). Las pruebas automáticas están en [servicios/test_disponibilidad_busqueda.py](../servicios/test_disponibilidad_busqueda.py).

> **Sin pantalla todavía:** el inicio de sesión del prestador (T5.2) y el interruptor de disponibilidad (T6.2) aún no tienen pantalla en React. Mientras tanto, el prestador inicia sesión y cambia su disponibilidad desde la **API navegable de Django**, que se abre en el mismo navegador y pasa por el proxy de Vite. Cuando entre T6.2, el paso 4 se hace con el interruptor y el resto de la guía no cambia.

## Resumen de lo que debes ver

| # | Estado del prestador | ¿Aparece en Resultados? |
|:---:|---|:---:|
| A | Recién registrado (Pendiente, Inactivo) | ❌ |
| B | Pendiente + **Activo** | ❌ |
| C | **Verificado** + Inactivo | ❌ |
| D | **Verificado + Activo** | ✅ |
| E | Verificado, pasa a **Inactivo** | ❌ en la siguiente búsqueda |
| F | Verificado, vuelve a **Activo** | ✅ sin volver a verificar |
| G | Documento **rechazado** + Activo | ❌ |

---

## 0. Preparación

**Terminal 1: Django**

```powershell
git switch feature/t6.3-pruebas-disponibilidad
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python manage.py migrate          # crea las tablas de usuarios y administracion
python manage.py test servicios.test_disponibilidad_busqueda
python manage.py runserver
```

- [ ] `migrate` aplica `usuarios.0001`, `usuarios.0002`, `administracion.0001` y `administracion.0002`.
- [ ] Las pruebas de T6.3 dan `Ran 9 tests` y `OK`.

**Terminal 2: React**

```powershell
cd frontend
npm install
npm run dev
```

**Dos ventanas del navegador.** La sesión de Django es una cookie por navegador, así que cada rol va en su propia ventana para que no se pisen:

| Ventana | Rol | Para qué |
|---|---|---|
| **Normal** | Cliente y administrador | Registro, carga del certificado, panel de revisión y búsqueda |
| **Incógnito** (Ctrl+Shift+N) | Prestador | Iniciar sesión y cambiar la disponibilidad |

Usa siempre `http://localhost:5173` en las dos ventanas.

---

## 1. Registrar el prestador → caso A

Ventana **normal** → http://localhost:5173/ → **Registro de prestador**.

1. Llena el formulario:
   - Usuario `rosa_t63`, correo `rosa_t63@example.com` y una contraseña segura (anótala).
   - Nombres `Rosa`, apellidos `Quispe`, DNI de 8 dígitos que no exista (ej. `63636363`).
   - Rubro **Cerrajería**.
   - En el mapa, clic sobre el centro de Lima. Radio de **5 km**.
2. Pulsa **Registrarme**.
   - [ ] «¡Registro enviado!», perfil **Pendiente de verificación** y el **ID** del prestador.
3. Abre http://127.0.0.1:8000/admin/prestadores/prestador/ y entra a Rosa. Anota su **latitud** y **longitud** para buscarla exactamente ahí.
   - [ ] Estado *Pendiente de verificación* y *Disponible* desmarcado.
4. http://localhost:5173/resultados → rubro **Cerrajería** → escribe la latitud y longitud de Rosa → **Usar coordenadas** → **Buscar**.
   - [ ] ❌ **Rosa no aparece** (caso A).

> Si en tu base hay otros cerrajeros cerca, también saldrán. Fíjate solo en *Rosa Quispe*.

---

## 2. Activar sin estar verificado → caso B

Ventana **incógnito**:

1. Abre http://localhost:5173/api/usuarios/login/. La página dice *405 Method Not Allowed* porque espera un POST; es normal.
2. Abajo, en el formulario, deja *Media type* en `application/json`, escribe esto en *Content* y pulsa **POST**:

   ```json
   {"username": "rosa_t63", "password": "TU_CONTRASEÑA"}
   ```

   - [ ] `200 OK` con `"rol": "prestador"`.
3. Abre http://localhost:5173/api/usuarios/disponibilidad/.
   - [ ] `{"disponible": false, "estado": "pendiente"}`.
4. En el formulario de abajo escribe esto y pulsa **PATCH**:

   ```json
   {"disponible": true}
   ```

   - [ ] `{"disponible": true, "estado": "pendiente"}`.

Ventana **normal** → Resultados → **Buscar** de nuevo.

- [ ] ❌ **Rosa sigue sin aparecer**: está Activa pero no Verificada (caso B).

Deja la ventana incógnito abierta en `/api/usuarios/disponibilidad/`.

---

## 3. Subir el certificado y verificar desde el panel (T9.2) → casos C y D

### 3.1 Subir el documento

Ventana **normal** → **Cargar certificado**. Escribe el ID de Rosa si no aparece ya puesto y sube un **PDF** de menos de 5 MB.

- [ ] Mensaje de éxito.
- [ ] Buscar otra vez en Resultados: ❌ Rosa no aparece (con el documento sin revisar sigue Pendiente).

### 3.2 Iniciar sesión como administrador

1. Ventana **normal** → menú **Revisión de documentos** (http://localhost:5173/administracion/revisiones).
2. Si dice «Esta pantalla es solo para administradores», pulsa **Iniciar sesión como administrador** y entra con tu superusuario (ej. `admin`). Si no tienes uno: `python manage.py createsuperuser`.
   - [ ] Vuelves al panel y ves la tarjeta del certificado de **Rosa Quispe** como pendiente.

### 3.3 Probar el caso C antes de aprobar

Primero pon a Rosa **Inactiva**: ventana **incógnito** → `/api/usuarios/disponibilidad/` → **PATCH** con `{"disponible": false}`.

Ventana **normal** → panel → **Aprobar** en la tarjeta de Rosa.

- [ ] La tarjeta sale de la lista de pendientes.
- [ ] En la ventana incógnito, recarga `/api/usuarios/disponibilidad/`: `{"disponible": false, "estado": "verificado"}`.
- [ ] Resultados → **Buscar**: ❌ **Rosa no aparece**. Está Verificada pero Inactiva (caso C).

### 3.4 Caso D: verificado + activo

Ventana **incógnito** → **PATCH** con `{"disponible": true}`.

- [ ] Resultados → **Buscar**: ✅ **Rosa aparece**, con su distancia (0 km si usaste sus coordenadas exactas).

---

## 4. Desaparece al ponerse inactiva → casos E y F

Pon las dos ventanas una al lado de la otra: incógnito con `/api/usuarios/disponibilidad/` y normal con Resultados mostrando a Rosa.

1. Incógnito → **PATCH** `{"disponible": false}` → pasa a ventana normal → **Buscar**.
   - [ ] ❌ **Rosa desaparece en esa misma búsqueda**, sin esperar ni reiniciar nada (caso E). La tarjeta anterior se reemplaza por el mensaje sin resultados (o por los otros cerrajeros).
2. Incógnito → **PATCH** `{"disponible": true}` → normal → **Buscar**.
   - [ ] ✅ **Rosa vuelve a aparecer** y no hubo que aprobarla otra vez (caso F).
3. Repite la alternancia 2 o 3 veces seguidas.
   - [ ] Cada búsqueda refleja el último PATCH.

> «Inmediatamente» significa en la **siguiente búsqueda**: Resultados no se refresca sola. El filtro se aplica en cada consulta y no hay caché.

Opcional, con la API directa en la ventana normal (cambia `6` si el ID de Cerrajería es otro, y pon las coordenadas de Rosa):
`http://localhost:5173/api/prestadores/buscar/?rubro=6&latitud=LAT&longitud=LON` → recarga con F5 después de cada PATCH.

---

## 5. Documento rechazado → caso G

1. Ventana **normal** → **Cargar certificado** → sube **otro** PDF para Rosa.
2. Panel de revisión → en la tarjeta nueva, **Rechazar** → escribe un motivo (ej. «Documento ilegible») → **Confirmar rechazo**.
   - [ ] Sin motivo, el botón de rechazar queda deshabilitado.

> El rechazo de este segundo documento **no** le quita la verificación a Rosa: queda Verificada mientras tenga al menos un documento aprobado. Para ver el caso G con un prestador nuevo:

3. Registra otro prestador (ej. `luis_t63`, DNI `64646464`, Cerrajería, mismo centro). Súbele un PDF, actívalo desde una **segunda** ventana incógnito (o cierra la primera e inicia sesión con `luis_t63`) y **rechaza** su documento en el panel.
   - [ ] `/api/usuarios/disponibilidad/` de Luis: `{"disponible": true, "estado": "rechazado"}`.
   - [ ] Resultados → **Buscar**: ❌ **Luis no aparece**, aunque está Activo (caso G).
4. Súbele otro PDF a Luis.
   - [ ] Su estado vuelve a `pendiente` y sigue sin aparecer. Aprueba el documento nuevo y ✅ aparece.

---

## 6. Inactivo tampoco recibe solicitudes (criterio de HU-6)

1. Con Rosa **Activa y Verificada**, en Resultados abre **Solicitar este servicio** en su tarjeta, pero **no envíes** todavía.
2. Incógnito → **PATCH** `{"disponible": false}`.
3. Ventana normal → envía la solicitud con datos válidos.
   - [ ] El envío falla con un mensaje de error y no se crea la solicitud. Compruébalo en http://127.0.0.1:8000/admin/servicios/solicitud/.

---

## 7. Limpieza

- Ventana incógnito: ciérrala.
- Si quieres borrar los datos de prueba: admin → **Prestadors** → selecciona a Rosa y Luis → *Delete*. Luego **Users** → `rosa_t63` y `luis_t63` → *Delete*.

## Qué cubren las pruebas automáticas

`python manage.py test servicios.test_disponibilidad_busqueda`. Son 9 pruebas.

| Prueba | Caso |
|---|---|
| `test_solo_verificado_y_activo_aparece` | Las 6 combinaciones de estado (pendiente, verificado, rechazado) × disponible, una por una |
| `test_las_seis_combinaciones_juntas` | Las 6 a la vez: solo sale la verificada y activa |
| `test_desactivar_a_uno_no_afecta_a_los_demas` | Desactivar a uno no saca a otro |
| `test_recien_registrado_no_aparece` | A |
| `test_activo_pero_sin_verificar_no_aparece` | B (también con documento sin revisar) |
| `test_verificado_pero_inactivo_no_aparece` | C |
| `test_aparece_al_cumplir_ambas_y_desaparece_al_ponerse_inactivo` | D, E y F |
| `test_activo_con_documento_rechazado_no_aparece` | G y la vuelta a Pendiente al subir otro documento |
| `test_inactivo_tampoco_recibe_solicitudes` | Sección 6 |

Las pruebas de flujo usan los endpoints reales: registro, carga del certificado, `PATCH` del administrador en `/api/administracion/revisiones/<id>/` y `PATCH /api/usuarios/disponibilidad/` con la sesión del prestador. Prueba de mutación: si se quita de la búsqueda el filtro de disponibilidad o el de verificado, fallan 5 pruebas en cada caso.
