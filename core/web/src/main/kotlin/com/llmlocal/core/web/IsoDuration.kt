package com.llmlocal.core.web

import kotlin.math.roundToInt

/**
 * ISO-8601 duration → minutes, with a free-text fallback.
 *
 * Handles `PT1H30M`, `PT0H20M0S`, `P0DT0H30M`, `PT1.5H` and, when the input
 * is not ISO, phrases such as `"1 hr 20 mins"`, `"45 minutes"` or
 * `"2h30"`. Ranges (`"30-40 minutes"`, `"1 to 2 hours"`,
 * `"1 hr 20 mins – 1 hr 40 mins"`) resolve to the **max** end so callers
 * don't silently truncate a long cook time.
 *
 * Returns `null` for `null`, blank, unparseable or **zero** input: sites
 * routinely emit `PT0M` / `PT0S` for a time they never filled in, and a
 * "0 min" badge is noise, so a zero duration is reported as unknown.
 */
object IsoDuration {

    /** Convert a duration string to total minutes, or `null` if unknown. */
    fun toMinutes(iso: String?): Int? {
        if (iso.isNullOrBlank()) return null
        val raw = iso.trim()
        val minutes = iso8601Minutes(raw)
            ?: raw.takeUnless { ISO_LOOKALIKE.matches(it) }?.let(::freeTextMinutes)
            ?: return null
        return minutes.takeIf { it > 0 }
    }

    /** `P…` with only designator characters: a malformed ISO string, not free text (`P2M` is not "2 minutes"). */
    private val ISO_LOOKALIKE = Regex("""^P[0-9A-Z.,]*$""", RegexOption.IGNORE_CASE)

    // ----- ISO-8601 ----------------------------------------------------------

    /**
     * `P[nW][nD][T[nH][nM][nS]]`, every component optional, decimals
     * allowed (`PT1.5H`), case-insensitive. Years/months are not durations
     * a recipe uses and are rejected.
     */
    private val ISO = Regex(
        """^P(?:(\d+(?:[.,]\d+)?)W)?(?:(\d+(?:[.,]\d+)?)D)?(?:T(?:(\d+(?:[.,]\d+)?)H)?(?:(\d+(?:[.,]\d+)?)M)?(?:(\d+(?:[.,]\d+)?)S)?)?$""",
        RegexOption.IGNORE_CASE,
    )

    private fun iso8601Minutes(text: String): Int? {
        if (!text.startsWith("P", ignoreCase = true)) return null
        val m = ISO.matchEntire(text) ?: return null
        val values = m.groupValues.drop(1)
        if (values.all { it.isEmpty() }) return null // bare "P" / "PT"
        val (weeks, days, hours, minutes, seconds) = values.map { it.toDecimal() }
        val total = weeks * 7 * 24 * 60 + days * 24 * 60 + hours * 60 + minutes + seconds / 60.0
        return total.roundToInt()
    }

    private fun String.toDecimal(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0

    // ----- free text ---------------------------------------------------------

    private val HOURS = Regex("""(\d+(?:[.,]\d+)?)\s*(?:hours?|hrs?|h)\b""", RegexOption.IGNORE_CASE)
    private val MINUTES = Regex("""(\d+(?:[.,]\d+)?)\s*(?:minutes?|mins?|m)\b""", RegexOption.IGNORE_CASE)
    private val SECONDS = Regex("""(\d+(?:[.,]\d+)?)\s*(?:seconds?|secs?|s)\b""", RegexOption.IGNORE_CASE)

    /** `2h30`, `1h 30` — hours followed by a bare minute count. */
    private val COMPACT_HOURS_MINUTES = Regex("""(\d+)\s*h\s*(\d{1,2})\b(?!\s*(?:h|hours?|hrs?))""", RegexOption.IGNORE_CASE)

    /** Range separators: `-`, `–`, `—`, `to`, `or`. */
    private val RANGE_SEPARATOR = Regex("""\s*(?:-|–|—|\bto\b|\bor\b)\s*""", RegexOption.IGNORE_CASE)

    /**
     * Split on range separators, evaluate each side, take the max. A side
     * that carries only a bare number ("30" in "30-40 minutes") inherits
     * the unit of the first side that has one.
     */
    private fun freeTextMinutes(text: String): Int? {
        val parts = text.split(RANGE_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val withUnits = parts.map { phraseMinutes(it) }
        val unit = parts.firstNotNullOfOrNull { bareNumberUnit(it) }
        val resolved: List<Int?> = parts.indices.map { i ->
            withUnits[i] ?: parts[i].toBareNumber()?.let { n -> unit?.let { (n * it).roundToInt() } }
        }
        return resolved.filterNotNull().maxOrNull()
    }

    /** Minutes for a single (non-range) phrase, or `null` if it has no recognised unit. */
    private fun phraseMinutes(text: String): Int? {
        COMPACT_HOURS_MINUTES.find(text)?.let { m ->
            if (!MINUTES.containsMatchIn(text)) {
                return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
            }
        }
        val hours = HOURS.findAll(text).sumOf { it.groupValues[1].toDecimal() }
        val minutes = MINUTES.findAll(text).sumOf { it.groupValues[1].toDecimal() }
        val seconds = SECONDS.findAll(text).sumOf { it.groupValues[1].toDecimal() }
        if (hours == 0.0 && minutes == 0.0 && seconds == 0.0 &&
            !HOURS.containsMatchIn(text) && !MINUTES.containsMatchIn(text) && !SECONDS.containsMatchIn(text)
        ) {
            return null
        }
        return (hours * 60 + minutes + seconds / 60.0).roundToInt()
    }

    /** Minutes-per-unit of the trailing unit word in [text], if any. */
    private fun bareNumberUnit(text: String): Double? = when {
        HOURS.containsMatchIn(text) -> 60.0
        MINUTES.containsMatchIn(text) -> 1.0
        SECONDS.containsMatchIn(text) -> 1.0 / 60.0
        else -> null
    }

    private fun String.toBareNumber(): Double? =
        Regex("""^\d+(?:[.,]\d+)?$""").matchEntire(trim())?.value?.toDecimal()
}
