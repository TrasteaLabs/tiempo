package com.trastealabs.tiempo.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Nombre por país en la ficha del modelo: «Francés». */
val WeatherModel.country: String
    get() = when (this) {
        WeatherModel.ECMWF -> "Europeo"
        WeatherModel.ICON -> "Alemán"
        WeatherModel.GFS -> "Americano"
        WeatherModel.METEO_FRANCE -> "Francés"
        WeatherModel.UKMO -> "Británico"
        WeatherModel.GEM -> "Canadiense"
    }

/** Sigla, debajo del nombre en la ficha. */
val WeatherModel.acronym: String
    get() = when (this) {
        WeatherModel.ECMWF -> "ECMWF"
        WeatherModel.ICON -> "ICON"
        WeatherModel.GFS -> "GFS"
        WeatherModel.METEO_FRANCE -> "Météo-France"
        WeatherModel.UKMO -> "UKMO"
        WeatherModel.GEM -> "GEM"
    }

/** H4: se aparta por la lluvia (contra la mayoría) o por la máxima (a 3° o más de la mediana). */
data class ModelVerdict(val day: ModelDay, val rainOff: Boolean, val maxOff: Boolean) {
    val off get() = rainOff || maxOff
}

private val ModelDay.wet get() = precipMm >= RAIN_MM

/** Con número par, la media de las dos centrales. */
private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val k = sorted.size
    return if (k % 2 == 1) sorted[k / 2] else (sorted[k / 2 - 1] + sorted[k / 2]) / 2
}

/** Mojado y seco empatados: nadie se aparta por la lluvia. La distancia a la mediana, redondeada a 0,1 antes de comparar. */
fun modelVerdicts(models: List<ModelDay>): List<ModelVerdict> {
    if (models.isEmpty()) return emptyList()
    val wet = models.count { it.wet }
    val dry = models.size - wet
    val mid = median(models.map { it.maxC })
    return models.map {
        ModelVerdict(
            day = it,
            rainOff = wet != dry && it.wet != (wet > dry),
            maxOff = (abs(it.maxC - mid) * 10).roundToLong() >= 30,
        )
    }
}

private val longDay = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-ES"))

/** "2026-11-05" → "Jueves, 5 de noviembre" (página del día, raya de las hojas de 24 h y TalkBack de la fila). */
fun longDayLabel(date: String): String = longDay.format(LocalDate.parse(date)).replaceFirstChar { it.uppercase() }
