package com.trastealabs.tiempo.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ReportPromptTest {
    private val now = madridAt(2026, 11, 12, 10, 20)
    private val zone = ZoneId.of("Europe/Madrid")

    private val warning = Warning(
        id = "2.49.0.1.724.0.TOR.ABCDEF123",
        level = WarningLevel.ORANGE,
        phenomenon = "Tormentas",
        areaName = "Campiña de Prueba",
        onset = now - 3600,
        expires = now + 6 * 3600,
        probability = "40%-70%",
        description = "Acumulación de precipitación en una hora: 20 mm",
        instruction = "Precaución",
        sent = now - 7200,
    )

    private val place = Place("Villanueva de Prueba", "ES", LatLon(37.89, -4.78))

    private fun request() = reportPrompt(
        reportFacts(
            reportSnap(now, place = place, warnings = listOf(warning)),
            now,
            zone,
        )!!,
    )

    @Test fun `una sola peticion lleva hoy, los dias y los avisos`() {
        val user = Json.parseToJsonElement(request().user).jsonObject
        assertEquals("2026-11-12", user["hoy"]!!.jsonPrimitive.content)
        assertEquals(10, user["dias"]!!.jsonArray.size)
        val avisos = user["avisos"]!!.jsonArray
        assertEquals(1, avisos.size)
        assertEquals("a1", avisos[0].jsonObject["clave"]!!.jsonPrimitive.content)
        assertEquals("naranja", avisos[0].jsonObject["nivel"]!!.jsonPrimitive.content)
    }

    @Test fun `ni el system ni el user llevan sitio, provincia, pais, zona, id ni coordenadas`() {
        val r = request()
        val all = r.system + "\n" + r.user + "\n" + r.schema.toString()
        listOf(
            "Villanueva", "Prueba", "Campiña", "Córdoba", "Andalucía", "España", "Spain",
            warning.id, "ABCDEF123", "37.89", "37,89", "-4.78", "4.78", "4,78", "37.8", "latitud", "longitud",
        ).forEach { assertFalse("no debe aparecer «$it»", all.contains(it, ignoreCase = true)) }
    }

    @Test fun `los avisos conservan la descripcion oficial`() {
        val user = Json.parseToJsonElement(request().user).jsonObject
        assertEquals(warning.description, user["avisos"]!!.jsonArray[0].jsonObject["descripcion"]!!.jsonPrimitive.content)
    }

    @Test fun `sin avisos la lista va vacia`() {
        val f = reportFacts(reportSnap(now), now, zone)!!
        val user = Json.parseToJsonElement(reportPrompt(f).user).jsonObject
        assertEquals(0, user["avisos"]!!.jsonArray.size)
    }

    @Test fun `el system pide la frase del widget de 55 caracteres como mucho, en la misma peticion`() {
        val s = request().system
        assertTrue(s.contains("\"widget\""))
        assertTrue(s.contains("55 caracteres"))
        assertTrue(s.contains("cuatro campos"))
    }

    @Test fun `el esquema tiene today, days, warnings y widget, todo requerido y cerrado`() {
        val schema = request().schema
        val keys = setOf("today", "days", "warnings", "widget")
        assertEquals("object", schema["type"]!!.jsonPrimitive.content)
        assertEquals(keys, schema["properties"]!!.jsonObject.keys)
        assertEquals(keys, schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
        assertEquals("string", schema["properties"]!!.jsonObject["widget"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertFalse(schema["additionalProperties"]!!.jsonPrimitive.boolean)
        for (key in listOf("days", "warnings")) {
            val items: JsonObject = schema["properties"]!!.jsonObject[key]!!.jsonObject["items"]!!.jsonObject
            assertFalse(items["additionalProperties"]!!.jsonPrimitive.boolean)
            assertEquals(2, (items["required"] as JsonArray).size)
        }
    }

    @Test fun `el system lleva el tono y pide usar solo cifras y horas de los datos`() {
        val s = request().system
        assertTrue(s.contains("Hoy entre 6° al amanecer y 19° hacia las 16:00. Sin lluvia."))
        assertTrue(s.contains("solo cifras y horas de los datos", ignoreCase = true))
        assertTrue(s.contains("una línea", ignoreCase = true))
    }

    @Test fun `la peticion es compacta, de una sola linea`() {
        assertFalse(request().user.contains('\n'))
    }

    @Test fun `los hechos de un dia sin nada no llevan campos vacios`() {
        val f = reportFacts(reportSnap(now), now, zone)!!
        val day = Json.parseToJsonElement(reportPrompt(f).user).jsonObject["dias"]!!.jsonArray[0].jsonObject
        assertEquals(setOf("fecha", "dia", "semana", "min", "min_hora", "max", "max_hora"), day.keys)
    }
}
