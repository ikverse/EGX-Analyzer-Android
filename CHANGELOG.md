# Changelog

All notable changes from 3.6 onwards. Earlier history is in `git log`.

---

## [5.6.0] — 2026-11-12

- **A new app icon and launch screen.** A sunflower pyramid whose inner edge is a price line, over the letters EGX, on a dark ground. It replaces the lime arrow on blue, and themed icons get the same mark in one colour.
- **Notifications carry the pyramid** in the status bar in place of the arrow.
- **A 1D range on every chart.** The EGX30 tile on Home, the Portfolio chart, the stock sheet, the full-screen chart, a trade's chart and a call's chart each gain 1D as their first range. It draws the newest session in five-minute bars, with times under the line in place of dates, and is fetched when you press it. Nothing about it is stored.
- **On Portfolio, 1D is the day and not the trade.** It shows how your open trades moved through the newest session, on average, beside the EGX30 over the same minutes; every other range still shows what the trades have made since they were bought.
- **1D is absent for a stock the price feed has no intraday bars for,** so it never opens an empty chart.
- **Prices follow the session a minute at a time while the app is open.** During trading hours, your open trades, the calls still open and your watchlist are checked every minute instead of every quarter of an hour. The price feed itself runs about 15 minutes behind the exchange, so that is as close as any figure here gets. A price that looks like a split is held back until the stock's history has been fetched again.
- **A same-day entry and target are ordered more finely.** When one session touches both, the app now reads one-minute bars for sessions up to 29 days old, and five-minute bars beyond that. Today's session is ordered as it happens instead of the day after.
- **Price fixes for four stocks.** AIHC and AIDC each read their own listing, ACTF's symbol is corrected, and ORAS no longer reads a flat old feed.
- **The EGX 33 mark is a plain star, and "Needs a look" shows each trade on two lines.**

---

## [5.5.0] — 2026-11-12

- **Home's AI brief opens and closes on a press.** Shut, it shows its summary; open, the points, what to watch and who wrote it. A brief you have just asked for arrives open.
- **News opens inside the app.** A headline opens in a browser tab drawn over the app, with a close button back to Home, instead of leaving for the browser.
- **A refresh button on the news tile** fetches the headlines again, and only them: not the market figures, and never the paid brief.
- **Each coloured tile has its own colours, picked by you,** with darker fills and matching pale text in dark mode.

---

## [5.4.1] — 2026-11-12

- **Dark mode is easier on the eyes.** The coloured tiles (lime, sunflower, lilac, sky, coral, cobalt) are mid-tone at night instead of full strength, and the light-grey lead tiles ("0 of 4", the session tile, Needs you) are a soft slate. Light mode is unchanged.
- **A report's table is easier to read.** The To T1 and To T2 columns are gone so the rest are wider; rows are taller and the figures, channel, status and move are a size bigger.

---

## [5.4.0] — 2026-11-12

- **An open report fills the screen on the unfolded Fold.** The list of sessions no longer sits beside it; the back arrow or system back returns to the list. Before, the report had a narrow column, and scrolling either side moved both.
- **A report's calls are one table of numbers.** Column names are said once on a dark band at the top, each stock has its own shaded band with its price now, and each call is a row under it. The ladder is still in the sheet a row opens.
- **More figures per call:** the peak since the call and how far above the entry it went, how far the price is above the stop ("hit" once stopped), and how far it is from target 2 as well as target 1.
- **Held as a book,** a report splits on the hinge: what it amounts to on the left, its calls on the right.

---

## [5.3.0] — 2026-11-12

- **Settings is laid out for each way the Fold is held.** Unfolded, the list of parts stays where it is while the part you opened scrolls beside it under its own name. Before, both shared one scroll, so reading down a long part took the list away and left half the screen empty.
- **Held as a book,** the same two columns split on the hinge, and each part shows its full summary.
- **Half folded on a table,** the upright half stays put: the status, or on Notifications the one you picked with its example. Pressing a question mark shows its explanation there, not in a sheet over the half you are pressing. The parts and the settings are on the flat half.
- **On the cover screen,** an open part keeps its back button and name in place while it scrolls.
- **Reader is split in two.** Reader keeps the AI, the provider and key, and Advanced. A new part, Reading rules, holds what to send, the wording to keep and drop, the prompt and company names.
- **The theme is on the Settings hub,** with Pure black under it whenever the theme can be dark. It was inside Prices and trades.
- **The status tile is coral when something needs a look** and dark only when everything works. A part with something wrong carries a dot.
- **A Check key button** on Reader's status tile, while a saved key has never been tried.
- **Cards on a settings page are white.** Colour is kept for a status, an update that is ready, and Delete all analyses.
- **Coral is lighter everywhere:** tiles, the widget, notifications and the launcher shortcut.
- The Ask AI model card named its one field three times. It has one heading and one question mark now.
- The question marks down a settings card stand in one column.
- An opened row showed a lone "I" where its arrow should be, across the app. It is a chevron now.
- The update switch on the blue About tile showed only its knob. Its track is visible, and Download or Install leads the row of buttons.
- A long endpoint address wraps onto a second line. It was cut off mid-word.
- Token usage lines up with the settings above it.
- The prompt history rows show each version's code, so three versions no longer read the same.

---

## [5.2.0] — 2026-11-12

- **A new first tab, Home, and the app opens on it.** It shows the market before your trades: the EGX30 with its level, its move on the day and a line over a week, a month or three months, then EGX70 EWI, EGX100 EWI and the Shariah 33 on a row of their own.
- **Your book is on Home in three tiles.** One says what your open trades are making on average, and its colour follows the sign of that figure: deep teal when ahead, soft sage when flat, maroon when behind. One names the trades close to a stop or to target 2, or past their deadline, and a press opens that trade. One counts the calls published for the session.
- **The market in one tile:** how many stocks rose, held and fell in the last session, then the five that rose most and the five that fell most. A press on one opens its sheet.
- **The dollar, gold and Brent oil** each have a small tile with their move on the day.
- **News.** Headlines about the Egyptian exchange in Arabic first, then English ones from Enterprise, Daily News Egypt and Amwal Al Ghad. Each shows its source and its age, and a press opens the article in your browser. They refresh when Home opens if they are more than half an hour old, and on a pull.
- **Ask AI for a market brief.** A button on Home sends the index figures, the session's movers and the headlines' titles to your Ask AI model and shows a short read of the day. It asks before sending, it is the only thing on Home that uses credits, and nothing from Telegram and none of your trades are sent.
- Home is laid out for all three ways the Fold is held: one column on the cover screen, two when unfolded or held as a book, and the indices above the crease with the news below it when half folded.

---

## [5.1.9] — 2026-11-12

- **The trades table on the unfolded Portfolio is rebuilt in numbers.** Four columns: the stock with what it is doing under its ticker, what you paid and where the price is, a bar, and what the trade has made with its time under it. The compact ladder it replaces printed no prices.
- **The bar is centred on what you paid.** Red grows left toward the stop and green grows right toward target 2, with a notch at target 1 and the stop and target prices under the ends. A price below the stop fills the red side and points out of it.
- **Open trades are grouped:** Running, Waiting for you to sell, and Past deadline, each under its own heading.
- **The time under a return says something about the trade.** "6 days left" while it runs, "hit 5 Nov" once its stop has broken, "52d late" past its deadline, and "in 4 days" when finished. A trade kept open past its stop used to read "0 trading days left".
- **Pulling a page down to fetch prices moves the page.** The floating disc is gone: the page follows the finger and uncovers a band saying what the pull will do, how old the prices are, and how far there is to pull. A pull while a fetch is running says so and does nothing.
- **Changing tab is smoother.** The tab names move by a scale and a cross-fade, and they no longer shift from page to page: the header keeps room for both buttons everywhere.
- **The search button on Analyze picks a stock** and opens its sheet.
- Best source showed its hit rate multiplied by 100 twice, so 54.9% read as 5490%, on Portfolio and on Analyze. Fixed.

---

## [5.1.8] — 2026-11-11

- **A trade's card on Portfolio says what the trade needs.** One sentence leads it, the most urgent thing true of the trade: "Past its deadline by 3 days", "1.2% above its stop", "Target 1 reached, 3.6% to target 2", or how far it is to the next target. It replaces the "You paid · now" line, whose two prices are on the ladder.
- **The call's trading days are a row of segments** on that card, filled as they are used, with how many are left beside them. Once the deadline has passed they are all filled in the alert colour.
- **The card says whether the price is keeping pace with the clock.** It compares how far the price has come toward target 1 with how much of the call's time is used: "Ahead of pace", "On pace", or "Behind pace: 70% of the time used, 20% of the way".
- The card's tags are down to the status, "Kept open" before the deadline, T+1 and a change of price scale. The return is larger.
- **The price ladder is redrawn.** One plain line with four upright ticks: red for the stop, dark for the entry, green for the two targets. A round blue dot sits on the line where the price is, with the price above it, and every level's price is on one row beneath. The coloured zones between the levels and the arrow under the line are gone. It is the same ladder everywhere it is drawn: Portfolio, the Results cards on the cover screen, Insights and the call and trade sheets. The wide Results table shows numbers, and its ladder is in the sheet a line opens.
- **A trade's ladder on Portfolio shows where the stock has been.** A pale stripe runs from the lowest to the highest price since you bought, and the entry is labelled "You paid".
- **The Results table on a wide screen is a tile per stock, in numbers.** The shared row of column names is gone: the names sit above the figures of a tile's first call, and a second call lines up under them. The stock's price is in the tile's head once, or "no price" where the feed has none. Under the stop and each target is how far it is from the entry, in that level's colour. Each live call then says its move since entry and how far the price still has to go to target 1. Live calls come before watched ones, and T+1 is written after the status of the call that named it.
- **The Results list is one card per session,** under week headings, drawn from the session's newest reading: the figure, the counts written out, best and worst so far, and the stocks as tags. A session still to come says what is waiting. In an open report the stocks can be sorted by closest to target, biggest move or report order, and where a session was read more than once a pill opens a list of each reading and the stocks new since the last. Unfolded, the list stays on the left and the report opens on the right.
- **The price chart is rebuilt** on the stock sheet, the Insights call sheet and the position card. Level names and prices are tags in a column beside the plot, spread so none overlaps; the latest close has a filled tag of its own. Three dates sit under the plot, and a touched session reads out in the headline so nothing below the chart moves. The ranges are one full-width track, so none is cut off on the cover screen. The stock sheet's chart tile is navy in the light theme.

---

## [5.1.7] — 2026-11-11

- **The Results table is grouped by stock.** Each stock has a heading with its name, how many calls from how many channels, and the latest price, shown once. Each call is a row led by its channel, so calls on the same stock compare one above the other. Watching rows show the levels their post gave. T+1 calls carry a tag.
- **The session card on Insights matches the rest of the page.** The counts are one strip with one-line labels. Each event is a row with the stock, who called it and when, and a status in plain words. The new-calls line is a footnote.
- **A trade's sheet and a call's sheet read in the same order:** who it is, where it stands, the levels, what happened. The levels are a ladder with each level in its own column. A trade kept open past its stop or deadline says what that has cost.
- **Ranking cards on the cover screen keep the source's full name.** The two figures sit on their own row under it, and no longer run past the tile's edge.
- Sentences on Portfolio and Insights that were set in the figure font are in the text font.

---

## [5.1.6] — 2026-11-11

- **Pills side by side are one height.** A pill holding Arabic, such as a channel name, stood taller than an English one beside it, because the Arabic font reaches further above and below the line. Every pill of a size is now one fixed height, still growing with the phone's font size setting.

---

## [5.1.5] — 2026-11-11

The rest of the pills, and one 5.1.4 brought.

- **Pills on coloured tiles are readable again in dark mode.** 5.1.4 drew every pill's word in the page ink, which vanished on the stock sheet's lilac calls tile. A pill's word now takes the ink of whatever it sits on.
- **The last pills in the old styles are gone:** the timing pill and "Edited" in the Results call sheet and cards, the Ask AI answer's verdict, stance, direction and rating, the image count beside a channel on Analyze, and "reached" on the stock sheet's levels. All are the one filled pill; Ask AI's verdict stays larger.
- **One colour per status everywhere:** running blue, target reached green, stopped red, and grey for ran out of time, closed by you, not counted and watching. "Running" was a different blue on Portfolio, and a trade that ran out of time was amber.
- The stock sheet's status pills use the same plain words as every other page, and the Results "Today" tile shows its status in the status colour.

---

## [5.1.4] — 2026-11-11

- **One pill style everywhere.** Every label pill is now the filled one "Running" uses: a soft wash of colour with the word in ink. The outlined pills (channel names, T+1, Not counted, Kept open, days left, Overdue, Close to its stop, the misread-level warning and others) were the old design. Neutral notes are grey, warnings red, good news green. Filter chips you press to choose are unchanged.

---

## [5.1.3] — 2026-11-11

- **Swipe left or right on a page to change tab.** The header's highlight follows your finger toward the next tab's name and the page leans a little; let go past a quarter of the screen, or flick, and the new tab's tiles pop in as if you had pressed its name. A short swipe settles back, and the first and last tabs stretch slightly to show there is nothing further. Chip rows, the wide report table, charts and the ticker tape keep their own sideways scrolling.

---

## [5.1.2] — 2026-11-11

Dark mode, and colours that disagreed.

- **Names and headings no longer vanish in dark mode.** Stock names on Portfolio and Results cards, the Open now and Finished headings and finished-trade rows were drawn black on the dark page. All text now defaults to the app's own ink.
- **Portfolio cards have no coloured left edge and no coloured outline.** The status pill says how a trade stands.
- **One status pill everywhere:** Portfolio's outlined pill is now the filled one Results and Insights use.
- **The price marker and "how far it went" line use a lighter blue at night**, so they can be read on a dark card.
- **The price ladder prints each figure once**: no "You paid" on Portfolio (the card already says it), and a price equal to the entry is not printed twice. The current price always sits under the bar.
- **"Close to its stop" is no longer shown on a trade already past its stop** (the same for target 2).
- **Insights' day tile counts only running calls as running**, and says how many are not counted, so it agrees with its chips.
- A disabled button, like Ask AI, fades as a whole instead of keeping a full fill.
- "0 trading days left" is no longer shown on a trade that has ended.
- The stock chart's level labels sit just above their dashed lines, on a patch of the chart's colour, so no line runs through them.
- The Insights note says "blue line", which is what it is.

---

## [5.1.1] — 2026-11-11

Fixes for 5.1.0.

- **Unfolded and half-folded, Portfolio now answers links to a trade.** Record sale from a notification opens the sale, and a trade opened from Insights or a notification opens beside the table. Before, nothing happened until the phone was folded.
- **Arriving at a finished trade no longer keeps scrolling back to it** each time prices refresh.
- **Deleting the report you have open closes it**, so the first back press afterwards is not lost. The same goes for a report a filter hides.
- **Settings no longer says prices need a look on a weekend or before the market opens.** Prices count as late only once a trading session has closed since they last arrived. Public holidays may still be flagged.
- Opening a report from "Your report is ready" no longer leaves it waiting to open again later.
- The "read by an earlier run" note on Analyze updates when a report is deleted and another run is saved.

---

## [5.1.0] — 2026-11-11

**Plainer, everywhere.** Every tab says what is happening in ordinary words, shows less at first, and keeps the rest one press away.

- **Tabs change in place.** Pressing a tab no longer slides the whole screen sideways through the tabs in between. The header stays put, the name you pressed grows while the old one shrinks, and the page's tiles pop in. There is no swiping between tabs; tabs change from the header at every width, and the row keeps the previous tab's name visible.
- **Search and filter buttons redrawn** to the design: round, one fixed size, with the design's own glyphs. On the cover screen they sit beside the session line.
- **Plain status words.** "Target 1 reached", "Ran out of time", "Watching", instead of the scorer's terms.
- **Price ladders** on calls and trades are compact, with the loss, near and far zones shaded, and show how far the price has travelled.
- **Hold for the menu.** A long press on a call or trade card opens its actions.
- **Results.** Each run is a tile; an open report is a page of its own, closed with back. Calls are grouped with chips above them, a call opens on a press, and "About this run" says how the run went in a sentence.
- **Insights.** The podium is gone; the ranking is tiles, a source's record opens on a press, every call has a timeline of what happened when, and a day's calls can be filtered by chips.
- **Portfolio.** Open trades are short cards and finished ones are rows, with a trade opening as a sheet. Unfolded, the trades are a table with Open, Finished and All. A "Needs you" tile names late trades and ones near a level.
- **Analyze.** A clearer hero, a progress line that counts the batches read and says roughly when reading ends, a note on sources read by an earlier run and not sent again, and a preview of the posts it will read.
- **New notification: "A report is ready"**, with its own switch, which opens Analyze.
- **Settings.**
  - A status tile at the top says whether everything is working; pressing it opens whatever needs a look.
  - Parts renamed: Reader, Prices and trades, Backup and sync.
  - Reader's rarely used settings are behind **Advanced**, and token usage moved there.
  - Every notification's **?** shows an example of what it looks like.
  - Diagnostics is now "Something not right?" with **Save a report**.
- **Dark mode.** Text and figures on coloured tiles were re-coloured wherever they were hard to read, and a contrast test now checks every pair.

---

## [5.0.0] — 2026-11-11

**A new design, everywhere.** Every tab, sheet and dialog is redrawn as rounded tiles on paper (graphite at night), in cobalt, lime, coral, sunflower, lilac and sky, with every figure in a monospaced face. Nothing from the old look is left.

- **Every Fold posture.** Folded, unfolded, half-folded flat on a table, and held like a book each get their own layout. Unfolded and in book posture, lists and the thing you opened sit side by side, split on the hinge in book posture. In tabletop, what you read stands on the upright half and what you press lies on the flat half.
- **Tabs in the header.** There is no bottom bar or floating button any more; the tabs are at the top of every page, and the Analyze button is on the Analyze page.
- **Portfolio.** A hero tile with your trades against EGX30, what needs you, open positions, today's session and your record.
- **Analyze.** Sources, content types and the target date as tiles, with a progress fill while a run reads.
- **Results.** Each run is a tile with its figures and outcomes. Unfolded, the runs list sits beside the open report.
- **Insights.** A podium of the best sources, the ranking, calls still in play and the session's figures.
- **The stock sheet.**
  - The price is set large.
  - A chart on cobalt with a new **Call** range, which starts a week before the newest call.
  - **I bought it** on the newest call still in play, and Ask AI behind the usual confirmation.
  - A price-alert sheet, and a levels tile showing how far each level is from now.
- **The full-screen chart.**
  - Five chart types, eight indicators and eight drawing tools.
  - It follows the app's light or dark theme, and its text is the app's figure face.
- **Settings.**
  - A hub of tiles, each opening its own page.
  - Steppers instead of sliders, and a Light / Dark / Follow system control.
  - A four-step Telegram sign-in.
  - Wording rules on lime and coral tiles.
  - Prices moved to General.
- **Notifications** carry a colour for each kind and the stock's logo on the right.
- **The home-screen widget** is redrawn, and gains a taller size with three counts: hit a target, stopped and overdue.
- **The app icon, the launch screen and the shortcut icons** are redrawn: a lime rising mark on cobalt, with a monochrome layer for themed icons.

## [4.8.1] — 2026-11-09

- Sheets that slide up from the bottom, such as the stock sheet, no longer close when you flick back up to the top of them. A drag that scrolled the sheet's content can't pull the sheet down; to close it from there, let go at the top and pull down again. The handle and the sheet's fixed top still close it as before.

## [4.8.0] — 2026-11-09

- The **stock sheet** matches the new design. The price has its own line in large type, with the day's move beside it in gain or loss colour, and it rolls to the new figure when prices refresh. The recommendation rate is set large too.
- The sheet's sections have no border, and they fade in one after another when it opens.
- The rows for your trades and for each call shrink slightly when pressed. **Show all calls** now has an arrow like other buttons that open a section.
- The **Alert** dialog's Rises to / Falls to buttons use the same chips as the filter sheet.
- The cards on a call's detail sheet also lose their border.

## [4.7.1] — 2026-11-08

- The **Cancel analysis** button shown during a run is solid again; the page behind it no longer shows through its label.
- Losses and stop losses are now a plain **signal red** instead of brick, in the app and on the widget.

## [4.7.0] — 2026-11-08

- The accent is now a lighter **amber**. Filled buttons such as Analyze use the light amber with dark text; orange text and links use a deeper amber so they stay readable.
- Losses and stop losses are now a warmer **brick red**. App errors, such as a failed run, use a deeper red so they never look like a loss.
- Trades that ran out of time are now **grey** instead of amber, so they can't be mistaken for a button.
- The **Ask AI** button matches the new design: a solid dark capsule to ask, an outline to reopen a saved answer. The violet glow and gold sparkle are gone; a light sweep shows while a request is out.
- The home-screen widget's red and the notification colour follow the new palette.

## [4.6.0] — 2026-11-08

- A **Sync with Telegram** switch in Settings → Data and backup → Sync. Off, this phone stops sending anything to your sync channel or reading from it; reading your source channels for an analysis still works. Nothing is lost while it is off, and switching it back on catches up straight away. The switch applies to this phone only.

## [4.5.1] — 2026-11-08

- Losses, stop losses, negative returns and anything else red about money are now a true dark red instead of pink.
- The accent colour on buttons and other things you press is now burnt orange, so it can't be mistaken for a loss.

## [4.5.0] — 2026-11-08

- **A new look.** Warm paper in light mode and warm ink in dark, one vermilion accent on every tab, and the Inter Tight typeface for names, tickers and figures. Lead figures are much larger, and cards are flat tonal surfaces with no borders.
- Vermilion is used only on things you can press. Good hit rates and record figures are drawn in ink, and open trades in the market blue. Losses are a deeper crimson so they never look like the accent.
- New motion: buttons change shape when pressed, the navigation highlight springs between tabs, lists fade in, figures roll to new values, outcome bars spring out and price lines draw in. All of it stays still when the phone's reduce-motion setting is on.
- The Analyze button is a solid capsule that fills with the run's progress.
- Every sheet now has the same rounded top and handle, and every text field has rounded corners.
- The launch screen matches the theme instead of flashing navy, notifications carry the accent colour, and the widget's overdue red matches the app.
- The app is about 600 KB smaller after removing two fonts it no longer draws.

## [4.2.3] — 2026-10-14

- A dropped connection or a provider error now fails the run with the cause and how long the request waited, instead of saving a report with no calls. The retry rules from 4.2.0 are back to the 4.1.1 behaviour (timeouts only).
- An unusable ranking from the model (such as an empty `[]`) is now asked for once more, and if it still fails the calls are ranked on the phone.
- Failed requests are included in the saved traces.

## [4.2.2] — 2026-10-14

- Save diagnostics now also saves the request traces (`egx-traces-<date>.zip`), so a run that returns nothing can be read off the phone.

## [4.2.1] — 2026-10-11

- Tapping a stock's heading or the column titles in a report's table no longer closes the report on wide screens.

## [4.2.0] — 2026-10-11

- **Watch list.** A Watch button on a stock's sheet follows it. A "Watching" card at the top of Insights shows its last close. Watched stocks are included in every price fetch.
- **Price alerts.** An Alert button on the stock sheet notifies you once when a stock rises or falls to a price you choose. It is checked whenever prices are fetched, and a still-trading price counts.
- **Save the source ranking as Excel** from "Sources ranked" in Insights.
- A price fetch with no connection now says "Could not reach the price feed" and tries again at the next slot, instead of counting the day as done. Partial failures show "N unreachable".
- A momentary provider refusal (429/5xx) or a dropped connection on one analysis batch is retried once instead of failing the whole run.
- Scheduled price fetches and the five-minute archive now wait for the saved record to load, so a cold alarm wake no longer sees an empty one. "Keep open" from a notification now works with the app closed.
- "Refresh chats" no longer closes the app on a Telegram error. Fetches, backups, restores, syncs and exports now finish after folding or unfolding the phone, and the daily backup no longer reports a false failure.
- Today's earlier backup copy is kept until the new one is complete.
- A provider address that Settings would refuse is now also refused when it arrives from sync, a restored backup, or a model pick.
- A saved key the phone can no longer decrypt is treated as missing, with "Save it again in Settings".
- "Use the next EGX session" returns from a historical date. "Preview messages" shows the real outcome. Setting rows toggle by tapping their words and are read properly by screen readers. A few wording fixes, a faster start, and some unused code removed.

## [4.1.1] — 2026-10-11

- Correcting a call's ticker now moves only that call, and it joins the other calls for that stock in the report instead of showing the stock twice.
- Editing a corrected call again no longer throws away the earlier correction. "Was …" under a field now shows what the model read.
- Undo on the message line now works after a first correction, and after a later one it brings back the previous correction instead of erasing all of them.
- A trade is never moved onto one you already hold for that stock and session. "Also correct the trade" starts unticked when the trade was recorded from a different channel, and undoing a correction moves a trade back only if that correction moved it.
- Company names you type stick, including for tickers the catalog doesn't know.
- Save is disabled while a price, date or image number can't be read. A decimal comma and Arabic digits are accepted, and unticking the timing chip now counts as a change.
- "Undo all" now also moves trades back and clears the AI opinions left on corrected calls.

## [4.1.0] — 2026-10-06

- **Expanded chart.** "Expand", beside the move % on a stock's sheet, opens its price chart on the whole screen: candles or a line, volume underneath, and the stop, entry and target lines drawn at their real prices. Drag the price axis up or down to zoom the scale in or out, and double-tap it to fit the chart again.
- Motion, press feedback, edge fades, undo and the Results cards were tidied across the app.

## [4.0.1] — 2026-09-25

- The app now ships a startup profile recorded from what it actually does when it opens and while you first scroll, so it should feel a bit faster to open. No other change.

## [4.0.0] — 2026-09-24

A major release: a full pass over the app's warnings, dialogs, pop-ups and pills, plus real speed and reliability work.

**Reports and corrections**
- Corrections can now be undone one call at a time, not just all at once, from both the report menu and the call's own menu.
- Undoing a correction that had moved a trade to a different ticker now moves the trade back too.
- A closed trade's "Edit trade" now edits the sale itself (price and date), and a closed trade can be reopened.
- "Share as text" now shares the corrected report, in plain text with no markdown.
- Removed the unused custom system prompt / phrase customization feature.

**Pop-ups, pills and holds**
- Holding a call card, an overdue trade, a session card, or a stock's name opens a proper menu naming what you held, instead of a plain two-button box.
- Insights call cards can now open the screenshot, copy the call as text, jump to its report, or record a purchase, straight from a hold.
- Every pill that used to do nothing now explains itself on tap: trade status, Overdue, Kept open, Price scale changed, T+1/Watching/date pills, and the EGX 33 badge. "Newer run exists" now opens that run directly.
- The outcome, T+1 and "levels look misread" pop-ups are slide-up sheets now, matching the rest of the app.

**Speed and reliability**
- The app opens noticeably faster - saved reports, trades and Ask AI answers now load in the background instead of blocking the first screen.
- Charts on trade and call cards now update after a price refresh, matching the stock sheet.
- Chat and channel pictures stay in memory once loaded, so they stop flashing back to a placeholder on every tab switch or fold.
- Correcting a call is faster on a large record - only that one report is re-read.
- Results and Insights now show your latest 10 reports/sessions with a "Show older" button instead of the whole record at once. Filters, search and jumping in from a notification still reach everything.
- Fixed a rare case where two rapid actions could briefly show stale data.
- Fixed a stack of saved-run cards sometimes snapping in size mid-swipe.

**Dialogs and sign-in**
- Bought/Sold/Edit trade now use a calendar picker for the date, and only Cancel closes the dialog - tapping outside no longer discards what you typed.
- The Analyze button now asks before cancelling a run in progress.
- An undo on the status line now stays up for 10 seconds and won't be interrupted by the next message.
- Telegram sign-in has a way back at every step (switch to phone number, resend code, change number, start over).
- Phone and code fields use a number pad; the keyboard's own key submits the step.
- Clearer error messages for the common Telegram sign-in failures.
- Ticked Telegram chats are remembered per phone and listed first.

**Settings and elsewhere**
- A dozen wording fixes: delete/remove warnings now say what they take with them; the T+1 and default-window explanations no longer overstate how long a call is followed; Sync and Delete explanations match what the app actually does; the overdue reminder is reworded to match how it actually works.
- The home-screen widget now opens Portfolio.
- One notification if the daily backup can't reach its folder, instead of failing silently.
- Ask AI's model picker now matches the analysis picker.
- "Remove credential" and "Reset provider" confirm first and say what they erase.
- Results and Portfolio's date filters gain "This week"/"This month" shortcuts.

## [3.8.9] — 2026-09-22

- Insights: fixed the T+1 pill on a call card reading as detached from the "Still open"/outcome pill above it - the gap between them wasn't the 4dp `spacedBy` the `Column` asked for but up to 48dp of invisible touch-target padding Material3's clickable `Surface` adds by default, which also centred the narrower T+1 pill instead of flushing it to the same left edge. Both pills now opt out of that minimum via `LocalMinimumInteractiveComponentSize`.
- Insights: added 8dp of breathing room above that same pill stack, between it and the card's own top edge.
- Insights: retuned the gap between a call's Arabic company name and the channel/source line below it from 16dp down to 10dp.
- Insights: "called `<date>`" now sits on the channel/source name's own line, separated by a middot, instead of leading the row of settled/repeat facts below it - which is now only drawn when there is a settled date or a repeat to say.

## [3.8.8] — 2026-09-20

- Results: fixed the "Tap to open report" hint and run dots overlapping the stat strip on a shut card held to a taller sibling's height - the `Box` used to pin the footer to the bottom sized itself to the taller of its two overlaid children rather than their sum, so it fell short whenever the floor was less than this card's own header-plus-footer height. The gap between them is now measured and inserted explicitly in a plain top-to-bottom layout, which cannot overlap.

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
