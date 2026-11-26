package com.trastealabs.tiempo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * ¿Se ve la luna desde el hemisferio sur? Lo pone la página del día (007) para su `DayLine`; por defecto, el norte.
 */
val LocalMoonSouth = staticCompositionLocalOf { false }

private val MoonLit = Color(0xFFF4F2FF)
private val MoonDark = Color.White.copy(alpha = 0.14f)
private val MoonEdge = Color.White.copy(alpha = 0.32f)
private val MoonHalo = Color(220, 225, 255).copy(alpha = 0.35f)

/**
 * La luna en su fase ([phase]: 0 = nueva, 0,5 = llena), con el `moonPath` de la maqueta: lienzo de 24×24 escalado a
 * [size] (18 dp como mínimo). El `contentDescription` lo pone quien lo usa. Con [south] (007), en espejo, como se ve
 * en el hemisferio sur (el creciente, con la luz a la izquierda).
 */
@Composable
fun MoonIcon(phase: Double, size: Dp, modifier: Modifier = Modifier, south: Boolean = false) {
    Canvas(modifier.size(maxOf(size, 18.dp))) {
        scale(this.size.width / 24f, pivot = Offset.Zero) { drawMoon(phase, south) }
    }
}

/** Dibuja la luna en un lienzo de 24×24 (ya escalado); con [south], en espejo (la fase al revés: 1 − fase). */
internal fun DrawScope.drawMoon(phase: Double, south: Boolean = false) {
    val shown = if (south) 1 - phase else phase
    val c = Offset(12f, 12f)
    drawCircle(Brush.radialGradient(listOf(MoonHalo, Color.Transparent), center = c, radius = 12.5f), radius = 12.5f, center = c)
    drawCircle(MoonDark, radius = 10f, center = c)
    drawCircle(MoonEdge, radius = 10f, center = c, style = Stroke(0.5f))
    moonPath(shown)?.let { drawPath(it, MoonLit) } ?: run {
        if ((1 - cos(2 * PI * shown)) / 2 > 0.99) drawCircle(MoonLit, radius = 10f, center = c)
    }
}

/** La parte iluminada; `null` si está casi nueva (solo el disco) o casi llena (el disco entero, lo pinta quien llama). */
internal fun moonPath(phase: Double): Path? {
    val k = cos(2 * PI * phase)
    val lit = (1 - k) / 2
    if (lit < 0.01 || lit > 0.99) return null
    val rx = (10 * abs(k)).toFloat()
    val circle = Rect(2f, 2f, 22f, 22f)
    val ellipse = Rect(12f - rx, 2f, 12f + rx, 22f)
    return Path().apply {
        moveTo(12f, 2f)
        if (phase < 0.5) {
            arcTo(circle, -90f, 180f, false)   // borde derecho
            arcTo(ellipse, 90f, if (phase > 0.25) 180f else -180f, false)
        } else {
            arcTo(circle, -90f, -180f, false)  // borde izquierdo
            arcTo(ellipse, 90f, if (phase < 0.75) -180f else 180f, false)
        }
        close()
    }
}
