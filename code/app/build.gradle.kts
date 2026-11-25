// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The public repository, owner/name, behind the About page's links and update checks. Left empty, the
// app links nowhere and never checks for updates.
val repository = providers.gradleProperty("jiumiRepository").getOrElse("").trim()
require(repository.isEmpty() || repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
    "jiumiRepository must be owner/repository"
}

android {
    namespace = "com.ybblab.jiumi"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.ybblab.jiumi"
        minSdk = 30
        targetSdk = 35
        versionCode = 13
        versionName = "0.7.0"
        buildConfigField("String", "REPOSITORY", "\"$repository\"")
        externalNativeBuild { cmake { targets += "jiumi_shell" } }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    // The shell launcher runs from the installer-owned native library directory.
    packaging { jniLibs.useLegacyPackaging = true }

    androidResources {
        localeFilters += listOf("en", "zh")
        // Lets Android list the app's languages in system settings.
        generateLocaleConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core:adb"))
    implementation(project(":core:probe"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
