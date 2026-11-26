package com.trastealabs.tiempo.ui

import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.trastealabs.tiempo.domain.SummaryLine
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.ui.glass.translucent
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.theme.summaryIcon
import java.time.ZoneId

/** Títulos de las tarjetas de la principal (009, H11): sin mayúsculas espaciadas, `titleMedium`, como `.ct` de la maqueta D. */
internal val SectionTitle = CristalType.titleMedium

/**
 * El Resumen de la principal (009, H12), en cristal: una franja por aviso ([warnings], ya ordenados, el más grave
 * arriba), que se toca para abrir su hoja ([onWarningClick]); sin conexión, debajo, de cuándo son ([savedLabel]); y las
 * frases de lo que viene ([lines], como mucho tres), cada una con su icono. Con lluvia, el agua encima, como en las
 * demás tarjetas. Con un parte de la IA ([report], 016) sale en lugar de las frases, con su marca ([reportLabel]).
 * Con clave, al pie va la píldora de la pregunta ([ask], 018 P4).
 */
@Composable
fun SummaryCard(
    warnings: List<Warning>,
    lines: List<SummaryLine>,
    now: Long,
    zone: ZoneId,
    onWarningClick: (Warning) -> Unit,
    modifier: Modifier = Modifier,
    savedLabel: String? = null,
    report: String? = null,
    reportLabel: String? = null,
    /** 018 P4: la píldora de la pregunta al pie de la tarjeta; `Hidden` (sin clave) no pinta nada. */
    ask: AskEntry = AskEntry.Hidden,
    onAsk: () -> Unit = {},
    /** El nombre del sitio que sale en la píldora. */
    askPlace: String = "",
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .testTag("summary_card")
            .fillMaxWidth()
            .translucent(shape)
            .rainOnGlass(8, shape)
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.summary_title),
            color = Color.White,
            style = sceneText(SectionTitle),
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 6.dp),
        )
        if (warnings.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 8.dp).padding(top = 2.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                warnings.forEach { warning -> WarningBanner(warning, now, zone, onClick = { onWarningClick(warning) }) }
            }
            if (savedLabel != null) {
                Text(
                    text = savedLabel,
                    color = OnSky.Secondary,
                    style = sceneText(CristalType.bodySmall),
                    modifier = Modifier.padding(horizontal = 18.dp).padding(top = 4.dp),
                )
            }
            if (lines.isNotEmpty()) Spacer(Modifier.height(4.dp))
        }
        if (report != null) {
            Text(
                text = report,
                color = Color.White,
                style = sceneText(CristalType.bodyMedium),
                modifier = Modifier.testTag("summary_report").fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp),
            )
            if (reportLabel != null) {
                Text(
                    text = reportLabel,
                    color = OnSky.Tertiary,
                    style = sceneText(CristalType.bodySmall),
                    modifier = Modifier.testTag("summary_report_label").padding(horizontal = 18.dp).padding(bottom = 2.dp),
                )
            }
        }
        if (report == null) lines.forEach { line ->
            Row(
                Modifier.fillMaxWidth().testTag("summary_line").padding(horizontal = 18.dp, vertical = 7.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painterResource(summaryIcon(line.icon)),
                    contentDescription = null,
                    modifier = Modifier.padding(top = 1.dp).size(18.dp),
                    colorFilter = ColorFilter.tint(Color.White),
                )
                Text(text = line.text, color = Color.White, style = sceneText(CristalType.bodyMedium))
            }
        }
        if (ask != AskEntry.Hidden) {
            AskPill(ask, askPlace, onAsk, Modifier.padding(horizontal = 8.dp).padding(top = 8.dp))
        }
    }
}
