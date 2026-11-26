package com.trastealabs.tiempo.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 021: respuestas de GitHub con un servidor de mentira. Los tests nunca llaman a GitHub. */
class GithubReleaseSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var source: GithubReleaseSource

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        source = GithubReleaseSource(OkHttpClient(), server.url("/repos/trastealabs/tiempo/releases?per_page=30").toString())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `200 da la lista y la peticion pide la API de GitHub`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"tag_name":"v1.0.0","assets":[]}]"""))
        val result = source.fetch()
        assertTrue(result is ReleasesResult.Ok)
        assertEquals("v1.0.0", (result as ReleasesResult.Ok).releases.single().tag)
        val request = server.takeRequest()
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("30", request.requestUrl!!.queryParameter("per_page"))
        assertEquals(null, request.getHeader("Authorization"))
    }

    @Test fun `403 y 429 son el limite de GitHub`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"API rate limit exceeded"}"""))
        assertEquals(ReleasesResult.Failed(CheckFailure.RATE_LIMITED), source.fetch())
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(ReleasesResult.Failed(CheckFailure.RATE_LIMITED), source.fetch())
    }

    @Test fun `404 es que el repositorio aun no existe`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
        assertEquals(ReleasesResult.Failed(CheckFailure.NOT_FOUND), source.fetch())
    }

    @Test fun `500 es otro fallo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502))
        assertEquals(ReleasesResult.Failed(CheckFailure.OTHER), source.fetch())
    }

    @Test fun `sin red es OFFLINE y no lanza`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(ReleasesResult.Failed(CheckFailure.OFFLINE), source.fetch())
    }

    @Test fun `servidor apagado es OFFLINE`() = runTest {
        val url = server.url("/x").toString()
        server.shutdown()
        assertEquals(ReleasesResult.Failed(CheckFailure.OFFLINE), GithubReleaseSource(OkHttpClient(), url).fetch())
        server = MockWebServer().apply { start() }   // para el tearDown
    }

    @Test fun `una respuesta que no es una lista es OTHER`() = runTest {
        server.enqueue(MockResponse().setBody("""{"message":"raro"}"""))
        assertEquals(ReleasesResult.Failed(CheckFailure.OTHER), source.fetch())
        server.enqueue(MockResponse().setBody("<html>"))
        assertEquals(ReleasesResult.Failed(CheckFailure.OTHER), source.fetch())
    }
}
