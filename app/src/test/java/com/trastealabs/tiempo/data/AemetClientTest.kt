package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.WarningLevel
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AemetClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: AemetClient

    private fun rss() = javaClass.getResource("/aemet_rss.xml")!!.readText()
        .replace("{BASE}", server.url("").toString().trimEnd('/'))

    private fun tarGz() = Buffer().write(javaClass.getResource("/aemet_avisos.tar.gz")!!.readBytes())

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = AemetClient(OkHttpClient(), server.url("/rss/CAP_AFAE_RSS.xml").toString())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `RSS y tar gz dan solo los avisos cuyo poligono contiene el punto`() = runTest {
        server.enqueue(MockResponse().setBody(rss()))
        server.enqueue(MockResponse().setBody(tarGz()))

        val warnings = client.fetchFor(TestData.place.position)

        assertEquals(listOf("tormentas"), warnings.map { it.phenomenon })
        assertEquals(WarningLevel.ORANGE, warnings.single().level)
        assertEquals("Llanura de Ejemplo", warnings.single().areaName)
        assertEquals("/rss/CAP_AFAE_RSS.xml", server.takeRequest().path)
        assertEquals(
            "/documentos_d/eltiempo/prediccion/avisos/cap/Z_CAP_C_LEMM_20250610093000_AFAE.tar.gz",
            server.takeRequest().path,
        )
    }

    @Test fun `en la otra zona salen los suyos`() = runTest {
        server.enqueue(MockResponse().setBody(rss()))
        server.enqueue(MockResponse().setBody(tarGz()))

        val warnings = client.fetchFor(LatLon(43.2, -8.0))

        assertEquals(listOf("lluvias"), warnings.map { it.phenomenon })
        assertEquals("Costa Lejana", warnings.single().areaName)
    }

    @Test fun `la peticion a AEMET no lleva la ubicacion (C9)`() = runTest {
        server.enqueue(MockResponse().setBody(rss()))
        server.enqueue(MockResponse().setBody(tarGz()))
        client.fetchFor(TestData.place.position)
        repeat(2) {
            val url = server.takeRequest().requestUrl!!
            assertTrue(url.query == null)
        }
    }

    @Test fun `si AEMET falla se lanza el error para que lo trate quien llama`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            client.fetchFor(TestData.place.position)
            fail("debería fallar")
        } catch (_: IOException) {
        }
    }

    @Test fun `un RSS sin enlace al tar gz es un fallo`() = runTest {
        server.enqueue(MockResponse().setBody("<rss><channel><item><title>Otra cosa</title></item></channel></rss>"))
        try {
            client.fetchFor(TestData.place.position)
            fail("debería fallar")
        } catch (_: IOException) {
        }
    }

    @Test fun `fetch sin conocido da los mismos avisos que fetchFor, con la fuente y los bytes de red`() = runTest {
        server.enqueue(MockResponse().setBody(rss()))
        server.enqueue(MockResponse().setBody(tarGz()))

        val fetch = client.fetch(TestData.place.position, null)

        assertEquals(listOf("tormentas"), fetch.warnings.map { it.phenomenon })
        assertTrue(fetch.news)
        assertTrue(fetch.source!!.endsWith("/Z_CAP_C_LEMM_20250610093000_AFAE.tar.gz"))
        assertTrue(fetch.bytes > 0)
        repeat(2) { assertTrue(server.takeRequest().requestUrl!!.query == null) }
    }
}
