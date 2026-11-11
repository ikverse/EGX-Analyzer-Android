# Stage 1 (shared pieces): exact change list

Part of [the 5.1.0 plan](../redesign-5.1-plan.md). Written 2026-11-11. Paths are under
`app/src/main/java/com/ikverse/egxanalyzer/`. Every later stage is written against the names and
signatures below, so keep them, or update the later stage files when one has to change.

Nothing in this stage changes how a screen looks, except the four dark-mode kit fixes (items 6 to 9).

## 1. Per file

1. **`ui/PriceLadder.kt`** (both overloads at 46 and 69; three callers today: `InsightsScreen.kt:1213`, `OccurrenceSheet.kt:93`, `PositionCard.kt:307`, all of which must keep working unchanged):
   - `compact: Boolean = false`: no printed prices, a lower height, for table rows and list rows.
   - Zone colouring between the levels: stop to entry, entry to target 1, target 1 to target 2, in the three new palette colours (item 6).
   - `travelled: ClosedFloatingPointRange<Double>? = null`: a stripe from lowest to highest reached.
   - `entryLabel: String? = null`: trades pass "You paid".
   - **The axis stays proportional to the real levels.** The mockups draw equal thirds; do not copy that. The existing KDoc explains why the shape carries the risk-to-reward balance.
2. **New `model/StatusWords.kt`** (pure):
   - `fun Outcome.plainWord(): String` and `fun PositionStatus.plainWord(): String`.
   - Words: Running, Target 1 reached, Target 2 reached, Stopped out, Ran out of time, Watching, Not counted, You sold.
   - The existing `label` fields stay as they are; exports and call text use them.
   - `fun relativeDay(date: LocalDate, today: LocalDate): String?`: "Today", "Yesterday", "Tomorrow", "N days ago", "Last week", else null. Stages 2 and 3 both use it; today's `relativeSession` in `ResultsScreen.kt` (around 1299-1307) is the starting point.
3. **`ui/kit/Overlays.kt`**: `@Composable fun HoldMenu(items: List<MenuItem>, modifier: Modifier = Modifier, onTap: (() -> Unit)? = null, content: @Composable () -> Unit)`.
   - A long press lifts the content (scale and shadow, still under `LocalReduceMotion`) and opens the existing `KitMenu` under it.
   - Copy the press handling from `InsightsScreen.kt` around 1469-1490, which already explains why plain `combinedClickable` is not enough.
   - No sideways gestures.
4. **`ui/kit/Extras.kt`**:
   - `data class TimelineStep(val text: String, val detail: String? = null, val date: String? = null, val tone: StepTone = StepTone.DONE)` with `enum class StepTone { DONE, ACTIVE, WAITING, GOOD, BAD }`.
   - `@Composable fun Timeline(steps: List<TimelineStep>, modifier: Modifier = Modifier)`.
   - `@Composable fun MoreRow(title: String, expanded: MutableState<Boolean>, modifier: Modifier = Modifier, summary: String? = null, content: @Composable ColumnScope.() -> Unit)`, built on `KitExpandable`. The state is passed in because every caller keeps it in `PageState`.
5. **`ui/kit/Tile.kt`**: `data class TileSignals(val gain: Color, val loss: Color, val accent: Color)` and `@Composable fun tileSignals(tone: TileTone): TileSignals`.
   - CARD: `palette.gain`, `palette.loss`, `palette.heroInk`.
   - INK in dark (a light fill, `#E6EAF2`) and HIGHLIGHT, TILE1, TILE2, TILE3 in both themes: `#087A40`, `#C62020`, `#1E2BFF`.
   - INK in light (a dark fill): the dark palette's `#4ADE80`, `#FF4D4D`, `#C8FF4D`.
   - ALERT: gain and loss both navy (`onTile`), since red on coral is 2.7:1.
   - HERO: gain `palette.highlight`, loss `palette.tile1`, accent white.
6. **`ui/theme/Palette.kt`**:
   - Dark `faint`: `#6B6F78` → `#8A8F99` (4.9:1 on cards; check the pure-black palette inherits it).
   - Three new roles `zoneLoss`, `zoneNear`, `zoneFar`. Light: `#F6C9C2`, `#CFEBDB`, `#9ED9B8`. Dark: `#6B2A26`, `#245C3E`, `#3A8F63`.
7. **`ui/kit/Buttons.kt:70`**: `ButtonKind.DANGER` label is `p.onTile` (navy) in dark, white in light. White on `#FF4D4D` is 3.3:1; navy is 5.4:1.
8. **`ui/kit/Market.kt:188`** (`OutcomeStrip`) and **`ui/InsightsTiles.kt:201`** (`OutcomeKey`): the "open" colour is `p.heroInk` instead of `p.hero` (2.4:1 on a dark card against 6.1:1). In light the two are the same colour.
9. **`ui/kit/Controls.kt`**: the switch's off track (145, 178) and the text field's resting border (258) use `MaterialTheme.colorScheme.outline` (`#7E838D` in dark) instead of `p.line` (1.5:1 on a card).

## 2. Tests

- Extend `PriceLadderTest`: the compact form draws no price labels; `travelled` clamps to the axis; `entryLabel` replaces "Entry"; the three existing call shapes still lay out.
- New `model/StatusWordsTest`: every `Outcome` and `PositionStatus` has a non-blank word; `relativeDay` boundaries.
- New `ui/theme/PaletteContrastTest` (plain JVM, WCAG formula): for Light, Dark and Black palettes, every text role on every surface it is documented for is at least 4.5:1 (`faint` at least 3:1, large text only), and `tileSignals` colours are at least 4.5:1 on their tile fill. This is the sweep for dark faults that were not found by reading.
- New kit test: a long press on `HoldMenu` content shows the menu items; a tap calls `onTap` and shows none.
- `NoOldElementsTest` and `OutsideSurfacesTest` must stay green.

## 3. Close of stage

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | grep -E "^e:|FAILED|BUILD"` and report.
