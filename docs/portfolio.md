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
  light/dark setting rather than the system's. Note `primary` is no longer the app's voice on every
  page — see **A hue per page** below. The overdue pill keeps `errorContainer`: amber says
  out of time, red says and you are late.
- **Positions is one card, holding every session card inside it.** It was a loose `titleLarge`
  heading, then a filter shelf, then a run of cards, all sitting directly on the page — a section
  with no edge of its own to say where it began or ended, which is how the shelf's fill came to
  read as continuous with the card beneath it. A `SectionCard` gives it one. The filters left for
  the header's sheet on 2026-09-09 and took the last of that argument with them; what survives of
  it is the sheet's title, **Filter positions**, which is how a control reached from the page
  header still says it narrows this card and not the record above it. The heading drops from `titleLarge` to the `titleMedium` every other card on
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

