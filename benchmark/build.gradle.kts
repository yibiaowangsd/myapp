plugins {
    id("com.android.test")
    kotlin("android")
}
android {
    namespace="com.personal.wo.benchmark"
    compileSdk=36
    buildToolsVersion="36.0.0"
    defaultConfig {
        minSdk=29;targetSdk=36
        testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.enabledRules"]=
            if(providers.gradleProperty("wo.profileGeneration").orNull=="true")"BaselineProfile" else "Macrobenchmark"
    }
    targetProjectPath=":app"
    experimentalProperties["android.experimental.self-instrumenting"]=true
    flavorDimensions+="connectivity"
    productFlavors {
        create("personalOffline"){dimension="connectivity"}
        create("personalEnhanced"){dimension="connectivity"}
    }
    buildTypes {
        create("benchmark") {
            isDebuggable=true
            signingConfig=signingConfigs.getByName("debug")
            matchingFallbacks+=listOf("release")
        }
    }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget="17" }
}
androidComponents { beforeVariants(selector().all()){it.enable=it.buildType=="benchmark"} }
dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.0")
}
dependencyLocking { lockAllConfigurations() }
