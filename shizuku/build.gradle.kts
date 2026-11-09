// Modulo ISOLATO per Shizuku.
//
// Per escluderlo completamente dall'APK:
//   1. commentare `implementation(project(":shizuku"))` in app/build.gradle.kts
//   2. commentare `include(":shizuku")` in settings.gradle.kts
//
// L'app continua a compilare e a funzionare: la raggiunge per RIFLESSIONE
// (nessuna dipendenza a compile-time) e, se la classe non c'e', usa il
// gateway "vuoto" (NoOp). Nessun ciclo di dipendenze: questo modulo NON
// dipende da :app.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nos.motobitride.shizuku"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.shizuku.api)
    // Necessario per la callback del permesso (manifest del provider).
    implementation(libs.shizuku.provider)
}
