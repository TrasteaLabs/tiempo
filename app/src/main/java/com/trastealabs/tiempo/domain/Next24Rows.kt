package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

// Hojas de viento y humedad de la principal (H6 de la 004). Acotadas el 2026-11-04, a petición del usuario («el
// desglose hora por hora es demasiado»): el viento en tramos de 3 horas en punto y la humedad por partes del día.

/** Una fila de la hoja: un tramo de viento, una parte del día de humedad, o la raya del día nuevo. */
sealed interface SheetRow {
    /**
     * Un tramo de 3 horas («Ahora» el primero, luego «18–21»…), con el viento y la racha más fuertes, en la cifra que
     * se ve, y de dónde sopla en la hora del viento más fuerte. null si ninguna hora del tramo trae ese dato.
     */
    data class Wind(val label: String, val windKmh: Int?, val gustKmh: Int?, val fromDeg: Int?) : SheetRow

    /** Una parte del día («Madrugada», «Mañana», «Tarde», «Noche») con su humedad mínima y máxima y el rocío más alto. */
    data class Humidity(val label: String, val minPct: Int, val maxPct: Int, val dewC: Double?) : SheetRow

    /** "MIÉRCOLES, 4 DE NOVIEMBRE". */
    data class DayBreak(val label: String) : SheetRow
}


/** Tramos del viento: 8 de 3 horas (unas 24 h), en punto (00–03, 03–06…), así ninguno cruza la medianoche. */
private const val WIND_BLOCK_H = 3
private const val WIND_BLOCKS = 8

/** Partes del día de la humedad: 4 de 6 horas. */
private const val PART_H = 6
private const val PARTS = 4
private val partNames = listOf("Madrugada", "Mañana", "Tarde", "Noche")

/**
 * Las horas desde la de [now] (como `next24Hours`), agrupadas por fecha local y tramo de [size] horas en punto, en
 * orden, hasta [count] grupos. El primero empieza en la hora de ahora, así que puede ser más corto. El día que se
 * atrasa la hora, la hora repetida cae en su mismo tramo.
 */
private fun groups(hourly: List<HourPoint>, now: Long, zone: ZoneId, size: Int, count: Int): List<Pair<Pair<LocalDate, Int>, List<HourPoint>>> {
    val start = hourly.indexOfLast { it.time <= now }.coerceAtLeast(0)
    val result = mutableListOf<Pair<Pair<LocalDate, Int>, MutableList<HourPoint>>>()
    for (point in hourly.drop(start)) {
        val local = Instant.ofEpochSecond(point.time).atZone(zone)
        val key = local.toLocalDate() to local.hour / size
        if (result.lastOrNull()?.first != key) {
            if (result.size == count) break
            result.add(key to mutableListOf())
        }
        result.last().second.add(point)
    }
    return result
}

/** La raya va delante de la primera fila de cada fecha nueva (no delante de la primera fila). */
private fun <T> MutableList<SheetRow>.addWithBreaks(groups: List<Pair<Pair<LocalDate, Int>, T>>, row: (Int, Pair<LocalDate, Int>, T) -> SheetRow?) {
    var lastDate: LocalDate? = null
    groups.forEachIndexed { i, (key, value) ->
        val r = row(i, key, value) ?: return@forEachIndexed
        if (lastDate != null && key.first != lastDate) add(SheetRow.DayBreak(longDayLabel(key.first.toString())))
        lastDate = key.first
        add(r)
    }
}

/** El viento de la hoja: tramos de 3 horas desde el de [now], con la raya del día nuevo. */
fun windRows(hourly: List<HourPoint>, now: Long, zone: ZoneId): List<SheetRow> = buildList {
    addWithBreaks(groups(hourly, now, zone, WIND_BLOCK_H, WIND_BLOCKS)) { i, (_, block), hours ->
        val strongest = hours.filter { it.windKmh != null }.maxByOrNull { it.windKmh!! }
        val from = block * WIND_BLOCK_H
        SheetRow.Wind(
            label = if (i == 0) "Ahora" else "%02d–%02d".format(from, from + WIND_BLOCK_H),
            windKmh = strongest?.windKmh?.roundToInt(),
            gustKmh = hours.mapNotNull { it.windGustsKmh }.maxOrNull()?.roundToInt(),
            fromDeg = strongest?.windDirectionDeg,
        )
    }
}

/** La humedad de la hoja: 4 partes del día desde la de [now]. Una parte sin ninguna humedad no sale. */
fun humidityRows(hourly: List<HourPoint>, now: Long, zone: ZoneId): List<SheetRow> = buildList {
    addWithBreaks(groups(hourly, now, zone, PART_H, PARTS)) { _, (_, part), hours ->
        val values = hours.mapNotNull { it.humidityPct }
        if (values.isEmpty()) return@addWithBreaks null
        SheetRow.Humidity(partNames[part], values.min(), values.max(), hours.mapNotNull { it.dewPointC }.maxOrNull())
    }
}

/** La racha (o, sin ella, el viento) más fuerte de los tramos, hacia arriba a la decena; como poco, 10. */
fun windScaleKmh(rows: List<SheetRow>): Int {
    val top = rows.filterIsInstance<SheetRow.Wind>().mapNotNull { it.gustKmh ?: it.windKmh }.maxOrNull() ?: 0
    return maxOf(10, (top + 9) / 10 * 10)
}
