package com.trastealabs.tiempo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.MoonShow
import com.trastealabs.tiempo.domain.Place
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.WarningLevel
import com.trastealabs.tiempo.ui.scene.MoonLook
import com.trastealabs.tiempo.ui.scene.PhotoCache
import com.trastealabs.tiempo.ui.scene.decodeBand
import com.trastealabs.tiempo.ui.scene.photoCrop
import com.trastealabs.tiempo.ui.theme.CristalType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El 4×2 pintado (010, P3). Tamaños medidos en el OPPO por cable (2026-11-10), a 3 px/dp (xxhdpi):
 * - `adb shell dumpsys appwidget`: el 4×2 es `WeatherWidgetLargeReceiver` (id 35, `views_bitmap_memory` = 848.144 B con la
 *   imagen vieja a 2 px/dp, 656 × 292) y el 4×1 `WeatherWidgetReceiver` (id 36, 419.328 B = 626 × 164). No da los dp.
 * - Los dp que da el escritorio a Glance (`LocalSize`, anotado al pintar): **335,3 × 158** el 4×2. La vista que se ve mide
 *   930 × 438 px = **310 × 146 dp** (`uiautomator dump`): Android pinta la imagen algo más pequeña (×0,92) con `Crop`.
 * - CLAUDE.md: el 4×2 «≈ 328 × 146 dp» y el 4×1 313 × 82 (Android dice 328 × 90). El 2×1 (P4): 152 × 82 dp.
 * El P4 añade el 4×1 y el 2×1 (al final, «P4»).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPainterTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val now = TestData.at(2025, 6, 10, 10, 20)
    private val density = 3f
    private val large = 328f to 146f
    private val oppoSizes = listOf(328f to 146f, 310f to 146f, 335.33f to 158f)

    /**
     * Robolectric no decodifica por regiones (`BitmapRegionDecoder` da un bitmap vacío): en los tests, la foto de verdad se
     * decodifica entera y se recorta con [photoCrop], que es lo que hace `decodeBand` en el móvil.
     */
    @Before fun realPhotos() {
        bandDecoder = BandDecoder { ctx, photo, w, h, sample ->
            val full = ctx.resources.openRawResource(PhotoCache.resOf(photo)).use { BitmapFactory.decodeStream(it) }
            val r = photoCrop(photo, w, h)
            val cut = Bitmap.createBitmap(full, r.left, r.top, r.width(), r.height())
            Bitmap.createScaledBitmap(cut, (r.width() + sample - 1) / sample, (r.height() + sample - 1) / sample, true)
        }
    }

    @After fun restoreDecoder() {
        bandDecoder = BandDecoder { ctx, photo, w, h, sample -> decodeBand(ctx, photo, w, h, sample) }
    }

    private fun frameOf(
        snapshot: com.trastealabs.tiempo.domain.WeatherSnapshot = TestData.snapshot(now, currentC = 12.0),
        failed: Boolean = false,
        at: Long = now,
        layout: WidgetLayout = WidgetLayout.LARGE,
        phrase: String? = null,
    ) = widgetFrame(StoredState(snapshot, failed), layout, at, context, phrase)

    private fun paint(
        frame: WidgetFrame = frameOf(), size: Pair<Float, Float> = large, moon: MoonLook? = null, bare: Boolean = false,
    ) = paintWidget(context, frame, size.first, size.second, density, moon, bare)

    private fun warned(
        level: WarningLevel, phenomenon: String, place: Place = TestData.place, layout: WidgetLayout = WidgetLayout.LARGE,
    ): WidgetFrame {
        val w = Warning("a", level, phenomenon, "Zona", now - 3600, now + 3 * 3600, null, "", null, now - 7200)
        return frameOf(TestData.snapshot(now, place = place).let { it.copy(warnings = listOf(w)) }, layout = layout)
    }

    private fun WidgetImage.text(t: String) = texts.first { it.text == t }

    // ---------- tamaño y letra (H2) ----------

    @Test fun `el 4x2 a 328 x 146 dp y 3 px por dp da 984 x 438 px`() {
        val image = paint()
        assertEquals(984, image.bitmap.width)
        assertEquals(438, image.bitmap.height)
    }

    @Test fun `todos los textos llevan una letra de R font, un tamano de un rol y un peso de 300 a 700`() {
        val roles = listOf(
            CristalType.displayLarge, CristalType.displayMedium, CristalType.displaySmall, CristalType.headlineLarge,
            CristalType.headlineMedium, CristalType.headlineSmall, CristalType.titleLarge, CristalType.titleMedium,
            CristalType.titleSmall, CristalType.bodyLarge, CristalType.bodyMedium, CristalType.bodySmall,
            CristalType.labelLarge, CristalType.labelMedium, CristalType.labelSmall,
        ).map { it.fontSize.value to it.fontWeight!!.weight }.toSet()
        val fonts = mapOf(
            300 to R.font.google_sans_flex_300, 400 to R.font.google_sans_flex_400, 500 to R.font.google_sans_flex_500,
            600 to R.font.google_sans_flex_600, 700 to R.font.google_sans_flex_700,
        )
        val frames = listOf(frameOf(), warned(WarningLevel.RED, "temperaturas máximas"), frameOf(failed = true))
        for (f in frames) {
            for (t in paint(f).texts) {
                val key = t.sizePx / density to t.weight
                assertTrue("«${t.text}» $key no es un rol de CristalType", key in roles)
                assertTrue("«${t.text}»: peso ${t.weight}", t.weight in fonts)
                assertEquals("«${t.text}»: la letra del peso ${t.weight}", ResourcesCompat.getFont(context, fonts.getValue(t.weight)), t.typeface)
            }
        }
        val empty = paint(widgetFrame(StoredState(null, false), WidgetLayout.LARGE, now, context))
        assertTrue(empty.texts.isNotEmpty())
        empty.texts.forEach { assertTrue(it.sizePx / density to it.weight in roles) }
    }

    @Test fun `la temperatura es displayLarge de 57 y baja a displayMedium de 45 si no cabe`() {
        assertEquals(57f * density, paint().text("12°").sizePx, 0.01f)
        assertEquals(45f * density, paint(size = 328f to 120f).text("12°").sizePx, 0.01f)
    }

    // ---------- lo que hay y dónde (H8) ----------

    @Test fun `sitio, temperatura, cielo y max y min arriba, y al menos cinco horas abajo`() {
        val image = paint()
        val hoursTop = (146f - 8f - 52f) * density
        val place = image.text("Villa Ejemplo")
        val temp = image.text("12°")
        val sky = image.text("Nublado")
        val range = image.text("Máx 18° · Mín 9°")
        listOf(place, temp, sky, range).forEach { assertTrue("«${it.text}» arriba", it.rect.centerY() < hoursTop) }
        val hourTemps = image.texts.filter { it.rect.top >= hoursTop - 1f && it.text.endsWith("°") }
        assertTrue("horas: ${hourTemps.map { it.text }}", hourTemps.size >= 5)
        val labels = image.texts.filter { it.rect.top >= hoursTop - 1f && !it.text.endsWith("°") }.map { it.text }
        assertEquals(listOf("Ahora", "11:00", "12:00", "13:00", "14:00", "15:00"), labels)
    }

    @Test fun `el cielo y el max y min van a la derecha de la temperatura`() {
        val image = paint()
        val temp = image.text("12°")
        assertTrue(image.text("Nublado").rect.left > temp.rect.right)
        assertTrue(image.text("Máx 18° · Mín 9°").rect.left > temp.rect.right)
        assertEquals(328f * density - 14f * density, image.text("Nublado").rect.right, 1f)
    }

    @Test fun `cinco horas caben en el ancho sin pisarse`() {
        val image = paint(frameOf(TestData.snapshot(now, currentC = -12.0)))
        val hoursTop = (146f - 8f - 52f) * density
        val labels = image.texts
            .filter { it.rect.top >= hoursTop - 1f && (it.text.matches(Regex("\\d\\d:\\d\\d")) || it.text == "Ahora") }
            .sortedBy { it.rect.left }
        labels.zipWithNext().forEach { (a, b) -> assertTrue("${a.text} / ${b.text}", a.rect.right <= b.rect.left) }
    }

    // ---------- velo (H10) ----------

    @Test fun `el velo da al menos 4,5 en las 14 fotos`() {
        for (size in oppoSizes) for (photo in SkyPhoto.entries) {
            val image = paint(frameOf().copy(photo = photo), size = size)
            assertTrue("$photo a $size: ${image.veil.minContrast}", image.veil.minContrast >= 4.5f)
        }
    }

    @Test fun `antes de abrir la app, el texto de la pildora llega a 4,5 en las 14 fotos`() {
        val empty = widgetFrame(StoredState(null, false), WidgetLayout.LARGE, now, context)
        for (photo in SkyPhoto.entries) {
            val image = paint(empty.copy(photo = photo))
            assertTrue("$photo: ${image.veil.minContrast}", image.veil.minContrast >= 4.5f)
            assertEquals("Abre la app para empezar", image.texts.single().text)
        }
    }

    // ---------- etiqueta y nombre largo ----------

    @Test fun `un nombre largo se corta con puntos suspensivos y no pisa la pildora`() {
        val long = Place("Sanlúcar de Barrameda", "ES", TestData.place.position)
        val image = paint(warned(WarningLevel.RED, "temperaturas máximas", long))
        val all = image.texts.map { it.text }
        val place = image.texts.firstOrNull { it.text.startsWith("Sanl") } ?: error("sin sitio: $all")
        val chip = image.texts.firstOrNull { it.text.startsWith("Te") } ?: error("sin etiqueta: $all")
        assertTrue("«${place.text}» (${place.rect.right}) pisa «${chip.text}» (${chip.rect.left})", place.rect.right <= chip.rect.left)
        val extreme = paint(warned(WarningLevel.ORANGE, "tormentas", Place("San Bartolomé de Pinares del Monte Ejemplo", "ES", TestData.place.position)))
        val name = extreme.texts.first { it.text.startsWith("San ") }
        assertTrue(name.text, name.text.endsWith("…"))
        val tormentas = extreme.texts.first { it.text.startsWith("To") }
        assertTrue(name.rect.right <= tormentas.rect.left)
    }

    @Test fun `la etiqueta siempre cabe dentro del widget`() {
        val image = paint(warned(WarningLevel.RED, "temperaturas máximas", Place("Sanlúcar de Barrameda", "ES", TestData.place.position)))
        image.texts.forEach { assertTrue("«${it.text}» ${it.rect}", it.rect.left >= 0f && it.rect.right <= 984f) }
    }

    // ---------- bare, determinismo, luna ----------

    @Test fun `bare no pinta textos y deja el mismo velo`() {
        val frame = frameOf()
        val full = paint(frame)
        val bare = paint(frame, bare = true)
        assertTrue(bare.texts.isEmpty())
        assertEquals(full.veil, bare.veil)
        assertFalse(full.bitmap.sameAs(bare.bitmap))
    }

    @Test fun `el mismo frame da el mismo bitmap`() {
        assertTrue(paint().bitmap.sameAs(paint().bitmap))
    }

    @Test fun `la luna se pinta si el frame la trae`() {
        val night = TestData.at(2025, 6, 10, 23, 30)
        val frame = frameOf(TestData.snapshot(night).let { it.copy(current = it.current.copy(isDay = false)) }, at = night)
        assertTrue(frame.moon != MoonShow.NONE)
        val lit = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val withMoon = paint(frame, moon = MoonLook(frame.moonPhase!!, false, lit))
        val without = paint(frame)
        assertFalse(withMoon.bitmap.sameAs(without.bitmap))
    }

    // ---------- memoria (H11) ----------

    @Test fun `la memoria con los tamanos del OPPO no pasa de la mitad de una pantalla de 1080 x 2400`() {
        val limit = 1080L * 2400 * 4 * 3 / 2 / 2 // la mitad de 1080 × 2400 × 4 × 1,5 bytes
        // Dos imágenes del tamaño que da Glance al 4×2 (SizeMode.Exact puede generar una por orientación; en horizontal no se midió).
        val sizes = listOf(335.33f to 158f, 335.33f to 158f)
        val total = sizes.sumOf { paint(size = it).bitmap.allocationByteCount.toLong() }
        assertTrue("$total B > $limit B", total <= limit)
        // Y aun con un 4×2 mucho mayor que el del OPPO (una tableta).
        assertTrue(paint(size = 600f to 300f).bitmap.allocationByteCount.toLong() <= limit)
    }

    // ---------- P4: el 4×1 y el 2×1 ----------

    private val medium = 328f to 90f
    private val mediumOppo = 313f to 82f
    private val small = 152f to 82f
    private val mediumSizes = listOf(medium, mediumOppo, 300f to 120f)
    private val roleKeys = listOf(
        CristalType.displayLarge, CristalType.displayMedium, CristalType.displaySmall, CristalType.headlineLarge,
        CristalType.headlineMedium, CristalType.headlineSmall, CristalType.titleLarge, CristalType.titleMedium,
        CristalType.titleSmall, CristalType.bodyLarge, CristalType.bodyMedium, CristalType.bodySmall,
        CristalType.labelLarge, CristalType.labelMedium, CristalType.labelSmall,
    ).map { it.fontSize.value to it.fontWeight!!.weight }.toSet()
    private val clock = Regex("\\d\\d:\\d\\d")

    /** El rol que le toca a cada texto del 4×1 y del 2×1, por lo que dice y dónde está (017 H9): no basta con que sea uno de los roles. */
    private fun expectedRole(layout: WidgetLayout, t: PaintedText): androidx.compose.ui.text.TextStyle = when {
        t.text.startsWith("Villa") -> if (layout == WidgetLayout.SMALL) CristalType.labelMedium else CristalType.titleMedium
        t.text == "Nublado" -> CristalType.titleSmall
        t.text.startsWith("Abre la app") -> CristalType.labelLarge
        t.text.matches(Regex("-?\\d+°")) -> if (t.rect.left / density < 100f) CristalType.displaySmall else CristalType.labelMedium
        t.text.contains(" / ") -> CristalType.bodyMedium
        t.text.matches(clock) || t.text == "Ahora" || t.text.startsWith("Te") || t.text == "Aviso" -> CristalType.labelMedium
        else -> error("«${t.text}» sin rol esperado")
    }

    private fun mediumFrame() = frameOf(layout = WidgetLayout.MEDIUM)
    private fun smallFrame() = frameOf(layout = WidgetLayout.SMALL)
    private fun paintAt(frame: WidgetFrame, size: Pair<Float, Float>, bare: Boolean = false) = paint(frame, size, bare = bare)

    @Test fun `el 4x1 a 328 x 90 y a 313 x 82 lleva sitio, temperatura, cielo, max y min y tres horas`() {
        for (size in listOf(medium, mediumOppo)) {
            val image = paintAt(mediumFrame(), size)
            assertEquals((size.first * density).toInt(), image.bitmap.width)
            assertEquals((size.second * density).toInt(), image.bitmap.height)
            val place = image.text("Villa Ejemplo")
            val temp = image.text("12°")
            val sky = image.text("Nublado")
            val range = image.text("18° / 9°")
            val hoursLeft = (size.first - 10f - 114f) * density
            listOf(place, temp, sky, range).forEach { assertTrue("«${it.text}» a la izquierda de las horas", it.rect.right <= hoursLeft) }
            assertTrue(sky.rect.left > temp.rect.right)
            val labels = image.texts.filter { it.rect.left >= hoursLeft - 1f && it.text.matches(clock) }.sortedBy { it.rect.left }
            assertEquals(listOf("11:00", "12:00", "13:00"), labels.map { it.text })
            labels.zipWithNext().forEach { (a, b) -> assertTrue(a.rect.right <= b.rect.left) }
            assertEquals(3, image.texts.count { it.rect.left >= hoursLeft - 1f && it.text.endsWith("°") })
        }
    }

    @Test fun `el 2x1 a 152 x 82 lleva icono, temperatura y debajo sitio con max y min`() {
        val image = paintAt(smallFrame(), small)
        assertEquals(456, image.bitmap.width)
        assertEquals(246, image.bitmap.height)
        val temp = image.text("12°")
        val line = image.text("Villa Ejemplo · 18°/9°")
        assertTrue(line.rect.top >= temp.rect.bottom - 1f)
        assertTrue(line.rect.right <= (152f - 10f) * density + 1f)
        assertTrue(image.texts.none { it.text.matches(clock) })
    }

    @Test fun `en el 2x1 el nombre se corta antes que las cifras`() {
        val long = Place("Puerto de Navacerrada", "ES", TestData.place.position)
        val state = TestData.snapshot(now, currentC = -5.0, place = long)
        val image = paintAt(frameOf(state, layout = WidgetLayout.SMALL), small)
        val line = image.texts.first { it.text.startsWith("Puerto") }
        assertTrue(line.text, line.text.contains("…") && line.text.endsWith(" · 18°/9°"))
        assertTrue(line.rect.right <= (152f - 10f) * density + 1f)
    }

    @Test fun `en el 4x1 y el 2x1 todos los textos son de un rol, con la letra de R font y peso de 300 a 700`() {
        val fonts = mapOf(
            300 to R.font.google_sans_flex_300, 400 to R.font.google_sans_flex_400, 500 to R.font.google_sans_flex_500,
            600 to R.font.google_sans_flex_600, 700 to R.font.google_sans_flex_700,
        )
        val cases = listOf(WidgetLayout.MEDIUM to mediumSizes, WidgetLayout.SMALL to listOf(small))
        for ((layout, sizes) in cases) for (size in sizes) {
            val frames = listOf(
                frameOf(layout = layout), warned(WarningLevel.RED, "temperaturas máximas", layout = layout),
                frameOf(failed = true, layout = layout), widgetFrame(StoredState(null, false), layout, now, context),
            )
            for (f in frames) for (t in paintAt(f, size).texts) {
                val key = t.sizePx / density to t.weight
                val role = expectedRole(layout, t)
                assertEquals("$layout «${t.text}» $key", role.fontSize.value to role.fontWeight!!.weight, key)
                assertEquals(ResourcesCompat.getFont(context, fonts.getValue(t.weight)), t.typeface)
            }
        }
    }

    @Test fun `la temperatura del 4x1 y del 2x1 es displaySmall de 36`() {
        assertEquals(36f * density, paintAt(mediumFrame(), medium).text("12°").sizePx, 0.01f)
        assertEquals(36f * density, paintAt(smallFrame(), small).text("12°").sizePx, 0.01f)
    }

    @Test fun `el velo da al menos 4,5 en las 14 fotos y en los tres tamanos`() {
        for (photo in SkyPhoto.entries) {
            for (size in mediumSizes) {
                val i = paintAt(mediumFrame().copy(photo = photo), size)
                assertTrue("4×1 $photo $size: ${i.veil.minContrast}", i.veil.minContrast >= 4.5f)
            }
            val s = paintAt(smallFrame().copy(photo = photo), small)
            assertTrue("2×1 $photo: ${s.veil.minContrast}", s.veil.minContrast >= 4.5f)
        }
    }

    @Test fun `un tamano intermedio elige su version y no saca textos fuera`() {
        for (size in listOf(300f to 120f, 260f to 110f, 330f to 129f)) {
            val layout = widgetLayout(size.first, size.second)
            val image = paintAt(frameOf(layout = layout), size)
            val w = size.first * density
            val h = size.second * density
            image.texts.forEach {
                assertTrue("$layout $size «${it.text}» ${it.rect}", it.rect.left >= 0f && it.rect.right <= w && it.rect.top >= 0f && it.rect.bottom <= h)
            }
        }
    }

    @Test fun `con Crop a 313 x 82 ningun texto queda a menos de 2 dp del borde de arriba ni del de abajo`() {
        val sanlucar = Place("Sanlúcar de Barrameda", "ES", TestData.place.position)
        val frames = listOf(mediumFrame(), warned(WarningLevel.RED, "temperaturas máximas", sanlucar, WidgetLayout.MEDIUM))
        for (f in frames) {
            // Glance da 328 × 90 y el móvil enseña 313 × 82: Crop recorta el exceso de alto repartido arriba y abajo.
            val image = paintAt(f, medium)
            val keepH = medium.first / (313f / 82f) * density
            val cut = (medium.second * density - keepH) / 2
            image.texts.forEach {
                assertTrue("«${it.text}» ${it.rect}", it.rect.top - cut >= 2f * density && it.rect.bottom <= medium.second * density - cut - 2f * density)
            }
        }
        val s = paintAt(smallFrame(), small)
        s.texts.forEach { assertTrue("«${it.text}»", it.rect.top >= 2f * density && it.rect.bottom <= 82f * density - 2f * density) }
    }

    @Test fun `con un nombre largo y aviso, el 4x1 corta el nombre y la etiqueta no lo pisa`() {
        val long = Place("Sanlúcar de Barrameda", "ES", TestData.place.position)
        val image = paintAt(warned(WarningLevel.ORANGE, "temperaturas máximas", long, WidgetLayout.MEDIUM), mediumOppo)
        val place = image.texts.first { it.text.startsWith("S") }
        assertTrue(place.text, place.text.endsWith("…"))
        val chip = image.texts.first { it.text.startsWith("Te") }
        assertTrue(place.rect.right <= chip.rect.left)
        image.texts.forEach { assertTrue("«${it.text}»", it.rect.left >= 0f && it.rect.right <= 313f * density) }
    }

    @Test fun `en el 2x1 el aviso es una pildora de Aviso y sin conexion la nube con la hora`() {
        val warn = paintAt(warned(WarningLevel.RED, "tormentas", layout = WidgetLayout.SMALL), small)
        assertTrue(warn.texts.any { it.text == "Aviso" })
        assertTrue(warn.texts.none { it.text.startsWith("Villa") })
        val snapshot = TestData.snapshot(now, fetchedAt = TestData.at(2025, 6, 10, 8, 15))
        val offline = paintAt(frameOf(snapshot, failed = true, layout = WidgetLayout.SMALL), small)
        assertTrue(offline.texts.any { it.text == "08:15" })
    }

    @Test fun `antes de abrir la app, Abre la app en el 2x1 y la frase entera en el 4x1`() {
        val s = paintAt(widgetFrame(StoredState(null, false), WidgetLayout.SMALL, now, context), small)
        assertEquals("Abre la app", s.texts.single().text)
        val m = paintAt(widgetFrame(StoredState(null, false), WidgetLayout.MEDIUM, now, context), medium)
        assertEquals("Abre la app para empezar", m.texts.single().text)
    }

    @Test fun `bare en el 4x1 y el 2x1 no pinta textos`() {
        assertTrue(paintAt(mediumFrame(), medium, bare = true).texts.isEmpty())
        assertTrue(paintAt(smallFrame(), small, bare = true).texts.isEmpty())
    }

    @Test fun `la memoria de los tres tamanos juntos sigue bajo la mitad de una pantalla`() {
        val limit = 1080L * 2400 * 4 * 3 / 2 / 2
        val total = paintAt(mediumFrame(), medium).bitmap.allocationByteCount.toLong() +
            paintAt(smallFrame(), small).bitmap.allocationByteCount.toLong() +
            paint(size = 335.33f to 158f).bitmap.allocationByteCount.toLong()
        assertTrue("$total B > $limit B", total <= limit)
    }

    // ---------- 017 P1 ----------

    @Test fun `con 1, 3 y 5 horas las horas se reparten por todo el ancho sin hueco a la derecha (017 H4)`() {
        for (n in listOf(1, 3, 5)) {
            val base = frameOf()
            val image = paint(base.copy(hours = base.hours.take(n)))
            val hoursTop = (146f - 8f - 52f) * density
            val labels = image.texts.filter { it.rect.top >= hoursTop - 1f && (it.text.matches(clock) || it.text == "Ahora") }.sortedBy { it.rect.left }
            assertEquals(n, labels.size)
            val share = (328f - 28f) / n * density
            val last = labels.last().rect
            assertEquals("$n horas: la última celda acaba en el borde", (328f - 14f) * density, (last.left + last.right) / 2 + share / 2, 1.5f)
            labels.zipWithNext().forEach { (a, b) -> assertTrue(a.rect.right <= b.rect.left) }
        }
    }

    @Test fun `con 1 y 2 horas el 4x1 las reparte por el ancho de las horas`() {
        for (n in listOf(1, 2)) {
            val base = mediumFrame()
            val image = paintAt(base.copy(hours = base.hours.take(n)), mediumOppo)
            val labels = image.texts.filter { it.text.matches(clock) || it.text == "Ahora" }.sortedBy { it.rect.left }
            assertEquals(n, labels.size)
            val right = (313f - 10f) * density
            val last = labels.last().rect
            assertEquals(right, (last.left + last.right) / 2 + (114f / n / 2) * density, 1.5f)
        }
    }

    @Test fun `la pildora de sin conexion entra en la medida del velo y su texto llega a 4,5 en las 14 fotos (017 H4)`() {
        val offline = frameOf(failed = true)
        for (size in listOf(large, mediumOppo)) for (photo in SkyPhoto.entries) {
            val layout = if (size == large) WidgetLayout.LARGE else WidgetLayout.MEDIUM
            val frame = frameOf(failed = true, layout = layout).copy(photo = photo)
            val withText = paint(frame, size)
            val bare = paint(frame, size, bare = true).bitmap
            val chip = withText.texts.first { it.text == "10:20" || it.text == offline.badge.let { b -> (b as WidgetBadge.Offline).time } }
            val ratios = ArrayList<Float>()
            var y = chip.rect.top + chip.rect.height() * 0.18f
            while (y < chip.rect.top + chip.rect.height() * 0.82f) {
                var x = chip.rect.left + 3f
                while (x < chip.rect.right - 3f) {
                    val p = bare.getPixel(x.toInt(), y.toInt())
                    ratios.add(ratioAt(Color.red(p).toFloat(), Color.green(p).toFloat(), Color.blue(p).toFloat(), 1f))
                    x += 4.5f
                }
                y += 4.5f
            }
            ratios.sort()
            val worst = ratios[(ratios.size * 0.01).toInt()]
            assertTrue("$layout $photo: $worst", worst >= 4.5f)
        }
    }

    @Test fun `el 4x1 a 280 dp deja el nombre con 6 letras o mas, el cielo entero y las horas dentro (017 H3)`() {
        val narrow = 280f to 82f
        val long = Place("Villanueva de la Serena", "ES", TestData.place.position)
        for (level in listOf(WarningLevel.RED, WarningLevel.YELLOW)) {
            val image = paintAt(warned(level, "temperaturas máximas", long, WidgetLayout.MEDIUM), narrow)
            val name = image.texts.first { it.text.startsWith("Villa") }
            assertTrue("«${name.text}» tiene menos de 6 letras", name.text.removeSuffix("…").length >= 6)
            val chip = image.texts.first { it.text.startsWith("Te") }
            assertTrue(name.rect.right <= chip.rect.left)
        }
        val image = paintAt(mediumFrame(), narrow)
        val wide = paintAt(mediumFrame(), mediumOppo)
        assertEquals("Nublado", image.text("Nublado").text)
        assertEquals("Villa Ejemplo", image.text("Villa Ejemplo").text)
        val hours = image.texts.filter { it.text.matches(clock) }.sortedBy { it.rect.left }
        assertEquals(3, hours.size)
        hours.zipWithNext().forEach { (a, b) -> assertTrue(a.rect.right <= b.rect.left) }
        assertTrue(hours.last().rect.right <= (280f - 10f) * density)
        // El cielo y la temperatura quedan donde estaban a 313 dp.
        assertEquals(wide.text("12°").rect.left, image.text("12°").rect.left, 0.5f)
        assertEquals(wide.text("Nublado").rect.left, image.text("Nublado").rect.left, 0.5f)
    }

    // ---------- 018 P3: la frase del parte en el 4×2 ----------

    private val shortPhrase = "Sol toda la tarde"
    private val longPhrase = "Mañana fresca, de 12°; sol y viento fuerte por la tarde"
    private val stormPhrase = "Tormentas desde las 17:00 y chubascos fuertes de noche."
    private val phraseSizeDp = CristalType.bodySmall.fontSize.value
    private val hoursTopPx = (146f - 8f - 52f) * density
    private val topRowBottomPx = 30f * density

    private fun PaintedText.isPhrase() = sizePx / density == phraseSizeDp && weight == 400
    private fun WidgetImage.phraseLines() = texts.filter { it.isPhrase() }.sortedBy { it.rect.top }

    /** Ningún texto se sale del widget ni de la zona de en medio (entre la fila de arriba y las horas), y ninguno pisa a otro. */
    private fun assertPhraseClean(image: WidgetImage, label: String, size: Pair<Float, Float> = large) {
        val widthPx = size.first * density
        val heightPx = size.second * density
        val hoursTopPx = (size.second - 8f - 52f) * density
        val lines = image.phraseLines()
        assertTrue("$label: sin líneas de frase", lines.isNotEmpty())
        for (t in image.texts) {
            assertTrue("$label «${t.text}» fuera: ${t.rect}", t.rect.left >= 0f && t.rect.right <= widthPx && t.rect.top >= 0f && t.rect.bottom <= heightPx)
        }
        for (l in lines) {
            assertTrue("$label «${l.text}» sube a la fila de arriba", l.rect.top >= topRowBottomPx - 1f)
            assertTrue("$label «${l.text}» baja a las horas", l.rect.bottom <= hoursTopPx + 1f)
        }
        // Las líneas de la frase y el máx/mín no pisan a nadie (la caja de línea de la cifra grande es más alta que sus dígitos, no cuenta).
        val mine = lines + image.texts.filter { it.text.startsWith("Máx ") }
        for (a in mine) for (b in image.texts) {
            if (a === b || (b.text.startsWith("Máx ") && a.text.startsWith("Máx "))) continue
            if (b.text.matches(Regex("-?\\d+°")) && b.rect.height() > 40f * density) continue
            assertFalse("$label «${a.text}» ${a.rect} pisa «${b.text}» ${b.rect}", android.graphics.RectF.intersects(a.rect, b.rect))
        }
        val temp = image.texts.first { it.rect.height() > 40f * density }
        for (a in mine) assertTrue("$label «${a.text}» pisa la cifra ${temp.rect}", a.rect.left >= temp.rect.right)
    }

    @Test fun `la frase demo de 55 mide 55 caracteres y lleva n con tilde, tilde y grados`() {
        assertEquals(55, com.trastealabs.tiempo.demo.demoPhrase("long")!!.length)
        assertEquals(longPhrase, com.trastealabs.tiempo.demo.demoPhrase("long"))
        assertTrue(longPhrase.any { it == 'ñ' } && longPhrase.contains('°'))
        assertEquals(55, stormPhrase.length)
        assertTrue(shortPhrase.length < 40)
    }

    @Test fun `con frase corta va en una linea, en lugar de la palabra del cielo, con el max y min debajo`() {
        val image = paint(frameOf(phrase = shortPhrase))
        val lines = image.phraseLines()
        assertEquals(listOf(shortPhrase), lines.map { it.text })
        assertTrue("no hay palabra del cielo", image.texts.none { it.text == "Nublado" })
        val range = image.text("Máx 18° · Mín 9°")
        assertTrue(range.rect.top >= lines.last().rect.bottom - 1f)
        // Alineada a la derecha, en el borde de 314 dp.
        assertEquals((328f - 14f) * density, lines.single().rect.right, 1f)
        assertEquals((328f - 14f) * density, range.rect.right, 1f)
        assertTrue(lines.single().rect.left > image.text("12°").rect.right)
        assertPhraseClean(image, "corta")
    }

    @Test fun `la frase va a 12 sp de peso 400 al 92 por ciento, la letra de R font`() {
        val image = paint(frameOf(phrase = longPhrase))
        for (l in image.phraseLines()) {
            assertEquals(12f * density, l.sizePx, 0.01f)
            assertEquals(400, l.weight)
            assertEquals(0.92f, l.alpha, 0.001f)
            assertEquals(ResourcesCompat.getFont(context, R.font.google_sans_flex_400), l.typeface)
        }
    }

    @Test fun `con la de 55 caracteres va en dos lineas enteras, sin puntos suspensivos, y nada se sale ni se pisa`() {
        for (phrase in listOf(longPhrase, stormPhrase)) for (size in oppoSizes) {
            val image = paint(frameOf(phrase = phrase), size = size)
            val lines = image.phraseLines()
            assertTrue(image.texts.none { it.text.contains("…") })
            if (size.first >= 328f) {
                // A 328 dp y más (lo que da Glance al 4×2 del OPPO) las dos de 55 caben en dos líneas.
                assertEquals("$phrase a $size", 2, lines.size)
                assertEquals(phrase, lines.joinToString(" ") { it.text })
                // Cada línea cabe entre la cifra y el borde derecho, con el icono del cielo.
                val temp = image.text("12°")
                for (l in lines) assertTrue("«${l.text}»", l.rect.left >= temp.rect.right + (10f + 22f) * density - 1f)
                assertPhraseClean(image, "55 ${phrase.take(8)} a $size", size)
            } else if (lines.isEmpty()) {
                // A 310 dp puede no caber: entonces es el widget de hoy, con la palabra del cielo, nunca a medias.
                assertTrue("$phrase a $size", image.texts.any { it.text == "Nublado" })
            } else {
                assertEquals(phrase, lines.joinToString(" ") { it.text })
            }
        }
    }

    @Test fun `con frase la palabra del cielo no sale y la cifra y las horas no se mueven`() {
        val frame = frameOf(phrase = longPhrase)
        val image = paint(frame)
        val plain = paint(frameOf())
        assertEquals(plain.text("12°").rect, image.text("12°").rect)
        assertTrue(image.texts.none { it.text == frame.skyText })
        val hours = { i: WidgetImage -> i.texts.filter { it.rect.top >= hoursTopPx - 1f }.map { it.text to it.rect } }
        assertEquals(hours(plain), hours(image))
        assertFalse(plain.bitmap.sameAs(image.bitmap))
    }

    @Test fun `el bloque de la frase y el max y min se centra entre la fila de arriba y las horas`() {
        for ((phrase, lines) in listOf(shortPhrase to 1, longPhrase to 2)) {
            val image = paint(frameOf(phrase = phrase))
            val ls = image.phraseLines()
            assertEquals(lines, ls.size)
            val range = image.text("Máx 18° · Mín 9°")
            val top = ls.first().rect.top
            val bottom = range.rect.bottom
            assertEquals("alto del bloque", (lines * 16f + 20f) * density, bottom - top, 1f)
            assertEquals("centrado", (topRowBottomPx + hoursTopPx) / 2, (top + bottom) / 2, 1.5f)
        }
    }

    @Test fun `con aviso o sin conexion la etiqueta sigue arriba y la frase debajo, sin pisarse`() {
        val withWarning = warned(WarningLevel.RED, "temperaturas máximas").copy(phrase = longPhrase)
        val offline = frameOf(failed = true, phrase = longPhrase)
        val sanlucar = Place("Sanlúcar de Barrameda", "ES", TestData.place.position)
        val both = warned(WarningLevel.ORANGE, "tormentas", sanlucar).copy(phrase = stormPhrase)
        for ((label, frame) in listOf("aviso" to withWarning, "sin conexión" to offline, "aviso y nombre largo" to both)) {
            val image = paint(frame)
            assertPhraseClean(image, label)
            val chip = image.texts.first { it.text.startsWith("Te") || it.text.startsWith("To") || it.text == "10:20" }
            for (l in image.phraseLines()) assertTrue("$label: «${chip.text}» sobre «${l.text}»", chip.rect.bottom <= l.rect.top)
        }
    }

    @Test fun `con la cifra mas ancha y la frase de 55 que no cabe, se pinta la palabra del cielo y nunca la frase a medias`() {
        // «-12°» ocupa más y deja menos ancho a la frase; si en dos líneas no entera, el widget queda como el de hoy.
        val wide = TestData.snapshot(now, currentC = -12.0)
        val tight = "Intervalos de nubes y claros, probables chubascos hoy"
        val fits = paint(frameOf(wide, phrase = longPhrase))
        val noFit = paint(frameOf(wide, phrase = "Sin precipitaciones apreciables ni nubosidad relevante"))
        assertTrue("sin frase", noFit.phraseLines().isEmpty())
        assertTrue("palabra del cielo", noFit.texts.any { it.text == "Nublado" })
        assertTrue(noFit.texts.none { it.text.contains("…") })
        assertTrue(noFit.bitmap.sameAs(paint(frameOf(wide)).bitmap))
        assertFalse(noFit.phraseShown)
        assertTrue(fits.phraseShown)
        // Y la misma frase en un widget estrecho tampoco cabe, nunca recortada.
        val narrow = paint(frameOf(phrase = tight), size = 200f to 146f)
        assertTrue(narrow.texts.none { it.text.contains("…") && it.isPhrase() })
        assertTrue(fits.phraseLines().all { !it.text.contains("…") })
    }

    @Test fun `una palabra mas ancha que la linea tampoco se pinta recortada`() {
        val image = paint(frameOf(phrase = "Supercalifragilisticoespialidosoextraordinariamente"))
        assertTrue(image.phraseLines().isEmpty())
        assertTrue(image.texts.any { it.text == "Nublado" })
    }

    @Test fun `sin frase, o con una en blanco, la imagen es identica a la de hoy pixel a pixel`() {
        val plain = paint(frameOf())
        assertTrue(plain.bitmap.sameAs(paint(frameOf(phrase = null)).bitmap))
        assertTrue(plain.bitmap.sameAs(paint(frameOf(phrase = "")).bitmap))
        assertTrue(plain.bitmap.sameAs(paint(frameOf(phrase = "   ")).bitmap))
        assertEquals(plain.texts.map { it.text }, paint(frameOf(phrase = null)).texts.map { it.text })
        assertEquals(plain.veil, paint(frameOf(phrase = null)).veil)
        assertTrue(plain.texts.any { it.text == "Nublado" })
    }

    @Test fun `en el 4x1 y el 2x1 la frase no cambia nada`() {
        for ((layout, size) in listOf(WidgetLayout.MEDIUM to mediumOppo, WidgetLayout.SMALL to small)) {
            val plain = paintAt(frameOf(layout = layout), size)
            val with = paintAt(frameOf(layout = layout, phrase = longPhrase), size)
            assertTrue("$layout", plain.bitmap.sameAs(with.bitmap))
        }
        // Aunque el frame traiga la frase a la fuerza, el pintor del 4×1 y del 2×1 no la dibuja.
        val forced = frameOf(layout = WidgetLayout.MEDIUM).copy(phrase = longPhrase)
        assertTrue(paintAt(forced, mediumOppo).texts.none { it.text.contains("Mañana fresca") })
    }

    @Test fun `con menos de 6 horas las horas se reparten como hoy y la frase no se toca`() {
        for (n in listOf(1, 3, 5)) {
            val base = frameOf(phrase = longPhrase)
            val frame = base.copy(hours = base.hours.take(n))
            val image = paint(frame)
            val noPhrase = paint(frame.copy(phrase = null))
            val labels = { i: WidgetImage -> i.texts.filter { it.rect.top >= hoursTopPx - 1f }.map { it.text to it.rect } }
            assertEquals("$n horas", labels(noPhrase), labels(image))
            assertEquals(2, image.phraseLines().size)
            assertPhraseClean(image, "$n horas")
        }
    }

    @Test fun `la frase queda dentro de la capa de arriba del velo, a menos de 86 dp, para que no vuelva la linea del 017`() {
        val image = paint(frameOf(phrase = longPhrase))
        val range = image.text("Máx 18° · Mín 9°")
        val top = VEIL_LAYERS.getValue(WidgetLayout.LARGE).first { it.side == VeilSide.TOP }
        assertTrue("el bloque acaba en ${range.rect.bottom / density} dp", range.rect.bottom <= top.fullDp * density)
        for (l in image.phraseLines()) assertTrue(l.rect.bottom <= top.fullDp * density)
    }

    @Test fun `el texto de la frase llega a 4,5 en las 14 fotos, de dia y de noche, con aviso y con la mas larga`() {
        val night = TestData.at(2025, 6, 10, 23, 30)
        val nightSnapshot = TestData.snapshot(night, currentC = 14.0).let { it.copy(current = it.current.copy(isDay = false)) }
        val cases = listOf(
            "corta" to frameOf(phrase = shortPhrase),
            "55" to frameOf(phrase = longPhrase),
            "tormentas" to frameOf(phrase = stormPhrase),
            "aviso" to warned(WarningLevel.ORANGE, "tormentas").copy(phrase = stormPhrase),
            "sin conexión" to frameOf(failed = true, phrase = stormPhrase),
            "noche" to frameOf(nightSnapshot, at = night, phrase = longPhrase),
        )
        for ((label, base) in cases) for (photo in SkyPhoto.entries) for (size in oppoSizes) {
            val frame = base.copy(photo = photo)
            val image = paint(frame, size)
            assertTrue("$label $photo $size: velo ${image.veil.minContrast}", image.veil.minContrast >= 4.5f)
        }
    }

    @Test fun `medido en los pixeles, el texto de la frase llega a 4,5 sobre las 14 fotos`() {
        for (photo in SkyPhoto.entries) {
            val frame = frameOf(phrase = longPhrase).copy(photo = photo)
            val withText = paint(frame)
            val bare = paint(frame, bare = true).bitmap
            val ratios = ArrayList<Float>()
            for (line in withText.phraseLines()) {
                var y = line.rect.top + line.rect.height() * 0.18f
                while (y < line.rect.top + line.rect.height() * 0.82f) {
                    var x = line.rect.left + 3f
                    while (x < line.rect.right - 3f) {
                        val p = bare.getPixel(x.toInt(), y.toInt())
                        ratios.add(ratioAt(Color.red(p).toFloat(), Color.green(p).toFloat(), Color.blue(p).toFloat(), 0.92f))
                        x += 4.5f
                    }
                    y += 4.5f
                }
            }
            ratios.sort()
            val worst = ratios[(ratios.size * 0.01).toInt()]
            assertTrue("$photo: $worst", worst >= 4.5f)
        }
    }

    @Test fun `la frase con n con tilde, tilde y grados se pinta entera`() {
        val image = paint(frameOf(phrase = "Mañana, más frío: 5° a las 08:00; después, sol"))
        assertEquals("Mañana, más frío: 5° a las 08:00; después, sol", image.phraseLines().joinToString(" ") { it.text })
    }
}
