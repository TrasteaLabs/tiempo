package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneOfTest {
    private fun day(code: Int, c: Double = 15.0) = sceneOf(CurrentConditions(c, c, code, isDay = true))
    private fun night(code: Int, c: Double = 15.0) = sceneOf(CurrentConditions(c, c, code, isDay = false))

    @Test fun `con sol el color sale de la temperatura que se ve`() {
        assertEquals(Scene.SUN_COOL, day(0, 19.4))
        assertEquals(Scene.SUN_MILD, day(0, 19.6)) // se ve «20°»
        assertEquals(Scene.SUN_MILD, day(0, 29.0))
        assertEquals(Scene.SUN_HOT, day(0, 30.0))
        assertEquals(Scene.SUN_HOT, day(0, 37.0))
        assertEquals(Scene.SUN_SCORCHING, day(0, 38.0))
        assertEquals(Scene.SUN_COOL, day(1, -3.0)) // casi despejado también es sol
    }

    @Test fun `el resto del cielo de dia`() {
        assertEquals(Scene.CLOUDS, day(2))
        assertEquals(Scene.CLOUDS, day(3))
        assertEquals(Scene.FOG, day(45))
        assertEquals(Scene.RAIN, day(51))
        assertEquals(Scene.RAIN, day(61))
        assertEquals(Scene.STORM, day(95))
        assertEquals(Scene.SNOW, day(71))
    }

    @Test fun `de noche solo hay dos escenas`() {
        assertEquals(Scene.NIGHT_RAIN, night(61))
        assertEquals(Scene.NIGHT_RAIN, night(95))
        assertEquals(Scene.NIGHT_RAIN, night(71))
        assertEquals(Scene.NIGHT_CLEAR, night(0, 40.0))
        assertEquals(Scene.NIGHT_CLEAR, night(3))
        assertEquals(Scene.NIGHT_CLEAR, night(45))
    }

    @Test fun `las once escenas salen con algun dato`() {
        val seen = buildSet {
            listOf(0, 1, 2, 3, 45, 51, 61, 71, 95).forEach { code ->
                listOf(-3.0, 10.0, 24.0, 33.0, 43.0).forEach { c ->
                    add(day(code, c))
                    add(night(code, c))
                }
            }
        }
        assertEquals(Scene.entries.toSet(), seen)
        assertEquals(11, Scene.entries.size)
    }

    @Test fun `las escenas claras son sol templado nubes niebla y nieve`() {
        assertEquals(
            setOf(Scene.SUN_MILD, Scene.CLOUDS, Scene.FOG, Scene.SNOW),
            Scene.entries.filter { it.isLight }.toSet(),
        )
    }

    @Test fun `shownDegrees redondea como la cifra que se ve`() {
        assertEquals(20, shownDegrees(19.6))
        assertEquals(19, shownDegrees(19.4))
        assertEquals(-3, shownDegrees(-3.4))
    }

    // ---------- 004 · escena de cada día ----------

    @Test fun `sceneOf con cielo y temperatura da lo mismo que con las condiciones`() {
        assertEquals(Scene.SUN_MILD, sceneOf(Sky.CLEAR, isDay = true, shownC = 19.6))
        assertEquals(Scene.SUN_COOL, sceneOf(Sky.CLEAR, isDay = true, shownC = 19.4))
        assertEquals(Scene.NIGHT_RAIN, sceneOf(Sky.RAIN, isDay = false, shownC = 10.0))
        assertEquals(Scene.CLOUDS, sceneOf(Sky.PARTLY_CLOUDY, isDay = true, shownC = 30.0))
    }

    private val now = TestData.at(2025, 6, 10, 23, 0)

    /** Hoy de noche y despejado; el 12, lluvia de 06 a 17; el 13, sol con 31° de máxima. */
    private val snapshot = run {
        val base = TestData.snapshot(now)
        val zone = TestData.zone
        val hourly = base.hourly.map {
            val local = java.time.Instant.ofEpochSecond(it.time).atZone(zone)
            when (local.toLocalDate().toString()) {
                "2025-06-12" -> it.copy(weatherCode = 61)
                "2025-06-13" -> it.copy(weatherCode = 0)
                else -> it
            }
        }
        val daily = base.daily.map { if (it.date == "2025-06-13") it.copy(maxC = 31.0) else it }
        base.copy(current = base.current.copy(weatherCode = 0, isDay = false), hourly = hourly, daily = daily)
    }

    @Test fun `hoy lleva la escena de ahora, tambien de noche`() {
        assertEquals(Scene.NIGHT_CLEAR, dayScene(snapshot, "2025-06-10", now))
    }

    @Test fun `los demas dias salen de su cielo y su maxima, siempre de dia`() {
        assertEquals(Scene.RAIN, dayScene(snapshot, "2025-06-12", now))
        assertEquals(Scene.SUN_HOT, dayScene(snapshot, "2025-06-13", now))
        assertEquals(Scene.CLOUDS, dayScene(snapshot, "2025-06-11", now))
    }

    @Test fun `pasada la medianoche hoy es el dia nuevo`() {
        val later = TestData.at(2025, 6, 11, 0, 30)
        assertEquals(Scene.NIGHT_CLEAR, dayScene(snapshot, "2025-06-11", later))
    }

    @Test fun `la columna de ahora ensena la temperatura y el cielo actuales si el dato es de esa hora`() {
        val hours = next24Hours(snapshot.hourly, now)
        val shown = withCurrentAt(hours, 0, snapshot.copy(fetchedAt = now))
        assertEquals(12.0, shown[0].temperatureC, 0.0)
        assertEquals(0, shown[0].weatherCode)
        assertEquals(hours.drop(1), shown.drop(1))
        // Con datos de otra hora, o sin columna, la previsión horaria.
        assertEquals(hours, withCurrentAt(hours, 0, snapshot.copy(fetchedAt = now - 7200)))
        assertEquals(hours, withCurrentAt(hours, null, snapshot))
        val at3 = withCurrentAt(hours, 3, snapshot.copy(fetchedAt = hours[3].time + 60))
        assertEquals(12.0, at3[3].temperatureC, 0.0)
        assertEquals(10.0, at3[0].temperatureC, 0.0)
    }
}
