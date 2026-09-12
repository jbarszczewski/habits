import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // KSP runs Room's annotation processor (generates the DAO implementations at build time).
    alias(libs.plugins.ksp)
    // Room Gradle plugin: exports the database schema as JSON so future migrations can be written and tested.
    alias(libs.plugins.room)
}

android {
    namespace = "com.jbarszczewski.habits"

    // Load release signing credentials from keystore.properties (gitignored).
    // If the file doesn't exist the build still works; release builds will
    // fall back to the debug key so CI / local debug builds aren't broken.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().also { props ->
        if (keystorePropsFile.exists()) props.load(keystorePropsFile.inputStream())
    }

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.jbarszczewski.habits"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // signingConfigs must be declared before buildTypes so buildTypes can reference them.
    // Only declare a "release" signing config when keystore.properties is present; a
    // SigningConfig block can't itself reference another one, so the fallback to the
    // debug key is chosen below when picking buildTypes.release.signingConfig instead.
    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // Use the stable release keystore when keystore.properties is present.
            // Otherwise fall back to the debug key so the project still builds
            // without it (e.g. fresh checkout, CI without secrets).
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // Generates BuildConfig so code can check BuildConfig.DEBUG (used for the debug seed task).
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}