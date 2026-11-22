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
        versionCode = 27
        versionName = "1.19"
    }

    // Instalación de prueba independiente: las bases SQLite de otras versiones no se comparten.
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".pdfv119"
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
