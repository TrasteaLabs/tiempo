package com.trastealabs.tiempo.ui.scene

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

/**
 * La luna de foto (007, H12) que se ve: [phase] (0–1) y [south] (girada 180°). [lit] es la luna ya calculada
 * ([MoonCache]); `null` mientras se calcula, y entonces se pinta la forma de la fase lisa.
 */
data class MoonLook(val phase: Double, val south: Boolean, val lit: Bitmap?)

/** El mapa de la luna, ya decodificado (ARGB). */
internal class MoonMap(val width: Int, val height: Int, val argb: IntArray)

/**
 * Las lunas iluminadas, en memoria y nunca en disco (C8). El mapa es `assets/moon_map.jpg`: «CGI Moon Kit» de la NASA
 * (Scientific Visualization Studio, svs.gsfc.nasa.gov/4720, `lroc_color_poles_1k.jpg`, con datos del LRO), de dominio
 * público. Se decodifica una vez por proceso, se calcula la cara visible y se suelta. Guarda 4 lunas (1 MB cada una),
 * por fase redondeada a 0,001 y hemisferio. La app las pide en un hilo de fondo ([lit]); el widget, al momento ([litNow]).
 */
object MoonCache {
    private const val ASSET = "moon_map.jpg"
    private const val KEEP = 4
    private const val TAG = "MoonBench"

    private data class Key(val milli: Long, val south: Boolean)

    private var face: MoonFace? = null
    private val lits = object : LinkedHashMap<Key, Bitmap>(KEEP, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bitmap>?) = size > KEEP
    }

    private fun key(phase: Double, south: Boolean) = Key((phase.mod(1.0) * 1000).roundToLong() % 1000, south)

    /** En un hilo de fondo (app). */
    suspend fun lit(context: Context, phase: Double, south: Boolean): Bitmap =
        peek(phase, south) ?: withContext(Dispatchers.Default) { litNow(context, phase, south) }

    /** En el hilo que llama (widget, que ya pinta fuera del hilo principal). */
    fun litNow(context: Context, phase: Double, south: Boolean): Bitmap {
        peek(phase, south)?.let { return it }
        val start = SystemClock.elapsedRealtimeNanos()
        val key = key(phase, south)
        val f = face(context)
        // Repartida entre los núcleos: en la versión de verdad, unos 60 ms la primera (mapa y cara incluidos).
        val pixels = litMoon(f, key.milli / 1000.0, south, ParallelRows)
        val bitmap = Bitmap.createBitmap(pixels, f.size, f.size, Bitmap.Config.ARGB_8888)
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.i(TAG, "luna ${key.milli / 1000.0} sur=$south: ${(SystemClock.elapsedRealtimeNanos() - start) / 1_000_000} ms")
        }
        return synchronized(lits) { lits.getOrPut(key) { bitmap } }
    }

    fun peek(phase: Double, south: Boolean): Bitmap? = synchronized(lits) { lits[key(phase, south)] }

    /** Cuántas lunas hay guardadas (tests). */
    internal val count: Int get() = synchronized(lits) { lits.size }

    /** Vacía la caché (tests). */
    internal fun clear() = synchronized(lits) {
        lits.clear()
        face = null
    }

    @Synchronized
    private fun face(context: Context): MoonFace = face ?: run {
        val start = SystemClock.elapsedRealtimeNanos()
        val map = loadMap(context)
        val decoded = SystemClock.elapsedRealtimeNanos()
        moonFace(map.argb, map.width, map.height, rows = ParallelRows).also {
            face = it
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                val end = SystemClock.elapsedRealtimeNanos()
                Log.i(TAG, "mapa: ${(decoded - start) / 1_000_000} ms, cara: ${(end - decoded) / 1_000_000} ms")
            }
        }
    }

    /** Decodifica el mapa del asset (sin escalar por densidad). */
    internal fun loadMap(context: Context): MoonMap {
        val options = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = context.assets.open(ASSET).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("No se pudo leer $ASSET")
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return MoonMap(bitmap.width, bitmap.height, pixels).also { bitmap.recycle() }
    }
}

/**
 * La luna de la escena (app): `null` si la escena no tiene luna ([needed] = false); si la tiene, la fase y el hemisferio
 * al momento, y la foto en cuanto [MoonCache] la tiene (las primeras décimas, la forma de la fase lisa).
 */
@Composable
fun rememberMoonLook(phase: Double, south: Boolean, needed: Boolean): MoonLook? {
    if (!needed) return null
    val context = LocalContext.current.applicationContext
    var lit by remember(phase, south) { mutableStateOf(MoonCache.peek(phase, south)) }
    LaunchedEffect(phase, south) {
        if (lit == null) lit = MoonCache.lit(context, phase, south)
    }
    return remember(phase, south, lit) { MoonLook(phase, south, lit) }
}
