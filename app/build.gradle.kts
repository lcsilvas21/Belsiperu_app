plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pe.belsiperu.cotizaciones"
    compileSdk = 36

    defaultConfig {
        applicationId = "pe.belsiperu.gestioncomercial"
        minSdk = 26
        targetSdk = 36
        versionCode = 33
        versionName = "1.25.0-rc1"
    }

    // Firma de publicación: solo se activa si el propietario configura
    // los cuatro secretos de subida fuera del repositorio.
    val uploadFile = System.getenv("BELSI_UPLOAD_KEYSTORE_PATH")
    val uploadAlias = System.getenv("BELSI_UPLOAD_KEY_ALIAS")
    val uploadStorePass = System.getenv("BELSI_UPLOAD_STORE_PASSWORD")
    val uploadKeyPass = System.getenv("BELSI_UPLOAD_KEY_PASSWORD")
    val hasUploadKey = listOf(uploadFile, uploadAlias, uploadStorePass, uploadKeyPass)
        .all { !it.isNullOrBlank() }
    if (hasUploadKey) {
        signingConfigs {
            create("playUpload") {
                storeFile = file(uploadFile!!)
                storeType = "pkcs12"
                storePassword = uploadStorePass
                keyAlias = uploadAlias
                keyPassword = uploadKeyPass
            }
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            if (hasUploadKey) signingConfig = signingConfigs.getByName("playUpload")
        }
        getByName("debug") {
            applicationIdSuffix = ".publictest"
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
    implementation("androidx.core:core:1.13.1")
}
