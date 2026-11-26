package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.LatLon
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** H1: la petición del aire y su lectura, con una respuesta inventada de Villa Ejemplo (40,0 / −3,0). */
class AirQualityClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OpenMeteoAirClient
    private val fetchedAt = 1_777_950_000L
    private val start = 1_777_932_000L   // 5-may-2026 00:00 en Madrid

    private val fixture = javaClass.getResource("/airquality_villa.json")!!.readText()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = OpenMeteoAirClient(OkHttpClient(), baseUrl = server.url("/v1/air-quality").toString(), nowSeconds = { fetchedAt })
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `la URL lleva las 10 variables, 5 dias, zona auto, unixtime y la posicion redondeada`() = runTest {
        server.enqueue(MockResponse().setBody(fixture))
        client.fetch(LatLon(40.41678, -3.70379))
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/air-quality", url.encodedPath)
        assertEquals("40.42", url.queryParameter("latitude"))
        assertEquals("-3.7", url.queryParameter("longitude"))
        assertEquals(
            "european_aqi,european_aqi_pm2_5,european_aqi_pm10,european_aqi_nitrogen_dioxide,european_aqi_ozone," +
                "european_aqi_sulphur_dioxide,pm10,dust,olive_pollen,grass_pollen",
            url.queryParameter("hourly"),
        )
        assertEquals("5", url.queryParameter("forecast_days"))
        assertEquals("auto", url.queryParameter("timezone"))
        assertEquals("unixtime", url.queryParameter("timeformat"))
        assertEquals(setOf("latitude", "longitude", "hourly", "timezone", "timeformat", "forecast_days"), url.queryParameterNames)
        assertNull(url.queryParameter("current"))
        assertNull(url.queryParameter("daily"))
        assertNull(url.queryParameter("past_days"))
    }

    @Test fun `se leen indices, subindices, PM10, polvo y polen`() = runTest {
        server.enqueue(MockResponse().setBody(fixture))
        val air = client.fetch(LatLon(40.0, -3.0))
        assertEquals(LatLon(40.0, -3.0), air.position)
        assertEquals(fetchedAt, air.fetchedAt)
        val first = air.hours.first()
        assertEquals(start, first.time)
        assertEquals(20, first.aqi)
        assertEquals(10, first.pm25Index)
        assertEquals(8, first.pm10Index)
        assertEquals(5, first.no2Index)
        assertEquals(20, first.ozoneIndex)
        assertEquals(1, first.so2Index)
        assertEquals(12.0, first.pm10!!, 0.0)
        assertEquals(1.0, first.dust!!, 0.0)
        assertEquals(40.0, first.olivePollen!!, 0.0)
        assertEquals(8.0, first.grassPollen!!, 0.0)
        // la última, la 120: índice sí, polen no
        val last = air.hours.last()
        assertEquals(start + 119 * 3600L, last.time)
        assertTrue(last.aqi != null)
        assertNull(last.olivePollen)
        assertNull(last.grassPollen)
        // el polen llega hasta la hora 98 (la de las 01:00 del cuarto día siguiente)
        assertEquals(start + 97 * 3600L, air.hours.last { it.olivePollen != null }.time)
    }

    @Test fun `un null deja ese campo en null`() = runTest {
        server.enqueue(MockResponse().setBody(fixture))
        val hour40 = client.fetch(LatLon(40.0, -3.0)).hours.single { it.time == start + 40 * 3600L }
        assertNull(hour40.so2Index)
        assertTrue(hour40.aqi != null)
    }

    @Test fun `una variable que falta deja ese campo en null y los indices se redondean`() = runTest {
        server.enqueue(MockResponse().setBody("""{"hourly":{"time":[100,3700],"european_aqi":[34.6,null],"pm10":[12.5,13.0]}}"""))
        val hours = client.fetch(LatLon(40.0, -3.0)).hours
        assertEquals(2, hours.size)
        assertEquals(35, hours[0].aqi)
        assertNull(hours[0].dust)
        assertNull(hours[0].olivePollen)
        assertNull(hours[0].ozoneIndex)
        assertNull(hours[1].aqi)
        assertEquals(13.0, hours[1].pm10!!, 0.0)
    }

    @Test fun `las horas sin ningun valor no se guardan`() = runTest {
        server.enqueue(MockResponse().setBody(fixture))
        val hours = client.fetch(LatLon(40.0, -3.0)).hours
        assertEquals(119, hours.size)
        assertTrue(hours.none { it.time == start + 30 * 3600L })
    }

    @Test fun `un cuerpo roto da IOException`() = runTest {
        server.enqueue(MockResponse().setBody("{ esto no es json"))
        assertFails { client.fetch(LatLon(40.0, -3.0)) }
    }

    @Test fun `sin hourly time da IOException`() = runTest {
        server.enqueue(MockResponse().setBody("""{"hourly":{"european_aqi":[30]}}"""))
        assertFails { client.fetch(LatLon(40.0, -3.0)) }
    }

    @Test fun `un HTTP 500 da IOException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertFails { client.fetch(LatLon(40.0, -3.0)) }
    }

    private suspend fun assertFails(block: suspend () -> Unit) {
        try {
            block()
            fail("tenía que fallar")
        } catch (_: IOException) {
        }
    }
}
