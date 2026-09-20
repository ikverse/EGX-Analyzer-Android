# Changelog

All notable changes from 3.6 onwards. Earlier history is in `git log`.

---

## [3.8.7] — 2026-09-20

- Results: fixed a regression from 3.8.6 where a shut card in a multi-run stack grew taller every recomposition without bound, making cards balloon in height and scrolling stutter badly - the footer-pinning `Box` was constrained to a height measured one level further out (outside its own padding), so every pass fed the previous pass's padding back in as more height to match. It now measures and constrains itself on the same node, so it can only ever match real content.

## [3.8.6] — 2026-09-20

- Results: fixed a layout bug where a saved run's card, when stretched to match a taller reading in the same multi-run stack, left the extra height as dead space below the "Tap to open report" hint and run dots instead of above them - the footer now stays flush with the card's true bottom edge whatever the surplus is

## [3.8.5] — 2026-09-20

- Main-card accent edge (the coloured hairline down the left side of a section card) narrowed from 3dp to 2dp
- Results: a saved run's card foot now reads "Tap to open/close report" with a chevron instead of the "View/Hide recommendations" button - a label rather than a second control, since the whole card already answers the tap

## [3.8.4] — 2026-09-20

- Results: a saved run's card now closes its recommendations on a press too, not just opens them - the whole card toggles either way, matching the "View/Hide recommendations" button at its foot
- Results: a session read more than once shows its page dots and "Run X/Y" label stacked vertically (dots over text) at the foot of the card, instead of beside the ⋮ menu at the top

## [3.8.1] — 2026-09-20

- Arrival flash slowed down: 250ms per half-cycle instead of 120ms, so the two blinks read at a comfortable pace instead of a stutter
- Insights call card (narrow layout): fixed a spacing bug where the wider gap asked for between the Arabic name and the channel name had been widening every line of the source info under it too (channel name, called/settled dates, source record, call context). That gap is now a dedicated spacer between the two blocks; every line within the source info is back to its original tight spacing

## [3.8.0] — 2026-09-20

- Version-number milestone. No functional changes since 3.7.25.

## [3.7.25] — 2026-09-20

- Arrival flash (the edge a card wears when the app scrolls to it): now flashes in the card's own status colour when it has a held outline (stopped out, partial/full target hit, open) instead of always the page accent; draws at 0.5dp instead of 2dp, matching the hairline weight; and blinks twice fast (about half a second total) instead of pulsing continuously for 2.4 seconds
- `ExtraColors.loss` darkened further: `#D32F2F` in the dark theme, `#9E1B1B` in light, for a more serious red than the coral `error` it was first split from
- The held outline's own weight dropped again, from 1dp to 0.5dp, thinner than the plain card hairline

## [3.7.22] — 2026-09-20

- Unfolded layout: the rail's wash and the page's own ground light now animate off one shared clock instead of two independent ones, so backgrounding the app mid-transition can no longer leave them out of step; the ground light also reaches further down every page, and the page's top wash eases back in on the way up rather than tracking the scroll pixel-for-pixel
- New `ExtraColors.loss` red, separate from Material's `error`: a trade or a price that actually lost money (stop-loss, stopped out, a negative return, the Ask AI opinion sheet's bearish verdicts) now reads in its own colour, leaving `error` for an ordinary fault - a rejected key, a failed run, a stale feed. Applied to the overdue chip, a position's status colour, the price ladder's stop line, and Insights' outcome rings
- Trade controls: a held card's coloured edge now matches the plain card hairline's weight instead of standing a half-step heavier
- Analyze: "Current/next EGX session" and "Specific date" are one row now, showing whichever date live mode resolves to; picking that same date from the calendar returns to live mode instead of needing a second control for it
- Analyze: the content-type checkboxes (Text/Images) are full-width stacked rows instead of a wrapping row that sized itself to its own content and sat bunched at the card's left edge

## [3.7.21] — 2026-09-20

- Portfolio: a position card's chart can now be pressed open or shut on any width, not just on a phone - it defaults open once the card is wide enough to draw it in the ladder's place and closed otherwise, and the choice is remembered per trade rather than per width so folding the phone doesn't reopen or close it
- Portfolio: the wide and narrow chart paths are unified, so a phone-width card's expanded chart now falls back to the price ladder when there isn't enough history too, matching what wide already did
- Insights call card: logo centered against the ticker and Arabic name together, and the ladder/chart now sit in the same neutral tinted panel "The call" already uses, instead of loose on the card
- Insights call card: a divider now separates the figure panels from the footer (Ask AI / price feed), matching the recommendation card's own footer divider
- Stock sheet: logo centered against the ticker and name together, same treatment as the recommendation and Insights cards

## [3.7.20] — 2026-09-19

- Results: the recommendation card's ⋮ menu is gone - Copy call went unused and Edit call is reached by holding the card; the corner it sat in now holds the Watching/T+1 pill (and Edited, when a call has been corrected), grouped and left-aligned, top-aligned with the ticker
- Results: risk:reward moved inside the tinted panel with the six other figures, instead of sitting below it
- Edit call sheet: "Undo all edits" moved here from the now-removed menu, shown in the button row only when there's something to undo

## [3.7.19] — 2026-09-19

- Results: the recommendation card's logo now centers against the ticker and the Arabic name together, instead of just the ticker line
- Results: risk:reward is now a Level row in the market's own blue, matching the six figures above it, instead of a plain caption line
- Results: a level pair's shorter tile now stretches to match its taller sibling, so a value that wraps to two lines (more likely at the folded, narrower width) no longer leaves the other side's colour bar short
- Results: holding on a recommendation card opens a blurred two-button prompt (Edit call / Cancel) instead of requiring the ⋮ menu
- Insights call card and the position card: the chart falls back to the price ladder when there isn't enough price history to draw a line, instead of an empty chart; history-fetching is shared between the two as `rememberPriceHistory` in `StockTrend.kt`

## [3.7.18] — 2026-09-19

- Portfolio: the position card grid drops from two columns to one, the same move Insights' call card grid got
- Position card: "Your trade" and "Where it stands" are now tinted panels (neutral tile / Portfolio's own indigo wash) with indigo headings, matching the call card's "The call"/"What happened" treatment; risk:reward is now a figure of its own, in blue, instead of text in the heading; the two panels stretch to match the taller one, per card
- Position card: above ~480dp of container the price chart expands on its own, in the ladder's place - press-to-reveal is unchanged on a phone
- Every tab now remembers how far it was scrolled and restores that position after a fold, instead of resetting to the top - `PageState.scrollOffset`, read by `Screen`
- `SectionPanelShape` and `WideCardMinWidth` moved into `DesignSystem.kt`, shared between the call card and the position card rather than declared twice
- Results: the occurrence sheet's level grid sits in a tinted panel and its header matches the call card's identity block; the recommendation card's header pills moved to their own row under the name

## [3.7.17] — 2026-09-19

- Insights: the call card grid drops from two columns to one — a call card always spans the full container width now, on a phone and once the Fold opens alike
- Insights call card: above ~480dp of container the card draws its wide layout instead of the phone one — a two-pane header (identity/pills on the left, channel/record on the right), a full price chart in place of the ladder, and "The call"/"What happened" running side by side instead of stacked
- Insights call card: `CallChartSection` is the same `PriceChart` the stock sheet and the position card draw — range chips, levels drawn across the line, a touch readout — built from the sessions the call was already judged on, so it costs no extra query

## [3.7.16] — 2026-09-19

- Insights call card: "risk : reward" moved out of "The call" heading to a grey caption under its four figures, instead of running in the panel's own rose accent
- Insights call card: outcome and T+1 pills moved out of the stacked top-right corner into a left-aligned wrapping row under the ticker, so the header's height is no longer set by whichever of the name block or the pill stack was taller
- Insights call card: "this source" and "also called by" lines moved up into the header, grouped with the channel name and called/settled line, ahead of the divider that now separates source info from what the app measured

## [3.7.15] — 2026-09-19

- Insights call card: channel name and the called/settled/repeat line moved into the header, closed by a divider, instead of running the source into the tight meta line below the name block
- Insights call card: "The call" and "What happened" headings take the page's own rose accent and sit in their own tinted panel (a neutral tile and the page's own soft accent) instead of a plain muted caption ruled apart by a hairline
- Insights call card: overall spacing widened from Space.s to Space.m between sections
- Position card: header column now carries Space.xs between the ticker row and the name below it; header height grew 52dp → 60dp to hold it
- Stock sheet: chart range chips packed tighter, 2dp between them instead of Space.xs

## [3.7.14] — 2026-09-19

- Position card and Insights call card: header drops the English company name, Arabic only
- Position card and Insights call card: meta line's middot separators ("channel · called …") now carry real dp padding (Space.s) instead of riding on the string's own spacing; the Insights card's line (up to four segments: channel, called, settled, repeat of) wraps in a FlowRow instead of running off the card

## [3.7.13] — 2026-09-19

- Position card: PriceLadder now hides while the chart is open, and returns the moment it's collapsed, instead of the two ever stacking
- Position card: whether the chart is open now survives folding the Fold — moved off a bare `remember` onto `PageState.expandedPositionCharts`, the same shape `openReportMarkdown` already uses
- Price chart's range/levels chips are tighter still — `PillPaddingH` instead of `Space.s`
- Price chart's target labels read "t1"/"t2" instead of "target 1"/"target 2" (StockSheet and the position card both, since the chart is shared)
- Price chart: extra clearance between the section heading's move% and the chart itself, so a level pinned to the chart's top edge doesn't crowd it

## [3.7.12] — 2026-09-19

- Price chart's range chips and Levels toggle (stock sheet and position card) are more compact — a house-style pill instead of Material's default FilterChip, same tap target
- Position card's chart no longer draws an entry/"you paid" line — it duplicated the ladder and the Entry figure at the exact same price; stop and both targets still show

## [3.7.11] — 2026-09-19

- Position card: the price chart is now hidden by default and slides open on a press anywhere on the header (outside the ticker, which still opens the stock sheet); a chevron beside the menu flips to show it
- Position card: the opened chart is the full one — range chips (1W–6M), a levels toggle, and the touch readout — built from the same pieces StockSheet draws rather than a smaller copy; PriceLadder is back to always showing
- Position card: the header's status/T+1/overdue/kept-open/price-scale pills now scroll sideways instead of wrapping, when there isn't room for all of them

## [3.7.10] — 2026-09-19

- Position card: an open trade now draws PriceChart in place of PriceLadder, so the levels are read against the month of closes behind them instead of a fixed ladder that never moves; a settled trade still gets the ladder
- Position card: status, T+1, overdue, kept-open and price-scale pills moved into the header, above a new divider separating identity and status from the trade's own facts below it
- Recommendation card: source label set to labelSmall; card and level-grid spacing widened from Space.s to Space.m

## [3.7.9] — 2026-09-19

- Recommendation card: header separated from the figure grid by a divider
- Recommendation card: "SOURCE" label added beside the channel name, same colour, name set larger
- Recommendation card: each figure (entry, stop, targets, support/resistance) now carries a vertical colour key spanning its label and value

## [3.7.8] — 2026-09-19

- Saved-run deck: a swipe on a multi-run day now turns the report tab it was headed for instead of turning nothing, once the deck itself has nowhere left to go in that direction

## [3.7.7] — 2026-09-19

- Analyze: a run now says which batch it is reading and how many images are read, as a determinate bar while it reads and an indeterminate one while it writes; a correction says which attempt it is
- Insights: the channel ranking draws Telegram's own picture of each source, with its initials where there is none, and a place chip on the sources that clear the minimum judged to rank
- Settings: the token tally leads with a figure strip and shows sent against returned as a bar, per model and overall
- Report card figures and the token tally share one `StatStrip`; `ShareBar` is the new one-quantity-in-two instrument

## [3.7.6] — 2026-09-18

- Recommendation card: timing pill top aligned with ticker logo by moving it into the ticker row

## [3.7.5] — 2026-09-18

- Recommendation card: timing pill moved inline with ticker, right-aligned before the ⋮ menu
- Recommendation card: navigation dots moved to center bottom of card
- Recommendation card: spacing added between Arabic stock name and channel name

## [3.7.4] — 2026-09-18

- Price chart: level guide lines restored to dashed; label halo stroke removed

## [3.7.3] — 2026-09-18

- Nav bar height increased from 74 dp to 82 dp
- Nav bar icons enlarged from 26 dp to 29 dp; indicator and label scaled proportionally
- Unselected icon alpha raised from 0.62 to 0.82 so all five destination colours read clearly
- Nav bar pill background darkened 20 % toward black

## [3.7.2] — 2026-09-18

- Price chart: level guide lines changed from dashed to solid; label halo widened to 6 dp with round cap so labels lift cleanly off the chart
- `LabelPillHeight` raised from 20 dp to 24 dp
- `ExpandableSection`: optional `showAccentEdge` param; expired-positions section hides the accent edge to avoid doubling it
- Recommendation card: redundant English stock name row removed from header
- Project docs moved from `CLAUDE.md` into `docs/`

## [3.7.1] — 2026-09-18

- Front card animates sinking into the deck on swipe

## [3.7.0] — 2026-09-17

- Recommendation deck redesigned to read and drag like a physical deck of cards

## [3.6.23] — 2026-09-17

- Maintenance release

## [3.6.22] — 2026-09-17

- Filter dates picked from a calendar instead of typed
- "Does it matter?" prompt removed
- Header icons enlarged

## [3.6.21] — 2026-09-14

- Settings cards tinted with the page's own yellow

## [3.6.20] — 2026-09-14

- Settings regrouped: Ask AI split out; Data and Backup added as a section

## [3.6.19] — 2026-09-14

- Maintenance release

## [3.6.18] — 2026-09-13

- Report table stock heading separated from its rows; row striping fixed

## [3.6.17] — 2026-09-12

- Scheduled analyses removed; alarm retains the three free slots

## [3.6.16] — 2026-09-12

- Fix: Room-reflected constructor preserved (regression from 3.6.15)

## [3.6.15] — 2026-09-12

- Crash logs written to disk and surfaced in the UI
- UI unit tests added
- APK size reduced by ~50 MB

## [3.6.14] — 2026-09-12

- Glass card transparency level opened up

## [3.6.13] — 2026-09-12

- Glass card defined by its edge and material, not shadow and sheen
- App surfaces unified into one material instead of three separate card styles

## [3.6.12] — 2026-09-12

- Two cards rendered as glass; action sheet frosts the page behind it
- Fix: horizontal swipe inside a page no longer also turns the tab bar

## [3.6.11] — 2026-09-12

- Report table stock heading now shows the stock's current price

## [3.6.10] — 2026-09-11

- Report row detail opens as a bottom sheet

## [3.6.9] — 2026-09-11

- Fix: header stock search box no longer disappears on the first keystroke

## [3.6.8] — 2026-09-11

- Fix: table cell shrinks its text style to fit rather than truncating digits

## [3.6.7] — 2026-09-11

- Pill shape unified across the app; two explicit height tokens (`PillHeight`, `LabelPillHeight`)

## [3.6.6] — 2026-09-11

- Extracted call can be edited inline
- Stock filter sourced from the catalog rather than free text

## [3.6.5] — 2026-09-10

- Results table rebuilt so each row fits the device's screen width

## [3.6.4] — 2026-09-10

- Navigation rail sits on the page's background colour; mark removed from the rail

## [3.6.3] — 2026-09-10

- Analyses table asserted on schema upgrade, not only on fresh install
- Image numbering and chunking separated; covered by tests
- Duplicate pixel reads eliminated between card loads

## [3.6.2] — 2026-09-09

- Fix: rail width and page content width both restored after previous trim

## [3.6.1] — 2026-09-09

- Filter switch moved to the trailing edge; action buttons reduced in size

## [3.6.0] — 2026-09-09

- Every page's filters moved into a sheet opened from the header
- Busy bar brought in to the page margin; spinner removed
