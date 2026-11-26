package com.trastealabs.tiempo.domain

/** La probabilidad de lluvia que merece la pena enseñar: la misma de la lista de días (`DailyList`). */
const val RAIN_SHOWN_FROM = 20

/**
 * La fila de lluvia de la gráfica de horas (019, Cristal 1.4 §4: sin «0 %» repetido). [shown]: para cada hora, si lleva
 * su probabilidad (20 % o más). [dry]: ninguna hora llega al 20 % ni tiene barra de lluvia, y la fila se cambia por la
 * frase «Sin lluvia…».
 */
data class HourRainRow(val shown: List<Boolean>, val dry: Boolean)

/** Lo mínimo para que una hora tenga barra (y sus milímetros): lo mismo que dibuja `HourlyChart`. */
const val RAIN_BAR_FROM_MM = 0.05

fun hourRainRow(hours: List<HourPoint>): HourRainRow {
    val shown = hours.map { (it.precipProbability ?: 0) >= RAIN_SHOWN_FROM }
    val dry = shown.none { it } && hours.none { it.precipMm >= RAIN_BAR_FROM_MM }
    return HourRainRow(shown, dry)
}
