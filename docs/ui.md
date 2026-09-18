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
  changing, because the pager publishes its own arrival at the end of every travel this class starts
  and an unguarded clear there would throw the return away in the same breath as the jump.
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
  shelf itself away a day later; see **A page's filters live in a sheet** under Gotchas. The in-report toolbar on
  Results keeps its own box, because that one is a different control inside a report card,
  narrowing that report's own table.
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
- It is the trap `revealIfOnScreen` is written against, arriving through the focus system rather
  than through a reveal. **Guarding on the destination alone does not catch it** — that was the
  first fix and it changed nothing, because the page is current and the tabs are settled at the
  moment the icon is pressed. What was wrong was the frame, not the page.

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

Seven cards, reorganized again on 2026-09-14, on the owner's request to group what belonged together
and cut down what each card said about itself: **Analysis**, **Ask AI**, **Telegram**,
**Notifications**, **General**, **Data and backup**, **About**. Ask AI and Data and backup are new;
Saved data and privacy is gone, folded into Data and backup along with Sync, Prices, Token usage and
Diagnostics.

- **Ask AI got its own card**, out of Analysis, where it sat as the last of seven subsections on the
  reasoning that it shares the provider and the key with a run. Sharing a key is not sharing a
  purpose — it is reached from a call card on Insights, not from a run — and burying a whole feature
  as the sixth thing under "Analysis" cost it to anyone who did not already know it was there.
- **Data and backup answers one question — what this app holds, and how to get it out or rid of
  it — instead of five settings that happen to touch a database.** Sync and Prices were two of four
  unrelated `General` subsections; Backup and Delete were their own card; Token usage sat inside
  Analysis as a spend report with nothing to do with configuring a run; Diagnostics was a bare
  `DiagnosticsControl` call loose in About. None of the four is a *setting* the way Analysis,
  Notifications or General hold settings — each is either a copy of the record (Sync, Prices,
  Backup), a report about it (Token usage), or a way to get a copy out or delete it (Diagnostics,
  Delete). One card is what makes "where is my data" answerable by opening one thing rather than
  three.
- **`General` is left with exactly what nothing else claims**: Appearance and Trade defaults, one
  control apiece, still not worth a card each.
- **About is left with only what it is named for**: the version and the update controls. Diagnostics
  moving out is what makes that true again — a device copy for chasing a bug was never about the
  build number.
- **The 2026-09-03 pass is not undone, only continued.** Appearance, Sync, Trades and the price
  refresh were four cards holding one control each, and `General`'s four `SubSection`s were the fix
  for that. This pass moves two of those four (Sync, Prices) into the card their content actually
  belongs with; Appearance and Trade defaults stay exactly where that pass put them.
- **A group nests once and never twice.** `SubSection` is a heading, a chevron and a rule, precisely
  because a card drawn inside a card reads as a mistake — so every one of these is a group inside a
  card and not a card inside one. `PricesSubSection` is drawn by its own file for length, not because
  it is a different kind of thing.
- **Every group carries a summary, and the summary is the point of folding it.** A closed group that
  said nothing would put the reader back to opening all of them to find the switch they came for,
  which is what the grouping was for.
- **Sync sits under Data and backup rather than under Telegram**, the same call the owner made on
  2026-09-03 for General: the account is what Telegram is about, and pressing Sync now is
  housekeeping. The Telegram card's own note says where sync went, so the two do not become a place
  each to look.
- **Every card and every group carries an `about`**, on the rule above: the note goes on the smallest
  thing it is true of, and a group-wide one is reachable without opening the group.
- **`Delete all saved analyses` keeps its own group, at the bottom of Data and backup.** The one
  irreversible button on the page should not sit at the end of a run of buttons that are not, and its
  note says to take a backup first.

## A hue per page

Every destination speaks in one colour, and the colour reaches four places: the wash at the top of
the page, the app's mark, the destination's own navigation indicator, and `primary`/`secondary` —
which is what carries it into the section cards, the chips, the checkboxes and the radios without a
parameter being threaded anywhere. Added 2026-09-08.

- **The whole scheme rests on `market` having been split out of `primary` earlier.** While `primary`
  meant both "the market got here" and "this is the app speaking", nothing could be done to it
  without moving a price. `PriceRole` reads `tertiary`, `error`, `ExtraColors.market` and
  `onSurface`, and `withAccent` touches none of them — so a page may have a hue and a figure still
  means the same thing on all five screens.
- **Each hue was chosen against what its own page draws**, not by taste. Analyze is `CYAN` and draws
  no prices at all; Results is `VIOLET`, because a report is the model speaking and `AiButton` is
  never drawn on that screen; Insights is `ROSE`, the one family no figure anywhere uses, because it
  draws every other hue plus the violet pill; Portfolio is `INDIGO`, since it draws no market blue
  while green, red and amber are busy; Settings is `BRONZE`, which draws no prices at all. The
  reasoning is on `AccentKey` and the check is `no page accent is one of the signal colours`.
- **`withAccent` is the mechanism and `DestinationScreen` is the one place it is applied** — the same
  function both shells build a page through. Not around the whole shell, because a sheet raised over
  the top is not on a page and must not take the hue of whatever happens to be behind it. The status
  line **is** on a page since 2026-09-09 and does take it — see **The status line**.
- **The navigation bar is outside every page's theme, so it asks each destination for its own.**
  `accentFor(destination.accent, LocalDarkTheme.current)`, in both `PillItem` and `AppRail`. Reading
  `secondaryContainer` out there would give all five slots Analyze's cyan.
- **All five destinations wear their hue at rest, not only the selected one** (`RestingIconAlpha`,
  0.62). The bar is where the mapping between a colour and a page is learned and it can only teach
  it by showing all five; what says where you are is the filled indicator and a hue at full
  strength, which is a larger difference than the grey-to-colour one it replaced.
- **The app's mark is no longer drawn on any page**, so nothing outside the navigation reads an
  accent from outside a page's theme any more. It took its hues as a parameter for the bar's own
  reason — drawn over the rail, outside every page's theme, a mark reading the local would have worn
  cyan on all five pages — and wearing the page's hue is what put it in the lit destination's own
  colour directly above it. See **What went with the band**.
- **A card has its own hue on top of the page's**, on the tile behind its icon and the 3px edge down
  its left side — `SectionCard.accent` and `ExpandableSection.accent`, both defaulting to the page's.
  The **first card on a page takes the page's hue** by passing nothing, and the rest name a
  `CardHue`. This reverses `iconTone`'s old note that colouring each icon would be a page of noise:
  that was true of a page of identical grey headings and is not true of a tile per card in hues the
  page keeps to. `iconTone` still outranks `accent`, because a card reporting its own state has
  something to say that its place in a column does not.
- **A card accent is chrome and never a figure**, exactly as the page's is. What a number means is
  still said by tertiary/error/market/expired, which is why a card may take any hue at all.
- **The Ask AI pill runs violet → the page's hue.** The model announcing itself is the same
  announcement everywhere; where it was asked from is not. It also settles the one collision in the
  scheme: a violet page and a violet pill cannot be confused when the pill is the only object
  *travelling out of* violet. Pinned by `the pill begins in violet on every page and ends in the
  page's own hue`, which fails in both directions — a ramp that followed the accent whole would read
  as five unrelated buttons, and one pinned whole would undo the feature.
- **The light theme's hues are not the dark theme's**, for the reason `market` was darkened for the
  light theme: on a near-white card the dark values come out between 1.5:1 and 2.5:1. Every light
  accent ink and every `CardHue` clears 4.5:1 on both the page and a card. The saturation pass that
  came with this took the light `tertiary`, `error` and `expired` **down** rather than up for the
  same reason — brighter versions measured 3.8–4.4:1, and every one of them is a price.
- **`PageWash` reads the scroll inside the draw lambda**: read at composition it would recompose
  the whole page on every frame of a scroll. The header's collapse fraction is passed as a lambda
  for the same reason, and the wash counts it as scroll so the tint does not sit at full strength
  through the whole collapse. `AppMark`'s aurora phase was the third reader of this rule until the
  mark went; the rule is the same one, and the next always-on animation drawn over a page wants it.


## Gotchas

- **The bar follows every page turn except the ones a travel started in the shell passes over, and
  the effect that scrolls is keyed on the pager.** The bar and the pager move the same pointer, so
  each follows the other, and all three ways that link can go wrong have now been shipped. Keyed on
  `appState.destination`, the scrolling effect was restarted by the very thing it was meant to serve
  — a swipe publishes its own arrival — and the restarted copy compared a target read at
  recomposition against a page read a frame or more later, so a second swipe arriving inside that
  window scrolled the reader **back to the page they had just left**. One `LaunchedEffect(pager)`
  owns the whole link now; nothing the pager says can restart it, so no stale target survives to be
  acted on. The guard against the write-back was the other two faults, and both came of the guard
  naming the **gesture** rather than the travel. Raised *inside* the scrolling coroutine it went up a
  frame after the scroll started and down a frame after it ended, and a page that turned over inside
  either gap was swallowed. Read from the pager's own `interactionSource` instead — up on
  `DragInteraction.Start`, down when the pager reported itself at rest — it was no better, because
  **Compose runs a swipe as two scroll sessions**, the finger's and the settling fling's, and
  `isScrollInProgress` reads false in the gap between them: the guard came down in that gap, so a
  *flick* — where the page is decided by velocity on the fling rather than by crossing the halfway
  mark under the hand — turned its page with the guard already closed and left the bar lit on the tab
  the reader had just left. A slow drag past halfway still worked, which is what made it look
  intermittent, and the same flag silently dropped a tap made while a swipe was still settling. The
  guard is `travelling` now: the page a travel started here is heading for, `null` otherwise. It is
  written by the only coroutine that scrolls, before it suspends, so nothing about how Compose splits
  a gesture into sessions can reach it. Page and guard are read in **one** `snapshotFlow` pair, so a
  turn is never delivered against a guard that changed after the turn was taken. What falls out of
  that: every turn the reader causes is an arrival however it turned — under the hand, on the fling,
  or as the pager settled — while the pages a travel crosses stay silent, and so does **the page a
  travel is abandoned on when a second send replaces it**, which left to speak wins the race against
  the send that cancelled it. Sends run through `collectLatest`, which starts each block
  **undispatched**, so a replacement raises the guard in the same continuation the cancelled block
  lowered it in and the abandoned page never gets a frame to name itself. A travel is outranked by
  nothing but a hand, so a tab tapped while the pages are still coasting is answered rather than
  dropped; a drag refuses it outright, and that refusal is swallowed — the guard comes down, the
  gesture names where they land, and if it puts the pager back on the page it set out from, turning
  no page and so naming nothing, the resting page is published so the bar cannot be left on a tab the
  pager never travelled to.
- **Pressing the destination already showing takes that page back to the top.** The press every
  bottom bar on this platform answers, and this app answered it with nothing — the way back from a
  session card deep inside Insights was to scroll all of it by hand. `AppState.scrollToTopRequest`
  is a **destination and a counter**, and both halves are load-bearing: the destination because the
  pager keeps the neighbouring pages composed, so a bare signal would take those to the top too and
  throw away a scroll position on a tab nobody touched; the counter because a second press is a
  second request, and a value that repeated would restart no effect. The shell publishes it through
  `LocalScrollToTop` from `DestinationScreen` — the one place both shells build a page and so the
  only place that knows which destination is being composed — and `Screen` animates the scroll, so
  all five pages get it from one change. **Deliberately not folded into `navigate()`**: the pager
  calls that on every swipe and its `snapshotFlow` reports the page it is already on the moment it
  starts collecting, so a scroll-to-top in there would fire on first composition and again on every
  settle, throwing the reader to the top for having swiped to a tab.
- **The Analyze action never leaves, and it follows the bar down.** It used to go with the bar on
  the same scroll — tidy, and it meant the one control that starts a run was reachable only from the
  top of the page. It is always on screen now, and rather than holding its height over the hole the
  bar leaves, it travels into the bar's own place: `animateDpAsState` between
  `NavBarFootprint + PillBottomMargin` (94dp, clear of the bar and then the same gap again, so the
  two read as one stack) and `PillBottomMargin` (10dp, the bar's own float off the bottom). The
  travel is exactly `NavBarFootprint`, which is what makes it land there rather than near there.
  **This was the rule `toastClearance` in the shell followed**, back when an outcome was a toast at
  the foot of the screen — same problem, same `animateDpAsState`, and its comment said why: "so a
  toast raised on a scrolled page does not hang over the gap where the bar used to be". The status
  line lives in the header now and that clearance is gone with it, so the action is the only
  floating chrome left that has to follow the bar at all. The wide layout draws it unconditionally with no bar to follow, so all of this is
  the compact branch only.
- **The action's ground is 0.84 in both states, against the bar's 0.94.** `actionFill` and
  `actionAuroraBase` carry the same figure deliberately. The bar tidies itself away while a page is
  read and the action does not, so the action is a permanent object over a page still being
  scrolled — and at the bar's opacity it reads as a slab parked on the page rather than as a control
  floating above it. The two states match because the transparency is a property of the button, not
  of one of its states: a button that changed weight the moment a run started would report the run
  twice, once in a way nobody could name. Only the grounds carry it — `onAction` stays opaque so the
  label survives whatever scrolls behind, and the `actionAurora` circles are the light *inside* the
  ground, so thinning those would dim the one thing saying a model is working.
- **The action's edge is a gradient in its own colours, and the bar's beside it is not.** Both are
  `FloatingSurface`, so they are the same material; they are deliberately not the same edge, because
  two identically outlined slabs at the foot of the screen said nothing about which of them did
  anything. `PageAccent.actionLine` carries it, and it has **its own stops rather than
  `actionFill`'s** for the reason `aiLine` has its own beside `aiFill`: the fill sits *inside* the
  line, so a line in the fill's colours is a line against itself and disappears. What it has to read
  against is the page scrolling behind the button — dark on one theme, near-white on the other — so
  the stops invert between the two while the hue does not. The hues are the aurora's own, in the
  aurora's own order, rather than a fourth set invented for the edge — derived per page from the
  seed table rather than written out five times, so the relationship between a page's fill, edge and
  aurora is stated once.
- **The edge is 0.74 against the ground's 0.84, and its hues run about a third under the aurora's.**
  It shipped opaque and full-strength on the argument that an edge letting the page through stops
  holding the shape — which was wrong on the device: it read as a bright cyan wire around the button,
  the loudest thing on a dark page and competing with the label it was meant to frame. The shape
  still holds, because what draws it is the contrast with the page rather than the weight of the
  line. `ActionPaletteTest` pins the properties and not the figures — nothing the action is drawn
  with is solid, every stop in a ramp shares one alpha (a ramp that fades along its length reads as
  a mistake), and the edge shares no stop with the fill, which is the slip that produces an invisible
  edge: reaching for `actionFill` when adding the gradient, because it is right there and already
  the right family. **Every case sweeps all five accents in both themes**, since the ramps are
  derived per page now and a property that holds for the one hue somebody looked at is exactly the
  kind that quietly fails on the other four.
- **Only the ready state wears it.** Running keeps the red `aiStop` hairline: that is the only state
  where pressing cancels, and no edge in the action's own colours could say so — the moving fill
  says a model is working, which is a different sentence. Blocked keeps the neutral outline every
  other floating thing has, for the reason it does not wear the fill either: a blocked button with
  the action's own edge round it would be inviting a press that does nothing.
- **`FloatingSurface.outline` is a `Brush`, not a `Color`.** One parameter rather than two that can
  both be set and disagree; a flat edge passes `SolidColor(…)`. The width stays 1dp for every
  floating edge in the app, gradient or not — the bar sits directly under the action on a compact
  screen, and an edge thicker on one of them would read as the two not matching rather than as one
  of them being the control. The colour is what separates them.
- **Every pill in the app is one shape and one of two heights**, `PillShape` and `LabelPillHeight`
  / `PillHeight` in `DesignSystem.kt`. Before 2026-09-11 they were all `CircleShape` at whatever
  height their own padding produced, which is what the owner reported as pills "too rounded" and
  "different sizes in one card": a capsule is a shape nothing else on a screen of 14dp cards makes,
  and a height derived from padding moves with the text, the font scale and whoever wrote that
  particular chip. Six families had grown - the ring on a card, the button on a card, the EGX 33
  badge, the stock sheet's filled flags and two chips spelled inline in the table - across four
  corners and five heights. They are one now: **6dp**; **20dp** for anything only read, with the text
  centred in a fixed box rather than propping it open; **32dp** for anything pressed, where the extra
  12dp is the fingertip. **The 6 is not a free number and 8 was tried first.** `CircleShape` takes
  half the *shorter* side, so the capsule on a 20dp pill was already only a 10dp corner - 8dp moved
  every label pill by two, shipped, and looked identical on the device, while making the EGX 33 badge
  *rounder* than the 4dp it had. 6dp is the largest corner that visibly cuts a 20dp pill. `OutlinePill` is the ring and
  `FilledPill` is the block of colour, and both draw one `PillLabel`, so a pill's padding and type
  cannot be restated anywhere. **What stays round is round by nature** and not by drift: avatars,
  logos, the empty state's glyph, the navigation indicator, the header's search field, `DayChip`
  (a seven-across day toggle) and `RiskRewardBar` (a bar, not a pill).
- **A card says its pills in one place.** The position card drew the status chip in the header and
  the rest of them a block lower down, and the call card stacked Edited over Timing in the top-right
  corner - against a name block three or four lines tall, so the one annotation every call card
  carries floated at the very top aligned with nothing: not the ticker's line, not the menu beside
  it, not a figure underneath. Both cards are now the same shape - identity on the left of the
  header, the menu on its right, every pill on one row beneath it, starting at the card's own inset
  so it lines up with the ticker above and `ENTRY` below. The status chip leads the position card's
  row (which is unconditional now, since a status is always there to say). No fact was added or
  removed by either move. The call card's own regroup was missed on the first pass and reported on
  2026-09-11 as pills "at the very top of the card, not aligned, placed randomly".
- **The EGX 33 badge is deliberately outside `PillShape`**, and was folded in once and had to come
  back out the same day. It is a 4dp square because round its outline sits concentric with the star
  inside it and the pair reads as a settings cog; the shared 6dp made it rounder than the 4dp it had
  and it read as a gear on the device within the hour. The pill rule is about labels, and this
  carries no wording.
- **T+1 is neutral, like every other note pill.** It was `primary` in all three places that draw it -
  the call card, the position card, Insights - on the reasoning that it is neither a verdict nor a
  warning and so may take the app's own voice. True, and not a reason to be the one differently
  coloured ring in a row of them; the wording already says the call named its own deadline. Asked
  for on 2026-09-11. It is still tappable in the two places it explains itself.
- **A button on a card is one of two things, and its colour says which.** `ActionPill` in
  `CommonUi.kt` is anything that changes the record — Bought, Sold, Keep open — as a `PillHeight`
  ring in the app's own `primary` at half strength. `DisclosureButton` is anything that only opens
  or closes a section — View / Hide recommendations, Source, Source trace — as a bare `primary`
  label with an arrow that flips with the section. They were a filled tonal button, two outlined
  ones and two text ones, which said that recording a purchase is a heavier act than recording a
  sale, and put those two in one row on a position card disagreeing about it. **Three things are
  deliberately outside the system**: the Ask AI pill, because violet is the model speaking and the
  one hue on these screens that is not a measurement; the Analyze action, which is 56dp of teal
  aurora and a tier above anything drawn on a card (see the entries above); and an `AlertDialog`'s
  buttons, which are Material's convention rather than this app's. **The pills are 32dp and the
  touch target is still 48**, through `minimumInteractiveComponentSize` — the trick the Ask AI pill
  already used, and the reason a smaller button here costs nothing to press. Settings, Backup and
  Prices were finished on 2026-09-09 by `SettingsButton`, the bullet below. **Channels is
  deliberately still Material's own**: its eight `Button`s are the steps of the Telegram sign-in,
  each the sole action of the card it is on and correctly the point of that screen.
- **`SettingsButton` is the third kind, for a page of settings rather than a card about one call.**
  `PillHeight` and `Space.m` against Material's 40dp and 24, and `labelMedium` like both card
  buttons — at the default it was the heaviest thing on a card whose subject is the words beside it,
  standing next to switches 32dp tall. It stays an `OutlinedButton` rather than becoming an
  `ActionPill`: a pill takes the page's own hue to say that pressing it changes the record, and a
  column of cyan rings down Settings is the page of coloured glyphs the question mark is muted to
  avoid. It carries a `filled` flag for the four card primaries — Save and verify, Sync now,
  Download, Install — **because they share a row with the outlined ones**, and a `FlowRow` 40dp tall
  at one end and 32 at the other reads as a layout fault rather than as emphasis. Which button is
  filled did not change; only how tall the row is. `AlertDialog` buttons stay Material's, the same
  exception the card pills make.
- **A report card opens on a press anywhere, and only while it is shut.** `Card(onClick = …,
  enabled = !expanded)` in `SavedAnalysisCard`, with `disabledContainerColor` pinned to the same
  fill so "disabled" does not read as greyed out. Open, that card holds the report's own toolbar,
  its call cards and the source trace, so a card-wide toggle would close the whole report on a tap
  landing in the gap between any two of them. The footer row — a `DisclosureButton`, which replaced
  a full-width filled button doing the same job as the card under it — is what closes it again.
- **Nothing inside the `NavigationRail` may fill its width, and one `fillMaxWidth()` took the whole
  unfolded layout out.** Material sizes a rail with `widthIn(min = ContainerWidth)` — a floor, not a
  width — so a child that fills the width stretches the rail to whatever it is measured against.
  `NavigationSuiteScaffoldLayout` measures the navigation suite against the entire window and then
  hands the page `width - railWidth`, so the rail became the screen and the page was measured at
  zero: five destinations centred on an empty display. It arrived with the app mark on 2026-09-09,
  as `Box(Modifier.fillMaxWidth().height(RailTopInset))` holding the mark in the rail's top gap, and
  was fixed in 3.6.2 by deleting that one call — the rail's column centres its children already.
  **A phone cannot show this**: there is no rail on the compact layout, so it shipped through a
  release that was checked folded, and only the Fold opened shows it. Anything new drawn in the rail
  wants a bounded width for the same reason.
- **A page's filters live in a sheet the header opens, not on a shelf on the page.** The page
  header's filter icon opens a `ModalBottomSheet` holding that page's filters; it replaced
  `FilterBar` on 2026-09-09, the day after the stock box left that shelf for the header.
- **What was wrong with the shelf was what the stock box left behind.** `FilterBar` was a search box
  and a Filters chip on one line, standing off the page on a shadow once a scroll had pushed it to
  the top. Take the box away and it is a chip alone: a loose one-control row above every list, and
  the row was the cheapest thing on it to keep — the chip opened a panel of three more chips, each
  of which opened a menu. Three levels deep for a question the reader asks in one press. The lift,
  the pin, the clamp against the parent's foot, `LocalViewportTop`, the transparent-until-floating
  fill and its `floatingColor` override all existed to make that one row behave on a scroll, and
  all of it went.
- **The trigger is in the header and the content is on the page, and neither could hold the other.**
  The header does not know what a page filters by — channels come off `savedResults`, dates off the
  trades — and the screen is composed below the icon that opens it. So the flag lives on
  `PageState.filtersOpen(destination)`, which is exactly the shape `stockBox(destination)` has
  and for the same reason. It is also why it survives a fold, where a `remember` inside the sheet
  would not: see the head of `PageState`.
- **A sheet, not a panel hanging off the header.** A sheet already means one thing here — the longer
  version of the thing that was pressed, which is what `StockSheet` and `InfoSheet` are — and a
  panel over a scrolling page would have to re-solve, in a second place, the pinning the shelf was
  written to solve. The width is the rest of it: the choices are **shown open**, as chips under a
  heading, where the shelf had room only for a chip that opened a menu. `MultiSelectSection`,
  `SingleSelectSection` and `SortSection` are that; `MultiSelectFilter` and its siblings stay in the
  file because the in-report toolbar still uses them, inside a card where there is no room for
  anything else.
- **The `All` chip leads every section and is selected while nothing else is.** "Untouched means
  everything" is the rule the menus had to state in words; on a surface with room for it, it is just
  the first chip.
- **The sort is below a rule, under its own heading, and there is no `All` in it.** An order is not
  a filter: it hides nothing, `filtersActive` leaves it out and Clear filters does not touch it. On
  the shelf it sat on the same line as the filters and was kept out of the clear-all by a comment no
  reader could see. And a list is always in *some* order, so there is nothing for an `All` to mean.
- **`Clear filters` is on the title's line, not at the foot.** The sections are as long as the page
  has channels, and a button under them is one the reader has to scroll to in order to undo
  something they can see from the top. It is offered on `filtersActive`, which counts the stock box
  too, because clearing means clearing.
- **The dot on the icon is `filtersInSheet`, not `filtersActive`, and that distinction is the whole
  of why it is honest** — it is the same one the shelf drew between `folded` and `active`. The stock
  box reports itself by staying open; a dot lit by it would report something the reader is already
  looking at, and would go on reporting it after they had cleared everything else.
- **Every sheet is composed outside the guard its shelf sat inside.** Results drew its shelf only
  with runs on the page; the Portfolio drew its own inside the Positions card, below the early
  return for an empty record — which is why `PositionFilterSheet` is split out of `PositionSection`
  and called above that return. The icon in the header is there either way, so a sheet that composed
  only sometimes would be an icon that opened nothing. The sections drop themselves when they have
  no options, so an empty page opens on the sort alone.
- **The Portfolio's sheet is titled `Filter positions`.** Its date and its order narrow the Positions
  card; Your record and Overdue are built from the whole portfolio on purpose, so a date picked here
  cannot hide a trade that is late. The shelf said that by sitting inside the card it filtered. A
  sheet reached from the page header would be claiming the page, so the title says it instead.
- **A breakpoint that *adds* content spends the width on itself.** `RecommendationTable` grew columns
  at 620dp and 900dp and `TodayCard`'s tiles capped their grid at four columns, and both produced the
  same result: the wider device showed **less** of what the reader came for — 49% of a table row on
  the tablet against 64% on the smaller Fold. The check that catches it is not "does the breakpoint
  fire at the right width" but **what fraction of the content is visible at each real container
  width**, computed for the Fold's 614dp, the tablet's 682 and the emulator's 715 — and remember all
  three are the *container*, after the rail's 80dp, the page's `Space.l` either side and the card's
  own inset. Both are fixed the same way, and it is worth stating as the rule: extra width goes into
  the elements already on screen, and anything a wider window adds must already be present in some
  form at every narrower one. See **A report's calls on screen** and the tile table under **What
  happened this session**.
- **`AdaptivePanes` is the only "side by side, or stacked when it will not fit" rule in the app**, and
  a second one would be a second threshold, a second fallback and a second gap to keep in step. A
  pair of equals is that helper with `mainWeight = 1f`, not a layout of its own — which is how
  Analyze's **Content types** and **Recommendation target date** now sit beside each other above
  600dp of container: stacked on the 379dp cover screen, 313dp each on the 638dp unfolded Fold, 347
  each on the tablet. Note the outer pane split is 720dp and **no real device here reaches it** on
  that page (the emulator's 739 does), so those two cards get the page's full width to divide.
  `alignHeights` stretches both columns to the taller; it is off by default because it is wrong for
  the case the helper was built for — a tall main pane would drag a short side column's last card
  down to meet it — and right for a pair, where two cards of equal standing ending at two heights
  reads as one of them having failed to load. **It measures rather than asking for an intrinsic, and
  that distinction shipped a crash.** `Modifier.height(IntrinsicSize.Max)` is the obvious way to
  write it and it throws: intrinsic measurement of a `SubcomposeLayout` is unsupported, and a pane
  holds whatever the screen puts in one — at the time a `BoxWithConstraints` in the Content types
  card, since replaced by a `FlowRow`. Because the Row branch is only taken above
  `minWidth`, it stood up on the cover screen and died the moment the phone was unfolded — v2.1.31,
  reported from the device. The rule outlived that call site: the next `BoxWithConstraints`,
  `LazyRow` or `SubcomposeAsyncImage` a pane acquires brings the crash straight back, and nothing
  about the panes says so. `ResponsiveRows` carries the identical warning about
  `IntrinsicSize.Min` a few hundred lines above, which is the part worth remembering: **the trap was
  already written down and got walked into anyway.** The height is read back with `onSizeChanged`
  and can only grow, so it settles in one pass, and its reset key is the width so a fold cannot
  carry one layout's height into the other.
- **Both were hand-built copies of `SectionCard` and are not any more.** That is what let them drift:
  same container and shape, and then one tinting its icon `primary` and the other leaving the
  calendar untinted, each spelling its own header row and divider. Drawing the background twice is
  how two cards meant to match stop matching. The **"Change date" button is gone** with them — the
  "Specific date" row has always opened the picker itself, so the button was a second control doing
  one job, and it was the reason that card changed height the instant the mode changed, which is the
  one thing a card sitting beside another must not do. The affordance moved into the line already
  there: the date, then `· tap to change`.
- **The checkboxes wrap rather than switching on a width, and the helper that switched them is
  gone.** `AdaptiveInline` asked the card how wide it was and laid three checkboxes across above
  420dp, and it had the fold exactly backwards: the card is at its *narrowest* when there is room to
  put it beside the date card, so the 379dp cover screen gave it 347 of content and got the compact
  row, while the unfolded Fold split 638 into two 313 columns, left 281, missed the threshold and
  stacked three long labels down a column. **The larger screen got the taller layout.** A `FlowRow`
  asks the labels how wide they actually are instead of guessing from a number written in the
  source, so one row survives the cover screen, the unfolded Fold and the tablet alike and a large
  font scale wraps instead of clipping. That left `AdaptiveInline` with no callers and it was
  deleted rather than kept: a five-line wrapper over `BoxWithConstraints` whose KDoc named the one
  card it was written for was never a general primitive, and a public helper nobody calls is read as
  the house answer by whoever needs the next one. Deleting it also takes a `SubcomposeLayout` back
  out of a pane `alignHeights` has to measure — see the crash above, whose rule stands without it.
- **A `remember` keyed on `saved.id` goes stale now that a report's contents can change.** A
  report's id never changes, and until corrections existed nothing else about a report ever changed
  either, so keying the parsed stocks on the id alone was correct for as long as it was written.
  The first correction made a stored run mutable and the report went on drawing the stocks as the
  model first read them — while **Insights showed the fix**, because it rebuilds from the record on
  every recompute, and leaving the screen fixed it, because that disposed the memo. Anything in
  `ResultDetail` derived from a report's *contents* is keyed on `saved.result.editRevision` as well
  as its id: the stocks, the channel names, and the timing and channel option lists. The revision
  rather than `saved` itself, so it is an integer comparison per recomposition rather than a deep
  compare of every call in the run. **View state stays keyed on the id alone** — the search box, the
  context toggle, the open filter panel — because those are about the reader rather than about the
  report.
- **A filter stores what is hidden, not what is shown**, for the same reason. Seeded once from the
  report, a set of shown names cannot contain a value that did not exist when it was seeded — so
  re-dating a card as Watching created a timing the filter had never heard of and the card the
  reader had just corrected **vanished** instead of updating. Storing the exclusions gives both
  halves: filters set on purpose survive a correction, and anything a correction creates is shown
  because nobody ever chose to hide it. `narrowed` is still measured against what the report
  actually offers, so an exclusion naming a timing the report no longer has lights nothing.
- **A sheet holds an anchor, not the objects it was opened with.** `ResultDetail`'s occurrence sheet
  captured the stock and the point it was opened with, so a correction made *from inside it* left it
  drawing the figures that had just been replaced. It holds `(originalStockCode, parseIndex)` and
  re-resolves on every recomposition — against the whole report rather than the filtered list, so a
  filter can never close a sheet the reader has open.
- **The two shells are two call sites, so no page may hold its own state.** `EgxAnalyzerApp` branches
  on `rail` around one `AppContent` for the rail and another for the pill, and again around
  `AnimatedContent` versus `DestinationPager`. Folding the phone flips `rail`, Compose disposes one
  subtree whole and composes the other from nothing, and every `remember` in every screen dies with
  it — which is how an open report vanished on unfolding and left the reader on the list of runs.
  Anything the reader would notice losing goes in `PageState`, hung off the application-scoped
  `AppState`; only transient chrome (a dropdown, a confirm dialog) stays in a `remember`.
  `rememberSaveable` under a `SaveableStateHolder` does **not** work here and was shipped once
  before it was understood: the branches swap inside one frame, so the arriving page reads the
  holder before the leaving page has written to it, and on the way back it restores what the
  previous fold left there. `movableContentOf` cannot reach across `HorizontalPager`'s lazy
  subcomposition. `PageState`'s own comment carries the whole reasoning.
- **A `bringIntoView` escapes the page it was asked from.** The request travels up through every
  scrollable ancestor, and on a phone the outermost one is `DestinationPager` — so a reveal fired
  from a page the reader has left scrolls *that page* back into view, which is the pager travelling
  back to the tab they were leaving. `beyondViewportPageCount = 1` is what keeps the page alive to
  fire it: the Portfolio is still composed, and still running the effect that reveals a trade, while
  Insights is the tab on screen. It beat a tab press rather than losing to one — `animateScrollToPage`
  and a reveal scroll run at the same `MutatePriority`, so the later of the two wins — while a swipe
  survived, because a drag holds the pager at `UserInput` where no reveal can take it, which is what
  made this read as the navigation bar alone being broken. Every reveal now goes through
  `revealIfOnScreen`, which drops the request unless `AppState.destination` is the tab the page is
  drawn on. Three call sites: the Portfolio's trade, Insights' call, the Results report. The card is
  left unfolded either way, and a reveal the reader has walked away from is dropped rather than held
  for their return — the rule `NavStop` already states, that revealing the wrong card is worse than
  revealing none.
- **The tab is not the arrival, and checking it alone fixed half of this.** A press sets
  `AppState.destination` in the same breath it starts the pager travelling, so a page that composes
  *during* that travel passes a destination check and cancels the very scroll carrying the reader to
  it — the pager, barely off the tab they pressed from, snaps back to it. Only a tab two or more
  pages away can do this: a neighbour is already composed and its effects do not run again. That is
  the whole of why the Portfolio reached Insights, next door, and never Results, two along, and why
  Results failed only once a report had been opened — `openRun` is `PageState.openResultId`, which
  outlives the tab, so it is null at a cold start and non-null forever after. `revealIfOnScreen`
  waits on `LocalTabsSettled` — written by `DestinationPager` off `pager.isScrollInProgress`, true
  beside a rail, and put back to true on dispose so a fold cannot strand it — then asks about the
  destination a second time on the other side of the wait. Waited out rather than dropped, because
  an arrival that *should* reveal composes its page mid-travel too: a notification opening a saved
  report is one, and dropping it would answer the notification with a page scrolled to wherever it
  was last left.
- Scrollbar overlays must be applied **outside** the scrolling node, or they are measured against
  the content and slide away with it.
- `NavigationSuiteScaffoldLayout` does **not** consume window insets for its content; the full
  `NavigationSuiteScaffold` does, through a private helper the layout never calls. Left alone the
  page pads itself clear of the gesture strip that the bar below it is already holding, and the
  cover screen shows a band of dead chrome the width of the strip — 15dp on the Fold 7.
