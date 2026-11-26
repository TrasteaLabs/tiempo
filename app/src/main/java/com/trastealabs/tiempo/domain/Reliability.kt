package com.trastealabs.tiempo.domain

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/** Los seis modelos de la hoja (H2), en el orden en que salen. KNMI y MET Norway no: en España copian al europeo. */
@Serializable
enum class WeatherModel(val forecastId: String) {
    ECMWF("ecmwf_ifs025"),
    ICON("icon_seamless"),
    GFS("gfs_seamless"),
    METEO_FRANCE("meteofrance_seamless"),
    UKMO("ukmo_seamless"),
    GEM("gem_seamless"),
}

/** Los del conjunto (unos 122 escenarios). Solo cuentan para el nivel. */
val ENSEMBLE_MODELS = listOf("ecmwf_ifs025", "gfs025", "icon_seamless")

/** Del mejor al peor. */
enum class ReliabilityLevel { VERY_HIGH, HIGH, MEDIUM, LOW }

@Serializable
data class ModelDay(val model: WeatherModel, val weatherCode: Int, val maxC: Double, val precipMm: Double)

@Serializable
data class DayReliability(
    /** "yyyy-MM-dd" en la hora local del sitio: va por fecha, no por posición en la lista. */
    val date: String,
    /** Escenarios con máxima y lluvia ese día. */
    val scenarios: Int,
    /** De ellos, con lluvia >= [RAIN_MM]. */
    val wetScenarios: Int,
    /** Rango de máximas sin el 10 % más bajo ni el 10 % más alto. */
    val maxLowC: Double,
    val maxHighC: Double,
    /** Solo los modelos que llegan a ese día, en el orden de [WeatherModel]. */
    val models: List<ModelDay> = emptyList(),
)

/** Contenido de `reliability.json` sin la marca de fallo. [position] es la pedida (redondeada), no la de la rejilla. */
@Serializable
data class Reliability(val position: LatLon, val zoneId: String, val fetchedAt: Long, val days: List<DayReliability>)

@Serializable
data class StoredReliability(val reliability: Reliability? = null, val lastFailedAt: Long? = null)

/** 0,9 mm es seco; 1,0 mm, lluvia. */
const val RAIN_MM = 1.0

/** Un día con menos escenarios no lleva nivel. */
const val MIN_SCENARIOS = 20

/** Resume los escenarios de un día. Recorte: se ordenan las máximas, k = n / 10, low = [k], high = [n-1-k]. */
fun summarizeDay(date: String, maxima: List<Double>, precip: List<Double>, models: List<ModelDay>): DayReliability {
    require(maxima.size == precip.size) { "cada escenario lleva máxima y lluvia" }
    val n = maxima.size
    val sorted = maxima.sorted()
    val k = n / 10
    return DayReliability(
        date = date,
        scenarios = n,
        wetScenarios = precip.count { it >= RAIN_MM },
        maxLowC = sorted.getOrElse(k) { 0.0 },
        maxHighC = sorted.getOrElse(n - 1 - k) { 0.0 },
        models = models,
    )
}

/**
 * H1. null con menos de [MIN_SCENARIOS] escenarios. Lluvia: la parte de la mayoría (con lluvia o seca), comparada
 * con enteros. Máxima: separación redondeada a 0,1 antes de comparar (22,3 − 20,3 da 2,0000000000000018).
 * Manda el peor de los dos. Se calcula al leer, no se guarda: si se retocan los umbrales, vale al momento.
 */
fun DayReliability.level(): ReliabilityLevel? {
    if (scenarios < MIN_SCENARIOS) return null
    val majority = maxOf(wetScenarios, scenarios - wetScenarios)
    val rain = when {
        majority * 100 >= 90 * scenarios -> ReliabilityLevel.VERY_HIGH
        majority * 100 >= 75 * scenarios -> ReliabilityLevel.HIGH
        majority * 100 >= 60 * scenarios -> ReliabilityLevel.MEDIUM
        else -> ReliabilityLevel.LOW
    }
    val spreadTenths = ((maxHighC - maxLowC) * 10).roundToLong()
    val max = when {
        spreadTenths <= 20 -> ReliabilityLevel.VERY_HIGH
        spreadTenths <= 40 -> ReliabilityLevel.HIGH
        spreadTenths <= 60 -> ReliabilityLevel.MEDIUM
        else -> ReliabilityLevel.LOW
    }
    return maxOf(rain, max)
}
