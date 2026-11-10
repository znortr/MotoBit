pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "motobit-ride"
include(":app")

// Modulo Shizuku: disattivabile commentando questa riga + la dipendenza in app/build.gradle.kts.
// Se il modulo non c'e', l'app usa il gateway "vuoto" (nessun errore di compilazione).
// VERIFICATO: con questa riga commentata la build resta verde (app + display).
include(":shizuku")

// Modulo display (risoluzione, rotazione, barra, luminosità). NON dipende da
// :shizuku in compilazione: lo raggiunge per riflessione, solo come fallback di
// shell. L'app lo raggiunge per riflessione: rimuovendolo, la pagina Risoluzione
// si disattiva da sola.
// VERIFICATO: con questa riga commentata la build resta verde.
include(":display")
