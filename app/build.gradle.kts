plugins {
    alias(libs.plugins.android.application)
    // alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.zuan.kernelmanager.next"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zuan.kernelmanager.next"
        minSdk = 29
        targetSdk = 37
        // CI (GitHub Actions) passes -Pzkm.versionName / -Pzkm.versionCode for nightly & beta builds.
        // For a normal build both fall back to the values after "?:".
        // When you publish a stable release: raise the fallback versionCode ABOVE every nightly/beta code.
        versionCode = (project.findProperty("zkm.versionCode") as String?)?.toIntOrNull() ?: 10000
        versionName = (project.findProperty("zkm.versionName") as String?) ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // DEFAULT VALUE - TARUH DI SINI
        buildConfigField("boolean", "ENABLE_BETA_FEATURES", "true")
    }

    // Signing key for CI builds. It is only used when KEYSTORE_FILE is set (GitHub Actions does that
    // from your repository secrets). Locally nothing changes.
    signingConfigs {
        create("ci") {
            val keystorePath = System.getenv("KEYSTORE_FILE")
            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    // TAMBAH BUILD TYPES YANG BENAR
    buildTypes {
        debug {
            if (signingConfigs.getByName("ci").storeFile != null) signingConfig = signingConfigs.getByName("ci")
            // Untuk debug build
            buildConfigField("boolean", "ENABLE_BETA_FEATURES", "true")
        }
        release {
            if (signingConfigs.getByName("ci").storeFile != null) signingConfig = signingConfigs.getByName("ci")
            // Keep the existing public versionCode until release history is verified;
            // lowering it would break Android upgrade ordering for existing installs.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "ENABLE_BETA_FEATURES", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    
    packaging {
        jniLibs {
            keepDebugSymbols += "**/*.so"
            // Multiple modules expose the same native artifacts; keep one deterministic copy.
            pickFirsts += "lib/arm64-v8a/libhttools_static.so"
            pickFirsts += "lib/armeabi-v7a/libhttools_static.so"
            pickFirsts += "lib/arm64-v8a/libtermux.so"
            pickFirsts += "lib/armeabi-v7a/libtermux.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    kotlin {
        //jvmToolchain(21)  // LEBIH SIMPLE
    }
}

dependencies {
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.androidx.appcompat)
    
    
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    
    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Compose Testing
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Haze
    implementation(libs.haze)
    implementation(libs.haze.materials)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    
    //Privileged ADB Shell (Axeron-API provides both Axeron & Shizuku client runtimes)
    implementation(libs.axeron.api)
    implementation(libs.axeron.provider)
    
    //Service
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.savedstate.ktx)

    // Third Party
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.libsu.nio)
    implementation(libs.composables.core)
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    // 16 KB page-size compatible AndroidX native components
    implementation("androidx.graphics:graphics-path:1.1.0")
    // MaterialShapes / RoundedPolygon (Icon Shapes style)
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation("androidx.compose.material:material:1.13.0-alpha01")
    implementation("io.coil-kt.coil3:coil-gif:3.6.3") // Sesuaikan versi Coil lu
    // KSUWEB
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.webkit)
    implementation(libs.material)
    implementation("org.json:json:20260814") 
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.github.kyant0:backdrop:2.0.1")
    // Kalau mau shape kapsul asli Kyant: 
    implementation("io.github.kyant0:capsule:2.1.3")
    implementation("com.vanniktech:android-image-cropper:4.7.0")
    
    // TERMINAL
    implementation(project(":terminal-emulator"))
    implementation(project(":terminal-view"))
    
    // HORIZON KERNEL FLASHER
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-common:1.11.1")
    implementation(project(":kernel-flasher"))
}
// ACS_SIGNING_CONFIG_START
val keystorePropertiesFile = file("keystore.properties")
val acsProps = if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.readLines()
        .filter { '=' in it }
        .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
} else emptyMap()

android {
    signingConfigs {
        maybeCreate("release").apply {
            val sf = acsProps["storeFile"]
            val sp = acsProps["storePassword"]
            val ka = acsProps["keyAlias"]
            val kp = acsProps["keyPassword"]
            if (!sf.isNullOrBlank()) storeFile = file(sf)
            if (!sp.isNullOrBlank()) storePassword = sp
            if (!ka.isNullOrBlank()) keyAlias = ka
            if (!kp.isNullOrBlank()) keyPassword = kp
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
// ACS_SIGNING_CONFIG_END
