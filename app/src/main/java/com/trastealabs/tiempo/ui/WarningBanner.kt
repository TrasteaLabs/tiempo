package com.trastealabs.tiempo.ui

import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.WarningLevel
import com.trastealabs.tiempo.domain.warningTitle
import com.trastealabs.tiempo.domain.warningWhenText
import java.time.ZoneId

/** Fondo y texto de cada nivel (diseño del 002): texto `#2A1804` sobre los tres, como en el widget. */
fun warningColors(level: WarningLevel): Pair<Color, Color> = when (level) {
    WarningLevel.YELLOW -> Color(0xFFF4C20D) to WarningInk
    WarningLevel.ORANGE -> Color(0xFFF28C28) to WarningInk
    WarningLevel.RED -> Color(0xFFD93025) to WarningInk
}

private val WarningInk = Color(0xFF2A1804)

/** Nivel del aviso en la semántica de la franja: así los tests comprueban el color sin pintar. */
val WarningLevelKey = SemanticsPropertyKey<WarningLevel>("WarningLevel")
private var SemanticsPropertyReceiver.warningLevel by WarningLevelKey

/**
 * Una franja del Resumen (009, H12): «⚠ Aviso naranja · tormentas / hoy 16:00–22:00 · Llanura de Ejemplo ›», del color
 * de su nivel; al tocarla, su hoja. Como `.warn` de la maqueta D: el icono en un círculo de tinta, el texto entero (sin
 * cortar) y, en rojo, en blanco. Sin sombra de texto.
 */
@Composable
fun WarningBanner(warning: Warning, now: Long, zone: ZoneId, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (background, dark) = warningColors(warning.level)
    val ink = if (warning.level == WarningLevel.RED) Color.White else dark
    val shape = RoundedCornerShape(18.dp)
    val press = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickable(interactionSource = press, role = Role.Button, onClickLabel = stringResource(R.string.open_warning), onClick = onClick)
            .semantics { warningLevel = warning.level }
            .testTag("warning_banner")
            .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(dark.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Image(
                painterResource(R.drawable.ic_wx_warning),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(ink),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = warningTitle(warning),
                color = ink,
                style = CristalType.titleSmall,
            )
            Text(
                text = "${warningWhenText(warning, now, zone)} · ${warning.areaName}",
                color = ink,  // 019: al 88 % daba 3,99:1 sobre el rojo
                style = CristalType.bodySmall,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Image(
            painterResource(R.drawable.ic_wx_expand),
            contentDescription = null,
            modifier = Modifier.size(18.dp).rotate(-90f),
            colorFilter = ColorFilter.tint(ink),
        )
    }
}
