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
T3.2 está implementada en `hu-3-busqueda`, pendiente de revisión visual completa y PR según
la definición de terminado del equipo. HU-3 continúa parcial; el ordenamiento
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

Pendiente en navegador:

- Revisar mapa, marcador, tarjetas y navegación en escritorio y celular,
  incluidos anchos pequeños y selección táctil. Las teselas de OpenStreetMap
  necesitan conexión a Internet.
- Probar Buscar sin rubro o ubicación, y una búsqueda sin coincidencias
  (por ejemplo, Gasfitería en `0, 0`).
- Simular fallo de red y reintentar; comprobar los estados de carga y error.
- Con red lenta, lanzar búsquedas consecutivas y cambiar rubro o ubicación
  durante la espera; confirmar que no reaparecen resultados anteriores.
- Volver a Registro y revisar que el formulario de Renzo siga funcionando.
