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
- **Ask approval before opening the emulator or taking a screenshot.** Build plus unit tests is the
  loop. Screenshots are expensive twice over: once when taken, then again on every later turn of
  the session. When one would help, say what it would show and which device or width, then wait
  for the literal word "approve". Not for confirming an install.

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
- **The header's buttons are there at every point of the collapse**, so a test may press one at
  `collapse = 0f` or `1f`. Find them by content description (`Filter by stock`, `Filters`): they
  are drawn on a `Canvas`, not an `Icon`.
- **The test window is short.** Robolectric's default screen puts anything much below the header
  under the fold, where `performClick` lands on nothing and says so only by the next assertion
  failing. `performScrollTo()` first - `PortfolioPageTest` learned this when the header grew 16dp.

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
  card that explained it in words was removed on 2026-09-03; one line in `Prices and trades → Prices`
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
  600dp of container and as cards below it. An open report fills the page with no list beside it
  since 2026-11-12, and book posture splits it on the crease. See **A report's calls on screen** in
  `docs/ui.md`.
- `ui/OccurrenceSheet.kt` — one call in full, opened by pressing a table row. See **The sheet a row
  opens** below.
- `data/XlsxWriter.kt` + `ui/ReportExport.kt` — a report as a spreadsheet, saved to Downloads or
  sent onward from the ⋮ menu on its card. See below.
- `data/Backup.kt` + `data/BackupRestore.kt` + `ui/BackupSection.kt` — the whole record as one file,
  the way back in from one, and the three buttons in Settings. See below.
- `ui/PortfolioScreen.kt` + `ui/PositionCard.kt` + `ui/TradeControls.kt` — the Portfolio tab, one
  trade's card, and the Bought button and closing controls that sit on a recommendation card.
- `ui/CommonUi.kt` holds `Figure` and `FigureGroup`, `Screen`, `SectionCard` and the other shared
  helpers, each rebuilt in place on the kit in 5.0; `ui/DesignSystem.kt` holds `AppDates`, `Space`
  and `TabularFigures`. `ActionPill`, `DisclosureButton`, `SettingsButton`, `AppMenu`, `MoreButton`,
  `StatStrip`, `ShareBar`, `FloatingSurface`, `Glass` and `PillShape` were deleted in 5.0.
- `ui/kit/` — the 5.0 component kit. See **Design language**.
- `ui/SettingsScreen.kt` — the Settings hub and its eight parts (`SettingsSection`), the open one held
  in `PageState.settingsSection` so a fold keeps it. It is the one page whose columns scroll on
  their own: `Screen` takes a `paneScroll` and draws a frame, and each column is a `ScrollPane`.
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
- `model/LivePrices.kt` — the fourth clock, and the only one that is a loop: a minute at a time
  while the app is on screen and the exchange is trading, for open trades, open calls and the
  watchlist. `IntradayRepository.pollLive` is what it calls. See **Following a session a minute at
  a time** in `docs/engine.md`.
- `model/AnalysisPlan.kt` — what a run covers, said explicitly, so the screen and the manual Analyze
  path build the same request.
- `data/JobScheduler.kt` + `data/ScheduleReceiver.kt` + `data/ScheduledJobWorker.kt` +
  `data/ScheduleMigration.kt` — the alarm, the things that mean re-book it, what runs, and the
  one-time move off the old job table. See below.
- `ui/PricesSection.kt` carries the price-refresh switch, the series-archive switch, the
  never-blank status lines and Fetch prices now, drawn as the `Prices` tile on Settings' General page. It also carries `SystemPermissions` — the exact-alarm and battery-optimization
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
- `model/DayBar.kt` — the 1D range every chart has since 2026-11-12: one session's five-minute
  bars, the arithmetic over them, and the Portfolio chart's day line. See **The 1D range** below.
- `ui/ExpandedChart.kt` + `assets/chart/` — the stock sheet's chart on the whole screen, opened by
  Expand beside the move% in "Where it stands": KLineChart in one web view, built to behave like
  TradingView — five chart types, eight indicators, eight drawing tools, A/L/% scales, the sheet's
  levels, and both axes a finger can stretch. The only web view in the app, and deliberately the only
  chart that is one — see **The expanded chart** under Gotchas. Its theme follows the app's, light or
  dark, and its text is JetBrains Mono from `assets/chart`.
- `data/ChartLayoutStore.kt` — the indicators that chart opens with and the lines drawn on each
  stock. Its own preference file, device-local, outside every backup, sync and diagnostics copy.
- `model/ApproachAlerts.kt` + `data/ApproachNotifier.kt` — a trade closing on its stop or target 2,
  said while there is still something to decide.
- `data/SessionDigestNotifier.kt` — what the whole session did, once, after the close.
- `data/AttentionNotifier.kt` — the two ways this app stops working without anything looking wrong:
  a feed that has gone quiet, and a schedule that did not run.
- `data/TradeActionReceiver.kt` — Keep open, pressed from a notification with the app closed.
- `data/AppShortcuts.kt` + `res/xml/shortcuts.xml` — long-press the launcher icon.
- `data/TodayWidget.kt` — the home-screen widget, and the only Glance in the codebase. Two sizes
  since 5.0, picked by `widgetLayoutFor`.
- `data/NotificationAccent.kt` — `NotificationTone`, the colour each kind of notification carries,
  and `StockBadge`, the logo or monogram on the right of one about a single stock.
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
- `ui/HomeScreen.kt` + `ui/HomeTiles.kt` + `ui/HomeFeed.kt` — the Home tab, its tiles, and the
  sub-interface of `AppState` it reads. See **The Home page** below.
- `state/LiveHome.kt` — what is behind `HomeFeed`, outside `LiveAppState` by the `LiveUpdates`
  pattern.
- `model/HomeMarket.kt` — Home's pure rules: `marketMovers`, `mergeHeadlines`, `leadHeadlines`,
  `headlineAge`, and `bookMood`, which colours the book tile.
- `data/HomeMarketRepository.kt` + `data/NewsRepository.kt` + `data/HomeCacheStore.kt` +
  `data/MarketBriefPrompt.kt` — four indices and three outside numbers off the chart feed, four RSS
  feeds, the one file Home keeps between launches, and the paid brief's question and answer.
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


## The Home page

The first tab since 2026-11-12, and the page the app opens on (`LiveAppState.destination` starts at
`HOME`; notifications and launcher shortcuts that name Portfolio still go to Portfolio). The owner
asked for the market, its indices, the news and their own book in one place.

- **Everything on it is free or already on the phone, except one button.** Four index series and
  three outside numbers come off the same public chart feed as the prices; the headlines are four
  RSS feeds; the movers and the breadth are read off `lastTwoCloses`; the book tiles read
  `portfolio` and `sessionDigest`. The brief is the one paid thing, and it is asked only through
  the `KitDialog` its button opens - `HomePageTest` holds that the press alone sends nothing.
  **Never press it on the owner's device**, for the same reason as never starting a run.
- **The brief sends public figures and headline titles and nothing else.** No Telegram content, no
  call, no trade, no ticker the owner holds. `MarketBriefPrompt.question` takes no portfolio
  argument on purpose. It rides `AnalysisRepository.ask`, so it uses the Ask AI model and that
  request's `json_object` response format, and its tokens land in the same usage tally.
- **EGX indices are asked for hourly bars.** The daily endpoint answers `^CASE30`,
  `^EGX70EWI.CA`, `^EGX100EWI.CA` and `^SHARIAH.CA` with one session each; `interval=1h&range=3mo`
  answered all four with 320 bars over 64 sessions (measured 2026-11-12). `parseIndexQuote`
  aggregates them with `DailyFromIntraday`, and falls back to the header's level with no move and
  no chart where the bars cannot be used. The symbols without `.CA` (`^EGX70EWI`) are a 404.
- **Indices are not listings.** They stay out of `daily_prices`, the catalog, the feed-health
  checks and the scoring, the way the EGX30 line on the Portfolio chart does. `LiveAppState` still
  fetches its own EGX30 history for that chart; the two are separate on purpose, because that one
  reaches back to the first trade and Home's reaches three months.
- **The feeds were checked, and three that look obvious do not answer**: Mubasher, Arab Finance and
  Ahram Online publish no working RSS (404 / 410). The Arabic feed is a Google News search for the
  exchange by its Arabic name; its titles arrive as `Headline - Publisher`, which `parseRss` splits.
- **A headline opens in a Custom Tab** (`androidx.browser`), not a web view of the app's: the
  article stays in the browser's process, and the chart remains the only web view here. Where no
  browser offers tabs it falls back to `ACTION_VIEW`. The toolbar is paper / graphite by the
  *system's* dark setting, which a Custom Tab follows rather than the app's own theme choice. The
  news tile's refresh button is `refreshHomeNews(force = true)` alone; `HomePageTest` holds that.
- **`parseRss` is patterns, not an XML parser, deliberately.** A feed is somebody else's document:
  nothing is resolved, followed or rendered, markup inside a field is removed, and only an
  `http(s)` link ever reaches `openHeadline` or the system.
- **`home_cache.json` in `filesDir` is the only thing Home stores.** Device-local, replaced whole,
  outside the database, every backup, the sync channel and Save diagnostics. No schema version.
- **Three new tile tones, for one tile.** `BOOK_UP` (deep teal `#1F5F5B`), `BOOK_FLAT` (soft sage
  `#DCE6D2`) and `BOOK_DOWN` (maroon `#6E1B27`) colour "Your book" by `bookMood`, which judges the
  figure **as printed** - under 0.05% either way is flat, so the colour cannot contradict a `0.0%`.
  The owner chose them on 2026-11-12 in place of lime. They are the same fills in all three themes,
  like every other tile colour, and `PaletteContrastTest` sweeps their signals.
- **The EGX30 tile is the stock sheet's chart tile** (`chartTileColors`): navy in light, the raised
  card at night. Not a kit tone, so `IndexHeroTile` sets its own fill and draws its own range
  track - the kit's `SegmentedControl` is drawn for the page's ground.
- **The outside numbers' moves are in the page's ink, not green and red.** A dearer dollar is not a
  gain to somebody holding Egyptian stocks.
- **Home has no stock box and no filter sheet**, like Settings: `PageState.stockBox` and
  `filtersOpen` answer null for it. `homeRange` and `homeNewsOpen` are in `PageState` so a fold
  keeps them.
- **Shipped in 5.2.0 untested on a device.** Built and unit-tested only; no emulator and no
  screenshot was taken before the release.

## The 1D range

Every chart with a range control has 1D as its first option since 2026-11-12: the EGX30 tile on
Home (`HomeRange.DAY`), the Portfolio chart (`ReturnRange.DAY`), and the four stock charts that
share `ChartRange` — the stock sheet, the full-screen chart, the position card's and the Insights
call card's (`ChartRange.DAY`). The default on each is unchanged.

- **It is the one range that is not a slice of what is stored.** The record is one close a day, so a
  day of it is one point. 1D is the newest session's five-minute bars, asked of the same public
  chart feed when the range is pressed: `IntradayRepository.dayBars`, behind `AppState.dayBars`.
  **Free, and never stored** — not in either database, not in the series archive, not synced.
  `LiveAppState` reuses a ticker's answer for a minute so the sheet, the full-screen chart and a
  card of one stock share a request.
- **"Newest session", not "today".** The request spans ten days and `newestSession()` cuts it down,
  so a Friday or a holiday shows the last session held and not an empty box. Sessions and the axis
  are read on `ScheduleClock.ZONE`.
- **Absent, not empty, where there is no intraday feed.** `AppState.hasDayBars` is false for a stock
  still on its legacy `SYMBOL.CA` symbol (which answers an intraday request with daily rows), and
  `offeredFor` drops 1D from that chart's track. The stock sheet's range is one `PageState` value
  for every stock, so a 1D left on from one stock is a month on a stock that cannot offer it —
  the rule Call already followed.
- **`PriceChart` and `DayChart` are one drawing.** `LineChart` in `StockTrend.kt` takes closes, three
  axis labels and which closes carry a dot; the two wrappers feed it sessions with dates or bars
  with times. A day's chart carries the levels and no call dots: a call is made on a day, and every
  bar is the same day. `DayChartOrNote` keeps the chart's height while the bars are on their way.
- **Home asks with its ordinary refresh**, one extra request for the EGX30 alone
  (`interval=5m&range=5d`, measured 2026-11-12: 41 bars part-way through a session), kept on
  `IndexQuote.day` and in `home_cache.json` as moments and closes. A cache from before has none,
  and the tile then offers no 1D.
- **On Portfolio, 1D is a different measure and the tile says so.** The other ranges are the return
  since each trade was bought; 1D is the open trades' average move through the session from each
  stock's first open (`bookDayLine`), beside the EGX30 on the same clock (`marketAlongDay`). A stock
  that has not traded yet counts as unmoved, so the average is over the same stocks all the way
  across. It costs one request per open trade with an intraday feed, plus one for the index.
- **The full-screen chart is told `intraday`.** The candles are then the bars at their real moments,
  the page reads them in `Africa/Cairo` instead of UTC, and the crosshair keeps the hour it strips
  from a session. **Not seen in a web view or on a device**: built and unit-tested only. Drawings
  made on a daily range are kept by their timestamps and have not been looked at on a 1D chart.

## Feature documentation

Detailed documentation lives in [`docs/`](docs/):

- [`docs/engine.md`](docs/engine.md) — What a run sends, scoring rules, ranking, call lifecycle
- [`docs/portfolio.md`](docs/portfolio.md) — Portfolio, trade alerts, session digest
- [`docs/features.md`](docs/features.md) — Ask AI, reports, corrections, export, backup, sync, update, scheduling
- [`docs/ui.md`](docs/ui.md) — Back/stock sheet, page header, filters, status line, settings, colors

## Design language (since 5.0.0, the Bento redesign)

The owner chose the "B+ mix" on 2026-11-09 — Bento tiles, with Pulse's mono figures, progress bars,
book-vs-EGX30 chart, live-session cues and dark look folded in — and it was built in ten stages on
`expanded-chart-tradingview` and shipped whole as 5.0.0, once no pre-5.0 element was left anywhere.
The mockups are on the claude.ai design canvas named in memory. Where an older note below or in
`docs/ui.md` describes paper and amber, 6dp pills, `ActionPill`, `SettingsButton`, glass or a
floating bar, this section is what is true now.

- **Tiles on a ground.** Paper `#EFEDE6` (graphite `#141518` at night, true black in the pure-black
  mode) with rounded tiles on it (`Kit.TileCorner`, 30dp; `Kit.TileGap`, 10dp). A tile's tone says
  what it is: ink for what leads a page, cobalt `#1E2BFF` for the hero, lime `#C8FF4D` for the
  highlight, coral `#FFB8A3` for alerts (lightened on 2026-11-12; it was `#FF6B4A`), and sunflower, lilac and sky (`tile1`–`tile3`) for the rest.
  `TileTone` names them; `tileColors` gives the ink that goes on each. Gain and loss are their own
  colours (`#0B8A4A` / `#C62020`) and never a tile fill.
- **The kit is `ui/kit/`, and it is the only place allowed to build on Material.** `Tile`, `Panel`,
  `KitButton` (primary / secondary / danger / on-tone, three sizes), `KitIconButton`, `KitChip`,
  `SegmentedControl`, `KitSwitch` / `KitSwitchFace`, `KitCheckbox`, `KitTextField` (figure, error,
  "was … Put back", password, keyboard action), `KitStepper`, `KitSheet` (a bottom sheet folded, a
  side panel unfolded or in book posture), `KitDialog`, `KitMenu`, `KitCalendarSheet`,
  `KitExpandable`, `KitTag`, `KitProgress`, `KitSplitBar`, `KitSpinner`, `KitRule`, and the market
  pieces — `TickerTape`, `OutcomeStrip`, `TargetProgress`, `BreadthBar`, `LiveDot`.
- **5.1 made it plainer, not different.** Statuses are plain words (`model/StatusWords.kt`: "Target
  1 reached", "Ran out of time"), a call or trade's levels are a compact `PriceLadder` with loss, near and
  far zones, a long press opens a card's menu (`HoldMenu`), a call's life is a `Timeline`, what few
  readers need sits behind a closed `MoreRow`, and a figure drawn on a coloured tile takes that
  tile's own gain and loss (`tileSignals`), because the plain ones fail on lime and on the light ink
  tile of dark mode. `PaletteContrastTest` measures every such pair. Plan and stages are in
  `docs/redesign-5.1-plan.md` and `docs/redesign-5.1/`.
- **`NoOldElementsTest` holds the rule.** It reads every file in `ui/` and `ui/theme/` and fails on
  any call to a Material component or a pre-5.0 control, by name. A new file is held to it the day
  it is added. Material's colour names (`onSurfaceVariant` and the like) still appear in places and
  are mapped onto the 5.0 palette in `theme/Theme.kt`, so they draw new colours.
- **Type.** Bricolage Grotesque for every word, JetBrains Mono for every figure (`TabularFigures`),
  IBM Plex Sans Arabic as the Arabic fallback. Inter Tight was removed in 5.0.0. The full-screen
  chart's page loads its own copy of JetBrains Mono from `assets/chart`.
- **Every design has three postures.** `LocalFoldPosture` is `Cover`, `Unfolded`, `Tabletop` (with
  the hinge's fractions) or `Book`. Unfolded and book put two columns side by side, book splitting
  on the hinge; tabletop keeps what is read on the upright half and what is pressed on the flat
  half, using `tabletopHeroHeight`. Every page and sheet has a branch for each.
- **One shell at every width.** No rail and no floating bar since 5.0: the tabs are in the page
  header, and Analyze's button is on the Analyze page. A tab changes in place - see the first
  bullets under **Gotchas** on `DestinationSwitch`.
- **The header's two buttons are the mockups' own**: 44dp round, raised tone, an 18dp glyph drawn
  as 2.2-wide strokes on a 24 grid (`HeaderGlyph` in `PageHeader.kt`), never Material's icons and
  never resized by the collapse. On the cover screen they sit at the right of the session line,
  which is 44dp tall there to hold them; unfolded they close the tab row.
- **Motion** is `popIn` on first show (tiles 80ms apart, the mockups' step), `popSpring`, `rememberDrawIn` for lines and bars,
  `pressScale` on a press, and all of it held still by `LocalReduceMotion`.
- **Outside the app** the same palette is written out by hand, because those surfaces have no theme:
  `res/values/colors.xml` (launcher, splash, shortcut icons), `NotificationTone` and `StockBadge` in
  `data/NotificationAccent.kt`, and the companion of `TodayWidget`. `OutsideSurfacesTest` checks
  them against `LightPalette`.
- **The brand mark is "Night", chosen by the owner on 2026-11-12**: a sunflower pyramid with a
  cobalt shaded face, the edge between them a price line, over `EGX` in paper, on ink. It replaced
  the lime arrow on cobalt, and the owner asked for no lime in it. The letters are JetBrains Mono
  800 written out as outlines (a vector drawable cannot hold text), so a change to them means
  extracting the glyphs again, not editing a string. The status-bar glyph is the pyramid alone,
  because the letters cannot be read at 24dp. The three shortcut icons were left as they were and
  still use lime. **Not seen on a device**: built and unit-tested only.

## Gotchas

- **The expanded chart is a web view, on one screen, and nothing else in the app is.** KLineChart
  has no native Android build, and `PriceChart` stays the chart on the sheet and on the call and
  position cards: a web view per card in a scrolling list is the wrong trade, one on a screen opened
  on purpose is the right one. `ExpandedChart.kt` draws the toolbar (range, chart type, Indicators,
  Draw, Levels — the range and levels are the sheet's own `PageState`, so pressing 6M in one is 6M in
  the other) and `assets/chart/chart.html` draws the chart and what sits on it: the price strip, the
  A/L/% buttons, `»` back to the newest session, the × beside each indicator. The app sends one JSON
  payload per change through `egxChart.setData`, plus `draw` / `cancel` / `undo` / `clearDrawings`.
  Only the bundled page loads: `shouldInterceptRequest` answers anything outside
  `file:///android_asset/` with nothing, and `blockNetworkLoads` is on, so the page never touches the
  network the app holds a permission for. R8 needs no rule for it, there being no JavaScript
  interface. The library is Apache 2.0 and its notice is in `THIRD-PARTY-NOTICES.md`.
- **The page is read, never heard from.** With no JavaScript interface the page cannot tell the app
  that the reader pressed × on an indicator or finished a trend line, so the app asks:
  `egxChart.state()` returns the indicators on, the drawings, and whether one is still being placed.
  It is pulled when the Indicators sheet opens, when the chart closes, on `ON_PAUSE`, and every 350ms
  while a drawing tool is lit. Two consequences worth knowing. The page applies the payload's
  indicator list **only when that list changes**, or a payload re-sent with the app's stale set
  would put back the indicator the reader just took off. And the stored drawings ride in the payload
  but are read **once**, on the first `setData`; after that the page owns them, and keeps them across
  a change of range by taking them off and putting them back by their dates around `applyNewData`.
- **Pan, fling, pinch and the long-press crosshair are the library's own touch handling**, and
  `chart.html` does not touch them. The date axis is the other strip, after the price axis, that the
  library stretches only for a mouse, so a finger on it is forwarded the same way.
- **The price axis scales by finger, and the library only does that for a mouse.** Dragging along
  the axis rescales it in KLineChart (up and down, like TradingView), but its touch handler for the
  axis does nothing, so `chart.html` hands a finger on the axis strip to the library as the same
  mouse drag. A double tap on the axis, or any change of range, mode or levels, returns to the
  auto-fit; the library's own double click does the same for a mouse. The auto-fit grows the axis to
  take the levels in by up to 2.5x the candles' span (`ExpandedChartJson.ScaleGrowth`, the figure
  `PriceChart`'s `MaxScaleGrowth` holds — they are two constants and must be moved together), by
  carrying their prices in an invisible indicator, since the library has no public call to set the
  range. A level past the cap, or off the screen after a drag, is pinned to the edge with an arrow.
  **Touch is untested on a device**: the page was checked in a desktop browser with synthetic touch
  events, which exercise the forwarding but not a real WebView on the Fold. The 2026-11-09 additions
  (indicators, drawings, scales, `»`, the price strip) were checked by script in a desktop browser
  and never by a finger: placing a drawing's points by tap is the part most likely to need work.

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
  version 14's case (the version-13 `positions` table gaining `scheduled_jobs`) went with the
  scheduled-analysis feature it tested and no longer exists; versions 15 onward are below, and
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
  counts overdue trades. Its palette is written out in its companion, because a widget has no access
  to the app's theme, and it cannot load the app's fonts, so its text is the system face.
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
- **The feed serves one-minute bars, and it runs a quarter of an hour behind the exchange.** Both
  measured on 2026-11-12, mid-session, on COMI's ISIN symbol. `interval=1m` answers with true
  one-minute bars, 270 a session. Asked **one session at a time** they reach **29 days back**; 30
  is refused outright, and a range is a separate limit of eight days per request. The newest bar
  was 905 and 975 seconds old on two readings a minute apart, so no polling rate gets closer to
  the exchange than about fifteen minutes - a faster loop asks for the same bar twice. The batched
  `v8/finance/spark` endpoint takes at most 20 symbols and answers with **closes only, no high or
  low**, which is why the live poll does not use it: a level touched between two closes is the
  thing being looked for.
- **Live bars sit in `intraday_bars` with no row in `intraday_fetches`, on purpose.** They are part
  of a day, and a fetch row would tell `fetchMissing` the session was done. That absence is also
  how `pruneLiveBars` finds yesterday's leftovers, so anything that starts writing bars without a
  fetch row for another reason will have them deleted on the next arrival.
- **A frozen feed is not the same as an unpriced stock, and is harder to see.** `unpriced` means no
  history at all; `stale` means the series answers every request while its newest session stays put.
  That has happened here — the ISIN migration — and nothing noticed at the time. Seven days, which
  clears the Friday–Saturday weekend plus a public holiday. Both are now standing state on Insights
  rather than a count in a toast — see **When the feed goes quiet**.
- **A tab changes in place; nothing slides sideways.** Until 2026-11-11 the
  five pages sat in a `HorizontalPager` - the pre-5.0 navigation, carried into 5.0 untouched and
  reported as "the old swiping animation". The mockups draw one header, identical on every page,
  with only the large name moving, and pages whose tiles pop in. So `DestinationSwitch` in
  `EgxAnalyzerApp.kt` composes **only the current page**, under `key(destination)`, and a change
  of tab is two things: `TabTravel` (in `AppHeader.kt`, held by the shell, published as
  `LocalTabTravel`) moves the emphasis along the tab row - the pressed name grows and takes the ink
  while the one left shrinks and greys, on `popSpring` - and the page is built afresh, so `popIn`
  runs on every arrival. `TabTravel` also holds the tab row's scroll position, because a header is
  rebuilt with its page and would otherwise start the row from zero each time. The row is scrolled
  so **the tab before the current one still shows**, as the mockups draw it.
- **What the pager took with it.** The `travelling` guard and its `snapshotFlow` pair,
  `sidewaysGesturesStayOnThePage`, `LocalTabsSettled`, the wait inside `revealIfOnScreen`, and the
  `swipeBetweenTabs` switch. With one page composed there is no neighbour to answer a press meant
  for another tab and no sideways scroller above a page for a `bringIntoView`, a focus request or
  a child's leftover fling to turn. `revealIfOnScreen` still checks the tab, and that is all.
  **The cost is that a page's first composition now happens on the press** rather than while the
  reader sat still on its neighbour; `PageState` is what makes the rebuild lossless, and `Screen`
  seeds the header's collapse from the saved scroll so a page left part-way down comes back that way.
- **The design is silent on two things here, and both were decided on 2026-11-11 rather than read
  off a mockup**: swiping between tabs, and where the header's buttons go once the session line has
  folded away (pinned at the right, coming to rest beside the collapsed tab names).
- **Swiping between tabs came back the same day, designed for this shell, not the pager.** The
  owner asked for it ("that is so obvious to have") and for nothing from the old build. `TabSwipe.kt`
  reads the drag on the page (`Modifier.tabSwipe` on `DestinationSwitch`'s box) and **nothing
  slides**: no neighbour is drawn. While the finger is down the header's emphasis follows it toward
  the neighbour's name (`TabTravel.preview` / `peek`, the row's scroll carried with it), and the page
  under the header leans at most 24dp and fades a quarter (`LocalTabSwipe`, read in `Screen`'s
  layer so a drag recomposes nothing). A quarter of the width or a flick commits through the same
  `navigate()` a press uses, and `TabTravel.goTo` finishes the emphasis from where the finger left
  it; short of that both spring back. Past either end the page stretches a little and returns.
  **It reads the final pass and gives up on anything already consumed**, which is how the chip
  rows, the wide table, the charts, the tape and the tab row keep their own sideways drags without
  a guard list; a gesture that starts mostly vertical is let go too. Reduce motion drops the lean
  and the follow but keeps the change of tab. Sheets, dialogs and the full-screen chart are their
  own windows, so the gesture never reaches them. `TabSwipeTest` holds all of this.
- **Pressing the destination already showing takes that page back to the top.** The press every
  bottom bar on this platform answers, and this app answered it with nothing — the way back from a
  session card deep inside Insights was to scroll all of it by hand. `AppState.scrollToTopRequest`
  is a **destination and a counter**, and both halves are load-bearing: the destination so a page only
  ever answers a press meant for it; the counter because a second press is a
  second request, and a value that repeated would restart no effect. The shell publishes it through
  `LocalScrollToTop` from `DestinationScreen` — the one place both shells build a page and so the
  only place that knows which destination is being composed — and `Screen` animates the scroll, so
  all five pages get it from one change. **Deliberately not folded into `navigate()`**, which is
  called for every arrival and not only for a press on the tab already showing.
- **Motion has one curve, one press, and a switch.** Written 2026-10-05 from an audit against the
  Emil Kowalski and Apple motion notes. `UiEase` in `DesignSystem.kt` is the curve every tween names
  (Compose's `FastOutSlowIn` starts gently, which is exactly when a finger is watching);
  `ExpandIn` / `CollapseOut` are how every disclosure opens and closes (200ms in, 140ms out), and the
  status line uses the same pair. `pressScale` is what every pressable does the moment a finger
  lands — 0.95, about 90ms down and back on `releaseSpring` since 4.5.0 — read in the layer's lambda so a press recomposes nothing; the caller
  owns the `InteractionSource` and hands the same one to its `clickable` or `Surface`. A new button
  uses it. `LocalReduceMotion` is the system's animation scale read at `AppRoot` (and again on every
  resume, because the setting lives in another app): the model's breathing, the action's drifting
  light and the press all hold still when it is on, because Compose's own transitions follow the
  system scale but loops this app draws itself do not. `LocalSolidSurfaces` is the same plus
  high-contrast text, and makes the frosted bar and the action solid — Android has no public
  "reduce transparency" flag, so this is a best-effort reading of the two settings that ask for the
  same thing. A pill off screen draws its resting self (`onGloballyPositioned` against the window),
  so a page of twenty cards is not running twenty clocks. **Predictive back is on** in the manifest.
- **A removal says so after the fact instead of asking first, where it can be undone.** Removing a
  position (`deletePosition`) and deleting a wording rule offer Undo on the status line for ten
  seconds, using the existing `StatusUndo`; the position card closes up before the removal runs so
  the rows below slide into the gap. Both undos save the record again *newer than the burial*, which
  is what makes the sync channel agree. **Deleting an analysis keeps its dialog**, and so do delete
  all, restore from a backup and removing the API key: deleting a report also deletes its messages
  from the Telegram sync channel, so there is nothing local to put back.
- **The page fades where it meets the header and the gesture strip, and holds a revealed card clear
  of the bar.** `fadeEdges` in `DesignSystem.kt` masks the edges of a scrolling column — nothing at
  rest, the top only once something has scrolled past it, the foot only while there is more under
  it. `Screen` also installs a `BringIntoViewResponder` that adds the bar's footprint to the bottom
  of any rect asked to be shown, so a trade revealed from a notification does not end with its Sold
  button under the bar.
- **The model line on a report card is two lines, each one line.** It was one, "always", because
  wrapped it snapped mid-word. Split into the model (middle-ellipsised, so its version suffix
  survives) and the run time (its own line), nothing wraps and the date that tells two runs apart is
  never the part cut off.
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
- **T+1 is neutral, like every other note pill.** It was `primary` in all three places that draw it -
  the call card, the position card, Insights - on the reasoning that it is neither a verdict nor a
  warning and so may take the app's own voice. True, and not a reason to be the one differently
  coloured ring in a row of them; the wording already says the call named its own deadline. Asked
  for on 2026-09-11. It is still tappable in the two places it explains itself.
- **An open report is a page of its own, and back closes it** (since 5.1). A report used to be a
  card that unfolded in place on a press and folded on the next, which meant any part of an open
  report that answered no tap of its own closed it - the wide table's headings were the case that
  bit. `PageState.openReport(id)` opens one; back handling has an "open report" step in
  `canGoBack`/`goBack`, mirrored in `FakeAppState`. The runs list is tiles, each pressed to open.
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
  heading, where the shelf had room only for a chip that opened a menu. `MultiSelectSection` and
  `SortSection` are that. A report's own calls are narrowed by chips above them since 5.1, and
  `CheckedSetFilter`, `StockFilterField` and `FilterRow` went with the old toolbar; the menu-style `MultiSelectFilter`, `SingleSelectFilter`, `SortFilter` and `SingleSelectSection`
  were deleted on 2026-10-11 as unused.
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
- **A reveal is dropped if the reader has left the tab it was for.** `revealIfOnScreen` checks
  `AppState.destination` and nothing else since the pager went (2026-11-11). While the tabs were a
  pager a `bringIntoView` could climb to it and turn the page, and a page composing mid-travel
  could cancel the travel carrying the reader to it; both are history, in `git log` before that date.
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
