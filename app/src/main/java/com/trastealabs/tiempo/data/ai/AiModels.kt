package com.trastealabs.tiempo.data.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 016 P2. El modelo pequeño de cada proveedor, en un solo sitio: si uno se retira, se cambia aquí.
 *
 * Contratos leídos de la documentación oficial el 2026-11-12:
 * - Claude: el contrato de plan.md, leído de la documentación de Anthropic el 2026-11-12 (no se releyó en P2).
 * - Gemini: https://aistudio.google.com/api-reference/generate-content (cuerpo, respuesta, `usageMetadata`),
 *   https://aistudio.google.com/docs/structured-output (cabecera `x-goog-api-key`, esquema JSON con
 *   `additionalProperties`), https://aistudio.google.com/docs/models (modelo estable más pequeño: Flash-Lite 3.5) y
 *   https://aistudio.google.com/docs/api-errors (códigos de error). Esas páginas empujan hacia la «Interactions API»;
 *   se usa `generateContent` porque su referencia sigue vigente y no necesita cabecera de revisión.
 * - ChatGPT: https://developers.openai.com/api/docs/guides/structured-outputs (Responses API con `text.format`),
 *   https://developers.openai.com/api/reference/resources/responses/methods/create (campos de petición y `usage`),
 *   https://developers.openai.com/api/docs/models/gpt-6-luna (modelo más barato, 0,10 $ / 0,50 $ por millón) y
 *   https://developers.openai.com/api/docs/guides/error-codes (errores).
 */
object AiModels {
    const val CLAUDE = "claude-haiku-5-5"
    const val GEMINI = "gemini-3.5-flash-lite"
    const val OPENAI = "gpt-6-luna"
}

internal const val AI_MAX_OUTPUT_TOKENS = 4000
private const val ERROR_TEXT_MAX = 200
private val JSON_TYPE = "application/json".toMediaType()

internal val aiJson = Json { ignoreUnknownKeys = true }

/**
 * Una sola petición POST de JSON, sin reintentos. Devuelve el cuerpo si el estado es 2xx; si no, lanza la
 * [AiException] que decida [mapError] (estado, cuerpo). Nunca escribe en ningún log ni mete cabeceras en un mensaje.
 */
internal suspend fun OkHttpClient.postJson(
    url: String,
    headers: Map<String, String>,
    body: JsonObject,
    mapError: (status: Int, body: String) -> AiException,
): String {
    val request = Request.Builder().url(url).post(body.toString().toRequestBody(JSON_TYPE)).apply {
        headers.forEach { (name, value) -> header(name, value) }
    }.build()
    val response = awaitResponse(newCall(request))
    response.use {
        val text = try {
            it.body?.string().orEmpty()
        } catch (e: IOException) {
            throw ioFailure(e)
        }
        if (!it.isSuccessful) throw mapError(it.code, text)
        return text
    }
}

private fun ioFailure(e: IOException) = if (e is InterruptedIOException) {
    AiException(AiFailure.TIMEOUT, "La llamada a la IA tardó demasiado")
} else {
    AiException(AiFailure.OFFLINE, "Sin conexión con la IA")
}

private suspend fun awaitResponse(call: Call): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(ioFailure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
}

/** El mensaje de error del proveedor (`error.message`), recortado, o `null` si el cuerpo no lo trae. */
internal fun providerMessage(body: String): String? = runCatching {
    val error = aiJson.parseToJsonElement(body).jsonObject["error"]
    val message = when (error) {
        is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull
        is JsonPrimitive -> error.contentOrNull
        else -> null
    }
    message?.take(ERROR_TEXT_MAX)
}.getOrNull()

/** El código (`error.code` o `error.type`) en minúsculas, para distinguir un tope de gasto de un límite de ritmo. */
internal fun providerCode(body: String): String = runCatching {
    val error = aiJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject
    listOfNotNull(error?.get("code"), error?.get("type"), error?.get("status"))
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .joinToString(" ")
        .lowercase()
}.getOrDefault("")

/** Reparto común de estados HTTP. Los 400 y 404 llevan el mensaje del proveedor; los demás, no (nada de claves). */
internal fun commonFailure(provider: String, status: Int, body: String): AiException = when (status) {
    401, 403 -> AiException(AiFailure.BAD_KEY, "$provider rechazó la clave ($status)")
    402 -> AiException(AiFailure.NO_CREDIT, "$provider: sin saldo")
    429 -> AiException(AiFailure.RATE_LIMIT, "$provider: demasiadas peticiones")
    in 500..599 -> AiException(AiFailure.PROVIDER, "$provider falló ($status)")
    else -> AiException(AiFailure.PROVIDER, "$provider respondió $status: ${providerMessage(body) ?: "sin detalle"}")
}

/** Lee el cuerpo como JSON; si no lo es, [AiFailure.BAD_REPLY]. */
internal fun parseReply(provider: String, body: String): JsonObject = try {
    aiJson.parseToJsonElement(body).jsonObject
} catch (e: IllegalArgumentException) {
    throw AiException(AiFailure.BAD_REPLY, "$provider no devolvió JSON")
}

internal fun badReply(provider: String, why: String) = AiException(AiFailure.BAD_REPLY, "$provider: $why")

internal fun JsonElement?.intField(name: String): Int =
    (this as? JsonObject)?.get(name)?.let { it as? JsonPrimitive }?.intOrNull ?: 0
