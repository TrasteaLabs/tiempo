package com.trastealabs.tiempo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.InetAddress
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * H12: el interceptor de red suma cada intento que sale hacia Open-Meteo (también si responde con error o hay
 * redirigir) y lo guardado dura. Los nombres de Open-Meteo y AEMET apuntan a un servidor local de pruebas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallCounterTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val now = ZonedDateTime.of(2026, 11, 3, 10, 0, 0, 0, zone).toEpochSecond()
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun counter() = CallCounter(prefs, zone = { zone }, nowSeconds = { now })

    private fun client(counter: CallCounter) = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String) = listOf(InetAddress.getByName(server.hostName))
        })
        .addNetworkInterceptor(CallCountingInterceptor(counter))
        .build()

    private fun get(client: OkHttpClient, host: String, query: String) {
        try {
            client.newCall(Request.Builder().url("http://$host:${server.port}/v1/x?$query").build()).execute().close()
        } catch (_: IOException) {
        }
    }

    private val forecast = "latitude=40.42&longitude=-3.7&current=temperature_2m,weather_code&hourly=temperature_2m"
    private val ensemble = "latitude=40.42&longitude=-3.7&daily=temperature_2m_max,precipitation_sum" +
        "&models=ecmwf_ifs025,gfs025,icon_seamless&forecast_days=10"
    private val air = "latitude=40.42&longitude=-3.7&hourly=european_aqi,european_aqi_pm2_5,european_aqi_pm10," +
        "european_aqi_nitrogen_dioxide,european_aqi_ozone,european_aqi_sulphur_dioxide,pm10,dust,olive_pollen,grass_pollen" +
        "&timezone=auto&timeformat=unixtime&forecast_days=5"

    @Test fun `suma en api y ensemble-api de Open-Meteo y no en AEMET`() {
        repeat(3) { server.enqueue(MockResponse().setBody("{}")) }
        val counter = counter()
        val http = client(counter)
        get(http, "api.open-meteo.com", forecast)
        get(http, "ensemble-api.open-meteo.com", ensemble)
        get(http, "www.aemet.es", "x=1")
        val expected = 10L + 2 * 122
        assertEquals(expected, counter.usage.value.dayTenths)
        assertEquals(expected, counter.usage.value.monthTenths)
    }

    @Test fun `una peticion a air-quality-api suma 1,0 (H2 del 005)`() {
        server.enqueue(MockResponse().setBody("{}"))
        val counter = counter()
        get(client(counter), "air-quality-api.open-meteo.com", air)
        assertEquals(10L, counter.usage.value.dayTenths)
        assertEquals(10L, counter.usage.value.monthTenths)
    }

    @Test fun `suma aunque Open-Meteo responda con error`() {
        server.enqueue(MockResponse().setResponseCode(429))
        val counter = counter()
        get(client(counter), "api.open-meteo.com", forecast)
        assertEquals(10L, counter.usage.value.dayTenths)
    }

    @Test fun `suma cada intento que llega al servidor, tambien las redirecciones`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://api.open-meteo.com:${server.port}/v1/y?$forecast"))
        server.enqueue(MockResponse().setBody("{}"))
        val counter = counter()
        get(client(counter), "api.open-meteo.com", forecast)
        assertEquals(2, server.requestCount)
        assertEquals(20L, counter.usage.value.dayTenths)
    }

    @Test fun `lo guardado sobrevive a un contador nuevo`() {
        server.enqueue(MockResponse().setBody("{}"))
        get(client(counter()), "ensemble-api.open-meteo.com", ensemble)
        val again = counter()
        assertEquals("2026-11-03", again.usage.value.day)
        assertEquals("2026-11", again.usage.value.month)
        assertEquals(244L, again.usage.value.dayTenths)
        assertEquals(244L, again.usage.value.monthTenths)
    }

    @Test fun `la prevision de un sitio guardado suma al mismo contador que la de donde estoy (H12 del 006)`() {
        repeat(2) { server.enqueue(MockResponse().setBody("{}")) }
        val counter = counter()
        val http = client(counter)
        get(http, "api.open-meteo.com", forecast)
        get(http, "api.open-meteo.com", forecast.replace("latitude=40.42&longitude=-3.7", "latitude=37.39&longitude=-5.98"))
        assertEquals(20L, counter.usage.value.dayTenths)
    }
}
