package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.TestData.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * H7, con `now` y zona inventados (Europe/Madrid). El 3-nov la luna salió a las 01:40 y se puso a las 14:50;
 * el 4-nov sale a las 02:47 y se pone a las 15:43; el 5-nov, 03:52 y 16:30.
 */
class MoonInfoTest {
    private fun snapshot(): WeatherSnapshot {
        val base = TestData.snapshot(at(2026, 11, 3, 12, 0))
        val moon = mapOf(
            "2026-11-03" to Triple(at(2026, 11, 3, 1, 40), at(2026, 11, 3, 14, 50), 0.807),
            "2026-11-04" to Triple(at(2026, 11, 4, 2, 47), at(2026, 11, 4, 15, 43), 0.841),
            "2026-11-05" to Triple(at(2026, 11, 5, 3, 52), at(2026, 11, 5, 16, 30), 0.875),
        )
        val daily = base.daily.mapIndexed { i, d ->
            val (rise, set, phase) = moon[d.date] ?: Triple(null, null, 0.9 + i * 0.01)
            d.copy(
                sunrise = at(2026, 11, 3 + i, 7, 58) + i * 60,
                sunset = at(2026, 11, 3 + i, 18, 21) - i * 60,
                moonrise = rise, moonset = set, moonPhase = phase,
            )
        }
        val yesterday = DayPoint(
            "2026-11-02", 19.0, 10.0, 0, 0.0,
            sunrise = at(2026, 11, 2, 7, 57), sunset = at(2026, 11, 2, 18, 22),
            moonrise = at(2026, 11, 2, 0, 38), moonset = at(2026, 11, 2, 14, 0), moonPhase = 0.773,
        )
        return base.copy(daily = daily, yesterday = yesterday)
    }

    @Test fun `antes del anochecer no hay ficha de la luna`() {
        assertNull(moonInfo(snapshot(), at(2026, 11, 3, 18, 20)))
    }

    @Test fun `justo despues del anochecer sale con la proxima salida y puesta`() {
        val m = moonInfo(snapshot(), at(2026, 11, 3, 18, 21) + 30)!!
        assertEquals("Sale a las 02:47 · se pone a las 15:43", m.events)
        assertNull(m.fraction)
    }

    @Test fun `el ejemplo de la spec, a las 23, con la fase de la noche (la del dia de la salida)`() {
        val m = moonInfo(snapshot(), at(2026, 11, 3, 23, 0))!!
        assertEquals("Sale a las 02:47 · se pone a las 15:43", m.events)
        assertEquals(0.841, m.phase, 0.0)
        assertEquals("Menguante · 23 %", m.label)
    }

    @Test fun `pasada la medianoche dice lo mismo`() {
        val m = moonInfo(snapshot(), at(2026, 11, 4, 1, 0))!!
        assertEquals("Sale a las 02:47 · se pone a las 15:43", m.events)
        assertEquals(0.841, m.phase, 0.0)
        assertNull(m.fraction)
    }

    @Test fun `con la luna ya fuera primero la puesta y su fraccion del arco`() {
        val m = moonInfo(snapshot(), at(2026, 11, 4, 5, 0))!!
        assertEquals("Se pone a las 15:43 · sale a las 03:52", m.events)
        assertEquals(133f / 776f, m.fraction!!, 0.001f)
        assertEquals(0.841, m.phase, 0.0)
    }

    @Test fun `al amanecer vuelve la del sol`() {
        val s = snapshot()
        assertNull(moonInfo(s, s.daily[1].sunrise!!))
        assertNull(moonInfo(s, at(2026, 11, 4, 12, 0)))
        assertNotNull(moonInfo(s, s.daily[1].sunrise!! - 1))
    }

    @Test fun `la luna que salio ayer y sigue fuera`() {
        // Creciente: sale por la tarde y se pone de madrugada del día siguiente.
        val base = TestData.snapshot(at(2026, 11, 3, 12, 0))
        val daily = base.daily.mapIndexed { i, d ->
            d.copy(
                sunrise = at(2026, 11, 3 + i, 7, 58), sunset = at(2026, 11, 3 + i, 18, 21),
                moonrise = if (i == 0) at(2026, 11, 3, 23, 20) else null,
                moonset = if (i == 0) at(2026, 11, 3, 11, 0) else null,
                moonPhase = 0.30 + i * 0.03,
            )
        }
        val yesterday = DayPoint("2026-11-02", 19.0, 10.0, 0, 0.0, moonrise = at(2026, 11, 2, 22, 30), moonPhase = 0.27)
        val s = base.copy(daily = daily, yesterday = yesterday)

        val m = moonInfo(s, at(2026, 11, 3, 1, 0))!!
        assertEquals("Se pone a las 11:00 · sale a las 23:20", m.events)
        assertEquals(0.27, m.phase, 0.0)
        assertEquals("Cuarto creciente · 56 %", m.label)
        assertEquals(150f / 750f, m.fraction!!, 0.001f)
    }

    @Test fun `en noche o dia polar no sale ni sol ni luna`() {
        val s = snapshot()
        val polarNight = s.copy(daily = s.daily.mapIndexed { i, d -> if (i == 0) d.copy(daylightSeconds = 0.0) else d })
        assertNull(moonInfo(polarNight, at(2026, 11, 3, 23, 0)))
        val polarDay = s.copy(daily = s.daily.mapIndexed { i, d -> if (i == 0) d.copy(daylightSeconds = 86_400.0) else d })
        assertNull(moonInfo(polarDay, at(2026, 11, 3, 23, 0)))
        val noSunset = s.copy(daily = s.daily.mapIndexed { i, d -> if (i == 0) d.copy(sunset = null) else d })
        assertNull(moonInfo(noSunset, at(2026, 11, 3, 23, 0)))
    }

    @Test fun `con datos de antes (sin fase) no hay ficha de la luna`() {
        val s = snapshot()
        val old = s.copy(
            daily = s.daily.map { it.copy(moonrise = null, moonset = null, moonPhase = null) },
            yesterday = null,
        )
        assertNull(moonInfo(old, at(2026, 11, 3, 23, 0)))
    }
}
