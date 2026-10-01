import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization) // the @Serializable nav routes (LLM.md §7)
}

// Release signing, read from keystore.properties at the repo root. The one there now points at a throwaway
// keystore (LLM.md §11 #3). Without the file the release APK comes out unsigned; debug builds never read it.
val releaseKeystore = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "com.pion.psremote"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.pion.psremote"
        minSdk = 28 // confirm.md Q4
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (!releaseKeystore.isEmpty) create("release") {
            storeFile = rootProject.file(releaseKeystore.getProperty("storeFile"))
            storePassword = releaseKeystore.getProperty("storePassword")
            keyAlias = releaseKeystore.getProperty("keyAlias")
            keyPassword = releaseKeystore.getProperty("keyPassword")
        }
    }

    buildTypes {
        // R8 shrinks, optimises and renames the code, then unused resources go. Configured before `benchmark`,
        // which copies it.
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Release-shaped, debug-signed and profileable: the build the :benchmark suite measures (LLM.md §10).
        // A debuggable build runs Compose without ART's optimisations, so its timings say nothing about
        // what a user sees. Shrunk like release but not renamed, so the baseline profile it records keeps
        // real names (benchmark-rules.pro).
        create("benchmark") {
            initWith(getByName("release"))
            proguardFiles("benchmark-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isProfileable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // Media3VideoPlayback attaches its EventLogger in debug builds only
    }

    // Compose UI suites that hold on any screen run twice from one source: on the JVM under Robolectric
    // (testDebugUnitTest) and on a phone (connectedDebugAndroidTest) — LLM.md §9.
    sourceSets {
        getByName("test").kotlin.srcDir("src/sharedTest/kotlin")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/kotlin")
    }

    testOptions {
        // Robolectric reads the merged resources, assets and manifest: the suites resolve real strings, list the
        // real demo folders, and launch ui-test-manifest's ComponentActivity.
        unitTests.isIncludeAndroidResources = true
        // Real text measurement (LEGACY graphics measures every glyph as 1 px), so a label that overflows its
        // button fails on the JVM as it would on a phone.
        unitTests.all {
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
            // Robolectric writes FileDescriptor's private fd through JDK internals, which JDK 17+ hide unless
            // opened; this machine's Gradle runs on JDK 24.
            it.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            )
        }
    }
}

composeCompiler {
    reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    metricsDestination.set(layout.buildDirectory.dir("compose-reports"))
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.media3)
    implementation(libs.bundles.koin)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.tracing.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.bundles.test.unit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.bundles.test.ui)

    androidTestImplementation(libs.bundles.test.device)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
