package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.WeatherModel
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** H2: las dos peticiones (seis modelos y conjunto) y su lectura, con respuestas inventadas de Madrid. */
class ReliabilityClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OpenMeteoReliabilityClient
    private val requests = mutableListOf<RecordedRequest>()

    private var models: MockResponse = MockResponse().setBody(fixture("models"))
    private var ensemble: MockResponse = MockResponse().setBody(fixture("ensemble"))

    private fun fixture(name: String) = javaClass.getResource("/openmeteo_${name}_madrid.json")!!.readText()

    @Before fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(requests) { requests += request }
                    return if (request.path!!.startsWith("/v1/ensemble")) ensemble else models
                }
            }
            start()
        }
        client = OpenMeteoReliabilityClient(
            OkHttpClient(),
            forecastUrl = server.url("/v1/forecast").toString(),
            ensembleUrl = server.url("/v1/ensemble").toString(),
            nowSeconds = { 1_793_000_000L },
        )
    }

    @After fun tearDown() = server.shutdown()

    private fun request(path: String) = requests.single { it.path!!.startsWith(path) }.requestUrl!!

    @Test fun `la peticion de modelos lleva los seis modelos y la posicion redondeada`() = runTest {
        client.fetch(LatLon(40.41678, -3.70379))
        val url = request("/v1/forecast")
        assertEquals("40.42", url.queryParameter("latitude"))
        assertEquals("-3.7", url.queryParameter("longitude"))
        assertEquals("weather_code,temperature_2m_max,precipitation_sum", url.queryParameter("daily"))
        assertEquals(
            "ecmwf_ifs025,icon_seamless,gfs_seamless,meteofrance_seamless,ukmo_seamless,gem_seamless",
            url.queryParameter("models"),
        )
        assertTrue("knmi" !in url.toString() && "metno" !in url.toString())
        assertEquals("auto", url.queryParameter("timezone"))
        assertEquals("10", url.queryParameter("forecast_days"))
        assertEquals(null, url.queryParameter("timeformat"))
        assertEquals(setOf("latitude", "longitude", "daily", "models", "timezone", "forecast_days"), url.queryParameterNames)
    }

    @Test fun `la peticion del conjunto lleva ECMWF GFS e ICON`() = runTest {
        client.fetch(LatLon(40.41678, -3.70379))
        val url = request("/v1/ensemble")
        assertEquals("40.42", url.queryParameter("latitude"))
        assertEquals("-3.7", url.queryParameter("longitude"))
        assertEquals("temperature_2m_max,precipitation_sum", url.queryParameter("daily"))
        assertEquals("ecmwf_ifs025,gfs025,icon_seamless", url.queryParameter("models"))
        assertEquals("auto", url.queryParameter("timezone"))
        assertEquals("10", url.queryParameter("forecast_days"))
        assertEquals(setOf("latitude", "longitude", "daily", "models", "timezone", "forecast_days"), url.queryParameterNames)
    }

    @Test fun `122 escenarios hasta el dia 8 y 82 despues`() = runTest {
        val r = client.fetch(LatLon(40.42, -3.70))
        assertEquals(10, r.days.size)
        assertEquals((5..14).map { "2026-11-%02d".format(it) }, r.days.map { it.date })
        r.days.take(8).forEach { assertEquals(122, it.scenarios) }
        r.days.drop(8).forEach { assertEquals(82, it.scenarios) }
    }

    @Test fun `cada escenario lleva su lluvia por el final de la clave`() = runTest {
        // Día 1 inventado: solo los 40 escenarios de ICON tienen 1,0 mm; el resto 0,9 o nada
        val day = client.fetch(LatLon(40.42, -3.70)).days.first()
        assertEquals(40, day.wetScenarios)
        assertTrue(day.maxLowC < day.maxHighC)
    }

    @Test fun `un modelo que no llega a un dia no sale ese dia`() = runTest {
        val days = client.fetch(LatLon(40.42, -3.70)).days
        assertEquals(WeatherModel.entries.toList(), days[0].models.map { it.model })
        // Météo-France llega 4 días, UKMO 7, ICON 8
        assertEquals(WeatherModel.entries - WeatherModel.METEO_FRANCE, days[4].models.map { it.model })
        assertEquals(listOf(WeatherModel.ECMWF, WeatherModel.ICON, WeatherModel.GFS, WeatherModel.GEM), days[7].models.map { it.model })
        assertEquals(listOf(WeatherModel.ECMWF, WeatherModel.GFS, WeatherModel.GEM), days[8].models.map { it.model })
        val france = days[1].models.single { it.model == WeatherModel.METEO_FRANCE }
        assertEquals(61, france.weatherCode)
        assertEquals(16.3, france.maxC, 0.001)
        assertEquals(2.8, france.precipMm, 0.001)
    }

    @Test fun `se guarda la posicion pedida, no la de la rejilla`() = runTest {
        val r = client.fetch(LatLon(40.41678, -3.70379))
        assertEquals(LatLon(40.42, -3.70), r.position)
        assertEquals("Europe/Madrid", r.zoneId)
        assertEquals(1_793_000_000L, r.fetchedAt)
    }

    @Test fun `si fallan los modelos los dias salen sin modelos`() = runTest {
        models = MockResponse().setResponseCode(500)
        val r = client.fetch(LatLon(40.42, -3.70))
        assertEquals(10, r.days.size)
        assertTrue(r.days.all { it.models.isEmpty() })
    }

    @Test fun `si los modelos llegan raros los dias salen sin modelos`() = runTest {
        models = MockResponse().setBody("{\"error\":true,\"reason\":\"x\"}")
        assertTrue(client.fetch(LatLon(40.42, -3.70)).days.all { it.models.isEmpty() })
    }

    @Test(expected = IOException::class)
    fun `si falla el conjunto lanza IOException`() = runTest {
        ensemble = MockResponse().setResponseCode(500)
        client.fetch(LatLon(40.42, -3.70))
    }

    @Test(expected = IOException::class)
    fun `si el conjunto dice que se ha pasado el limite lanza IOException`() = runTest {
        ensemble = MockResponse().setResponseCode(429).setBody("{\"error\":true,\"reason\":\"Daily API request limit exceeded\"}")
        client.fetch(LatLon(40.42, -3.70))
    }

    @Test(expected = IOException::class)
    fun `un conjunto vacio lanza IOException`() = runTest {
        ensemble = MockResponse().setBody("")
        client.fetch(LatLon(40.42, -3.70))
    }

    @Test(expected = IOException::class)
    fun `un conjunto sin escenarios lanza IOException`() = runTest {
        ensemble = MockResponse().setBody("{\"timezone\":\"Europe/Madrid\",\"daily\":{\"time\":[\"2026-11-05\"]}}")
        client.fetch(LatLon(40.42, -3.70))
    }
}
