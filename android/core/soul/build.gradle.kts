plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "io.androllm.core.soul"
    compileSdk = 36
    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        targetSdk = 36
    }
    lint {
        targetSdk = 36
    }
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-compiler:2.57.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.3")
    testImplementation("junit:junit:4.13.2")
}
