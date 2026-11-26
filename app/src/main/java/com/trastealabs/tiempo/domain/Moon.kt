package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// La luna viene entera de Open-Meteo (salida, puesta y fase): aquí no se calcula astronomía.

enum class MoonPhaseName(val label: String) {
    NEW("Luna nueva"),
    WAXING_CRESCENT("Creciente"),
    FIRST_QUARTER("Cuarto creciente"),
    WAXING_GIBBOUS("Gibosa creciente"),
    FULL("Luna llena"),
    WANING_GIBBOUS("Gibosa menguante"),
    LAST_QUARTER("Cuarto menguante"),
    WANING_CRESCENT("Menguante"),
}

/** A menos de un día (0,034 del ciclo) de una fase principal, se dice esa fase (H3). En milésimas. */
private const val MAIN_PHASE_MILLIS = 34

/**
 * H3. Las cuatro principales si está a 0,034 o menos de 0 (o 1), 0,25, 0,5 o 0,75. Se compara en milésimas
 * redondeadas para que 0,284 − 0,25 no falle por decimales.
 */
fun moonPhaseName(phase: Double): MoonPhaseName {
    fun near(x: Double) = (abs(phase - x) * 1000).roundToLong() <= MAIN_PHASE_MILLIS
    return when {
        near(0.0) || near(1.0) -> MoonPhaseName.NEW
        near(0.25) -> MoonPhaseName.FIRST_QUARTER
        near(0.5) -> MoonPhaseName.FULL
        near(0.75) -> MoonPhaseName.LAST_QUARTER
        phase < 0.25 -> MoonPhaseName.WAXING_CRESCENT
        phase < 0.5 -> MoonPhaseName.WAXING_GIBBOUS
        phase < 0.75 -> MoonPhaseName.WANING_GIBBOUS
        else -> MoonPhaseName.WANING_CRESCENT
    }
}

/** Parte iluminada del disco, en %: 0,84 → 23. */
fun moonIlluminatedPct(phase: Double): Int = ((1 - cos(2 * PI * phase)) / 2 * 100).roundToInt()

/** "Menguante · 23 %". */
fun moonPhaseLabel(phase: Double): String = "${moonPhaseName(phase).label} · ${moonIlluminatedPct(phase)} %"

/** H7. Ficha de la luna de la principal. */
data class MoonInfo(
    val phase: Double,
    /** "Menguante · 23 %". */
    val label: String,
    /** "Sale a las 02:47 · se pone a las 15:43", en el orden en que llegan. */
    val events: String,
    /** Por dónde va en el arco (0 al salir, 1 al ponerse); null si aún no ha salido. */
    val fraction: Float?,
)

private const val DAY_SECONDS = 86_400.0
private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

private data class MoonEvent(val time: Long, val rise: Boolean, val phase: Double?)

/**
 * H7. De noche (del anochecer de hoy al amanecer, siempre del día de [now]), la ficha de la luna; de día, en noche
 * o día polar o con datos de antes (sin fase hoy), `null` y sale la del sol como siempre.
 */
fun moonInfo(snapshot: WeatherSnapshot, now: Long): MoonInfo? {
    val day = today(snapshot, now) ?: return null
    val sunrise = day.sunrise ?: return null
    val sunset = day.sunset ?: return null
    val light = day.daylightSeconds ?: (sunset - sunrise).toDouble()
    if (light <= 0.0 || light >= DAY_SECONDS) return null
    if (now in sunrise until sunset) return null
    val todayPhase = day.moonPhase ?: return null

    val days = listOfNotNull(snapshot.yesterday) + snapshot.daily
    val events = days.flatMap { d ->
        listOfNotNull(
            d.moonrise?.let { MoonEvent(it, rise = true, d.moonPhase) },
            d.moonset?.let { MoonEvent(it, rise = false, d.moonPhase) },
        )
    }.sortedBy { it.time }

    val lastRise = events.lastOrNull { it.rise && it.time <= now }
    val up = lastRise != null && events.none { !it.rise && it.time > lastRise.time && it.time <= now }
    val next = events.firstOrNull { it.time > now }
    val fraction = if (up) {
        events.firstOrNull { !it.rise && it.time > now }
            ?.let { set -> ((now - lastRise!!.time).toFloat() / (set.time - lastRise.time)).coerceIn(0f, 1f) }
    } else {
        null
    }

    val zone = ZoneId.of(snapshot.zoneId)
    val text = next?.let { first ->
        val second = events.firstOrNull { it.time > first.time && it.rise != first.rise }
        listOfNotNull(
            phrase(first, zone, capital = true),
            second?.let { phrase(it, zone, capital = false) },
        ).joinToString(" · ")
    }.orEmpty()

    val phase = when {
        up -> lastRise!!.phase
        else -> events.firstOrNull { it.rise && it.time > now }?.phase
    } ?: todayPhase
    return MoonInfo(phase, moonPhaseLabel(phase), text, fraction)
}

private fun phrase(e: MoonEvent, zone: ZoneId, capital: Boolean): String {
    val verb = if (e.rise) "sale" else "se pone"
    val text = "$verb a las ${hhmm.format(Instant.ofEpochSecond(e.time).atZone(zone))}"
    return if (capital) text.replaceFirstChar { it.uppercaseChar() } else text
}

/**
 * H8. Siguiente instante (> [now]) en que la pantalla cambia sin datos nuevos: la medianoche del móvil y la de la
 * previsión, cualquier amanecer, anochecer, salida o puesta de la luna del snapshot y, desde la 005, la siguiente
 * hora del aire guardado (la cifra de la ficha es la de la hora de ahora, H6). Sin snapshot, medianoche.
 */
fun nextScreenTick(now: Long, phoneZone: ZoneId, snapshot: WeatherSnapshot?): Long {
    val phoneMidnight = now + secondsToNextMidnight(now, phoneZone)
    if (snapshot == null) return phoneMidnight
    val forecastMidnight = now + secondsToNextMidnight(now, ZoneId.of(snapshot.zoneId))
    val events = (listOfNotNull(snapshot.yesterday) + snapshot.daily).flatMap {
        listOfNotNull(it.sunrise, it.sunset, it.moonrise, it.moonset)
    }
    val nextAirHour = snapshot.air?.hours?.filter { it.time > now }?.minOfOrNull { it.time }
    return (events.filter { it > now } + listOfNotNull(nextAirHour) + phoneMidnight + forecastMidnight).min()
}

/** 007, H12. La fase de hoy para dibujar la luna; sin dato (datos de antes), llena (0,5). */
fun moonPhaseToday(snapshot: WeatherSnapshot, now: Long): Double = today(snapshot, now)?.moonPhase ?: 0.5

/** 007, H12. En el hemisferio sur la luna se ve girada 180° (la luz del creciente, a la izquierda). */
fun isSouth(snapshot: WeatherSnapshot): Boolean = snapshot.place.position.lat < 0
