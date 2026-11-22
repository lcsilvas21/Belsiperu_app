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
        versionCode = 9
        versionName = "1.1"
    }

    // La aplicación de prueba se instala junto a la original.
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".fotos"
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
