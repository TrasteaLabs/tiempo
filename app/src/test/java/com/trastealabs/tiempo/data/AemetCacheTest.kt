package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.WarningLevel
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream

/**
 * La caché de AEMET (015 P1): servidor falso que se porta como el de verdad. El RSS manda `Last-Modified` y contesta
 * 304 a `If-Modified-Since`; el tar.gz lleva `Cache-Control: max-age=345600` (4 días), con su hora en el nombre.
 */
class AemetCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private val tarPath = "/documentos_d/eltiempo/prediccion/avisos/cap/"
    private val firstTar = "Z_CAP_C_LEMM_20250610093000_AFAE.tar.gz"
    private val secondTar = "Z_CAP_C_LEMM_20250610100000_AFAE.tar.gz"
    private val firstModified = "Tue, 10 Jun 2025 07:30:00 GMT"
    private val secondModified = "Tue, 10 Jun 2025 08:00:00 GMT"

    private val here = TestData.place.position              // el aviso naranja de tormentas
    private val coast = LatLon(43.2, -8.0)                  // el amarillo de lluvias, otra zona del mismo tar.gz
    private val nowhere = LatLon(38.0, -4.0)                // fuera de todo polígono

    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private lateinit var client: AemetClient
    private val base = OkHttpClient()

    // Lo que publica el servidor ahora mismo.
    private var rssModified = firstModified
    private var rssCacheControl: String? = null
    private var currentTar = firstTar
    private val tars = mutableMapOf<String, ByteArray>()

    /** Cada petición que llegó: ruta, `If-Modified-Since` y cabeceras enteras. */
    private val seen = mutableListOf<RecordedRequest>()

    private fun rssRequests() = seen.filter { it.path!!.startsWith("/rss/") }
    private fun tarRequests() = seen.filter { it.path!!.endsWith(".tar.gz") }

    private fun rssBody() = javaClass.getResource("/aemet_rss.xml")!!.readText()
        .replace("{BASE}", server.url("").toString().trimEnd('/'))
        .replace(firstTar, currentTar)

    private fun fixture(name: String) = javaClass.getResource(name)!!.readBytes()

    @Before fun setUp() {
        tars[firstTar] = fixture("/aemet_avisos.tar.gz")
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    seen += request
                    val path = request.path!!
                    return when {
                        path.startsWith("/rss/") ->
                            if (request.getHeader("If-Modified-Since") == rssModified) {
                                MockResponse().setResponseCode(304).setHeader("Last-Modified", rssModified)
                                    .also { r -> rssCacheControl?.let { r.setHeader("Cache-Control", it) } }
                            } else {
                                MockResponse().setBody(rssBody()).setHeader("Last-Modified", rssModified)
                                    .also { r -> rssCacheControl?.let { r.setHeader("Cache-Control", it) } }
                            }
                        path.startsWith(tarPath) && tars.containsKey(path.removePrefix(tarPath)) ->
                            MockResponse().setBody(Buffer().write(tars.getValue(path.removePrefix(tarPath))))
                                .setHeader("Cache-Control", "max-age=345600")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
        http = aemetHttp(base, tmp.newFolder())
        client = AemetClient(http, server.url("/rss/CAP_AFAE_RSS.xml").toString())
    }

    @After fun tearDown() {
        http.cache?.close()
        server.shutdown()
    }

    private fun tormentas() = listOf(Warning("x", WarningLevel.ORANGE, "tormentas", "Llanura de Ejemplo", 1, 2, null, "", null, 0))

    // ---------- H1 ----------

    @Test fun `H1 si el RSS no ha cambiado se pregunta con If-Modified-Since, llega 304 y no se pide el tar gz`() = runTest {
        val first = client.fetch(here, null)
        assertEquals(listOf("tormentas"), first.warnings.map { it.phenomenon })
        assertTrue("la primera vez baja de verdad", first.bytes > fixture("/aemet_avisos.tar.gz").size)
        val before = server.requestCount

        val second = client.fetch(here, KnownWarnings(first.source!!, here, first.warnings))

        assertEquals("una sola petición más", before + 1, server.requestCount)
        assertEquals(2, rssRequests().size)
        assertNull(rssRequests()[0].getHeader("If-Modified-Since"))
        assertEquals(firstModified, rssRequests()[1].getHeader("If-Modified-Since"))
        assertEquals(1, tarRequests().size)
        assertEquals(0L, second.bytes)
        assertEquals("el mismo tar.gz de antes: nada nuevo", false, second.news)
        assertEquals(first.warnings, second.warnings)
        assertEquals(first.source, second.source)
    }

    @Test fun `H1 el RSS se revalida aunque el servidor lo declare fresco por mucho tiempo`() = runTest {
        rssCacheControl = "max-age=86400"
        client.fetch(here, null)
        client.fetch(here, null)
        assertEquals(2, rssRequests().size)
        assertEquals(firstModified, rssRequests()[1].getHeader("If-Modified-Since"))
    }

    // ---------- H2 ----------

    @Test fun `H2 si el RSS cambia pero enlaza el mismo tar gz, el tar gz no se baja otra vez`() = runTest {
        client.fetch(here, null)
        rssModified = secondModified            // otro Last-Modified: el RSS se baja entero (200)

        val second = client.fetch(here, null)

        assertEquals(2, rssRequests().size)
        assertEquals("el tar.gz salió de la caché", 1, tarRequests().size)
        assertTrue("solo el RSS llegó por la red, no el tar.gz", second.bytes in 1 until fixture("/aemet_avisos.tar.gz").size.toLong())
        assertEquals(listOf("tormentas"), second.warnings.map { it.phenomenon })
    }

    // ---------- H3 ----------

    @Test fun `H3 con el mismo tar gz y la misma posicion se reutilizan los avisos sin analizar nada`() = runTest {
        val first = client.fetch(here, null)
        val invented = tormentas().map { it.copy(id = "inventado-que-no-esta-en-el-tar") }
        val before = server.requestCount

        val second = client.fetch(here, KnownWarnings(first.source!!, here, invented))

        assertEquals(invented, second.warnings)           // no salen del tar.gz: no se analizó ningún CAP
        assertFalse(second.news)
        assertEquals(0L, second.bytes)
        assertEquals(before + 1, server.requestCount)     // solo la pregunta del RSS
        assertEquals(1, tarRequests().size)
    }

    @Test fun `H3 también con el RSS cambiado si enlaza el mismo tar gz`() = runTest {
        val first = client.fetch(here, null)
        val invented = tormentas().map { it.copy(id = "inventado") }
        rssModified = secondModified

        val second = client.fetch(here, KnownWarnings(first.source!!, here, invented))

        assertEquals(invented, second.warnings)
        assertFalse(second.news)
        assertEquals(1, tarRequests().size)
    }

    // ---------- H4 ----------

    @Test fun `H4 al cambiar de posicion sin publicacion nueva salen los avisos del sitio nuevo sin bajar nada`() = runTest {
        val first = client.fetch(here, null)
        val known = KnownWarnings(first.source!!, here, first.warnings)
        val before = server.requestCount

        val other = client.fetch(coast, known)

        assertEquals(listOf("lluvias"), other.warnings.map { it.phenomenon })
        assertEquals("Costa Lejana", other.warnings.single().areaName)
        assertFalse(other.news)
        assertEquals(0L, other.bytes)
        assertEquals("solo la pregunta del RSS", before + 1, server.requestCount)
        assertEquals(1, tarRequests().size)

        val none = client.fetch(nowhere, known)
        assertTrue(none.warnings.isEmpty())
        assertEquals(1, tarRequests().size)
    }

    // ---------- H5 ----------

    @Test fun `H5 cuando AEMET publica un tar gz nuevo, los avisos son los nuevos en esa misma consulta`() = runTest {
        rssCacheControl = "max-age=86400"       // aun así: el RSS se revalida y no se pierde el aviso nuevo
        val first = client.fetch(coast, null)
        assertEquals(listOf("lluvias"), first.warnings.map { it.phenomenon })

        // AEMET publica otro: el mismo aviso de la costa, ahora de viento
        val cap = String(fixture("/cap_amarillo_lejos.xml")).replace("PR;Lluvias", "VI;Viento")
        tars[secondTar] = tarGz("aviso.xml" to cap.toByteArray())
        currentTar = secondTar
        rssModified = secondModified

        val second = client.fetch(coast, KnownWarnings(first.source!!, coast, first.warnings))

        assertTrue(second.news)
        assertEquals(listOf("viento"), second.warnings.map { it.phenomenon })
        assertEquals(2, tarRequests().size)
        assertTrue(second.source!!.endsWith(secondTar))
        assertTrue(second.bytes > 0)

        // y el siguiente refresco ya no baja nada
        val third = client.fetch(coast, KnownWarnings(second.source!!, coast, second.warnings))
        assertFalse(third.news)
        assertEquals(0L, third.bytes)
        assertEquals(2, tarRequests().size)
    }

    @Test fun `si el tar gz nuevo falla al bajar, se lanza el error para que se queden los avisos guardados`() = runTest {
        val first = client.fetch(here, null)
        currentTar = secondTar                  // el RSS lo enlaza pero el servidor no lo tiene (404)
        rssModified = secondModified
        try {
            client.fetch(here, KnownWarnings(first.source!!, here, first.warnings))
            fail("debería fallar")
        } catch (_: IOException) {
        }
    }

    // ---------- Caché vaciada ----------

    @Test fun `con la cache vaciada la peticion va sin condicion y se baja todo, nunca avisos vacios`() = runTest {
        client.fetch(here, null)
        http.cache!!.evictAll()                 // Android puede vaciar cacheDir cuando quiera

        val second = client.fetch(here, null)

        assertNull(rssRequests()[1].getHeader("If-Modified-Since"))
        assertEquals(2, tarRequests().size)
        assertTrue(second.bytes > fixture("/aemet_avisos.tar.gz").size)
        assertEquals(listOf("tormentas"), second.warnings.map { it.phenomenon })
    }

    // ---------- H6 / C9 ----------

    @Test fun `H6 ninguna peticion, tampoco las condicionales, lleva la ubicacion`() = runTest {
        val first = client.fetch(here, null)
        client.fetch(coast, KnownWarnings(first.source!!, here, first.warnings))
        rssModified = secondModified
        client.fetch(here, null)

        assertTrue(seen.size >= 4)
        for (request in seen) {
            val url = request.requestUrl!!
            assertNull(url.query)
            val everything = url.toString() + request.headers.toString()
            for (needle in listOf("40.0", "-3.0", "43.2", "-8.0")) {
                assertFalse("$needle en ${request.path} / ${request.headers}", everything.contains(needle, ignoreCase = true))
            }
        }
    }

    // ---------- H7 ----------

    @Test fun `H7 aemetHttp tiene cache y el cliente compartido no`() {
        assertNotNull(http.cache)
        assertNull(base.cache)
        assertEquals(AEMET_CACHE_BYTES, http.cache!!.maxSize())
        assertEquals(1L shl 20, AEMET_CACHE_BYTES)
    }

    @Test fun `H7 la cache solo aparece en el cliente de AEMET`() {
        val root = File("src/main/java")
        assertTrue("no encuentro ${root.absolutePath}", root.isDirectory)
        val users = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { file ->
            file.readLines().any { line ->
                val code = line.substringBefore("//")
                Regex("""\bCache\(""").containsMatchIn(code) || Regex("""\.cache\(""").containsMatchIn(code)
            }
        }.map { it.name }.toList()
        assertEquals(listOf("AemetClient.kt"), users)
    }

    // ---------- ayudas ----------

    /** Un tar.gz mínimo (ustar) con los ficheros dados, como el de AEMET pero pequeño. */
    private fun tarGz(vararg files: Pair<String, ByteArray>): ByteArray {
        val tar = ByteArrayOutputStream()
        for ((name, content) in files) {
            val header = ByteArray(512)
            name.toByteArray().copyInto(header)
            "%011o".format(content.size).toByteArray().copyInto(header, 124)
            header[156] = '0'.code.toByte()
            "ustar".toByteArray().copyInto(header, 257)
            tar.write(header)
            tar.write(content)
            tar.write(ByteArray((512 - content.size % 512) % 512))
        }
        tar.write(ByteArray(1024))
        return ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(tar.toByteArray()) } }.toByteArray()
    }
}
