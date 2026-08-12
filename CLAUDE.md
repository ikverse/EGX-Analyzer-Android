# EGX Analyzer — Android

Reads Egyptian Exchange stock recommendations out of Telegram channels, extracts them with a cloud
model, scores every call against real prices, and ranks the channels on what they actually
delivered. The Windows desktop counterpart was retired on 2026-08-12 at v0.1.126 — its source is
kept in the sibling repo, unchanged and unreleased. This is the only app under development, so
nothing here is constrained by keeping the two in step.

## Working agreements

- **Never implement without approval.** State the change as a list and wait for the literal word
  "approve". "ok", "do it", and a refinement are not approval.
- **Never start an analysis run.** They cost the owner cloud credits and send their Telegram content
  to the provider. Build, install, open the app to the right screen, and hand over.
- **No on-screen verification unless asked.** Build plus unit tests is the loop. Screenshots are
  expensive twice over: once when taken, then again on every later turn of the session. Worth it
  when a layout complaint cannot be diagnosed any other way; not for confirming an install.

## Commands

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | grep -E "^e:|FAILED|BUILD"
```

Filter Gradle output rather than dumping it — raw build logs were the single largest source of
context growth in an earlier session.

```bash
export ADB="$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Wrap every adb call in `timeout N` — a device that drops mid-command leaves adb blocking on
`- waiting for device -` until the tool times out.

Screenshots need the display id; plain `screencap -p` fails with "Multiple displays":

```bash
"$ADB" -s emulator-5554 shell "screencap -p -d 4619827259835644672 /sdcard/s.png"
```

On the Fold 7 the numbering runs the opposite way to the guess: the **cover** panel is `displayId=0`,
physical id `4630946872173396372`, and the **inner** panel is `displayId=1`, `4630946449689556883`.
`dumpsys display | grep uniqueId` reads them back as `local:<id>`. Whichever panel is shut reports
`isActive=false`, which is the cheapest way to tell whether the phone is open without asking.

## Devices and their real geometry

| Device | Serial | Width |
|---|---|---|
| Fold 7, unfolded | `RFCY70BT1VP` | 1968×2184 @420 = **750 dp** |
| Fold 7, cover | same | 1080×2520 @420 = **411 dp** |
| Huawei tablet | `5DKBB25B27201723` | 1840×2800 @360 = **818 dp** |
| Emulator AVD | `EGX_Pixel_10_Pro_Fold_API_36` | pin with `wm size`/`wm density` |

The emulator's own inner display is 851 dp, which is **wider than any real device here**. Layout
thresholds verified only at 851 dp have shipped broken to the 750 dp Fold before. Pin it:

```bash
"$ADB" -s emulator-5554 shell wm size 1968x2184; "$ADB" -s emulator-5554 shell wm density 420
```

The AVD config lives at `~/.android/avd/EGX_Pixel_10_Pro_Fold_API_36.avd/config.ini`. It must keep
`hw.gpu.enabled=yes` and `hw.gpu.mode=host` — with software rendering the emulator crawls badly
enough that taps land seconds late. Cold-boot with `-no-snapshot-load` rather than wiping data.

## Where things live

- `data/AnalysisRepository.kt` — builds the cloud request, chunks sources, harvests the answer.
- `data/AnalysisChunking.kt` — 8 images per request. Beyond ~32 the model loses track of which
  image it is citing, which produced exclusions naming the wrong card.
- `data/ConsolidatedParser.kt` — the model's JSON into `ConsolidatedRecommendation`.
- `model/Scoring.kt` — how a call is judged. See below.
- `data/PerformanceCalculator.kt` — per-channel and per-session rollups, and the ranking.
- `data/PortfolioCalculator.kt` + `model/Position.kt` — the trades the user actually took. See below.
- `data/AnalysisPolicy.kt` + `data/RuleSet.kt` + `data/BuiltInRules.kt` — the local wording filter.
- `data/PromptComposer.kt` — generates the prompt sent to the model.
- `data/ReportSync.kt` + `data/RuleSync.kt` + `data/PositionSync.kt` — what travels between devices.
- `ui/PortfolioScreen.kt` + `ui/TradeControls.kt` — the Portfolio tab, and the Bought button and
  closing controls that sit on a recommendation card.
- `ui/` — one file per screen, plus `CommonUi.kt` and `DesignSystem.kt` for shared pieces.

## Scoring, and why each rule is there

A call is replayed over the next N trading sessions (`Scoring.DEFAULT_WINDOW_SESSIONS` is 10,
adjustable in Settings).

- The entry must trade before anything else counts.
- A stop counts as broken only past **2%** — the channels themselves say
  `يتاكد بالكسر بنسبة 2%`. Applying it turned 26 stop-outs into 7.
- Entries and targets compare with a small epsilon because Yahoo sends **32-bit floats**: 1.03
  arrives as `1.0299999713897705`, and comparing exactly recorded a reached target as a stop-out.
  The slack is a millionth — it undoes storage noise, it is not a tolerance.
- A session that reaches a target **and** breaks the stop is a partial hit that fell back, not a
  loss. Daily bars cannot prove the order; this credits the favourable one deliberately.
- Prices that are not positive are stripped before scoring. A session in progress can arrive with a
  high of zero, which force-stopped every call on that stock.
- **Judged** outcomes are full hit, partial hit, stopped, expired. Still open, entry never traded,
  ambiguous and not priced say nothing about the channel and are excluded from every rate.
- A channel needs `MINIMUM_JUDGED_TO_RANK` (5) settled calls before its rate is allowed to lead.
  Without it, two good calls beat a month of evidence.

## The portfolio

Pressing **Bought** on a recommendation card records a `Position`; the Portfolio tab is where every
trade is then managed, in whatever state it has reached.

- The **deadline belongs to the recommendation**: N trading sessions from the session the call was
  made for, whatever date the user bought on. Buying two days late does not buy two extra days.
- Status comes from `Scoring.score` with **no entry band** - the user has bought, so the entry is a
  fact rather than something the market must still offer - over only the sessions actually held. A
  target reached before the purchase is not the holder's gain. This is why the 2% stop rule and the
  float slack are identical to every other judgement the app makes.
- **Every percentage is measured from the user's own prices.** Closed by hand it is realized, from
  the price they gave; otherwise it is an estimate, marked at the stop, the target, or the last
  close, and labelled as an estimate.
- **Channel hit rates are deliberately not affected.** Insights judges the source on the levels it
  printed, not on what the user did about them — the two answer different questions, and a channel's
  record must not move because someone bought late or sold early. A card the user is in gets an
  outline and one extra line, and nothing else moves.
- A trade **snapshots its levels and its window**. Deleting the report, re-running the session, or
  changing the global scoring window afterwards must not rewrite a trade that already happened. The
  window is editable **by hand and only by hand**, from Edit trade on the position's card — that is
  the user moving their own deadline on purpose, which is the opposite of a setting moving it
  silently. Moving it can close a running trade or reopen one the deadline had closed.
- The buy dialog offers the global scoring window and lets it be overwritten. `windowCustom` records
  that the user typed over what they were offered, rather than being recomputed by comparing against
  the setting later: the setting moves, the choice did not.
- **Keep Open defeats every automatic close but target 2** — target 1, a stop, and an expired window
  all stop ending the trade; only a recorded sale or a full target hit does. A full hit is the trade
  doing the thing it was bought to do, so there is nothing left to hold it open for, and the button
  is not offered on one. `marketStatus` goes on saying what the market did, so the card still reads
  "the call itself: stopped out". The one status that is rewritten is `EXPIRED`, which would
  otherwise put the word "Expired" on a card sitting in the Open section. `PositionView.keptOpen` is
  `position.keepOpen && open`, so the flag survives on the row but nothing claims a closed trade is
  being kept open; the pill carries the state and the card's ⋮ menu undoes it, because a button
  repeating the pill was taking the place beside Sold.
- **`ranOutOfTime` is the one rule behind both the Expired section and overdue**: the deadline has
  passed, no sale was recorded, and the trade ended at neither target 2 nor the stop. A trade that
  reached its target did what it was bought to do; one the stop took out ended where the call said
  to; one the user sold ended where they say it did. What is left ran out of time while they were
  still in it, and that is the only case worth chasing. It deliberately covers the trade the deadline
  closed while the user was still holding it, and a partial hit that never saw target 2 — which
  keeps its own "Partial target hit" chip, because "Expired" would hide that it got somewhere.
  Counted in calendar days from `deadlineDate`, with `today` injected into `PortfolioCalculator` so
  it can be tested at all. Zero on the day the deadline lands: expiring today is not being late.
- Nothing about overdue is cached: it is derived on every recompute from the stored deadline and the
  current date, which is what makes it right after a restart. A `LifecycleResumeEffect` in
  `MainActivity` recomputes when the app returns to the foreground, so a phone left on the Portfolio
  tab overnight does not go on showing yesterday's count.
- **One card per session, holding that session's trades in every state**, with **Open**, **Expired**
  and **Closed** as sections inside it. A day's trades were one decision, and splitting them across
  an open list and a closed one meant scrolling to find the other half. A card holding something
  still running starts expanded; the rest start folded. Its summary names all three counts, each in
  its state's colour — open is `primary`, expired is the **amber in `ExtraColors`**, closed is
  `onSurfaceVariant`, and the chips in `TradeControls` use the same three so a section and the trades
  under it agree. Expired is deliberately neither red nor purple: a trade that ran out of time can be
  up 5%, so error red would report a loss it never made and would collide with Stopped out, and
  `secondary` is a hue that means nothing in this app. Amber is **added** to the palette rather than
  borrowed from it, because every scheme role is already spoken for — see `ExtraColors` and
  `LocalExtraColors` in `theme/Theme.kt`, provided by `EgxAnalyzerTheme` so it follows the app's own
  light/dark setting rather than the system's. The overdue pill keeps `errorContainer`: amber says
  out of time, red says and you are late.
- **`PortfolioOrder` holds the sort rules, for the screen and the calculator both.** `URGENT` is the
  default and what `PortfolioCalculator` groups by, so anything reading the portfolio without a
  screen gets the order the screen opens on. It sorts overdue-first then newest — date order alone
  was precisely backwards, because a trade is overdue *because* its call is old. The two date orders
  carry no such override, on purpose. Every option sorts **both** levels: cards by session date,
  positions inside a section by entry date, which is the only date that separates two trades taken
  on one call.
- The Portfolio's **date filter and sort reuse Results' own controls** — `SingleSelectFilter` over
  the sessions actually held, and `SortFilter`, both inside a `FilterRow`. **Filtering happens in the
  screen, never in `PortfolioCalculator`** — `OverdueWorker` raises the daily reminder off the whole
  record, and a date picked on screen must not silence it. The record card above the row ignores the
  filter; it means the whole record.
- **The order is stored, the date filter is not**, and the difference is what each does to the
  screen. An order hides nothing, so `AppPreferences.portfolioOrder` keeps it across restarts —
  written by **name**, because storing an enum by ordinal would silently reinterpret every install's
  choice the moment the options are reordered, and `enumPreference` falls back to `URGENT` on a value
  this build no longer knows (`SettingsRepositoryTest` covers both). A date filter that persisted
  would greet someone weeks later showing one session and nothing else, which is how a user concludes
  their trades have gone missing — so it stays a session-only `remember`, like Results'.
- **A kept-open trade past its deadline sits under Open, not Expired**, and carries the overdue pill
  there. Expired is where the app parked a trade it stopped tracking; putting that word over one the
  user deliberately kept open would undo the feature. So Expired ⊆ overdue, but not the reverse.
- `PortfolioStats` says **settled**, not closed, and counts every trade no longer running, expired
  ones included. The win rate and the averages have to, or they stop describing the record — a trade
  that went nowhere for ten sessions is a result. "Closed" is reserved for the section, which means
  what the user means by it: sold by hand, taken by the stop, or the targets reached.
- `OverdueWorker` is the **only thing that runs while the app is closed**: once a day, no network,
  no Telegram, and it must never start an analysis. It reads `LocalDataStore` directly rather than
  through `AppState`, which would drag a Telegram session up with it. Off via a Settings checkbox,
  which cancels the work rather than letting it wake up and find nothing to say.
- Positions **travel as revisions**, like wording rules and unlike reports. A position's id is
  derived from the call - `AMOC@2026-07-20` - so the same trade recorded on two devices is one
  holding rather than two that can never be reconciled. A delete is a revision too, so a later edit
  overtakes it.
- `PortfolioCalculator` computes `PortfolioStats` (win rate, averages, best and worst) whether or
  not the screen draws them, so a new figure is a UI change. There are no trade sizes, so every
  total is an average of percentages; a money total would be invented.

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

- Reports are append-only: a saved run never changes, so syncing them is a union.
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
- `searchChatsOnServer` does not find private supergroups. Read `chatCache` instead — getting this
  wrong created three duplicate sync channels in the owner's Telegram.
- **Settings travel as one revision each**, newest wins, tie broken by device — the same rule as
  rules and trades, in `SettingsSync.kt`. This is what makes a reinstall survivable: an install that
  has never saved a setting has a stamp of zero, so it defends nothing and takes everything.
  Published on change through `AppState.publishSettings`, coalesced by three seconds because the
  scoring window is a slider and would otherwise upload every value it is dragged across. Two things
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
  makes the app eligible to ask; the user still grants "install unknown apps" on a system page, and
  `canInstall()` is read **at the tap**, because returning from that page does not recompose.
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
- **Nothing new runs in the background.** `OverdueWorker` is still the only thing that runs while
  the app is closed.

## Gotchas

- `local.properties` holds `telegramApiId` / `telegramApiHash` and is gitignored. Absent, the app
  falls back to asking for them, so a fresh checkout still builds.
- `Uri` is stubbed in unit tests; tests that need inputs use `AnalysisInput.Text`.
- `LocalDataStore.DATABASE_VERSION` — bump it and add the table to **both** `onCreate` and
  `onUpgrade`. Currently 10.
- **Migrations are tested** — `LocalDataStoreMigrationTest` runs under Robolectric, which supplies
  enough of Android for a real SQLite database in a plain unit test. It writes the version-9 table
  by hand and upgrades it, deliberately: a test that builds its "old" schema from today's code
  tests nothing, because both sides move together. Add a case there for every version bump. Note
  Robolectric coexists with the explicit `org.json` test dependency, which was the risk when it
  went in. **Robolectric needs Java 21** to stand up a sandbox for SDK 36 — it refuses on 17 with
  "requires Java 21 (have Java 17)", which is a green run locally on the JBR and a red one anywhere
  pinned lower. CI pins 21 for that reason.
- `PriceRepository` fetches **from where a stock's stored history stops**, via `period1`/`period2`,
  not a fixed range. It used to ask for `5d`: a phone left shut for a week got a hole that every
  later refresh stepped straight over, permanently — and a call whose window contains a hole never
  completes, so it never expires and never shows as overdue. A stock with an open trade is fetched
  from that trade's call date instead, which heals holes that already exist. An empty dated response
  falls back to `range=1y`, so the change cannot do worse than the fixed range it replaced.
- Scrollbar overlays must be applied **outside** the scrolling node, or they are measured against
  the content and slide away with it.
- `NavigationSuiteScaffoldLayout` does **not** consume window insets for its content; the full
  `NavigationSuiteScaffold` does, through a private helper the layout never calls. Left alone the
  page pads itself clear of the gesture strip that the bar below it is already holding, and the
  cover screen shows a band of dead chrome the width of the strip — 15dp on the Fold 7.
- **The signing key is the update.** Android refuses an update signed by a different key than the
  install it would replace, so every release has to carry the same signature and the key has to
  outlive the machine that made it — it lives in GitHub secrets (`EGX_KEYSTORE_BASE64`,
  `EGX_KEYSTORE_PASSWORD`, `EGX_KEY_ALIAS`), and locally in `local.properties` as
  `EGX_KEYSTORE_FILE` and the same two names. Absent, the release build is simply unsigned rather
  than failing, so a fresh checkout still builds. The **move from debug-signed to release-signed
  builds costs one uninstall on every device**, and the order matters: sync first, then uninstall,
  then install the release APK, then sign in to Telegram and let the launch sync bring the record,
  the trades, the rules and the settings back. Only the API key is retyped by hand.
- Release: bump `appVersionName` in `app/build.gradle.kts` (versionCode is derived), commit, tag
  `vX.Y.Z`, push both. The tag is the whole process — CI runs the tests, signs the release APKs, and
  publishes them as a GitHub release, which is where the app looks. A tag whose tests fail publishes
  nothing.
- **`-PabiSplits` is passed by CI and nowhere else.** The ABI split is off by default on purpose:
  enabled everywhere, an ordinary `assembleDebug` would stop producing `app-debug.apk` and start
  producing one file per architecture, breaking the install command above and the CI artifact. To
  reproduce what a release ships, pass the flag by hand.
