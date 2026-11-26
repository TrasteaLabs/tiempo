package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayTest {
    private val s = TestData.snapshot(TestData.at(2025, 6, 10, 10, 20))

    @Test fun `a las 23 59 sigue siendo hoy`() {
        assertEquals("2025-06-10", today(s, TestData.at(2025, 6, 10, 23, 59))?.date)
    }

    @Test fun `a las 00 01 es el dia siguiente`() {
        assertEquals("2025-06-11", today(s, TestData.at(2025, 6, 11, 0, 1))?.date)
    }

    @Test fun `un dia fuera de la prevision no devuelve nada`() {
        assertNull(today(s, TestData.at(2025, 7, 1, 12)))
    }
}
