# Third-party notices

## Fonts

Three families are bundled in `app/src/main/res/font/` and drawn by `ui/theme/Type.kt`. Two further
weights of IBM Plex Mono sit in `app/src/next/res/font/`, where only the `next` build type picks them
up — the redesign sets every figure at 600 and a rank number at 700, and the shipping app has no use
for either, so they are kept out of its APK. All three families
are licensed under the **SIL Open Font License, Version 1.1**, which permits bundling in an
application, modification, and redistribution, and asks that the licence travel with the fonts —
which is what this file is for. The full text is at <https://openfontlicense.org>.

| Family | Files | Source |
|---|---|---|
| **Cairo** | `cairo_semibold.ttf`, `cairo_bold.ttf` | [google/fonts `ofl/cairo`](https://github.com/google/fonts/tree/main/ofl/cairo) |
| **IBM Plex Sans Arabic** | `plex_arabic_regular.ttf`, `plex_arabic_medium.ttf`, `plex_arabic_semibold.ttf` | [google/fonts `ofl/ibmplexsansarabic`](https://github.com/google/fonts/tree/main/ofl/ibmplexsansarabic) |
| **IBM Plex Mono** | `plex_mono_regular.ttf`, `plex_mono_medium.ttf`, `plex_mono_semibold.ttf`†, `plex_mono_bold.ttf`† | [google/fonts `ofl/ibmplexmono`](https://github.com/google/fonts/tree/main/ofl/ibmplexmono) |

Copyright: Cairo — the Cairo Project Authors. IBM Plex Sans Arabic and IBM Plex Mono — IBM Corp.

### The one modification

Cairo ships upstream only as a variable font, `Cairo[slnt,wght].ttf`. The two files here were
**instanced** from it — the weight axis pinned at 600 and 700 and the slant at 0 — using
`fontTools.varLib.instancer`, so they load as ordinary static fonts. Nothing else about them was
touched, and no reserved font name is claimed. Instancing is what the OFL calls a modified version;
the licence is unchanged and travels with them.

The other seven are upstream statics, byte for byte. † marks the two that live under
`app/src/next/res/font/` rather than `main`.

### Why these three, and not fewer

A row in this app reads `AMOC · الإسكندرية للزيوت المعدنية · 7.20 – 7.45` — Arabic, Latin and figures
in one line, with the figures meant to be compared down a column. Cairo sets the names, IBM Plex
Sans Arabic sets the sentences in both scripts without changing voice mid-line, and IBM Plex Mono
sets every figure at one digit width. They are bundled rather than fetched because the app is
sideloaded onto two phones and has to draw its own record with no network.

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
