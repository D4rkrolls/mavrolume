plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "camera.mavrolume.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "camera.mavrolume.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "device"
    productFlavors {
        create("fold") {
            dimension = "device"
            minSdk = 35
            buildConfigField("boolean", "PHONE_ONLY", "false")
        }
        create("phone") {
            dimension = "device"
            minSdk = 29
            applicationIdSuffix = ".phone"
            buildConfigField("boolean", "PHONE_ONLY", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.window:window:1.3.0")
    testImplementation("junit:junit:4.13.2")
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    // Compose UI
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Activity & Navigation
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // Lifecycle (think: state management that survives config changes)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // CameraX — the camera framework
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Hilt — dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines — async operations
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // DataStore — persistent preferences
    implementation(libs.datastore.preferences)

    // EXIF — read/write photo metadata
    implementation(libs.exifinterface)

    // Image loading for gallery
    implementation(libs.coil.compose)

    // Core KTX — Kotlin extensions for Android APIs
    implementation(libs.core.ktx)

    // Instrumentation tests (LUT generation)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
}
