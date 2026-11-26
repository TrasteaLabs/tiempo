package com.trastealabs.tiempo

import com.trastealabs.tiempo.domain.CurrentConditions
import com.trastealabs.tiempo.domain.DayPoint
import com.trastealabs.tiempo.domain.HourPoint
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.Place
import com.trastealabs.tiempo.domain.WeatherSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Datos inventados (C5): una ciudad de ejemplo que no es ninguna ubicación real del usuario. */
object TestData {
    val zone: ZoneId = ZoneId.of("Europe/Madrid")
    val place = Place("Villa Ejemplo", "ES", LatLon(40.0, -3.0))

    fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toEpochSecond()

    /**
     * Previsión que empieza ayer a las 00:00 local y llega a 10 días vista.
     * [yesterdayTempC] es la temperatura de ayer a la hora de [now]; `null` quita esa hora (sin dato de ayer).
     */
    fun snapshot(
        now: Long,
        currentC: Double = 12.0,
        yesterdayTempC: Double? = 15.0,
        fetchedAt: Long = now,
        place: Place = this.place,
    ): WeatherSnapshot {
        val nowLocal = Instant.ofEpochSecond(now).atZone(zone)
        val yesterdayStart = nowLocal.toLocalDate().minusDays(1).atStartOfDay(zone)
        val yesterdayHour = nowLocal.withMinute(0).withSecond(0).withNano(0).minusDays(1).toEpochSecond()
        val hourly = (0 until 11 * 24).mapNotNull { i ->
            val t = yesterdayStart.plusHours(i.toLong()).toEpochSecond()
            val temp = if (t == yesterdayHour) yesterdayTempC ?: return@mapNotNull null else 10.0
            HourPoint(t, temp, 20, 0.0, 3, true)
        }
        val today = nowLocal.toLocalDate()
        val daily = (0 until 10).map { i ->
            DayPoint(today.plusDays(i.toLong()).toString(), 18.0 + i, 9.0 + i, 30, 1.5)
        }
        return WeatherSnapshot(
            place = place,
            zoneId = zone.id,
            fetchedAt = fetchedAt,
            current = CurrentConditions(currentC, currentC - 2.0, 3, true),
            hourly = hourly,
            daily = daily,
        )
    }
}
