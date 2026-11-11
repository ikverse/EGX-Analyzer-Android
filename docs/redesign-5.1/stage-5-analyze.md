# Stage 5 (Analyze): exact change list

Part of [the 5.1.0 plan](../redesign-5.1-plan.md). Written 2026-11-11 from a full read of the files
named below. Paths are under `app/src/main/java/com/ikverse/egxanalyzer/`. Re-check line numbers
before editing. Assumes Stage 1 is built (`tileSignals`, `Timeline`, `MoreRow`).

**Correction to the plan:** a run-finished notification already exists. `data/AnalysisNotifier.kt`
posts "Analysis finished" (`:55`, tap opens the report via `EXTRA_RESULT_ID` → `MainActivity.kt:76`
→ `openSavedResult`) and "Analysis failed" (`:69`), wired in `EgxApplication.kt:142-149`. What is
missing is an off switch, an audible channel, and a tap target on failure. There are no scheduled
analysis runs in the code: `executeRun` (`LiveAppState.kt:4224`) has one caller, `analyze()` at
`:4194`, and `JobScheduler.rebook` books only market refresh, close sweep and price series.

## 1. Per file

**`model/ReadingEstimate.kt` (new, pure)**
- `object ReadingEstimate { fun endsAt(readingStarted: Instant, now: Instant, done: Int, total: Int): Instant?; fun phrase(now: Instant, endsAt: Instant?): String? }`
- `endsAt` is null when `done <= 0`, `total <= done`, or elapsed ≤ 0; otherwise `now + elapsed × (total − done) / done`.
- `phrase` returns "Less than a minute to go" (under 45 s, or already past), "About a minute to go", or "About N minutes to go" (nearest minute).

**`model/AnalysisProgress.kt`**
- Add `val skipped: Int = 0` after `correction` (`:35`): sources answered from an earlier reading.
- Add `val unitsDone` / `val units`: images when `images > 0`, otherwise batches. This is the estimate's input, since a text-only run has `images == 0`.
- `line()` (`:69-80`): "Reading batch b of n · x of y images read" → "Reading the posts · x of y pictures"; without images, "Reading the posts · batch b of n". Writing strings unchanged.
- `badge()` (`:89`) unchanged.

**`data/AnalysisRepository.kt`**
- `extract` (`:296-330`): pass `skipped = reused.size` in the READING progress.
- Add `var reused = 0` on `Harvest` and pass it in the WRITING progress (`:158-167`).

**`data/ReportReadyNotifier.kt` (new, house style; copy `SessionDigestNotifier`'s shape)**
- Channel `"report-ready"`, name "A report is ready", `IMPORTANCE_DEFAULT`; `setColor(NotificationTone.TRADES.argb)`; `NOTIFICATION_ID = 1008`; request codes 8 and 9.
- `fun ready(calls: Int, resultId: Long)`: title "Your report is ready", text "N calls found"; content intent carries `AnalysisNotifier.EXTRA_RESULT_ID`.
- `fun failed(reason: String)`: title "The run did not finish", BigText reason; intent carries new `EXTRA_SHOW_ANALYZE`.
- `permitted()` as the others.

**`data/AnalysisNotifier.kt`**
- Remove `finished()` (`:55-67`), `failed()` (`:69-79`) and `show()` (`:89-92`). Keep `running`, `cancelled`, `permitted`, `EXTRA_RESULT_ID`.
- Channel name "Analysis" → "A run in progress"; description (`:35`) → "Shown while a run is going."
- `running()` (`:47-49`): "Analysis running" / "N sources · model" → "Reading the posts" / "N posts · model".
- Reason for the split: channel `analysis` is `IMPORTANCE_LOW` (silent), and the finished notice reuses id 1001, the foreground service's id, right after `AnalysisService.stop` (`EgxApplication.kt:143-144`). The service teardown may remove it; not confirmed on a device.

**`EgxApplication.kt:142-149`**
- `analysisFinished`: stop the service, `notifier.cancelled()`, then `if (resultId != null && state.appPreferences.reportReadyEnabled) runCatching { ReportReadyNotifier(this).ready(recommendations, resultId) }`.
- `analysisStopped`: `reason == null` (cancelled) → `notifier.cancelled()` only; otherwise the same preference gate → `.failed(reason)`.

**`MainActivity.openRequestedResult` (`:75`)**
- Add: `EXTRA_SHOW_ANALYZE` → `appState.navigate(AppDestination.ANALYZE)`.

**`state/LiveAppState.kt`**
- `:4319`: `analyze(request) { noteProgress(it) }`. New `private fun noteProgress(p)`: sets `analysisProgress`; on the first READING report stores `@Volatile readingStartedAt`; sets `analysisReadingEndsAt = ReadingEstimate.endsAt(readingStartedAt, now, p.unitsDone, p.units)` while READING, null on WRITING.
- `finally` (`:4367-4371`): clear both.
- `:4343`: `analysisFinished(savedResults.firstOrNull()?.id, …)` → the id of the row whose `requestId == result.requestId`. Today a report that fails to read back sends the notification to an older report.
- Add `updateReportReady(enabled)` beside `updateFeedAlerts` (`:2927`).
- Add `sourcesReadEarlier` (see section 2).

**`ui/AppState.kt`**
- Add `val analysisReadingEndsAt: Instant?` after `:187`, `val sourcesReadEarlier: Set<String>`, and `fun updateReportReady(enabled: Boolean)` after `:589`. Add matching fields to `FakeAppState`.

**`model/AppPreferences.kt`, `data/SettingsRepository.kt` (`:265`, `:293`, `:713`), `data/SettingsSync.kt` (`:87`, `:173`, `:242` KNOWN)**
- `reportReadyEnabled: Boolean = true`, key `report_ready_enabled`, synced like `feedAlertsEnabled`.

**`ui/SettingsScreen.kt` (`:1074`, group "The app itself")**
- Add `SettingToggle("Tell me when a report is ready", prefs.reportReadyEnabled, appState::updateReportReady, about = …)` and include it in `switchesOn(…)`. Stage 6 relabels it.

**`ui/AnalyzeTiles.kt`**
- `AnalyzeHero` (`:56-108`), ready state:
  - "Ready to analyze" → "Ready when you are".
  - "N messages" → big count plus "new posts to read"; "No messages loaded" → "No posts loaded yet".
  - Sub-line "from N sources · N requests · cards read before are skipped" → "from N chats · calls for {EEE d MMM}".
  - New line under the button when `earlier > 0`: "N of them were read earlier and will not be read again."
  - `:92` and `:103`: `p.alert` → `p.tile1` (dark fix; also fixes light).
  - Remove the `requests` parameter.
  - Running state moves out to new `RunningTile`; the `showButton` parameter goes.
- `AnalyzeButton` (`:111-161`): ready branch only (drop the "N requests" sub-label, `:154-156`); remove the running branch, `runFill` and `RunningLabel` use.
- **New `RunningTile(appState, onStop, modifier, showStop: Boolean = true)`**, HERO tone:
  - Title "Reading the posts" / "Writing the report" / "Starting".
  - Big `unitsDone/units`, shown only while READING.
  - `ReadingEstimate.phrase(now, appState.analysisReadingEndsAt)` on a 1 s tick, READING only.
  - `KitProgress(fraction)` while `fraction != null`; otherwise the existing sweep (`Modifier.runFill(null, …)`).
  - Kit `Timeline` with three steps: "Collected the posts · {telegramSources.size}" (done), "Reading the pictures and text · x of y" (active or done), "Writing the report" (waiting or active; shows `line()` on a correction).
  - `KitButton("Stop")`.
  - Semantics `contentDescription = "Analysis running"`.
- **New `RunNotesTile(appState)`**:
  - When `progress.skipped > 0`: "N posts were already read earlier, so they are skipped."
  - "You can leave this page" / "The run carries on, and a notification tells you when the report is ready." The second clause only when notifications are permitted and `reportReadyEnabled`.
- `SourcesTile` (`:168`): "Sources" → "Chats"; "N in this run" → "N chosen" (`channels.count { selected }`); add `locked: Boolean` (rows not clickable, alpha 0.6, caption "locked while it runs").
- `ReadTile` (`:239`): "Read" → "What to read"; `:255` `palette.loss` → `tileSignals(TileTone.HIGHLIGHT).loss` (dark fix); add `locked`.
- `CallsForTile` (`:264`): "Calls for" → "Calls for which day"; "Next session" → "Next trading day"; "A chosen date" → "Picked day"; "Change →" → "Pick a day"; "Use next session" → "Use next trading day"; add `locked`.
- `ModelTile` (`:288`): "Model · {provider}" → "Read by"; "No model chosen" stays. New sub-line from `ModelSuitabilityRules.capabilitiesOf(availableModels.first { id == config.model })`: SUITABLE → "can read pictures"; UNSUITABLE → "cannot read pictures" (in `tileSignals(TILE2).loss`); unknown or catalogue not loaded → provider display name. Add `locked`, which hides "Change".
- `LastRunTile` (`:309`): sub-line "{Today at HH:mm} · N calls found"; button "Open report" → "Open it".
- `NotificationsOffTile`: unchanged.

**`ui/AnalyzeScreen.kt`**
- `:156-159`: delete `requests`.
- `contentTypeLabel` (`:461-465`): "Images" → "Pictures", "Voice" → "Voice notes".
- KitCalendarSheet title (`:212`): → "Calls for which day".
- `AnalyzeBlocker` (`:530-533`): NO_CONTENT_TYPE → "Choose at least one thing to read."; NO_SOURCES → "No posts are loaded. Choose chats, then load their posts."
- `MessagesPreview` (`:313-403`) → renamed `PostsPreview(appState, blocker, attempted, expanded: Boolean)`:
  - Title "What it will read"; count "N posts".
  - Buttons "Preview messages" / "Refresh messages" / "Show messages" / "Hide messages" → "Load the posts" / "Reload" / "Show the posts" / "Hide the posts".
  - `expanded = true` (Unfolded, Book): list always open, no height cap.
  - Cover: first 4 rows plus kit `MoreRow`. The open flag moves to `PageState`.
- `MessageTile` (`:412`) → `PostRow`:
  - Avatar, chat name, HH:mm.
  - Second line "Picture · {preview}" / "Text · …" / "Voice note · …".
  - "Already read earlier · skipped" when `sourceId in sourcesReadEarlier`.
  - "{Type} · not being read" when its content type is unticked.
  - Replace `MaterialTheme.colorScheme.onSurfaceVariant` (`:422`, `:437`, `:446`) with `palette.muted`.
- `RunningLabel` and `elapsedSince` (`:705-740`): delete if unused after the rebuild (`RunProgress` in `AppHeader.kt:277` keeps its own). `runFill` stays.
- Delete the dead Material imports (`:7`, `:69-71`, `:75-79`).

## 2. State

- `PageState` (`:207` block): `val analyzePostsOpen: MutableState<Boolean>`, replacing the `remember` at `AnalyzeScreen.kt:331`, which a fold loses. `attempted`, `confirmingCancel` and `pickingDate` (`:162`, `:166`, `:208`) are also `remember`; move them to `analyzeAttempted`, `analyzeConfirmingStop`, `analyzePickingDate`.
- `AppState.analysisReadingEndsAt`: an end instant set at each batch boundary, so the phrase counts down without recomputing between batches.
- `AppState.sourcesReadEarlier: Set<String>`: `localDataStore.sourceReads(model, analysisReadingKey(), resolveAnalysisWindow(mode, date).start).keys ∩ telegramTraces.keys`. Recompute at the end of `syncTelegramSources`, on model, prompt or language change, and after a run saves.
- Preference `report_ready_enabled`. No database change, no version bump, no migration.

## 3. Manifest

`POST_NOTIFICATIONS` is declared (`AndroidManifest.xml:10`), with `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` (`:11-12`). Nothing to add. The new channel `report-ready` is created in the notifier's `init`. The permission prompt stays on the Analyze press (`AnalyzeScreen.kt:176`).

## 4. Postures (replaces the `when` at `AnalyzeScreen.kt:245-282`)

- **Cover** (`else`, `:272`):
  - Ready: hero, SetupCard, notifications-off, 2×2 grid (Chats, What to read / Calls for which day, Read by), PostsPreview (collapsed), LastRun.
  - Running: RunningTile, RunNotesTile, then the four tiles `locked`.
- **Unfolded and Book** (`:246`): `SplitColumns(posture, 0.5f)`.
  - Start: hero or RunningTile, SetupCard, notifications-off, the four tiles, LastRun.
  - End: `PostsPreview(expanded = true)`, replacing sources, readAndDate and MessagesPreview there.
- **Tabletop** (`:259`):
  - Ready: as Cover, with hero above and button below the crease as now.
  - Running: `RunningTile(showStop = false)` plus the skipped line above; below, the four locked tiles, "These unlock when the run finishes. You can leave this page and it carries on.", and `KitButton("Stop this run")`.
- The posts preview is the existing `telegramSources` list, filled by `syncTelegramSources()` from the current "Preview messages" button (`:363-375`).

## 5. Tests

`AnalyzePageTest` updates:
- `:88-89`: "Ready when you are", "3" and "new posts to read".
- `:107-110`: find the content description "Analysis running", click "Stop", then press the dialog's confirm (two "Stop" nodes now; use `onAllNodesWithText("Stop")`).
- `:126-129`: "Chats", "2 chosen".
- `:135-136` and `:142-143`: "Ready when you are", "What it will read".
- `:147-152`: unchanged in behaviour.

New tests:
- `model/ReadingEstimateTest`: nothing before the first batch; 2 of 6 units in 60 s → 120 s left; done ≥ total → null; phrase boundaries.
- `AnalysisProgressTest`: the `:69-70` strings; units fall back to batches; writing gives no estimate input.
- `AnalyzePageTest`: estimate shown while reading and absent while writing; tiles locked while running; tabletop running shows "Stop this run"; blocker text on the hero uses `palette.tile1`; an already-read post row says skipped.
- `SettingsRepositoryTest` and the SettingsSync test: `reportReadyEnabled` defaults true and round-trips.
- `data/ReportReadyNotifierTest` (Robolectric, `ShadowNotificationManager`): `ready` posts id 1008 on `report-ready` with the result extra; nothing posted without permission.
- `OutsideSurfacesTest` / `NotificationToneTest`: no change (still TRADES).
- `SetupCardTest`: check for any blocker-string assertions.

## 6. Where the mockup cannot be followed, and what to do instead

- **"46 new posts" / "12 read earlier" before a run:** not available today (`priorReads` is built inside `executeRun`, `:4272`). `sourcesReadEarlier` supplies it, but it is an upper bound: `Harvest.reuse` (`AnalysisRepository.kt:431-445`) refuses readings that do not line up, and the wording filter drops sources. Before posts are loaded there is no count at all; show "No posts loaded yet" and "Load the posts".
- **"24/34":** progress counts pictures, not posts (`AnalysisProgress.images`). Label it "pictures", or "batch b of n" for text-only runs.
- **"Collected the posts" step:** no such phase is reported. Loading happens in `analyze()` (`:4188-4193`) before status becomes RUNNING. Draw it as already done, with `telegramSources.size`.
- **"read this morning":** only "earlier" is known (`sourceReads` has no time of day).
- **Post captions such as "a chart with buy and target prices":** do not exist. Use the content type plus `SourceTrace.preview`; pictures and voice notes have a blank or file-name preview.
- **"Gemini Flash":** no friendly model names exist. Show the model id.
- **Scheduled runs:** none exist. Any future one must call `executeRun`, which already ends in `analysisFinished` / `analysisStopped`, so it gets the same single notification.

## 7. Owner decisions (defaults assumed if not answered)

1. Should the report-ready notification be suppressed when the app is in the foreground? An on-screen run already jumps to Results (`:4353`). Assumed: it always fires.
2. The off switch ships in this stage under "The app itself"; Stage 6 only relabels it.
