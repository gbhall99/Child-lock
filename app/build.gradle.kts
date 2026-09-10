import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing: keystore.properties (git-ignored) or CI environment variables.
// See RELEASING.md. Absent both, release builds are unsigned and only debug installs.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String): String? = keystoreProps.getProperty(key) ?: System.getenv("CHILDLOCK_" + key.uppercase())
val hasReleaseKey = signingValue("storeFile") != null

android {
    namespace = "com.gbhall.childlock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gbhall.childlock"
        minSdk = 26
        targetSdk = 36
        // CI derives the version code from the release tag; 1 is the local default.
        versionCode = (System.getenv("CHILDLOCK_VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.0"
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(signingValue("storeFile")!!)
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
            }
        }
    }

    // Two builds. The released app cannot skip ads at all: the capability is
    // compiled out, not merely hidden (see REVIEW.md, group D).
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            isDefault = true
        }
        create("sideload") {
            dimension = "distribution"
            versionNameSuffix = "-sideload"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }

    bundle {
        language { enableSplit = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// The app deliberately uses only the Android framework and the Kotlin
// standard library: no AndroidX. That keeps the APK tiny and the code easy
// to audit for something that sits over every other app.
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
