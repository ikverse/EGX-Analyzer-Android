package com.ikverse.egxanalyzer.model

/**
 * One stock, as the header's search box needs to know it.
 *
 * The catalog behind this lives in `data/EgxCatalog.kt` and its `EgxStock` carries aliases, a
 * refresh timestamp and the merge rules that keep a downloaded entry from overwriting a better seed
 * one. None of that is a screen's business, and `ui` may not import `data` in any case, so what
 * crosses into the UI is a ticker and the two names a person might type.
 *
 * Both names are nullable because the catalog's own are: a downloaded entry sometimes arrives with
 * only the English side filled in.
 */
data class DirectoryStock(
    val ticker: String,
    val nameEnglish: String?,
    val nameArabic: String?,
)
