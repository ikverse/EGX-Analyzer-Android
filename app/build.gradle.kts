plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// One place decides the version. It stayed at 0.1.0 through every build, so a device could not be
// asked which one it was running - a question that cost real time while diagnosing a black screen.
// Bump the name here; the code is derived from it so the two can never disagree.
val appVersionName = "1.0.0"
val appVersionCode = appVersionName.split(".").let { (major, minor, patch) ->
    major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
}

/**
 * The Telegram application credentials, read from `local.properties`, which git ignores.
 *
 * An api_id identifies the *application*, not the person using it - every third-party Telegram
 * client registers one at my.telegram.org and ships it. Asking each user to register their own was
 * a form standing between them and a phone number for no reason. Absent, the app falls back to
 * asking, so a checkout without the file still builds and still works.
 */
val telegramProperties: Map<String, String> = rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.readLines()
    ?.mapNotNull { line ->
        val text = line.trim()
        if (text.startsWith("#") || "=" !in text) return@mapNotNull null
        val key = text.substringBefore("=").trim()
        key to text.substringAfter("=").trim()
    }
    ?.toMap()
    .orEmpty()
val telegramApiId: String = telegramProperties["telegramApiId"] ?: "0"
val telegramApiHash: String = telegramProperties["telegramApiHash"] ?: ""

android {
    namespace = "com.ikverse.egxanalyzer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ikverse.egxanalyzer"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId)
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            // A sideloaded build should be obvious in Settings without checking a commit hash.
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.window)
    implementation(libs.tdl.coroutines.android)
    // Encodes the tg://login link TDLib hands back; scanning it beats typing a phone and a code.
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    // android.jar stubs org.json in local unit tests, so supply a real implementation.
    testImplementation(libs.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
