package com.ikverse.egxanalyzer.model

/**
 * The EGX 33 Shariah index, as the exchange's September 2026 review left it.
 *
 * Held here rather than fetched, because nothing the app talks to carries index membership. The
 * catalog endpoint answers with `symbol`, `name` and `sector` and nothing else; the exchange
 * publishes the constituents on a page behind a bot challenge no plain HTTP client can answer;
 * and the one machine-readable third-party list was still missing this review's addition two
 * days after it took effect. So the exchange's own announcement is the source, transcribed.
 *
 * 34 symbols, 33 companies: Faisal Islamic Bank is listed twice, in pounds as `FAIT` and in
 * dollars as `FAITA`, and a channel can name either.
 *
 * The index is reviewed every March and September, so this list goes stale twice a year and is
 * corrected by editing it. `ICFC` is deliberately here although the catalog's 223 symbols do
 * not carry it - membership is a fact about the company, not about whether a price feed
 * reaches it.
 *
 * Internal rather than private so the test can count it. A transcribed list has no source to
 * be checked against, which leaves the count as the only thing that catches a symbol dropped
 * or repeated while editing - and a repeat is invisible from outside, because a set eats it.
 */
val egx33 = setOf(
    "ACGC", "ADIB", "AMOC", "ARCC", "ATQA", "CLHO", "EFID", "EFIH", "EGAL", "EGAS",
    "ETEL", "ETRS", "FAIT", "FAITA", "GOUR", "ICFC", "IFAP", "ISPH", "JUFO", "LCSW",
    "MASR", "MCQE", "MPCO", "MTIE", "OCDI", "ORAS", "ORHD", "ORWE", "PHDC", "RACC",
    "RMDA", "SAUD", "SKPC", "TMGH",
)

/**
 * Whether the exchange counts this stock among the Shariah-compliant thirty-three.
 *
 * Cleaned the way [find] cleans, so it answers for a ticker in any form a card carries it -
 * `comi`, `COMI`, or the `COMI.CA` the price feed uses.
 */
fun isEgx33(ticker: String): Boolean =
    ticker.trim().uppercase().removeSuffix(".CA") in egx33
