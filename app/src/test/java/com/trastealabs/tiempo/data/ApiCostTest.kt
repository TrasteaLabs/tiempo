package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.AirQuality
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.Reliability
import com.trastealabs.tiempo.domain.StoredReliability
import com.trastealabs.tiempo.domain.airDue
import com.trastealabs.tiempo.domain.reliabilityDue
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** H10: el peso de cada petición, como lo cuenta Open-Meteo, y lo que gasta un día normal. */
class ApiCostTest {
    private val position = LatLon(40.42, -3.70)
    private val http = OkHttpClient()
    private val forecast = OpenMeteoClient(http).requestUrl(position)
    private val reliability = OpenMeteoReliabilityClient(http)
    private val models = reliability.modelsUrl(position)
    private val ensemble = reliability.ensembleUrl(position)
    private val air = OpenMeteoAirClient(http).requestUrl(position)

    @Test fun `la prevision pesa 3,0 llamadas (H2 del 004)`() {
        assertEquals(30, openMeteoWeightTenths(forecast))
    }

    @Test fun `los seis modelos pesan 1,8 llamadas`() {
        assertEquals(18, openMeteoWeightTenths(models))
    }

    @Test fun `el conjunto pesa 24,4 llamadas`() {
        assertEquals(244, openMeteoWeightTenths(ensemble))
    }

    @Test fun `el aire pesa 1,0 llamadas (H2 del 005)`() {
        assertEquals(10, openMeteoWeightTenths(air))
    }

    @Test fun `una peticion pequena pesa al menos una llamada`() {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=40.42&longitude=-3.7&current=temperature_2m".toHttpUrl()
        assertEquals(10, openMeteoWeightTenths(url))
    }

    @Test fun `mas de 14 dias pesa mas`() {
        val days28 = forecast.newBuilder().setQueryParameter("forecast_days", "27").build()   // + 1 de past_days
        assertEquals(60, openMeteoWeightTenths(days28))
        val days16 = models.newBuilder().setQueryParameter("forecast_days", "16").build()
        assertTrue(openMeteoWeightTenths(days16) > 18)
    }

    @Test fun `sin forecast_days cuenta 7 dias`() {
        val url = forecast.newBuilder().removeAllQueryParameters("forecast_days").removeAllQueryParameters("past_days").build()
        assertEquals(30, openMeteoWeightTenths(url))
    }

    @Test fun `un dia normal con refresco cada 30 min y 10 aperturas no pasa de 600 llamadas`() {
        val (tenths, reliabilityFetches, airFetches) = simulateDay(everyMinutes = 30)
        assertEquals(7, reliabilityFetches)
        assertEquals(8, airFetches)
        assertTrue("gasta ${tenths / 10.0} llamadas", tenths <= 6_000)
        assertEquals(365.0, tenths / 10.0, 1.0)
    }

    @Test fun `un dia normal mas 3 guardados abiertos 2 veces con mas de 3 h entre medias no pasa de 600 (H12 del 006)`() {
        // Tres sitios inventados, a más de 5 km entre sí y de «donde estoy».
        val saved = listOf(LatLon(37.39, -5.98), LatLon(43.36, -8.41), LatLon(39.47, -0.38))
        val (tenths, reliabilityFetches, airFetches) = simulateDay(everyMinutes = 30, saved = saved, savedOpens = listOf("10:00", "17:00"))
        assertEquals(7 + 6, reliabilityFetches)
        assertEquals(8 + 6, airFetches)
        assertTrue("gasta ${tenths / 10.0} llamadas", tenths <= 6_000)
        assertEquals(546.0, tenths / 10.0, 2.0)
    }

    @Test fun `aunque trabajo y alarma refresquen cada 15 min no pasa de 600 llamadas`() {
        val (tenths, _, airFetches) = simulateDay(everyMinutes = 15)
        assertEquals(8, airFetches)
        assertTrue("gasta ${tenths / 10.0} llamadas", tenths <= 6_000)
        assertEquals(509.0, tenths / 10.0, 1.0)
    }

    /**
     * Un miércoles cualquiera en Europe/Madrid, sin fiabilidad guardada al empezar: un refresco cada [everyMinutes]
     * más 10 aperturas de la app. Tras cada previsión, la fiabilidad si toca (H8) y el aire si toca (H2 del 005).
     * Devuelve décimas y veces que se pidieron la fiabilidad y el aire.
     */
    private fun simulateDay(
        everyMinutes: Int,
        saved: List<LatLon> = emptyList(),
        savedOpens: List<String> = emptyList(),
    ): Triple<Long, Int, Int> {
        val zone = ZoneId.of("Europe/Madrid")
        val start = ZonedDateTime.of(2026, 11, 4, 0, 0, 0, 0, zone).toEpochSecond()
        val background = (0 until 24 * 60 step everyMinutes).map { start + it * 60L }
        val opens = listOf("08:05", "08:50", "10:15", "12:40", "14:05", "15:30", "17:20", "19:45", "21:10", "23:35")
            .map { val (h, m) = it.split(":").map(String::toInt); start + h * 3600L + m * 60L }
        var stored = StoredReliability()
        var tenths = 0L
        var fetches = 0
        var storedAir: AirQuality? = null
        var airFetches = 0
        // Cada guardado lleva lo suyo: su aire y su fiabilidad, con el mismo ritmo (H11 del 006).
        val savedAt = savedOpens.map { val (h, m) = it.split(":").map(String::toInt); start + h * 3600L + m * 60L }
        val savedRel = saved.associateWith { StoredReliability() }.toMutableMap()
        val savedAir = saved.associateWith { null as AirQuality? }.toMutableMap()
        for (now in savedAt) for (p in saved) {
            tenths += openMeteoWeightTenths(forecast)
            if (airDue(savedAir[p], p, now)) {
                tenths += openMeteoWeightTenths(air)
                savedAir[p] = AirQuality(p, now, emptyList())
                airFetches++
            }
            if (reliabilityDue(savedRel.getValue(p), p, now, zone)) {
                tenths += openMeteoWeightTenths(models) + openMeteoWeightTenths(ensemble)
                savedRel[p] = StoredReliability(Reliability(p, zone.id, now, emptyList()))
                fetches++
            }
        }
        for (now in (background + opens).sorted()) {
            tenths += openMeteoWeightTenths(forecast)
            if (airDue(storedAir, position, now)) {
                tenths += openMeteoWeightTenths(air)
                storedAir = AirQuality(position, now, emptyList())
                airFetches++
            }
            if (reliabilityDue(stored, position, now, zone)) {
                tenths += openMeteoWeightTenths(models) + openMeteoWeightTenths(ensemble)
                stored = StoredReliability(Reliability(position, zone.id, now, emptyList()))
                fetches++
            }
        }
        return Triple(tenths, fetches, airFetches)
    }
}
