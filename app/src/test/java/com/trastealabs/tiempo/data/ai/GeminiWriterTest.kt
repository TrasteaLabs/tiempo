package com.trastealabs.tiempo.data.ai

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiWriterTest : WriterContractTest() {
    override val okFile = "ai_gemini_ok.json"
    override fun writer() = GeminiWriter(http, testKey, baseUrl = server.url("/v1beta/models").toString())

    @Test fun requestHasTheDocumentedShape() {
        enqueue(200, resource(okFile))
        call()
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertEquals("/v1beta/models/${AiModels.GEMINI}:generateContent", r.path)
        assertEquals(testKey, r.getHeader("x-goog-api-key"))
        // La clave solo va en la cabecera, nunca en la URL.
        assertFalse(r.requestUrl.toString().contains(testKey))
        val body = bodyOf(r)
        assertEquals(setOf("systemInstruction", "contents", "generationConfig"), body.keys)
        val system = body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray.single().jsonObject
        assertEquals(request.system, system["text"]!!.jsonPrimitive.content)
        val content = body["contents"]!!.jsonArray.single().jsonObject
        assertEquals("user", content["role"]!!.jsonPrimitive.content)
        assertEquals(request.user, content["parts"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content)
        val config = body["generationConfig"]!!.jsonObject
        assertEquals("application/json", config["responseMimeType"]!!.jsonPrimitive.content)
        assertEquals(request.schema, config["responseJsonSchema"])
        assertEquals("4000", config["maxOutputTokens"]!!.jsonPrimitive.content)
    }

    @Test fun tokensAddThinkingToOutput() {
        enqueue(200, resource(okFile))
        val reply = call()
        assertEquals(2400, reply.inputTokens)
        assertEquals(1000, reply.outputTokens)
        assertTrue(reply.json.contains("\"today\""))
    }

    @Test fun thoughtPartsAreSkipped() {
        enqueue(
            200,
            """{"candidates":[{"content":{"parts":[{"text":"pienso","thought":true},{"text":"{\"a\":1}"}]},"finishReason":"STOP"}],"usageMetadata":{}}""",
        )
        assertEquals("{\"a\":1}", call().json)
    }

    @Test fun blockedPromptTruncatedOrEmptyAreBadReply() {
        enqueue(200, """{"promptFeedback":{"blockReason":"SAFETY"}}""")
        assertEquals(AiFailure.BAD_REPLY, failureOf { call() })
        enqueue(200, """{"candidates":[{"content":{"parts":[{"text":"{"}]},"finishReason":"MAX_TOKENS"}]}""")
        assertEquals(AiFailure.BAD_REPLY, failureOf { call() })
        enqueue(200, """{"candidates":[{"finishReason":"STOP"}]}""")
        assertEquals(AiFailure.BAD_REPLY, failureOf { call() })
    }

    @Test fun badKeyAlsoWhenReportedAs400() {
        enqueue(400, """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}""")
        assertEquals(AiFailure.BAD_KEY, failureOf { call() })
        assertNull(server.takeRequest().getHeader("authorization"))
    }
}
