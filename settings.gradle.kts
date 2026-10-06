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
        maven("https://jitpack.io") {
            content { includeGroup("com.github.Tgo1014") }
        }
    }
}

rootProject.name = "DnieSignerPeruEvolution"
include(":app", ":dnie-core", ":dnie-protocol", ":dnie-nfc", ":dnie-pdf", ":dnie-icao")
include(":dnie-vault-core")
include(":dnie-passkeys-core", ":dnie-passkeys-android")
include(":dnie-diagnostics-core")
include(":dnie-bridge-core", ":dnie-bridge-android")
