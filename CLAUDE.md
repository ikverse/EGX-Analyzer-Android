# EGX Analyzer — Android

Reads Egyptian Exchange stock recommendations out of Telegram channels, extracts them with a cloud
model, scores every call against real prices, and ranks the channels on what they actually
delivered. The Windows desktop counterpart was retired on 2026-08-12 at v0.1.126 — its source is
kept in the sibling repo, unchanged and unreleased. This is the only app under development, so
nothing here is constrained by keeping the two in step.

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
- `model/ModelSuitability.kt` + `model/CloudModelInfo.kt` — which models the picker offers. A run
  sends screenshots, so image input is the bar: OpenRouter states its modalities and is believed,
  and the providers that answer with bare ids have their names read. An unrecognised name is
  unknown rather than rejected — the picker hides it, Show all and typing an id still reach it.
- `data/ModelUsageStore.kt` + `model/TokenUsage.kt` — what each model has cost in tokens, summed
  from the `usage` block on every answer. Device-local and never synced: a token count describes
  one phone's spending. A run's own total is in its diagnostics; this is the lifetime tally, and
  the only place Ask AI's spending appears at all.
- `data/AnalysisChunking.kt` — 8 images per request. Beyond ~32 the model loses track of which
  image it is citing, which produced exclusions naming the wrong card.
- `data/ConsolidatedParser.kt` — the model's JSON into `ConsolidatedRecommendation`.
- `model/Scoring.kt` — how a call is judged. See below.
- `data/IntradayRepository.kt` — five-minute bars for the sessions daily figures cannot order, and
  hourly bars for the stocks no daily feed carries at all. See below.
- `data/DailyFromIntraday.kt` — bars aggregated into daily sessions, for those stocks. See below.
- `model/CallSanity.kt` — whether a call's levels can be believed. See below.
- `model/CallShortlist.kt` — which card is worth a paid question. See below.
- `model/CallAlerts.kt` + `data/CallAlertNotifier.kt` — a stock reaching a buy zone nobody took.
- `data/PerformanceCalculator.kt` — per-channel and per-session rollups, and the ranking.
- `model/SettledCall.kt` — the verdict of a call the market has finished with, frozen once and never
  replayed. See below.
- `ui/ChannelScoreSheet.kt` — how a source is scored, opened by pressing its card in the ranking.
- `data/PriceHealth.kt` — which stocks the feed has gone quiet about and what it costs. The Settings
  card that explained it in words was removed on 2026-09-03; one line in `General → Prices` is what
  is left on screen, and `feed_checks` / `feed_faults` in `LocalDataStore` are the log that replaced
  the rest of it, for a diagnostics copy read off a device. See below.
- `data/PortfolioCalculator.kt` + `model/Position.kt` — the trades the user actually took. See below.
- `model/TradeAlerts.kt` + `data/TradeStatusNotifier.kt` — what has changed about a trade since the
  user was last told, and how the phone says so. See below.
- `model/SessionDigest.kt` + `ui/TodayCard.kt` — what the market did on one trading session, and
  the card that says so on Portfolio and Insights. See below.
- `data/AnalysisPolicy.kt` + `data/RuleSet.kt` + `data/BuiltInRules.kt` — the local wording filter.
- `data/PromptComposer.kt` — generates the prompt sent to the model.
- `data/ReportSync.kt` + `data/RuleSync.kt` + `data/PositionSync.kt` — what travels between devices.
- `data/XlsxWriter.kt` + `ui/ReportExport.kt` — a report as a spreadsheet, saved to Downloads or
  sent onward from the ⋮ menu on its card. See below.
- `data/Backup.kt` + `data/BackupRestore.kt` + `ui/BackupSection.kt` — the whole record as one file,
  the way back in from one, and the three buttons in Settings. See below.
- `ui/PortfolioScreen.kt` + `ui/PositionCard.kt` + `ui/TradeControls.kt` — the Portfolio tab, one
  trade's card, and the Bought button and closing controls that sit on a recommendation card.
- `ui/CommonUi.kt` holds `Figure` and `FigureGroup`, and `ui/DesignSystem.kt` holds `AppDates` —
  the one figure layout and the one set of date patterns, for every screen that draws either.
  `CommonUi.kt` also holds `ActionPill` and `DisclosureButton`, the two kinds of button a card is
  allowed to carry. See **A button on a card is one of two things** under Gotchas.
- `ui/EgxAnalyzerApp.kt` holds `AppHeader` and `AppStatusLine` — the app's name, and the one line
  that says what it is doing or has just done. See **The status line** below.
- `model/ScheduleClock.kt` + `model/MarketRefresh.kt` + `model/CloseSweep.kt` +
  `model/AnalysisSchedule.kt` — when the three things this phone does on its own fire, and what the
  analysis one is. `ScheduleClock.lastFinalSession` is also the one answer to "has that session
  closed", which the scorer and the still-trading flag both read. See below.
- `model/AnalysisPlan.kt` — what a run covers, said explicitly, so the screen and the clock build
  the same request. See **What this phone does on its own** below.
- `data/JobScheduler.kt` + `data/ScheduleReceiver.kt` + `data/ScheduledJobWorker.kt` +
  `data/JobRunner.kt` + `data/ScheduleMigration.kt` — the alarm, the things that mean re-book it,
  what runs, and the one-time move off the old job table. See below.
- `ui/SchedulesSection.kt` — the schedule summary on Analyze and the editor in Settings.
  `ui/PricesSection.kt` carries the price-refresh checkbox, the never-blank status line and Fetch
  prices now, drawn as the `Prices` group inside Settings' `General` card.
- `data/OpinionPrompt.kt` + `data/OpinionPromptStore.kt` + `data/OpinionSearchBrief.kt` +
  `data/OpinionParser.kt` + `ui/StockOpinionSheet.kt` — Ask AI, on a call card in Insights.
  See below.
- `ui/NavStack.kt` — one step of history, so back undoes a jump the app made on the reader's
  behalf. See **What back does** below.
- `ui/StockSheet.kt` — everything the app knows about one ticker, in one sheet. `LocalOpenStock` is
  how a ticker anywhere opens it. See below.
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
- `ui/` — one file per screen, plus `CommonUi.kt` and `DesignSystem.kt` for shared pieces.

## Scoring, and why each rule is there

A call is replayed from the session it was made for until it reaches a target or breaks its stop.
**There is no scoring window and no setting behind one.** `Scoring.JUDGING_HORIZON_SESSIONS` (30) is
the outer bound on how long it may take about it, not a deadline anyone chose — the point of the
record is *how long* a source's calls take, and a ten-session window answered that by filing every
slower winner as having reached nothing. A bound rather than none at all, because "still open" is
not a verdict: a source whose calls drift sideways for six weeks has said something, and unbounded
scoring would drop exactly those calls out of every rate while keeping the ones that resolved. The
one exception is a **T+1 card**, judged over its own two sessions — the only call whose deadline the
channel printed itself.

`Scoring.DEFAULT_WINDOW_SESSIONS` (10) survives as something else entirely: what the Bought dialog
offers as a **trade's** deadline. `judgingWindow()` and `offeredTradeWindow(setting)` in
`model/CallDerivations.kt` are the two, deliberately separate — the reader who wants to be out
inside a week must not turn every call a source made into a call that reached nothing in a week.
`AppPreferences.defaultTradeWindowSessions` is that setting, still stored and synced under its old
name `scoringWindowSessions` because renaming a persisted key resets it on every device.

- **The record starts on `PerformanceCalculator.ANALYSIS_START`, 3 August 2026.** Everything before
  it came from testing the extraction rather than from reading the market, and a rate resting on it
  describes the test. A constant and not a setting: a floor that can be dragged is a floor that
  silently rewrites every figure the app has ever shown. Prices from before it are still fetched and
  still stored — a split check compares against them — and no call is judged on them. The rows are
  **filtered, not deleted**; reversing a filter costs nothing and un-deleting a year of prices is
  not possible. `scoringSince` is that floor rather than the earliest call behind it: derived from
  the calls it was null exactly when nothing had been scored, which is the one case the empty state
  needs it for, so the screen could never name the date it was waiting on.
- The entry must trade before anything else counts.
- A stop counts as broken only past **2%** — the channels themselves say
  `يتاكد بالكسر بنسبة 2%`. Applying it turned 26 stop-outs into 7.
- Entries and targets compare with a small epsilon because Yahoo sends **32-bit floats**: 1.03
  arrives as `1.0299999713897705`, and comparing exactly recorded a reached target as a stop-out.
  The slack is a millionth — it undoes storage noise, it is not a tolerance.
- A session that reaches a target **and** breaks the stop is a partial hit that fell back, not a
  loss. Daily bars cannot prove the order; this credits the favourable one deliberately.
- Prices that are not positive are stripped before scoring. A session in progress can arrive with a
  high of zero, which force-stopped every call on that stock.
- **A day the exchange was shut is not a session, whatever the feed says.** Yahoo does not omit an
  EGX holiday: it answers with the previous close repeated across the high, the low and the close
  and no volume against it. `neverTraded` refuses those at the parse, and `dropNonTradingSessions`
  clears the ones already stored - ten dates across a year, on ninety-one of ninety-two stocks at
  once. Stored, they are sessions to everything that counts sessions: a window short by however many
  fall inside it, **a T+1 posted the day before one spending its whole sell side on a day nothing
  could trade**, and every "sessions to a target" figure carrying them. The volume is what separates
  a closed exchange from a stock so illiquid it printed once - the three prices being one number is
  not enough on its own. Clearing them also re-arms `PriceSanity.isStale`, which is written for
  exactly the frozen-feed case and had been blinded by those rows for a year.
- **An open outside its own session's high and low is read as unknown.** This feed reports the
  previous session's close in the open field on ninety-eight EGX rows in a hundred, and where the
  stock gapped away from it the number lands outside the range it claims to begin - seven of the
  fifteen sessions with stored five-minute bars put the open outside the first bar of their own
  session, six of them below where trading actually started. `DailySession.traded` nulls those, so
  the split check falls back to close-to-close rather than measuring a day's move from the day
  before. What it cannot catch is a previous close that happens to land inside the range, which no
  data here separates from a real open. Dropping `buyableAtOpen` altogether was tried and costs far
  more than it buys: every call reaching its target on the session it was made for would need
  intraday bars, and would be unjudged wherever the feed no longer has them.
- **A window is spent when its last session closes, not when that session appears - and the close
  means 14:45, not midnight.** `Scoring.score` and `PortfolioCalculator` both take a `finalThrough`
  date: the newest session the exchange has finished with, which is what
  `ScheduleClock.lastFinalSession` turns the clock into. Today's session is in `daily_prices` from
  the opening bell - the daily feed is re-asked for the last three days on every refresh, so a
  half-traded session overwrites itself as it goes - and counting it the moment it arrived reported
  a **T+1 call as Expired at 10:00 on the session the card said to sell in**, priced to the first
  trade of the morning. On the thirty-session horizon the same bug is invisible; on a two-session
  window it is half the trade. The first fix for it waited for the *date* to turn over, which
  overshot by nine hours: a trade whose last session ended at 14:30 stayed open until 00:00 and was
  then announced whenever the phone next happened to look, which on most phones was the following
  morning. `lastFinalSession` is one definition for both this and `LatestPrice.provisional` - a card
  that calls a price still moving can never be one the scorer has already run a call out of time on
  - and a call can still settle late but never early. **Only running out of time waits** - a target
  reached or a stop broken inside the live session still settles it on the spot, because those are
  facts about the session whether or not it has finished, and expiry is the one verdict the rest of
  the day can still overturn. `PerformanceCalculator.report` and `PortfolioCalculator` each read the
  date once for a whole recompute, so a rebuild that crossed the close cannot judge its first calls
  against a different session from its last. `today` survives beside it in `PortfolioCalculator` for
  the one question that really is about the calendar: how many whole days past its deadline a trade
  the user is holding on purpose has run.
- **Judged** outcomes are full hit, partial hit, stopped, expired. Still open, entry never traded,
  ambiguous, not priced and **prices changed scale** say nothing about the channel and are excluded
  from every rate.
- An **expired call carries a return**, measured from the entry to the last close before the horizon
  ran out - where a reader following it still stood when the time ran out. It reported none before,
  which kept every expired call out of the average return while leaving it inside the rate that
  average is read beside, so a channel whose calls fizzle out flat read exactly like one whose calls
  all resolved. `settledOn` stays null: the market reached no level the call named. Expiry is rare
  now by construction, and it is the only place the horizon is ever named on screen - on the one
  call it actually caught, in that call's own outcome sentence.
- **How long a call took is a figure, not a leftover.** `sessionsElapsed` is printed bare on the
  call card - it used to read "6 of 10", a fraction of a deadline that no longer exists - and
  `ChannelScore.medianSessionsToHit` and `medianSessionsToStop` roll it up per source, as **Sessions
  to a target** and **Sessions to a stop** on the channel card, with the first also in the hero's
  sub-line. The pair is the point: stops in two sessions against targets in fifteen is a source
  asking a reader to take every loss quickly and every gain slowly, and no rate on the card says so.
  Both were capped at the old window, which is why they were barely worth printing before. A partial
  hit that fell back to the stop is counted only in the target figure - it settled on its target -
  or one call would describe how fast the source is right and how fast it is wrong at once.
- **A call re-posted on the next analysed session is the same call.** Channels print a standing
  recommendation every morning until it resolves, and one idea was collecting a judged call per
  posting - so a source running a daily table outweighed one that posts when it has something to
  say, on nothing but how often it posts. Same source, same stock, every level identical; move a
  stop or lift a target and it is a new call. Adjacency is measured against the sessions actually
  analysed rather than the calendar, so a session going by without the call starts a fresh one.
  `ScoredCall.repeatOf` is a **mark, not a deletion**: the card stays on its session, because that
  is the record of what the channel published that day, and every rate leaves it out.
- **A T+1 call says so on its card**, as a chip beside the outcome that opens the rule behind it.
  It is the one call with a deadline the channel printed itself, and that was the most consequential
  thing about a card the card never said — it reached the screen only as a caption under a figure
  and inside the outcome sentence behind another chip, so a two-session call and a thirty-session
  one looked identical until one of them expired. `ScoredCall.isTPlusOne` is the test, and it is
  **carried from the card's own `effective_date_basis`, not derived**. It used to read
  `entrySessions < windowSessions`, which held only while a T+1 was the one call whose entry closed
  early; the moment that band was allowed to trade across both its sessions the two numbers became
  equal and every T+1 chip on the screen would have gone quietly out.
- **A T+1 band is on offer for both of its sessions.** `T_PLUS_ONE_ENTRY_SESSIONS` equals the
  window, so a buy zone the market first reached on the sell session is a trade that was there to
  take. It was the buy session and no further, on the reasoning that a band first trading on the
  sell session was never takeable - which is a rule about settlement the cards do not print, and
  refusing an entry the market genuinely presented judges a call the channel never made. No call
  anywhere shortens its entry now; the parameter stays because how long a band was on offer is its
  own question, and a window is not an answer to it.
- A **split or bonus issue inside the window** makes the call unjudgeable rather than a loss. The
  levels were printed in the old money and every price after the split is quoted in the new, so a
  2-for-1 reads as a 50% collapse and files the call as a stop-out — silently, and against whichever
  channel happened to call that stock. A break dated on the window's *first* session still costs the
  call, because the levels were printed before that session opened; that can take a call made after
  the split, and it is the right direction to err in.
### Ranking the channels

Being right often is not the same as being worth following, and the ranking used to assume it was.

- **The order is what a call was worth, not how often it worked.** A hit rate is bought by moving
  the target closer to the entry: reach for +2% against a -10% stop and nine calls in ten get there,
  while the tenth takes back more than the nine made. `ChannelScore.averageReturn` - the mean over
  every judged call - is what decides the order, on the card and on the hero both.
- **The headline figure is the win rate, divided at the two targets** - `62% / 35%`, from
  `anyTargetRate` and `fullHitRate`, drawn by `winRateSplit` in `ui/InsightsScreen.kt`. The two are
  **nested, not disjoint**: the second is a subset of the first, so it reads as "reached a target on
  62% of judged calls, and ran the whole way on 35%". Splitting them into target-1-only and target-2
  would make them sum to the win rate and would print a first number *lower* than the rate the
  channel achieved, so a source that kept reaching target 2 would show a shrinking target-1 figure
  for doing better. The second is smaller and in `onSurfaceVariant` because it is the deeper cut of
  one rate rather than a rival to it - and because two full-size numbers beside a two-line Arabic
  name do not fit a card at `ChannelCardMinWidth` (280dp). `averageReturn` sits under it as the
  **Per judged call** figure, which is what keeps the card able to say why the list is in the order
  it is: neither number answers alone, and a rate on its own is the one that misleads.
- **The hero opens the page and sits in a card.** `SectionCard`, the non-expandable one, with no
  title — background only, so it keeps its own `BEST RECORD` overline rather than gaining a second
  heading in a second type style above it. It was loose on the page until then, which made it the
  only thing on Insights without an edge round it: readable while it stood alone, and an unfinished
  heading once a card sat directly beneath it. `OutcomeBar` takes `on = surfaceContainer` there and
  not `background` — that parameter is the ground its softened target-2 segment is composited onto,
  so a card round the hero without it draws one segment a colour the card never shows.
- **A channel still needs `MINIMUM_JUDGED_TO_RANK` (5) settled calls to lead at all.** Without it
  two good calls beat a month of evidence. Below the floor the figures are reported exactly as
  measured; they simply stop sorting above channels with a record behind them.
- Within the floor the sort key is `discountedReturn`: the average pulled toward zero by how little
  is behind it, `mean × n / (n + 5)`. Six calls at +5% (2.73) sit below fifty at +4.5% (4.09)
  without either figure being misreported. **A lower bound on the mean was tried first and is wrong
  for this data** - at the ten-to-thirty calls a channel actually has, the spread of stock returns
  is worth several points and the gap between two channels' averages a fraction of one, so the bound
  ranks on variance and almost nothing else. It put a +2%-target source at -1.55 above a source
  making more per call at -3.80, which is the exact ordering the change existed to overturn.
- `anyTargetRateFloor` is the **Wilson 95% lower bound** on the hit rate, in the card's sub-line as
  "target 1 at least X%". 6 of 6 is a true 100% resting on a floor of 61%; 40 of 50 is 80% resting
  on 67%, and the second is the better record. The normal approximation puts the first at 100%,
  which is the claim being questioned, so Wilson rather than that. It is named for **target 1**
  because it bounds `anyTargetRate` and nothing else - beside a headline carrying two rates, an
  unqualified "at least X%" would read as a floor under both.
- `averageRiskReward` is (target 1 − entry midpoint) / (entry midpoint − stop), over every call the
  channel made rather than only the judged ones - it describes the levels it prints, which it
  printed whatever the market did about them. The context a hit rate cannot be read without: 90% at
  0.3 to 1 is a losing source. A call whose levels contradict each other is left out rather than
  counted as risking nothing.
- **The record reaches the call card, which is where the decision is made.** `SourceRecord` in
  `ui/InsightsScreen.kt` prints one line under the channel's name: what a call from this source has
  been worth on average, over how many judged. Every other figure on that card judges the one call;
  this is the only thing on it that says anything about who made it. The whole record was measured
  for every channel and reached only the ranking — a page the reader has to think to go and read
  separately, while the grid of fresh cards is where a call is actually weighed. `averageReturn`
  rather than the hit rate, for the reason the ranking is ordered on it. Below
  `MINIMUM_JUDGED_TO_RANK` the figure **keeps its number and loses its colour**, which is the rule
  the channel card already followed; the words "too few to rank" are added here and not there
  because a card sitting in a session has no ranking around it to say so. Absent, not blank, for a
  source with nothing judged — a line reading "no record yet" on every card of a fresh install is a
  line on every card.
- **`CallOrder` lets the reader sort a session's calls, and alphabetical is still the default.**
  `PerformanceCalculator` goes on ordering by ticker — that is the record's own order, and anything
  reading a report without a screen gets it — while `AppPreferences.callOrder` decides what the
  screen lays out. Alphabetical is the one order that carries no information: a fresh report is a
  grid of twenty cards where the two worth reading were placed by the first letter of the stock.
  `SOURCE` sorts on `discountedReturn` and **not** the raw average, so a card and the ranking can
  never disagree about which of two sources is ahead. Nulls sort **last** in every option — a call
  whose source has no record, or whose levels contradict each other, has not earned the top of the
  list by being unmeasurable, and sorting them high would make a fresh install's order arbitrary.
  It is a view and never the record: `CallOrderTest` checks that every option lays out every call,
  because an order that quietly dropped the one unmeasurable card would be strictly worse than the
  alphabet it replaced. Stored by **name** like `portfolioOrder`, travels with the other settings,
  and moves no figure on the page.
- **A channel card is pressable, and it opens the method.** `ui/ChannelScoreSheet.kt`, a sheet from
  the bottom in the shape of the Ask AI answer, because that is already what this app means by "the
  longer version of the card you pressed". Every rule above was argued out and written down, and all
  of it lived here and in the source — a reader deciding whether to follow a channel was shown a
  verdict and no method. Bullets rather than prose, each keyed to a colour the outcome bars already
  taught, revealed in sequence at a bullet every 45ms so the eye is led down the list once. It
  states no verdict of its own and adds no figure the card does not carry: every line is either a
  rule or one of that channel's own numbers put into a sentence.
- **A session card says how its session went, in colour.** `SessionSummary` names every verdict and
  counts it in the hue that outcome wears everywhere else — the same shape the Portfolio's session
  cards have carried since they were built, and for the same reason: a folded card is most of what
  this page shows. A partial hit and a full one share the target's green, exactly as they do on a
  trade's chip, and the words beside them separate the two. Under the counts sits what one call from
  that session was worth, and the card's icon takes `PriceRole.forReturn` of it — grey where nothing
  has settled, which is what a session with nothing to say should look like. All of it comes from
  one `PerformanceCalculator.tally`, so a session's line cannot count differently from the rates
  above it. The title carries the **weekday**, because a run is aimed at a trading session and
  Sunday is one here.
- **`refine` re-runs the second pass, it does not carry it over.** Crowding, re-postings and the
  shortlist signals all describe a call against the calls *around* it, and a filter changes which
  calls those are — a card left reading "2 other sources" beside a page showing one of them would
  be the screen disagreeing with itself. `enrich` is one function called by both `report` and
  `refine` for the same reason every other figure is recomputed in both: the figures on screen
  describe the calls on screen.

### A call the market has finished with

Everything on the Insights tab is derived on every recompute and deliberately so — a figure worked
out from the prices cannot drift from them. `settled_calls` is the one exception, and it is narrow:
a call that reached target 2, broke its stop, or banked target 1 and gave it back to the stop is
**closed**, and no session after any of those can change it. Replaying thirty sessions of prices to
be told so again is work with one possible answer, and the query for those sessions is the expensive
half of it. `model/SettledCall.kt` holds the row; `PerformanceCalculator.report` takes the map and a
callback, so the calculator stays free of Android like every other input it has.

- **Only the three that settle.** An expiry and an entry that never traded are settled in principle,
  and are left live: both are rare, both cost nothing to derive, and freezing them would widen the
  one stored thing on this page for no gain. A partial hit still standing is emphatically not
  frozen — target 2 is still in reach, which is the whole reason a call runs to its settlement.
- **The key carries the levels and the window**, not just the call. A report re-read by a newer
  prompt can come back with a different stop on the same card, and a verdict reached about the old
  one says nothing about the new one — so it asks under a different key, finds nothing, and is
  scored from scratch.
- **The sessions it was judged on are stored with the verdict.** They are the evidence: the card
  draws them, and a frozen verdict beside a table read from somewhere else could disagree with
  itself. A JSON column, the way `scheduled_jobs` keeps its settings — the shape belongs to the row
  and nothing queries into it.
- **Two events drop it**, and both rewrite the prices underneath a verdict: a **heal**, which
  replaces a stock's whole stored series, and a **newly recorded change of scale**, which says the
  levels and the prices were never in the same money. Only a *new* break — `savePriceBreaks` is
  called with whatever the last fetch found, and treating a re-report as news would throw the record
  open on every refresh forever, on every stock that has ever split.
- **Local and never synced**, exactly like `price_events`. Every device fetches the same public feed
  and settles a call the same way, so shipping one phone's conclusion into another's evidence would
  put an opinion where a measurement belongs.
- The faults, the crowding and the shortlist signals are **not** part of it. Those describe a call
  against the calls around it, which change as the record grows, and `enrich` re-derives them for
  settled and running calls alike.

### Ordering the two events inside one session

A session that first offered the entry **and** reached a target says nothing about which came first,
and crediting the target would credit a call nobody could have taken — the price may only have
fallen into the buy zone after the target was hit. Three answers are tried, cheapest first.

- **The open**, where it already sits inside the band. It precedes every other price of the day, so
  it settles the order for free. Unchanged, and still the first thing asked.
- **Five-minute bars for that one session**, fetched by `IntradayRepository` and stored in
  `intraday_bars`. The first bar covering the buy zone against the first bar reaching the target.
  Bars are kept **only for sessions a call could not order**, and only their high and low: a year of
  five-minute bars for two hundred stocks is millions of rows to answer a question that arises a
  handful of times. A closed session's bars never change, so a row is written once and never
  refreshed, and a heal (`clearIntraday`) drops them with the prices — a split rewrites the intraday
  history too, and bars kept across one are in the old money.
- **Scoring the window both ways.** The entry is a fact of that session under either reading, since
  its low traded through the band, so the reader holds from its close whichever way round it
  happened and only that day's own target is in doubt. Score it as entry-first and as target-first:
  where the two agree there is nothing left to be ambiguous about, and the **pessimistic run is
  reported** — where they agree it is also the later settlement date, because it never credits the
  unproven target. `AMBIGUOUS` survives only when the two genuinely disagree.

Fetching happens on the **daily refresh, not when a card is opened**. The feed keeps five-minute
bars for about 60 days, so a call nobody looks at inside that window becomes permanently unorderable
with no sign that a clock was running. `intraday_fetches` records that a session was asked about
**even when nothing came back**, or the sessions past the retention wall — exactly the ones with
nothing to give — are re-requested on every refresh forever.

`Ambiguity` now has two values, because they ask for different things: `ENTRY_AND_TARGET` may still
be answerable by fetching, `SAME_INTRADAY_BAR` never will be.

### Where the stock is now

Every other figure on a call's card stops short of saying it: peak and trough are the extremes over
the sessions the call was replayed on, and the return is measured to wherever the call settled — so
a card could report a target hit three weeks ago and give no clue what the price has done since.
**Latest close** is that figure, with its session date and the move from the entry midpoint under
it. The two are labelled plainly as **Peak** and **Trough** with the session that set each beneath:
they stop at the settlement session on a full hit or a stop-out but go on past it on a partial hit,
and no single label was true of all three - the date says the part that matters.

- It comes from `PerformanceReport.latestPrices`, keyed by ticker and filled from
  `LocalDataStore.latestSessions()`. A property of the **stock**, not of any one call: taking it
  from `ScoredCall.sessions` would give the end of that call's window, which for anything settled is
  not the current price. `refine` leaves it alone, so a filtered view still knows where its stocks
  stand.
- **"prices to \<date\>" on the page comes from the prices themselves**, not from
  `lastPriceRefreshDay` — that records a refresh going out, not coming back with anything, and on a
  day the exchange did not trade the two are days apart.
- A session dated today is marked **"still trading"**, because it is: the close is going to move.
  `DailySession.inconsistent` catches the same thing from the other side — GBCO came back on 16
  August with a close of 30.16 beneath a low of 30.31, the two fields written at different moments
  of an unfinished session.

### What happens when a stock gets recommended

`ChannelScore` says whether to read a source. `StockScore` says whether the market has ever done
what anybody printed about **this stock**, across every source that named it — a different question,
and one no rate on the page could answer.

It is measured and it is **not a section on Insights**. It had one, under the ranking, and it was
the wrong question in the wrong place: which stock the market has been kind to is not which source
is worth reading, and a second ranking under the first invited the two to be read as halves of one
answer. The figures still reach the reader through the two places they decide something — a card's
own shortlist signals, and the list inside the Ask AI prompt.

- **One piece of arithmetic, two groupings.** `PerformanceCalculator.tally` returns every figure a
  group of calls yields, and both `channelScores` and `stockScores` are built from it. Two copies
  would be two copies that agree until one is touched, and every rule folded in — repeats out of
  every rate, risk to reward over every call rather than only the judged ones, medians rather than
  means — was argued out for channels and is just as true of stocks.
- Grouped on the **normalized** ticker, or `COMI` and `COMI.CA` split one stock's record in half and
  rank both halves. Same floor, same order, same thin-record rule as the channel cards.
- `sources` counts distinct channels **over every posting**, repeats included: a source that named a
  stock named it, however many mornings it went on saying so.

### The two questions the record can ask itself

`RecordSplit` sets one subset of the record beside the rest of it. Both instances rest on something
the app has always detected and always thrown away — that several sources named one stock for one
session, and that a source kept re-posting a call rather than saying it once.

- **Two figures, never a verdict.** At the ten to thirty judged calls each side actually has, the
  spread of stock returns swamps the gap between two means, so "consensus calls do better" would be
  reading noise out loud. The counts are printed beside the percentages and the section ends by
  saying so in as many words.
- **`MINIMUM_JUDGED_TO_COMPARE` is 10, higher than the ranking floor**, and deliberately: ranking
  picks one source out of several and is wrong recoverably, while a split makes a claim about a
  *difference*, which needs more behind it than an ordering does. Below it the split is **absent**
  rather than hedged — and on a fresh record one side is usually empty, which is exactly the state
  a hedge would dress up as a finding.
- `ScoredCall.alsoCalledBy` counts the **other** sources, not all of them: a card saying "1 source"
  about itself is a card counting itself as company. `repostings` is zero on a repeat, because the
  figure belongs to the call that was kept standing rather than to the standing.

### Aiming the paid question

`CallShortlist.signals` answers which of twenty cards is worth an Ask AI request. Everything it
needs was already computed and already on the screen; the request was simply being fired at whatever
card the reader had scrolled to.

- **It ranks attention and predicts nothing.** No signal is evidence about where a stock is going;
  each is a reason this card is a better *question* than the one beside it.
- **Counted, never weighted.** Weights would imply the four had been calibrated against outcomes,
  and they have not been. A count says exactly what it is — how many separate things line up on one
  card — and the card names its own signals, so the order can be checked by eye. A bare score would
  be a recommendation wearing a statistic's clothes, which nothing else on this page does.
- **A missing input raises no signal rather than a negative one.** A source with no record, a stock
  nobody else called, a call with no levels, a stock with no price: the shortlist is a reason to
  look, and absence of evidence is not a reason to look away.
- `MINIMUM_JUDGED_FOR_A_RECORD` **must equal** `PerformanceCalculator.MINIMUM_JUDGED_TO_RANK`, or a
  card raises "strong source" off a record the ranking itself declines to rank. It is stated rather
  than imported because `model` depends on nothing above it, the same reason `Scoring` holds its own
  constants, and `CallShortlistTest` pins the two equal.
- `CallOrder.WORTH_ASKING` sorts on the signal count, with risk to reward as the tie-break — at four
  signals a great many cards share a count, and the alphabet deciding which is seen first is the
  thing that enum exists to stop.

### Telling the user a call has become takeable

`TradeAlerts` watches trades the user is in. `CallAlerts` watches the calls they are **not** in,
which was the gap: prices refresh through the session on their own, so the app knew at eleven in the
morning that a stock had traded into a buy zone a source printed, and told nobody unless they opened
it.

- **A fact, never an instruction.** *AMOC has traded into the buy zone this source printed* — the
  same register as "your trade hit its target". The channel is named because the band is its claim
  and not the app's.
- **Default off, and alone in that.** Every other notification here reports something that happened
  to a thing the user chose — a trade they took, a deadline they set. This one arrives unprompted
  about a call they only read, and a feature that starts buzzing about stocks on its own is one that
  gets the whole app silenced. Its own channel beside the trade one, because Android silences a
  whole channel at a time and the two are different questions.
- **Only the crossing *into* the band.** A price drifting back out is not news, and announcing both
  directions would double every notification for a stock moving around inside its own zone.
- **First sight is recorded and never announced**, exactly as `TradeAlerts` needed. A settled call,
  a call already held, and a re-posting all say nothing — the first is history, the second is the
  Portfolio's to speak about, and the third is the same bet as the call it repeats.
- **A call whose stock has no price is kept, not forgotten.** It is marked as still watched *before*
  the price is looked for. Dropping the reading would lose which side of the band it was on, so the
  day a quiet feed comes back, a band the price had been sitting in for a fortnight would be
  announced as though it had just been reached. `CallAlertsTest` caught this before it shipped.
- **Keyed on the call, not the holding.** `alertId` is `opinionId`'s key — ticker, session *and*
  channel — because two channels calling one stock print two different buy zones and the price can
  be inside one and outside the other. Keyed on the shared `positionId` the second call could never
  be announced at all. The notification still *carries* `positionId`, because that is what
  `AppState.openCall` and the arrival effect match on.
- **Device-local and never synced**, for the reason `position_status_seen` is: two devices holding
  one record would each announce the same stock coming into range.
- **It books no background work.** The sweep rides the price refresh that was already happening, so
  switching it on adds a notification and not a wake-up. The switch gates the notification and never
  the sweep, so turning it on reports what happens next rather than a backlog.

### Rebuilding a history the daily feeds do not carry

A stock whose legacy `SYMBOL.CA` symbol is a 404 gets only the ISIN feed's single session, so a call
on it keeps a permanent hole in its window: it never completes, so it never expires, never resolves,
and sits pending for good while quietly sitting outside every rate. `IntradayRepository.dailyHistory`
builds one out of the intraday feed, which does hold the stock.

- **Hourly, not five-minute**, and that decides whether the feature is worth having. Both were
  measured on 19 August 2026: the 5m feed reaches back about four weeks — under the 30 sessions a
  call is judged over, so it would leave the exact hole this exists to fill — while `interval=1h`
  reaches back about two years. A daily bar needs a session's extremes and its two ends, and an hour
  gives those as exactly as five minutes does. `HOURLY_RANGE_DAYS` is 700, inside the measured two
  years with room: a window wider than the feed keeps is refused outright rather than trimmed, and
  a refusal costs the whole history rather than its oldest end.
- **Nothing is invented.** The open is the day's first bar's open, the close its last bar's close,
  the high and low the extremes across it, the volume the sum. That is the definition of a daily
  bar, not an approximation of one. A day whose bars carry no usable price is **dropped**, not
  stored as a session that traded at nothing — a low of zero sits under every stop ever printed.
- **Every rebuilt session is marked, and the mark is the point.** This app records rather than
  corrects everywhere else — it refuses to guess a split ratio and rescale a year of prices — so a
  fabricated row presented as what the exchange reported would be the sharpest break with its own
  rules in the codebase. `DailySession.derived` carries it; the call card's session table says how
  many of its rows were rebuilt and why; the refresh says "N rebuilt from hourly bars".
- **It rests on the `source` column and needed no migration.** That column has carried provenance
  since the table was created and had exactly one value in it, so a second value is the column being
  used for its purpose rather than a flag smuggled through it — and on `daily_prices` no migration
  means no chance of an upgrade taking the prices already on the phone with it. What can go wrong is
  the string, so `PriceRepositoryDerivedTest` pins the round trip on **all three** read paths, plus
  the case of a row with no source at all, which reads as reported because that is what it is.
- **A reported session always replaces a rebuilt one.** The table is keyed on (ticker, session_date)
  and replaces on conflict, and the refresh writes the rebuilt rows **first** and the reported ones
  second — so the day a stock's real feed comes back, its rows take over for free and this can only
  ever narrow. It is also why `merge` lays the reported sessions over the derived ones and not the
  other way round.
- **Asked for only when the stock really is that thin** — fewer than `THIN_HISTORY_SESSIONS` (5)
  known sessions after the daily merge. A working legacy symbol answers a 40-day window with about
  25 sessions and one without answers with a single session whatever is asked, so five sits in a gap
  no threshold inside behaves differently in. Once a history is built the stock is no longer thin
  and is never asked again. A genuinely new listing is thin too, and rebuilding its history returns
  exactly the sessions that exist — right, not wrong.
- **The granularity is checked, not assumed**, which is why `parseSessionBars` is its own function.
  Legacy symbols ignore `interval` and answer with daily rows; aggregating those would produce a
  "derived" session built from one daily bar, identical to what it came from and marked as though
  finer evidence stood behind it. `dailyHistory` refuses a legacy symbol outright for the same
  reason `fetchOne` does.
- **`SessionBar` is deliberately not `IntradayBar`.** The stored bar carries a high and a low and
  nothing else, because that is all the ordering question needs; one type for both jobs would have
  fields that are populated or null depending on whether the bar had been through the database, and
  the aggregation would silently build sessions with no open on every bar that had. `SessionBar` is
  only ever built fresh from a response and never stored.

### Reading the extraction as sceptically as the feed

`PriceSanity` has guarded the price feed since the beginning and nothing has ever guarded the
**extraction**, which is the other half of every judgement the app makes. `CallSanity.faults` is
that check, run on every recompute and stored nowhere.

- **The failure it catches is a misread, not a bad call.** A source can be wrong about a stock all
  day and still print four numbers that hang together; a vision model reading a screenshot can put
  the decimal point in the wrong place, swap two target rows, or pick up the stop from the card
  above. A band read somewhere the stock has never traded mostly neutralises itself — the entry
  never trades and the call leaves every rate — but a **stop read into the target row scores
  perfectly plausibly**, and counts against whichever channel happened to be misread.
- **It marks and never excludes, and that was a deliberate choice.** Every rate counts a suspect
  call exactly as before; the card gains an amber chip that opens an explanation. Excluding one
  would move published figures on the strength of a heuristic, and a heuristic that is 95% right
  would then be silently rewriting a channel's record on the other 5%. Reporting is recoverable;
  a rate that quietly dropped calls is not.
- **Amber, not error red.** Red on a call card already means the stop took it out, and a second red
  meaning something entirely different is one the reader has to stop and disambiguate.
- **The distance threshold is loose on purpose.** `MAX_DISTANCE_FACTOR` is 2: a patient call naming
  a dip well under today's price is ordinary and must stay clean, while a misplaced decimal is out
  by a factor of ten. Measured against the session's **traded range** rather than its close, or a
  stock that moved 5% that day would collect a fault for a band inside the range it actually traded.
- **An unpriced stock collects no fault for being unpriced.** Without a session the structural
  checks still run — they need no price — and only the distance check is skipped. The other way
  round, every call on a stock the feed has never carried would be captioned as misread, which is
  the app blaming the extraction for its own missing data.
- Pure, with no Android in it, like `Scoring` and `PriceSanity`. `CallSanityTest` spends most of its
  length on the calls that must come back **clean**, because the two mistakes do not cost the same:
  a missed misread leaves the app where it has always been, and a false one puts a caveat on an
  honest call on the one screen whose purpose is to be trusted.

### When the feed goes quiet

Three things can happen to a stock's prices, and all three end the same way: the app goes quiet
about that stock rather than wrong about it, and every rate on the page quietly rests on fewer calls
than the reader thinks. That is exactly why they need saying out loud. `PriceHealth.assess` is the
one place that does.

- **The card that explained it in words is gone**, removed on the owner's decision of 2026-09-03
  along with the whole `Price feed` section of Settings. `PriceHealth` is untouched: it is still
  computed on every recompute, it still raises the "price feed has gone quiet" notification, and
  `PriceHealthTest` still covers it. What went is the page — the per-stock fault list, `FeedFaultRow`
  and `FeedFault.plainly`, the sentences that named each affected stock and said whether fetching
  again could help. `FeedFault.detail` is unaffected; it is the short form the record keeps.
- **What is left of it is one line**, in `General → Prices` and only when something is actually
  wrong: how many stocks are not coming through and how many calls they are holding, in the error
  colour where any call is waiting. That is the half of the card anybody acted on — the count of
  broken symbols is trivia, and how much of the record they are holding is the reason to read it at
  all. It is a **state** rather than a report, so it is drawn from `priceHealth` on every recompute
  like everything else on that line.
- **No screen names which stock has gone quiet any more; the database does.** `feed_checks` and
  `feed_faults` are the log (schema 27, `LocalDataStore.saveFeedHealth`), and `Save diagnostics`
  already copies the file they live in — so which stock, which fault, how stale and how many calls
  it was holding all come off a phone without a cable, which matters for an app other people use.
  What is *not* recoverable is the prose: the table stores `STALE`, not the sentence explaining
  what a retired symbol looks like from here. Bringing `FeedFault.plainly` back was declined on
  purpose — ask before proposing it.
- **A check is recorded only when it says something the newest recorded one did not.** A recompute
  runs on every price refresh, so writing each one would fill the log with two hundred identical
  "everything is fine" rows and push out the check that actually changed. The comparison is a
  signature over the stock count, each stock's faults and its calls held — sorted, so faults merely
  coming back in a different order is not a change. **A clean check is still a row**: a log that
  only ever wrote when something was wrong could not separate "checked, nothing wrong" from "never
  checked", which is exactly what the card's always-present list existed to say.
- **Pruned to the newest 200 checks**, inside the same transaction as the write that makes room, so
  a log that never changes is never opened for writing at all. Nothing reads it back for the
  screen — the line in `General → Prices` is derived from `priceHealth` on every recompute exactly
  as it always was, so the table cannot disagree with what is on screen. Device-local and never
  synced, like `price_events` and `session_events`.
- A **Fetch prices** button sits under that line, offered whatever the state, because it is also how
  a reader confirms nothing has changed. It reads the free public feed and sends nothing to the
  model, and its own note says so.

- **It replaces nothing and it is not the toast.** The refresh still finishes with
  "Priced 40/42 · 2 unpriced · 1 stale"; that reports a **refresh**, and is gone from the screen a
  second later. This reports a **state**. `unpricedStocks` and `awaitingSessions` had been on
  `PerformanceReport` since the beginning and were drawn by nothing at all, so the state a reader
  most needs before believing a hit rate was the one state the page never showed.
- **Derived, never stored.** Every input is already read on a recompute — the newest session per
  stock, `price_events`, and the scored calls — so it needed no table and no migration, and it is
  right after a restart for the same reason an outcome is. Computed on the IO thread inside
  `recomputePerformance`, off the read that recompute has already paid for.
- **The figure it exists for is `callsHeld`**, not the count of stocks. A tally of stale symbols is
  trivia; "these 4 stocks are holding 11 calls out of every rate above" is the sentence that changes
  how the page is read.
- **That claim has to be true, so the rule is deliberately narrow.** Only `UNPRICED` under a missing
  or frozen feed, only `PRICE_BREAK` under a recorded break, and only `OPEN` under a stale one. A
  call whose entry never traded is unjudged because of what the **market** did, and sweeping every
  unjudged call in would let one stale symbol appear to suppress a source's whole record. Repeats
  are excluded, the same rule the rates follow.
- **A stock carries every fault it has, not the worst one.** Frozen *and* split is a real state, and
  a list naming only the more severe would hide the other on the stocks with most wrong with them.
  The one exception is that a stock with no history at all is `UNPRICED` and never also `STALE` —
  both are true in a loose reading, and reporting both counts one broken stock twice in a list whose
  whole point is a count.
- **Only stocks the record names.** The catalog holds every Cairo listing; reporting a frozen feed
  for a stock nobody was ever recommended is a page of noise hiding the four rows that matter.
- Read off the **whole** record and never a filtered view, for the reason `PortfolioCalculator` is
  not filtered: a channel filter is a view of the calls, never a claim about which prices are
  broken. It is computed on every recompute whatever Insights is showing, which is what lets it sit
  on a different screen from the record it describes.
- No Android in it, like `PriceSanity`, and it borrows that file's `MAX_SESSION_AGE_DAYS` rather
  than choosing its own — two answers to "how old is too old" is one of them being wrong.

## The portfolio

Pressing **Bought** on a recommendation card records a `Position`; the Portfolio tab is where every
trade is then managed, in whatever state it has reached.

- The **deadline belongs to the recommendation**: N trading sessions from the session the call was
  made for, whatever date the user bought on. Buying two days late does not buy two extra days.
- Status comes from `Scoring.score` with **no entry band** - the user has bought, so the entry is a
  fact rather than something the market must still offer - over only the sessions actually held. A
  target reached before the purchase is not the holder's gain. This is why the 2% stop rule and the
  float slack are identical to every other judgement the app makes.
- **Every percentage is measured from the user's own prices.** Closed by hand it is realized, from
  the price they gave; otherwise it is an estimate, marked at the stop, the target, or the last
  close, and labelled as an estimate.
- **A sale can be made in two parts, because that is how one of these calls is usually taken**:
  half the holding at target 1 and the rest at target 2, on two different days. The Sold dialog
  offers the call's own two targets prefilled and a share that starts at 50%; typing 100 collapses
  it back to the one price and one day a sale used to be, which is why there is no second dialog and
  no switch between them. `Position.exitPrice` still holds **one** price - the legs weighted by the
  split - so the return, the ladder, the win rate and every average go on reading a single figure
  and none of them knows a sale can have parts. `exitPrice1`, `exitDate1`, `exitPrice2` and
  `exitSplitPct` sit beside it saying how that figure was made up, and are null on a sale made at
  one price, which is exactly what such a sale is. `exitDate` is the second leg's day rather than a
  fifth column repeating it, since the day the position went flat and the day the last part went
  are one fact. **The estimate for a trade with no recorded sale is deliberately untouched**: a full
  hit is still valued at target 2. Applying the split there would silently restate the return on
  every closed trade already on the phone, and the split is what the reader *did*, not what the call
  said.
- **The card draws the levels before it lists them.** `PriceLadder`, the same drawing Results and
  Insights use, with one difference that is the whole point of this tab: the entry mark is the
  price actually paid rather than the band the channel printed, and the arrow is where the trade
  stands now rather than the call's high-water mark. Nothing is plotted across a `priceScaleChanged`
  break - the levels are old money and the price is new, so the arrow would point at a place on the
  axis that does not exist. Under it, two named groups rather than eight loose figures: **Your
  trade**, carrying the risk-to-reward worked out from the paid entry, with each level captioned by
  its distance from that entry; and **Where it stands**, carrying the return, the last close **with
  the session it closed on**, the peak and trough **since the entry**, and the deadline. The return
  is printed once - it used to be a figure and again a percentage in the line below, and one number
  in two places on one card is a number the reader checks against itself.
- **`peakSinceEntry` and `troughSinceEntry` are the scorer's own, over the held sessions.** They
  were computed on every recompute and dropped, so the one question the card could not answer was
  how far up a trade had been before it came back. Since the *entry*, not the call, for the reason
  the verdict is: a stock that ran to its target before the user bought did not do it for them.
  Null across a split, exactly as the return is. `sessionsHeld` is the same distinction counted -
  `sessionsElapsed` runs from the call, and on a trade bought late the two differ.
- **A price and the session it was set on travel together.** `latestQuote` returns both, so a feed
  several sessions behind cannot print a stale close as though it were today's. `PortfolioCalculator`
  takes `latestQuoteFor` for that reason; `evaluate` still takes the price and the date loose,
  because everything it scores needs only the price.
- **Channel hit rates are deliberately not affected.** Insights judges the source on the levels it
  printed, not on what the user did about them — the two answer different questions, and a channel's
  record must not move because someone bought late or sold early. A card the user is in gets an
  outline, one extra line, and a press that leads to the trade; no figure on it moves.
- **The two cards for one call press through to each other.** A held call in Insights opens its
  trade in the Portfolio, and that trade opens the call back — the two tabs answer different
  questions about one recommendation, and reading both used to mean finding the second by hand. The
  key is `ScoredCall.positionId`, which is `positionId(normalizeTicker(ticker), openedOn)`: the same
  key `heldFor` matches on, so a card can never lead somewhere its own outline disagreed with. Only
  a card with a counterpart is pressable at all — an untraded call and a trade whose analysis has
  been deleted answer no press, because there is nothing to open. Arriving opens the section holding
  the card, scrolls to it, and flashes its edge (`arrivalFlash`, shared with the report a
  notification opens). **A filter is cleared only when it is what hides the target**: a link landing
  on "nothing matches these filters" is broken, and a filter thrown away on a trip the reader is
  about to make back is one they have to set again. Two channels calling one stock on one session
  are two cards and one holding, so both flash and the first is scrolled to.
- A trade **snapshots its levels and its window**. Deleting the report, re-running the session, or
  changing the default trade window afterwards must not rewrite a trade that already happened. The
  window is editable **by hand and only by hand**, from Edit trade on the position's card — that is
  the user moving their own deadline on purpose, which is the opposite of a setting moving it
  silently. Moving it can close a running trade or reopen one the deadline had closed.
- **A trade taken on a T+1 call says so, in a pill on its card.** `Position.isTPlusOne`, copied off
  the card at the purchase alongside the levels and for the same reason — the report behind a trade
  can be deleted or re-run, and neither may take back what the trade was taken on. It is
  deliberately **not** derived from the two-session window: that window is what a T+1 is offered and
  also what a reader whose default is two takes on every call they record, so deriving it would put
  words in the channel's mouth. Without it the shortest deadline in the app reached the Portfolio as
  a bare "2 of 2 left", which reads as a trade about to run out rather than one that was always
  meant to last two sessions — the same silence the chip in Insights exists to break, on the screen
  where the money actually is. Trades recorded before the column are marked by
  `AppState.markTPlusOneTrades`, which reads `ScoredCall.isTPlusOne` off the record by the
  `positionId` the two tabs already press through on: **set, never cleared** — a deleted report says
  nothing about a trade rather than saying it was ordinary — and written without touching
  `updatedAt` or publishing, because this device is finishing a record it already had rather than
  changing the trade. The pill's sentence names the user's own window where they typed over the two
  they were offered, so it never claims the channel set a deadline the reader chose.
- The buy dialog offers `defaultTradeWindowSessions` and lets it be overwritten. `windowCustom`
  records that the user typed over what they were offered, rather than being recomputed by comparing
  against the setting later: the setting moves, the choice did not. **This is the only window left
  that anybody sets**, and it decides when a trade expires and nothing else - the channel that made
  the call is judged on how long the call took, never on how long this reader gave it.
- **Keep Open defeats every automatic close but target 2** — target 1, a stop, and an expired window
  all stop ending the trade; only a recorded sale or a full target hit does. A full hit is the trade
  doing the thing it was bought to do, so there is nothing left to hold it open for, and the button
  is not offered on one. `marketStatus` goes on saying what the market did, so the card still reads
  "the call itself: stopped out". The one status that is rewritten is `EXPIRED`, which would
  otherwise put the word "Expired" on a card sitting in the Open section. `PositionView.keptOpen` is
  `position.keepOpen && open`, so the flag survives on the row but nothing claims a closed trade is
  being kept open; the pill carries the state and the card's ⋮ menu undoes it, because a button
  repeating the pill was taking the place beside Sold.
- **`ranOutOfTime` is the one rule behind both the Expired section and overdue**: the deadline has
  passed, no sale was recorded, and the trade ended at neither target 2 nor the stop. A trade that
  reached its target did what it was bought to do; one the stop took out ended where the call said
  to; one the user sold ended where they say it did. What is left ran out of time while they were
  still in it, and that is the only case worth chasing. It deliberately covers the trade the deadline
  closed while the user was still holding it, and a partial hit that never saw target 2 — which
  keeps its own "Partial target hit" chip, because "Expired" would hide that it got somewhere.
  Counted in calendar days from `deadlineDate`, with `today` injected into `PortfolioCalculator` so
  it can be tested at all. The deadline lands at the **close** of the window's last session, so the
  first morning a trade is expired is the morning after it - and the clock reads one day from that
  morning, which is exactly when the reminder used to arrive. `sessionsRemaining` counts the same
  way: the session trading right now is owed, not spent, so a T+1 trade reads "1 of 2 left" through
  the session it is to be sold in rather than dropping into the Expired section at its bell.
- Nothing about overdue is cached: it is derived on every recompute from the stored deadline and the
  current date, which is what makes it right after a restart. A `LifecycleResumeEffect` in
  `MainActivity` recomputes when the app returns to the foreground, so a phone left on the Portfolio
  tab overnight does not go on showing yesterday's count.
- **The Overdue card is the first thing on the tab**, above Your record, and it is absent whenever
  nothing is late. Overdue was only ever *findable* before: a count in the record card, then a scroll
  through folded sessions hunting for what it was counting. `overdueRoster` is the whole record
  filtered and ordered most-overdue-then-ticker - `PortfolioOrder.URGENT`'s own rule, so the card
  cannot lead with a trade the list beneath it disagrees about - and the record's `overdue` StatTile
  is gone, because a count beside a list that names the same trades is a figure to reconcile.
  Deliberately **not** narrowed by the date filter, for the reason `PortfolioCalculator` is not: a
  date picked on screen is a view of the positions, not a claim about which are late.
- One **tile** per late trade rather than a row, through the same `responsiveColumns`/`ResponsiveRows`
  the position cards use - two across at 411dp, four on the Fold and the tablet. It carries ticker,
  return so far, the day count and, in the space that saved, `position.entryDate` - **the entry date,
  not the call's**, which is the one figure saying how long the trade has actually been held; the
  session it opens into is titled by the call date, and on a trade bought late the two differ. The
  day count is bare (`6d`) and the only thing in the error colour: under a heading reading Overdue it
  cannot be read as anything else. `kept open` / `expired` is the fact that gives way if a tile is
  too narrow, and it is there because the two are not the same - one is the user holding on purpose,
  the other is the app having stopped tracking a trade they are still in.
- A press calls **`AppState.openPosition`** - the same entrance a call in Insights uses - so the
  arrival effect in `PositionSection` does the rest: clear the date filter only if it is what hides
  the trade, unfold the session card, scroll, flash the edge. A second path would be a second way for
  the app to disagree with itself about where a trade is.
- **One card per session, holding that session's trades in every state**, with **Open**, **Expired**
  and **Closed** as sections inside it. A day's trades were one decision, and splitting them across
  an open list and a closed one meant scrolling to find the other half. **Every card starts
  folded**, one holding a running trade included — enough traded sessions and the cards that opened
  themselves were most of the screen, where the list of dates is what makes the record readable.
  Its summary is what a folded card informs with: it names all three counts, each in
  its state's colour — open is `primary`, expired is the **amber in `ExtraColors`**, closed is
  `onSurfaceVariant`, and the chips in `TradeControls` use the same three so a section and the trades
  under it agree. Expired is deliberately neither red nor purple: a trade that ran out of time can be
  up 5%, so error red would report a loss it never made and would collide with Stopped out, and
  `secondary` is a hue that means nothing in this app. Amber is **added** to the palette rather than
  borrowed from it, because every scheme role is already spoken for — see `ExtraColors` and
  `LocalExtraColors` in `theme/Theme.kt`, provided by `EgxAnalyzerTheme` so it follows the app's own
  light/dark setting rather than the system's. The overdue pill keeps `errorContainer`: amber says
  out of time, red says and you are late.
- **Positions is one card, holding its own filters and every session card inside it.** It was a
  loose `titleLarge` heading, then the filter shelf, then a run of cards, all sitting directly on
  the page — a section with no edge of its own to say where it began or ended, which is how the
  shelf's fill came to read as continuous with the card beneath it. A `SectionCard` gives it one,
  and the filters inside it are unambiguously *its* filters rather than something floating above
  whatever comes next. The heading drops from `titleLarge` to the `titleMedium` every other card on
  the tab uses, which is the point: Overdue, What happened and Your record are cards, and Positions
  was the one section pretending to be a page. **The session cards inside take
  `surfaceContainerHigh`** — a card within a card goes one step up or it is the same fill as its
  parent with nothing but a hairline between them, the rule `OverdueTile` and `EventTile` already
  follow. `ExpandableSection.containerColor` is named by the caller rather than assumed, because
  only the caller knows what its card is sitting on.
- **A card whose children are cards holds them in by `Space.s`, not `Space.l`.** Three frames stand
  between the page and a trade — the Positions card, the session card, the trade card — and every
  one of them was paying a full 16dp inset on top of a hairline it had already drawn, which left the
  trade card **285dp of a 411dp cover screen**: 126dp, nearly a third of the panel, spent on chrome.
  `SectionCard.contentInset` and `ExpandableSection.contentInset` are that dial, `Space.l` by default
  so every other card on the tab is untouched, and both give it back 32dp — 317dp on the cover, 335
  on each of the unfolded panel's two columns. **The heading and its rule keep the full inset**
  whatever the content takes, which is the whole reason the two are separate paddings now: a title
  sitting 8dp from its card's edge while every other title on the page sits at 16 is exactly the
  almost-aligned the spacing scale exists to stop. The trade card's own `Space.m` is deliberately
  untouched — it is the innermost, and the only one of the four holding content rather than another
  card. The reduced inset is **named by the caller** for `containerColor`'s reason: only the caller
  knows whether what it is about to draw has an edge of its own.
- **`PortfolioOrder` holds the sort rules, for the screen and the calculator both.** `URGENT` is the
  default and what `PortfolioCalculator` groups by, so anything reading the portfolio without a
  screen gets the order the screen opens on. It sorts overdue-first then newest — date order alone
  was precisely backwards, because a trade is overdue *because* its call is old. The two date orders
  carry no such override, on purpose. Every option sorts **both** levels: cards by session date,
  positions inside a section by entry date, which is the only date that separates two trades taken
  on one call.
- The Portfolio's **date filter and sort reuse Results' own controls** — `SingleSelectFilter` over
  the sessions actually held, and `SortFilter`, both inside a `FilterRow`. **Filtering happens in the
  screen, never in `PortfolioCalculator`** — `OverdueWorker` raises the daily reminder off the whole
  record, and a date picked on screen must not silence it. The record card above the row ignores the
  filter; it means the whole record.
- **The order is stored, the date filter is not**, and the difference is what each does to the
  screen. An order hides nothing, so `AppPreferences.portfolioOrder` keeps it across restarts —
  written by **name**, because storing an enum by ordinal would silently reinterpret every install's
  choice the moment the options are reordered, and `enumPreference` falls back to `URGENT` on a value
  this build no longer knows (`SettingsRepositoryTest` covers both). A date filter that persisted
  would greet someone weeks later showing one session and nothing else, which is how a user concludes
  their trades have gone missing — so it stays a session-only `remember`, like Results'.
- **A kept-open trade past its deadline sits under Open, not Expired**, and carries the overdue pill
  there. Expired is where the app parked a trade it stopped tracking; putting that word over one the
  user deliberately kept open would undo the feature. So Expired ⊆ overdue, but not the reverse.
- `PortfolioStats` says **settled**, not closed, and counts every trade no longer running, expired
  ones included. The win rate and the averages have to, or they stop describing the record — a trade
  that went nowhere for ten sessions is a result. "Closed" is reserved for the section, which means
  what the user means by it: sold by hand, taken by the stop, or the targets reached.
- `OverdueWorker` runs once a day while the app is closed: no network, no Telegram, and it must
  never start an analysis. It reads `LocalDataStore` directly rather than through `AppState`, which
  would drag a Telegram session up with it. It is no longer the only thing that runs
  while the app is closed — see **What this phone does on its own** below, which reverses that
  rule deliberately. It
  answers **two** questions off the one portfolio it builds: what is overdue, and what the calendar
  has quietly closed. It is booked while **either** notification is on and cancelled only when both
  are off, which is why `AppState`'s callback is `dailyCheckChanged` and not the old
  `overdueRemindersChanged`. Turning the overdue reminder off used to cancel the work outright, and
  doing that now would take the deadline notifications with it silently.
  **It is the backstop and not the answer**, because WorkManager books a period and not a time: it
  runs somewhere inside each rolling 24 hours, starting wherever it was first enqueued, so the daily
  look landed at an hour nobody chose — one phone had it at 11:59 — and drifts under Doze. That is
  the right shape for "this trade is three days overdue" and the wrong one for "this trade ended
  this afternoon", which is `CloseSweep`'s job. Both are booked on the same question,
  `AppState.tradeWatchWanted`, so the two can never be left disagreeing about whether anyone is
  listening.
- Positions **travel as revisions**, like wording rules and unlike reports. A position's id is
  derived from the call - `AMOC@2026-07-20` - so the same trade recorded on two devices is one
  holding rather than two that can never be reconciled. A delete is a revision too, so a later edit
  overtakes it.
- `PortfolioCalculator` computes `PortfolioStats` (win rate, averages, best and worst) whether or
  not the screen draws them, so a new figure is a UI change. There are no trade sizes, so every
  total is an average of percentages; a money total would be invented.

### Telling the user their trade moved

The Portfolio has always known — it re-derives every status from the prices on disk — and that was
exactly the gap, because it only knew it to someone who opened it. Prices now refresh through the
session on their own (see **What this phone does on its own**), so a target reached at eleven
in the morning was being
answered correctly by a screen nobody was looking at.

**Three things raise these, and each covers what the others cannot.** A price refresh catches what
the market did while it was trading, and only while the refresh checkbox is on. The **sweep at the
close** catches the ending the market brings about by stopping: a window runs out because a session
finished, not because a price moved, and 14:45 is the first moment anything can honestly say so.
The daily worker is behind both for the phone whose alarm the system dropped.

- **The one thing that had to be stored is what the user has already been told.** A status is a
  reading of the prices and the calendar and goes on being derived every time; `position_status_seen`
  is not a cache of it. "Tell me when this changes" is a question about the difference between two
  readings, and nothing else on disk remembers the first one. Device-local and never synced, for the
  reason `scheduled_jobs` is: a phone and a tablet holding one record would each announce the same
  stop, and being told twice about one trade is how a channel gets switched off.
- **A trade seen for the first time is recorded and never announced.** Without it the first run
  after this shipped would have introduced itself by reporting a stop hit in June, and every new
  purchase would announce whatever the market had already done to the call before the user bought.
- **`TradeState` carries `open` beside the status, because the status alone cannot see one of the
  endings.** A trade stopped out after taking target 1 keeps the label "Partial target hit" — the
  label is about what the market did with the call, and it did reach target 1 — while the trade
  itself closes. Watching the label would miss that ending entirely. `ranOutOfTime` then separates
  the two ways a partial hit can close, so a deadline is never reported as a stop.
- **Only what the market or the calendar did.** Recording a sale, closing a trade by hand, pressing
  Keep Open: an app that buzzes about the button somebody just pressed is one whose notifications
  get turned off. `recomputePortfolio` takes `announceChanges` for exactly this, true on the price
  refresh, the first build of the record on a start, a foreground return and a sync, and false on
  every path that is the user editing their own trade. The **sweep runs either way** — a user edit
  updates the record silently, which is what stops the next price refresh announcing it.
- **A trade thrown back open is recorded and not announced.** A split heals a stock's whole stored
  series, the sessions behind a settled verdict are refetched, and the verdict can come undone.
  "AMOC is open again" would be reporting a repair as though the market had done it.
- **The switch decides whether the phone speaks, never what it remembers.** `tradeAlertsEnabled`
  gates the notification and not the sweep, so switching it back on reports what happens next
  instead of reciting a month of settled history. Its own Settings checkbox and its own notification
  channel beside the overdue one — the two are different questions, one reporting something that
  happened and the other asking for a decision the app cannot make, and Android silences a whole
  channel at a time.
- **One notification per trade, under a group summary.** Each of these leads somewhere different, so
  a single digest could carry only one of them to the card it belongs to; the group is what keeps a
  bad morning from becoming four separate buzzes. The id is derived from the position id rather than
  counted off, so a trade that changes twice replaces its own notification instead of landing on a
  different one each time. A tap goes through **`AppState.openPosition`** — the same entrance a call
  in Insights and a tile on the Overdue card use — so a notification cannot become a second, quieter
  way of finding a trade.
- **`TradeAlerts` is pure and has no Android in it**, like `ScheduleClock`: what counts as a change
  is a rule about trading, and `TradeAlertsTest` drives it through every ending by running
  `PortfolioCalculator` over real sessions. A hand-built `PositionView` would let the test assert
  whatever it liked about a state the scorer might never produce, which is the one way a test about
  status changes passes while the app stays silent.

### Warning before the level, not after it

Every notification above reports something the market has finished doing — a stop announced is a
stop already taken, a buy zone announced is a price already there. `ApproachAlerts` is the only one
that arrives while there is still a decision to make.

- **Two levels and deliberately not four.** The stop, because it is the only level a reader can act
  on — selling early is a decision rather than a regret. And target 2, because it is the one level
  that ends a trade outright. Target 1 closes nothing that needs acting on, and a third alert per
  trade is how a channel gets switched off.
- **A distance, never an instruction.** *AMOC is 1.4% from its stop.* What to do about it is exactly
  the judgement this app refuses to make everywhere else, and it does not start on a lock screen. The
  return is on the notification because it decides whether a stop coming up is a loss to cut or a
  profit to protect, and those are opposite situations wearing one sentence.
- **First sight is recorded and never announced**, the rule `CallAlerts` needed: without it the first
  sweep would announce every trade that happens to be sitting near a level. **Leaving is recorded and
  coming back speaks again** — a price that pulls off its stop in the morning and closes on it in the
  afternoon has done this twice. A price oscillating around the threshold can therefore speak more
  than once, accepted for the reason `CallAlerts` accepts it: the alternative is a trade that drifts
  off its stop and is then silently taken by it.
- `position_approach_seen` is keyed per trade **and per level**: a price can be a whisker from its
  stop and nowhere near target 2, and one key for both would let whichever was checked first swallow
  the other. Device-local, like its two siblings.
- **Default off**, with the threshold a setting (`approachThresholdPercent`, 1-10%, default 2). It is
  inherently the noisiest thing the app can say, because a price near a level goes on being near it —
  and there is no right threshold for everybody, since a tight stop on a liquid large cap and a wide
  one on a thin mid cap mean different things by "close".
- Pure, with no Android in it, like `TradeAlerts` and `CallAlerts`. `ApproachAlertsTest` drives it
  through `PortfolioCalculator` over real sessions for the same reason.

### The two silences the app can hear

`AttentionNotifier` covers the two ways this app stops working while nothing looks wrong. Both were
already detected and both reached the reader only on a screen they had to think to open.

- **A frozen feed looks exactly like a calm market.** `PriceHealth` has computed this on every
  recompute since it was written and, until this existed, reached only a card in Settings — the card
  that has since been removed, which leaves this notification as the only thing that raises it at
  all. `AppState.reviewFeedHealth`
  announces the crossing **into** a spell and re-arms on the way out — announcing the state instead
  would be a daily line about a symbol that retired in June. `SettingsRepository.feedReportedQuiet`
  is that memory: a single boolean rather than a table, because it is one fact rather than a row per
  thing. **Default on**, because it reports the app being unable to do its job rather than something
  the market did.
- **A schedule that was due and did not run.** The reported symptom of a broken schedule is silence.
  `runDueScheduledJobs` announces `MISSED` and `FAILED` and deliberately **not** `SKIPPED`: "paid runs
  are not allowed to spend credits on this phone" is the standing state of that switch, and a daily
  notification restating it would be the app asking to be allowed to spend. **It carries no run-now
  action** — a one-tap way to spend from a lock screen is the same act as spending. Default on.
- **All seven switches live in Settings under `Notifications`**, not in `Trades` where they grew up.
  That was true of the first two and increasingly untrue of the rest: a feed that has gone quiet and
  a schedule that did not fire are the app reporting on itself, and neither has anything to do with
  how long to hold a position. One card because they are one decision - how much this app may
  interrupt - and because the switch somebody opens Settings to turn off is now under the heading
  naming what they came to stop. Since 2026-09-03 the seven are **grouped inside that card** —
  `Your trades` (three), `Calls and sessions` (two), `The app itself` (two) — because seven switches
  in a row is a list nobody reads to the end of, and the three headings answer the question a reader
  actually arrives with, which is never "which of these seven" but "what kind of thing keeps
  interrupting me". Each group's summary counts its own switches through `switchesOn`, off the same
  flags as the card's total, so a group and its card cannot disagree. `Trades` as a card is gone: the
  default trade window is now `General → Trade defaults`.
- **Its own channel and not the overdue one**, although both are the same "you need to look at this"
  register. That channel is named for trades past their deadline, and Android silences a whole channel
  at a time — folding a feed fault into it would mean muting one silently muted the other, which is
  the exact failure these exist to break.

### Answering a trade from the shade

`TradeStatusNotifier` carries two actions, and which of them can be one tap is decided by what the
app already knows.

- **Keep open is a true one-tap**, through `TradeActionReceiver`: it is a boolean on the row and
  needs nothing from the reader. Offered on every ending but `TARGET2_HIT`, which is the one Keep
  Open cannot argue with. It recomputes with `announceChanges` false, like every user edit — an app
  that buzzes about the button somebody just pressed is one whose notifications get switched off.
- **Record sale cannot be**, and that is a fact about the data rather than a limitation of the shade:
  a sale needs the price the reader got and the day they got it, and the app must not invent either.
  What the action does instead is land them on those two fields — `AppState.openPositionToSell`,
  which reveals the card *and* opens its dialog. The two are separate requests on purpose: every
  cross-tab press reveals, and one entrance that always did both would put a price field in front of
  the reader every time they followed a call to its trade.
- **The overdue notification gains neither.** It is deliberately one notification for every late
  trade — one per trade is how a fortnight away becomes eight buzzes — so there is no single trade for
  an action to name.

## What happened this session

The app has always known every fact on this card and has never had a place to say it. A target
reached at eleven on Tuesday morning reached the reader as a status, on a card, inside a folded
section, on a tab they had to choose — so *what happened today*, the one question with a daily
rhythm to it, was the question no screen answered. `SessionDigest` is that answer, drawn by
`TodayCard` **second on both tabs — under Overdue on the Portfolio and under the hero on Insights**.
Second and not first, for one reason on both: each of those is the thing its page exists for — the
trades asking to be acted on, the standing verdict on the sources — while this is what changed since
the reader last looked. Perishable, so it goes above everything that takes scrolling to reach, and
no higher. **The two tabs share the arithmetic and not the scope** — see the first bullet below.

- **The Portfolio's card is the reader's own trades; Insights' is everything.** It began as one
  card on both tabs, on the reasoning that one digest cannot then report a session two ways — which
  was right about the arithmetic and wrong about the question. The Portfolio is the tab holding the
  reader's money, and a session where three channels' calls reached targets and the reader held none
  of them was reported there as though something of theirs had happened; they had to open the card
  to find nothing of theirs in it. `SessionDigest.heldOnly()` is the narrowing, and it is a **filter
  over the built digest rather than a second build**, so every figure on both cards still comes from
  one pass and one set of rules — the scope decides what is counted, never how. `newCalls` goes with
  the call events: what channels published is a fact about the sources, and on Insights it sits
  under a page of those calls where it has something to refer to. Every derived figure follows for
  free, the headline included — which matters most, because it colours the folded card, and a red
  heading over a session whose only stop was somebody else's call would be the Portfolio reporting a
  loss the reader never took. The titles diverge with the scope (**What happened to your trades** on
  the Portfolio), since the same title over two cards showing different things is the exact reading
  this split exists to prevent.
- **A session, never a day, and the session comes from the prices.** The card reports on
  `PerformanceReport.pricesTo` — the newest session any stock has a row for — which is the same
  figure the page's "prices to \<date\>" comes from and for the same reason. That is what makes the
  list hold still: it is a function of one session's prices, and a closed session's prices do not
  move, so the list a reader saw at noon is the list they see at six. It turns over when a refresh
  first brings back a row for the next session, which is the exchange's own answer to when the next
  session started. A **wall-clock rule was the obvious alternative and is wrong** — on an EGX
  holiday it would claim a session that never traded and report an empty one as fact, where reading
  it off the feed correctly turns over not at all.
- **Derived on every recompute, like the portfolio and unlike `position_status_seen`.** This is the
  sharpest difference from `TradeAlerts`, and the two questions are genuinely different.
  `TradeAlerts` asks *what should I say out loud, once*, which is a question about what the user has
  already been told, so it needs a memory and is allowed to notice a week-old stop today. A digest
  asks *what happened on that session*, which has one right answer forever — so a phone switched off
  for a week comes back with each of those sessions filled in correctly, rather than with seven days
  of news piled onto the day it was turned on. Every event is dated by `settledOn`, `stoppedOn`,
  `deadlineDate` or the close that crossed a band, never by when the app noticed.
- **A trade recorded long after the fact still reports what the market did to it**, which is the
  rule `TradeAlerts` must not have: first sight is silent there, because announcing a stop from June
  would be the app introducing itself with old news. Here it is a record, and the market did what it
  did. It costs nothing, because the scorer only ever replays the sessions actually held — a target
  reached before the user bought was never theirs and never enters the digest.
- **Trades speak for the calls they were taken on.** A held call is reported once, as the trade,
  carrying the user's own return; the channel's version of the same event is dropped. The same rule
  `CallAlerts` follows, for the same reason — two entries for one stock is one event reported twice,
  and the trade is the one carrying money. Re-postings are out of it entirely, everywhere.
- **New calls are counted and deliberately not stored.** Every one of them is already on disk in
  full, in the analysis it came from, and a row per call in a second table would be a copy of the
  record free to fall out of step with it. What the market *did* has no such home, which is exactly
  why the rest of it is written down.
- **`session_events` is the archive and is never read back for the screen** (schema 21). The card
  builds its own from the same recompute that builds the portfolio, so it cannot drift from the tabs
  around it; the table exists for the questions only a history can answer — how often a source's
  calls move on the session after they are printed, what a bad week actually looked like.
  `STORED_SESSIONS` (30) sessions are derived and written per recompute, and each is written
  **whole**: delete the session, insert what is true now. A heal rewrites a stock's prices, and rows
  derived from the old ones have to go with them rather than sit beside their replacements — which
  is also why a session that yields nothing is cleared rather than left as it was. Device-local and
  never synced, like `position_status_seen` and `price_events`.
- **The heading is the card**, because it is what a folded card informs with. It names the session
  and then counts what happened, each part in the colour that fact already wears everywhere else —
  green a target, red a stop, amber a window that ran out, blue a price the market reached. Zeros
  are omitted. **A stop outranks a target in the one colour the icon gets**, deliberately: a green
  heading over a session that also took a stop is the card burying the news the reader most needs.
- **Expanded by default, alone among the cards in this app.** Everything else folds because a screen
  of open cards is unreadable; this one is read once and scrolled past, and a card that must be
  opened before it says anything is a card nobody opens. `PageState.todayExpanded` is shared by both
  tabs — folding it away on the Portfolio only to find it open on Insights would read as two cards
  that happen to agree — and it is session-only, so "expanded by default" is true of every launch.
  Still shared now that the two scopes differ: it is recognisably the same card asking the same
  question of a narrower record, not a second card that happens to sit in the same place.
- **A quiet session says so rather than vanishing**, which is the one place this card parts company
  with Overdue. "Nothing is late" is the state the app is normally in and a permanent card
  announcing it would be furniture; "nothing moved on this session" is a genuine answer to the
  question being asked, and its absence would read as the app not having looked. The card is absent
  only when there is no session at all — a fresh install with no prices. On the Portfolio the wording
  is narrower — **"None of your trades moved on this session"** — because there it now is: the market
  can have had a busy session that none of the reader's trades were in, and "nothing moved" would be
  the card overstating its own scope.
- **Tiles, through the Overdue card's helper but deliberately not its numbers** — 170dp across up
  to **three** columns, against Overdue's 150dp up to four. Two separate mistakes were made getting
  to those, and both are worth keeping written down because both are easy to repeat. The first was
  copying Overdue's four-column cap: `responsiveColumns` spends surplus width on *more* columns, so
  a grid tuned for "6d · 12 Aug · +3.4%" gave the unfolded panel four narrow tiles while the cover
  screen got two wider ones — **the big screen truncating harder than the small one**. The second
  was fixing that by sizing the minimum against **device** width rather than the **container**: by
  the time the grid is measured the width has lost the page's 16dp either side, the card's 16dp
  either side, and on a wide window the rail as well, which is 64dp on the cover and about 144dp on
  the Fold. 200dp looked comfortable against 411 and sat just above half of the real 347, collapsing
  the cover to one full-width tile.

  | screen | device | container | at 150dp | at 200dp | at 170dp |
  |---|---|---|---|---|---|
  | Fold cover | 411 | **347** | 2 x 173 | **1 x 347** | 2 x 173 |
  | Fold inner | 750 | **606** | 4 x 151 | 3 x 202 | 3 x 202 |
  | Tablet | 818 | **674** | 4 x 168 | 3 x 225 | 3 x 225 |

  The cap is what fixes the wide screens; the minimum only has to stay under half the narrowest
  container. 170 rather than back to 150 is for the widths in between — a 600dp window takes three
  cramped 152dp columns at 150 and two roomy 228dp ones at 170. The shared helper stays; what has to
  fit in a column is a property of that column's contents, not of a house grid.
- **Three lines, four on a wide screen, and only the last may be lost.** The ticker, then what
  happened on a line of its own, then the figure that qualifies it — a trade's return in its own
  green or red, or on a call the channel that printed it. The first two used to share one line
  joined by a separator, which put "stopped out after target 1" into competition for width with the
  fact saying how it landed. What happened is allowed to **wrap to two lines rather than ellipse**,
  because a column width is a bet about the reader's font scale and wrapping is what actually keeps
  the phrase whole; `ResponsiveRows` sizes every card in a row to the tallest, so it costs the
  alignment nothing. The figure line is held to one and is **absent rather than blank** where an
  event has neither.
- **The call's own date is the fourth line, and only where there is room** — gated on
  `LocalWindowWidth != COMPACT`, which is the shell's published answer to that question and not a
  second measurement taken in the card, so the two cannot disagree about where the line falls. It
  reads **"called 14 Aug"** rather than a bare date: the heading above already names the session the
  events belong to, so an unlabelled date under one reads as the day it happened, when it is the
  session the call was *printed for* — which on a card that can carry a stop from a recommendation
  three weeks old is the context nothing else on the tile supplies. `shortDate` and `PriceRole.muted`,
  the same helper and the same role the Overdue tile gives its entry date.
- A press goes to `AppState.openPosition` for a trade and `AppState.openCall` for a call —
  the two entrances every other cross-tab press already uses, so the same tile leads to the same
  place from either tab. Not narrowed by any filter on either screen, for the reason Overdue is not.
- **A call expiring is not an event here, where a trade's window running out is.** A trade's
  deadline is the user's own and arriving is news to them; a call expiring is
  `JUDGING_HORIZON_SESSIONS` running out, which is the app's backstop rather than anything the
  market did. `PRICE_BREAK` silences both — the app refuses to value across a split, and a card
  announcing a stop it has just admitted it cannot read would be the one place that refusal failed.
- **It can now say itself once, after the close.** `SessionDigestNotifier`, gated on
  `sessionDigestEnabled` and **default off**. The gap it fills is precisely the session where nothing
  of the reader's own moved: every other notification here is about one trade or one call, so an
  afternoon when three of their sources' calls reached targets and they held none of them was
  completely silent. The **whole** session and never `heldOnly` - narrowing it would leave this
  saying what the per-trade notifications already said, one buzz later. Default off because a daily
  line arrives on a rhythm rather than on an event, which is the `callAlertsEnabled` case exactly.
  A quiet session is **silent in the shade and not on the card**: on screen "nothing moved" answers a
  question the reader asked by looking; in the shade it is an interruption to report an absence.
- **`session_digest_announced` is what stops it repeating** (schema 25). The card is rebuilt on every
  recompute and rightly so - a session has one right answer forever - which is exactly why it cannot
  decide on its own whether it has been spoken. Same distinction `position_status_seen` draws: what
  happened is derived, what was *said* is stored. A row per session rather than one high-water mark,
  because a phone switched off across a session would otherwise count it as already said. Only the
  **newest** session is ever announced: a phone off for a week comes back with every session filled
  in correctly on the card, and announcing each would be seven days of news piled onto one evening.
  The row is written **before** the notifier is called, so a crash between the two costs one silent
  evening rather than repeating the same line on every later recompute.
- **`SessionDigest` is pure and has no Android in it**, like `TradeAlerts` and `ScheduleClock`.
  `SessionDigestTest` builds every trade through `PortfolioCalculator` and every verdict through
  `Scoring`, because a hand-built view would let it assert whatever it liked about a state the
  scorer might never produce — the one way a test about what the market did passes while the card
  stays empty.

## Ask AI

A button on a call card in Insights sends one paid request and asks four questions: how the stock
has actually traded, what the business is, where it goes over three spans of time, and what the
levels the channel printed are worth. The answer comes back in Arabic and is kept on the card.

- **It asked two questions until 2026-08-27, and every answer read the same.** That was the
  complaint and the diagnosis is worth keeping, because nothing was broken: search was on, the DATA
  block was rich, the temperature was 0.4. The prompt was the fault, in two ways at once. Its first
  two sections were almost entirely prohibitions - do not recite a figure, do not do arithmetic, do
  not print a level, do not name a fundamental you did not find, do not mention the index or the
  sector, never more than two figures in a sentence - so on a stock whose news window came back
  quiet, every concrete thing was forbidden and what was left was hedged Arabic that fitted any
  ticker on the exchange. And the shape it was pouring into was one free-text field about the stock:
  one paragraph, asked of a model with little it was permitted to say, converges on one paragraph.
  Schema **3** answers both. The prohibitions are **scoped** rather than global - `standing` and
  `on_the_call` now *require* the figures the rest of the prompt keeps out of the prose - and there
  are four fields where there was one, two of them lists the reader can compare across stocks
  without reading a word.
- **`standing` is the field that cannot come out the same twice**, because it is measured rather
  than recalled: where the close sits against its own averages, where in the range, what the stock
  has done since the call, what the volume says. It is required to name at least three of those.
- **`forecast` is three spans asked separately** - short (days to four weeks), medium (one to three
  months), long (six to twelve). Each carries a direction, a reason and its own confidence, and they
  are allowed to disagree; a stock can be stretched into next month and sound into next year, and
  saying both is more useful than averaging them into nothing. The prompt sets what each span may
  rest on: price and volume are enough for `short`, `medium` needs a catalyst or a sector reason,
  `long` needs a reason from the business and is `SIDEWAYS` at `LOW` confidence where there is none.
  A reason that carries all three spans is one reading rather than three, and the prompt says so.
- **`horizon` is derived from the forecast and no longer asked for.** Two fields answering "how
  long" is two fields free to contradict each other - a `SHORT` horizon printed beside a short leg
  pointing down. `Horizon.from` maps three spans onto the four values, counting a medium leg toward
  `SHORT` because one to three months is a period a reader plans in weeks. The parser still reads a
  `horizon` token where one arrives anyway: an answer given in the old shape has told us something,
  and the sheet keeps printing the stored horizon on every opinion saved before this.
- **`on_the_call.checks` rates the printed numbers one at a time** - risk to reward, the stop, both
  targets, whether the entry band is still valid - each `GOOD`, `FAIR` or `POOR` with an Arabic note
  in which a figure is expected rather than forbidden. It was one prose blob covering four questions
  in three sentences, which averages into the same three sentences: "the ratio is acceptable but the
  stop is tight" fitted almost every call. The five are **re-ordered into the app's own order by the
  parser**, not trusted from the model: the sheet draws them as a fixed list beside the levels on
  the card, and a model that shuffled them would rearrange the reader's screen between answers.
- **A forecast arrives whole or not at all**, in the parser and again on the way out of the
  database. Two legs on screen would not be a smaller forecast - it would be the app choosing which
  span to hide, on a sheet whose whole point is that the three can disagree. `standing` is tolerant
  where `outlook` is not, and the asymmetry is deliberate: an answer with no reading of the stock is
  not an answer, while one that skipped the price section still carries everything the request paid
  for, and the section is simply absent on the sheet - which is itself the signal.
- **The prompt names the boilerplate it will not accept.** Four Arabic phrases every market column
  reaches for when it has nothing to say - `يُنصح بالحذر`, `يفضل التريث حتى وضوح الرؤية` and two more - are
  listed and forbidden by name, alongside the rule they are examples of: a sentence that would still
  be true with the ticker swapped is a sentence to delete. The escape hatch is honesty rather than
  padding, and it is stated as such: where the model has nothing specific it says so and returns
  `LOW` confidence.
- **General knowledge of the business is now wanted, where it used to be banned.** The old prompt
  refused sector and index talk unless a news item named it, which is right about invention and left
  a quiet stock with nothing to say: a fertilizer producer lives on urea prices and the gas it is
  charged, and a model forbidden from saying so is a model reduced to describing a chart. It is
  allowed as **background** - no date, no figure, no claim about the current quarter or the last
  result, all of which still need a source.

- **Its own prompt, and nothing of the analysis reaches it.** `assets/stock_opinion.md`, read by
  `OpinionPromptStore` — a separate class from `PromptStore` on purpose. The analysis prompt carries
  a schema the desktop agreed on, a rules anchor `PromptComposer` fills in, and a version history
  the user approves; this one carries none of that, no wording rule is folded into it, and no run's
  `promptId` ever names it. Two stores rather than a second method on one, because sharing a class
  is how a rule about reading a Telegram card eventually reaches an opinion about a stock.
- **Which figure may be named depends on which field is being written**, and that scoping is what
  schema 3 changed. The DATA block is printed on the card the sheet opens from — entry band, stop,
  targets, peak, trough, sessions elapsed, return, latest close — so in `headline`, `outlook` and a
  forecast leg's `why` it may name a figure where it carries a point and never more than two in a
  sentence. In `standing` and `on_the_call` the numbers are the answer, and a reading with no figure
  in it is an assertion. Explaining arithmetic the reader can do is still forbidden everywhere.
- **Every figure the model is required to discuss is supplied rather than left to be worked out**:
  risk to reward from the middle of the entry band, the move from that midpoint to the latest close,
  the distance from the 20- and 50-session averages, the gap between those two averages, and where
  the close sits inside the period's range. A language model asked to divide two prices gets it
  wrong often enough to matter, and it would be wrong *confidently*, inside a verdict. Requiring it
  to read a gap back while making it derive that gap would have been requiring a wrong number.
- **The answer is Arabic; the seven token fields are not.** `verdict`, `confidence`, `stance`,
  `direction`, `item`, `rating` and `tone` come back as English tokens and the screen prints its own
  Arabic for them (`StockOpinion.Verdict.arabic` and friends). Letting the model answer those in
  Arabic would put the parser at the mercy of its choice of synonym, and a verdict that fails to
  parse is a verdict the card cannot colour. Prices and dates stay in Western digits so a figure the
  answer names matches the one printed beside it.
- **Live search is on by default**, and that is the point of it: without one the model has nothing
  the app does not already have. DashScope takes `enable_search` in the request body plus
  `search_options.search_strategy` for the deep pass; OpenRouter takes the **`web` plugin** —
  `plugins: [{id: "web", max_results, search_prompt}]` — and no other provider is sent anything, an
  unknown key being rejected outright by some OpenAI-compatible gateways, which would fail the
  request rather than the search. News is prompted as reported, not verified, and may never
  overturn a price fact.
- **The prompt used to suppress the search the request had just paid for.** Section 5 described a
  `NEWS` block and ended "with no NEWS section, say nothing about recent news either way" — and
  nothing in the app has ever built a `NEWS` block, so on every searched request the provider
  injected results and the prompt told the model to ignore them. That is why a searched answer read
  like an unsearched one. That was fixed in schema **2**: news is required output when a `SEARCH`
  block is present, and `news`, `catalysts`, `risks` and `unknowns` are always-present lists. What
  it did not fix was the register — an answer that had searched and found nothing still read like
  every other one, which is what schema 3 is for.
- **`OpinionSearchBrief` is what the search is aimed with, and it produces two texts that are not
  the same text.** `query` goes into the question, naming the company in both scripts plus every
  alias `EgxCatalog` holds — a search on `COMI` alone finds a four-letter string, while the Arabic
  name is what Mubasher prints and "CIB" is what everyone says — along with the dated window, what
  to look for, and where Egyptian company news is actually published. `resultPreamble` goes to
  OpenRouter as `search_prompt`, which is **not** the query: it introduces the results after the
  question has been read, which is the last and cheapest place to reject a stale headline.
- **The news window is a lookback and defaults to 15 days**, set in Settings (15/30/90/180) and
  recorded on the answer as `newsWindowDays` rather than read back from Settings when the sheet
  draws — the setting moves, and an opinion has to keep saying what window it was actually given.
  Neither provider offers a real date filter on the OpenAI-compatible endpoint, so the window is
  enforced by instruction plus by requiring a date on every item. A short window returning nothing
  is the honest result and is printed as one; **upcoming catalysts are not clipped to it**, because
  a lookback is a claim about staleness and a dividend three weeks out is not stale.
- **The model prints no levels of its own, deliberately.** Section 3 forbids an entry, a stop or a
  target: the reader has a call in front of them and asked what to make of it, not for a second set
  of numbers to reconcile with the first. What it may say about price is what it would want to see
  before paying today's, in words. This survived schema 3 untouched, and it is the one place a
  forecast might have been read as licence to name a price — a direction over a span is not a
  target, and `direction` is deliberately separate from `verdict` for the neighbouring reason: a
  stock can be heading up and still be a poor buy at today's close because the move is already paid
  for.
- **The block carries what the app had and never sent.** Volume was in `DailySession` from the
  beginning and never reached the model, so a call was judged with no idea whether the stock could
  be traded at size; `OpinionPrompt` now sends average volume, average value traded, and a
  reference position as a share of one session's turnover, plus 20- and 50-session average closes,
  the period high and low, and every other call the app holds on that stock. The averages are
  **computed in Kotlin or not stated** — the same rule risk-to-reward already followed, for the
  same reason. Ten sessions called a fifty-session average is a wrong figure, not a rounded one, so
  it is omitted instead.
- **Other channels calling the same stock are crowding, not confirmation**, and the prompt says so.
  Settled calls on that stock go in a second list, which is the only record the app holds of what
  happens when this particular stock is recommended.
- **Its own model setting**, `SettingsRepository.opinionModel`, defaulting to `qwen-plus`. The
  analysis runs on a vision model because it reads screenshots; this request carries no image, and
  paying vision rates for it buys nothing. Device-local like the analysis model and unlike
  `AppPreferences`: a model id means nothing on a phone pointed at another provider. **It is also
  the next lever if answers still converge** — schema 3 removes the reasons a prompt can make every
  stock read alike, and what is left after that is how much the model actually knows about an
  Egyptian mid-cap, which `qwen-plus` is the cheapest answer to. Changing it is the user's spend and
  therefore the user's call, so the default is unchanged.
- **`ask()` shares the transport with `analyze()` and nothing else** — endpoint, saved credential,
  timeout, cancel map. A second repository would mean a second copy of the credential zeroing and
  the connection handling, which drift apart the first time either is touched. Temperature is 0.4
  rather than the 0.0 an extraction pins: reading a price off a card has one right answer, a view on
  a stock does not, and at zero every question came back in the same cautious register.
- **One press is one paid request, confirmed first.** The dialog is **two sentences and a grey
  footnote** naming the model and the search window — it was a paragraph, and most of that length
  went on things that do not change the one decision being made, which is whether to spend a
  request. A confirmation nobody finishes reading has stopped confirming anything. Once answered the
  button reads `AI opinion · <verdict>` and reopens the saved answer for nothing; `Ask again` is the
  only way to pay twice, and a card whose request is already out cannot start a second.
- **The sheet reads in the order the questions were asked** — what the stock has done, what the
  business is, where it goes, what the call is worth — then the findings, then the footer. The
  horizon leaves the line under the ticker on any answer carrying a forecast: three dated spans sit
  a few lines below, and a single holding period above them is the same claim said worse. It is
  still printed on a schema 2 answer, which has no forecast to replace it.
- **Three columns, added the way the findings columns were** — `standing`, `forecast` and `checks`,
  by `ALTER` with a guard each in `addOpinionDetailColumns`, database version 23. The two lists are
  JSON in a column apiece for the reason `news` and `catalysts` are: nothing queries inside them,
  and a column per field would mean a migration every time the prompt learns to ask one more thing.
  A leg written under a span or a direction this build does not know costs the **forecast** and
  nothing else around it, which is the same rule an unreadable tone follows and deliberately not the
  rule an unreadable verdict follows.
- **An opinion is keyed by ticker, session *and* channel** (`opinionId`), which is deliberately not
  `positionId`. Two channels calling one stock on one session are two cards printing different
  levels, so an opinion on one is not an opinion on the other — where a holding is one holding
  however many sources called it.
- **Deleting the report deletes its opinions**, on all three paths and on a report another device
  buried. `ScoredCall.requestId` exists for this and only this. `deleteResult(id)` has to read the
  request id back *before* the row goes — the opinions are keyed on the request id, and doing it the
  other way round is a cascade that deletes nothing while looking correct, which
  `StockOpinionStoreTest` covers on both paths.
- **Never synced.** Everything else that travels is a record of what happened; this is one model's
  answer to one question at one moment, and the cheapest way for another device to have it is to ask
  there.
- A stored row whose verdict this build cannot read is **dropped rather than defaulted**: a card
  colouring an answer it could not read would be inventing one.

## Exporting a report to Excel

A report card's ⋮ menu writes the results table as an `.xlsx`, two ways: **Save to Downloads** puts
it on the phone, **Send as Excel** hands the same file to a chooser. The file is the whole report,
not what the screen is filtered to: the menu is there whether the card is open or shut, and a file
that quietly held a subset is the wrong default for a record. Narrowing happens in Excel instead,
through the filter dropdowns on row 1.

- **No dependency.** An xlsx is a zip of XML parts, and the corner of it needed here is small enough
  to write outright. Apache POI is about 12MB of dex, drags xmlbeans and needs desugaring, all for
  one sheet. Written by hand the whole export is plain Kotlin with no Android in it, which is what
  lets it be tested without a device — the same reason the scoring code is testable.
- **Three deliberate differences from the table on screen**, each forced by making the filters work.
  **No stock heading rows**: an autofilter needs uniform rows under one header, and filtering would
  hide a heading and strand the stocks under it, so the code and both names lead every row instead.
  **Banding by stock, not by row**: with the headings gone a tint per stock is what shows where one
  ends, where the table's alternating stripe would say nothing once a filter has hidden half of what
  it was counting. **Entry as low and high**: `1.2 – 1.35` in one cell can be neither sorted nor
  added up, and single-price rows landing beside it as numbers would turn the column to text.
- **Prices and dates go in as numbers and dates**, never as their printed form, or the column cannot
  be sorted, filtered or totalled — which is the whole reason to export a spreadsheet rather than a
  table of text. An absent figure is an **empty cell**, not the em dash the table draws: a dash in a
  numeric column turns it to text and files under its own heading in the filter dropdown.
- **The light palette, whatever the phone's theme.** A spreadsheet is read on a white page. The
  roles are unchanged: green is a target, red is a stop, cyan a price the market reached, grey
  context, and a derived return is softened exactly as `ReturnCell` softens it — its sign's own
  colour at `PriceRole.DerivedAlpha`, mixed onto white by `derivedTint` because an xlsx font colour
  carries no alpha. **Softened, never greyed**: the prompt leaves the percentage null unless a card
  prints one, so most rows are derived, and a grey for those against a green for the rest left one
  column in two hues with the grey ones reading as context. `returnFrom` and the alpha are both
  shared with the table rather than copied, so the two can never disagree about one row.
- Every column is written, including the context and notes the table drops below 620dp and 900dp: a
  sheet has no width to run out of. The **source image column is not exported** — a picture in a
  cell means media parts, a drawing and anchor geometry, for something a spreadsheet is not read for.
- **Saving goes through `MediaStore.Downloads`, not a path.** From API 29 that needs no storage
  permission and no picker, and the file is registered as it lands, so the Files app and every
  spreadsheet app see it at once rather than after the next media scan. The write is `IS_PENDING`
  until it is whole and the entry is deleted if it fails, or a part-written spreadsheet sits in
  Downloads looking exactly like a finished one. MediaStore renames a second export of one session
  to `... (1).xlsx` rather than overwriting, and **the toast names what Downloads actually created**,
  not what was asked for - the two differ precisely when a re-run's earlier reading is already
  saved. Nothing opens afterwards, so that toast is the only sign it worked.
- Sending stages the file in `filesDir/exports/`, which is **emptied on every export**, and grants it
  through `${applicationId}.exports`. Its own authority rather than another path on the traces
  provider, for the reason the manifest gives beside it. Saving needs no provider at all.
- Fill indices **0 and 1 are reserved** by the format for "none" and "gray125". A real fill at either
  shifts every other fill by one, which draws the sheet a column out rather than failing outright.
- The output was checked by loading it with an independent reader (openpyxl, warnings as errors),
  not only by asserting on our own XML: a hand-written workbook that Excel rejects says so with one
  dialog and no reason, which no test over our own strings would catch.

## Reading a problem off a device

**Settings → About → Save diagnostics** copies `egx_analyzer.db` into Downloads, from where
`adb pull //sdcard/Download/egx-diagnostics-<date>.db` reaches it. It exists because there is no
other way off a release-signed build: `run-as` refuses a package that is not debuggable,
`adb backup` is closed by `android:allowBackup="false"`, and installing a debug build to get at the
data means uninstalling this one and taking the record with it.

- It goes through the **same `writeToDownloads` as the spreadsheet export** — `IS_PENDING` until
  whole, entry deleted on failure, and the message names what Downloads actually created.
- **`checkpoint()` first, and it is not optional.** SQLite runs in write-ahead mode, so the newest
  commits sit in a `-wal` sidecar until something folds them in — copying the database alone hands
  over a record missing exactly the recent activity worth asking about.
- **The feed-health log travels in it**, which is most of why it is written down at all — see
  **When the feed goes quiet**. `SELECT * FROM feed_checks ORDER BY checked_at DESC` is the history
  of what the app noticed about the price feed, and `feed_faults` joined on `checked_at` is which
  stocks each check was about.
- **No credential travels in it.** Provider keys and the Telegram database key are encrypted by
  Android Keystore in their own preferences file and have never been in this database.

## Backing up, and getting it back

**Settings → Saved data and privacy** holds three buttons: *Back up now*, *Choose a folder*, and
*Restore from a backup*. The gap they close is not which cloud the record sits in — the sync channel
was already that, and it is the right answer for a multi-user app, because signing into Telegram is
what makes this app work at all, so it is the one cloud account every user is certain to have. The
gap is that **`Save diagnostics` had written the record out since the first release and nothing had
ever read one back**. Someone who lost their phone, lost their Telegram account, or cleared the
app's storage was holding a file nothing on earth could do anything with.

- **A backup is a zip: `record.db`, `settings.json`, `backup.json`.** The database alone is not a
  backup, which is why `Save diagnostics` stays exactly as it is and is not renamed into one —
  settings live in preferences, so an install restored from a bare database comes back holding
  everyone's reports and scoring them against a trade window nobody picked. `settings.json` is the
  **same document `SettingsSync` publishes**, so one reader serves the file and the channel.
- **Restore accepts either, told apart by the first bytes of the file and never by its name.** A
  file that has been round a cloud folder, a chat and a downloads directory arrives labelled
  anything at all, and refusing a good backup over its name is the sort of thing that happens on the
  one day it matters. A bare `.db` is accepted because those files are already on people's phones
  and computers and a format that could not read them would strand the only copy some users hold;
  what it cannot bring back is settings, and `readBackup` reports that as `settings = null`.
- **The backup is opened through `LocalDataStore` under a second database name**, which is why that
  class now takes one. `onUpgrade` then runs over an old file exactly as an app update would and it
  is read through today's columns. A reader written for the backup would have to carry its own copy
  of every migration and would be wrong the first time one was added and not copied. `onDowngrade`
  raises **`BackupTooNewException`** rather than failing inside a query: the file is fine and this
  build is behind it, and that is the difference between someone updating the app and someone
  deleting their only copy.
- **A restore only ever adds.** Every comparison is the sync's own `(updatedAt, device)` rule, with
  one deliberate difference: a revision the backup marks deleted is **skipped, not adopted**. Between
  two live devices a tombstone has to travel or a delete does not stick; a file is one moment
  preserved, and letting last week's moment remove a trade recorded yesterday would make this
  dangerous to press. Somebody opens a backup because something is missing, and the one outcome they
  must never get is more missing. Deletes go on travelling through the channel.
- **A report this device buried but has not yet published is not restored either.** That delete is a
  decision already taken and still in flight, and restoring over it would leave a tombstone about to
  be published for a report sitting on disk again.
- **The decisions are pure functions in `BackupRestore.kt`** — `rulesToRestore`,
  `positionsToRestore`, `runsToRestore`, `promptVersionsToRestore` — exactly as `syncActions` and
  `rulesToUpload` are, so they are tested as ordinary Kotlin. `AppState.restoreFrom` applies them and
  nothing else. `BackupRoundTripTest` runs under Robolectric and covers the other half: every
  decision is worthless if what comes out of the zip is not what went in, and a backup that reads
  back empty looks exactly like a phone that had nothing on it.
- **`storedRuns()` reads the analyses table without parsing a payload.** Not `results()`, which
  counts what it cannot read: a report written by a later build in a shape this one does not
  understand would be dropped on the way through — lost by the very operation reached for to stop
  losing things.
- **A folder, never an account, and that is the whole design.** `ACTION_OPEN_DOCUMENT_TREE` puts
  OneDrive, Dropbox, Nextcloud, an SD card and a plain local folder in one list, so each user ends
  up backed up to whatever cloud they already have while the app learns none of them, holds no
  credential for any of them, and costs nothing per person as more people use it. The grant is
  **persisted**, or it dies with the process and the daily backup silently stops.
- **Written under `.part` and renamed once whole.** A chosen folder has no equivalent of MediaStore's
  `IS_PENDING`, and a write that stops part way would leave a truncated zip under the name of a
  finished one — which is the worst failure here: not an absent backup, which is obvious, but a
  present one that turns out to be empty on the day it is needed.
- **Daily, on resume, and only into a chosen folder.** `MainActivity.backUpIfDue` — beside the
  overdue refresh and outside the root for the same reason, that it is app behaviour rather than UI.
  Guarded by day, or a phone opened six times before lunch writes six copies of an unchanged record
  and pushes five real days out of the seven kept. It **never** writes to Downloads automatically:
  MediaStore appends rather than replaces and nothing prunes there, so a daily write would pile up a
  file per day forever. The manual button still falls back to Downloads, which is right for
  something somebody pressed.
- **The day is recorded only on success**, so a failed write is retried on the next resume rather
  than counted as done, and nothing is announced either way. What reveals a folder that has quietly
  stopped accepting writes is the line in Settings naming how many copies it holds and the newest.
- **Seven are kept, and only files this app named are ever considered.** The user picked a folder,
  not a folder this app owns. Pruning sorts by name and calls that chronological, which holds only
  because the name carries an ISO date and nothing else varies — reading the folder's own modified
  times would be at the mercy of whichever cloud app syncs it, and several rewrite them on upload.
  Pruning happens **after** the new backup is whole: the old copies are what stands between a failed
  write and having nothing at all.
- **The provider API key is not in a backup**, for the reason it is left out of settings sync: a live
  cloud credential does not belong in a file about to be copied into a cloud folder. Neither is the
  Telegram database key, which is sealed to the Keystore of the device that made it and would be
  useless elsewhere. Prices, sessions and intraday bars do travel and nothing depends on them — they
  are fetched again from the feed.
- **Nothing is uploaded by a restore.** Whatever it brings back is missing from the channel too if it
  was ever lost there, and the next sync's own diff carries it up — one place for the rule about
  what gets published rather than two.

## Wording rules and the generated prompt

The 21 Arabic and English phrases that drop old or already-hit cards used to be Kotlin lists. They
are rows now — shipped, visible, switchable, not deletable — and users add their own beside them.

- Matching normalizes both sides through `WordingRule.normalize`: diacritics, tatweel, emoji, and
  the alef/ya/ta-marbuta spellings. The source text goes through the same function, which is the
  only reason a stored phrase matches a typed one.
- The prompt is **generated, never edited**. `consolidated_recommendation.md` carries
  `<!-- EGX_RULES: <slot> -->` markers; each version is composed from that file plus the enabled
  rules, never from the previous version, so removing a rule removes its wording.
- **A marker must never sit after a blank line** — removing it would leave the blank behind and the
  prompt would drift from the shipped one without anyone touching a rule. A test enforces this.
- With nothing configured, the composed prompt is byte-identical to the shipped file.
- A version's id is a hash of the shipped prompt plus the enabled model-scope rules, so two devices
  with the same configuration agree without coordinating.

## Sync

Everything travels through a private Telegram channel titled `EGX Analyzer sync`.

- Reports are append-only: a saved run never changes, so syncing them is a union.
- **Publishing is automatic.** A finished run and every change to a position upload themselves in
  the background through `AppState.publish`, so another device only ever has to pull. Failures are
  swallowed on purpose: the record is already on disk and the next sync's diff carries it, and an
  error about Telegram raised while someone records a trade is about something they did not ask for.
- Pulling stays on the Sync button, plus **one full sync per launch**, triggered by Telegram
  reaching `READY` rather than by app start - there is no session at start, so a sync then would
  fail every time. It announces itself only when something actually moved.
- `syncChatId` holds a **mutex**. Two automatic publishes landing together would otherwise each find
  no channel and each create one, which is how three duplicates once ended up in the owner's
  Telegram.
- Deletes leave a `deleted-<id>.json` tombstone. Without one, a device that still holds the report
  uploads it back and the delete undoes itself.
- Rules travel as **revisions**, not rows, because a table is edited where a report is not. The
  merge picks the winner by `(updatedAt, device)`.
- Fields an older app does not understand are kept and written back untouched. They are **stored**,
  in the `unknown` column, rather than only surviving inside one sync — before that, `publishPosition`
  sent `{}` and this device erased a newer version's data simply by editing a trade's price.
- **A Telegram database with no key left to open it is deleted, not handed a new one.** TDLib's
  database is encrypted with a 32-byte key in Android Keystore; if the key goes missing while the
  database survives, nothing on the device will ever open it — and generating a replacement made it
  permanent, because the new key was then stored as though it were the right one. The tablet sat on
  "error 401: Wrong database encryption key" every launch with no way out but clearing its storage.
  Now the orphaned directory goes when the key has to be generated, and a 401 naming an encryption
  key wipes it and re-initializes once. It costs a QR sign-in and nothing else: no report, trade,
  rule or setting has ever lived in `filesDir/tdlib`. `isWrongDatabaseKey` checks the **message**,
  not only the code — 401 is also what an ordinary signed-out session reports, and wiping over one
  of those would sign someone out for no reason.
- `searchChatsOnServer` does not find private supergroups. Read `chatCache` instead — getting this
  wrong created three duplicate sync channels in the owner's Telegram.
- **Settings travel as one revision each**, newest wins, tie broken by device — the same rule as
  rules and trades, in `SettingsSync.kt`. This is what makes a reinstall survivable: an install that
  has never saved a setting has a stamp of zero, so it defends nothing and takes everything.
  Published on change through `AppState.publishSettings`, coalesced by three seconds because the
  trade window is a slider and would otherwise upload every value it is dragged across. Two things
  are deliberately excluded: the **provider API key**, because syncing it would put a live cloud
  credential in a chat to save typing one field once, and **`lastPriceRefreshDay`**, because on an
  install with no prices at all it claims they were fetched today and leaves the phone unpriced
  until tomorrow. Adopting settings re-books or cancels `OverdueWorker` and regenerates the prompt.
- **Generated prompts travel as a union**, like reports and unlike rules: the id is a hash of what
  composed them, so a version never changes. Without them a restored install holds every report and
  can no longer show the prompt any of them was judged under. Both sides compare
  `SyncedPromptVersion.keyFor(id)` — the file-name form — or the same prompt uploads itself forever.

## Updating itself

Sideloaded, so nothing else will ever offer it an update: without this, a new version reaches a
phone only by plugging it into the machine that built it. It reads one public URL —
`releases/latest` on `ikverse/EGX-Analyzer-Android` — so no token ships in the app.

- **Two deliberate taps, Download then Install**, in Settings → About. The second hands the APK to
  Android's package installer, which asks again in its own words. `REQUEST_INSTALL_PACKAGES` only
  makes the app eligible to ask; the user still grants "install unknown apps" on a system page.
- **A download is finished only when its byte count matches the release.** A connection closed early
  reads as end-of-file with no exception, so "the stream ended" and "the file arrived" were the same
  event: a truncated APK was renamed to a finished one, failed its signature check because half an
  APK has no certificates, and the app reported that the release was **signed with a different key**
  — true of nothing, and it sent the search a long way from the network fault behind it. Hence
  `DownloadedApk`: damaged and wrong-key are separate answers, because one means fetch it again and
  the other means uninstall by hand.
- **A download resumes and retries.** 70MB on a phone loses its connection — a Wi-Fi handover, a
  lift, a screen locking — and "Software caused connection abort" used to delete the part file and
  start from zero, so the download had to win a coin toss in one go. The part file is the progress
  now: three attempts, each sending `Range: bytes=<what is on disk>-`. **A 200 rather than a 206
  starts the file over**, because a server that ignored the range is sending the whole thing again
  and appending it would build a corrupt APK that only says so at the signature check, seventy
  megabytes later. An HTTP refusal (`HttpFailure`) is never retried — a rate limit does not improve
  by being asked three times in ten seconds.
- **Granting that permission force-stops the app** on Samsung, and everything the app knew about the
  70MB it had just fetched died with the process while the file sat in `filesDir/updates/`
  untouched. So the **file is the record**: its name carries the version, `downloaded()` reads it
  back on every launch, and the card returns as Ready to install. A download survives the permission
  grant that was needed to install it, and is never paid for twice. Anything unreadable, caught up
  with, or signed by another key is deleted there rather than offered — all three end at an
  installer refusing it. Nothing else deletes downloads: `check()` used to clear them when GitHub
  offered nothing, which would throw away a good file over a release page briefly missing an entry.
- **The APK is written into a `PackageInstaller` session, never shared as a URI.** The file does not
  cross a process boundary, so nothing depends on provider export rules, on a grant outliving a
  handoff, on package visibility, or on which app the resolver picks — the chain that killed three
  releases in a row. The system reports the outcome to `UpdateInstallReceiver`:
  `STATUS_PENDING_USER_ACTION` is Android's own confirmation dialog, which the app launches and the
  user still approves. **The app finally knows whether an install happened**, which it never did.
  `STATUS_SUCCESS` usually never arrives — installing this app replaces this process.
- Superseded, kept as the reason the above exists: **granting the installer read access by package**
  did not work either.
  `FLAG_GRANT_READ_URI_PERMISSION` grants to the activity receiving the intent; Samsung's installer
  takes it in `InstallStart`, hands off to `InstallLaunch`, and only then reads the APK to build its
  staging session — by which point that grant is gone. It failed with *"Permission Denial: opening
  provider androidx.core.content.FileProvider … that is not exported"* and **closed without a word**,
  so the phone looked like it had ignored the button. The handler is resolved rather than named
  (this device answers `com.google.android.packageinstaller`, others `com.android.packageinstaller`),
  which needs the `<queries>` element in the manifest — package visibility hides the installer
  otherwise and the lookup returns nothing. Revoked when the card leaves Ready.
- The button reads **Allow installs** before the permission exists and **Install** after, refreshed
  by a `LifecycleResumeEffect` because returning from a system page does not recompose a card on its
  own. A button saying Install that opens a settings page is how someone grants the permission,
  comes back, and concludes the install silently failed.
- The APK lands in `filesDir/updates/` and reaches the installer through a **second FileProvider**,
  authority `${applicationId}.updates`. Not another path on the traces provider: that one is named
  for what it shares outward, and an authority whose name lies about what it carries is how the
  wrong file gets granted to the wrong app.
- **The downloaded APK's signature is compared with this build's before Install is offered.** It is
  not a second opinion on Android's check — it is the same check, made early enough to be explained.
  Left to Android it is "App not installed" with no reason, which reads as a broken download.
- Versions compare **as numbers** (`AppVersion`): 1.0.10 is the release after 1.0.9, and as text it
  sorts before it. A release with no APK attached — a tag pushed while the build was still running —
  is not an update, and neither is a draft or a pre-release.
- **A release is two APKs — `arm64-v8a` and `x86_64` — and the app picks its own.** Most of the size
  is TDLib's native libraries, so arm64 is 72MB where a universal build is 134MB. One version, one
  build, one signature; only the libraries inside differ, and the versionCode is deliberately **not**
  offset per ABI, so nothing can read as a different version. **No universal APK is published** —
  it was 134MB of upload for devices that do not exist here, and it made every release wait on it.
  Installing by hand means taking `arm64-v8a`. `preferredApkName` takes the first entry of
  `Build.SUPPORTED_ABIS` that a name matches, then universal, then an APK named for no architecture
  at all — the last two are dormant now but keep a release that does carry one working, and are what
  let a phone update from a release cut before the split. An APK for another architecture is never a
  fallback: Android refuses to install one, so offering it would promise an update that cannot happen
  after a download the size of the whole app. A device that is neither of the two is told there is no
  update, which is true.
- **The launch check speaks only when there is something new**, exactly like the launch sync. It is
  independent of Telegram, so it does not wait for a session. Failures are silent: being offline is
  not news. Switchable off in Settings, and the switch travels with the rest.
- **The updater adds nothing to the background.** The one thing that does is **What this phone
  does on its own** below.

## What this phone does on its own

A checkbox for prices, one sweep at the close, and up to four analyses. This is the one feature that
reverses a rule the app had held since the beginning — that nothing but `OverdueWorker` runs while
the app is closed.

It used to be a general scheduler: a table of jobs, each with a name, a kind of work, and a choice
of firing once, on chosen weekdays, or repeatedly inside a window, set up through a form of about
nine hundred lines. That was torn out on 2026-08-30, and what came back on the same day was **four
schedules, each a time and a set of weekdays** — because the shape of the form, not the number of
schedules, was what nobody could get through. The kind of work is gone (keeping prices fresh is a
checkbox that needs no configuration at all), the trigger is gone, the name is gone, and the cap
means the whole of what this phone does unattended fits on one screen. `ScheduledJob`,
`JobTrigger`, `JobWork` and the `scheduled_jobs` table stay gone.

- **Keeping prices fresh** — `model/MarketRefresh.kt`, switched on in Settings under General → Prices.
  Every 15 minutes, Sunday to Thursday, 10:00 to 14:45 Cairo. Free: it reads the same public feed
  the Fetch prices button does. Fifteen minutes because Android holds
  `setExactAndAllowWhileIdle` to roughly one alarm every ten while dozing, so anything shorter is
  not refused, it is quietly stretched — and a schedule that promises a frequency the system will
  not keep is worse than one that promises less.
- **The sweep at the close** — `model/CloseSweep.kt`, booked while **either** trade notification is
  on and configured by nothing. One fire at 14:45 Cairo on a trading day: it fetches the day's
  prices once, re-scores the record off them, and announces what the session did — which is what
  makes a window that ran out this afternoon a notification this afternoon. Free, the same public
  feed. It fetches rather than only sweeping because a sweep can only judge the rows on disk: with
  the price checkbox off there may be no row for today's session at all, so nothing would have
  expired and the wake would announce nothing. Nothing is fetched twice — `CloseSweep.dueFire` is
  answered against `lastPriceRefreshAt`, so the 14:45 refresh slot on a phone that keeps prices
  fresh has already done this fire's work and it stands down. **Deliberately without a grace
  window**, unlike the other two: a refresh slot that is late has been superseded fifteen minutes
  later, while this fire has no successor for a day, so a phone that was asleep at 14:45 still owes
  it at nine that evening.
- **The scheduled analyses** — `model/AnalysisSchedule.kt`, edited in Settings and summarised on
  the Analyze card. At most `AnalysisSchedule.MAX` of them, each with a time, the weekdays it
  keeps, and the chats it froze. Paid, and see the guards below.

**The window runs to 14:45, a quarter of an hour past the close.** The exchange stops at 14:30 and
the day's figures settle over the minutes after it, so a window ending on the bell stores a session
that is very nearly but not quite final.

- **Cairo time, always.** A schedule belongs to the exchange, not to wherever the phone is: a user
  who books a run for before the open means before the open in Cairo, and one that shifted an hour
  when they landed would read a session that had not happened.
- **The weekdays are chosen, any of the seven, and the weekend is never a default.** A fire on a
  Friday or a Saturday is not the dead letter it looks like: `egxTargetSession` maps a shut day to
  the next session that exists, so the run is aimed at Sunday, and `resolveAnalysisWindow` starts
  its window at the Thursday that closed the week. A weekend schedule is therefore the one that
  reads what the chats posted over the weekend and has Sunday's report ready before Sunday opens —
  and the scheduled run needs no special case for it, because the session-flip guard compares the
  same two answers and they agree. `ScheduleClockTest` holds that pairing, since a disagreement
  there would be a schedule that fires every week and skips itself every week.
  `ScheduleClock.tradingDays` stays Sun–Thu but is no longer a bound on anything: it is what the
  price refresh's slots are built from, and what "every trading day" means on a row.
  `DEFAULT_DAYS` is those five, so the weekend costs money only where it was ticked on purpose.
  An **empty** week is reported as a blocked schedule rather than quietly corrected, because
  correcting it means choosing days for the user.
- **Four, and the cap is the point.** Small enough that every schedule fits on one screen, and a
  bound on the bill: each one that fires is a paid request, so the most a day can cost is four of
  them. The old table had no cap at all, which is part of why nobody could say what it would do.
  Enforced in `SettingsRepository.saveAnalysisSchedules` as well as on the screen.
- **Identity is an id, never a position.** A run records its outcome from a process with no screen
  in it, while the list on disk may have been edited since it started. `recordAnalysisSchedule`
  reads, replaces by id and writes; an outcome for an id that has gone is dropped rather than
  bringing a deleted schedule back. `nextId` is one past the highest ever handed out and is never
  reused.
- **`ScheduleClock` and `MarketRefresh` have no Android in them**, because a rule about what
  happens at 07:00 next Sunday cannot be checked by waiting for next Sunday. The zone is a
  parameter only so a test can drive it through a daylight-saving gap on purpose.
- **A fire is compared against the fire last served, never against the wall clock.** `lastFiredAt`
  stores the *scheduled* moment, so a run that started 20 minutes late still counts as having
  filled its 07:00 slot — comparing real start times would let one slot fire twice on a phone whose
  clock moved.
- **Grace is what makes a schedule work at all.** Two hours for the analysis, which covers a phone
  left face-down through a night in Doze; one slot for the price refresh, because the next one is
  never more than a quarter of an hour away and fetching for a slot about to be superseded is a
  wasted pass over a public feed. Past the grace the analysis records itself as **missed** rather
  than running late. Only the most recent unserved fire is ever considered: a week with the phone
  off comes back owing one run, not seven.
- **A price refresh skips when one has already happened since its fire came due.** Without it,
  opening the app inside a missed slot's window fetches every stock twice within seconds.
  Hence `lastPriceRefreshAt` beside `lastPriceRefreshDay` — the day cannot answer "since this fire".
- **AlarmManager is the clock; WorkManager does the work.** WorkManager's delays are a floor and
  not a promise — in Doze a fifteen-minute period becomes whenever the system next feels like it —
  so `JobScheduler` books one exact alarm at the earliest of the three next fires — a refresh slot,
  the sweep at the close, or a schedule — and the run that answers it books the next. One alarm
  rather than one each: only the nearest matters.
  `setExactAndAllowWhileIdle` where the user has granted `SCHEDULE_EXACT_ALARM`, falling back to
  the inexact form where they have not — the app asks rather than declaring `USE_EXACT_ALARM`,
  which is meant for alarm clocks.
- **Four things mean re-book**, all handled by `ScheduleReceiver`: the alarm firing, a reboot, an
  update replacing the app, and the exact-alarm permission changing — plus every launch, from
  `EgxApplication`. An alarm survives none of the first three, and one nobody re-booked has
  silently stopped keeping time. Re-booking happens in the receiver, which needs no network — a
  phone that boots into a tunnel still comes out with its alarm set — while the work goes to
  WorkManager, which waits for one. The receiver sweeps while **anything** is on and cancels only
  when everything is off, the same shape as the daily check and for the same reason.
  `ScheduleClock.nextFireOf` is what the one alarm is booked from: the earliest fire any enabled
  schedule has left, beside the price refresh's own and the close sweep's.
- **Device-local, and never synced.** Everything else the app records travels through the sync
  channel; these must not, because three phones keeping one schedule is the same work done three
  times — and for an analysis, three times the bill for one answer. Both live in
  `SettingsRepository` rather than `AppPreferences`, which is published.
- **`ScheduledJobWorker` goes through `AppState`, which is the opposite of what `OverdueWorker`
  does, and is deliberate.** That worker answers a question out of the database and touches nothing
  else. A scheduled run does the same work a button on screen does, and a second implementation of
  a price refresh would be a second set of rules about what is fetched, what is re-scored and what
  the record then says — one of the two would eventually be wrong, and it would be the one nobody
  is watching. The cost is that waking the process brings the catalog, the stale-price check and a
  sync catch-up with it; none of them is paid.
- **The status line is never blank.** Silence is the failure mode of every scheduler on this
  platform: the phone puts the app to sleep, nothing fires, and nothing says so. So every price
  refresh writes a note including the ones that did nothing, and the two system permissions that
  would stop it working — exact alarms, and Samsung's battery optimization — are reported in the
  error colour *ahead of* any cheerful line about the last fetch. `marketRefreshLine` ranks them,
  and it is unit-tested, because these are the sentences that will be read on the morning somebody
  wonders why the prices have not moved.
- **The two system permissions are shown whether or not they are granted.** A page that goes quiet
  once something is right leaves the reader unable to tell "granted" from "the app forgot to check".

### Moving off the old job table

`ScheduleMigration` runs once, from an `init` block placed deliberately **above** the state it
writes — Kotlin runs initialisers in source order, and a migration below those properties would be
overwritten by their own initialisers. It carries an intent across without inventing one:

- An enabled `PRICE_REFRESH` row, whatever its trigger, becomes the checkbox. After the close,
  hourly, through the session — every one of them was a way of asking the same question.
- A `REPEAT` `ANALYSIS` row keeps its time, its chats and its switch, and up to four of them are
  carried rather than only the first, numbered in the order the table held them. Which weekdays
  each kept is **not** carried — that column went with the table — so every carried schedule gets
  the whole trading week, which loses no run the owner had booked where guessing narrower would. A
  `ONCE` or `INTERVAL` one is still dropped: a one-shot is a button press with a delay on it and
  its moment has passed, and an interval was the shape that paid for the same session over and
  over. What replaced that shape is a second schedule with its own time and the freshness check
  below, which is the difference between reading what has been posted since and buying the same
  answer twice.
- A carried schedule is **armed at the migration**, so one whose hour has already gone by today
  does not owe a run the moment the app finishes migrating and pay for it through the grace window.
- Then `scheduled_jobs` is dropped. A table nothing reads is one the next reader of the file has to
  work out the status of.

### The scheduled analyses

- **Two switches, and both have to be on.** The schedule's own, and `paidSchedulesEnabled`, which
  is what stands between the clock and the owner's money — one switch for all four schedules, since
  it is a decision about spending and not about any one of them. `JobRunner.paidWorkAllowed`
  **defaults to refusing**, which is the point: a caller that wants paid work has to say so. A run
  whose switch is off is passed over and says so on the card — it is not hidden, and it is not run.
  Arming the clock to spend money later is the same act as spending it, so it needs the owner's own
  hand.
- **`AnalysisPlan` exists because a run can start from two places.** `analyze()` used to read the
  Analyze screen's fields directly, and a scheduled run has its own answer to every one. Two
  functions assembling a request would have been two sets of rules about what gets sent, and the
  one that drifted would have been the unattended one. Both paths build a plan and hand it to
  `executeRun`. Its `onScreen` flag changes **nothing about what is run or saved** — only whether
  the reader is thrown onto Results.
- The plan deliberately **does not carry the provider, model or key**. Those follow Settings at the
  moment the run starts: a schedule that pinned a model would go on sending to one the user had
  moved off. The **chats and content types are frozen** when the schedule is aimed, for the opposite
  reason, and the same one a position snapshots its levels: re-ticking chats on Analyze months
  later must not silently re-aim a run that happens while nobody is watching.
- Always the **next session**, never a historical date. A repeating schedule re-reading one fixed
  day would pay for the same answer every week.
- **Four guards, and each is a way of being wrong that costs a real request.** All of them end in
  `JobSkipped` — written down, not charged, tried again at the next fire.
  - **The session flips at 14:30 Cairo.** A fire delayed across that line — by Doze, by a phone
    that was off, by the grace window doing exactly what it is for — would buy an analysis of the
    following day and produce a report that looks entirely ordinary. So the session the fire was
    booked for is compared with the one a run now would cover, and a disagreement stops it. This is
    the subtle one, and `RecommendationDateTest` documents the rule it rests on.
  - **Nothing new since the last report**, which is what a second schedule in a day turns
    `duplicateOf` into. The old rule was that a report already covering this session and these
    chats meant skip, full stop — right while a repeat could only be the same request paid for
    twice, and wrong the moment a midday schedule exists to pick up what was posted after the
    morning one. So the sources are collected first (free: it reads Telegram's own store and
    reaches no provider), and `SourceFreshness.newSources` compares them against the saved
    report's by Telegram message id. Nothing new means skip; one new message means run. A source
    with no message id counts as new, because it cannot be shown to have been read before and
    erring towards running is the right way round for a guard that only exists to stop paying for
    a repeat of nothing. The Analyze button's own duplicate warning is untouched.
  - **Preconditions** — a credential and model saved, no run already going, and a Telegram session.
  - **No sources** — chats that posted nothing in the window are a skip, not a failure.
- **A cold start has to wait for TDLib.** The alarm wakes a process that may have been dead, and the
  encrypted database has to open and the session come back before a chat can be read. Ninety
  seconds; past that the fire is skipped rather than run against no session, which would look
  exactly like a schedule that does not work.
- **The worker goes foreground for a paid run**, through `setForeground` rather than by starting
  `AnalysisService`. Two problems, one answer: WorkManager stops ordinary work at about ten minutes
  and an analysis can outlast that — the response timeout alone reaches fifteen — and from Android
  12 an app in the background may not start a foreground service at all, so the service the app has
  always used would be refused precisely when it is needed. Going foreground lifts the ceiling and
  makes the later `AnalysisService` start legal, because an app already running one may start
  another. It uses **the same notification id**, so the reader sees one notification that fills in
  with real numbers rather than two describing one run. `analysisRunning` is wrapped in
  `runCatching` regardless: losing a paid run that is already under way to an exception about a
  notification would be the worst possible trade.
- WorkManager declares `SystemForegroundService` **without a foreground service type**, and from
  Android 14 one without a type is refused outright. The manifest merges `dataSync` onto it.
- **A price refresh wake brings nothing else up.** `startedForSchedule` leaves Telegram, the sync
  and the update check unstarted, which is the difference between a wake that fetches prices and
  one that connects to Telegram and catches up on four kinds of synced document first. An analysis
  needs all three and starts the app in full.
- **Every row names the chats it covers**, two of them and a count, then its week, then its next
  fire. The whole point of freezing the selection is that it stops matching what is ticked on
  screen, so a line saying only "analyse the next session" cannot be checked without opening it —
  and with four rows the coverage is also how the reader tells which one they are looking at.
- **One list of reasons, split by who can fix them.** `scheduleBlocker` holds what belongs to one
  schedule — switched off, no days, no chats, chats gone — and is what a row says in place of its
  next fire. `sharedBlocker` holds the two that stop all four at once, the money switch and the
  credential, and is drawn **once** above the rows: on a list, saying "paid runs are switched off"
  four times over the switch that answers it is noise, not honesty. `blockedReason` composes both
  in the old order and is what a single-schedule summary reads. The order is what the reader has to
  fix first: the switch, then what it is aimed at, then anything the run needs. A card listing four
  problems fixes none.
- **`schedulesSummary` is the one line both screens read.** The Analyze card and the closed Settings
  card build it from the same function, because the two disagreeing is exactly how a summary ends
  up promising a next run over schedules that are all blocked. It reports a shared blocker before
  any count, then how many are on, the earliest fire any of them will actually reach, and how many
  are blocked — and it carries a `warning` flag so the same sentence is red where it needs to be.
- **An empty chat list is not evidence the chats have gone.** On a cold start Telegram has not
  loaded, and "its chats are no longer in the app" would be the wrong alarm at the worst moment. It
  is also only raised when **all** of the chats have gone: losing one of four leaves a run that
  still reads the other three.
- **Re-aiming is deliberate and never quiet.** `ReaimControl` draws nothing at all while the frozen
  aim matches what Analyze has ticked, and otherwise offers one button naming what it would take —
  the frozen side is already on the line above it. Freezing is right; a selection that can never be
  corrected would mean deleting the schedule to fix a typo.

### Where they are drawn, and why only in one place

Every control used to be drawn twice — in full on the Analyze card and again in Settings — which is
two places to edit one thing and, worse, two places that can disagree about it. As of 2026-08-30
**Settings owns the schedules and Analyze summarises them.**

- **Analyze gets one line and a button.** It is the screen where a run is aimed, so what it owes the
  reader is the answer to "is anything going to happen without me" — the count, the next fire, and
  the way to the controls. `editSchedules()` sets `openScheduleSettings`, navigates, and the
  Settings section opens itself and clears the flag as it takes it, so coming back later finds it
  closed like every other group.
- **A row is a switch, a time, seven day chips and a delete.** The time is set by pressing the time
  itself — the separate "Change" button beside it was a second control for one number — and the
  days are chips rather than named checkboxes because seven names down a page is the shape of form
  this feature was rebuilt to get away from, and because a filled-in week is a pattern the eye
  reads before it reads a letter of it. The chips start on Sunday, as the exchange's week does, and
  sit on a line of their own: seven of them beside a switch, a time and a delete would overflow the
  cover panel, and a week with its last days clipped off is worse than a week on its own line.
- **Under the row, at most two lines, and the second only when there is something to act on.** What
  it covers and when it next runs — or, in the error colour and in place of it, what is stopping it.
  Then the last outcome, and the re-aim button when the aim has drifted from the screen.
- **The four grey paragraphs of prose are gone.** They explained why the feature is shaped as it is,
  which is what this file and the KDoc are for; on the screen they were four blocks of small text
  between the reader and one switch.

## What back does, and the stock sheet

Two things the app could always have done and never did: go back, and put one stock in one place.

- **Back had no handler at all.** `openCall`, `openPosition` and `openSavedResult` throw the reader
  across tabs — from a card, from a digest tile, from a notification — and the system's back button
  answered every one of them by closing the app. `NavStack` is one step of history: the tab a jump
  left, and what to reveal on arriving back at it. `AppState.goBack` spends it, then falls through
  to clearing the current tab's filters, then to the system. **The jump before the filter**,
  deliberately — both can be outstanding at once and the jump is the more recent, so answering the
  filter first would strip a narrowing the reader set up on purpose while leaving them on a tab they
  did not choose.
- **One deep, and that is the design.** The five destinations are peers; a back button that walked
  back through a morning's tab presses would take a dozen presses to leave. What was missing is the
  *last* jump the app made on the reader's behalf, and nothing else. A tab the reader chose is not a
  jump, so `navigate` clears the stack rather than adding to it — guarded on the destination actually
  changing, because the pager publishes its own arrival at the end of every travel this class starts
  and an unguarded clear there would throw the return away in the same breath as the jump.
- **A notification records no return.** It arrives at a tab the reader was not on and usually at an
  app that was not running, so there is nowhere to go back to; recording one would land the first
  back press on the Analyze tab they never visited.
- **A filter is cleared whole**, not one control at a time. Three presses to undo three chips would
  be back re-enacting the reader's typing, and the screen's own Clear filters — two presses away
  inside the folded panel — clears them together too. `PageState.filtersActive` and `clearFilters`
  are the one predicate, read by the three screens *and* by the shell, because three screens each
  stating their own version is three that agree until one gains a filter.
- **`StockSheet` is a sheet, not a sixth destination.** A ticker's story was spread across four
  screens and the only way to gather it was to type the same code into three search boxes.
  `StockSearch` had already made those boxes ask one question; this is where the answers meet. A
  sheet on `ChannelScoreSheet`'s terms because a stock is not a peer of Analyze and Settings — it is
  the longer version of a thing that was pressed, which is what a sheet from the bottom already means
  here. It also keeps back simple, since `ModalBottomSheet` takes the press first, and it opens from
  any tab without moving the reader off the one they are reading.
- **It states nothing new.** Every figure on it is drawn somewhere else; what it adds is that they
  are drawn together. The exception is `StockScore`, which has been computed for every stock since it
  was written and reached the reader only through the shortlist signals and the Ask AI prompt — so
  "what happens when anybody recommends this stock" was a question the app could answer and no screen
  asked. Rows lead through `openPosition` and `openCall`, the two entrances every cross-tab press
  already uses, and dismiss the sheet first: one left open over the tab it just sent the reader to is
  covering the card it sent them to read.
- **`LocalOpenStock` is a composition local, and that is a deliberate exception.** `onOpenTrade` and
  `onOpenCall` are threaded because they travel one or two levels and belong to the card offering
  them. A ticker is drawn on a call card inside a session card inside a band, on the same card from
  Results, on a position card inside a card, and in a table row — threading it would add a parameter
  to eight signatures to reach four leaves. It sits beside `LocalWindowWidth`, which is where the
  shell already publishes what every screen may need and no screen owns.
- **A tile that already presses somewhere keeps its press.** Digest tiles and Overdue tiles still
  lead to the trade or the call; the ticker only becomes a target where nothing was carrying one.

## The status line

One line in the header says what the app is doing and what it has just done. It was a floating toast
at the foot of the screen until 2026-08-25.

- **It moved because of where it was, not how it looked.** An app that reports something after
  almost every tap was answering from the far end of the screen from the button that had been
  pressed — on the unfolded panel, the better part of a foot away. And it was the only piece of
  chrome that had to be lifted clear of the navigation bar and lowered again as that bar came and
  went (`toastClearance`), which is a whole mechanism existing to keep one transient message off one
  transient bar. Both problems are answered by putting it where the app's own name already is.
- **`busyLabel` and `statusMessage` share the line, and a running action wins it.** They describe
  the same activity a moment apart — `runAction` sets the first, then clears it and sets the second
  — so two surfaces meant the header could say "Fetching prices" while a tick sat under it reporting
  the *previous* fetch. `BusyBar` is gone; the `LinearProgressIndicator` stays as a hairline under
  the header, and its label moved up into the line.
- **`StatusStage` is what the glyph and the timing read**, not `succeeded`. A step still running is
  `WORKING` and gets a spinner; reading progress off `succeeded` put a tick beside work the app had
  not finished, which is what "Connecting to Telegram" used to show. It defaults from `succeeded`,
  so the fifty-odd ordinary outcomes are unchanged and only a step in flight names it. Not to be
  confused with `StatusTone` in `CommonUi.kt`, which is GOOD/BAD/NEUTRAL for a `StatusPill` — the
  two are unrelated and the name collision is why this one is `Stage`.
- **A confirmation clears itself after 4 seconds; a failure waits to be tapped.** A failure is the
  one kind worth reading twice and the one kind that can arrive while the reader is looking
  somewhere else — a provider's refusal is often the only account of why nothing happened. The
  timer is a `LaunchedEffect` keyed on the message, so a second outcome cancels the first one's
  clock rather than clearing the new line early.
- **Beside the name where there is width, under it where there is not**, on `LocalWindowWidth`
  rather than on the header's own measurement: the shell already works the width out to choose a
  rail or a bar, and that value is published precisely so two parts of the app cannot disagree about
  where the line falls. Below 600dp the cover screen has under 200dp spare after a 22sp title, which
  is most of these messages truncated, so the line drops to a row of its own there.
- **The row fills the width and the arrangement does the aligning.** Capping it instead left it
  stranded mid-header on the wide layout: a capped row inside a weighted slot sits at the start of
  that slot, not at its end.
- **It animates height as well as opacity.** On the compact layout the line has a row of its own, so
  a plain fade makes the header jump a line taller the instant a message lands — which reads as the
  page twitching rather than as an announcement.
- **It carries at most one action, and only on something destructive.** `StatusMessage.undo` is a
  word the reader can press to take back what the line has just reported, and it is a slot on this
  line rather than a snackbar **deliberately** - the floating toast was removed on 2026-08-25 because
  it answered from the far end of the screen from the button that had been pressed, and bringing one
  back for this would undo that on purpose. The line already says what happened, sits where the app's
  own name is, and clears itself after four seconds, which is exactly the shape an undo wants. Two
  paths offer one: recording a sale (`reopenPosition`, which restores the row it was handed rather
  than one read back, and carries a newer stamp so the sale is undone on other devices too) and Keep
  Open. Everything else is an edit the reader can simply make again, and a button after every
  confirmation would turn the quietest chrome in the app into the loudest. A line carrying one is
  **not** dismissable by tapping the row, or the offer would be thrown away by the gesture meant to
  read it.
- **The tone is one tinted glyph and never the text.** Colouring the words would make every routine
  confirmation the loudest thing on screen, and this line now sits beside the app's own name, which
  is the last place that should flash. Same rule the toast followed.
- **Wording.** Sentence case, no trailing full stop, an ellipsis only on something still running,
  and `·` only between counts — `Priced 40/42 · 2 unpriced · 1 stale` is what it is for, where
  `Key verified · 8 models` was using it to join a clause to a count. One event gets one wording:
  the chat count is `N chats` from both the launch collector and the Analyze refresh, which used to
  say "loaded" and "found".

## Where an explanation lives

Every screen used to say all of it at once. A checkbox was one line of control under four lines of
grey prose, three times in a row, and Settings was mostly prose — so the settings were what you had
to hunt for, between the explanations of them. The words are good words and none of them were cut;
they moved one tap off, next to the thing they are about. This is the rule the schedules already
followed when their four grey paragraphs went (see **Where they are drawn**) and it is now the whole
app's.

- **One affordance, and it is `Icons.AutoMirrored.Outlined.HelpOutline`.** A question mark rather
  than an ⓘ, because `Icons.Outlined.Info` is already the About card's own icon and one glyph cannot
  mean both "the version number" and "explain this". The question mark was already doing this job on
  the channel ranking and on "Does it matter?"; this makes it the rule rather than those two
  screens' habit. Auto-mirrored, since half the content here is Arabic. Muted rather than `primary`:
  it sits beside dozens of controls, and a page of coloured glyphs is the same clutter in a smaller
  font.
- **It opens a `ModalBottomSheet` on `ChannelScoreSheet`'s terms** — same padding, same scroll, same
  skipped partial state. A reader who has opened one explanation in this app has opened all of them.
- **The note goes on the smallest thing it is true of.** A rule about one checkbox rides that
  checkbox (`SettingToggle`'s `about`); one about a group rides the group's heading
  (`ExpandableSection`, `SectionCard` and `SubSection` all take an `about`), where it is reachable
  without opening the card at all. `WordingFlowNote` and `GeneratedPromptNote` are exported for
  exactly that — the words belong to the section that owns them, the heading belongs to Settings.
- **`SettingToggle` leads with its control, switch or checkbox alike.** Fifteen hand-built rows had
  drifted into a checkbox leading here, a switch trailing there, and two gaps between control and
  label. Which control is used says how heavy the setting is — a switch arms the phone to act
  unattended — never where it sits.
- **`SettingLabel` defaults to `labelLarge` and takes `bodyLarge` for a value.** A version number and
  a slider's current reading were body type before they gained a question mark; shrinking a figure to
  make room for the affordance beside it is the affordance changing what it was added to explain.
- **Every question mark on a settings card sits in one column, and `SettingRow` is what puts it
  there.** Four rows had been built by hand - Save diagnostics, Restore from a backup, Fetch prices
  now, Add a schedule - and each put its question mark immediately after the button, where
  `SettingToggle` and `SettingLabel` put theirs at the trailing edge: the same affordance in two
  places on one card, so neither read as a column. A row that carries one also holds open
  `ChevronGutter`, the 24dp `ExpandableSection` spends on its chevron, because the group's own
  question mark sits *before* that chevron - without the gutter the headings' column and the
  contents' column stand 24dp apart down a page made almost entirely of those two things. The
  gutter is spent only on a row that has a question mark, so nothing else gives up any width.
- **`SettingToggle` draws its checkbox 14dp left of where Material puts it.** That padding is inside
  the touch target, so a card whose buttons, text and sliders all start at the card's own edge had
  its checkboxes starting 14dp further in - the one thing on the page not lining up with the rest.
  The target keeps its full 48dp and overhangs the card's own padding by that much, so pressing it
  is unchanged - shifted rather than resized, because forcing the size from outside puts the
  constraints on the wrong side of Material's internal padding and draws the box against the corner
  of its cell instead of in the middle. Both
  controls then stand in one `ControlColumn` as wide as the switch, which is what puts a checkbox's
  label and a switch's label at the same place; left to themselves the two sat 16dp apart.
- **Five kinds of text deliberately stayed on the page**, and the distinction is what stops this
  becoming a way to hide things: an `AlertDialog`'s body, because a confirmation *is* its
  explanation; live status and error lines, which report a state rather than a rule; empty states,
  which say what to do next; a per-item warning like the screenshot-sanity caveat, which is about
  that one call; and any line with a button attached, like the notifications-off prompt on Analyze.
- **`InfoNoteTest` reads the sources rather than composing.** A note that has lost its prose draws an
  icon and opens an empty sheet, which looks like an unfinished feature rather than a deleted
  paragraph — silent everywhere else, so it is checked where the words are written.

### How Settings is grouped

Ten cards became **seven** on 2026-09-03, and nothing was removed but the price-feed fault list (see
**When the feed goes quiet**). In order: **Analysis**, **Scheduled analysis**, **Telegram**,
**Notifications**, **General**, **Saved data and privacy**, **About**.

- **The problem was cards holding one control each.** Appearance, Sync, Trades and the price refresh
  were four cards, and each cost a header, a summary line and a tap to reach a single dropdown or a
  single button — four cards that could not be told apart at a glance because each said nothing but
  its own name. `General` is where they went, as four `SubSection`s: *Appearance*, *Trade defaults*,
  *Sync*, *Prices*.
- **`General` does not claim they are one subject.** What they have in common is that none of them is
  worth a card — which is what a General is for, and saying so in the source is what stops the next
  reader trying to find the theme. The bottom two do belong together: Sync and Prices are the free,
  unpaid ways this device keeps its own copy current, and neither sends anything to the AI provider.
- **A group nests once and never twice.** `SubSection` is a heading, a chevron and a rule, precisely
  because a card drawn inside a card reads as a mistake — so every one of these is a group inside a
  card and not a card inside one. `PricesSubSection` is drawn by its own file for length, not because
  it is a different kind of thing.
- **Every group carries a summary, and the summary is the point of folding it.** A closed group that
  said nothing would put the reader back to opening all of them to find the switch they came for,
  which is what the grouping was for.
- **Sync sits under General rather than under Telegram**, which was the other candidate and is the
  owner's call: the account is what Telegram is about, and pressing Sync now is housekeeping. The
  Telegram card's own note says where sync went, so the two do not become a place each to look.
- **Every card and every group carries an `about`**, on the rule above: the note goes on the smallest
  thing it is true of, and a group-wide one is reachable without opening the group. The pass on
  2026-09-03 filled the gaps — `Analysis → Model`, `→ What to send`, `→ Validation`, the Telegram
  card, all four General groups, the three Notifications groups, and the About card.
- **`Delete all saved analyses` has a group to itself, at the bottom of Saved data and privacy.** The
  one irreversible button on the page should not sit at the end of a run of buttons that are not, and
  its note says to take a backup first.

## Gotchas

- `local.properties` holds `telegramApiId` / `telegramApiHash` and is gitignored. Absent, the app
  falls back to asking for them, so a fresh checkout still builds.
- `Uri` is stubbed in unit tests; tests that need inputs use `AnalysisInput.Text`.
- `LocalDataStore.DATABASE_VERSION` — bump it and add the table to **both** `onCreate` and
  `onUpgrade`. Currently 27. **Bumping the constant is half of it**: `session_events` was added to
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
  `feed_checks` and `feed_faults`, written against the version-26 table for that same reason
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
  anything. `ExtraColors.actionLine` carries it, and it has **its own stops rather than
  `actionFill`'s** for the reason `aiLine` has its own beside `aiFill`: the fill sits *inside* the
  line, so a line in the fill's colours is a line against itself and disappears. What it has to read
  against is the page scrolling behind the button — dark on one theme, near-white on the other — so
  the stops invert between the two while the hue does not. The hues are the aurora's own, in the
  aurora's own order (cyan, blue, teal), rather than a fourth set invented for the edge.
- **The edge is 0.74 against the ground's 0.84, and its hues run about a third under the aurora's.**
  It shipped opaque and full-strength on the argument that an edge letting the page through stops
  holding the shape — which was wrong on the device: it read as a bright cyan wire around the button,
  the loudest thing on a dark page and competing with the label it was meant to frame. The shape
  still holds, because what draws it is the contrast with the page rather than the weight of the
  line. `ActionPaletteTest` pins the properties and not the figures — nothing the action is drawn
  with is solid, every stop in a ramp shares one alpha (a ramp that fades along its length reads as
  a mistake), and the edge shares no stop with the fill, which is the slip that produces an invisible
  edge: reaching for `actionFill` when adding the gradient, because it is right there and already
  the right family.
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
  already used, and the reason a smaller button here costs nothing to press. Settings, Channels,
  Backup and Schedules were left out of the pass on 2026-09-03 and still hold ~40 buttons in the
  old mixed state.
- **A report card opens on a press anywhere, and only while it is shut.** `Card(onClick = …,
  enabled = !expanded)` in `SavedAnalysisCard`, with `disabledContainerColor` pinned to the same
  fill so "disabled" does not read as greyed out. Open, that card holds the report's own toolbar,
  its call cards and the source trace, so a card-wide toggle would close the whole report on a tap
  landing in the gap between any two of them. The footer row — a `DisclosureButton`, which replaced
  a full-width filled button doing the same job as the card under it — is what closes it again.
- **A page's filters sit on a shelf, not on the page.** `FilterBar` wraps them in a
  `surfaceContainerLow` surface — deliberately a step *below* the `surfaceContainer` cards it
  filters and above the `background` well they sit on, so it reads as a shelf the controls stand on
  rather than as another card competing with the record. No title: the chips name themselves. It was
  a bare `FlowRow` between two cards, which is the one loose element on pages otherwise built of
  them, and on a 411dp cover screen four controls plus a text button wrapped into three ragged
  lines. **The fill is transparent, and that is the second attempt** — it was `surfaceContainerLow`
  on the sound-sounding reasoning that a shelf sits a step below the cards it filters, which is
  wrong about this palette: the well is `#0B0F14`, that shelf was `#11161C`, a card is `#151A21`.
  Six units apart, so a shelf immediately above a card read as one continuous background with a
  hairline through it, which is exactly what got reported. No fill cannot make that mistake, and it
  is also what lets the same shelf sit on the page ground on two tabs and inside a card on the
  third. **Clear filters sat inside that flow**, so it landed wherever the wrap put it and moved
  every time a chip's label changed — `All channels` becoming `2 channels` is enough to shift it.
  Pinned hard right now, where its appearing and disappearing costs the layout nothing.
- **One layout at every width: the search box, then a Filters chip, and nothing else on the line.**
  It shipped as two — everything on one line when it fitted, folded when it did not — and the wide
  form was the wrong answer even where it fitted: a shelf carrying four controls and a button is a
  toolbar the reader has to read before they can ignore it, on a page whose subject is underneath
  it. Two controls is a line that gets scanned rather than read, and it is the same line on both
  panels, so the tab does not rearrange itself when the phone opens. The controls open onto a line
  of their own — **one line that scrolls sideways, not a row that wraps**. Three chips fit a cover
  screen only while their labels are short: the panel has 355dp inside it on Insights and Results and
  323 on the Portfolio, where the Positions card costs it another 32, and `Source record, best first`
  alone takes Insights past 385. Wrapping made the panel two lines tall for one long label;
  `scrollableRow` keeps it one at every width, and `fadingScrollbar` draws nothing when there is
  nothing to scroll, so the short case is indistinguishable from a plain row and the 606dp panel
  never scrolls at all. `FilterBar`'s content takes a `RowScope` for that reason where `FilterRow`
  keeps its `FlowRow` — the in-report toolbar shares its row with a Hide button and is a different
  shape. The fold is the pattern that toolbar already used, chip label included, applied at every
  width here rather than only on a cover screen. **The search never folds**: Results
  and the Portfolio both carry the same comment, that it is "the control someone arrives at the
  screen already knowing they want".
- **The search box is elastic and it took a fix to `StockFilterField` to become so.** That composable
  ended its chain with `.width(StockFieldWidth)`, which beat anything a caller passed — so the
  `weight(1f)` around it did nothing and it sat at 150dp on a 606dp line with the rest spent on
  nothing. Its own height and width are applied **first** now, then the caller's, so 150dp stays the
  default for the in-report toolbar that shares a row with other controls, and `FilterBar` overrides
  it. The bar hands the modifier down (`search: (Modifier) -> Unit`) the way `ResponsiveRows` hands
  one to its items, rather than trusting each call site to remember: the bar owns how wide that box
  is. It comes out at roughly 245dp on the cover screen, 504 on the unfolded Fold, 572 on the tablet.
- **`Clear filters` lives in the panel, not on the line**, which is the price of holding that line to
  two controls: with the panel shut and a filter on, clearing means opening it first. The chip reads
  "Filters on" so it is never a surprise, and the search box keeps its own cross for the case that
  comes up most.
- **`folded` is separate from `active`, and that is the whole of why the chip is honest.** `active`
  offers Clear filters; `folded` lights the chip and counts only the controls actually hidden. A
  chip reading "Filters on" because of the search box beside it would be reporting something the
  reader is already looking at — and would go on reporting it after they had cleared everything
  else. **`FilterRow` survives** and is still what the in-report toolbar uses: that one lives inside
  the report card and already solved this, and a shelf nested in a card is chrome inside chrome.
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
- **The redesign was abandoned on 2026-08-19 — the shipping UI is the UI.** The three `next` bullets
  that follow are kept as a record of how the side-by-side app worked, not as a live plan. `next`,
  `src/next` and `src/current` are left in place but dormant: no new UI work goes into them, no
  `-next` tag gets published, and UI changes are edits to `src/main/java/…/ui/`. Nothing here
  affects `assembleDebug` or `assembleRelease`, which never compile `src/next`.
- **A redesign is judged as a second app: the `next` build type.** Its own `applicationId`, launcher
  label (`EGX Next`) and splash ground, so it installs beside the real app rather
  than over it — the downgrade Android refuses. A tag ending `-next` runs `assembleNext` and
  publishes a **prerelease**, which keeps it out of `releases/latest`, the one endpoint the updater
  reads. A build type and not a product flavour: a flavour dimension moves `app-debug.apk` out from
  under the install command above and the release job at once. `assembleDebug` and `assembleRelease`
  are untouched. It was removed once, when the 2.1.0 redesign shipped, and restored on 2026-08-13
  when that redesign was judged too close to the original.
- **Two UIs, chosen by build type, never compiled together.** `next` is being rebuilt from zero and
  shares only the data layer, so today's UI and the redesign are two bodies of source — and Android
  source sets *merge* with `main`, so a same-named file in both is a duplicate class. The entry
  point is what varies: `src/current/java/…/ui/AppRoot.kt` and `src/next/java/…/ui/AppRoot.kt`,
  identical in signature and nothing else, each applying its own theme. `MainActivity` calls
  `AppRoot` and does not know which it got. `src/current` is registered on **`kotlin`**, not only
  `java` — the Kotlin compilation does not follow Java source dirs, and getting that wrong builds
  `next` perfectly while debug and release fail to resolve `AppRoot`, which reads as the split being
  backwards. The redesign lives under `src/next/java/…/next/` and imports nothing from `ui` except
  `AppState` and `AppDestination`; the day it imports a screen is the day it starts copying what it
  replaces. `src/current` is one directory to delete when `next` becomes the app.
- **`next` reads the real sync channel and cannot write to it.** It started with a channel of its
  own, which was safe and useless: a fresh channel is empty, and a screen full of prices is exactly
  the thing that looks fine with no rows in it. It shares `EGX Analyzer sync` now, so it opens on the
  whole record. What keeps the two apart is `BuildConfig.SYNC_READ_ONLY` →
  `TelegramRepository.READ_ONLY`, and it guards **seven** paths, not the five `upload*` calls it
  looks like. The sixth is `resolveSyncChat`, which must not *create* a channel — a read-only build
  that failed to find the real one would otherwise put a second channel of that name in the owner's
  Telegram, the duplicate that function exists to prevent. The seventh is **`buryReport`**, which
  does not read like a write and is the one that could do lasting harm: it deletes the report's
  message and publishes a tombstone, so a delete pressed in `next` would take that report off every
  device for good. The test for completeness is not "which functions upload" but which TDLib calls
  mutate — today `sendMessage`, `deleteMessages` and `createNewSupergroupChat`. Prices never sync, so `next`
  still needs its own refresh before any figure means anything; the API key never syncs, which is why
  `next` cannot start an analysis even by accident.
- **`-PabiSplits` is passed by CI and nowhere else.** The ABI split is off by default on purpose:
  enabled everywhere, an ordinary `assembleDebug` would stop producing `app-debug.apk` and start
  producing one file per architecture, breaking the install command above and the CI artifact. To
  reproduce what a release ships, pass the flag by hand.
