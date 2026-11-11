# Stage 3 (Insights): exact change list

Part of [the 5.1.0 plan](../redesign-5.1-plan.md). Written 2026-11-11 from a full read of the files
named below. Paths are under `app/src/main/java/com/ikverse/egxanalyzer/`. Re-check line numbers
before editing. Assumes Stage 1 is built (`PriceLadder(compact, travelled, entryLabel)`,
`StatusWords`, `HoldMenu`, `Timeline`, `MoreRow`, `tileSignals`) and Stage 2's relative-day helper.

## 1. Per file

### `ui/InsightsTiles.kt`
- **`InsightsPodium` 86–141, `PodiumStep` 143–190, constants 192–194:** delete (no mockup draws a podium; this also removes the lime-on-INK bar). Its count line `"N sources · N calls"` moves to the ranking heading.
- **`RankingTile` 215–254 → `RankingTiles(channels, tracked: Int, photoFor, selected: String?, onOpen: (ChannelScore) -> Unit, onExport, modifier)`:**
  - Add heading row: `"Who delivered"` and `"${channels.size} sources · $tracked calls"`.
  - The first three ranked sources each get their own `Tile`: HIGHLIGHT, TILE3, TILE2. The rest go in one CARD tile.
  - Keep `Show all N sources` / `Show the first 4` and `Save as Excel`.
  - `showAll` (223, `rememberSaveable`) and `explaining` (224, `remember`) move to `PageState`. The sheet call at 251–253 moves to `InsightsScreen`.
- **`RankingRow` 258–311:**
  - Remove the line `"N calls · avg +3.5% · 4 sessions"` (288–292) and the large `anyTargetRate` percent (300–304).
  - Add two figures: `formatPercent(averageReturn)` over `"earned per call"`, and `"${reachedTarget1}/${judged}"` over `"reached target"`. Subtitle is `"${judged} finished calls"`.
  - Thin source: `"N calls · too few judged to rank"` → `"N calls"` plus tag `"Too new to rank"`, figures muted.
  - Return colour comes from `tileSignals(tone)`.
  - `onClickLabel` `"Explain how this source is scored"` → `"Open this source's record"`.
  - Moved to the score sheet: calls posted, `medianSessionsToHit`, `anyTargetRate` as a percent.
- `OutcomeKey`, `InPlayRow`, `FeedNote`, `FigureCell`, `dashedOutline`: unchanged. Update the file KDoc at 63–67.

### `ui/ChannelScoreSheet.kt`
- **`ChannelScoreSheet` 72–309:** body moves into new `internal fun ColumnScope.ChannelRecord(channel, photoPath, latestCalls: List<ScoredCall>, onOpenCall: (ScoredCall) -> Unit)`. The sheet becomes `KitSheet(title = channel.channel) { ChannelRecord(...) }` and gains `latestCalls`. Title `"How X is scored"` → the channel name.
- **INK head 83–116:**
  - Delete the label `"HOW THIS SOURCE IS SCORED"` (84–88; this is the lime-on-INK fault). Replace with `"${calls} calls posted · ${judged} finished"` in the soft content colour.
  - `HeadFigure`s become: `averageReturn` / `"earned per call"` (tone from `tileSignals(TileTone.INK)` by sign, none when thin; replaces `p.highlight` at 108); `anyTargetRate` / `"reached target"`; `medianSessionsToHit` / `"trading days to get there"`.
  - Move the `OutcomeStrip` (114–115) out of the INK tile into "More numbers".
- **New CARD tile `"IF YOU HAD FOLLOWED EVERY CALL"`:** two cells, `"Sell everything at target 1"` (`sellAtTarget1Return`) and `"Sell half, let half run"` (`splitReturn`), the better one on HIGHLIGHT. Under them: `"Holding on paid off in ${fullHits} of ${reachedTarget1} calls."` With `policyCalls == 0`, show the existing 239–242 sentence instead.
- **`PolicyBars` 340–367:** delete. Old labels `"Sell all at target 1"` / `"Half at target 1, half runs"` are replaced by the cells above.
- **New CARD tile `"LATEST CALLS"`:** up to four newest non-repeat calls: logo, ticker, status tag, return. A press calls `onOpenCall`.
- **`MoreRow("More numbers")`, closed:**
  - `fullHitRate` "reached target 2" and `judged` "settled calls" (old head, 110–111).
  - Grid 144–151 minus its first cell: risk : reward, sessions to target 1, sessions to a stop.
  - Grid 247–254: calls posted, settled, re-posted counted once, not tradable.
  - `anyTargetRateFloor`, and the `OutcomeStrip` with `OutcomeKey`.
- **`MoreRow("How this is scored")`, closed:** `ExplainerSection` 1–5 (118–298) with every `Bullet` string unchanged, minus the two `FigureGrid`s and `PolicyBars` moved above. The disclaimer (300–307) stays visible below.
- Rewrite the KDoc at 50–71; lines 69–70 ("adds no figure the card does not already carry") become false.

### `ui/InsightsScreen.kt`
- **`InsightsScreen.tiles` 334–352:** drop `InsightsPodium`; order becomes `TodayCard`, `OutcomeKey`, `RankingTiles`, `InPlayRow`, `FeedNote`, `WatchlistCard`. The `podiumFloor` parameter goes.
- **Pending-call effect 270–279:** also clear the new day chip.
- **`record` 414–505:** keep `expandableBands`, `Show older`, and the reveal effect (384–406). Add heading `"Day by day"`.
- **`SessionSummary` 649–710:** delete. Old words `"N partial"`, `"N full"`, `"N stopped"`, `"N out of time"`, `"N pending"` and the line `"N calls · N sources · +x% per call · N re-posted, counted once"` go.
- **`SessionCard` 713–861 and `RecordTile` 2245–2285 → `DayTile` and `DayOpened`:**
  - `DayTile` (closed): relative word (`"Yesterday"`, `"2 days ago"`; reuse Stage 2's helper), date as `EEE d MMM`, `"${partialHits + fullHits}/${tally.calls}"`, `averageReturn`, `OutcomeStrip`, and `"New"` as now.
  - `DayOpened`: HIGHLIGHT tile with the date, `"N calls from N channels"`, `"+x% per call"`, the strip, and counts `"N reached target · N ran out of time · N stopped · N running"`.
  - Button `"Open that day's report"` replaces the header hold and `HoldPrompt("Open this session's report")` (773, 849–860).
  - `DayChips`: All, `Reached N`, `Stopped N`, `Out of time N`, `Running N`. Caption: `"The dark line on each bar is how far the price travelled."`
  - Provenance text (792–803) and `"N re-posted, counted once"` move, unchanged, to a muted foot line.
- **`ScoredCallRow` 863–1574 → `FinishedCallCard`:**
  - Keeps: `nameBlock` (971–1005), the held line (1170–1198), `ExtractionWarning`, `TimingLabel`, `arrivalFlash`/`heldBorder`, `TradeDialog` (1527–1573), and the Ask AI dialog and sheet (919–948).
  - Adds at right: `believableReturn` with tone rule 1370–1374 unchanged, and under it `"in N days"` / `"so far"` / `"next-day call"` (T+1) / `"at the last close"` (EXPIRED) / `"to target 1, then stopped"`.
  - Tags: the `StatusWords` word and the channel.
  - `PriceLadder(travelled = troughLow..peakHigh, reached = peakHigh)`.
  - Removed from the card and moved to the sheet: `sourceBlock` (1067–1131), both `FigureGroup`s (1253–1430), the wide `CallChartSection` branch (1224–1245), the `AskAiButton`/`PriceFeedButton` row and `SessionTable` (1436–1460).
  - Tap opens `CallSheet` for every call (1471–1490). Hold opens `HoldMenu`, replacing `HoldPrompt` at 1491–1519 and 1006–1017. Items: `I bought this` / `Open your trade`, `Ask AI` / `AI Response`, `Open the chart`, `See the original post`, `Copy`, `Open its report`, `Show only TICKER`.
- **New `NotCountedCard(call)`:** for ENTRY_NOT_REACHED, UNPRICED, PRICE_BREAK and AMBIGUOUS. `dashedOutline`, logo, ticker, the first sentence of `reason()` (ENTRY_NOT_REACHED: `"The price never came into the buying range"`), tag `"Not counted"`. No return, no ladder.
- **`OutcomeLabel` 527–542:** delete. `reason()` (553–615) stays and feeds the sheet. `PriceFeedButton` 1991–2001: delete.
- **New `CallSheet(call, appState, latest, channelScore, held, …, onDismiss)`:**
  - Card header.
  - `CallChartSection` (1908–1989, unchanged) when history has more than one close, else the ladder.
  - `Timeline` from new pure `ScoredCall.timeline()` (in `model`).
  - Three cells: `"Lowest dip"` (`fromEntry(troughLow)`), `"Gain vs risk"` (`riskReward`), `"Now"` (latest close).
  - `MoreRow("More numbers")`: Peak and Trough with dates, `Sessions to settle` / `Sessions elapsed` with the T+1 caption, the Return captions, all of "The call" group, `Latest close` caption, called/settled/repeat-of segments, `SourceRecord`, `CallContext`, `SessionTable` with its rebuilt note.
  - Actions: Ask AI, `Original post`, `Chart`, and `Open your trade` or `I bought this`.

## 2. State (`ui/PageState.kt`, Insights block 162–177). No new `AppState` members.

- `insightsOpenCall: MutableState<String?>`, keyed by `opinionId(ticker, openedOn, channel)` (`positionId` is shared by two channels on one stock). It is the tabletop selection and the open sheet.
- `insightsOpenChannel: MutableState<String?>`: book posture draws the record as page content.
- `insightsRankingShowAll: MutableState<Boolean>`: `rememberSaveable` does not survive a fold here.
- `insightsDayFilter: MutableState<String?>`: the chip. Not in `filtersActive`, because it is visible on the page.
- `insightsOpenRows: MutableState<Set<String>>`: which `MoreRow`s are open.
- `openInsightsSession`: unchanged meaning; also the selected day chip.

## 3. Postures (replaces the `when` at 507–523)

- **Cover:** one column: tiles, then day tiles opening in place. Replaces the `else` branch.
- **Unfolded:** `SplitColumns(0.45f)`. Start is the tiles. End is day chips (one per shown session, plus `Older` which adds `PAGE_SIZE`), the `DayOpened` head, and new `DayCallTable` with columns Stock, Stop to target (compact ladder), Made, status. Day defaults to the newest. Row tap opens `CallSheet` as a side panel. Replaces 508–516.
- **Book:** start is `RankingTiles` (selected row marked) then the other tiles and day tiles. End is `ChannelRecord` for `insightsOpenChannel`, defaulting to the first ranked source; no sheet.
- **Tabletop:** upright half, `heightIn(min = tabletopHeroHeight)`: `DayOpened` head, the selected call's card, and its timeline as tags. Flat half: day chips, `CallKeys` (ticker and return chips), and actions Ask AI, Original post, Chart, That day's report. The rest of the page follows. `CallSheet` is not opened in this posture. Replaces `tiles(tabletopHeroHeight)` at 520.

## 4. Tests (`app/src/test/java/com/ikverse/egxanalyzer/ui/`)

`InsightsPageTest` breaks in three places:
- Podium test (93–100): the leader now appears once, so `size >= 2` fails. Assert it exists once and rename the test.
- Show all (103–110): `"4 calls · too few judged to rank"` → assert `"Too new to rank"`.
- Row opens sheet (113–119): index `[1]` → `[0]`. `"HOW THIS SOURCE IS SCORED"` is gone; assert `"earned per call"`, then that `"How a call is judged"` is absent until `"How this is scored"` is pressed.
- The rest pass unchanged. `InsightsSheetsTest` is unaffected.

New cases, one `setContent` each:
- A day tile opens and a chip narrows it.
- ENTRY_NOT_REACHED draws `"Not counted"` with no return.
- A card tap opens the sheet; `"Peak"` is absent until `"More numbers"` is pressed.
- A hold shows `"Copy"`.
- Unfolded shows `"Stop to target"`.
- Book shows the leader's `"If you had followed every call"` with no press.
- Tabletop shows the keys.
- `ChannelRecord` composes under `ThemeMode.DARK`.
- Plain JVM: `ScoredCall.timeline()` for FULL_HIT, `stoppedAfterPartial` with and without `stoppedOn`, EXPIRED T+1, and OPEN.

## 5. Where the plan or mockup cannot be followed, and what to do instead

- a. **"Last 30 days":** no 30-day figure exists; the record runs from `scoringSince`. Show the sources and calls count instead.
- b. **Timeline entry step:** `ScoredCall` has no entry date or fill price (`Scoring.kt:243–273` keeps only a boolean). Draw `"Price came into the buying range"` undated, with the zone. Adding an `enteredOn` field would need a database bump and is not part of this plan.
- c. **"Days":** `sessionsElapsed` and `medianSessionsToHit` are trading sessions, and the second is a median. Word them "trading days".
- d. **"Holding paid off in 8 of 14":** use `fullHits` of `reachedTarget1`. The two policy returns rest on `policyCalls`, a different subset; the unchanged bullets say so.
- e. **Today tile with an EGX30 day move, and an "In play now" list with ladders:** both are in the mockup and neither was in the agreed Stage 3 list. `indexByDay` is cumulative, not a day move. `TodayCard` and `InPlayRow` are kept as they are.
- f. **Tap opens the sheet** contradicts the comments at 964–970 and 1465–1470 (tap opens the trade; an unheld card answers no tap). The trade moves to the sheet and the hold menu; update those comments.
- g. **Cover "one day opened":** the mockup draws a separate screen with a back arrow. In-place expansion is kept, so the reveal path and `NavStack` are untouched.
- h. **Podium:** removed. If the owner wants it kept, line 124 becomes `tileSignals(TileTone.INK)` instead.

## 6. Owner decisions (defaults assumed if not answered)

1. Drop the podium. Assumed yes.
2. The undated "came into the buying range" step. Assumed acceptable.
3. `TodayCard` and `InPlayRow` stay as they are today. Assumed yes.
