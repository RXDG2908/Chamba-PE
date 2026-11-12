# Exposición de HU-19 · Escaneo y validación del DNI

Guía de Renzo León para exponer su parte del Sprint 3 (T19.1, T19.2 y T19.3, 9 h). Dura unos 10 minutos más preguntas.

## 1. La idea en una frase

> El prestador toma dos fotos de su DNI con el celular; el sistema lee los datos de la parte de atrás, los compara con lo que el prestador registró y con una fuente externa, y **nunca aprueba por sí solo**: si algo no cuadra, lo manda a revisión manual del administrador.

**Por qué importa:** Chamba PE pone a desconocidos en la casa de los clientes. Verificar quién es el prestador es la base de la confianza.

## 2. El flujo, paso a paso

```text
 Celular / navegador                    Django (puerto 8000)
 ───────────────────                    ────────────────────────────────────────
 1. Prestador inicia sesión
 2. Entra a /verificar-dni
 3. Foto del frente  ─┐
 4. Foto del reverso ─┴─ POST /api/verificacion/dni/ ─▶  a. ¿Aceptó el consentimiento?   (403 si no)
                                                         b. ¿Imágenes JPG/PNG sanas ≤ 5 MB? (400 si no)
                                                         c. ¿Menos de 5 intentos hoy?      (429 si no)
                                                         d. OCR: leer las 3 líneas MRZ del reverso
                                                         e. Dígitos de control de la MRZ
                                                         f. ¿DNI = el del prestador? ¿Vigente?
                                                         g. Recortar la foto del titular
                                                         h. Consultar nombres en la fuente
                                                         i. ¿Nombres iguales (sin tildes)?
 5. Ve el resultado  ◀─────────── JSON ───────────────   j. Guardar el intento
    ✅ DNI validado
    🟡 En revisión manual (con el motivo)
```

**Regla de oro:** cualquier falla (OCR caído, fuente sin respuesta, nombres distintos, DNI vencido…) termina en **revisión manual**, jamás en un error 500 ni en una aprobación automática. `Prestador.estado` no cambia: aprobar sigue siendo del administrador (HU-9).

## 3. Puertos y piezas

| Pieza | Dirección | Qué es |
|---|---|---|
| Django (backend) | `http://127.0.0.1:8000` | API, base de datos y archivos |
| Vite / React (frontend) | `http://localhost:5173` | La pantalla que ve el prestador |
| Proxy de Vite | `/api` → `8000` | React llama a `/api/...` en su mismo origen, así la **cookie de sesión y el CSRF** funcionan sin CORS |
| Base de datos | `db.sqlite3` | Tabla `verificacion_intentodni` |
| Fotos del DNI | carpeta `privado/` | Fuera de `/media/`, sin URL pública |

**Rutas de la API** (todas bajo `/api/verificacion/`):

| Método y ruta | Quién | Para qué |
|---|---|---|
| `POST /dni/` | Prestador | Envía `anverso` y `reverso` (multipart) |
| `GET /dni/` | Prestador | Su último intento |
| `GET /dni/?prestador=<id>` | Administrador | Todos los intentos de ese prestador |
| `GET /dni/<id>/foto/<anverso\|reverso\|titular>/` | Administrador | Ve una foto (no hay URL pública) |

**Pantalla:** `http://localhost:5173/verificar-dni` (solo rol prestador).

## 4. Antes de exponer (checklist)

```bash
# Terminal 1 · backend
venv/Scripts/python.exe manage.py migrate
venv/Scripts/python.exe manage.py runserver 8000
```

```bash
# Terminal 2 · frontend
npm --prefix frontend run dev -- --port 5173 --strictPort
```

- [ ] Una cuenta de **prestador** con DNI `12345678` (o `87654321`): son los DNIs de la tabla simulada. Contraseña conocida.
- [ ] Una cuenta de **administrador** (para mostrar las fotos).
- [ ] Probar la cámara **en el celular** (hay que abrir la pantalla por HTTPS o `localhost`; con la IP de la PC, Chrome bloquea la cámara). Plan B: el botón «Abrir cámara» sube un archivo.
- [ ] Tener a mano una **imagen ficticia** del reverso con una MRZ válida (nunca un DNI real).
- [ ] Ejecutar la suite y dejarla en una terminal: `venv/Scripts/python.exe manage.py test verificacion -v 2`.
- [ ] ⚠️ **EasyOCR no viene instalado.** Si quieres el OCR real en vivo: `venv/Scripts/python.exe -m pip install easyocr` (pesado, descarga modelos en la primera lectura; hazlo antes y con internet). Si no, el servidor responde «lector no disponible» y el caso queda en revisión manual — también es una buena demo del manejo de errores.

> **Sé honesto con el profesor:** la cámara real y el OCR con una foto de DNI real **no están probados todavía**; lo que sí está probado son las 42 pruebas automáticas, el flujo en el navegador y el manejo de errores.

## 5. Guion (≈10 min)

| Min | Qué haces | Qué dices |
|:---:|---|---|
| 0–1 | Diapositiva / este MD, sección 1 | El problema: confiar en un prestador desconocido |
| 1–3 | Dibujas el flujo (sección 2) | Qué pasa desde la foto hasta el resultado y por qué nunca se aprueba solo |
| 3–5 | Abres `mrz.py` | Cómo se lee el DNI: la MRZ y los dígitos de control |
| 5–6 | Abres `servicio.py` y `validadores.py` | Comparación con el prestador y con la fuente; nombres sin tildes |
| 6–8 | **Demo** en el navegador | Dos fotos → resultado; luego un caso que falla → revisión manual |
| 8–9 | Terminal con las pruebas | 42 pruebas, incluidos los casos de error |
| 9–10 | Sección 8 de este MD | Decisiones y límites honestos |

## 6. Archivos a abrir, en este orden

### 6.1 `verificacion/mrz.py` — el corazón (T19.2)
Qué es la MRZ: las 3 líneas con `<<<` en el reverso del DNI (estándar ICAO 9303, formato TD1). Traen **dígitos de control**, así que se puede comprobar si la lectura es correcta sin ninguna base de datos.
- `digito_control` (línea 36): suma de cada carácter × pesos 7, 3, 1, módulo 10. Es el truco matemático.
- `buscar_lineas` (47): encuentra las 3 líneas de 30 caracteres entre el texto que devuelve el OCR.
- `interpretar` (76): comprueba los 4 controles (documento, nacimiento, vencimiento y compuesto), corrige letras que el OCR confunde con números (O→0, B→8) y vuelve `NXX` a Ñ (así viene la Ñ en el DNI peruano).

> Frase útil: *«Por qué la MRZ y no el frente: el criterio de HU-19 pide dígito verificador, y el de la MRZ está publicado; el código del anverso no tiene especificación oficial.»*

### 6.2 `verificacion/servicio.py` — las reglas (T19.2 y T19.3)
- `analizar` (22): corre los pasos d–i del flujo y junta una lista de **motivos**: `mrz_ilegible`, `mrz_invalida`, `dni_distinto`, `dni_vencido`, `sin_foto`, `lector_no_disponible`, `fuente_no_disponible`, `nombres_distintos`.
- `consultar_fuente` (8): si la fuente falla o responde algo raro, devuelve «no disponible» (nada de 500).
- Muestra los `try/except`: *«cada pieza externa puede fallar y el sistema no se cae.»*

### 6.3 `verificacion/validadores.py` — intercambiable (T19.3)
- `ValidadorSimulado` (23) con `TABLA_SIMULADA` (17): DNIs ficticios, sin internet. Es el predeterminado.
- `ValidadorEldni` (33): reservado; hoy responde «no disponible» porque eldni.com **no tiene API** (es un formulario con token y publicidad, y no es oficial de RENIEC). Decisión pendiente del Product Owner.
- `obtener_validador` (49): se elige con la variable de entorno `VALIDADOR_DNI`.
- `normalizar` (58) y `nombres_coinciden` (66): mayúsculas, sin tildes, Ñ = N; **igualdad exacta**, no «parecido».

### 6.4 `verificacion/lector.py` — OCR cambiable
- `LectorDni` es una interfaz; `LectorEasyOcr` es la implementación real y `obtener_lector()` la entrega. Así se puede cambiar EasyOCR por Tesseract sin tocar nada más (y las pruebas usan un lector falso).

### 6.5 `verificacion/views.py` — la API
- `IntentoDniView.post` (82): el orden de las defensas: consentimiento → imágenes → 5 intentos/día → análisis → guardado.
- `error_de_imagen` (42) / `imagen_valida` (32): solo JPG/PNG de hasta 5 MB y **abre la imagen completa** (un archivo roto con buen encabezado no pasa).
- `FotoIntentoDni`: las fotos solo las ve el administrador.

### 6.6 `verificacion/models.py` — privacidad
- `IntentoDni` (25): lo leído, los motivos, el resultado y la fecha.
- `AlmacenPrivado` (10): guarda en `privado/`, **fuera de `/media/`**. Django publica todo `/media/` en desarrollo; una subcarpeta habría quedado visible sin sesión.
- `eliminar_datos_biometricos` (54): borra fotos y datos leídos y deja solo resultado, motivos y fecha (Ley 29733); lo usará HU-21 de Luis.

### 6.7 `frontend/src/VerificarDni.jsx` — la pantalla (T19.1)
- `Camara` (36): `getUserMedia` con cámara trasera y marco guía; si no hay permiso, `<input capture="environment">`.
- `VerificarDni` (134): pasos frente → reverso → vista previa con «Repetir» → «Enviar para validar».
- `Resultado` (105): «DNI validado» o «En revisión manual» con los motivos en palabras simples.
- `mensajeDeError` (21): un mensaje claro para cada error del servidor.
- Ruta y enlace en `frontend/src/App.jsx` (líneas 64 y 92): solo se ve para el rol prestador.

### 6.8 `verificacion/consentimiento.py` — el enganche con Luis
- `tiene_consentimiento(prestador)` devuelve `True` hasta que Luis (HU-21) la reemplace. Muestra cómo dos personas conectan su trabajo con un contrato.

### 6.9 `docs/API_VERIFICACION_DNI.md` — el contrato
Se escribió **antes** del código para que Luis pudiera preparar sus pruebas sin esperar.

## 7. Demo en vivo (plan A y plan B)

**Plan A (con OCR real instalado):**
1. Inicia sesión como prestador `12345678` → menú **«Verificar mi DNI»**.
2. Toma el frente y el reverso (o sube las imágenes ficticias).
3. Resultado: **DNI validado** con DNI, apellidos y nombres leídos.
4. Repite con otro caso: un reverso borroso → «En revisión manual: no pudimos leer…».
5. Inicia sesión como administrador y pide la foto: `http://localhost:8000/api/verificacion/dni/<id>/foto/titular/` (solo con sesión de admin).

**Plan B (sin OCR / sin cámara):**
1. Muestra la pantalla: cámara bloqueada → botón «Abrir cámara», vista previa y «Repetir».
2. Envía: el servidor responde «lector no disponible» y el caso queda en **revisión manual** (prueba de que no hay error 500).
3. Corre en la terminal:
   ```bash
   venv/Scripts/python.exe manage.py test verificacion -v 2
   ```
   y señala los grupos de pruebas (ver abajo).

## 8. Pruebas que puedes señalar (42 en `verificacion/tests.py`)

| Grupo | Qué prueba |
|---|---|
| `DigitoControlTests` (75) | El cálculo contra el ejemplo público de ICAO |
| `InterpretarMrzTests` (86) | MRZ correcta, Ñ, letras confundidas, y **cada campo alterado** rompe su dígito de control |
| `ValidadorTests` (135) | Tildes, mayúsculas, nombres «parecidos» que NO coinciden, elección del validador |
| `ApiDniTests` (169) | Permisos (403), CSRF, 400 por imágenes rotas, máximo 5 intentos (429), fotos solo para el administrador, fallas del OCR y de la fuente sin 500, borrado parcial |

Suite completa del proyecto: **208 pruebas OK** (3 «fallas esperadas» documentadas del impedimento 16).

## 9. Decisiones técnicas para defender

| Decisión | Por qué |
|---|---|
| Leer la **MRZ del reverso** y no el frente | Tiene dígitos de control estándar y públicos |
| **Nunca aprobar solo**; todo lo dudoso a revisión manual | Un falso «verificado» es peor que una revisión de más |
| Validador **intercambiable** | eldni.com no es oficial; se puede cambiar por un servicio con convenio |
| OCR detrás de una **interfaz** | EasyOCR es pesado; se puede cambiar y las pruebas no lo necesitan |
| Fotos **fuera de `MEDIA_ROOT`** | Datos personales; no deben tener URL pública |
| **5 intentos por día** | Evita fuerza bruta y abuso del OCR |
| Contrato primero, código después | Luis pudo preparar pruebas en paralelo |

## 10. Preguntas probables

**¿Por qué no usan solo eldni.com?** No tiene API ni es fuente oficial de RENIEC, y puede caerse. Por eso es intercambiable y su caída manda el caso a revisión manual. Hoy está sin conectar a la espera de la decisión del PO.

**¿Qué pasa si el OCR lee mal?** Los dígitos de control lo detectan (`mrz_invalida`), o no encuentra las líneas (`mrz_ilegible`). En ambos casos va a revisión manual y el prestador puede reintentar.

**¿Se puede engañar con una foto de una foto?** Esta parte solo valida el documento. La prueba de vida (selfie con parpadeo) y la comparación facial son **HU-20**, que quedó fuera del compromiso inicial del Sprint 3.

**¿Qué hacen con los datos personales?** Fotos privadas, solo el administrador las ve, y HU-21 permite al prestador pedir su eliminación (`eliminar_datos_biometricos`). La Ley 29733 de protección de datos personales es la referencia.

**¿Qué falta?** Probar la cámara en un celular y EasyOCR con una imagen real, que Luis prepare las muestras (T19.4), el consentimiento real de HU-21 y que se unan los PR #27 y #28.

**¿Por qué dos PR?** El #27 es el contrato (documento) y el #28 el código que lo implementa, así se revisan por separado.

## 11. Límites honestos (dilo tú antes de que pregunten)

- La cámara real y el OCR con un DNI real **aún no se probaron**.
- `eldni` **no consulta nada todavía**.
- La zona de recorte de la foto del titular es una estimación fija que hay que calibrar con muestras.
- Falta el consentimiento real (HU-21) y la comparación facial (HU-20).

---

*PR relacionados:* [#27 contrato](https://github.com/RXDG2908/Chamba-PE/pull/27) · [#28 implementación](https://github.com/RXDG2908/Chamba-PE/pull/28)
