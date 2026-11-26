package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

// 016: los hechos que se envían al modelo. Salen de la previsión guardada, ya redondeados como se ven en pantalla, con
// las horas locales del sitio y sin nada que diga dónde está (H17). Todo con `now` y la zona, nunca con el reloj.

/** Un tramo del día entre dos horas «HH:mm» ([to] puede ser «00:00»: medianoche) y, si es de lluvia, sus mm. */
data class Span(val from: String, val to: String, val mm: Int = 0)

data class DayFacts(
    /** YYYY-MM-DD. */
    val date: String,
    /** «hoy», «mañana» o «jueves 14». */
    val label: String,
    val minC: Int,
    /** «07:00»; null sin horas de ese día. */
    val minAt: String?,
    val maxC: Int,
    val maxAt: String?,
    val rainMm: Int,
    val rain: List<Span>,
    val rainAllDay: Boolean,
    /** Horas con código de tormenta (95–99). */
    val storm: List<String>,
    /** Solo desde 50 km/h. */
    val gustKmh: Int?,
    val gustAt: String?,
    /** Solo con la mínima en 0° o menos. */
    val frostC: Int?,
    val fog: Span?,
    /** La precipitación es mayormente nieve. */
    val snow: Boolean = false,
)

data class WarningFacts(
    /** `a1`, `a2`…: lo único que ve el modelo para nombrar el aviso. */
    val key: String,
    val level: String,
    val phenomenon: String,
    /** «hoy 15:00». */
    val from: String,
    val to: String,
    val probability: String?,
    val description: String,
    /** `Warning.id`: solo en memoria, para deshacer la clave; nunca se envía. */
    val id: String,
)

data class ReportFacts(
    val basis: ReportBasis,
    /** El primero de [days]. */
    val today: DayFacts,
    /** Desde hoy, como mucho 10. */
    val days: List<DayFacts>,
    val warnings: List<WarningFacts>,
)

private const val HOUR_S = 3600L
private const val DESCRIPTION_MAX = 500
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
internal val DAY_NAMES = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/**
 * Los hechos del parte, o null si no hay ninguna hora desde la que está en curso (datos muy viejos: no se llama).
 * «Hoy» cuenta la lluvia, la tormenta, las rachas y la niebla desde la hora en curso; la mínima y la máxima, del día
 * entero.
 */
fun reportFacts(snapshot: WeatherSnapshot, now: Long, zone: ZoneId): ReportFacts? {
    if (snapshot.hourly.none { it.time + HOUR_S > now }) return null
    val todayDate = dateOf(now, zone)
    val hoursByDate = snapshot.hourly.groupBy { dateOf(it.time, zone) }
    val points = snapshot.daily.filter { it.date >= todayDate.toString() }.take(10).associateBy { it.date }

    // Sin el día de hoy en `daily` (datos raros) se sacan las cifras de sus horas; sin ellas tampoco, no hay parte.
    val todayHours = hoursByDate[todayDate].orEmpty()
    val todayPoint = points[todayDate.toString()] ?: run {
        if (todayHours.isEmpty()) return null
        DayPoint(todayDate.toString(), todayHours.maxOf { it.temperatureC }, todayHours.minOf { it.temperatureC }, null, 0.0)
    }
    val dates = (listOf(todayDate.toString()) + points.keys.filter { it > todayDate.toString() }.sorted()).take(10)
    val days = dates.map { date ->
        val point = if (date == todayDate.toString()) todayPoint else points.getValue(date)
        dayFacts(point, hoursByDate[LocalDate.parse(date)].orEmpty(), now, zone, todayDate)
    }
    val today = days.first()
    val active = activeWarnings(snapshot.warnings, now)
    val warnings = active.mapIndexed { i, w -> warningFacts("a${i + 1}", w, todayDate, zone) }
    val basis = ReportBasis(
        date = todayDate.toString(),
        maxC = today.maxC,
        minC = today.minC,
        wetToday = today.rain.isNotEmpty(),
        rainStartHour = rainStartHour(snapshot.hourly, now, zone, todayDate),
        warningIds = active.map { it.id }.sorted(),
    )
    return ReportFacts(basis, today, days, warnings)
}

private fun dayFacts(point: DayPoint, all: List<HourPoint>, now: Long, zone: ZoneId, todayDate: LocalDate): DayFacts {
    val date = LocalDate.parse(point.date)
    // Hoy cuenta desde la hora en curso; los demás días, enteros.
    val hours = all.filter { it.time + HOUR_S > now }.sortedBy { it.time }
    val minC = shownDegrees(point.minC)
    val lowest = all.minByOrNull { it.temperatureC }
    val highest = all.maxByOrNull { it.temperatureC }
    val wet = hours.filter { it.wetHour }
    val spans = runs(hours) { it.wetHour }.map { run ->
        Span(clock(run.first().time, zone), clock(run.last().time + HOUR_S, zone), run.sumOf { it.precipMm }.roundToInt())
    }
    val gust = hours.filter { it.windGustsKmh != null }.maxByOrNull { it.windGustsKmh!! }
    val gustKmh = gust?.windGustsKmh?.takeIf { it >= GUST_KMH }
    val fog = runs(hours) { it.weatherCode == 45 || it.weatherCode == 48 }.firstOrNull()?.let { run ->
        Span(clock(run.first().time, zone), clock(run.last().time + HOUR_S, zone))
    }
    return DayFacts(
        date = point.date,
        label = labelOf(date, todayDate),
        minC = minC,
        minAt = lowest?.let { clock(it.time, zone) },
        maxC = shownDegrees(point.maxC),
        maxAt = highest?.let { clock(it.time, zone) },
        rainMm = wet.sumOf { it.precipMm }.roundToInt(),
        rain = spans,
        // «Todo el día» solo si el día entero llueve; a media mañana, lo que queda no es «todo el día».
        rainAllDay = hours.isNotEmpty() && wet.size == hours.size && hours.size == all.size,
        storm = hours.filter { it.weatherCode in 95..99 }.map { clock(it.time, zone) },
        gustKmh = gustKmh?.roundToInt(),
        gustAt = gust?.takeIf { gustKmh != null }?.let { clock(it.time, zone) },
        frostC = minC.takeIf { it <= 0 },
        fog = fog,
        snow = wet.isNotEmpty() && wet.count { skyOf(it.weatherCode) == Sky.SNOW } * 2 > wet.size,
    )
}

private val HourPoint.wetHour: Boolean get() = precipMm >= WET_MM

/** Tramos de horas seguidas (cada una empieza donde acaba la anterior) que cumplen [test]. */
private fun runs(hours: List<HourPoint>, test: (HourPoint) -> Boolean): List<List<HourPoint>> {
    val out = ArrayList<List<HourPoint>>()
    var current = ArrayList<HourPoint>()
    for (h in hours) {
        val breaks = !test(h) || (current.isNotEmpty() && h.time != current.last().time + HOUR_S)
        if (breaks && current.isNotEmpty()) {
            out.add(current)
            current = ArrayList()
        }
        if (test(h)) current.add(h)
    }
    if (current.isNotEmpty()) out.add(current)
    return out
}

/**
 * La primera hora con lluvia desde ahora, contada en horas reales desde las 00:00 de hoy (0–47). Si ya llueve, cuenta
 * desde que empezó hoy: así una lluvia que sigue no «se mueve» con cada refresco.
 */
private fun rainStartHour(hourly: List<HourPoint>, now: Long, zone: ZoneId, today: LocalDate): Int? {
    val midnight = today.atStartOfDay(zone).toEpochSecond()
    val byTime = hourly.associateBy { it.time }
    val first = hourly.filter { it.wetHour && it.time + HOUR_S > now && it.time < midnight + 48 * HOUR_S }
        .minByOrNull { it.time } ?: return null
    var t = first.time
    while (t - HOUR_S >= midnight && byTime[t - HOUR_S]?.wetHour == true) t -= HOUR_S
    return ((t - midnight) / HOUR_S).toInt()
}

private fun warningFacts(key: String, w: Warning, today: LocalDate, zone: ZoneId) = WarningFacts(
    key = key,
    level = levelName(w.level),
    phenomenon = w.phenomenon,
    from = whenText(w.onset, today, zone),
    to = whenText(w.expires, today, zone),
    probability = w.probability,
    description = w.description.trim().replace(Regex("\\s+"), " ").take(DESCRIPTION_MAX),
    id = w.id,
)

private fun whenText(t: Long, today: LocalDate, zone: ZoneId): String {
    val date = dateOf(t, zone)
    val word = when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "hoy"
        1L -> "mañana"
        -1L -> "ayer"
        else -> DAY_NAMES[date.dayOfWeek.value - 1]
    }
    return "$word ${clock(t, zone)}"
}

private fun labelOf(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, date)) {
    0L -> "hoy"
    1L -> "mañana"
    else -> "${DAY_NAMES[date.dayOfWeek.value - 1]} ${date.dayOfMonth}"
}

private fun dateOf(epochSeconds: Long, zone: ZoneId): LocalDate = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()

private fun clock(epochSeconds: Long, zone: ZoneId): String = CLOCK.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

/** 018 (H17): «martes» para «2026-11-17»; null si la fecha no se lee. El modelo no adivina el día de la semana. */
internal fun weekdayOf(date: String): String? = try {
    DAY_NAMES[LocalDate.parse(date).dayOfWeek.value - 1]
} catch (e: java.time.format.DateTimeParseException) {
    null
}
