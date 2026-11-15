# Chamba PE — Android

App Android de Chamba PE para clientes y prestadores. Kotlin, Jetpack Compose y Material 3,
con un único módulo `app` y arquitectura MVVM.

> **Estado:** primera versión. Compila, pasa las pruebas unitarias y los flujos del **cliente** y
> del **prestador** se probaron en un emulador (API 36) contra Django local y el proyecto Firebase
> real. **No se probó** en un teléfono físico ni la lectura real de la ubicación (el emulador no la
> entregó). El enfoque (API Django + Firebase, en lugar del backend Spring del
> [plan Android](../../docs/PLAN_DESARROLLO_ANDROID.md)) está **pendiente de acuerdo con el equipo**.

## Qué hace

| Función | Cómo | Tema del curso de Móviles |
|---|---|---|
| Ingreso y registro de clientes | Firebase Authentication (correo y contraseña) | Semana 11 |
| Roles cliente / prestador | Perfil en Firestore `usuarios/{uid}`; el rol decide las pestañas | Semana 11 |
| Rubros y búsqueda de prestadores | Retrofit contra la API Django (`/api/prestadores/rubros/` y `/buscar/`) | Semanas 9 y 10 |
| Datos guardados y modo sin conexión | Room: última búsqueda por rubro y rubros | Semana 8 |
| Favoritos | Room; se conservan al cerrar la app y al volver a buscar | Semana 8 |
| Mapa interactivo: tocar para elegir la ubicación, marcadores numerados de prestadores con su ficha y «Ver todo» | OpenStreetMap con osmdroid (sin clave de API), equivalente al Leaflet de la web | Semanas 11 y 12 |
| «Usar mi ubicación» | Permiso de ubicación aproximada + Fused Location | Semana 11 |
| Enviar solicitud y ver su estado en vivo | Firestore `solicitudes/{id}` | Semana 11 |
| Bandeja del prestador: aceptar / rechazar | Firestore, filtrado por `prestadorId` | Semana 11 |
| Aviso «Solicitud enviada» | Notificación local con canal y permiso `POST_NOTIFICATIONS` | Semana 12 |
| Navegación y pestañas por rol | Navigation Compose + `NavigationBar` | Semanas 5 y 6 |

**No incluye todavía:** notificaciones push con FCM (necesitan un emisor en el backend),
registro de prestadores, disponibilidad, certificados ni verificación de DNI.

El mapa usa las teselas públicas de OpenStreetMap, pensadas para uso moderado: la app se
identifica con su nombre de paquete y guarda las teselas en caché. Para un uso masivo habría
que contratar o montar un servidor de teselas.

## Estructura

```text
app/src/main/java/pe/tecsup/chambape/
├── MainActivity.kt
├── ChambaPeApplication.kt     AppContainer: crea API, base de datos y repositorios
├── domain/model/              modelos que usa la interfaz (sin Retrofit, Room ni Firebase)
├── data/
│   ├── remote/                ApiService (Retrofit) y DTO
│   ├── local/                 entidades, DAO y base de datos Room
│   ├── mapper/                DTO -> entidad -> modelo
│   ├── auth/                  AuthRepository (interface) y FirebaseAuthRepository
│   └── repository/            PrestadorRepository y SolicitudRepository (interfaces + implementación)
├── device/                    ubicación y notificaciones (interfaces + implementación Android)
└── ui/                        pantallas Compose, ViewModel con UiState y navegación
```

Los ViewModel dependen de las interfaces, no de Retrofit ni de Firebase; por eso
`BusquedaViewModelTest` usa un repositorio falso.

## Requisitos

- JDK 17 o superior (probado con Temurin 25).
- Android SDK con la plataforma 37. Con Android Studio se instala solo; `local.properties`
  (no versionado) debe apuntar a él: `sdk.dir=C\:/Users/<usuario>/AppData/Local/Android/Sdk`.
- `app/google-services.json` del proyecto Firebase `chamba-pe-tecsup` (no versionado). Se descarga
  desde la consola de Firebase: Configuración del proyecto → app Android `pe.tecsup.chambape`.
- No hace falta instalar Gradle: se usa el wrapper (`gradlew`).

## Comandos

Desde `mobile/android/`:

```bash
./gradlew assembleDebug        # compila el APK de depuración
./gradlew testDebugUnitTest    # pruebas unitarias
./gradlew lintDebug            # lint de Android
./gradlew installDebug         # instala en un emulador o dispositivo conectado
```

## Conectar con la API Django

La app llama por defecto a `http://127.0.0.1:8000/`, es decir, al propio dispositivo. Con el
servidor de Django corriendo en la PC (`python manage.py runserver`), redirige el puerto:

```bash
adb reverse tcp:8000 tcp:8000
```

Así funciona en emulador y en teléfono por USB sin cambiar `ALLOWED_HOSTS`. Para otra URL:
`./gradlew assembleDebug -Pchambape.apiBaseUrl=https://mi-servidor/`. Solo se permite HTTP sin
cifrar hacia `127.0.0.1`, `localhost` y `10.0.2.2`.

## Firebase

- Proyecto: `chamba-pe-tecsup` (cuenta de Renzo). Auth por correo/contraseña y Firestore activos.
- Reglas en [`firestore.rules`](firestore.rules). Tras cambiar reglas o proveedores: `firebase deploy --only firestore,auth`
  (sin desplegar `auth`, el ingreso falla con `CONFIGURATION_NOT_FOUND`).
- Desde la app solo se registran **clientes**. Para probar un **prestador**: crear el usuario en
  Authentication y, en Firestore, el documento `usuarios/{uid}` con `nombre`, `rol: "prestador"`
  y `prestadorId` (número: el ID del prestador en la API Django).
  El nombre del campo distingue mayúsculas: `prestadorID` no funciona.

| Colección | Campos | Quién accede |
|---|---|---|
| `usuarios/{uid}` | `nombre`, `correo`, `rol`, `prestadorId?`, `creadoEn` | Solo su dueño; el rol y `prestadorId` no se cambian desde la app |
| `solicitudes/{id}` | `prestadorId`, `prestadorNombre`, `clienteUid`, `clienteNombre`, `descripcion`, `direccion`, `estado`, `creadaEn`, `respondidaEn?` | El cliente que la creó y el prestador destinatario; solo este cambia el estado |

Las solicitudes de la app viven en Firestore: **no** son las mismas que las de la web (Django).
Unificarlas es una decisión pendiente del equipo.

## Versiones

Todas en [`gradle/libs.versions.toml`](gradle/libs.versions.toml); ese archivo lo comparten todas
las tareas `AND-*` y sus cambios se coordinan con el equipo.

| Elemento | Valor |
|---|---|
| Gradle (wrapper) / AGP | 9.9.1 / 9.5.1 (AGP incluye Kotlin; no se aplica `kotlin-android`) |
| Plugin de Compose / KSP | 2.4.21 / 2.3.14 |
| Compose BOM / Firebase BOM | 2026.09.00 / 35.2.0 |
| Room / Retrofit / Navigation | 2.8.5 / 3.0.0 / 2.10.2 |
| osmdroid | 6.1.20 |
| `compileSdk` / `targetSdk` / `minSdk` | 37 / 37 / 26 |
| Paquete | `pe.tecsup.chambape` |

## Qué no va en el repositorio

`local.properties`, `google-services.json`, almacenes de firmas (`*.jks`, `*.keystore`) y
`keystore.properties` están en `.gitignore`. No se incrustan URL de producción ni credenciales.
