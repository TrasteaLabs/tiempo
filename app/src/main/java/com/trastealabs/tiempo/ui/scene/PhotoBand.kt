package com.trastealabs.tiempo.ui.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.trastealabs.tiempo.domain.SkyPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * El trozo de la foto (en píxeles de la foto, 1080 × 2400) que se ve en un hueco de [widthPx] × [heightPx]: la foto
 * escalada a su ancho, sin estirarse, con el centro en [ScreenLook.crop] y sin salirse. Lo usan las postales de Sitios,
 * la hoja de borrar y (P3) el widget.
 */
fun photoCrop(photo: SkyPhoto, widthPx: Int, heightPx: Int): Rect =
    bandRect(SCREEN_LOOKS.getValue(photo).crop * PhotoCache.PHOTO_HEIGHT, widthPx, heightPx)

/** [photoCrop] con el centro en [centerY] (px de la foto); pegado al borde si no cabe centrado. */
internal fun bandRect(centerY: Float, widthPx: Int, heightPx: Int): Rect {
    val photoW = PhotoCache.PHOTO_WIDTH
    val photoH = PhotoCache.PHOTO_HEIGHT
    val h = (heightPx.toFloat() * photoW / widthPx.coerceAtLeast(1)).toInt().coerceIn(1, photoH)
    val top = (centerY - h / 2f).toInt().coerceIn(0, photoH - h)
    return Rect(0, top, photoW, top + h)
}

/**
 * Solo esa franja, con `BitmapRegionDecoder`; [sample] = `inSampleSize` (2 en postales). Mide la franja entre [sample]:
 * la foto no se decodifica entera.
 */
fun decodeBand(context: Context, photo: SkyPhoto, widthPx: Int, heightPx: Int, sample: Int = 1): Bitmap {
    val rect = photoCrop(photo, widthPx, heightPx)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    // Las fotos están en drawable-nodpi (no en raw): se abren como flujo para decodificar solo la franja.
    @Suppress("ResourceType")
    val stream = context.applicationContext.resources.openRawResource(PhotoCache.resOf(photo))
    val decoded = stream.use {
        @Suppress("DEPRECATION")
        val decoder = BitmapRegionDecoder.newInstance(it, false) ?: error("No se pudo abrir la foto ${photo.file}")
        try {
            decoder.decodeRegion(rect, options) ?: error("No se pudo leer la franja de ${photo.file}")
        } finally {
            decoder.recycle()
        }
    }
    // En el móvil ya viene a ese tamaño; esto solo cubre a los decodificadores que ignoran `inSampleSize` (Robolectric).
    val w = (rect.width() + sample - 1) / sample
    val h = (rect.height() + sample - 1) / sample
    return if (decoded.width > w + 1 || decoded.height > h + 1) {
        Bitmap.createScaledBitmap(decoded, w, h, true).also { if (it !== decoded) decoded.recycle() }
    } else {
        decoded
    }
}

/**
 * Las franjas de las postales y de la hoja de borrar, por (foto, ancho, alto); se decodifican fuera del hilo principal
 * y se guardan las últimas [capacity] (con 14 fotos posibles se repiten mucho entre las 11 postales).
 */
class PhotoBands(private val context: Context, private val capacity: Int = 8) {
    private data class Key(val photo: SkyPhoto, val width: Int, val height: Int)

    private val bands = object : LinkedHashMap<Key, ImageBitmap>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, ImageBitmap>?) = size > capacity
    }

    /** Cuántas hay guardadas (tests). */
    val count: Int get() = synchronized(bands) { bands.size }

    /** La franja ya decodificada, o `null` si aún no (para pintarla sin esperar). */
    fun peek(photo: SkyPhoto, widthPx: Int, heightPx: Int): ImageBitmap? =
        synchronized(bands) { bands[Key(photo, widthPx, heightPx)] }

    /** [get], pero un fallo al decodificar (memoria, fichero) deja `null`: la postal se queda sin foto, no se cierra la app. */
    suspend fun getOrNull(photo: SkyPhoto, widthPx: Int, heightPx: Int): ImageBitmap? = try {
        get(photo, widthPx, heightPx)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    suspend fun get(photo: SkyPhoto, widthPx: Int, heightPx: Int): ImageBitmap {
        val key = Key(photo, widthPx, heightPx)
        peek(photo, widthPx, heightPx)?.let { return it }
        val image = withContext(Dispatchers.Default) {
            decodeBand(context, photo, widthPx, heightPx, sample = 2).also { it.prepareToDraw() }.asImageBitmap()
        }
        synchronized(bands) { bands[key] = image }
        return image
    }
}
