# Third-party notices

## Fonts

Three families are bundled in `app/src/main/res/font/` and drawn by `ui/theme/Type.kt`. All three
are licensed under the **SIL Open Font License, Version 1.1**, which permits bundling in an
application, modification, and redistribution, and asks that the licence travel with the fonts —
which is what this file is for. The full text is at <https://openfontlicense.org>.

| Family | Files | Source |
|---|---|---|
| **Cairo** | `cairo_semibold.ttf`, `cairo_bold.ttf` | [google/fonts `ofl/cairo`](https://github.com/google/fonts/tree/main/ofl/cairo) |
| **IBM Plex Sans Arabic** | `plex_arabic_regular.ttf`, `plex_arabic_medium.ttf`, `plex_arabic_semibold.ttf` | [google/fonts `ofl/ibmplexsansarabic`](https://github.com/google/fonts/tree/main/ofl/ibmplexsansarabic) |
| **IBM Plex Mono** | `plex_mono_regular.ttf`, `plex_mono_medium.ttf` | [google/fonts `ofl/ibmplexmono`](https://github.com/google/fonts/tree/main/ofl/ibmplexmono) |

Copyright: Cairo — the Cairo Project Authors. IBM Plex Sans Arabic and IBM Plex Mono — IBM Corp.

### The one modification

Cairo ships upstream only as a variable font, `Cairo[slnt,wght].ttf`. The two files here were
**instanced** from it — the weight axis pinned at 600 and 700 and the slant at 0 — using
`fontTools.varLib.instancer`, so they load as ordinary static fonts. Nothing else about them was
touched, and no reserved font name is claimed. Instancing is what the OFL calls a modified version;
the licence is unchanged and travels with them.

The other five are upstream statics, byte for byte.

### Why these three, and not fewer

A row in this app reads `AMOC · الإسكندرية للزيوت المعدنية · 7.20 – 7.45` — Arabic, Latin and figures
in one line, with the figures meant to be compared down a column. Cairo sets the names, IBM Plex
Sans Arabic sets the sentences in both scripts without changing voice mid-line, and IBM Plex Mono
sets every figure at one digit width. They are bundled rather than fetched because the app is
sideloaded onto two phones and has to draw its own record with no network.
