# Stage 4 (Portfolio): exact change list

Part of [the 5.1.0 plan](../redesign-5.1-plan.md). Written 2026-11-11 from a full read of the files
named below. Paths are under `app/src/main/java/com/ikverse/egxanalyzer/`. Line numbers are as of
commit `0d0ebd6` plus the uncommitted 5.0.0 tree; re-check them before editing. Assumes Stage 1 is
built (`StatusWords.kt`, `tileSignals`, `HoldMenu`, `Timeline`, `MoreRow`, compact `PriceLadder`).

## 1. Per file

**ui/PortfolioTiles.kt**
- `PortfolioTiles` (91-133): split into `PortfolioRecord(appState, portfolio, heroHeight, compact: Boolean)` (top tile, Needs you, and when not compact Today, Best source, AI read, footer) and the trades list, which moves to PortfolioScreen. Drop `onSelectTrade`, `decisionsOpen`, and the `AnimatedVisibility` at 115-117.
- `HeroTile` (137-197):
  - "Open trades · N · average return" becomes "Your trades so far".
  - Headline figure `stats.openReturnPct` becomes `stats.averageReturnPct`.
  - "Settled x% · N trades" becomes "average per trade · EGX30 did +x% over {range.label}"; the EGX30 clause is omitted when `market` is empty.
  - Add a three-count row under the chart: "N open now", "N won", "N lost".
  - Chart, `ChartLegend` and the range control stay.
  - New `compact: Boolean` (book list, tabletop): no chart or range control, counts on one line ("9 won · 3 lost · 4 open").
- `HitRateTile` (283-319): removed, superseded by won/lost. `weeklyHitRates` stays in model.
- `NeedsYouTile` (323-341), `line()` (343-347), `DecisionPanel` (356-420), `detail()` (422-429): replaced by `NeedsYouTile(late: List<PositionView>, appState, onOpen: (String) -> Unit, oneLine: Boolean)`.
  - Fed by `overdueRoster(portfolio.positions)`; absent when empty.
  - ALERT tile holding one INK tile per late trade: title, return, one sentence, two buttons.
  - Expired, no target: "{T} has run out of time" / "Its {window} trading days ended {shortDate(deadlineDate)} without reaching a target or the stop."
  - Expired after target 1: "…ended {date} after reaching target 1."
  - Kept open: "{T} is {overdueDays} days past its deadline" / "You are keeping it open."
  - "Record sale" becomes "I sold it" (still `appState.openPositionToSell`).
  - Second button: "Keep it open" on expired trades (opens the existing `KeepOpenDialog`); "Follow the deadline" becomes "Stop keeping it open" on kept-open ones (`setKeepOpen(false)`).
  - `oneLine` (book): the title plus "Sold it, or keep it open?"; a press selects the trade.
  - Dark fix at 369: `val s = tileSignals(TileTone.INK)`; return colour `if (ret < 0) s.loss else s.gain`.
  - The `TargetProgress` at 382-387 goes with the panel (it also drew `p.loss` on INK).
- `PositionsTile` (433-497) and `ClosedThisMonthTile` (606-642): removed, replaced by the Open now and Finished lists.
- `TodayTile` (501-534): kept. Dark fix at 518: `BreadthBar(..., tone = TileTone.INK)`.
- New `BestWorstTile(stats)` (unfolded only): "Best and worst", from `stats.best` and `stats.worst` (ticker, date, return).
- `BestSourceTile`, `AiReadTile`, `FeedFooter`: unchanged, drawn after the lists.

**ui/kit/Market.kt**
- `BreadthBar` (149-164): add `tone: TileTone = TileTone.CARD`; take up/down colours from `tileSignals(tone)`, flat from the tile's content colour at 0.4 alpha.

**ui/PositionCard.kt**
- `PositionCard` (99-710) is rewritten as the short card: `PositionCard(view, near: NeedReason?, highlighted, onHighlightShown, onOpen, actions: List<MenuItem>, modifier)`. `appState` and the six callbacks leave.
  - Body: logo and ticker; "You paid {entry} · now {price}"; return with "so far" (open) or nothing (settled).
  - Tags: the Stage 1 status word; "{n} trading days left" (open); "Close to its stop" / "Close to target 2" from `near`; "Overdue {n} days"; "Kept open"; "T+1"; the price-scale chip.
  - Then `PriceLadder(compact = false, entryLabel = "You paid", reached = …)`, keeping the `priceScaleChanged` guard at 322.
  - Wrapped in `HoldMenu(items = actions)`; tap calls `onOpen`.
  - Keep `arrivalFlash`, the status edge (657-675) and the `leaving` collapse.
- Removed from the card: the header chart toggle and chevron (151-168, 254-264), the ⋮ button and menu (265-283), the ticker long-press filter (180-218, which becomes the hold-menu item "Show only {T}"), the channel line (363-411), both `FigureGroup` panels (437-601), `DeadlineSteps` and the `profitLine` text (604-612), and the Sold / Keep open row (617-645).
- New `FinishedRow(view, onOpen, actions)`: logo, ticker, status-word tag, return.
- New `TradeDetail(view, appState, actions: TradeActions, showButtons: Boolean = true)`, shared by the sheet, the book pane and the tabletop top. In order:
  1. Header: ticker, Arabic name, return + "since you bought" (realized: "you made"), status tag, channel tag.
  2. `PositionChartSection` (723-818, kept) for open trades with more than one close; otherwise the ladder.
  3. "Time left · {remaining} of {window} trading days" over `DeadlineSteps` (1005-1025, kept).
  4. `Timeline` from dated facts only: "You bought at {entry} · the call said {entryLow–entryHigh}" on `entryDate`; `soldInParts` legs (`exitDate1`, `exitDate`); stopped (`stoppedOn`); settled (`settledOn`); sold (`exitDate`); deadline passed (`deadlineDate`); "Now {price} · {x}% below target 1" on `currentPriceOn`.
  5. Three figures: "Peak since entry" becomes "Best so far" (`fromEntry(peakSinceEntry)`), "Trough since entry" becomes "Worst dip", "Risk : reward" becomes "Gain vs risk" ("1.4×").
  6. `profitLine()` (947-962) unchanged, and `keepOpenNote`.
  7. `MoreRow("More numbers")`: Stop loss, Target 1, Target 2 with distance captions; Last close with its session; peak and trough as prices with dates; Deadline ("N of M left · custom") with sessions held; the Entry caption "bought {date}".
  8. Buttons: "Sold" becomes "I sold it" (when `awaitingSale`); "Edit trade" becomes "Fix the trade"; "See the call" (when `onOpenCall != null`); "Chart" (`LocalOpenStock`); "Keep open" becomes "Keep it open" (when `!keptOpen && !finished && awaitingSale`); "Follow the deadline again" becomes "Stop keeping it open"; "Reopen trade" becomes "Undo the sale"; "Remove" (danger).
- New `TradeSheet(view, appState, actions, onDismiss)`: a `KitSheet` titled with the ticker around `TradeDetail`.
- `TradeDialog` / `SellDialog` hosting (677-709) moves to the screen.

**ui/TradeControls.kt**
- `SellDialog` (603): `val s = tileSignals(TileTone.INK)`; `color = if (ret < 0) s.loss else s.gain`.
- `SellButton` (383-421): label "Sold" becomes "I sold it"; `openNow` and `onOpened` are removed (the screen hosts the dialog). `TradeAction` at 217 is unaffected.
- `KeepOpenButton` (319-341): label becomes "Keep it open". Make `KeepOpenDialog` (351) `internal`.
- `PositionStatusChip` (833-847): label from Stage 1's status word instead of `status.label`; explanations unchanged.

**ui/PortfolioScreen.kt**
- `PortfolioScreen` (83-160): `PositionFilterSheet`, the skeletons and the empty state are unchanged. The `when` at 136-158 becomes four branches (section 3). Add `TradeDialogHost(appState)` once, above the `when`.
- `SelectedTrade` (180-205): removed.
- `PositionSection` (270-456) becomes `TradeLists(portfolio, appState, layout)`.
  - Keeps: the filter block (316-331), the arrival effect (338-373) and the empty state (375-400).
  - Flattens `shown` to one list (`groups.sortedWith(order.groups).flatMap { positions.sortedWith(order.positions) }`), split into open and not-open.
  - Draws "Open now" (cards), then "Finished" (rows, first 3) with "See all {n}" (`MoreRow`).
  - "All positions" heading: removed.
  - Arrival: `openGroups += …` (368) becomes "set scope to ALL when the trade is hidden by scope, or set `portfolioFinishedAll`". Same filter clearing, delay, reveal and flash.
  - `pendingSell` becomes `pages.portfolioDialog.value = TradeDialogRequest(id, SELL)` followed by `consumePendingSell()`.
- Removed: `PositionJump` (466-483), `GroupSummary` (499-514), `SummaryStates` (526-533), `PositionSubSection` (542-557), the tone vals (566-571), `PositionGrid` (575-624).
- New `TradesTable(views, …)` (unfolded): columns Stock ("paid → now" beneath), Stop to target (`PriceLadder(compact = true)`, takes the spare width), Made, and time ("{n} days left" / "time is up" / "in {sessionsHeld} days") over the status word. Sub-heading "Finished lately" in ALL scope. Rows take `HoldMenu`.
- New `TradeKeys(views, selected, onSelect)` (tabletop): ticker, short word, return.
- New `rememberTradeActions(view, appState): TradeActions`: the one place that maps to `recordSale`, `reprice`, `setKeepOpen`, `clearSale`, `deletePosition` and `openCall(id, NavStop(PORTFOLIO, positionId = id))`. Remove still goes through the collapse and `deletePosition`'s Undo.
- `overdueRoster` (254-257) and `tabletopHeroHeight` (167-173): unchanged.

**model/PortfolioPulse.kt**
- Add `fun wonLost(positions): Pair<Int, Int>`: settled trades with `returnPct > 0`, and the rest with a return. This is `winRate`'s rule (PortfolioCalculator.kt:315-317).
- Add `fun nearLevel(needs: List<NeedsYouItem>): Map<String, NeedReason>`.
- `needsYou` is unchanged, but is now read only for the near-level tags.

## 2. State (ui/PageState.kt)

- Keep `portfolioSelectedTrade` (127). It now means "the trade open in full" in every posture: sheet on cover and unfolded, pane in book and tabletop. A fold moves it between them.
- Remove `portfolioDecisionsOpen` (130), `openPortfolioGroups` (182), `expandedPositionCharts` (205).
- Add `portfolioScope: MutableState<TradeScope>` (`OPEN`, `FINISHED`, `ALL`; default `OPEN`) for the chips.
- Add `portfolioFinishedAll: MutableState<Boolean>` for "See all N".
- Add `portfolioMoreNumbers: MutableState<Boolean>` for the More numbers row.
- Add `portfolioDialog: MutableState<TradeDialogRequest?>` (id plus `SELL`, `EDIT`, `EDIT_SALE`, `KEEP_OPEN`), so a fold does not close the dialog and the hold menu, sheet and Needs you tile share one host.
- `filtersActive`, `filtersInSheet`, `clearFilters`: unchanged. Scope is a view, like the sort order.
- AppState: no new members.

## 3. Postures

- **Cover** (was `else`, 150-157): `PortfolioRecord(compact = false)`, then `TradeLists`; tap opens `TradeSheet`.
- **Unfolded** (was shared with Book, 137-149): `SplitColumns(unfoldedStart = 0.4f)`. Start: record plus `BestWorstTile`. End: chips "Open N / Finished N / All" plus `TradesTable`. Tap opens `TradeSheet`, which is already a side panel there (`opensSheetsAsPanel`).
- **Book** (new branch): start is `PortfolioRecord(compact = true)`, `NeedsYouTile(oneLine = true)` and the lists as compact rows. End is `TradeDetail` of `selected ?: first open by URGENT` (derived, not written). No sheet.
- **Tabletop** (was `else`): a fixed-height column of `tabletopHeroHeight(posture)` holding the compact top tile and `TradeDetail(showButtons = false)` cut down to header, ladder and time left. Below the crease: chips Open / Finished, `TradeKeys`, and the action buttons.

## 4. Tests

`PortfolioPageTest` breaks in six places:
- `the cover screen shows every tile…`: assert "Your trades so far", "Open now", "You paid", "Running"; drop the Hit rate, Open positions and All positions assertions and the "COMI: 60%…" description.
- `needs you opens the decision panel…`: the fixture needs an overdue trade (`overdueDays = 3`, `deadlineDate` set, `ranOutOfTime = true`). Press "I sold it"; assert `openPositionToSell` got the id.
- `unfolded, pressing a position…`: press the COMI row; assert "since you bought" and `pages.portfolioSelectedTrade.value`.
- `book posture…`: assert "Open now" and "Time left" both exist.
- `tabletop…`: assert "Your trades so far" and "I sold it".
- EGX30 legend tests: the new caption also contains "EGX30", so match the legend exactly, and assert "EGX30 did" is absent without an index.

`OverdueRosterTest`, the filter-sheet test and the empty-record test are unchanged.

New cases:
- Hold on a card opens the menu with "Fix the trade".
- A trade 1.2% above its stop shows "Close to its stop"; one further away does not.
- "See all" reveals the fourth finished trade.
- Scope chips filter the table.
- More numbers shows "Target 2".
- A selection set before composing in Book draws that trade.
- Dark theme: the Needs you return colour equals `tileSignals(INK).loss`.
- `wonLost` unit test, including a 0.0 return counted as lost.
- Arrival (`pendingPositionId`) on a finished trade beyond three sets `portfolioFinishedAll`.

## 5. Where the mockup cannot be followed, and what to do instead

- **"Average per trade vs EGX30."** No like-for-like figure exists: `IndexPoint.changePct` is the index's move over the chart's days, not per trade. Show `averageReturnPct` beside the index's move over the selected range, with the range named.
- **Won / lost.** Not in `PortfolioStats`. Derive them with `winRate`'s rule (expired trades included; a zero return is a loss).
- **"Days left."** `sessionsRemaining` counts trading sessions. Write "trading days".
- **"Half sold."** A running trade cannot be half sold; both legs are recorded together when it closes. Drop the tag; the timeline shows the two legs on sold trades.
- **Date target 1 was reached.** Not kept on `PositionView`. The status tag says it; there is no timeline step for it.
- **Session cards (docs/portfolio.md 138-179)** go, and with them the folded-by-default rule and the per-session summary. The date filter and the three sort orders keep working on the flat list; update the doc at close-out.
- **Needs you** narrows to late trades (the documented Overdue card, whole portfolio, unfiltered). Near-stop and near-target-2 become card tags using `approachThresholdPercent`.
- **"Keep it open"** makes no sense on a trade already kept open, hence "Stop keeping it open".
- **Ladder** stays proportional to the real levels, so table rows will not line up as the mockup's equal thirds do.

## 6. Owner decisions (both confirmed on 2026-11-11)

1. The Hit rate, Open positions and Closed this month tiles are removed. Today, Best source and AI read are kept.
2. The session cards go, replaced by the flat Open now and Finished lists.
