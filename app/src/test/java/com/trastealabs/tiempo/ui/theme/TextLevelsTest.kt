package com.trastealabs.tiempo.ui.theme

import androidx.compose.ui.graphics.Color
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.ui.day.bandShadeAt
import com.trastealabs.tiempo.ui.scene.DAY_BAND_SHADE
import com.trastealabs.tiempo.widget.ratioAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cristal 1.4 (D25, encargo 019): cada nivel de texto llega a 4,5:1 sobre el punto más claro de lo que tiene detrás, con
 * las 14 fotos y sus velos (`TEXT_BACKDROPS`, medido por `tools/medir_niveles.py`) y sobre las superficies opacas.
 */
class TextLevelsTest {
    private fun ratio(text: Color, r: Float, g: Float, b: Float): Float {
        assertEquals("el texto es blanco con alfa", Color.White, text.copy(alpha = 1f))
        return ratioAt(r, g, b, text.alpha)
    }

    private fun check(levels: Map<String, Color>, surfaces: Set<String>) {
        val backdrops = TEXT_BACKDROPS.filter { it.surface in surfaces }
        assertEquals(surfaces, backdrops.map { it.surface }.toSet())
        val bad = levels.flatMap { (name, color) ->
            backdrops.mapNotNull { b ->
                val r = ratio(color, b.r, b.g, b.b)
                if (r < 4.5f) "$name sobre ${b.photo} ${b.surface}: ${"%.2f".format(r)}:1" else null
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun `hay punto mas claro para las 14 fotos en cada superficie`() {
        val surfaces = TEXT_BACKDROPS.map { it.surface }.toSet()
        assertEquals(setOf("header", "card", "scene", "screen_card", "postal", "day_band"), surfaces)
        for (s in surfaces) assertEquals(s, SkyPhoto.entries.toSet(), TEXT_BACKDROPS.filter { it.surface == s }.map { it.photo }.toSet())
    }

    @Test fun `los tres niveles de la escena llegan a 4,5 en la principal y en la franja del dia`() {
        check(
            mapOf("principal" to OnSky.Text, "secundario" to OnSky.Secondary, "terciario" to OnSky.Tertiary),
            setOf("header", "card", "day_band", "scene", "screen_card", "postal"),
        )
    }

    @Test fun `los niveles de Ajustes y Sitios llegan a 4,5 sobre su escena, sus tarjetas y las postales`() {
        check(
            mapOf(
                "principal" to OnScreen.Text, "secundario" to OnScreen.Secondary,
                "terciario" to OnScreen.Tertiary, "ayuda" to OnScreen.Hint,
            ),
            setOf("scene", "screen_card", "postal"),
        )
    }

    @Test fun `los niveles de las hojas llegan a 4,5 sobre la hoja, sus paneles y el dialogo`() {
        val sheet = CristalColors.SheetSurface
        val dialog = CristalColors.DialogSurface
        fun over(base: Color, white: Float) = Triple(
            base.red * 255 * (1 - white) + 255 * white,
            base.green * 255 * (1 - white) + 255 * white,
            base.blue * 255 * (1 - white) + 255 * white,
        )
        val surfaces = mapOf(
            "hoja" to over(sheet, 0f), "tarjeta de la ventana (7 %)" to over(sheet, 0.07f),
            "panel de la hoja (14 %)" to over(sheet, 0.14f), "dialogo" to over(dialog, 0f),
        )
        val levels = mapOf(
            "principal" to OnSheet.Text, "secundario" to OnSheet.Secondary,
            "terciario" to OnSheet.Tertiary, "ayuda" to OnSheet.Hint,
        )
        for ((s, c) in surfaces) for ((l, color) in levels) {
            val r = ratio(color, c.first, c.second, c.third)
            assertTrue("$l sobre $s: $r", r >= 4.5f)
        }
    }

    /** Sin esto, subir un nivel a blanco pleno «por si acaso» borra la jerarquía donde sí hay sitio. */
    @Test fun `donde hay sitio el secundario y el terciario no son blanco pleno`() {
        assertTrue(OnScreen.Secondary.alpha < 1f)
        assertTrue(OnSheet.Secondary.alpha < 1f)
        assertTrue(OnSheet.Tertiary.alpha < OnSheet.Secondary.alpha)
    }

    // ---------- La franja de la ventana del día ----------

    @Test fun `el oscurecido de la franja es el de siempre cuando la foto no pide mas`() {
        assertEquals(0f, bandShadeAt(0.3f, 0f), 1e-6f)
        assertEquals(0.55f, bandShadeAt(1f, 0f), 1e-6f)
        assertEquals(0.275f, bandShadeAt(0.65f, 0f), 1e-6f)
    }

    @Test fun `con fuerza extra la zona del texto queda a esa fuerza y arriba no se oscurece`() {
        assertEquals(SkyPhoto.entries.toSet(), DAY_BAND_SHADE.keys)
        val k = DAY_BAND_SHADE.getValue(SkyPhoto.CLOUDS)
        assertTrue("las nubes piden oscurecido extra", k > 0f)
        assertEquals(0f, bandShadeAt(0.05f, k), 1e-6f)
        assertEquals(k, bandShadeAt(0.4f, k), 1e-6f)   // el texto empieza en el 36 %
        assertTrue(bandShadeAt(0.36f, k) > 0.95f * k)
        for (photo in listOf(SkyPhoto.NIGHT_CLEAR, SkyPhoto.NIGHT_CLOUDS, SkyPhoto.NIGHT_RAIN)) {
            assertEquals(photo.name, 0f, DAY_BAND_SHADE.getValue(photo))
        }
    }

    // ---------- La guardia de alfas sueltas ----------

    private val ui = File("src/main/java/com/trastealabs/tiempo/ui")

    /** Un alfa de texto escrito a mano: blanco con alfa de 0,36 en adelante (los rellenos y bordes van por debajo), o un `Text(` con `.copy(alpha`. */
    private fun isLooseTextAlpha(code: String): Boolean {
        if ("Text(" in code && ".copy(alpha" in code) return true
        // La tinta de un texto con alfa (`color = ink.copy(alpha = 0.88f)`) y un `Modifier.alpha(0.75f)` fijo sobre una fila.
        if (Regex("""color = (ink|text|content)\w*\.copy\(alpha""").containsMatchIn(code)) return true
        if (Regex("""\.alpha\(0\.\d+f?\)""").containsMatchIn(code)) return true
        val m = Regex("""Color\.White\.copy\(alpha = ([0-9.]+)f?\)""").findAll(code)
        return m.any { it.groupValues[1].toFloat() >= 0.36f }
    }

    @Test fun `la guardia caza lo que promete`() {
        assertTrue(isLooseTextAlpha("""Text(x, color = Color.White.copy(alpha = 0.7f))"""))
        assertTrue(isLooseTextAlpha("""color = Color.White.copy(alpha = 0.92f),"""))
        assertTrue(isLooseTextAlpha("""Text(name, color = ink.copy(alpha = if (selected) 1f else 0.75f))"""))
        assertTrue(isLooseTextAlpha("""val c = Color.White.copy(alpha = 0.4f)"""))
        assertTrue(isLooseTextAlpha("""color = ink.copy(alpha = 0.88f),"""))
        assertTrue(isLooseTextAlpha("""Row(Modifier.alpha(0.75f), verticalAlignment = Alignment.CenterVertically) {"""))
        assertTrue(!isLooseTextAlpha("""WeatherIcon(sky, isDay, Modifier.alpha(if (past) PAST_ALPHA else 1f))"""))
        assertTrue(!isLooseTextAlpha("""private val Border = Color.White.copy(alpha = 0.30f)"""))
        assertTrue(!isLooseTextAlpha("""Text(x, color = OnSky.Secondary)"""))
    }

    @Test fun `ningun texto lleva un alfa escrito a mano fuera de Type`() {
        assertTrue("no encuentro ${ui.absolutePath}", ui.isDirectory)
        val bad = ui.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "Type.kt" }.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) ->
                val code = line.substringBefore("//").trim()
                !code.startsWith("*") && isLooseTextAlpha(code)
            }.map { (i, l) -> "${file.name}:${i + 1}: ${l.trim()}" }
        }.toList()
        assertTrue("alfas sueltos (usa OnSky, OnScreen u OnSheet):\n" + bad.joinToString("\n"), bad.isEmpty())
    }
}
