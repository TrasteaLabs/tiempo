package com.trastealabs.tiempo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La voz de la casa (020, Cristal §9 y §14): ningún texto de `strings.xml` lleva lo que la voz prohíbe. Como el de
 * Cuaderno (`src/ui/voice.test.ts`), y con una prueba de que cada expresión caza lo que promete.
 */
class VoiceTest {
    private val strings = File("src/main/res/values/strings.xml")

    /** Lo prohibido: preguntar si está seguro, «cargando», el «pronto» sin fecha, el «¡Ups!» y el «nosotros» de empresa. */
    private val banned: List<Pair<String, Regex>> = listOf(
        "Cargando…" to Regex("""\bcargando\b""", RegexOption.IGNORE_CASE),
        "¡Ups!" to Regex("""¡\s*ups\b""", RegexOption.IGNORE_CASE),
        "¿Estás seguro?" to Regex("""est[aá]s\s+segur[oa]""", RegexOption.IGNORE_CASE),
        "pronto" to Regex("""\bpronto\b""", RegexOption.IGNORE_CASE),
        "nosotros" to Regex(
            """\b(nosotr[oa]s|nuestr[oa]s?|hemos|estamos|somos|tenemos|podemos|queremos|sentimos|lamentamos|vamos|te\s+avisaremos)\b""",
            RegexOption.IGNORE_CASE,
        ),
    )

    /** El texto de cada `<string>` y cada `<item>`, con su nombre o su línea. */
    private fun texts(): List<Pair<String, String>> {
        assertTrue("no encuentro ${strings.absolutePath}", strings.isFile)
        val entry = Regex("""<(string|item)\b([^>]*)>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
        val name = Regex("""name="([^"]+)"""")
        return entry.findAll(strings.readText()).map { m ->
            val where = name.find(m.groupValues[2])?.groupValues?.get(1) ?: "item"
            where to m.groupValues[3]
        }.toList()
    }

    @Test fun `strings xml no lleva nada de lo que la voz prohibe`() {
        val all = texts()
        assertTrue("strings.xml parece vacío (${all.size} textos)", all.size > 100)
        val hits = banned.flatMap { (what, re) ->
            all.filter { (_, text) -> re.containsMatchIn(text) }.map { (where, text) -> "«$what» en $where: $text" }
        }
        assertTrue("textos con lo prohibido:\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun `cada expresion caza lo que promete y deja pasar lo de la casa`() {
        val caught = mapOf(
            "Cargando…" to listOf("Cargando…", "cargando la previsión"),
            "¡Ups!" to listOf("¡Ups! Algo ha fallado", "¡ups!"),
            "¿Estás seguro?" to listOf("¿Estás seguro?", "¿Estas segura de borrarlo?"),
            "pronto" to listOf("Llega pronto", "Pronto"),
            "nosotros" to listOf("Lo hemos guardado", "Estamos buscando", "nuestro servidor", "Ninguno pasa por nosotros"),
        )
        banned.forEach { (what, re) ->
            caught.getValue(what).forEach { assertTrue("«$what» no caza «$it»", re.containsMatchIn(it)) }
        }
        val fine = listOf(
            "Buscando dónde estás…",
            "Descargando el tiempo de Villa Ejemplo…",
            "Te avisaré a las 07:30",
            "Prontitud",
            "Estás en Villa Ejemplo",
        )
        fine.forEach { text ->
            assertEquals("«$text» no debería cazarse", emptyList<String>(), banned.filter { it.second.containsMatchIn(text) }.map { it.first })
        }
    }
}
