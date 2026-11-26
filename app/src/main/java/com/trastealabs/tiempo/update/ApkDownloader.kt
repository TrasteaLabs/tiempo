package com.trastealabs.tiempo.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

fun interface ApkDownloader {
    /**
     * Baja [url] a [dest] y devuelve su SHA-256 en hexadecimal. [onProgress] recibe bytes bajados y total (0 si no se
     * sabe). Lanza [IOException] si no llega entero; entonces [dest] no queda.
     */
    suspend fun download(url: String, dest: File, expectedSize: Long, onProgress: (Long, Long) -> Unit): String
}

/** Baja con OkHttp (sigue la redirección de GitHub a su almacén) y calcula el SHA-256 mientras escribe. */
class HttpApkDownloader(private val http: OkHttpClient) : ApkDownloader {
    override suspend fun download(url: String, dest: File, expectedSize: Long, onProgress: (Long, Long) -> Unit): String =
        withContext(Dispatchers.IO) {
            dest.parentFile?.mkdirs()
            val part = File(dest.path + ".part")
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("GitHub respondió ${response.code}")
                    val body = response.body ?: throw IOException("Respuesta sin cuerpo")
                    val total = body.contentLength().takeIf { it > 0 } ?: expectedSize.coerceAtLeast(0)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var done = 0L
                    var lastReported = -1L
                    body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                done += read
                                // Cada ~1 % (o cada 256 KB si no se sabe el total): no repintar por cada trozo.
                                val step = if (total > 0) total / 100 else 256 * 1024L
                                if (done - lastReported >= step) {
                                    lastReported = done
                                    onProgress(done, total)
                                }
                            }
                        }
                    }
                    if (total > 0 && done != total) throw IOException("Llegaron $done de $total bytes")
                    onProgress(done, total)
                    if (dest.exists()) dest.delete()
                    if (!part.renameTo(dest)) throw IOException("No se pudo guardar el fichero")
                    digest.digest().toHex()
                }
            } finally {
                part.delete()
            }
        }
}
