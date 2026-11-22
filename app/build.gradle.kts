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
        versionCode = 13
        versionName = "1.5"
    }

    // La aplicación de prueba se instala junto a la original.
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".fotoselect"
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
