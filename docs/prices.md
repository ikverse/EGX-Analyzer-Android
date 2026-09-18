## Prices and feeds

- `PriceRepository` fetches **from where a stock's stored history stops**, via `period1`/`period2`,
  not a fixed range. It used to ask for `5d`: a phone left shut for a week got a hole that every
  later refresh stepped straight over, permanently — and a call whose window contains a hole never
  completes, so it never expires and never shows as overdue. A stock with an open trade is fetched
  from that trade's call date instead, which heals holes that already exist. An empty dated response
  falls back to `range=1y`, so the change cannot do worse than the fixed range it replaced.
- **Every fetch is checked for a change of scale before it is stored** — `PriceSanity`, called from
  `PriceRepository`. A move beyond 30% between two sessions no more than a week apart is not
  something the exchange permits, so it is a corporate action: a split or a bonus issue. The check
  runs across the **boundary between what is stored and what was just fetched**, because that is
  where it always falls — incremental fetching is what leaves the two halves in different money, and
  Yahoo rewrites its own history when a stock splits. So the first response is to **refetch the
  whole year and replace the stored series** (`deleteSessionsFrom` + `clearPriceBreaks`), which
  heals it. Only a break that survives that is recorded, in `price_events`, and it is recorded
  rather than corrected: guessing a ratio and rescaling a year of prices would be the app inventing
  history. Breaks are **local and deliberately not synced** — every device fetches the same public
  feed and reaches the same conclusion, and a device's opinion about a feed is not evidence.
- **The ISIN feed's *daily* endpoint serves only the newest session; its *intraday* endpoint holds
  the history.** Measured 19 August 2026 across all 262 Cairo listings: 257 returned exactly one
  daily session and 236 of those only that day — `^CASE30` included, so it is no longer the way to
  ask whether the exchange traded. The same symbols answer `interval=5m` with about four weeks and
  `interval=1h` with about two years. History therefore comes from the **legacy `SYMBOL.CA`** feed,
  which is alive and deep (25 sessions in a 40-day window), and the ISIN symbol contributes the
  current session. This is why `fetchAllFeeds` must keep reading both — dropping the legacy feed
  would leave every stock with a one-day history.
- **A stock with no legacy symbol had no daily history at all, and now has a rebuilt one.** VLMRA is
  the case: `VLMRA.CA` is a 404, so the merge had only the ISIN feed's single session and a call
  made on it kept a permanent hole in its window — which never completes, so the call never expired
  and sat pending for good, outside every rate. `IntradayRepository.dailyHistory` aggregates the
  intraday feed into daily sessions instead. See **Rebuilding a history the daily feeds do not
  carry**.
- **The legacy `SYMBOL.CA` feed ignores `interval` and answers with daily rows.** Nothing in the
  response shape says so — only `meta.dataGranularity` does. Taken at face value, the one daily bar
  it returns would be read as the whole session and would "prove" that the entry and the target
  happened at the same instant: a confident verdict on a question the feed was never asked.
  `parseSessionBars` checks the granularity against whatever was **asked for** and refuses a
  mismatch, and intraday is only ever requested against the ISIN symbol. Also measured: a 5m window
  reaching back **59 days answers, 90 days is refused outright with HTTP 422** rather than trimmed,
  so the window has to be clamped by the caller or the whole request fails.
- **A frozen feed is not the same as an unpriced stock, and is harder to see.** `unpriced` means no
  history at all; `stale` means the series answers every request while its newest session stays put.
  That has happened here — the ISIN migration — and nothing noticed at the time. Seven days, which
  clears the Friday–Saturday weekend plus a public holiday. Both are now standing state on Insights
  rather than a count in a toast — see **When the feed goes quiet**.
