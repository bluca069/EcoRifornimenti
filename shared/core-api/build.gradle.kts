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
            api(project(":shared:core-model"))
            api(project(":shared:core-geo"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        jvmMain.dependencies { implementation(libs.ktor.client.okhttp) }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        if (providers.gradleProperty("enableIos").orNull == "true") {
            iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        }
    }
}

android {
    namespace = "net.ecorifornimenti.app.api"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.androidMinSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Il test di integrazione contro l'Osservaprezzi vero si accende con
// `-Dintegrazione=true`: la property va propagata alla JVM dei test, altrimenti
// resta al daemon di Gradle e il test si auto-salta.
tasks.withType<Test>().configureEach {
    val attivo = providers.systemProperty("integrazione").orElse("false")
    inputs.property("integrazione", attivo)
    doFirst { systemProperty("integrazione", attivo.get()) }
}
