# Desktop parity backlog

Changes proven on Android that the desktop still needs. Android leads because it is quick to
rebuild and install; the desktop ships through a tagged release and a PyInstaller sidecar, so
porting is batched rather than continuous.

Ordered by what it costs to leave alone.

## 1. Yahoo dropped the legacy symbols — desktop has no prices after 29 July 2026

`app/scoring.py:25` fetches one symbol per stock:

```python
YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}.CA"
```

Yahoo moved EGX listings to ISIN-form symbols on 30 July 2026. `SYMBOL.CA` is frozen at 29 July
and `EGS…CA` starts at 30 July, so **neither form alone covers a window spanning that date**.
Desktop currently gets nothing for any session from 30 July onward, which stops Insights scoring
silently rather than loudly — unpriced stocks look like stocks that did not move.

Android reads both feeds and merges by date, live feed winning, from a 211-row map of
EGX symbol / Yahoo symbol / ISIN / company name built by price continuity rather than by name:

- `app/src/main/assets/yahoo_symbols.json`
- `app/src/main/java/com/ikverse/egxanalyzer/data/SymbolMap.kt`
- `app/src/main/java/com/ikverse/egxanalyzer/data/PriceRepository.kt` — the merge

The asset ports across unchanged. This is the one item worth a release on its own.

## 2. Prompt schema 4

Desktop is on schema 3 (`app/ai/prompts/consolidated_recommendation.md:1`), Android on 4. The
difference is not cosmetic — schema 3 defines `date` as the constant `TARGET_DATE` while also
forbidding the model from copying `TARGET_DATE` into `date`, which is not a rule that can be
obeyed. That is why stale re-posted cards kept arriving dated to the target session with the real
date sitting in `visible_source_date`.

Schema 4 makes `date` the model's own judgement read from the source, and adds an `excluded` array
so a dropped item says why it was dropped instead of being pure absence.

Ports to `app/ai/prompts/consolidated_recommendation.md`, plus the parser side:
`ModelExclusion`, `ConsolidatedParser.exclusions()`, and the Results display.

## 3. Source date gate

`app/analysis_validation.py` had `enforce_target_date` removed. Android replaced it with arithmetic
rather than an instruction, in `model/SourceDateGate.kt`: a source belongs to the session it names,
and only the target session or the one before it qualifies — the trading session before, so a card
printed over a weekend still counts for Sunday's open.

Both directions are rejected. Older is a re-posted stale card. Later is next session's card,
published in the afternoon, which belongs to tomorrow's analysis where it also appears.

Covered by `SourceDateGateTest`, which ports as-is.

## 4. Temperature fixed at 0

Neither app sends `temperature` today, so both get the provider default — around 0.7, which is
sampling on a task that has one right answer printed in the source. Android now sends `0.0`
(`data/AnalysisRepository.kt`).

Desktop has two request builders and both need it:

| Call | Line | Used when |
| --- | --- | --- |
| `chat.completions.create` | `app/ai/service.py:511` | provider ≠ openai — the Qwen path |
| `responses.create` | `app/ai/service.py:532` | provider = openai |

No setting and no slider in either app: no value above 0 is useful for extraction.

Worth knowing: 0 is near-deterministic, not bit-identical. Hosted models still vary slightly from
batching and floating-point ordering, and output changes outright when the provider updates the
model behind a `qwen-*` name.

## 5. Insights: newest run per channel

Desktop dedupes by `(ticker, channel, opened_on)`. Android resolves a session to the newest run
that actually covered each chat, so a later run over fewer channels does not erase a channel an
earlier run scored (`data/PerformanceCalculator.kt`).

Android also warns before a duplicate run — same target date and same channels only — and merges
the delta rather than replacing the card.

## 6. Chunked extraction and a separate consolidation pass

The desktop sends one request per run with every image in it, the arrangement that made the model
lose track of which image it was describing: in a 32-image run it cited IMAGE_REF 4 for a card that
was image 15, and 13 of 37 exclusions named an image other than the one they described. Desktop is
exposed to exactly this, in `app/ai/service.py`.

Android now splits a run into extraction requests of 8 images (`data/AnalysisChunking.kt`), maps
chunk-local references back to the run's numbering, and finishes with one text-only consolidation
request that ranks the occurrences (`assets/consolidation.md`). Prompt schema 5.

It also reconciles every image sent against the response, so an image the model never mentioned is
reported rather than silently missing.

## 7. Apply the source date gate to parsed output

Android's `SourceDateGate` existed for a day without being reachable: `ConsolidatedParser.parse`
was called without a target date, which makes the gate accept everything. Worth checking that
whatever the desktop grows in its place is actually wired to a date, since the failure is silent
and looks exactly like a gate that works.

## Not portable

Background analysis, the foreground service and notification deep-linking are Android platform
concerns with no desktop counterpart.
