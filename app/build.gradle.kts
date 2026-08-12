plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// One place decides the version. It stayed at 0.1.0 through every build, so a device could not be
// asked which one it was running - a question that cost real time while diagnosing a black screen.
// Bump the name here; the code is derived from it so the two can never disagree.
val appVersionName = "1.0.8"
val appVersionCode = appVersionName.split(".").let { (major, minor, patch) ->
    major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
}

/**
 * Whatever `local.properties` holds. Git ignores that file, so it is where a machine's own secrets
 * live: the Telegram credentials below, and the release signing key further down.
 */
val localProperties: Map<String, String> = rootProject.file("local.properties")
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

/** A setting from `local.properties` on a developer machine, or the environment on CI. */
fun buildSetting(name: String): String? =
    (localProperties[name] ?: System.getenv(name))?.takeIf(String::isNotBlank)

/**
 * The Telegram application credentials.
 *
 * An api_id identifies the *application*, not the person using it - every third-party Telegram
 * client registers one at my.telegram.org and ships it. Asking each user to register their own was
 * a form standing between them and a phone number for no reason. Absent, the app falls back to
 * asking, so a checkout without the file still builds and still works.
 *
 * Read from the environment as well as from `local.properties`, because the file is gitignored and
 * so a build made anywhere but this machine had neither. The first release built by CI shipped
 * without them and asked its user to register an application at my.telegram.org before it would
 * show a sign-in code - which is the form this bundling exists to remove, reappearing in the one
 * build that matters.
 */
val telegramApiId: String = buildSetting("telegramApiId")
    ?: buildSetting("TELEGRAM_API_ID")
    ?: "0"
val telegramApiHash: String = buildSetting("telegramApiHash")
    ?: buildSetting("TELEGRAM_API_HASH")
    ?: ""

/**
 * The key release builds are signed with.
 *
 * This is what makes an over-the-air update possible at all. Android refuses an update signed by a
 * different key than the install it would replace, so every release has to carry the same signature
 * - which means the key has to outlive the machine that made it, and lives in GitHub secrets rather
 * than in this repository. Absent, the release build is simply unsigned: a fresh checkout still
 * builds, and CI still runs the tests, on a machine that has never seen the key.
 */
val releaseKeystore = buildSetting("EGX_KEYSTORE_FILE")
    ?.let(rootProject::file)
    ?.takeIf { it.exists() }
val releaseKeystorePassword = buildSetting("EGX_KEYSTORE_PASSWORD")
val releaseKeyAlias = buildSetting("EGX_KEY_ALIAS")

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

    signingConfigs {
        if (releaseKeystore != null && releaseKeystorePassword != null && releaseKeyAlias != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                // Nearly always the same as the store's, and one setting fewer to get wrong.
                keyPassword = buildSetting("EGX_KEY_PASSWORD") ?: releaseKeystorePassword
            }
        }
    }

    /**
     * One APK per architecture, and a universal one beside them.
     *
     * Most of this app's size is TDLib's native libraries, and a phone has no use for the ones
     * built for other chips - carrying all four made every over-the-air update a 134MB download.
     * They are one version, one build and one signature; only the libraries inside differ.
     *
     * Off unless CI asks for it, which is the whole point of the flag. Enabled everywhere, the
     * ordinary debug build would stop producing `app-debug.apk` and start producing one file per
     * architecture, breaking the install command and the artifact this repository documents.
     */
    splits {
        abi {
            isEnable = project.hasProperty("abiSplits")
            reset()
            // The two devices here are arm64; x86_64 is the emulator. armeabi-v7a is not built -
            // nothing this app runs on is 32-bit.
            include("arm64-v8a", "x86_64")
            // No universal APK. It was 134MB of an upload that carries 145MB of APKs anyone would
            // actually install, and it made every release wait on a file for devices that do not
            // exist here. The cost is that a device which is neither of the two above is told there
            // is no update - which is true, since there would be nothing it could install. The
            // fallback in preferredApkName stays, so a release that does carry one still works.
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Null where the key is not configured, which leaves the APK unsigned rather than
            // failing the build. An unsigned APK cannot be installed, so nothing can mistake one
            // for a release.
            signingConfig = signingConfigs.findByName("release")
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

    testOptions {
        // Robolectric reads the merged manifest and resources to stand up a context; without this
        // it starts with neither and every test that needs one fails on the same complaint.
        unitTests.isIncludeAndroidResources = true
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
    // The daily overdue check. Nothing else in the app runs while it is closed.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.tdl.coroutines.android)
    // Encodes the tg://login link TDLib hands back; scanning it beats typing a phone and a code.
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    // android.jar stubs org.json in local unit tests, so supply a real implementation.
    testImplementation(libs.json)
    // Enough of Android to open a real SQLite database in a plain unit test, which is the only way
    // an onUpgrade path can be checked without a phone. See LocalDataStoreMigrationTest.
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
