# Changelog

All notable changes from 3.6 onwards. Earlier history is in `git log`.

---

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
