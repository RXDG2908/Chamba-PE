# Prueba en un celular real · 26/11/2026

David Valcarcel. Teléfono: Samsung Galaxy A13 (Android 14, Chrome 158) conectado por USB a la PC
(`adb reverse tcp:5199`, así el celular abre `http://localhost:5199` como contexto seguro y permite cámara
y notificaciones sin HTTPS). Django y la web de `main` (`2c29fd1`) con una base temporal y cuentas ficticias;
la base y las fotos se borraron al terminar.

## HU-19 y HU-20: cámaras

| Prueba | Resultado |
|---|---|
| DNI con la cámara trasera (HU-19) | La cámara funciona, pero Chrome entregó **640×480**: la MRZ no se leyó (`mrz_ilegible`) y el rostro recortado del DNI medía ~40 px |
| Selfie con parpadeo (HU-20) | Avisos, detección del parpadeo y foto automática funcionan. Similitud **28,5 %** → zona gris (revisión manual), por el rostro diminuto del DNI |
| Con la corrección de esta rama (DNI 1920×1080, selfie 1280×720) | DNI a 1920×1080, rostro del DNI ~97 px; similitud **44,7 %** (mismo titular), a un punto del umbral de «verificado» (0,45). La MRZ siguió sin leerse (límite conocido del OCR, impedimento 24) |

Conclusión: sin pedir resolución, la verificación en celulares reales casi siempre caía en revisión
manual. Esta rama pide 1920×1080 para el DNI y 1280×720 para la selfie. Los umbrales de HU-20 se
siguen calibrando en T20.4 con fotos del equipo.

## T8.3: push real de FCM

Proyecto Firebase `chamba-pe-c043f` (el que usa el equipo, confirmado por Luis). Envío real desde Django
con la cuenta de servicio guardada fuera del repositorio. Se mide desde que el cliente envía la solicitud
hasta la hora (`when`) del aviso en Android, leída con `adb shell dumpsys notification` y corregida por la
diferencia de reloj PC–celular (ida y vuelta de adb < 200 ms).

| Situación | Enviados | Recibidos | Mín | Mediana | Máx | > 5 s |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| Chrome en primer plano | 10 | 10 | 0,60 s | 0,67 s | 0,85 s | 0 |
| Chrome en segundo plano | 10 | 10 | 0,64 s | 0,80 s | 1,19 s | 0 |
| Pantalla apagada | 10 | 3 | 0,58 s | 0,59 s | 0,66 s | 0 |
| Pantalla apagada (repetición) | 10 | 0 | — | — | — | — |

**Pantalla apagada: sin concluir.** FCM aceptó todos los envíos y el token seguía válido, pero el aviso
solo se muestra si el service worker confirma la sesión con `GET /api/usuarios/yo/`. Al revisar después,
el enlace USB (`adb reverse`) se había perdido, así que el celular no llegaba al servidor de la PC: es la
causa más probable, no comprobada. Las dos pruebas cortas siguientes (pantalla encendida) tampoco llegaron,
lo que coincide con esa causa. Falta repetir con el enlace verificado antes de cada tanda.

**Hallazgo menor:** con 25 avisos de Chrome sin leer, Android dejó de mostrar los nuevos. Mejora posible:
cerrar los avisos anteriores al mostrar uno nuevo.
