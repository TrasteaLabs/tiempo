package com.trastealabs.tiempo.ui

import com.trastealabs.tiempo.ui.theme.OnSky
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import com.trastealabs.tiempo.ui.scene.LocalBackdrop
import com.trastealabs.tiempo.ui.theme.CristalColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.HourPoint
import com.trastealabs.tiempo.domain.SheetRow
import com.trastealabs.tiempo.domain.shownDegrees
import com.trastealabs.tiempo.domain.humidityRows
import com.trastealabs.tiempo.domain.windRows
import com.trastealabs.tiempo.domain.windScaleKmh
import com.trastealabs.tiempo.ui.theme.Tabular
import java.time.ZoneId

/** Qué hoja de 24 h está abierta (H6 de la 004). */
enum class HourlySheetKind { WIND, HUMIDITY }

private val HumidityFrom = Color(0xFF7FB3EC)
private val HumidityTo = Color(0xFFB5DBFF)
private val RowText = CristalType.bodyMedium.merge(Tabular).copy(color = Color.White)
private val Muted = OnSheet.Tertiary

/**
 * H6. Las próximas 24 h desde [now], acotadas (2026-11-04): el viento en 8 tramos de 3 horas, con la flecha hacia donde
 * sopla, una barra (racha al 30 % y viento en blanco) y «14 · 28», siempre con la cifra; o la humedad en 4 partes del
 * día, con su barra de la mínima a la máxima, «45–78 %» y el rocío. Es un `ModalBottomSheet` de M3 (012, P5): se baja con
 * el dedo, el asa, el velo y atrás, con el movimiento y el velo de M3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourlySheet(kind: HourlySheetKind, hourly: List<HourPoint>, now: Long, zone: ZoneId, onDismiss: () -> Unit) {
    val wind = kind == HourlySheetKind.WIND
    val rows = remember(hourly, now, zone, wind) { if (wind) windRows(hourly, now, zone) else humidityRows(hourly, now, zone) }
    val scale = windScaleKmh(rows)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CristalColors.SheetSurface,
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OnSky.Control) },
    ) {
        // Dentro, nada de la escena y sin la sombra del texto de la principal.
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(shadow = null),
            LocalBackdrop provides null,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.padding(start = 22.dp, end = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painterResource(if (wind) R.drawable.ic_wx_wind else R.drawable.ic_wx_humidity),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        stringResource(if (wind) R.string.card_wind else R.string.card_humidity),
                        style = CristalType.titleLarge,
                    )
                }
                Column(
                    modifier = Modifier
                        .testTag("hourly_sheet")
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, bottom = 22.dp),
                ) {
                    Text(
                        stringResource(if (wind) R.string.sheet_wind_hint else R.string.sheet_humidity_hint),
                        color = OnSheet.Secondary,
                        style = CristalType.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                    HeadRow(wind)
                    rows.forEach { row ->
                        when (row) {
                            is SheetRow.DayBreak -> DayBreakRow(row.label)
                            is SheetRow.Wind -> WindRow(row, scale)
                            is SheetRow.Humidity -> HumidityRow(row)
                        }
                    }
                }
            }
        }
    }
}

private val HourColumn = 52.dp
/** «Madrugada» a 14,5 sp. */
private val PartColumn = 84.dp

@Composable
private fun HeadRow(wind: Boolean) {
    val style = CristalType.labelMedium.copy(color = Muted)
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (wind) {
            Text(stringResource(R.string.sheet_hours), style = style, modifier = Modifier.width(HourColumn))
            Box(Modifier.width(22.dp))
            Text(stringResource(R.string.sheet_wind_column), style = style, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.sheet_kmh), style = style, textAlign = TextAlign.End, modifier = Modifier.width(70.dp))
        } else {
            Box(Modifier.width(PartColumn))
            Text(stringResource(R.string.card_humidity), style = style, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.sheet_humidity_range), style = style, textAlign = TextAlign.End, modifier = Modifier.width(HumidityText))
            Text(stringResource(R.string.sheet_dew), style = style, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
        }
    }
}

/** Raya del día nuevo: el día tal como llega de Next24Rows («Miércoles, 11 de junio»), igual que en la principal. */
@Composable
private fun DayBreakRow(label: String) {
    Column(Modifier.testTag("sheet_day").fillMaxWidth().padding(top = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.28f)))
        Text(
            label,
            style = CristalType.labelMedium,
            color = OnSheet.Secondary,
            modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun SheetRowLayout(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(Modifier.testTag("sheet_row").semantics(mergeDescendants = true) {}.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** tramo · flecha · barra · «**14** · 28». Con viento flojo, la cifra igual («**2** · 7»): «Calma» confundía. */
@Composable
private fun WindRow(row: SheetRow.Wind, scale: Int) {
    val kmh = row.windKmh
    val gust = row.gustKmh
    SheetRowLayout {
        Text(row.label, style = RowText, modifier = Modifier.width(HourColumn))
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            val from = row.fromDeg
            // Sin viento no hay hacia dónde apuntar.
            if (kmh != null && kmh > 0 && from != null) {
                WindArrow(((from + 180) % 360 + 360) % 360, 18.dp, Modifier.testTag("wind_arrow"))
            }
        }
        Canvas(Modifier.weight(1f).height(8.dp)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.10f), size = size, cornerRadius = radius)
            fun bar(value: Int?, color: Color) {
                val v = value ?: return
                val w = (v.toFloat() / scale).coerceIn(0f, 1f) * size.width
                if (w > 0f) drawRoundRect(color, size = Size(maxOf(w, size.height), size.height), cornerRadius = radius)
            }
            bar(gust, Color.White.copy(alpha = 0.30f))
            bar(kmh, Color.White)
        }
        val text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(kmh?.toString() ?: "–") }
            if (gust != null) withStyle(SpanStyle(color = Muted)) { append(" · $gust") }
        }
        Text(text, style = RowText, textAlign = TextAlign.End, modifier = Modifier.width(70.dp))
    }
}

private val HumidityText = 70.dp

/** parte del día · barra de la mínima a la máxima (sobre 0–100 %) · «45–**78** %» · rocío «11°» apagado. */
@Composable
private fun HumidityRow(row: SheetRow.Humidity) {
    SheetRowLayout {
        Text(row.label, style = RowText, modifier = Modifier.width(PartColumn))
        Canvas(Modifier.weight(1f).height(8.dp)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.10f), size = size, cornerRadius = radius)
            val x0 = size.width * row.minPct.coerceIn(0, 100) / 100f
            val x1 = size.width * row.maxPct.coerceIn(0, 100) / 100f
            // Como poco, un punto del alto de la barra, centrado en el tramo.
            val w = maxOf(x1 - x0, size.height)
            val left = ((x0 + x1 - w) / 2).coerceIn(0f, size.width - w)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(HumidityFrom, HumidityTo), startX = left, endX = left + w),
                topLeft = Offset(left, 0f),
                size = Size(w, size.height),
                cornerRadius = radius,
            )
        }
        val text = buildAnnotatedString {
            if (row.minPct != row.maxPct) withStyle(SpanStyle(color = Muted)) { append("${row.minPct}–") }
            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("${row.maxPct}") }
            append(" %")
        }
        Text(text, style = RowText, textAlign = TextAlign.End, modifier = Modifier.width(HumidityText))
        Text(
            row.dewC?.let { "${shownDegrees(it)}°" } ?: "–",
            style = RowText.copy(color = Muted),
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}
