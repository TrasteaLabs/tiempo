package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DaysFromTest {
    private val s = TestData.snapshot(TestData.at(2025, 6, 10, 10, 20))

    @Test fun `a las 23 59 la lista empieza hoy`() {
        val days = daysFrom(s, TestData.at(2025, 6, 10, 23, 59))
        assertEquals("2025-06-10", days.first().date)
        assertEquals(10, days.size)
    }

    @Test fun `a las 00 01 la lista empieza en el dia nuevo`() {
        val days = daysFrom(s, TestData.at(2025, 6, 11, 0, 1))
        assertEquals("2025-06-11", days.first().date)
        assertTrue(days.size <= 10)
    }

    @Test fun `nunca da mas de 10 dias`() {
        val extra = s.daily + DayPoint("2025-06-20", 20.0, 10.0, 0, 0.0)
        assertEquals(10, daysFrom(s.copy(daily = extra), TestData.at(2025, 6, 10, 9, 0)).size)
    }

    @Test fun `la medianoche se cuenta en la zona de la prevision, no en UTC`() {
        // 00:30 en Madrid (verano) son las 22:30 UTC del día anterior.
        val days = daysFrom(s, TestData.at(2025, 6, 11, 0, 30))
        assertEquals("2025-06-11", days.first().date)
    }
}
