package com.trastealabs.tiempo.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.MoonInfo
import com.trastealabs.tiempo.domain.SunInfo
import com.trastealabs.tiempo.domain.moonIlluminatedPct
import com.trastealabs.tiempo.domain.moonInfo
import com.trastealabs.tiempo.domain.isSouth
import com.trastealabs.tiempo.domain.moonPhaseName
import com.trastealabs.tiempo.domain.WeatherSnapshot
import com.trastealabs.tiempo.domain.airNow
import com.trastealabs.tiempo.domain.pollenToday
import com.trastealabs.tiempo.domain.humidityInfo
import com.trastealabs.tiempo.domain.sunInfo
import com.trastealabs.tiempo.domain.windInfo
import com.trastealabs.tiempo.ui.glass.translucent
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.theme.Tabular
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val TileShape = RoundedCornerShape(22.dp)
private val TileTitle = CristalType.titleSmall
private val TileValue = CristalType.headlineSmall.merge(Tabular)
internal val TileDetail = CristalType.bodySmall

/**
 * Fichas bajo los 10 días (H10–H12): viento y humedad a media anchura y sol a todo el ancho, con el
 * arco. La que no tiene su dato principal no sale; con un snapshot de antes de la 002, ninguna.
 * Viento y humedad se tocan y suben su hoja de 24 h (H6 de la 004) si [onWindClick] / [onHumidityClick] no son
 * null; quien llama los pasa a null si la primera de las 24 horas no trae ese dato (datos de antes). Debajo, las del aire y el polen (005), que suben la hoja de los próximos
 * días con [onAirClick] / [onPollenClick] (null: no se tocan).
 */
@Composable
fun WeatherCards(
    snapshot: WeatherSnapshot,
    now: Long,
    onWindClick: (() -> Unit)? = null,
    onHumidityClick: (() -> Unit)? = null,
    onAirClick: (() -> Unit)? = null,
    onPollenClick: (() -> Unit)? = null,
) {
    val wind = windInfo(snapshot.current)
    val humidity = humidityInfo(snapshot.current)
    val sun = sunInfo(snapshot, now)
    if (wind != null || humidity != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Rejilla de dos columnas, como la maqueta: si falta una, la otra se queda en su mitad.
            val half = Modifier.weight(1f)
            if (wind != null) {
                Tile(R.drawable.ic_wx_wind, stringResource(R.string.card_wind), wind.speed, wind.detail, half.testTag("wind_card"), onWindClick, seed = 3)
            }
            if (humidity != null) {
                Tile(R.drawable.ic_wx_humidity, stringResource(R.string.card_humidity), humidity.value, humidity.detail, half.testTag("humidity_card"), onHumidityClick, seed = 4)
            }
            if (wind == null || humidity == null) Spacer(half)
        }
    }
    // De noche, la luna en lugar del sol (H7); con datos de antes no hay luna y sigue la del sol.
    val moon = moonInfo(snapshot, now)
    when {
        moon != null -> MoonTile(moon, south = isSouth(snapshot))
        sun != null -> SunTile(sun)
    }
    // 005: debajo, el aire (si hay cifra para esta hora) y el polen (si hoy llega a «bajo»). Recorren las horas
    // del aire varias veces: solo cuando cambia el snapshot o `now`.
    val air = remember(snapshot, now) { airNow(snapshot, now) }
    val pollen = remember(snapshot, now) { pollenToday(snapshot, now) }
    // Con lluvia, el agua encima de cada ficha (P2, H8b); fuera de la principal, nada.
    if (air != null) AirTile(air, onAirClick, Modifier.rainOnGlass(6, TileShape))
    if (pollen != null) PollenTile(pollen, onPollenClick, Modifier.rainOnGlass(7, TileShape))
}

@Composable
private fun Tile(@DrawableRes icon: Int, title: String, value: String, detail: String?, modifier: Modifier, onClick: (() -> Unit)?, seed: Int) {
    // La ficha entera es el botón: TalkBack la lee junta y añade «Abre las próximas 24 horas».
    val press = remember { MutableInteractionSource() }
    val click = if (onClick != null) {
        Modifier.clip(TileShape).clickable(interactionSource = press, role = Role.Button, onClickLabel = stringResource(R.string.open_next_24), onClick = onClick)
    } else {
        Modifier
    }
    Column(modifier.translucent(TileShape).rainOnGlass(seed, TileShape).then(click).padding(horizontal = 14.dp, vertical = 12.dp)) {
        TileTitle(icon, title)
        Text(text = value, color = Color.White, style = sceneText(TileValue), modifier = Modifier.padding(top = 6.dp))
        if (detail != null) {
            Text(
                text = detail,
                color = OnSky.Secondary,
                style = sceneText(TileDetail),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun TileTitle(@DrawableRes icon: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Image(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            colorFilter = ColorFilter.tint(OnSky.Secondary),
        )
        Text(text = title, color = OnSky.Secondary, style = sceneText(TileTitle))
    }
}

@Composable
private fun SunTile(sun: SunInfo) {
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("sun_card")
            .translucent(TileShape)
            .rainOnGlass(5, TileShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SunArc(sun.sunFraction)
        Column {
            TileTitle(R.drawable.ic_wx_twilight, stringResource(R.string.card_sun))
            Text(
                text = sun.range,
                color = Color.White,
                style = sceneText(CristalType.titleLarge.merge(Tabular)),
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = sun.daylight,
                color = OnSky.Secondary,
                style = sceneText(TileDetail),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MoonTile(moon: MoonInfo, south: Boolean) {
    val spoken = stringResource(
        R.string.moon_card_description, moonPhaseName(moon.phase).label, moonIlluminatedPct(moon.phase), moon.events,
    ).trimEnd()
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("moon_card")
            .clearAndSetSemantics { contentDescription = spoken }
            .translucent(TileShape)
            .rainOnGlass(5, TileShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkyArc(moon.fraction, MoonDone) { center ->
            // La luna de 22 dp (11 de radio en la caja de 100): sin fracción, en el extremo izquierdo del horizonte.
            val unit = size.width / 100f
            val side = 22f * unit
            translate(center.x - side / 2, center.y - side / 2) {
                scale(side / 24f, pivot = Offset.Zero) { drawMoon(moon.phase, south) }
            }
        }
        Column {
            TileTitle(R.drawable.ic_wx_clear_night, stringResource(R.string.card_moon))
            Text(
                text = moon.label,
                color = Color.White,
                style = sceneText(CristalType.titleLarge.merge(Tabular)),
                modifier = Modifier.padding(top = 6.dp),
            )
            if (moon.events.isNotEmpty()) {
                Text(
                    text = moon.events,
                    color = OnSky.Secondary,
                    style = sceneText(TileDetail),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private val ArcLine = Color.White.copy(alpha = 0.35f)
private val ArcDone = Color(0xFFFFD27A)
private val MoonDone = Color(0xFFCDD2FF)
private val SunDot = Color(0xFFFFE356)

/** Arco del sol: en dorado hasta la hora del dato y el sol en ella; de noche ([fraction] `null`), el arco sin sol. */
@Composable
private fun SunArc(fraction: Float?) {
    if (fraction == null) {
        SkyArc(null, ArcDone, body = null)
    } else {
        SkyArc(fraction, ArcDone) { center -> drawCircle(SunDot, radius = 5f * size.width / 100f, center = center) }
    }
}

/**
 * Arco del día (`sunArc` de la maqueta, 100×54 con el centro en (50, 46) y radio 38): punteado entero, en [done]
 * hasta [fraction] y [body] dibujado en ese punto. Sin [fraction], [body] va en el extremo izquierdo del horizonte.
 */
@Composable
private fun SkyArc(fraction: Float?, done: Color, body: (DrawScope.(Offset) -> Unit)?) {
    Canvas(Modifier.size(100.dp, 54.dp)) {
        val unit = size.width / 100f
        val center = Offset(50f * unit, 46f * unit)
        val radius = 38f * unit
        val box = Size(radius * 2, radius * 2)
        val topLeft = Offset(center.x - radius, center.y - radius)
        drawArc(
            ArcLine, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = topLeft, size = box,
            style = Stroke(2f * unit, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * unit, 4f * unit))),
        )
        drawLine(ArcLine, Offset(4f * unit, center.y), Offset(96f * unit, center.y), strokeWidth = 1f * unit)
        if (fraction != null) {
            val k = fraction.coerceIn(0f, 1f)
            drawArc(
                done, startAngle = 180f, sweepAngle = 180f * k, useCenter = false, topLeft = topLeft, size = box,
                style = Stroke(2.5f * unit, cap = StrokeCap.Round),
            )
            val angle = PI * (1 - k)
            body?.invoke(this, Offset(center.x + radius * cos(angle).toFloat(), center.y - radius * sin(angle).toFloat()))
        } else {
            body?.invoke(this, Offset(12f * unit, center.y))
        }
    }
}
