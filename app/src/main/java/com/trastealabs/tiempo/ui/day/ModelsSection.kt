package com.trastealabs.tiempo.ui.day

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import com.trastealabs.tiempo.ui.theme.OnSheet
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.DayPoint
import com.trastealabs.tiempo.domain.DayReliability
import com.trastealabs.tiempo.domain.HourPoint
import com.trastealabs.tiempo.domain.ModelVerdict
import com.trastealabs.tiempo.domain.acronym
import com.trastealabs.tiempo.domain.country
import com.trastealabs.tiempo.domain.dayIconSky
import com.trastealabs.tiempo.domain.ModelBoard
import com.trastealabs.tiempo.domain.Sky
import com.trastealabs.tiempo.domain.dimmedModels
import com.trastealabs.tiempo.domain.modelBoard
import com.trastealabs.tiempo.domain.spoken
import com.trastealabs.tiempo.domain.skyOf
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.theme.Tabular
import com.trastealabs.tiempo.ui.theme.WeatherIcon
import com.trastealabs.tiempo.ui.theme.degrees
import com.trastealabs.tiempo.ui.theme.millimetres
import java.time.ZoneId

/** La ficha de un modelo atenuado (H9 de la 004): así los tests la ven sin mirar la opacidad. */
val ModelDimmedKey = SemanticsPropertyKey<Boolean>("ModelDimmed")
private var SemanticsPropertyReceiver.modelDimmed by ModelDimmedKey

/** Fichas por fila (rejilla de la maqueta). */
private const val COLUMNS = 3

/**
 * Lo atenuado (019, Cristal 1.4): se apaga el dibujo (icono, fondo, borde y la muestra de la leyenda), no el texto, que
 * baja como mucho al terciario y se sigue leyendo a 4,5:1.
 */
private const val DIMMED_ALPHA = 0.36f

/**
 * «Modelos», la última sección de la página del día (H9 y H10 de la 004): el marcador de tres casillas, «Lo que
 * te enseño» con la previsión de siempre ([shown]) y una ficha por modelo que llega a ese día. El que se aparta va
 * atenuado (ninguno si van divididos), con la leyenda debajo solo si hay alguno.
 */
@Composable
fun ModelsSection(day: DayReliability, shown: DayPoint, hourly: List<HourPoint>, zone: ZoneId) {
    val models = dimmedModels(day.models)
    Column(Modifier.fillMaxWidth()) {
        Scoreboard(modelBoard(day))
        ShownStrip(shown, hourly, zone)
        models.chunked(COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (verdict, dimmed) -> ModelCard(verdict, dimmed, Modifier.weight(1f)) }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (models.any { it.second }) DimmedLegend()
    }
}

private val BoardValue = CristalType.titleLarge.merge(Tabular)
private val BoardNote = CristalType.labelMedium.copy(textAlign = TextAlign.Center)

/** Lo que vale 0 en la casilla de los modelos: icono atenuado y cifra en terciario, para que la casilla no cambie de forma. */
private const val ZERO_ALPHA = 0.45f

/**
 * H10. Marcador de tres casillas (columnas 1,15 : 1 : 1, rayas finas entre ellas): modelos secos y con lluvia,
 * rango de máximas y % de los escenarios. Sin modelos, solo la de escenarios. TalkBack lo lee como frase.
 */
@Composable
private fun Scoreboard(board: ModelBoard) {
    val spoken = board.spoken()
    Row(
        Modifier
            .testTag("model_board")
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clearAndSetSemantics { contentDescription = spoken }
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.13f)),
    ) {
        if (board.models > 0) {
            BoardCell(Modifier.weight(1.15f), stringResource(R.string.board_models)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(Sky.CLEAR, isDay = true, size = 18.dp, modifier = Modifier.alpha(if (board.dryModels == 0) ZERO_ALPHA else 1f))
                    Text(board.dryModels.toString(), color = if (board.dryModels == 0) OnSheet.Tertiary else OnSheet.Text, style = sceneText(BoardValue), modifier = Modifier.padding(start = 4.dp))
                }
                Row(
                    Modifier.padding(start = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WeatherIcon(Sky.RAIN, isDay = true, size = 18.dp, modifier = Modifier.alpha(if (board.wetModels == 0) ZERO_ALPHA else 1f))
                    Text(board.wetModels.toString(), color = if (board.wetModels == 0) OnSheet.Tertiary else OnSheet.Text, style = sceneText(BoardValue), modifier = Modifier.padding(start = 4.dp))
                }
            }
            BoardDivider()
            BoardCell(Modifier.weight(1f), stringResource(R.string.board_maxima)) {
                BoardIcon(R.drawable.ic_wx_thermometer)
                val range = if (board.maxLow == board.maxHigh) "${board.maxLow}°" else "${board.maxLow}–${board.maxHigh}°"
                BoardNumber(range)
            }
            BoardDivider()
        }
        val note = if (board.scenariosDry) stringResource(R.string.board_scenarios_dry, board.scenarios)
        else stringResource(R.string.board_scenarios_wet, board.scenarios)
        BoardCell(Modifier.weight(1f), note) {
            BoardIcon(R.drawable.ic_wx_fan)
            BoardNumber("${board.scenarioPct} %")
        }
    }
}

@Composable
private fun BoardCell(modifier: Modifier, note: String, value: @Composable () -> Unit) {
    Column(
        modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { value() }
        Text(note, color = OnSheet.Secondary, style = sceneText(BoardNote))
    }
}

/** Los roles que puede llevar la cifra de la casilla, de mayor a menor (Cristal 1.2: baja de rol, no de tamaño). */
private val BoardRoles = listOf(CristalType.titleLarge, CristalType.titleMedium, CristalType.bodyMedium)

/** Cifra de la casilla en una línea: si no cabe («22–24°» en una casilla estrecha), baja de rol hasta `bodyMedium`. */
@Composable
private fun BoardNumber(text: String) {
    var role by remember(text) { mutableIntStateOf(0) }
    BasicText(
        text,
        style = sceneText(BoardRoles[role].merge(Tabular)).copy(color = Color.White),
        maxLines = 1,
        softWrap = false,
        onTextLayout = { if (it.didOverflowWidth && role < BoardRoles.lastIndex) role++ },
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun BoardIcon(icon: Int) {
    Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
}

@Composable
private fun BoardDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.14f)))
}

/** Franja ancha «Lo que te enseño»: el cielo, la máxima y la lluvia de la lista de 10 días. */
@Composable
private fun ShownStrip(shown: DayPoint, hourly: List<HourPoint>, zone: ZoneId) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WeatherIcon(dayIconSky(shown.date, hourly, zone), isDay = true, size = 26.dp)
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(R.string.reliability_shown), color = Color.White, style = CristalType.titleSmall)
            Text(text = stringResource(R.string.reliability_shown_note), color = OnSheet.Tertiary, style = CristalType.labelMedium)
        }
        Text(text = degrees(shown.maxC), color = Color.White, style = sceneText(CristalType.titleLarge.merge(Tabular)))
        Text(
            text = millimetres(shown.precipSumMm),
            color = OnSheet.Secondary,
            textAlign = TextAlign.End,
            style = sceneText(CristalType.bodySmall.merge(Tabular)),
            modifier = Modifier.width(52.dp),
        )
    }
}

/** Ficha de un modelo: nombre por país, sigla, cielo de día, máxima y lluvia. Atenuada: fondo, borde e icono al 36 % y el texto en terciario. */
@Composable
private fun ModelCard(verdict: ModelVerdict, dimmed: Boolean, modifier: Modifier = Modifier) {
    val m = verdict.day
    val shape = RoundedCornerShape(16.dp)
    val dimmedState = stringResource(R.string.model_dimmed_state)
    Column(
        modifier
            .testTag("model_card")
            .semantics(mergeDescendants = true) {
                modelDimmed = dimmed
                if (dimmed) stateDescription = dimmedState
            }
            .clip(shape)
            .background(Color.White.copy(alpha = if (dimmed) 0.09f * DIMMED_ALPHA else 0.09f))
            .border(1.5.dp, Color.White.copy(alpha = if (dimmed) 0.14f * DIMMED_ALPHA else 0.14f), shape)
            .padding(start = 11.dp, end = 11.dp, top = 10.dp, bottom = 9.dp),
    ) {
        val main = if (dimmed) OnSheet.Tertiary else OnSheet.Text
        Text(text = m.model.country, color = main, style = CristalType.titleSmall, maxLines = 1)
        Text(text = m.model.acronym, color = OnSheet.Tertiary, style = CristalType.labelMedium, maxLines = 1)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            WeatherIcon(skyOf(m.weatherCode), isDay = true, size = 26.dp, modifier = Modifier.alpha(if (dimmed) DIMMED_ALPHA else 1f))
            Text(text = degrees(m.maxC), color = main, style = sceneText(CristalType.titleLarge.merge(Tabular)))
        }
        Text(
            text = millimetres(m.precipMm),
            color = if (dimmed) OnSheet.Tertiary else OnSheet.Secondary,
            style = sceneText(CristalType.bodySmall.merge(Tabular)),
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** «Atenuado: se aparta del resto», con una muestra atenuada. */
@Composable
private fun DimmedLegend() {
    Row(
        Modifier.testTag("models_dimmed_legend").padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(16.dp, 11.dp)
                .alpha(DIMMED_ALPHA)
                .clip(RoundedCornerShape(3.dp))
                .background(OnSheet.Tertiary),
        )
        Text(text = stringResource(R.string.models_dimmed), color = OnSheet.Secondary, style = CristalType.bodySmall)
    }
}
