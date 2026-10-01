// Drives the installed app from outside, on a device: startup, demo-open and tutorial frame timing, and the
// baseline profile in app/src/main/baseline-prof.txt. Not a layer of the app — nothing in :app depends on it
// (LLM.md §3, §10). Run: ./gradlew :benchmark:connectedBenchmarkAndroidTest
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.pion.psremote.benchmark"
    compileSdk { version = release(37) }

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        // Matches :app's `benchmark` build type, which is the one installed and measured.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

androidComponents {
    // Only the variant that targets :app's benchmark build exists: a debug one would measure a debuggable app.
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
