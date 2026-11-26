package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.WarningLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CapParserTest {
    private fun fixture(name: String) = javaClass.getResource("/$name")!!.readBytes()

    @Test fun `un CAP naranja de tormentas da el aviso con horario y textos en espanol`() {
        val w = CapParser.parse(fixture("cap_naranja_tormentas.xml"))!!.warning
        assertEquals("2.49.0.0.724.0.ES.20250610093000.999901TO1749538800", w.id)
        assertEquals(WarningLevel.ORANGE, w.level)
        assertEquals("tormentas", w.phenomenon)
        assertEquals("Llanura de Ejemplo", w.areaName)
        assertEquals(TestData.at(2025, 6, 10, 16), w.onset)
        assertEquals(TestData.at(2025, 6, 10, 22), w.expires)
        assertEquals(TestData.at(2025, 6, 10, 9, 30), w.sent)
        assertEquals("40%-70%", w.probability)
        assertEquals("Tormentas con granizo y rachas muy fuertes.", w.description)
        assertEquals("Evite zonas inundables.", w.instruction)
    }

    @Test fun `el CAP trae el poligono de su zona`() {
        val alert = CapParser.parse(fixture("cap_naranja_tormentas.xml"))!!
        val polygon = alert.areas.single().polygons.single()
        assertEquals(LatLon(39.5, -3.5), polygon.first())
        assertEquals(5, polygon.size)
        assertNotNull(alert.areaContaining(TestData.place.position))
        assertNull(alert.areaContaining(LatLon(43.2, -8.0)))
    }

    @Test fun `un aviso verde se descarta`() {
        assertNull(CapParser.parse(fixture("cap_verde.xml")))
    }

    @Test fun `una cancelacion se descarta`() {
        assertNull(CapParser.parse(fixture("cap_cancel.xml")))
    }

    @Test fun `un XML que no es un CAP se descarta sin romper`() {
        assertNull(CapParser.parse("<rss><channel/></rss>".toByteArray()))
        assertNull(CapParser.parse("esto no es XML".toByteArray()))
    }
}
