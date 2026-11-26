package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.LatLon
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class PlaceLookupTest {
    private val lookup = AssetPlaceLookup(
        PlaceIndex {
            ("R\tES\tMadrid\nR\tCO\tCundinamarca\nR\tES\tBarcelona\n" +
                "Madrid\t0\t3255944\t40.42\t-3.70\nMadrid\t1\t0\t4.73\t-74.26\nGetafe\t0\t180747\t40.31\t-3.73\n" +
                "Barcelona\t2\t1620343\t41.39\t2.16\n").byteInputStream()
        },
    )

    private fun real() = AssetPlaceLookup(PlaceIndex { GZIPInputStream(File("src/main/assets/places.tsv.gz").inputStream()) })

    @Test fun `el centro de Madrid devuelve Madrid`() {
        val p = lookup.nearest(LatLon(40.4168, -3.7038))
        assertEquals("Madrid", p.name)
        assertEquals("ES", p.countryCode)
    }

    @Test fun `el sitio mas cercano gana aunque otro tenga el mismo nombre`() {
        assertEquals("CO", lookup.nearest(LatLon(4.7, -74.2)).countryCode)
    }

    @Test fun `un punto cerca de Getafe devuelve Getafe`() {
        assertEquals("Getafe", lookup.nearest(LatLon(40.30, -3.72)).name)
    }

    @Test fun `la lista real incluida en la app resuelve Madrid`() {
        val real = real()
        val p = real.nearest(LatLon(40.4168, -3.7038))
        assertEquals("Madrid", p.name)
        assertEquals("ES", p.countryCode)
    }

    private data class Sample(val lat: Double, val lon: Double, val name: String, val countryCode: String)

    /**
     * H1 (006): 40 coordenadas inventadas (rejilla sobre España y unas cuantas fuera), con el nombre que daba la lista
     * de antes de regenerarla con habitantes y regiones. Con la lista nueva tienen que dar lo mismo.
     */
    private val samples = listOf(
        Sample(36.7, -8.1, "Faro", "PT"),
        Sample(36.7, -6.2, "Añina", "ES"),
        Sample(36.7, -4.3, "Cala del Moral", "ES"),
        Sample(36.7, -2.4, "El Bobar", "ES"),
        Sample(36.7, -0.5, "Arzew", "DZ"),
        Sample(38.1, -8.1, "Ferreira do Alentejo", "PT"),
        Sample(38.1, -6.2, "Pallares", "ES"),
        Sample(38.1, -4.3, "Huertos Familiares San Fernando", "ES"),
        Sample(38.1, -2.4, "Cañadas", "ES"),
        Sample(38.1, -0.5, "Isla Plana", "ES"),
        Sample(39.5, -8.1, "Sardoal", "PT"),
        Sample(39.5, -6.2, "Santa Marta de Magasca", "ES"),
        Sample(39.5, -4.3, "San Pablo", "ES"),
        Sample(39.5, -2.4, "El Rus", "ES"),
        Sample(39.5, -0.5, "La Presa", "ES"),
        Sample(40.9, -8.1, "Arouca", "PT"),
        Sample(40.9, -6.2, "Pelarrodríguez", "ES"),
        Sample(40.9, -4.3, "Abades", "ES"),
        Sample(40.9, -2.4, "Sotodosos", "ES"),
        Sample(40.9, -0.5, "Los Olmos", "ES"),
        Sample(42.3, -8.1, "A Costa", "ES"),
        Sample(42.3, -6.2, "Tabuyo del Monte", "ES"),
        Sample(42.3, -4.3, "Itero de la Vega", "ES"),
        Sample(42.3, -2.4, "Trevijano", "ES"),
        Sample(42.3, -0.5, "Bentué de Rasal", "ES"),
        Sample(43.3, -8.1, "Viña", "ES"),
        Sample(43.3, -6.2, "Meruja", "ES"),
        Sample(43.3, -4.3, "San Vicente del Monte", "ES"),
        Sample(43.3, -2.4, "Olatz", "ES"),
        Sample(43.3, -0.5, "Artiguelouve", "FR"),
        Sample(39.57, 2.65, "Palma", "ES"),
        Sample(28.47, -16.25, "Santa Cruz de Tenerife", "ES"),
        Sample(41.39, 2.17, "Barcelona", "ES"),
        Sample(38.35, -0.48, "Alicante", "ES"),
        Sample(35.89, -5.32, "Ceuta", "ES"),
        Sample(38.72, -9.14, "Graça", "PT"),
        Sample(48.86, 2.35, "Paris 04 Hôtel-de-Ville", "FR"),
        Sample(51.51, -0.13, "London", "GB"),
        Sample(-34.6, -58.38, "Buenos Aires", "AR"),
        Sample(35.68, 139.69, "Hatsudai", "JP"),
    )

    @Test fun `las 40 coordenadas de ejemplo dan el mismo nombre que con la lista de antes (H1)`() {
        val real = real()
        val wrong = samples.mapNotNull { s ->
            val p = real.nearest(LatLon(s.lat, s.lon))
            if (p.name == s.name && p.countryCode == s.countryCode) null else "${s.lat},${s.lon}: ${p.name} ${p.countryCode} ≠ ${s.name} ${s.countryCode}"
        }
        assertEquals(emptyList<String>(), wrong)
        assertEquals(40, samples.size)
    }
}
