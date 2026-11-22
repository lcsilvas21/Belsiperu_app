plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pe.belsiperu.cotizaciones"
    compileSdk = 35

    defaultConfig {
        applicationId = "pe.belsiperu.cotizaciones"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "1.8"
    }

    // APK de prueba independiente para no sobreescribir la instalación y datos actuales.
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".clientes"
            versionNameSuffix = "-prueba"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Escaneo mediante cámara; Google Play Services gestiona el permiso.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
