package com.trastealabs.tiempo.data.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

/**
 * 016 P2. ChatGPT por la Responses API (documentación leída el 2026-11-12, ver [AiModels]): `instructions`, `input`,
 * `text.format` con esquema estricto y `store: false` (no se guarda nada en su servidor). Una sola petición.
 */
class OpenAiWriter(
    private val http: OkHttpClient,
    private val key: String,
    private val baseUrl: String = "https://api.openai.com/v1/responses",
    private val model: String = AiModels.OPENAI,
) : ReportWriter {
    override suspend fun write(request: ReportRequest): ReportReply {
        val body = buildJsonObject {
            put("model", model)
            put("instructions", request.system)
            put(
                "input",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", request.user)
                        },
                    )
                },
            )
            put("max_output_tokens", AI_MAX_OUTPUT_TOKENS)
            put("store", false)
            put("reasoning", buildJsonObject { put("effort", "low") })
            put(
                "text",
                buildJsonObject {
                    put(
                        "format",
                        buildJsonObject {
                            put("type", "json_schema")
                            put("name", "parte")
                            put("strict", true)
                            put("schema", request.schema)
                        },
                    )
                },
            )
        }
        val text = http.postJson(
            url = baseUrl,
            headers = mapOf("Authorization" to "Bearer $key"),
            body = body,
        ) { status, errorBody ->
            val code = providerCode(errorBody)
            if (status == 429 && QUOTA_WORDS.any { it in code }) {
                AiException(AiFailure.NO_CREDIT, "$NAME: sin saldo o tope de gasto")
            } else {
                commonFailure(NAME, status, errorBody)
            }
        }
        return parse(text)
    }

    private fun parse(text: String): ReportReply {
        val root = parseReply(NAME, text)
        when ((root["status"] as? JsonPrimitive)?.contentOrNull) {
            "incomplete" -> throw badReply(NAME, "respuesta incompleta")
            "failed", "cancelled" -> throw AiException(AiFailure.PROVIDER, "$NAME no pudo generar la respuesta")
        }
        val output = (root["output"] as? JsonArray) ?: throw badReply(NAME, "sin salida")
        val parts = output.flatMap { item ->
            val o = item as? JsonObject
            if ((o?.get("type") as? JsonPrimitive)?.contentOrNull == "message") {
                (o["content"] as? JsonArray) ?: JsonArray(emptyList())
            } else {
                emptyList()
            }
        }.mapNotNull { it as? JsonObject }
        if (parts.any { (it["type"] as? JsonPrimitive)?.contentOrNull == "refusal" }) {
            throw badReply(NAME, "rechazó la petición")
        }
        val json = parts.firstNotNullOfOrNull {
            if ((it["type"] as? JsonPrimitive)?.contentOrNull == "output_text") (it["text"] as? JsonPrimitive)?.contentOrNull else null
        } ?: throw badReply(NAME, "sin texto")
        val usage = root["usage"]
        return ReportReply(json, usage.intField("input_tokens"), usage.intField("output_tokens"))
    }

    private companion object {
        const val NAME = "ChatGPT"
        val QUOTA_WORDS = listOf("quota", "credit", "spend_limit", "usage_limit")
    }
}
