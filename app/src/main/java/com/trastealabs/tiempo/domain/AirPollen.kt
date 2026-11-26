package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

// Reglas del aire y del polen (005). Todas reciben el snapshot y `now`; usan `snapshot.air` y la zona de la
// previsión, y «hoy» es la fecha local de `now` en esa zona. Sin aire guardado devuelven null o una lista vacía.

enum class AirLevel(val label: String) {
    GOOD("Buena"),
    FAIR("Razonablemente buena"),
    MODERATE("Regular"),
    POOR("Desfavorable"),
    VERY_POOR("Muy desfavorable"),
    EXTREMELY_POOR("Extremadamente desfavorable"),
}

/** H4: ≤ 20, ≤ 40, ≤ 60, ≤ 80, ≤ 100, > 100. */
fun airLevel(aqi: Int): AirLevel = when {
    aqi <= 20 -> AirLevel.GOOD
    aqi <= 40 -> AirLevel.FAIR
    aqi <= 60 -> AirLevel.MODERATE
    aqi <= 80 -> AirLevel.POOR
    aqi <= 100 -> AirLevel.VERY_POOR
    else -> AirLevel.EXTREMELY_POOR
}

enum class AirCause(val label: String) {
    PARTICLES("Por las partículas"),
    SAHARAN_DUST("Por polvo del Sáhara"),
    OZONE("Por el ozono"),
    NO2("Por el dióxido de nitrógeno"),
    SO2("Por el dióxido de azufre"),
}

/**
 * H5. El subíndice más alto: partículas (el mayor de PM2,5 y PM10), ozono, NO₂ y SO₂, sin contar los null; en un
 * empate, el primero de ese orden. Si ganan las partículas y el polvo es al menos la mitad de las PM10, calima.
 * null si la hora no tiene índice, si es «Buena» o si no trae ningún subíndice.
 */
fun airCause(hour: AirHour): AirCause? {
    val aqi = hour.aqi ?: return null
    if (airLevel(aqi) == AirLevel.GOOD) return null
    val particles = listOfNotNull(hour.pm25Index, hour.pm10Index).maxOrNull()
    val candidates = listOf(
        AirCause.PARTICLES to particles,
        AirCause.OZONE to hour.ozoneIndex,
        AirCause.NO2 to hour.no2Index,
        AirCause.SO2 to hour.so2Index,
    ).mapNotNull { (cause, index) -> index?.let { cause to it } }
    // maxBy devuelve el primero de los máximos: así se resuelven los empates.
    val winner = candidates.maxByOrNull { it.second }?.first ?: return null
    if (winner == AirCause.PARTICLES) {
        val pm10 = hour.pm10
        val dust = hour.dust
        if (pm10 != null && dust != null && pm10 > 0 && 2 * dust >= pm10) return AirCause.SAHARAN_DUST
    }
    return winner
}

/** H6. La cifra de la hora de ahora. */
data class AirNow(val aqi: Int, val level: AirLevel, val cause: AirCause?) {
    /** "Calidad del aire: razonablemente buena, índice 35. Por el ozono." (sin la causa si es null). */
    fun spoken(): String =
        "Calidad del aire: ${level.label.lowercase()}, índice $aqi." + (cause?.let { " ${it.label}." } ?: "")
}

/**
 * H6. La hora que contiene [now] (time ≤ now < time + 3600) con su índice: con datos de hace horas, la previsión
 * para esta hora. null si lo guardado no llega a esta hora o si esa hora no trae índice.
 */
fun airNow(snapshot: WeatherSnapshot, now: Long): AirNow? {
    val air = snapshot.air ?: return null
    val hour = air.hours.firstOrNull { it.time <= now && now < it.time + HOUR_S } ?: return null
    val aqi = hour.aqi ?: return null
    return AirNow(aqi, airLevel(aqi), airCause(hour))
}

/** H9. Un día de la hoja del aire, con el índice de su peor hora. */
data class AirDay(val date: String, val worstAqi: Int, val level: AirLevel)

/** H9. Hoy y los 3 días siguientes que estén completos para el índice, cada uno con su peor hora. */
fun airDays(snapshot: WeatherSnapshot, now: Long): List<AirDay> {
    val air = snapshot.air ?: return emptyList()
    val zone = ZoneId.of(snapshot.zoneId)
    return sheetDates(now, zone).mapNotNull { date ->
        val day = DayWindow(date, zone)
        if (!day.complete(air.hours) { it.aqi }) return@mapNotNull null
        val worst = air.hours.filter { day.contains(it) }.mapNotNull { it.aqi }.max()
        AirDay(date.toString(), worst, airLevel(worst))
    }
}

enum class PollenType(val label: String) { OLIVE("Olivo"), GRASS("Gramíneas") }

enum class PollenLevel(val label: String) { NONE("Nada"), LOW("Bajo"), MODERATE("Moderado"), HIGH("Alto") }

/** H7 (umbrales de la Red Española de Aerobiología). Olivo: 0, ≤ 50, ≤ 200, más. Gramíneas: 0, ≤ 25, ≤ 50, más. */
fun pollenLevel(type: PollenType, grains: Int): PollenLevel {
    val (low, moderate) = when (type) {
        PollenType.OLIVE -> 50 to 200
        PollenType.GRASS -> 25 to 50
    }
    return when {
        grains <= 0 -> PollenLevel.NONE
        grains <= low -> PollenLevel.LOW
        grains <= moderate -> PollenLevel.MODERATE
        else -> PollenLevel.HIGH
    }
}

/** H9. Un día de la hoja del polen. */
data class PollenDay(val date: String, val olive: PollenLevel, val grass: PollenLevel)

/**
 * H9. Hoy y los 3 días siguientes completos para el olivo y para las gramíneas. El nivel sale de la media de las
 * horas del día que traen dato, redondeada (0,5 → 1).
 */
fun pollenDays(snapshot: WeatherSnapshot, now: Long): List<PollenDay> {
    val air = snapshot.air ?: return emptyList()
    val zone = ZoneId.of(snapshot.zoneId)
    return sheetDates(now, zone).mapNotNull { date ->
        val day = DayWindow(date, zone)
        val olive = day.pollen(air.hours, PollenType.OLIVE) { it.olivePollen } ?: return@mapNotNull null
        val grass = day.pollen(air.hours, PollenType.GRASS) { it.grassPollen } ?: return@mapNotNull null
        PollenDay(date.toString(), olive, grass)
    }
}

/** H8. Los tipos de hoy que llegan a «bajo», olivo primero. */
data class PollenToday(val types: List<Pair<PollenType, PollenLevel>>) {
    /** "Polen hoy: olivo alto, gramíneas moderado." */
    fun spoken(): String =
        "Polen hoy: " + types.joinToString(", ") { (type, level) -> "${type.label.lowercase()} ${level.label.lowercase()}" } + "."
}

/**
 * H8. Sale del primer día de [pollenDays] si es hoy, así la ficha y la hoja dicen siempre lo mismo. null si ningún
 * tipo llega a «bajo» o si hoy no está completo.
 */
fun pollenToday(snapshot: WeatherSnapshot, now: Long): PollenToday? {
    val today = Instant.ofEpochSecond(now).atZone(ZoneId.of(snapshot.zoneId)).toLocalDate().toString()
    val day = pollenDays(snapshot, now).firstOrNull()?.takeIf { it.date == today } ?: return null
    val types = listOf(PollenType.OLIVE to day.olive, PollenType.GRASS to day.grass).filter { it.second != PollenLevel.NONE }
    return if (types.isEmpty()) null else PollenToday(types)
}

private const val HOUR_S = 3600L

/** Hoy y los 3 días siguientes (H9). */
private const val SHEET_DAYS = 4L

private fun sheetDates(now: Long, zone: ZoneId): List<LocalDate> {
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    return (0 until SHEET_DAYS).map { today.plusDays(it) }
}

/** Las horas de una fecha local: de sus 00:00 a las 00:00 del día siguiente (23, 24 o 25 horas). */
private class DayWindow(date: LocalDate, zone: ZoneId) {
    val start: Long = date.atStartOfDay(zone).toEpochSecond()
    val end: Long = date.plusDays(1).atStartOfDay(zone).toEpochSecond()

    fun contains(hour: AirHour) = hour.time in start until end

    /**
     * El dato tiene valor en o antes de la primera hora del día, en o después de la última (`end − 3600`) y en
     * alguna del día. Así una hora vacía en medio no quita el día y la cola vacía del final sí.
     */
    fun <T : Any> complete(hours: List<AirHour>, value: (AirHour) -> T?): Boolean {
        val withValue = hours.filter { value(it) != null }
        return withValue.any { it.time <= start } &&
            withValue.any { it.time >= end - HOUR_S } &&
            withValue.any { contains(it) }
    }

    /** H7. Nivel del día con la media de sus horas con dato; null si el día no está completo para este tipo. */
    fun pollen(hours: List<AirHour>, type: PollenType, value: (AirHour) -> Double?): PollenLevel? {
        if (!complete(hours, value)) return null
        val values = hours.filter { contains(it) }.mapNotNull(value)
        return pollenLevel(type, values.average().roundToInt())
    }
}
