rootProject.name = "EcoRifornimenti"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":shared:core-model")
include(":shared:core-geo")
include(":shared:core-api")
include(":shared:ui")
include(":androidApp")
