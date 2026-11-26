import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// URL de la API Django. Por defecto apunta al propio dispositivo: con
// `adb reverse tcp:8000 tcp:8000` llega al runserver de la PC sin tocar ALLOWED_HOSTS.
// Se puede cambiar con -Pchambape.apiBaseUrl=... o en ~/.gradle/gradle.properties.
val apiBaseUrl = (findProperty("chambape.apiBaseUrl") as String?) ?: "http://127.0.0.1:8000/"

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
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room: caché de búsquedas y favoritos
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit: API Django (rubros y búsqueda)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // Firebase: sesión (Auth) y perfiles/solicitudes (Firestore)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Mapa: Google Maps para Compose (como el lab CampusGPS); la clave va en local.properties
    implementation(libs.maps.compose)

    // Ubicación y corrutinas
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
