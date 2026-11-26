package com.trastealabs.tiempo.data.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

/**
 * 016 P2. Gemini por `generateContent` (documentación leída el 2026-11-12, ver [AiModels]). La clave va en la
 * cabecera `x-goog-api-key`, nunca en la URL. El pensamiento va a nivel bajo para que no se coma el tope de salida.
 * Una sola petición, sin reintentos.
 */
class GeminiWriter(
    private val http: OkHttpClient,
    private val key: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/models",
    private val model: String = AiModels.GEMINI,
) : ReportWriter {
    override suspend fun write(request: ReportRequest): ReportReply {
        fun parts(text: String) = buildJsonArray { add(buildJsonObject { put("text", text) }) }
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject { put("parts", parts(request.system)) })
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("parts", parts(request.user))
                        },
                    )
                },
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("responseMimeType", "application/json")
                    put("responseJsonSchema", request.schema)
                    put("maxOutputTokens", AI_MAX_OUTPUT_TOKENS)
                    put("thinkingConfig", buildJsonObject { put("thinkingLevel", "LOW") })
                },
            )
        }
        val text = http.postJson(
            url = "${baseUrl.trimEnd('/')}/$model:generateContent",
            headers = mapOf("x-goog-api-key" to key),
            body = body,
        ) { status, errorBody ->
            // Una clave mal escrita puede llegar como 400 «API key not valid» según el servicio.
            if (status == 400 && providerMessage(errorBody)?.contains("API key", ignoreCase = true) == true) {
                AiException(AiFailure.BAD_KEY, "$NAME rechazó la clave (400)")
            } else {
                commonFailure(NAME, status, errorBody)
            }
        }
        return parse(text)
    }

    private fun parse(text: String): ReportReply {
        val root = parseReply(NAME, text)
        if (((root["promptFeedback"] as? JsonObject)?.get("blockReason")) != null) throw badReply(NAME, "petición bloqueada")
        val candidate = ((root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?: throw badReply(NAME, "sin candidatos")
        val finish = (candidate["finishReason"] as? JsonPrimitive)?.contentOrNull
        if (finish != null && finish != "STOP") throw badReply(NAME, "terminó con $finish")
        val parts = ((candidate["content"] as? JsonObject)?.get("parts") as? JsonArray) ?: JsonArray(emptyList())
        val json = parts.mapNotNull { part ->
            val o = part as? JsonObject
            val thought = (o?.get("thought") as? JsonPrimitive)?.booleanOrNull == true
            if (thought) null else (o?.get("text") as? JsonPrimitive)?.contentOrNull
        }.joinToString("")
        if (json.isEmpty()) throw badReply(NAME, "sin texto")
        val usage = root["usageMetadata"]
        // Los tokens de pensamiento se cobran como salida.
        val out = usage.intField("candidatesTokenCount") + usage.intField("thoughtsTokenCount")
        return ReportReply(json, usage.intField("promptTokenCount"), out)
    }

    private companion object {
        const val NAME = "Gemini"
    }
}
