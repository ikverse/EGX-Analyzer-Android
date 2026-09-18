# EGX Analyzer — Android

Reads Egyptian Exchange stock recommendations out of Telegram channels, extracts them with a cloud
model, scores every call against real prices, and ranks the channels on what they actually
delivered. The Windows desktop counterpart was retired on 2026-08-12 at v0.1.126 — its source is
kept in the sibling repo, unchanged and unreleased. This is the only app under development, so
nothing here is constrained by keeping the two in step.

Release history from 3.6 onwards is in [`CHANGELOG.md`](CHANGELOG.md). Update it as part of every release.

## Working agreements

- **Never implement without approval.** State the change as a list and wait for the literal word
  "approve". "ok", "do it", and a refinement are not approval.
- **Never start an analysis run.** They cost the owner cloud credits and send their Telegram content
  to the provider. Build, install, open the app to the right screen, and hand over. This now covers
  a second way to start one: **never switch on paid schedules**, and never create an analysis job
  on the owner's device. Arming the clock to spend money later is the same act as spending it.
- **No on-screen verification unless asked.** Build plus unit tests is the loop. Screenshots are
  expensive twice over: once when taken, then again on every later turn of the session. Worth it
  when a layout complaint cannot be diagnosed any other way; not for confirming an install.

## Commands

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | grep -E "^e:|FAILED|BUILD"
```

Filter Gradle output rather than dumping it — raw build logs were the single largest source of
context growth in an earlier session.

```bash
export ADB="$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" -s <serial> install -r --user 0 app/build/outputs/apk/debug/app-debug.apk
```

`--user 0` is not optional on the Fold 7. Without it the session installs for **every** profile,
which put a copy into Samsung's `DUAL_APP` user (95) on every sideload — a second launcher icon for
one package, with its own data, so opening it showed an app with no reports and no Telegram session
and looked exactly like the record had been lost. Removed with
`pm uninstall --user 95 com.ikverse.egxanalyzer`, which leaves user 0's install and data alone.

Wrap every adb call in `timeout N` — a device that drops mid-command leaves adb blocking on
`- waiting for device -` until the tool times out.

Screenshots need the display id; plain `screencap -p` fails with "Multiple displays":

```bash
"$ADB" -s emulator-5554 shell "screencap -p -d 4619827259835644672 /sdcard/s.png"
```

On the Fold 7 the numbering runs the opposite way to the guess: the **cover** panel is `displayId=0`,
physical id `4630946872173396372`, and the **inner** panel is `displayId=1`, `4630946449689556883`.
`dumpsys display | grep uniqueId` reads them back as `local:<id>`. Whichever panel is shut reports
`isActive=false`, which is the cheapest way to tell whether the phone is open without asking.

## Tests, and the two kinds there are

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | grep -E "^e:|FAILED|BUILD"
```

One task runs both kinds, which is the point of how the second one is set up.

- **Plain JVM tests** are most of them: `Scoring`, `PerformanceCalculator`, the parsers, the
  chunking. Functions of their arguments, no Android, milliseconds each.
- **Robolectric tests** open a real SQLite database in a plain unit test, which is the only way an
  `onUpgrade` path can be checked without a phone. See the migration note under **Gotchas**.
- **Compose tests, since 2026-09-12**, drive the UI in the same run — `createComposeRule` hosted by
  Robolectric, so there is no `src/androidTest` and no device or emulator in the loop. Robolectric
  needs **Java 21**; CI pins it, and the JBR above is 21.

**Why this exists at all.** The box that disappeared on the first letter (2026-09-11) reached the
owner's phone and was reported from it, and nothing in ninety-odd test files could have caught it:
every one of them was a function of its arguments, and that fault was a composition losing state it
should never have been holding. The working agreement above says not to verify on screen, which is
right — screenshots are expensive twice over — but it left the UI with no net under it at all. This
is the net, and it costs seconds rather than an emulator.

- `PageHeaderTest` holds the 2026-09-11 fix in the shape it was reported: open the box, type into
  it, **rebuild the header from scratch**, and the box is still open with the letters in it. The
  rebuild is the whole mechanism of the bug, so the test does it on purpose with a `key(generation)`
  it can bump.
- `RecommendationTableTest` holds the property the 2026-09-09 table rebuild was for — no width shows
  fewer columns than a narrower one — by moving the container across the three real containers this
  app is drawn at (614 / 682 / 715dp) and asserting every column at each. That mistake has been made
  twice here, once in the table and once in the `TodayCard` tile grid.
- **One `setContent` per test.** The rule refuses a second, so a property that varies with width is
  tested by making the width `mutableIntStateOf` and moving it, not by calling `setContent` in a
  loop. That failure reads as "has already set content" and says nothing about the loop.
- **Find the field, not the placeholder.** `onNodeWithText("Search stocks")` reaches the `Text`
  drawn under an empty field, which has no `SetText` action and refuses `performTextInput`.
  `onNode(hasSetTextAction())` is the field.
- **The header's icons arrive with the last of the collapse**, so a test that wants to press one
  passes `collapse = 1f`. At `0f` there is nothing to press, which is the header working as designed.

## Devices and their real geometry

| Device | Serial | Width |
|---|---|---|
| Fold 7, unfolded | `RFCY70BT1VP` | 1968×2184 @420 = **750 dp** |
| Fold 7, cover | same | 1080×2520 @420 = **411 dp** |
| Huawei tablet | `5DKBB25B27201723` | 1840×2800 @360 = **818 dp** |
| Emulator AVD | `EGX_Pixel_10_Pro_Fold_API_36` | pin with `wm size`/`wm density` |

The emulator's own inner display is 851 dp, which is **wider than any real device here**. Layout
thresholds verified only at 851 dp have shipped broken to the 750 dp Fold before. Pin it:

```bash
"$ADB" -s emulator-5554 shell wm size 1968x2184; "$ADB" -s emulator-5554 shell wm density 420
```

The AVD config lives at `~/.android/avd/EGX_Pixel_10_Pro_Fold_API_36.avd/config.ini`. It must keep
`hw.gpu.enabled=yes` and `hw.gpu.mode=host` — with software rendering the emulator crawls badly
enough that taps land seconds late. Cold-boot with `-no-snapshot-load` rather than wiping data.

## Where things live

- `data/AnalysisRepository.kt` — builds the cloud request, chunks sources, harvests the answer.
- `data/SourceReadings.kt` — what one run read out of one message, in a shape the next run can use,
  so a second schedule in a day does not pay to read the same cards again. See **What a run sends,
  and what it does not send twice** below.
- `model/ModelSuitability.kt` + `model/CloudModelInfo.kt` — which models the picker offers. A run
  sends screenshots, so image input is the bar: OpenRouter states its modalities and is believed,
  and the providers that answer with bare ids have their names read. An unrecognised name is
  unknown rather than rejected — the picker hides it, Show all and typing an id still reach it.
- `data/ModelUsageStore.kt` + `model/TokenUsage.kt` — what each model has cost in tokens, summed
  from the `usage` block on every answer. Device-local and never synced: a token count describes
  one phone's spending. A run's own total is in its diagnostics; this is the lifetime tally, and
  the only place Ask AI's spending appears at all.
- `model/AnalysisChunking.kt` — 8 images per request. Beyond ~32 the model loses track of which
  image it is citing, which produced exclusions naming the wrong card.
- `model/ExtractionPlan.kt` — which images a run sends and under which of its own numbers, stated
  apart from the chunking because a source read by an earlier run keeps its number and is not sent.
  See **What a run sends, and what it does not send twice** below.
- `data/ConsolidatedParser.kt` — the model's JSON into `ConsolidatedRecommendation`.
- `model/RecommendationEdit.kt` + `ui/EditCallSheet.kt` — correcting what the model misread off a
  screenshot, as an overlay on the report rather than a rewrite of its answer. See **Correcting a
  misread call** below.
- `model/Scoring.kt` — how a call is judged. See below.
- `data/IntradayRepository.kt` — five-minute bars for the sessions daily figures cannot order,
  hourly bars for the stocks no daily feed carries at all, and the kept archive below. See below.
- `data/PriceSeriesStore.kt` + `model/SeriesHarvest.kt` + `model/PriceSeriesSummary.kt` — the
  five-minute record of every session, copied before the feed forgets it, in a database of its own.
  See **Keeping the sessions the feed forgets** below.
- `data/DailyFromIntraday.kt` — bars aggregated into daily sessions, for those stocks. See below.
- `model/CallSanity.kt` — whether a call's levels can be believed. See below.
- `model/CallShortlist.kt` — which card is worth a paid question. See below.
- `model/CallAlerts.kt` + `data/CallAlertNotifier.kt` — a stock reaching a buy zone nobody took.
- `model/PerformanceCalculator.kt` — per-channel and per-session rollups, and the ranking.
- `data/CrashLog.kt` — what the app was doing when it died, kept in `filesDir` until somebody asks
  for it. Installed before anything else; travels out with Save diagnostics. See **Reading a problem
  off a device**.
- `state/LiveUpdates.kt` + `state/StatusChannel.kt` — the updater, which is the one region that
  could leave `LiveAppState`, and the status line it needed to be able to write from outside it. See
  **How big `LiveAppState` actually is**.
- `model/SettledCall.kt` — the verdict of a call the market has finished with, frozen once and never
  replayed. See below.
- `ui/ChannelScoreSheet.kt` — how a source is scored, opened by pressing its card in the ranking.
- `data/PriceHealth.kt` — which stocks the feed has gone quiet about and what it costs. The Settings
  card that explained it in words was removed on 2026-09-03; one line in `Data and backup → Prices`
  is what is left on screen, and `feed_checks` / `feed_faults` in `LocalDataStore` are the log that replaced
  the rest of it, for a diagnostics copy read off a device. See below.
- `data/PortfolioCalculator.kt` + `model/Position.kt` — the trades the user actually took. See below.
- `model/TradeAlerts.kt` + `data/TradeStatusNotifier.kt` — what has changed about a trade since the
  user was last told, and how the phone says so. See below.
- `model/SessionDigest.kt` + `ui/TodayCard.kt` — what the market did on one trading session, and
  the card that says so on Portfolio and Insights. See below.
- `data/AnalysisPolicy.kt` + `model/RuleSet.kt` + `model/BuiltInRules.kt` — the local wording filter.
- `data/PromptComposer.kt` — generates the prompt sent to the model.
- `data/ReportSync.kt` + `data/RuleSync.kt` + `data/PositionSync.kt` — what travels between devices.
- `ui/RecommendationTable.kt` + `ui/RecommendationCard.kt` — a report's calls, as a table above
  600dp of container and as cards below it. See **A report's calls on screen** below.
- `ui/OccurrenceSheet.kt` — one call in full, opened by pressing a table row. See **The sheet a row
  opens** below.
- `data/XlsxWriter.kt` + `ui/ReportExport.kt` — a report as a spreadsheet, saved to Downloads or
  sent onward from the ⋮ menu on its card. See below.
- `data/Backup.kt` + `data/BackupRestore.kt` + `ui/BackupSection.kt` — the whole record as one file,
  the way back in from one, and the three buttons in Settings. See below.
- `ui/PortfolioScreen.kt` + `ui/PositionCard.kt` + `ui/TradeControls.kt` — the Portfolio tab, one
  trade's card, and the Bought button and closing controls that sit on a recommendation card.
- `ui/CommonUi.kt` holds `Figure` and `FigureGroup`, and `ui/DesignSystem.kt` holds `AppDates` —
  the one figure layout and the one set of date patterns, for every screen that draws either.
  `CommonUi.kt` also holds `ActionPill` and `DisclosureButton`, the two kinds of button a card is
  allowed to carry, and `SettingsButton`, the one a settings page carries. See **A button on a card
  is one of two things** under Gotchas.
- `ui/PageHeader.kt` — the page's own name and icon at the top of every screen, shrinking as the
  page is read, and the two controls that arrive with the collapsed bar: the page's stock filter,
  and the icon that opens the rest of its filters. It replaced the `EGX Analyzer` band on
  2026-09-09. See **The page header** below.
- `ui/TickerPicker.kt` + `ui/PageStocks.kt` — the catalog under that stock filter: which listings to
  offer somebody typing, with the ones the tab actually holds first, and where that set comes from
  for each tab. See **The stock filter, moved into the header** below.
- `ui/Filters.kt` — `FilterSheet` and its sections, which is where a page's filters live since
  2026-09-09; `FilterRow` and the chip-and-menu filters beside it, still used by the in-report
  toolbar. See **A page's filters live in a sheet** under Gotchas.
- `ui/EgxAnalyzerApp.kt` holds `AppStatusLine` — the one line that says what the app is doing or
  has just done, drawn by `Screen` under the page's own title. See **The status line** below.
- `model/ScheduleClock.kt` + `model/MarketRefresh.kt` + `model/CloseSweep.kt` +
  `model/SeriesHarvest.kt` — when the three things this phone does on its own fire.
  `ScheduleClock.lastFinalSession` is also the one answer to "has that session closed", which the
  scorer and the still-trading flag both read. See below.
- `model/AnalysisPlan.kt` — what a run covers, said explicitly, so the screen and the manual Analyze
  path build the same request.
- `data/JobScheduler.kt` + `data/ScheduleReceiver.kt` + `data/ScheduledJobWorker.kt` +
  `data/ScheduleMigration.kt` — the alarm, the things that mean re-book it, what runs, and the
  one-time move off the old job table. See below.
- `ui/PricesSection.kt` carries the price-refresh checkbox, the series-archive checkbox, the
  never-blank status lines and Fetch prices now, drawn as the `Prices` group inside Settings'
  `General` card. It also carries `SystemPermissions` — the exact-alarm and battery-optimization
  rows both features depend on.
- `data/OpinionPrompt.kt` + `data/OpinionPromptStore.kt` + `data/OpinionSearchBrief.kt` +
  `data/OpinionParser.kt` + `ui/StockOpinionSheet.kt` — Ask AI, on a call card in Insights.
  See below.
- `ui/NavStack.kt` — one step of history, so back undoes a jump the app made on the reader's
  behalf. See **What back does** below.
- `ui/StockSheet.kt` — everything the app knows about one ticker, in one sheet. `LocalOpenStock` is
  how a ticker anywhere opens it. See below.
- `ui/StockTrend.kt` — `PriceChart` and `DayRange`, the two drawings on that sheet: where a stock
  has been over the chosen range with the levels it is judged against drawn across it, and where its
  close sits inside its own day. `ChartRange` is the 1W/1M/2M/3M/6M row under the chart.
- `model/ApproachAlerts.kt` + `data/ApproachNotifier.kt` — a trade closing on its stop or target 2,
  said while there is still something to decide.
- `data/SessionDigestNotifier.kt` — what the whole session did, once, after the close.
- `data/AttentionNotifier.kt` — the two ways this app stops working without anything looking wrong:
  a feed that has gone quiet, and a schedule that did not run.
- `data/TradeActionReceiver.kt` — Keep open, pressed from a notification with the app closed.
- `data/AppShortcuts.kt` + `res/xml/shortcuts.xml` — long-press the launcher icon.
- `data/TodayWidget.kt` — the home-screen widget, and the only Glance in the codebase.
- `ui/CallText.kt` — one call as plain text, for the ⋮ on a call card.
- `ui/InfoSheet.kt` — `InfoNote`, the question mark that opens one, and the `SettingToggle` /
  `SettingLabel` rows every explained control is built from. See **Where an explanation lives**.
- `data/EgxCatalog.kt` + `data/EgxSeedStocks.kt` — what a stock is called, and the table it is
  called from. See **What a stock is called** below.
- `ui/` — one file per screen, plus `CommonUi.kt` and `DesignSystem.kt` for shared pieces.

## The UI and the engine

The `ui` package imports **nothing** from `data`. That is a rule, not an accident, and the way back
to it if it ever breaks is `grep -r "egxanalyzer.data" app/src/main/java/com/ikverse/egxanalyzer/ui`
returning nothing.

- **`ui/AppState.kt` is an interface** — 64 read-only properties, 2 writable ones, and the actions.
  It is the whole of what a screen may see and do. Nothing else in `ui` knows a repository exists.
- **`state/LiveAppState.kt` implements it**, and is the only thing holding repositories, the
  database, the network and a `Context`. It is built once, in `EgxApplication`.
- **`debug/.../ui/preview/FakeAppState.kt` also implements it**, entirely out of constructor
  defaults, so `ui/preview/ScreenPreviews.kt` draws every screen in Android Studio with no app
  running. In `src/debug`, so none of it ships.

Two rules keep it that way:

- **A screen takes `appState: AppState` and never an `Activity`.** An activity cannot exist in a
  preview, and one parameter of it high up the tree is what forces every screen below to take one.
  The single exception is the fold lookup in `EgxAnalyzerApp`, which reads `LocalContext` and
  tolerates null. Anything else needing the platform - a permission, a settings page, a share sheet,
  a file - is a method on the interface, implemented once in `LiveAppState`.
- **Pure calculation belongs in `model`, not behind the interface.** `PerformanceCalculator`,
  `AnalysisChunking`, `RuleSet` and `isEgx33` are functions of their arguments with no Android and
  no I/O in them, so a screen calls them directly. Routing those through `AppState` would have made
  the interface a phone book. What goes behind the interface is anything that touches a device.

### How big `LiveAppState` actually is, and what can leave it

4,427 lines, of which **2,674 are code** — 35% of that file is documentation, so the headline number
overstates it by a third. It is still the largest thing here and every change touches it, so on
2026-09-12 it was asked what could be lifted out. The answer was: one region, and the reason the
rest cannot go is worth writing down so it is not rediscovered.

- **The updater went**, to `state/LiveUpdates.kt`. It reads no price, no report and no trade; it is
  a function of `UpdateRepository`, one preference and the status line, and nothing recomputes when
  it moves. `AppUpdates` is the sub-interface carved off `AppState` for it, and `LiveAppState` says
  `AppUpdates by updates`, so all nine members arrive with **no forwarding written by hand**.
- **`StatusChannel` is what made it possible.** `statusMessage` was a property of `LiveAppState`, so
  anything that needed to say something had to *be* that class — which is a good part of how the
  file grew. The line now lives in a small object both hold, and `LiveAppState.statusMessage` reads
  and writes straight through to it.
- **Built as a constructor parameter with a default**, because a default may read the parameters
  declared before it but never `this`. That is what keeps this a one-step build: a collaborator
  needing the host's `appScope` or its cached `appPreferences` would have to be made first and told
  about its host afterwards, and a half-built collaborator is a worse thing than a long file.
- **Ask AI, the backup and the exports did not go, and it is the same reason each time.**
  `askAboutCall` reads `performance`, `portfolio` and `runAction`; the backup reads `databaseFile`,
  `checkpointDatabase`, `settingsDocument` and `restoreFrom`. Those are the heart of the class, so
  handing a collaborator live references back into its host would be the same object graph with an
  extra hop in it — longer to read, not shorter. **Splitting those is a design change about who owns
  the recompute, not a move**, and it was left alone rather than churned on a guess.
- The pattern is now here for the next region that earns it: sub-interface on `AppState`, a class in
  `state/`, everything it needs as a parameter, `by` at the declaration.


## Feature documentation

Detailed documentation lives in [`docs/`](docs/):

- [`docs/engine.md`](docs/engine.md) — What a run sends, scoring rules, ranking, call lifecycle
- [`docs/portfolio.md`](docs/portfolio.md) — Portfolio, trade alerts, session digest
- [`docs/features.md`](docs/features.md) — Ask AI, reports, corrections, export, backup, sync, update, scheduling
- [`docs/ui.md`](docs/ui.md) — Back/stock sheet, page header, filters, status line, settings, colors
- [`docs/prices.md`](docs/prices.md) — Price feeds: incremental fetching, scale breaks, the ISIN and legacy endpoints
- [`docs/release.md`](docs/release.md) — Signing, ABI splits, R8 and what minification cost once

## Gotchas

- `local.properties` holds `telegramApiId` / `telegramApiHash` and is gitignored. Absent, the app
  falls back to asking for them, so a fresh checkout still builds.
- `Uri` is stubbed in unit tests; tests that need inputs use `AnalysisInput.Text`.
- **There are two databases now, and only one of them is the record.** `egx_analyzer.db` is
  everything the app knows; `egx_price_series.db` is the five-minute archive, deliberately outside
  every backup and every diagnostics copy. Nothing reads the second, so a change to `LocalDataStore`
  never has to think about it — but a change to `Backup.kt` or to Save diagnostics that starts
  sweeping up "the app's databases" would pull in a file sized in hundreds of megabytes. See
  **Keeping the sessions the feed forgets**.
- `LocalDataStore.DATABASE_VERSION` — bump it and add the table to **both** `onCreate` and
  `onUpgrade`. Currently 28. **Bumping the constant is half of it**: `session_events` was added to
  both hooks and left at 20, so a fresh install had the table and every upgrade silently did not —
  which fails at the first write and nowhere earlier. `SessionEventStoreTest` caught it. Adding it to only one of the two is the mistake that gets made:
  `CallAlertStoreTest` caught exactly that on version 19 before it shipped.
- **Migrations are tested** — `LocalDataStoreMigrationTest` runs under Robolectric, which supplies
  enough of Android for a real SQLite database in a plain unit test. It writes the version-9 table
  by hand and upgrades it, deliberately: a test that builds its "old" schema from today's code
  tests nothing, because both sides move together. Add a case there for every version bump —
  version 14 has its own in `ScheduledJobStoreTest`, which writes the version-13 `positions` table
  and checks that gaining `scheduled_jobs` did not cost the trades already on the phone, and
  version 15 has the same case in `StockOpinionStoreTest` for `stock_opinions`, version 16
  has one beside it for the findings columns, version 18 has one in `TradeStatusStoreTest` for
  `position_status_seen`, version 19 has one in `CallAlertStoreTest` for `call_alert_seen`,
  version 20 has one in `SettledCallStoreTest` for `settled_calls`, version 21 has one in
  `SessionEventStoreTest` for `session_events`, and version 23 has one in `StockOpinionStoreTest`
  for the `standing`, `forecast` and `checks` columns — that last one writes the **version 22**
  table, which is what every phone that has ever pressed Ask AI is actually on, so it is the
  upgrade that runs on a real device rather than the oldest one that still can, and version 24 has
  one in `LocalDataStoreMigrationTest` for `is_t_plus_one` on `positions`, written against the
  version-23 table for the same reason, and version 25 has one beside it for
  `position_approach_seen` and `session_digest_announced` — the two-tables case, which is the
  shape this trap is usually walked into, and version 26 has one beside *that* for the four
  split-exit columns on `positions`, written against the version-25 table because that is where
  every phone holding trades actually is, and version 27 has one in `FeedHealthStoreTest` for
  `feed_checks` and `feed_faults`, written against the version-26 table for that same reason, and
  version 28 has one in `LocalDataStoreMigrationTest` for `source_reads` on `analyses`, written
  against the version-27 table because that is where every phone holding reports actually is.
  **That one arrived red and is the trap in its sharpest form**: `analyses` predates every other
  table here, so it lived in `onCreate` alone — which held until a column was added to it by
  `ALTER`, because a hand-built old database does not hold that table at all and answers the ALTER
  with "no such table". One new column turned **twenty-one** migration tests red across six files,
  none of them about reports. `createAnalyses()` is now in both hooks like everything else, which
  is the same rule one line up, read the other way round: it is not only a new table that belongs
  in `onUpgrade` but any table an `ALTER` is about to name
  — added by `ALTER`, one guard per column, so the risk
  is not that the upgrade fails but that it takes the answers already on the phone with it. Note
  Robolectric coexists with the explicit `org.json` test dependency, which was the risk when it
  went in. **Robolectric needs Java 21** to stand up a sandbox for SDK 36 — it refuses on 17 with
  "requires Java 21 (have Java 17)", which is a green run locally on the JBR and a red one anywhere
  pinned lower. CI pins 21 for that reason.
- **A shortcut's `<extra android:value="true"/>` is a String, not a boolean.** It was written that
  way first and the shortcut silently did nothing: `getBooleanExtra` handed back its default and the
  app opened on whatever tab it was already on, which is indistinguishable from a shortcut that is
  not wired up. Both static shortcuts name an **action** instead, and `AppShortcuts.setOverdue` uses
  the same one so the launcher's two entrances are read one way. A shortcut intent must carry an
  action at all, or the launcher refuses it outright.
- **Glance is the only second UI toolkit here, and it is for the widget alone.** A widget is
  RemoteViews drawn by the launcher, so it genuinely is a different thing rather than another screen;
  Glance is what keeps it written in the same idiom instead of in an XML layout. Nothing outside
  `data/TodayWidget.kt` imports it, and that file imports nothing from `ui` but `AppDates`. **It
  reads and never computes** — the digest comes out of `session_events` and the overdue count off
  `SettingsRepository.lastOverdueCount`, a deliberate cache. Rebuilding the portfolio would be the
  most expensive thing in the app running on the cheapest surface it has, in a process the system is
  free to kill halfway through. The widget declares **no `updatePeriodMillis`**: the record changes
  when prices arrive rather than on a clock, and the app pushes a redraw from the same callback that
  counts overdue trades. The one colour it needs is written out, because a widget has no access to
  `MaterialTheme`.
