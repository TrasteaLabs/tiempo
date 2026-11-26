package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.LatLon
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class OpenMeteoClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OpenMeteoClient

    private fun fixture() = javaClass.getResource("/openmeteo_madrid.json")!!.readText()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = OpenMeteoClient(OkHttpClient(), server.url("/v1/forecast").toString(), nowSeconds = { 1_749_543_000L })
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `la peticion lleva lat y lon a 2 decimales y los parametros del plan`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        client.fetch(LatLon(40.41678, -3.70379))
        val url = server.takeRequest().requestUrl!!
        assertEquals(40.42, url.queryParameter("latitude")!!.toDouble(), 0.0)
        assertEquals(-3.70, url.queryParameter("longitude")!!.toDouble(), 0.0)
        assertEquals(
            "temperature_2m,apparent_temperature,weather_code,is_day," +
                "wind_speed_10m,wind_direction_10m,wind_gusts_10m,relative_humidity_2m,dew_point_2m,visibility",
            url.queryParameter("current"),
        )
        assertEquals(
            "temperature_2m,precipitation_probability,precipitation,weather_code,is_day," +
                "wind_speed_10m,wind_gusts_10m,wind_direction_10m,relative_humidity_2m,dew_point_2m",
            url.queryParameter("hourly"),
        )
        assertEquals(
            "temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,sunrise,sunset,daylight_duration," +
                "moonrise,moonset,moon_phase",
            url.queryParameter("daily"),
        )
        assertEquals("auto", url.queryParameter("timezone"))
        assertEquals("unixtime", url.queryParameter("timeformat"))
        assertEquals("1", url.queryParameter("past_days"))
        assertEquals("10", url.queryParameter("forecast_days"))
    }

    @Test fun `la respuesta se convierte en snapshot con zona 24 horas de ayer y 10 dias`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val s = client.fetch(LatLon(40.42, -3.70))

        assertEquals("Europe/Madrid", s.zoneId)
        assertEquals(1_749_543_000L, s.fetchedAt)
        assertEquals(19.4, s.current.temperatureC, 0.001)
        assertEquals(18.1, s.current.apparentC, 0.001)
        assertEquals(3, s.current.weatherCode)
        assertTrue(s.current.isDay)

        // ayer 00:00 → final del día 10: 11 días × 24 horas, una por hora
        assertEquals(11 * 24, s.hourly.size)
        s.hourly.zipWithNext().forEach { (a, b) -> assertEquals(3600L, b.time - a.time) }
        val first = Instant.ofEpochSecond(s.hourly.first().time).atZone(ZoneId.of(s.zoneId))
        assertEquals("2025-06-09", first.toLocalDate().toString())
        assertEquals(0, first.hour)
        assertNull(s.hourly[5].precipProbability)

        // 10 días desde hoy: el día de ayer que trae past_days se descarta
        assertEquals(10, s.daily.size)
        assertEquals("2025-06-10", s.daily.first().date)
        assertEquals("2025-06-19", s.daily.last().date)
        assertEquals(24.5, s.daily.first().maxC, 0.001)
    }

    @Test fun `la respuesta trae viento humedad rocio visibilidad sol y el dia de ayer`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val s = client.fetch(LatLon(40.42, -3.70))

        assertEquals(12.3, s.current.windKmh!!, 0.001)
        assertEquals(45.0, s.current.windGustsKmh!!, 0.001)
        assertEquals(225, s.current.windDirectionDeg)
        assertEquals(68, s.current.humidityPct)
        assertEquals(10.2, s.current.dewPointC!!, 0.001)
        assertEquals(24140.0, s.current.visibilityM!!, 0.001)

        // 10 jun 2025 en Madrid: amanece 06:44 y anochece 21:44 (hora local)
        val today = s.daily.first()
        val zone = ZoneId.of(s.zoneId)
        assertEquals("06:44", hhmm(today.sunrise!!, zone))
        assertEquals("21:44", hhmm(today.sunset!!, zone))
        assertEquals(54_000.5, today.daylightSeconds!!, 0.001)

        // el día de past_days ya no se tira: es snapshot.yesterday
        val yesterday = s.yesterday!!
        assertEquals("2025-06-09", yesterday.date)
        assertEquals(24.0, yesterday.maxC, 0.001)
        assertEquals(53_940.5, yesterday.daylightSeconds!!, 0.001)
        assertEquals("06:44", hhmm(yesterday.sunrise!!, zone))
    }

    @Test fun `la respuesta trae viento humedad y rocio por horas y la luna de cada dia (H1 del 004)`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val s = client.fetch(LatLon(40.42, -3.70))
        val zone = ZoneId.of(s.zoneId)

        // Hoy a las 10:00 (la hora 34 desde ayer a las 00:00)
        val hour = s.hourly[34]
        assertEquals("2025-06-10 10:00", ymdhm(hour.time, zone))
        assertEquals(9.4, hour.windKmh!!, 0.001)
        assertEquals(21.2, hour.windGustsKmh!!, 0.001)
        assertEquals(150, hour.windDirectionDeg)
        assertEquals(74, hour.humidityPct)
        assertEquals(9.2, hour.dewPointC!!, 0.001)
        // Una hora con el viento a null: solo ese campo queda vacío.
        assertNull(s.hourly[7].windKmh)
        assertEquals(28.1, s.hourly[7].windGustsKmh!!, 0.001)

        val today = s.daily.first()
        assertEquals("03:50", hhmm(today.moonrise!!, zone))
        assertEquals("15:50", hhmm(today.moonset!!, zone))
        assertEquals(0.454, today.moonPhase!!, 0.0001)
        // ayer también trae la luna (para la que salió ayer y sigue fuera)
        assertEquals(0.42, s.yesterday!!.moonPhase!!, 0.0001)
        assertEquals("03:00", hhmm(s.yesterday!!.moonrise!!, zone))
    }

    @Test fun `un dia sin salida o sin puesta de la luna se guarda sin romper nada (H1 del 004)`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val s = client.fetch(LatLon(40.42, -3.70))

        val noRise = s.daily.first { it.date == "2025-06-12" }
        assertNull(noRise.moonrise)
        assertTrue(noRise.moonset != null && noRise.moonPhase != null)
        val noSet = s.daily.first { it.date == "2025-06-14" }
        assertNull(noSet.moonset)
        assertTrue(noSet.moonrise != null && noSet.moonPhase != null)
        assertEquals(10, s.daily.size)
    }

    @Test fun `sin las variables nuevas la respuesta se sigue leyendo`() = runTest {
        val old = Json.parseToJsonElement(fixture()).jsonObject.let { root ->
            val current = JsonObject(root.getValue("current").jsonObject.filterKeys { it !in newCurrent })
            val hourly = JsonObject(root.getValue("hourly").jsonObject.filterKeys { it !in newHourly })
            val daily = JsonObject(root.getValue("daily").jsonObject.filterKeys { it !in newDaily })
            JsonObject(root + mapOf("current" to current, "hourly" to hourly, "daily" to daily))
        }
        server.enqueue(MockResponse().setBody(old.toString()))
        val s = client.fetch(LatLon(40.42, -3.70))

        assertNull(s.current.windKmh)
        assertNull(s.current.humidityPct)
        assertNull(s.daily.first().sunrise)
        assertNull(s.daily.first().daylightSeconds)
        assertEquals("2025-06-09", s.yesterday!!.date)
        // las de la 004
        s.hourly.forEach {
            assertNull(it.windKmh)
            assertNull(it.windGustsKmh)
            assertNull(it.windDirectionDeg)
            assertNull(it.humidityPct)
            assertNull(it.dewPointC)
        }
        (s.daily + s.yesterday!!).forEach {
            assertNull(it.moonrise)
            assertNull(it.moonset)
            assertNull(it.moonPhase)
        }
    }

    private val newCurrent = setOf(
        "wind_speed_10m", "wind_direction_10m", "wind_gusts_10m", "relative_humidity_2m", "dew_point_2m", "visibility",
    )
    private val newHourly = setOf("wind_speed_10m", "wind_gusts_10m", "wind_direction_10m", "relative_humidity_2m", "dew_point_2m")
    private val newDaily = setOf("sunrise", "sunset", "daylight_duration", "moonrise", "moonset", "moon_phase")

    private fun ymdhm(epoch: Long, zone: ZoneId) =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochSecond(epoch).atZone(zone))

    private fun hhmm(epoch: Long, zone: ZoneId) =
        DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochSecond(epoch).atZone(zone))

    @Test(expected = java.io.IOException::class)
    fun `un error del servidor lanza IOException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        client.fetch(LatLon(40.42, -3.70))
    }
}
