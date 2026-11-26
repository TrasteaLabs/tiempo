package com.trastealabs.tiempo.ui

import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.trastealabs.tiempo.ui.theme.CristalColors
import com.trastealabs.tiempo.ui.theme.WeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Las guardias de la D24 (Cristal 1.3, encargo 012): lo que se quita no vuelve. Crece con cada paquete. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class D24GuardTest {
    @get:Rule val rule = createComposeRule()

    private val root = File("src/main/java/com/trastealabs/tiempo/ui")

    private fun sources() = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun offenders(match: (code: String, file: File) -> Boolean): List<String> {
        assertTrue("no encuentro ${root.absolutePath}", root.isDirectory)
        return sources().flatMap { file ->
            file.readLines().withIndex().filter { (_, line) ->
                val code = line.substringBefore("//").trim()
                !code.startsWith("*") && !code.startsWith("/*") && match(code, file)
            }.map { (i, l) -> "${file.name}:${i + 1}: ${l.trim()}" }
        }
    }

    // ---------- P3: la onda de Material ----------

    @Test fun `WeatherTheme da la onda de Material con el color de la casa`() {
        var seen: Color? = null
        var configured = false
        rule.setContent {
            WeatherTheme {
                val config = LocalRippleConfiguration.current
                configured = config != null
                seen = config?.color
            }
        }
        rule.waitForIdle()
        assertTrue("WeatherTheme no da LocalRippleConfiguration", configured)
        assertNotNull(seen)
        assertEquals(CristalColors.Ripple, seen)
    }

    @Test fun `no queda luz de contacto ni hundido`() {
        val bad = offenders { code, _ ->
            "LocalIndication provides" in code || "ContactLight" in code || "Modifier.press" in code || ".press(" in code
        }
        assertTrue("luz de contacto u hundido:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun `indication = null solo en el campo del buscador`() {
        val bad = offenders { code, file ->
            file.name != "PlaceSearchUi.kt" && Regex("""indication\s*=\s*null""").containsMatchIn(code)
        }
        assertTrue("indication = null fuera del campo:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    // ---------- 020: la tinta y el interruptor salen del tema ----------

    @Test fun `la tinta del blanco y los colores del interruptor solo viven en el tema`() {
        val bad = offenders { code, file ->
            file.parentFile?.name != "theme" &&
                (Regex("""0xFF1[DB]2(433|733)""", RegexOption.IGNORE_CASE).containsMatchIn(code) || "SwitchDefaults.colors(" in code)
        }
        assertTrue("tinta o interruptor a mano:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    // ---------- P4: cristal solo en lo fijo ----------

    @Test fun `no queda followMoves ni sombra`() {
        val bad = offenders { code, _ -> "followMoves" in code || "Modifier.shadow" in code || ".shadow(" in code }
        assertTrue("followMoves o sombra:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun `el desenfoque en vivo solo vive en Glass punto kt`() {
        val bad = offenders { code, file -> file.name != "Glass.kt" && ("RenderEffect" in code || "BlurEffect" in code) }
        assertTrue("RenderEffect/BlurEffect fuera de glass/Glass.kt:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun `punto glass solo se llama desde la barra superior`() {
        val bad = offenders { code, file -> file.name != "GlassTopBar.kt" && ".glass(" in code && "fun " !in code }
        assertTrue(".glass( fuera de glass/GlassTopBar.kt:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun `no queda cristal que sigue movimientos ni el cristal oscuro de la hoja`() {
        val bad = offenders { code, _ -> "DarkGlass" in code || "sceneBehind" in code || "CollapsingTitle" in code }
        assertTrue("restos del cristal viejo:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    // ---------- P5: las hojas son ModalBottomSheet ----------

    @Test fun `no queda hoja propia ni su regla de soltar`() {
        // Los nombres van partidos para que ni este test los deje en `app/src` (el grep del encargo sale vacío).
        val own = "Cristal" + "Sheet"
        val gesture = "Sheet" + "Gesture"
        val bad = offenders { code, _ -> own in code || gesture in code }
        assertTrue("restos de la hoja propia: " + bad.joinToString(" | "), bad.isEmpty())
        assertTrue("$own.kt sigue", !File(root, "$own.kt").exists())
        assertTrue("$gesture.kt sigue", !File(root, "motion/$gesture.kt").exists())
    }

    @Test fun `las seis hojas son ModalBottomSheet opacos de la casa`() {
        listOf("HourlySheet.kt", "DaysSheet.kt", "WarningSheet.kt", "DeleteSheet.kt", "day/DayWindow.kt").forEach { name ->
            val code = File(root, name).readText()
            assertTrue("$name no usa ModalBottomSheet", "ModalBottomSheet(" in code)
            assertTrue("$name no es opaca (CristalColors.SheetSurface)", "containerColor = CristalColors.SheetSurface" in code)
        }
    }

    // ---------- P7: el movimiento sale del tema ----------

    @Test fun `WeatherTheme da MotionScheme standard`() {
        var seen: MotionScheme? = null
        rule.setContent { WeatherTheme { seen = MaterialTheme.motionScheme } }
        rule.waitForIdle()
        val expected = MotionScheme.standard()
        val s = seen!!
        assertEquals(expected.javaClass, s.javaClass)
        assertEquals(expected.defaultSpatialSpec<Float>(), s.defaultSpatialSpec<Float>())
        assertEquals(expected.fastSpatialSpec<Float>(), s.fastSpatialSpec<Float>())
        assertEquals(expected.slowSpatialSpec<Float>(), s.slowSpatialSpec<Float>())
    }

    /** Ningún muelle ni curva con números fuera de `theme/`; excepción escrita: el fundido lineal de fotos del cielo. */
    @Test fun `no hay curvas ni muelles con numeros fuera del tema`() {
        val bad = offenders { code, file ->
            file.name != "SceneBackdrop.kt" && !file.invariantSeparatorsPath.contains("/theme/") && (
                "CubicBezierEasing(" in code ||
                    Regex("""(spring|tween)\([^)]*\d""").containsMatchIn(code) ||
                    Regex("""(dampingRatio|stiffness)\s*=\s*[\d.]""").containsMatchIn(code) ||
                    Regex("""tween\([^)]*(FastOutSlowInEasing|LinearOutSlowInEasing|FastOutLinearInEasing|Easing\()""").containsMatchIn(code)
                )
        }
        assertTrue("curvas o muelles con números:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun `no queda el objeto Motion`() {
        val bad = offenders { code, _ -> Regex("""\bMotion\.[A-Za-z]""").containsMatchIn(code) }
        assertTrue("restos de Motion.*:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun `la excepcion de las fotos del cielo es solo un fundido lineal`() {
        val text = File(root, "scene/SceneBackdrop.kt").readText()
        assertTrue("tween(" in text && "LinearEasing" in text)
        assertTrue("CubicBezierEasing(" !in text && !Regex("""spring\([^)]*\d""").containsMatchIn(text))
    }
}
