package com.trastealabs.tiempo.ui.scene

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.domain.DayMoment
import com.trastealabs.tiempo.domain.Sky
import com.trastealabs.tiempo.domain.SkyEffects
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.domain.effectsOf
import com.trastealabs.tiempo.ui.glass.GlassTone
import com.trastealabs.tiempo.ui.glass.forPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** La foto del cielo detrás de todo (009, P1): quieta, con la luna de noche y con fundido al cambiar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneBackdropTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photos = PhotoCache(context)

    // 120 × 266 dp a 1 px/dp: pequeño, para que los tests vayan rápido.
    private val w = 120
    private val h = 266

    private fun show(
        photo: () -> SkyPhoto,
        effects: SkyEffects = SkyEffects.NONE,
        clock: SkyClock? = null,
        moon: MoonLook? = null,
        light: Boolean = false,
        effectsNow: () -> SkyEffects = { effects },
        onBackdrop: (Backdrop?, GlassTone) -> Unit = { _, _ -> },
    ) = rule.setContent {
        androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(1f)) {
            Box(Modifier.size(w.dp, h.dp)) {
                SceneBackdrop(photo(), effectsNow(), photos, clock = clock, moon = moon, light = light) {
                    onBackdrop(LocalBackdrop.current, LocalGlassTone.current)
                }
            }
        }
    }

    /** Lo que pinta el fondo para el cristal, en un bitmap del tamaño de la escena. */
    private fun paint(backdrop: Backdrop): ImageBitmap {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
            backdrop.draw(this)
        }
        return image
    }

    private fun close(a: Int, b: Int) = abs(a - b) <= 6

    @Test fun `las fotos se leen de verdad del recurso`() {
        val night = photos.get(SkyPhoto.NIGHT_CLEAR, 1080, 2400).toPixelMap()
        val top = night[540, 2]
        // Arriba, el #050A21 del degradado (más el velo de nubes, muy tenue).
        assertTrue("$top", top.red < 0.08f && top.blue in 0.09f..0.2f)
        val storm = photos.get(SkyPhoto.STORM, 1080, 2400)
        assertEquals(1080, storm.width)
        assertEquals(2400, storm.height)
        // Para una franja pequeña se decodifica a un cuarto.
        assertEquals(270, photos.get(SkyPhoto.RAIN, 260, 300).width)
        assertTrue(photos.cloudLayer(2400).width > 0)
    }

    @Test fun `pinta la foto pedida y su cristal`() {
        var seen: Backdrop? = null
        var tone: GlassTone? = null
        show({ SkyPhoto.CLEAR_COOL }, onBackdrop = { b, t -> seen = b; tone = t })
        rule.waitForIdle()
        rule.onNodeWithTag("sky-photo-sky_despejado_frio").assertExists()
        assertSame(GlassTone.forPhoto(SkyPhoto.CLEAR_COOL), tone)
        val pixels = paint(seen!!).toPixelMap()
        val expected = photos.get(SkyPhoto.CLEAR_COOL, w, h).toPixelMap()
        // Arriba en el centro, el azul de la foto (#1C5CBD).
        val got = pixels[w / 2, 1]
        assertTrue("$got", got.blue > 0.6f && got.red < 0.25f)
        assertTrue(close((got.blue * 255).toInt(), (expected[expected.width / 2, 1].blue * 255).toInt()))
    }

    @Test fun `sin reloj quieta y con reloj se marca en movimiento`() {
        show({ SkyPhoto.RAIN })
        rule.onNodeWithTag("sky-still").assertExists()
        rule.onNodeWithTag("sky-moving").assertDoesNotExist()
    }

    @Test fun `con reloj se marca en movimiento`() {
        show({ SkyPhoto.RAIN }, clock = SkyClock())
        rule.onNodeWithTag("sky-moving").assertExists()
        rule.onNodeWithTag("sky-still").assertDoesNotExist()
    }

    @Test fun `de noche pinta la luna en la esquina`() {
        val lit = MoonCache.litNow(context, 0.5, false)
        var withMoon: Backdrop? = null
        show({ SkyPhoto.NIGHT_CLEAR }, effectsOf(Sky.CLEAR, DayMoment.NIGHT), moon = MoonLook(0.5, false, lit),
            onBackdrop = { b, _ -> withMoon = b })
        rule.waitForIdle()
        val moonPixels = paint(withMoon!!).toPixelMap()
        // La luna llena, al 82 % del ancho y con el centro a 92 dp: clara sobre el azul marino.
        val c = moonPixels[(w * 0.82f).toInt(), 92]
        assertTrue("$c", c.red > 0.35f && c.green > 0.35f)
        // Lejos de la luna, la foto.
        val far = moonPixels[10, 200]
        assertTrue("$far", far.red < 0.2f)
    }

    @Test fun `sin luna en los efectos no la pinta aunque la haya`() {
        val lit = MoonCache.litNow(context, 0.5, false)
        var seen: Backdrop? = null
        show({ SkyPhoto.NIGHT_CLEAR }, SkyEffects.NONE, moon = MoonLook(0.5, false, lit), onBackdrop = { b, _ -> seen = b })
        rule.waitForIdle()
        val c = paint(seen!!).toPixelMap()[(w * 0.82f).toInt(), 92]
        assertTrue("$c", c.red < 0.2f)
    }

    @Test fun `el cambio de foto funde, dos fotos durante el fundido y una al acabar`() {
        var photo by mutableStateOf(SkyPhoto.CLOUDS)
        var seen: Backdrop? = null
        show({ photo }, clock = SkyClock(), onBackdrop = { b, _ -> seen = b })
        rule.waitForIdle()
        val from = paint(seen!!).toPixelMap()[w / 2, 2]
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { photo = SkyPhoto.GOLDEN_CLOUDS }
        // Con el reloj parado, la espera aplica el cambio y arranca el fundido; luego, medio fundido.
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag("sky-photo-sky_nubes").assertExists()
        rule.onNodeWithTag("sky-photo-sky_dorada_nubes").assertExists()
        // A medio fundido se pinta una mezcla: el rojo, entre el de las dos fotos.
        val half = paint(seen!!).toPixelMap()[w / 2, 2]
        rule.mainClock.advanceTimeBy(PHOTO_FADE_MS.toLong())
        rule.onNodeWithTag("sky-photo-sky_nubes").assertDoesNotExist()
        rule.onNodeWithTag("sky-photo-sky_dorada_nubes").assertExists()
        val to = paint(seen!!).toPixelMap()[w / 2, 2]
        val lo = minOf(from.red, to.red) + 0.005f
        val hi = maxOf(from.red, to.red) - 0.005f
        assertTrue("de ${from.red} a ${to.red}, a medias ${half.red}", lo < hi && half.red in lo..hi)
    }

    @Test fun `H9 cambiar el dato funde la capa de luz, a medias se pinta una mezcla de las dos escenas`() {
        var effects by mutableStateOf(SkyEffects.NONE)
        var seen: Backdrop? = null
        show({ SkyPhoto.RAIN }, clock = SkyClock(), light = true, effectsNow = { effects }, onBackdrop = { b, _ -> seen = b })
        rule.waitForIdle()
        val before = paint(seen!!).toPixelMap()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { effects = effectsOf(Sky.RAIN, DayMoment.DAY) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        val half = paint(seen!!).toPixelMap()
        rule.mainClock.advanceTimeBy(PHOTO_FADE_MS.toLong())
        val after = paint(seen!!).toPixelMap()
        fun sum(m: androidx.compose.ui.graphics.PixelMap) = (0 until w step 3).sumOf { x -> (0 until h step 3).sumOf { y -> m[x, y].run { red + green + blue }.toDouble() } }
        val (a, b, c) = Triple(sum(before), sum(half), sum(after))
        assertTrue("la lluvia no cambia nada: $a $c", abs(a - c) > 1.0)
        assertTrue("a medias ($b) no está entre $a y $c", b > minOf(a, c) && b < maxOf(a, c))
    }

    @Test fun `H9 con la escena en marcha y sin dato nuevo no se piden fotogramas`() {
        show({ SkyPhoto.RAIN }, effectsOf(Sky.RAIN, DayMoment.DAY), clock = SkyClock(), light = true)
        // Con un bucle de fotogramas, esperar a que se pare no terminaría.
        rule.waitForIdle()
    }

    @Test fun `sin reloj el fundido dura 150 ms`() {
        var photo by mutableStateOf(SkyPhoto.CLOUDS)
        show({ photo })
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { photo = SkyPhoto.RAIN }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithTag("sky-photo-sky_nubes").assertExists()
        rule.mainClock.advanceTimeBy(PHOTO_FADE_STILL_MS.toLong() + 50)
        rule.onNodeWithTag("sky-photo-sky_nubes").assertDoesNotExist()
        rule.onNodeWithTag("sky-photo-sky_lluvia").assertExists()
    }
}
