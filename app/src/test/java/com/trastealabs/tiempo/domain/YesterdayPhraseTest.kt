package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YesterdayPhraseTest {
    private val now = TestData.at(2025, 6, 10, 10, 20)

    @Test fun `mas frio que ayer`() {
        val s = TestData.snapshot(now, currentC = 12.0, yesterdayTempC = 15.0)
        assertEquals("3 °C más frío que ayer a esta hora", yesterdayPhrase(s, now))
    }

    @Test fun `mas calido que ayer`() {
        val s = TestData.snapshot(now, currentC = 20.0, yesterdayTempC = 17.0)
        assertEquals("3 °C más cálido que ayer a esta hora", yesterdayPhrase(s, now))
    }

    @Test fun `menos de un grado es igual`() {
        val s = TestData.snapshot(now, currentC = 12.6, yesterdayTempC = 12.0)
        assertEquals("Igual que ayer a esta hora", yesterdayPhrase(s, now))
    }

    @Test fun `sin hora de ayer no hay frase`() {
        val s = TestData.snapshot(now, yesterdayTempC = null)
        assertNull(yesterdayPhrase(s, now))
    }
}
