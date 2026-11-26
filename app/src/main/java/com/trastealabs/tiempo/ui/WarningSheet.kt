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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.levelName
import com.trastealabs.tiempo.domain.warningPeriodText
import com.trastealabs.tiempo.domain.warningSourceText
import java.time.ZoneId

/**
 * Texto completo del aviso (H15 de la 001, H8 del 002): un `ModalBottomSheet` de M3 (012, P5) que se baja con el dedo, el
 * asa, el velo y atrás.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarningSheet(warning: Warning, now: Long, zone: ZoneId, onDismiss: () -> Unit, plain: String? = null) {
    val (color, ink) = warningColors(warning.level)
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
                    Modifier
                        .padding(start = 22.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(color)
                        .padding(start = 9.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Image(
                        painterResource(R.drawable.ic_wx_warning),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(ink),
                    )
                    Text(text = "Aviso ${levelName(warning.level)}", color = ink, style = CristalType.labelMedium)
                }
                Column(
                    modifier = Modifier
                        .testTag("warning_sheet")
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, bottom = 22.dp),
                ) {
                    Text(
                        text = warning.phenomenon.replaceFirstChar { it.uppercase() },
                        style = CristalType.headlineSmall,
                        modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
                    )
                    // 016 (H3): la explicación de la IA va encima y el texto oficial sigue debajo, entero.
                    if (plain != null) Field(stringResource(R.string.warning_plain), plain, Modifier.testTag("warning_plain"))
                    Field(stringResource(R.string.warning_area), warning.areaName)
                    Field(stringResource(R.string.warning_period), warningPeriodText(warning, zone))
                    warning.probability?.let { Field(stringResource(R.string.warning_probability), it) }
                    if (warning.description.isNotBlank()) Field(stringResource(R.string.warning_comment), warning.description)
                    warning.instruction?.let { Field(stringResource(R.string.warning_instruction), it) }
                    // El texto de la 001 lleva «Fuente: » delante; aquí la etiqueta va en su columna.
                    Field(stringResource(R.string.warning_source), warningSourceText(warning, now, zone).removePrefix("Fuente: "))
                }
            }
        }
    }
}

/** Par etiqueta / valor (`.kv` de la maqueta), con una raya fina encima. */
@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier) {
    HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.12f))
    Row(modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = label, color = OnSheet.Tertiary, style = CristalType.bodyMedium, modifier = Modifier.width(96.dp))
        Text(text = value, color = Color.White, style = CristalType.bodyMedium)
    }
}
