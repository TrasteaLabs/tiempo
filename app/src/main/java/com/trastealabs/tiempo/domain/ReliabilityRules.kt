package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** H6: más vieja que esto, no se enseña. */
const val RELIABILITY_VALID_S = 12 * 3600L

/** H6: más lejos que esto, es de otro sitio. */
const val SAME_PLACE_KM = 5.0

/** Tras un fallo (también el «te has pasado del límite»), una hora sin reintentar para no gastar cuota. */
const val RETRY_AFTER_FAILURE_S = 3600L

/** Distancia por el círculo máximo (haversine, R = 6371 km). */
fun distanceKm(a: LatLon, b: LatLon): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceAtMost(1.0)))
}

private const val EARTH_RADIUS_KM = 6371.0

/**
 * H8. Toca pedir si nunca hay, es de otro sitio (> 5 km) o su edad es de 3 h o más entre las 07:00 y las 23:59
 * (hora local de [now] en [zone]), o de 6 h o más entre las 00:00 y las 06:59. La edad va en segundos reales,
 * así que el cambio de hora no la descuadra. Tras un fallo, nada hasta pasada [RETRY_AFTER_FAILURE_S].
 */
fun reliabilityDue(stored: StoredReliability, position: LatLon, now: Long, zone: ZoneId): Boolean {
    stored.lastFailedAt?.let { if (now - it < RETRY_AFTER_FAILURE_S) return false }
    val last = stored.reliability ?: return true
    if (distanceKm(last.position, position) > SAME_PLACE_KM) return true
    val hour = Instant.ofEpochSecond(now).atZone(zone).hour
    val every = if (hour >= 7) 3 * 3600L else 6 * 3600L
    return now - last.fetchedAt >= every
}

/** H6. La guardada si tiene 12 h o menos y es del sitio de la previsión (<= 5 km); si no, null. */
fun validReliability(stored: Reliability?, snapshot: WeatherSnapshot, now: Long): Reliability? =
    stored?.takeIf {
        now - it.fetchedAt <= RELIABILITY_VALID_S && distanceKm(it.position, snapshot.place.position) <= SAME_PLACE_KM
    }
