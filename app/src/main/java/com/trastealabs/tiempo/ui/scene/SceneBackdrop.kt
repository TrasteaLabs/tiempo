package com.trastealabs.tiempo.ui.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.trastealabs.tiempo.domain.MoonShow
import com.trastealabs.tiempo.domain.SkyEffects
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.ui.IntroState
import com.trastealabs.tiempo.ui.glass.GlassTone
import com.trastealabs.tiempo.ui.glass.forPhoto
import com.trastealabs.tiempo.ui.glass.glassTextShadow
import com.trastealabs.tiempo.ui.introAstro
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Fundido al cambiar de foto (llega el atardecer, empieza a llover): 1,2 s; sin animaciones, 150 ms (maqueta). */
const val PHOTO_FADE_MS = 1200
const val PHOTO_FADE_STILL_MS = 150

/** La luna de la maqueta D: pequeña, en la esquina (x al 82 % del ancho, centro a 92 dp, 70 dp de diámetro). */
private const val MOON_X = 0.82f
private const val MOON_Y_DP = 92f
private const val MOON_D_DP = 70f

/** Tapada por nubes o lluvia, la luna es algo más pequeña (`md * .82` de la maqueta). */
private const val MOON_SMALLER = 0.82f

/** Lo que sube el astro al abrir (dp): lo mismo que [com.trastealabs.tiempo.ui.introAstro]. */
private const val ASTRO_RISE_DP = 36f

/** Con el desplazamiento, la luna sube a la mitad de velocidad que el contenido (maqueta). */
private const val MOON_SCROLL = 0.5f

/** Paralaje de la foto en la principal (P2, variante B): sube un 8 % de lo que se desplaza el contenido. */
private const val PHOTO_SCROLL = 0.08f

/** Para el paralaje, la foto de la principal sobra un 6 % por abajo (`OVY` de la maqueta): nunca asoma su borde. */
private const val PHOTO_OVERSCAN = 1.06f

/** Una foto que se ve y su opacidad (la de debajo del fundido, entera). */
internal class PhotoLayer(val image: ImageBitmap, val alpha: Float)

/** Una escena que se ve: su capa de luz (la principal; `null` en los días), qué luna lleva y cuánto se ve (0–1). */
internal class ScenePass(val light: LightLayer?, val moon: MoonShow, val alpha: Float)

/** Lo que identifica una escena: la foto y lo que lleva encima. Si cambia alguno, la escena nueva funde sobre la vieja. */
private data class Shown(val photo: SkyPhoto, val effects: SkyEffects)

/**
 * Lo que hay detrás del cristal: la foto del cielo (009) y la luna. La barra de cristal de Ajustes y Sitios la vuelve a
 * pintar, recortada y difuminada ([com.trastealabs.tiempo.ui.glass.glass]). Quieta (012 P6): se pinta cuando cambia algo.
 */
class Backdrop internal constructor(
    private val anchor: Anchor,
    private val size: () -> IntSize,
    private val layers: () -> List<PhotoLayer>,
    /** Pinta la luna de la escena con su [MoonShow] y su opacidad (0–1). */
    private val moon: DrawScope.(MoonShow, Float) -> Unit = { _, _ -> },
    /** Las escenas que se ven: una, o dos durante el fundido entre datos. */
    private val scenes: () -> List<ScenePass> = { emptyList() },
    /** Desplazamiento de la principal, para el paralaje; `null` = la foto quieta, como la de los días. */
    private val scroll: (() -> Int)? = null,
    /** Lo que le falta al astro por subir al abrir (de 1 a 0); 0 = ya en su sitio. */
    private val astroRise: () -> Float = { 0f },
) {
    internal class Anchor {
        var coordinates: LayoutCoordinates? = null
    }

    /** Dónde cae [coordinates] respecto a la escena; `null` si alguno aún no tiene sitio. */
    fun offsetOf(coordinates: LayoutCoordinates?): Offset? {
        val self = anchor.coordinates?.takeIf { it.isAttached } ?: return null
        val other = coordinates?.takeIf { it.isAttached } ?: return null
        return self.localPositionOf(other, Offset.Zero)
    }

    /** ¿Se ve algo de la escena en [region] (en sus coordenadas)? Fuera de la pantalla, el cristal no la pinta. */
    fun covers(region: Rect): Boolean {
        val (w, h) = size()
        if (w <= 0 || h <= 0) return false
        return region.overlaps(Rect(0f, 0f, w.toFloat(), h.toFloat()))
    }

    /**
     * Pinta la escena en sus coordenadas: la foto (las dos durante el fundido) cubriendo la pantalla desde arriba y, por
     * cada escena que se ve, sus estrellas, la luna y lo demás de la capa de luz. Con [glass] (lo que difumina el
     * cristal), sin las rayas de lluvia.
     */
    fun draw(scope: DrawScope, glass: Boolean = false) = with(scope) {
        val (w, h) = size()
        if (w <= 0 || h <= 0) return@with
        val scrollPx = scroll?.invoke()?.toFloat() ?: 0f
        for (layer in layers()) drawCover(layer.image, w, h, layer.alpha, scrollPx, scroll != null)
        val enter = 1f - astroRise()
        for (pass in scenes()) {
            pass.light?.drawBack(this, scrollPx, pass.alpha)
            moon(pass.moon, pass.alpha)
            pass.light?.drawFront(this, scrollPx, pass.alpha, enter, glass)
        }
    }
}

/**
 * [image] cubriendo [w] × [h], centrada de lado y pegada arriba (lo de abajo de la foto es lo que sobra). Con
 * [parallax], algo más grande para que pueda subir con [scrollPx] sin enseñar su borde.
 */
private fun DrawScope.drawCover(image: ImageBitmap, w: Int, h: Int, alpha: Float, scrollPx: Float, parallax: Boolean) {
    if (alpha <= 0f) return
    var scale = max(w.toFloat() / image.width, h.toFloat() / image.height)
    if (parallax) scale = max(scale, PHOTO_OVERSCAN * h / image.height)
    val dw = image.width * scale
    val dh = image.height * scale
    val top = if (parallax) -(scrollPx * PHOTO_SCROLL).coerceIn(0f, dh - h) else 0f
    val left = (w - dw) / 2
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
        alpha = alpha.coerceIn(0f, 1f),
        filterQuality = FilterQuality.Low,
    )
}

/**
 * La luna de la NASA ([look], con su fase) en [cx], [cy] con [d] de diámetro, con su brillo como `moon()` de la maqueta.
 * Mientras [MoonCache] aún no la tiene, solo el brillo.
 */
internal fun DrawScope.drawMoon(look: MoonLook, lit: ImageBitmap?, show: MoonShow, cx: Float, cy: Float, d: Float, appear: Float = 1f) {
    if (show == MoonShow.NONE) return
    val a = show.alpha * appear
    val phaseLit = (1 - kotlin.math.cos(2 * Math.PI * look.phase)).toFloat() / 2
    val gk = if (phaseLit < 0.01f) 0f else sqrt(phaseLit)
    val side = (if (look.phase < 0.5) 1 else -1) * (if (look.south) -1 else 1)
    val off = d / 2 * 0.55f * (1 - phaseLit) * side
    softGlow(Offset(cx + off, cy), d / 2 * (0.5f + 0.5f * gk) + d * 0.18f, d * 0.45f, Color(120, 130, 230), 0.22f * gk * a)
    softGlow(Offset(cx + off * 0.8f, cy), d / 2 * (0.6f + 0.4f * gk) + d * 0.04f, d * 0.15f, Color(190, 200, 255), 0.36f * gk * a)
    val image = lit ?: return
    val k = d / (2 * moonRadius(image.width))
    val side2 = (image.width * k).roundToInt()
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset((cx - side2 / 2f).roundToInt(), (cy - side2 / 2f).roundToInt()),
        dstSize = IntSize(side2, side2),
        alpha = a,
        filterQuality = FilterQuality.Medium,
    )
}

/** Un disco de luz que se desvanece en [sigma] alrededor de [r] (`softDisc` de la maqueta, con un degradado radial). */
private fun DrawScope.softGlow(center: Offset, r: Float, sigma: Float, color: Color, alpha: Float) {
    if (alpha <= 0.002f || r <= 0f) return
    val outer = r + 3 * sigma
    val inner = max(0f, r - 3 * sigma) / outer
    drawCircle(
        Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            inner to color.copy(alpha = alpha),
            (r / outer) to color.copy(alpha = alpha / 2),
            1f to color.copy(alpha = 0f),
            center = center,
            radius = outer,
        ),
        radius = outer,
        center = center,
    )
}

/** La escena de detrás, para el cristal. `null` fuera de [SceneBackdrop] (el cristal se queda sin difuminar). */
val LocalBackdrop: ProvidableCompositionLocal<Backdrop?> = staticCompositionLocalOf { null }

/** El tono del cristal de la foto ([GlassTone.forPhoto]); lo fija [SceneBackdrop]. */
val LocalGlassTone: ProvidableCompositionLocal<GlassTone> = compositionLocalOf { GlassTone.CLEAR }

/**
 * El fondo a pantalla completa (009): la foto del cielo [photo], cargada de [photos] al tamaño de la pantalla, y encima
 * lo de [effects] (la luna de la NASA de noche; con [light], la capa de luz de la principal). Todo quieto (012 P6): se
 * pinta cuando cambia el dato y no pide fotogramas. Cuando cambian la foto o [effects], la escena nueva funde sobre la
 * vieja en [PHOTO_FADE_MS] ([PHOTO_FADE_STILL_MS] sin [clock]). Los paneles de la barra de cristal de dentro la difuminan
 * a través de [LocalBackdrop], con el tono de la foto ([LocalGlassTone]); en las fotos claras el texto lleva sombra.
 *
 * Sin [light] (los días), solo la foto y la luna. [clock] es el permiso para animar los fundidos (`null` = Quitar
 * animaciones o ahorro de batería). Etiquetas para los tests: `sky-moving` o `sky-still` según [clock], y
 * `sky-photo-<recurso>` por cada foto que se ve (dos durante el fundido). [scroll] es el desplazamiento del contenido: la
 * luna sube a la mitad de velocidad. Con [intro], el astro (la luna, o el destello del sol) sube a su sitio al abrir.
 */
@Composable
fun SceneBackdrop(
    photo: SkyPhoto,
    effects: SkyEffects,
    photos: PhotoCache,
    clock: SkyClock? = null,
    moon: MoonLook? = null,
    intro: IntroState? = null,
    scroll: () -> Int = { 0 },
    light: Boolean = false,
    content: @Composable () -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val anchor = remember { Backdrop.Anchor() }
    val moving = clock != null
    // El fundido: [current] entra sobre [leaving].
    var current by remember { mutableStateOf(Shown(photo, effects)) }
    var leaving by remember { mutableStateOf<Shown?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(photo, effects) {
        val next = Shown(photo, effects)
        if (next == current) return@LaunchedEffect
        // A mitad de otro fundido (A→B y llega C), sale la que más se ve: así no salta de golpe.
        if (leaving == null || fade.value >= 0.5f) leaving = current
        current = next
        fade.snapTo(0f)
        fade.animateTo(1f, tween(if (moving) PHOTO_FADE_MS else PHOTO_FADE_STILL_MS, easing = LinearEasing))
        leaving = null
    }
    val currentImage = remember(current.photo, size, photos) {
        if (size.width > 0 && size.height > 0) photos.get(current.photo, size.width, size.height) else null
    }
    val leavingImage = remember(leaving?.photo, size, photos) {
        leaving?.photo?.takeIf { size.width > 0 && size.height > 0 }?.let { photos.get(it, size.width, size.height) }
    }
    val density = LocalDensity.current.density
    val currentScroll by rememberUpdatedState(scroll)
    val currentIntro by rememberUpdatedState(intro)
    val lit = remember(moon?.lit) { moon?.lit?.asImageBitmap() }
    val currentLight = rememberLightLayer(light, current.effects, size, density, photos)
    val leavingLight = rememberLightLayer(
        // Solo se construye si lo que sale lleva otra cosa encima: con los mismos efectos se pinta una sola capa.
        light && leaving != null && leaving?.effects != current.effects, leaving?.effects ?: SkyEffects.NONE, size, density, photos,
    )
    val backdrop = remember(anchor, currentImage, leavingImage, moon, lit, density, currentLight, leavingLight) {
        Backdrop(
            anchor,
            size = { size },
            layers = {
                buildList {
                    if (leavingImage != null) add(PhotoLayer(leavingImage, 1f))
                    if (currentImage != null) add(PhotoLayer(currentImage, if (leavingImage != null) fade.value else 1f))
                }
            },
            moon = { show, appear ->
                if (moon != null && show != MoonShow.NONE) {
                    val d = MOON_D_DP * density * (if (show == MoonShow.CLEAR) 1f else MOON_SMALLER)
                    val rise = currentIntro?.let { introAstro(it.ms).first * density } ?: 0f
                    val cy = MOON_Y_DP * density - currentScroll() * MOON_SCROLL + rise
                    drawMoon(moon, lit, show, size.width * MOON_X, cy, d, appear)
                }
            },
            scenes = {
                val out = leaving
                val now = current
                // Si lo de encima no cambia (solo la foto), una sola vez: dos mitades de lo mismo no suman uno entero.
                if (out == null || out.effects == now.effects) {
                    listOf(ScenePass(currentLight, now.effects.moon, 1f))
                } else {
                    listOf(ScenePass(leavingLight, out.effects.moon, 1f - fade.value), ScenePass(currentLight, now.effects.moon, fade.value))
                }
            },
            scroll = if (light) ({ currentScroll() }) else null,
            astroRise = { currentIntro?.let { (introAstro(it.ms).first / ASTRO_RISE_DP) } ?: 0f },
        )
    }
    val tone = GlassTone.forPhoto(current.photo)
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .onGloballyPositioned { anchor.coordinates = it }
            .testTag(if (moving) "sky-moving" else "sky-still"),
    ) {
        // El cielo, en su capa: repintarlo (fundido, luna) no vuelve a grabar el contenido.
        Box(Modifier.matchParentSize().graphicsLayer().drawBehind { backdrop.draw(this) })
        leaving?.let { Box(Modifier.matchParentSize().testTag("sky-photo-${it.photo.file}")) }
        Box(Modifier.matchParentSize().testTag("sky-photo-${current.photo.file}"))
        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            LocalGlassTone provides tone,
            LocalTextStyle provides LocalTextStyle.current.copy(shadow = glassTextShadow(tone)),
        ) {
            content()
        }
    }
}

/** La capa de luz de [effects] al tamaño de la pantalla, o `null` si [enabled] no, o aún no hay tamaño. */
@Composable
private fun rememberLightLayer(enabled: Boolean, effects: SkyEffects, size: IntSize, density: Float, photos: PhotoCache): LightLayer? {
    val clouds = remember(enabled, effects, size.height, photos) {
        if (enabled && size.height > 0 && LightLayer.needsClouds(effects)) photos.cloudLayer(size.height) else null
    }
    return remember(enabled, effects, size, density, clouds) {
        if (enabled && size.width > 0 && size.height > 0) LightLayer(effects, size.width.toFloat(), size.height.toFloat(), density, clouds) else null
    }
}
