package com.trastealabs.tiempo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.data.MorningMark
import com.trastealabs.tiempo.data.RefreshLogEntry
import com.trastealabs.tiempo.domain.DEFAULT_MORNING_MINUTE
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.glass.translucent
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.OnScreen
import com.trastealabs.tiempo.ui.theme.CristalType
import com.trastealabs.tiempo.ui.theme.cristalSwitchColors
import com.trastealabs.tiempo.ui.theme.Tabular
import java.time.Instant
import java.time.ZoneId

/**
 * Lo que enseña «Parte de la mañana» en Ajustes (018 P2). [denied]: está encendida pero el sistema no deja avisar
 * (permiso negado o quitado en los ajustes del móvil). [last] y [lastAt]: el último aviso enviado, del registro.
 */
data class MorningPanel(
    val enabled: Boolean = false,
    val minuteOfDay: Int = DEFAULT_MORNING_MINUTE,
    val denied: Boolean = false,
    val last: MorningMark? = null,
    val lastAt: Long? = null,
)

/**
 * El panel a partir de los ajustes, del permiso del sistema y del registro: encendida sin permiso es [MorningPanel.denied];
 * «la última vez» es la línea de aviso más reciente (la que lleva [RefreshLogEntry.morning]), no un refresco.
 */
fun morningPanelOf(enabled: Boolean, minuteOfDay: Int, allowed: Boolean, entries: List<RefreshLogEntry>): MorningPanel {
    val last = entries.lastOrNull { it.morning != null }
    return MorningPanel(enabled, minuteOfDay, denied = enabled && !allowed, last = last?.morning, lastAt = last?.at)
}

/** El permiso de notificaciones: se mira y se pide desde la sección; los tests ponen el suyo. */
interface NotificationPermission {
    fun granted(): Boolean

    /** Pide el permiso; [onResult] con lo que respondió el usuario (o el sistema, si ya no deja preguntar). */
    fun request(onResult: (Boolean) -> Unit)
}

@Composable
fun rememberNotificationPermission(): NotificationPermission {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        pending[0]?.invoke(ok)
        pending[0] = null
    }
    return remember(context, launcher) {
        object : NotificationPermission {
            // Antes de Android 13 no hay permiso que pedir: las notificaciones están permitidas.
            override fun granted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

            override fun request(onResult: (Boolean) -> Unit) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return onResult(true)
                pending[0] = onResult
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private val Warn = Color(0xFFF4C20D)

/** «7:30» (24 h, sin cero delante en la hora). */
internal fun morningClock(minuteOfDay: Int): String = "%d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

/**
 * La tarjeta de «Parte de la mañana» (018 P2, H13): interruptor, hora y, encendida, la última vez que avisó. Encender pide
 * el permiso; si se niega, el interruptor queda apagado y sale cómo darlo. Translúcida, sin cristal en vivo.
 */
@Composable
fun MorningSection(
    state: MorningPanel,
    now: Long,
    zone: ZoneId,
    onToggle: (Boolean) -> Unit,
    onTime: (Int) -> Unit,
    permission: NotificationPermission = rememberNotificationPermission(),
) {
    var deniedHere by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val on = state.enabled && !state.denied
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("morning_card")
            .translucent(RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("morning_switch")
                .toggleable(value = on, role = Role.Switch) { want ->
                    when {
                        !want -> {
                            deniedHere = false
                            onToggle(false)
                        }
                        permission.granted() -> {
                            deniedHere = false
                            onToggle(true)
                        }
                        else -> permission.request { ok ->
                            deniedHere = !ok
                            // Encendida en los ajustes pero sin permiso: si no lo da, se apaga y no queda un estado oculto.
                            if (ok) onToggle(true) else if (state.enabled) onToggle(false)
                        }
                    }
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.morning_switch), color = Color.White, style = sceneText(CristalType.titleSmall))
                Text(
                    stringResource(R.string.morning_switch_hint),
                    color = OnScreen.Secondary,
                    style = sceneText(CristalType.bodyMedium),
                )
            }
            Switch(
                checked = on,
                onCheckedChange = null,
                thumbContent = if (on) {
                    { Icon(painterResource(R.drawable.ic_wx_check), null, Modifier.size(16.dp)) }
                } else {
                    null
                },
                colors = cristalSwitchColors(),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("morning_time_row")
                .clickable(enabled = on, role = Role.Button) { picking = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Apagada, la fila se lee en secundario (019: sin alfa sobre el texto).
            Text(
                stringResource(R.string.morning_time),
                color = if (on) OnScreen.Text else OnScreen.Secondary,
                style = sceneText(CristalType.titleSmall),
                modifier = Modifier.weight(1f),
            )
            Text(
                morningClock(state.minuteOfDay),
                color = if (on) OnScreen.Text else OnScreen.Secondary,
                style = sceneText(CristalType.titleLarge.merge(Tabular)),
                modifier = Modifier.testTag("morning_time_value"),
            )
        }
        if (on && state.lastAt != null && state.last != null) {
            Text(
                lastText(state.lastAt, state.last, now, zone),
                color = OnScreen.Secondary,
                style = sceneText(CristalType.bodySmall),
                modifier = Modifier.testTag("morning_last").padding(bottom = 8.dp),
            )
        }
        if (deniedHere || state.denied) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("morning_denied")
                    .padding(vertical = 8.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(painterResource(R.drawable.ic_wx_warning), null, Modifier.size(20.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Warn))
                Text(stringResource(R.string.morning_denied), color = Color.White, style = sceneText(CristalType.bodyMedium))
            }
        }
    }
    if (picking) {
        TimeDialog(
            minuteOfDay = state.minuteOfDay,
            onDismiss = { picking = false },
            onConfirm = {
                picking = false
                onTime(it)
            },
        )
    }
}

@Composable
private fun lastText(at: Long, mark: MorningMark, now: Long, zone: ZoneId): String {
    val time = Instant.ofEpochSecond(at).atZone(zone)
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    val day = when (time.toLocalDate()) {
        today -> stringResource(R.string.morning_today)
        today.minusDays(1) -> stringResource(R.string.morning_yesterday)
        else -> "%02d/%02d".format(time.dayOfMonth, time.monthValue)
    }
    val how = stringResource(if (mark.withAi) R.string.morning_last_ai else R.string.morning_last_rules)
    return stringResource(R.string.morning_last, day, morningClock(time.hour * 60 + time.minute), how)
}

/** El `TimePicker` de M3 en 24 h, en su diálogo, con «Dejar como está» y «Poner hora» (020: botones con verbo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minuteOfDay: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val picker = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("morning_time_dialog"),
        text = { TimePicker(picker) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.morning_time_cancel)) } },
        confirmButton = {
            TextButton(onClick = { onConfirm(picker.hour * 60 + picker.minute) }) { Text(stringResource(R.string.morning_time_ok)) }
        },
    )
}
