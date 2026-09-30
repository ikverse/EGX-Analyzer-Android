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
  buried. **Correcting a call's ticker or its session deletes that call's opinion too**, for a
  sharper reason than orphaning: the answer is about the other company. See **Correcting a misread
  call**. `ScoredCall.requestId` exists for this and only this. `deleteResult(id)` has to read the
  request id back *before* the row goes — the opinions are keyed on the request id, and doing it the
  other way round is a cascade that deletes nothing while looking correct, which
  `StockOpinionStoreTest` covers on both paths.
- **Never synced.** Everything else that travels is a record of what happened; this is one model's
  answer to one question at one moment, and the cheapest way for another device to have it is to ask
  there.
- A stored row whose verdict this build cannot read is **dropped rather than defaulted**: a card
  colouring an answer it could not read would be inventing one.

## A report's calls on screen

A table above `TableMinWidth` (600dp of container) and `RecommendationCards` below it. The cards
were always right; the table was rebuilt on 2026-09-09 because it did not fit any screen the app
runs on, and fitted the bigger ones worst.

- **The width a row came to was a function of nothing but how many columns had been added.** Sixteen
  fixed `Dp` widths behind a horizontal scroll: 889dp of them at the Fold's **614dp** of container,
  1277dp at the tablet's **682dp**, because crossing `ContextMinWidth` (620dp) *appended* 388dp of
  context columns to buy 68dp of viewport. So the tablet showed 49% of a row where the smaller Fold
  showed 64% — **the bigger screen truncating harder than the small one**, which is the mistake the
  `TodayCard` tile grid already records against itself, made a second time in a second place. The
  container is 614 / 682 / 715dp on the Fold, the tablet and the emulator: the rail's 80dp, the
  page's `Space.l` either side and the report card's `Space.m` either side are already spent before
  the table is measured, which is why it measures itself with `BoxWithConstraints` rather than
  asking the window.
- **Three things got the row under the width it has.** A target and its return are **one cell**,
  price over percent — they are read together and were 162dp of two fixed columns apiece, so three
  weighted cells stand where six columns and 486dp of them did. **Timing rides the source** as a chip under the channel's name,
  which is what killed the 96dp column whose two words wrapped and took the row's height with them.
  And **the source image left the table**: a press opens `OccurrenceSheet`, which already draws the
  screenshot at a size worth looking at, so the 72dp thumbnail was a column spent restating that a
  press was available.
- **Only `SourceWidth` and `ChevronWidth` are fixed now; every figure column is weighted.** That is
  the whole of the fix and it is a property rather than a number: a wider window widens the columns
  instead of adding more of them, so no width can ever again show less of a row than a narrower one.
- **`RiskColumnMinWidth` (656dp) adds a column, and it is deliberately not the old mistake.** Risk to
  reward is on **every row at every width** — the entry cell's second line below it, its own column
  with a proportion bar above it — so nothing appears or disappears as a window changes size. Extra
  width buys the same row more room to say what it was already saying, and never a figure the
  narrower screen was denied. The Fold's 614dp lands under the threshold and the tablet's 682dp over
  it, which is the intended split. It is also the figure the table never carried at all: it is on
  the recommendation card and in the occurrence sheet, and it is the context a target cannot be read
  without — 90% at 0.3 to 1 is a losing source.
- **Context is a toggle, not a breakpoint**, and it draws **one muted line under the row** rather
  than four columns. Support, resistance and the two dates are true of a call and are not what
  anyone judges it by — the old column list said so itself, directly above them — so appearing
  because the screen got wider was the one arrangement that could not be right: the reader who wants
  them could not ask, and the reader who does not got them at the cost of the figures that decide
  something. A line rather than columns, so asking for them can never put the table back into a
  sideways scroll. Session-only and per report, like the toolbar's own filters, and drawn only
  beside the table — the cards carry every figure already.
- **A stock is a rounded block, not a band.** `surfaceContainerHigh` inside the report card's
  `surfaceContainer`, the one-step-up rule the Portfolio's session cards follow. It was a full-bleed
  `surfaceContainerHighest` strip under a **2dp** rule, the heaviest divider in the app.
- **One grid device, not three.** The vertical rule after the first column and the `HorizontalDivider`
  under every row are gone, and the stripe is raised from `surfaceContainerLowest` at 0.4 — nearly
  invisible on dark, which is exactly why the other two were needed — to `surfaceContainer` at 0.55.
- **The Watch list chip takes the page's own hue.** It was `tertiaryContainer`, which is the family
  `PriceRole.target` is drawn from, so a status chip was wearing the colour that means *a target
  price* on every other surface in the app. An accent is chrome and a signal is a figure; this is
  the first. **The timing chip is neutral for every timing** for the same reason and it is worth
  saying out loud, because a hue per timing is the obvious next idea: market blue is the one that
  suggests itself, and it means *a price the market reached*.
- **The row height is a minimum, not a fixed height.** Every cell is held to one line per figure, so
  at any one font scale the rows are the same height and a column reads down — while a large scale
  grows them all rather than clipping any. The old table wrapped in two columns at anything above
  the default scale, and a wrapped row stood half again as tall as the one above it, which is most
  of what the owner was looking at when they said it was hard to read.
- **The toolbar is still pinned and the pinning is all that survives of the scroll machinery.** It
  translates by how far the table's top has passed the viewport's, clamped inside the table's own
  height, so it never hangs over the next card. It carries the report card's own fill because that
  is what it slides across.

### The sheet a row opens

`OccurrenceSheet` is the longer version of a table row, and until 2026-09-11 it was the last
surface in the app still built as one column of dividers - the shape `StockSheet` left behind. It
is a **header, a scroller and an action bar** now, on that sheet's own terms.

- **It had no scroller at all**, alone among the seven sheets here, and that is a bug rather than a
  look: a call carrying all six levels, a long Arabic note, or an ordinary one at a large font
  scale ran off the bottom of the screen with no way to reach it. `sheetDragSlop().scrollableColumn()`
  is the pair every other sheet carries, and it also answers the pull-at-the-top problem in the
  same breath.
- **The identity band is fixed and the record scrolls under it** - logo, ticker, EGX 33 mark, both
  names on one line, and the `⋮`. The ticker opens `StockSheet` through `LocalOpenStock`, which is
  the rule every other full-width card naming a stock follows.
- **The channel is drawn at all for the first time.** It has been a parameter of this function
  since it was written and reached the screen nowhere, so two occurrences of one stock in one
  report - which differ by nothing else - were told apart by nothing.
- **`Edit` moved into the `⋮`.** A violet text button in the top-right corner was the loudest thing
  on a sheet whose subject is a set of figures. It is `CallMenu`, the recommendation card's own
  menu, which also brings **Copy call** to a surface that had no way of getting a call's numbers
  out of the app. The `Edited` pill still opens the editor directly, and it sits beside the timing
  pill on the header's own row - the shape both call cards were given the same day.
- **The figures are `LevelGrid` and no longer a `FlowRow`.** Six tiles flowed at whatever width
  their numbers happened to print, which is why Resistance sat alone on a line under the other
  five. This reverses `LevelGrid`'s own note that the sheet prints left to right in its own order:
  that order is right for a table and an export, which are read down a column, and a sheet is the
  one surface where a single call is the whole subject.
- **`entryText` and the implied return are shared rather than copied.** The sheet kept its own
  `entryText` and read `returnTp1Pct` straight, so a target the channel had not put a percentage
  against showed a bare price here and a computed one on the card - two readings of one call,
  differing on nothing but whether the source happened to print the figure.
- **The peak is a figure and not only the ladder's arrow.** `peakSince` has been passed to this
  sheet since it was written and drawn only as a mark on a scale; the header names it, measured
  against the entry midpoint, which is the basis the scorer measures every return from.
- **The source is a section and not a footer.** The screenshot is the evidence every figure above
  it rests on and was drawn at 88dp under a run-on line whose loudest content was a nineteen-digit
  Telegram message id. It is 104dp beside what the model quoted, the card's printed date and the
  target date are two labelled facts rather than one joined line, and the ids drop to a monospace
  last line - they are for checking the app against a report, not for reading a call.
- **`SheetSection` and `SheetSectionLabel` moved to `CommonUi.kt`**, from `StockSheet.kt` where
  they were private. Two sheets draw that card now, and a card shape spelled twice is two that
  agree until one is adjusted. `EditCallSheet` keeps its own two-argument `SheetSection`, which is
  a different signature and deliberately untouched.

## Correcting a misread call

The model reads tickers and levels off channel screenshots and gets one wrong from time to time — a
transposed code, a decimal in the wrong place, a stop picked up from the card above. The only remedy
used to be deleting the report and paying to run it again, which throws away every other call in it
to fix one. `Edit call`, in the ⋮ the recommendation card already carries, is that remedy.

- **It is an overlay and deliberately not a rewrite**, and that follows from a fact easy to miss: a
  report's stocks are **not stored**. They are re-parsed from the model's `rawResponse` every time
  the report is read, so an edit written into the parsed object vanishes on the next read, and an
  edit written into `rawResponse` destroys the one record of what the model actually said.
  `RecommendationEdits.apply` runs straight after `ConsolidatedParser.parse` in
  `LocalDataStore.toAnalysisResult`, which buys three things at once: every screen picks the
  correction up with **no change of its own** — the cards, the table, the spreadsheet export, every
  rate on Insights, the alerts and which tickers the price feed is asked about all derive from that
  one list — the model's answer survives, so undoing is always available, and deleting the report
  takes its corrections with it, because they live in its payload.
- **In the payload, not a table of its own**, so there is no schema bump and no migration — and the
  payload is edited **in place as JSON** rather than decoded and written out again, so every key a
  later version added survives the write. Only two of them move: the list of corrections, and
  `editRevision`.
- **Anchored on the parse, not on the screen.** `originalStockCode` is the code the model read — not
  the corrected one, which is the thing being changed, and filing under it would mean a correction
  could never be found again to be undone — and `pointIndex` is the occurrence's position in that
  stock's *parsed* list. The screen filters occurrences by timing and by channel before drawing
  them, so the index a card sits at is a fact about the filter; `RecommendationDataPoint.parseIndex`
  carries the right one onto the card, assigned after every drop the parse makes.
- **A fingerprint decides whether it still fits.** A newer prompt can return a different reading of
  the same response, and the slot an edit was filed against may now hold another occurrence
  entirely. `editFingerprint` is checked before the overlay is applied, and a mismatch **drops the
  edit** rather than applying it blind — the call reads as the model left it, which is visible and
  recoverable, where a silently rewritten one is neither. It is checked a second time in
  `CallEditor.editFor`, so the `Edited` chip can never mark a card that is showing the model's own
  figures.
- **Emptying a field and saying nothing about it are different acts.** `EditField` in `cleared` is
  the first; a null is the second. The model inventing a target that is not on the card is as common
  as it misreading one, so without the distinction the reader could only ever change a figure and
  never delete one. Clearing both halves of the entry band takes `buy_price` with it, or a single
  price the model returned survives through `buyPriceLow ?: buyPrice` and the deletion looks as if
  it failed.
- **Names are re-derived, never carried.** The parse names a stock through `EgxCatalog.namesFor`, so
  a correction that moved the code and kept the names would print the right ticker over the wrong
  company — a card that agrees with itself and is wrong, which is worse than either mistake alone.
  `model` may not reach `data`, so the catalog is passed in as a lambda.
- **The percentages are recomputed** through `returnFrom`, the same basis the scorer measures a
  return from. The model returns them and both the card and the spreadsheet print what is stored, so
  a corrected target beside an uncorrected percentage would be two numbers on one card that
  contradict each other.

### What a correction has to be followed through by hand

Everything derived from the extraction comes right on the next recompute. `editRecommendation` in
`LiveAppState` exists for the four things that are **copies taken at some earlier moment**.

- **The AI opinion is deleted rather than re-filed.** `opinionId` is ticker, session and channel, so
  a corrected ticker orphans it — but re-keying it would be worse than losing it. The answer is
  about the wrong company: it read that company's news, rated that company's levels and forecast
  that company's next three months. It is wrong, not misfiled.
- **A trade is re-keyed, and only when the reader asks.** `positionId` is derived from the ticker and
  the session, so a corrected ticker cannot leave the trade where it is: the new row is written and
  the old one **buried**, the way every removal already travels, and `position_status_seen` and
  `position_approach_seen` are dropped because both are keyed on the id that just moved. Behind an
  explicit checkbox because it is the only part of this that touches money, and the reader may have
  bought the stock the model named rather than the one on the card. It also brings the trade's
  **copied levels** up to date — a trade snapshots them so that re-running an analysis cannot move a
  trade already taken, and a correction is the one case that rule was not written for.
- **The stored reading is dropped**, and this is the root rather than a symptom. A run writes down
  what it read of each message so the next one need not pay to read it again, and that reading still
  carries the wrong ticker; left alone, tomorrow's run adopts the same misread **for free** and puts
  it back into a fresh report with nothing on screen to say why. Only the message the occurrence was
  read out of, and every source carrying that message — a caption and the photos under it are one
  card, and forgetting one of them would have the next run adopt the mistake from the source beside
  it.
- **Prices are refreshed**, so the corrected ticker has a history to be scored on. Free: the same
  public feed the Fetch prices button reads. Silent, because the reader asked for a correction
  rather than for a fetch.

**Frozen verdicts need nothing, and pruning them would be wrong.** `settledKey` is a fingerprint of
the ticker, the levels and the window, so a corrected call asks under a key that has never been
written and is scored from scratch; the row left behind names a call nothing will ask about again.
Clearing by ticker — the only cheap way to find it — would take the verdicts of every *other* call
on that stock with it.

**One consequence is named and not prevented.** `PerformanceCalculator.callsByChannel` keeps one
call per stock per channel and drops the rest, so correcting a code to one already in the report
from the same chat quietly costs a call on Insights. Both readings may genuinely be right and
merging them would invent a call neither channel made — so `CallEditor.clashFor` says so in the
sheet instead, above the Save button, with every other consequence.

### Where it is reached from

- **The ⋮ on the recommendation card**, under `Copy call`, plus `Undo all edits` once the report
  carries any. Not the occurrence sheet alone: that sheet opens from the **table**, and the table is
  not drawn at all below 600dp of container — which is every phone in portrait, and so most of the
  time this is used. The sheet has its own entrance to the same `CallEditor`.
- **The consequences are stated before the press, not after it.** Deleting an opinion and re-keying
  a trade are not things to discover afterwards, so the block above the buttons names them and the
  trade is named by the price and the day it was bought on.
- **The ticker is a picker over `EgxCatalog.entries()`**, searched through `StockSearch` across the
  code and both names, filling both names when a row is chosen. Free text is still accepted — a
  listing this build's catalog has never heard of is a real thing and refusing it would make a new
  listing uncorrectable — but it says so, because an unknown code is also what a typo looks like and
  it will not price. `AppState.stockCatalog` is how it reaches `ui`, which imports nothing from
  `data`.
- **Every changed field shows what the model read underneath it**, in the muted grey a derived figure
  already wears, with a press that puts that one field back. That is also the "what changed" view;
  there is no second panel for it.
- **The session a whole report is for is not editable.** The occurrence's own `date` is, and usually
  decides nothing — a call is dated by the session the run was aimed at and this is only read where
  that is absent. Moving the report's own target date would move every call in it at once, which is
  a different and much larger act.

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
- Every column is written, including the notes and the context the table keeps a press or a toggle
  away: a sheet has no width to run out of, which is the one thing the screen does. The **source image column is not exported** — a picture in a
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

**Settings → Data and backup → Diagnostics → Save diagnostics** copies `egx_analyzer.db` into Downloads, from where
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
- **The crash log goes with it, when there is one** — `egx-crashes-<date>.txt` beside the `.db`, and
  no second file on a phone that has not crashed. See below.

### What the app was doing when it died

Nothing logged anything here until 2026-09-12 — not a `Log.e`, not a handler — so a crash left the
process and took the only account of itself with it. On this machine that costs a `logcat`; on
somebody else's phone it costs the whole report, because *"it closed itself"* is all they can say
and all that can be asked of them. `data/CrashLog.kt` is the file that puts the record on the device
before anybody tries to read one off it.

- **Installed in `EgxApplication.onCreate`, ahead of everything.** Before the database, the
  scheduler and Telegram, none of which it needs — a crash during a process's first launch is the
  one most worth having and the one a handler installed any later would miss.
- **A plain file in `filesDir`, never the database.** The database can itself be the reason the app
  is dying, SQLite in a dying process is the last place to ask for a write, and the record is synced
  and backed up — a crash is a fact about one phone, and shipping it to every other device is the
  opposite of what this is for. It stays out of the backup by construction: `writeBackupTo` takes a
  named database and a settings document rather than sweeping a directory.
- **The handler does as little as it can**: one read, one write, no coroutines, no `AppState`, all
  of it inside `runCatching`, and then it hands the throwable to whatever handler was already there
  so Android still shows its dialog and still ends the process. A crash logger that throws replaces
  the exception the user actually hit with its own; one that swallows leaves a dead app on screen
  looking alive. `CrashLogTest` holds both of those, the second with a throwable whose own
  `printStackTrace` fails.
- **Newest first, twenty entries and 64 KB, whichever comes first.** Either budget alone has a hole:
  one enormous stack trace would spend the lot, and twenty small ones is twenty nobody reads. A
  phone crashing in a loop must not fill its own storage saying so. An entry larger than the whole
  budget is kept **whole and alone** — a trace cut at a byte offset reads as a crash inside the
  logger rather than as a trim.
- **The line in Settings is what makes the button get pressed.** A crash log nobody knows about is a
  crash log nobody sends, and the reader will not think to look: the app reappeared, so as far as
  they know it recovered. `Closed unexpectedly <when> · v<version>` in the error colour above *Save
  diagnostics*, only where there is something to say, with **Forget** beside it.

## Backing up, and getting it back

**Settings → Data and backup → Backup** holds three buttons: *Back up now*, *Choose a folder*, and
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
- **A correction in a backup counts as something this device is missing.** `runsToRestore` compares
  `editRevision` rather than only the id, so a backup carrying a newer correction of a report this
  device already holds is taken — both sides holding the report by id alone would have left it in
  the file for ever, which is exactly the case someone opens a backup to fix. A revision at or below
  the one held is never taken, so this cannot roll a correction back and the rule above is intact.
- **A report this device buried but has not yet published is not restored either.** That delete is a
  decision already taken and still in flight, and restoring over it would leave a tombstone about to
  be published for a report sitting on disk again.
- **The decisions are pure functions in `BackupRestore.kt`** — `rulesToRestore`,
  `positionsToRestore`, `runsToRestore`, `promptVersionsToRestore` — exactly as `syncActions` and
  `rulesToUpload` are, so they are tested as ordinary Kotlin. `LiveAppState.restoreFromBackup` applies them and
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

## What a stock is called

Every name on every screen comes from `EgxSeedStocks`, a compiled-in table of all 232 listings with
an English and an Arabic name each. **No name anywhere comes from the model.** That is the rule the
whole of this exists to hold, and it was broken in three separate places at once.

- **The catalog was applied to a list no screen reads.** `EgxCatalog.enrich` ran over
  `AnalysisResult.recommendations`, while the cards, the portfolio and the scorer all read
  `consolidated` — the model's raw JSON, which never went near the catalog. So AMOC was "Alexandria
  Mineral Oils" in one report and "Amouk" in the next with the catalog sitting there holding its
  name. The naming happens in `ConsolidatedParser.parse` now, which is **re-run from the stored
  response every time a report is loaded**, so reports saved before this pick the names up with no
  migration and no rerun. `LocalDataStore` names the flat list and the positions on read for the
  same reason: a trade recorded months ago stored whatever the model called the stock that day.
- **The download had no Arabic at all and erased what did.** The catalog endpoint returns symbol,
  name and sector — no Arabic name and no aliases — and the merge replaced whole entries, so the
  first refresh after install wiped the Arabic name of every seeded stock and COMI's "CIB" alias
  with it. That is how a device ends up holding 223 stocks and not one Arabic name, which is what
  the owner's diagnostics showed. `merge` folds field by field: a download can add a listing or fill
  a blank, and can never empty something already there.
- **A ticker the table does not hold gets no name, not the model's.** This is the root fix and it is
  a property of `enrich` rather than of how complete the table happens to be — a company listed
  tomorrow shows a bare ticker until its row is written, and can never show a name that moves
  between runs while it waits. The run records the unknown ticker in its **diagnostics notes, not
  its validation warnings**: a warning there buys a paid correction request, and there is nothing
  for the model to correct. Silence is why this went unnoticed for months — QNBA was in report after
  report under a different invented name each time, once as "National Bank of **Kuwait** - Egypt",
  and nothing anywhere said the catalog had never heard of it.
- **Arabic is sourced, not read off a screenshot, and that is the second thing that was tried.**
  Freezing the most frequent reading across the owner's own runs stops the drift and does not make a
  wrong reading right: it elected "آراب ديربي" for a dairy company, a mangled string for GPIM, and
  67 of its 114 entries won a split vote. Every one was thrown away and re-sourced. Arabic is the
  line the cards print first, so it is the line that had to be right.
- **Nine listings the endpoint does not return are shipped anyway**, each confirmed a real listing
  by its own price history rather than by the model saying so. QNB Alahli is among them, which is
  the measure of how incomplete that endpoint is.
- **Two of those are second codes rather than second companies.** EFHI's closes match EFIH's on all
  256 sessions the two share, NAKH's match KRDI's on all 18 — so they are the model mistyping a
  ticker, and the price feed served them anyway. They are **named after the company they are a code
  for** rather than left blank, because they turn up in reports and a bare ticker tells the reader
  less than the company's own name does. The codes stay separate: merging them changes what the
  scorer counts, which is a different decision from what a card is called.
- **Compiled in rather than shipped as an asset**, for the reason `StockLogos` is a `when` over
  literals: an entry that fails to load is an entry that silently loses its name, and with the model
  fallback gone there is nothing behind it. Regenerated by hand, not on a build — see
  `EgxSeedStocks`' own header for where each column came from.
- `EgxCatalog.enrichmentEnabled` mirrors the preference, and it is **held on the catalog** because
  the two new callers — the parser, which rebuilds every saved report, and the position reader —
  have no preferences to hand. `LiveAppState` sets it from every path that can change it.

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

- **A run's extraction is append-only; what the reader has corrected in it is not.** Syncing used
  to be a plain union on exactly that reasoning — a saved run never changes, so the only question
  was who was missing it. Correcting a misread call made a stored run mutable for the first time,
  so reports now carry an `editRevision` and the newest one wins: a report both sides hold at
  different revisions travels from whichever holds the newer one, and equal revisions still move
  nothing, which keeps the ordinary case free. A **delete outranks a correction**, or a higher
  revision would drag a buried report back.
- **The revision is in the file name, so deciding costs no downloads.** `<requestId>.json` for a
  report nobody has corrected — the name every report already in a channel was uploaded under, so
  nothing had to be moved — and `<requestId>-r<n>.json` once it has been. `SyncedRun.requestIdOf`
  strips the suffix, so every revision of one report is recognised as that report rather than as
  several strangers. A request id is a UUID and carries plain hyphens, which is why the suffix is
  `-r<digits>` from the **last** mark rather than anything a hyphen alone could match.
- **`adoptResult` overwrites only on a strictly higher revision**, and only the payload:
  `source_reads` is this device's own cache of what it read out of each message, keyed by the
  prompt rather than by the report, and is nobody else's to replace. Burying a report deletes
  **every** revision of it in the channel — leaving an earlier copy behind would let another device
  download it and bring the report back under the tombstone's nose.
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

Two checkboxes and one sweep at the close, all free. This is the one area that reverses a rule the
app had held since the beginning — that nothing but `OverdueWorker` runs while the app is closed.

It used to include a fourth thing: up to four **paid** analysis schedules, each a time and a set of
weekdays, added on 2026-08-30 to replace an even earlier general scheduler nobody could get through.
That paid feature was removed entirely on 2026-09-12, on the owner's decision — it had never run for
real (proving it meant spending real credits, which was always the owner's to trigger) and its own
moving parts were most of what this whole area cost to build and maintain. `AnalysisSchedule`,
`JobRunner`, `ScheduledRun`, `ScheduledRunService`, `ui/SchedulesSection.kt`, `paidSchedulesEnabled`
and the schedule editor in Settings are gone. What is left below is exactly the three free things
that shared its alarm, none of which needed any of that machinery in the first place.

- **Keeping prices fresh** — `model/MarketRefresh.kt`, switched on in Settings under Data and backup →
  Prices.
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
  window**, unlike the refresh above: a refresh slot that is late has been superseded fifteen
  minutes later, while this fire has no successor for a day, so a phone that was asleep at 14:45
  still owes it at nine that evening.
- **Keeping the sessions the feed forgets** — `model/SeriesHarvest.kt`, switched on beside the
  refresh above. One fire a trading day at 14:45 Cairo, which copies every priced stock's
  five-minute bars into an archive of its own. Free, the same public feed, off by default. See
  **Keeping the sessions the feed forgets** below.

**The window runs to 14:45, a quarter of an hour past the close.** The exchange stops at 14:30 and
the day's figures settle over the minutes after it, so a window ending on the bell stores a session
that is very nearly but not quite final.

- **Cairo time, always.** These belong to the exchange, not to wherever the phone is: a fetch fired
  for the open means the open in Cairo, and one that shifted an hour when the phone landed
  somewhere would read a session that had not happened.
- **`ScheduleClock` and `MarketRefresh` have no Android in them**, because a rule about what
  happens at 10:00 next Sunday cannot be checked by waiting for next Sunday. The zone is a
  parameter only so a test can drive it through a daylight-saving gap on purpose.
- **A price refresh skips when one has already happened since its fire came due.** Without it,
  opening the app inside a missed slot's window fetches every stock twice within seconds.
  Hence `lastPriceRefreshAt` beside `lastPriceRefreshDay` — the day cannot answer "since this fire".
- **AlarmManager is the clock; WorkManager does the work.** WorkManager's delays are a floor and
  not a promise — in Doze a fifteen-minute period becomes whenever the system next feels like it —
  so `JobScheduler` books one exact alarm at the earliest of the three next fires — a refresh slot,
  the sweep at the close, or the archive harvest — and the run that answers it books the next. One
  alarm rather than one each: only the nearest matters.
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
- **Device-local, and never synced.** Everything else the app records travels through the sync
  channel; these must not, because three phones keeping one schedule is the same work done three
  times. Both switches live in `SettingsRepository` rather than `AppPreferences`, which is
  published.
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
  Drawn by `SystemPermissions` in `ui/PricesSection.kt`.

### Keeping the sessions the feed forgets

Every other price in this app is read to answer a question, and only what the answer needed is
stored. This copies the whole five-minute session and keeps it, because **the feed will not**: it
serves five-minute bars for about two months (`IntradayRepository.RETENTION_DAYS`, 59, measured) and
then they are gone, from everyone, permanently. That is the one fact the whole design rests on — a
session not copied inside that window cannot be recovered by any later request, which is why this is
a fire on a clock rather than something a screen does when asked. A record that only grows while
somebody remembers to open the app has holes exactly where the phone was busy, and the holes cannot
be filled in afterwards.

- **Nothing in the app reads it.** No figure, no rate, no verdict rests on a row here, and switching
  it off changes nothing on any screen. It exists so the record can be asked questions nobody has
  thought of yet, and saying that plainly is what keeps it from acquiring a reader later and
  becoming load-bearing without anyone deciding it should.
- **Its own database file, `egx_price_series.db`, and that is the design rather than a detail.**
  `egx_analyzer.db` is zipped whole into every backup, seven of which are kept, and copied whole by
  Save diagnostics. This table is an order of magnitude larger than everything in that file put
  together — **measured**: 61 bytes a row `WITHOUT ROWID` against 122 with a rowid and a
  `session_date` index, so 104 stocks × 54 bars × 250 sessions is 86 MB a year against the 6.4 MB
  the whole record occupies after two. A table inside it would have turned a daily backup into a
  daily 86 MB write to somebody's cloud folder. A separate file is excluded from the backup and from
  diagnostics **by construction** — no flag to set, nothing to remember, and no way for a later
  change to either of them to quietly start carrying it. `Backup.kt` is untouched by this feature,
  which is the point.
- **The trade is stated on the switch, not only here**: what is in this archive is not in a backup,
  so a lost phone loses it. Survivable in a way the alternative is not, because none of it is
  evidence — losing it costs a research archive rather than the record of what anybody recommended.
  Save price series is how a user keeps a copy of their own.
- **`WITHOUT ROWID`, keyed `(ticker, bar_at)`, and no `session_date` column.** The key is the whole
  of a row's identity and every read is by it, so the hidden rowid and its second b-tree are pure
  overhead; the date is `bar_at` read in UTC, derivable in one expression, and storing it would
  spend a text field on a million rows to save an arithmetic the export does per row anyway. On a
  table this size those two choices are the difference between 86 MB a year and 172.
- **One request per stock, not one per session.** The endpoint answers a whole date range at
  five-minute granularity, so a first run backfills the feed's entire window in about a hundred
  requests and every evening after that asks each stock for the one session it is missing. Session
  by session the first run would have been sixty times that against a public feed the app is a guest
  on.
- **`harvest_marks` exists because `max(bar_at)` cannot answer it.** A session the feed genuinely has
  nothing for — a holiday it omits, a stock suspended for a week — comes back empty, so a mark
  derived from the newest stored bar would never pass it and every harvest from then on would ask
  about the same empty days forever. The mark advances on a request that was **answered**, whatever
  the answer contained; a request that *failed* leaves it alone, because this is the one table in
  the app where "fetch it again tomorrow" is not a remedy. Bars and mark are written in one
  transaction for the same reason: a mark without its bars skips those sessions for good.
- **`ScheduleClock.lastFinalSession` is the guard against half a day.** A harvest that ran at noon
  would copy the morning and mark the session done, and the afternoon would never be fetched — the
  feed will not serve it twice and the mark says the day is finished. It is the app's one definition
  of "has that session closed", the same one the scorer and the still-trading flag read.
- **The ISIN symbol only**, the insistence `fetchOne` and `dailyHistory` already make: a legacy
  `SYMBOL.CA` symbol ignores `interval` and answers with daily rows, which stored here would be
  five-minute bars that are nothing of the kind. Measured against the real record on 2026-09-07:
  **104 of 106 priced tickers can serve them**, none is stuck on a legacy symbol, and the two that
  cannot (`AIFI`, `ICFC`) are absent from `yahoo_symbols.json` altogether.
- **Deliberately without a grace window**, as `CloseSweep` is, and it is not stood down by an
  ordinary price refresh. A refresh asks for daily rows and stores one line for a whole session, so
  a refresh at four o'clock has done nothing whatever about that session's bars — sharing
  `lastPriceRefreshAt` would have every refresh stand the harvest down and the archive would quietly
  never fill. Hence `lastSeriesHarvestAt` beside it.
- **Off by default, and more emphatically than the refresh above.** That one spends somebody's
  traffic; this spends about 86 MB a year of their storage, on an app other people use. Device-local
  and never synced, for `marketRefreshEnabled`'s reason and one of its own: the archive is per-device
  by construction, so a switch that travelled would promise a second phone an archive it does not
  have.
- **The status line is never blank**, the rule the refresh follows and for a sharper reason: a
  refresh that stops shows up as prices that have not moved, and an archive that stops shows up as
  nothing at all until somebody goes looking for a session that is no longer anywhere.
  `seriesHarvestLine` ranks one state above the report of the last copy — switched on, has run, and
  has still copied nothing — because every other line would file that as a success.
- **The export is a CSV, which is the opposite choice from Save diagnostics beside it.** That hands
  over a record for somebody to debug, so the file itself is the point; this is read by whoever
  wanted the archive, in a spreadsheet or a script, where a `.db` is a step and an installed tool
  between them and the rows. Streamed a row at a time through `forEachBar` — assembling a million
  rows first is an out-of-memory on the one kind of device this runs on.

### Moving off the old job table

`ScheduleMigration` runs once, from an `init` block placed deliberately **above** the state it
writes — Kotlin runs initialisers in source order, and a migration below those properties would be
overwritten by their own initialisers. It carries the one intent worth carrying:

- An enabled `PRICE_REFRESH` row, whatever its trigger, becomes the checkbox. After the close,
  hourly, through the session — every one of them was a way of asking the same question.
- An `ANALYSIS` row is dropped along with the rest of the table: the paid schedule feature it
  belonged to is gone (see above), so there is nothing left to carry it into.
- Then `scheduled_jobs` is dropped. A table nothing reads is one the next reader of the file has to
  work out the status of.

