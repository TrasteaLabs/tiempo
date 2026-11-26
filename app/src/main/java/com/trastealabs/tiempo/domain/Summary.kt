package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

// 009, H12: el Resumen de la principal. Reglas fijas sobre la previsión guardada (sin IA); todo con `now` y la zona
// del dato, nunca con el reloj del móvil, para poder probar medianoche, cambio de hora y datos viejos.

enum class SummaryIcon { RAIN, RAIN_STOP, TEMP_UP, TEMP_DOWN, GUST, FOG, FROST, DRY }

data class SummaryLine(val icon: SummaryIcon, val text: String)

/** Una hora con lluvia (o nieve): desde 0,2 mm. */
internal const val WET_MM = 0.2

/** Desde 1 mm, la frase lleva la cantidad del episodio. */
private const val AMOUNT_MM = 1.0

/** Rachas que se cuentan. */
internal const val GUST_KMH = 50.0

/** Un día con lluvia, para «Lluvia el jueves». */
private const val DAY_RAIN_MM = 1.0

/** Cambio de la máxima de mañana que se cuenta, en grados de pantalla. */
private const val TEMP_STEP = 3

private const val HOUR = 3600L
private const val MAX_LINES = 3

/** «Esta noche» acaba a las 10:00 y, si es de día, empieza a las 18:00. */
private const val NIGHT_START = 18
private const val NIGHT_END = 10

private val HourPoint.wet: Boolean get() = precipMm >= WET_MM
private val HourPoint.foggy: Boolean get() = weatherCode == 45 || weatherCode == 48

/**
 * Como mucho 3, por orden de importancia: lluvia en 24 h, rachas, helada, niebla, máxima de mañana y lluvia más
 * adelante. Solo cuentan las horas desde la que está en curso. Sin ninguna, «Sin lluvia en los próximos 10 días»; sin
 * horas desde ahora (datos muy viejos), nada.
 */
fun summaryLines(snapshot: WeatherSnapshot, now: Long, zone: ZoneId): List<SummaryLine> {
    val window = snapshot.hourly.filter { it.time + HOUR > now }.take(24)
    if (window.isEmpty()) return emptyList()
    val rain = rainLine(window, now, zone)
    val lines = listOfNotNull(
        rain,
        gustLine(window, now, zone),
        frostLine(window, now, zone),
        fogLine(window, now, zone),
        tomorrowMaxLine(snapshot, now, zone),
        if (rain == null) laterRainLine(snapshot, now, zone) else null,
    )
    return lines.take(MAX_LINES).ifEmpty { listOf(SummaryLine(SummaryIcon.DRY, dryText(daysFrom(snapshot, now).size))) }
}

/** Con datos viejos quedan menos de 10 días de previsión: solo se promete lo que se sabe. */
private fun dryText(days: Int): String = when {
    days >= 10 -> "Sin lluvia en los próximos 10 días"
    days >= 2 -> "Sin lluvia en los próximos $days días"
    else -> "Sin lluvia hasta el final de la previsión"
}

/** Regla 1. Llueve ahora: cuándo para; si no, cuándo empieza. Con la cantidad del episodio desde ahora. */
private fun rainLine(window: List<HourPoint>, now: Long, zone: ZoneId): SummaryLine? {
    val current = window.first()
    if (current.wet) {
        val snow = skyOf(current.weatherCode) == Sky.SNOW
        val stop = window.firstOrNull { !it.wet }
        val episode = window.takeWhile { it.wet }
        val text = when {
            stop == null && window.size < 24 -> (if (snow) "Nieve" else "Lluvia") + " hasta el final de la previsión"
            stop == null -> if (snow) "Nieve todo el día" else "Lluvia todo el día"
            atMidnight(stop.time, zone) -> (if (snow) "Para de nevar" else "Para de llover") + " a medianoche"
            sameDay(stop.time, now, zone) -> (if (snow) "Para de nevar" else "Para de llover") + " a las ${hhmm(stop.time, zone)}"
            // En 24 h, otro día solo puede ser mañana (también de madrugada: «hasta mañana a las 01:00»).
            else -> (if (snow) "Nieve" else "Lluvia") + " hasta mañana a las ${hhmm(stop.time, zone)}"
        }
        return SummaryLine(if (stop == null) SummaryIcon.RAIN else SummaryIcon.RAIN_STOP, text + amount(episode))
    }
    val start = window.firstOrNull { it.wet } ?: return null
    // La cantidad, dentro de las 24 h de las que habla la frase.
    val episode = window.dropWhile { it.time < start.time }.takeWhile { it.wet }
    val verb = if (skyOf(start.weatherCode) == Sky.SNOW) "Empieza a nevar" else "Empieza a llover"
    return SummaryLine(SummaryIcon.RAIN, "$verb ${whenWord(start.time, now, zone)} a las ${hhmm(start.time, zone)}" + amount(episode))
}

/** Regla 2: la racha mayor desde 50 km/h y su hora (la primera, si se repite). */
private fun gustLine(window: List<HourPoint>, now: Long, zone: ZoneId): SummaryLine? {
    val top = window.filter { it.windGustsKmh != null }.maxByOrNull { it.windGustsKmh!! } ?: return null
    val gust = top.windGustsKmh!!
    if (gust < GUST_KMH) return null
    return SummaryLine(
        SummaryIcon.GUST,
        "Rachas de ${gust.roundToInt()} km/h ${whenWord(top.time, now, zone)} hacia las ${hhmm(top.time, zone)}",
    )
}

/** Regla 3: la mínima de esta noche (hasta las 10:00; de día, desde las 18:00) en 0° o menos, la cifra de pantalla. */
private fun frostLine(window: List<HourPoint>, now: Long, zone: ZoneId): SummaryLine? {
    val local = local(now, zone)
    val (from, to) = if (local.hour < NIGHT_END) {
        now to local.toLocalDate().atTime(NIGHT_END, 0).atZone(zone).toEpochSecond()
    } else {
        local.toLocalDate().atTime(NIGHT_START, 0).atZone(zone).toEpochSecond() to
            local.toLocalDate().plusDays(1).atTime(NIGHT_END, 0).atZone(zone).toEpochSecond()
    }
    val min = window.filter { it.time + HOUR > from && it.time < to }.minOfOrNull { it.temperatureC } ?: return null
    if (shownDegrees(min) > 0) return null
    return SummaryLine(SummaryIcon.FROST, "Helada esta noche: ${shownDegrees(min)}°")
}

/** Regla 4: niebla ahora (hasta cuándo) o más tarde (hacia cuándo). */
private fun fogLine(window: List<HourPoint>, now: Long, zone: ZoneId): SummaryLine? {
    if (window.first().foggy) {
        val stop = window.firstOrNull { !it.foggy }
        val text = when {
            stop == null && window.size < 24 -> "Niebla hasta el final de la previsión"
            stop == null -> "Niebla todo el día"
            atMidnight(stop.time, zone) -> "Niebla hasta medianoche"
            sameDay(stop.time, now, zone) -> "Niebla hasta las ${hhmm(stop.time, zone)}"
            else -> "Niebla hasta mañana a las ${hhmm(stop.time, zone)}"
        }
        return SummaryLine(SummaryIcon.FOG, text)
    }
    val start = window.firstOrNull { it.foggy } ?: return null
    return SummaryLine(SummaryIcon.FOG, "Niebla ${whenWord(start.time, now, zone)} hacia las ${hhmm(start.time, zone)}")
}

/** Regla 5: la máxima de mañana frente a la de hoy, en las cifras que se ven, si cambia 3° o más. */
private fun tomorrowMaxLine(snapshot: WeatherSnapshot, now: Long, zone: ZoneId): SummaryLine? {
    val date = local(now, zone).toLocalDate()
    val today = snapshot.daily.firstOrNull { it.date == date.toString() } ?: return null
    val tomorrow = snapshot.daily.firstOrNull { it.date == date.plusDays(1).toString() } ?: return null
    val max = shownDegrees(tomorrow.maxC)
    val diff = max - shownDegrees(today.maxC)
    return when {
        diff >= TEMP_STEP -> SummaryLine(SummaryIcon.TEMP_UP, "Mañana, máxima de $max°: $diff° más que hoy")
        diff <= -TEMP_STEP -> SummaryLine(SummaryIcon.TEMP_DOWN, "Mañana, máxima de $max°: ${-diff}° menos que hoy")
        else -> null
    }
}

/** Regla 6: sin lluvia en 24 h, el primer día de los próximos (sin hoy) con 1 mm o más. */
private fun laterRainLine(snapshot: WeatherSnapshot, now: Long, zone: ZoneId): SummaryLine? {
    val today = local(now, zone).toLocalDate()
    val day = daysFrom(snapshot, now).firstOrNull { it.date > today.toString() && it.precipSumMm >= DAY_RAIN_MM } ?: return null
    val date = LocalDate.parse(day.date)
    val ahead = ChronoUnit.DAYS.between(today, date)
    val text = when {
        ahead == 1L -> "Lluvia mañana"
        ahead < 7 -> "Lluvia el ${WEEKDAYS[date.dayOfWeek.value - 1]}"
        else -> "Lluvia el ${WEEKDAYS[date.dayOfWeek.value - 1]} ${date.dayOfMonth}"
    }
    return SummaryLine(SummaryIcon.RAIN, text)
}

/**
 * «Resumen del día» de la ventana del día (P4): una frase. Con lluvia, de cuándo a cuándo y cuánto; seco, la máxima y
 * su hora. Hoy cuenta desde la hora en curso. Sin horas de ese día, la máxima y la mínima; sin el día, "".
 */
fun daySummary(snapshot: WeatherSnapshot, date: String, now: Long, zone: ZoneId): String {
    val hours = snapshot.hourly.filter { it.time + HOUR > now && local(it.time, zone).toLocalDate().toString() == date }
    if (hours.isEmpty()) {
        val day = snapshot.daily.firstOrNull { it.date == date } ?: return ""
        return "Máxima de ${shownDegrees(day.maxC)}° y mínima de ${shownDegrees(day.minC)}°"
    }
    val wet = hours.filter { it.wet }
    if (wet.isEmpty()) {
        val top = hours.maxBy { it.temperatureC }
        return "Sin lluvia; máxima de ${shownDegrees(top.temperatureC)}° hacia las ${hhmm(top.time, zone)}"
    }
    val noun = if (wet.count { skyOf(it.weatherCode) == Sky.SNOW } * 2 > wet.size) "Nieve" else "Lluvia"
    val isToday = date == local(now, zone).toLocalDate().toString()
    val text = when {
        wet.size == hours.size -> if (isToday && hours.size < 24) "$noun el resto del día" else "$noun todo el día"
        else -> {
            // Si hay horas secas en medio, «a ratos»: no se dice que llueve seguido de la primera a la última.
            val first = hours.indexOf(wet.first())
            val last = hours.indexOf(wet.last())
            val gaps = last - first + 1 > wet.size
            "$noun ${if (gaps) "a ratos " else ""}de ${hhmm(wet.first().time, zone)} a ${hhmm(wet.last().time + HOUR, zone)}"
        }
    }
    return text + amount(wet)
}

/** Avisos en vigor o que empiezan en 24 h, el más grave arriba y, a igualdad, el que empieza antes. */
fun activeWarnings(warnings: List<Warning>, now: Long): List<Warning> =
    warnings
        .filter { it.expires > now && it.onset <= now + 24 * HOUR }
        .sortedWith(compareByDescending<Warning> { it.level }.thenBy { it.onset })

/** Sin conexión, de cuándo son los avisos guardados: «Avisos guardados · datos de las 08:15» (o «de ayer, 08:15»). */
fun savedWarningsLabel(fetchedAt: Long, now: Long, zone: ZoneId): String {
    val time = hhmm(fetchedAt, zone)
    return when (val day = dayWord(fetchedAt, now, zone)) {
        null -> "Avisos guardados · datos de las $time"
        else -> "Avisos guardados · datos $day, $time"
    }
}

private fun amount(hours: List<HourPoint>): String {
    val mm = hours.sumOf { it.precipMm }
    return if (mm >= AMOUNT_MM) " · ${mm.roundToInt()} mm" else ""
}

/** «hoy», «esta noche» (de 21:00 a 05:59 de la noche que viene o la que está en curso), «mañana» o el día. */
private fun whenWord(t: Long, now: Long, zone: ZoneId): String {
    val at = local(t, zone)
    val n = local(now, zone)
    return when (ChronoUnit.DAYS.between(n.toLocalDate(), at.toLocalDate())) {
        0L -> if (at.hour >= 21 || (at.hour < 6 && n.hour < 6)) "esta noche" else "hoy"
        1L -> if (at.hour < 6 && n.hour >= 12) "esta noche" else "mañana"
        else -> "el " + WEEKDAYS[at.dayOfWeek.value - 1]
    }
}

/** Las 00:00 locales: «a medianoche» se entiende mejor que «hasta mañana a las 00:00». */
private fun atMidnight(t: Long, zone: ZoneId) = local(t, zone).let { it.hour == 0 && it.minute == 0 }

private fun sameDay(a: Long, b: Long, zone: ZoneId) = local(a, zone).toLocalDate() == local(b, zone).toLocalDate()

private val WEEKDAYS = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

private fun local(epochSeconds: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochSecond(epochSeconds).atZone(zone)

private fun hhmm(epochSeconds: Long, zone: ZoneId): String = HHMM.format(local(epochSeconds, zone))
