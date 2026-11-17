package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.Dividend
import com.ikverse.egxanalyzer.model.DividendCheck
import com.ikverse.egxanalyzer.model.DividendRecord
import com.ikverse.egxanalyzer.model.PaidDividend
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * What the app knows about dividends, in one small file in `filesDir`.
 *
 * Like `home_cache.json`: a copy of public facts, replaced whole, and losing it costs one fetch of
 * the news page (the feed's table refills as prices are fetched). So it is device-local and stays
 * out of the database, every backup, the sync channel and Save diagnostics, with no schema version.
 */
class DividendStore(private val file: File) {

    fun load(): DividendRecord =
        runCatching { if (file.exists()) decodeDividends(file.readText()) else DividendRecord() }.getOrDefault(DividendRecord())

    fun save(record: DividendRecord) {
        runCatching {
            // Written beside and renamed over, so a process killed mid-write leaves the old copy.
            val draft = File(file.parentFile, "${file.name}.tmp")
            draft.writeText(encodeDividends(record))
            if (!draft.renameTo(file)) {
                file.writeText(draft.readText())
                draft.delete()
            }
        }
    }

    companion object {
        const val FILE_NAME = "dividends.json"
    }
}

internal fun encodeDividends(record: DividendRecord): String = JSONObject().apply {
    put("newsAt", record.newsAt)
    put("dividends", JSONArray().apply {
        record.dividends.forEach { d ->
            put(JSONObject().apply {
                put("code", d.code)
                d.amount?.let { put("amount", it) }
                d.assemblyDay?.let { put("assembly", it.toString()) }
                d.paymentDay?.let { put("payment", it.toString()) }
                d.lastCumDay?.let { put("lastCum", it.toString()) }
                d.announcedDay?.let { put("announced", it.toString()) }
                put("estimated", d.lastCumEstimated)
                d.feedExDay?.let { put("feedEx", it.toString()) }
                d.feedAmount?.let { put("feedAmount", it) }
                put("check", d.check.name)
                put("headline", d.headline)
                put("url", d.url)
            })
        }
    })
    put("paid", JSONObject().apply {
        record.paid.forEach { (ticker, list) ->
            put(ticker, JSONArray().apply { list.forEach { put(JSONArray().put(it.exDay.toString()).put(it.amount)) } })
        }
    })
}.toString()

internal fun decodeDividends(text: String): DividendRecord {
    val json = JSONObject(text)
    fun JSONObject.day(key: String): LocalDate? = optString(key).takeIf(String::isNotEmpty)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun JSONObject.num(key: String): Double? = if (has(key)) optDouble(key).takeUnless(Double::isNaN) else null
    val dividends = json.optJSONArray("dividends")?.let { array ->
        (0 until array.length()).mapNotNull { i ->
            val d = array.optJSONObject(i) ?: return@mapNotNull null
            val code = d.optString("code").takeIf(String::isNotBlank) ?: return@mapNotNull null
            Dividend(
                code = code,
                amount = d.num("amount"),
                assemblyDay = d.day("assembly"),
                paymentDay = d.day("payment"),
                lastCumDay = d.day("lastCum"),
                announcedDay = d.day("announced"),
                lastCumEstimated = d.optBoolean("estimated"),
                feedExDay = d.day("feedEx"),
                feedAmount = d.num("feedAmount"),
                check = runCatching { DividendCheck.valueOf(d.optString("check")) }.getOrDefault(DividendCheck.WAITING),
                headline = d.optString("headline"),
                url = d.optString("url"),
            )
        }
    }.orEmpty()
    val paid = json.optJSONObject("paid")?.let { all ->
        all.keys().asSequence().associateWith { ticker ->
            val list = all.optJSONArray(ticker) ?: JSONArray()
            (0 until list.length()).mapNotNull { i ->
                val row = list.optJSONArray(i) ?: return@mapNotNull null
                val day = runCatching { LocalDate.parse(row.optString(0)) }.getOrNull() ?: return@mapNotNull null
                val amount = row.optDouble(1).takeUnless(Double::isNaN) ?: return@mapNotNull null
                PaidDividend(day, amount)
            }
        }
    }.orEmpty()
    return DividendRecord(dividends, paid, json.optLong("newsAt"))
}
