# 5.1.0 plan: five tabs reimagined, plus dark-mode fixes

Written 2026-11-11. Status: **agreed as a list, not yet approved for implementation, nothing built.**
The owner asked for it to be saved so a later session can execute it directly.

Mockups (design canvas, private to the owner): https://claude.ai/artifact/GPsLYMemATxU1MEqCF7fVQ
Pages on it: Results, Insights, Portfolio, Analyze, Settings, Dark mode. Every tab is drawn for the
cover screen (411dp), unfolded (750dp), book and tabletop. The mockups use sample data and were
never checked rendered by the author. **Where a mockup and a stage file disagree, the stage file
wins**: each stage file ends with a section listing what the mockup shows that the app cannot do,
and what to build instead.

## The exact change lists

Each was written from a full read of that tab's code on 2026-11-11, with function names and line
numbers. Line numbers are as of the 5.0.0 tree; re-check them before editing.

| Stage | File | What it covers |
|---|---|---|
| 1 | [stage-1-shared.md](redesign-5.1/stage-1-shared.md) | Ladder, status words, hold menu, timeline, More row, tile-aware colours, dark palette fixes |
| 2 | [stage-2-results.md](redesign-5.1/stage-2-results.md) | Run tiles, open report, chips, cards, table, About this run sheet |
| 3 | [stage-3-insights.md](redesign-5.1/stage-3-insights.md) | Ranking tiles, day tiles, finished-call cards, call sheet, channel record |
| 4 | [stage-4-portfolio.md](redesign-5.1/stage-4-portfolio.md) | Top tile, Needs you, position cards, trade sheet, trades table |
| 5 | [stage-5-analyze.md](redesign-5.1/stage-5-analyze.md) | Ready and running tiles, time estimate, report-ready notification, posts preview |
| 6 | [stage-6-settings.md](redesign-5.1/stage-6-settings.md) | Hub and status tile, Reader page with Advanced, notification samples |
| 7 | below | Docs, version, changelog, commit, tag |

Stages must run in order: 2 to 6 are written against Stage 1's names, Stage 3 uses a helper from
Stage 2's list, and Stage 6 uses the preference Stage 5 adds.

## Rules that apply while executing

- The approval rule in `CLAUDE.md` still holds in whatever session runs this: post the stage's
  list, wait for the literal word "approve", then build only what is listed. Anything discovered
  mid-way that is not in the stage file goes back to the owner first.
- Never start an analysis run, never switch on paid schedules.
- No emulator and no screenshots without asking first. Build plus unit tests is the loop.
- Pushing the tag publishes the release. Ask again before `git push` of the tag.
- `ui` imports nothing from `data`. Platform work goes behind `AppState`, implemented in
  `state/LiveAppState.kt` and mirrored in `FakeAppState`.
- `NoOldElementsTest` must stay green: only `ui/kit/` may build on Material.
- Anything the reader would notice losing on a fold goes in `PageState`, not a `remember`.
- No sideways swipe gestures on rows: they fight the pager's swipe between tabs.
- One `setContent` per Compose test.

## The idea in one paragraph

Each screen leads with the answer in plain words and one picture, and the technical detail moves one
level down behind a closed row. The picture is the same everywhere: a horizontal line running
stop, entry, target 1, target 2, drawn to the real proportions of the call, with a dot where the
price is. Status is a plain word. Tap opens the thing in full; hold opens its actions.

## Owner decisions still open

Each stage file states the default it assumes. These are the ones worth a yes or no before the
stage that needs them:

1. **Stage 2:** the price move on a call reads "vs entry", not "today" (no day move exists behind
   `AppState`). Assumed yes.
2. **Stage 2:** system back closes an open report, which touches `state/LiveAppState.kt`. Assumed yes.
3. **Stage 3:** the podium on "Who delivered" is removed. Assumed yes.
4. **Stage 3:** the "came into the buying range" step on a call's timeline has no date (the app
   does not keep one). Assumed acceptable.
5. **Stage 3:** the Today card and the In play row stay as they are today. Assumed yes.
6. **Stage 4:** the Hit rate, Open positions and Closed this month tiles are removed.
   **Confirmed by the owner on 2026-11-11.**
7. **Stage 4:** the per-session cards on Portfolio are replaced by flat Open now and Finished
   lists. **Confirmed by the owner on 2026-11-11.**
8. **Stage 5:** the report-ready notification fires even when the app is on screen. Assumed yes.
9. **Stage 6:** the theme setting stays under "Prices and trades". Assumed yes.

## Corrections found while reading the code

- **The ladder is proportional, not equal thirds.** The mockups draw three equal zones; the app's
  `PriceLadder` deliberately draws to scale and keeps doing so.
- **A run-finished notification already exists** (`data/AnalysisNotifier.kt`). Stage 5 gives it its
  own audible channel, an off switch and a tap target on failure, instead of adding a second one.
- **There are no scheduled analysis runs** in the code today, so nothing to reconcile there.
- **Several figures in the mockups do not exist** and are replaced by ones that do: "today" moves,
  "last 30 days", "half sold", friendly model names, post captions, "posts" counted during a run
  (the app counts pictures), "days" (the app counts trading sessions).
- **Diagnostics lives on the Data page**, not About; it is relabelled where it is.

## Stage 7: Close-out

1. Run `NoOldElementsTest`, `OutsideSurfacesTest` and the full unit suite:
   `./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | grep -E "^e:|FAILED|BUILD"`
2. Update `docs/ui.md` (including 521-543, the settings section), `docs/portfolio.md` (138-179, the
   session cards), and `CLAUDE.md`: the design-language section, line 175's settings pointer, the
   "report card toggles on a press" gotcha (no longer true), the in-report toolbar notes under
   "A page's filters live in a sheet", and the KDoc at `PricesSection.kt:32`.
3. Bump `appVersionName` to 5.1.0 in `app/build.gradle.kts`.
4. Add the 5.1.0 entry to `CHANGELOG.md`.
5. Commit on the current branch and create the tag `v5.1.0` locally.
6. **Stop and ask before pushing the tag.**

## Dark-mode review, the measurements behind the fixes

Measured 2026-11-11 with the WCAG contrast formula against `ui/theme/Palette.kt`.

Passing in dark: ink on ground/card/raised 12 to 15:1; muted 6 to 7.7:1; heroInk 5.5 to 7:1; gain on
dark surfaces 8 to 10:1; loss 4.9:1 on cards and 5.6:1 on ground (4.4:1 on raised, marginal, left
alone); navy on every coloured tile 6 to 15:1; white on cobalt 6.6:1; status containers 7 to 12:1.

Root cause of most failures: `TileTone.INK` is `p.ink to p.ground`, so in dark mode it is a light
tile, while colours drawn on it (`highlight`, `gain`, `loss`) stay tuned for dark surfaces.

| Where | What | Contrast | Fixed in |
|---|---|---|---|
| `ChannelScoreSheet.kt:87`, `:109` | Lime label and figure on the INK tile | 1.03:1 | Stage 3 |
| `TradeControls.kt:603` | Return in lime or red on the INK tile | 1.03:1 / 2.7:1 | Stage 4 |
| `SettingsScreen.kt:226-236` | Lime circle on the INK tile, light icon on lime | 1.03:1 | Stage 6 |
| `InsightsTiles.kt:124` | Podium's lime bar on the INK tile | 1.03:1 | Stage 3 (podium removed) |
| `PortfolioTiles.kt:369` | Loss figure on the INK tile | 2.7:1 | Stage 4 |
| `PortfolioTiles.kt:506-518` | Up/down bar on the INK tile | 1.45:1 / 2.7:1 | Stage 4 |
| `AnalyzeTiles.kt:92`, `:103` | Coral text on cobalt (light too: 2.6:1) | 2.3:1 | Stage 5 |
| `AnalyzeTiles.kt:255` | Red on lime | 2.8:1 | Stage 5 |
| `kit/Buttons.kt:70` | White on the dark-mode red | 3.3:1 | Stage 1 |
| `kit/Market.kt:188` | "Open" bar segment, cobalt on a dark card | 2.4:1 | Stage 1 |
| `Palette.kt:99` | Quiet grey on cards | 3.2:1 | Stage 1 |
| `kit/Controls.kt` | Field border and switch off track | 1.5:1 | Stage 1 |

Not audited by reading: every one of the roughly 118 uses of a theme colour. Stage 1's
`PaletteContrastTest` is the sweep for the rest.

## Out of scope

- Emulator checks and screenshots, unless asked for and approved at the time.
