package com.trastealabs.tiempo.ui

import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.AirLevel
import com.trastealabs.tiempo.domain.AirNow
import com.trastealabs.tiempo.domain.PollenLevel
import com.trastealabs.tiempo.domain.PollenToday
import com.trastealabs.tiempo.domain.PollenType
import com.trastealabs.tiempo.domain.airLevel
import com.trastealabs.tiempo.ui.glass.translucent
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.theme.Tabular
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Fichas del aire y del polen (005, diseño B · Medidores): a todo el ancho, con la composición de la del sol.

/** Escala europea, aclarada para el cristal (`diseno.md`). */
internal fun AirLevel.color(): Color = when (this) {
    AirLevel.GOOD -> Color(0xFF6EE7DC)
    AirLevel.FAIR -> Color(0xFF7FD9A8)
    AirLevel.MODERATE -> Color(0xFFF2E36A)
    AirLevel.POOR -> Color(0xFFFF7B6E)
    AirLevel.VERY_POOR -> Color(0xFFE8577F)
    AirLevel.EXTREMELY_POOR -> Color(0xFFB98AF0)
}

/** null en «nada»: se pinta apagado. */
internal fun PollenLevel.color(): Color? = when (this) {
    PollenLevel.NONE -> null
    PollenLevel.LOW -> Color(0xFF8FDDA0)
    PollenLevel.MODERATE -> Color(0xFFF5D36B)
    PollenLevel.HIGH -> Color(0xFFFF8A6B)
}

@DrawableRes
internal fun PollenType.icon(): Int = when (this) {
    PollenType.OLIVE -> R.drawable.ic_wx_olive
    PollenType.GRASS -> R.drawable.ic_wx_grass
}

private val LevelText = CristalType.titleLarge
private val PollenLine = CristalType.bodyLarge

/** H6. Arco de seis colores con la cifra, el nivel en palabras y qué lo empeora. */
@Composable
internal fun AirTile(air: AirNow, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    WideTile(air.spoken(), onClick, modifier.testTag("air_card")) {
        AirGauge(air.aqi)
        Column {
            TileTitle(R.drawable.ic_wx_air, stringResource(R.string.card_air))
            Text(air.level.label, color = Color.White, style = sceneText(LevelText), modifier = Modifier.padding(top = 6.dp))
            air.cause?.let {
                Text(
                    it.label,
                    color = OnSky.Secondary,
                    style = sceneText(TileDetail),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** H8. Un círculo por tipo que llega a «bajo» y «Olivo **alto**». */
@Composable
internal fun PollenTile(pollen: PollenToday, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    WideTile(pollen.spoken(), onClick, modifier.testTag("pollen_card")) {
        Row(
            Modifier.width(100.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pollen.types.forEach { (type, level) -> PlantDisc(type, level, 44.dp) }
        }
        Column {
            TileTitle(R.drawable.ic_wx_pollen, stringResource(R.string.card_pollen))
            pollen.types.forEach { (type, level) ->
                val line = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(type.label) }
                    append(" ")
                    append(level.label.lowercase())
                }
                Text(line, color = Color.White, style = sceneText(PollenLine), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/**
 * Ficha ancha como la del sol, entera un botón: TalkBack la lee como [spoken] y añade «Abre los próximos días».
 * Sin [onClick] (sin días que enseñar) no se toca.
 */
@Composable
private fun WideTile(spoken: String, onClick: (() -> Unit)?, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    val openLabel = stringResource(R.string.open_next_days)
    // clearAndSetSemantics borra también lo que pone clickable detrás: la acción va aquí, con su etiqueta.
    val press = remember { MutableInteractionSource() }
    val click = if (onClick != null) Modifier.clip(TileShape).clickable(interactionSource = press, onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = spoken
                if (onClick != null) {
                    role = Role.Button
                    onClick(label = openLabel) { onClick(); true }
                }
            }
            .translucent(TileShape)
            .then(click)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

private val KnobBorder = Color(20, 30, 60).copy(alpha = 0.3f)
private const val GAUGE_TOP = 120

/**
 * `gauge` de la maqueta en un lienzo de 100 × 54 unidades (el de `SkyArc`: centro (50, 46), radio 38): seis tramos
 * de 26,8°, el del nivel de [aqi] entero y más grueso; un punto blanco en la cifra (escala 0–120) y la cifra dentro.
 */
@Composable
internal fun AirGauge(aqi: Int, width: Dp = 100.dp) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // La cifra del arco: titleLarge en el grande, titleMedium en el pequeño de la hoja (nunca un tamaño propio).
    val style = sceneText(Tabular.merge((if (width >= 100.dp) CristalType.titleLarge else CristalType.titleMedium).copy(color = Color.White)))
    val current = airLevel(aqi)
    Canvas(Modifier.size(width, width * 0.54f)) {
        val unit = size.width / 100f
        val center = Offset(50f * unit, 46f * unit)
        val radius = 38f * unit
        val box = Size(radius * 2, radius * 2)
        val topLeft = Offset(center.x - radius, center.y - radius)
        AirLevel.entries.forEachIndexed { i, level ->
            val on = level == current
            drawArc(
                level.color().copy(alpha = if (on) 1f else 0.42f),
                startAngle = 181.6f + 30f * i,
                sweepAngle = 26.8f,
                useCenter = false,
                topLeft = topLeft,
                size = box,
                style = Stroke((if (on) 6f else 4f) * unit),
            )
        }
        val angle = PI * (1 - min(aqi, GAUGE_TOP).coerceAtLeast(0) / GAUGE_TOP.toDouble())
        val knob = Offset(center.x + radius * cos(angle).toFloat(), center.y - radius * sin(angle).toFloat())
        drawCircle(Color.White, radius = 5.5f * unit, center = knob)
        drawCircle(KnobBorder, radius = 5.5f * unit, center = knob, style = Stroke(1.5f * unit))
        val text = measurer.measure(aqi.toString(), style)
        drawText(text, topLeft = Offset(center.x - text.size.width / 2f, 44f * unit - text.firstBaseline))
    }
}

/** La planta en un círculo del color de su nivel (`plantDisc` de la maqueta); «nada», apagado. */
@Composable
internal fun PlantDisc(type: PollenType, level: PollenLevel, size: Dp) {
    val color = level.color()
    val ink = color ?: OnSky.Control   // «nada»: el icono apagado (no es texto)
    val fill = color?.copy(alpha = 0.16f) ?: Color.White.copy(alpha = 0.06f)
    val edge = color?.copy(alpha = 0.8f) ?: Color.White.copy(alpha = 0.2f)
    Box(
        Modifier.size(size).clip(CircleShape).background(fill).border(1.5.dp, edge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(type.icon()),
            contentDescription = null,
            modifier = Modifier.size(size * 0.58f),
            colorFilter = ColorFilter.tint(ink),
        )
    }
}
