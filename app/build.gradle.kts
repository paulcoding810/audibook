import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * versionCode derived from versionName, so a release can't reuse one: 1.2.3 → 10203.
 * release.yml computes the same value to promote that build on Play; keep the two in sync.
 */
fun versionCodeOf(name: String): Int {
    val parts = name.split('.').map { it.toIntOrNull() ?: error("versionName must be MAJOR.MINOR.PATCH, got $name") }
    require(parts.size == 3 && parts[1] < 100 && parts[2] < 100) { "versionName must be MAJOR.MINOR.PATCH with MINOR and PATCH below 100, got $name" }
    return parts[0] * 10_000 + parts[1] * 100 + parts[2]
}

val keystoreProperties = Properties().apply {
    rootProject.file("key.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "com.paulcoding.audibook"
    compileSdk {
        version = release(36)
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile", "release.keystore"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.paulcoding.audibook"
        minSdk = 26
        targetSdk = 36
        versionName = "1.1.0"
        versionCode = versionCodeOf(versionName!!)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GITHUB_REPO", "\"paulcoding810/audibook\"")

    }

    splits {
        abi {
            // Bundles are split by Play itself, and AGP rejects ABI splits combined with resource shrinking there.
            isEnable = gradle.startParameter.taskNames.none { it.contains("bundle", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
        }
        create("gplay") {
            dimension = "distribution"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    implementation(libs.ktor.android)
    implementation(libs.slf4j.simple)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    implementation(libs.sherpa.onnx) { artifact { type = "aar" } }

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
