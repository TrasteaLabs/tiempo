package com.trastealabs.tiempo.widget

import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.domain.WarningLevel
import com.trastealabs.tiempo.domain.activeWarning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Las tres versiones de la maqueta (H13): 2×1, 4×1 y 4×2 en el OPPO. */
enum class WidgetLayout { SMALL, MEDIUM, LARGE }

/** Estrecho (< 250 dp) → pequeño; bajo (< 130 dp; un 4×1 del OPPO mide 90 y el de Pixel 117) → mediano; si no, grande. */
fun widgetLayout(widthDp: Float, heightDp: Float): WidgetLayout = when {
    widthDp < 250f -> WidgetLayout.SMALL
    heightDp < 130f -> WidgetLayout.MEDIUM
    else -> WidgetLayout.LARGE
}

/** La etiqueta de la esquina (grande), junto al sitio (mediano) o bajo la temperatura (pequeño). */
sealed interface WidgetBadge {
    /** Grande: el fenómeno («Tormentas»); mediano: su primera palabra («Temperaturas»); pequeño: «Aviso». */
    data class Warning(val level: WarningLevel, val text: String) : WidgetBadge

    /** Nube tachada y la hora del dato. */
    data class Offline(val time: String) : WidgetBadge

    /** Solo el grande: la hora del dato. */
    data class Updated(val time: String) : WidgetBadge
}

/** El aviso es el mismo que la franja de la app ([activeWarning]) y manda sobre el sin conexión (H15, H16). */
fun widgetBadge(state: StoredState, layout: WidgetLayout, now: Long, shortWarning: String): WidgetBadge? {
    val snapshot = state.snapshot ?: return null
    val warning = activeWarning(snapshot.warnings, now)
    val time = HHMM.format(Instant.ofEpochSecond(snapshot.fetchedAt).atZone(ZoneId.of(snapshot.zoneId)))
    return when {
        warning != null -> WidgetBadge.Warning(
            warning.level,
            when (layout) {
                WidgetLayout.LARGE -> warning.phenomenon.capitalized()
                WidgetLayout.MEDIUM -> warning.phenomenon.substringBefore(' ').capitalized()
                WidgetLayout.SMALL -> shortWarning
            },
        )
        state.lastAttemptFailed -> WidgetBadge.Offline(time)
        layout == WidgetLayout.LARGE -> WidgetBadge.Updated(time)
        else -> null
    }
}

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }
