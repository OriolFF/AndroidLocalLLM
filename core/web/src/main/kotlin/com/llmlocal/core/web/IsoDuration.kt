package com.llmlocal.core.web

/**
 * ISO-8601 duration → minutes, with a free-text fallback.
 *
 * Handles `PT1H30M`, `PT0H20M0S`, `P0DT0H30M` and, when the input is not
 * ISO, phrases such as "1 hr 20 mins". Returns `null` for `null`, blank or
 * unparseable input.
 *
 * Implementation + table test land in WS2.
 */
object IsoDuration {

    fun toMinutes(iso: String?): Int? = TODO("WS2")
}
