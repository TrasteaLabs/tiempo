package com.trastealabs.tiempo.data.ai

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Lo que comparten los tres adaptadores: errores HTTP, JSON roto, servidor caído y una sola petición (016 P2). */
abstract class WriterContractTest {
    protected lateinit var server: MockWebServer
    protected val http = OkHttpClient()
    protected val testKey = "clave-de-prueba-1234"
    protected val request = ReportRequest(
        system = "Eres el parte del tiempo.",
        user = """{"hoy":{"maxC":21}}""",
        schema = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
        },
    )

    /** Un escritor apuntando al servidor falso. */
    protected abstract fun writer(): ReportWriter
    protected abstract val okFile: String

    @Before fun startServer() {
        server = MockWebServer().apply { start() }
    }

    @After fun stopServer() {
        server.shutdown()
    }

    protected fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "falta $name" }.bufferedReader().readText()

    protected fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("content-type", "application/json").setBody(body))
    }

    protected fun call(): ReportReply = runBlocking { writer().write(request) }

    protected fun failureOf(block: () -> Unit): AiFailure {
        try {
            block()
        } catch (e: AiException) {
            return e.failure
        }
        fail("No lanzó AiException")
        error("inalcanzable")
    }

    protected fun bodyOf(r: RecordedRequest): JsonObject = Json.parseToJsonElement(r.body.readUtf8()).jsonObject

    @Test fun goodReplyGivesJsonAndTokens() {
        enqueue(200, resource(okFile))
        val reply = call()
        assertTrue(reply.json.contains("\"today\""))
        assertEquals(1, server.requestCount)
        assertTrue(reply.inputTokens > 0 && reply.outputTokens > 0)
    }

    @Test fun status401IsBadKey() {
        enqueue(401, """{"error":{"message":"clave mala"}}""")
        assertEquals(AiFailure.BAD_KEY, failureOf { call() })
    }

    @Test fun status402IsNoCredit() {
        enqueue(402, """{"error":{"message":"sin saldo"}}""")
        assertEquals(AiFailure.NO_CREDIT, failureOf { call() })
    }

    @Test fun status429IsRateLimit() {
        enqueue(429, """{"error":{"message":"despacio"}}""")
        assertEquals(AiFailure.RATE_LIMIT, failureOf { call() })
    }

    @Test fun status500And529ArePROVIDER() {
        enqueue(500, """{"error":{"message":"caído"}}""")
        assertEquals(AiFailure.PROVIDER, failureOf { call() })
        enqueue(529, """{"error":{"message":"saturado"}}""")
        assertEquals(AiFailure.PROVIDER, failureOf { call() })
    }

    @Test fun status400KeepsProviderMessageAndStatus404IsProvider() {
        enqueue(400, """{"error":{"message":"campo raro"}}""")
        try {
            call()
            fail()
        } catch (e: AiException) {
            assertEquals(AiFailure.PROVIDER, e.failure)
            assertTrue(e.message.orEmpty().contains("campo raro"))
        }
        enqueue(404, """{"error":{"message":"modelo no existe"}}""")
        assertEquals(AiFailure.PROVIDER, failureOf { call() })
    }

    @Test fun bodyThatIsNotJsonIsBadReply() {
        enqueue(200, "esto no es json")
        assertEquals(AiFailure.BAD_REPLY, failureOf { call() })
    }

    @Test fun deadServerIsOffline() {
        val w = writer()
        server.shutdown()
        assertEquals(AiFailure.OFFLINE, failureOf { runBlocking { w.write(request) } })
    }

    @Test fun errorMessagesNeverCarryTheKey() {
        enqueue(401, """{"error":{"message":"bad"}}""")
        try {
            call()
            fail()
        } catch (e: AiException) {
            assertFalse(e.message.orEmpty().contains(testKey))
        }
    }

    @Test fun oneRequestPerCallEvenOnServerError() {
        enqueue(500, "{}")
        failureOf { call() }
        assertEquals(1, server.requestCount)
    }
}

class ClaudeWriterTest : WriterContractTest() {
    override val okFile = "ai_claude_ok.json"
    override fun writer() = ClaudeWriter(http, testKey, baseUrl = server.url("/v1/messages").toString())

    @Test fun requestHasTheContractedShape() {
        enqueue(200, resource(okFile))
        call()
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertEquals("/v1/messages", r.path)
        assertEquals(testKey, r.getHeader("x-api-key"))
        assertEquals("2026-10-21", r.getHeader("anthropic-version"))
        val body = bodyOf(r)
        assertEquals(AiModels.CLAUDE, body["model"]!!.jsonPrimitive.content)
        assertEquals("claude-haiku-5-5", AiModels.CLAUDE)
        assertEquals("4000", body["max_tokens"]!!.jsonPrimitive.content)
        assertEquals(request.system, body["system"]!!.jsonPrimitive.content)
        val message = body["messages"]!!.jsonArray.single().jsonObject
        assertEquals("user", message["role"]!!.jsonPrimitive.content)
        assertEquals(request.user, message["content"]!!.jsonPrimitive.content)
        val config = body["output_config"]!!.jsonObject
        assertEquals("low", config["effort"]!!.jsonPrimitive.content)
        assertEquals("json_schema", config["format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(request.schema, config["format"]!!.jsonObject["schema"])
        listOf("temperature", "top_p", "top_k", "thinking").forEach { assertNull(it, body[it]) }
        // H17: nada fuera de lo que viene de la petición, el modelo y los parámetros.
        assertEquals(setOf("model", "max_tokens", "system", "messages", "output_config"), body.keys)
    }

    @Test fun thinkingBlockInFrontIsSkipped() {
        enqueue(200, resource(okFile))
        assertTrue(call().json.startsWith("{\"today\""))
    }

    @Test fun refusalAndMaxTokensAreBadReply() {
        enqueue(200, """{"content":[{"type":"text","text":"{}"}],"stop_reason":"refusal","usage":{}}""")
        assertEquals(AiFailure.BAD_REPLY, failureOf { call() })
        enqueue(200, """{"content":[{"type":"text","text":"{"}],"stop_reason":"max_tokens","usage":{}}""")
        assertEquals(AiFailure.BAD_REPLY, failureOf { call() })
    }

    @Test fun replyWithoutTextBlockIsBadReply() {
        enqueue(200, buildJsonObject { put("content", buildJsonArray { }) }.toString())
        assertEquals(AiFailure.BAD_REPLY, failureOf { call() })
    }

    @Test fun tokensComeFromUsage() {
        enqueue(200, resource(okFile))
        val reply = call()
        assertEquals(2500, reply.inputTokens)
        assertEquals(1500, reply.outputTokens)
    }
}
