# Verificación facial · HU-20

> Rama `feature/hu20-reconocimiento-facial` (25/11/2026). App de Django `verificacion`, junto a HU-19 y HU-21.

**Historia:** como prestador, quiero tomarme una selfie en vivo que se compare con la foto de mi DNI, a fin de
demostrar que soy el titular.

| Criterio de aceptación | Cómo se cumple |
|---|---|
| Solo se acepta una foto tomada con la cámara y tras detectar un parpadeo | La pantalla `/verificar-rostro` usa solo la cámara frontal (`getUserMedia`, sin selector de archivos) y toma la foto sola cuando MediaPipe detecta ojos abiertos → cerrados → abiertos con un único rostro en cuadro. El servidor exige `parpadeo=true` (ver *Límites*) |
| Se guarda el porcentaje de similitud y la decisión con fecha y hora | `IntentoFacial`: `similitud`, `porcentaje` (0–100), `decision`, `motivos`, `creado`. Se conservan aunque se borre la selfie |
| En la zona gris, el administrador ve ambas fotos lado a lado | Sección «Rostro en revisión manual» de `/administracion/revisiones`: foto del DNI y selfie lado a lado (una bajo la otra en el celular), similitud y botones «Es la misma persona» / «No es la misma persona» |

## Decisiones del PO (25/11)

- **OpenCV en lugar de DeepFace.** Detector YuNet + reconocedor SFace (modelos ONNX oficiales de `opencv_zoo`).
  OpenCV ya venía con EasyOCR; DeepFace habría sumado TensorFlow (~600 MB). El comparador está detrás de la
  interfaz `ComparadorFacial`, así que se puede cambiar sin tocar la API.
- **La decisión automática solo se registra.** «Verificado» no cambia `Prestador.estado`; como en HU-19, el
  administrador sigue aprobando al prestador (HU-9).
- **Consentimiento aparte.** Finalidad `verificacion_facial`, versión `demo-facial-v1` (texto provisional, falta
  aprobarlo). Aceptar el DNI no autoriza lo facial ni al revés, como pedían HU-21 y `API_CONSENTIMIENTO.md`.

## Reglas de decisión (T20.3)

Similitud coseno de SFace entre la selfie y la foto del titular que recortó HU-19 (el último intento de DNI con foto).

| Caso | Decisión | Motivo |
|---|---|---|
| `similitud ≥ UMBRAL_FACIAL_VERIFICADO` (0,45) y DNI validado | `verificado` | — |
| `similitud ≥ 0,45` con el DNI en revisión manual | `revision_manual` | `dni_en_revision` |
| `UMBRAL_FACIAL_RECHAZO` (0,25) `≤ similitud < 0,45` | `revision_manual` | `zona_gris` |
| `similitud < 0,25` | `rechazado` | `similitud_baja` |
| Sin rostro o con varios en la selfie | `rechazado` (repite la foto) | `sin_rostro_selfie` / `varios_rostros_selfie` |
| Sin rostro en la foto del DNI | `revision_manual` | `sin_rostro_dni` |
| Faltan los modelos o fallan al cargar | `revision_manual` (nunca se aprueba a ciegas) | `comparador_no_disponible` |

Los umbrales se cambian con variables de entorno. El autor de SFace sugiere 0,363 para «misma persona» con
fotos de calidad; la foto del DNI es pequeña e impresa, por eso hay una zona gris amplia. **T20.4** los ajusta
con fotos del equipo (abajo).

## API

Todas con sesión de Django y CSRF en las escrituras. Prestador: `EsPrestador`; administrador: `EsAdministrador`.

| Método y ruta | Quién | Qué hace |
|---|---|---|
| `GET` · `POST /api/verificacion/facial/consentimiento/` | Prestador | Estado y aceptación (`{"acepto": true, "version_texto": "demo-facial-v1"}`), igual que el de DNI |
| `POST /api/verificacion/facial/consentimiento/revocar/` | Prestador | Revoca; impide nuevas comparaciones (no borra) |
| `POST /api/verificacion/facial/` | Prestador | Multipart `selfie` (JPG/PNG ≤ 5 MB) y `parpadeo=true`. 201 con el intento |
| `GET /api/verificacion/facial/` | Prestador | Su último intento (404 si no tiene) |
| `GET /api/verificacion/facial/?decision=revision_manual` | Administrador | Cola sin revisar y con selfie, con nombre y DNI del prestador |
| `GET /api/verificacion/facial/<id>/foto/selfie/` · `.../foto/dni/` | Administrador | Las dos fotos (`Cache-Control: private, no-store`); no hay URL pública |
| `PATCH /api/verificacion/facial/<id>/revision/` | Administrador | `{"revision": "aprobado" \| "rechazado"}`; 409 si ya se revisó (gana el primero) |
| `POST /api/verificacion/facial/eliminar/` | Prestador | `{"confirmo": true}`: borra todas sus selfies; quedan porcentaje, decisión y fecha |

Respuesta de un intento:

```json
{"id": 7, "decision": "revision_manual", "motivos": ["zona_gris"], "similitud": 0.3812, "porcentaje": 38.1,
 "parpadeo": true, "revision": "", "revisado_en": null, "tiene_selfie": true, "tiene_foto_dni": true,
 "creado": "2026-11-26T04:21:54.177530Z"}
```

Errores de `POST /facial/`: 403 sin consentimiento facial · 400 `{"selfie": [...]}` / `{"parpadeo": [...]}` ·
409 sin foto del titular del DNI, con una eliminación del DNI pendiente o si mientras se comparaba se revocó el
consentimiento o se pidió borrar · 429 más de 5 intentos por día · 503 si no se pudo guardar.

## Privacidad y borrado

- Las selfies van a `RAIZ_PRIVADA/facial/<prestador>/<uuid>/`, fuera de `/media/`.
- La comparación corre fuera de la transacción; al guardar se comprueba con `ControlDatosDni.generacion` que no se
  revocó el consentimiento ni se pidió borrar en el medio (si pasó, no se guarda la selfie).
- Borrar selfies es inmediato y se puede repetir; la eliminación del DNI de HU-21 no las toca (alcance aparte).
  Si HU-21 borra la foto del titular, el administrador ve el aviso «La foto del DNI ya se borró».

## Modelos

No se versionan (`modelos_faciales/` está en `.gitignore`):

```powershell
python manage.py descargar_modelos_faciales
```

Descarga `face_detection_yunet_2023mar.onnx` (~230 KB) y `face_recognition_sface_2021dec.onnx` (~37 MB) y
comprueba su SHA-256; si no coincide, no los usa. Otra carpeta: variable `MODELOS_FACIALES`. En la web, el modelo de
MediaPipe (`src/assets/face_landmarker.task`, 3,7 MB) y su WebAssembly se sirven desde la propia app, sin CDN.

## T20.4: calibrar con fotos del equipo

1. Cada integrante que acepte crea una carpeta con su nombre y pone la foto de su DNI recortada y 2 o 3 selfies.
2. `python manage.py calibrar_umbral_facial <carpeta>`: compara todos los pares y muestra cuántos de la misma
   persona y de personas distintas quedarían verificados, en revisión o rechazados, y propone umbrales.
3. Se ponen en `UMBRAL_FACIAL_VERIFICADO` y `UMBRAL_FACIAL_RECHAZO`. Las fotos no salen de la carpeta ni se guardan.

## Límites conocidos

- **La prueba de vida corre en el navegador.** El servidor solo recibe `parpadeo=true`: alguien que llame a la API
  a mano puede saltársela. Lo compensa la zona gris con revisión humana y que la decisión no aprueba sola. Una
  prueba de vida en el servidor (varios cuadros) queda como mejora.
- Sin fotos reales en el repo: las pruebas usan un comparador falso; con los modelos reales solo se prueba que
  una imagen sin rostro nunca se aprueba. La calibración con rostros reales es T20.4.
- La app Android no tiene esta pantalla todavía.
