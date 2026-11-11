> **5.0.0 redrew every surface in the Bento kit** — see **Design language** in `CLAUDE.md`. The
> behaviour described here (back, the stock sheet, the header, filters, the status line, where an
> explanation lives) still holds; where a passage names a pre-5.0 element - a 6dp pill,
> `ActionPill`, `SettingsButton`, `SectionCard` as a bordered card, amber, glass, the rail or the
> floating bar - it is history, kept for the reasoning.

## What back does, and the stock sheet

Two things the app could always have done and never did: go back, and put one stock in one place.

- **Back had no handler at all.** `openCall`, `openPosition` and `openSavedResult` throw the reader
  across tabs — from a card, from a digest tile, from a notification — and the system's back button
  answered every one of them by closing the app. `NavStack` is one step of history: the tab a jump
  left, and what to reveal on arriving back at it. `AppState.goBack` spends it, then falls through
  to clearing the current tab's filters, then to the system. **The jump before the filter**,
  deliberately — both can be outstanding at once and the jump is the more recent, so answering the
  filter first would strip a narrowing the reader set up on purpose while leaving them on a tab they
  did not choose.
- **One deep, and that is the design.** The five destinations are peers; a back button that walked
  back through a morning's tab presses would take a dozen presses to leave. What was missing is the
  *last* jump the app made on the reader's behalf, and nothing else. A tab the reader chose is not a
  jump, so `navigate` clears the stack rather than adding to it — guarded on the destination actually
  changing, so a second `navigate` to the tab already showing cannot throw the return away.
- **A notification records no return.** It arrives at a tab the reader was not on and usually at an
  app that was not running, so there is nowhere to go back to; recording one would land the first
  back press on the Analyze tab they never visited.
- **A filter is cleared whole**, not one control at a time. Three presses to undo three chips would
  be back re-enacting the reader's typing, and the screen's own Clear filters — two presses away
  inside the folded panel — clears them together too. `PageState.filtersActive` and `clearFilters`
  are the one predicate, read by the three screens *and* by the shell, because three screens each
  stating their own version is three that agree until one gains a filter.
- **`StockSheet` is a sheet, not a sixth destination.** A ticker's story was spread across four
  screens and the only way to gather it was to type the same code into three search boxes.
  `StockSearch` had already made those boxes ask one question; this is where the answers meet. A
  sheet on `ChannelScoreSheet`'s terms because a stock is not a peer of Analyze and Settings — it is
  the longer version of a thing that was pressed, which is what a sheet from the bottom already means
  here. It also keeps back simple, since `ModalBottomSheet` takes the press first, and it opens from
  any tab without moving the reader off the one they are reading.
- **It states two things that are new, and the rest was only ever scattered.** `StockScore` has been
  computed for every stock since it was written and reached the reader only through the shortlist
  signals and the Ask AI prompt — so "what happens when anybody recommends this stock" was a question
  the app could answer and no screen asked. `PriceChart` is the other: every price anywhere else in
  this app is a single figure, so whether a stock has been climbing for a month or fell off a cliff
  last Tuesday was the one thing the record held and no screen drew. It reads
  `AppState.priceHistory`, which is a **suspending disk read** off `LocalDataStore.sessionsFrom` —
  the report keeps one session per stock, so a line built from it would have a hole wherever nobody
  happened to have made a call — asked for after the sheet is already on screen, so the rest of it
  never waits behind a query. Everything else on the sheet is drawn somewhere else already; what it
  adds is that they are drawn together. Rows lead through `openPosition` and `openCall`, the two
  entrances every cross-tab press already uses, and dismiss the sheet first: one left open over the
  tab it just sent the reader to is covering the card it sent them to read.
- **A header, a scroller and an action bar**, rather than one column of dividers. The heading and the
  price are what the sheet was opened to see and no longer scroll away; the record, the trades and
  the calls run between them, each in a card of its own on `surfaceContainerHigh`; and the two things
  a reader does about a stock sit on the bottom edge. The record's rates carry `OutcomeBar`, which
  now takes a `CallTally` as well as a `ChannelScore` — both reduce to the same private `Verdicts`,
  so a stock's band and a source's band are one drawing and cannot come to disagree. The calls list
  is **capped at five** with a press that counts the rest: a stock the whole channel list likes
  carries thirty, and thirty rows push the record and the trades off the top of a scroll opened to
  compare all three.
- **The chart carries the levels, and that is what makes it a decision rather than a picture.** The
  stop, the entry band and both targets sit on the same scale as the price, so the gap between the
  line's right-hand end and each of them is a length rather than arithmetic done in the head over a
  column of figures on another card. **Whose levels** is decided the way the rest of the app treats a
  held stock: the reader's own trade where they hold one — snapshotted at the purchase, so re-running
  the analysis cannot move a line under a trade already taken — and the newest call's otherwise, with
  a caption naming which. Behind a `Levels` chip, absent rather than disabled on a stock nobody has
  called. **The scale may grow to fit them by at most `MaxScaleGrowth` (2.5) of the price's own
  span**: honouring a target the stock has been nowhere near would flatten a month of real movement,
  so a level beyond that is pinned to the edge and marked with an arrow, which is the rule
  `PriceLadder` already follows in the other direction. A **ring on the line wherever a call was
  made**, in both states — nothing else in the app can show whether the channels name this stock near
  its tops.
- **The line is read by touching it.** A tap or a drag along the chart picks the nearest session -
  **snapped to whole sessions**, because a close is one figure a day and a price read off the gap
  between two of them is a price nobody quoted - and the caption under the chart becomes
  `4 Sep · 86.40 · +12.4% since 12 Jun`, or names the source where that session carries a call. It
  **replaces the dates' own line** rather than appearing above it: a caption that arrived on touch
  would push the chart up under the finger that asked for it. The reading **stays after the finger
  lifts** and is cleared by changing the range, which is a `remember` keyed on what is drawn rather
  than a rule anybody had to write.
- **Two gesture detectors, and the split is what keeps the sheet scrollable.** `detectTapGestures`
  reads without claiming anything; `detectHorizontalDragGestures` claims only once the finger has
  moved **horizontally** past touch slop, so a straight-down swipe over a 150dp band still scrolls
  the sheet behind it. `pick` is held in a `rememberUpdatedState` because `pointerInput` is keyed on
  the points and does not restart when the selection changes - a lambda captured when the chart was
  first drawn would go on comparing against the index chosen *then*, which is a haptic tick and a
  state write on every pixel of a drag rather than on every session crossed. The tick is
  `HapticFeedbackType.TextHandleMove`, the platform's own for a marker being dragged along a track,
  and it is the only haptic in the app.
- **The range row is calendar time and one fetch.** `1W` `1M` `2M` `3M` `6M`, measured back from the
  **newest session the app holds** rather than from today's date — the right edge of the line is that
  session whatever the calendar says, and a week counted from today on a feed three weeks behind
  would draw an empty box for a stock whose prices are merely old. Six months is fetched once when
  the sheet opens and every shorter range is a slice of it, so no press reads the disk. The row
  scrolls sideways (`scrollableRow`) because five chips plus the toggle is about 290dp inside the
  355dp a card leaves on the cover screen — it fits with nothing spare, and a large font scale would
  otherwise clip a chip off the end. **A weighted child cannot go in that row**: it scrolls, so its
  width is unbounded and a weight cannot be measured. Both choices live in `PageState`, so folding
  the phone does not put a reader who has just pressed 6M back on a month of line.
- **Expand opens the same line as a TradingView-style chart** (`ExpandedChart.kt`, since
  2026-11-09). The toolbar is two rows of the same chips: the range, a chart-type menu (Candles,
  Hollow candles, Bars, Line, Area), **Indicators** (a sheet of switches: MA, EMA, Bollinger Bands
  and Parabolic SAR over the price; Volume, MACD, RSI and KDJ in panes below it), **Draw** (a third
  row: trend line, ray, horizontal line and ray, vertical line, parallel channel, Fib retracement,
  note, then Undo and Clear all) and Levels. On the chart itself, as TradingView puts them: the
  session's O H L C, change and volume across the top, an × beside each indicator's legend, A / L /
  % under the price axis, and `»` back to the newest session once it is off screen. One finger pans,
  two pinch, a long press leaves a crosshair, and both axes stretch under a finger. The indicators
  and each stock's drawings are remembered on the phone (`ChartLayoutStore`) and go nowhere else;
  the chart type is not remembered and opens on candles. The header carries the last close and its
  day's move. Range stops at 6M, because that is what the sheet fetches.
- **The header names its own figures.** `LAST CLOSE` over the price, or `LATEST PRICE` on a session
  still trading, and the move under it in **pounds as well as percent** — the app prints a price move
  in money nowhere else, and on a stock trading at 0.24 the percent is the figure that says nothing.
  The date reads "since 3 Sep", naming the session the move was measured *from*, which is the only
  wording that is true over a close dated the 4th.
- **The action bar is the call card's own two controls, against the newest call.** `AskAiButton` with
  the same confirmation and the same saved-answer sheet, and `TradeAction`, which is the app's only
  way of recording a purchase — this is one more surface asking it, not a second way of asking. A
  question about a stock is a question about what somebody said about it and a trade is recorded
  against the call it was taken on, so the bar names the call it is acting on underneath itself; the
  newest is the one a reader opening a stock this morning is acting on.
- **`LocalOpenStock` is a composition local, and that is a deliberate exception.** `onOpenTrade` and
  `onOpenCall` are threaded because they travel one or two levels and belong to the card offering
  them. A ticker is drawn on a call card inside a session card inside a band, on the same card from
  Results, on a position card inside a card, on the day's event tiles on two tabs, and in a table
  row — threading it would add a parameter to a dozen signatures to reach six leaves. It sits beside `LocalWindowWidth`, which is where the
  shell already publishes what every screen may need and no screen owns.
- **A card that already presses somewhere keeps its press, and the ticker becomes a second target
  inside it — but only where the card has room for two.** That was the rule the other way round until
  2026-09-07 — the ticker was a target only where nothing else was — which left several places a
  stock could be looked at and not opened. Two targets on one card, and the smaller has to be aimed
  at: the card is one thing that happened and the ticker is the stock it happened to, and a reader
  deciding about the first often wants the second. The **arrow stays outside** the inner press, since
  it is what says the card itself leads somewhere. **Four leaves carry it** — the recommendation
  card, the results table, the position card and the Insights call card — which is the count
  `LocalOpenStock` exists to keep out of a dozen intermediate signatures.
- **The two tile grids are the exception, and were the rule until 2026-09-08.** The Overdue tiles
  and the day's event tiles had the inner press and lost it: they are the smallest things in the app
  that lead anywhere — 150dp and 170dp minimums, two or three across a cover screen — and the ticker
  sits at the leading edge, exactly where a thumb reaching for the tile lands. The owner reported
  missing the trade and getting the stock sheet instead, often enough to ask for it back. A target
  that is hit on the way to another target is not a second affordance, it is the first one made
  unreliable. Nothing is unreachable: both tiles open a trade or a call, and both of those carry the
  ticker press. Size is what decides this, not what kind of thing the card is — a tile grid gets one
  target, a full-width card can hold two.
- **A sheet holds still while its content is read - `Modifier.sheetDragSlop`.** `ModalBottomSheet`
  hands a downward drag its content could not use straight to the sheet, and the sheet hides on
  56dp of travel or a flick of 125dp/s. A record already scrolled to its top uses none of the pull,
  so "let me see the start again" was closing the stock sheet under the reader's finger - reported
  2026-09-11. The two thresholds are settable only through `rememberSheetState`, which is `internal`
  to material3, so a 64dp slop is held in front of the sheet instead: a nested-scroll connection on
  the scroller swallows that much downward drag, and the leftover fling velocity with it, before the
  sheet is allowed to move. It re-arms whenever the content actually scrolls, so a long record read
  back to its top arrives with the whole 64dp in hand. **On the scroller and not on the sheet**, and
  not by raising the thresholds, because both of those also govern the drag handle - a handle that
  has to be dragged half a screen is a handle that looks stuck. All seven sheets with a scroller carry
  it: the stock sheet, the occurrence sheet, the filter sheet, Info, Channel score, Stock opinion
  and Edit call.

## The page header

Every screen is topped by its own name and the destination's own icon, shrinking into a bar as the
page is read. It replaced the band that said `EGX Analyzer` above all five tabs, removed 2026-09-09
along with the rounded well the page used to sit in.

- **The band said the same two words on every tab and cost a row of the window to do it**, while
  what a reader actually needs at the top — which page this is — was the first line *inside* the
  scroll and left the moment they read anything. The two swapped places: the page name is the
  header, and it is the thing that stays.
- **One title shrinking, not two cross-fading.** Material's large app bar fades a big title out and
  a small one in, which is two titles briefly drawn over each other. `PageHeader` interpolates the
  size (30sp → 20sp), the tracking and the icon (30dp → 22dp) on one collapse fraction, so the name
  gets smaller and stays put. It costs a recomposition of that row per frame of the collapse and
  nothing below it, because `collapse` is passed as a **lambda** — read at `Screen`'s call site it
  would recompose the whole page instead. Same rule `PageWash` follows from the draw phase.
- **The header eats the scroll rather than riding it.** A `NestedScrollConnection` in `Screen`
  consumes the first `HeaderCollapseTravel` (40dp) of every downward gesture before the page is
  offered any of it. Without that the content moves at twice the speed of the finger over those
  40dp, because the page rises by whatever height the header gives up *on top of* its own travel.
- **It grows back only with the page at its top.** Expanding on any upward delta pops the title open
  mid-page, and on the three screens that pull to refresh it would fight the gesture — this
  connection is the outer one, so it sees a drag before `PullToRefreshBox` does. Gated on
  `scroll.value == 0` the two take turns in the order a reader expects: the header first, then the
  refresh. Pressing the tab you are already on expands it alongside the scroll, or the press would
  strand the one piece of chrome it is aimed at.
- **The title and icon both come from `AppDestination`.** `Screen` takes the destination rather than
  a title string, reads `label` and `selectedIcon`, and tints the glyph `primary` — which inside a
  page is that page's hue. So the glyph at the top of Analyze and the lit glyph in the navigation
  are one drawing in one cyan and cannot drift. The **selected** icon, because the page you are
  looking at is the selected one.
- **The page name left the text column, and that is a real cost.** `PageTextInset` puts every
  heading on a page in one column an inset in from the card edges, and the title used to sit in it.
  It cannot now: the icon takes the page-edge inset and the name sits to the right of it. The title
  left the page to become chrome, so it stopped obeying a rule about text on a page.
- **The two icons are on screen from the first frame, sized on the destination icon's own curve**
  (`lerp(ExpandedIcon, CollapsedIcon, collapse)`, 30dp → 22dp) rather than fading in over the last
  40% of the shrink at one fixed size — since 2026-09-17. The earlier version answered one gesture
  with two different animations: the title shrinking continuously and the icons cutting in late,
  and a page at the top of a long list gave no sign it could be searched or filtered until the
  reader had scrolled. Now the big header carries both controls at full size and they shrink into
  the collapsed bar's icons rather than appearing there — the same animation the page name already
  makes. Always pressable, because there is no longer a partial-fade frame for a stray tap to land
  on. A page that is filtered still carries a dot on the filter icon (`HeaderAction`'s `dot`
  parameter), but no longer needs a separate "arrive early" case to be honest about it — every page
  arrives early now.

### Swiping between tabs (since 5.1.3)

A sideways swipe on a page changes tab, and nothing slides: only one page is ever composed. While
the finger is down the header's emphasis moves toward the neighbouring tab's name and the page
under the header leans up to 24dp and fades slightly; a quarter of the width or a flick commits,
and the new page's tiles pop in exactly as after a press. Short of that it springs back; past the
first or last tab it stretches a little and returns. Anything below that scrolls sideways - chip
rows, the wide table, the charts, the tape, the tab row - keeps the gesture, because `TabSwipe`
reads the final pass and gives up on a consumed one. The full account is in CLAUDE.md under
**Gotchas**.

### The stock filter, moved into the header

- **It is the page's own filter, and since 2026-09-11 it is picked rather than typed.** Results,
  Insights and the Portfolio each drew a stock box on their filter shelf; the header's icon opens
  that same box, over the same state (`PageState.resultsStock` and its two siblings). What reaches
  that state changed: typing narrows a list of catalog listings (`TickerPicker`, `TickerPickerList`)
  and only a **pick** filters the page, so the filter always holds a real EGX ticker.
- **Free text was the fault it fixes.** A reader could narrow a page to `comi`, to `Commercia`, or
  to a misremembered spelling that matched nothing at all, and the page answered every one of those
  with an empty list indistinguishable from having no runs. A pick can only be a listing the
  exchange actually has, which is also what lets the empty states name the **company** —
  `TickerPicker.name` — instead of echoing whatever was in the box.
- **This is still not the lookup that was rejected on 2026-09-09.** That one *replaced* filtering:
  the box searched a directory and a press opened a stock sheet, and `StockLookup`, `DirectoryStock`
  and `AppState.stockDirectory` went with it. Here a press filters the page, and the sheet is a
  **trailing arrow on a row**, drawn only where the app holds a record worth opening — a listing
  nobody has ever analysed has no score, no calls and no trades, so `StockSheet` would open on a
  shell.
- **The arrow is at the end of the row and deliberately not on the logo.** The logo sits at the
  leading edge, which is where a thumb reaching for the row lands — the exact failure the Overdue
  and event tiles lost their inner ticker press over on 2026-09-08. A target hit on the way to
  another target is not a second affordance.
- **`pageStocks(appState, destination)` is what the list leads with.** The stocks the tab actually
  holds — runs for Results, scored calls for Insights, positions for the Portfolio — group under
  **On this page**, the rest of the catalog under **All other stocks**. It lives outside the three
  screens for `filtersActive`'s reason: the question is asked from above the screen that owns the
  answer. Passed to `PageHeader` as a **lambda** and called once when the list opens, or every page
  would subscribe to all three records and walk them on every recomposition.
- **The list is a `Popup` whose content covers the window below the field**, with a scrim under the
  card that takes every press aimed past it. Drawn as a sibling of the header it would be painted
  under the page; drawn inside it, clipped to a 56dp bar. `focusable = false` is what keeps the
  keyboard up, and it is why back is still answered by `PageHeader`'s own `BackHandler`.
- **It is drawn as tall as the window *above the keyboard*, not as tall as the window.** A popup is
  its own window and is handed none of this one's insets, so `SearchField` takes
  `WindowInsets.ime` off the measurement itself. Measured against the bare window the list ran to
  within a row of the keys on a tall phone and clean under them on a short one. `PickerMaxHeight`
  is a ceiling over that rather than the usual height.
- **The rest of the exchange is held back until something is typed.** An empty box offers **On this
  page** and a line saying where the other listings are; typing brings **All other stocks** with it.
  Two hundred-odd rows dropped over the page the moment the icon is pressed is a menu to be scrolled
  rather than read, and the half worth reading was the first few rows of it.
- **The box closes onto the pick rather than back to the title.** `PickedStock` draws the mark, the
  code and the company in the bar, and pressing it reopens the picker. The X and back clear the
  page. A press past the list means "never mind the **list**": a pick already made keeps the box and
  collapses it onto that pick, a half-typed query keeps the field and loses only the list, and only
  an untouched empty box goes away. `StockBox.listing` is what lets those be three answers rather
  than one — a press landing there while somebody is typing must not be able to undo their typing.
- **The three shelves lost their box**, which left each of them a Filters chip alone — a loose
  one-control row above every list, floating on a shadow, to open a panel. That is what took the
  shelf itself away a day later; see **A page's filters live in a sheet** under Gotchas. Since 5.1 a report
  is a page of its own rather than a card that unfolds, and what narrows it is a row of chips above its
  calls - one per group, shown open - rather than a toolbar with a box.
- **`PageState.stockBox(destination)` is what decides whether a page has one**, the same shape
  `filtersActive` has and for the same reason: the question is asked from outside the screen that
  owns the answer. Analyze and Settings answer null and get **no search icon at all** — an icon
  opening a box that narrows nothing is a control the page cannot honour.
- **Every part of the box lives on `PageState`, and none of it in the header's own `remember`.**
  `StockBox` holds the pick, whether the box is open, what is typed into it and whether the list is
  down. See **The box that disappeared on the first letter** below for what the header holding two
  of those cost.
- **The box is the indicator as well as the control.** It stays open while a stock is picked and
  the close button clears as well as closes, so a page narrowed to one stock always has the box on
  screen saying so — and it now says it in the company's own name rather than in what was typed. Without that a filtered page with no visible box is a page that looks as
  though it has lost its other rows. It is also why the header is **held collapsed** while the box
  is open: most pages stop scrolling once a filter is narrowing them, and an expanding title would
  otherwise take back the row being typed into.
- **The `folded` flag on those three shelves still leaves the stock box out**, for its original
  reason restated: the box shows its own text while it is narrowing anything, so a chip lit by it
  would report something the reader is already looking at.

### The box that disappeared on the first letter

Reported on 2026-09-11, the day after the picker shipped: *"I can't write anything in the box, the
box disappears."* Pressing the search icon opened the box and the list; the first key press put the
page's title back, with nothing typed.

- **`opened` and `typed` were `remember`ed inside `PageHeader`.** The box existed only for as long
  as that composition did, so anything that rebuilt the header between the key press and the letter
  landing took the box with it and left the defaults — closed, empty.
- **The picker's first day hid it rather than caused it.** Before the picker, the box's text *was*
  the page's filter and lived on `PageState`; a rebuilt header came straight back open with the text
  still in it, so the same teardown was invisible. Moving the text into the composition is what made
  it visible, which is the same lesson `PageState`'s own doc block opens with, arrived at from the
  other direction.
- **The fix is `StockBox`, not a guard.** All four fields live on the page, so there is no longer a
  state of "the box is open" that a rebuild can lose. The second path to the same symptom — a press
  past the list calling `close()` when nothing was picked — is gone with it: `listing` is now its
  own flag, so that press puts the list away and leaves a half-typed query alone.

### The bug that opening it shipped with

Pressing the icon on any tab but the first walked the reader back towards Analyze, one tab per
press. Worth writing down, because nothing in it was broken and every part behaved correctly.

- **The box grew out of the icon's corner** — `expandHorizontally(expandFrom = Alignment.End)`.
  That starts the content at zero width with its **end** pinned, so on the first frame the field is
  laid out with its left edge most of a screen to the left of the window.
- **The focus request fired on exactly that frame.** A `BasicTextField` taking focus asks every
  scrollable ancestor to bring its rect into view, and the outermost of those is
  `DestinationPager`. It scrolled left to reveal a rect sitting off the start edge, landed on the
  previous page, and the pager published that page as an arrival.
- **Both ends are fixed**, deliberately rather than whichever one was cheaper: the transition is a
  plain cross-fade so the field is full width from its first frame, and the focus waits for the
  transition **and** the pager **and** the destination before it is requested. Either alone leaves
  the trap armed for the next person who animates this row.
- **Since 2026-11-11 there is no pager for a focus request to turn** - tabs change in place, see
  `DestinationSwitch` - so the focus now waits only for the cross-fade. The cross-fade stays, for
  the reason above: a field laid out off the window's edge is a trap for any scroller.

### The system bars blend into the page

- **The page runs up behind the status bar.** The `Scaffold` pads every side but the top
  (`safeDrawing.only(Horizontal + Bottom)`), so the page's own background and the accent wash at the
  top of it carry on up behind the clock and the battery.
- **`contentWindowInsets = WindowInsets(0.dp)` is not tidying, and leaving it out shipped both
  faults at once.** A `Scaffold` reports through its `paddingValues` whatever its
  `contentWindowInsets` names and the modifier chain has not consumed - and the default is
  `systemBars`, of which this layout deliberately leaves the **top** unconsumed. So `padding(padding)`
  applied a status bar's height that `PageHeader` was already applying itself: a bar's worth of
  empty band above every page, *and* the page's own background pushed back below the status bar,
  which is precisely the band this change existed to remove. One value fixes both because they were
  one fault. With the name band gone, a
  `surfaceContainer` strip over a `background` page is a seam across the top of every screen and
  nothing up there justifies one. `PageHeader` pads itself by the top inset, reading `safeDrawing`
  rather than `statusBars` so a tall cutout is cleared too. `PageWash` grew from 120dp to 160dp to
  cover the bar before it starts on the page, so the tint fades over the same stretch of reading.
- **The page is no longer set into anything**, so the well's rounded top corners and `wellOutline`
  went with the band that made them read as an inset panel. A radius against the top of the window
  is a curve against the frame of the screen, and a hairline there is a line under the status bar.
- **The bar glyphs follow the app's own theme, not the phone's**, set from `EgxAnalyzerTheme` where
  `themeMode` is already resolved. `enableEdgeToEdge()` with no arguments follows
  `isSystemInDarkTheme` — the *system's* setting — so forcing **Light** in Settings on a phone set
  to dark left white glyphs on a near-white page: an invisible clock, and nothing in the code saying
  anything was wrong. It matters more now that the page is drawn behind them. The activity comes
  from `LocalContext`, the way the fold does in `EgxAnalyzerApp`: null in a `@Preview`, which simply
  means there is no window to set.
- **`themes.xml` carries no `statusBarColor`, `navigationBarColor` or `windowLightStatusBar`.** From
  API 35 the platform ignores the first two under edge to edge, and all three were painting or
  forcing values (`#0B0F14`, light-on-dark) belonging to neither palette.

### What went with the band

- **`AppHeader` is gone**, and with it the whole travelling-mark mechanism: anchors in window
  coordinates, `onMarkAnchor` threaded through `AppContent`, and two animations, all so one glyph
  could be in the header and slide into the rail as that header collapsed. The mark moved into
  `AppRail`, in the gap `RailTopInset` was already holding open — and then went as well, the same
  day, once it could be seen there: see the two bullets below. The app's name and artwork have left
  the UI on both layouts; the launcher icon and the notification glyph are unchanged, which is where
  that artwork still earns its keep.
- **`AppMark` is gone, and the gap it stood in is not.** `ic_egx_notification` is three ascending
  bars and a rising arrow, and the three destinations under it are `AutoGraph`, `Assessment` and
  `Insights` — so in the rail it was a **fourth chart glyph at the head of a column of chart
  glyphs**, unlabelled among labelled ones, wearing the current page's own aurora, which is the hue
  of the one item beside it drawn at full strength. `AppMarkRailSize`'s own note had already named
  the failure — *"at a glyph's size it reads as a sixth destination that has lost its label"* — and
  answered it with 36dp against 28, which is not a difference. Sizing was never the fault: what it
  was there to say, `PageHeader` now says on both layouts in the page's name and its own glyph, so
  the mark was the third cyan chart glyph across one corner. `MarkSweepMilliseconds` and
  `MarkSweepSpan` went with it. `RailTopInset` stays, because what it does is drop the first icon
  level with the heading beside it, and that is unaffected by whether anything fills it.
  `PageAccent.markAurora` is left in the theme and is now read only by `ActionPaletteTest`.
- **The rail is on `background`, not `surfaceContainer`.** It was the chrome colour so that it read
  as the band turning the corner down the side of the page — and with the band gone that left a slab
  in the one colour every `SectionCard` is also drawn in: a full-height card beside a page of cards,
  roughly twice as light as the page between them, with no divider and nothing saying which of the
  two was chrome. It is the same seam the top edge was cleared of, stood on its end, and the
  argument is the one already written there. On the page's own ground the destinations stand on the
  page and what separates it is its cards' own inset.
- **The two shell grounds went with it**, and one of them was still drawing a band: `Surface` in
  both shells and `Scaffold.containerColor` are `background` now. The `Scaffold`'s content is padded
  out of the horizontal and bottom safe-drawing insets, so what its container paints is the strip
  behind the gesture bar — in `surfaceContainer` that was a full-width band about 20dp tall along
  the foot of **every** page, which is exactly what the top of the window had just been cleared of.
  They stay painted rather than left transparent, because the theme parents
  `Theme.Material.Light` and the window background behind them is very nearly white.
- **`headerVisible` is gone.** Nothing leaves with the navigation pill any more; the page header is
  pinned and the pill still hides on its own signal.
- **The progress hairline moved onto the page**, under the header, where the status line is.

## The status line

One line says what the app is doing and what it has just done. It was a floating toast at the foot
of the screen until 2026-08-25, then a row in the app-name band until that band was removed on
2026-09-09.

- **It moved because of where it was, not how it looked.** An app that reports something after
  almost every tap was answering from the far end of the screen from the button that had been
  pressed — on the unfolded panel, the better part of a foot away. And it was the only piece of
  chrome that had to be lifted clear of the navigation bar and lowered again as that bar came and
  went (`toastClearance`), which is a whole mechanism existing to keep one transient message off one
  transient bar. Both problems are answered by putting it where the app's own name already is.
- **`busyLabel` and `statusMessage` share the line, and a running action wins it.** They describe
  the same activity a moment apart — `runAction` sets the first, then clears it and sets the second
  — so two surfaces meant the header could say "Fetching prices" while a tick sat under it reporting
  the *previous* fetch. `BusyBar` is gone; the `LinearProgressIndicator` stays as a hairline under
  the header, and its label moved up into the line.
- **`StatusStage` is what the glyph and the timing read**, not `succeeded`. A step still running is
  `WORKING` and gets a spinner; reading progress off `succeeded` put a tick beside work the app had
  not finished, which is what "Connecting to Telegram" used to show. It defaults from `succeeded`,
  so the fifty-odd ordinary outcomes are unchanged and only a step in flight names it. Not to be
  confused with `StatusTone` in `CommonUi.kt`, which is GOOD/BAD/NEUTRAL for a `StatusPill` — the
  two are unrelated and the name collision is why this one is `Stage`.
- **A confirmation clears itself after 4 seconds; a failure waits to be tapped.** A failure is the
  one kind worth reading twice and the one kind that can arrive while the reader is looking
  somewhere else — a provider's refusal is often the only account of why nothing happened. The
  timer is a `LaunchedEffect` keyed on the message, so a second outcome cancels the first one's
  clock rather than clearing the new line early.
- **`Screen` draws it now, under the page's own name**, on a row of its own on every layout. The
  band it used to sit in is gone and the page starts at the top of the window, so there is nowhere
  above the page left to be. What mattered about its old home survives: it is **outside the scroll**,
  so a message landing while the reader is halfway down a page is never announced off screen. The
  wide layout's "beside the name where there is width" rule went with the name — there is no name to
  sit beside, and `alignEnd` with it.
- **The move put it inside the page's theme**, so the working spinner and an undo's label — the two
  things on this line allowed to carry `primary` — wear the hue of the page they were raised on
  rather than cyan everywhere. That is the accent scheme working as written: what a figure means
  never moves, and chrome takes the colour of where it is.
- **It animates height as well as opacity.** The line has a row of its own, so a plain fade makes
  the page jump a line the instant a message lands — which reads as the content twitching rather
  than as an announcement.
- **It carries at most one action, and only on something destructive.** `StatusMessage.undo` is a
  word the reader can press to take back what the line has just reported, and it is a slot on this
  line rather than a snackbar **deliberately** - the floating toast was removed on 2026-08-25 because
  it answered from the far end of the screen from the button that had been pressed, and bringing one
  back for this would undo that on purpose. The line already says what happened, sits at the top of
  the page, and clears itself after four seconds, which is exactly the shape an undo wants. Two
  paths offer one: recording a sale (`reopenPosition`, which restores the row it was handed rather
  than one read back, and carries a newer stamp so the sale is undone on other devices too) and Keep
  Open. Everything else is an edit the reader can simply make again, and a button after every
  confirmation would turn the quietest chrome in the app into the loudest. A line carrying one is
  **not** dismissable by tapping the row, or the offer would be thrown away by the gesture meant to
  read it.
- **The tone is one tinted glyph and never the text.** Colouring the words would make every routine
  confirmation the loudest thing on screen. Same rule the toast followed.
- **Wording.** Sentence case, no trailing full stop, an ellipsis only on something still running,
  and `·` only between counts — `Priced 40/42 · 2 unpriced · 1 stale` is what it is for, where
  `Key verified · 8 models` was using it to join a clause to a count. One event gets one wording:
  the chat count is `N chats` from both the launch collector and the Analyze refresh, which used to
  say "loaded" and "found".

## Where an explanation lives

Every screen used to say all of it at once. A checkbox was one line of control under four lines of
grey prose, three times in a row, and Settings was mostly prose — so the settings were what you had
to hunt for, between the explanations of them. The words are good words and none of them were cut;
they moved one tap off, next to the thing they are about. This is the rule the schedules already
followed when their four grey paragraphs went (see **Where they are drawn**) and it is now the whole
app's.

- **One affordance, and it is `Icons.AutoMirrored.Outlined.HelpOutline`.** A question mark rather
  than an ⓘ, because `Icons.Outlined.Info` is already the About card's own icon and one glyph cannot
  mean both "the version number" and "explain this". The question mark was already doing this job on
  the channel ranking; this makes it the rule rather than that one screen's habit. Auto-mirrored,
  since half the content here is Arabic. Muted rather than `primary`: it sits beside dozens of
  controls, and a page of coloured glyphs is the same clutter in a smaller font.
- **It opens a `ModalBottomSheet` on `ChannelScoreSheet`'s terms** — same padding, same scroll, same
  skipped partial state. A reader who has opened one explanation in this app has opened all of them.
- **The note goes on the smallest thing it is true of.** A rule about one checkbox rides that
  checkbox (`SettingToggle`'s `about`); one about a group rides the group's heading
  (`ExpandableSection`, `SectionCard` and `SubSection` all take an `about`), where it is reachable
  without opening the card at all. `WordingFlowNote` and `GeneratedPromptNote` are exported for
  exactly that — the words belong to the section that owns them, the heading belongs to Settings.
- **`SettingToggle` leads with its name and trails with its switch.** Fifteen hand-built rows had
  drifted into a checkbox leading here, a switch trailing there, and two gaps between control and
  label; the first pass at this made the control lead in every case, which levelled them and put
  every label 52dp in from the card's edge — past the heading above it, past the sentences under it,
  past the buttons on the same card. So the one column a settings card is actually read down, the
  names of the settings, was the only column on it that started nowhere in particular. Since
  2026-09-09 the name starts where everything else on the card starts and the switches make their
  own column at the trailing edge, under the heading's chevron.
- **`SettingLabel` defaults to `labelLarge` and takes `bodyLarge` for a value.** A version number and
  a slider's current reading were body type before they gained a question mark; shrinking a figure to
  make room for the affordance beside it is the affordance changing what it was added to explain.
- **Every question mark on a settings card sits in one column, and `SettingRow` is what puts it
  there.** Four rows had been built by hand - Save diagnostics, Restore from a backup, Fetch prices
  now, Add a schedule - and each put its question mark immediately after the button, where
  `SettingToggle` and `SettingLabel` put theirs at the trailing edge: the same affordance in two
  places on one card, so neither read as a column. Where the row has a **control**, the question
  mark now sits inboard of it — next to the words it explains rather than past the thing it does
  not — and the control takes the trailing edge; where the row ends at the question mark, as an
  action row does, the mark keeps the edge and holds open `ChevronGutter`, the 24dp
  `ExpandableSection` spends on its chevron, because the group's own question mark sits *before*
  that chevron. The gutter is spent only on a row that ends at a question mark, so nothing else
  gives up any width — and a row with a switch must not spend it, or the switch stops short of the
  chevron's column by exactly that much. **What this costs** is that a toggle row's question mark no
  longer lines up with the heading's, since the switch now stands in the column the heading's
  chevron does; the marks down the body of a card still line up with each other.
- **`SettingRow` sizes that question mark to 36dp rather than `IconButton`'s 48.** A switch is 32dp
  and a `SettingsButton` is 32dp, so the full touch target made every row with an explanation taller
  than every row without — one list of settings at two heights, and the taller ones picked out by
  nothing more meaningful than having a paragraph behind them. The padding goes **outside** the
  size and not inside it: applied after, it eats the target down to 12dp rather than moving it.
- **`ControlColumn` is gone, and so is the nudge that went with it.** It was a 52dp leading column
  the control stood in, wide as a switch, so that a row built by hand could start its label where a
  switch row started its own — and beside it `SettingToggle` shifted its checkbox 14dp left of where
  Material draws it, because a card whose buttons, text and sliders all began at the card's own edge
  had its checkboxes beginning 14dp further in. Both were answers to the control leading. With the
  switch at the trailing edge there is no leading column left to align to: labels start at the
  card's own inset, which is what everything else on the card already did.
- **An action row keeps its button leading.** A button carries its own label, so there is no text
  beside it to align and nothing for the trailing edge to line up with — pushed there it would
  leave the line empty. Which is why `SettingRow.trailing` is null on those rows rather than being
  handed the button.
- **Five kinds of text deliberately stayed on the page**, and the distinction is what stops this
  becoming a way to hide things: an `AlertDialog`'s body, because a confirmation *is* its
  explanation; live status and error lines, which report a state rather than a rule; empty states,
  which say what to do next; a per-item warning like the screenshot-sanity caveat, which is about
  that one call; and any line with a button attached, like the notifications-off prompt on Analyze.
- **`InfoNoteTest` reads the sources rather than composing.** A note that has lost its prose draws an
  icon and opens an empty sheet, which looks like an unfinished feature rather than a deleted
  paragraph — silent everywhere else, so it is checked where the words are written.

### How Settings is grouped

Settings is a hub of tiles, each opening its part as a page of its own - `SettingsSection` in
`SettingsScreen.kt`: **Telegram**, **Reader**, **Ask AI**, **Notifications**, **Prices and trades**,
**Backup and sync** and **About**. The names were made plain in 5.1 (they were Analysis, General and
Data & backup). Pressing a tile opens its part with a back button over its title; phone back returns
to the hub. The open part is `PageState.settingsSection`, so a fold keeps it.

- **A status tile leads the hub** (since 5.1): "Everything is working", or "N things need a look",
  over three lines - Telegram, the reader, prices - each with a tick or a warning. Pressing it opens
  the first part that is wrong. `settingsStatus` in `ui/SettingsStatus.kt` works it out without
  composing, so `SettingsStatusTest` checks every line as plain text. The prices line reads
  `AppState.lastPriceRefreshAt`, which is written only when prices actually arrived, so a failed
  refresh can never be reported as fresh. A key saved but not yet checked, and prices never fetched,
  are said but are not faults.
- **The six other parts are tiles in two columns**, each in its own tone with a one-line summary.
  The ink Telegram tile with its lime circle went in 5.1: in dark mode it was a light tile carrying
  a lime meant for dark ones, 1.03:1.
- **Postures.** Unfolded and in book posture the parts are a column of tiles on the left, under a
  one-line form of the status, with the open part beside them. In tabletop the status - or, on
  Notifications, the chosen notification and its example - stands above the crease; below it are the
  parts as chips and, on Notifications, a grid where a tap picks what is explained and only the
  switch toggles anything.
- **Reader opens on its own status tile** ("Ready to read", "The key was rejected" and so on), then
  **Which AI**. The settings few people need - the endpoint and region, Reset provider, the timeout,
  correction retries, and Token usage - are behind a closed **Advanced** row at the foot.
  `PageState.settingsAdvancedOpen` holds it open through a rebuild.
- **Notifications are seven, one `NotificationKind` each** (`ui/NotificationSamples.kt`): a plain
  label, a second line saying when it comes, and a sample. Every `?` ends with **What it looks
  like** and a drawn notification built from that sample, so the reader can see what a switch means
  before turning it on. The seventh, **A report is ready**, is new in 5.1, on its own channel.
- **Each part's groups are tiles**, titled, with the group's `about` on its heading and a summary
  under it. The wording rules are two tiles of their own - lime for what keeps a message, coral for
  what drops one - under a heading, because a tile drawn round two tiles reads as a mistake.
- **Whole numbers are steppers, not sliders.** Every slider here set a small whole number - a
  timeout in half-minutes, retries, a percentage, a session count - and a stepper is exact where a
  slider needed dragging. The news window and the result count are segmented controls.
- **The Telegram sign-in is four steps**, counted by `signInStep()`: the application, signing in,
  proving it is you (a code, a password, an email, registering), and choosing the chats. The one in
  hand is a cobalt tile; the ones behind are ticked rows and the ones ahead faded. The QR code stays
  black on white whatever the theme.
- **Backup and sync** holds the backup buttons, sync, and **Something not right?** (formerly
  Diagnostics) with its **Save a report** button. `Delete all analyses` is its own coral tile at the
  foot, and still asks first.

## A hue per page — retired in 4.5.0 (history)

Every destination used to speak in its own colour (cyan, violet, rose, indigo, bronze), carried
into the navigation, a wash at the top of the page, the section cards and the controls. The
2026-11 redesign replaced that with one vermilion accent on every page; see **Design language** in
`CLAUDE.md`. What survives of the mechanism is the plumbing - `AccentKey`, `PageTheme`,
`withAccent`, `CardHue` - which now resolves every key to the same seed, so the five pages still
name themselves and a later design could give the hues back without touching a screen.

Two rules from that scheme still hold, because they were never about the hues:

- **`market` is split out of `primary`.** `PriceRole` reads `tertiary`, `error`,
  `ExtraColors.market` and `onSurface`, and `withAccent` touches none of them, so the accent can
  change without a figure changing meaning.
- **An accent is chrome and never a figure.** With vermilion that is stricter than before: it is a
  red in an app where red means a loss, so a good rate is ink and an open trade is market blue.

## Amber, signal red and grey — 4.7.0 (history; 5.0 replaced the amber with the Bento palette)

The accent became amber (`#F29A2E` fill / `#9A5A06` text on paper, `#FFB050` on ink), the loss red
became signal red (`#C62020` / `#FF4D4D`; brick, `#B3261E` / `#F26B5B`, in 4.7.0 itself), and two
things had to move with them:

- **The accent has two shades on paper, and `primary` is the darker one.** The pale fill is 2:1
  against paper, so it cannot be text; `primary` stays `ink` because Material reads it for text
  everywhere (dialog buttons, links, a selected chip). Anything that is a *filled* accent button
  reads `PageAccent.base` and `onBase` (dark) directly: the Analyze action and a filled
  `SettingsButton`. `onInk` (white on paper) is what `onPrimary` now is. A new filled button goes
  through `pageAccent.base`, not `colorScheme.primary`, or it comes out the dark amber.
- **`error` moved to `#8C1D18` on paper**, because brick was exactly Material's own `#B3261E`, and a
  fault and a loss must not be one red. It stayed there when the loss moved on to signal red. `ActionPaletteTest` pins it.
- **Expired is a warm grey** (`#6B655C` / `#B5AEA3`). It was amber, which would have made a trade
  that ran out of time look like a button. `ActionPaletteTest` holds it apart from the accent.
- **Ask AI is ink.** A solid ink capsule when pressing it is billed, an ink hairline when it only
  reopens an answer, same height and press as `ActionPill`. The violet, the breathing halo and the
  gold mark were the glass design's and are gone; while a request is out a band of light sweeps
  across it, built only while working and still under reduce motion.

## The price ladder (redrawn 2026-11-11)

`PriceLadder` was redrawn to the owner's mockup, and it is one component, so every place that draws it changed together.

- **One plain line, four ticks of one size.** Red for the stop, ink for the entry, green for the targets, all upright and centred. The loss, near and far zones on the line are gone (`ladderZones` was deleted; the palette's `zoneLoss` / `zoneNear` / `zoneFar` are no longer read by anything). The ticks used to point down for the stop and up for the targets so the two could be told apart without colour; that was given up for the mockup, and position on the axis plus the row of prices is what says which is which now.
- **Where the price is, is a dot on the line with its figure above it.** The ring round the dot is cut out of the canvas's own layer (`CompositingStrategy.Offscreen` and `BlendMode.Clear`), so it shows whatever the ladder sits on without the ladder being told. Past either end the dot becomes an arrowhead pointing out.
- **Every level's price is under the line.** A label with no room drops to the next row down (`layoutPriceLabels`); nothing but the price itself prints over the line. An entry range still packs its two ends side by side.
- **The travelled stripe is the line's full thickness, pale cobalt.** The Portfolio trade card passes the lowest and highest since the purchase and names the entry "You paid".
- **Not checked on a device.** Built and unit-tested only.

## The Home page (since 2026-11-12)

The first tab. One header like every other page, no stock box and no filter icon.

Blocks, in the cover screen's order:

1. **EGX30** - the chart tile's navy (raised card at night): level, move on the day, a line, and a
   1W / 1M / 3M track. `HomeRange` is in `PageState`.
2. **EGX70 EWI, EGX100 EWI, Shariah 33** - sunflower, lilac, sky: level, move, a 22-session line.
3. **Your book** - teal, sage or maroon by the sign of the average open return; tags for trades
   near target 2, near a stop, or overdue. Pressed, it goes to Portfolio.
4. **Needs a look** (coral, absent with none) beside **Calls** for the session. A trade pressed
   opens that trade; Calls goes to Insights.
5. **Market** - the breadth counts, the kit's `BreadthBar`, and one sentence.
6. **Top gainers / Top losers** - five each; a row opens the stock sheet through `LocalOpenStock`.
7. **USD / EGP, Gold, Brent**.
8. **News** - five Arabic then three English before Show all; Arabic rows are laid out right to left.
9. **AI market brief** (lilac, once there is one) and the Ask AI button that opens the confirmation.

Postures:

- **Cover**: one column, the order above.
- **Unfolded / book**: `SplitColumns` at 0.5 - blocks 1 to 5 and 7 on the left, 6, 8 and 9 on the
  right; book splits on the hinge.
- **Tabletop**: above the crease, the EGX30 (compact, no range track) with the outside numbers
  beside the three indices and the book tile; below it, news and the brief button beside Needs a
  look, Calls, Market and the movers.

A pull refreshes prices, the indices and the news together. Arriving refreshes the indices if they
are over ten minutes old and the news if over thirty.
