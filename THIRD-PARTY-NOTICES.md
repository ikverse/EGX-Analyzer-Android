# Third-party notices

## Fonts

Four families are bundled in `app/src/main/res/font/` and drawn by `ui/theme/Type.kt`. All are
licensed under the **SIL Open Font License, Version 1.1**, which permits bundling in an
application, modification, and redistribution, and asks that the licence travel with the fonts —
which is what this file is for. The full text is at <https://openfontlicense.org>.

| Family | Files | Source |
|---|---|---|
| **Bricolage Grotesque** | `bricolage_grotesque.ttf` (the upstream variable font, axes `opsz`, `wdth`, `wght`, unmodified) | [google/fonts `ofl/bricolagegrotesque`](https://github.com/google/fonts/tree/main/ofl/bricolagegrotesque) |
| **JetBrains Mono** | `jetbrains_mono.ttf` (the upstream variable font, axis `wght`, unmodified), in `res/font` and a second copy in `assets/chart` for the full-screen chart's page | [google/fonts `ofl/jetbrainsmono`](https://github.com/google/fonts/tree/main/ofl/jetbrainsmono) |
| **IBM Plex Sans Arabic** | `plex_arabic_regular.ttf`, `plex_arabic_medium.ttf`, `plex_arabic_semibold.ttf` | [google/fonts `ofl/ibmplexsansarabic`](https://github.com/google/fonts/tree/main/ofl/ibmplexsansarabic) |

Copyright: Bricolage Grotesque — 2022 The Bricolage Grotesque Project Authors
(<https://github.com/ateliertriay/bricolage>). JetBrains Mono — 2020 The JetBrains Mono Project
Authors (<https://github.com/JetBrains/JetBrainsMono>). IBM Plex Sans Arabic — IBM Corp.

Every file is upstream, byte for byte. The variable fonts are instanced per weight (and, for
Bricolage, per optical size) at runtime through their variation axes rather than as separate
files, so nothing here is a modified version.

**Since 5.0**, Bricolage Grotesque sets names, headings and text, JetBrains Mono sets every figure,
and IBM Plex Sans Arabic is the fallback that carries every Arabic letter, since neither of the
other two has Arabic. What follows describes the 4.x arrangement.

### Why these two

A row in this app reads `AMOC · الإسكندرية للزيوت المعدنية · 7.20 – 7.45` — Arabic, Latin and figures
in one line, with the figures meant to be compared down a column. Since the 2026-11 redesign, Inter
Tight sets page names, tickers and every figure, with tabular digits so a column of prices lines up;
IBM Plex Sans Arabic sets the sentences in both scripts without changing voice mid-line, since Inter
Tight has no Arabic. Cairo and IBM Plex Mono, bundled before the redesign, were removed in 4.5.0.
They are bundled rather than fetched because the app is sideloaded onto two phones and has to draw
its own record with no network.

## Company logos

222 vector drawables in `app/src/main/res/drawable/`, named `logo_<ticker>.xml`, one per EGX company
the catalog lists. Drawn by `ui/StockLogo.kt`, mapped by the generated `ui/StockLogos.kt`.

Each is a **registered trademark of the company it identifies** — not the app's to license. They are
used here nominatively: to mark the company's own stock in a list of its own stock, which is what
the mark exists to do and is the same use a broker's app or a price screen makes of it. No
endorsement is claimed or implied, and none of these companies is affiliated with this app.

### Provenance, and the caveat

The source files were SVGs served by TradingView's symbol CDN
(`s3-symbol-logo.tradingview.com`), located through the `logoid` their public EGX scanner returns
per ticker, then converted to Android vector drawables with
[`svg2vectordrawable`](https://www.npmjs.com/package/svg2vectordrawable) (MIT). 216 matched on
ticker; 6 — `ACTF`, `AIHC`, `ALRA`, `ANFI`, `FERC`, `NAPR` — matched on company name, because
TradingView lists them under a different or a since-renamed ticker.

**The caveat, recorded plainly:** the trademark position above covers using the marks. It does not
cover taking TradingView's asset files, and their terms do not grant that. This is a redistribution
of their copies, and it is here because no source of EGX logos with clearer terms exists — the
alternatives checked were Clearbit's logo API (shut down), Wikidata (8 EGX companies tagged, 3 with
a logo), and the app's own catalog endpoint (serves none). Replacing any of these with a file taken
from the company's own site or press kit is strictly better, and requires nothing but dropping it in
under the same name.

`ARVA` (Arab Valves) has no logo published by any of these sources. It draws the monogram fallback
in `StockLogo.kt`, as does any ticker a later catalog refresh introduces.

## KLineChart

The expanded chart on the stock sheet is drawn by [KLineChart](https://github.com/klinecharts/KLineChart)
9.8.12, bundled unchanged as `app/src/main/assets/chart/klinecharts.min.js`. It is licensed under the
**Apache License, Version 2.0** (<https://www.apache.org/licenses/LICENSE-2.0>), which permits
bundling and redistribution and asks that the licence and the copyright notice travel with it. The
notice (© 2019 lihu) is the header comment at the top of that file, which is left in place for that
reason. The same file, at the same version, is bundled by Signal Lab.
