package com.trastealabs.tiempo.ui

import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.DstShift
import com.trastealabs.tiempo.domain.HourPoint
import com.trastealabs.tiempo.domain.skyOf
import com.trastealabs.tiempo.domain.hourRainRow
import com.trastealabs.tiempo.domain.RAIN_BAR_FROM_MM
import com.trastealabs.tiempo.ui.theme.skyLabel
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import kotlin.math.roundToInt
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.Tabular
import com.trastealabs.tiempo.ui.theme.WeatherIcon
import com.trastealabs.tiempo.ui.theme.degrees
import com.trastealabs.tiempo.ui.theme.millimetres
import com.trastealabs.tiempo.ui.theme.percent
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

// Medidas de cada columna, de arriba abajo, con la letra al 100 %. Con la letra del sistema más grande crecen con ella
// (hasta el 200 %, 019): a 52 dp «Ahora» se partía y las cifras se pisaban.
private val BaseColumnWidth = 52.dp
private val BaseIconTop = 22.dp
private val BaseDotTop = 78.dp          // la temperatura más alta
private val BaseDotBottom = 118.dp      // la más baja
private val BaseBarBottom = 156.dp
private val BaseBarMax = 28.dp
private val BasePercentTop = 160.dp
private val BaseMmTop = 176.dp
private val BaseChartHeight = 194.dp

/**
 * Las horas que ya han pasado (hoy en la página del día), y su tramo de línea. Se apaga el dibujo (barras, puntos,
 * línea e icono); el texto queda en terciario y se lee (019).
 */
private const val PAST_ALPHA = 0.38f
private const val PAST_LINE_ALPHA = 0.35f

/** Una hora que ya ha pasado (atenuada): así los tests la ven sin mirar la opacidad. */
val PastHourKey = SemanticsPropertyKey<Boolean>("PastHour")
private var SemanticsPropertyReceiver.pastHour by PastHourKey

/**
 * Gráfica de horas (H3; en la 004 también la del día): línea de temperatura y barras de lluvia, deslizando de lado.
 * [nowIndex] es la columna resaltada, con «Ahora» y el punto grande; las anteriores van atenuadas. `null`: ninguna.
 * Con [dst], una raya discontinua blanca antes de esa columna (cambio de hora). Se abre enseñando «Ahora».
 */
@Composable
fun HourlyChart(
    hours: List<HourPoint>,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    nowIndex: Int? = 0,
    dst: DstShift? = null,
    dryText: String = stringResource(R.string.hours_dry_24h),
) {
    if (hours.isEmpty()) return
    val k = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val ColumnWidth = BaseColumnWidth * k
    val IconTop = BaseIconTop * k
    val DotTop = BaseDotTop * k
    val DotBottom = BaseDotBottom * k
    val BarBottom = BaseBarBottom * k
    val BarMax = BaseBarMax * k
    val PercentTop = BasePercentTop * k
    val MmTop = BaseMmTop * k
    val ChartHeight = BaseChartHeight * k
    val rain = remember(hours) { hourRainRow(hours) }
    val low = hours.minOf { it.temperatureC }
    val span = max(hours.maxOf { it.temperatureC } - low, 1.0)
    // Lo más alto llena la barra, pero sin que una llovizna parezca un diluvio.
    val rainScale = max(hours.maxOf { it.precipMm }, 2.0)
    fun dotY(t: Double): Dp = DotBottom - (DotBottom - DotTop) * ((t - low) / span).toFloat()
    val past = nowIndex ?: 0

    val scroll = rememberScrollState()
    val columnPx = with(LocalDensity.current) { ColumnWidth.roundToPx() }
    // La columna de antes de «Ahora» en el borde izquierdo.
    LaunchedEffect(nowIndex, columnPx) { if (nowIndex != null && nowIndex > 1) scroll.scrollTo((nowIndex - 1) * columnPx) }

    Box(modifier) {
    Box(Modifier.horizontalScroll(scroll).padding(horizontal = 4.dp)) {
        Box(Modifier.width(ColumnWidth * hours.size).height(ChartHeight)) {
            Canvas(Modifier.fillMaxSize()) {
                val cw = ColumnWidth.toPx()
                val points = hours.mapIndexed { i, h -> Offset(cw * i + cw / 2, dotY(h.temperatureC).toPx()) }

                // La columna «Ahora», resaltada (maqueta).
                if (nowIndex != null) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.12f),
                        topLeft = Offset(cw * nowIndex + 3.dp.toPx(), 0f),
                        size = Size(cw - 6.dp.toPx(), size.height),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                    )
                }

                // Cambio de hora: raya discontinua entre las dos columnas.
                if (dst != null && dst.index in 1 until hours.size) {
                    val x = cw * dst.index
                    drawLine(
                        OnSky.Tertiary,
                        start = Offset(x, 4.dp.toPx()),
                        end = Offset(x, size.height - 4.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                    )
                }

                hours.forEachIndexed { i, h ->
                    if (h.precipMm < RAIN_BAR_FROM_MM) return@forEachIndexed
                    val height = max(BarMax.toPx() * min(h.precipMm / rainScale, 1.0).toFloat(), 3.dp.toPx())
                    drawRoundRect(
                        color = OnSky.Rain.copy(alpha = 0.9f * if (i < past) PAST_ALPHA else 1f),
                        topLeft = Offset(points[i].x - 10.dp.toPx(), BarBottom.toPx() - height),
                        size = Size(20.dp.toPx(), height),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }

                fun pathOf(from: Int, to: Int) = Path().apply {
                    moveTo(points[from].x, points[from].y)
                    (from + 1..to).forEach { lineTo(points[it].x, points[it].y) }
                }
                val line = pathOf(0, points.lastIndex)
                val area = Path().apply {
                    addPath(line)
                    lineTo(points.last().x, DotBottom.toPx() + 14.dp.toPx())
                    lineTo(points.first().x, DotBottom.toPx() + 14.dp.toPx())
                    close()
                }
                drawPath(
                    area,
                    Brush.verticalGradient(
                        listOf(OnSky.Temperature.copy(alpha = 0.28f), Color.Transparent),
                        startY = DotTop.toPx(),
                        endY = DotBottom.toPx() + 14.dp.toPx(),
                    ),
                )
                val stroke = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round)
                // Lo que ya ha pasado, más apagado; desde «Ahora», la línea entera.
                if (past > 0) drawPath(pathOf(0, past), OnSky.Temperature.copy(alpha = PAST_LINE_ALPHA), style = stroke)
                drawPath(pathOf(past, points.lastIndex), OnSky.Temperature, style = stroke)
                points.forEachIndexed { i, p ->
                    drawCircle(
                        Color.White.copy(alpha = if (i < past) PAST_ALPHA else 1f),
                        radius = (if (i == nowIndex) 4.dp else 2.5.dp).toPx(),
                        center = p,
                    )
                }
            }
            Row {
                hours.forEachIndexed { i, h ->
                    HourColumn(h, now = i == nowIndex, past = i < past, showRain = rain.shown[i], zone = zone, dotY = dotY(h.temperatureC))
                }
            }
        }
    }
    // Ninguna hora llega al 20 % ni tiene barra: una frase en el sitio de la fila, en vez de «0 %» hora tras hora.
    if (rain.dry) {
        Text(
            dryText,
            Modifier.testTag("hours_dry").offset(y = PercentTop).padding(horizontal = 16.dp),
            color = OnSky.Secondary,
            style = sceneText(CristalType.labelMedium),
        )
    }
    }
}

@Composable
private fun HourColumn(hour: HourPoint, now: Boolean, past: Boolean, showRain: Boolean, zone: ZoneId, dotY: Dp) {
    val k = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val ColumnWidth = BaseColumnWidth * k
    val IconTop = BaseIconTop * k
    val PercentTop = BasePercentTop * k
    val MmTop = BaseMmTop * k
    val ChartHeight = BaseChartHeight * k
    val hourOfDay = Instant.ofEpochSecond(hour.time).atZone(zone).hour
    val label = if (now) stringResource(R.string.now) else "%02d".format(hourOfDay)
    val sky = skyOf(hour.weatherCode)
    val hasMm = hour.precipMm >= RAIN_BAR_FROM_MM
    // TalkBack (019): «14:00, 21 grados, nubes, 40 % de lluvia, 1,2 mm», una vez por columna.
    val spoken = buildList {
        add(if (now) label else stringResource(R.string.hour_spoken_time, hourOfDay))
        add(stringResource(R.string.hour_spoken_temp, hour.temperatureC.roundToInt()))
        add(stringResource(skyLabel(sky)))
        if (showRain) add(stringResource(R.string.hour_spoken_rain, hour.precipProbability ?: 0))
        if (hasMm) add(millimetres(hour.precipMm))
    }.joinToString(", ")
    val ink = if (past) OnSky.Tertiary else OnSky.Text
    Box(
        Modifier
            .width(ColumnWidth)
            .height(ChartHeight)
            .testTag("hour")
            .clearAndSetSemantics {
                contentDescription = spoken
                if (past) pastHour = true
            },
    ) {
        val center = Modifier.align(Alignment.TopCenter)
        Text(label, center.padding(top = 6.dp), color = if (past) OnSky.Tertiary else OnSky.Secondary, style = sceneText(CristalType.labelMedium.merge(Tabular)))
        WeatherIcon(sky, hour.isDay, center.offset(y = IconTop + 6.dp).alpha(if (past) PAST_ALPHA else 1f))
        Text(
            degrees(hour.temperatureC),
            center.offset(y = dotY - 24.dp * k),
            color = ink,
            style = sceneText(CristalType.titleSmall.merge(Tabular)),
        )
        // La lluvia, solo si merece la pena mirarla (20 % o más, como la lista de días). En blanco: el azul claro no
        // llegaba a 4,5:1 sobre las tarjetas de día (019); el color lo lleva la barra.
        if (showRain) {
            Text(
                percent(hour.precipProbability),
                center.offset(y = PercentTop),
                color = ink,
                style = sceneText(CristalType.labelMedium.merge(Tabular)),
            )
        }
        if (hasMm) {
            Text(millimetres(hour.precipMm), center.offset(y = MmTop), color = ink, style = sceneText(CristalType.labelMedium.merge(Tabular)))
        }
    }
}
