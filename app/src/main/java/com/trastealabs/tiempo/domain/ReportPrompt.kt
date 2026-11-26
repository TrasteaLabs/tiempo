package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.data.ai.ReportRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// 016: la petición común a los tres proveedores. Una sola llamada pide el parte de hoy, la frase de cada día y la
// explicación de cada aviso. Lo único que sale del móvil son estas cifras y horas (H17): ni sitio, ni zona, ni ids.

private val SYSTEM = """
Eres un meteorólogo de cabecera que escribe el parte del día en español, en estilo de parte estricto, no prosa. Siempre en este orden:
1. Temperatura: mínima y máxima de hoy y hacia qué hora cada una.
2. Lluvia: si hay o no; si es todo el día o sobre todo en unas horas; si es poca o mucha.
3. Solo si los hay, con su hora: tormenta, viento fuerte, helada, niebla. Si no hay nada de eso, no lo menciones.

Ejemplos del tono:
Hoy entre 6° al amanecer y 19° hacia las 16:00. Sin lluvia.
Hoy entre 9° a las 7:00 y 21° hacia las 16:00. Lluvia débil sobre todo de 17:00 a 21:00, con posible tormenta hacia las 18:00.
Hoy no pasa de 13°, con mínima de 8° de madrugada. Lluvia todo el día, más fuerte entre las 11:00 y las 15:00; cae bastante, unos 22 mm.

Recibirás los datos en JSON: "hoy" es la fecha de hoy; "dias" son los días desde hoy (el primero es hoy) con sus cifras ya redondeadas; "avisos" son los avisos oficiales en vigor.
Responde solo con un JSON con cuatro campos:
- "today": el parte de hoy, con el esquema de arriba, en una línea de 320 caracteres como mucho.
- "days": una frase por cada día de "dias" (incluido hoy), con su "date" tal cual y el mismo esquema pero más corta: una línea de 180 caracteres como mucho.
- "warnings": una explicación por cada aviso de "avisos", con su "key" tal cual (a1, a2…): en una sola frase de 220 caracteres como mucho, qué va a pasar y cuándo, en lenguaje llano y sin la jerga de AEMET. Si no hay avisos, una lista vacía.
- "widget": lo más importante de hoy en una sola frase de 55 caracteres como mucho, sin punto final, para un widget de pantalla (por ejemplo «Lluvia desde las 17:00, máxima de 19°»). Las mismas reglas de cifras y horas.

Reglas que no se rompen:
- Usa solo cifras y horas de los datos, escritas igual (grados con °, horas como HH:mm, mm para la lluvia). No calcules, no redondees de otra manera y no inventes ninguna cifra ni hora.
- Cada texto, una línea: sin saltos de línea, sin listas, sin formato.
- No nombres lugares ni añadas nada que no esté en los datos. No des consejos.
- Un día sin nada que contar tiene un parte corto; no lo rellenes.
""".trimIndent()

/** Los hechos ya masticados en una petición: instrucciones, datos en JSON compacto y el esquema de la respuesta. */
fun reportPrompt(facts: ReportFacts): ReportRequest =
    ReportRequest(system = SYSTEM, user = factsJson(facts).toString(), schema = SCHEMA)

/** Los datos en JSON, compartidos con la petición de la pregunta (018). */
internal fun factsJson(facts: ReportFacts): JsonObject = buildJsonObject {
    put("hoy", facts.basis.date)
    put("dias", buildJsonArray { facts.days.forEach { add(dayJson(it)) } })
    put("avisos", buildJsonArray { facts.warnings.forEach { add(warningJson(it)) } })
}

/** Solo lo que hay: un día seco no lleva campos de lluvia, tormenta ni racha. */
private fun dayJson(d: DayFacts): JsonObject = buildJsonObject {
    put("fecha", d.date)
    put("dia", d.label)
    weekdayOf(d.date)?.let { put("semana", it) }
    put("min", d.minC)
    d.minAt?.let { put("min_hora", it) }
    put("max", d.maxC)
    d.maxAt?.let { put("max_hora", it) }
    if (d.rain.isNotEmpty()) {
        put("lluvia_mm", d.rainMm)
        putJsonArray("lluvia") {
            d.rain.forEach { s -> add(buildJsonObject { put("de", s.from); put("a", s.to); put("mm", s.mm) }) }
        }
    }
    if (d.rainAllDay) put("lluvia_todo_el_dia", true)
    if (d.snow) put("nieve", true)
    if (d.storm.isNotEmpty()) put("tormenta", JsonArray(d.storm.map { JsonPrimitive(it) }))
    d.gustKmh?.let { put("racha_kmh", it) }
    d.gustAt?.let { put("racha_hora", it) }
    d.frostC?.let { put("helada_c", it) }
    d.fog?.let { f -> putJsonObject("niebla") { put("de", f.from); put("a", f.to) } }
}

private fun warningJson(w: WarningFacts): JsonObject = buildJsonObject {
    put("clave", w.key)
    put("nivel", w.level)
    put("fenomeno", w.phenomenon)
    put("desde", w.from)
    put("hasta", w.to)
    w.probability?.let { put("probabilidad", it) }
    put("descripcion", w.description)
}

internal fun strings(vararg keys: String): JsonElement = JsonArray(keys.map { JsonPrimitive(it) })

private fun listOfTexts(keyName: String): JsonObject = buildJsonObject {
    put("type", "array")
    putJsonObject("items") {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject(keyName) { put("type", "string") }
            putJsonObject("text") { put("type", "string") }
        }
        put("required", strings(keyName, "text"))
        put("additionalProperties", false)
    }
}

/** Objeto cerrado con todo requerido (los esquemas estrictos piden propiedades fijas): listas, no mapas. */
private val SCHEMA: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("today") { put("type", "string") }
        put("days", listOfTexts("date"))
        put("warnings", listOfTexts("key"))
        putJsonObject("widget") { put("type", "string") }
    }
    put("required", strings("today", "days", "warnings", "widget"))
    put("additionalProperties", false)
}
