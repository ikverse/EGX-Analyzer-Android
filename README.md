# EGX Analyzer for Android

Standalone Android application for collecting EGX sources and sending them to a selected cloud
model for recommendation analysis.

## Baseline

- Android 12 or newer (`minSdk 31`)
- Jetpack Compose and Material 3
- Adaptive bottom navigation, navigation rail, and companion pane
- Folding-feature observation through Jetpack WindowManager
- Qwen Cloud, OpenRouter, Hugging Face, and OpenAI cloud selectors
- No Ollama or other local-model UI
- Provider credentials encrypted with a non-exportable Android Keystore key
- Authenticated cloud model discovery with a selectable provider model list and manual fallback
- QwenCloud/Alibaba regional endpoint presets with QwenCloud International as the default
- Persisted system/light/dark appearance preference
- Persisted analysis language, temperature, response timeout, and default content types
- Provider endpoint/model reset without altering the saved credential
- Telegram session status and actions available from Settings
- On-device saved-analysis count and confirmed bulk deletion
- On-device Telegram user sign-in through TDLib with restored encrypted sessions
- Telegram chat selection, dated history retrieval, and text/photo/voice download
- Optional on-device source import through Android document pickers
- Cancellable provider-neutral cloud requests for text, images, and voice messages
- SQLite-backed saved recommendations with source traceability
- Per-provider endpoint/model settings and encrypted credential removal/replacement

No `OPENAI_API_KEY`, provider environment variable, or environment file is created or read.

## Open and build

Open this directory in Android Studio. The bundled Gradle wrapper targets the locally available
Android 36.1 SDK while retaining Android 12 as the minimum supported version.

```powershell
.\gradlew.bat :app:assembleDebug
```

## Architecture

- `model`: cloud configuration and provider-neutral analysis contracts
- `data`: secure credentials, persistent settings, and analysis repository boundary
- `ui`: responsive Compose shell and feature screens
- `ui/theme`: dark/light Material color schemes

Telegram requires an application API ID and API hash from
[my.telegram.org](https://my.telegram.org). They are entered at runtime; the API hash and TDLib
database key are encrypted with Android Keystore and are never read from an environment file.
TDLib stores its encrypted session and downloaded media only in app-private storage. The app does
not depend on or communicate with the desktop Python service.
