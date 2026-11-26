package com.trastealabs.tiempo.widget

import android.content.Context
import androidx.annotation.DrawableRes
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.AiReport
import com.trastealabs.tiempo.domain.LOADING_PHOTO
import com.trastealabs.tiempo.domain.MoonShow
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.domain.WIDGET_MAX
import com.trastealabs.tiempo.domain.WarningLevel
import com.trastealabs.tiempo.domain.activeWarning
import com.trastealabs.tiempo.domain.effectsNow
import com.trastealabs.tiempo.domain.isSouth
import com.trastealabs.tiempo.domain.moonPhaseToday
import com.trastealabs.tiempo.domain.next24Hours
import com.trastealabs.tiempo.domain.reportFacts
import com.trastealabs.tiempo.domain.shownDegrees
import com.trastealabs.tiempo.domain.skyOf
import com.trastealabs.tiempo.domain.skyPhotoNow
import com.trastealabs.tiempo.domain.today
import com.trastealabs.tiempo.domain.visibleReport
import com.trastealabs.tiempo.ui.theme.skyLabel
import com.trastealabs.tiempo.ui.theme.weatherIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Una hora de la fila de abajo: «Ahora» o «15:00», su icono y su temperatura. */
data class WidgetHour(val label: String, @DrawableRes val icon: Int, val temp: String)

/**
 * Lo que pinta el widget en un tamaño (010, P3): la foto, los textos ya escritos, las horas, la etiqueta y la luna. Sin
 * dibujar nada: lo dibuja [paintWidget]. Sin datos ([place] = `null`), solo la foto de nubes y «Abre la app…».
 */
data class WidgetFrame(
    val layout: WidgetLayout,
    val photo: SkyPhoto,
    val place: String?,
    val temp: String,
    @DrawableRes val skyIcon: Int,
    val skyText: String,
    val max: String,
    val min: String,
    /** 4×2: «Ahora» y las 5 siguientes; 4×1: las 3 siguientes; 2×1: ninguna. */
    val hours: List<WidgetHour>,
    /** El de hoy ([widgetBadge]): aviso > sin conexión > hora. */
    val badge: WidgetBadge?,
    val moon: MoonShow,
    val moonPhase: Double?,
    val south: Boolean,
    /**
     * 018 P3: la frase del parte de hoy, solo en el 4×2 (en el 4×1 y el 2×1 siempre `null`). El pintor la enseña en lugar de
     * la palabra del cielo si cabe entera en dos líneas; si no, queda el widget de hoy.
     */
    val phrase: String? = null,
)

/** La misma foto que la principal ([skyPhotoNow]); sin datos, la de nubes. */
fun widgetPhoto(state: StoredState, now: Long): SkyPhoto =
    state.snapshot?.let { skyPhotoNow(it, now) } ?: LOADING_PHOTO

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

private fun deg(c: Double): String = "${shownDegrees(c)}°"

/** Los nombres largos de fenómeno van solos en la píldora; los cortos llevan «· hasta las 21:00» (maqueta). */
private const val SHORT_PHENOMENON = 12

/**
 * 018 P3: la frase que enseña el 4×2, de [report] (lo guardado, sin red: C6). Solo si la app enseñaría ese parte
 * ([visibleReport]: de hoy y con la previsión sin cambios de verdad), con frase y de [WIDGET_MAX] caracteres como mucho.
 * Sin datos, sin horas por delante, sin parte, parte de ayer o viejo (sin `widget`): `null` y el widget de siempre.
 */
fun widgetPhrase(state: StoredState, report: AiReport?, now: Long): String? {
    val snapshot = state.snapshot ?: return null
    val facts = reportFacts(snapshot, now, ZoneId.of(snapshot.zoneId)) ?: return null
    return visibleReport(report, facts.basis)?.widget?.trim()?.takeIf { it.isNotEmpty() && it.length <= WIDGET_MAX }
}

/** [context] solo resuelve el nombre del cielo (`skyLabel`). [phrase] (018 P3) solo se guarda en el 4×2 y con datos. */
fun widgetFrame(state: StoredState, layout: WidgetLayout, now: Long, context: Context, phrase: String? = null): WidgetFrame {
    val snapshot = state.snapshot
        ?: return WidgetFrame(
            layout, widgetPhoto(state, now), place = null, temp = "", skyIcon = 0, skyText = "", max = "", min = "",
            hours = emptyList(), badge = null, moon = MoonShow.NONE, moonPhase = null, south = false,
        )
    val zone = ZoneId.of(snapshot.zoneId)
    val sky = skyOf(snapshot.current.weatherCode)
    val day = today(snapshot, now)
    val upcoming = next24Hours(snapshot.hourly, now).drop(1)
    fun hour(label: String, code: Int, isDay: Boolean, c: Double) = WidgetHour(label, weatherIcon(skyOf(code), isDay), deg(c))
    val hours = when (layout) {
        WidgetLayout.LARGE -> listOf(
            hour(context.getString(R.string.now), snapshot.current.weatherCode, snapshot.current.isDay, snapshot.current.temperatureC),
        ) + upcoming.take(5).map { hour(clock(it.time, zone), it.weatherCode, it.isDay, it.temperatureC) }
        WidgetLayout.MEDIUM -> upcoming.take(3).map { hour(clock(it.time, zone), it.weatherCode, it.isDay, it.temperatureC) }
        WidgetLayout.SMALL -> emptyList()
    }
    val effects = effectsNow(snapshot, now)
    return WidgetFrame(
        layout = layout,
        photo = widgetPhoto(state, now),
        place = snapshot.place.name,
        temp = deg(snapshot.current.temperatureC),
        skyIcon = weatherIcon(sky, snapshot.current.isDay),
        skyText = context.getString(skyLabel(sky)),
        max = day?.let { deg(it.maxC) }.orEmpty(),
        min = day?.let { deg(it.minC) }.orEmpty(),
        hours = hours,
        badge = widgetBadge(state, layout, now, context.getString(R.string.widget_warning))?.let { withUntil(it, state, layout, now, context) },
        moon = effects.moon,
        moonPhase = if (effects.moon == MoonShow.NONE) null else moonPhaseToday(snapshot, now),
        south = isSouth(snapshot),
        phrase = phrase?.takeIf { layout == WidgetLayout.LARGE },
    )
}

/** En el 4×2, el aviso corto lleva su hora de fin si acaba hoy: «Tormentas · hasta las 21:00». */
private fun withUntil(badge: WidgetBadge, state: StoredState, layout: WidgetLayout, now: Long, context: Context): WidgetBadge {
    if (layout != WidgetLayout.LARGE || badge !is WidgetBadge.Warning) return badge
    val snapshot = state.snapshot ?: return badge
    val warning = activeWarning(snapshot.warnings, now) ?: return badge
    val zone = ZoneId.of(snapshot.zoneId)
    val end = Instant.ofEpochSecond(warning.expires).atZone(zone)
    val sameDay = end.toLocalDate() == Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    return if (badge.text.length <= SHORT_PHENOMENON && sameDay) {
        badge.copy(text = context.getString(R.string.widget_until, badge.text, hhmm.format(end)))
    } else {
        badge
    }
}

private fun clock(epochSeconds: Long, zone: ZoneId): String = hhmm.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

private fun levelWord(context: Context, level: WarningLevel): String = context.getString(
    when (level) {
        WarningLevel.YELLOW -> R.string.widget_level_yellow
        WarningLevel.ORANGE -> R.string.widget_level_orange
        WarningLevel.RED -> R.string.widget_level_red
    },
)

/** Lo que lee TalkBack del widget entero: sitio, temperatura, cielo, máxima y mínima, y el aviso o la hora del dato. */
fun widgetDescription(frame: WidgetFrame, context: Context): String {
    val place = frame.place ?: return context.getString(R.string.widget_open_app)
    val head = buildString {
        append("$place, ${frame.temp}, ${frame.skyText}")
        if (frame.max.isNotEmpty() && frame.min.isNotEmpty()) append(context.getString(R.string.widget_desc_range, frame.max, frame.min))
        frame.phrase?.let { append(". ").append(it) } // la frase ya viene escrita por el parte: solo el punto que la separa
    }
    val tail = when (val badge = frame.badge) {
        is WidgetBadge.Warning -> context.getString(R.string.widget_desc_warning, levelWord(context, badge.level), badge.text)
        is WidgetBadge.Offline -> context.getString(R.string.widget_desc_offline, badge.time)
        is WidgetBadge.Updated -> context.getString(R.string.widget_desc_updated, badge.time)
        null -> ""
    }
    return head + tail
}
