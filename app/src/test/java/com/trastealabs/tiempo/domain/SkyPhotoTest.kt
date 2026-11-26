package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.ui.scene.PHOTO_LOOKS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SkyPhotoTest {
    private val zone = TestData.zone

    // Un día de noviembre: sale a las 08:16 y se pone a las 18:14 (como en la maqueta).
    private val rise = TestData.at(2026, 11, 7, 8, 16)
    private val set = TestData.at(2026, 11, 7, 18, 14)
    private val day = DayPoint("2026-11-07", 20.0, 9.0, 30, 0.0, sunrise = rise, sunset = set)
    private fun moment(h: Int, m: Int, isDay: Boolean = true) = momentOf(TestData.at(2026, 11, 7, h, m), zone, day, isDay)

    @Test fun `todas las combinaciones dan una foto con su webp y su aspecto (H3)`() {
        val seen = mutableSetOf<SkyPhoto>()
        for (sky in Sky.entries) for (m in DayMoment.entries) for (c in -5..44) {
            val photo = skyPhotoOf(sky, m, c.toDouble())
            seen += photo
            assertTrue("$sky $m $c: falta ${photo.file}.webp", File("src/main/res/drawable-nodpi/${photo.file}.webp").isFile)
            assertNotNull("$sky $m $c: sin PhotoLook", PHOTO_LOOKS[photo])
        }
        assertEquals(SkyPhoto.entries.toSet(), seen)
        assertEquals(14, SkyPhoto.entries.size)
        assertTrue(File("src/main/res/drawable-nodpi/sky_cloud_layer.png").isFile)
        assertEquals(SkyPhoto.entries.toSet(), PHOTO_LOOKS.keys)
    }

    @Test fun `la tabla de diseno fila a fila`() {
        val d = DayMoment.DAY
        val g = DayMoment.GOLDEN
        val n = DayMoment.NIGHT
        // Despejado, por temperatura.
        assertEquals(SkyPhoto.CLEAR_COOL, skyPhotoOf(Sky.CLEAR, d, 14.0))
        assertEquals(SkyPhoto.CLEAR_MILD, skyPhotoOf(Sky.CLEAR, d, 24.0))
        assertEquals(SkyPhoto.CLEAR_HOT, skyPhotoOf(Sky.CLEAR, d, 34.0))
        assertEquals(SkyPhoto.SCORCHING, skyPhotoOf(Sky.CLEAR, d, 41.0))
        for (c in listOf(14.0, 24.0, 34.0, 41.0)) {
            assertEquals(SkyPhoto.GOLDEN_CLEAR, skyPhotoOf(Sky.CLEAR, g, c))
            assertEquals(SkyPhoto.NIGHT_CLEAR, skyPhotoOf(Sky.CLEAR, n, c))
        }
        // Nubes y claros, cubierto.
        for (sky in listOf(Sky.PARTLY_CLOUDY, Sky.CLOUDY)) {
            assertEquals(SkyPhoto.CLOUDS, skyPhotoOf(sky, d, 16.0))
            assertEquals(SkyPhoto.GOLDEN_CLOUDS, skyPhotoOf(sky, g, 16.0))
            assertEquals(SkyPhoto.NIGHT_CLOUDS, skyPhotoOf(sky, n, 16.0))
        }
        // Niebla.
        assertEquals(SkyPhoto.FOG, skyPhotoOf(Sky.FOG, d, 8.0))
        assertEquals(SkyPhoto.FOG, skyPhotoOf(Sky.FOG, g, 8.0))
        assertEquals(SkyPhoto.NIGHT_CLOUDS, skyPhotoOf(Sky.FOG, n, 8.0))
        // Llovizna y lluvia.
        for (sky in listOf(Sky.DRIZZLE, Sky.RAIN)) {
            assertEquals(SkyPhoto.RAIN, skyPhotoOf(sky, d, 13.0))
            assertEquals(SkyPhoto.RAIN, skyPhotoOf(sky, g, 13.0))
            assertEquals(SkyPhoto.NIGHT_RAIN, skyPhotoOf(sky, n, 13.0))
        }
        // Tormenta.
        assertEquals(SkyPhoto.STORM, skyPhotoOf(Sky.STORM, d, 21.0))
        assertEquals(SkyPhoto.STORM, skyPhotoOf(Sky.STORM, g, 21.0))
        assertEquals(SkyPhoto.NIGHT_RAIN, skyPhotoOf(Sky.STORM, n, 21.0))
        // Nieve.
        assertEquals(SkyPhoto.SNOW, skyPhotoOf(Sky.SNOW, d, 0.0))
        assertEquals(SkyPhoto.SNOW, skyPhotoOf(Sky.SNOW, g, 0.0))
        assertEquals(SkyPhoto.NIGHT_CLOUDS, skyPhotoOf(Sky.SNOW, n, 0.0))
    }

    @Test fun `cortes de temperatura con la cifra que se ve`() {
        val d = DayMoment.DAY
        assertEquals(SkyPhoto.CLEAR_COOL, skyPhotoOf(Sky.CLEAR, d, 19.4))
        assertEquals(SkyPhoto.CLEAR_MILD, skyPhotoOf(Sky.CLEAR, d, 19.5)) // se ve «20°»
        assertEquals(SkyPhoto.CLEAR_MILD, skyPhotoOf(Sky.CLEAR, d, 20.0))
        assertEquals(SkyPhoto.CLEAR_MILD, skyPhotoOf(Sky.CLEAR, d, 29.4))
        assertEquals(SkyPhoto.CLEAR_HOT, skyPhotoOf(Sky.CLEAR, d, 29.5))
        assertEquals(SkyPhoto.CLEAR_HOT, skyPhotoOf(Sky.CLEAR, d, 30.0))
        assertEquals(SkyPhoto.CLEAR_HOT, skyPhotoOf(Sky.CLEAR, d, 37.4))
        assertEquals(SkyPhoto.SCORCHING, skyPhotoOf(Sky.CLEAR, d, 37.5))
        assertEquals(SkyPhoto.SCORCHING, skyPhotoOf(Sky.CLEAR, d, 38.0))
        assertEquals(SkyPhoto.CLEAR_COOL, skyPhotoOf(Sky.CLEAR, d, -5.0))
        assertEquals(SkyPhoto.SCORCHING, skyPhotoOf(Sky.CLEAR, d, 44.0))
    }

    @Test fun `limites del momento alrededor de la salida`() {
        assertEquals(DayMoment.NIGHT, moment(7, 45)) // 31 min antes
        assertEquals(DayMoment.GOLDEN, moment(7, 46)) // 30 min antes
        assertEquals(DayMoment.GOLDEN, moment(8, 16))
        assertEquals(DayMoment.GOLDEN, moment(9, 0)) // 44 min después
        assertEquals(DayMoment.DAY, moment(9, 1)) // 45 min después
    }

    @Test fun `limites del momento alrededor de la puesta`() {
        assertEquals(DayMoment.DAY, moment(17, 28)) // 46 min antes
        assertEquals(DayMoment.GOLDEN, moment(17, 29)) // 45 min antes
        assertEquals(DayMoment.GOLDEN, moment(18, 14))
        assertEquals(DayMoment.GOLDEN, moment(18, 43)) // 29 min después
        assertEquals(DayMoment.NIGHT, moment(18, 44)) // 30 min después
    }

    @Test fun `medianoche es de noche y no depende de isDay`() {
        assertEquals(DayMoment.NIGHT, moment(0, 0, isDay = true))
        assertEquals(DayMoment.NIGHT, moment(23, 59, isDay = true))
        assertEquals(DayMoment.DAY, moment(13, 0, isDay = false))
    }

    @Test fun `sin salida ni puesta manda isDay`() {
        val polar = DayPoint("2026-11-07", 1.0, -4.0, 0, 0.0)
        val noon = TestData.at(2026, 11, 7, 12, 0)
        assertEquals(DayMoment.DAY, momentOf(noon, zone, polar, isDay = true))
        assertEquals(DayMoment.NIGHT, momentOf(noon, zone, polar, isDay = false))
        assertEquals(DayMoment.NIGHT, momentOf(noon, zone, null, isDay = false))
        assertEquals(DayMoment.DAY, momentOf(noon, zone, day.copy(sunset = null), isDay = true))
    }

    @Test fun `skyPhotoNow usa el tiempo de ahora, el momento y la temperatura`() {
        val noon = TestData.at(2026, 11, 7, 13, 0)
        val base = TestData.snapshot(noon)
        val today = base.daily.map { if (it.date == "2026-11-07") it.copy(sunrise = rise, sunset = set) else it }
        val snap = base.copy(daily = today, current = base.current.copy(weatherCode = 0, temperatureC = 33.0))
        assertEquals(SkyPhoto.CLEAR_HOT, skyPhotoNow(snap, noon))
        assertEquals(SkyPhoto.GOLDEN_CLEAR, skyPhotoNow(snap, TestData.at(2026, 11, 7, 18, 0)))
        assertEquals(SkyPhoto.NIGHT_CLEAR, skyPhotoNow(snap, TestData.at(2026, 11, 7, 22, 0)))
        assertEquals(SkyPhoto.NIGHT_RAIN, skyPhotoNow(snap.copy(current = snap.current.copy(weatherCode = 61)), TestData.at(2026, 11, 7, 22, 0)))
    }

    @Test fun `dayPhoto va siempre de dia con el cielo y la maxima del dia`() {
        val night = TestData.at(2026, 11, 7, 23, 0)
        val snap = TestData.snapshot(night) // horas con código 3 (cubierto), máximas de 18 a 27
        for (d in snap.daily) assertEquals(d.date, SkyPhoto.CLOUDS, dayPhoto(snap, d.date, night))
        val sunny = snap.copy(hourly = snap.hourly.map { it.copy(weatherCode = 0) })
        assertEquals(SkyPhoto.CLEAR_COOL, dayPhoto(sunny, "2026-11-08", night)) // 19°
        assertEquals(SkyPhoto.CLEAR_MILD, dayPhoto(sunny, "2026-11-09", night)) // 20°
        val rainy = snap.copy(hourly = snap.hourly.map { it.copy(weatherCode = 61) })
        assertEquals(SkyPhoto.RAIN, dayPhoto(rainy, "2026-11-10", night))
    }

    @Test fun `cargando sin datos van las nubes`() {
        assertEquals(SkyPhoto.CLOUDS, LOADING_PHOTO)
    }

    @Test fun `effectsOf como la maqueta`() {
        val d = DayMoment.DAY
        val g = DayMoment.GOLDEN
        val n = DayMoment.NIGHT
        assertEquals(Flare.FULL, effectsOf(Sky.CLEAR, d).flare)
        assertEquals(Flare.WARM, effectsOf(Sky.CLEAR, g).flare)
        effectsOf(Sky.CLOUDY, d).let { assertEquals(Flare.GLOW, it.flare); assertTrue(it.cloudPan) }
        assertEquals(Flare.WARM_GLOW, effectsOf(Sky.PARTLY_CLOUDY, g).flare)
        effectsOf(Sky.FOG, d).let { assertEquals(Flare.FOG, it.flare); assertTrue(it.fog && it.cloudPan) }
        effectsOf(Sky.RAIN, d).let { assertEquals(1f, it.rain); assertEquals(false, it.stormFlash); assertEquals(Flare.NONE, it.flare) }
        assertEquals(1f, effectsOf(Sky.DRIZZLE, g).rain)
        effectsOf(Sky.STORM, d).let { assertEquals(1.4f, it.rain); assertTrue(it.stormFlash) }
        effectsOf(Sky.SNOW, d).let { assertEquals(Flare.GLOW, it.flare); assertTrue(it.snow && it.cloudPan) }
        // Noche.
        effectsOf(Sky.CLEAR, n).let { assertEquals(70, it.stars); assertEquals(MoonShow.CLEAR, it.moon); assertEquals(Flare.NONE, it.flare) }
        effectsOf(Sky.CLOUDY, n).let {
            assertEquals(14, it.stars)
            assertEquals(MoonShow.VEILED, it.moon)
            assertEquals(0.7f, it.moon.alpha)
            assertTrue(it.cloudPan)
        }
        effectsOf(Sky.FOG, n).let { assertTrue(it.fog); assertEquals(MoonShow.VEILED, it.moon) }
        effectsOf(Sky.SNOW, n).let { assertTrue(it.snow); assertEquals(14, it.stars) }
        effectsOf(Sky.RAIN, n).let { assertEquals(1f, it.rain); assertEquals(MoonShow.RAIN, it.moon); assertEquals(0, it.stars) }
        effectsOf(Sky.STORM, n).let { assertEquals(1.4f, it.rain); assertTrue(it.stormFlash); assertEquals(0.4f, it.moon.alpha) }
        // De día nunca hay luna ni estrellas.
        for (sky in Sky.entries) for (m in listOf(d, g)) {
            assertEquals(MoonShow.NONE, effectsOf(sky, m).moon)
            assertEquals(0, effectsOf(sky, m).stars)
        }
    }
}
