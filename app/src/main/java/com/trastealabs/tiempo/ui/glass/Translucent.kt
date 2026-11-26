package com.trastealabs.tiempo.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.trastealabs.tiempo.ui.scene.LocalGlassTone

private val TranslucentBorder = Color.White.copy(alpha = 0.30f)
private val TranslucentHighlight = Color.White.copy(alpha = 0.35f)

/**
 * Panel translúcido (012 P4, D24): el mismo relleno en degradado `tone.top → tone.bottom`, el borde de 1 dp y el brillo
 * de arriba que el cristal, pero **sin** desenfoque: ni capa, ni `RenderEffect`, ni volver a pintar la escena de detrás,
 * ni leer dónde está el panel. Se dibuja una vez y desplazar no lo repinta. Para lo fijo con desenfoque en vivo,
 * [glass] (solo la barra superior).
 */
@Composable
fun Modifier.translucent(shape: Shape, tone: GlassTone = LocalGlassTone.current): Modifier =
    this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val clip = Path().apply { addOutline(outline) }
        val belowTop = Path().apply {
            addOutline(outline)
            translate(Offset(0f, 1.dp.toPx()))
        }
        val fill = Brush.verticalGradient(listOf(tone.top, tone.bottom))
        val stroke = Stroke(2.dp.toPx())
        onDrawBehind {
            drawOutline(outline, fill)
            clipPath(clip) {
                drawOutline(outline, TranslucentBorder, style = stroke)
                clipPath(belowTop, ClipOp.Difference) { drawRect(TranslucentHighlight) }
            }
        }
    }
