import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// URL del Back End de usuario (Spring Boot, backend/spring), como en el diagrama de arquitectura.
// Por defecto apunta al propio dispositivo: con `adb reverse tcp:8080 tcp:8080` llega al Spring
// de la PC. Spring responde igual que Django en las 13 rutas que usa la app; para volver a Django:
// -Pchambape.apiBaseUrl=http://127.0.0.1:8000/ (o en ~/.gradle/gradle.properties).
val apiBaseUrl = (findProperty("chambape.apiBaseUrl") as String?) ?: "http://127.0.0.1:8080/"

// Clave de Google Maps (Maps SDK for Android), como en el lab CampusGPS: va en local.properties
// (MAPS_API_KEY=...), que no se versiona. Sin clave el mapa queda en blanco, pero la app compila.
val propiedadesLocales = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "pe.tecsup.chambape"
    compileSdk = 37

    defaultConfig {
        applicationId = "pe.tecsup.chambape"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        manifestPlaceholders["MAPS_API_KEY"] = propiedadesLocales.getProperty("MAPS_API_KEY", "")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room: caché de búsquedas y favoritos
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit: API de usuario (Spring Boot, con las mismas respuestas que Django)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // Firebase: solo la sesión (Auth). Perfiles y solicitudes viven en Django.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // Mapa: Google Maps para Compose (como el lab CampusGPS); la clave va en local.properties
    implementation(libs.maps.compose)

    // Ubicación y corrutinas
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
