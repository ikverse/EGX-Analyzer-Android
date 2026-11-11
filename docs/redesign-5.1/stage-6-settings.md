# Stage 6 (Settings): exact change list

Part of [the 5.1.0 plan](../redesign-5.1-plan.md). Written 2026-11-11 from a full read of the files
named below. Paths are under `app/src/main/java/com/ikverse/egxanalyzer/`. Re-check line numbers
before editing. Assumes Stage 1 (`tileSignals`, `MoreRow`) and Stage 5 (the preference
`appPreferences.reportReadyEnabled` and setter `updateReportReady`) are built.

## 1. Per file

### `ui/SettingsScreen.kt`

- **`SettingsSection` (89-97):** `ANALYSIS("Reader")`, `GENERAL("Prices and trades")`, `DATA("Backup and sync")`. Enum constants stay as they are.
- **`settingsSummaries` (176-196):**

| Section | Old summary | New summary |
|---|---|---|
| TELEGRAM | `Signed in · N chats` / `Not connected` | `Signed in · N chats` / `Not signed in` |
| ANALYSIS | `<provider> · <model>` | `<model or "No AI chosen"> · ` + `key working` / `key rejected` / `key not checked` / `no key` |
| ASK_AI | `<model> · news from N days` / `· no search` | `Searches the web · N days back` / `No web search` |
| NOTIFICATIONS | `N of 6 on` | `N of 7 on` |
| GENERAL | `<theme> · prices and trade defaults` | `<theme> · ` + `Fresh all session` / `Fetched once a day` + ` · N-session trades` |
| DATA | `N saved analyses` | `N reports · ` + `sync on` / `sync off` + ` · ` + `backing up to a folder` / `no backup folder` |
| ABOUT | unchanged | unchanged |

- **`notificationSwitches` (202-211) and `NOTIFICATION_COUNT` (1578):** add the Stage 5 flag; 6 becomes 7.
- **`SettingsHub` (216-343):**
  - Remove the Telegram INK tile (224-263) whole, including the lime circle and icon at 231-236. That is the dark fix: nothing lime is drawn on INK any more.
  - Add `SettingsStatusTile(appState, onOpen)` first.
  - Telegram becomes an ordinary `HubTile`. Tones: Telegram `TILE3`, Reader `TILE2`, Notifications `TILE1`, Ask AI `CARD`, Prices and trades `HIGHLIGHT`, Backup and sync `CARD`, in a two-column grid in that order. About row unchanged.
  - `HubTile` value/detail come from `summaries` (`substringBefore(" · ")` / `substringAfter`), so hub and nav column cannot disagree.
  - DATA tile drops `tokenUsageSummary` (315).
- **New `SettingsStatusTile(appState, onOpen, compact: Boolean = false)`:** `TileTone.INK`. Headline plus three lines, each led by a check or warning icon tinted `tileSignals(TileTone.INK).gain` / `.loss`. No disc behind the icon. `compact` gives one line joined by ` · ` for the unfolded left column. Pressing it opens the first failing section; no click when all is good.
- **`SectionNavTile` (387-410), `SectionHeader` (414-422):** no change; they read `section.title`.
- **`TelegramPage` (494-541):** only the note at 500: "under Data and backup" becomes "under Backup and sync".
- **`AnalysisPage` (546-811):**

| Control (line) | Fate |
|---|---|
| New `ReaderStatusTile`: `Ready to read` / `The key was rejected` / `Key saved, not checked yet` / `No key yet` / `No AI chosen` | Visible, first. Replaces the `StatusPill` block (607-611) |
| `AnalysisModelCard` (591); its label at `ModelSection.kt:72` "Analysis model" becomes "Which AI" | Visible |
| Provider menu (612-629) | Visible |
| Endpoint field (630-636) | Advanced |
| "Choose endpoint region" (637-653) | Advanced |
| Key field, Save and verify, Remove key, `settingsMessage` (654-683) | Visible |
| "Reset provider" (672) and its dialog | Advanced (it resets the endpoint) |
| Output language and content toggles (702-728) | Visible |
| "Cloud response timeout" stepper (729-742) | Advanced |
| Wording heading and `AnalysisRulesSection` (747-760) | Visible |
| Prompt group (762-772) | Visible |
| "Correction retries" stepper (790-796) | Advanced. It takes the first paragraph of the Validation note (780-782) as its own `about` |
| Catalog toggle, Refresh catalog, `catalogMessage` (797-809) | Visible. The Validation group keeps the catalog paragraph; its summary becomes `Company names added` / `not added` |
| `Group("Token usage") { ModelUsageSection }`, moved from `DataPage` 1260-1274 | Advanced |

  Last on the page: `MoreRow("Advanced", summary = "Connection, waiting time, retries and usage", expanded = pages.settingsAdvancedOpen)`.
- **`NotificationsPage` (947-1102):** keep the three groups and their notes; "Your trades" stays, which `SettingsPageTest` relies on. Rows are driven by the new `NotificationKind`. Each is `SettingToggle(label, detail = kind.detail, about = existingNote.withSample(kind))`. Add one muted line under the first heading: "Tap ? on any line to see an example." `REPORT_READY` joins "The app itself". The feed note at 1096-1097 "Prices under General" becomes "Prices under Prices and trades". Threshold stepper unchanged.

| Kind (pref) | Current label | New label / detail | Sample title | Sample line |
|---|---|---|---|---|
| `tradeAlertsEnabled` | Tell me when the market changes a trade | A trade reaches a target or its stop / as soon as prices show it | COMI took target 1 | Bought at 82.50, now 88.10. |
| `approachAlertsEnabled` | Warn me before a trade reaches a level | A trade is getting close / near its stop or its second target | COMI is closing on its stop | 1.8% above the stop at 79.00. |
| `callAlertsEnabled` | Tell me when a stock reaches a buy zone | A stock drops into a buying range / for calls you have not bought | COMI is in its buy zone | Trading at 82.90, inside 82.00 to 83.50. |
| `sessionDigestEnabled` | Tell me what the session did | How the day went / once, after the market closes | 9 Nov · what the session did | 3 reached a target, 1 stopped out. 2 of them were yours. |
| `overdueRemindersEnabled` | Remind me about trades kept open past their deadline | A trade is past its time / once a day until you decide | 1 trade is overdue | Kept open past the deadline with no sale recorded yet. |
| `feedAlertsEnabled` | Tell me when the price feed goes quiet | Prices stop arriving / so a quiet app is not mistaken for a quiet market | 2 stocks have no usable prices | ABUK, SWDY have gone quiet, so 3 calls cannot be judged. |
| `reportReadyEnabled` (Stage 5) | Tell me when a report is ready | A report is ready / when a run finishes or fails | Your report is ready | 12 calls found |

  Sample titles copy the real notifiers' shapes (`TradeStatusNotifier:79`, `ApproachNotifier:73`, `CallAlertNotifier:78`, `SessionDigestNotifier:80`, `OverdueNotifier:59`, `AttentionNotifier:71`, and Stage 5's `ReportReadyNotifier`).
- **`GeneralPage` (1107-1175):** no change.
- **`DataPage` (1180-1319):** remove the Token usage group (1260-1274). `Group("Diagnostics", summary = "A device copy for chasing a bug")` (1276-1288) becomes `Group("Something not right?", summary = "Save a report of what the app was doing")`.
- **`DiagnosticsControl` (1368-1424):** button "Save diagnostics" becomes "Save a report" (1403). Note text at 1380 "Save diagnostics puts it" becomes "Save a report puts it". Failure text at 1413 becomes "Could not save the report".

### `ui/InfoSheet.kt`

- `InfoNote` (60): add `val sample: NotificationSample? = null`, plus `fun InfoNote.withSample(kind: NotificationKind)`. `infoNote(` keeps its signature, so the `InfoNoteTest` lint still parses every call.
- `InfoSheet` (138-164): after the paragraphs tile, when `note.sample != null`, draw "What it looks like" and `NotificationPreview(sample)`.
- `SettingToggle` (258-287): new `detail: String? = null`, drawn `bodySmall` muted under the label inside the same weighted column.

### New `ui/NotificationSamples.kt`

- `data class NotificationSample(title, text)`.
- `enum class NotificationKind(label, detail, sample)` with the seven rows above. Pure, no composables in the enum.
- `@Composable NotificationPreview(sample)`: a `Panel` on `palette.raised` with "EGX Analyzer", the title in bold and the line in muted. No Material beyond `Text`.

### New `ui/SettingsStatus.kt`

Pure `settingsStatus(...)` returning `SettingsStatus(headline, lines: List<StatusLine(text, ok, section)>)`, testable without composing.

## 2. What feeds the status tile

| Line | Source (`AppState.kt`) | Good | Bad |
|---|---|---|---|
| Telegram | `telegramAuthState.step == READY` (139) | Telegram is connected | Telegram is signed out |
| Reader | `credentialVerified` (132), `cloudConfiguration.hasCredential` and `.model` (126) | The reader is ready (`true`, model set) | The key was rejected (`false`); No key saved yet; The key is saved but not checked (`null` with a key; neutral, not a fault); No AI chosen |
| Prices | new `lastPriceRefreshAt`, `pricesRefreshing` (196), `priceHealth` (195) | Prices arrived `whenLabel(...)` (`PricesSection.kt:396`) | Fetching prices… (neutral); Prices not fetched yet (0; neutral); Prices last arrived yesterday 14:45 (Cairo day before today and not refreshing); N of M stocks not coming through (`!priceHealth.clean`, which outranks the time) |

Headline: "Everything is working" when no line is bad; otherwise "1 thing needs a look" / "N things need a look".

Tile summaries also read `channels.size`, `opinionSearchEnabled()`, `opinionNewsWindowDays()`, `marketRefreshEnabled` (228), `appPreferences.defaultTradeWindowSessions` and `.themeMode`, `telegramSyncEnabled` (646), `backupFolder` (234), `savedResults.size` and `updateState`.

## 3. State

- **`PageState.kt`, after line 135:**
  - `settingsAdvancedOpen: MutableState<Boolean> = mutableStateOf(false)`. A fold while editing the endpoint must not shut the row.
  - `settingsNotification: MutableState<NotificationKind> = mutableStateOf(NotificationKind.TRADE)`. The tabletop selection.
- **`AppState.kt`, beside 230:** `val lastPriceRefreshAt: Long`.
  - Reason: `marketRefreshNoteAt` is stamped on skipped and failed fires too (`LiveAppState.kt:3066-3079`) and only while the refresh is switched on, so "prices arrived" built from it would be false.
  - `SettingsRepository.lastPriceRefreshAt()` (`SettingsRepository.kt:386`) is already persisted and written only when the feed answered (`LiveAppState.kt:3393-3404`).
  - `LiveAppState`: a `mutableStateOf` seeded from it and re-read right after `recordPriceRefreshDay` (3402). `FakeAppState`: default `0L`.
- No new setting is persisted in this stage.

## 4. Postures

- **Cover** (`else`, 162-170): same branch; the hub is the new one.
- **Unfolded and Book** (116-133): same `SplitColumns`. `SettingsStatusTile(compact = true)` goes above the `SectionNavTile` column; the rest is unchanged.
- **Tabletop** (135-160): replaces the `chunked(2)` grid.
  - `shown = open ?: SettingsSection.TELEGRAM`.
  - Upper `Column(heightIn(min = tabletopHeroHeight(posture)))`: for NOTIFICATIONS, `NotificationExplainer(kind)` (the label "Notifications · selected", `kind.label`, the note's paragraphs, and `NotificationPreview`); for any other section, the full `SettingsStatusTile`.
  - Below the hinge: a `FlowRow` of `KitChip`, one per section. It wraps and does not scroll sideways (the pager rule). Then the page.
  - On NOTIFICATIONS the page is a two-column grid of `NotificationKey` tiles. The key body is `clickable` and selects. The `KitSwitchFace` is a separate `toggleable(Role.Switch)` with `contentDescription = kind.label`; it toggles and selects. No "?" in tabletop, because the explanation is already above the hinge.
  - The selection is `PageState.settingsNotification`, default the first row.

## 5. Tests

- **`SettingsPageTest`:**
  - "Telegram not connected" (lines 71, 74, 77, 94) no longer exists. Use "Telegram is signed out".
  - Line 72: "Notifications" now matches the tile and nothing else; keep.
  - Lines 89 and 114: "General" becomes "Prices and trades".
  - Line 101: "Data & backup" becomes "Backup and sync".
- **`TelegramSyncToggleTest:56`:** "Data & backup" becomes "Backup and sync". "Sync now" at line 59 is now unique on the page.
- **`SettingToggleTest`, `SectionCardTest`, `PricesSectionTest`, `InfoNoteTest`:** unchanged.
- **New `SettingsStatusTest`** (pure): all good; signed out; rejected; no key; never fetched; fetched yesterday; feed quiet outranks the time; headline plural.
- **New `NotificationSamplesTest`:** `NotificationKind.entries.size == 7`, no blank label or sample; opening "?" on "How the day went" shows "What it looks like" and the sample title.
- **`SettingsPageTest` additions:**
  - "Advanced" starts closed: "HTTPS API endpoint" is absent, present after a click, and still present after `generation++`.
  - "A report is ready" is on the page.
  - Dark theme: the hub composes and "Everything is working" exists with a READY, verified fake.
  - `Tabletop` posture: pressing a key body changes the explainer title and toggles nothing.

## 6. Where the plan or mockup cannot be followed, and what to do instead

1. **The plan said "About page" for diagnostics.** Diagnostics lives on the Data page (1276). Relabel it there; do not move it.
2. **The mockup merges theme into "Look and about".** Appearance stays inside GENERAL, now titled "Prices and trades". Lead that tile's summary with the theme name so it can still be found.
3. **Token usage sits on the Data page, not Reader.** Moving it under Advanced is the one cross-page move. `refreshModelUsage()` then only runs once Advanced is opened, which is fine.
4. **The hub's "Sync now" button (253-261) goes with the Telegram tile.** It stays on Backup and sync.
5. **The mockup's lime check discs on the ink status tile are the same 1.03:1 fault in dark.** Use tinted icons via `tileSignals`.
6. **Mockup items not built:** "12 minutes ago" (use `whenLabel`), "How much it has read" (runs and Ask AI answers), "Backed up today", and the masked key with "Replace".
7. **Stale pointers to update in Stage 7:** the KDoc at `PricesSection.kt:32`; `docs/ui.md:521-543`; `CLAUDE.md:175`.

## 7. Owner decision (default assumed if not answered)

1. Theme stays under "Prices and trades". Assumed yes.
