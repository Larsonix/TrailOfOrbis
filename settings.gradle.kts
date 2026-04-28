pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            name = "azuredoom"
            url = uri("https://maven.azuredoom.com/mods")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "TrailOfOrbis"
