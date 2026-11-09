plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
}

android {
    namespace = "com.nos.motobitride"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nos.motobitride"
        minSdk = 29
        targetSdk = 36
        // Versione: UNICA sorgente di verita' e' MOTO_VERSION in gradle.properties.
        val motoVer = providers.gradleProperty("MOTO_VERSION").get()
        val (vMaj, vMin, vPat) = Regex("""(\d+)\.(\d+)\.(\d+)""").find(motoVer)!!.destructured
        versionName = motoVer
        versionCode = vMaj.toInt() * 1000 + vMin.toInt() * 100 + vPat.toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Custom HUD Lib
    implementation(files("libs/hudlib.aar"))

    // Shizuku (modulo isolato). Per escluderlo: commentare questa riga
    // e `include(":shizuku")` in settings.gradle.kts.
    // L'app compila lo stesso: usa il gateway vuoto.
    implementation(project(":shizuku"))

    // Camera X
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit barcode scanning
    implementation(libs.barcode.scanning)
    
    // Basic
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.material3)

    // Controllo giornaliero delle scadenze veicolo (sopravvive al riavvio,
    // nessun permesso per allarmi esatti)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Solo le icone core (Home, Settings): l'icona moto e' un vettoriale locale
    // (res/drawable/ic_moto.xml). material-icons-extended aggiungeva ~50 MB di
    // dex per una sola icona.
    implementation("androidx.compose.material:material-icons-core")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}