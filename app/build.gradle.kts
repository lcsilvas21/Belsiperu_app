plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "pe.belsiperu.cotizaciones"; compileSdk = 35
 defaultConfig { applicationId = "pe.belsiperu.cotizaciones"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1" } }

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
