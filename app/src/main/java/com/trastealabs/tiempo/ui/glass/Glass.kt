package com.trastealabs.tiempo.ui.glass

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import com.trastealabs.tiempo.ui.scene.Backdrop
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.ui.scene.PHOTO_LOOKS
import com.trastealabs.tiempo.ui.scene.LocalBackdrop
import com.trastealabs.tiempo.ui.scene.LocalGlassTone

/**
 * Tonos del cristal (diseño, «Componentes»): relleno de arriba abajo y difuminado (desviación típica,
 * como el `blur()` de CSS de la maqueta). [shadow]: el texto encima lleva sombra (cristal oscuro sobre cielo claro).
 * Desde el 009 es una clase: además de los tres fijos, cada foto del cielo tiene el suyo ([forPhoto]).
 */
class GlassTone(val top: Color, val bottom: Color, val blur: Dp, val shadow: Boolean = false) {
    companion object {
        /** Normal: blanco del 30 % al 13 %. */
        val CLEAR = GlassTone(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.13f), 18.dp)

        /** Escenas claras (H3): azul `rgb(40,50,90)` del 16 % al 30 %. */
        val DEEP = GlassTone(Color(40, 50, 90).copy(alpha = 0.16f), Color(40, 50, 90).copy(alpha = 0.30f), 18.dp, shadow = true)

        /** Hoja, diálogo y pantallas de mensaje (H8). */
        val DARK = GlassTone(Color(24, 30, 56).copy(alpha = 0.70f), Color(14, 18, 36).copy(alpha = 0.86f), 28.dp)

        /** Uno por foto, hechos una vez: el mismo objeto cada vez (no recompone de más). */
        internal val byPhoto: Map<SkyPhoto, GlassTone> by lazy {
            SkyPhoto.entries.associateWith { photo ->
                val tint = PHOTO_LOOKS.getValue(photo).glassTint
                if (tint <= 0f) {
                    CLEAR
                } else {
                    GlassTone(
                        PhotoInk.copy(alpha = maxOf(0.04f, tint - 0.06f)),
                        PhotoInk.copy(alpha = minOf(1f, tint + 0.06f)),
                        18.dp,
                        shadow = true,
                    )
                }
            }
        }
    }
}

/** La tinta del cristal velado de las fotos (`rgb(16,22,46)` de la maqueta). */
private val PhotoInk = Color(16, 22, 46)

/**
 * El cristal de las tarjetas sobre [photo] (009, H5): claro si el texto blanco ya llega a 4,5:1; si no, velado en tinta
 * de `glassTint − 0,06` arriba a `glassTint + 0,06` abajo, como `glassVars` de la maqueta.
 */
fun GlassTone.Companion.forPhoto(photo: SkyPhoto): GlassTone = byPhoto.getValue(photo)

/**
 * Sombra del texto en escenas claras: `0 1px 3px rgba(20,30,60,.35)` de la maqueta. `Shadow` va en px:
 * medidas pensadas para el OPPO (3 px/dp).
 */
fun glassTextShadow(tone: GlassTone): Shadow? =
    if (tone.shadow) Shadow(Color(20, 30, 60).copy(alpha = 0.35f), Offset(0f, 3f), blurRadius = 9f) else null

/** El estilo de texto de la escena (con la sombra, si toca) más [style]: un `style` a secas la perdería. */
@Composable
@ReadOnlyComposable
fun sceneText(style: TextStyle): TextStyle = LocalTextStyle.current.merge(style)

private val Border = Color.White.copy(alpha = 0.30f)
private val Highlight = Color.White.copy(alpha = 0.35f)

/** Para pasar el difuminado de CSS (desviación típica) al radio de `RenderEffect` (Skia: σ ≈ 0,577·r). */
private const val SIGMA_TO_RADIUS = 1.73f

/**
 * Panel de cristal (H5; 012 P4: solo la barra superior de Ajustes y Sitios, `GlassTopBar`): vuelve a pintar, recortada a
 * [shape], la escena de detrás difuminada con `RenderEffect`, y encima el relleno translúcido, el borde y el brillo de
 * arriba. Para tarjetas, chips y campos, [translucent]: el mismo relleno sin desenfoque.
 *
 * Es un [Modifier.Node]: guarda sus coordenadas en un campo (no en estado) y las lee al dibujar, y solo pide repintar
 * cuando cambia el sitio del panel. Sin `followMoves`: lo que se mueve (hojas, ventana del día) es opaco.
 */
private fun Modifier.glassOver(shape: Shape, tone: GlassTone, backdrop: Backdrop?): Modifier =
    this.graphicsLayer().then(GlassElement(shape, tone, backdrop)).graphicsLayer()

/** [glass] con el [LocalBackdrop] y el [LocalGlassTone] de la escena. */
@Composable
fun Modifier.glass(shape: Shape, tone: GlassTone = LocalGlassTone.current): Modifier = glassOver(shape, tone, LocalBackdrop.current)

private data class GlassElement(val shape: Shape, val tone: GlassTone, val backdrop: Backdrop?) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(shape, tone, backdrop)

    override fun update(node: GlassNode) {
        node.shape = shape
        node.tone = tone
        node.backdrop = backdrop
        node.invalidateDraw()
    }
}

private class GlassNode(var shape: Shape, var tone: GlassTone, var backdrop: Backdrop?) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var coordinates: LayoutCoordinates? = null
    private var lastRoot = Offset.Unspecified

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        val root = coordinates.positionInRoot()
        if (root != lastRoot) {
            lastRoot = root
            invalidateDraw()
        }
    }

    private var layer: GraphicsLayer? = null

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer()
    }

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
    }

    override fun ContentDrawScope.draw() {
        val outline = shape.createOutline(size, layoutDirection, this)
        val clip = Path().apply { addOutline(outline) }
        val belowTop = Path().apply {
            addOutline(outline)
            translate(Offset(0f, 1.dp.toPx()))
        }
        val backdrop = backdrop
        val glassLayer = layer
        val region = backdrop?.offsetOf(coordinates)?.let { Rect(it, size) }
        if (backdrop != null && glassLayer != null && region != null && backdrop.covers(region)) {
            val radius = tone.blur.toPx() * SIGMA_TO_RADIUS
            glassLayer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
            glassLayer.record { translate(-region.left, -region.top) { backdrop.draw(this, glass = true) } }
            clipPath(clip) { drawLayer(glassLayer) }
        }
        drawOutline(outline, Brush.verticalGradient(listOf(tone.top, tone.bottom)))
        clipPath(clip) {
            // Borde de 1 dp por dentro y brillo de 1 dp arriba (`inset 0 1px 0`).
            drawOutline(outline, Border, style = Stroke(2.dp.toPx()))
            clipPath(belowTop, ClipOp.Difference) { drawRect(Highlight) }
        }
        drawContent()
    }
}
