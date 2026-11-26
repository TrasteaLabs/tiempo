package com.trastealabs.tiempo.data

import okhttp3.HttpUrl

/** Escenarios de cada modelo del conjunto (el control y sus miembros). */
val ENSEMBLE_MEMBERS = mapOf("ecmwf_ifs025" to 51, "gfs025" to 31, "icon_seamless" to 40)

/**
 * H10. Peso de una petición como lo cuenta Open-Meteo, en décimas de llamada (24,4 → 244): enteros, para sumar sin
 * errores de redondeo. peso = max(1, variables × N / 10) × max(1, días / 14), con N = modelos (o escenarios en el
 * conjunto) y días = forecast_days (7 si falta) + past_days.
 */
fun openMeteoWeightTenths(url: HttpUrl): Long {
    val variables = listOf("current", "hourly", "daily", "minutely_15").sumOf { url.listParameter(it).size }
    val models = url.listParameter("models")
    val n = when {
        url.host.startsWith("ensemble-api.") -> models.sumOf { ENSEMBLE_MEMBERS[it] ?: 1 }.coerceAtLeast(1)
        else -> models.size.coerceAtLeast(1)
    }
    val days = (url.queryParameter("forecast_days")?.toIntOrNull() ?: 7) + (url.queryParameter("past_days")?.toIntOrNull() ?: 0)
    // variables × N / 10 en décimas es variables × N; el factor de días, redondeado hacia arriba a la décima
    val base = maxOf(10L, variables.toLong() * n)
    return if (days <= 14) base else (base * days + 13) / 14
}

private fun HttpUrl.listParameter(name: String): List<String> =
    queryParameter(name)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
