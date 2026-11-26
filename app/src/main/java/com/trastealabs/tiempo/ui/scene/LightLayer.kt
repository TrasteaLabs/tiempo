package com.trastealabs.tiempo.ui.scene

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.trastealabs.tiempo.domain.Flare
import com.trastealabs.tiempo.domain.MoonShow
import com.trastealabs.tiempo.domain.SkyEffects
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * La capa de luz de la principal (009, P2; quieta desde el 012 P6): lo que va encima de la foto, copiado de `drawFx` de
 * la maqueta (variante B, «Profundidad»). Las texturas (lluvia, cortinas, bruma, el destello, el copo) se pintan una vez
 * al crear la capa y se dibujan en un solo instante fijo ([STILL_SECONDS]): nada avanza con los fotogramas (D24). Al
 * cambiar de escena, una capa funde con otra con [draw]`(appear)`.
 *
 * Las medidas de la maqueta van en dp (su marco es de 420 × 880 dp); aquí se pasan a px con la densidad.
 */

private const val TAU = 2 * PI

/** El instante fijo de la escena (siempre quieta): el `STILL_T` de la maqueta. */
const val STILL_SECONDS = 7.3

/** Resolución de las texturas finas (la lluvia), en px por dp: más no se nota y ocupa memoria. */
private const val RAIN_TEX = 1.5f

/** Resolución de las texturas blandas (cortinas, destello), en px por dp: son borrosas y se estiran. */
private const val SOFT_TEX = 0.5f

/** Paralaje al desplazar la principal: lo que sube cada cosa por cada px de desplazamiento (maqueta, variante B). */
private const val STARS_SCROLL = 0.05f
private const val FLARE_SCROLL = 0.5f

/** Los colores de la capa de nubes de delante (`E.veil` de la maqueta), según el cielo y el momento. */
private enum class Veil(val r: Int, val g: Int, val b: Int) {
    DAY_CLOUDS(246, 248, 252), GOLDEN_CLOUDS(255, 188, 150), RAIN(170, 180, 196), STORM(110, 106, 140),
    NIGHT_RAIN(74, 82, 110), NIGHT_CLOUDS(104, 114, 150),
}

/** `E.veil` de la maqueta a partir de los efectos (la maqueta lo saca del cielo y el momento, que dan lo mismo). */
private fun veilOf(e: SkyEffects): Veil? = when {
    e.moon == MoonShow.RAIN -> Veil.NIGHT_RAIN
    e.moon == MoonShow.VEILED -> Veil.NIGHT_CLOUDS
    e.moon != MoonShow.NONE -> null
    e.rain > 0f -> if (e.stormFlash) Veil.STORM else Veil.RAIN
    e.cloudPan && !e.fog && !e.snow -> if (e.flare == Flare.WARM_GLOW) Veil.GOLDEN_CLOUDS else Veil.DAY_CLOUDS
    else -> null
}

private class LightStar(val x: Float, val y: Float, val r: Float, val lo: Float, val period: Float, val phase: Float)
private class LightFlake(val x: Float, val y: Float, val r: Float, val speed: Float, val sway: Float, val period: Float, val phase: Float)

/** Las 70 estrellas de la maqueta (`rng(77)`), en fracciones de la pantalla y dp. */
private val STARS: List<LightStar> = Mulberry32(77).let { r ->
    List(70) { LightStar(r.next(), r.next() * 0.55f, 0.5f + r.next() * r.next() * 1.4f, 0.3f + r.next() * 0.3f, 2 + r.next() * 3, r.next() * TAU.toFloat()) }
}

/** Los 26 copos de la maqueta (`rng(91)`): pocos, grandes y lentos. */
private val FLAKES: List<LightFlake> = Mulberry32(91).let { r ->
    List(26) { LightFlake(r.next(), r.next(), 2.2f + r.next() * 2.8f, 22 + r.next() * 18, 6 + r.next() * 8, 4 + r.next() * 3, r.next() * TAU.toFloat()) }
}

/** Una capa de nubes de delante: escala, velocidad (dp/s), opacidad, oscurecida, cuánto sube al desplazar. */
private class VeilPass(val k: Float, val speed: Float, val alpha: Float, val dark: Float, val parallax: Float)

/**
 * Lo que va encima de la foto (H4, H6): destello, nubes de delante, cortinas y lluvia, bancos de niebla, copos y
 * estrellas, todo quieto en [STILL_SECONDS]. La luna de la NASA la pinta [SceneBackdrop] entre [drawBack] (estrellas) y
 * [drawFront] (lo demás), para que las nubes le pasen por delante. [clouds] es la capa de nubes de la foto
 * (`sky_cloud_layer`); sin ella no hay nubes de delante.
 */
class LightLayer(
    private val effects: SkyEffects,
    private val widthPx: Float,
    private val heightPx: Float,
    private val density: Float,
    private val clouds: ImageBitmap? = null,
) {
    private val wDp = widthPx / density
    private val hDp = heightPx / density

    /**
     * px por dp, con otro nombre: dentro de las funciones de pintar, `density` sería la del lienzo, y la del lienzo que
     * graba el cristal entra en bucle al leerla (StackOverflowError en el OPPO).
     */
    private val px = density
    private val veil = veilOf(effects)?.takeIf { clouds != null }
    private val veilColors = veil?.let { v ->
        listOf(1f, 0.7f).associateWith { k -> Color((v.r * k).roundToInt(), (v.g * k).roundToInt(), (v.b * k).roundToInt()) }
    }
    private val veilPasses: List<VeilPass> = when {
        veil == null -> emptyList()
        effects.rain > 0f -> {
            val sp = if (effects.rain > 1f) 24f else 15f
            listOf(VeilPass(1.35f, sp * 0.45f, 0.55f, 0.7f, -0.12f), VeilPass(1f, sp, 0.72f, 1f, -0.2f))
        }
        else -> listOf(VeilPass(1f, 10f, if (effects.moon != MoonShow.NONE) 0.75f else 0.7f, 1f, -0.2f))
    }

    // Las texturas, pintadas una vez.
    private val rainTiles: List<ImageBitmap> = if (effects.rain > 0f) {
        listOf(rainTile(5, 150, 240, 520, 36f..80f, 0.55f..0.75f, 0.22f..0.4f), rainTile(9, 70, 320, 640, 70f..140f, 0.75f..1f, 0.25f..0.45f))
    } else {
        emptyList()
    }
    private val rainBrushes = rainTiles.map { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) }
    private val haze: ImageBitmap? = if (effects.rain > 0f) hazeTexture() else null
    private val fogBanks: List<ImageBitmap> = if (effects.fog) List(3) { fogBank(it) } else emptyList()
    private val flareKind = effects.flare
    private val glowOnly = flareKind == Flare.GLOW || flareKind == Flare.WARM_GLOW || flareKind == Flare.FOG
    private val warm = flareKind == Flare.WARM || flareKind == Flare.WARM_GLOW
    private val flareSprite: ImageBitmap? = if (flareKind != Flare.NONE) flareTexture() else null
    private val flakeSprite: ImageBitmap? = if (effects.snow) flakeTexture() else null

    /** Todo, en orden: estrellas y lo de delante. [appear] (0–1) es cuánto se ve; [enter] (0–1), cuánto ha entrado el destello. */
    fun draw(scope: DrawScope, scrollPx: Float, appear: Float = 1f, enter: Float = 1f) {
        drawBack(scope, scrollPx, appear)
        drawFront(scope, scrollPx, appear, enter)
    }

    /** Lo de detrás de la luna: las estrellas, fijas. */
    fun drawBack(scope: DrawScope, scrollPx: Float, appear: Float = 1f) = with(scope) {
        drawProbe?.invoke(this@LightLayer)
        if (effects.stars == 0) return@with
        val alpha = (STAR_ALPHA * appear).coerceIn(0f, 1f)
        if (alpha <= 0.002f) return@with
        for (s in STARS.subList(0, min(effects.stars, STARS.size))) {
            val c = Offset(s.x * widthPx, s.y * heightPx - scrollPx * STARS_SCROLL)
            // Las grandes, con un poco de halo (`sigma` 0,8 de la maqueta).
            if (s.r > 1.2f) drawCircle(Color.White, radius = (s.r + 1.2f) * px, center = c, alpha = alpha * 0.25f)
            drawCircle(Color.White, radius = s.r * px, center = c, alpha = alpha)
        }
    }

    /** Lo de delante de la luna. Con [glass], sin las rayas de lluvia: difuminadas no se ven y cuestan. */
    fun drawFront(scope: DrawScope, scrollPx: Float, appear: Float = 1f, enter: Float = 1f, glass: Boolean = false) = with(scope) {
        if (appear <= 0.002f) return@with
        drawVeil(appear, scrollPx)
        drawFog(appear)
        drawFlare(appear * enter, enter, scrollPx)
        drawSnow(appear)
        if (!glass) drawRain(appear)
    }

    private fun DrawScope.drawVeil(fade: Float, scrollPx: Float) {
        val image = clouds ?: return
        val colors = veilColors ?: return
        val bw = widthPx * 2
        val bh = image.height * bw / image.width
        for (p in veilPasses) {
            val vs = p.k
            val off = 0f
            val y = scrollPx * p.parallax
            val filter = ColorFilter.tint(colors.getValue(p.dark), BlendMode.Modulate)
            withTransform({
                translate(widthPx / 2, y + bh / 2 * p.k - bh * (p.k - 1) * 0.35f)
                scale(vs, vs, Offset.Zero)
                translate(-widthPx / 2, -bh / 2)
            }) {
                for (x in listOf(off - bw, off)) {
                    drawImage(
                        image, IntOffset.Zero, IntSize(image.width, image.height),
                        IntOffset(x.roundToInt(), 0), IntSize(bw.roundToInt(), bh.roundToInt()),
                        alpha = fade * p.alpha, colorFilter = filter, filterQuality = FilterQuality.Low,
                    )
                }
            }
        }
    }

    private fun DrawScope.drawFog(fade: Float) {
        val bw = widthPx * 2
        fogBanks.forEachIndexed { i, bank ->
            val bh = (200 + i * 30) * px
            val dir = if (i % 2 == 1) -1 else 1
            val off = if (dir > 0) 0f else bw / 2
            val y = heightPx * (0.3f + i * 0.22f) - bh / 2
            for (x in listOf(off - bw, off)) {
                drawImage(
                    bank, IntOffset.Zero, IntSize(bank.width, bank.height),
                    IntOffset(x.roundToInt(), y.roundToInt()), IntSize(bw.roundToInt(), bh.roundToInt()),
                    alpha = fade * 0.55f, filterQuality = FilterQuality.Low,
                )
            }
        }
    }

    private fun DrawScope.drawFlare(k: Float, enter: Float, scrollPx: Float) {
        val sprite = flareSprite ?: return
        if (k <= 0.002f) return
        // El sol de la variante B no sigue la hora: arriba a la derecha, y entra desde un poco más arriba.
        val sx = widthPx * 0.76f
        val sy = heightPx * 0.005f - scrollPx * FLARE_SCROLL - (1 - enter) * heightPx * 0.18f
        val size = sprite.width * px / SOFT_TEX
        withTransform({
            translate(sx, sy)
        }) {
            drawImage(
                sprite, IntOffset.Zero, IntSize(sprite.width, sprite.height),
                IntOffset((-size / 2).roundToInt(), (-size / 2).roundToInt()), IntSize(size.roundToInt(), size.roundToInt()),
                alpha = k, blendMode = BlendMode.Screen, filterQuality = FilterQuality.Low,
            )
        }
        if (glowOnly) return
        // Reflejos de la lente: en la recta del sol al centro de la imagen, y más allá.
        val vx = widthPx * 0.5f - sx
        val vy = heightPx * 0.42f - sy - scrollPx * 0.9f
        for (g in GHOSTS) {
            val c = Offset(sx + vx * g.at, sy + vy * g.at)
            val r = g.radius * px
            drawCircle(
                Brush.radialGradient(
                    0f to g.color.copy(alpha = g.alpha * 0.5f * k), 0.78f to g.color.copy(alpha = g.alpha * k),
                    1f to g.color.copy(alpha = 0f), center = c, radius = r,
                ),
                radius = r, center = c, blendMode = BlendMode.Screen,
            )
        }
    }

    private fun DrawScope.drawSnow(fade: Float) {
        val t = STILL_SECONDS
        val sprite = flakeSprite ?: return
        for (f in FLAKES) {
            val y = (mod(f.y * hDp + t * f.speed, hDp + 20.0) - 10).toFloat() * px
            val x = (f.x * wDp + f.sway * sin(TAU * t / f.period + f.phase).toFloat()) * px
            // El sprite es un copo de radio [FLAKE_R] px con su halo; se escala al radio del copo.
            val s = f.r * px / FLAKE_R
            val side = sprite.width * s
            drawImage(
                sprite, IntOffset.Zero, IntSize(sprite.width, sprite.height),
                IntOffset((x - side / 2).roundToInt(), (y - side / 2).roundToInt()), IntSize(side.roundToInt(), side.roundToInt()),
                alpha = 0.75f * fade, filterQuality = FilterQuality.Low,
            )
        }
    }

    private fun DrawScope.drawRain(fade: Float) {
        val t = STILL_SECONDS
        val hazeImage = haze ?: return
        if (fade <= 0.002f) return
        val speed = effects.rain
        val storm = speed > 1f
        val cloudV = if (storm) 24f else 15f
        val bw = widthPx * 2
        val whole = Rect(0f, 0f, widthPx, heightPx)
        val canvas = drawContext.canvas
        canvas.saveLayer(whole, androidx.compose.ui.graphics.Paint().apply { alpha = fade })
        // Cortinas a lo lejos: avanzan con las nubes.
        val hOff = 0f
        val hh = hazeImage.height * bw / hazeImage.width
        for (x in listOf(hOff - bw, hOff)) {
            drawImage(
                hazeImage, IntOffset.Zero, IntSize(hazeImage.width, hazeImage.height),
                IntOffset(x.roundToInt(), (heightPx * 0.12f).roundToInt()), IntSize(bw.roundToInt(), hh.roundToInt()),
                alpha = if (storm) 1f else 0.8f, filterQuality = FilterQuality.Low,
            )
        }
        // Las dos capas de rayas: cada una se desplaza en diagonal a su velocidad.
        listOf(300f to 0.38f, 640f to 0.5f).forEachIndexed { i, (v, a) ->
            val tile = rainTiles[i]
            val s = px / RAIN_TEX
            val oy = mod(t * v * speed * px, (tile.height * s).toDouble()).toFloat()
            val ox = mod(-t * v * speed * 0.14 * px, (tile.width * s).toDouble()).toFloat()
            withTransform({
                translate(ox, oy)
                scale(s, s, Offset.Zero)
            }) {
                drawRect(
                    rainBrushes[i], topLeft = Offset(-tile.width.toFloat(), -tile.height.toFloat()),
                    size = Size(widthPx / s + tile.width * 2, heightPx / s + tile.height * 2),
                    alpha = (a * if (storm) 1.25f else 1f).coerceAtMost(1f),
                )
            }
        }
        // La lluvia nace por debajo de las nubes y va en cortinas que avanzan con ellas (la máscara de la maqueta).
        val cOff = 0f
        drawRect(
            Brush.horizontalGradient(*CURTAIN, startX = cOff, endX = cOff + bw, tileMode = TileMode.Repeated),
            blendMode = BlendMode.DstIn,
        )
        drawRect(Brush.verticalGradient(*FALL, startY = 0f, endY = heightPx), blendMode = BlendMode.DstIn)
        canvas.restore()
    }

    /** Un mosaico de rayas de lluvia (`rainTile` de la maqueta) de [w] × [h] dp, que se repite sin costuras. */
    private fun rainTile(
        tileSeed: Int, n: Int, w: Int, h: Int,
        lens: ClosedFloatingPointRange<Float>, widths: ClosedFloatingPointRange<Float>, alphas: ClosedFloatingPointRange<Float>,
    ): ImageBitmap {
        val bitmap = Bitmap.createBitmap((w * RAIN_TEX).roundToInt(), (h * RAIN_TEX).roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(RAIN_TEX, RAIN_TEX)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
        val r = Mulberry32(tileSeed)
        repeat(n) {
            val x = r.next() * w
            val y = r.next() * h
            val l = r.between(lens)
            val dx = -l * 0.14f
            val a = r.between(alphas)
            paint.color = android.graphics.Color.argb((a * 255).roundToInt(), 235, 242, 255)
            paint.strokeWidth = r.between(widths)
            for (oy in listOf(0, -h, h)) for (ox in listOf(0, -w, w)) {
                canvas.drawLine(x + ox, y + oy, x + ox + dx, y + oy + l, paint)
            }
        }
        return bitmap.also { it.prepareToDraw() }.asImageBitmap()
    }

    /** Las cortinas que cuelgan de las nubes: 9 columnas suaves e inclinadas, ya difuminadas (`HAZE` de la maqueta). */
    private fun hazeTexture(): ImageBitmap {
        val w = 2 * wDp
        val h = 0.8f * hDp
        val bitmap = Bitmap.createBitmap(max(1, (w * SOFT_TEX).roundToInt()), max(1, (h * SOFT_TEX).roundToInt()), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(SOFT_TEX, SOFT_TEX)
        val r = Mulberry32(44)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(16f * SOFT_TEX, BlurMaskFilter.Blur.NORMAL) }
        repeat(9) {
            val x = r.next() * w
            val cw = 30 + r.next() * 70
            val a1 = 0.10f + r.next() * 0.08f
            val a2 = 0.05f + r.next() * 0.05f
            fun c(a: Float) = android.graphics.Color.argb((a * 255).roundToInt(), 225, 232, 245)
            paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(c(0f), c(a1), c(a2), c(0f)), floatArrayOf(0f, 0.25f, 0.8f, 1f), Shader.TileMode.CLAMP)
            for (ox in listOf(0f, -w, w)) {
                val path = Path().apply {
                    moveTo(x + ox, 0f); lineTo(x + cw + ox, 0f); lineTo(x + cw - 90 + ox, h); lineTo(x - 90 + ox, h); close()
                }
                canvas.drawPath(path, paint)
            }
        }
        return bitmap.also { it.prepareToDraw() }.asImageBitmap()
    }

    /** Un banco de niebla (`FOG[i]` de la maqueta): bruma blanca con el ruido de las nubes, que se repite de lado. */
    private fun fogBank(i: Int): ImageBitmap {
        val layer = CloudParams(
            c = intArrayOf(244, 244, 248), sh = intArrayOf(200, 200, 212), a = 0.75f, scale = 180f + i * 40, stretch = 3.4f,
            lo = 0.42f, hi = 0.7f, y0 = 0.12f, y1 = 0.95f, seed = 301 + i, res = 0.4f, warp = 0.5f, light = 1f,
        )
        return cloudLayer(layer, 2 * wDp, 200f + i * 30)
    }

    /** El resplandor y los haces del sol (`flareSprite` de la maqueta), a media resolución: es todo borroso. */
    private fun flareTexture(): ImageBitmap {
        val radius = wDp * when {
            flareKind == Flare.FOG -> 1.1f
            glowOnly -> 0.95f
            else -> 0.9f
        }
        val side = max(2, ceil(radius * 2 * SOFT_TEX).toInt())
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(SOFT_TEX, SOFT_TEX)
        val c = radius
        val a = when {
            flareKind == Flare.FOG -> 0.55f
            glowOnly -> 0.5f
            else -> 1f
        }
        val c0 = if (warm) intArrayOf(255, 214, 160) else intArrayOf(255, 248, 232)
        val c1 = if (warm) intArrayOf(255, 170, 110) else intArrayOf(255, 232, 196)
        fun rgba(col: IntArray, alpha: Float) = android.graphics.Color.argb((alpha.coerceIn(0f, 1f) * 255).roundToInt(), col[0], col[1], col[2])
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            c, c, radius,
            intArrayOf(rgba(c0, a), rgba(c0, a * 0.95f), rgba(c0, a * 0.7f), rgba(c1, a * 0.42f), rgba(c1, a * 0.14f), rgba(c1, a * 0.04f), rgba(c1, 0f)),
            floatArrayOf(0f, 0.07f, 0.13f, 0.28f, 0.55f, 0.8f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, radius * 2, radius * 2, paint)
        if (!glowOnly) {
            paint.maskFilter = BlurMaskFilter(14f * SOFT_TEX, BlurMaskFilter.Blur.NORMAL)
            for (i in 0 until 5) {
                val ang = PI * (0.56 + i * 0.1) + if (i % 2 == 1) 0.03 else 0.0
                val l = radius * (0.8f + (i % 3) * 0.12f)
                val wd = 0.05 + (i % 2) * 0.03
                paint.shader = LinearGradient(
                    c, c, c + cos(ang).toFloat() * l, c + sin(ang).toFloat() * l,
                    intArrayOf(rgba(c0, 0.3f), rgba(c0, 0.09f), rgba(c0, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
                )
                val path = Path().apply {
                    moveTo(c, c)
                    lineTo(c + cos(ang - wd).toFloat() * l, c + sin(ang - wd).toFloat() * l)
                    lineTo(c + cos(ang + wd).toFloat() * l, c + sin(ang + wd).toFloat() * l)
                    close()
                }
                canvas.drawPath(path, paint)
            }
        }
        return bitmap.also { it.prepareToDraw() }.asImageBitmap()
    }

    /** Un copo: disco blanco con el borde difuminado (`softDisc` con `sigma` = 0,45 · r), de radio [FLAKE_R] px. */
    private fun flakeTexture(): ImageBitmap {
        val sigma = FLAKE_R * 0.45f
        val outer = FLAKE_R + 3 * sigma
        val side = ceil(outer * 2).toInt()
        val pixels = IntArray(side * side)
        for (y in 0 until side) for (x in 0 until side) {
            val rho = sqrt((x + 0.5f - side / 2f).pow(2) + (y + 0.5f - side / 2f).pow(2))
            val a = phi(((FLAKE_R - rho) / sigma).toDouble())
            pixels[y * side + x] = android.graphics.Color.argb((a * 255).roundToInt().coerceIn(0, 255), 255, 255, 255)
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).also { it.prepareToDraw() }.asImageBitmap()
    }

    private class Ghost(val at: Float, val radius: Float, val color: Color, val alpha: Float)

    companion object {
        private const val FLAKE_R = 16f
        /** La opacidad de las estrellas (el instante quieto de la maqueta). */
        private const val STAR_ALPHA = 0.8f
        private val GHOSTS = listOf(
            Ghost(0.42f, 9f, Color(255, 220, 150), 0.16f), Ghost(0.62f, 26f, Color(140, 220, 210), 0.07f),
            Ghost(0.9f, 14f, Color(255, 200, 140), 0.12f), Ghost(1.28f, 56f, Color(170, 160, 255), 0.05f),
        )

        /** Las franjas más y menos densas de las cortinas de lluvia, que barren de lado (`CURTAIN`). */
        private val CURTAIN = arrayOf(0f to 0.35f, 0.12f to 1f, 0.27f to 0.45f, 0.4f to 0.9f, 0.5f to 0.35f, 0.62f to 1f, 0.77f to 0.45f, 0.9f to 0.9f, 1f to 0.35f)
            .map { (o, a) -> o to Color.Black.copy(alpha = a) }.toTypedArray()

        /** La lluvia nace bajo las nubes: transparente arriba, entera desde el 32 % de la pantalla. */
        private val FALL = arrayOf(0f to Color.Transparent, 0.1f to Color.Black.copy(alpha = 0.05f), 0.32f to Color.Black, 1f to Color.Black.copy(alpha = 0.85f))

        /** ¿Lleva nubes de delante (la capa `sky_cloud_layer`)? */
        fun needsClouds(effects: SkyEffects): Boolean = veilOf(effects) != null

        /** Para los tests: cada capa que se pinta. */
        @VisibleForTesting
        internal var drawProbe: ((LightLayer) -> Unit)? = null
    }
}

private fun mod(a: Double, n: Double): Double = ((a % n) + n) % n

/** La función de distribución de la normal (para el borde de los copos, como `phi` de la maqueta). */
private fun phi(z: Double): Double {
    val t = 1 / (1 + 0.2316419 * abs(z))
    val poly = t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
    val tail = exp(-z * z / 2) / sqrt(2 * PI) * poly
    return if (z >= 0) 1 - tail else tail
}

/* ---------- el ruido de las nubes (`hash2`, `vnoise`, `fbm` y `cloudLayer` de la maqueta) ---------- */

private class CloudParams(
    val c: IntArray, val sh: IntArray, val a: Float, val scale: Float, val stretch: Float, val lo: Float, val hi: Float,
    val y0: Float, val y1: Float, val seed: Int, val res: Float, val warp: Float, val light: Float,
)

private fun hash2(ix: Int, iy: Int, seed: Int): Double {
    var h = ix * 374761393 + iy * 668265263 + seed * 982451653
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h.toLong() and 0xFFFFFFFFL) / 4294967296.0
}

private fun vnoise(x: Double, y: Double, seed: Int, period: Double): Double {
    val ix = floor(x)
    val iy = floor(y).toInt()
    val fx = x - ix
    val fy = y - iy
    val u = fx * fx * (3 - 2 * fx)
    val v = fy * fy * (3 - 2 * fy)
    val i0 = (if (period > 0) mod(ix, period) else ix).toInt()
    val i1 = (if (period > 0) mod(ix + 1, period) else ix + 1).toInt()
    val a = hash2(i0, iy, seed)
    val b = hash2(i1, iy, seed)
    val c = hash2(i0, iy + 1, seed)
    val d = hash2(i1, iy + 1, seed)
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
}

private fun fbm(x: Double, y: Double, seed: Int, octaves: Int, period: Double): Double {
    var s = 0.0
    var a = 0.5
    var f = 1.0
    var n = 0.0
    repeat(octaves) { i ->
        s += a * vnoise(x * f, y * f, seed + i * 17, if (period > 0) period * f else 0.0)
        n += a
        a *= 0.5
        f *= 2
    }
    return s / n
}

private fun smooth(x: Double): Double = x.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
private fun sstep(a: Double, b: Double, x: Double) = smooth((x - a) / (b - a))

/** Una capa de nubes de [w] × [h] dp, repetible de lado (`cloudLayer(L, W, H, true)` de la maqueta). */
private fun cloudLayer(l: CloudParams, w: Float, h: Float): ImageBitmap {
    val pw = max(1, ceil(w * l.res).toInt())
    val ph = max(1, ceil(h * l.res).toInt())
    val pixels = IntArray(pw * ph)
    val sc = l.scale * l.res
    val sy = 1.0 / sc
    val sx = 1.0 / (sc * l.stretch)
    val period = max(1.0, (pw * sx).roundToInt().toDouble())
    for (y in 0 until ph) {
        val yy = y.toDouble() / ph
        val pres = (if (l.y0 <= 0) 1.0 else sstep(l.y0 - 0.1, l.y0 + 0.08, yy)) * (1 - sstep(l.y1 - 0.15, l.y1 + 0.1, yy))
        if (pres <= 0.002) continue
        for (x in 0 until pw) {
            val bx = x * sx
            val by = y * sy
            val wx = fbm(bx * 0.5 + 3.1, by * 0.5, l.seed + 5, 3, period / 2) - 0.5
            val wy = fbm(bx * 0.5, by * 0.5 + 7.7, l.seed + 9, 3, period / 2) - 0.5
            val n = fbm(bx + l.warp * wx, by + l.warp * wy, l.seed, 5, period)
            val a = sstep(l.lo.toDouble(), l.hi.toDouble(), n) * pres
            if (a <= 0.003) continue
            val n2 = fbm(bx + l.warp * wx, by + l.warp * wy - 0.18, l.seed, 5, period)
            var lit = (0.55 + (n - n2) * l.light).coerceIn(0.0, 1.0)
            lit *= 1 - 0.3 * sstep(l.hi.toDouble(), l.hi + 0.18, n)
            val r = (l.sh[0] + (l.c[0] - l.sh[0]) * lit).roundToInt()
            val g = (l.sh[1] + (l.c[1] - l.sh[1]) * lit).roundToInt()
            val b = (l.sh[2] + (l.c[2] - l.sh[2]) * lit).roundToInt()
            pixels[y * pw + x] = android.graphics.Color.argb((255 * a * l.a).roundToInt().coerceIn(0, 255), r, g, b)
        }
    }
    return Bitmap.createBitmap(pixels, pw, ph, Bitmap.Config.ARGB_8888).also { it.prepareToDraw() }.asImageBitmap()
}
