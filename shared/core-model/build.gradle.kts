plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    if (providers.gradleProperty("enableIos").orNull == "true") {
        listOf(iosArm64(), iosSimulatorArm64())
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            // Le date di comunicazione dei prezzi si confrontano qui: e' il modello
            // a sapere se un prezzo e' troppo vecchio per essere mostrato.
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

android {
    namespace = "net.ecorifornimenti.app.model"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.androidMinSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
