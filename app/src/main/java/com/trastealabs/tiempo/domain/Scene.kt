package com.trastealabs.tiempo.domain

/** Las once escenas del fondo. [isLight]: escenas claras → cristal oscuro y sombra de texto (H3). */
enum class Scene(val isLight: Boolean) {
    SUN_COOL(false), SUN_MILD(true), SUN_HOT(false), SUN_SCORCHING(false),
    CLOUDS(true), FOG(true), RAIN(false), STORM(false), SNOW(true),
    NIGHT_CLEAR(false), NIGHT_RAIN(false),
}

/**
 * La escena que toca ahora (H1). Con sol, el color sale de la temperatura que se ve ([shownDegrees]):
 * escalones pensados para Córdoba, para que «calor» no salga casi siempre.
 */
fun sceneOf(current: CurrentConditions): Scene = sceneOf(skyOf(current.weatherCode), current.isDay, current.temperatureC)

/** La regla de la escena con un cielo, día o noche y la temperatura que se va a ver (la actual, o la máxima de un día). */
fun sceneOf(sky: Sky, isDay: Boolean, shownC: Double): Scene {
    if (!isDay) return if (sky.isPrecipitation) Scene.NIGHT_RAIN else Scene.NIGHT_CLEAR
    return when (sky) {
        Sky.CLEAR -> {
            val shown = shownDegrees(shownC)
            when {
                shown < 20 -> Scene.SUN_COOL
                shown < 30 -> Scene.SUN_MILD
                shown < 38 -> Scene.SUN_HOT
                else -> Scene.SUN_SCORCHING
            }
        }
        Sky.PARTLY_CLOUDY, Sky.CLOUDY -> Scene.CLOUDS
        Sky.FOG -> Scene.FOG
        Sky.DRIZZLE, Sky.RAIN -> Scene.RAIN
        Sky.STORM -> Scene.STORM
        Sky.SNOW -> Scene.SNOW
    }
}

/**
 * Escena de la página de un día (H5 de la 004). Hoy, la de ahora (también de noche). Los demás días, la que saldría
 * con su cielo ([dayIconSky]) y su máxima, siempre de día.
 */
fun dayScene(snapshot: WeatherSnapshot, date: String, now: Long): Scene {
    if (today(snapshot, now)?.date == date) return sceneOf(snapshot.current)
    val day = snapshot.daily.firstOrNull { it.date == date } ?: return sceneOf(snapshot.current)
    return sceneOf(dayIconSky(date, snapshot.hourly, java.time.ZoneId.of(snapshot.zoneId)), isDay = true, shownC = day.maxC)
}
