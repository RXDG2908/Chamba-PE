# ChambaPe - Frontend

Frontend del proyecto integrador **ChambaPe**, desarrollado con React y Vite.

## Avance Sprint 1 - T3.2

La pantalla Resultados está conectada a Django mediante el proxy `/api` de Vite.
Conserva el formulario de registro de Renzo y la navegación existente.

### Funcionalidades realizadas

- Creación del frontend utilizando React + Vite.
- Diseño de la interfaz principal de ChambaPe.
- Implementación del listado de prestadores.
- Visualización de información de los prestadores mediante tarjetas.
- Selector de rubros desde `GET /api/prestadores/rubros/`.
- Selección de ubicación con Leaflet y marcador; también permite introducir
  coordenadas exactas y confirmarlas con **Usar coordenadas**.
- Botón Buscar: envía rubro, latitud y longitud a
  `GET /api/prestadores/buscar/` usando rutas relativas y el proxy existente.
- Tarjetas con nombres, apellidos, todos los rubros y distancia geográfica
  en km (presentada con hasta dos decimales). Se eliminaron las calificaciones
  y los perfiles fijos del frontend; se conserva el orden recibido de la API.
- Estados inicial, cargando, sin resultados y error en español; reintento de
  carga de rubros, validación de rubro y ubicación y rangos de coordenadas.
- Las nuevas búsquedas cancelan la petición anterior mediante `AbortController`.
  Cada respuesta comprueba que pertenece a la petición vigente. Cambiar
  filtros limpia resultados y cancela la búsqueda; salir de Resultados
  también cancela las peticiones pendientes.
- Diseño adaptable: controles apilados en celular, tarjetas flexibles y
  navegación con ajuste de línea.

Contrato del backend: [API de búsqueda](../docs/API_BUSQUEDA.md).
T3.2 fue implementada en `hu-3-busqueda` e integrada por el PR #3; el cuadro
de Renzo en main ya la registra como terminada. Las pruebas manuales de Luis
del 14/10 se detallan abajo y corresponden a `7e671cd`. HU-3 continúa parcial; el ordenamiento
(T3.3) y las pruebas de datos/rendimiento (T3.4) corresponden a David.

## Tecnologías utilizadas

- React
- Vite
- JavaScript
- HTML
- CSS

## Ejecutar el proyecto

Instalar las dependencias:

npm install

Iniciar el servidor de desarrollo:

npm run dev

Luego ingresar a la dirección mostrada por Vite, normalmente:

http://localhost:5173/

Django debe estar disponible en `http://127.0.0.1:8000`:

```powershell
# Desde la raíz del repositorio, en otra terminal:
.\venv\Scripts\python.exe manage.py runserver
```

El proxy existente se usa con el servidor de desarrollo (`npm.cmd run dev`);
para desplegar el build se debe configurar la ruta `/api` hacia Django en el
servidor de alojamiento.

## Probar el prestador ficticio local

1. Mantener Django y Vite activos y abrir la URL indicada por Vite.
2. Pulsar **Resultados** y seleccionar **Gasfitería** cuando carguen los rubros.
3. Introducir latitud `-12.046374` y longitud `-77.042793`.
4. Pulsar **Usar coordenadas**: debe aparecer el marcador y la confirmación
   de ubicación. También se puede seleccionar otra ubicación tocando el mapa.
5. Pulsar **Buscar**. Con el registro ficticio local existente debe aparecer
   **Demo Gasfitero ficticio**, rubro Gasfitería y distancia **0 km**.

La pantalla solo consulta datos: no prepara, modifica ni borra prestadores.
Si falta el registro de prueba, el comando idempotente está documentado en
la API de búsqueda. No es la tarea T3.4.

## Verificación del 06/10/2026

Ejecutados dentro de `frontend/`:

```powershell
npm.cmd run lint
npm.cmd run build
```

Ambos terminaron con código 0: ESLint sin errores y build de Vite correcto
(62 módulos). Comprobación HTTP del proxy de Vite en `localhost:5173`:
rubros 200; búsqueda con Gasfitería y las coordenadas exactas 200, con el
prestador id 2 a 0.0 km. Estas comprobaciones no sustituyen pruebas visuales.

Luis confirmó manualmente la tarjeta **Demo Gasfitero ficticio**, Gasfitería,
**0 km**, y respuestas HTTP 200 tanto vacía como positiva. Tras incorporar
el commit del equipo `ae51360` a la base de la rama, se repitieron lint y build:
ambos correctos. La comprobación positiva de la tarjeta no cubre las demás
pruebas visuales listadas abajo.

Pendientes registrados el 06/10 (históricos; véase la actualización del 14/10):

- Revisar mapa, marcador, tarjetas y navegación en escritorio y celular,
  incluidos anchos pequeños y selección táctil. Las teselas de OpenStreetMap
  necesitan conexión a Internet.
- Probar Buscar sin rubro o ubicación, y una búsqueda sin coincidencias
  (por ejemplo, Gasfitería en `0, 0`).
- Simular fallo de red y reintentar; comprobar los estados de carga y error.
- Con red lenta, lanzar búsquedas consecutivas y cambiar rubro o ubicación
  durante la espera; confirmar que no reaparecen resultados anteriores.
- Volver a Registro y revisar que el formulario de Renzo siga funcionando.

## Comprobaciones de cierre T3.2 — 14/10/2026

Rama `pruebas-cierre-sprint1`. Las pruebas automatizadas/HTTP del asistente
y las manuales de Luis Abad corresponden a la base `7e671cd`, antes de
incorporar origin/main. Después se actualizó la rama por fast-forward al
PR #4, `e20c533`; estas pruebas no se repitieron sobre esa base.
Se revisaron CLAUDE.md, PLAN_EQUIPO.md, README.md, AVANCES.md, el contrato
de búsqueda y los informes `docs/pruebas-T2.3.md` y `docs/pruebas-T4.3.md`.
No se encontró AGENTS.md en el repositorio ni en sus directorios ascendentes.

### Pruebas automatizadas y comprobaciones HTTP del asistente sobre 7e671cd

| Comprobación | Resultado y alcance |
|---|---|
| `npm.cmd run lint`, desde `frontend/` | Código 0, sin errores. El fallo histórico de UploadCertificado no se reproduce en esta base; no se modificó ese componente. |
| `npm.cmd run build`, desde `frontend/` | Código 0, Vite 8.3.2, 62 módulos. No es una prueba visual. |
| `.\venv\Scripts\python.exe manage.py test servicios.tests --noinput`, desde la raíz | 11 pruebas OK, 0.217 s; base de pruebas creada y destruida. Filtros, cobertura, distancia, datos públicos, parámetros y lista vacía. |
| HTTP a Vite `http://127.0.0.1:5173/` y módulo ResultadosPrestadores | 200; verifica que se sirven los recursos, sin renderizado visual. |
| Proxy `/api/prestadores/rubros/` | 200, 10 rubros. |
| Proxy búsqueda Gasfitería (id 1), `-12.046374, -77.042793` | 200, Demo Gasfitero ficticio (id 2), distancia 0.0 km. No se observó la tarjeta en navegador. |
| Proxy búsqueda Gasfitería, `0, 0` | 200, `[]`. No se observó el mensaje de lista vacía en navegador. |
| Proxy búsqueda con latitud `91` | 400, «La coordenada debe estar entre -90 y 90 grados.» |
| Django detenido y luego reiniciado | Rubros y búsqueda devolvieron 502 con Django detenido; al reiniciar y repetir ambas consultas, 200. Se repitió también la búsqueda vacía: 200, `[]`. Esto comprueba recuperación HTTP, no el mensaje ni el reintento de React. |

Comando backend ejecutado **desde la raíz**:

```powershell
.\venv\Scripts\python.exe manage.py test servicios.tests --noinput
```

Faltaba `django-cors-headers` tanto en el entorno existente como en
`requirements.txt`, aunque settings ya registra `corsheaders`.
Se instaló localmente con `.\venv\Scripts\python.exe -m pip install django-cors-headers`
y se propuso localmente fijar la versión comprobada, `4.9.0`. Al incorporar
`e20c533`, se conservó `django-cors-headers>=4.4` añadido por Renzo en el PR #4;
el pin local redundante se retiró. No queda un cambio pendiente ni se propone
un commit adicional de esa dependencia.
No se recreó el entorno virtual ni se ejecutaron migraciones sobre la base local.
`preparar_busqueda_local` se ejecutó dos veces: ambas reutilizaron el id 2 sin
modificaciones. SHA-256 de `db.sqlite3` antes de preparar datos y después de
las consultas: `87071DB1599A9FC259AD66F0203C040F53DA9FE9B36C3BD9EFE941B5F0BD35CB`.

La revisión de `ResultadosPrestadores.jsx` encontró cancelación al cambiar
rubro, editar/confirmar coordenadas, tocar el mapa, buscar de nuevo y salir;
las respuestas y errores comprueban señal de aborto e identidad de la petición.
No se detectó un defecto de T3.2 por lectura/HTTP, por lo que no se cambió su
código. Esta revisión no demuestra el comportamiento ante respuestas tardías.

### Pruebas manuales realizadas por Luis — 14/10/2026, base 7e671cd

Luis comunicó los siguientes resultados de su prueba en navegador. Son sus
observaciones manuales, separadas de la ejecución automatizada del asistente
y de las pruebas de Renzo registradas en AVANCES.md; no se repitieron sobre
`e20c533`. No se comunicaron navegador ni dimensiones exactas.

| Caso manual | Resultado observado por Luis |
|---|---|
| Vista móvil simulada y escritorio | Controles, mapa y tarjeta sin cortes laterales observados. No acredita un teléfono físico. |
| Gasfitería, latitud `-12.046374`, longitud `-77.042793` | Demo Gasfitero ficticio a 0 km. |
| Cambio a Electricidad | Desaparece la tarjeta anterior y aparece el mensaje de búsqueda vacía. |
| Django detenido | Aparece el mensaje de que no se pudo comunicar con el servidor. |
| Django reiniciado; Buscar sin recargar | Desaparece el error y vuelve el gasfitero. |
| Búsquedas consecutivas Gasfitería → Electricidad | Network mostró solicitudes anteriores `rubro=1` canceladas y la última `rubro=2` con HTTP 200. Quedó el mensaje vacío sin reaparecer el gasfitero. |

Estas observaciones cubren presentación móvil simulada/escritorio, resultado
positivo/vacío, error/recuperación de búsqueda y búsquedas consecutivas con
cambio de rubro en `7e671cd`. No demuestran cambios rápidos de ubicación
ni los demás casos pendientes indicados abajo.

### Pasos de reproducción y pendientes específicos

El asistente no tiene herramienta de navegador disponible ni
Playwright/Puppeteer instalado; no ejecutó pruebas visuales. Lint, build y
HTTP no sustituyen las pruebas manuales de Luis. Los siguientes pasos sirven
para reproducir los casos y ampliar cobertura; no se afirma que todos se hayan
realizado ni que se hayan probado sobre el nuevo main.

1. Inicia Django desde la raíz con
   `.\venv\Scripts\python.exe manage.py runserver 127.0.0.1:8000 --noreload`.
   En otra terminal, desde `frontend/`, ejecuta
   `npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort`.
   Abre `http://127.0.0.1:5173/` y pulsa **Resultados**.
2. En escritorio (1280 × 800) y emulación móvil (360 × 800 y 390 × 844),
   comprueba navegación, controles, mapa, marcador y tarjetas sin recortes ni
   desplazamiento horizontal. Toca el mapa y comprueba que cambia la ubicación.
   La respuesta táctil en un teléfono físico sigue requiriendo ese dispositivo.
3. Elige Gasfitería, confirma `-12.046374, -77.042793` y busca: debe verse
   **Demo Gasfitero ficticio**, Gasfitería, **0 km**. Confirma `0, 0` y busca:
   debe verse «No encontramos prestadores disponibles…», sin la tarjeta anterior.
   Prueba también Buscar sin rubro/ubicación y coordenadas fuera de rango.
4. Con rubros ya cargados, detén Django con Ctrl+C y busca. Debe aparecer
   «No pudimos comunicarnos con el servidor. Inténtalo de nuevo.» y desaparecer
   las tarjetas. Reinicia Django y pulsa **Buscar**: debe recuperarse el resultado.
   Después, con Django detenido, sal a Registro y vuelve a Resultados: comprueba
   el error de rubros; reinicia Django y pulsa **Reintentar carga de rubros**.
5. En DevTools → Network, activa **Slow 3G** y conserva el registro de peticiones.
   Busca en Lima y, antes de la respuesta, cambia a Electricidad: las tarjetas
   deben limpiarse y ninguna respuesta vieja debe repoblarlas. Repite cambiando
   las coordenadas a `0, 0`, confirmándolas y buscando. Espera todas las respuestas:
   debe quedar el estado vacío, sin Demo. Repite en sentido inverso: debe quedar
   Demo en Lima. Pulsa **Buscar de nuevo** varias veces durante la carga y prueba
   salir a Registro y volver: no deben aparecer resultados de peticiones previas.
6. Registra anchos, navegador, resultados y fallos observados en este README
   y AVANCES.md; la regresión manual del formulario de Renzo también queda pendiente.

Pendiente específicamente: concurrencia al editar/confirmar coordenadas o
tocar el mapa durante una búsqueda, salir/volver con peticiones pendientes,
reintento de carga de rubros, validaciones visibles, regresión del registro y
prueba táctil en teléfono físico. La emulación móvil no sustituye este último.
La búsqueda vacía por Gasfitería en `0, 0` solo tiene evidencia HTTP del asistente;
Luis observó el estado vacío mediante Electricidad. La repetición de estos
casos sobre `e20c533` también queda pendiente.

Se conserva el estado de T3.2 ya registrado por Renzo en main. HU-3 sigue
parcial por T3.3/T3.4. No se ejecutaron nuevas pruebas, scripts de rendimiento
ni la suite de T2.3/T4.3 al incorporar main; sus informes históricos se
conservan. T2.3 y T4.3 no se cierran ni se declara terminado el sprint.

Los tres archivos locales originales quedaron resguardados en el stash
`cc6a40f64f6245b604f5560623e9503f4f76f1c9` antes de integrar origin/main.
Se combinaron las evidencias de documentación con los avances de Renzo,
sin modificar código funcional, migrar ni alterar la base local. No se crearon
commits de trabajo, push ni PR.
