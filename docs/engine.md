## What a run sends, and what it does not send twice

Everything here is about what leaves the phone, and none of it changes a rule about what a call
means. Three things decide what a run costs: how many pixels each card is sent at, how many times
the prompt is repeated, and how many cards are sent at all.

- **A card is fetched at the smallest size that is still legible, not the largest.** Telegram offers
  the same photo at several sizes and `appendMessage` took `maxByOrNull { width * height }`, which
  for a channel card is usually 2560 on the long edge where 1280 sits beside it. A vision model is
  billed by **pixel area**, so that one call was paying about four times over for a card that reads
  the same either way, on every image in every run. `preferredPhotoSize` takes the smallest size at
  or above `LEGIBLE_LONG_EDGE` (1280, Telegram's own `y` and what most channels post at to begin
  with), and the largest where none reaches it — nothing is ever resized here, and a card that
  arrives small arrives small. It also cuts the download, and it changes nothing else: the file-path
  dedupe, the IMAGE_REF numbering and the traces are all untouched.
- **A message a run has already read is not sent again.** `resolveAnalysisWindow` starts at
  yesterday's opening hour, so a midday schedule covers every message the morning one covered, and
  re-reading a card the model has already read is the one cost in the path nobody chose. Each run
  writes down what it read of each source, in `analyses.source_reads`; the next run adopts those
  rows and sends only what nobody has read yet. `AnalysisDiagnostics.reusedSources` is the figure on
  the report card that says why its requests and images are fewer than its sources.
- **The reading is keyed by the question it answers** — the prompt version, the model and the notes
  language, since a new prompt reads the same card by different rules and a different language
  answers in different words. Change any of them and nothing is reused, which is right rather than
  wasteful: it is a different question.
- **The numbering is a separate question from the chunking, and `ExtractionPlan` is where it is
  answered.** `IMAGE_REF n` resolves to entry `n - 1` of `imagePaths`, which is every image the run
  carries — so references are assigned over all of them while the chunks are built over only the
  ones being sent. Folding the two together would renumber every card after a skipped one and hand
  it somebody else's picture, in a report that reads perfectly well. Pure, beside
  `AnalysisChunking`, and `ExtractionPlanTest` is most of what stands behind this feature: the test
  that matters is that a reused source keeps its number and the card after it is still image 3.
- **Refs are stored per source, never per run.** `IMAGE_REF` is a position in one request, so the
  same card can be image 3 one morning and image 11 the next. A stored reading numbers each row
  against its own source's images — a fact about the message — and `SourceReadings.lay` puts it back
  onto whatever numbering the new run gives it. Anything that does not line up exactly is refused
  and simply sent again: a refusal costs a request, and getting it wrong would file one channel's
  levels under another channel's card in a report that looks entirely ordinary. That asymmetry is
  why most of `SourceReadingsTest` is about the readings that must be **refused**.
- **A reading is written only from a chunk that accounted for every image it was given**, and never
  from one whose answer cites a `TELEGRAM_ID` the request did not carry. An answer that lost track
  of one card says nothing dependable about the cards beside it.
- **It is stored on the analyses row, not in a table of its own**, so deleting a report takes its
  readings with it — which is what makes *delete the report and run it again* the way to have a
  misread card read afresh. Device-local and never synced, like `price_events`: it is a cache of one
  device's working rather than a record of what anybody recommended, and it is deliberately outside
  `AnalysisResult.toJson`, so it never travels through the sync channel or into a backup payload.
  A run also carries forward the readings it reused, so a reading survives as long as any run that
  covered the message does.
- **A retry sends only the messages it has to.** A chunk that leaves an IMAGE_REF out of both
  `extracted` and `excluded` used to be sent again whole — eight images to re-read one — and now
  only the messages holding the missing images go back. A message is still never split, so a card's
  caption returns with it. The second reading **replaces the first for those messages alone**, which
  is what stops a chunk that merely forgot one image contributing every other card twice, and it is
  taken only where it accounted for more of them than the first did: an answer that came back worse
  must not take with it the cards the first one did read.
- **The correction echo is 4,000 characters, not 12,000.** A consolidation correction already
  carries every extracted occurrence; the echo is context for the correction rather than the
  material for it, and on a busy session twelve thousand characters was a second copy of the whole
  document at the model's own prices.
- **The system prompt is marked for caching, on OpenRouter and only on OpenRouter.** Every chunk
  sends the same seventeen kilobytes one request after another, which is exactly what a prefix cache
  is for. It changes what the repeats are **billed** at and not what they **count** — the tokens
  still arrive in the usage block and are still added up on the card — so it is worth saying out
  loud rather than being read as a saving that failed to show. Qwen caches a repeated prefix without
  being asked, and an unknown key is rejected outright by some OpenAI-compatible gateways, which
  would fail the request rather than only the caching.
- **The flat "prioritize these phrases" line is gone from a generated prompt.** `PromptComposer` has
  already placed those phrases at the sections that decide the things they are about, and restating
  them at the end was the same instruction in two vocabularies — which is how the two come to
  disagree. It still goes out on the fallback path, where the shipped prompt has no slots filled in.

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

### The two questions the record used to ask itself

`RecordSplit` set one subset of the record beside the rest of it — calls several sources named for
one session against calls only one named, and calls a source kept re-posting against calls it posted
once — under **"Does it matter?"** on Insights. The section and the arithmetic behind it are both
gone. At the ten to thirty judged calls each side ever carried, the spread of stock returns swamped
the gap between two means, so the honest version of the section was one a reader opened to be told
that two figures differ by less than can be read; the dishonest version would have been "consensus
calls do better", which is reading noise out loud. Nothing replaced it, and `PerformanceReport` no
longer computes a split for a screen that would not print it.

- **What detected it stays, because a call card says it.** `ScoredCall.alsoCalledBy` counts the
  **other** sources, not all of them: a card saying "1 source" about itself is a card counting
  itself as company. `repostings` is zero on a repeat, because the figure belongs to the call that
  was kept standing rather than to the standing. Both are still filled in by `enrich` and still
  printed on the card — as facts about a call, which is all they ever were.

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
- **What is left of it is one line**, in `Data and backup → Prices` and only when something is actually
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
  screen — the line in `Data and backup → Prices` is derived from `priceHealth` on every recompute exactly
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

### The first run, for somebody who did not build this

`SetupCard`, at the top of Analyze, since 2026-09-12. It lists the four things a run needs, ticks
the ones this phone has done, and goes away for good once there is a report on the device.

- **Every one of these was already enforced and none of them was ever stated together.**
  `analyzeBlocker` returns the *first* thing stopping a run, and each card draws its own — which is
  the right arrangement once somebody knows the app, because the complaint sits with the control
  that answers it. For a first run it is four round trips: press the button, be told about the key,
  press again, be told about the model, press again, the content types, press again, the sources.
  Each is discovered only by pressing a button that then refuses, and nothing said what the four
  were. That was tolerable while the only user had built the thing; see **going multi-user**.
- **It states and it never does.** No key field, no chat picker, no model list on this card: a
  second way to do something is a second thing to keep in step with the first. The one action it
  offers is *Open Settings*, and only while a step that lives there is actually outstanding.
- **`setupSteps` reads the same state `analyzeBlocker` reads.** Two lists of what a run requires,
  kept in two places, is one list that quietly stops matching what the button enforces.
- **Three things, not four, on a fresh install** — `CloudConfiguration` ships with its provider's
  default model, so that row arrives ticked. The row stays in the list because the blank it guards
  against is reachable: clearing the field is what `NO_MODEL` exists for. `SetupCardTest` asserts
  the three so a change that starts counting four is caught saying so.
- **Dismissed on `PageState`, not in the composition**, session-only — the rule the stock box was
  written in blood for. A card that came back on every fold could not be dismissed at all.

