# HU-8: entrega independiente para validación T8.3

Fecha: 12/11/2026 (Lima). Rama local: feature/hu8-publicacion.
Base verificada por API GitHub y fetch: origin/main
`ecd633438719b88bba13fe54407dd7c2f3bcc177`. Sin commits propios, push ni PR.
**HU-8 permanece abierta; T8.3 corresponde a David y no está completada.**

## Alcance y dependencias

Se extrajo T8.1/T8.2 del worktree feature/hu8-notificaciones sin modificarlo.
La autenticación, disponibilidad, administración, mapa y navegación por roles
son las de main (#13/#15/#17/#18/#11/#14/#16/#31/#32/#33 ya integrados).
Se conservaron el redondeo público de coordenadas, guardas de rutas, menú,
pruebas y dependencias anteriores. No se trasladaron planificación de sprints,
evidencias antiguas, HU-19, HU-21 ni Android. Este canal es web responsive, no Android nativo.

T8.1: TokenFCM y migración servicios.0002_tokenfcm; alta/baja por usuario y sesión;
envío FCM data-only posterior al commit; baja de tokens inválidos; revocación al
cerrar sesión o cambiar de cuenta. Fallar FCM no elimina la solicitud.
T8.2: permiso voluntario, recepción foreground/background, aviso genérico, detalle
privado y aceptar/rechazar; no se contrata ni acepta automáticamente.

## Correcciones y contrato

- Worker: reserva en memoria las visualizaciones concurrentes; solo marca visto
  después de mostrar u observar un aviso. Libera la reserva incluso si falla consultar
  o mostrar; una entrega posterior puede reintentar. No hay reintento automático propio.
- HTTP 409: retira inmediatamente las acciones viejas y recarga desde el servidor,
  tanto en bandeja como en detalle. Si falla esa consulta, conserva las acciones
  retiradas y ofrece Reintentar consulta. No vuelve a enviar aceptar/rechazar.
- Rutas de solicitudes solo para prestadores; acceso privado comprobado en backend.
  Responder usa actualización condicional pendiente→estado para impedir dos respuestas.

| Ruta | Resultado/seguridad |
|---|---|
| POST /api/servicios/notificaciones/token/ | JSON token; 200 idempotente; usuario/sesión del servidor |
| DELETE misma ruta | JSON token; 204; solo elimina si pertenece al usuario actual |
| GET /api/servicios/solicitudes/recibidas/ | Bandeja propia |
| GET /api/servicios/solicitudes/recibidas/{id}/ | Detalle propio; 404 para ajeno/inexistente |
| POST /api/servicios/solicitudes/{id}/aceptar/ o rechazar/ | Sesión/rol/CSRF; 200 o 409 si ya respondida |

Sin sesión/rol o sin CSRF en mutaciones: 403. Token mal formado: 400.
El payload contiene solo solicitud_id y usuario_id; el aviso no revela nombre,
teléfono, descripción o coordenadas. El worker consulta /api/usuarios/yo/ antes de
mostrar. Solicitudes y consulta pública del cliente conservan el contrato HU-4 actual.

## Preparar una copia de pruebas

Cuando se autorice y publique esta rama, David debe usar un checkout limpio del SHA
comunicado, sin fusionar su #22 completo ni trabajar sobre su árbol con cambios.
Hasta entonces los cambios solo existen en este worktree y no están disponibles
mediante clone/fetch. Los siguientes comandos se ejecutan en la copia independiente.

Entorno verificado: Python 3.14, Django 6.1.2, DRF 3.18.3, django-cors-headers 4.9.0,
firebase-admin 7.7.0; Node 24.22.3, Firebase web 13.3.0, Vite 8.3.2 y Playwright 1.64.1.
El lockfile fija dependencias npm; requirements mantiene los rangos del proyecto
y agrega firebase-admin>=6.5,<8. Para repetir exactamente el entorno Python:

~~~powershell
python -m venv venv
.\venv\Scripts\python.exe -m pip install -r requirements.txt
.\venv\Scripts\python.exe -m pip install Django==6.1.2 djangorestframework==3.18.3 django-cors-headers==4.9.0 firebase-admin==7.7.0
$env:FCM_ENABLED='false'
.\venv\Scripts\python.exe manage.py check
.\venv\Scripts\python.exe manage.py makemigrations --check --dry-run
.\venv\Scripts\python.exe manage.py migrate
.\venv\Scripts\python.exe manage.py showmigrations servicios
.\venv\Scripts\python.exe manage.py test --noinput
.\venv\Scripts\python.exe -m pip check
cd frontend
npm ci
npx playwright install chromium
npm run test:push
npm run test:hu8
npm run test:hu7
npm run lint
npm run build
~~~

La migración y los datos deben ir a la db.sqlite3 de esa copia, nunca a una base de
trabajo existente. No copiar db.sqlite3, media, venv, node_modules o .env de otro
integrante. No se omiten pruebas existentes. El script test:hu7-local genera
evidencias en docs/evidencias/hu7-local: ejecutarlo en una copia desechable si se
quieren conservar los artefactos versionados. Requiere HU7_TEST_PYTHON con ruta
absoluta del intérprete y usa su propia SQLite temporal, sin Firebase.

## Datos ficticios reproducibles

En la copia aislada, después de migrate, abrir manage.py shell y ejecutar este
bloque. La contraseña se introduce localmente, no se publica. La comprobación de
nombres impide sobrescribir cuentas anteriores; si existen, usar otra copia limpia.

~~~python
from getpass import getpass
from django.contrib.auth import get_user_model
from django.db import transaction
from prestadores.models import Prestador, Rubro
User = get_user_model()
nombres = ['t83_cliente', 't83_prestador_a', 't83_prestador_b', 't83_admin']
assert not User.objects.filter(username__in=nombres).exists(), 'Usar una base aislada nueva'
clave = getpass('Contraseña local de las cuentas ficticias: ')
assert len(clave) >= 12
with transaction.atomic():
    for i, nombre in enumerate(nombres):
        u = User.objects.create_user(username=nombre, password=clave,
            email=nombre + '@example.invalid', is_staff=(nombre == 't83_admin'))
        if nombre in ['t83_prestador_a', 't83_prestador_b']:
            p = Prestador.objects.create(usuario=u, nombres='Ficticio', apellidos=nombre,
                dni=f'{i:08d}', telefono='000000000', latitud='-12.046374',
                longitud='-77.042793', radio_cobertura_km=5, disponible=True,
                estado=Prestador.Estado.VERIFICADO)
            p.rubros.add(Rubro.objects.get(nombre='Gasfitería'))
del clave
~~~

El alta de Prestador asigna su rol mediante las señales existentes. La marca
verificado es una fixture de laboratorio, no aprobación de un prestador real.
Buscar Gasfitería en las mismas coordenadas; usar descripción ficticia y teléfono
de prueba de nueve dígitos. No emplear DNI, fotos o datos personales reales.

## Configuración para FCM real (pendiente de David)

1. Acordar el proyecto de pruebas autorizado. Registrar/usar app web y configuración
   pública Firebase; habilitar FCM HTTP v1 y FCM Registration API según ese proyecto.
2. Crear frontend/.env.local a partir de frontend/.env.example, sin sobrescribir
   uno existente. Completar API key/configuración web y clave VAPID **pública**.
   Todas las variables VITE_* terminan en el bundle: nunca poner claves privadas.
3. El backend usa Application Default Credentials. Obtener acceso autorizado de
   envío FCM; si usa JSON de cuenta de servicio, guardarlo fuera del repo y compartir
   acceso por un canal apropiado, nunca por commits, capturas o chat del proyecto.
4. En PowerShell de backend, desde la raíz de la copia:

~~~powershell
$env:FCM_ENABLED='true'
$env:FIREBASE_PROJECT_ID='ID_DEL_PROYECTO_DE_PRUEBAS'
$env:GOOGLE_APPLICATION_CREDENTIALS='C:/credenciales/chamba-pruebas/firebase-service-account.json'
.\venv\Scripts\python.exe manage.py runserver
~~~

En otra terminal, frontend/: npm run dev. Usar http://localhost:5173 de manera
consistente para cookies/permiso. Django no carga .env automáticamente: .env.example
es una plantilla de variables de proceso. Reiniciar backend/Vite tras configurar.
Backend, app web y VAPID deben corresponder al mismo proyecto. No hace falta Firebase
Authentication, Firestore ni Storage. El mensaje «activadas» confirma registro del
navegador, no habilitación del servidor ni entrega real. Verificar FCM_ENABLED.

Para móvil por LAN se necesita HTTPS confiable; HTTP con IP local no basta. Preparar
un entorno de prueba autorizado con hosts/CSRF/origen coherentes. En despliegue,
servir /firebase-messaging-sw.js y sus assets como JavaScript desde el mismo origen,
/api mediante proxy y fallback SPA para /solicitudes/{id}. No usar servidor Vite
de desarrollo como despliegue público. El build revisado usa configuración vacía
y no acredita conexión con Firebase.

Referencias oficiales de configuración:
[recepción web](https://firebase.google.com/docs/cloud-messaging/js/receive),
[envío Admin SDK](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk) y
[gestión de tokens](https://firebase.google.com/docs/cloud-messaging/manage-tokens).

## Guion y registro T8.3

Usar dos perfiles de navegador separados: cliente y prestador A. En el prestador,
/solicitudes → Activar notificaciones → permiso concedido → registro HTTP 200.
El cliente busca y envía una solicitud a A. Registrar ID, aparición de un aviso,
clic, detalle propio, aceptación/rechazo y estado consultado por el cliente.
Repetir con B para verificar destinatario. No incluir tokens o cookies en evidencias.

| Escenario manual | Qué observar |
|---|---|
| Primer plano | Una notificación por solicitud; recorrido onMessage→worker |
| Segundo plano / pestaña cerrada con navegador abierto | Recepción y clic hasta detalle correcto |
| Aceptar/rechazar y dos pestañas | Una respuesta final; segunda 409 y UI actualizada sin botones obsoletos |
| Sesión vencida / logout | Sin aviso para sesión revocada; detalle protegido |
| Cambio directo A→B sin logout | No mostrar avisos de A; reactivar para registrar B |
| Cliente/administrador/otro prestador | Sin detalle ajeno ni acciones permitidas |
| Permiso denegado / navegador incompatible / configuración ausente | Mensaje recuperable, bandeja operativa |
| Red o Firebase fallan | Solicitud conservada; no declarar entrega; bandeja como alternativa |
| Reactivar, varios dispositivos, token inválido | Destinatarios correctos, baja de inválidos y tiempos por dispositivo |
| Worker reiniciado y avisos ya cerrados | Registrar duplicados si aparecen; no prometer deduplicación persistente |
| Teléfono físico HTTPS | Permiso, recepción, clic y respuesta; identificar SO/navegador y restricciones |

Para <5 s, usar video/reloj común desde la acción de envío hasta la aparición del
aviso; no medir solo HTTP 201, aceptación del SDK o GET de bandeja. Propuesta de
muestreo: al menos 10 envíos por condición principal; acordar matriz con David/PO.
Registrar cada intento, también pérdidas, duplicados y >5 s, sin promediar para
ocultar incumplimientos. No afirmar garantía universal por pasar una muestra.

Plantilla: fecha/hora, SHA publicado, proyecto de prueba (sin secretos), ID solicitud,
SO/navegador/dispositivo, red, permisos, estado de pestaña, inicio/fin, diferencia,
recibido/no recibido, duplicados, clic/destino, respuesta/estado cliente y evidencia.
Un mensaje no observado se registra como no recibido durante la ventana de observación
acordada, no como tiempo cero. El #22 existente mide bandeja HTTP; conservarlo como
tal y añadir validación push, sin atribuir sus tiempos a FCM.

## Resultados de esta preparación

| Verificación | Resultado del 12/11/2026 |
|---|---|
| Django completo | 191/191 OK en 41,499 s; SQLite de pruebas, FCM simulado/deshabilitado |
| Worker Node | 7/7 OK: reintentos, concurrencia, destinatario, deduplicación y clic simulados |
| Playwright HU-8 | 11/11 OK: 409 en lista/detalle, fallo y reintento de consulta, aceptar/rechazar, roles, sesión y menú; API simulada |
| Playwright HU-7 | 16/16 OK sin modificar sus pruebas |
| Integración HU-7 | 14/14 OK, Django/SQLite reales y datos ficticios; copia aislada para no alterar evidencias; sin FCM |
| Lint y build | OK; emite worker en raíz y assets |
| Django check / makemigrations --check --dry-run | Sin problemas / sin migraciones pendientes |
| Migraciones | migrate completo, reversa servicios 0002→0001 y reaplicación 0002: OK en SQLite en memoria |
| Dependencias | pip check OK; npm audit --omit=dev: 0 vulnerabilidades reportadas; versiones previas del lock conservadas |

Playwright completó todos los casos, pero el cierre automático de los servidores
de prueba quedó esperando en este Windows. Se detuvieron únicamente los procesos
creados por estas ejecuciones, después de los casos; las tres suites finalizaron
con código 0. Los tiempos finales incluyen esa espera (HU-8 2,4 min; HU-7 1,8 min;
integración 57,4 s); no son medidas de latencia de producto.

La instalación npm necesitó caché temporal propia por permisos del entorno y reportó
avisos de deprecación de OpenTelemetry y scripts no aprobados de dependencias.
Lint/build y pruebas pasaron sin habilitarlos; no se cambiaron paquetes globales.
Se usó el entorno Python existente solo como intérprete/dependencias de lectura,
con bytecode deshabilitado y cwd en la copia nueva; no se instalaron paquetes en él.

**No se enviaron ni recibieron notificaciones FCM reales durante esta preparación.**
La evidencia histórica del worktree original no se presenta como validación del
nuevo conjunto. T8.3, dispositivos físicos, permisos reales y medidas <5 s siguen
pendientes. Sin cola durable/reintentos de aplicación: el envío es síncrono después
del commit; varios dispositivos/fallos pueden aumentar latencia. La deduplicación
en memoria no sobrevive al reinicio del worker cuando el aviso ya se cerró.

## Integridad y archivos de la entrega

Se compararon HEAD, estado Git y SHA-256 de 1.470 archivos versionados/nuevos en
los nueve worktrees previos: sin diferencias. main local no cambió. La única
referencia compartida actualizada fue origin/main mediante fetch; la rama nueva
permanece en ecd6334, sin commits ni índice preparado. No se copiaron .env.local
ni credenciales. El escaneo de patrones de claves privadas/tokens no encontró
coincidencias en los archivos de la entrega. No sustituye la revisión humana del diff.

Modificados (11): .gitignore; core/settings.py; requirements.txt;
servicios/models.py, urls.py y views.py; usuarios/views.py;
frontend/package.json, package-lock.json, vite.config.js y src/App.jsx.

Nuevos (15): .env.example; docs/HU8_NOTIFICACIONES.md; frontend/.env.example;
frontend/firebase-messaging-sw.js; frontend/playwright.hu8.config.mjs;
frontend/src/SolicitudesRecibidas.jsx, firebase-config.js, notificaciones.js y
push-worker.js; frontend/tests/push-worker.test.mjs y hu8/solicitudes.spec.mjs;
servicios/migrations/0002_tokenfcm.py; servicios/notificaciones.py, push.py y test_push.py.

## Revisión antes de publicar

Revisar diff y archivos nuevos de esta rama; no usar git add . desde el worktree
original. No incluir bases, media, .env.local, credenciales, tokens, artefactos de
pruebas o documentos de otras HU. Las plantillas no contienen valores secretos.
Tras aprobación expresa, se podrán crear commits y publicar solo esta rama, comunicar
a David SHA e instrucciones y mantener HU-8 abierta. Esta preparación se detiene
antes de commit/push/PR; no integra en main ni modifica las otras ramas.
