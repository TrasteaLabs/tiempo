package com.trastealabs.tiempo.ui.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.ui.glass.GlassTone
import com.trastealabs.tiempo.ui.glass.glassTextShadow

/** La tinta del velo de las pantallas de la 010 (`INK` de la maqueta, #0A1028). */
private const val INK_R = 10
private const val INK_G = 16
private const val INK_B = 40

/** Lado del bitmap de la escena: la foto a un octavo (1080 × 2400 → 135 × 300). */
private const val SAMPLE = 8

/** Radio de cada pasada del desenfoque de caja, en píxeles del bitmap (3 pasadas ≈ sigma de 4,5 px ≈ 14 dp). */
private const val BLUR_RADIUS = 4

/**
 * La escena de Ajustes y Sitios (010, Cristal): [photo] desenfocada y velada con [ScreenLook.sceneVeil]; quieta. Se pinta
 * una sola vez en un bitmap pequeño (desenfoque de caja en Kotlin, no `RenderEffect`, que Robolectric no ejecuta).
 * Da su propio [LocalBackdrop] y [LocalGlassTone]: el cristal de dentro vuelve a pintar detrás esta escena, no la
 * foto nítida de la principal.
 */
@Composable
fun SecondaryBackdrop(photo: SkyPhoto, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val look = SCREEN_LOOKS.getValue(photo)
    val image = remember(photo) { SecondaryScenes.get(context, photo, look.sceneVeil).asImageBitmap() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val anchor = remember { Backdrop.Anchor() }
    val backdrop = remember(anchor, image) {
        Backdrop(anchor, size = { size }, layers = { listOf(PhotoLayer(image, 1f)) })
    }
    val tone = remember(photo) { screenTone(look.screenTint) }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .onGloballyPositioned { anchor.coordinates = it }
            .testTag("secondary-backdrop"),
    ) {
        Box(Modifier.matchParentSize().graphicsLayer().drawBehind { backdrop.draw(this) })
        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            LocalGlassTone provides tone,
            LocalTextStyle provides LocalTextStyle.current.copy(shadow = glassTextShadow(tone)),
        ) {
            content()
        }
    }
}

/**
 * El cristal de encima de la escena: `glassBG(t)` de la maqueta (blanco del 30 % al 20 % de `1 − t`, sobre tinta de
 * alfa [tint]), compuesto en dos colores de arriba y de abajo.
 */
internal fun screenTone(tint: Float): GlassTone {
    fun layer(whiteAlpha: Float): Color {
        val w = whiteAlpha * (1 - tint)
        val alpha = w + tint * (1 - w)
        if (alpha <= 0f) return Color.Transparent
        val r = (255f * w + INK_R * tint * (1 - w)) / alpha
        val g = (255f * w + INK_G * tint * (1 - w)) / alpha
        val b = (255f * w + INK_B * tint * (1 - w)) / alpha
        return Color(r / 255f, g / 255f, b / 255f, alpha)
    }
    return GlassTone(layer(0.30f), layer(0.20f), 18.dp, shadow = tint > 0f)
}

/**
 * Las escenas desenfocadas ya hechas, una por foto y velo (012 P4, choque 6 del 011): Ajustes y Sitios las piden cada
 * vez que se abren y, al volver de Sitios, no se vuelven a calcular. Como mucho 14 de ~160 kB.
 */
internal object SecondaryScenes {
    private val cache = HashMap<Pair<SkyPhoto, Float>, Bitmap>()

    /** Cuántas se han calculado de verdad (los tests miran que la segunda vez no sube). */
    @Volatile
    var computed = 0
        private set

    @Synchronized
    fun get(context: Context, photo: SkyPhoto, veil: Float): Bitmap =
        cache.getOrPut(photo to veil) {
            computed++
            blurredScene(context, photo, veil)
        }

    @Synchronized
    fun clear() = cache.clear()
}

/**
 * [photo] a 135 × 300 px, desenfocada con una caja de [BLUR_RADIUS] px en 3 pasadas y velada con la tinta a [veil].
 */
internal fun blurredScene(context: Context, photo: SkyPhoto, veil: Float): Bitmap {
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inSampleSize = SAMPLE
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val source = BitmapFactory.decodeResource(context.applicationContext.resources, PhotoCache.resOf(photo), options)
        ?: error("No se pudo leer la foto ${photo.file}")
    val w = source.width
    val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
    source.recycle()
    val channels = Array(3) { c -> IntArray(w * h) { i -> (pixels[i] shr (16 - 8 * c)) and 0xFF } }
    val temp = IntArray(w * h)
    for (channel in channels) repeat(3) {
        boxBlur(channel, temp, w, h, BLUR_RADIUS, horizontal = true)
        boxBlur(temp, channel, w, h, BLUR_RADIUS, horizontal = false)
    }
    val ink = intArrayOf(INK_R, INK_G, INK_B)
    val out = IntArray(w * h) { i ->
        var argb = 0xFF shl 24
        for (c in 0..2) {
            val v = (channels[c][i] * (1 - veil) + ink[c] * veil + 0.5f).toInt().coerceIn(0, 255)
            argb = argb or (v shl (16 - 8 * c))
        }
        argb
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

/** Una pasada de caja de ancho 2·[r]+1 con el borde repetido, de [src] a [dst]. */
private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val length = if (horizontal) w else h
    val step = if (horizontal) 1 else w
    val window = 2 * r + 1
    for (line in 0 until lines) {
        val base = if (horizontal) line * w else line
        var sum = 0
        for (k in -r..r) sum += src[base + k.coerceIn(0, length - 1) * step]
        for (i in 0 until length) {
            dst[base + i * step] = (sum + window / 2) / window
            sum += src[base + (i + r + 1).coerceAtMost(length - 1) * step] - src[base + (i - r).coerceAtLeast(0) * step]
        }
    }
}
