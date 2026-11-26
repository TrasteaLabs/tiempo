package com.trastealabs.tiempo.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.trastealabs.tiempo.domain.SkyEffects
import com.trastealabs.tiempo.ui.scene.STILL_SECONDS
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * La lluvia contra el cristal de las tarjetas de la principal (009, P2: H8b), como `drawWet` de la maqueta: salpica en
 * el borde de arriba (2–4 gotitas que saltan y caen en 0,32 s) y deja gotas en el cristal que duran 5–13 s; de vez en
 * cuando una escurre hacia abajo con un rastro tenue. Todo es función del instante (no hay estado que avance por
 * fotogramas): cada gota tiene su hueco y cada salpicadura su ranura de tiempo, sacados de la semilla.
 */

/** El radio de las esquinas de las tarjetas (dp): las salpicaduras caen en el tramo recto del borde. */
private const val CORNER_DP = 22f

/** Una gota sobre el cristal, en dp desde la esquina de la tarjeta. */
data class Bead(val x: Float, val y: Float, val radius: Float, val alpha: Float)

/** El agua de una tarjeta de [w] × [h] dp. En tormenta, más gotas (hasta 26 frente a 16). */
internal class WetGlass(
    private val w: Float,
    private val h: Float,
    storm: Boolean,
    private val seed: Int,
) {
    /** Cuántas gotas caben a la vez (cuantos huecos hay). */
    val maxBeads: Int = min(if (storm) 26 else 16, floor(w * h / (if (storm) 2400f else 3800f)).toInt())

    /** Las gotas fijas del instante quieto ([STILL_SECONDS]): siempre las mismas, sin escurrir. */
    fun beads(): List<Bead> {
        val now = STILL_SECONDS
        val out = ArrayList<Bead>(maxBeads)
        for (i in 0 until maxBeads) {
            // Cada hueco tiene su ritmo: una gota que dura 5–13 s y luego un rato vacío, hasta su periodo.
            val period = 14 + 4 * unit(i, -1L, 0)
            val q = now + unit(i, -2L, 0) * period
            val gen = floor(q / period).toLong()
            val age = q - gen * period
            val life = 5 + 8 * unit(i, gen, 1)
            if (age >= life) continue
            val x = 6 + unit(i, gen, 2) * (w - 12)
            val y = 6 + unit(i, gen, 3) * (h - 12)
            val radius = 0.8f + unit(i, gen, 4) * unit(i, gen, 5) * 2.4f
            if (y > h - 4) continue
            val alpha = (min(1.0, age / 0.25) * min(1.0, (life - age) / 0.6)).toFloat().coerceIn(0f, 1f)
            out += Bead(x, y, radius, alpha)
        }
        return out
    }

    /** Un número de 0 a 1 que solo depende de la semilla, el hueco, la generación y la clave. */
    private fun unit(index: Int, gen: Long, key: Int): Float {
        var h = seed * -1640531535 + index * 374761393 + gen.toInt() * 668265263 + (gen ushr 32).toInt() * 1013904223 + key * 982451653
        h = (h xor (h ushr 15)) * 1274126177
        h = (h xor (h ushr 13)) * -1028477379
        h = h xor (h ushr 16)
        return ((h.toLong() and 0xFFFFFFFFL) / 4294967296.0).toFloat()
    }
}

private val BeadShadow = Color(6, 12, 30)

/** Pinta las gotas de [wet], recortadas a [clip]. */
private fun DrawScope.drawWet(wet: WetGlass, clip: Path) {
    val d = density
    val beads = wet.beads()
    if (beads.isEmpty()) return
    clipPath(clip) {
        for (b in beads) {
            val c = Offset(b.x * d, b.y * d)
            val r = b.radius * d
            drawCircle(Color.White, radius = r, center = c, alpha = 0.16f * b.alpha)
            // Su sombra, abajo, y su brillo, arriba a la izquierda.
            drawArc(
                BeadShadow, startAngle = 0f, sweepAngle = 180f, useCenter = true,
                topLeft = Offset(c.x - r * 0.8f, c.y + r * 0.35f - r * 0.8f), size = Size(r * 1.6f, r * 1.6f), alpha = 0.22f * b.alpha,
            )
            drawCircle(Color.White, radius = max(0.5f * d, r * 0.3f), center = Offset(c.x - r * 0.35f, c.y - r * 0.35f), alpha = 0.6f * b.alpha)
        }
    }
}

/**
 * La lluvia contra el cristal de una tarjeta (H8b): sin lluvia en [effects], nada. Gotas fijas (012 P6: ni gotas que
 * escurren ni salpicaduras, nada pide fotogramas). Va encima de lo de dentro de la tarjeta. [seed] distingue unas
 * tarjetas de otras.
 */
fun Modifier.rainOnGlass(effects: SkyEffects, seed: Int, shape: Shape = RoundedCornerShape(CORNER_DP.dp)): Modifier {
    if (effects.rain == 0f) return this
    val storm = effects.rain > 1f
    return this
        .drawWithCache {
            val wet = WetGlass(size.width / density, size.height / density, storm, seed)
            val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            onDrawWithContent {
                drawContent()
                drawWet(wet, clip)
            }
        }
        .graphicsLayer()
}

/** La lluvia de la principal para sus tarjetas ([rainOnGlass]); `null` fuera de ella (Ajustes, Sitios, el día). */
class GlassRain(val effects: SkyEffects)

val LocalGlassRain: ProvidableCompositionLocal<GlassRain?> = compositionLocalOf { null }

/** [rainOnGlass] con la lluvia de [LocalGlassRain]; sin ella, nada. */
@Composable
fun Modifier.rainOnGlass(seed: Int, shape: Shape = RoundedCornerShape(CORNER_DP.dp)): Modifier {
    val rain = LocalGlassRain.current ?: return this
    return rainOnGlass(rain.effects, seed, shape)
}
