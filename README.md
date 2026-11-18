# EGX Analyzer for Android

Reads Egyptian Exchange stock recommendations out of Telegram channels, extracts them with a cloud
model, scores every call against real prices, and ranks the channels on what they actually
delivered.

## What it does

- **Home** — the EGX indices, the day's movers and breadth, market headlines, and your own book.
- **Portfolio** — the trades you actually took, tracked in percent, with stop and target alerts and
  a session digest.
- **Analyze** — signs in to Telegram on the device (TDLib), reads the chosen channels over a date
  range, and sends the messages and screenshots to the cloud model you pick (Qwen Cloud,
  OpenRouter, Hugging Face or OpenAI).
- **Results** — each run as a report of calls, with corrections, spreadsheet export and sharing.
- **Insights** — every call scored against Cairo prices, channels ranked on what they delivered,
  consensus, alerts, and a per-stock sheet with a full-screen chart.
- **Settings** — models, prices, schedules, backup to a file or Google Drive, and sync between
  devices through a private Telegram channel or Drive.

Provider keys and the Telegram session are encrypted with the Android Keystore and never leave the
device except to the provider you chose. Nothing is read from environment files.

## Requirements

- Android 12 or newer (`minSdk 31`, `targetSdk 36`). Designed for phones, foldables and tablets.
- To build: Android Studio with its bundled JBR (Java 21; Robolectric needs it for the tests).
- A Telegram `api_id` and `api_hash` from [my.telegram.org](https://my.telegram.org). Put them in
  `local.properties` as `telegramApiId` / `telegramApiHash`, or enter them in the app at runtime.

## Build and test

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

One task runs everything: plain JVM tests, Robolectric database tests, and Compose UI tests hosted
by Robolectric. No device or emulator is needed.

## Releases

Bump `appVersionName` in `app/build.gradle.kts`, add an entry to [`CHANGELOG.md`](CHANGELOG.md),
commit, tag `vX.Y.Z` and push both. CI runs the tests, signs the per-ABI release APKs and publishes
them as a GitHub release, which is where the app's updater looks.

## Layout

- `app/src/main/java/com/ikverse/egxanalyzer/model` — pure calculation: scoring, ranking, chunking.
- `…/data` — repositories, the database, Telegram, prices, backup and sync.
- `…/state` — `LiveAppState`, the one implementation of the `AppState` interface screens read.
- `…/ui` — Compose screens, with the component kit in `ui/kit`. `ui` never imports `data`.

## Documentation

- [`docs/engine.md`](docs/engine.md) — what a run sends, scoring, ranking, a call's lifecycle.
- [`docs/portfolio.md`](docs/portfolio.md) — positions, trade alerts, the session digest.
- [`docs/features.md`](docs/features.md) — Ask AI, reports, export, backup, sync, updates, schedules.
- [`docs/ui.md`](docs/ui.md) — navigation, the page header, filters, settings, colours.
- [`docs/desktop-parity.md`](docs/desktop-parity.md) — the closed record of the retired desktop app.
- [`CHANGELOG.md`](CHANGELOG.md) — release history.
- [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md) — licences of bundled libraries and fonts.
