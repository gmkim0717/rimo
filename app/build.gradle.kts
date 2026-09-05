import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val updateUrl: String = providers.gradleProperty("rimo.updateUrl").getOrElse("https://example.invalid/update.json")
val updateUrlIsPlaceholder = updateUrl.contains("example.invalid")

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.rimo.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rimo.player"
        minSdk = 28
        targetSdk = 34
        // -Primo.versionCode=N / -Primo.versionName=X let manual update tests build a "newer" APK
        // without editing this file. Releases use the values here.
        versionCode = providers.gradleProperty("rimo.versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("rimo.versionName").orNull ?: "0.1.0"

        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Guard: a release build with the placeholder URL would ship an app that can never update.
// Hooked on preReleaseBuild so it fails before any compilation happens.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(!updateUrlIsPlaceholder) {
            "rimo.updateUrl is still the placeholder (${updateUrl}). " +
                "Set the real manifest URL in gradle.properties or pass -Primo.updateUrl=..."
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
}
