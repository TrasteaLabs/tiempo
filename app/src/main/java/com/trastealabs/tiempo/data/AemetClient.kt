package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.WarningLevel
import com.trastealabs.tiempo.domain.pointInPolygon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Lo que el móvil ya sabe de un sitio: de qué tar.gz salieron sus avisos, para qué posición (redondeada) y cuáles son. */
data class KnownWarnings(val source: String, val position: LatLon, val warnings: List<Warning>)

/**
 * [source] = enlace absoluto del tar.gz de donde salen [warnings]; [news] = ese tar.gz no es el de [KnownWarnings]
 * (o no había); [bytes] = bytes de cuerpo que llegaron por la red en esta consulta (0 si todo salió de la caché o fue 304).
 */
data class WarningFetch(val warnings: List<Warning>, val source: String?, val news: Boolean, val bytes: Long)

interface WarningSource {
    /** Avisos cuyo polígono contiene [position]. Lanza si AEMET no responde: lo trata quien llama (H16). */
    suspend fun fetchFor(position: LatLon): List<Warning>

    /**
     * Lo mismo, sabiendo lo que ya hay guardado del sitio (015). Punto de enganche con implementación por defecto: los
     * falsos de los tests de antes no lo implementan y siguen valiendo.
     */
    suspend fun fetch(position: LatLon, known: KnownWarnings?): WarningFetch =
        WarningFetch(fetchFor(position), source = null, news = true, bytes = 0)
}

const val AEMET_CACHE_BYTES = 1L shl 20

/**
 * El cliente de AEMET: el compartido ([base]) con una caché en disco solo para él (H7; en `cacheDir`, que Android puede
 * vaciar: solo se perdería ahorro). Comparte pool, hilos e interceptores con [base] y no lo toca.
 */
fun aemetHttp(base: OkHttpClient, cacheDir: File): OkHttpClient =
    base.newBuilder().cache(Cache(File(cacheDir, "aemet-http"), AEMET_CACHE_BYTES)).build()

/**
 * Avisos de AEMET sin clave: el RSS público enlaza un tar.gz con todos los CAP de España,
 * que se descarga entero y se filtra aquí por polígono. AEMET nunca recibe la ubicación (C9).
 *
 * Con [http] hecho por [aemetHttp] (015): el RSS se revalida siempre (`If-Modified-Since`; un 304 no pesa nada) y el
 * tar.gz, que lleva su hora en el nombre, se sirve de la caché mientras tenga el mismo nombre. Si además se sabe de qué
 * tar.gz salieron los avisos del sitio y no se ha movido, ni se pide ni se analiza.
 */
class AemetClient(
    private val http: OkHttpClient,
    private val rssUrl: String = "https://www.aemet.es/documentos_d/eltiempo/prediccion/avisos/rss/CAP_AFAE_RSS.xml",
) : WarningSource {

    override suspend fun fetchFor(position: LatLon): List<Warning> = fetch(position, null).warnings

    override suspend fun fetch(position: LatLon, known: KnownWarnings?): WarningFetch = withContext(Dispatchers.IO) {
        val base = rssUrl.toHttpUrl()
        val rss = get(base, REVALIDATE)
        val link = tarLink(rss.body) ?: throw IOException("El RSS de AEMET no enlaza el fichero de avisos")
        val url = base.resolve(link)?.forceHttps() ?: throw IOException("Enlace de AEMET no válido: $link")
        val source = url.toString()
        val news = known?.source != source
        // El mismo tar.gz para el mismo sitio: lo guardado sigue valiendo, sin pedir ni analizar nada.
        if (!news && known != null && known.position == position) {
            return@withContext WarningFetch(known.warnings, source, news = false, bytes = rss.networkBytes)
        }
        // El tar.gz no cambia mientras se llame igual: vale el de la caché aunque haya caducado (o el de la red si no estaba).
        val tar = get(url, SAME_NAME_SAME_FILE)
        val warnings = TarReader.xmlFiles(tar.body)
            .mapNotNull { (_, xml) -> CapParser.parse(xml) }
            .mapNotNull { alert -> alert.areaContaining(position)?.let { alert.warning.copy(areaName = it.name) } }
            .distinctBy { it.id }
        WarningFetch(warnings, source, news, rss.networkBytes + tar.networkBytes)
    }

    /** [networkBytes]: lo que de verdad llegó por la red (0 si salió de la caché o fue un 304; las cabeceras no cuentan). */
    private class Fetched(val body: ByteArray, val networkBytes: Long)

    private fun get(url: HttpUrl, cache: CacheControl): Fetched =
        http.newCall(Request.Builder().url(url).cacheControl(cache).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("AEMET respondió ${response.code}")
            val body = response.body?.bytes() ?: throw IOException("AEMET respondió vacío")
            val network = response.networkResponse
            Fetched(body, if (network != null && network.code != 304) body.size.toLong() else 0L)
        }

    /** El enlace al tar.gz se saca del RSS cada vez (puede cambiar de nombre); mejor el del estado completo. */
    private fun tarLink(rss: ByteArray): String? {
        val doc = try {
            parseXml(rss)
        } catch (e: Exception) {
            throw IOException("El RSS de AEMET no se puede leer", e)
        }
        val items = doc.getElementsByTagName("item").elements().map { it.childText("title").orEmpty() to it.childText("link") }
        val tars = items.filter { (_, link) -> link != null && link.endsWith(".tar.gz") }
        return (tars.firstOrNull { (title, _) -> title.contains("completo", ignoreCase = true) } ?: tars.firstOrNull())?.second
    }

    // Android no deja HTTP sin cifrar; aemet.es sirve lo mismo por HTTPS.
    private fun HttpUrl.forceHttps(): HttpUrl =
        if (!isHttps && (host == "aemet.es" || host.endsWith(".aemet.es"))) newBuilder().scheme("https").build() else this

    private companion object {
        /**
         * El RSS siempre pregunta: con `max-age=0` la copia nunca cuenta como fresca y OkHttp la revalida con
         * `If-Modified-Since` (un 304 sirve la copia); sin copia, lo baja entero. No vale `no-cache`: en la petición
         * OkHttp lo entiende como «ignora la caché» y ni manda la condición.
         */
        val REVALIDATE: CacheControl = CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build()

        /** El tar.gz de AEMET lleva la hora de publicación en el nombre: mismo nombre, mismo contenido. */
        val SAME_NAME_SAME_FILE: CacheControl = CacheControl.Builder().maxStale(7, TimeUnit.DAYS).build()
    }
}

data class CapArea(val name: String, val polygons: List<List<LatLon>>)

/** Un aviso leído de un CAP, con sus zonas; [warning] lleva el nombre de la primera. */
data class CapAlert(val warning: Warning, val areas: List<CapArea>) {
    fun areaContaining(p: LatLon): CapArea? = areas.firstOrNull { area -> area.polygons.any { pointInPolygon(p, it) } }
}

/** Lee un CAP 1.2 de AEMET. Devuelve null si es verde, una cancelación o no se entiende. */
object CapParser {
    fun parse(xml: ByteArray): CapAlert? = try {
        parseAlert(parseXml(xml).documentElement)
    } catch (e: Exception) {
        null
    }

    private fun parseAlert(alert: Element): CapAlert? {
        if (alert.localTag() != "alert") return null
        if (alert.childText("msgType") == "Cancel") return null
        if (alert.childText("status")?.let { it != "Actual" } == true) return null
        val infos = alert.children("info")
        val info = infos.firstOrNull { it.childText("language")?.startsWith("es") == true } ?: infos.firstOrNull() ?: return null

        val level = when (info.param("parameter", "AEMET-Meteoalerta nivel")?.lowercase(Locale.ROOT)) {
            "amarillo" -> WarningLevel.YELLOW
            "naranja" -> WarningLevel.ORANGE
            "rojo" -> WarningLevel.RED
            else -> return null
        }
        // "TO;Tormentas" → "tormentas"
        val phenomenon = (info.param("eventCode", "AEMET-Meteoalerta fenomeno")?.substringAfter(';') ?: info.childText("event"))
            ?.trim()?.trimEnd(';')?.lowercase(SPANISH)?.takeIf { it.isNotEmpty() } ?: return null
        val areas = info.children("area").map { area ->
            CapArea(area.childText("areaDesc").orEmpty(), area.children("polygon").mapNotNull { polygon(it.textContent) })
        }
        val warning = Warning(
            id = alert.childText("identifier") ?: return null,
            level = level,
            phenomenon = phenomenon,
            areaName = areas.firstOrNull()?.name.orEmpty(),
            onset = epoch(info.childText("onset") ?: info.childText("effective") ?: return null),
            expires = epoch(info.childText("expires") ?: return null),
            probability = info.param("parameter", "AEMET-Meteoalerta probabilidad"),
            description = info.childText("description").orEmpty(),
            instruction = info.childText("instruction"),
            sent = epoch(alert.childText("sent") ?: return null),
        )
        return CapAlert(warning, areas)
    }

    /** "lat,lon lat,lon …" */
    private fun polygon(text: String): List<LatLon>? =
        text.trim().split(Regex("\\s+")).mapNotNull { pair ->
            val parts = pair.split(',')
            if (parts.size != 2) return@mapNotNull null
            val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            LatLon(lat, lon)
        }.takeIf { it.size >= 3 }

    private fun epoch(text: String): Long = OffsetDateTime.parse(text.trim()).toEpochSecond()

    /** `<parameter><valueName>name</valueName><value>…</value></parameter>` */
    private fun Element.param(tag: String, name: String): String? =
        children(tag).firstOrNull { it.childText("valueName") == name }?.childText("value")

    private val SPANISH = Locale.forLanguageTag("es-ES")
}

/** Lector mínimo de tar (ustar, con nombres largos GNU y PAX), comprimido o no. */
object TarReader {
    private const val BLOCK = 512

    /** Ficheros `.xml` del tar, en orden: (nombre, contenido). */
    fun xmlFiles(archive: ByteArray): List<Pair<String, ByteArray>> {
        val data = if (archive.size >= 2 && archive[0] == 0x1f.toByte() && archive[1] == 0x8b.toByte()) {
            GZIPInputStream(archive.inputStream()).use { it.readBytes() }
        } else {
            archive
        }
        val files = mutableListOf<Pair<String, ByteArray>>()
        var offset = 0
        var longName: String? = null
        while (offset + BLOCK <= data.size) {
            if ((offset until offset + BLOCK).all { data[it] == 0.toByte() }) break
            val name = cString(data, offset, 100)
            val size = octal(data, offset + 124, 12)
            val type = data[offset + 156].toInt().toChar()
            val prefix = if (cString(data, offset + 257, 6).startsWith("ustar")) cString(data, offset + 345, 155) else ""
            offset += BLOCK
            if (size < 0 || offset + size > data.size) throw IOException("Fichero tar cortado")
            val content = data.copyOfRange(offset, offset + size.toInt())
            when (type) {
                'L' -> longName = String(content, Charsets.UTF_8).trimEnd('\u0000')
                'x' -> longName = paxPath(content) ?: longName
                '0', '\u0000' -> {
                    val fullName = longName ?: if (prefix.isNotEmpty()) "$prefix/$name" else name
                    if (fullName.endsWith(".xml", ignoreCase = true)) files += fullName to content
                    longName = null
                }
                else -> longName = null
            }
            offset += ((size + BLOCK - 1) / BLOCK * BLOCK).toInt()
        }
        return files
    }

    private fun cString(data: ByteArray, start: Int, length: Int): String {
        var end = start
        while (end < start + length && data[end] != 0.toByte()) end++
        return String(data, start, end - start, Charsets.UTF_8)
    }

    private fun octal(data: ByteArray, start: Int, length: Int): Long {
        val text = cString(data, start, length).trim()
        return if (text.isEmpty()) 0 else text.toLongOrNull(8) ?: -1
    }

    /** Registros PAX "27 path=carpeta/fichero.xml\n" */
    private fun paxPath(content: ByteArray): String? =
        String(content, Charsets.UTF_8).lineSequence()
            .map { it.substringAfter(' ') }
            .firstOrNull { it.startsWith("path=") }
            ?.removePrefix("path=")
}

// Utilidades de DOM: javax.xml existe igual en Android y en la JVM de los tests.

private fun parseXml(bytes: ByteArray): Document {
    val factory = DocumentBuilderFactory.newInstance()
    // Sin DTD ni entidades externas; no todas las implementaciones aceptan la opción, así que se intenta.
    runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    factory.isExpandEntityReferences = false
    return factory.newDocumentBuilder().parse(bytes.inputStream())
}

private fun Element.localTag(): String = tagName.substringAfter(':')

private fun Element.children(tag: String): List<Element> =
    childNodes.let { nodes -> (0 until nodes.length).mapNotNull { nodes.item(it) as? Element } }.filter { it.localTag() == tag }

private fun Element.childText(tag: String): String? = children(tag).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }

private fun org.w3c.dom.NodeList.elements(): List<Element> = (0 until length).mapNotNull { item(it) as? Element }
