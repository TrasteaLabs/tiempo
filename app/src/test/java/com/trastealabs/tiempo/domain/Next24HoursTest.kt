package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Next24HoursTest {
    /** Una hora cada 3600 s desde las 00:00 local de [from], durante [days] días. */
    private fun hourly(from: LocalDate, days: Int = 3): List<HourPoint> {
        val start = from.atStartOfDay(TestData.zone).toEpochSecond()
        val end = from.plusDays(days.toLong()).atStartOfDay(TestData.zone).toEpochSecond()
        return (start until end step 3600).map { HourPoint(it, 10.0, 0, 0.0, 0, true) }
    }

    private fun localHour(t: Long) = Instant.ofEpochSecond(t).atZone(TestData.zone).hour

    private fun assertConsecutive(hours: List<HourPoint>) {
        assertEquals(24, hours.size)
        hours.zipWithNext { a, b -> assertEquals(3600L, b.time - a.time) }
        assertEquals(24, hours.map { it.time }.toSet().size)
    }

    @Test fun `desde las 10 20 empieza en las 10 00 y da 24 horas`() {
        val now = TestData.at(2025, 6, 10, 10, 20)
        val hours = next24Hours(hourly(LocalDate.of(2025, 6, 9)), now)
        assertEquals(TestData.at(2025, 6, 10, 10, 0), hours.first().time)
        assertEquals(TestData.at(2025, 6, 11, 9, 0), hours.last().time)
        assertConsecutive(hours)
    }

    @Test fun `en punto empieza en esa misma hora`() {
        val now = TestData.at(2025, 6, 10, 10, 0)
        assertEquals(now, next24Hours(hourly(LocalDate.of(2025, 6, 9)), now).first().time)
    }

    @Test fun `el 26-oct-2025 la hora de las 02 se repite pero no hay instantes repetidos`() {
        val now = TestData.at(2025, 10, 25, 22, 30)
        val hours = next24Hours(hourly(LocalDate.of(2025, 10, 25)), now)
        assertConsecutive(hours)
        // 22 23 00 01 02 02 03 … : el reloj local repite las 02:00 al volver a la hora de invierno.
        assertEquals(listOf(22, 23, 0, 1, 2, 2, 3), hours.take(7).map { localHour(it.time) })
    }

    @Test fun `el 30-mar-2025 la hora de las 02 no existe y no se salta ningun instante`() {
        val now = TestData.at(2025, 3, 29, 23, 10)
        val hours = next24Hours(hourly(LocalDate.of(2025, 3, 29)), now)
        assertConsecutive(hours)
        // 23 00 01 03 04 … : de las 01:59 se pasa a las 03:00 en el reloj local.
        assertEquals(listOf(23, 0, 1, 3, 4), hours.take(5).map { localHour(it.time) })
    }

    @Test fun `a medianoche sigue sin saltos`() {
        val now = TestData.at(2025, 6, 10, 23, 59)
        val hours = next24Hours(hourly(LocalDate.of(2025, 6, 10)), now)
        assertEquals(TestData.at(2025, 6, 10, 23, 0), hours.first().time)
        assertConsecutive(hours)
    }

    @Test fun `si la prevision se acaba devuelve lo que queda`() {
        val day = LocalDate.of(2025, 6, 10)
        val now = TestData.at(2025, 6, 10, 20, 0)
        val hours = next24Hours(hourly(day, days = 1), now)
        assertEquals(4, hours.size)
        assertTrue(hours.all { it.time >= TestData.at(2025, 6, 10, 20, 0) })
    }
}
