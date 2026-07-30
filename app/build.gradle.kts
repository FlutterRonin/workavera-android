import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Signing credentials live outside the repository. When the file is absent --
// a fresh clone, or CI that signs elsewhere -- the release build stays
// unsigned rather than failing, so `assembleRelease` still works for anyone
// who only wants to check that it compiles.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.workavera.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.workavera.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        // Read from the VERSION file rather than repeated here, so the number
        // the app reports and the one the release artifact is named after
        // cannot drift apart.
        versionName = rootProject.file("VERSION").readText().trim()
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Null when there are no credentials to sign with. An unsigned APK
            // cannot be parsed by Android at all, so the build task warns
            // rather than letting it surface as a failed install.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)

    // Compose-native markdown rendering for Docs (and Chat later): core + M3
    // styling + Coil 3 image loading.
    implementation(libs.markdown.renderer.android)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.coil3)

    // Move-and-scale image cropper for the avatar flow.
    implementation(libs.easycrop)

    testImplementation(kotlin("test"))
}
