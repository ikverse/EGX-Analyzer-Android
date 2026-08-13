plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// One place decides the version. It stayed at 0.1.0 through every build, so a device could not be
// asked which one it was running - a question that cost real time while diagnosing a black screen.
// Bump the name here; the code is derived from it so the two can never disagree.
val appVersionName = "2.1.3"
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

        /**
         * The private Telegram channel every device syncs through.
         *
         * A build setting rather than a constant in the source, so a build installed *beside* the
         * real app can be pointed somewhere on purpose. See the `next` build type below, which
         * shares this one and is held off writing to it by SYNC_READ_ONLY.
         */
        buildConfigField("String", "SYNC_CHAT_TITLE", "\"EGX Analyzer sync\"")

        /**
         * Whether a build may write to that channel.
         *
         * False everywhere the app is the app. True only for `next`, which reads the record and must
         * not be able to change it - see TelegramRepository.READ_ONLY, where the guard actually sits.
         */
        buildConfigField("boolean", "SYNC_READ_ONLY", "false")
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

        /**
         * The redesign, installed beside the real app rather than over it.
         *
         * A build type and deliberately not a product flavour. A flavour dimension inserts itself
         * into every output path, which would move `app-debug.apk` out from under the install
         * command this repository documents and out from under the release job at the same time.
         * This leaves `assembleDebug` and `assembleRelease` exactly where they are and adds
         * `assembleNext` beside them.
         *
         * Its own `applicationId`, so it is a second app with its own data, its own Telegram session
         * and its own launcher entry. That is what makes the redesign revertible: abandoning it is
         * uninstalling this app, where replacing the real one would mean a downgrade Android
         * refuses - uninstall, reinstall, and a fresh QR sign-in.
         *
         * Release-signed and not debuggable, inherited from `release`. A UI is being judged on how it
         * feels to use, and a debuggable build does not feel like the one that would ship.
         *
         * Declared after `release` because `initWith` copies whatever that block has already been
         * configured with - the signing config most of all, which is null when the keystore is
         * absent, so a checkout without the key still builds.
         */
        create("next") {
            initWith(getByName("release"))
            applicationIdSuffix = ".next"
            versionNameSuffix = "-next"
            // Named again rather than left to initWith. Whether initWith carries a signing config
            // is not something this machine can check - it holds no key, so every build here is
            // unsigned either way - and the first build that could tell us would be a published
            // prerelease no device can install. Same expression `release` uses: null without the
            // key, which leaves the APK unsigned rather than failing.
            signingConfig = signingConfigs.findByName("release")

            /**
             * The real app's channel, read and never written.
             *
             * It started with a channel of its own, which was the safe answer and the useless one: a
             * fresh channel is empty, so the redesign opened on a record with nothing in it - and a
             * dense screen full of prices is exactly the thing that looks fine with no rows. It
             * shares the real channel now and gets every report, trade and rule the app has.
             *
             * What kept the two apart was the channel; what keeps them apart now is this flag.
             * Reports, positions, rules, settings and prompt versions all travel as revisions that
             * merge newest-wins, so a single write from here could overwrite something the real app
             * meant - and deleting a report is worse than that, because it takes the report off
             * every device permanently. `TelegramRepository.READ_ONLY` holds all seven paths; the
             * list, and how to tell when it is complete, is documented there.
             *
             * Prices are not synced at all, so this build still has to fetch its own before any
             * figure is worth looking at. The provider API key is not synced either, which is why
             * this build cannot start an analysis even by accident.
             */
            buildConfigField("boolean", "SYNC_READ_ONLY", "true")
        }
    }

    /**
     * Which UI a build draws, chosen by build type rather than by a branch in the code.
     *
     * `next` is being rebuilt from zero and shares only the data layer, so the two UIs are two
     * bodies of source that must never be compiled together - and Android source sets *merge* with
     * `main` rather than replacing it, so a file of the same name in both is a duplicate class.
     *
     * So the entry point lives per build type. `src/current/java` and `src/next/java` each hold one
     * `ui/AppRoot.kt`, identical in signature and nothing else, and `MainActivity` calls it without
     * knowing which it got. Everything under `src/main` - AppState, the repositories, the scoring,
     * the database - is shared by both, which is the whole point.
     *
     * `src/current` rather than leaving today's UI in `main`: named for what it is, and a single
     * directory to delete on the day `next` becomes the app.
     *
     * Registered on `kotlin` and not only on `java`: `src/next/java` is a source directory the
     * Android plugin creates for a build type by itself, but `src/current` is not a build type and
     * has to be named. Added to `java` alone it compiles nothing - the Kotlin compilation does not
     * follow the Java source dirs - and `MainActivity` fails to resolve `AppRoot` for debug and
     * release while `next` builds perfectly, which reads as the split being backwards.
     */
    sourceSets {
        getByName("debug").kotlin.srcDir("src/current/java")
        getByName("release").kotlin.srcDir("src/current/java")
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
