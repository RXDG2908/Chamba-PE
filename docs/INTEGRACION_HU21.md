# HU-21: preparación independiente de integración

Corte: 12/11/2026 (Lima). Rama feature/hu21-publicacion, worktree
Chamba-PE-hu21-publicacion. Base origin/main: `3a373b51d70779703c73ab345b885878002f3cde`.
Origen de los cambios: feature/hu21-consentimiento-eliminacion, HEAD `5f510c7`,
con cambios locales recuperados y respaldados. El original se conserva intacto.
Sin commit, push, PR, merge o cierre de HU-21 en esta entrega.

## Integración

Se aplicaron por parches únicamente los cambios HU-21 de verificacion y VerificarDni;
App conserva el menú por roles, administración, disponibilidad, certificados, DNI y
solicitudes FCM, agregando consentimiento y eliminación para prestadores. Se añaden
modelos, migraciones 0002/0003, coordinación SQLite, endpoints, procesador manual,
interfaz y pruebas. Se reutiliza sin cambios el método eliminar_datos_biometricos
de HU-19. No se traslada AVANCES ni se sustituye configuración antigua del frontend.
package-lock.json y dependencias existentes permanecen idénticos a main; package.json
solo añade test:hu21. No se copiaron bases, credenciales, entornos ni evidencias antiguas.

Los documentos pruebas-T21.1/T21.2 conservan cortes históricos del worktree original.
No acreditan por sí solos la integración actual; los resultados de esta preparación
se registran aquí. HU-19 y HU-8 ya están integradas en la base utilizada.

## Validación actual

- Django completo: 295/295 OK en 80,164 s, sin expectedFailure. Incluye casos HU-19,
  HU-8, administración, consentimiento, eliminación y concurrencia SQLite multiproceso.
- Playwright HU-21: 55/55 OK, un worker, cero retries; backend y cámara simulados.
- Worker FCM: 7/7 OK con APIs/transporte simulados.
- Regresión de interfaz HU-8: 11/11 OK; mapa HU-7: 16/16 OK.
- Integridad: HEAD, estado y SHA-256 de archivos versionados/nuevos de los diez
  worktrees previos coinciden con el inicio; main local permanece intacto.
- Entrega: 11 archivos modificados y 27 nuevos, sin patrones de secretos detectados;
  índice vacío. No hay cambios en servicios, usuarios, administración, autenticación,
  mapa, lockfile, scripts FCM ni lectura/validación MRZ fuera de los puntos HU-21.
- Lint y build: OK. Build advierte un chunk de aplicación de 500,39 kB; no se oculta
  ni se cambia el umbral. No bloquea la revisión, pero queda optimización pendiente.
- Django check y makemigrations --check --dry-run: correctos, sin esquema pendiente.
- En SQLite en memoria: migrate completo, reversa verificacion 0003→0001 y reaplicación
  0003 correctas, comprobando presencia/ausencia de la tabla de eliminación.
- Método de borrado HU-19: AST idéntico a main. Una comprobación auxiliar inicial
  falló al decodificar la salida Git con la codificación Windows; corregirla a UTF-8
  confirmó identidad sin alterar código funcional ni omitir aserciones.

Entorno: Python 3.14/Django 6.1.2, DRF 3.18.3, Pillow y firebase-admin del entorno
existente, utilizado solo en lectura con PYTHONDONTWRITEBYTECODE=1; ejecución desde
este worktree. npm ci usa el lock de main, sin modificarlo. FCM_ENABLED=false salvo
pruebas con transporte simulado. No se usaron credenciales ni notificaciones reales.
Playwright requiere detener sus servidores de prueba tras completar los casos en
este Windows; las salidas finales deben revisarse, no contar solo líneas de casos.

## Reproducción y límites

En un entorno Python aislado con requirements.txt instalado:

~~~powershell
$env:PYTHONDONTWRITEBYTECODE='1'
$env:FCM_ENABLED='false'
python manage.py test --noinput
python manage.py check
python manage.py makemigrations --check --dry-run
cd frontend
npm ci
npx playwright install chromium
npm run test:hu21
npm run test:push
npm run test:hu8
npm run test:hu7
npm run lint
npm run build
~~~

No aplicar migraciones a una base de trabajo para revisar este documento. La suite
Django crea bases temporales y usa imágenes sintéticas. Playwright no demuestra
interfaz contra Django real, cámara/OCR reales o uso físico en teléfono. Pendientes:
revisión de texto provisional demo-dni-v2, retención, responsable/frecuencia del
procesador y pruebas manuales de integración navegador→Django y cámara. Solicitar
eliminación no significa que ya esté atendida; se conserva el procesador manual.
No se promete eliminación forense, de backups o de huérfanos sin propietario.

Esta preparación queda para revisión; no publica ni declara HU-21 terminada.
