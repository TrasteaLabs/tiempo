package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/** Límites del uso gratis de Open-Meteo que enseña Ajustes (H11). */
const val DAILY_CALL_LIMIT = 10_000L
const val MONTHLY_CALL_LIMIT = 300_000L

/**
 * H12. Llamadas a Open-Meteo gastadas hoy y este mes, en décimas (como `openMeteoWeightTenths`). `day` es
 * "2026-11-03" y `month` "2026-11", en la zona del móvil; vacíos hasta la primera petición.
 */
data class ApiUsage(val day: String = "", val dayTenths: Long = 0, val month: String = "", val monthTenths: Long = 0)

private fun localDate(now: Long, zone: ZoneId): LocalDate = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()

private fun LocalDate.monthKey(): String = toString().take(7)

/** Suma una petición; si ha cambiado el día (o el mes), ese contador empieza de 0. */
fun ApiUsage.plus(weightTenths: Long, now: Long, zone: ZoneId): ApiUsage {
    val date = localDate(now, zone)
    val today = date.toString()
    val thisMonth = date.monthKey()
    return ApiUsage(
        day = today,
        dayTenths = (if (day == today) dayTenths else 0) + weightTenths,
        month = thisMonth,
        monthTenths = (if (month == thisMonth) monthTenths else 0) + weightTenths,
    )
}

/** Redondeado a la llamada; 0 si lo guardado es de otro día, aunque no haya salido ninguna petición desde entonces. */
fun ApiUsage.todayCalls(now: Long, zone: ZoneId): Long =
    if (day == localDate(now, zone).toString()) (dayTenths + 5) / 10 else 0

fun ApiUsage.monthCalls(now: Long, zone: ZoneId): Long =
    if (month == localDate(now, zone).monthKey()) (monthTenths + 5) / 10 else 0

/** Segundos hasta las 00:00 siguientes en [zone] (24 o 25 h el día del cambio de hora), para repintar «hoy». */
fun secondsToNextMidnight(now: Long, zone: ZoneId): Long =
    localDate(now, zone).plusDays(1).atStartOfDay(zone).toEpochSecond() - now

/** "1.046", "10.000": a mano, porque el formato español no agrupa los números de 4 cifras. */
fun groupedThousands(n: Long): String {
    val digits = n.toString()
    return digits.reversed().chunked(3).joinToString(".").reversed()
}

/** Del 1 % en adelante, entero ("2 %"); por debajo, con un decimal y coma ("0,3 %"); lo que no llega, "< 0,1 %". */
fun usagePercent(calls: Long, limit: Long): String {
    if (calls == 0L) return "0 %"
    val tenths = (calls * 1000.0 / limit).roundToLong()
    if (tenths == 0L) return "< 0,1 %"
    return if (tenths >= 10) "${(calls * 100.0 / limit).roundToLong()} %" else "0,$tenths %"
}
