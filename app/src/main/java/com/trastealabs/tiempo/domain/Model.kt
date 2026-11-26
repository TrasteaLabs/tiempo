package com.trastealabs.tiempo.domain

import kotlinx.serialization.Serializable

// Instantes en epoch segundos UTC; la zona horaria es la IANA que devuelve Open-Meteo.

@Serializable
data class LatLon(val lat: Double, val lon: Double)

/** [position] ya redondeada a 2 decimales (C9). */
@Serializable
data class Place(val name: String, val countryCode: String, val position: LatLon)

@Serializable
data class WeatherSnapshot(
    val place: Place,
    val zoneId: String,
    val fetchedAt: Long,
    val current: CurrentConditions,
    val hourly: List<HourPoint>,
    val daily: List<DayPoint>,
    val warnings: List<Warning> = emptyList(),
    val warningsFetchedAt: Long? = null,
    /** El día anterior a `daily[0]`; solo para «más/menos que ayer». */
    val yesterday: DayPoint? = null,
    /** Desde la 005; null en un snapshot de antes, y entonces no salen las fichas del aire ni del polen. */
    val air: AirQuality? = null,
    /**
     * Desde la 015: el enlace (público) del tar.gz de AEMET de donde salen [warnings]. Con él, si AEMET no ha publicado
     * nada y el sitio no se ha movido, no se baja ni se analiza nada. null en un snapshot de antes.
     */
    val warningsSource: String? = null,
)

@Serializable
data class CurrentConditions(
    val temperatureC: Double,
    val apparentC: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    // Desde la 002; todos con valor por defecto para seguir leyendo un snapshot.json de antes.
    val windKmh: Double? = null,
    val windGustsKmh: Double? = null,
    val windDirectionDeg: Int? = null,
    val humidityPct: Int? = null,
    val dewPointC: Double? = null,
    val visibilityM: Double? = null,
)

@Serializable
data class HourPoint(
    val time: Long,
    val temperatureC: Double,
    val precipProbability: Int?,
    val precipMm: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    // Desde la 004; null en un snapshot de antes (y entonces las fichas no abren hoja).
    val windKmh: Double? = null,
    val windGustsKmh: Double? = null,
    /** De dónde viene, redondeado. */
    val windDirectionDeg: Int? = null,
    /** Redondeada. */
    val humidityPct: Int? = null,
    val dewPointC: Double? = null,
)

@Serializable
data class DayPoint(
    val date: String,
    val maxC: Double,
    val minC: Double,
    val precipProbabilityMax: Int?,
    val precipSumMm: Double,
    // Desde la 002, como en CurrentConditions.
    val sunrise: Long? = null,
    val sunset: Long? = null,
    val daylightSeconds: Double? = null,
    // Desde la 004. Con fase y sin salida: ese día no sale. Sin fase: datos de antes (no hay luna).
    val moonrise: Long? = null,
    val moonset: Long? = null,
    /** Punto del ciclo: 0 = nueva, 0,5 = llena. */
    val moonPhase: Double? = null,
)

@Serializable
enum class WarningLevel { YELLOW, ORANGE, RED }

@Serializable
data class Warning(
    val id: String,
    val level: WarningLevel,
    val phenomenon: String,
    val areaName: String,
    val onset: Long,
    val expires: Long,
    val probability: String?,
    val description: String,
    val instruction: String?,
    val sent: Long,
)

/** Contenido de snapshot.json. */
@Serializable
data class StoredState(val snapshot: WeatherSnapshot?, val lastAttemptFailed: Boolean)

// --- 006 · Sitios guardados ---

/** Un sitio guardado (C8): nombre, provincia o región, país y coordenadas. Sus datos van en `places/<id>/`. */
@Serializable
data class SavedPlace(
    /** "p" + savedAt en ms; nombre de su carpeta, no cambia nunca. */
    val id: String,
    /** El que se enseña: el nombre en español si lo hay, si no el principal de GeoNames. */
    val name: String,
    /** Provincia (España) o región de primer nivel; "" si no hay. */
    val region: String,
    /** ISO 3166-1 alfa-2. */
    val countryCode: String,
    /** La del lugar de GeoNames, a 2 decimales. */
    val position: LatLon,
    /** Epoch s. */
    val savedAt: Long,
)

fun SavedPlace.toPlace(): Place = Place(name, countryCode, position)

/** Mismo nombre, país y posición a 2 decimales: «ya guardado» (H6). */
fun SavedPlace.sameSpot(name: String, countryCode: String, position: LatLon): Boolean =
    this.name == name && this.countryCode == countryCode && this.position.rounded2() == position.rounded2()

private fun LatLon.rounded2(): LatLon = LatLon(Math.round(lat * 100) / 100.0, Math.round(lon * 100) / 100.0)

/** El sitio que se ve: «donde estoy» o uno guardado. */
sealed interface PlaceRef {
    data object Here : PlaceRef
    data class Saved(val id: String) : PlaceRef
}

/** Contenido de `saved_places.json`, en orden de guardado. */
@Serializable
data class SavedPlaces(val places: List<SavedPlace> = emptyList())

const val MAX_SAVED = 10
