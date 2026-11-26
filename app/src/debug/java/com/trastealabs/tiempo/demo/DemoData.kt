package com.trastealabs.tiempo.demo

import com.trastealabs.tiempo.data.RefreshLogEntry
import com.trastealabs.tiempo.data.RefreshOutcome
import com.trastealabs.tiempo.data.ai.AiStatus
import com.trastealabs.tiempo.data.ai.AiUsage
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.domain.AiReport
import com.trastealabs.tiempo.domain.AskAnswer
import com.trastealabs.tiempo.domain.AskFailure
import com.trastealabs.tiempo.domain.AskKind
import com.trastealabs.tiempo.domain.DayFacts
import com.trastealabs.tiempo.domain.reportFacts
import com.trastealabs.tiempo.domain.AirHour
import com.trastealabs.tiempo.domain.AirQuality
import com.trastealabs.tiempo.domain.ApiUsage
import com.trastealabs.tiempo.domain.CurrentConditions
import com.trastealabs.tiempo.domain.DayPoint
import com.trastealabs.tiempo.domain.DayReliability
import com.trastealabs.tiempo.domain.HourPoint
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.ModelDay
import com.trastealabs.tiempo.domain.Place
import com.trastealabs.tiempo.domain.Reliability
import com.trastealabs.tiempo.domain.SavedPlace
import com.trastealabs.tiempo.domain.Scene
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.WarningLevel
import com.trastealabs.tiempo.domain.WeatherModel
import com.trastealabs.tiempo.domain.WeatherModel.ECMWF
import com.trastealabs.tiempo.domain.WeatherModel.GEM
import com.trastealabs.tiempo.domain.WeatherModel.GFS
import com.trastealabs.tiempo.domain.WeatherModel.ICON
import com.trastealabs.tiempo.domain.WeatherModel.METEO_FRANCE
import com.trastealabs.tiempo.domain.WeatherModel.UKMO
import com.trastealabs.tiempo.domain.WeatherSnapshot
import com.trastealabs.tiempo.refresh.AemetOutcome
import com.trastealabs.tiempo.refresh.RefreshOrigin
import com.trastealabs.tiempo.ui.AiPanel
import com.trastealabs.tiempo.ui.AskEntry
import com.trastealabs.tiempo.ui.AskState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import com.trastealabs.tiempo.domain.DayMoment
import com.trastealabs.tiempo.domain.Sky
import com.trastealabs.tiempo.domain.SkyEffects
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.domain.effectsOf
import kotlin.math.PI
import kotlin.math.cos

/** Lo que se puede añadir a la escena: aviso, sin conexión o widget vacío (antes de abrir la app). */
enum class DemoBadge { WARNING, OFFLINE, EMPTY }

/** Escena y estado de un `Intent` (`--es scene STORM --es badge WARNING`); lo desconocido, sol templado y nada. */
/** 007: un sitio inventado del hemisferio sur (a −34,6 de latitud), con la hora de la demo. */
val demoSouthPlace = Place("Puerto Inventado", "AR", LatLon(-34.6, -58.4))

fun demoScene(name: String?): Scene = Scene.entries.firstOrNull { it.name == name?.uppercase() } ?: Scene.SUN_MILD

fun demoBadge(name: String?): DemoBadge? = DemoBadge.entries.firstOrNull { it.name == name?.uppercase() }

/**
 * 009: la foto del cielo de `--es photo CLEAR_COOL` (nombre de [SkyPhoto], o el del recurso: `sky_despejado_frio`), con lo
 * que lleva encima en su cielo y momento de ejemplo (los `SAMPLE` de la maqueta: de noche, la luna). `null` si no hay
 * extra o no se conoce.
 */
fun demoPhoto(name: String?): Pair<SkyPhoto, SkyEffects>? {
    val photo = SkyPhoto.entries.firstOrNull { it.name == name?.uppercase() || it.file == name?.lowercase() } ?: return null
    val (sky, moment) = when (photo) {
        SkyPhoto.CLEAR_COOL, SkyPhoto.CLEAR_MILD, SkyPhoto.CLEAR_HOT, SkyPhoto.SCORCHING -> Sky.CLEAR to DayMoment.DAY
        SkyPhoto.CLOUDS -> Sky.CLOUDY to DayMoment.DAY
        SkyPhoto.FOG -> Sky.FOG to DayMoment.DAY
        SkyPhoto.RAIN -> Sky.RAIN to DayMoment.DAY
        SkyPhoto.STORM -> Sky.STORM to DayMoment.DAY
        SkyPhoto.SNOW -> Sky.SNOW to DayMoment.DAY
        SkyPhoto.GOLDEN_CLEAR -> Sky.CLEAR to DayMoment.GOLDEN
        SkyPhoto.GOLDEN_CLOUDS -> Sky.CLOUDY to DayMoment.GOLDEN
        SkyPhoto.NIGHT_CLEAR -> Sky.CLEAR to DayMoment.NIGHT
        SkyPhoto.NIGHT_CLOUDS -> Sky.CLOUDY to DayMoment.NIGHT
        SkyPhoto.NIGHT_RAIN -> Sky.RAIN to DayMoment.NIGHT
    }
    return photo to effectsOf(sky, moment)
}

/**
 * Tiempo inventado de cada escena (los de las maquetas de Cristal), en un sitio de ejemplo: código WMO,
 * día o noche, temperaturas, viento, humedad y aviso.
 */
private data class DemoWeather(
    val code: Int, val isDay: Boolean, val t: Double, val feels: Double, val max: Double, val min: Double,
    val wind: Double, val windDeg: Int, val gusts: Double, val humidity: Int, val dewPoint: Double,
    val visibility: Double, val rainPct: Int, val warning: Pair<WarningLevel, String>,
)

private fun weatherOf(scene: Scene): DemoWeather = when (scene) {
    Scene.SUN_COOL -> DemoWeather(0, true, 14.0, 13.0, 16.0, 4.0, 14.0, 0, 30.0, 52, 4.0, 40_000.0, 0, WarningLevel.YELLOW to "vientos")
    Scene.SUN_MILD -> DemoWeather(0, true, 24.0, 24.0, 26.0, 12.0, 11.0, 270, 22.0, 40, 9.0, 40_000.0, 0, WarningLevel.YELLOW to "vientos")
    Scene.SUN_HOT -> DemoWeather(0, true, 34.0, 35.0, 36.0, 21.0, 9.0, 270, 20.0, 31, 9.0, 40_000.0, 0, WarningLevel.YELLOW to "temperaturas máximas")
    Scene.SUN_SCORCHING -> DemoWeather(0, true, 41.0, 42.0, 43.0, 26.0, 8.0, 135, 18.0, 14, 7.0, 40_000.0, 0, WarningLevel.ORANGE to "temperaturas máximas")
    Scene.CLOUDS -> DemoWeather(3, true, 16.0, 15.0, 18.0, 10.0, 12.0, 225, 25.0, 68, 10.0, 24_000.0, 10, WarningLevel.YELLOW to "vientos")
    Scene.FOG -> DemoWeather(45, true, 8.0, 6.0, 14.0, 6.0, 3.0, 90, 8.0, 96, 7.4, 320.0, 0, WarningLevel.YELLOW to "nieblas")
    Scene.RAIN -> DemoWeather(61, true, 13.0, 11.0, 15.0, 10.0, 22.0, 225, 45.0, 91, 11.0, 8_000.0, 80, WarningLevel.YELLOW to "lluvias")
    Scene.STORM -> DemoWeather(95, true, 21.0, 22.0, 24.0, 15.0, 28.0, 200, 70.0, 84, 18.0, 6_000.0, 70, WarningLevel.ORANGE to "tormentas")
    Scene.SNOW -> DemoWeather(71, true, 0.0, -4.0, 2.0, -3.0, 16.0, 0, 35.0, 88, -2.0, 1_500.0, 60, WarningLevel.YELLOW to "nevadas")
    Scene.NIGHT_CLEAR -> DemoWeather(0, false, 11.0, 10.0, 19.0, 8.0, 5.0, 45, 12.0, 64, 4.0, 40_000.0, 0, WarningLevel.YELLOW to "vientos")
    Scene.NIGHT_RAIN -> DemoWeather(61, false, 10.0, 7.0, 14.0, 8.0, 25.0, 180, 50.0, 94, 9.0, 7_000.0, 85, WarningLevel.ORANGE to "lluvias")
}

private val demoZone: ZoneId = ZoneId.of("Europe/Madrid")

/** `--es clock HH:mm`: esa hora de hoy (en la zona de la demo); sin ella o mal escrita, [now]. */
fun demoNow(clock: String?, now: Long): Long {
    val time = clock?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return now
    return Instant.ofEpochSecond(now).atZone(demoZone).toLocalDate().atTime(time).atZone(demoZone).toEpochSecond()
}
internal val demoPlace = Place("Madrid", "ES", LatLon(40.42, -3.70))

/**
 * Días variados para la página del día (004, P2): a partir de mañana, uno nublado, uno de lluvia, uno en calma y uno de
 * calor, así cada página lleva una escena distinta. Hoy y los demás, el tiempo de la escena.
 */
private enum class DemoDay { SCENE, CLOUDY, RAINY, CALM, HOT }

private fun demoDay(offset: Int): DemoDay = when (offset) {
    1 -> DemoDay.CLOUDY
    2 -> DemoDay.RAINY
    3 -> DemoDay.CALM
    4 -> DemoDay.HOT
    else -> DemoDay.SCENE
}

/**
 * Un `snapshot.json` inventado que da [scene] en [now]: previsión desde ayer a las 00:00 hasta 10 días
 * vista, con viento, humedad y sol, y días variados ([demoDay]). [DemoBadge.EMPTY] da el estado de antes de abrir la app.
 * Con [place], [zone] y [fetched], el de un sitio guardado de la demo del 006. Con [moonPhase] (007), la fase de la luna
 * de hoy (los demás días, 0,034 más cada uno).
 */
fun demoState(
    scene: Scene,
    badge: DemoBadge?,
    now: Long,
    place: Place = demoPlace,
    zone: ZoneId = demoZone,
    fetched: Long? = null,
    moonPhase: Double? = null,
): StoredState {
    if (badge == DemoBadge.EMPTY) return StoredState(null, lastAttemptFailed = false)
    val w = weatherOf(scene)
    // Sin conexión: el dato es de hace dos horas.
    val fetchedAt = fetched ?: if (badge == DemoBadge.OFFLINE) now - 2 * 3600 else now
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()

    // Curva diaria entre la mínima (06:00) y la máxima (16:00); ayer, 2° más.
    fun tempAt(hour: Int, dayOffset: Int): Double {
        val k = (1 - cos(2 * PI * (hour - 4) / 24)) / 2
        return w.min + (w.max - w.min) * k + if (dayOffset < 0) 2.0 else dayOffset * 0.3
    }
    val start = today.minusDays(1).atStartOfDay(zone)
    val hourly = (0 until 11 * 24).map { i ->
        val t = start.plusHours(i.toLong())
        val day = t.hour in 8..18
        val kind = demoDay(i / 24 - 1)
        val rainPct = when (kind) { DemoDay.RAINY -> 85; DemoDay.CLOUDY -> 20; DemoDay.CALM, DemoDay.HOT -> 0; DemoDay.SCENE -> w.rainPct }
        val code = when (kind) { DemoDay.CLOUDY -> 3; DemoDay.RAINY -> 61; DemoDay.CALM, DemoDay.HOT -> 0; DemoDay.SCENE -> w.code }
        val windScale = when (kind) { DemoDay.CALM -> 0.15; DemoDay.RAINY -> 1.8; else -> 1.0 }
        HourPoint(
            time = t.toEpochSecond(),
            temperatureC = tempAt(t.hour, i / 24 - 1) + if (kind == DemoDay.HOT) 12.0 else 0.0,
            precipProbability = if (rainPct > 0) (rainPct - (i % 5) * 10).coerceAtLeast(0) else 0,
            precipMm = if (rainPct >= 50) 0.4 + (i % 4) * 0.5 else 0.0,
            weatherCode = if (i % 7 == 3 && code == 0) 2 else code,
            isDay = day,
            // Viento que sube por la tarde, rachas al doble, y humedad al revés que la temperatura.
            windKmh = windScale * w.wind * (0.6 + 0.6 * (1 - cos(2 * PI * (t.hour - 4) / 24)) / 2),
            windGustsKmh = windScale * w.gusts * (0.6 + 0.6 * (1 - cos(2 * PI * (t.hour - 4) / 24)) / 2),
            windDirectionDeg = (w.windDeg + (i % 6) * 10) % 360,
            humidityPct = (w.humidity + 20 - 30 * (1 - cos(2 * PI * (t.hour - 4) / 24)) / 2).toInt().coerceIn(5, 100),
            dewPointC = w.dewPoint + (i % 3) * 0.4,
        )
    }

    fun dayPoint(date: LocalDate, offset: Int): DayPoint {
        // Amanece a las 07:58 y anochece a las 18:21 (la maqueta); cada día, 1 min antes de anochecer.
        var sunrise = date.atTime(LocalTime.of(7, 58)).atZone(zone).toEpochSecond()
        var sunset = date.atTime(LocalTime.of(18, 21)).atZone(zone).toEpochSecond() - offset * 60
        if (!w.isDay && offset == 0) {
            // Escenas de noche (010, P3): que ahora sea de noche de verdad, para que el widget pinte su foto y su luna.
            sunrise = now + 6 * 3600
            sunset = now - 3 * 3600
        } else if (w.isDay && offset == 0 && (now < sunrise || now > sunset)) {
            // 018 P3: una escena de día lanzada de noche (las capturas del OPPO a las 00:00): que ahora sea de día, para ver su foto.
            sunrise = now - 3 * 3600
            sunset = now + 6 * 3600
        }
        val kind = demoDay(offset)
        val hot = if (kind == DemoDay.HOT) 12.0 else 0.0
        val rainPct = when (kind) { DemoDay.RAINY -> 85; DemoDay.CLOUDY -> 20; DemoDay.CALM, DemoDay.HOT -> 0; DemoDay.SCENE -> w.rainPct }
        return DayPoint(
            date = date.toString(),
            maxC = w.max + offset * 0.3 + hot,
            minC = w.min + offset * 0.3 + hot,
            precipProbabilityMax = (rainPct - if (kind == DemoDay.SCENE) offset * 5 else 0).coerceAtLeast(0),
            precipSumMm = if (rainPct >= 50) 3.2 else 0.0,
            sunrise = sunrise,
            sunset = sunset,
            daylightSeconds = (sunset - sunrise).toDouble(),
            // La luna sale a las 02:47 y se retrasa ~50 min por día; el día 6 no sale (como el 1-nov en Córdoba).
            moonrise = if (offset == 6) null else date.atTime(LocalTime.of(2, 47)).atZone(zone).toEpochSecond() + offset * 50 * 60,
            moonset = date.atTime(LocalTime.of(15, 43)).atZone(zone).toEpochSecond() + offset * 45 * 60,
            moonPhase = ((moonPhase ?: 0.841) + offset * 0.034).mod(1.0),
        )
    }

    val warnings = if (badge == DemoBadge.WARNING) {
        val (level, phenomenon) = w.warning
        listOf(
            Warning(
                id = "demo",
                level = level,
                phenomenon = phenomenon,
                areaName = "Zona de ejemplo",
                onset = now - 3600,
                expires = now + 5 * 3600,
                probability = "40%-70%",
                description = "Aviso inventado para ver la escena.",
                instruction = null,
                sent = now - 7200,
            ),
        )
    } else {
        emptyList()
    }

    val snapshot = WeatherSnapshot(
        place = place,
        zoneId = zone.id,
        fetchedAt = fetchedAt,
        current = CurrentConditions(
            temperatureC = w.t,
            apparentC = w.feels,
            weatherCode = w.code,
            isDay = w.isDay,
            windKmh = w.wind,
            windGustsKmh = w.gusts,
            windDirectionDeg = w.windDeg,
            humidityPct = w.humidity,
            dewPointC = w.dewPoint,
            visibilityM = w.visibility,
        ),
        hourly = hourly,
        daily = (0 until 10).map { dayPoint(today.plusDays(it.toLong()), it) },
        warnings = warnings,
        warningsFetchedAt = fetchedAt,
        yesterday = dayPoint(today.minusDays(1), -2),
    )
    return StoredState(snapshot, lastAttemptFailed = badge == DemoBadge.OFFLINE)
}

/** Un modelo de la maqueta: cielo (código WMO), máxima y lluvia. */
private fun md(model: WeatherModel, code: Int, max: Double, mm: Double) = ModelDay(model, code, max, mm)

private const val CLEAR = 0
private const val PARTLY = 2
private const val CLOUDY = 3
private const val DRIZZLE = 53
private const val RAIN = 61

/**
 * Los 10 días de la maqueta del encargo 003 desde hoy: escenarios, con lluvia, rango
 * de máximas y modelos, para ver el anillo en sus cuatro niveles y la hoja en sus tres frases
 * (hoy coinciden, el día 2 se aparta el francés, el día 5 van divididos).
 */
fun demoReliability(now: Long): Reliability {
    val today = Instant.ofEpochSecond(now).atZone(demoZone).toLocalDate()
    fun day(i: Int, scenarios: Int, wet: Int, low: Double, high: Double, vararg models: ModelDay) =
        DayReliability(today.plusDays(i.toLong()).toString(), scenarios, wet, low, high, models.toList())
    val days = listOf(
        day(0, 122, 0, 22.0, 24.0, md(ECMWF, CLEAR, 23.0, 0.0), md(ICON, CLEAR, 24.0, 0.0), md(GFS, PARTLY, 23.0, 0.0),
            md(METEO_FRANCE, CLEAR, 22.0, 0.0), md(UKMO, PARTLY, 23.0, 0.0), md(GEM, PARTLY, 22.0, 0.0)),
        day(1, 122, 5, 20.0, 22.0, md(ECMWF, PARTLY, 21.0, 0.0), md(ICON, PARTLY, 22.0, 0.0), md(GFS, PARTLY, 21.0, 0.1),
            md(METEO_FRANCE, CLOUDY, 20.0, 0.2), md(UKMO, PARTLY, 21.0, 0.0), md(GEM, CLEAR, 22.0, 0.0)),
        day(2, 122, 34, 19.0, 21.0, md(ECMWF, PARTLY, 20.0, 0.1), md(ICON, CLEAR, 21.0, 0.0), md(GFS, PARTLY, 20.0, 0.2),
            md(METEO_FRANCE, RAIN, 17.0, 2.8), md(UKMO, PARTLY, 20.0, 0.0), md(GEM, CLOUDY, 19.0, 0.4)),
        day(3, 122, 9, 18.0, 22.0, md(ECMWF, CLEAR, 20.0, 0.0), md(ICON, CLEAR, 21.0, 0.0), md(GFS, CLEAR, 19.0, 0.0),
            md(METEO_FRANCE, PARTLY, 20.0, 0.0), md(UKMO, CLEAR, 20.0, 0.0), md(GEM, CLEAR, 21.0, 0.0)),
        day(4, 122, 15, 21.0, 23.0, md(ECMWF, CLEAR, 22.0, 0.0), md(ICON, CLEAR, 23.0, 0.0), md(GFS, PARTLY, 21.0, 0.0),
            md(UKMO, CLEAR, 22.0, 0.0), md(GEM, PARTLY, 21.0, 0.1)),
        day(5, 122, 66, 17.0, 23.0, md(ECMWF, RAIN, 20.0, 3.4), md(ICON, PARTLY, 23.0, 0.2), md(GFS, RAIN, 18.0, 6.1),
            md(UKMO, DRIZZLE, 21.0, 1.2), md(GEM, CLOUDY, 22.0, 0.4)),
        day(6, 122, 87, 18.0, 20.0, md(ECMWF, RAIN, 19.0, 5.8), md(ICON, RAIN, 18.0, 7.4), md(GFS, RAIN, 19.0, 9.0),
            md(UKMO, DRIZZLE, 20.0, 2.1), md(GEM, CLOUDY, 21.0, 0.6)),
        day(7, 82, 48, 16.0, 20.0, md(ECMWF, RAIN, 18.0, 4.2), md(GFS, RAIN, 19.0, 5.5), md(GEM, CLOUDY, 20.0, 0.3)),
        day(8, 82, 10, 14.0, 21.0, md(ECMWF, CLOUDY, 18.0, 0.4), md(GFS, PARTLY, 19.0, 0.2), md(GEM, CLOUDY, 17.0, 0.8)),
        day(9, 82, 30, 13.0, 22.0, md(ECMWF, PARTLY, 19.0, 0.1), md(GFS, CLEAR, 21.0, 0.0), md(GEM, CLOUDY, 17.0, 0.5)),
    )
    return Reliability(demoPlace.position, demoZone.id, now, days)
}

/** P3 del 003: 214 llamadas hoy y 1.046 este mes, en la zona del móvil (la del contador). */
fun demoUsage(now: Long): ApiUsage {
    val today = Instant.ofEpochSecond(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    return ApiUsage(day = today, dayTenths = 2140, month = today.take(7), monthTenths = 10460)
}

/** 016 P3: la sección «IA» de Ajustes con una clave inventada que acaba en 1234 y un gasto de ejemplo. */
fun demoAi(now: Long): AiPanel {
    val today = Instant.ofEpochSecond(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    return AiPanel(
        provider = AiProvider.CLAUDE,
        hints = mapOf(AiProvider.CLAUDE to "1234"),
        usage = AiUsage(
            day = today, dayHere = 3, daySaved = 1, dayAsk = 3, month = today.take(7),
            monthCalls = 47, monthInputTokens = 118_400, monthOutputTokens = 69_800,
        ),
        status = AiStatus(at = now - 25 * 60, failure = null),
    )
}

/**
 * 016 P4: un parte inventado para `--es ai dry|rain|wet`, en el tono de la spec y con las cifras de la escena de
 * [snapshot] (salen de los mismos hechos que se mandarían a la IA, así que son coherentes y pasan `visibleReport`).
 * `dry` y `rain` llevan el de hoy y el de cada día; `wet` añade la explicación del aviso de la demo. Otro valor, sin parte.
 */
fun demoReport(kind: String?, snapshot: WeatherSnapshot, now: Long): AiReport? {
    if (kind !in listOf("dry", "rain", "wet")) return null
    val facts = reportFacts(snapshot, now, ZoneId.of(snapshot.zoneId)) ?: return null
    fun temps(d: DayFacts): String {
        val lo = "${d.minC}°" + (d.minAt?.let { " a las $it" } ?: "")
        val hi = "${d.maxC}°" + (d.maxAt?.let { " hacia las $it" } ?: "")
        return if (d.rainAllDay) "no pasa de ${d.maxC}°, con mínima de $lo" else "entre $lo y $hi"
    }
    fun rain(d: DayFacts): String = when {
        d.rainAllDay -> "Lluvia todo el día" + if (d.rainMm >= 10) "; cae bastante, unos ${d.rainMm} mm." else "."
        d.rain.isNotEmpty() -> "Lluvia sobre todo de ${d.rain.first().from} a ${d.rain.last().to}."
        else -> "Sin lluvia."
    }
    val today = "Hoy ${temps(facts.today)}. ${rain(facts.today)}"
    val days = facts.days.associate { d ->
        d.date to "${d.label.replaceFirstChar { it.uppercase() }} ${temps(d)}. ${rain(d)}"
    }
    val warnings = if (kind == "wet") {
        facts.warnings.associate { it.id to "Puede llover con ganas durante buena parte del día; mejor salir con paraguas y sin prisas." }
    } else emptyMap()
    return AiReport(now, AiProvider.CLAUDE, facts.basis, today, days, warnings)
}

/** 018 P4: los ocho estados de `--es ask`; `closed` y `cap` solo enseñan la píldora (con el tope, apagada). */
val demoAskStates = listOf("closed", "writing", "asking", "answer", "stale", "offtopic", "fail", "cap")

/** La pregunta de las capturas. */
const val DEMO_QUESTION = "¿Puedo tender mañana?"

/** 018 P4: la entrada de la píldora de cada estado de la demo; sin `--es ask`, no hay píldora. */
fun demoAskEntry(kind: String?): AskEntry = when (kind) {
    null -> AskEntry.Hidden
    "cap" -> AskEntry.Capped(20)
    in demoAskStates -> AskEntry.Open
    else -> AskEntry.Hidden
}

/** 018 P4: lo que enseña la hoja en cada estado de la demo (`closed` y `cap` no la abren). `stale`: datos de hace 5 h. */
fun demoAskState(kind: String?, now: Long, dataAt: Long): AskState = when (kind) {
    "asking" -> AskState.Asking(DEMO_QUESTION)
    "answer" -> AskState.Answered(DEMO_QUESTION, demoAnswer(AskKind.ANSWER, now, dataAt))
    "stale" -> AskState.Answered(DEMO_QUESTION, demoAnswer(AskKind.ANSWER, now, now - 5 * 3600))
    "offtopic" -> AskState.Answered("¿Quién ganó la liga?", demoAnswer(AskKind.OFF_TOPIC, now, dataAt))
    "fail" -> AskState.Failed(DEMO_QUESTION, AskFailure.UNSURE)
    else -> AskState.Writing
}

/** ¿La demo abre la hoja al empezar? */
fun demoAskOpens(kind: String?): Boolean = kind != null && kind in demoAskStates && kind != "closed" && kind != "cap"

fun demoAnswer(kind: AskKind, now: Long, dataAt: Long) = AskAnswer(
    kind = kind,
    text = if (kind == AskKind.ANSWER) "Sí, hoy secará bien: hacia las 16:00 habrá sol y 21°." else "",
    at = now,
    dataAt = dataAt,
)

/**
 * 018 P3: la frase del widget 4×2 de `--es phrase <texto>`. `short` y `long` son las de ejemplo (la de 55 caracteres lleva «ñ»,
 * tilde y «°»); `none` guarda un parte sin frase (el widget de siempre); cualquier otro texto, tal cual (ya recortado a 55).
 */
fun demoPhrase(kind: String?): String? = when (kind) {
    null, "none" -> null
    "short" -> "Sol toda la tarde"
    "long" -> "Mañana fresca, de 12°; sol y viento fuerte por la tarde"
    else -> kind.take(55)
}

/** 018 P3: un parte inventado con solo la frase del widget, con la huella de [snapshot] a [now] (así `visibleReport` lo da por bueno). */
fun demoWidgetReport(phrase: String?, snapshot: WeatherSnapshot, now: Long): AiReport? {
    val facts = reportFacts(snapshot, now, ZoneId.of(snapshot.zoneId)) ?: return null
    return AiReport(now, AiProvider.CLAUDE, facts.basis, widget = phrase)
}

/**
 * 015 P2: un día inventado de refrescos para la sección «Refrescos» de Ajustes (`--es screen settings`): cada 30 min,
 * con un hueco de 3 h 20 min en medio (el móvil dormido), un «sin conexión», un AEMET que falla, dos alarmas y los KB de
 * cuando AEMET publicó algo. Todo relativo a [now]; la lista sale la más vieja primero, como el registro de verdad.
 */
fun demoRefreshes(now: Long): List<RefreshLogEntry> {
    val before = (0..29).map { k -> 540 + 30 * k }.reversed()   // de hace 23 h 30 min a hace 9 h
    val after = (0..11).map { j -> 340 - 30 * j }               // de hace 5 h 40 min a hace 10 min
    fun entry(minutesAgo: Int): RefreshLogEntry {
        val at = now - minutesAgo * 60L
        val worker = RefreshOrigin.WORKER
        val ok = RefreshOutcome.OK
        val same = AemetOutcome.UNCHANGED
        return when (minutesAgo) {
            1350 -> RefreshLogEntry(at, worker, ok, AemetOutcome.NEW, 352_256)
            1230 -> RefreshLogEntry(at, worker, ok, AemetOutcome.FAILED)
            960 -> RefreshLogEntry(at, worker, RefreshOutcome.OFFLINE, AemetOutcome.NOT_APPLICABLE)
            780 -> RefreshLogEntry(at, worker, ok, same, 1_536)
            660, 340 -> RefreshLogEntry(at, RefreshOrigin.ALARM, ok, same)
            600 -> RefreshLogEntry(at, worker, ok, AemetOutcome.NEW, 188_416)
            2 -> RefreshLogEntry(at, RefreshOrigin.APP, ok, same)
            else -> RefreshLogEntry(at, worker, ok, same)
        }
    }
    return (before + after + 2).map(::entry)
}

/** Qué empeora el aire en la demo: el subíndice que gana (y, con calima, el polvo). */
private enum class DemoCause { NONE, OZONE, NO2, PARTICLES, DUST }

/** Un caso de `AIRS` de la maqueta del 005: la cifra de ahora, su causa y la peor hora de hoy y de los 3 días siguientes. */
private data class DemoAir(val now: Int, val cause: DemoCause, val worst: List<Int>)

private val demoAirs = mapOf(
    "good" to DemoAir(16, DemoCause.NONE, listOf(18, 23, 16, 19)),
    "fair" to DemoAir(35, DemoCause.OZONE, listOf(42, 38, 45, 31)),
    "moderate" to DemoAir(52, DemoCause.NO2, listOf(70, 58, 52, 47)),
    "poor" to DemoAir(68, DemoCause.DUST, listOf(72, 64, 48, 35)),
    "very_poor" to DemoAir(88, DemoCause.PARTICLES, listOf(97, 85, 66, 44)),
    "extreme" to DemoAir(134, DemoCause.DUST, listOf(134, 112, 81, 55)),
)

/** Granos/m³ que dan cada nivel (0 nada, 1 bajo, 2 moderado, 3 alto): olivo y gramíneas. */
private val oliveGrains = listOf(0.0, 30.0, 120.0, 300.0)
private val grassGrains = listOf(0.0, 15.0, 40.0, 80.0)

/** Niveles de hoy y de los 3 días siguientes: recorren todos (olivo alto, moderado, bajo y nada; gramíneas al revés). */
private val oliveDays = listOf(3, 2, 1, 0)
private val grassDays = listOf(2, 3, 0, 1)

/**
 * `--es air good|fair|moderate|poor|very_poor|extreme|none` (por defecto `fair`) y `--es pollen none|olive|grass|both`
 * (por defecto `none`, lo de noviembre: el polen a 0). Como la API: 120 horas desde las 00:00 de hoy, el índice en
 * todas y el polen hasta la hora 97, así que salen hoy y los 3 días siguientes. Cada día, la peor hora a las 15:00
 * (a las 16:00 si ahora son las 15) y la cifra de la maqueta en la hora de ahora.
 */
fun demoAir(air: String?, pollen: String?, now: Long, zone: ZoneId): AirQuality? {
    if (air == "none") return null
    val case = demoAirs[air] ?: demoAirs.getValue("fair")
    val start = Instant.ofEpochSecond(now).atZone(zone).toLocalDate().atStartOfDay(zone)
    val nowHour = Instant.ofEpochSecond(now).atZone(zone).withMinute(0).withSecond(0).withNano(0).toEpochSecond()
    val peakHour = if (Instant.ofEpochSecond(now).atZone(zone).hour == 15) 16 else 15
    val withOlive = pollen == "olive" || pollen == "both"
    val withGrass = pollen == "grass" || pollen == "both"
    val hours = (0 until 120).map { i ->
        val t = start.plusHours(i.toLong())
        val day = t.toLocalDate().toEpochDay() - start.toLocalDate().toEpochDay()
        val worst = case.worst[day.toInt().coerceAtMost(case.worst.lastIndex)]
        val time = t.toEpochSecond()
        val aqi = when {
            time == nowHour -> case.now
            t.hour == peakHour -> worst
            else -> (minOf(case.now, worst) * 0.6).toInt()
        }
        val d = day.toInt().coerceAtMost(3)
        AirHour(
            time = time,
            aqi = aqi,
            pm25Index = if (case.cause == DemoCause.PARTICLES) aqi else aqi / 3,
            pm10Index = when (case.cause) { DemoCause.PARTICLES -> aqi * 4 / 5; DemoCause.DUST -> aqi; else -> aqi / 3 },
            no2Index = if (case.cause == DemoCause.NO2) aqi else aqi / 4,
            ozoneIndex = when (case.cause) { DemoCause.OZONE, DemoCause.NONE -> aqi; DemoCause.NO2 -> aqi / 2; else -> aqi / 3 },
            so2Index = 1,
            pm10 = if (case.cause == DemoCause.DUST) aqi.toDouble() else 12.0,
            dust = if (case.cause == DemoCause.DUST) aqi * 0.7 else 1.0,
            olivePollen = if (i < 97) (if (withOlive) oliveGrains[oliveDays[d]] else 0.0) else null,
            grassPollen = if (i < 97) (if (withGrass) grassGrains[grassDays[d]] else 0.0) else null,
        )
    }
    return AirQuality(demoPlace.position, now, hours)
}

/**
 * Un sitio guardado de la demo del 006 (los de la maqueta): su escena, su zona y hace cuántos minutos es su dato
 * (para «Datos de ayer», «Datos del domingo»…).
 */
data class DemoSaved(
    val id: String,
    val name: String,
    val region: String,
    val countryCode: String,
    val position: LatLon,
    val zone: ZoneId,
    val scene: Scene,
    val ageMinutes: Long,
    /** Sin datos todavía (guardado sin conexión): la postal sale de cristal, «Aún sin datos». */
    val noData: Boolean = false,
    /** La temperatura de ahora, si no es la de la escena (p. ej. −5° con nieve). */
    val tempC: Double? = null,
) {
    val saved: SavedPlace get() = SavedPlace(id, name, region, countryCode, position, savedAt = 0)
}

private val madrid = ZoneId.of("Europe/Madrid")

/**
 * Los 10 de la demo, en su orden: los 5 primeros son «some». Los de `full` cubren las fotos de las postales: nieve a −5°,
 * niebla, tormenta con datos de hace cinco días, Nueva York (otra hora), Tokio de noche y Lisboa sin datos.
 */
private val demoSavedAll = listOf(
    DemoSaved("sev", "Sevilla", "Sevilla", "ES", LatLon(37.38, -5.97), madrid, Scene.SUN_MILD, 5),
    DemoSaved("mad", "Madrid", "Madrid", "ES", LatLon(40.42, -3.70), madrid, Scene.CLOUDS, 130),
    DemoSaved("lco", "A Coruña", "A Coruña", "ES", LatLon(43.37, -8.40), madrid, Scene.RAIN, 10),
    DemoSaved("lpa", "Las Palmas de Gran Canaria", "Las Palmas", "ES", LatLon(28.10, -15.41), ZoneId.of("Atlantic/Canary"), Scene.SUN_MILD, 0),
    DemoSaved("nyc", "Nueva York", "New York", "US", LatLon(40.71, -74.01), ZoneId.of("America/New_York"), Scene.SUN_COOL, 3 * 24 * 60),
    DemoSaved("nav", "Puerto de Navacerrada", "Madrid", "ES", LatLon(40.79, -4.01), madrid, Scene.SNOW, 20, tempC = -5.0),
    DemoSaved("vlc", "Valencia", "Valencia", "ES", LatLon(39.47, -0.38), madrid, Scene.FOG, 5 * 60),
    DemoSaved("bio", "Bilbao", "Vizcaya", "ES", LatLon(43.26, -2.93), madrid, Scene.STORM, 5 * 24 * 60),
    DemoSaved("tyo", "Tokio", "Tokyo", "JP", LatLon(35.69, 139.69), ZoneId.of("Asia/Tokyo"), Scene.NIGHT_CLEAR, 4 * 24 * 60),
    DemoSaved("lis", "Lisboa", "Lisboa", "PT", LatLon(38.72, -9.14), ZoneId.of("Europe/Lisbon"), Scene.SUN_MILD, 0, noData = true),
)

/** `--es saved none|some|full`: 0, 5 o 10 guardados; por defecto, 5. */
fun demoSaved(kind: String?): List<DemoSaved> = when (kind) {
    "none" -> emptyList()
    "full" -> demoSavedAll
    else -> demoSavedAll.take(5)
}

/** Lo guardado de un sitio de la demo: su escena, en su zona y con su edad. */
fun demoSavedState(p: DemoSaved, now: Long): StoredState {
    if (p.noData) return StoredState(null, lastAttemptFailed = true)
    val state = demoState(p.scene, null, now, Place(p.name, p.countryCode, p.position), p.zone, fetched = now - p.ageMinutes * 60)
    val temp = p.tempC ?: return state
    return state.copy(snapshot = state.snapshot?.let { it.copy(current = it.current.copy(temperatureC = temp)) })
}

/** El sitio de nombre largo de la maqueta (009, P3, `--ez long true`), con la posición de Madrid de la demo. */
internal val demoLongPlace = Place("Sanlúcar de Barrameda", "ES", demoPlace.position)

/**
 * [count] avisos a la vez (009, P3, `--ei warnings 0..3`), desordenados para ver que el más grave sale arriba: amarillo
 * por viento mañana, rojo por lluvias ahora y naranja por tormentas esta tarde. Con [long], los textos largos de la
 * maqueta (zona y fenómeno).
 */
fun demoWarnings(count: Int, long: Boolean, now: Long): List<Warning> {
    val area = if (long) "Campiña cordobesa y Sierra Morena de Córdoba" else "Zona de ejemplo"
    fun w(id: String, level: WarningLevel, phenomenon: String, onset: Long, hours: Int) = Warning(
        id = id, level = level, phenomenon = phenomenon, areaName = area, onset = onset, expires = onset + hours * 3600L,
        probability = "40%-70%", description = "Aviso inventado para ver el Resumen.", instruction = null, sent = now - 7200,
    )
    return listOf(
        w("demo-y", WarningLevel.YELLOW, if (long) "viento, rachas de 70 km/h del suroeste" else "viento", now + 20 * 3600, 9),
        w("demo-r", WarningLevel.RED, if (long) "lluvias y tormentas, hasta 40 mm en 1 h" else "lluvias", now - 3600, 12),
        w("demo-o", WarningLevel.ORANGE, "tormentas", now + 4 * 3600, 6),
    ).take(count.coerceIn(0, 3))
}
