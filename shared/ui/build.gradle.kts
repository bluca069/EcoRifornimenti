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

/**
 * Il plugin CocoaPods di Kotlin genera un Podfile "sintetico" il cui `post_install`
 * alza il deployment target dei pod a **12.0** (KT-57741). Xcode 26 accetta solo da
 * 15.0 in su, quindi la compilazione di MapLibre falliva prima ancora di iniziare:
 * "deployment target is set to 12.0, but the range of supported versions is 15.0...".
 *
 * Qui si riscrive quel blocco perche' la soglia sia la nostra (15.0, la stessa
 * dichiarata in `cocoapods { ios.deploymentTarget }`), subito dopo che il Podfile e'
 * stato generato e prima che CocoaPods lo usi.
 */
tasks.matching { it.name == "podGenIos" }.configureEach {
    // Il percorso si risolve qui, non dentro doLast: cosi' l'azione non si porta
    // dietro il Project, che la configuration cache non sa serializzare.
    val podfile = layout.buildDirectory.file("cocoapods/synthetic/ios/Podfile").get().asFile
    doLast {
        if (!podfile.exists()) return@doLast
        val testo = podfile.readText()
        val corretto = testo
            .replace("deployment_target_major < 12", "deployment_target_major < 15")
            .replace("deployment_target_major == 12", "deployment_target_major == 15")
            .replace("version = \"#{12}.#{0}\"", "version = \"#{15}.#{0}\"")
        if (corretto != testo) podfile.writeText(corretto)
    }
}
