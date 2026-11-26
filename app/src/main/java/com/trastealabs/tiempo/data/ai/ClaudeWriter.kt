package com.trastealabs.tiempo.data.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

/**
 * 016 P2. Claude por HTTP (contrato de plan.md). Sin `temperature`, `top_p`, `top_k`, `thinking` ni prefill: dan 400
 * en este modelo. Una sola petición, sin reintentos.
 */
class ClaudeWriter(
    private val http: OkHttpClient,
    private val key: String,
    private val baseUrl: String = "https://api.anthropic.com/v1/messages",
    private val model: String = AiModels.CLAUDE,
) : ReportWriter {
    override suspend fun write(request: ReportRequest): ReportReply {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", AI_MAX_OUTPUT_TOKENS)
            put("system", request.system)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", request.user)
                        },
                    )
                },
            )
            put(
                "output_config",
                buildJsonObject {
                    put("effort", "low")
                    put(
                        "format",
                        buildJsonObject {
                            put("type", "json_schema")
                            put("schema", request.schema)
                        },
                    )
                },
            )
        }
        val text = http.postJson(
            url = baseUrl,
            headers = mapOf("x-api-key" to key, "anthropic-version" to VERSION),
            body = body,
        ) { status, errorBody -> commonFailure(NAME, status, errorBody) }
        return parse(text)
    }

    private fun parse(text: String): ReportReply {
        val root = parseReply(NAME, text)
        val stop = root["stop_reason"]?.jsonPrimitive?.contentOrNull
        if (stop == "refusal" || stop == "max_tokens") throw badReply(NAME, "terminó con $stop")
        val content = (root["content"] as? JsonArray) ?: throw badReply(NAME, "sin contenido")
        val json = content.firstNotNullOfOrNull { block ->
            val o = block as? JsonObject
            if (o?.get("type")?.jsonPrimitive?.contentOrNull == "text") o["text"]?.jsonPrimitive?.contentOrNull else null
        } ?: throw badReply(NAME, "sin bloque de texto")
        val usage = root["usage"]
        return ReportReply(json, usage.intField("input_tokens"), usage.intField("output_tokens"))
    }

    private companion object {
        const val NAME = "Claude"
        const val VERSION = "2026-10-21"
    }
}
