package com.trastealabs.tiempo.ui.theme

import kotlin.math.roundToInt

/** Escala de la barra de rango (H6 del 002): de frío a calor; fuera de los extremos, el color del extremo. */
private val Stops = listOf(
    2.0 to 0xFF8CCBFF.toInt(),
    14.0 to 0xFFA8E6CF.toInt(),
    22.0 to 0xFFFFD27A.toInt(),
    32.0 to 0xFFFF8A5B.toInt(),
)

/** Color ARGB de [c] grados, interpolado entre los puntos de la escala. */
fun tempColor(c: Double): Int {
    if (c <= Stops.first().first) return Stops.first().second
    if (c >= Stops.last().first) return Stops.last().second
    val i = Stops.indexOfFirst { c < it.first }
    val (t0, c0) = Stops[i - 1]
    val (t1, c1) = Stops[i]
    val f = (c - t0) / (t1 - t0)
    fun channel(shift: Int): Int {
        val a = (c0 shr shift) and 0xFF
        val b = (c1 shr shift) and 0xFF
        return (a + (b - a) * f).roundToInt() shl shift
    }
    return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
}
