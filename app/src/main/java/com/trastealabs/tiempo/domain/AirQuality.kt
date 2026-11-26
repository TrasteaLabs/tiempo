package com.trastealabs.tiempo.domain

import kotlinx.serialization.Serializable

/** Una hora de la previsión del aire de CAMS (vía Open-Meteo). Cada campo es null si no llegó. */
@Serializable
data class AirHour(
    /** Epoch s, comienzo de la hora. */
    val time: Long,
    /** `european_aqi`. */
    val aqi: Int? = null,
    val pm25Index: Int? = null,
    val pm10Index: Int? = null,
    val no2Index: Int? = null,
    val ozoneIndex: Int? = null,
    val so2Index: Int? = null,
    /** µg/m³. */
    val pm10: Double? = null,
    /** µg/m³. */
    val dust: Double? = null,
    /** Granos/m³. */
    val olivePollen: Double? = null,
    val grassPollen: Double? = null,
)

@Serializable
data class AirQuality(
    /** La posición redondeada que se pidió. */
    val position: LatLon,
    val fetchedAt: Long,
    val hours: List<AirHour>,
)

/** CAMS Europa se renueva una vez al día y la global dos: pedirlo más a menudo no lo hace más fresco. */
const val AIR_REFRESH_S = 3 * 3600L

/** Toca pedir el aire: no hay, es de otro sitio (> [SAME_PLACE_KM]), tiene [AIR_REFRESH_S] o más, o su edad es negativa (reloj atrasado). */
fun airDue(stored: AirQuality?, position: LatLon, now: Long): Boolean {
    if (stored == null) return true
    if (distanceKm(stored.position, position) > SAME_PLACE_KM) return true
    val age = now - stored.fetchedAt
    return age < 0 || age >= AIR_REFRESH_S
}

/** Lo que se guarda: el nuevo si llegó; si no, el anterior si es del mismo sitio (≤ [SAME_PLACE_KM]); si no, null. */
fun keptAir(fresh: AirQuality?, previous: AirQuality?, position: LatLon): AirQuality? =
    fresh ?: previous?.takeIf { distanceKm(it.position, position) <= SAME_PLACE_KM }
