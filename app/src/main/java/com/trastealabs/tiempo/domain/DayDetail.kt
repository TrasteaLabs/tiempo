package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

// Página del día (H5 de la 004): lo que se puede calcular sin pantalla.

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

private fun localHour(epoch: Long, zone: ZoneId): String = "%02d".format(Instant.ofEpochSecond(epoch).atZone(zone).hour)

/**
 * H5.3. Las horas de [date] en hora local, en orden: 24, o 25 y 23 el día del cambio de hora (son instantes
 * seguidos: no se repite ni se salta ninguna por error, solo cambia la etiqueta). Faltan horas → las que haya.
 */
fun hoursOfDay(hourly: List<HourPoint>, date: String, zone: ZoneId): List<HourPoint> =
    hourly
        .filter { Instant.ofEpochSecond(it.time).atZone(zone).toLocalDate().toString() == date }
        .sortedBy { it.time }

/** La columna «Ahora»: la última que ha empezado (`time <= now`), si `now` cae dentro de esas horas; si no, null. */
fun nowIndex(hours: List<HourPoint>, now: Long): Int? {
    val i = hours.indexOfLast { it.time <= now }
    if (i < 0 || now >= hours[i].time + 3600) return null
    return i
}

/**
 * La raya del cambio de hora va entre las columnas `index − 1` e `index`: la primera pareja seguida con distinto
 * desfase. `before`/`after` son sus horas locales ("02"/"02" al atrasar; "01"/"03" al adelantar).
 */
data class DstShift(val index: Int, val backward: Boolean, val before: String, val after: String)

fun dstShift(hours: List<HourPoint>, zone: ZoneId): DstShift? {
    val rules = zone.rules
    for (i in 1 until hours.size) {
        val a = rules.getOffset(Instant.ofEpochSecond(hours[i - 1].time))
        val b = rules.getOffset(Instant.ofEpochSecond(hours[i].time))
        if (a != b) {
            return DstShift(i, backward = b.totalSeconds < a.totalSeconds, localHour(hours[i - 1].time, zone), localHour(hours[i].time, zone))
        }
    }
    return null
}

/** H5.4. null si ninguna hora trae viento (datos de antes: no sale la tarjeta). */
data class DayWind(
    val calm: Boolean,
    /** "22 km/h" o "Calma". */
    val speed: String,
    /** Hacia donde sopla: (de dónde viene + 180) % 360; null en calma o sin dirección. */
    val towardsDeg: Int?,
    /** "Suroeste · 17:00" (sin dirección, "17:00"); en calma, "No pasa de 5 km/h en todo el día". */
    val detail: String,
    /** "Rachas de 41"; null en calma o sin rachas. */
    val gusts: String?,
)

/**
 * La hora más fuerte (la primera si empatan). Calma con la regla de la ficha ([CALM_KMH]): la cifra que se ve
 * es menor que 5 (4,4 → calma; 4,6 → «5 km/h»), así la tarjeta, la ficha y las hojas dicen lo mismo.
 */
fun dayWind(hours: List<HourPoint>, zone: ZoneId): DayWind? {
    val strongest = hours.filter { it.windKmh != null }.maxByOrNull { it.windKmh!! } ?: return null
    val kmh = strongest.windKmh!!.roundToInt()
    if (kmh < CALM_KMH) return DayWind(true, "Calma", null, "No pasa de 5 km/h en todo el día", null)
    val at = hhmm.format(Instant.ofEpochSecond(strongest.time).atZone(zone))
    val from = strongest.windDirectionDeg
    val gust = hours.mapNotNull { it.windGustsKmh }.maxOrNull()?.roundToInt()
    return DayWind(
        calm = false,
        speed = "$kmh km/h",
        towardsDeg = from?.let { ((it + 180) % 360 + 360) % 360 },
        detail = if (from != null) "${compassName(from)} · $at" else at,
        gusts = gust?.let { "Rachas de $it" },
    )
}

/** H5.5. `label` = "55–93 %" o "70 %" si no cambia. */
data class DayHumidity(val minPct: Int, val maxPct: Int) {
    val label: String get() = if (minPct == maxPct) "$minPct %" else "$minPct–$maxPct %"
}

/** null sin humedad (datos de antes). */
fun dayHumidity(hours: List<HourPoint>): DayHumidity? {
    val values = hours.mapNotNull { it.humidityPct }
    if (values.isEmpty()) return null
    return DayHumidity(values.min(), values.max())
}

/** "Hoy", "Mañana" o "Dentro de N días" (la página añade " · {sitio}"). */
fun relativeDayLabel(date: String, today: String): String =
    when (val n = ChronoUnit.DAYS.between(LocalDate.parse(today), LocalDate.parse(date))) {
        0L -> "Hoy"
        1L -> "Mañana"
        else -> "Dentro de $n días"
    }

// ---------- «Sol y luna» (H5.6, P3) ----------

/** (hora·60 + minuto) / 1440, en hora local: dónde cae [epoch] en la línea del día. */
fun clockFraction(epoch: Long, zone: ZoneId): Float {
    val t = Instant.ofEpochSecond(epoch).atZone(zone)
    return (t.hour * 60 + t.minute) / 1440f
}

/** `range` = "07:58 – 18:21"; `daylight` = "10 h 23 min de luz"; `rise`/`set` en la línea del día (0 a 1). */
data class DaySun(val range: String, val daylight: String, val rise: Float, val set: Float)

private const val DAY_SECONDS = 86_400.0

/** null sin salida o puesta, o en noche y día polar (0 o 86400 s de luz), como la ficha del sol. */
fun daySun(day: DayPoint, zone: ZoneId): DaySun? {
    val rise = day.sunrise ?: return null
    val set = day.sunset ?: return null
    val light = day.daylightSeconds ?: (set - rise).toDouble()
    if (light <= 0.0 || light >= DAY_SECONDS) return null
    val minutes = (light / 60).roundToInt()
    return DaySun(
        range = "${clock(rise, zone)} – ${clock(set, zone)}",
        daylight = "${minutes / 60} h ${minutes % 60} min de luz",
        rise = clockFraction(rise, zone),
        set = clockFraction(set, zone),
    )
}

/**
 * La luna de ese día. `label` = "Menguante · 32 %"; `events` en el orden del día ("Sale 01:44 · se pone 15:12",
 * "No sale este día · se pone 18:39"…); `bands`, los tramos de la línea del día en que está fuera.
 */
data class DayMoon(val phase: Double, val label: String, val events: String, val bands: List<ClosedFloatingPointRange<Float>>)

/** null sin fase (datos de antes). Con fase y sin salida o sin puesta, ese día no sale o no se pone. */
fun dayMoon(day: DayPoint, zone: ZoneId): DayMoon? {
    val phase = day.moonPhase ?: return null
    val rise = day.moonrise
    val set = day.moonset
    val events = when {
        rise != null && set != null && rise <= set -> "Sale ${clock(rise, zone)} · se pone ${clock(set, zone)}"
        rise != null && set != null -> "Se pone ${clock(set, zone)} · sale ${clock(rise, zone)}"
        set != null -> "No sale este día · se pone ${clock(set, zone)}"
        rise != null -> "Sale ${clock(rise, zone)} · no se pone este día"
        else -> "No sale ni se pone este día"
    }
    val r = rise?.let { clockFraction(it, zone) }
    val s = set?.let { clockFraction(it, zone) }
    val bands = when {
        r != null && s != null && rise!! <= set!! -> listOf(r..s)
        r != null && s != null -> listOf(0f..s, r..1f)
        s != null -> listOf(0f..s)
        r != null -> listOf(r..1f)
        else -> emptyList()
    }
    return DayMoon(phase, moonPhaseLabel(phase), events, bands)
}

private fun clock(epoch: Long, zone: ZoneId): String = hhmm.format(Instant.ofEpochSecond(epoch).atZone(zone))
