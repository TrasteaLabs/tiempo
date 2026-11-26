package com.trastealabs.tiempo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream

class TarReaderTest {
    private fun fixture(name: String) = javaClass.getResource("/$name")!!.readBytes()

    @Test fun `el tar gz de ejemplo da sus ficheros XML`() {
        val files = TarReader.xmlFiles(fixture("aemet_avisos.tar.gz"))
        assertEquals(
            listOf(
                "Z_CAP_C_LEMM_20250610093000_AFAZ_naranja_tormentas.xml",
                "Z_CAP_C_LEMM_20250610093000_AFAZ_verde.xml",
                "Z_CAP_C_LEMM_20250610093000_AFAZ_amarillo_lejos.xml",
            ),
            files.map { it.first },
        )
        val first = files.first().second.toString(Charsets.UTF_8)
        assertTrue(first.startsWith("<?xml"))
        assertTrue(first.trimEnd().endsWith("</alert>"))
    }

    @Test fun `un tar sin comprimir tambien se lee`() {
        val plain = GZIPInputStream(fixture("aemet_avisos.tar.gz").inputStream()).readBytes()
        assertEquals(3, TarReader.xmlFiles(plain).size)
    }
}
