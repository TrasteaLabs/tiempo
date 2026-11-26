package com.trastealabs.tiempo.domain

import java.time.ZoneId

/**
 * Las fotos del cielo (009, H2–H3): las genera `tools/build_skies.py` en `res/drawable-nodpi`. [file] es el nombre del
 * recurso sin extensión.
 */
enum class SkyPhoto(val file: String) {
    CLEAR_COOL("sky_despejado_frio"), CLEAR_MILD("sky_despejado_templado"), CLEAR_HOT("sky_despejado_calor"),
    SCORCHING("sky_calor_fuerte"), CLOUDS("sky_nubes"), FOG("sky_niebla"), RAIN("sky_lluvia"),
    STORM("sky_tormenta"), SNOW("sky_nieve"), GOLDEN_CLEAR("sky_dorada_despejado"),
    GOLDEN_CLOUDS("sky_dorada_nubes"), NIGHT_CLEAR("sky_noche_despejada"),
    NIGHT_CLOUDS("sky_noche_nubes"), NIGHT_RAIN("sky_noche_lluvia"),
}

/** El momento del día que elige la foto: día, hora dorada (amanecer o atardecer) o noche. */
enum class DayMoment { DAY, GOLDEN, NIGHT }

/** Cargando sin datos: la foto de nubes, quieta (nunca un fondo liso). */
val LOADING_PHOTO = SkyPhoto.CLOUDS

private const val GOLDEN_BEFORE_SUNRISE = 30 * 60L
private const val GOLDEN_AFTER_SUNRISE = 45 * 60L
private const val GOLDEN_BEFORE_SUNSET = 45 * 60L
private const val GOLDEN_AFTER_SUNSET = 30 * 60L

/**
 * GOLDEN: de 30 min antes a 45 min después de la salida y de 45 min antes a 30 min después de la puesta;
 * NIGHT fuera de la luz; DAY el resto. Salida y puesta, las de Open-Meteo de ese día ([day]); sin ellas (datos de antes o
 * un día sin salida ni puesta), [isDay]. Todo en instantes UTC: [zone] no cambia el resultado, va por el contrato.
 */
@Suppress("UNUSED_PARAMETER")
fun momentOf(now: Long, zone: ZoneId, day: DayPoint?, isDay: Boolean): DayMoment {
    val rise = day?.sunrise
    val set = day?.sunset
    if (rise == null || set == null || set <= rise) return if (isDay) DayMoment.DAY else DayMoment.NIGHT
    return when {
        now >= rise - GOLDEN_BEFORE_SUNRISE && now < rise + GOLDEN_AFTER_SUNRISE -> DayMoment.GOLDEN
        now >= set - GOLDEN_BEFORE_SUNSET && now < set + GOLDEN_AFTER_SUNSET -> DayMoment.GOLDEN
        now >= rise + GOLDEN_AFTER_SUNRISE && now < set - GOLDEN_BEFORE_SUNSET -> DayMoment.DAY
        else -> DayMoment.NIGHT
    }
}

/** La tabla «Qué fondo sale» de `diseno.md`. La temperatura es la que se enseña: cortes de hoy (20, 30, 38). */
fun skyPhotoOf(sky: Sky, moment: DayMoment, shownC: Double): SkyPhoto {
    if (moment == DayMoment.NIGHT) {
        return when (sky) {
            Sky.CLEAR -> SkyPhoto.NIGHT_CLEAR
            Sky.DRIZZLE, Sky.RAIN, Sky.STORM -> SkyPhoto.NIGHT_RAIN
            Sky.PARTLY_CLOUDY, Sky.CLOUDY, Sky.FOG, Sky.SNOW -> SkyPhoto.NIGHT_CLOUDS
        }
    }
    val golden = moment == DayMoment.GOLDEN
    return when (sky) {
        Sky.CLEAR -> if (golden) {
            SkyPhoto.GOLDEN_CLEAR
        } else {
            val shown = shownDegrees(shownC)
            when {
                shown < 20 -> SkyPhoto.CLEAR_COOL
                shown < 30 -> SkyPhoto.CLEAR_MILD
                shown < 38 -> SkyPhoto.CLEAR_HOT
                else -> SkyPhoto.SCORCHING
            }
        }
        Sky.PARTLY_CLOUDY, Sky.CLOUDY -> if (golden) SkyPhoto.GOLDEN_CLOUDS else SkyPhoto.CLOUDS
        Sky.FOG -> SkyPhoto.FOG
        Sky.DRIZZLE, Sky.RAIN -> SkyPhoto.RAIN
        Sky.STORM -> SkyPhoto.STORM
        Sky.SNOW -> SkyPhoto.SNOW
    }
}

/** El momento de ahora en el sitio del dato: la salida y la puesta de hoy, o `isDay` de Open-Meteo. */
fun momentNow(snapshot: WeatherSnapshot, now: Long): DayMoment =
    momentOf(now, ZoneId.of(snapshot.zoneId), today(snapshot, now), snapshot.current.isDay)

/** La foto de la principal y de hoy: el tiempo de ahora, el momento y la temperatura que se enseña. */
fun skyPhotoNow(snapshot: WeatherSnapshot, now: Long): SkyPhoto =
    skyPhotoOf(skyOf(snapshot.current.weatherCode), momentNow(snapshot, now), snapshot.current.temperatureC)

/** El cielo de un día de la lista ([dayIconSky]); sin ese día, el de ahora. */
fun daySky(snapshot: WeatherSnapshot, date: String): Sky =
    if (snapshot.daily.any { it.date == date }) dayIconSky(date, snapshot.hourly, ZoneId.of(snapshot.zoneId)) else skyOf(snapshot.current.weatherCode)

/**
 * La foto de un día de la lista: su cielo ([dayIconSky]) con su máxima, siempre de día. Para hoy, la ventana del día usa
 * la de la principal ([skyPhotoNow]).
 */
@Suppress("UNUSED_PARAMETER")
fun dayPhoto(snapshot: WeatherSnapshot, date: String, now: Long): SkyPhoto {
    val day = snapshot.daily.firstOrNull { it.date == date }
    return skyPhotoOf(daySky(snapshot, date), DayMoment.DAY, day?.maxC ?: snapshot.current.temperatureC)
}

/** El destello de arriba (P2): entero, ámbar, solo resplandor, resplandor ámbar o el difuso de la niebla. */
enum class Flare { NONE, FULL, WARM, GLOW, WARM_GLOW, FOG }

/** La luna de la NASA encima de la foto: al 100 % ([CLEAR]), 70 % ([VEILED]) o 40 % ([RAIN]), o ninguna. */
enum class MoonShow(val alpha: Float) { NONE(0f), CLEAR(1f), VEILED(0.7f), RAIN(0.4f) }

/** Lo que va encima de la foto (`effectsOf` de la maqueta). P1 solo pinta la luna; el resto se mueve en P2. */
data class SkyEffects(
    val flare: Flare,
    val cloudPan: Boolean,
    /** 0, 1 o 1,4 (tormenta: un 40 % más de lluvia). */
    val rain: Float,
    val stormFlash: Boolean,
    /** 0, 14 o 70. */
    val stars: Int,
    val moon: MoonShow,
    val fog: Boolean,
    val snow: Boolean,
) {
    companion object {
        val NONE = SkyEffects(Flare.NONE, cloudPan = false, rain = 0f, stormFlash = false, stars = 0, moon = MoonShow.NONE, fog = false, snow = false)
    }
}

/** `effectsOf()` de la maqueta: qué se mueve encima de la foto con ese cielo y en ese momento. */
fun effectsOf(sky: Sky, moment: DayMoment): SkyEffects {
    val none = SkyEffects.NONE
    val golden = moment == DayMoment.GOLDEN
    if (moment == DayMoment.NIGHT) {
        return when (sky) {
            Sky.CLEAR -> none.copy(stars = 70, moon = MoonShow.CLEAR)
            Sky.DRIZZLE, Sky.RAIN -> none.copy(rain = 1f, moon = MoonShow.RAIN)
            Sky.STORM -> none.copy(rain = 1.4f, stormFlash = true, moon = MoonShow.RAIN)
            Sky.PARTLY_CLOUDY, Sky.CLOUDY, Sky.FOG, Sky.SNOW -> none.copy(
                stars = 14, moon = MoonShow.VEILED, cloudPan = true, fog = sky == Sky.FOG, snow = sky == Sky.SNOW,
            )
        }
    }
    return when (sky) {
        Sky.CLEAR -> none.copy(flare = if (golden) Flare.WARM else Flare.FULL)
        Sky.PARTLY_CLOUDY, Sky.CLOUDY -> none.copy(flare = if (golden) Flare.WARM_GLOW else Flare.GLOW, cloudPan = true)
        Sky.FOG -> none.copy(flare = Flare.FOG, fog = true, cloudPan = true)
        Sky.DRIZZLE, Sky.RAIN -> none.copy(rain = 1f)
        Sky.STORM -> none.copy(rain = 1.4f, stormFlash = true)
        Sky.SNOW -> none.copy(flare = Flare.GLOW, snow = true, cloudPan = true)
    }
}

/** Lo de encima de la principal ahora. */
fun effectsNow(snapshot: WeatherSnapshot, now: Long): SkyEffects =
    effectsOf(skyOf(snapshot.current.weatherCode), momentNow(snapshot, now))
