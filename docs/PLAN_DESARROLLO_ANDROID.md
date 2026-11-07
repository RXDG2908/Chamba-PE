# Plan de desarrollo Android — Chamba-PE

**Proyecto integrador · Tecsup · Diseño y Desarrollo de Software**

| Control del documento | Valor |
|---|---|
| Versión | 0.1 — propuesta para revisión |
| Fecha de elaboración | 07/11/2026; no constituye una fecha de inicio de implementación |
| Equipo revisor | David Valcarcel, Renzo León y Luis Abad |
| Estado | **PENDIENTE DE APROBACIÓN DEL EQUIPO** |
| Ubicación futura del proyecto | `mobile/android/` |
| Alcance de esta entrega | Planificación; no crea el proyecto Android ni implementa servicios |

> Este documento propone cómo desarrollar Android. No declara aprobados el reparto,
> el calendario, los servicios Firebase ni la solución de base de datos compartida.
> Tampoco autoriza integrar o publicar los avances locales de otras historias.

## 1. Objetivo y relación con la planificación vigente

Construir gradualmente una aplicación Android para **clientes y prestadores** que
consuma la API REST de usuarios en Spring Boot y aplique MVVM de manera verificable.
La administración permanece en React y Django. Los tres integrantes participarán
en desarrollo móvil, pruebas y revisión; Android no será responsabilidad exclusiva de Luis.

La primera entrega funcional propuesta es **consultar rubros y buscar prestadores**,
con una lista y coordenadas introducidas manualmente. Permite verificar arquitectura,
red y estados de pantalla antes de incorporar autenticación, GPS, mapas o notificaciones.

### 1.1. Separación respecto del Sprint 3

El acuerdo vigente de [SPRINTS.md](../SPRINTS.md) asigna 24 h de trabajo y 6 h de
reserva sobre 30 h de capacidad. Ese acuerdo comprende cierre heredado de HU-7/HU-8,
certificados, HU-19 y HU-21. Las cantidades describen la planificación aprobada,
**no la disponibilidad restante actual**. HU-13 y HU-20 quedaron postergadas en ese acuerdo.

Las tareas `AND-*` de este documento son propuestas adicionales: **no pertenecen
automáticamente al compromiso aprobado del Sprint 3**, no consumen su reserva y
no se asignan automáticamente al Sprint 4. El equipo debe estimarlas, desglosarlas
y decidir su incorporación en una planificación posterior o una replanificación explícita.
No se fijan fechas de entrega, horas ni story points oficiales para Android.

Las tablas originales del Lab 07 y README conservan planificación histórica. Para
determinar compromisos de sprint debe prevalecer el acuerdo posterior de SPRINTS,
sin convertir las maquetas o la descripción del producto en evidencia de implementación.

## 2. Revisión del repositorio y punto de partida

Se revisaron en modo lectura la estructura, modelos, rutas, contratos, historias y
documentación de las ramas/worktrees existentes. El corte remoto consultado es
`main` en `ecd633438719b88bba13fe54407dd7c2f3bcc177`; la referencia local
`origin/main` permanece en `1d80cb7`. No se realizó fetch ni integración para redactar este plan.

| Elemento | Situación observada | Consecuencia para Android |
|---|---|---|
| Django | Apps `usuarios`, `prestadores`, `servicios`, `certificaciones` y `administracion`; actualmente atiende también funciones de usuarios | El destino Spring exige una transición por contratos; no está realizada |
| React | Aplicación principal en `frontend/`; existe además un proyecto React en raíz | Se usan como referencia funcional, no como implementación Android |
| Persistencia | SQLite configurada actualmente en Django | No hay una base compartida Django/Spring ya aprobada o integrada |
| Android y Spring Boot | No se encontraron los proyectos correspondientes en la estructura revisada | Kotlin, Compose y la API Spring son trabajo futuro |
| HU-1 a HU-7 y HU-9 | Existen funciones Django/React; main incorpora correcciones de certificados, menú/rutas y HU-7 | Reutilizar reglas y contratos, sin declarar que existe un equivalente móvil nativo |
| HU-8 | Avance local parcial de FCM en `feature/hu8-notificaciones`; pendiente de cierre e integración | No equivale a notificaciones Android terminadas ni acredita su latencia |
| HU-19 | Código y contrato en #27/#28; #28 tiene como base la rama de #27 | Dependencia pendiente; no reimplementar OCR ni atribuir estos avances a Android |
| HU-21 | T21.1/T21.2 y correcciones validadas localmente en su worktree; sin publicación | Reutilizar su contrato una vez estabilizado y expuesto mediante la API acordada |
| HU-10/HU-11/HU-12 | Funciones previstas en el backlog posterior | Su inclusión móvil depende también del backend correspondiente |

La revisión y las pruebas previas de HU-21 no certifican Android, Spring ni una
combinación futura con main. La aplicación móvil no debe depender de cambios
sin publicar en el equipo de una sola persona para considerarse entregada.

### 2.1. Fuentes del proyecto

- [README](../README.md): propósito, alcance y arquitectura histórica.
- [PLAN_EQUIPO](../PLAN_EQUIPO.md): integrantes, capacidad, colaboración y definición de terminado.
- [SPRINTS](../SPRINTS.md): acuerdo vigente del Sprint 3 y backlog posterior.
- [Lab 07](Lab07_CPS_ChambaYa.md): 21 historias y criterios de aceptación.
- [API de autenticación Django](API_AUTENTICACION.md) y [API de búsqueda](API_BUSQUEDA.md): referencias actuales; no contratos Spring ya implementados.
- [Avance local HU-8](HU8_NOTIFICACIONES.md): alcance y límites del trabajo parcial de notificaciones.
- [PR #27](https://github.com/RXDG2908/Chamba-PE/pull/27) y [PR #28](https://github.com/RXDG2908/Chamba-PE/pull/28): dependencia de verificación de identidad.
- Documentación de consentimiento y eliminación del worktree `feature/hu21-consentimiento-eliminacion`, revisada sin copiarla ni modificarla en esta entrega.

README y PLAN_EQUIPO todavía describen móvil como «por definir» y backend Django.
La arquitectura de destino indicada por el equipo se documenta aquí; armonizar
después esos documentos requerirá una tarea autorizada distinta.

## 3. Arquitectura de destino

| Responsabilidad | Tecnología acordada / decisión |
|---|---|
| Aplicación móvil de usuarios | Kotlin, Jetpack Compose y Material Design 3 |
| Patrón obligatorio | MVVM, por indicación del profesor |
| Estado y asincronía | ViewModel, corrutinas, StateFlow y UiState |
| Consumo HTTP | Retrofit y DTO |
| Navegación | Navigation Compose |
| Backend REST de usuarios | Spring Boot; pendiente de implementación e integración |
| Administración | React y Django |
| Firebase | Propuesto por Renzo; servicios concretos pendientes de aprobación |
| Base compartida | Dirección propuesta, pendiente de aprobación definitiva, motor y gobierno de datos |

```text
Android (Kotlin / Compose / MVVM) ── REST ──> Spring Boot de usuarios
Web React de usuarios ───────────── REST ──> Spring Boot de usuarios
React de administración ─────────── REST ──> Django de administración

Django y Spring ──> persistencia compartida propuesta, aún por aprobar
Backend autorizado ──> FCM ──> Android, si el equipo aprueba ese servicio
```

Android no se conecta directamente a la base de datos. Spring valida identidad,
propiedad, permisos y reglas de negocio; ocultar botones en el móvil no sustituye
esa protección. Un adaptador temporal de Spring hacia Django debe identificarse
como transición: no demuestra por sí solo que la lógica de usuarios ya fue migrada.

### 3.1. Decisiones de datos e identidad previas a las escrituras

- Mantener una identidad y unos identificadores de usuario coherentes; no crear
  otro catálogo independiente de cuentas, contraseñas o roles en Firebase.
- Acordar el contrato de autenticación de Spring. Django utiliza hoy sesión y
  CSRF; no existe un JWT compartido que Android pueda dar por disponible.
- Definir caducidad, renovación, logout y revocación. Si se eligen cookies, deben
  conservarse las protecciones CSRF correspondientes; no desactivarlas para facilitar el móvil.
- Aprobar motor, esquema y responsables de migraciones/escrituras antes de
  compartir datos. PostgreSQL puede evaluarse como candidato; no es una decisión tomada.
- Auditar las señales Django: crear cuentas/prestadores/certificados tiene efectos
  que una escritura directa desde Spring no ejecuta automáticamente.
- No permitir que ambos backends creen o alteren las mismas tablas de forma
  independiente. La transición necesita un responsable de esquema y una matriz de escrituras.
- Definir la propiedad de solicitudes: el modelo actual conserva contacto del
  cliente sin FK a su cuenta. Esa deuda no debe trasladarse sin revisión al flujo privado móvil.

Estas decisiones son dependencias del backend; este plan no implementa cambios
en Django, Spring, autenticación ni persistencia.

## 4. MVVM y organización de paquetes

Se conservará `feature/busqueda/data`, `domain` y `presentation` dentro de un único
módulo `app` inicial. No hace falta convertir cada paquete en un módulo Gradle.

| Componente | Ubicación | Responsabilidad y límites |
|---|---|---|
| View | `presentation/BusquedaRoute.kt`, `BusquedaScreen.kt`, `components/` | Compose representa UiState y entrega acciones. No llama a Retrofit ni contiene reglas del servidor |
| ViewModel | `presentation/BusquedaViewModel.kt` | Valida entradas de pantalla, coordina corrutinas, cancelación y reintento; publica estado inmutable |
| Estado de la View | `presentation/BusquedaUiState.kt` | Describe filtros, validaciones y resultado visible; no es el modelo persistente del negocio |
| Model | `domain/model/` y `data/` | Modelos propios, errores y acceso a fuentes de datos; no debe confundirse Model únicamente con DTO |
| Contrato Repository | `domain/repository/BusquedaRepository.kt` | Operaciones independientes de Android y Retrofit |
| Implementación Repository | `data/repository/BusquedaRepositoryImpl.kt` | Consulta la fuente remota, transforma DTO y traduce errores |
| API y DTO | `data/remote/` | Contrato HTTP de Spring y representación de sus respuestas |
| Mappers | `data/mapper/` | Conversión explícita entre DTO y modelos usados por la aplicación |

`domain` organiza parte del Model; no es un cuarto componente obligatorio de MVVM.
Inicialmente contendrá modelos y el contrato del Repository. Se añadirán casos
de uso cuando exista lógica compleja o reutilizable, evitando clases que solo
repitan una llamada. El ViewModel dependerá del contrato, no de la implementación de Retrofit.

### 4.1. Estructura propuesta de búsqueda

El siguiente árbol es una especificación: **no representa carpetas ya creadas**.
El namespace `pe.tecsup.chambape` es una propuesta que debe confirmarse.

```text
mobile/android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/values/strings.xml
        │   └── java/pe/tecsup/chambape/
        │       ├── MainActivity.kt
        │       ├── ChambaPeApplication.kt
        │       ├── navigation/
        │       │   ├── AppNavHost.kt
        │       │   └── AppDestination.kt
        │       ├── di/AppContainer.kt
        │       ├── core/
        │       │   ├── network/RetrofitFactory.kt
        │       │   └── designsystem/
        │       │       ├── theme/
        │       │       └── components/
        │       └── feature/busqueda/
        │           ├── data/
        │           │   ├── remote/BusquedaApi.kt
        │           │   ├── remote/dto/{RubroDto,PrestadorDto}.kt
        │           │   ├── mapper/BusquedaMapper.kt
        │           │   └── repository/BusquedaRepositoryImpl.kt
        │           ├── domain/
        │           │   ├── model/{Rubro,PrestadorResumen,FiltroBusqueda,BusquedaError}.kt
        │           │   └── repository/BusquedaRepository.kt
        │           └── presentation/
        │               ├── BusquedaRoute.kt
        │               ├── BusquedaScreen.kt
        │               ├── BusquedaViewModel.kt
        │               ├── BusquedaViewModelFactory.kt
        │               ├── BusquedaUiState.kt
        │               └── components/{FiltrosBusqueda,PrestadorCard}.kt
        ├── test/java/pe/tecsup/chambape/
        │   ├── testing/MainDispatcherRule.kt
        │   └── feature/busqueda/
        │       ├── presentation/BusquedaViewModelTest.kt
        │       ├── data/{BusquedaRepositoryImplTest,BusquedaMapperTest}.kt
        │       └── fake/{FakeBusquedaRepository,FakeBusquedaApi}.kt
        └── androidTest/java/pe/tecsup/chambape/
            ├── feature/busqueda/BusquedaScreenTest.kt
            └── navigation/AppNavigationTest.kt
```

Las llaves agrupan nombres de archivos para abreviar el árbol. Nuevas funcionalidades
seguirán el mismo criterio por feature cuando sean aprobadas; no se crearán carpetas
vacías de todo el backlog desde la primera entrega.

### 4.2. Flujo, estado y navegación

```text
Acción de usuario → ViewModel → Repository → Retrofit → API Spring
Respuesta → DTO → mapper → modelo → UiState → View Compose
```

- El ViewModel usa `viewModelScope`; mantiene `MutableStateFlow` privado y expone
  `StateFlow<BusquedaUiState>` de solo lectura. La View observa con `collectAsStateWithLifecycle()`.
- El Repository expone operaciones `suspend`. Las corrutinas no bloquean el hilo
  principal y `CancellationException` se propaga, sin convertirla en error de pantalla.
- Una búsqueda nueva cancela o invalida la anterior. Una respuesta antigua no
  puede reemplazar filtros o resultados recientes.
- Se modelan estados independientes para rubros y resultados: `Idle`, `Loading`,
  `Success`, `Empty` y `Error`, conservando los filtros necesarios para reintentar.
- La carga no se interpreta como lista vacía. Los fallos HTTP, conexión y conversión
  muestran mensajes comprensibles, sin trazas o información interna.
- `BusquedaRoute` conecta estado y callbacks; `BusquedaScreen` puede probarse sin
  ViewModel real. El ViewModel no recibe Activity, Composable ni NavController.
- Navigation Compose vive en la capa de interfaz. Se navega con identificadores
  mínimos, no con documentos, credenciales o fotografías en los argumentos.
- La recreación conserva filtros mediante estado apropiado y, cuando corresponda,
  `SavedStateHandle`. Los resultados pueden recargarse; StateFlow no sustituye persistencia tras muerte del proceso.

### 4.3. Diseño consistente y adaptable

Material Design 3 definirá colores, tipografía, espaciado y componentes comunes.
Los textos estarán en recursos. Se comprobarán contraste, etiquetas accesibles,
tamaño táctil, escalado de fuente, foco y anuncios de carga/error.

En teléfonos compactos los filtros irán sobre la lista; en pantallas amplias se
evaluará una distribución en paneles. No se fijarán tamaños que impidan rotación,
teclado o texto ampliado. Se revisarán temas claro/oscuro, márgenes del sistema,
listas desplazables y estados deshabilitados, sin declarar accesibilidad completa solo por pasar pruebas automáticas.

## 5. Funcionalidades Android y trazabilidad con las HU

Todas las filas siguientes describen **trabajo móvil propuesto**.

| Historias | Funcionalidad prevista en Android | Condición para desarrollarla |
|---|---|---|
| HU-3 + catálogo asociado a HU-1 | Consultar rubros, buscar por ubicación y presentar resultados | Primera funcionalidad; contrato de lectura Spring y datos ficticios |
| HU-5 | Registro, login, usuario actual, logout y recuperación ante sesión vencida | Autoridad de identidad y contrato Spring aprobados |
| HU-1 | Registro del prestador, rubros y cobertura | Unicidad de cuenta/DNI y estado pendiente garantizados por backend |
| HU-2 | Selección y carga de certificados PDF/JPG hasta 5 MB | Propiedad, permisos, validación real y carga multipart en Spring acordadas |
| HU-4 | Enviar/consultar solicitud; bandeja propia y aceptar/rechazar | Propiedad del cliente, concurrencia e idempotencia definidas |
| HU-6 | Consultar/cambiar disponibilidad del prestador | Sesión, rol y reglas autoritativas de backend |
| HU-7 | Mapa y ubicación del cliente; marcadores y detalle público | Proveedor de mapas y permisos aprobados; conservar filtro y precisión pública de main |
| HU-8 | Notificaciones y acceso a una solicitud propia | FCM aprobado; identidad, tokens y emisor único acordados |
| HU-21 | Consentimiento versionado, revocación y solicitud/consulta/reintento de eliminación | Contrato estable, barreras de captura y operación del procesador definidos |
| HU-19 | Captura de anverso/reverso y consulta del resultado | Después de las protecciones HU-21; procesamiento y revisión siguen en backend |
| HU-9 | Mostrar al prestador su estado de revisión | Solo consulta propia; el panel de aprobación permanece en React/Django |
| HU-13 | Manejar el rechazo de una cuenta suspendida/bloqueada | Backend de moderación disponible; no construir administración dentro de Android |
| HU-10/HU-11/HU-12 | Recomendación, seguimiento y calificación | Backends y criterios aprobados en su planificación; fuera de la primera entrega |
| HU-20 | Selfie/prueba de vida y consumo de comparación facial | Historia postergada; alcance, consentimiento específico y decisiones técnicas adicionales |
| HU-14/HU-15 | Alternativos y chatbot | Backlog futuro, sin compromiso móvil actual |
| HU-16/HU-17/HU-18 | Consumo de efectos de configuración cuando corresponda | Gestión de rubros/comisiones, parámetros y métricas continúa siendo administrativa |

El registro completo del prestador y certificados se desglosará en tarjetas nuevas
antes de comprometerlos; no queda implícitamente incluido en la tarea de login.
Pagos y un historial ampliado aparecen en la descripción del producto, pero no se
incorporan a este MVP sin historia, contrato y aprobación específicos.

No se inventarán calificaciones, ETA ni ubicación en tiempo real. Mientras HU-12
no esté disponible, el tratamiento de «Sin calificaciones» deberá acordarse con el PO.
El centro de cobertura del prestador no equivale a su posición actual.

## 6. Primera entrega: rubros y búsqueda

**Recorrido aceptable:** abrir búsqueda → cargar rubros → elegir rubro → introducir
coordenadas → buscar → ver resultados reales de Spring, vacío o error recuperable.

| Contrato de referencia actual en Django | Propuesta para Spring, todavía por aprobar |
|---|---|
| `GET /api/prestadores/rubros/` | Mantener un recurso de catálogo con ID y nombre; ruta final/versionado por definir |
| `GET /api/prestadores/buscar/?rubro=…&latitud=…&longitud=…` | Conservar semántica, tipos, errores y privacidad; no asumir que esa ruta ya existe en Spring |

El contrato debe especificar identificadores, números JSON, nulabilidad, códigos
HTTP, errores por campo y comportamiento sin resultados. El cliente no fijará
IDs de rubros en el código ni decidirá quién está verificado.

Criterios de aceptación de esta primera entrega:

1. Solo se consultan datos: no se crean cuentas, solicitudes ni notificaciones.
2. Se validan rubro y coordenadas finitas dentro de sus rangos. La primera versión
   funciona sin permiso GPS, cámara ni Firebase.
3. El backend decide disponibilidad, verificación, rubro, cobertura inclusiva y
   orden por distancia. Android conserva el orden recibido y no expone datos privados.
4. La salida pública respeta la precisión acordada con main; los cálculos del
   servidor conservan su precisión interna. No se recalcula cobertura a partir del marcador redondeado.
5. Rubros y resultados manejan carga, éxito, vacío, errores y reintento sin perder
   filtros. Una búsqueda antigua no sustituye una nueva.
6. ViewModel, Repository y pantalla tienen pruebas. La integración real se
   demuestra desde emulador y al menos un dispositivo físico con la API Spring.
7. El criterio HU-3 de respuesta menor de 3 s se mide con datos y condiciones
   registrados, distinguiendo ejecución del servidor de latencia total del móvil.

Los mocks permiten desarrollar en paralelo, pero **no sustituyen la aceptación de
Android → Spring real**. Si Spring está pendiente, se registra como dependencia,
sin presentar el recorrido simulado como funcionalidad integrada.

## 7. Fases y dependencias

| Fase propuesta | Resultado | Condición para avanzar |
|---|---|---|
| F0. Acuerdos | Arquitectura, identidad, alcance, responsables, contratos y versiones compatibles | Checklist del equipo aprobado; sin consumir reservas automáticamente |
| F1. Base y lectura | Configuración Android, diseño, navegación y búsqueda MVVM con fuentes simuladas y pruebas | AND-01 a AND-09; no requiere Firebase |
| F2. Primera integración real | Rubros/búsqueda contra Spring en dispositivos | API de lectura disponible; AND-16/AND-17 y criterios de la sección 6 |
| F3. Identidad y operaciones | Sesión, navegación privada, primer flujo de solicitud y disponibilidad | Contrato de identidad, propiedad y escrituras aprobado; AND-10 a AND-13 |
| F4. Mapa y ampliaciones básicas | Ubicación/mapa; posterior desglose de registro/certificados | Decisiones de permisos/proveedor y contratos multipart; AND-14 |
| F5. Notificaciones | FCM Android, tokens y consulta privada de solicitud | Selección Firebase, backend emisor único e identidad estables; AND-15/AND-18 |
| F6. Privacidad e identidad documental | Consentimiento/eliminación primero; captura DNI después | Contratos HU-19/HU-21 estabilizados; AND-19 a AND-21 |
| F7. Evolución | Recomendación, seguimiento, calificación y demás alcance aprobado | Nueva planificación y APIs disponibles; sin promesas de fechas |

Dependencias externas a Android que deberán resolverse mediante tareas propias:

- **API de lectura:** Spring y datos de prueba; bloquea el cierre de F2.
- **Identidad y datos:** autoridad única, permisos y gobierno de persistencia;
  bloquean operaciones privadas y de escritura.
- **Notificaciones:** contrato de tokens, emisor único y configuración autorizada;
  bloquean F5, no F1/F2.
- **Privacidad:** estabilización de HU-19/HU-21, texto y atención de eliminaciones;
  bloquea F6. Un resultado OCR validado no aprueba automáticamente al prestador.

La API de usuario de Spring podrá delegar temporalmente OCR y eliminación al
servicio responsable existente si el equipo lo aprueba. No se trasladarán esas
reglas a Kotlin ni se mantendrán dos procesos de borrado independientes.

## 8. Reparto propuesto entre David, Renzo y Luis

**TODAS LAS ASIGNACIONES ESTÁN PENDIENTES DE APROBACIÓN DEL EQUIPO.**

Los identificadores AND son etiquetas nuevas de planificación, no reemplazos de
T19/T21 ni tareas ya terminadas. Se proponen siete tarjetas por integrante para
repartir frentes técnicos y aprendizaje; el número de tarjetas no equivale a horas.
El equipo debe estimar y dividir las que resulten grandes antes de comprometerlas.

Revisión cruzada propuesta: **David revisa a Renzo; Luis revisa a David; Renzo
revisa a Luis**. La revisión se adapta si afecta una especialidad compartida,
manteniendo al menos un revisor distinto del autor.

### 8.1. Base y primera funcionalidad

| ID | Responsable propuesto | Descripción | Dependencias | Resultado esperado | Criterios de aceptación |
|---|---|---|---|---|---|
| AND-01 | Renzo | Preparar proyecto Kotlin/Compose y catálogo de versiones | F0; confirmar JDK, SDK y herramientas compatibles | Base compilable en `mobile/android/` | Compilación reproducible y arranque; sin secretos ni cambios en otros proyectos |
| AND-02 | David | Crear tema Material 3 y componentes comunes de carga/vacío/error | AND-01; identidad visual acordada | Sistema visual reutilizable | Claro/oscuro, texto ampliado y pantalla compacta/amplia revisados; componentes con semántica accesible |
| AND-03 | Luis | Preparar Navigation Compose y conexión de destinos iniciales | AND-01; rutas acordadas | Navegación de búsqueda con callbacks | Volver funciona; sin llamadas HTTP en navegación ni NavController en ViewModel |
| AND-04 | David | Definir Retrofit, DTO y mappers de rubros/búsqueda | Contrato de lectura aprobado; AND-01 | Cliente HTTP desacoplado y configuración por entorno | Campos/tipos coinciden; errores HTTP/conversión controlados; mapper probado; sin URL de producción o credenciales incrustadas |
| AND-05 | Renzo | Implementar modelos, contrato e implementación de Repository de búsqueda | AND-04 | Acceso a datos sustituible en pruebas | API encapsulada; devuelve modelos propios; vacío y cancelación diferenciados de error |
| AND-06 | Luis | Construir BusquedaViewModel y UiState | AND-05 | Coordinación de filtros y resultados | StateFlow público inmutable; corrutinas; validación, reintento y cancelación de búsqueda anterior |
| AND-07 | David | Construir filtros y lista Compose de búsqueda | AND-02/AND-03/AND-06 | Pantalla usable sin GPS | Presenta todos los estados; conserva orden, filtros y datos públicos; sin Retrofit en Composable |
| AND-08 | Renzo | Añadir pruebas unitarias de Repository y contrato HTTP | AND-04/AND-05 | Casos aislados de datos/red | Respuestas válidas, vacías, inválidas, HTTP y cancelación; MockWebServer o equivalente, sin servidor real |
| AND-09 | Luis | Añadir pruebas unitarias de ViewModel | AND-06 | Regresiones del estado MVVM | Carga→éxito/vacío/error; entrada inválida no llama Repository; reintento y respuesta tardía probados sin sleeps |

### 8.2. Operaciones de usuarios y mapa

| ID | Responsable propuesto | Descripción | Dependencias | Resultado esperado | Criterios de aceptación |
|---|---|---|---|---|---|
| AND-10 | Renzo | Preparar Repository/adaptador de identidad y sesión Android | Identidad Spring aprobada; F2 | Acceso único al estado autenticado | Login, usuario actual, caducidad y logout comprobados; no duplica usuarios en Firebase; sin persistir contraseñas |
| AND-11 | Luis | Construir ViewModel/pantalla de acceso y navegación por rol | AND-03/AND-10 | Acceso a funciones privadas | Error y sesión vencida recuperables; cambio de cuenta limpia estado; backend decide permisos |
| AND-12 | David | Implementar primer recorrido móvil de crear/consultar solicitud | AND-11; contrato HU-4 y propiedad definidos | Solicitud y estado real desde la API | Dueño correcto; validaciones y doble envío controlados; no anuncia aceptación ni push por recibir 202/201 |
| AND-13 | Luis | Implementar disponibilidad del prestador con MVVM | AND-11; contrato HU-6 | Consulta/cambio confirmado por servidor | Otros roles no acceden; error no deja estado ficticio; efecto en búsqueda verificado |
| AND-14 | David | Añadir mapa y obtención opcional de ubicación al recorrido de búsqueda | F2; proveedor/licencia/permisos aprobados | Marcadores coherentes con resultados | Permiso denegado conserva alternativa manual; cobertura y precisión no cambian; no inventa calificaciones |

La bandeja y aceptar/rechazar del prestador, el registro completo y certificados
se dividirán en tarjetas específicas al aprobar F3/F4; no se consideran entregados
por completar AND-11 o AND-12.

### 8.3. Verificación, Firebase y privacidad

| ID | Responsable propuesto | Descripción | Dependencias | Resultado esperado | Criterios de aceptación |
|---|---|---|---|---|---|
| AND-15 | Renzo | Incorporar cliente FCM y registro/rotación de token Android | Firebase aprobado; AND-10/AND-11; contrato backend de dispositivos | Canal Android separado del worker web | Permisos explícitos; token asociado por backend a usuario actual; logout/cambio de cuenta y rotación probados |
| AND-16 | David | Añadir pruebas Compose y de navegación del primer recorrido | AND-02/AND-03/AND-07 | Pruebas de interfaz repetibles | Carga, éxito, vacío, error, reintento, selección y volver; tamaños/fuente/orientación revisados |
| AND-17 | Luis | Verificar primera integración REST en emulador y dispositivo | AND-04 a AND-09; Spring de lectura disponible | Evidencia Android→Spring con datos ficticios | Contrato y errores reales; conectividad y tiempos registrados; lista no depende de mocks |
| AND-18 | Renzo | Verificar recepción y apertura segura desde notificaciones | AND-12/AND-15; bandeja/detalle privado disponibles | Matriz de pruebas FCM Android | Primer/segundo plano, permisos, duplicados, token inválido, logout y usuario ajeno; recepción real separada de aceptación del proveedor |
| AND-19 | David | Añadir consentimiento DNI versionado y revocación en MVVM | F6; identidad y contrato HU-21 | Aceptación explícita y consulta propia | Versión/texto del servidor; sin aceptación implícita; error 503 recuperable; alcance no autoriza reconocimiento facial |
| AND-20 | Luis | Añadir solicitud/historial/reintento de eliminación con MVVM | F6; contrato y operación HU-21; AND-19 | Flujo móvil de eliminación comprobable | Cinco estados; idempotencia; respuestas antiguas no sustituyen escrituras; cuenta/DNI registrado conservados; no confunde pendiente con atendida |
| AND-21 | Renzo | Añadir captura anverso/reverso y consumo del resultado de HU-19 | AND-19/AND-20; contratos estables y permiso de cámara | Captura móvil con procesamiento en backend | Consentimiento vigente y barrera explícitos antes de capturar/enviar; rechazo desconocido bloquea; sin fotos en logs/galería automática; fallo de fuente pasa a revisión |

| Integrante | Tarjetas propuestas | Participación móvil principal |
|---|---|---|
| David | 02, 04, 07, 12, 14, 16, 19 | Diseño, red/DTO, pantallas, mapa, consentimiento y pruebas Compose |
| Renzo | 01, 05, 08, 10, 15, 18, 21 | Base, Repository, identidad, FCM, cámara y pruebas |
| Luis | 03, 06, 09, 11, 13, 17, 20 | Navegación, ViewModels, acceso, disponibilidad, integración y eliminación |

Las pruebas en dispositivo físico serán cruzadas y compartidas: cada integrante
debe ejecutar y documentar al menos un recorrido construido por otro. Las nuevas
historias móviles se rotarán para que los tres continúen participando en interfaz y datos.

## 9. Decisión de servicios Firebase

Firebase fue propuesto por Renzo. Su adopción concreta se decidirá con David y Luis.
**No se configura ningún servicio ni se generan credenciales con este documento.**

| Servicio | Evaluación propuesta | Decisión pendiente |
|---|---|---|
| Cloud Messaging (FCM) | Candidato principal para HU-8; aprovechar contratos y aprendizajes del avance web sin copiar su worker a Android | Proyecto/ambientes autorizados, responsable, emisor backend y contrato de tokens |
| Authentication | No incorporarlo por defecto; añade otra autoridad si no se diseña una integración de identidad | Elegir autoridad única. Si se adopta, definir UID↔ID existente, verificación de tokens, roles y migración; sin segundo registro paralelo |
| Firestore / Realtime Database | No necesarios para la primera búsqueda REST | No duplicar perfiles, solicitudes o consentimientos; justificar cualquier nuevo caso antes de aprobarlos |
| Cloud Storage | No necesario inicialmente; ya existe almacenamiento privado de documentos en backend | Solo evaluar con política de acceso, retención y eliminación coherente con HU-21 |
| Crashlytics / Performance Monitoring | Opcionales para diagnóstico después de estabilizar el recorrido | Beneficio, privacidad, datos recolectados, costos y mantenimiento |
| Analytics y otros | Fuera del alcance inicial | Exigir propósito y aprobación; no habilitar por conveniencia del asistente de configuración |

### 9.1. Condiciones para FCM

- Usar FCM no implica elegir Firebase Authentication. El token de dispositivo no
  prueba identidad ni sustituye una sesión/token de acceso de Spring.
- El backend registra el dispositivo para el usuario autenticado, verifica propiedad
  y maneja rotación, revocación y cambio de cuenta. El contrato web actual basado en
  sesión Django no debe suponerse compatible sin cambios con Android/Spring.
- Acordar un solo emisor por evento. La convivencia Django/Spring no puede enviar
  dos notificaciones por la misma solicitud ni omitir el aviso al cambiar el escritor.
- El mensaje llevará identificadores mínimos. Android consultará el detalle privado
  por REST antes de mostrar datos o ejecutar aceptar/rechazar; el payload no concede permisos.
- Pedir permiso de notificaciones cuando corresponda; cubrir denegación y ofrecer
  bandeja. Probar canales, primer/segundo plano y restricciones de cada versión Android.
- Medir el criterio HU-8 de menos de 5 s mediante recepción real repetida, con
  condiciones, pérdidas y duplicados registrados. La aceptación del envío por FCM
  y una consulta rápida de bandeja no acreditan entrega al dispositivo.
- No prometer recepción universal después de cierre forzado o restricciones del
  sistema. Documentar los escenarios realmente verificados.
- La configuración cliente se gestionará según la política acordada. Claves privadas
  de servidor, cuentas de servicio y credenciales de firma nunca irán dentro del APK
  ni en commits; no registrar tokens, DNI o contenido privado en logs.

## 10. Estrategia de pruebas y definición de aceptación

| Nivel | Casos mínimos | Evidencia requerida |
|---|---|---|
| ViewModel | Idle/carga/éxito/vacío/error, validación, reintento, cancelación y respuestas obsoletas | Pruebas unitarias con Repository falso y `kotlinx-coroutines-test`; sin sleeps para imponer orden |
| Repository y DTO | Parámetros, mapeo, nulabilidad, HTTP, JSON inválido, red, vacío y propagación de cancelación | Fuente falsa y pruebas de contrato con servidor HTTP simulado |
| Compose | Representación de UiState, callbacks, botones, accesibilidad semántica y navegación | Pruebas instrumentadas sin depender de API real para cada estado |
| API Spring | Contrato, roles, propiedad, sesión vencida, errores, concurrencia e idempotencia en escrituras | Pruebas integradas con usuarios/datos ficticios y entorno de prueba |
| Emulador | Instalación, arranque, rotación, volver, muerte/recreación, teclado y red | Modelo de emulador y versión Android registrados |
| Dispositivo físico | Red real, fuente ampliada, permisos y ciclo de vida; cámara/FCM cuando entren al alcance | Dispositivo y versión identificados; revisión por otro integrante |
| Privacidad | Consentimiento/barrera, limpieza de imágenes temporales, cierre de sesión y eliminación | Comprobar cuenta conservada y datos del alcance eliminados; no solo mensaje de éxito |
| Regresión existente | Django/React y funcionalidades afectadas al incorporar cambios de backend | Suites pertinentes en entorno aislado, sin alterar pruebas o evidencias de HU-7/HU-8/HU-19/HU-21 |

Para desarrollo local se diferenciará el host del emulador del dispositivo físico:
el localhost del teléfono no es el servidor del equipo. La URL será configurable;
se preferirá HTTPS y cualquier excepción HTTP de desarrollo quedará limitada a debug.

No se usarán datos biométricos reales en pruebas automáticas, capturas, repositorio
o reportes. Un mock no cuenta como integración real, y un emulador no sustituye la
prueba física de cámara, permisos o notificaciones. No se aumentarán timeouts ni
se quitarán aserciones para presentar aprobaciones.

Una entrega se acepta cuando cumple su HU y sus criterios específicos, respeta
MVVM, tiene revisión cruzada y pruebas pertinentes, y su integración autorizada
cumple la definición de terminado del equipo. Compilar o mostrar una pantalla
simulada no equivale a cerrar la historia.

## 11. Git, colaboración y conservación de avances

El flujo siguiente es una propuesta para cuando se apruebe comenzar; no se ejecuta ahora.

1. Verificar la base remota y acordar una rama base estable. No iniciar Android
   desde un árbol sucio de HU-8/HU-19/HU-21 ni reutilizar sus ramas como espacio móvil.
2. Usar ramas independientes por tarea, por ejemplo `feature/android/and-01-base`
   o `feature/android/and-06-busqueda-viewmodel`. Los nombres son ejemplos, no ramas creadas.
3. Mantener commits pequeños con identificador y comportamiento verificable;
   separar cambios Android de cambios Spring o migraciones del backend.
4. Abrir PR revisables con alcance, dependencia de API, criterios, pruebas y límites.
   No publicar directo en main; aplicar revisión cruzada antes de integrar.
5. Si una tarea depende de otra rama, indicar la dependencia y acordar el orden;
   no copiar commits de Renzo ni presentar cambios heredados como trabajo nuevo.
6. Coordinar ediciones compartidas de Gradle, catálogo de versiones, navegación y
   tema. La propiedad temporal de un archivo ayuda a evitar conflictos, sin excluir revisiones.
7. Conservar todos los cambios locales/untracked, documentación, respaldos y
   evidencias antes de cualquier operación futura de integración. No usar resets,
   limpiezas o sustituciones completas de directorios para resolver conflictos.
8. Una vez autorizado el desarrollo, actualizar AVANCES/SPRINTS según las reglas
   del equipo. **Esta entrega solo crea este plan**, por lo que no modifica esos documentos.

HU-8 permanece como avance separado; #27/#28 mantienen su cadena de dependencia;
HU-21 conserva sus cambios locales y método de borrado. La planificación Android
no los integra ni los declara terminados. Un cambio necesario en sus contratos
se propondrá en una tarea separada y con revisión de sus responsables.

## 12. Riesgos y decisiones pendientes

| Riesgo / decisión | Efecto | Tratamiento propuesto |
|---|---|---|
| Capacidad y alcance | Añadir Android/Spring podría desplazar el compromiso S3 | Estimar después de aprobar; no consumir reservas ni inventar fechas |
| API Spring inexistente | Pantallas funcionales solo contra mocks | Contratos y primera API de lectura antes de aceptar integración |
| Identidad duplicada | Usuarios/roles/sesiones incoherentes | Resolver autoridad única antes de autenticación y FCM privados |
| Datos compartidos | Migraciones incompatibles y señales Django omitidas | Aprobar esquema, escritor por operación y pruebas de transición |
| Ramas y cambios locales | Pérdida de avances o integración accidental | Preservar respaldos y usar tareas/ramas independientes autorizadas |
| Versiones Android | Incompatibilidad de Kotlin, Compose, Gradle, JDK y SDK | Matriz compatible y versiones fijadas al preparar AND-01 |
| GPS y mapas | Permiso denegado, costos/licencias y diferencias de precisión | Coordenadas manuales primero; proveedor y políticas antes de AND-14 |
| FCM parcial | Avisos duplicados, destinatario incorrecto o latencia no demostrada | Un emisor, registro autenticado y matriz de recepción real |
| Privacidad HU-19/HU-21 | Captura sin permiso, copias residuales o falsa atención | Barreras explícitas, contratos reutilizados y pruebas de archivos/backend |
| Eliminaciones manuales | Solicitudes pendientes bloquean capturas indefinidamente | Acordar operador, ventanas de atención, seguimiento y recuperación antes de aceptar F6 |
| Escalado visual | Pantallas utilizables solo en un equipo | Compose adaptable, fuente ampliada y prueba cruzada física |
| Backend futuro de HU-10/11/12/20 | Promesas móviles sin soporte real | Planificación posterior y criterios propios; no incluirlos por arrastre |

Para el procesamiento manual de HU-21 se propone discutir a Luis como operador
titular, David como suplente y Renzo como apoyo de HU-19, sin darlos por asignados.
Durante demostraciones podrían revisarse pendientes cada 15–30 minutos y al
inicio/cierre de la ventana acordada; no se promete operación continua.
`--recuperar` atiende ejecuciones interrumpidas en procesando, no reemplaza el
reintento de parcial/fallida. Debe comprobarse el estado final, no solo el código
de salida del comando. Android muestra el estado del servidor y no decide que
una eliminación está atendida por haber registrado su solicitud.

## 13. Checklist de aprobación del equipo

- [ ] David, Renzo y Luis revisaron el plan y acordaron sus cambios.
- [ ] Se confirmó MVVM, Compose/Material 3 y ubicación `mobile/android/`.
- [ ] Se aprobó el primer alcance de rubros/búsqueda y sus criterios de aceptación.
- [ ] Se asignaron y estimaron tareas sin alterar implícitamente Sprint 3 o Sprint 4.
- [ ] Se confirmó el reparto y la revisión cruzada; los tres participan en Android.
- [ ] Se acordaron namespace/applicationId, versiones compatibles y dispositivos disponibles.
- [ ] Se definió contrato, responsable y entorno de la primera API Spring.
- [ ] Se resolvió la estrategia de identidad, permisos y relación con cuentas existentes.
- [ ] Se aprobó o descartó la base compartida y se definió su gobierno si se adopta.
- [ ] Se eligieron expresamente los servicios Firebase y su responsable; FCM no implica Auth.
- [ ] Se confirmó que no habrá duplicación de usuarios, perfiles o datos de negocio.
- [ ] Se acordaron privacidad, almacenamiento y operación de HU-19/HU-21 antes de F6.
- [ ] Se acordaron el flujo Git, la conservación de avances y los límites de cada PR.
- [ ] Se aprobaron la matriz de pruebas y el criterio de evidencia en dispositivo físico.
- [ ] El equipo autorizó expresamente iniciar una primera tarea de implementación.

| Integrante | Decisión | Observaciones |
|---|---|---|
| David Valcarcel | Pendiente | Por registrar |
| Renzo León | Pendiente | Por registrar |
| Luis Abad | Pendiente | Por registrar |

Hasta completar esa revisión, el documento es una propuesta. Su creación no
autoriza configurar Firebase, generar credenciales, programar Android/Spring,
integrar ramas ni publicar cambios.

## 14. Referencias técnicas

Documentación oficial consultada para fundamentar separación de capas, observación
del estado, navegación y opciones Firebase; no fija versiones o servicios por el equipo:

- [Android: recomendaciones de arquitectura](https://developer.android.com/topic/architecture/recommendations).
- [Android: navegación](https://developer.android.com/guide/navigation), conservando Navigation Compose como requisito acordado.
- [Firebase: inicio de FCM en Android](https://firebase.google.com/docs/cloud-messaging/android/get-started).
- [Firebase: integración con un sistema de autenticación propio](https://firebase.google.com/docs/auth/android/custom-auth), alternativa a evaluar solo si se aprueba Authentication; no está implementada.
