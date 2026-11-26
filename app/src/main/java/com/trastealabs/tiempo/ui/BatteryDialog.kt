package com.trastealabs.tiempo.ui

import com.trastealabs.tiempo.ui.theme.OnSheet
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.ui.glass.GlassButton
import com.trastealabs.tiempo.ui.theme.CristalColors
import androidx.compose.ui.Modifier
import com.trastealabs.tiempo.ui.glass.WhiteButton

/**
 * Consejo de batería, una sola vez tras la primera carga: ColorOS corta el segundo plano y el widget
 * se congelaría (H11 de la 001). Pide la ubicación "todo el tiempo" (H7) y la actividad en segundo plano.
 * Un `AlertDialog` de M3 tal cual, opaco (012 P4, D24): sin cristal ni fundido propio.
 */
@Composable
fun BatteryDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CristalColors.DialogSurface,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        shape = RoundedCornerShape(30.dp),
        title = { Text(stringResource(R.string.battery_title), style = CristalType.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.battery_note), style = CristalType.bodyMedium)
                Step(1, stringResource(R.string.battery_step1_title), stringResource(R.string.battery_step1_body))
                Step(2, stringResource(R.string.battery_step2_title), stringResource(R.string.battery_step2_body))
            }
        },
        confirmButton = { WhiteButton(stringResource(R.string.battery_open_settings), onOpenSettings) },
        dismissButton = { GlassButton(stringResource(R.string.battery_not_now), onDismiss) },
    )
}

@Composable
private fun Step(number: Int, title: String, body: String) {
    Row {
        Text("$number.", style = CristalType.titleSmall, modifier = Modifier.width(22.dp))
        Column {
            Text(title, style = CristalType.titleSmall)
            Text(body, color = OnSheet.Secondary, style = CristalType.bodyMedium)
        }
    }
}
