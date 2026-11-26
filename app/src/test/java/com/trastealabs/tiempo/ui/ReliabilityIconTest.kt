package com.trastealabs.tiempo.ui

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.trastealabs.tiempo.domain.ReliabilityLevel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReliabilityIconTest {
    @Test fun `los cuatro niveles dan cuatro dibujos distintos (H3)`() {
        val ids = ReliabilityLevel.entries.map(::reliabilityIconRes)
        assertEquals(4, ids.toSet().size)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val names = ids.map { context.resources.getResourceEntryName(it) }
        assertEquals(listOf("ic_rel_4", "ic_rel_3", "ic_rel_2", "ic_rel_1"), names)
        // Cada anillo enciende tantos trozos como dice su nombre: muy alta 4 … baja 1, el resto apagados.
        names.forEach { name ->
            val xml = File("src/main/res/drawable/$name.xml").readText()
            val lit = Regex("""strokeAlpha="1(\.0)?"""").findAll(xml).count()
            val dim = Regex("""strokeAlpha="0\.26"""").findAll(xml).count()
            assertEquals(name, name.last().digitToInt(), lit)
            assertEquals(name, 4 - lit, dim)
        }
    }

    @Test fun `TalkBack lee el nivel (H3)`() {
        assertEquals(
            listOf("Fiabilidad muy alta", "Fiabilidad alta", "Fiabilidad media", "Fiabilidad baja"),
            ReliabilityLevel.entries.map(::reliabilityLabel),
        )
    }
}
