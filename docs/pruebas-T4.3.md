# Pruebas T4.3 — API buscar → solicitar → aceptar/rechazar

Fecha: 12/10/2026 (America/Lima). Responsable de pruebas: Luis Abad.
Estado: **API verificada en copia aislada; T4.3 y HU-4 siguen parciales**.

## Código probado y aislamiento

- Búsqueda: HEAD de `hu-3-busqueda`, `5d2e1dbc0e6ad2362307800e37f0ad9bb5592e5a`.
- Solicitudes de Renzo: referencia local `origin/hu-4-solicitud`,
  `eb39859273a265b3e8907ccf6af0099138bc3ac1`.
- Sin fetch nuevo: los resultados corresponden exactamente a esos commits.
- Se exportó HEAD con `git archive` a `TemporaryDirectory`. Allí se añadieron
  los archivos de T4.1 desde el commit de Renzo: core/settings.py, core/urls.py,
  servicios/{admin,apps,models,serializers,tests,urls,views}.py y la migración 0001
  con su __init__.py. No se hicieron merges, checkout, commits ni push.
- Antes de combinar, el ejecutor comprobó igualdad de las definiciones de búsqueda
  y sus tests mediante AST, y de distancias.py, prestadores/urls.py y
  ResultadosPrestadores.jsx byte a byte. Renzo ya incluye esa misma búsqueda;
  solo incorpora solicitudes y sus imports/configuración.
- Configuración exclusiva de pruebas: SQLite en memoria para NAME y TEST.NAME,
  MEDIA_ROOT dentro de la copia desechable. Las migraciones se aplicaron únicamente
  a la BD de pruebas creada y destruida por Django.
- La copia se eliminó al terminar. Se verificaron rama, HEAD, git status y hashes
  de los archivos T2.3, db.sqlite3 y archivos de media: **PRESERVATION_OK**.
  AVANCES.md y los nuevos documentos se actualizan después de esta comprobación.

Los cambios pendientes de registro/certificaciones T2.3 no se copiaron ni ejecutaron:
su fallo de asociación HU-2 continúa pendiente y no queda resuelto por este resultado.

## Contrato revisado de Renzo

| Operación | Endpoint | Acceso y respuesta |
|---|---|---|
| Buscar | GET /api/prestadores/buscar/?rubro=ID&latitud=LAT&longitud=LON | Público; 200, lista con ID del prestador, rubros y distancia |
| Solicitar | POST /api/servicios/solicitudes/ | Público; 201, ID y estado `pendiente` |
| Consultar estado | GET /api/servicios/solicitudes/ID/ | Público; 200, estado, estado_display y respondida |
| Recibir | GET /api/servicios/solicitudes/recibidas/ | Autenticación del prestador; 200, su bandeja con contacto del cliente |
| Aceptar | POST /api/servicios/solicitudes/ID/aceptar/ | Prestador propietario; 200, `aceptada` y fecha respondida |
| Rechazar | POST /api/servicios/solicitudes/ID/rechazar/ | Prestador propietario; 200, `rechazada` y fecha respondida |

El envío exige `prestador`, `rubro`, `cliente_nombre`, `cliente_telefono`,
`descripcion`, `latitud` y `longitud`. Valida disponibilidad, rubro ofrecido y
cobertura. `id`, `estado` y `creada` son de solo lectura. `rubro` es un ID al enviar,
pero un nombre al consultar estado/bandeja. La consulta de estado omite los datos
de contacto; el cliente consulta con el ID devuelto al crear.

La bandeja y respuestas usan los mecanismos de autenticación de DRF; los nuevos
casos utilizaron Basic Auth real con cuenta/contraseña creadas en la BD de pruebas,
sin force_login ni force_authenticate. El cliente usa otra instancia de Client,
sin cookies ni credenciales del prestador. No se implementó HU-5.

## Ejecución y resultados reales

Ejecutor reproducible guardado en [pruebas_T4_3.py](pruebas_T4_3.py).
Desde la raíz, PowerShell:

```powershell
.\venv\Scripts\python.exe docs/pruebas_T4_3.py
```

Dentro de la copia, el ejecutor usa el mismo Python del venv para:

```powershell
python manage.py test prestadores.tests servicios.tests t43_flow_tests --settings=t43_settings --noinput --verbosity=2
```

Resultado real: **32 pruebas OK**, 4.521 s de pruebas, salida 0, system check
sin problemas. Son 7 de registro existentes en HEAD, 11 de búsqueda/distancia,
12 de Solicitud de Renzo y **2 nuevas de flujo T4.3**. Los tests de los compañeros
no se modificaron. No se ejecutó el descubrimiento genérico de test_reporte.py
ni se hicieron peticiones al servidor local.

Se utilizó un prestador ficticio disponible, rubro exclusivo de pruebas,
centro (0, 0), radio 5 km; cliente en (0.010000, 0.000000). La búsqueda devolvió
el prestador a **1.111950802335329 km**, y ese ID obtenido de la búsqueda se pasó
al envío de la solicitud.

| Paso | Caso aceptar | Caso rechazar |
|---|---|---|
| Buscar y seleccionar ID real de respuesta | 200, un prestador | 200, un prestador |
| Enviar solicitud a ese ID | 201, `pendiente` | 201, `pendiente` |
| Cliente consulta estado inicial | 200, `pendiente`, respondida null | 200, `pendiente`, respondida null |
| Prestador consulta bandeja con Basic Auth | 200, solicitud y contacto correctos | 200, solicitud y contacto correctos |
| Prestador responde | 200, `aceptada` | 200, `rechazada` |
| Cliente independiente consulta de nuevo | 200, `aceptada` / “Aceptada” | 200, `rechazada` / “Rechazada” |
| Verificación de persistencia | FK prestador/rubro correctas, estado y fecha guardados | FK prestador/rubro correctas, estado y fecha guardados |
| Intentar respuesta contraria después | 409, conserva `aceptada` | 409, conserva `rechazada` |

Cada flujo crea una solicitud independiente. Django restablece la BD entre casos,
por lo que los IDs pueden repetirse: no es una reutilización de la misma solicitud.
La fecha `respondida` consultada por el cliente coincide con la respuesta del
prestador. El JSON de consulta no expone cliente_nombre ni cliente_telefono.
El ejecutor imprime `EVIDENCIA_T43` con los pasos y respuestas de cada flujo.

Los 12 tests de Renzo también aprobaron: envío válido, obligatorios, prestador
no disponible, rubro no ofrecido, fuera de cobertura, bandeja propia, permisos,
aceptación y consulta, rechazo, segunda respuesta bloqueada, otro prestador 404
y respuesta anónima 403. No se encontraron fallos en estos casos de API.

## Bloqueos y reproducción

**Integración T4.1 — Renzo/revisión del equipo.** En la rama de trabajo actual,
core/urls.py no incluye /api/servicios/ y servicios no está en INSTALLED_APPS;
el modelo/migración de Solicitud solo existe en la referencia remota revisada.
Reproducir por comparación:

```powershell
git diff HEAD eb39859273a265b3e8907ccf6af0099138bc3ac1 -- core/settings.py core/urls.py servicios
```

La prueba exitosa de la copia no implica que el flujo ya exista en tu rama o main.
Queda pendiente revisión e integración mediante el procedimiento del equipo,
cuando se autorice. No se fusionó nada durante estas pruebas.

**React T4.2 — David.** No hay conexión de solicitudes de servicio desde las
tarjetas, formulario de envío, bandeja aceptar/rechazar ni pantalla que consulte
el estado de la solicitud. El componente UploadCertificado carga documentos;
no llama a /api/servicios/solicitudes/.

Para reproducir el bloqueo: iniciar frontend/, abrir Resultados y buscar un
prestador. La tarjeta no ofrece Solicitar; App.jsx solo navega entre registro y
resultados. Inspeccionar ResultadosPrestadores.jsx, App.jsx y UploadCertificado.jsx
confirma que no se puede seguir el flujo de servicio desde React. No se añadieron
botones, pantallas ni funcionalidad de los compañeros.

## Pendientes de T4.3 completa

1. Revisar/integrar T4.1 y disponer de pantallas T4.2 conectadas a los endpoints
   anteriores, preservando ID prestador/rubro y ubicación seleccionada.
2. Probar desde React con cliente y prestador en sesiones separadas: buscar,
   solicitar, recibir en bandeja, aceptar y visualizar el nuevo estado del cliente.
3. Repetir con otra solicitud rechazada; comprobar errores, permisos, recuperación
   de conexión y presentación móvil. Las pruebas API no verifican esos elementos.
4. Revisar la entrega por PR y cumplir la definición de terminado del equipo.

No se realizaron pruebas completas desde React ni pruebas manuales de navegador
en esta sesión. T4.3 sigue parcial, sin horas nuevas ni cambios al burndown;
HU-4 no se marca completa. Los bloqueos T2.3 documentados previamente se conservan.
