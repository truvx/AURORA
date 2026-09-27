plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

import java.util.Properties
import java.io.FileInputStream

// Load local.properties for API keys
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "dev.aurora.player"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.aurora.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-beta1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // No YouTube key here on purpose. A buildConfigField ships the key inside every
        // copy of the APK, where `strings` finds it in seconds. It is entered in Settings
        // instead and kept in the Keystore - see data/security/SecretStore.kt.

        /*
         * The Google OAuth client ID, which is a different kind of thing from a key.
         *
         * An installed-app client has no secret by design - Google says so, and PKCE is
         * what stands in for one - so this genuinely can ship in the APK, and has to: the
         * authorization request cannot be made without it. It is read from
         * local.properties rather than written here only so that one person's Cloud
         * project identifier does not end up committed to a shared repository.
         *
         * Absent, the app falls back to the pasted API key and says so in Settings.
         */
        val googleClientId: String = localProperties.getProperty("GOOGLE_OAUTH_CLIENT_ID")
            ?: System.getenv("GOOGLE_OAUTH_CLIENT_ID")
            ?: ""
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"$googleClientId\"")

        /*
         * Where the AI gateway lives.
         *
         * It used to be `http://10.0.2.2:3000/` written into the source - the emulator's
         * alias for the host machine, which on a real phone is nothing at all. The signed
         * beta could therefore never reach the AI, and a debug build sent a Google ID token
         * to it over plaintext HTTP.
         *
         * Set AURORA_GATEWAY_URL in local.properties to your deployment. Left unset, a
         * debug build falls back to the emulator loopback, which is the only host the debug
         * network-security config permits cleartext to; a release build gets nothing and
         * says the AI is not configured rather than failing at the socket.
         */
        val gatewayUrl: String = localProperties.getProperty("AURORA_GATEWAY_URL")
            ?: System.getenv("AURORA_GATEWAY_URL")
            ?: ""
        buildConfigField("String", "AI_GATEWAY_URL", "\"$gatewayUrl\"")

        /*
         * The redirect Google will send the browser back to.
         *
         * Installed-app clients get the reversed client ID as a private-use scheme. It is
         * derived rather than configured separately so the two can never disagree - a
         * redirect that does not match the client exactly is rejected by Google, and
         * "exact redirects" is what AGENTS.md asks for.
         */
        manifestPlaceholders["appAuthRedirectScheme"] =
            googleClientId.substringBefore(".apps.googleusercontent.com")
                .takeIf { it.isNotBlank() && it != googleClientId }
                ?.let { "com.googleusercontent.apps.$it" }
                ?: "dev.aurora.player.unconfigured"
    }


    signingConfigs {
        // Release signing is driven by local.properties or CI environment variables so no
        // keystore or password is ever committed. Without them the release build is
        // unsigned rather than silently falling back to the debug key, which would produce
        // an artifact that looks releasable and is not.
        create("release") {
            val storePath = localProperties.getProperty("RELEASE_STORE_FILE")
                ?: System.getenv("RELEASE_STORE_FILE")
            if (storePath != null && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                    ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                    ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
                    ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
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
            // Only attach the signing config when a keystore was actually resolved.
            signingConfig = signingConfigs.getByName("release").takeIf {
                it.storeFile?.exists() == true
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        // Release-like but installable: macrobenchmark refuses to measure a debuggable
        // build, because debuggable drastically changes runtime performance and would make
        // the numbers meaningless. Signed with the debug key so it can be installed without
        // the release keystore.
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            applicationIdSuffix = ".benchmark"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }



    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.core.ktx)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Network
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    // OAuth, for signing in to Google instead of pasting a key.
    //
    // A hand-written authorization flow is a security protocol written by hand: PKCE,
    // state, exact redirect matching, refresh and revocation, each with a way to be subtly
    // wrong. AppAuth is the OpenID Foundation's implementation of exactly this and is what
    // Google's own installed-app guidance points at.
    implementation("net.openid:appauth:0.11.1")

    // UI Utilities
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("io.github.kyant0:shapes:1.2.0")


    // Unit testing
    testImplementation(libs.junit)
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation(libs.room.testing)
    testImplementation(libs.coroutines.test)

    // Instrumented testing
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation("androidx.test:rules:1.6.1")
    debugImplementation(libs.compose.ui.test.manifest)
}

room {
    schemaDirectory("$projectDir/schemas")
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1")
    }
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    // Tests that need a live AI gateway on localhost:3000 are skipped unless explicitly
    // enabled, so the default suite is runnable offline and in CI.
    // Enable with: ./gradlew test -PauroraLiveAi=true
    systemProperty(
        "aurora.liveAi",
        providers.gradleProperty("auroraLiveAi").getOrElse("false")
    )
    systemProperty(
        "aurora.gatewayToken",
        providers.gradleProperty("auroraGatewayToken").getOrElse("")
    )
    // The YouTube key is no longer in BuildConfig, so the live test takes it the same way
    // the gateway token arrives: passed in for that run, never written into the artifact.
    systemProperty(
        "aurora.youtubeKey",
        providers.gradleProperty("auroraYouTubeKey").getOrElse("")
    )
}

