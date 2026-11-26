package com.trastealabs.tiempo.ui.scene

/** El generador `rng` de la maqueta (mulberry32), para sacar las mismas partículas que ella. */
internal class Mulberry32(private var seed: Int) {
    fun next(): Float {
        seed += 0x6D2B79F5
        var t = (seed xor (seed ushr 15)) * (1 or seed)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return (((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0).toFloat()
    }

    /** Un valor entre los dos extremos de [range]. */
    fun between(range: ClosedFloatingPointRange<Float>): Float = range.start + next() * (range.endInclusive - range.start)
}
