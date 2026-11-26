package com.trastealabs.tiempo.ui

import com.trastealabs.tiempo.ui.theme.AppFigures
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.ReliabilityLevel
import com.trastealabs.tiempo.domain.WeatherSnapshot
import com.trastealabs.tiempo.domain.dateTimeLabel
import com.trastealabs.tiempo.domain.otherOffset
import com.trastealabs.tiempo.domain.skyOf
import com.trastealabs.tiempo.domain.today
import com.trastealabs.tiempo.domain.yesterdayPhrase
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.Tabular
import com.trastealabs.tiempo.ui.theme.skyLabel
import java.time.ZoneId
import kotlin.math.roundToInt

private fun Double.deg(): String = roundToInt().toString()

/**
 * Cabecera sin tarjeta de la pantalla D (009, H11), centrada sobre el cielo: «Donde estoy» o «Sitio guardado» con el
 * nombre, que se toca para ir a Sitios ([onPlaceClick]); debajo, la fecha y hora del dato (o, en su lugar, [status]: la
 * píldora de sin conexión o «Actualizando…»; si el sitio tiene otro desfase que el móvil, acaba en «, hora de allí»);
 * la temperatura grande, el tiempo, «Máx · Mín · Sensación» y las píldoras de ayer y, con [todayLevel], de la
 * fiabilidad. Desde la temperatura hasta las píldoras es un solo botón que abre la página de hoy ([onPanelClick]).
 * [lastSeenTemp] y [travel] (la entrada en marcha) hacen que la temperatura cuente desde la última vista al abrir en frío.
 * El astro va en el cielo, en la esquina (`SceneBackdrop`), y el velo de lectura detrás, en [HeaderVeil].
 */
@Composable
fun Header(
    snapshot: WeatherSnapshot,
    now: Long,
    modifier: Modifier = Modifier,
    status: String? = null,
    offline: Boolean = false,
    todayLevel: ReliabilityLevel? = null,
    onPanelClick: () -> Unit = {},
    placeName: String = snapshot.place.name,
    saved: Boolean = false,
    onPlaceClick: () -> Unit = {},
    phoneZone: ZoneId = ZoneId.systemDefault(),
    lastSeenTemp: Int? = null,
    travel: IntroState? = null,
) {
    val zone = ZoneId.of(snapshot.zoneId)
    Column(
        modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeaderPlace(placeName, saved, onPlaceClick)
        if (status != null) {
            StatusBanner(status, Modifier.padding(top = 4.dp), icon = if (offline) R.drawable.ic_wx_cloud_off else null)
        } else {
            val date = dateTimeLabel(snapshot.fetchedAt, zone)
            Text(
                text = if (otherOffset(now, zone, phoneZone)) stringResource(R.string.local_time_suffix, date) else date,
                color = OnSky.Secondary,
                style = sceneText(CristalType.bodySmall.copy(textAlign = TextAlign.Center)),
            )
        }
        MainPanel(snapshot, now, todayLevel, onPanelClick, lastSeenTemp, travel)
    }
}

/** El nombre que se toca, centrado (`.place` de la maqueta D): la línea de encima sin versalitas y el nombre a 28 sp, entero. */
@Composable
private fun HeaderPlace(name: String, saved: Boolean, onClick: () -> Unit) {
    val said = stringResource(if (saved) R.string.place_button_saved else R.string.place_button_here, name)
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = press, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = said }
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Image(
                painterResource(if (saved) R.drawable.ic_wx_bookmark else R.drawable.ic_wx_location),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
            Text(
                text = stringResource(if (saved) R.string.place_saved else R.string.place_here),
                color = OnSky.Text,
                style = sceneText(CristalType.labelMedium),
            )
        }
        // La flecha va pegada a la última palabra, también si el nombre ocupa dos líneas («Sanlúcar de Barrameda»).
        Text(
            text = buildAnnotatedString {
                append(name)
                append(" ")
                appendInlineContent(CHEVRON, "⌄")
            },
            color = Color.White,
            style = sceneText(CristalType.headlineMedium.copy(textAlign = TextAlign.Center)),
            // Para TalkBack y los tests, solo el nombre (la flecha es adorno).
            modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(name) },
            inlineContent = mapOf(
                CHEVRON to InlineTextContent(Placeholder(22.sp, 22.sp, PlaceholderVerticalAlign.TextCenter)) {
                    Image(
                        painterResource(R.drawable.ic_wx_expand),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        colorFilter = ColorFilter.tint(OnSky.Text),
                    )
                },
            ),
        )
    }
}

@Composable
private fun MainPanel(
    snapshot: WeatherSnapshot,
    now: Long,
    todayLevel: ReliabilityLevel?,
    onClick: () -> Unit,
    lastSeenTemp: Int?,
    travel: IntroState?,
) {
    val day = today(snapshot, now)
    val phrase = yesterdayPhrase(snapshot, now)
    val sky = skyOf(snapshot.current.weatherCode)
    val feels = snapshot.current.apparentC.deg()
    val panelPress = remember { MutableInteractionSource() }
    Column(
        Modifier
            .testTag("main_panel")
            .clip(RoundedCornerShape(30.dp))
            // Todo el bloque es el botón (semántica unida: TalkBack lo lee junto y añade «Abre el día de hoy»).
            .clickable(interactionSource = panelPress, role = Role.Button, onClickLabel = stringResource(R.string.open_today), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Al abrir en frío cuenta desde la última cifra vista (012 P9, H12); en cualquier otro momento, la de ahora, quieta.
        TravelingTemp(
            to = snapshot.current.temperatureC.roundToInt(),
            from = lastSeenTemp,
            travel = travel,
            color = Color.White,
            // El «°» pesa a la derecha: 18 dp a la izquierda para que la cifra quede centrada a la vista (maqueta).
            modifier = Modifier.padding(start = 18.dp, top = 4.dp),
            style = sceneText(AppFigures.mainTemp).merge(Tabular),
        )
        Text(text = stringResource(skyLabel(sky)), color = Color.White, style = sceneText(CristalType.titleLarge))
        Text(
            text = if (day != null) {
                stringResource(R.string.max_min_feels, day.maxC.deg(), day.minC.deg(), feels)
            } else {
                stringResource(R.string.feels_like, feels)
            },
            color = OnSky.Text,
            style = sceneText(CristalType.bodyMedium.copy(textAlign = TextAlign.Center)).merge(Tabular),
            modifier = Modifier.padding(top = 3.dp),
        )
        if (phrase != null || todayLevel != null) {
            Row(
                Modifier.padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (phrase != null) YesterdayPill(phrase, Modifier.weight(1f, fill = false))
                if (todayLevel != null) ReliabilityPill(todayLevel)
            }
        }
    }
}

/**
 * El velo de lectura de la cabecera (009, H5): tinta `#0A1028` con la fuerza [alpha] que pide la foto
 * (`PhotoLook.headerVeil`), pareja de arriba hasta el 42 % del alto de la pantalla (toda la zona del texto) y que se
 * desvanece hasta el 56 %. Pareja y no en óvalo como `.heroveil` de la maqueta: el óvalo se apagaba en los bordes de la
 * zona y no llegaba a 4,5:1 en el OPPO (P3); `tools/build_skies.py` calcula la fuerza con este velo y con el resplandor
 * del sol encima. Va con la cabecera al desplazar ([scroll], en píxeles; solo se repinta). Con [alpha] 0 no pinta nada.
 */
@Composable
fun HeaderVeil(alpha: Float, scroll: () -> Int, modifier: Modifier = Modifier) {
    if (alpha <= 0f) return
    Box(
        modifier
            .fillMaxSize()
            .testTag("header_veil")
            .drawBehind {
                val top = -scroll().toFloat()
                val end = size.height * VEIL_FADE_END
                if (top + end <= 0f) return@drawBehind
                val brush = Brush.verticalGradient(
                    0f to VeilInk.copy(alpha = alpha),
                    VEIL_FULL / VEIL_FADE_END to VeilInk.copy(alpha = alpha),
                    1f to VeilInk.copy(alpha = 0f),
                    startY = top,
                    endY = top + end,
                )
                drawRect(brush, topLeft = Offset(0f, top), size = Size(size.width, end))
            },
    )
}

/** Hasta dónde llega el velo entero y dónde acaba de desvanecerse, en fracción del alto de la pantalla. */
private const val VEIL_FULL = 0.42f
private const val VEIL_FADE_END = 0.56f

private const val CHEVRON = "chevron"

private val VeilInk = Color(0xFF0A1028)

/** Píldora redonda con el anillo de hoy (diseño B): 32 dp. Sin clic propio: tocarla es tocar el panel (004). */
@Composable
private fun ReliabilityPill(level: ReliabilityLevel) {
    Box(
        Modifier
            .testTag("reliability_pill")
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.17f)),
        contentAlignment = Alignment.Center,
    ) {
        ReliabilityIcon(level, size = 20.dp)
    }
}

/** Píldora de ayer con su flecha: sube, baja o igual. */
@Composable
private fun YesterdayPill(phrase: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.17f))
            .padding(start = 9.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(painterResource(trendIcon(phrase)), contentDescription = null, Modifier.size(16.dp))
        Text(text = phrase, color = Color.White, style = CristalType.bodySmall)
    }
}

/** La frase la escribe `yesterdayPhrase`: «más frío», «más cálido» o «Igual». */
@DrawableRes
private fun trendIcon(phrase: String): Int = when {
    "frío" in phrase -> R.drawable.ic_wx_trend_down
    "cálido" in phrase -> R.drawable.ic_wx_trend_up
    else -> R.drawable.ic_wx_trend_flat
}
