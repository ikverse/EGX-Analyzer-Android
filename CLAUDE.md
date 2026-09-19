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
- `model/AnalysisProgress.kt` — how far along a run is, reported by `AnalysisRepository.analyze`'s
  `onProgress` and drawn by `RunProgress` on Analyze and by the action's own label. **Determinate
  while it reads and indeterminate while it writes**: `ExtractionPlan.chunks()` hands back the whole
  list of batches before the first one is sent, so the reading is countable, and the consolidation
  is one answer of unknown length that becomes two or three if it needs correcting. A bar covering
  both would walk backwards on a correction, and `RunningLabel`'s comment used to say — correctly,
  at the time — that any figure claiming to know how far along a run was would be invented.
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
  is one of two things** under Gotchas. It holds two instruments as well: `StatStrip`, a handful of
  counts bounded and divided so they are read against each other (a report card's figures and the
  token tally's), and `ShareBar`, how one quantity divides in two — deliberately not `OutcomeBar`,
  which is four fixed verdicts in four fixed colours with a legend drawn above the cards using it.
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
- `ui/StockLogo.kt` — the mark beside a name. `StockLogo` is the company's own, bundled for 222 of
  223; `ChannelAvatar` is Telegram's picture of a source, drawn on the Insights ranking out of the
  same cache `ChannelsSection` reads. Both fall back to one `Monogram`, and for the channel that
  fallback is the ordinary case rather than a failure: a pruned cache, a report synced from the
  other phone, and an on-device import all arrive with no path.
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
- `PriceRepository` fetches **from where a stock's stored history stops**, via `period1`/`period2`,
  not a fixed range. It used to ask for `5d`: a phone left shut for a week got a hole that every
  later refresh stepped straight over, permanently — and a call whose window contains a hole never
  completes, so it never expires and never shows as overdue. A stock with an open trade is fetched
  from that trade's call date instead, which heals holes that already exist. An empty dated response
  falls back to `range=1y`, so the change cannot do worse than the fixed range it replaced.
- **Every fetch is checked for a change of scale before it is stored** — `PriceSanity`, called from
  `PriceRepository`. A move beyond 30% between two sessions no more than a week apart is not
  something the exchange permits, so it is a corporate action: a split or a bonus issue. The check
  runs across the **boundary between what is stored and what was just fetched**, because that is
  where it always falls — incremental fetching is what leaves the two halves in different money, and
  Yahoo rewrites its own history when a stock splits. So the first response is to **refetch the
  whole year and replace the stored series** (`deleteSessionsFrom` + `clearPriceBreaks`), which
  heals it. Only a break that survives that is recorded, in `price_events`, and it is recorded
  rather than corrected: guessing a ratio and rescaling a year of prices would be the app inventing
  history. Breaks are **local and deliberately not synced** — every device fetches the same public
  feed and reaches the same conclusion, and a device's opinion about a feed is not evidence.
- **The ISIN feed's *daily* endpoint serves only the newest session; its *intraday* endpoint holds
  the history.** Measured 19 August 2026 across all 262 Cairo listings: 257 returned exactly one
  daily session and 236 of those only that day — `^CASE30` included, so it is no longer the way to
  ask whether the exchange traded. The same symbols answer `interval=5m` with about four weeks and
  `interval=1h` with about two years. History therefore comes from the **legacy `SYMBOL.CA`** feed,
  which is alive and deep (25 sessions in a 40-day window), and the ISIN symbol contributes the
  current session. This is why `fetchAllFeeds` must keep reading both — dropping the legacy feed
  would leave every stock with a one-day history.
- **A stock with no legacy symbol had no daily history at all, and now has a rebuilt one.** VLMRA is
  the case: `VLMRA.CA` is a 404, so the merge had only the ISIN feed's single session and a call
  made on it kept a permanent hole in its window — which never completes, so the call never expired
  and sat pending for good, outside every rate. `IntradayRepository.dailyHistory` aggregates the
  intraday feed into daily sessions instead. See **Rebuilding a history the daily feeds do not
  carry**.
- **The legacy `SYMBOL.CA` feed ignores `interval` and answers with daily rows.** Nothing in the
  response shape says so — only `meta.dataGranularity` does. Taken at face value, the one daily bar
  it returns would be read as the whole session and would "prove" that the entry and the target
  happened at the same instant: a confident verdict on a question the feed was never asked.
  `parseSessionBars` checks the granularity against whatever was **asked for** and refuses a
  mismatch, and intraday is only ever requested against the ISIN symbol. Also measured: a 5m window
  reaching back **59 days answers, 90 days is refused outright with HTTP 422** rather than trimmed,
  so the window has to be clamped by the caller or the whole request fails.
- **A frozen feed is not the same as an unpriced stock, and is harder to see.** `unpriced` means no
  history at all; `stale` means the series answers every request while its newest session stays put.
  That has happened here — the ISIN migration — and nothing noticed at the time. Seven days, which
  clears the Friday–Saturday weekend plus a public holiday. Both are now standing state on Insights
  rather than a count in a toast — see **When the feed goes quiet**.
- **The bar follows every page turn except the ones a travel started in the shell passes over, and
  the effect that scrolls is keyed on the pager.** The bar and the pager move the same pointer, so
  each follows the other, and all three ways that link can go wrong have now been shipped. Keyed on
  `appState.destination`, the scrolling effect was restarted by the very thing it was meant to serve
  — a swipe publishes its own arrival — and the restarted copy compared a target read at
  recomposition against a page read a frame or more later, so a second swipe arriving inside that
  window scrolled the reader **back to the page they had just left**. One `LaunchedEffect(pager)`
  owns the whole link now; nothing the pager says can restart it, so no stale target survives to be
  acted on. The guard against the write-back was the other two faults, and both came of the guard
  naming the **gesture** rather than the travel. Raised *inside* the scrolling coroutine it went up a
  frame after the scroll started and down a frame after it ended, and a page that turned over inside
  either gap was swallowed. Read from the pager's own `interactionSource` instead — up on
  `DragInteraction.Start`, down when the pager reported itself at rest — it was no better, because
  **Compose runs a swipe as two scroll sessions**, the finger's and the settling fling's, and
  `isScrollInProgress` reads false in the gap between them: the guard came down in that gap, so a
  *flick* — where the page is decided by velocity on the fling rather than by crossing the halfway
  mark under the hand — turned its page with the guard already closed and left the bar lit on the tab
  the reader had just left. A slow drag past halfway still worked, which is what made it look
  intermittent, and the same flag silently dropped a tap made while a swipe was still settling. The
  guard is `travelling` now: the page a travel started here is heading for, `null` otherwise. It is
  written by the only coroutine that scrolls, before it suspends, so nothing about how Compose splits
  a gesture into sessions can reach it. Page and guard are read in **one** `snapshotFlow` pair, so a
  turn is never delivered against a guard that changed after the turn was taken. What falls out of
  that: every turn the reader causes is an arrival however it turned — under the hand, on the fling,
  or as the pager settled — while the pages a travel crosses stay silent, and so does **the page a
  travel is abandoned on when a second send replaces it**, which left to speak wins the race against
  the send that cancelled it. Sends run through `collectLatest`, which starts each block
  **undispatched**, so a replacement raises the guard in the same continuation the cancelled block
  lowered it in and the abandoned page never gets a frame to name itself. A travel is outranked by
  nothing but a hand, so a tab tapped while the pages are still coasting is answered rather than
  dropped; a drag refuses it outright, and that refusal is swallowed — the guard comes down, the
  gesture names where they land, and if it puts the pager back on the page it set out from, turning
  no page and so naming nothing, the resting page is published so the bar cannot be left on a tab the
  pager never travelled to.
- **Pressing the destination already showing takes that page back to the top.** The press every
  bottom bar on this platform answers, and this app answered it with nothing — the way back from a
  session card deep inside Insights was to scroll all of it by hand. `AppState.scrollToTopRequest`
  is a **destination and a counter**, and both halves are load-bearing: the destination because the
  pager keeps the neighbouring pages composed, so a bare signal would take those to the top too and
  throw away a scroll position on a tab nobody touched; the counter because a second press is a
  second request, and a value that repeated would restart no effect. The shell publishes it through
  `LocalScrollToTop` from `DestinationScreen` — the one place both shells build a page and so the
  only place that knows which destination is being composed — and `Screen` animates the scroll, so
  all five pages get it from one change. **Deliberately not folded into `navigate()`**: the pager
  calls that on every swipe and its `snapshotFlow` reports the page it is already on the moment it
  starts collecting, so a scroll-to-top in there would fire on first composition and again on every
  settle, throwing the reader to the top for having swiped to a tab.
- **The Analyze action never leaves, and it follows the bar down.** It used to go with the bar on
  the same scroll — tidy, and it meant the one control that starts a run was reachable only from the
  top of the page. It is always on screen now, and rather than holding its height over the hole the
  bar leaves, it travels into the bar's own place: `animateDpAsState` between
  `NavBarFootprint + PillBottomMargin` (94dp, clear of the bar and then the same gap again, so the
  two read as one stack) and `PillBottomMargin` (10dp, the bar's own float off the bottom). The
  travel is exactly `NavBarFootprint`, which is what makes it land there rather than near there.
  **This was the rule `toastClearance` in the shell followed**, back when an outcome was a toast at
  the foot of the screen — same problem, same `animateDpAsState`, and its comment said why: "so a
  toast raised on a scrolled page does not hang over the gap where the bar used to be". The status
  line lives in the header now and that clearance is gone with it, so the action is the only
  floating chrome left that has to follow the bar at all. The wide layout draws it unconditionally with no bar to follow, so all of this is
  the compact branch only.
- **The action's ground is 0.84 in both states, against the bar's 0.94.** `actionFill` and
  `actionAuroraBase` carry the same figure deliberately. The bar tidies itself away while a page is
  read and the action does not, so the action is a permanent object over a page still being
  scrolled — and at the bar's opacity it reads as a slab parked on the page rather than as a control
  floating above it. The two states match because the transparency is a property of the button, not
  of one of its states: a button that changed weight the moment a run started would report the run
  twice, once in a way nobody could name. Only the grounds carry it — `onAction` stays opaque so the
  label survives whatever scrolls behind, and the `actionAurora` circles are the light *inside* the
  ground, so thinning those would dim the one thing saying a model is working.
- **The action's edge is a gradient in its own colours, and the bar's beside it is not.** Both are
  `FloatingSurface`, so they are the same material; they are deliberately not the same edge, because
  two identically outlined slabs at the foot of the screen said nothing about which of them did
  anything. `PageAccent.actionLine` carries it, and it has **its own stops rather than
  `actionFill`'s** for the reason `aiLine` has its own beside `aiFill`: the fill sits *inside* the
  line, so a line in the fill's colours is a line against itself and disappears. What it has to read
  against is the page scrolling behind the button — dark on one theme, near-white on the other — so
  the stops invert between the two while the hue does not. The hues are the aurora's own, in the
  aurora's own order, rather than a fourth set invented for the edge — derived per page from the
  seed table rather than written out five times, so the relationship between a page's fill, edge and
  aurora is stated once.
- **The edge is 0.74 against the ground's 0.84, and its hues run about a third under the aurora's.**
  It shipped opaque and full-strength on the argument that an edge letting the page through stops
  holding the shape — which was wrong on the device: it read as a bright cyan wire around the button,
  the loudest thing on a dark page and competing with the label it was meant to frame. The shape
  still holds, because what draws it is the contrast with the page rather than the weight of the
  line. `ActionPaletteTest` pins the properties and not the figures — nothing the action is drawn
  with is solid, every stop in a ramp shares one alpha (a ramp that fades along its length reads as
  a mistake), and the edge shares no stop with the fill, which is the slip that produces an invisible
  edge: reaching for `actionFill` when adding the gradient, because it is right there and already
  the right family. **Every case sweeps all five accents in both themes**, since the ramps are
  derived per page now and a property that holds for the one hue somebody looked at is exactly the
  kind that quietly fails on the other four.
- **Only the ready state wears it.** Running keeps the red `aiStop` hairline: that is the only state
  where pressing cancels, and no edge in the action's own colours could say so — the moving fill
  says a model is working, which is a different sentence. Blocked keeps the neutral outline every
  other floating thing has, for the reason it does not wear the fill either: a blocked button with
  the action's own edge round it would be inviting a press that does nothing.
- **`FloatingSurface.outline` is a `Brush`, not a `Color`.** One parameter rather than two that can
  both be set and disagree; a flat edge passes `SolidColor(…)`. The width stays 1dp for every
  floating edge in the app, gradient or not — the bar sits directly under the action on a compact
  screen, and an edge thicker on one of them would read as the two not matching rather than as one
  of them being the control. The colour is what separates them.
- **Every pill in the app is one shape and one of two heights**, `PillShape` and `LabelPillHeight`
  / `PillHeight` in `DesignSystem.kt`. Before 2026-09-11 they were all `CircleShape` at whatever
  height their own padding produced, which is what the owner reported as pills "too rounded" and
  "different sizes in one card": a capsule is a shape nothing else on a screen of 14dp cards makes,
  and a height derived from padding moves with the text, the font scale and whoever wrote that
  particular chip. Six families had grown - the ring on a card, the button on a card, the EGX 33
  badge, the stock sheet's filled flags and two chips spelled inline in the table - across four
  corners and five heights. They are one now: **6dp**; **20dp** for anything only read, with the text
  centred in a fixed box rather than propping it open; **32dp** for anything pressed, where the extra
  12dp is the fingertip. **The 6 is not a free number and 8 was tried first.** `CircleShape` takes
  half the *shorter* side, so the capsule on a 20dp pill was already only a 10dp corner - 8dp moved
  every label pill by two, shipped, and looked identical on the device, while making the EGX 33 badge
  *rounder* than the 4dp it had. 6dp is the largest corner that visibly cuts a 20dp pill. `OutlinePill` is the ring and
  `FilledPill` is the block of colour, and both draw one `PillLabel`, so a pill's padding and type
  cannot be restated anywhere. **What stays round is round by nature** and not by drift: avatars,
  logos, the empty state's glyph, the navigation indicator, the header's search field, `DayChip`
  (a seven-across day toggle) and `RiskRewardBar` (a bar, not a pill).
- **A card says its pills in one place.** The position card drew the status chip in the header and
  the rest of them a block lower down, and the call card stacked Edited over Timing in the top-right
  corner - against a name block three or four lines tall, so the one annotation every call card
  carries floated at the very top aligned with nothing: not the ticker's line, not the menu beside
  it, not a figure underneath. Both cards are now the same shape - identity on the left of the
  header, the menu on its right, every pill on one row beneath it, starting at the card's own inset
  so it lines up with the ticker above and `ENTRY` below. The status chip leads the position card's
  row (which is unconditional now, since a status is always there to say). No fact was added or
  removed by either move. The call card's own regroup was missed on the first pass and reported on
  2026-09-11 as pills "at the very top of the card, not aligned, placed randomly".
- **The EGX 33 badge is deliberately outside `PillShape`**, and was folded in once and had to come
  back out the same day. It is a 4dp square because round its outline sits concentric with the star
  inside it and the pair reads as a settings cog; the shared 6dp made it rounder than the 4dp it had
  and it read as a gear on the device within the hour. The pill rule is about labels, and this
  carries no wording.
- **T+1 is neutral, like every other note pill.** It was `primary` in all three places that draw it -
  the call card, the position card, Insights - on the reasoning that it is neither a verdict nor a
  warning and so may take the app's own voice. True, and not a reason to be the one differently
  coloured ring in a row of them; the wording already says the call named its own deadline. Asked
  for on 2026-09-11. It is still tappable in the two places it explains itself.
- **A button on a card is one of two things, and its colour says which.** `ActionPill` in
  `CommonUi.kt` is anything that changes the record — Bought, Sold, Keep open — as a `PillHeight`
  ring in the app's own `primary` at half strength. `DisclosureButton` is anything that only opens
  or closes a section — View / Hide recommendations, Source, Source trace — as a bare `primary`
  label with an arrow that flips with the section. They were a filled tonal button, two outlined
  ones and two text ones, which said that recording a purchase is a heavier act than recording a
  sale, and put those two in one row on a position card disagreeing about it. **Three things are
  deliberately outside the system**: the Ask AI pill, because violet is the model speaking and the
  one hue on these screens that is not a measurement; the Analyze action, which is 56dp of teal
  aurora and a tier above anything drawn on a card (see the entries above); and an `AlertDialog`'s
  buttons, which are Material's convention rather than this app's. **The pills are 32dp and the
  touch target is still 48**, through `minimumInteractiveComponentSize` — the trick the Ask AI pill
  already used, and the reason a smaller button here costs nothing to press. Settings, Backup and
  Prices were finished on 2026-09-09 by `SettingsButton`, the bullet below. **Channels is
  deliberately still Material's own**: its eight `Button`s are the steps of the Telegram sign-in,
  each the sole action of the card it is on and correctly the point of that screen.
- **`SettingsButton` is the third kind, for a page of settings rather than a card about one call.**
  `PillHeight` and `Space.m` against Material's 40dp and 24, and `labelMedium` like both card
  buttons — at the default it was the heaviest thing on a card whose subject is the words beside it,
  standing next to switches 32dp tall. It stays an `OutlinedButton` rather than becoming an
  `ActionPill`: a pill takes the page's own hue to say that pressing it changes the record, and a
  column of cyan rings down Settings is the page of coloured glyphs the question mark is muted to
  avoid. It carries a `filled` flag for the four card primaries — Save and verify, Sync now,
  Download, Install — **because they share a row with the outlined ones**, and a `FlowRow` 40dp tall
  at one end and 32 at the other reads as a layout fault rather than as emphasis. Which button is
  filled did not change; only how tall the row is. `AlertDialog` buttons stay Material's, the same
  exception the card pills make.
- **A report card opens on a press anywhere, and only while it is shut.** `Card(onClick = …,
  enabled = !expanded)` in `SavedAnalysisCard`, with `disabledContainerColor` pinned to the same
  fill so "disabled" does not read as greyed out. Open, that card holds the report's own toolbar,
  its call cards and the source trace, so a card-wide toggle would close the whole report on a tap
  landing in the gap between any two of them. The footer row — a `DisclosureButton`, which replaced
  a full-width filled button doing the same job as the card under it — is what closes it again.
- **Nothing inside the `NavigationRail` may fill its width, and one `fillMaxWidth()` took the whole
  unfolded layout out.** Material sizes a rail with `widthIn(min = ContainerWidth)` — a floor, not a
  width — so a child that fills the width stretches the rail to whatever it is measured against.
  `NavigationSuiteScaffoldLayout` measures the navigation suite against the entire window and then
  hands the page `width - railWidth`, so the rail became the screen and the page was measured at
  zero: five destinations centred on an empty display. It arrived with the app mark on 2026-09-09,
  as `Box(Modifier.fillMaxWidth().height(RailTopInset))` holding the mark in the rail's top gap, and
  was fixed in 3.6.2 by deleting that one call — the rail's column centres its children already.
  **A phone cannot show this**: there is no rail on the compact layout, so it shipped through a
  release that was checked folded, and only the Fold opened shows it. Anything new drawn in the rail
  wants a bounded width for the same reason.
- **A page's filters live in a sheet the header opens, not on a shelf on the page.** The page
  header's filter icon opens a `ModalBottomSheet` holding that page's filters; it replaced
  `FilterBar` on 2026-09-09, the day after the stock box left that shelf for the header.
- **What was wrong with the shelf was what the stock box left behind.** `FilterBar` was a search box
  and a Filters chip on one line, standing off the page on a shadow once a scroll had pushed it to
  the top. Take the box away and it is a chip alone: a loose one-control row above every list, and
  the row was the cheapest thing on it to keep — the chip opened a panel of three more chips, each
  of which opened a menu. Three levels deep for a question the reader asks in one press. The lift,
  the pin, the clamp against the parent's foot, `LocalViewportTop`, the transparent-until-floating
  fill and its `floatingColor` override all existed to make that one row behave on a scroll, and
  all of it went.
- **The trigger is in the header and the content is on the page, and neither could hold the other.**
  The header does not know what a page filters by — channels come off `savedResults`, dates off the
  trades — and the screen is composed below the icon that opens it. So the flag lives on
  `PageState.filtersOpen(destination)`, which is exactly the shape `stockBox(destination)` has
  and for the same reason. It is also why it survives a fold, where a `remember` inside the sheet
  would not: see the head of `PageState`.
- **A sheet, not a panel hanging off the header.** A sheet already means one thing here — the longer
  version of the thing that was pressed, which is what `StockSheet` and `InfoSheet` are — and a
  panel over a scrolling page would have to re-solve, in a second place, the pinning the shelf was
  written to solve. The width is the rest of it: the choices are **shown open**, as chips under a
  heading, where the shelf had room only for a chip that opened a menu. `MultiSelectSection`,
  `SingleSelectSection` and `SortSection` are that; `MultiSelectFilter` and its siblings stay in the
  file because the in-report toolbar still uses them, inside a card where there is no room for
  anything else.
- **The `All` chip leads every section and is selected while nothing else is.** "Untouched means
  everything" is the rule the menus had to state in words; on a surface with room for it, it is just
  the first chip.
- **The sort is below a rule, under its own heading, and there is no `All` in it.** An order is not
  a filter: it hides nothing, `filtersActive` leaves it out and Clear filters does not touch it. On
  the shelf it sat on the same line as the filters and was kept out of the clear-all by a comment no
  reader could see. And a list is always in *some* order, so there is nothing for an `All` to mean.
- **`Clear filters` is on the title's line, not at the foot.** The sections are as long as the page
  has channels, and a button under them is one the reader has to scroll to in order to undo
  something they can see from the top. It is offered on `filtersActive`, which counts the stock box
  too, because clearing means clearing.
- **The dot on the icon is `filtersInSheet`, not `filtersActive`, and that distinction is the whole
  of why it is honest** — it is the same one the shelf drew between `folded` and `active`. The stock
  box reports itself by staying open; a dot lit by it would report something the reader is already
  looking at, and would go on reporting it after they had cleared everything else.
- **Every sheet is composed outside the guard its shelf sat inside.** Results drew its shelf only
  with runs on the page; the Portfolio drew its own inside the Positions card, below the early
  return for an empty record — which is why `PositionFilterSheet` is split out of `PositionSection`
  and called above that return. The icon in the header is there either way, so a sheet that composed
  only sometimes would be an icon that opened nothing. The sections drop themselves when they have
  no options, so an empty page opens on the sort alone.
- **The Portfolio's sheet is titled `Filter positions`.** Its date and its order narrow the Positions
  card; Your record and Overdue are built from the whole portfolio on purpose, so a date picked here
  cannot hide a trade that is late. The shelf said that by sitting inside the card it filtered. A
  sheet reached from the page header would be claiming the page, so the title says it instead.
- **A breakpoint that *adds* content spends the width on itself.** `RecommendationTable` grew columns
  at 620dp and 900dp and `TodayCard`'s tiles capped their grid at four columns, and both produced the
  same result: the wider device showed **less** of what the reader came for — 49% of a table row on
  the tablet against 64% on the smaller Fold. The check that catches it is not "does the breakpoint
  fire at the right width" but **what fraction of the content is visible at each real container
  width**, computed for the Fold's 614dp, the tablet's 682 and the emulator's 715 — and remember all
  three are the *container*, after the rail's 80dp, the page's `Space.l` either side and the card's
  own inset. Both are fixed the same way, and it is worth stating as the rule: extra width goes into
  the elements already on screen, and anything a wider window adds must already be present in some
  form at every narrower one. See **A report's calls on screen** and the tile table under **What
  happened this session**.
- **`AdaptivePanes` is the only "side by side, or stacked when it will not fit" rule in the app**, and
  a second one would be a second threshold, a second fallback and a second gap to keep in step. A
  pair of equals is that helper with `mainWeight = 1f`, not a layout of its own — which is how
  Analyze's **Content types** and **Recommendation target date** now sit beside each other above
  600dp of container: stacked on the 379dp cover screen, 313dp each on the 638dp unfolded Fold, 347
  each on the tablet. Note the outer pane split is 720dp and **no real device here reaches it** on
  that page (the emulator's 739 does), so those two cards get the page's full width to divide.
  `alignHeights` stretches both columns to the taller; it is off by default because it is wrong for
  the case the helper was built for — a tall main pane would drag a short side column's last card
  down to meet it — and right for a pair, where two cards of equal standing ending at two heights
  reads as one of them having failed to load. **It measures rather than asking for an intrinsic, and
  that distinction shipped a crash.** `Modifier.height(IntrinsicSize.Max)` is the obvious way to
  write it and it throws: intrinsic measurement of a `SubcomposeLayout` is unsupported, and a pane
  holds whatever the screen puts in one — at the time a `BoxWithConstraints` in the Content types
  card, since replaced by a `FlowRow`. Because the Row branch is only taken above
  `minWidth`, it stood up on the cover screen and died the moment the phone was unfolded — v2.1.31,
  reported from the device. The rule outlived that call site: the next `BoxWithConstraints`,
  `LazyRow` or `SubcomposeAsyncImage` a pane acquires brings the crash straight back, and nothing
  about the panes says so. `ResponsiveRows` carries the identical warning about
  `IntrinsicSize.Min` a few hundred lines above, which is the part worth remembering: **the trap was
  already written down and got walked into anyway.** The height is read back with `onSizeChanged`
  and can only grow, so it settles in one pass, and its reset key is the width so a fold cannot
  carry one layout's height into the other.
- **Both were hand-built copies of `SectionCard` and are not any more.** That is what let them drift:
  same container and shape, and then one tinting its icon `primary` and the other leaving the
  calendar untinted, each spelling its own header row and divider. Drawing the background twice is
  how two cards meant to match stop matching. The **"Change date" button is gone** with them — the
  "Specific date" row has always opened the picker itself, so the button was a second control doing
  one job, and it was the reason that card changed height the instant the mode changed, which is the
  one thing a card sitting beside another must not do. The affordance moved into the line already
  there: the date, then `· tap to change`.
- **The checkboxes wrap rather than switching on a width, and the helper that switched them is
  gone.** `AdaptiveInline` asked the card how wide it was and laid three checkboxes across above
  420dp, and it had the fold exactly backwards: the card is at its *narrowest* when there is room to
  put it beside the date card, so the 379dp cover screen gave it 347 of content and got the compact
  row, while the unfolded Fold split 638 into two 313 columns, left 281, missed the threshold and
  stacked three long labels down a column. **The larger screen got the taller layout.** A `FlowRow`
  asks the labels how wide they actually are instead of guessing from a number written in the
  source, so one row survives the cover screen, the unfolded Fold and the tablet alike and a large
  font scale wraps instead of clipping. That left `AdaptiveInline` with no callers and it was
  deleted rather than kept: a five-line wrapper over `BoxWithConstraints` whose KDoc named the one
  card it was written for was never a general primitive, and a public helper nobody calls is read as
  the house answer by whoever needs the next one. Deleting it also takes a `SubcomposeLayout` back
  out of a pane `alignHeights` has to measure — see the crash above, whose rule stands without it.
- **A `remember` keyed on `saved.id` goes stale now that a report's contents can change.** A
  report's id never changes, and until corrections existed nothing else about a report ever changed
  either, so keying the parsed stocks on the id alone was correct for as long as it was written.
  The first correction made a stored run mutable and the report went on drawing the stocks as the
  model first read them — while **Insights showed the fix**, because it rebuilds from the record on
  every recompute, and leaving the screen fixed it, because that disposed the memo. Anything in
  `ResultDetail` derived from a report's *contents* is keyed on `saved.result.editRevision` as well
  as its id: the stocks, the channel names, and the timing and channel option lists. The revision
  rather than `saved` itself, so it is an integer comparison per recomposition rather than a deep
  compare of every call in the run. **View state stays keyed on the id alone** — the search box, the
  context toggle, the open filter panel — because those are about the reader rather than about the
  report.
- **A filter stores what is hidden, not what is shown**, for the same reason. Seeded once from the
  report, a set of shown names cannot contain a value that did not exist when it was seeded — so
  re-dating a card as Watching created a timing the filter had never heard of and the card the
  reader had just corrected **vanished** instead of updating. Storing the exclusions gives both
  halves: filters set on purpose survive a correction, and anything a correction creates is shown
  because nobody ever chose to hide it. `narrowed` is still measured against what the report
  actually offers, so an exclusion naming a timing the report no longer has lights nothing.
- **A sheet holds an anchor, not the objects it was opened with.** `ResultDetail`'s occurrence sheet
  captured the stock and the point it was opened with, so a correction made *from inside it* left it
  drawing the figures that had just been replaced. It holds `(originalStockCode, parseIndex)` and
  re-resolves on every recomposition — against the whole report rather than the filtered list, so a
  filter can never close a sheet the reader has open.
- **The two shells are two call sites, so no page may hold its own state.** `EgxAnalyzerApp` branches
  on `rail` around one `AppContent` for the rail and another for the pill, and again around
  `AnimatedContent` versus `DestinationPager`. Folding the phone flips `rail`, Compose disposes one
  subtree whole and composes the other from nothing, and every `remember` in every screen dies with
  it — which is how an open report vanished on unfolding and left the reader on the list of runs.
  Anything the reader would notice losing goes in `PageState`, hung off the application-scoped
  `AppState`; only transient chrome (a dropdown, a confirm dialog) stays in a `remember`.
  `rememberSaveable` under a `SaveableStateHolder` does **not** work here and was shipped once
  before it was understood: the branches swap inside one frame, so the arriving page reads the
  holder before the leaving page has written to it, and on the way back it restores what the
  previous fold left there. `movableContentOf` cannot reach across `HorizontalPager`'s lazy
  subcomposition. `PageState`'s own comment carries the whole reasoning.
- **A `bringIntoView` escapes the page it was asked from.** The request travels up through every
  scrollable ancestor, and on a phone the outermost one is `DestinationPager` — so a reveal fired
  from a page the reader has left scrolls *that page* back into view, which is the pager travelling
  back to the tab they were leaving. `beyondViewportPageCount = 1` is what keeps the page alive to
  fire it: the Portfolio is still composed, and still running the effect that reveals a trade, while
  Insights is the tab on screen. It beat a tab press rather than losing to one — `animateScrollToPage`
  and a reveal scroll run at the same `MutatePriority`, so the later of the two wins — while a swipe
  survived, because a drag holds the pager at `UserInput` where no reveal can take it, which is what
  made this read as the navigation bar alone being broken. Every reveal now goes through
  `revealIfOnScreen`, which drops the request unless `AppState.destination` is the tab the page is
  drawn on. Three call sites: the Portfolio's trade, Insights' call, the Results report. The card is
  left unfolded either way, and a reveal the reader has walked away from is dropped rather than held
  for their return — the rule `NavStop` already states, that revealing the wrong card is worse than
  revealing none.
- **The tab is not the arrival, and checking it alone fixed half of this.** A press sets
  `AppState.destination` in the same breath it starts the pager travelling, so a page that composes
  *during* that travel passes a destination check and cancels the very scroll carrying the reader to
  it — the pager, barely off the tab they pressed from, snaps back to it. Only a tab two or more
  pages away can do this: a neighbour is already composed and its effects do not run again. That is
  the whole of why the Portfolio reached Insights, next door, and never Results, two along, and why
  Results failed only once a report had been opened — `openRun` is `PageState.openResultId`, which
  outlives the tab, so it is null at a cold start and non-null forever after. `revealIfOnScreen`
  waits on `LocalTabsSettled` — written by `DestinationPager` off `pager.isScrollInProgress`, true
  beside a rail, and put back to true on dispose so a fold cannot strand it — then asks about the
  destination a second time on the other side of the wait. Waited out rather than dropped, because
  an arrival that *should* reveal composes its page mid-travel too: a notification opening a saved
  report is one, and dropping it would answer the notification with a page scrolled to wherever it
  was last left.
- Scrollbar overlays must be applied **outside** the scrolling node, or they are measured against
  the content and slide away with it.
- `NavigationSuiteScaffoldLayout` does **not** consume window insets for its content; the full
  `NavigationSuiteScaffold` does, through a private helper the layout never calls. Left alone the
  page pads itself clear of the gesture strip that the bar below it is already holding, and the
  cover screen shows a band of dead chrome the width of the strip — 15dp on the Fold 7.
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
  producing one file per architecture, breaking the install command above and the CI artifact. To
  reproduce what a release ships, pass the flag by hand.

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
