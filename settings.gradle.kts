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
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "MicroMind"

// Pure-Kotlin game logic. Builds and tests on any JVM, no Android SDK needed.
include(":core")

// Android app. Included only when an Android SDK is available
// (Android Studio writes local.properties; CI sets ANDROID_HOME).
// Force-skip with -PcoreOnly=true.
val hasSdk = file("local.properties").exists() ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null
if (hasSdk && providers.gradleProperty("coreOnly").orNull != "true") {
    include(":app")
}
