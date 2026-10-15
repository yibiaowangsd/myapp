plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.personal.wo"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.personal.wo"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
    }
    flavorDimensions += "connectivity"
    productFlavors {
        create("personalOffline") { dimension = "connectivity"; buildConfigField("boolean", "ENHANCED", "false") }
        create("personalEnhanced") { dimension = "connectivity"; buildConfigField("boolean", "ENHANCED", "true") }
    }
    signingConfigs {
        val releaseStore = providers.environmentVariable("WO_KEYSTORE_PATH").orNull
        if (releaseStore != null) create("personalRelease") {
            storeFile = file(releaseStore)
            storePassword = providers.environmentVariable("WO_KEYSTORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("WO_KEY_ALIAS").orNull ?: "wo"
            keyPassword = providers.environmentVariable("WO_KEY_PASSWORD").orNull
        }
    }
    buildTypes {
        debug { ndk { abiFilters += listOf("arm64-v8a","x86_64") } }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("personalRelease")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk { abiFilters.clear(); abiFilters += "arm64-v8a" }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs.useLegacyPackaging = false; resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/LICENSE*") }
    lint { abortOnError = true; disable += "ObsoleteLintCustomCheck" }
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.sqlite:sqlite:2.5.2")
    implementation("net.zetetic:sqlcipher-android:4.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.google.crypto.tink:tink-android:1.18.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
dependencyLocking { lockAllConfigurations() }
