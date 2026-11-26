package com.trastealabs.tiempo.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import com.trastealabs.tiempo.ui.scene.LocalBackdrop
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.OnSheet
import com.trastealabs.tiempo.ui.theme.CristalColors
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.AirDay
import com.trastealabs.tiempo.domain.PollenDay
import com.trastealabs.tiempo.domain.PollenLevel
import com.trastealabs.tiempo.domain.PollenType
import java.time.LocalDate

/** Qué hoja de los próximos días está abierta (005, H9). */
enum class DaysSheetKind { AIR, POLLEN }

private val Rule = Color.White.copy(alpha = 0.12f)
private val SheetPadding = 22.dp

/** Lo que la fila del aire se sale del relleno de la hoja por cada lado. */
private val AirBleed = 16.dp

/** H9. Hoy y los 3 días siguientes, cada uno con el arco pequeño de su peor hora y el nivel debajo. */
@Composable
fun AirSheet(days: List<AirDay>, today: String, onDismiss: () -> Unit) {
    DaysSheetFrame("air_sheet", R.drawable.ic_wx_air, R.string.card_air, R.string.sheet_air_hint, R.string.sheet_air_note, onDismiss) {
        Block {
            // Para que quepa «Extremadamente desfavorable», la fila gana 16 dp por cada lado (la hoja flota 8 dp separada de
            // los bordes: son los mismos 8 dp que ganaba antes) y deja 2 dp entre días. Aun así, a 360 dp de ancho
            // «Extremadamente» no cabe a 12 sp: se parte con guion, igual en los cuatro días.
            val levelStyle = fittedLevelStyle(days.map { it.level.label }, gap = 2.dp, bleed = AirBleed)
            DaysRow(days.map { it.date }, today, gap = 2.dp, modifier = Modifier.bleed(AirBleed)) { i ->
                val day = days[i]
                val spoken = stringResource(R.string.air_index, day.worstAqi)
                Box(Modifier.semantics { contentDescription = spoken }) { AirGauge(day.worstAqi, 76.dp) }
                Text(
                    day.level.label,
                    style = levelStyle.copy(textAlign = TextAlign.Center),
                    maxLines = 3,
                )
            }
        }
    }
}

/** H9. Un bloque por tipo (olivo y gramíneas) con la planta en su círculo por día; «Nada», apagado. */
@Composable
fun PollenSheet(days: List<PollenDay>, today: String, onDismiss: () -> Unit) {
    DaysSheetFrame("pollen_sheet", R.drawable.ic_wx_pollen, R.string.card_pollen, R.string.sheet_pollen_hint, R.string.sheet_pollen_note, onDismiss) {
        PollenType.entries.forEach { type ->
            Block(Modifier.testTag("pollen_block_" + type.name.lowercase())) {
                Row(
                    Modifier.padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Image(
                        painterResource(type.icon()),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(Color.White),
                    )
                    Text(type.label, style = CristalType.bodyMedium)
                }
                DaysRow(days.map { it.date }, today, gap = 6.dp) { i ->
                    val level = if (type == PollenType.OLIVE) days[i].olive else days[i].grass
                    val none = level == PollenLevel.NONE
                    PlantDisc(type, level, 42.dp)
                    Text(
                        level.label,
                        style = if (none) CristalType.bodySmall else CristalType.labelMedium,
                        color = if (none) OnSheet.Hint else OnSheet.Text,
                    )
                }
            }
        }
    }
}

/**
 * El marco de las hojas de los próximos días (012, P5): un `ModalBottomSheet` de M3 con el icono y el título arriba, la
 * pista, [content] y la nota. Se baja con el dedo, el asa, el velo y atrás.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaysSheetFrame(
    tag: String,
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes hint: Int,
    @StringRes note: Int,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CristalColors.SheetSurface,
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OnSky.Control) },
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(shadow = null),
            LocalBackdrop provides null,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.padding(start = SheetPadding, end = SheetPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        colorFilter = ColorFilter.tint(Color.White),
                    )
                    Text(stringResource(title), style = CristalType.titleLarge)
                }
                Column(
                    modifier = Modifier
                        .testTag(tag)
                        .fillMaxWidth()
                        .padding(start = SheetPadding, end = SheetPadding, bottom = 22.dp),
                ) {
                    Text(
                        stringResource(hint),
                        color = OnSheet.Secondary,
                        style = CristalType.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                    content()
                    Text(
                        stringResource(note),
                        color = OnSheet.Secondary,
                        style = CristalType.bodySmall,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
        }
    }
}

/** Un bloque con su raya fina arriba (`.pblock` de la maqueta). */
@Composable
private fun Block(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), content = content)
    }
}

/** La fila de días, igual en las dos hojas: cada día una columna con su etiqueta y [cell], que TalkBack lee junta. */
@Composable
private fun DaysRow(
    dates: List<String>,
    today: String,
    gap: Dp,
    modifier: Modifier = Modifier,
    cell: @Composable ColumnScope.(Int) -> Unit,
) {
    val weekdays = stringArrayResource(R.array.weekdays_short)
    val todayLabel = stringResource(R.string.today)
    val tomorrowLabel = stringResource(R.string.tomorrow)
    val tomorrow = runCatching { LocalDate.parse(today).plusDays(1).toString() }.getOrNull()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        dates.forEachIndexed { i, date ->
            val label = when (date) {
                today -> todayLabel
                tomorrow -> tomorrowLabel
                else -> LocalDate.parse(date).let { "${weekdays[it.dayOfWeek.value - 1]} ${it.dayOfMonth}" }
            }
            Column(
                Modifier.weight(1f).testTag("days_sheet_day").semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(label, style = CristalType.labelMedium, color = OnSheet.Secondary)
                cell(i)
            }
        }
        // Con menos de 4 días, cada columna sigue con su ancho de 4.
        repeat(SHEET_COLUMNS - dates.size) { Box(Modifier.weight(1f)) }
    }
}

private const val SHEET_COLUMNS = 4

/**
 * El nivel del aire en la hoja va siempre a 12 sp (019: 11 solo en ejes). Si la palabra más larga no cabe (a 360 dp,
 * «Extremadamente»), se parte con guion en vez de bajar de tamaño.
 */
private val LevelStyles = listOf(CristalType.labelMedium, CristalType.labelMedium.copy(hyphens = Hyphens.Auto))

/**
 * El rol del nivel del aire en la hoja (lo mismo en los cuatro días): el primero de [LevelStyles] en que la palabra más
 * larga cabe en una columna (la hoja más [bleed] por cada lado, en 4 columnas con [gap] entre ellas). Si ni el más
 * pequeño cabe, se queda ese: baja de rol, nunca a un tamaño propio.
 */
@Composable
private fun fittedLevelStyle(labels: List<String>, gap: Dp, bleed: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // La hoja de M3 ocupa todo el ancho (hasta 640 dp).
    val sheetWidth = minOf(LocalConfiguration.current.screenWidthDp.dp, 640.dp) - SheetPadding * 2
    val column = with(density) { ((sheetWidth + bleed * 2 - gap * (SHEET_COLUMNS - 1)) / SHEET_COLUMNS).toPx() }
    val words = remember(labels) { labels.flatMap { it.split(' ') } }
    return LevelStyles.firstOrNull { style ->
        // Un poco de holgura: el ancho medido se redondea a píxeles enteros.
        words.all { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width <= column * 0.97f }
    } ?: LevelStyles.last()
}

/** Ensancha [x] por cada lado, saliéndose del relleno de la hoja (`margin: 0 -8px` de la maqueta). */
private fun Modifier.bleed(x: Dp): Modifier = layout { measurable, constraints ->
    val extra = (x * 2).roundToPx()
    val wider = if (constraints.hasBoundedWidth) {
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    } else {
        constraints
    }
    val placeable = measurable.measure(wider)
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}
