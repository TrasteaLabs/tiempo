package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


/** Vigente ahora o empieza en ≤24 h; el de nivel más alto y, a igual nivel, el que empieza antes (H14). El del widget. */
fun activeWarning(warnings: List<Warning>, now: Long): Warning? = activeWarnings(warnings, now).firstOrNull()

/** Rayos desde el punto (lon = x, lat = y). El polígono puede venir cerrado o abierto. */
fun pointInPolygon(p: LatLon, polygon: List<LatLon>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if ((a.lat > p.lat) != (b.lat > p.lat) &&
            p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

fun levelName(level: WarningLevel): String = when (level) {
    WarningLevel.YELLOW -> "amarillo"
    WarningLevel.ORANGE -> "naranja"
    WarningLevel.RED -> "rojo"
}

/** "Aviso naranja · tormentas" */
fun warningTitle(w: Warning): String = "Aviso ${levelName(w.level)} · ${w.phenomenon}"

/** "hoy 16:00–22:00", "mañana 06:00–12:00", "hoy 22:00–mañana 08:00" */
fun warningWhenText(w: Warning, now: Long, zone: ZoneId): String {
    val today = local(now, zone).toLocalDate()
    val start = local(w.onset, zone)
    val end = local(w.expires, zone)
    val endText = if (end.toLocalDate() == start.toLocalDate()) hhmm(end) else "${relativeDay(end.toLocalDate(), today)} ${hhmm(end)}"
    return "${relativeDay(start.toLocalDate(), today)} ${hhmm(start)}–$endText"
}

/** "martes 10 jun, de 16:00 a 22:00" o "del martes 10 jun a las 22:00 al miércoles 11 jun a las 08:00" */
fun warningPeriodText(w: Warning, zone: ZoneId): String {
    val start = local(w.onset, zone)
    val end = local(w.expires, zone)
    return if (start.toLocalDate() == end.toLocalDate()) {
        "${longDate(start)}, de ${hhmm(start)} a ${hhmm(end)}"
    } else {
        "del ${longDate(start)} a las ${hhmm(start)} al ${longDate(end)} a las ${hhmm(end)}"
    }
}

/** "Fuente: AEMET · emitido hoy a las 09:30" */
fun warningSourceText(w: Warning, now: Long, zone: ZoneId): String {
    val sent = local(w.sent, zone)
    val day = when (ChronoUnit.DAYS.between(local(now, zone).toLocalDate(), sent.toLocalDate())) {
        0L -> "hoy"
        -1L -> "ayer"
        else -> "el ${sent.dayOfMonth} ${MONTHS[sent.monthValue - 1]}"
    }
    return "Fuente: AEMET · emitido $day a las ${hhmm(sent)}"
}

// Nombres fijos y no los del Locale: así salen igual en el móvil y en los tests.
private val WEEKDAYS = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

private fun local(epochSeconds: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochSecond(epochSeconds).atZone(zone)

private fun hhmm(t: ZonedDateTime): String = HHMM.format(t)

private fun longDate(t: ZonedDateTime): String =
    "${WEEKDAYS[t.dayOfWeek.value - 1]} ${t.dayOfMonth} ${MONTHS[t.monthValue - 1]}"

private fun relativeDay(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, date)) {
    0L -> "hoy"
    1L -> "mañana"
    -1L -> "ayer"
    else -> WEEKDAYS[date.dayOfWeek.value - 1]
}
