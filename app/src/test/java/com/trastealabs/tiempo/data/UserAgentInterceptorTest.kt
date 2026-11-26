package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.BuildConfig
import com.trastealabs.tiempo.update.UpdateConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 021: todas las llamadas salen con el User-Agent de la app, sin modelo de móvil ni versión de Android. */
class UserAgentInterceptorTest {
    @Test fun `pone el User-Agent de la app y pisa el de OkHttp`() {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse())
            val client = OkHttpClient.Builder().addInterceptor(UserAgentInterceptor(UpdateConfig.userAgent)).build()
            client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()
            val ua = server.takeRequest().getHeader("User-Agent")!!
            assertEquals("Tiempo/${BuildConfig.VERSION_NAME} (+https://github.com/trastealabs/tiempo)", ua)
            assertFalse(ua.contains("okhttp"))
            assertFalse(ua.contains("Android"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun `un cliente derivado lo conserva`() {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse())
            val base = OkHttpClient.Builder().addInterceptor(UserAgentInterceptor("Tiempo/9.9.9 (+x)")).build()
            base.newBuilder().build().newCall(Request.Builder().url(server.url("/y")).build()).execute().close()
            assertEquals("Tiempo/9.9.9 (+x)", server.takeRequest().getHeader("User-Agent"))
        } finally {
            server.shutdown()
        }
    }
}
