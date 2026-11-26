package com.trastealabs.tiempo.data.ai

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiWriterTest : WriterContractTest() {
    override val okFile = "ai_openai_ok.json"
    override fun writer() = OpenAiWriter(http, testKey, baseUrl = server.url("/v1/responses").toString())

    @Test fun requestHasTheDocumentedShape() {
        enqueue(200, resource(okFile))
        call()
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertEquals("/v1/responses", r.path)
        assertEquals("Bearer $testKey", r.getHeader("Authorization"))
        val body = bodyOf(r)
        assertEquals(setOf("model", "instructions", "input", "max_output_tokens", "store", "reasoning", "text"), body.keys)
        assertEquals(AiModels.OPENAI, body["model"]!!.jsonPrimitive.content)
        assertEquals(request.system, body["instructions"]!!.jsonPrimitive.content)
        val input = body["input"]!!.jsonArray.single().jsonObject
        assertEquals("user", input["role"]!!.jsonPrimitive.content)
        assertEquals(request.user, input["content"]!!.jsonPrimitive.content)
        assertEquals("false", body["store"]!!.jsonPrimitive.content)
        val format = body["text"]!!.jsonObject["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        assertEquals("true", format["strict"]!!.jsonPrimitive.content)
        assertEquals(request.schema, format["schema"])
    }

    @Test fun readsTextAfterReasoningItemAndTokens() {
        enqueue(200, resource(okFile))
        val reply = call()
        assertTrue(reply.json.startsWith("{\"today\""))
        assertEquals(2300, reply.inputTokens)
        assertEquals(1200, reply.outputTokens)
    }

    @Test fun refusalIncompleteAndEmptyAreBadReply() {
        enqueue(200, """{"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"no"}]}]}""")
        assertEquals(AiFailure.BAD_REPLY, failureOf { call() })
        enqueue(200, """{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[]}""")
        assertEquals(AiFailure.BAD_REPLY, failureOf { call() })
        enqueue(200, """{"status":"completed","output":[]}""")
        assertEquals(AiFailure.BAD_REPLY, failureOf { call() })
    }

    @Test fun quotaOn429IsNoCreditButRateLimitIsRateLimit() {
        enqueue(429, """{"error":{"type":"insufficient_quota","code":"credit_balance_exhausted","message":"sin créditos"}}""")
        assertEquals(AiFailure.NO_CREDIT, failureOf { call() })
        enqueue(429, """{"error":{"type":"rate_limit_error","code":"slow_down","message":"despacio"}}""")
        assertEquals(AiFailure.RATE_LIMIT, failureOf { call() })
    }
}
