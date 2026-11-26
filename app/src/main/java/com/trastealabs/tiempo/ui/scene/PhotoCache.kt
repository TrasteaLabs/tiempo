package com.trastealabs.tiempo.ui.scene

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.SkyPhoto

/**
 * Las fotos del cielo (009), decodificadas al tamaño en que se pintan y guardadas en memoria: la que se ve, la del
 * fundido y alguna más ([capacity]). Una por actividad. Se decodifica con `inSampleSize` (las franjas de los días, a un
 * cuarto) en `ARGB_8888` y se sube a la GPU con `prepareToDraw()`.
 */
class PhotoCache(context: Context, private val capacity: Int = 3) {
    private val resources: Resources = context.applicationContext.resources

    private data class Key(val res: Int, val sample: Int)

    private val photos = object : LinkedHashMap<Key, ImageBitmap>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, ImageBitmap>?) = size > capacity
    }

    /** [photo] para pintarla en [widthPx] × [heightPx] (cubriendo): lo más pequeña que no pierda detalle. */
    fun get(photo: SkyPhoto, widthPx: Int, heightPx: Int): ImageBitmap {
        val sample = sampleFor(PHOTO_WIDTH, PHOTO_HEIGHT, widthPx, heightPx)
        return load(resOf(photo), sample)
    }

    /**
     * La capa de nubes de delante (P2), para [heightPx] de alto de pantalla. Se guarda a su resolución de cálculo, la
     * mitad de lo que ocupa en pantalla (es blanda y la app la estira): en pantallas pequeñas se reduce otro tanto.
     */
    fun cloudLayer(heightPx: Int): ImageBitmap {
        val sample = sampleFor(LAYER_WIDTH, LAYER_HEIGHT, 1, heightPx * 7 / 20)
        return load(R.drawable.sky_cloud_layer, sample)
    }

    /** Cuántas hay guardadas (tests). */
    val count: Int get() = synchronized(photos) { photos.size }

    private fun load(@DrawableRes res: Int, sample: Int): ImageBitmap = synchronized(photos) {
        photos.getOrPut(Key(res, sample)) {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeResource(resources, res, options) ?: error("No se pudo leer la foto $res")
            bitmap.prepareToDraw()
            bitmap.asImageBitmap()
        }
    }

    companion object {
        /** El tamaño de las fotos que escribe `tools/build_skies.py`. */
        const val PHOTO_WIDTH = 1080
        const val PHOTO_HEIGHT = 2400
        /** La capa de nubes, a la mitad de su tamaño de pintar (2 anchos de pantalla por 0,7 de alto). */
        const val LAYER_WIDTH = 1080
        const val LAYER_HEIGHT = 793

        /** La mayor potencia de 2 que, al dividir la foto, aún la deja cubrir [w] × [h]. */
        fun sampleFor(photoW: Int, photoH: Int, w: Int, h: Int): Int {
            if (w <= 0 || h <= 0) return 1
            var sample = 1
            while (photoW / (sample * 2) >= w && photoH / (sample * 2) >= h) sample *= 2
            return sample
        }

        @DrawableRes
        fun resOf(photo: SkyPhoto): Int = when (photo) {
            SkyPhoto.CLEAR_COOL -> R.drawable.sky_despejado_frio
            SkyPhoto.CLEAR_MILD -> R.drawable.sky_despejado_templado
            SkyPhoto.CLEAR_HOT -> R.drawable.sky_despejado_calor
            SkyPhoto.SCORCHING -> R.drawable.sky_calor_fuerte
            SkyPhoto.CLOUDS -> R.drawable.sky_nubes
            SkyPhoto.FOG -> R.drawable.sky_niebla
            SkyPhoto.RAIN -> R.drawable.sky_lluvia
            SkyPhoto.STORM -> R.drawable.sky_tormenta
            SkyPhoto.SNOW -> R.drawable.sky_nieve
            SkyPhoto.GOLDEN_CLEAR -> R.drawable.sky_dorada_despejado
            SkyPhoto.GOLDEN_CLOUDS -> R.drawable.sky_dorada_nubes
            SkyPhoto.NIGHT_CLEAR -> R.drawable.sky_noche_despejada
            SkyPhoto.NIGHT_CLOUDS -> R.drawable.sky_noche_nubes
            SkyPhoto.NIGHT_RAIN -> R.drawable.sky_noche_lluvia
        }
    }
}
