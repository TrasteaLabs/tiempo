package com.trastealabs.tiempo.domain

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeParseException

// 018: lo que contesta el modelo a una pregunta. Misma comprobación que el parte (H2): toda cifra y toda hora tienen
// que estar en los datos enviados. Nada de esto se guarda (H7): vive en memoria mientras la pantalla lo enseña.

/** Lo que enseña la respuesta: una frase del modelo, o «no es del tiempo» / «fuera de rango» con la frase de la app. */
enum class AskKind { ANSWER, OFF_TOPIC, OUT_OF_RANGE }

/**
 * @property text vacío con [AskKind.OFF_TOPIC] y [AskKind.OUT_OF_RANGE]: la frase la pone la app (lleva el nombre del sitio).
 * @property at cuándo contestó (epoch s).
 * @property dataAt `snapshot.fetchedAt`: de cuándo son los datos con que se contestó.
 */
data class AskAnswer(val kind: AskKind, val text: String, val at: Long, val dataAt: Long)

/** Por qué no hay respuesta. `UNSURE` = la respuesta no pasó [checkAnswer]. */
enum class AskFailure { NO_KEY, CAP, UNSURE, OFFLINE, BAD_KEY, NO_CREDIT, TIMEOUT, CUT, OTHER }

sealed interface AskResult {
    data class Ok(val answer: AskAnswer) : AskResult
    data class Failed(val failure: AskFailure) : AskResult
}

const val ANSWER_MAX = 320

private val MARKDOWN = charArrayOf('*', '#', '`')

/**
 * La respuesta comprobada, o null si no vale: JSON roto o con texto alrededor, `kind` desconocido, texto vacío, de más
 * de [ANSWER_MAX], con saltos de línea o Markdown, o con una cifra u hora que no esté en [facts] ni en [question].
 */
fun checkAnswer(json: String, facts: ReportFacts, question: String, now: Long, dataAt: Long): AskAnswer? {
    val root = try {
        Json.parseToJsonElement(json) as? JsonObject
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } ?: return null
    val kind = when ((root["kind"] as? JsonPrimitive)?.takeIf { it.isString }?.content) {
        "answer" -> AskKind.ANSWER
        "off_topic" -> AskKind.OFF_TOPIC
        "out_of_range" -> AskKind.OUT_OF_RANGE
        else -> return null
    }
    if (kind != AskKind.ANSWER) return AskAnswer(kind, "", now, dataAt)
    val raw = (root["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    if (raw.any { it in MARKDOWN }) return null
    val text = accepted(raw, ANSWER_MAX, allowedFor(facts, question)) ?: return null
    if (wrongWeekday(text, facts)) return null
    return AskAnswer(kind, text, now, dataAt)
}

/** Las cifras y horas de todos los días y avisos, el día del mes de cada fecha y lo que traiga la propia pregunta. */
private fun allowedFor(facts: ReportFacts, question: String): Allowed {
    val numbers = HashSet<String>()
    val times = HashSet<String>()
    for (d in facts.days) {
        val a = allowedOf(d)
        numbers += a.numbers
        times += a.times
        try {
            numbers += LocalDate.parse(d.date).dayOfMonth.toString()
        } catch (e: DateTimeParseException) {
            // Una fecha rara no deja pasar nada más.
        }
    }
    for (w in facts.warnings) {
        val a = allowedOf(w)
        numbers += a.numbers
        times += a.times
    }
    numbers += numbersIn(question)
    times += timesIn(question)
    return Allowed(numbers, times)
}

private val WEEKDAY_AND_DAY =
    Regex("""(?iu)(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo),?\s+(\d{1,2})\b""")

private fun plain(s: String) = s.lowercase().replace('á', 'a').replace('é', 'e')

/**
 * 018 (H17): «miércoles 17» cuando el 17 de los datos es martes. Solo mira «día de la semana + número» con un día de los
 * datos que tenga ese número; un número que no es de ningún día ya lo tira la comprobación de cifras.
 */
private fun wrongWeekday(text: String, facts: ReportFacts): Boolean = WEEKDAY_AND_DAY.findAll(text).any { m ->
    val said = plain(m.groupValues[1])
    val n = m.groupValues[2].toInt()
    facts.days.any { d ->
        val date = try { LocalDate.parse(d.date) } catch (e: DateTimeParseException) { null }
        val real = weekdayOf(d.date)?.let(::plain)
        date != null && date.dayOfMonth == n && real != null && real != said
    }
}
