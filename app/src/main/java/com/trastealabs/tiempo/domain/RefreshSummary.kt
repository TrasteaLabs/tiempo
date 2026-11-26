package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.data.RefreshLogEntry
import com.trastealabs.tiempo.data.RefreshOutcome

/** Lo que enseña la sección «Refrescos» de Ajustes (015 P2, H11). */
data class RefreshSummary(
    /** La línea más reciente, buena o no. */
    val last: RefreshLogEntry?,
    /** La mayor edad que llegó a tener el dato en las últimas 24 h; null si no hay ningún refresco bueno registrado. */
    val worstGapSeconds: Long?,
    /** Las líneas de las últimas 24 h, la más reciente primero (con los avisos de la mañana, que se enseñan en la lista). */
    val last24h: List<RefreshLogEntry>,
)

private const val WINDOW_SECONDS = 24 * 3600L

/**
 * Las reglas del resumen (H11, H12), todo en epoch para que la medianoche y el cambio de hora no cambien ninguna cifra;
 * la zona solo se usa al pintar las horas.
 *
 * Peor hueco = con los refrescos buenos ([RefreshOutcome.OK]) ordenados, el mayor `g[i+1] − g[i]` para cada `g[i+1]`
 * dentro de la ventana (el anterior puede caer antes de ella) y `now − último bueno`. Con uno solo, `now − ese`.
 */
fun refreshSummary(entries: List<RefreshLogEntry>, now: Long): RefreshSummary {
    val from = now - WINDOW_SECONDS
    val sorted = entries.sortedBy { it.at }
    // Una línea de aviso de la mañana (018 P2) no es un refresco: no es «el último», ni cuenta para el hueco.
    val refreshes = sorted.filter { it.morning == null }
    val good = refreshes.filter { it.outcome == RefreshOutcome.OK && it.at <= now }.map { it.at }
    val worst = if (good.isEmpty()) {
        null
    } else {
        val between = good.zipWithNext().filter { (_, next) -> next >= from }.map { (prev, next) -> next - prev }
        (between + (now - good.last())).max()
    }
    return RefreshSummary(
        last = refreshes.lastOrNull(),
        worstGapSeconds = worst,
        last24h = sorted.filter { it.at >= from }.asReversed(),
    )
}

/** «41 min», «2 h 05 min», «3 h»: lo que dura un hueco, en minutos enteros hacia abajo; menos de uno, «menos de 1 min». */
fun gapText(seconds: Long): String {
    val minutes = (seconds / 60).coerceAtLeast(0)
    if (minutes == 0L) return "menos de 1 min"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h ${m.toString().padStart(2, '0')} min"
    }
}

/** «344 KB», con punto de miles: lo que bajó AEMET, redondeado al KB y como poco 1 KB si bajó algo. */
fun kbText(bytes: Long): String = "${groupedThousands(((bytes + 512) / 1024).coerceAtLeast(1))} KB"
