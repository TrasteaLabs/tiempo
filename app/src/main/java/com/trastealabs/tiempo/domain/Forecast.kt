package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

private fun zoneOf(snapshot: WeatherSnapshot): ZoneId = ZoneId.of(snapshot.zoneId)

/** El día local de [now] (a medianoche pasa al siguiente). */
fun today(snapshot: WeatherSnapshot, now: Long): DayPoint? {
    val date = Instant.ofEpochSecond(now).atZone(zoneOf(snapshot)).toLocalDate().toString()
    return snapshot.daily.firstOrNull { it.date == date }
}

/**
 * Las 24 horas desde la hora en curso (H3). Las horas son instantes UTC seguidos, así que en el
 * cambio de hora no se repite ni se salta ninguna: solo cambia la etiqueta local.
 */
fun next24Hours(hourly: List<HourPoint>, now: Long): List<HourPoint> {
    val start = hourly.indexOfLast { it.time <= now }.coerceAtLeast(0)
    return hourly.drop(start).take(24)
}

/**
 * La columna [index] enseña la temperatura y el cielo actuales (los de la cabecera) si el dato es de esa hora;
 * con datos de otra hora (sin conexión), o sin columna, se queda la previsión horaria.
 */
fun withCurrentAt(hours: List<HourPoint>, index: Int?, snapshot: WeatherSnapshot): List<HourPoint> {
    val hour = index?.let { hours.getOrNull(it) } ?: return hours
    if (snapshot.fetchedAt - hour.time !in 0 until 3600) return hours
    val current = snapshot.current
    return hours.toMutableList().also {
        it[index] = hour.copy(temperatureC = current.temperatureC, weatherCode = current.weatherCode, isDay = current.isDay)
    }
}

/** Los días desde hoy en la zona de la previsión (a medianoche empieza el siguiente), como mucho 10. */
fun daysFrom(snapshot: WeatherSnapshot, now: Long): List<DayPoint> {
    val date = Instant.ofEpochSecond(now).atZone(zoneOf(snapshot)).toLocalDate().toString()
    return snapshot.daily.filter { it.date >= date }.take(10)
}

/**
 * Icono de un día (H4) con sus horas de 06:00 a 17:59: si hay precipitación en al menos dos de ellas,
 * la más grave; si no, el cielo más frecuente (a igualdad, el más grave).
 */
fun dayIconSky(date: String, hourly: List<HourPoint>, zone: ZoneId): Sky {
    val skies = hourly
        .filter {
            val local = Instant.ofEpochSecond(it.time).atZone(zone)
            local.toLocalDate().toString() == date && local.hour in 6..17
        }
        .map { skyOf(it.weatherCode) }
    if (skies.isEmpty()) return Sky.CLOUDY
    val wet = skies.filter { it.isPrecipitation }
    if (wet.size >= 2) return wet.max()
    return skies.groupingBy { it }.eachCount()
        .maxWith(compareBy<Map.Entry<Sky, Int>> { it.value }.thenBy { it.key })
        .key
}

/**
 * Compara la temperatura actual con la de la misma hora local de ayer.
 * `null` si la fuente no dio esa hora (H2).
 */
fun yesterdayPhrase(snapshot: WeatherSnapshot, now: Long): String? {
    val zone = zoneOf(snapshot)
    val yesterdayHour = Instant.ofEpochSecond(now).atZone(zone)
        .withMinute(0).withSecond(0).withNano(0)
        .minusDays(1)
        .toEpochSecond()
    val before = snapshot.hourly.firstOrNull { it.time == yesterdayHour } ?: return null
    val diff = snapshot.current.temperatureC - before.temperatureC
    if (abs(diff) < 1.0) return "Igual que ayer a esta hora"
    val degrees = abs(diff).roundToInt()
    val word = if (diff < 0) "más frío" else "más cálido"
    return "$degrees °C $word que ayer a esta hora"
}
