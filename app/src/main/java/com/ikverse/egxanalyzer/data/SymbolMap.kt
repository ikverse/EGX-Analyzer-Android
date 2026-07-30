package com.ikverse.egxanalyzer.data

import android.content.res.AssetManager
import com.ikverse.egxanalyzer.model.Scoring
import org.json.JSONArray

/** How Yahoo identifies one EGX stock, alongside the code the exchange and the sources use. */
data class SymbolMapping(
    val egxSymbol: String,
    val yahooSymbol: String,
    val isin: String?,
    val company: String?,
)

/**
 * Which Yahoo symbol carries a stock's prices.
 *
 * Yahoo moved EGX listings onto ISIN-form symbols on 2026-07-30. The old `SYMBOL.CA` feed still
 * serves history but stops at 29 July; `EGS...CA` serves everything after it and nothing before.
 * Neither is complete on its own, so prices are read from both and stored under the exchange's own
 * code.
 *
 * The pairing is not derivable from the ticker and Yahoo transliterates Arabic names differently
 * from the exchange - "Abu Qir" against "Abou Kir" - so matching on names alone produced three
 * separate stocks claiming one symbol. The shipped mapping was instead built by joining the two
 * feeds on price: a session's open equals the previous session's close, so the legacy 29 July close
 * and the ISIN 30 July open identify the same stock exactly.
 */
class SymbolMap(private val assets: AssetManager) {

    private val byEgxSymbol: Map<String, SymbolMapping> by lazy {
        runCatching {
            val text = assets.open(MAPPING_ASSET).bufferedReader().use { it.readText() }
            val entries = JSONArray(text)
            buildMap {
                for (index in 0 until entries.length()) {
                    val item = entries.optJSONObject(index) ?: continue
                    val egx = Scoring.normalizeTicker(item.optString("egx"))
                    val yahoo = item.optString("yahoo").takeIf(String::isNotBlank) ?: continue
                    if (egx.isBlank()) continue
                    put(
                        egx,
                        SymbolMapping(
                            egxSymbol = egx,
                            yahooSymbol = yahoo,
                            isin = item.optString("isin").takeIf(String::isNotBlank),
                            company = item.optString("company").takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    val size: Int get() = byEgxSymbol.size

    operator fun get(egxSymbol: String): SymbolMapping? =
        byEgxSymbol[Scoring.normalizeTicker(egxSymbol)]

    /**
     * Every symbol worth asking Yahoo about for one stock, newest feed first.
     *
     * An unmapped stock still gets its legacy symbol tried: that is where a year of history lives,
     * and a stock the mapping missed is better served by stale prices than by none.
     */
    fun feedsFor(egxSymbol: String): List<String> {
        val egx = Scoring.normalizeTicker(egxSymbol)
        if (egx.isBlank()) return emptyList()
        val legacy = "$egx$LEGACY_SUFFIX"
        val live = byEgxSymbol[egx]?.yahooSymbol
        return if (live == null || live == legacy) listOf(legacy) else listOf(live, legacy)
    }

    private companion object {
        const val MAPPING_ASSET = "yahoo_symbols.json"
        const val LEGACY_SUFFIX = ".CA"
    }
}
