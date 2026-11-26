package com.trastealabs.tiempo.domain

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// 016: comprobar lo que contesta el modelo (H7), decidir cuándo toca un parte nuevo (H10) y cuándo el guardado ya no
// se puede enseñar (H8). Todo con `now`; nada de reloj.

/** Un parte de más de 6 h ya pide otro, pero se sigue viendo mientras llega. */
const val REPORT_MAX_AGE_S = 6 * 3600L

private const val TODAY_MAX = 320
private const val DAY_MAX = 180
private const val WARNING_MAX = 220

/** 018: la frase del widget 4×2 no pasa de 55 caracteres (el diseño la parte en dos líneas). */
const val WIDGET_MAX = 55

private const val TEMP_STEP = 2
private const val RAIN_STEP_H = 2

// 017 (H10): una hora se puede escribir «7:00», «7.00», «a las 7», «a las 7h» o «a las 7h30»; todas valen «07:00».
private val TIME_COLON = Regex("""(?<![\d:])(\d{1,2}):(\d{2})(?![\d:])""")
private val TIME_DOT = Regex("""(?<![\d:.,])(\d{1,2})\.(\d{2})(?![\d:]|[.,]\d)(?!\s?(?:°|º|%|mm\b|km\b|l/))""")
// «7h» y «7» solo son hora con «las» delante («a las 7h», «hasta las 7», «desde las 7»); sueltos («3 h de lluvia»,
// «3 horas», «7h de sol») son cifras, como siempre.
private val TIME_LAS = Regex(
    """\b(?:a|hasta|desde|hacia|sobre|de|y|entre|tras) las? (\d{1,2})(?:\s?h(?:(\d{2}))?(?![\p{L}\d]))?(?![\d:.,]|\s?(?:°|º|%|mm\b|km\b))""",
    RegexOption.IGNORE_CASE,
)
private val NUMBER = Regex("""\d+(?:[.,]\d+)?""")
private val LINE_BREAKS = charArrayOf('\n', '\r', '\u0085', ' ', ' ')

/** Cifras y horas que un texto puede nombrar. */
internal class Allowed(val numbers: Set<String>, val times: Set<String>)

/**
 * El parte comprobado cifra a cifra, o null si [json] no es el JSON pedido (roto, con texto alrededor, sin ningún campo
 * conocido). Cada texto tiene que ser de una línea, no vacío, no pasar de su tope, y toda hora «H:mm» y todo número
 * tiene que estar en los hechos de *esa* parte; lo que falla se descarta solo (`today = null`, ese día o ese aviso fuera).
 */
fun checkReply(json: String, facts: ReportFacts, provider: AiProvider, now: Long): AiReport? {
    val root = try {
        Json.parseToJsonElement(json) as? JsonObject
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } ?: return null
    if (listOf("today", "days", "warnings", "widget").none { it in root }) return null

    val today = root.text("today")?.let { accepted(it, TODAY_MAX, allowedOf(facts.today)) }
    // 018: la frase del widget se comprueba con las cifras de hoy; si falla, sin frase y el resto del parte sigue.
    val widget = root.text("widget")?.let { accepted(it, WIDGET_MAX, allowedOf(facts.today)) }

    val days = LinkedHashMap<String, String>()
    for (entry in (root["days"] as? JsonArray).orEmpty()) {
        val obj = entry as? JsonObject ?: continue
        val date = obj.text("date") ?: continue
        val day = facts.days.firstOrNull { it.date == date } ?: continue
        if (date in days) continue
        days[date] = accepted(obj.text("text") ?: continue, DAY_MAX, allowedOf(day)) ?: continue
    }

    val warnings = LinkedHashMap<String, String>()
    for (entry in (root["warnings"] as? JsonArray).orEmpty()) {
        val obj = entry as? JsonObject ?: continue
        val warning = facts.warnings.firstOrNull { it.key == obj.text("key") } ?: continue
        if (warning.id in warnings) continue
        warnings[warning.id] = accepted(obj.text("text") ?: continue, WARNING_MAX, allowedOf(warning)) ?: continue
    }
    return AiReport(now, provider, facts.basis, today, days, warnings, widget)
}

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** El texto limpio de espacios en los extremos, o null si no vale. */
internal fun accepted(text: String, max: Int, allowed: Allowed): String? {
    val t = text.trim()
    if (t.isEmpty() || t.length > max || t.any { it in LINE_BREAKS }) return null
    if (!timesIn(t).all { it in allowed.times }) return null
    if (!numbersIn(t).all { it in allowed.numbers }) return null
    return t
}

private class TimeHit(val range: IntRange, val value: String)

private fun hhmm(h: String, m: String) = h.padStart(2, '0') + ":" + m

private fun timeHits(s: String): List<TimeHit> {
    val hits = ArrayList<TimeHit>()
    TIME_COLON.findAll(s).forEach { hits += TimeHit(it.range, hhmm(it.groupValues[1], it.groupValues[2])) }
    // «7.00» solo es una hora si cabe en un reloj; «12.75» se queda como número.
    TIME_DOT.findAll(s).filter { it.groupValues[1].toInt() < 24 && it.groupValues[2].toInt() < 60 }
        .forEach { hits += TimeHit(it.range, hhmm(it.groupValues[1], it.groupValues[2])) }
    TIME_LAS.findAll(s).forEach { hits += TimeHit(it.range, hhmm(it.groupValues[1], it.groupValues[2].ifEmpty { "00" })) }
    return hits
}

internal fun timesIn(s: String): List<String> = timeHits(s).map { it.value }

/** Los números que no son parte de una hora; `7`, `07` y `7,0` son el mismo. */
internal fun numbersIn(s: String): List<String> {
    val chars = s.toCharArray()
    timeHits(s).forEach { hit -> hit.range.forEach { chars[it] = ' ' } }
    return NUMBER.findAll(String(chars)).map { normalized(it.value) }.toList()
}

private fun normalized(token: String): String {
    val value = token.replace(',', '.').toDouble()
    return if (value == Math.floor(value) && value < 1e15) value.toLong().toString() else value.toString()
}

private fun numbersOf(vararg ints: Int?): Set<String> = ints.filterNotNull().map { abs(it).toString() }.toSet()

internal fun allowedOf(d: DayFacts): Allowed = Allowed(
    numbers = numbersOf(d.minC, d.maxC, d.rainMm, d.gustKmh, d.frostC) +
        d.rain.map { abs(it.mm).toString() } + numbersIn(d.label),
    times = (listOfNotNull(d.minAt, d.maxAt, d.gustAt, d.fog?.from, d.fog?.to) +
        d.rain.flatMap { listOf(it.from, it.to) } + d.storm).toSet(),
)

internal fun allowedOf(w: WarningFacts): Allowed {
    val sources = listOfNotNull(w.from, w.to, w.probability, w.description)
    return Allowed(
        numbers = sources.flatMap { numbersIn(it) }.toSet(),
        times = sources.flatMap { timesIn(it) }.toSet(),
    )
}

/** Sin parte, de más de [REPORT_MAX_AGE_S] o con la previsión cambiada de verdad. */
fun reportDue(current: AiReport?, basis: ReportBasis, now: Long): Boolean =
    current == null || now - current.generatedAt > REPORT_MAX_AGE_S || changedForReal(current.basis, basis)

/** Otro día; máxima o mínima 2° o más; lluvia que aparece o desaparece o se mueve 2 h o más; avisos distintos. */
fun changedForReal(old: ReportBasis, new: ReportBasis): Boolean {
    if (old.date != new.date) return true
    if (abs(old.maxC - new.maxC) >= TEMP_STEP || abs(old.minC - new.minC) >= TEMP_STEP) return true
    if (old.wetToday != new.wetToday) return true
    val a = old.rainStartHour
    val b = new.rainStartHour
    if ((a == null) != (b == null)) return true
    if (a != null && b != null && abs(a - b) >= RAIN_STEP_H) return true
    return old.warningIds != new.warningIds
}

/**
 * El parte que se puede enseñar: el guardado, salvo que la previsión de ahora ([basis]) haya cambiado de verdad desde
 * que se hizo (incluido otro día, H8). Sin huella (sin horas desde ahora) no se enseña nada.
 */
fun visibleReport(report: AiReport?, basis: ReportBasis?): AiReport? {
    if (report == null || basis == null) return null
    return report.takeUnless { changedForReal(it.basis, basis) }
}

private val LABEL_TIME = DateTimeFormatter.ofPattern("HH:mm")

/** La marca discreta de la tarjeta (H9): «IA · 08:15», con la hora del parte en la zona del sitio. */
fun reportLabel(report: AiReport, zone: ZoneId): String =
    "IA · " + LABEL_TIME.format(Instant.ofEpochSecond(report.generatedAt).atZone(zone))
