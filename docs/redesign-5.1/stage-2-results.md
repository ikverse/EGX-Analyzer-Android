# Stage 2 (Results): exact change list

Part of [the 5.1.0 plan](../redesign-5.1-plan.md). Written 2026-11-11 from a full read of the files
named below. Paths are under `app/src/main/java/com/ikverse/egxanalyzer/`. Re-check line numbers
before editing. Assumes Stage 1 is built (`StatusWords.kt`, `HoldMenu`, `Timeline`, `MoreRow`,
`tileSignals`, compact `PriceLadder`). `ReportExport.kt` is in `data/`, not `ui/`; nothing in it
changes.

## 1. Per file

### `ui/ResultsScreen.kt`

**`ResultsScreen` (134-464)**
- Keep 141-299 (filter sheet, `shown`, empty states, pending-result effect, intro).
- 314-371 local `card(...)`: drop `headerMinHeight`, `expanded`, `onShare`, `onSaveLocally`, `onExport`, `onDelete`, `peakFor`, `latestFor`, `traceRoot`, `stockFilter`, `onFilterToTicker`, `trades`, `editor`. A tile now only opens.
- 374-384 `stack(...)`: always pass `openRunId = null`. `SavedRunStack` (679-942) is untouched, so `SavedRunStackTest` stays green.
- 310-312 `bands` / `expandableBands`: removed from this screen (the function and its test stay). The list is `ResponsiveRows(days, columns)`.
- 408-460 posture branch: replaced, see section 3.
- Closing an open report: the bar's back button plus system back, see section 5 item 2.

**`SavedAnalysisCard` (970-1261) becomes `RunTile`**, params `saved, tone, outcomes, lead: Boolean, isOpen: Boolean, stack, onBringForward, onOpen, highlighted, onHighlightShown, modifier`.
- Removed: expanded-state `clickable` (1051-1061), ⋮ and `KitMenu` (1086-1109), `#id · time` line (1079-1084), channel-names line (1127-1140), provider/model line (1141-1147), `ReportFigures` branch (1149-1158), `AnimatedVisibility { ResultDetail }` (1210-1224), both dialogs (1228-1260, moved to the About sheet).
- Text changes:
  - `"Monday morning"` (`runTitle`) → `"Mon 9 Nov"` (`EEE d MMM`); undated runs keep `runTitle`.
  - `"For Mon 9 Nov 2026 · Yesterday"` → small label above the date: `"Yesterday"`, or `"Today · session open"` when the target is today and `ScheduleClock.lastFinalSession()` is before it.
  - `"2 calls · 1 hit · 1 stopped"` → lead tile (first day): `"14 calls on 9 stocks, from 3 channels"` plus three figures `reached target` / `still running` / `stopped`. Other tiles: `"7/11"` + `"reached target"`.
  - `onClickLabel "Open report"` stays. `"Run 2/3"` dots (1193-1208) stay.
  - `isOpen` (book only) swaps the label to `"… · open on the right"`.
- `ReportFigures` (1270-1296) deleted. `RUN_META_FORMAT` (1717) deleted.

**`ResultDetail` (1318-1577) becomes `OpenReport(saved, appState, layout: ReportLayout, onClose)`**, `enum ReportLayout { CARDS, TABLE, TABLETOP }`.
- Removed: `hiddenTimings`, `hiddenChannels`, `search`, `filtersOpen`, `showContext` (1365-1376), `Controls` (1406-1432), `Toolbar` (1440-1488), the `"Hide"` button, `"Context"` chip, `"Filters"`/`"Filters on"` chip, `showTrace` button and `TraceAndDiagnostics` call (1543-1549), the `detectTapGestures { }` swallow (1508).
- Added, in order:
  - `ReportBar(onBack, onShare, onAbout)`: `"Results"` back button (content description `"Back to all runs"`), icon buttons `"Share this report"`, `"About this run"`.
  - `ReportHeader(saved, outcomes, watching)`: one `Tile(tone = INK)`: relative label, date, `"14 calls and 2 to watch · 9 stocks · 3 channels"` (append `" · N bought"` when `tradedCount > 0`, replacing the old TRADED cell), `OutcomeStrip`, `"3 reached target"`, `"10 running"`, `"1 stopped"`. Colours on this tile come from `tileSignals(TileTone.INK)`.
  - `StatusChips(counts, hidden, onToggle, onAll)`: `All`, `Running 10`, `Reached 3`, `Stopped 1`, `Watching 2`.
  - Calls: `RecommendationTable` when `layout == TABLE && maxWidth >= TableMinWidth`, else `RecommendationCards`.
  - `MoreRow("About this run")` at the foot.
- The page's stock filter (`appState.pages.resultsStock`) still narrows stocks, read directly; `"Nothing in this report matches those filters."` stays.
- `detail` anchor (1334) moves to `PageState`; the resolution block 1553-1576 is otherwise unchanged.

**`TraceAndDiagnostics` (1581-1679) becomes `AboutRunSheet(saved, traceRoot, editor, onShare, onExport, onSaveLocally, onDelete, onDismiss)`** on `KitSheet(title = "About this run")`.
- `"Made Wed 11 Nov at 09:12"`.
- Four figures: `posts read` (`diagnostics.inputCount`, `"from N channels"`), `calls kept` (`"and N to watch"`), `posts left out` (`excludedSources.size + modelExclusions.size`), time taken (`durationMilliseconds` as `"41s"`, `"N posts were already read"` from `reusedSources`).
- `"Why N were left out"`: reasons grouped and counted (`reason.replace('_',' ')`).
- `MoreRow("For the curious")`, closed: provider · model, `tokenLine`, request/image counts, warnings, sources list, unaccounted images, trace path. These are the existing text lines 1584-1676, moved verbatim.
- Buttons: `"Share"` (text), `"Save as Excel"` opening a two-item `KitMenu` (`"Share as Excel"`, `"Save Excel to Downloads"`; both actions exist today), `"Undo all corrections"` when `editor.hasEdits`, `"Delete this run"` (danger). Both `KitDialog`s move here unchanged (`"Delete this analysis?"`, `"Delete everywhere"`).

**New `SelectedCallPane`, `CallKeys`, `TabletopActions`** for tabletop (section 3).

### `model/RunSummary.kt` (new; pure, no Android)
- `enum class CallGroup { RUNNING, REACHED, STOPPED, WATCHING, ENDED }`.
- `fun scoredCallFor(saved, stock, point, report): ScoredCall?`: match `reportId == saved.id && originalStockCode == stock.anchorOf(point) && pointIndex == point.parseIndex`; fall back to normalized ticker + `point.callDate(target)` + channel, because sessions merge re-runs (`PerformanceCalculator.kt:167`) and an older reading may own no scored call.
- `fun callGroup(point, scored): CallGroup`: `isWatching` → WATCHING; FULL/PARTIAL_HIT → REACHED; STOPPED → STOPPED; EXPIRED → ENDED; everything else, and null, → RUNNING.

### `ui/RecommendationCard.kt`
- `RecommendationCards` (73-128): add params `statusFor: (RecommendationDataPoint) -> Pair<String, CallGroup>`, `latest: LatestPrice?`, `onOpen: (RecommendationDataPoint) -> Unit`, `holdItems: (RecommendationDataPoint) -> List<MenuItem>`. Pager and dots stay.
- `RecommendationCard` (137-310):
  - Removed: `expanded`, `SourceLine` (189-192), the `LevelGrid` panel and Risk / reward (205-220), the `TradeAction` / `"Source"` / `"Hide source"` row (235-271), `OccurrenceDetail` (273-281, and the function 634-664), `quickEditOpen` + `HoldPrompt("Edit call")` (298-306).
  - Changed: `combinedClickable` (165-170) → `HoldMenu(items = holdItems(point), onTap = { onOpen(point) })`. Fill `palette.ground` → `palette.card` in `callBlock` (716-720); the `heldBorder` ring stays.
  - New body: logo + ticker + name | price and move; tag row (status `KitTag`, channel `KitTag`, `"T+1"` when set, `EditedChip`); then `PriceLadder(point, compact = false, reached = latest close)`.
  - Watching: no ladder; sentence `"Worth buying only if it closes above 68.00. Not a call to take yet."` (level = entry high; without one: `"On the watch list. Not a call to take yet."`).
- `StockHeader` (313-391): pills column 372-389 removed (the tags row replaces it); gains a trailing price slot.
- `SourceLine` (401-420) deleted. `CallMenu`, `TimingChip`, `LevelGrid`, `entryText`, `levelText`, `targetReturn` stay (the sheet uses them).

### `ui/RecommendationTable.kt`
- `RecommendationTable` (94-197): remove `showContext`, `toolbar`, and the pin machinery (138-180). Add `statusFor`, `holdItems`. One flat block, no per-stock `StockBlock`/`StockHeading` (232-401 deleted; the `"LAST CLOSE"`/`"LATEST PRICE"` heading goes, the price becomes the `Now` column).
- `ColumnHeader` (404-419): `Source, Entry, Target 1, Target 2, Stop, Support, Resistance` → `Stock, Now, Entry, Stop, Target 1, Target 2, Stop to target`, plus an unlabelled status column.
- Widths: `StockWidth = 132.dp` (logo, ticker, channel under it), five figure columns `weight(1f)` each (Entry keeps `EntryWeight` 1.6), ladder `weight(2f)`, `StatusWidth = 92.dp`. `ChevronWidth` deleted. At 600dp: 600 − 24 − 132 − 92 = 352 over 7.6 shares ≈ 46dp per share, with `AutoSizeText` holding the figures. All columns present at every width.
- `CallRow` (476-543): `SourceCell` → `StockCell`; `StackedCell` percent sub-lines and `R:R` dropped (they live in the sheet); `LevelCell` support/resistance removed; chevron removed; `combinedClickable` (505-508, hold = screenshot) → `HoldMenu`. Ladder cell `PriceLadder(point, compact = true, reached = …)`; Watching rows show `"only above 68.00"` in the ladder cell and `—` in Stop/Targets.
- Deleted: `ContextLine` (718-734), `riskReward` (737-740), `LevelCell`, `StopCell`/`TargetCell` percent logic, `SourceCell`.

### `ui/OccurrenceSheet.kt`
- No change. `CallMenu` stays.

### `ui/Filters.kt`
- `CheckedSetFilter` (79-…), `FilterRow` (376), `StockFilterField` (401) lose their only callers (`ResultsScreen.kt:1411-1484`). Delete all three, per the house rule on unused helpers; check the comment reference at `PageHeader.kt:467`.

## 2. State

`PageState`, Results block (137-160), all reset when `openResultId` changes:
- `resultsHiddenGroups: MutableState<Set<CallGroup>>`: the chips; a fold must not clear them.
- `resultsOpenCall: MutableState<Pair<String, Int>?>`: the sheet's anchor `(originalStockCode, parseIndex)`, moved out of `remember` (1334) so a fold keeps the sheet.
- `resultsSelectedCall: MutableState<Pair<String, Int>?>`: tabletop's upper pane; separate, so flattening the phone does not pop a sheet.
- `resultsAboutOpen: MutableState<Boolean>`.
- `filtersActive`/`clearFilters`/`filtersInSheet` unchanged: the chips are per report, like the toolbar they replace.

`AppState` / `LiveAppState`: one change, for system back (section 5 item 2).

## 3. Postures

Replaces 408-460 entirely:
- **No run open.** Cover and Tabletop: `ResponsiveRows` list (old else-branch minus the open band). Unfolded and Book: existing `SplitColumns` + `"Pick a run"` tile (410-428) kept; its text loses `"and the source trace"`.
- **Cover, open:** list not composed; `OpenReport(CARDS)` full width.
- **Unfolded, open:** `OpenReport(TABLE)` full width (the 614dp container clears `TableMinWidth`). The `"Best so far"` lime tile beside the header: ticker and best `(close − entry mid) / entry mid`, `"since the call"`.
- **Book, open:** `SplitColumns(posture, 0.42f, start = compact run tiles with isOpen, end = OpenReport(CARDS))`. New branch; today book gives an open report the whole row.
- **Tabletop, open:** `OpenReport(TABLETOP)`: `SelectedCallPane` with `heightIn(min = tabletopHeroHeight(posture))` (ink tile: logo, name, `Now`, status + channel tags; white tile `"Where it stands"`, large ladder, three figures `To target 1`, `Highest since`, `Gain vs risk`), then below the crease `StatusChips`, `CallKeys` (dot + ticker + price; pressing selects), and `TabletopActions`: `"I bought this"`, `"Ask AI"`, `"Original post"`, `"Chart"`. Replaces the `headerFloor` trick (436, 449). The first call is selected by default.

## 4. Tests

**Break, and the update:**
- `ResultsPageTest`
  - `a shut run is named…`: `"Monday morning"` → `"Mon 9 Nov"`; drop `"#31 · 9 Nov 09:12"`; `"2 calls · 1 hit · 1 stopped"` → the new wording as built, `"reached target"`, `"stopped"`. The strip's content description is unchanged.
  - `pressing a run opens…`: `"STOCKS"` → the header sentence as built; `"Source trace and diagnostics"` → `"About this run"`.
  - `delete asks first…`: open the run, press `"About this run"`, `performScrollTo()` then `"Delete this run"`; the dialog asserts stay.
  - `unfolded, a pressed run…`: `"STOCKS"` → the `Stock` column header.
- `RecommendationTableTest`: `columns` → `Stock, Now, Entry, Stop, Target 1, Target 2, Stop to target`; remove `showContext` and delete both dates tests; pass the new lambdas. Still move one container across 614 / 682 / 715dp and assert every column at each.
- Unaffected: `SavedRunStackTest`, `RunOrderTest`, `GroupRunsByDayTest`, `ExpandableBandsTest`, `ReportExportTest`.

**New:**
- `RecommendationTableTest`: a long press on a row shows `"I bought this"` and `"Fix a wrong number"`; a Watching row shows `"only above"` and no ladder.
- `ResultsPageTest`: pressing `Stopped` leaves the stopped call and removes the running one, `All` restores both; hold on a call card opens the menu; Book posture shows the run list and `"About this run"` together; in Tabletop pressing a call key changes the pane's ticker; `"For the curious"` is closed at first (the model id is absent until pressed).
- New `model/CallGroupTest`: each `Outcome` → group; watching wins; id match beats fallback; an older re-run resolves by fallback.

## 5. Where the plan or mockup cannot be followed, and what to do instead

1. **"A report card toggles on a press."** Gone by design: the open report is no longer inside the tile, so no gap can close it. Remove that `CLAUDE.md` gotcha in Stage 7's doc pass.
2. **System back.** `canGoBack`/`goBack` live in `state/LiveAppState.kt:2535-2565` and know only the nav stack and filters. Add an "open report" step there (close the report before clearing filters) and mirror it in `FakeAppState`. This touches `state/`.
3. **Filters store what is hidden.** Honoured (`resultsHiddenGroups`). But the groups are a fixed enum, so a card corrected to Watching while only `Running` is shown would vanish, which is the exact fault that rule was written for. Keep a small `keptVisible` set of anchors edited while a filter is on, cleared when a chip is pressed.
4. **Five chips, six outcomes.** Expired, entry-never-traded and unpriced have no chip. Unscored → Running (matches `runOutcomes`); Expired → a sixth chip `Ended`, drawn only when its count is above zero. Draw every chip only when non-zero, except `All`.
5. **One card or row per stock with several channel tags ("2 channels").** Conflicts with `RecommendationCard.kt:64-71`: two channels give different levels. Keep one card per occurrence in the pager and one table row per occurrence, one channel tag each.
6. **"+2.1% today".** No day move exists behind `AppState`; `RecommendationTable.kt:285-289` records why (a suspend read per stock). Show the move against the entry midpoint, labelled `"vs entry"`.
7. **Ask AI** takes a `ScoredCall` and costs money. Offer the item only when `scoredCallFor` resolves, and route it through the existing `AskAiDialog` confirmation (`StockOpinionSheet.kt:516`); with a saved answer the label is `"AI Response"`.
8. **Trade controls leave the card.** `TradeAction`'s held, overdue and price-scale pills go with it; the ring stays and the sheet keeps the full control. `"I bought this"` is offered only when `trades.dateOf(point) != null` and nothing is held.
9. **Hold on the ticker** (`"Filter this page to COMI"`, `RecommendationCard.kt:359`, `RecommendationTable.kt:333`) and hold-for-screenshot on a row are replaced by the one hold menu; `"See the original post"` covers the screenshot. Add `"Show only {TICKER}"` as a seventh item so the filter shortcut is not lost.
10. **Ladder** stays proportional, not the mockups' equal thirds.
11. **Legacy reports** (`consolidated` empty, 1538-1541): keep `LegacyDetail`, no chips.

## 6. Owner decisions (defaults assumed if not answered)

1. "vs entry" in place of "today" on the price move (item 6). Assumed yes.
2. Stage 2 may touch `state/LiveAppState.kt` for system back (item 2). Assumed yes.
