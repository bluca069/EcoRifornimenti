plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // Solo per iOS: serve a tirare dentro MapLibre, che si distribuisce come pod.
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    if (providers.gradleProperty("enableIos").orNull == "true") {
        listOf(iosArm64(), iosSimulatorArm64())

        cocoapods {
            name = "EcoRifornimentiKit"
            version = "0.1.0"
            summary = "Prezzi carburante attorno alla posizione corrente"
            homepage = "https://github.com/computer-pro/ecorifornimenti"
            ios.deploymentTarget = "15.0"
            podfile = project.file("../../iosApp/Podfile")
            framework {
                baseName = "EcoRifornimentiKit"
                isStatic = false
            }
            // La mappa: stesso motore di Android, stessi tile OpenFreeMap.
            pod("MapLibre") {
                version = libs.versions.maplibreIos.get()
                // I binding servono solo alla schermata della mappa.
                moduleName = "MapLibre"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core-model"))
            api(project(":shared:core-geo"))
            api(project(":shared:core-api"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.coroutines.core)
            // Le date dei prezzi arrivano in UTC: vanno portate nel fuso di chi guarda.
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            // La mappa: MapLibre e' una View classica, incastonata con AndroidView.
            implementation(libs.maplibre.android)
            implementation(libs.maplibre.annotation)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // I test della UI usano una sorgente finta: nessuna dipendenza da rete o Ktor.
    }
}

android {
    namespace = "net.ecorifornimenti.app.ui"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.androidMinSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
