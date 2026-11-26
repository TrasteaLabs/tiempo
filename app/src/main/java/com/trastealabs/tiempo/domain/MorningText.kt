package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// 018 P2: cuándo toca la notificación de la mañana y qué dice. Todo con `now` y la zona, nunca con el reloj del móvil,
// para poder probar medianoche, cambio de hora y cambio de huso.

/** Hora de salida de la notificación: las 7:30, en minutos desde medianoche. */
const val DEFAULT_MORNING_MINUTE = 450

/** Pasadas dos horas de la hora elegida ya no se avisa (el móvil estaba apagado o sin red): mejor nada que un parte de otra hora. */
const val MORNING_GRACE_S = 7200L

/** Con un dato de más de 3 h, la notificación dice de qué hora es. */
const val MORNING_STALE_S = 3 * 3600L

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** El instante de [minuteOfDay] en [day] y [zone]. En el salto de la primavera, la primera hora que existe después. */
fun morningAt(day: LocalDate, zone: ZoneId, minuteOfDay: Int): Long =
    day.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone).toEpochSecond()

/** La siguiente vez que toca avisar, estrictamente posterior a [now]; correcta en los dos cambios de hora. */
fun nextMorning(now: Long, zone: ZoneId, minuteOfDay: Int): Long {
    var day = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    // Con tres días sobra: el de hoy o el de mañana ya cae después de `now`.
    repeat(3) {
        val at = morningAt(day, zone, minuteOfDay)
        if (at > now) return at
        day = day.plusDays(1)
    }
    return morningAt(day, zone, minuteOfDay)
}

/**
 * ¿Toca avisar ahora? Encendida, sin aviso hoy ([lastDay] es la fecha local `YYYY-MM-DD` del último aviso o del día que
 * se salta) y con [now] entre la hora elegida y esa hora más [MORNING_GRACE_S].
 */
fun morningDue(now: Long, zone: ZoneId, enabled: Boolean, minuteOfDay: Int, lastDay: String): Boolean {
    if (!enabled) return false
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    if (lastDay == today.toString()) return false
    val at = morningAt(today, zone, minuteOfDay)
    return now >= at && now <= at + MORNING_GRACE_S
}

/** Los textos que se rellenan con cifras: salen de `strings.xml`, y los tests ponen los suyos. */
data class MorningStrings(
    /** «Hoy 12° a 26°». */
    val title: (minC: Int, maxC: Int) -> String,
    /** «Aviso amarillo por viento» (sin el « · » que lo separa del título). */
    val warning: (level: String, phenomenon: String) -> String,
    /** «Datos de las 03:10». */
    val dataAt: (time: String) -> String,
)

data class MorningText(val title: String, val body: String, val withAi: Boolean)

/**
 * La notificación de hoy, o null si lo guardado no cubre hoy (sin el día de hoy en la previsión) o no hay nada que decir.
 * Título: la mínima y la máxima de hoy y, con un aviso de AEMET en vigor hoy, el más grave. Texto: el parte de la IA
 * ([report], ya filtrado con `visibleReport`) o, sin uno válido, las frases de reglas [rules]; con un dato de más de 3 h,
 * una línea más con su hora. [zone] es la del dato.
 */
fun morningText(
    snapshot: WeatherSnapshot,
    report: AiReport?,
    rules: List<String>,
    now: Long,
    zone: ZoneId,
    strings: MorningStrings,
): MorningText? {
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    val day = snapshot.daily.firstOrNull { it.date == today.toString() } ?: return null
    val aiText = report?.today?.takeIf { it.isNotBlank() }
    val text = aiText ?: rules.filter { it.isNotBlank() }.joinToString(" ") { it.trimEnd().let { s -> if (s.last() in ".!?") s else "$s." } }
    if (text.isBlank()) return null

    var title = strings.title(shownDegrees(day.minC), shownDegrees(day.maxC))
    warningToday(snapshot.warnings, now, zone)?.let { w ->
        title += " · " + strings.warning(levelName(w.level), w.phenomenon)
    }
    val body = if (now - snapshot.fetchedAt > MORNING_STALE_S) {
        text + "\n" + strings.dataAt(CLOCK.format(Instant.ofEpochSecond(snapshot.fetchedAt).atZone(zone)))
    } else {
        text
    }
    return MorningText(title, body, withAi = aiText != null)
}

/** El aviso más grave que está en vigor en algún momento de hoy (y, a igual nivel, el que empieza antes). */
private fun warningToday(warnings: List<Warning>, now: Long, zone: ZoneId): Warning? {
    val endOfToday = Instant.ofEpochSecond(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toEpochSecond()
    return warnings
        .filter { it.expires > now && it.onset < endOfToday }
        .sortedWith(compareByDescending<Warning> { it.level }.thenBy { it.onset })
        .firstOrNull()
}
