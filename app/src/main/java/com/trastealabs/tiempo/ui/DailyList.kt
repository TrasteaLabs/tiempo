package com.trastealabs.tiempo.ui

import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.DayPoint
import com.trastealabs.tiempo.domain.Reliability
import com.trastealabs.tiempo.domain.ReliabilityLevel
import com.trastealabs.tiempo.domain.HourPoint
import com.trastealabs.tiempo.domain.dayIconSky
import com.trastealabs.tiempo.domain.level
import com.trastealabs.tiempo.domain.longDayLabel
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.Tabular
import com.trastealabs.tiempo.ui.theme.WeatherIcon
import com.trastealabs.tiempo.ui.theme.degrees
import com.trastealabs.tiempo.ui.theme.millimetres
import com.trastealabs.tiempo.ui.theme.percent
import com.trastealabs.tiempo.ui.theme.tempColor
import java.time.LocalDate
import java.time.ZoneId

/** Temperatura actual que marca el punto de la barra de «Hoy» (H6 del 002): así los tests la ven sin pintar. */
val NowTemperatureKey = SemanticsPropertyKey<Double>("NowTemperature")
private var SemanticsPropertyReceiver.nowTemperature by NowTemperatureKey

/**
 * Próximos días (H4): día, icono (06:00–18:00), lluvia en % y mm, mínima, barra de rango y máxima.
 * Las barras comparten la escala de todos los días, así se ve qué día es más frío o más cálido; cada una
 * va del color de su mínima al de su máxima y, en «Hoy», un punto marca [nowC] (H6 del 002).
 * Con [reliability] válida (003), cada día lleva su anillo tras el nombre. Tocar cualquier fila, haya fiabilidad o no,
 * llama a [onDayClick] con su fecha (004, H4: abre la página de ese día).
 */
@Composable
fun DailyList(
    days: List<DayPoint>,
    hourly: List<HourPoint>,
    zone: ZoneId,
    today: String,
    modifier: Modifier = Modifier,
    nowC: Double? = null,
    reliability: Reliability? = null,
    onDayClick: (date: String) -> Unit = {},
) {
    if (days.isEmpty()) return
    val levels = rowLevels(reliability)
    val low = minOf(days.minOf { it.minC }, nowC ?: Double.MAX_VALUE)
    val high = maxOf(days.maxOf { it.maxC }, nowC ?: -Double.MAX_VALUE, low + 1.0)
    val weekdays = stringArrayResource(R.array.weekdays_short)
    val todayLabel = stringResource(R.string.today)
    Column(modifier) {
        days.forEachIndexed { i, day ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
            val isToday = day.date == today
            val name = if (isToday) todayLabel else weekdays[LocalDate.parse(day.date).dayOfWeek.value - 1]
            val columns = if (levels == null) PlainColumns else ReliabilityColumns
            DayRow(name, day, hourly, zone, low, high, if (isToday) nowC else null, columns, levels?.get(day.date), onDayClick)
        }
    }
}

@Composable
private fun DayRow(
    name: String,
    day: DayPoint,
    hourly: List<HourPoint>,
    zone: ZoneId,
    low: Double,
    high: Double,
    nowC: Double?,
    columns: Columns,
    level: ReliabilityLevel?,
    onDayClick: (date: String) -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    // Con la letra del sistema más grande, las columnas de texto crecen con ella (hasta el 200 %, 019): a anchura fija,
    // «Hoy» y las cifras se partían letra a letra.
    val k = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("day")
            .clickable(interactionSource = press, role = Role.Button) { onDayClick(day.date) }
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(name, Modifier.width(columns.day * k), color = OnSky.Text, style = CristalType.bodyMedium, maxLines = 1, softWrap = false)
        // Un día sin nivel (pocos escenarios) deja el hueco vacío: las columnas siguen alineadas.
        if (columns.ring != null) {
            if (level != null) {
                val label = stringResource(R.string.reliability_row, longDayLabel(day.date), reliabilityLabel(level).lowercase())
                ReliabilityIcon(level, size = columns.ring, contentDescription = label)
            } else {
                Box(Modifier.size(columns.ring))
            }
        }
        WeatherIcon(dayIconSky(day.date, hourly, zone), isDay = true, size = 24.dp)
        // La lluvia, solo si merece la pena mirarla (maqueta: 20 % o más).
        Column(Modifier.width(46.dp * k)) {
            if ((day.precipProbabilityMax ?: 0) >= 20) {
                Text(
                    percent(day.precipProbabilityMax),
                    color = OnSky.Text,  // 019: el azul claro no llega a 4,5:1 sobre el cielo de día
                    style = sceneText(CristalType.labelMedium.merge(Tabular)),
                )
            }
            if (day.precipSumMm >= 0.05) {
                Text(millimetres(day.precipSumMm), color = OnSky.Secondary, style = sceneText(CristalType.labelMedium.merge(Tabular)))
            }
        }
        Text(
            degrees(day.minC),
            Modifier.width(columns.temp * k),
            color = OnSky.Secondary,
            textAlign = TextAlign.End,
            style = sceneText(CristalType.bodyMedium.merge(Tabular)),
        )
        RangeBar(day.minC, day.maxC, low, high, nowC, Modifier.weight(1f))
        Text(
            degrees(day.maxC),
            Modifier.width(columns.temp * k),
            color = OnSky.Text,
            textAlign = TextAlign.End,
            style = sceneText(CristalType.titleSmall.merge(Tabular)),
        )
    }
}

/** Mínima→máxima del día sobre la escala [low]–[high], del color de cada temperatura, y el punto de [nowC]. */
@Composable
private fun RangeBar(min: Double, max: Double, low: Double, high: Double, nowC: Double?, modifier: Modifier = Modifier) {
    val semantics = if (nowC != null) Modifier.semantics { nowTemperature = nowC } else Modifier
    Canvas(modifier.height(10.dp).then(semantics)) {
        val bar = 6.dp.toPx()
        val top = (size.height - bar) / 2
        val radius = CornerRadius(bar / 2)
        fun x(t: Double) = ((t - low) / (high - low)).toFloat() * size.width
        drawRoundRect(Color.White.copy(alpha = 0.18f), topLeft = Offset(0f, top), size = Size(size.width, bar), cornerRadius = radius)
        val start = x(min)
        val end = x(max)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color(tempColor(min)), Color(tempColor(max))), startX = start, endX = end),
            topLeft = Offset(start, top),
            size = Size(maxOf(end - start, bar), bar),
            cornerRadius = radius,
        )
        if (nowC != null) {
            val center = Offset(x(nowC), size.height / 2)
            drawCircle(Color.Black.copy(alpha = 0.15f), radius = 7.dp.toPx(), center = center)
            drawCircle(Color.White, radius = 5.dp.toPx(), center = center)
        }
    }
}

/** Anchos de la fila: sin fiabilidad, los del 002; con ella, los del diseño del 003 (con el hueco del anillo). */
private class Columns(val day: Dp, val ring: Dp?, val temp: Dp)

/**
 * Nivel de cada fecha, o null si la fila va como siempre: sin fiabilidad o sin ningún día con nivel (todos con
 * menos de 20 escenarios), para no dejar la columna del anillo vacía en todas las filas.
 */
internal fun rowLevels(reliability: Reliability?): Map<String, ReliabilityLevel?>? =
    reliability?.days?.associate { it.date to it.level() }?.takeIf { levels -> levels.values.any { it != null } }

private val PlainColumns = Columns(day = 40.dp, ring = null, temp = 30.dp)
private val ReliabilityColumns = Columns(day = 36.dp, ring = 18.dp, temp = 28.dp)

