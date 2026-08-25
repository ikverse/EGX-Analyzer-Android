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
- `data/PriceHealth.kt` + `ui/PriceFeedSection.kt` — which stocks the feed has gone quiet about,
  what it costs, and the Settings card that explains it in words. See below.
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
- `ui/EgxAnalyzerApp.kt` holds `AppHeader` and `AppStatusLine` — the app's name, and the one line
  that says what it is doing or has just done. See **The status line** below.
- `model/ScheduleClock.kt` + `model/ScheduledJob.kt` — when a scheduled job fires, and what one is.
- `model/AnalysisPlan.kt` — what a run covers, said explicitly, so the screen and the scheduler
  build the same request. See **Schedules** below.
- `data/JobScheduler.kt` + `data/ScheduleReceiver.kt` + `data/ScheduledJobWorker.kt` +
  `data/JobRunner.kt` — the alarm, the four things that mean re-book it, and what runs. See below.
- `ui/SchedulesSection.kt` + `ui/SchedulesSheet.kt` — the card on Analyze, the section in Settings,
  and the sheet both open.
- `data/OpinionPrompt.kt` + `data/OpinionPromptStore.kt` + `data/OpinionSearchBrief.kt` +
  `data/OpinionParser.kt` + `ui/StockOpinionSheet.kt` — Ask AI, on a call card in Insights.
  See below.
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
  one looked identical until one of them expired. `ScoredCall.isTPlusOne` is the test, spelled once:
  a T+1 card is the only call whose entry may trade in fewer sessions than it is judged over, which
  three places used to ask by hand as `entrySessions < windowSessions`.
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
one place that does, drawn by `PriceFeedSettingsSection` — **in Settings, beside the other
diagnostic**, and no longer on Insights.

- **It explains a figure rather than being one**, which is why it moved. On Insights every reader of
  the record scrolled past a fault report about four stocks to reach the record of every call, and
  the thing it qualifies already says so where it matters: a call on a stock with no prices is
  reported as unjudged on its own card. It is consulted when something looks wrong, and that is what
  Settings is for.
- **Present even when nothing is wrong**, which is the one deliberate difference from the Insights
  version. There it was absent on a healthy day, because a section reading "0 problems" over a
  record is a section that stops being read on the day it says something. Here it is a place to go
  and look, and a diagnostic that vanishes when it is working leaves the reader unable to tell
  "everything is fine" from "the app forgot to check".
- **The wording is for someone who wants to know why a price is missing**, not for someone who knows
  what a symbol migration is. `FeedFault.plainly` says what happened, what it costs in calls, and
  whether fetching again can do anything about it — which for two of the three faults it cannot.
  `FeedFault.detail` is the short form the record keeps, and stays as it was.
- A **Fetch prices** button sits under it, offered whatever the state, because it is also how a
  reader confirms nothing has changed. It reads the free public feed and sends nothing to the model,
  and the card says so.

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
  it can be tested at all. Zero on the day the deadline lands: expiring today is not being late.
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
  while the app is closed — see **Schedules** below, which reverses that rule deliberately. It now
  answers **two** questions off the one portfolio it builds: what is overdue, and what the calendar
  has quietly closed since yesterday — a window runs out because a date passed, and on a phone that
  opened nothing and refreshed nothing there is no other moment at which anyone would notice. So it
  is booked while **either** notification is on and cancelled only when both are off, which is why
  `AppState`'s callback is `dailyCheckChanged` and not the old `overdueRemindersChanged`. Turning
  the overdue reminder off used to cancel the work outright, and doing that now would take the
  deadline notifications with it silently.
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
session on their own (see **Schedules**), so a target reached at eleven in the morning was being
answered correctly by a screen nobody was looking at.

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
- **`SessionDigest` is pure and has no Android in it**, like `TradeAlerts` and `ScheduleClock`.
  `SessionDigestTest` builds every trade through `PortfolioCalculator` and every verdict through
  `Scoring`, because a hand-built view would let it assert whatever it liked about a state the
  scorer might never produce — the one way a test about what the market did passes while the card
  stays empty.

## Ask AI

A button on a call card in Insights sends one paid request and asks two questions: what the model
makes of the stock at today's price, and what it makes of the levels the channel printed. The answer
comes back in Arabic and is kept on the card.

- **Its own prompt, and nothing of the analysis reaches it.** `assets/stock_opinion.md`, read by
  `OpinionPromptStore` — a separate class from `PromptStore` on purpose. The analysis prompt carries
  a schema the desktop agreed on, a rules anchor `PromptComposer` fills in, and a version history
  the user approves; this one carries none of that, no wording rule is folded into it, and no run's
  `promptId` ever names it. Two stores rather than a second method on one, because sharing a class
  is how a rule about reading a Telegram card eventually reaches an opinion about a stock.
- **The whole of section 1 exists to stop the model reading the card back.** The DATA block is
  printed on the card the sheet opens from — entry band, stop, targets, peak, trough, sessions
  elapsed, return, latest close — so listing them back is not an opinion, and explaining arithmetic
  the reader can do is worse. It may name a figure where it carries a point and never more than two
  in a row. The escape hatch is honesty rather than padding: where price history is genuinely all it
  has, it says so in one sentence and returns `LOW` confidence.
- **Two figures the card does not carry are supplied rather than left to be worked out**: risk to
  reward from the middle of the entry band, and the move from that midpoint to the latest close.
  A language model asked to divide two prices gets it wrong often enough to matter, and it would be
  wrong *confidently*, inside a verdict.
- **The answer is Arabic; the four token fields are not.** `verdict`, `horizon`, `confidence` and
  `stance` come back as English tokens and the screen prints its own Arabic for them
  (`StockOpinion.Verdict.arabic` and friends). Letting the model answer those in Arabic would put
  the parser at the mercy of its choice of synonym, and a verdict that fails to parse is a verdict
  the card cannot colour. Prices and dates stay in Western digits so a figure the answer names
  matches the one printed beside it.
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
  like an unsearched one. `stock_opinion.md` is schema **2**: news is required output when a
  `SEARCH` block is present, and `news`, `catalysts`, `risks` and `unknowns` are always-present
  lists.
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
- **The model prints no levels of its own, deliberately.** Section 2 forbids an entry, a stop or a
  target: the reader has a call in front of them and asked what to make of it, not for a second set
  of numbers to reconcile with the first. What it may say about price is what it would want to see
  before paying today's, in words.
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
  `AppPreferences`: a model id means nothing on a phone pointed at another provider.
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
- **The updater adds nothing to the background.** The one thing that does is **Schedules** below.

## Schedules

Work this phone does on its own, at a time the user chose. This is the one feature that reverses a
rule the app had held since the beginning — that nothing but `OverdueWorker` runs while the app is
closed. It was built free-first on purpose — the whole machinery shipped carrying nothing but a
price refresh, so alarms, reboots and whatever the phone's battery manager does to a sleeping app
were proved on work that costs nothing before any of it was trusted with work that bills.

**Two switches, and both have to be on.** `schedulesEnabled` runs schedules at all;
`paidSchedulesEnabled` lets one send a paid request, and is what stands between the clock and the
owner's money. `JobRunner.paidWorkAllowed` **defaults to refusing**, which is the point: a paid job
type cannot arm itself by existing, and a caller that wants one has to say so. A paid job whose
switch is off is passed over and says so on its card - it is not hidden, and it is not run.

- **A job is a saved configuration plus a trigger.** `ScheduledJob` in `model/ScheduledJob.kt`:
  a `JobTrigger` (`Once` at a date and time, or `Repeat` over a set of days at a clock time), a
  `JobWork` (`PriceRefresh`, free; `Analysis`, paid), a grace window, and what became of the last
  fire. The work's own settings live in a JSON column rather than columns of their own, so the next
  job type is a new key and not another migration.
- **Device-local, and never synced.** Everything else the app records travels through the sync
  channel; a schedule must not, because three phones keeping one schedule is the same work done
  three times — and once analyses can be scheduled, three times the bill for one answer. Nothing in
  `*Sync.kt` reads `scheduled_jobs`, and the master switch is deliberately **outside**
  `AppPreferences` (which is published) as `SettingsRepository.schedulesEnabled`.
- **Cairo time, always.** A schedule belongs to the exchange, not to wherever the phone is: a user
  who books a run for after the close means after the close in Cairo, and a job that shifted an hour
  when they landed somewhere would read a session that had not happened.
- **`ScheduleClock` is the whole of the timekeeping and has no Android in it**, because a rule about
  what happens at 18:00 next Sunday cannot be checked by waiting for next Sunday. `nextFire` books
  the alarm, `previousFire`/`unservedFire` decide what is owed, and the zone is a parameter only so
  a test can drive it through a daylight-saving gap on purpose.
- **A fire is compared against the fire last served, never against the wall clock.** `lastFiredAt`
  stores the *scheduled* moment, so a run that started 20 minutes late still counts as having filled
  its 18:00 slot — comparing real start times would let one slot fire twice on a phone whose clock
  moved.
- **Grace is what makes a schedule work at all**, default two hours. A phone that was off, in Doze
  or out of signal at the appointed minute is the normal case, and a schedule that fires punctually
  or not at all mostly does not fire. Past the grace the run is recorded as **missed** rather than
  started late. Only the most recent unserved fire is ever considered: a week with the phone off
  comes back owing one run, not seven.
- **AlarmManager is the clock; WorkManager does the work.** WorkManager's delays are a floor and not
  a promise — in Doze "18:00" becomes "some time that evening" — so `JobScheduler` books one exact
  alarm at the earliest fire across every enabled job, and the run that answers it books the next.
  One alarm rather than one per job: only the nearest matters. `setExactAndAllowWhileIdle` where the
  user has granted `SCHEDULE_EXACT_ALARM`, falling back to the inexact form where they have not —
  the app asks rather than declaring `USE_EXACT_ALARM`, which is meant for alarm clocks.
- **Four things mean re-book**, all handled by `ScheduleReceiver`: the alarm firing, a reboot, an
  update replacing the app, and the exact-alarm permission changing. An alarm survives none of the
  last three, and a schedule nobody re-booked has silently stopped keeping time. Re-booking happens
  in the receiver, which needs no network — a phone that boots into a tunnel still comes out with
  its alarm set — while the work goes to WorkManager, which waits for one.
- **`ScheduledJobWorker` goes through `AppState`, which is the opposite of what `OverdueWorker`
  does, and is deliberate.** That worker answers a question out of the database and touches nothing
  else. A scheduled job does the same work a button on screen does, and a second implementation of a
  price refresh would be a second set of rules about what is fetched, what is re-scored and what the
  record then says — one of the two would eventually be wrong, and it would be the one nobody is
  watching. The cost is that waking the process brings the catalog, the stale-price check and a sync
  catch-up with it; none of them is paid.
- **A price refresh skips when one has already happened since its fire came due.** Without it,
  opening the app inside a missed job's grace window fetches every stock twice within seconds, and
  the price feed is a public one the app is a guest on. Hence `lastPriceRefreshAt` beside
  `lastPriceRefreshDay` — the day cannot answer "since this fire".
- **Every path records an outcome, including the ones that do nothing.** Silence is the failure mode
  of every scheduler on this platform: the phone puts the app to sleep, nothing fires, and nothing
  says so. A schedule whose last line reads "Skipped · prices had already been fetched" is
  diagnosable; a blank one on a morning it should have run is not.
- **The UI is a sheet, not a sixth tab.** This app has no back stack — five destinations and modal
  surfaces for everything else — and a screen set up once does not earn permanent navigation weight
  at 411dp. `SchedulesSection` puts the card on **Analyze**, which is where a run is configured and
  therefore where a run-with-a-time-on-it belongs; `SchedulesSettingsSection` puts the same sheet in
  Settings beside the two system permissions that decide whether any of it works.
- **The two system permissions are shown whether or not they are granted.** Exact alarms, and
  Samsung's battery optimization, which puts an app it considers unused to sleep and takes every
  schedule with it. A page that goes quiet once something is right leaves the reader unable to tell
  "granted" from "the app forgot to check".
- **A job from a newer build is kept, shown, and never run.** `JobWork.Unsupported` carries the kind
  **and the raw settings** it could not read, and both are written back unchanged, so a downgrade
  does not lose a schedule and does not rewrite it into something the build that understands it
  would no longer recognise. An `ANALYSIS` row whose settings will not parse lands here too, rather
  than being read as an analysis of no chats — which is a paid request for an empty answer.

### The scheduled analysis

- **`AnalysisPlan` exists because a run can now start from two places.** `analyze()` used to read
  the Analyze screen's fields directly — which chats are ticked, which content types, which target
  date — and a scheduled run has its own answer to every one. Two functions assembling a request
  would have been two sets of rules about what gets sent and what the report then claims to cover,
  and the one that drifted would have been the unattended one. Both paths build a plan and hand it
  to `executeRun`. Its `onScreen` flag changes **nothing about what is run or saved** — only
  whether the reader is thrown onto Results and whether the report they had open is swapped. The
  run state itself is set either way, so the Analyze button shows a scheduled run and cancels it.
- The plan deliberately **does not carry the provider, model or key**. Those follow Settings at the
  moment the run starts: a schedule that pinned a model would go on sending to one the user had
  moved off. The **chats and content types are frozen** when the job is made, for the opposite
  reason — the same reason a position snapshots its levels. Re-ticking chats on Analyze months
  later must not silently re-aim a run that happens while nobody is watching.
- Always the **next session**, never a historical date. A repeating schedule re-reading one fixed
  day would pay for the same answer every week.
- **Four guards, and each is a way of being wrong that costs a real request.** All of them end in
  `JobSkipped` — written down, not charged, tried again at the next fire.
  - **The session flips at 14:30 Cairo.** A fire delayed across that line — by Doze, by a phone that
    was off, by the grace window doing exactly what it is for — would buy an analysis of the
    following day and produce a report that looks entirely ordinary. So the session the fire was
    booked for is compared with the one a run now would cover, and a disagreement stops it. This is
    the subtle one, and `RecommendationDateTest` documents the rule it rests on.
  - **`duplicateOf`**, the same check the Analyze button uses: a report already covering that
    session and those chats means skip, not pay twice.
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
- **A schedule names the chats it covers**, two of them and a count. The whole point of freezing the
  selection is that it stops matching what is ticked on screen, so a row saying only "Analyse the
  next session" cannot be checked without opening it.
- **`blockedReason` is one list, read by the row and the card above it.** They disagreeing is how a
  card promises "Next Sun 07:00" for a job the row underneath reports as blocked, which happened:
  the summary only looked at whether a job was enabled, so a paid job with the paid switch off was
  counted as the next run. The order is what the reader has to fix first — the master switch before
  the job's own, both before anything the work needs. A card listing four problems fixes none.
- **An empty chat list is not evidence the chats have gone.** On a cold start Telegram has not
  loaded, and "its chats are no longer in the app" would be the wrong alarm at the worst moment. It
  is also only raised when **all** of a job's chats have gone: losing one of four leaves a run that
  still reads the other three.
- **Re-aiming is deliberate and never quiet.** `ReaimControl` shows the frozen selection and the
  current one and offers a button naming which it takes. Freezing is right; a selection that can
  never be corrected would mean deleting the job to fix a typo.
- **Defaults differ by work type, and both are about the session.** A price refresh goes at 18:00,
  after the 14:30 close, when there is a settled session to fetch. An analysis goes at 08:00, before
  the 10:00 open, while the levels can still be acted on — the same run at six in the evening is a
  post-mortem. Switching the work type in the editor moves the time and the name **only while they
  are still the ones the form filled in**; what the user typed is theirs.
- The scheduler is in `src/main`, so the **`next` build type inherits it** — its own applicationId
  means its own preferences, where both switches start off. The claim that `next` cannot start an
  analysis even by accident still holds and now rests on a second thing as well: a scheduled run
  refuses without a saved credential, and the API key has never synced.

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
- **The tone is one tinted glyph and never the text.** Colouring the words would make every routine
  confirmation the loudest thing on screen, and this line now sits beside the app's own name, which
  is the last place that should flash. Same rule the toast followed.
- **Wording.** Sentence case, no trailing full stop, an ellipsis only on something still running,
  and `·` only between counts — `Priced 40/42 · 2 unpriced · 1 stale` is what it is for, where
  `Key verified · 8 models` was using it to join a clause to a count. One event gets one wording:
  the chat count is `N chats` from both the launch collector and the Analyze refresh, which used to
  say "loaded" and "found".

## Gotchas

- `local.properties` holds `telegramApiId` / `telegramApiHash` and is gitignored. Absent, the app
  falls back to asking for them, so a fresh checkout still builds.
- `Uri` is stubbed in unit tests; tests that need inputs use `AnalysisInput.Text`.
- `LocalDataStore.DATABASE_VERSION` — bump it and add the table to **both** `onCreate` and
  `onUpgrade`. Currently 21. **Bumping the constant is half of it**: `session_events` was added to
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
  version 20 has one in `SettledCallStoreTest` for `settled_calls`, and version 21 has one in
  `SessionEventStoreTest` for `session_events`
  — added by `ALTER`, one guard per column, so the risk
  is not that the upgrade fails but that it takes the answers already on the phone with it. Note
  Robolectric coexists with the explicit `org.json` test dependency, which was the risk when it
  went in. **Robolectric needs Java 21** to stand up a sandbox for SDK 36 — it refuses on 17 with
  "requires Java 21 (have Java 17)", which is a green run locally on the JBR and a red one anywhere
  pinned lower. CI pins 21 for that reason.
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
- **Two layouts, on `LocalWindowWidth`**, which is the shell's published answer rather than a fourth
  threshold. Wide: one line, a leading `FilterList` glyph, the controls in a **weighted** `FlowRow`
  so an overflow wraps *inside* the shelf instead of pushing Clear off the end — Results needs that,
  its sort chip reads `Run date, newest`, nearly twice the width of the others, and four controls do
  not fit 638dp. Compact: the search box stays out and the rest fold behind a chip, which is the
  pattern Results' in-report toolbar already used, label included. **The search never folds** —
  Results and the Portfolio both carry the same comment, that it is "the control someone arrives at
  the screen already knowing they want", and burying it would contradict the reason it leads.
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
  `alignHeights` stretches both columns to the taller through `IntrinsicSize.Max`; it is off by
  default because it is wrong for the case the helper was built for — a tall main pane would drag a
  short side column's last card down to meet it — and right for a pair, where two cards of equal
  standing ending at two heights reads as one of them having failed to load.
- **Both were hand-built copies of `SectionCard` and are not any more.** That is what let them drift:
  same container and shape, and then one tinting its icon `primary` and the other leaving the
  calendar untinted, each spelling its own header row and divider. Drawing the background twice is
  how two cards meant to match stop matching. The **"Change date" button is gone** with them — the
  "Specific date" row has always opened the picker itself, so the button was a second control doing
  one job, and it was the reason that card changed height the instant the mode changed, which is the
  one thing a card sitting beside another must not do. The affordance moved into the line already
  there: the date, then `· tap to change`.
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
