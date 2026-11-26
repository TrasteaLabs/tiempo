package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.TestData.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SunInfoTest {
    /** Un día con salida y puesta en hora local de [TestData.zone]. */
    private fun day(y: Int, m: Int, d: Int, rise: Pair<Int, Int>?, set: Pair<Int, Int>?, daylight: Double? = null) =
        DayPoint(
            date = "%04d-%02d-%02d".format(y, m, d),
            maxC = 20.0, minC = 10.0, precipProbabilityMax = 0, precipSumMm = 0.0,
            sunrise = rise?.let { at(y, m, d, it.first, it.second) },
            sunset = set?.let { at(y, m, d, it.first, it.second) },
            daylightSeconds = daylight,
        )

    private fun snapshot(now: Long, daily: List<DayPoint>, yesterday: DayPoint? = null, fetchedAt: Long = now) =
        TestData.snapshot(now, fetchedAt = fetchedAt).copy(daily = daily, yesterday = yesterday)

    private val now = at(2025, 11, 4, 15, 2)
    private val today = day(2025, 11, 4, 7 to 58, 18 to 21, daylight = 37380.0)          // 10 h 23 min
    private fun yesterday(daylight: Double) = day(2025, 11, 3, 7 to 57, 18 to 22, daylight)

    @Test fun `salida y puesta, horas de luz y diferencia con ayer (H12)`() {
        val info = sunInfo(snapshot(now, listOf(today), yesterday(37500.0)), now)!!
        assertEquals("07:58 – 18:21", info.range)
        assertEquals("10 h 23 min de luz · 2 min menos que ayer", info.daylight)
    }

    @Test fun `un minuto mas que ayer`() {
        assertEquals(
            "10 h 23 min de luz · 1 min más que ayer",
            sunInfo(snapshot(now, listOf(today), yesterday(37320.0)), now)!!.daylight,
        )
    }

    @Test fun `sin diferencia, igual que ayer`() {
        assertEquals(
            "10 h 23 min de luz · igual que ayer",
            sunInfo(snapshot(now, listOf(today), yesterday(37390.0)), now)!!.daylight,
        )
    }

    @Test fun `sin ayer, solo las horas de luz`() {
        assertEquals("10 h 23 min de luz", sunInfo(snapshot(now, listOf(today)), now)!!.daylight)
    }

    @Test fun `ayer tambien puede venir en la lista de dias`() {
        assertEquals(
            "10 h 23 min de luz · 2 min menos que ayer",
            sunInfo(snapshot(now, listOf(yesterday(37500.0), today)), now)!!.daylight,
        )
    }

    @Test fun `el sol va a mitad del arco a mediodia y no esta de noche`() {
        val day = day(2025, 11, 4, 8 to 0, 18 to 0, daylight = 36000.0)
        fun fraction(fetchedAt: Long) = sunInfo(snapshot(now, listOf(day), fetchedAt = fetchedAt), now)!!.sunFraction
        assertEquals(0.5f, fraction(at(2025, 11, 4, 13, 0))!!, 0.001f)
        assertEquals(0f, fraction(at(2025, 11, 4, 8, 0))!!, 0.001f)
        assertNull(fraction(at(2025, 11, 4, 7, 0)))
        assertNull(fraction(at(2025, 11, 4, 20, 0)))
    }

    @Test fun `el dia del cambio de hora las horas salen en hora de invierno y la duracion no se descuadra`() {
        // 26-oct-2025: a las 03:00 vuelven a ser las 02:00. Sin daylightSeconds, la duración sale de los instantes.
        val saturday = day(2025, 10, 25, 8 to 13, 19 to 32)    // CEST: 06:13–17:32 UTC, 11 h 19 min
        val sunday = day(2025, 10, 26, 7 to 15, 18 to 30)      // CET:  06:15–17:30 UTC, 11 h 15 min
        val now = at(2025, 10, 26, 12, 0)
        val info = sunInfo(snapshot(now, listOf(saturday, sunday)), now)!!
        assertEquals("07:15 – 18:30", info.range)
        assertEquals("11 h 15 min de luz · 4 min menos que ayer", info.daylight)
    }

    @Test fun `noche polar y dia polar no tienen ficha`() {
        assertNull(sunInfo(snapshot(now, listOf(today.copy(daylightSeconds = 0.0))), now))
        assertNull(sunInfo(snapshot(now, listOf(today.copy(daylightSeconds = 86400.0))), now))
    }

    @Test fun `sin salida o puesta no hay ficha`() {
        assertNull(sunInfo(snapshot(now, listOf(today.copy(sunrise = null))), now))
        assertNull(sunInfo(snapshot(now, listOf(today.copy(sunset = null))), now))
    }

    @Test fun `un snapshot de antes de actualizar no tiene ficha`() {
        assertNull(sunInfo(snapshot(now, listOf(day(2025, 11, 4, null, null))), now))
    }

    @Test fun `a las 00_01 usa el dia nuevo`() {
        val monday = day(2025, 11, 3, 7 to 57, 18 to 22, daylight = 37500.0)
        val tuesday = day(2025, 11, 4, 7 to 58, 18 to 21, daylight = 37380.0)
        val late = at(2025, 11, 3, 23, 59)
        val early = at(2025, 11, 4, 0, 1)
        assertEquals("07:57 – 18:22", sunInfo(snapshot(late, listOf(monday, tuesday)), late)!!.range)
        val info = sunInfo(snapshot(early, listOf(monday, tuesday), fetchedAt = late), early)
        assertNotNull(info)
        assertEquals("07:58 – 18:21", info!!.range)
        assertEquals("10 h 23 min de luz · 2 min menos que ayer", info.daylight)
        // El dato es de anoche: de noche, sin sol en el arco.
        assertNull(info.sunFraction)
    }
}
