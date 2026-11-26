package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

// Fichas bajo los 10 días (H10–H12). `null` = la ficha no aparece (falta el dato principal).

/** Ficha de viento: "12 km/h" y debajo "Suroeste · rachas 45" o "Calma". */
data class WindInfo(val speed: String, val detail: String?)

/** Por debajo de esto (en la cifra que se ve), «Calma» (H10). La única regla de calma: la usan la ficha y la página del día. */
internal const val CALM_KMH = 5

fun windInfo(c: CurrentConditions): WindInfo? {
    val kmh = c.windKmh?.roundToInt() ?: return null
    val detail = if (kmh < CALM_KMH) {
        "Calma"
    } else {
        val direction = c.windDirectionDeg?.let(::compassName)
        val gusts = c.windGustsKmh?.roundToInt()
        when {
            direction != null && gusts != null -> "$direction · rachas $gusts"
            direction != null -> direction
            gusts != null -> "Rachas $gusts"
            else -> null
        }
    }
    return WindInfo("$kmh km/h", detail)
}

private val compass = listOf("Norte", "Noreste", "Este", "Sureste", "Sur", "Suroeste", "Oeste", "Noroeste")

/** De dónde viene el viento, en 8 rumbos de 45° centrados en cada uno (Norte: de 338° a 22°). */
fun compassName(deg: Int): String {
    val d = ((deg % 360) + 360) % 360
    return compass[((d * 2 + 45) / 90) % 8]
}

/** Ficha de humedad: "68 %" y debajo "Rocío 10°"; con niebla, "Visibilidad 300 m" o "Visibilidad 2 km". */
data class HumidityInfo(val value: String, val detail: String?)

fun humidityInfo(c: CurrentConditions): HumidityInfo? {
    val pct = c.humidityPct ?: return null
    val visibility = c.visibilityM?.takeIf { skyOf(c.weatherCode) == Sky.FOG }
    val detail = when {
        visibility != null -> "Visibilidad ${distance(visibility)}"
        c.dewPointC != null -> "Rocío ${shownDegrees(c.dewPointC)}°"
        else -> null
    }
    return HumidityInfo("$pct %", detail)
}

/**
 * Menos de un kilómetro, de cien en cien metros; si no, kilómetros sin decimales. Con niebla muy densa
 * el redondeo daría «0 m»: entonces, «< 100 m».
 */
private fun distance(metres: Double): String {
    val hundreds = (metres / 100).roundToInt() * 100
    return when {
        hundreds == 0 -> "< 100 m"
        hundreds < 1000 -> "$hundreds m"
        else -> "${(metres / 1000).roundToInt()} km"
    }
}

/**
 * Ficha de sol: "07:58 – 18:21", "10 h 23 min de luz · 2 min menos que ayer" y la posición del sol en
 * el arco a la hora del dato (0 al salir, 1 al ponerse; `null` de noche).
 */
data class SunInfo(val range: String, val daylight: String, val sunFraction: Float?)

private const val DAY_SECONDS = 86_400.0
private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

/**
 * El día de [now] (a medianoche pasa al nuevo) comparado con el anterior, que puede venir en `daily` o
 * en `snapshot.yesterday`. Sin ficha si faltan salida o puesta, o en noche y día polar (Open-Meteo da
 * 0 o 86400 s de luz). Las duraciones salen de segundos, nunca de restar horas locales: el cambio de
 * hora no las tuerce.
 */
fun sunInfo(snapshot: WeatherSnapshot, now: Long): SunInfo? {
    val day = today(snapshot, now) ?: return null
    val sunrise = day.sunrise ?: return null
    val sunset = day.sunset ?: return null
    val light = daylightOf(day) ?: return null
    if (light <= 0.0 || light >= DAY_SECONDS) return null

    val zone = ZoneId.of(snapshot.zoneId)
    val range = "${clock(sunrise, zone)} – ${clock(sunset, zone)}"
    val minutes = (light / 60).roundToInt()
    val hours = "${minutes / 60} h ${minutes % 60} min de luz"
    val previousDate = LocalDate.parse(day.date).minusDays(1).toString()
    val previous = (snapshot.daily + listOfNotNull(snapshot.yesterday)).firstOrNull { it.date == previousDate }
    val comparison = previous?.let(::daylightOf)?.let { before ->
        val diff = ((light - before) / 60).roundToInt()
        when {
            diff == 0 -> "igual que ayer"
            diff > 0 -> "$diff min más que ayer"
            else -> "${abs(diff)} min menos que ayer"
        }
    }
    val fraction = snapshot.fetchedAt
        .takeIf { sunset > sunrise && it in sunrise..sunset }
        ?.let { (it - sunrise).toFloat() / (sunset - sunrise) }
    return SunInfo(range, listOfNotNull(hours, comparison).joinToString(" · "), fraction)
}

private fun daylightOf(day: DayPoint): Double? =
    day.daylightSeconds ?: if (day.sunrise != null && day.sunset != null) (day.sunset - day.sunrise).toDouble() else null

private fun clock(epochSeconds: Long, zone: ZoneId): String = hhmm.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))
