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
        versionCode = 14
        versionName = "1.6"
    }

    // Prueba de DNI con identificador propio: no reemplaza otra APK de desarrollo ni borra sus datos.
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dni"
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
