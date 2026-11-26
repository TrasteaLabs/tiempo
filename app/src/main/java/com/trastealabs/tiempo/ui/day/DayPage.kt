package com.trastealabs.tiempo.ui.day

import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.OnSheet
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.DayHumidity
import com.trastealabs.tiempo.domain.DayPoint
import com.trastealabs.tiempo.domain.DayReliability
import com.trastealabs.tiempo.domain.DayWind
import com.trastealabs.tiempo.domain.ReliabilityLevel
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.domain.WeatherSnapshot
import com.trastealabs.tiempo.domain.clockFraction
import com.trastealabs.tiempo.domain.dayHumidity
import com.trastealabs.tiempo.domain.dayIconSky
import com.trastealabs.tiempo.domain.dayMoon
import com.trastealabs.tiempo.domain.daySummary
import com.trastealabs.tiempo.domain.daySun
import com.trastealabs.tiempo.domain.dayWind
import com.trastealabs.tiempo.domain.dstShift
import com.trastealabs.tiempo.domain.hoursOfDay
import com.trastealabs.tiempo.domain.level
import com.trastealabs.tiempo.domain.longDayLabel
import com.trastealabs.tiempo.domain.nowIndex
import com.trastealabs.tiempo.domain.relativeDayLabel
import com.trastealabs.tiempo.domain.withCurrentAt
import com.trastealabs.tiempo.ui.HourlyChart
import com.trastealabs.tiempo.ui.ReliabilityIcon
import com.trastealabs.tiempo.ui.SectionTitle
import com.trastealabs.tiempo.ui.WindArrow
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.reliabilityLabel
import com.trastealabs.tiempo.ui.scene.PhotoCache
import com.trastealabs.tiempo.ui.theme.Tabular
import com.trastealabs.tiempo.ui.theme.WeatherIcon
import com.trastealabs.tiempo.ui.theme.millimetres
import com.trastealabs.tiempo.ui.theme.skyLabel
import com.trastealabs.tiempo.ui.scene.DAY_BAND_SHADE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import kotlin.math.roundToInt

/** La franja de la foto del día (`.band` de la maqueta D). */
private val BandHeight = 196.dp

/** La foto de la franja: un 118 % de su ancho, centrada y subida un 8 % del alto de la franja (maqueta). */
private const val BAND_PHOTO_WIDTH = 1.18f
private const val BAND_PHOTO_LIFT = 0.08f

/** El oscurecido de abajo de la franja, para leer la fecha y las cifras encima. */
private val BandShade = Color(10, 16, 40)

/**
 * Alfa del oscurecido a la fracción [t] del alto de la franja (019): el de siempre (0 en el 30 %, 0,55 abajo) o, si la
 * foto lo pide ([k], `DAY_BAND_SHADE`, medido por `tools/medir_niveles.py`), [k] con una rampa suave del 5 al 40 % del
 * alto, lo que sea más. Así el texto blanco llega a 4,5:1 sobre el punto más claro de las 14 fotos.
 */
internal fun bandShadeAt(t: Float, k: Float): Float {
    val base = ((t - 0.3f) / 0.7f).coerceIn(0f, 1f) * 0.55f
    val u = ((t - 0.05f) / 0.35f).coerceIn(0f, 1f)
    return maxOf(base, k * u * u * (3f - 2f * u))
}

/** El oscurecido de la franja de [photo], con una parada cada 5 % (sin escalones a la vista). Uno por foto, hecho una vez. */
private val bandBrushes: Map<SkyPhoto, Brush> by lazy {
    SkyPhoto.entries.associateWith { photo ->
        val k = DAY_BAND_SHADE.getValue(photo)
        Brush.verticalGradient(*Array(21) { i -> (i / 20f).let { t -> t to BandShade.copy(alpha = bandShadeAt(t, k)) } })
    }
}

private fun bandShadeBrush(photo: SkyPhoto): Brush = bandBrushes.getValue(photo)

/** Las tarjetas de la ventana oscura (`.dsheet.dark .dcard`): blanco al 7 % con un borde al 8 %. */
private val CardFill = Color.White.copy(alpha = 0.07f)
private val CardBorder = Color.White.copy(alpha = 0.08f)

/** Los tramos de una página que se prepara por adelantado (P5, H9): nada, la franja, resumen y gráfica, el resto. */
private const val STAGES = 3

private val HumidityLow = Color(0xFFB5DBFF)
private val HumidityHigh = Color(0xFF6FAEF0)

/**
 * Una página de la ventana del día (009, H8 y H13; antes, la página del día de la 004), de arriba abajo: la franja con
 * la foto del día ([photo], quieta, de [photos]) y encima la fecha, la máxima, la mínima, el tiempo y la lluvia; luego
 * «Resumen del día», «Hora a hora», «Viento y humedad», «Sol y luna» y «Modelos», en tarjetas oscuras.
 * [today] es la fecha de hoy en la zona de la previsión; [reliability], la fiabilidad válida de ese día (con nivel)
 * o null. [bottomInset], lo que tapa abajo la barra de navegación.
 *
 * [ahead] (P5, H9): la página está compuesta por adelantado, sin verse. Con [ahead] la página empieza vacía y compone
 * su contenido en tramos, un fotograma cada uno, mientras [prepare]; sin [prepare] se queda vacía. Así, al empezar a
 * deslizar, la página de al lado ya está hecha y la siguiente no compone nada en ese fotograma.
 */
@Composable
fun DayPage(
    snapshot: WeatherSnapshot,
    day: DayPoint,
    today: String,
    now: Long,
    reliability: DayReliability?,
    scroll: ScrollState,
    photo: SkyPhoto,
    photos: PhotoCache,
    bottomInset: Dp = 0.dp,
    ahead: Boolean = false,
    prepare: Boolean = true,
    aiText: String? = null,
) {
    var stage by remember { mutableIntStateOf(if (ahead) 0 else STAGES) }
    LaunchedEffect(prepare) {
        while (prepare && stage < STAGES) {
            withFrameNanos { }
            stage++
        }
    }
    val zone = ZoneId.of(snapshot.zoneId)
    val hours = hoursOfDay(snapshot.hourly, day.date, zone)
    val isToday = day.date == today
    val level = reliability?.level()
    Column(
        Modifier
            .testTag("day_page_${day.date}")
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = 8.dp, end = 8.dp, bottom = 18.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (stage < 1) return@Column
        DayBand(photo, photos) {
            Column(Modifier.testTag("day_date")) {
                Text(longDayLabel(day.date), color = Color.White, style = sceneText(CristalType.titleMedium))
                Text(
                    stringResource(R.string.day_place, relativeDayLabel(day.date, today), snapshot.place.name),
                    color = OnSky.Secondary,  // va sobre la franja de la foto, no sobre la hoja
                    style = sceneText(CristalType.bodySmall),
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            DayPanel(day, snapshot, zone)
        }
        if (stage < 2) return@Column
        val rulesSummary = remember(snapshot, day.date, now) { daySummary(snapshot, day.date, now, zone) }
        val summary = aiText ?: rulesSummary   // 016 (H2): la frase de la IA de ese día; sin ella, la de reglas
        if (summary.isNotEmpty()) {
            DayCard(stringResource(R.string.day_summary), Modifier.testTag("day_summary")) {
                Text(
                    summary,
                    color = Color.White,
                    style = sceneText(CristalType.bodyMedium),
                    modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 4.dp),
                )
            }
        }
        DayCard(stringResource(R.string.hour_by_hour), Modifier.testTag("day_hours")) {
            // «Ahora» y las horas pasadas solo en hoy, y siguen a `now` (también pasada la medianoche).
            val nowColumn = if (isToday) nowIndex(hours, now) else null
            val dst = dstShift(hours, zone)
            // La gráfica en su propia capa: deslizar entre días no la vuelve a grabar (plan, Z1-01).
            Box(Modifier.graphicsLayer()) {
                HourlyChart(withCurrentAt(hours, nowColumn, snapshot), zone, nowIndex = nowColumn, dst = dst, dryText = stringResource(R.string.hours_dry_day))
            }
            if (dst != null) {
                Text(
                    text = if (dst.backward) stringResource(R.string.dst_backward, dst.before)
                    else stringResource(R.string.dst_forward, dst.before, dst.after),
                    color = OnSheet.Secondary,
                    style = sceneText(CristalType.bodySmall),
                    modifier = Modifier.testTag("day_dst_note").padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
        if (stage < 3) return@Column
        val wind = dayWind(hours, zone)
        val humidity = dayHumidity(hours)
        if (wind != null || humidity != null) WindHumidityCard(wind, humidity)
        val sun = daySun(day, zone)
        val moon = dayMoon(day, zone)
        if (sun != null || moon != null) {
            DayCard(stringResource(R.string.sun_and_moon), Modifier.testTag("day_sun_moon")) {
                // La raya de «ahora», solo hoy y siguiendo a `now`.
                DayLine(sun, moon, nowFraction = if (isToday) clockFraction(now, zone) else null)
            }
        }
        if (reliability != null && level != null) {
            DayCard(stringResource(R.string.models), Modifier.testTag("day_models"), trailing = { LevelPill(level) }) {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp)) {
                    ModelsSection(reliability, day, snapshot.hourly, zone)
                }
            }
        }
    }
}

/**
 * La franja de arriba (H8): la foto del día, quieta y sin capa de luz, con un oscurecido abajo y [content] encima. La
 * foto se lee fuera del hilo principal (a un cuarto de sus píxeles); hasta que llega, se ve el cristal oscuro.
 */
@Composable
private fun DayBand(photo: SkyPhoto, photos: PhotoCache, content: @Composable ColumnScope.() -> Unit) {
    val image by produceState<ImageBitmap?>(null, photo, photos) {
        value = withContext(Dispatchers.IO) { photos.get(photo, PhotoCache.PHOTO_WIDTH / 2, PhotoCache.PHOTO_HEIGHT / 2) }
    }
    Box(
        Modifier
            .testTag("day_band")
            .fillMaxWidth()
            .height(BandHeight)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Box(
            Modifier
                .testTag("day_band_photo_${photo.file}")
                .matchParentSize()
                .drawBehind {
                    image?.let { img ->
                        val w = size.width * BAND_PHOTO_WIDTH
                        val h = w * img.height / img.width
                        drawImage(
                            img,
                            dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), (-size.height * BAND_PHOTO_LIFT).roundToInt()),
                            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        )
                    }
                    drawRect(bandShadeBrush(photo))
                },
        )
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            content = content,
        )
    }
}

/** Máxima y mínima grandes, con el tiempo y la lluvia a la derecha (`.dbig` de la maqueta). */
@Composable
private fun DayPanel(day: DayPoint, snapshot: WeatherSnapshot, zone: ZoneId) {
    val sky = dayIconSky(day.date, snapshot.hourly, zone)
    val minLabel = stringResource(R.string.day_min, day.minC.roundToInt().toString())
    Row(
        Modifier.testTag("day_panel").fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${day.maxC.roundToInt()}°",
            color = Color.White,
            style = sceneText(CristalType.displayLarge).merge(Tabular),
        )
        Text(
            text = "${day.minC.roundToInt()}°",
            color = OnSky.Secondary,
            style = sceneText(CristalType.headlineSmall).merge(Tabular),
            modifier = Modifier.padding(bottom = 4.dp).semantics { contentDescription = minLabel },
        )
        // Si no cabe, el nombre pasa a dos líneas, sin acortarlo.
        Column(Modifier.weight(1f).padding(bottom = 4.dp), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WeatherIcon(sky, isDay = true, size = 22.dp)
                Text(
                    text = stringResource(skyLabel(sky)),
                    color = Color.White,
                    style = sceneText(CristalType.titleSmall.copy(textAlign = TextAlign.End)),
                )
            }
            Text(
                text = day.precipProbabilityMax?.let { stringResource(R.string.day_rain, it, millimetres(day.precipSumMm)) }
                    ?: stringResource(R.string.day_rain_mm, millimetres(day.precipSumMm)),
                color = OnSky.Text,  // 019: el azul claro no llega a 4,5:1 sobre el cielo de día
                style = sceneText(CristalType.bodySmall).merge(Tabular),
            )
        }
    }
}

/** Píldora con el anillo y «Fiabilidad media». */
@Composable
private fun LevelPill(level: ReliabilityLevel) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.17f))
            .padding(start = 9.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ReliabilityIcon(level, size = 16.dp, contentDescription = null)
        Text(text = reliabilityLabel(level), color = Color.White, style = sceneText(CristalType.bodySmall))
    }
}

/** Tarjeta oscura de la ventana, con su título tal cual (009, H11) y, a la derecha, [trailing]. */
@Composable
private fun DayCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardFill)
            .border(1.dp, CardBorder, shape)
            .padding(top = 12.dp, bottom = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp).padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, color = Color.White, style = sceneText(SectionTitle))
            trailing?.invoke()
        }
        content()
    }
}

/** «Viento y humedad»: dos columnas con una raya fina entre ellas (solo la que tenga datos). */
@Composable
private fun WindHumidityCard(wind: DayWind?, humidity: DayHumidity?) {
    DayCard(stringResource(R.string.wind_and_humidity), Modifier.testTag("day_wind_humidity")) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (wind != null) {
                Column(Modifier.weight(1f).testTag("day_wind")) {
                    Label(R.drawable.ic_wx_wind, stringResource(R.string.card_wind))
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (wind.towardsDeg != null) WindArrow(wind.towardsDeg, 20.dp, Modifier.testTag("wind_arrow"))
                        Text(wind.speed, color = Color.White, style = sceneText(BigValue))
                    }
                    Small(wind.detail)
                    wind.gusts?.let { Small(it) }
                }
            }
            if (wind != null && humidity != null) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.18f)))
            }
            if (humidity != null) {
                Column(Modifier.weight(1f).testTag("day_humidity")) {
                    Label(R.drawable.ic_wx_humidity, stringResource(R.string.card_humidity))
                    Text(humidity.label, color = Color.White, style = sceneText(BigValue), modifier = Modifier.padding(top = 6.dp))
                    HumidityBar(humidity, Modifier.padding(top = 11.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.humidity_axis_min), color = OnSheet.Tertiary, style = sceneText(CristalType.labelSmall))
                        Text(stringResource(R.string.humidity_axis_max), color = OnSheet.Tertiary, style = sceneText(CristalType.labelSmall))
                    }
                }
            }
        }
    }
}

private val BigValue = CristalType.headlineSmall.merge(Tabular)

@Composable
private fun Label(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Image(painterResource(icon), contentDescription = null, Modifier.size(15.dp))
        Text(text, color = OnSheet.Secondary, style = sceneText(CristalType.bodySmall))
    }
}

@Composable
private fun Small(text: String) {
    Text(text, color = OnSheet.Secondary, style = sceneText(CristalType.bodySmall.merge(Tabular)), modifier = Modifier.padding(top = 3.dp))
}

/** Barra de 0 a 100 % con el tramo de la mínima a la máxima. */
@Composable
private fun HumidityBar(humidity: DayHumidity, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(Color.White.copy(alpha = 0.18f), size = size, cornerRadius = radius)
        val start = size.width * humidity.minPct.coerceIn(0, 100) / 100f
        val end = maxOf(size.width * humidity.maxPct.coerceIn(0, 100) / 100f, start + size.height)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(HumidityLow, HumidityHigh), startX = start, endX = end),
            topLeft = Offset(start, 0f),
            size = Size(end - start, size.height),
            cornerRadius = radius,
        )
    }
}

