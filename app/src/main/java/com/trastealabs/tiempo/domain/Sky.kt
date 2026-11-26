package com.trastealabs.tiempo.domain

/** Estado del cielo, de menor a mayor gravedad (el orden lo usa [dayIconSky]). */
enum class Sky { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

/** Código WMO de Open-Meteo → [Sky]. Lo desconocido, nublado. */
fun skyOf(wmoCode: Int): Sky = when (wmoCode) {
    0, 1 -> Sky.CLEAR
    2 -> Sky.PARTLY_CLOUDY
    3 -> Sky.CLOUDY
    45, 48 -> Sky.FOG
    51, 53, 55, 56, 57 -> Sky.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
    71, 73, 75, 77, 85, 86 -> Sky.SNOW
    95, 96, 99 -> Sky.STORM
    else -> Sky.CLOUDY
}

/** Llueve, nieva o hay tormenta: lo que cuenta como "día con precipitación" en [dayIconSky]. */
val Sky.isPrecipitation: Boolean get() = this >= Sky.DRIZZLE
