package com.trastealabs.tiempo.ui

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextOverflow
import com.trastealabs.tiempo.ui.theme.CristalRippleInk
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.WhiteInk
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.data.ai.AI_DAILY_ASK
import com.trastealabs.tiempo.data.ai.AiFailure
import com.trastealabs.tiempo.data.ai.AiStatus
import com.trastealabs.tiempo.data.ai.AiUsage
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.domain.groupedThousands
import com.trastealabs.tiempo.ui.glass.GlassButton
import com.trastealabs.tiempo.ui.glass.WhiteButton
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.glass.translucent
import com.trastealabs.tiempo.ui.theme.OnScreen
import com.trastealabs.tiempo.ui.theme.CristalType
import com.trastealabs.tiempo.ui.theme.Tabular
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lo que enseña la sección «IA» de Ajustes (016 P3). La clave nunca viaja aquí: solo [hints], sus últimos caracteres.
 * `testText` es el parte de la última prueba si salió bien; si falló, el motivo está en [status].
 */
data class AiPanel(
    val provider: AiProvider = AiProvider.CLAUDE,
    val hints: Map<AiProvider, String> = emptyMap(),
    val usage: AiUsage = AiUsage(),
    val status: AiStatus? = null,
    val testing: Boolean = false,
    val testText: String? = null,
)

/** Lo que se puede hacer en la sección. */
sealed interface AiAction {
    data class Choose(val provider: AiProvider) : AiAction

    data class SaveKey(val provider: AiProvider, val key: String) : AiAction {
        // La clave no sale en ningún texto de depuración.
        override fun toString() = "SaveKey($provider)"
    }

    data class ClearKey(val provider: AiProvider) : AiAction
    data object Test : AiAction
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val Gold = Color(0xFFFFD27A)

/** La tarjeta de «IA»: proveedor, clave (pegar o borrar), «Probar ahora» y el gasto. Translúcida, sin cristal en vivo. */
@Composable
fun AiSection(panel: AiPanel, now: Long, zone: ZoneId, onAi: (AiAction) -> Unit) {
    val hint = panel.hints[panel.provider]
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("ai_card")
            .translucent(RoundedCornerShape(28.dp))
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (hint == null) {
            Text(
                stringResource(R.string.ai_intro),
                color = OnScreen.Secondary,
                style = sceneText(CristalType.bodyMedium),
                modifier = Modifier.testTag("ai_intro"),
            )
        }
        // 021: qué sale del móvil y dónde vive la clave, siempre a la vista (con clave o sin ella).
        Text(
            stringResource(R.string.ai_privacy),
            color = OnScreen.Secondary,
            style = sceneText(CristalType.bodyMedium),
            modifier = Modifier.testTag("ai_privacy"),
        )
        Text(
            stringResource(R.string.ai_provider_label),
            color = OnScreen.Text,
            style = sceneText(CristalType.titleSmall),
        )
        ProviderPicker(panel.provider) { onAi(AiAction.Choose(it)) }
        Text(
            stringResource(R.string.ai_key_label),
            color = OnScreen.Text,
            style = sceneText(CristalType.titleSmall),
        )
        if (hint != null) {
            Text(
                stringResource(R.string.ai_key_hint_saved, hint),
                color = Color.White,
                style = sceneText(CristalType.bodyLarge.merge(Tabular)),
                modifier = Modifier.testTag("ai_key_hint"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).testTag("ai_test")) {
                    if (panel.testing) {
                        Text(
                            stringResource(R.string.ai_testing),
                            color = Color.White,
                            style = sceneText(CristalType.bodyMedium),
                            modifier = Modifier.testTag("ai_testing").padding(vertical = 12.dp),
                        )
                    } else {
                        WhiteButton(stringResource(R.string.ai_test), { onAi(AiAction.Test) })
                    }
                }
                Box(Modifier.weight(1f).testTag("ai_clear")) {
                    GlassButton(stringResource(R.string.ai_clear), { onAi(AiAction.ClearKey(panel.provider)) })
                }
            }
        } else {
            KeyField(panel.provider, onAi)
            KeyLink(panel.provider)
        }
        panel.testText?.let {
            Text(
                stringResource(R.string.ai_test_ok, it),
                color = Color.White,
                style = sceneText(CristalType.bodyMedium),
                modifier = Modifier.testTag("ai_test_result"),
            )
        }
        panel.status?.let { status ->
            val at = TIME_FORMAT.format(Instant.ofEpochSecond(status.at).atZone(zone))
            Text(
                text = status.failure?.let { stringResource(R.string.ai_last_failed, at, failureText(it)) }
                    ?: stringResource(R.string.ai_last_ok, at),
                color = OnScreen.Secondary,
                style = sceneText(CristalType.bodyMedium),
                modifier = Modifier.testTag("ai_status"),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
        val used = panel.usage.at(now, zone)
        Text(
            stringResource(R.string.ai_usage_title),
            color = OnScreen.Text,
            style = sceneText(CristalType.titleSmall),
        )
        Text(
            stringResource(
                R.string.ai_usage_today,
                groupedThousands((used.dayHere + used.daySaved).toLong()),
                pluralStringResource(R.plurals.ai_usage_asks, used.dayAsk, used.dayAsk, AI_DAILY_ASK),
            ),
            color = Color.White,
            style = sceneText(CristalType.bodyMedium.merge(Tabular)),
            modifier = Modifier.testTag("ai_usage_today"),
        )
        Text(
            stringResource(
                R.string.ai_usage_month,
                groupedThousands(used.monthCalls.toLong()),
                groupedThousands(used.monthInputTokens + used.monthOutputTokens),
            ),
            color = Color.White,
            style = sceneText(CristalType.bodyMedium.merge(Tabular)),
            modifier = Modifier.testTag("ai_usage_month"),
        )
    }
}

@Composable
private fun failureText(failure: AiFailure): String = stringResource(
    when (failure) {
        AiFailure.NO_KEY -> R.string.ai_failure_no_key
        AiFailure.BAD_KEY -> R.string.ai_failure_bad_key
        AiFailure.NO_CREDIT -> R.string.ai_failure_no_credit
        AiFailure.RATE_LIMIT -> R.string.ai_failure_rate_limit
        AiFailure.OFFLINE -> R.string.ai_failure_offline
        AiFailure.PROVIDER -> R.string.ai_failure_provider
        AiFailure.BAD_REPLY -> R.string.ai_failure_bad_reply
        AiFailure.TIMEOUT -> R.string.ai_failure_timeout
    },
)

/**
 * El proveedor (020): el botón segmentado de M3, un tercio para cada uno. El elegido en blanco con la tinta del tema, el
 * resto como los demás controles; sin la marca de elegido de M3, para que el ancho no salte.
 */
@Composable
private fun ProviderPicker(chosen: AiProvider, onChoose: (AiProvider) -> Unit) {
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = Color.White,
        activeContentColor = WhiteInk,
        activeBorderColor = Color.White,
        inactiveContainerColor = Color.White.copy(alpha = 0.16f),
        inactiveContentColor = Color.White,
        inactiveBorderColor = OnSky.Control,
    )
    val providers = AiProvider.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("ai_provider")) {
        providers.forEachIndexed { i, provider ->
            val name = stringResource(
                when (provider) {
                    AiProvider.CLAUDE -> R.string.ai_provider_claude
                    AiProvider.GEMINI -> R.string.ai_provider_gemini
                    AiProvider.OPENAI -> R.string.ai_provider_openai
                },
            )
            // Sobre el blanco del elegido, la onda es de tinta.
            val ripple = if (provider == chosen) CristalRippleInk else LocalRippleConfiguration.current
            CompositionLocalProvider(LocalRippleConfiguration provides ripple) {
                SegmentedButton(
                    selected = provider == chosen,
                    onClick = { onChoose(provider) },
                    shape = SegmentedButtonDefaults.itemShape(i, providers.size),
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("ai_provider_${provider.name}"),
                    colors = colors,
                    icon = {},
                ) {
                    // A letra muy grande en un móvil estrecho no cabe entero: puntos suspensivos, no media palabra.
                    Text(name, style = CristalType.titleSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 021: dónde saca cada proveedor sus claves de API (la página de claves de su consola). */
internal fun keyPageUrl(provider: AiProvider): String = when (provider) {
    AiProvider.CLAUDE -> "https://console.anthropic.com/settings/keys"
    AiProvider.GEMINI -> "https://aistudio.google.com/apikey"
    AiProvider.OPENAI -> "https://platform.openai.com/api-keys"
}

/** «Dónde sacar una clave de Claude», que abre la consola del proveedor en el navegador. */
@Composable
private fun KeyLink(provider: AiProvider) {
    val uri = LocalUriHandler.current
    val name = stringResource(
        when (provider) {
            AiProvider.CLAUDE -> R.string.ai_provider_claude
            AiProvider.GEMINI -> R.string.ai_provider_gemini
            AiProvider.OPENAI -> R.string.ai_provider_openai
        },
    )
    Text(
        stringResource(R.string.ai_key_where, name),
        color = Color.White,
        style = sceneText(CristalType.labelLarge),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("ai_key_where")
            .clickable(role = Role.Button) { runCatching { uri.openUri(keyPageUrl(provider)) } }
            .padding(vertical = 14.dp),
    )
}

/** El campo de contraseña y «Guardar»: con el campo vacío no hace nada. */
@Composable
private fun KeyField(provider: AiProvider, onAi: (AiAction) -> Unit) {
    // Solo en memoria: la clave escrita no va al estado guardado de la pantalla.
    var text by remember(provider) { mutableStateOf("") }
    val style = CristalType.bodyLarge.copy(color = Color.White)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val save = {
        val key = text.trim()
        if (key.isNotEmpty()) {
            // No se vacía el campo: si guardar falla, lo pegado sigue ahí. Al guardarse, aparece la pista y
            // este campo sale de pantalla (y con él el texto).
            onAi(AiAction.SaveKey(provider, key))
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isEmpty()) {
            Text(stringResource(R.string.ai_key_field), color = OnScreen.Hint, style = style)
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Gold),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            // «Hecho» hace lo mismo que «Guardar» y cierra el teclado.
            keyboardActions = KeyboardActions(onDone = {
                save()
                keyboard?.hide()
                focusManager.clearFocus()
            }),
            modifier = Modifier.fillMaxWidth().testTag("ai_key_field"),
        )
    }
    Box(Modifier.testTag("ai_save")) {
        GlassButton(stringResource(R.string.ai_save), { save() })
    }
}
