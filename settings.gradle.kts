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
include(":shizuku")
