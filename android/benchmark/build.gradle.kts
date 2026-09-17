plugins {
    // AGP 9 provides Kotlin support built in; a separate kotlin-android plugin is refused.
    alias(libs.plugins.android.test)
}

android {
    namespace = "dev.aurora.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Benchmarks run against a release-like build of :app so the numbers reflect what a
    // user would actually experience, not a debuggable build with extra instrumentation.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        // Measure only the release-like variant; a debuggable build would report numbers
        // that do not reflect what a user experiences.
        create("benchmark") {
            isDebuggable = false
            // The test APK still needs signing to install; the debug key is fine here
            // because nothing about this variant ships.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(libs.junit.ext)
    implementation(libs.espresso.core)
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
}
