# T7.2 — mapa de prestadores

Implementación del 27/10/2026 en `feature/t7.2-mapa-marcadores`, con pruebas manuales reportadas por Luis y pendiente de revisión e integración. HU-7 y Sprint 2 siguen abiertos.

## Comportamiento

Se reutiliza `GET /api/prestadores/buscar/` y el mapa Leaflet de Resultados.
Cada resultado conserva su tarjeta y formulario, y tiene un marcador en su centro
de cobertura registrado (no es seguimiento en tiempo real). Azul identifica al
cliente; verde y número identifican al prestador. Un `+` indica coordenadas
compartidas: cualquier marcador de ese punto abre una ventana con todos sus
prestadores, desplazable si hace falta. También funciona si coinciden con el cliente.

Las ventanas muestran nombre, todos los rubros, distancia en km y «Sin
calificaciones», porque el contrato actual no incluye puntuaciones reales.
Los textos se renderizan con React, sin interpolar datos del servidor en HTML.
Pulsar marcadores, ventanas o el botón de encuadre no selecciona otra ubicación.
Al cambiar rubro o ubicación, se cancela la petición y se limpian tarjetas y
marcadores. Una respuesta cancelada no puede reemplazar los resultados nuevos.
Coordenadas inválidas del servidor producen un error, sin resultados parciales.

El mapa encuadra cliente/resultados al cambiar esos datos; explorar o abrir
ventanas no vuelve a encuadrarlo. «Ver ubicación y resultados» recupera el encuadre.
En celular las coordenadas se apilan y la ventana limita su ancho al espacio disponible.

## Validación ejecutada

- `cd frontend; npm run lint`: OK.
- `cd frontend; npm run build`: OK (Vite, 73 módulos).
- Dependencias existentes restauradas desde el lockfile; sin bibliotecas nuevas.
- No se modificaron registros locales ni se enviaron solicitudes de prueba.

La automatización visual fue bloqueada por Computer Use al no poder determinar
con confianza la URL activa de Chrome. Las comprobaciones visuales siguientes
fueron realizadas manualmente por **Luis en Chrome**, según su reporte; no se
atribuyen a la automatización ni a lint/build.

## Pruebas manuales realizadas por Luis

| Caso | Resultado reportado |
|---|---|
| Buscar Gasfitería | El prestador ficticio Demo Gasfitero aparece en el mapa y en su tarjeta. |
| Abrir el marcador | La ventana muestra nombre, rubro, distancia y «Sin calificaciones». |
| Cerrar y volver a abrir la ventana | Las coordenadas del cliente se conservan. |
| Cambiar de rubro | Se eliminan el marcador y la tarjeta anteriores. |
| Buscar un rubro sin coincidencias | Aparece «No encontramos prestadores». |
| Vista de 390 px de ancho | Mapa y ventana del marcador se ven correctamente. |
| Vista de 320 × 800 px | Tarjeta, campos y botón de solicitud se ven sin cortes. |

Luis aclaró que las variaciones de distancia entre capturas corresponden a
cambios manuales de su ubicación; no son evidencia de un cambio involuntario
causado por los marcadores. No se ha probado el envío de solicitudes.

## Abrir y ampliar la comprobación manual

Desde la raíz, en una terminal:

```powershell
.\venv\Scripts\python.exe manage.py runserver
```

En otra:

```powershell
cd frontend
npm run dev -- --host 127.0.0.1
```

Abrir `http://127.0.0.1:5173/resultados` (usar el puerto que indique Vite si cambia).
Las teselas de OpenStreetMap necesitan conexión a Internet.

La lista siguiente es una guía de comprobación ampliada; solo los casos de la
tabla anterior se consideran ejecutados. Permanecen pendientes las coordenadas
compartidas (también con el cliente), encuadre/exploración, limpieza al editar o
seleccionar ubicación, cancelación con respuestas tardías, carga y errores de
red/coordenadas del servidor, validaciones y envío de solicitudes, navegación
completa por teclado y comprobación global de desbordamiento horizontal.

1. Elegir un rubro y una ubicación cubierta por prestadores existentes. Buscar:
   deben coincidir cantidad de tarjetas y marcadores. Abrir un marcador y comprobar
   nombre, rubros, km y «Sin calificaciones»; la ubicación y las tarjetas no cambian.
2. Si ya existen prestadores con coordenadas iguales, abrir el punto con `+` y
   consultar todos, desplazando la ventana. No crear ni editar registros para esta
   prueba sin acordarlo antes; si no hay coincidencias, dejar el caso pendiente.
3. Mover/zoom del mapa; debe conservar la exploración hasta pulsar «Ver ubicación
   y resultados». Pulsar ese botón y comprobar que no se limpien los resultados.
4. Cambiar rubro, editar coordenadas o tocar un punto libre: desaparecen tarjetas y
   marcadores anteriores. En una conexión lenta, iniciar una búsqueda y cambiar
   inmediatamente rubro/ubicación: la respuesta anterior no debe reaparecer.
5. Buscar fuera de cobertura: mensaje sin resultados, sin marcadores de prestadores.
   Simular desconexión en DevTools y repetir: error recuperable, sin datos anteriores.
6. Revisar el formulario de solicitud en las tarjetas y sus validaciones sin enviarlo.
   El envío real queda pendiente: crea un registro y debe probarse con datos acordados.
7. En DevTools, probar anchos de 320 y 390 px y escritorio. Revisar mapa, ventana,
   formulario, tarjetas y navegación: sin barra horizontal; botones utilizables.

## Pendientes

Consulta pública a GitHub el 27/10/2026: PR [#14](https://github.com/RXDG2908/Chamba-PE/pull/14)
de T7.1 abierto, sin integrar (`state: open`, `merged: false`). La rama T7.2
hereda los commits `5c30dfb` (coordenadas de la API), `5ddaa14` (documentación
T7.1) y el merge `e42ae77` con main. Los cambios nuevos de T7.2 son JSX/CSS
para marcadores y ventanas, más documentación y pruebas manuales; no añaden
otro endpoint ni implementan el filtro administrativo.

Filtro Verificado en búsqueda/solicitudes a cargo de
David (T9.1), sin filtro adicional en el frontend; T7.3 y pruebas de integración.
Confirmar continuidad del contrato al integrar roles/disponibilidad. HU-12 deberá
definir el contrato de calificaciones antes de mostrar puntuaciones. Sin Firebase.

La entrega se separa en dos commits: implementación JSX/CSS y documentación
Markdown con pruebas manuales y dependencias. Su publicación no cierra HU-7 ni
Sprint 2; siguen pendientes revisión, integración y las comprobaciones indicadas.
