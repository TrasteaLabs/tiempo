package com.trastealabs.tiempo.ui.day

import com.trastealabs.tiempo.ui.theme.OnSheet
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.DayMoon
import com.trastealabs.tiempo.domain.DaySun
import com.trastealabs.tiempo.ui.MoonIcon
import com.trastealabs.tiempo.ui.LocalMoonSouth
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.theme.Tabular

private val SunBand = Color(0xFFFFD27A)
private val MoonBand = Color(0xFFCDD2FF)
private val Tick = Color.White.copy(alpha = 0.16f)
private val TickLabel = CristalType.labelMedium.copy(color = OnSheet.Tertiary)

/**
 * «Sol y luna» de la página del día (H5.6 de la 004, `timeline` de la maqueta): la línea de 00 a 24 con las marcas
 * cada 6 h, la banda del sol y la de la luna (una o dos), y solo hoy ([nowFraction]) la raya de «ahora». Debajo,
 * las filas del sol y de la luna. Sin luna (datos de antes), solo la del sol. Con [south] (007, por defecto lo que diga
 * [LocalMoonSouth]), la luna en espejo.
 */
@Composable
fun DayLine(sun: DaySun?, moon: DayMoon?, nowFraction: Float?, south: Boolean = LocalMoonSouth.current) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 2.dp)) {
        Timeline(sun, moon, nowFraction)
        if (sun != null) {
            LineRow(Modifier.testTag("day_sun_row"), R.drawable.ic_wx_clear_day, SunBand, sun.range, sun.daylight)
        }
        if (moon != null) {
            if (sun != null) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
            LineRow(Modifier.testTag("day_moon_row"), R.drawable.ic_wx_clear_night, MoonBand, moon.events, moon.label) {
                MoonIcon(moon.phase, 40.dp, south = south)
            }
        }
    }
}

/** El dibujo de la línea (lo leen las filas de debajo: TalkBack no lo lee). */
@Composable
private fun Timeline(sun: DaySun?, moon: DayMoon?, nowFraction: Float?) {
    val measurer = rememberTextMeasurer()
    val nowLabel = stringResource(R.string.now_mark)
    Canvas(
        Modifier
            .testTag("day_line")
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp)
            .height(62.dp)
            .clearAndSetSemantics {},
    ) {
        // La caja de la maqueta: 62 de alto; la línea va de 4 a ancho − 4.
        val u = size.height / 62f
        val left = 4f * u
        val span = size.width - 8f * u
        fun x(f: Float) = left + f.coerceIn(0f, 1f) * span
        listOf(0, 6, 12, 18, 24).forEach { h ->
            val tx = x(h / 24f)
            drawLine(Tick, Offset(tx, 4f * u), Offset(tx, 44f * u), strokeWidth = 1f * u)
            tickLabel(measurer, "%02d".format(h), tx, 58f * u, when (h) { 0 -> -1; 24 -> 1; else -> 0 })
        }
        fun band(from: Float, to: Float, y: Float, color: Color) {
            val a = x(from)
            val b = maxOf(x(to), a + 9f * u)
            drawRoundRect(color, Offset(a, y * u), Size(b - a, 9f * u), CornerRadius(4.5f * u))
        }
        sun?.let { band(it.rise, it.set, 10f, SunBand) }
        moon?.bands?.forEach { band(it.start, it.endInclusive, 28f, MoonBand) }
        if (nowFraction != null) {
            val nx = x(nowFraction)
            drawLine(
                Color.White, Offset(nx, 2f * u), Offset(nx, 46f * u), strokeWidth = 1.6f * u,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * u, 3f * u)),
            )
            val label = measurer.measure(nowLabel, TickLabel.copy(color = Color.White))
            drawText(label, topLeft = Offset(nx - 4f * u - label.size.width, 58f * u - label.size.height * 0.8f))
        }
    }
}

/** Texto centrado en [x] (o pegado a su izquierda o derecha en los extremos), con la línea base cerca de [baseline]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.tickLabel(
    measurer: TextMeasurer, text: String, x: Float, baseline: Float, anchor: Int,
) {
    val layout = measurer.measure(text, TickLabel)
    val w = layout.size.width.toFloat()
    val left = when (anchor) { -1 -> x; 1 -> x - w; else -> x - w / 2 }
    drawText(layout, topLeft = Offset(left, baseline - layout.size.height * 0.8f))
}

/** Fila de debajo de la línea: icono en su color, cifra principal (16 sp) y detalle (12 sp); a la derecha, [trailing]. */
@Composable
private fun LineRow(
    modifier: Modifier,
    icon: Int,
    tint: Color,
    title: String,
    detail: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Image(painterResource(icon), contentDescription = null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(tint))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = sceneText(CristalType.bodyLarge.merge(Tabular)))
            Text(detail, color = OnSheet.Secondary, style = sceneText(CristalType.bodySmall.merge(Tabular)))
        }
        trailing?.invoke()
    }
}
