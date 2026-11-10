// Modulo ISOLATO per il display (risoluzione, densità, rotazione, barra, luminosità).
//
// NON dipende da :shizuku: lo raggiunge per riflessione, solo come fallback di
// shell. Togliendo :shizuku la build resta verde e qui si usa solo la via
// "permessi" (WRITE_SECURE_SETTINGS + WRITE_SETTINGS), che è anche quella che
// funziona a Shizuku spento.
//
// L'app lo raggiunge per RIFLESSIONE: togliendo questo modulo la pagina
// Risoluzione si disattiva da sola, senza rompere la compilazione.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nos.motobitride.display"
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
    implementation(libs.hiddenapibypass)
}
