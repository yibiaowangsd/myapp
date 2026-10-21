plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Local/CI non-secret application registration values. Empty defaults deliberately block authorization.
val huaweiAppId = providers.gradleProperty("wo.huawei.appId").orElse("").get()
val huaweiScopes = providers.gradleProperty("wo.huawei.approvedScopes").orElse("").get()
val huaweiCertificate = providers.gradleProperty("wo.huawei.certificateSha256")
    .orElse("c9b24de261dc30ebbc92da22f3f7826beb2c3015141c7b43d63b1ca27de9cb58").get()
require(huaweiAppId.isEmpty() || huaweiAppId.matches(Regex("[0-9]+"))) { "Huawei App ID must be the actual numeric registration ID" }
require(huaweiScopes.isEmpty() || huaweiScopes.split(",").all { it in setOf("STEPS","EXERCISE","SLEEP","HISTORY_WEEK") }) { "Unknown Huawei scope name" }
require(huaweiCertificate.matches(Regex("[a-fA-F0-9]{64}"))) { "Huawei certificate must be 64 hex SHA-256 characters" }
android {
    namespace = "com.personal.wo"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.personal.wo"
        minSdk = 29
        targetSdk = 36
        versionCode = 14
        versionName = "2.0.0"
        buildConfigField("String", "HUAWEI_APP_ID", "\"\"")
        buildConfigField("String", "HUAWEI_APPROVED_SCOPES", "\"\"")
        buildConfigField("String", "HUAWEI_CERT_SHA256", "\"\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
    }
    flavorDimensions += "connectivity"
    productFlavors {
        create("personalOffline") { dimension = "connectivity"; buildConfigField("boolean", "ENHANCED", "false") }
        create("personalEnhanced") {
            dimension = "connectivity"; buildConfigField("boolean", "ENHANCED", "true")
            buildConfigField("String", "HUAWEI_APP_ID", "\"$huaweiAppId\"")
            buildConfigField("String", "HUAWEI_APPROVED_SCOPES", "\"$huaweiScopes\"")
            buildConfigField("String", "HUAWEI_CERT_SHA256", "\"$huaweiCertificate\"")
            manifestPlaceholders["huaweiAppId"] = "appid=$huaweiAppId"
            proguardFile("proguard-huawei.pro")
        }
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
        if(providers.gradleProperty("wo.benchmarks").orNull=="true")create("benchmark") {
            initWith(getByName("release"))
            val generation=providers.gradleProperty("wo.profileGeneration").orNull=="true"
            isMinifyEnabled=!generation
            isShrinkResources=!generation
            isDebuggable=false
            signingConfig=signingConfigs.getByName("debug")
            matchingFallbacks+=listOf("release")
            ndk { abiFilters.clear();abiFilters+=listOf("arm64-v8a","x86_64") }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs.useLegacyPackaging = false; resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/LICENSE*") }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true; disable += "ObsoleteLintCustomCheck" }
}
dependencies {
    "personalEnhancedImplementation"("com.huawei.hms:health:6.16.0.300")
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
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
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
dependencyLocking { lockAllConfigurations() }
