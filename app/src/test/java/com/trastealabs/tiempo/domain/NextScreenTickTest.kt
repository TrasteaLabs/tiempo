package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.TestData.at
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/** H8: el próximo instante en que la pantalla cambia sola. */
class NextScreenTickTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    /** Sol de 07:58 a 18:21; la luna sale a las 01:40 + 65 min por día y se pone a las 14:50 + 53 min por día. */
    private fun snapshot(): WeatherSnapshot {
        val base = TestData.snapshot(at(2026, 11, 3, 12, 0))
        val daily = base.daily.mapIndexed { i, d ->
            d.copy(
                sunrise = at(2026, 11, 3 + i, 7, 58), sunset = at(2026, 11, 3 + i, 18, 21),
                moonrise = at(2026, 11, 3 + i, 1, 40) + i * 3900L, moonset = at(2026, 11, 3 + i, 14, 50) + i * 3180L,
                moonPhase = 0.807 + i * 0.01,
            )
        }
        return base.copy(daily = daily)
    }

    @Test fun `el que llegue antes de amanecer, anochecer, luna y medianoche`() {
        val s = snapshot()
        assertEquals(at(2026, 11, 3, 14, 50), nextScreenTick(at(2026, 11, 3, 12, 0), madrid, s))   // puesta de la luna
        assertEquals(at(2026, 11, 3, 18, 21), nextScreenTick(at(2026, 11, 3, 14, 50), madrid, s))  // anochecer
        assertEquals(at(2026, 11, 4, 0, 0), nextScreenTick(at(2026, 11, 3, 18, 21), madrid, s))    // medianoche
        assertEquals(at(2026, 11, 4, 2, 45), nextScreenTick(at(2026, 11, 4, 0, 0), madrid, s))     // salida de la luna
        assertEquals(at(2026, 11, 4, 7, 58), nextScreenTick(at(2026, 11, 4, 2, 45), madrid, s))    // amanecer
    }

    @Test fun `siempre despues de now, aunque now caiga justo en un evento`() {
        val s = snapshot()
        assertEquals(at(2026, 11, 3, 18, 21), nextScreenTick(at(2026, 11, 3, 18, 21) - 1, madrid, s))
        assertEquals(at(2026, 11, 4, 0, 0), nextScreenTick(at(2026, 11, 3, 18, 21), madrid, s))
    }

    @Test fun `la medianoche del movil y la de la prevision cuentan las dos`() {
        val s = snapshot()
        // El móvil en Canarias: su medianoche es la 01:00 de Madrid, pero la de la previsión llega antes.
        assertEquals(at(2026, 11, 4, 0, 0), nextScreenTick(at(2026, 11, 3, 18, 30), ZoneId.of("Atlantic/Canary"), s))
        // El móvil en Atenas: su medianoche es a las 23:00 de Madrid.
        assertEquals(at(2026, 11, 3, 23, 0), nextScreenTick(at(2026, 11, 3, 18, 30), ZoneId.of("Europe/Athens"), s))
    }

    /** Aire por horas desde las 00:00 del 3-nov, [count] horas. */
    private fun withAir(count: Int): WeatherSnapshot {
        val start = at(2026, 11, 3, 0, 0)
        val hours = (0 until count).map { AirHour(start + it * 3600L, aqi = 30) }
        return snapshot().copy(air = AirQuality(TestData.place.position, start, hours))
    }

    @Test fun `con aire guardado late en la siguiente hora del aire si llega antes (H6 del 005)`() {
        val s = withAir(120)
        assertEquals(at(2026, 11, 3, 13, 0), nextScreenTick(at(2026, 11, 3, 12, 20), madrid, s))
        assertEquals(at(2026, 11, 3, 14, 0), nextScreenTick(at(2026, 11, 3, 13, 0), madrid, s))
        // la puesta de la luna (14:50) llega antes que la hora de las 15:00
        assertEquals(at(2026, 11, 3, 14, 50), nextScreenTick(at(2026, 11, 3, 14, 10), madrid, s))
    }

    @Test fun `con el aire acabado, como antes`() {
        // Aire solo hasta las 05:00: a las 12:00 manda la puesta de la luna
        assertEquals(at(2026, 11, 3, 14, 50), nextScreenTick(at(2026, 11, 3, 12, 0), madrid, withAir(6)))
    }

    @Test fun `sin snapshot, la medianoche del movil`() {
        assertEquals(at(2026, 11, 4, 0, 0), nextScreenTick(at(2026, 11, 3, 18, 30), madrid, null))
    }

    @Test fun `con datos de antes (sin luna ni sol), medianoche`() {
        val old = TestData.snapshot(at(2026, 11, 3, 12, 0))
        assertEquals(at(2026, 11, 4, 0, 0), nextScreenTick(at(2026, 11, 3, 12, 0), madrid, old))
    }
}
