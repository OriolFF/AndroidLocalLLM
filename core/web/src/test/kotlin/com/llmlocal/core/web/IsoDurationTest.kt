package com.llmlocal.core.web

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class IsoDurationTest {

    private fun check(vararg cases: Pair<String?, Int?>) {
        for ((input, expected) in cases) {
            assertWithMessage("toMinutes(%s)", input).that(IsoDuration.toMinutes(input)).isEqualTo(expected)
        }
    }

    @Test
    fun `null, blank and garbage return null`() = check(
        null to null,
        "" to null,
        "   " to null,
        "hello" to null,
        "PTxyz" to null,
        "overnight" to null,
    )

    @Test
    fun `iso hours minutes seconds`() = check(
        "PT1H30M" to 90,
        "PT2H" to 120,
        "PT45M" to 45,
        "PT0H20M0S" to 20,
        "PT90S" to 2,
        "PT1M30S" to 2,
        "PT1M20S" to 1,
    )

    @Test
    fun `iso days and weeks`() = check(
        "P0DT0H30M" to 30,
        "P1DT2H" to 26 * 60,
        "P1W" to 7 * 24 * 60,
    )

    @Test
    fun `iso is case insensitive and accepts decimals`() = check(
        "pt1h30m" to 90,
        "PT1.5H" to 90,
        "PT2,5H" to 150,
        "PT0.5M" to 1,
    )

    @Test
    fun `iso with no components or zero duration is unknown`() = check(
        "P" to null,
        "PT" to null,
        "P0D" to null,
        "PT0M" to null,
        "PT0S" to null,
        "PT0H0M0S" to null,
    )

    @Test
    fun `iso rejects years and months`() = check(
        "P1Y" to null,
        "P2M" to null,
    )

    @Test
    fun `free text hours and minutes`() = check(
        "1 hr 20 mins" to 80,
        "2 hours 30 minutes" to 150,
        "1.5 hours" to 90,
        "1 hour" to 60,
        "20 minutes" to 20,
        "20 mins" to 20,
        "20 min" to 20,
        "45m" to 45,
        "2h30" to 150,
        "1h 15m" to 75,
        "about 25 minutes, plus cooling" to 25,
        "Cook: 35 minutes" to 35,
        "90 seconds" to 2,
    )

    @Test
    fun `ranges resolve to the max end`() = check(
        "30-40 minutes" to 40,
        "30 – 40 minutes" to 40,
        "30 to 40 minutes" to 40,
        "1 to 2 hours" to 120,
        "1-2 hrs" to 120,
        "40 minutes to 1 hour" to 60,
        "1 hr 20 mins - 1 hr 40 mins" to 100,
        "2 or 3 hours" to 180,
    )

    @Test
    fun `zero free text is unknown`() = check(
        "0 minutes" to null,
        "0 hrs 0 mins" to null,
    )
}
