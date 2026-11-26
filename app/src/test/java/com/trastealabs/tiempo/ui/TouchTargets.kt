package com.trastealabs.tiempo.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import org.junit.Assert.assertTrue

/**
 * Cristal 1.4 §5: todo lo que se toca tiene al menos 48 dp de zona. Mide la zona táctil (la que Compose amplía con
 * `minimumInteractiveComponentSize`), no solo el dibujo.
 */
fun SemanticsNodeInteraction.assertTouchAtLeast48(what: String = "") {
    val node = fetchSemanticsNode()
    val density = node.layoutInfo.density.density
    // El hueco que ocupa el nodo en la disposición (con `minimumInteractiveComponentSize`, los 48 dp que reserva), no
    // `touchBoundsInRoot`: ese lo amplía Compose a 48 dp en todo lo pulsable y no vigilaría nada.
    val w = node.layoutInfo.width / density
    val h = node.layoutInfo.height / density
    assertTrue("$what: zona táctil de ${w}×${h} dp, menos de 48", w >= 47.99f && h >= 47.99f)
}
