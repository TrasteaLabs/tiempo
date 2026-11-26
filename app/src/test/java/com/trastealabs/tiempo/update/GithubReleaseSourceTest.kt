package com.trastealabs.tiempo.update

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
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

    @Test fun `la API por defecto pide la pagina mas larga`() {
        assertEquals("100", UpdateConfig.RELEASES_API_URL.toHttpUrl().queryParameter("per_page"))
    }

    // 022: con muchas betas seguidas la estable se sale de la lista; entonces se pide la última estable aparte.
    @Test fun `sin estable en la lista se pide releases latest y se anade`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"tag_name":"v1.1.0-beta.40","prerelease":true,"assets":[]}]"""))
        server.enqueue(MockResponse().setBody("""{"tag_name":"v1.0.0","prerelease":false,"assets":[]}"""))
        val result = source.fetch() as ReleasesResult.Ok
        assertEquals(listOf("v1.1.0-beta.40", "v1.0.0"), result.releases.map { it.tag })
        server.takeRequest()
        assertEquals("/repos/trastealabs/tiempo/releases/latest", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test fun `con una estable en la lista no se pide nada mas`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"tag_name":"v1.1.0-beta.40","prerelease":true},{"tag_name":"v1.0.0"}]"""))
        assertEquals(2, (source.fetch() as ReleasesResult.Ok).releases.size)
        assertEquals(1, server.requestCount)
    }

    @Test fun `si latest falla o no hay estable vale la lista`() = runTest {
        val betas = """[{"tag_name":"v1.0.0-beta.4","prerelease":true}]"""
        server.enqueue(MockResponse().setBody(betas))
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(listOf("v1.0.0-beta.4"), (source.fetch() as ReleasesResult.Ok).releases.map { it.tag })
        server.enqueue(MockResponse().setBody(betas))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(listOf("v1.0.0-beta.4"), (source.fetch() as ReleasesResult.Ok).releases.map { it.tag })
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
