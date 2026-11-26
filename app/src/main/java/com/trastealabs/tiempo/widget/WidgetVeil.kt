package com.trastealabs.tiempo.widget

import android.graphics.RectF
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

/**
 * El velo del widget (010, P3): el `solve()` de la maqueta, en Kotlin puro (con 5,0 en vez de 4,6, ver [VEIL_TARGET]). El velo de tinta se mide al pintar, con los
 * rectángulos reales de cada texto sobre los píxeles de la foto, y es el más suave con el que el 1 % peor de los puntos
 * de texto llega a 4,8:1.
 */
enum class VeilSide { LEFT, RIGHT, TOP, BOTTOM, ALL }

/** Una capa del velo: [fullDp] a pleno desde el lado, y se desvanece en [fadeDp]; su alfa es `k × weight` (tope [VEIL_MAX]). */
data class VeilLayer(val side: VeilSide, val fullDp: Float, val fadeDp: Float, val weight: Float = 1f)

/** Las capas de la maqueta (variante A, `L` de `widget()`). */
val VEIL_LAYERS: Map<WidgetLayout, List<VeilLayer>> = mapOf(
    WidgetLayout.LARGE to listOf(VeilLayer(VeilSide.TOP, 86f, 10f, 0.8f), VeilLayer(VeilSide.BOTTOM, 56f, 30f)),
    WidgetLayout.MEDIUM to listOf(VeilLayer(VeilSide.ALL, 0f, 0f)),
    WidgetLayout.SMALL to listOf(VeilLayer(VeilSide.ALL, 0f, 0f)),
)

/** Un texto a medir: su caja en píxeles del bitmap y la opacidad del texto blanco. */
data class TextSample(val rect: RectF, val alpha: Float)

/** [k]: la fuerza del velo (0 a 1); [minContrast]: el 1 % peor contraste con ese velo (99 si no hay textos). */
data class VeilSolution(val k: Float, val minContrast: Float)

/** Tinta del velo, `rgb(10,16,40)`. */
const val VEIL_R = 10
const val VEIL_G = 16
const val VEIL_B = 40

/** Contraste que busca el velo: 5,0 para que, medido en capturas del OPPO (algo más flojas que el cálculo), llegue a 4,5. */
const val VEIL_TARGET = 5.0f

/** Alfa máximo de una capa. */
const val VEIL_MAX = 0.92f

private const val STEPS = 14

/** Luminancia relativa (WCAG) de un color 0–255. */
private fun lum(r: Float, g: Float, b: Float): Float {
    fun c(v: Float): Float {
        val s = v / 255f
        return if (s <= 0.03928f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * c(r) + 0.7152f * c(g) + 0.0722f * c(b)
}

/** Contraste de un texto blanco con opacidad [a] sobre el color (r, g, b). */
fun ratioAt(r: Float, g: Float, b: Float, a: Float): Float {
    val lb = lum(r, g, b)
    val lt = lum(255f * a + r * (1 - a), 255f * a + g * (1 - a), 255f * a + b * (1 - a))
    return (maxOf(lb, lt) + 0.05f) / (minOf(lb, lt) + 0.05f)
}

/**
 * La rampa del fundido (017): 1 en `t = 0` y 0 en `t = 1`, suavizada (`smoothstep`) para que no haya pendiente brusca en
 * sus dos bordes, que es lo que se veía como una línea sobre la foto con la rampa lineal.
 */
fun veilFade(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return 1f - c * c * (3f - 2f * c)
}

/** El alfa de tinta que dan todas las capas en (x, y) dp de un widget de [w] × [h] dp, con fuerza [k] (`layerAlpha`). */
fun layerAlpha(layers: List<VeilLayer>, x: Float, y: Float, w: Float, h: Float, k: Float): Float {
    var keep = 1f
    for (l in layers) {
        val dist = when (l.side) {
            VeilSide.LEFT -> x
            VeilSide.RIGHT -> w - x
            VeilSide.TOP -> y
            VeilSide.BOTTOM -> h - y
            VeilSide.ALL -> 0f
        }
        val a = when {
            l.side == VeilSide.ALL -> 1f
            dist <= l.fullDp -> 1f
            dist >= l.fullDp + l.fadeDp -> 0f
            else -> veilFade((dist - l.fullDp) / l.fadeDp)
        }
        keep *= 1f - min(VEIL_MAX, k * l.weight) * a
    }
    return 1f - keep
}

/** Un punto de texto: el color de la foto debajo, la opacidad del texto y dónde cae (dp). */
private class Spot(val r: Float, val g: Float, val b: Float, val a: Float, val xDp: Float, val yDp: Float)

/** Los puntos de muestreo de [texts] (cada 1,5 dp, del 18 al 82 % de la altura), con el color de [argb]. */
private fun spots(argb: IntArray, width: Int, height: Int, density: Float, texts: List<TextSample>): List<Spot> {
    val out = ArrayList<Spot>()
    val step = 1.5f * density
    for (t in texts) {
        val r = t.rect
        if (r.width() <= 0f) continue
        var y = r.top + r.height() * 0.18f
        while (y < r.top + r.height() * 0.82f) {
            var x = r.left + density
            while (x < r.right - density) {
                val xi = x.toInt()
                val yi = y.toInt()
                if (xi in 0 until width && yi in 0 until height) {
                    val p = argb[yi * width + xi]
                    out.add(Spot(((p shr 16) and 0xFF).toFloat(), ((p shr 8) and 0xFF).toFloat(), (p and 0xFF).toFloat(), t.alpha, x / density, y / density))
                }
                x += step
            }
            y += step
        }
    }
    return out
}

/** El percentil 1 (el 1 % peor) de [ratios]. */
private fun worst(ratios: FloatArray): Float {
    if (ratios.isEmpty()) return 99f
    ratios.sort()
    return ratios[floor(ratios.size * 0.01).toInt()]
}

/** El menor valor de `k` en 0..1 con el que [f] llega a [VEIL_TARGET] (14 pasos); 0 si ya llega sin velo. */
private fun search(f: (Float) -> Float): Float {
    if (f(0f) >= VEIL_TARGET) return 0f
    var lo = 0f
    var hi = 1f
    repeat(STEPS) {
        val m = (lo + hi) / 2
        if (f(m) >= VEIL_TARGET) hi = m else lo = m
    }
    return hi
}

/**
 * Mide el velo: [argb] son los píxeles de la foto ya dibujada (sin velo, [width] × [height] px), [texts] las cajas de los
 * textos en esos píxeles y [layers] las capas del tamaño. Devuelve la fuerza `k` y el contraste que queda.
 */
fun solveVeil(
    argb: IntArray, width: Int, height: Int, density: Float, texts: List<TextSample>, layers: List<VeilLayer>,
): VeilSolution {
    val spots = spots(argb, width, height, density, texts)
    if (spots.isEmpty() || layers.isEmpty()) return VeilSolution(0f, 99f)
    val wDp = width / density
    val hDp = height / density
    fun contrast(k: Float): Float {
        val ratios = FloatArray(spots.size)
        for ((i, s) in spots.withIndex()) {
            val a = layerAlpha(layers, s.xDp, s.yDp, wDp, hDp, k)
            ratios[i] = ratioAt(s.r * (1 - a) + VEIL_R * a, s.g * (1 - a) + VEIL_G * a, s.b * (1 - a) + VEIL_B * a, s.a)
        }
        return worst(ratios)
    }
    val k = search(::contrast)
    return VeilSolution(k, contrast(k))
}

/**
 * El alfa de la píldora oscura de «Abre la app» (antes de abrir la app): la de la maqueta ([base], 0,42) o la que haga
 * falta para que su texto blanco llegue a [VEIL_TARGET] sobre la foto bajo [rect] (px de [argb]).
 */
fun solvePill(argb: IntArray, width: Int, height: Int, density: Float, rect: RectF, base: Float): VeilSolution {
    val spots = spots(argb, width, height, density, listOf(TextSample(rect, 1f)))
    if (spots.isEmpty()) return VeilSolution(base, 99f)
    fun contrast(alpha: Float): Float {
        val ratios = FloatArray(spots.size) { i ->
            val s = spots[i]
            ratioAt(s.r * (1 - alpha) + VEIL_R * alpha, s.g * (1 - alpha) + VEIL_G * alpha, s.b * (1 - alpha) + VEIL_B * alpha, 1f)
        }
        return worst(ratios)
    }
    if (contrast(base) >= VEIL_TARGET) return VeilSolution(base, contrast(base))
    var lo = base
    var hi = VEIL_MAX
    repeat(STEPS) {
        val m = (lo + hi) / 2
        if (contrast(m) >= VEIL_TARGET) hi = m else lo = m
    }
    return VeilSolution(hi, contrast(hi))
}
