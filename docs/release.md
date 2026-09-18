## Release and packaging

- **The signing key is the update.** Android refuses an update signed by a different key than the
  install it would replace, so every release has to carry the same signature and the key has to
  outlive the machine that made it — it lives in GitHub secrets (`EGX_KEYSTORE_BASE64`,
  `EGX_KEYSTORE_PASSWORD`, `EGX_KEY_ALIAS`), and locally in `local.properties` as
  `EGX_KEYSTORE_FILE` and the same two names. Absent, the release build is simply unsigned rather
  than failing, so a fresh checkout still builds. The **move from debug-signed to release-signed
  builds costs one uninstall on every device**, and the order matters: sync first, then uninstall,
  then install the release APK, then sign in to Telegram and let the launch sync bring the record,
  the trades, the rules and the settings back. Only the API key is retyped by hand.
- Release: bump `appVersionName` in `app/build.gradle.kts` (versionCode is derived), commit, tag
  `vX.Y.Z`, push both. The tag is the whole process — CI runs the tests, signs the release APKs, and
  publishes them as a GitHub release, which is where the app looks. A tag whose tests fail publishes
  nothing.
- **The redesign was abandoned on 2026-08-19 and deleted on 2026-09-12 — the shipping UI is the
  UI.** What went with it: `app/src/next` (7,901 lines under `…/next/`), the `next` build type, the
  `src/current` source set and the `sourceSets` block that registered it, the `SYNC_CHAT_TITLE` and
  `SYNC_READ_ONLY` build settings, `TelegramRepository.READ_ONLY` and its seven guards, and the CI
  step that turned a `-next` tag into a prerelease. `AppRoot` is one file in `src/main/java/…/ui/`
  again and `MainActivity` calls it with an `AppState` alone — the `activity` parameter existed
  only because the redesign's copy of the file still wanted one, and the two signatures had to
  agree. The channel name is a plain `const` in `TelegramRepository`, because there is one app
  looking for one channel. **Nothing guards the sync channel from a second build any more**, which
  is the one thing this removal gave up: if a side-by-side app is ever wanted again, the read-only
  mode is in the history at `01e4d57` and the seven mutating paths it covered are `sendMessage`,
  `deleteMessages` and `createNewSupergroupChat`, wherever they are called from.
- **`-PabiSplits` is passed by CI and nowhere else.** The ABI split is off by default on purpose:
  enabled everywhere, an ordinary `assembleDebug` would stop producing `app-debug.apk` and start
  producing one file per architecture, breaking the install command in the main guide's
  **Commands** and the CI artifact. To reproduce what a release ships, pass the flag by hand.

### R8, and the 50 MB of icons nobody draws

Release builds have been minified since 2026-09-12. The arm64 APK — the one every real phone here
downloads, and downloads again on every update — went from **81.3 MB to 31.4 MB**, which is 61% of
it gone. Measured, not estimated: build `:app:assembleRelease -PabiSplits` with the flag either way
and compare.

- **It is one dependency.** `material-icons-extended` is a 36 MB artifact that ships every Material
  icon as generated code, and this app draws a few dozen of them. The library is built on the
  assumption that R8 strips the rest; unminified it put **56 MB of dex** in the APK, against 6.8 MB
  after. Everything else the shrinker did is a rounding error beside that.
- **Nothing it does touches the native half.** TDLib is 15–26 MB of `.so` per architecture, which is
  what the ABI split is for and what no Java shrinker has an opinion about.
- **`-dontobfuscate`, deliberately.** The saving above is *shrinking* — removing code nothing
  reaches — and not renaming what is left. Renaming buys a few per cent more and would cost the
  crash log built the same day: a stack trace out of an obfuscated build reads as
  `a.b.c(Unknown Source)` and means nothing without the mapping file for that exact release. If that
  trade is ever revisited, `mapping.txt` has to be published with every release and kept forever — a
  mapping file that has been lost is a crash log that cannot be read.
- **TDLib needs no rules here.** `tdl-coroutines.aar` carries its own consumer rules, keeping
  `org.drinkless.tdlib.JsonClient`'s native methods and its log callback, and AGP applies them
  unasked. `proguard-rules.pro` says so, because their absence otherwise reads as an omission.
- **This app's own code needs no rules, which turned out to be the wrong thing to check**: there is
  no reflection in it — no `Class.forName`, no `getDeclaredField`, no `getIdentifier` (`StockLogos`
  says in as many words why it is 222 lines instead) — and the eight manifest components are kept
  because the manifest names them. What that reasoning missed is the next bullet: a dependency can reflect on its own
  classes without this app ever naming them.
- **3.6.15 did not open, and this is what it cost.** WorkManager keeps its queue in a Room database;
  Room builds the generated `_Impl` by reflection, so nothing referenced its constructor and R8
  deleted it. `androidx.startup.InitializationProvider` then died before the app drew anything:
  `NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []`. Room 2.6.1 does ship a
  rule and it is not enough — `-keep class * extends androidx.room.RoomDatabase` keeps the class and
  lets the members be shrunk off it. Naming the member is the fix, and it is one line in
  `proguard-rules.pro`. Fixed in 3.6.16.
- **"This app has no reflection" was the wrong question.** It is true of this app's own code and was
  never the thing that mattered: what matters is whether anything in the *process* reflects, and Room
  does, one dependency down, on a database this app never mentions. A new dependency is a reason to
  read its `proguard.txt` rather than assume it has one that works.
- **`usage.txt` is how this is checked**, in `app/build/outputs/mapping/release/`. It lists every
  member R8 removed, by name — `public void <init>()` was sitting under `WorkDatabase_Impl` in the
  3.6.15 build, and reading it before tagging would have caught this. `grep` it for `<init>()` under
  any `_Impl` class after a dependency bump. WorkManager's own workers were never at risk:
  work-runtime 2.10.5 names the members its rule keeps.
- **The crash log could not report this one.** `InitializationProvider` is a ContentProvider, and
  those are installed ahead of `Application.onCreate` — so `CrashLog.install` had not run and the
  file it writes was empty. `adb logcat -b crash -d` is what reads a crash from before the app
  starts; the ordinary buffer is drowned by Wi-Fi chatter on an emulator and the crash scrolls off it.
- **How a minified build gets tested without a device or a signature.** Turn minification on for the
  `debug` build type with the same `proguardFiles`, and install that over the existing debug install
  — same signing key, so the data survives, and the R8 pipeline is identical to release. That
  reproduced the launch failure exactly and then proved the fix. Take it back out afterwards; it is
  a diagnostic, not a setting.
