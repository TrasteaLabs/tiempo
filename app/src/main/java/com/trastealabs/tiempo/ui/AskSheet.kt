package com.trastealabs.tiempo.ui

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.data.ai.AI_DAILY_ASK
import com.trastealabs.tiempo.data.ai.ASK_MAX_CHARS
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.domain.AskAnswer
import com.trastealabs.tiempo.domain.AskFailure
import com.trastealabs.tiempo.domain.AskKind
import com.trastealabs.tiempo.ui.scene.LocalBackdrop
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.OnSheet
import com.trastealabs.tiempo.ui.theme.CristalColors
import com.trastealabs.tiempo.ui.theme.CristalType
import com.trastealabs.tiempo.ui.theme.Tabular
import com.trastealabs.tiempo.ui.theme.WhiteInk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
private val Amber = Color(0xFFF4C20D)
private val Ink: Color
    @Composable get() = WhiteInk

/** H4: con datos de más de 3 h, la respuesta dice de cuándo son. */
private const val ASK_STALE_S = 3 * 3600L

/**
 * La hoja de la pregunta (018 P4): un `ModalBottomSheet` opaco como el resto, sin estado a medias. Se cierra con el dedo,
 * el asa, el velo o atrás; al cerrarse se pierden la pregunta y la respuesta (H7), que solo viven en memoria.
 *
 * @param place el nombre del sitio que se ve («Villa Ejemplo», «Sevilla»).
 * @param dataAt de cuándo es la previsión guardada (`snapshot.fetchedAt`), para la cabecera.
 * @param lastDay el último día de la previsión.
 * @param zone la zona del sitio: las horas se leen en la suya.
 * @param now el «ahora» de la pantalla; la hora de la respuesta es la suya propia (`AskAnswer.at`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskSheet(
    place: String,
    provider: AiProvider,
    state: AskState,
    dataAt: Long,
    lastDay: LocalDate,
    @Suppress("UNUSED_PARAMETER") now: Long,
    zone: ZoneId,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // La pregunta escrita vive aquí, en memoria: «Cancelar» y «Cambiar la pregunta» la dejan puesta.
    var text by remember { mutableStateOf("") }
    var cut by remember { mutableStateOf(false) }
    val providerName = providerName(provider)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("ask_sheet"),
        containerColor = CristalColors.SheetSurface,
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OnSky.Control) },
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(shadow = null),
            LocalBackdrop provides null,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 22.dp, end = 22.dp, bottom = 22.dp),
            ) {
                Text(
                    text = stringResource(R.string.ask_title, place),
                    style = CristalType.titleLarge,
                    modifier = Modifier.testTag("ask_title"),
                )
                Text(
                    text = stringResource(R.string.ask_subtitle, clock(dataAt, zone), dayLabel(lastDay)),
                    color = OnSheet.Tertiary,
                    style = CristalType.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp).testTag("ask_subtitle"),
                )
                when (state) {
                    AskState.Writing -> Writing(
                        text = text,
                        cut = cut,
                        providerName = providerName,
                        onChange = { new ->
                            cut = new.length > ASK_MAX_CHARS
                            text = new.take(ASK_MAX_CHARS)
                        },
                        onSend = { onSend(text.trim()) },
                    )
                    is AskState.Asking -> Asking(state.question, place, onCancel)
                    is AskState.Answered -> Answered(
                        question = state.question,
                        answer = state.answer,
                        place = place,
                        lastDay = lastDay,
                        zone = zone,
                        onAgain = {
                            text = ""
                            cut = false
                            onCancel()
                        },
                    )
                    is AskState.Failed -> Failed(
                        question = state.question,
                        failure = state.failure,
                        providerName = providerName,
                        onRetry = { onSend(state.question) },
                        onChange = onCancel,
                    )
                }
            }
        }
    }
}

@Composable
private fun providerName(provider: AiProvider): String = stringResource(
    when (provider) {
        AiProvider.CLAUDE -> R.string.ai_provider_claude
        AiProvider.GEMINI -> R.string.ai_provider_gemini
        AiProvider.OPENAI -> R.string.ai_provider_openai
    },
)

private fun clock(epochSeconds: Long, zone: ZoneId): String = TIME_FORMAT.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

private fun dayLabel(day: LocalDate): String = "${day.dayOfMonth} ${MONTHS[day.monthValue - 1]}."

/** Escribiendo: el campo con su botón de enviar y, debajo, a dónde va lo escrito y el contador. */
@Composable
private fun Writing(text: String, cut: Boolean, providerName: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    val canSend = text.isNotBlank()
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val fieldShape = RoundedCornerShape(24.dp)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val label = stringResource(R.string.ask_field_label)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp, max = 120.dp)
                .clip(fieldShape)
                .border(1.dp, if (focused) OnSheet.Secondary else Color.White.copy(alpha = 0.22f), fieldShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val style = CristalType.bodyLarge.copy(fontSize = 16.sp, color = Color.White)
            if (text.isEmpty()) {
                Text(stringResource(R.string.ask_hint), color = OnSheet.Hint, style = style)
            }
            BasicTextField(
                value = text,
                onValueChange = onChange,
                textStyle = style,
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ask_field")
                    .semantics { contentDescription = label }
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        FilledIconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier.minimumInteractiveComponentSize().size(40.dp).testTag("ask_send"),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White,
                contentColor = Ink,
                disabledContainerColor = Color.White.copy(alpha = 0.18f),
                disabledContentColor = OnSky.Control,
            ),
        ) {
            Icon(painterResource(R.drawable.ic_wx_send), contentDescription = stringResource(R.string.ask_send), modifier = Modifier.size(20.dp))
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = if (cut) stringResource(R.string.ask_cut, ASK_MAX_CHARS) else stringResource(R.string.ask_sent_to, providerName),
            color = if (cut) Amber else OnSheet.Tertiary,
            style = CristalType.bodySmall,
            modifier = Modifier.weight(1f).testTag("ask_info"),
        )
        Text(
            text = stringResource(R.string.ask_counter, text.length, ASK_MAX_CHARS),
            color = OnSheet.Tertiary,
            style = CristalType.bodySmall.merge(Tabular),
            modifier = Modifier.padding(start = 12.dp).testTag("ask_counter"),
        )
    }
}

/** La pregunta, arriba, con su icono: en los estados que no son escribir. */
@Composable
private fun QuestionRow(question: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(
            painterResource(R.drawable.ic_wx_chat),
            contentDescription = null,
            tint = OnSheet.Tertiary,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Text(question, color = OnSheet.Tertiary, style = CristalType.bodyLarge, modifier = Modifier.testTag("ask_question"))
    }
}

/** Contestando: el indicador, tres barras del tamaño de la respuesta y «Cancelar». */
@Composable
private fun Asking(question: String, place: String, onCancel: () -> Unit) {
    QuestionRow(question)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        Text(
            stringResource(R.string.ask_working, place),
            color = OnSheet.Text,
            style = CristalType.bodyMedium,
            modifier = Modifier.testTag("ask_working"),
        )
    }
    Column(Modifier.padding(top = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.92f, 0.84f, 0.46f).forEach { w ->
            Box(Modifier.fillMaxWidth(w).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.14f)))
        }
    }
    FilledTonalButton(
        onClick = onCancel,
        modifier = Modifier.testTag("ask_cancel"),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White),
    ) {
        Text(stringResource(R.string.ask_cancel), style = CristalType.labelLarge)
    }
}

/** La respuesta (o «solo sé de…») con su hora y, si el dato es viejo, de cuándo son los datos. */
@Composable
private fun Answered(question: String, answer: AskAnswer, place: String, lastDay: LocalDate, zone: ZoneId, onAgain: () -> Unit) {
    QuestionRow(question)
    val body = if (answer.kind == AskKind.ANSWER) {
        answer.text
    } else {
        stringResource(R.string.ask_out_of_scope, place, dayLabel(lastDay))
    }
    Text(
        text = body,
        color = OnSheet.Text,
        style = CristalType.bodyLarge,
        modifier = Modifier.fillMaxWidth().testTag("ask_answer"),
    )
    Row(Modifier.padding(top = 10.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.ask_answer_label, clock(answer.at, zone)),
            color = OnSheet.Tertiary,
            style = CristalType.bodySmall,
            modifier = Modifier.testTag("ask_answer_label"),
        )
        if (answer.at - answer.dataAt > ASK_STALE_S) {
            Text(" · ", color = OnSheet.Tertiary, style = CristalType.bodySmall)
            Icon(painterResource(R.drawable.ic_wx_schedule), contentDescription = null, tint = OnSheet.Tertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(3.dp))
            Text(
                stringResource(R.string.ask_stale, clock(answer.dataAt, zone)),
                color = OnSheet.Tertiary,
                style = CristalType.bodySmall,
                modifier = Modifier.testTag("ask_stale"),
            )
        }
    }
    FilledTonalButton(
        onClick = onAgain,
        modifier = Modifier.testTag("ask_again"),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White),
    ) {
        Icon(painterResource(R.drawable.ic_wx_edit), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.ask_again), style = CristalType.labelLarge)
    }
}

/** El fallo con su causa en llano; «Reintentar» (salvo con el tope o sin clave, que no se arreglan reintentando) y «Cambiar la pregunta». */
@Composable
private fun Failed(question: String, failure: AskFailure, providerName: String, onRetry: () -> Unit, onChange: () -> Unit) {
    QuestionRow(question)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(painterResource(R.drawable.ic_wx_error), contentDescription = null, tint = Amber, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Text(
            text = when (failure) {
                AskFailure.UNSURE -> stringResource(R.string.ask_fail_unsure)
                AskFailure.OFFLINE -> stringResource(R.string.ask_fail_offline)
                AskFailure.BAD_KEY -> stringResource(R.string.ask_fail_bad_key, providerName)
                AskFailure.NO_CREDIT -> stringResource(R.string.ask_fail_no_credit, providerName)
                AskFailure.TIMEOUT -> stringResource(R.string.ask_fail_timeout, providerName)
                AskFailure.CUT -> stringResource(R.string.ask_fail_cut)
                AskFailure.OTHER -> stringResource(R.string.ask_fail_other, providerName)
                AskFailure.CAP -> stringResource(R.string.ask_fail_cap, AI_DAILY_ASK)
                AskFailure.NO_KEY -> stringResource(R.string.ask_fail_no_key, providerName)
            },
            color = Color.White,
            style = CristalType.bodyLarge,
            modifier = Modifier.testTag("ask_failure"),
        )
    }
    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (failure != AskFailure.CAP && failure != AskFailure.NO_KEY) {
            Button(
                onClick = onRetry,
                modifier = Modifier.testTag("ask_retry"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
            ) {
                Icon(painterResource(R.drawable.ic_wx_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.ask_retry), style = CristalType.labelLarge)
            }
        }
        TextButton(
            onClick = onChange,
            modifier = Modifier.testTag("ask_change"),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) {
            Text(stringResource(R.string.ask_change), style = CristalType.labelLarge)
        }
    }
}
