package com.trastealabs.tiempo.data.ai

import kotlinx.serialization.json.JsonObject
import java.io.IOException

// 016: la costura entre el motor del parte y los proveedores de IA. El resto de la app no sabe cuál hay.

/** Lo que se pide al modelo: instrucciones, datos y esquema de la respuesta. Nada más sale del móvil (H17). */
data class ReportRequest(val system: String, val user: String, val schema: JsonObject)

/** El texto JSON que contestó el modelo y los tokens gastados. */
data class ReportReply(val json: String, val inputTokens: Int, val outputTokens: Int)

enum class AiFailure { NO_KEY, BAD_KEY, NO_CREDIT, RATE_LIMIT, OFFLINE, PROVIDER, BAD_REPLY, TIMEOUT }

class AiException(val failure: AiFailure, message: String) : IOException(message)

interface ReportWriter {
    /** Una sola petición, sin reintentos. Lanza [AiException]. */
    suspend fun write(request: ReportRequest): ReportReply
}
