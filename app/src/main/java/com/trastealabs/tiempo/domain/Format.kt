package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
private val longDate = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM · HH:mm", Locale.forLanguageTag("es-ES"))

private fun clock(epochSeconds: Long, zone: ZoneId): String =
    hhmm.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

/**
 * La franja sin conexión. Si el dato es de hoy, solo la hora («Sin conexión · datos de las 09:12»); si no, también el
 * día, en la zona del sitio: «datos de ayer, 09:12», «datos del lunes, 09:12» o «datos del 28 de octubre, 09:12» (006).
 */
fun offlineLabel(fetchedAt: Long, zone: ZoneId, now: Long): String {
    val time = clock(fetchedAt, zone)
    return when (val day = dayWord(fetchedAt, now, zone)) {
        null -> "Sin conexión · datos de las $time"
        else -> "Sin conexión · datos $day, $time"
    }
}

/** «de ayer», «del lunes» (2–6 días) o «del 28 de octubre»; null si [fetchedAt] es del mismo día local que [now]. */
internal fun dayWord(fetchedAt: Long, now: Long, zone: ZoneId): String? {
    val then = Instant.ofEpochSecond(fetchedAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(then, today)
    return when {
        days <= 0 -> null
        days == 1L -> "de ayer"
        days <= 6 -> "del " + weekday.format(then)
        else -> "del " + dayMonth.format(then)
    }
}

private val weekday = DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("es-ES"))
private val dayMonth = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-ES"))

/** La cifra que se ve en pantalla (19,6 → 20). La usan [sceneOf] y todos los «°». */
fun shownDegrees(c: Double): Int = c.roundToInt()

/** Fecha y hora del dato bajo el sitio: "Martes, 3 de noviembre · 15:02". */
fun dateTimeLabel(fetchedAt: Long, zone: ZoneId): String =
    longDate.format(Instant.ofEpochSecond(fetchedAt).atZone(zone)).replaceFirstChar { it.uppercase() }

/** "15:10" en la zona del sitio (postales y edad del dato, 006). */
internal fun clockAt(epochSeconds: Long, zone: ZoneId): String = clock(epochSeconds, zone)
