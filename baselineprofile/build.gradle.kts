plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

// Not an app of its own - an instrumented test that drives :app on a connected device and records
// which classes and methods it touches while starting and scrolling. What it produces is a text
// file, not code the app ships; :app's own `baselineProfile` block below is what makes every
// release package that file.
android {
    namespace = "com.ikverse.egxanalyzer.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Generates against the release build - a debug build never runs code the way a real device does,
// and that is exactly the behavior this profile is meant to describe.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
