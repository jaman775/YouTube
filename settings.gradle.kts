pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipe Extractor is published on JitPack only.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "Drishyopot"
include(":app")
