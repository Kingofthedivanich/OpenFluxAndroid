plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.p1neapplexpress.openflux"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.p1neapplexpress.openflux.fork"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0" // fork targeting OpenFlux core 0.0.3

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = false
        aidl = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        // Must stay true: NativeProcessSupervisor execs libp1npplydtransport.so
        // as a subprocess (ProcessBuilder), not dlopen()s it as a library --
        // that requires a real extracted file on disk at nativeLibraryDir.
        // useLegacyPackaging=false (direct mmap from the APK, no extraction)
        // was tried to satisfy the 16KB page-size alignment checker, but it
        // leaves nativeLibraryDir empty and breaks every tunnel from
        // starting. The 16KB warning is a forward-compat notice, not a
        // functional error, on today's 4KB-page hardware.
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.github.g00fy2.quickie:quickie-bundled:1.10.0")
}
