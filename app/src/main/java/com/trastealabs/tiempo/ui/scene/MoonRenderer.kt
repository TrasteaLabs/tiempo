package com.trastealabs.tiempo.ui.scene

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * La luna de foto (007, H12): el mapa de la NASA (toda la luna en proyección cilíndrica) proyectado sobre la esfera tal
 * como se ve desde la Tierra, e iluminado según la fase. Puro, con los píxeles en `IntArray` (ARGB), para probarlo en la
 * JVM con un mapa inventado. Porta `moonTexture` y `moonLit` de la maqueta HTML del encargo 007.
 */

/** Lado, en px, de la luna calculada: con el mapa de 1024 × 512, más no da más detalle. */
const val MOON_PX = 512

/** Radio del disco dentro del cuadrado de [size] px: 4 px de margen para el borde suavizado y el relieve. */
fun moonRadius(size: Int): Float = size / 2f - 4f

/** La cara visible, sin luz: centro en latitud 0° y longitud 0°. Fuera del disco, transparente. */
class MoonFace(val size: Int, val argb: IntArray)

/**
 * Quién recorre las filas: de una en una ([SequentialRows], tests) o repartidas entre los núcleos ([ParallelRows], el
 * móvil). Cada fila solo escribe la suya, así que el resultado es el mismo.
 */
fun interface RowRunner {
    fun run(rows: Int, row: (Int) -> Unit)
}

val SequentialRows = RowRunner { rows, row -> for (y in 0 until rows) row(y) }

val ParallelRows = RowRunner { rows, row -> java.util.stream.IntStream.range(0, rows).parallel().forEach { row(it) } }

/** Cada punto del disco toma el color del mapa ([map], [mapWidth] × [mapHeight]) en su latitud y longitud. */
fun moonFace(map: IntArray, mapWidth: Int, mapHeight: Int, size: Int = MOON_PX, rows: RowRunner = SequentialRows): MoonFace {
    val n = size
    val c = n / 2.0
    val r = moonRadius(size).toDouble()
    val out = IntArray(n * n)
    rows.run(n) { y -> for (x in 0 until n) {
        val nx = (x + 0.5 - c) / r
        val ny = (y + 0.5 - c) / r
        val q = nx * nx + ny * ny
        if (q > 1.02) continue
        val nz = sqrt(maxOf(0.0, 1 - q))
        val lat = asin((-ny).coerceIn(-1.0, 1.0))
        val lon = atan2(nx, nz)
        val u = ((lon / (2 * PI) + 0.5) * mapWidth - 0.5).coerceIn(0.0, mapWidth - 1.001)
        val v = ((0.5 - lat / PI) * mapHeight - 0.5).coerceIn(0.0, mapHeight - 1.001)
        val u0 = u.toInt()
        val v0 = v.toInt()
        val fu = u - u0
        val fv = v - v0
        val a = map[v0 * mapWidth + u0]
        val b = map[v0 * mapWidth + u0 + 1]
        val cc = map[(v0 + 1) * mapWidth + u0]
        val d = map[(v0 + 1) * mapWidth + u0 + 1]
        // Bilineal, canal a canal, sin crear nada por píxel (cuenta en el móvil).
        val w00 = (1 - fu) * (1 - fv)
        val w10 = fu * (1 - fv)
        val w01 = (1 - fu) * fv
        val w11 = fu * fv
        val red = bilinear(a, b, cc, d, 16, w00, w10, w01, w11)
        val green = bilinear(a, b, cc, d, 8, w00, w10, w01, w11)
        val blue = bilinear(a, b, cc, d, 0, w00, w10, w01, w11)
        out[y * n + x] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    } }
    return MoonFace(n, out)
}

// El tinte de la luz del sol y el de la luz cenicienta (`MOON_TINT` y `EARTH_TINT` de la maqueta).
private const val MOON_TINT_R = 0.99
private const val MOON_TINT_G = 1.0
private const val MOON_TINT_B = 1.03
private const val EARTH_TINT_R = 0.75
private const val EARTH_TINT_G = 0.85
private const val EARTH_TINT_B = 1.15

/** Luz cenicienta: la parte en sombra deja ver un 3,5 % (y tapa casi del todo las estrellas). */
private const val EARTHSHINE = 0.035

/**
 * La luna iluminada en [phase] (0 nueva, 0,25 cuarto creciente, 0,5 llena), ARGB sin premultiplicar. Fuera del disco,
 * transparente; la sombra, al 3,5 %. Con [south], girada 180°, como se ve en el hemisferio sur.
 */
fun litMoon(face: MoonFace, phase: Double, south: Boolean, rows: RowRunner = SequentialRows): IntArray {
    val n = face.size
    val a = face.argb
    val c = n / 2.0
    val r = moonRadius(n).toDouble()
    val th = 2 * PI * phase
    val flip = if (south) -1 else 1
    val sx = flip * sin(th)
    val sz = -cos(th)
    val e = EARTHSHINE
    val out = IntArray(n * n)
    rows.run(n) { y -> for (x in 0 until n) {
        val nx = (x + 0.5 - c) / r
        val ny = (y + 0.5 - c) / r
        val q = nx * nx + ny * ny
        if (q >= 1.01) continue
        val edge = ((1 - sqrt(q)) * r + 0.5).coerceIn(0.0, 1.0)
        val nz = sqrt(maxOf(0.0, 1 - q))
        val sxIdx = if (south) n - 1 - x else x
        val syIdx = if (south) n - 1 - y else y
        val i = syIdx * n + sxIdx
        // Relieve: el brillo del mapa inclina un poco el suelo, y el borde de la sombra sale rugoso.
        val right = if (sxIdx + 3 < n) redOf(a[i + 3]) else redOf(a[i])
        val left = if (sxIdx - 3 >= 0) redOf(a[i - 3]) else redOf(a[i])
        val gx = (right - left) / 255.0 * flip
        val mu0 = (nx + gx * 0.18) * sx + nz * sz
        val ls = if (mu0 <= 0) 0.0 else min(1.0, 2 * mu0 / (mu0 + nz + 1e-4))
        val light = smooth((mu0 + 0.02) / 0.11) * (0.55 + 0.45 * (if (ls > 0) ls.pow(0.7) else 0.0))
        val al = light + (1 - light) * e
        val p = a[i]
        // Cada canal: la luz del sol (algo más blanca) y, en la sombra, la de la Tierra (azulada), sobre el alfa.
        val sun = 1.16 * light / al
        val earth = 0.75 * e * (1 - light) / al
        val red = channel((p ushr 16) and 0xFF, sun * MOON_TINT_R + earth * EARTH_TINT_R)
        val green = channel((p ushr 8) and 0xFF, sun * MOON_TINT_G + earth * EARTH_TINT_G)
        val blue = channel(p and 0xFF, sun * MOON_TINT_B + earth * EARTH_TINT_B)
        val alpha = (255 * al * edge).toInt().coerceIn(0, 255)
        out[y * n + x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    } }
    return out
}

/** Parte iluminada del disco (0–1). */
fun litFraction(phase: Double): Double = (1 - cos(2 * PI * phase)) / 2

private fun redOf(p: Int) = (p ushr 16) and 0xFF

@Suppress("NOTHING_TO_INLINE")
private inline fun bilinear(a: Int, b: Int, c: Int, d: Int, shift: Int, w00: Double, w10: Double, w01: Double, w11: Double): Int {
    val v = ((a ushr shift) and 0xFF) * w00 + ((b ushr shift) and 0xFF) * w10 +
        ((c ushr shift) and 0xFF) * w01 + ((d ushr shift) and 0xFF) * w11
    return (v + 0.5).toInt().coerceIn(0, 255)
}

@Suppress("NOTHING_TO_INLINE")
private inline fun channel(value: Int, k: Double): Int = (value * k + 0.5).toInt().coerceIn(0, 255)

private fun smooth(x: Double): Double {
    val t = x.coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}
