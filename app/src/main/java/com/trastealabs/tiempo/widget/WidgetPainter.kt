package com.trastealabs.tiempo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.annotation.FontRes
import androidx.compose.ui.text.TextStyle
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.WarningLevel
import com.trastealabs.tiempo.ui.scene.MoonLook
import com.trastealabs.tiempo.ui.scene.decodeBand
import com.trastealabs.tiempo.ui.scene.moonRadius
import com.trastealabs.tiempo.ui.theme.CristalType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * El widget pintado entero en una imagen (010, P3): Glance solo admite letras del sistema por nombre, así que la foto, el
 * velo, los textos con la letra de la app, los iconos, la luna y la píldora se dibujan aquí, a [MAX_WIDGET_SCALE] px por dp
 * como mucho. Glance solo pone la imagen (con su descripción) y el toque que abre la app. Pinta los tres tamaños (4×2, 4×1 y 2×1).
 */
const val MAX_WIDGET_SCALE = 3f

/** Las esquinas redondeadas del widget, en dp. */
const val WIDGET_CORNER = 22f

/** Lo que solo hace la demo (debug): pintar el widget sin textos para medir el contraste. En la release nadie lo toca. */
object WidgetDemo {
    @Volatile var bare = false
}

/** Quién saca la franja de la foto: [decodeBand] en el móvil; los tests de píxeles (Robolectric no sabe decodificar por regiones) la cambian. */
internal fun interface BandDecoder {
    fun decode(context: Context, photo: com.trastealabs.tiempo.domain.SkyPhoto, widthPx: Int, heightPx: Int, sample: Int): Bitmap
}

internal var bandDecoder = BandDecoder { context, photo, w, h, sample -> decodeBand(context, photo, w, h, sample) }

/** Un texto pintado: su caja de línea en px del bitmap, el tamaño de su rol (en px), su peso, su opacidad y su letra. */
data class PaintedText(
    val text: String, val rect: RectF, val sizePx: Float, val weight: Int, val alpha: Float, val typeface: Typeface,
)

/** El resultado: la imagen, los textos pintados (vacío con `bare`) y el velo que se midió. */
class WidgetImage(val bitmap: Bitmap, val texts: List<PaintedText>, val veil: VeilSolution, val phraseShown: Boolean = false)

private class Role(val sizeDp: Float, val lineDp: Float, val weight: Int)

private fun role(style: TextStyle) = Role(style.fontSize.value, style.lineHeight.value, style.fontWeight!!.weight)

// Los roles del widget (plan, «De la maqueta a la escala»). La temperatura baja a displayMedium si no cabe.
private val PlaceRole = role(CristalType.titleMedium)
private val TempRole = role(CristalType.displayLarge)
private val TempSmallRole = role(CristalType.displayMedium)
private val TempCompactRole = role(CristalType.displaySmall)
private val SkyRole = role(CristalType.titleSmall)
private val RangeRole = role(CristalType.bodyMedium)
private val HourLabelRole = role(CristalType.labelMedium)
private val ChipRole = role(CristalType.labelMedium)
private val EmptyRole = role(CristalType.labelLarge)
private val LineRole = role(CristalType.labelMedium)
private val PhraseRole = role(CristalType.bodySmall)

@FontRes
private fun fontOf(weight: Int): Int = when (weight) {
    300 -> R.font.google_sans_flex_300
    400 -> R.font.google_sans_flex_400
    500 -> R.font.google_sans_flex_500
    600 -> R.font.google_sans_flex_600
    else -> R.font.google_sans_flex_700
}

private const val TEXT = 0.92f
private const val MUTED = 0.75f

private val WarningInk = Color.rgb(0x2A, 0x18, 0x04)

private fun warningColor(level: WarningLevel): Int = when (level) {
    WarningLevel.YELLOW -> Color.rgb(0xF4, 0xC2, 0x0D)
    WarningLevel.ORANGE -> Color.rgb(0xF2, 0x8C, 0x28)
    WarningLevel.RED -> Color.rgb(0xD9, 0x30, 0x25)
}

/** Texto sobre la píldora de ese nivel: oscuro en amarillo y naranja, blanco en rojo (maqueta). */
private fun warningTextColor(level: WarningLevel): Int = if (level == WarningLevel.RED) Color.WHITE else WarningInk

private class IconOp(@DrawableRes val res: Int, val rect: RectF, val tint: Int?)

private class PillOp(val rect: RectF, val color: Int)

/** Los márgenes del 4×2 de la maqueta (dp). */
private const val PAD_X = 14f
private const val PAD_TOP = 10f
private const val PAD_BOTTOM = 8f
private const val TOP_ROW = 20f
private const val HOURS_CELLS = 6
private const val MOON_BOX = 26f
private const val MOON_DISC = 22f
private const val CHIP_MIN = 58f
private const val NAME_MIN = 36f

/** Los márgenes del 4×1 y del 2×1 de la maqueta (dp). */
private const val M_PAD_L = 12f
private const val M_PAD_R = 10f
private const val M_HOURS_W = 114f
private const val M_GAP = 8f
private const val M_MOON_BOX = 20f
private const val S_MOON_BOX = 22f
private const val PILL_H = 20f
private const val LINE_GAP = 2f

/**
 * Pinta el widget de [frame] en un bitmap de [widthDp] × [heightDp] a `min(density, MAX_WIDGET_SCALE)` px por dp. [moon]
 * es la luna de la NASA ya calculada (solo con `frame.moon` ≠ NONE). Con [bare] solo van la foto y el velo (para medir el
 * contraste con capturas del móvil).
 */
fun paintWidget(
    context: Context, frame: WidgetFrame, widthDp: Float, heightDp: Float, density: Float, moon: MoonLook?, bare: Boolean = false,
): WidgetImage {
    val s = min(density, MAX_WIDGET_SCALE)
    val w = (widthDp * s).roundToInt().coerceAtLeast(1)
    val h = (heightDp * s).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { this.density = (s * 160).roundToInt() }
    val canvas = Canvas(bitmap)

    // 1. La foto, en su franja y sin estirarse.
    val band = bandDecoder.decode(context, frame.photo, w, h, 2)
    canvas.drawBitmap(band, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
    band.recycle()

    // 2. La maqueta de textos, con sus cajas.
    val plan = Plan(context, s, widthDp, heightDp)
    if (frame.place == null) {
        plan.empty(context.getString(if (frame.layout == WidgetLayout.SMALL) R.string.widget_open_app_short else R.string.widget_open_app))
    } else {
        when (frame.layout) {
            WidgetLayout.LARGE -> plan.large(frame)
            WidgetLayout.MEDIUM -> plan.medium(frame)
            WidgetLayout.SMALL -> plan.small(frame)
        }
    }

    // 3. El velo, medido con los textos reales sobre los píxeles de la foto.
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    var pillAlpha = 0f
    val veil = if (frame.place == null) {
        solvePill(pixels, w, h, s, plan.emptyPill, base = 0.42f).also { pillAlpha = it.k }
    } else {
        solveVeil(pixels, w, h, s, plan.samples, VEIL_LAYERS.getValue(frame.layout))
    }
    if (frame.place != null) drawVeil(canvas, VEIL_LAYERS.getValue(frame.layout), veil.k, s, w, h)

    // 4. Píldoras, iconos, luna y textos (nada de esto con `bare`).
    if (!bare) {
        if (frame.place == null) {
            canvas.drawRoundRect(plan.emptyPill, plan.emptyPill.height() / 2, plan.emptyPill.height() / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((pillAlpha * 255).roundToInt(), VEIL_R, VEIL_G, VEIL_B)
            })
        }
        plan.pills.forEach { p ->
            canvas.drawRoundRect(p.rect, p.rect.height() / 2, p.rect.height() / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.color })
        }
        plan.icons.forEach { drawIcon(context, canvas, it) }
        if (moon != null && frame.moon.alpha > 0f) drawMoon(canvas, plan.moonCenter, plan.moonDiameter, moon, frame.moon.alpha)
        plan.texts.forEach { drawText(canvas, it) }
    }

    clearCorners(canvas, w.toFloat(), h.toFloat(), WIDGET_CORNER * s)
    return WidgetImage(bitmap, if (bare) emptyList() else plan.texts.map { it.painted }, veil, phraseShown = plan.phraseShown)
}

/** Un texto por pintar: lo que lleva [PaintedText], su color y dónde queda su línea base. */
private class TextOp(val painted: PaintedText, val color: Int, val baseline: Float, val align: Paint.Align)

/** La disposición: cada pieza con su caja en px. */
private class Plan(val context: Context, val s: Float, val widthDp: Float, val heightDp: Float) {
    val texts = ArrayList<TextOp>()
    val icons = ArrayList<IconOp>()
    val pills = ArrayList<PillOp>()
    val samples = ArrayList<TextSample>()
    var moonCenter = PointF2(0f, 0f)
    var moonDiameter = 0f
    var emptyPill = RectF()

    /** 018 P3: `true` si la frase del parte cabía y se pintó (si no, se pintó la palabra del cielo). */
    var phraseShown = false

    private fun fontFace(weight: Int): Typeface =
        ResourcesCompat.getFont(context, fontOf(weight)) ?: Typeface.DEFAULT

    private fun paintOf(r: Role): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = fontFace(r.weight)
        textSize = r.sizeDp * s
        fontFeatureSettings = "tnum"
    }

    private fun width(text: String, r: Role): Float = paintOf(r).measureText(text)

    /** [text] recortado con «…» para que quepa en [maxDp]. */
    private fun fit(text: String, r: Role, maxDp: Float): String {
        val paint = paintOf(r)
        if (paint.measureText(text) <= maxDp * s) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxDp * s) t = t.dropLast(1)
        return t.trimEnd() + "…"
    }

    /** Añade un texto de la caja de línea que empieza en ([xDp], [topDp]); [align] RIGHT/CENTER: [xDp] es el borde/centro. */
    private fun add(
        text: String, r: Role, xDp: Float, topDp: Float, alpha: Float, color: Int = Color.WHITE,
        align: Paint.Align = Paint.Align.LEFT, measured: Boolean = true, baselineDp: Float? = null,
    ): RectF {
        val paint = paintOf(r)
        val wPx = paint.measureText(text)
        val left = when (align) {
            Paint.Align.LEFT -> xDp * s
            Paint.Align.RIGHT -> xDp * s - wPx
            else -> xDp * s - wPx / 2
        }
        val fm = paint.fontMetrics
        val lineH = r.lineDp * s
        val top = if (baselineDp != null) baselineDp * s - ((lineH - (fm.descent - fm.ascent)) / 2 - fm.ascent) else topDp * s
        val base = top + (lineH - (fm.descent - fm.ascent)) / 2 - fm.ascent
        val rect = RectF(left, top, left + wPx, top + lineH)
        val painted = PaintedText(text, rect, r.sizeDp * s, r.weight, alpha, paint.typeface)
        texts.add(TextOp(painted, color, base, Paint.Align.LEFT))
        if (measured) samples.add(TextSample(rect, alpha))
        return rect
    }

    private fun icon(@DrawableRes res: Int, leftDp: Float, centerYDp: Float, sizeDp: Float, tint: Int? = null) {
        icons.add(IconOp(res, RectF(leftDp * s, (centerYDp - sizeDp / 2) * s, (leftDp + sizeDp) * s, (centerYDp + sizeDp / 2) * s), tint))
    }

    /** Antes de abrir la app: «Abre la app para empezar» en su píldora, en medio. */
    fun empty(text: String) {
        val tw = width(text, EmptyRole) / s
        val pillW = 14f + 18f + 8f + tw + 14f
        val pillH = 34f
        val left = (widthDp - pillW) / 2
        val top = (heightDp - pillH) / 2
        emptyPill = RectF(left * s, top * s, (left + pillW) * s, (top + pillH) * s)
        icon(R.drawable.ic_wx_touch, left + 14f, top + pillH / 2, 18f)
        add(text, EmptyRole, left + 14f + 18f + 8f, top + pillH / 2 - EmptyRole.lineDp / 2, 1f, measured = false)
    }

    /** 4×2: arriba sitio y etiqueta; en medio, temperatura y cielo; abajo, «Ahora» y cinco horas. */
    fun large(frame: WidgetFrame) {
        val hoursH = HourLabelRole.lineDp + 1f + 18f + 1f + HourLabelRole.lineDp
        val hoursTop = heightDp - PAD_BOTTOM - hoursH
        topRow(frame, PAD_X, widthDp - PAD_X, PAD_TOP + TOP_ROW / 2, MOON_BOX)
        val topRowBottom = PAD_TOP + TOP_ROW
        middle(frame, topRowBottom, hoursTop)
        hours(frame, hoursTop)
    }

    /** La fila del sitio, de [left] a [right] dp y centrada en [cy]: nombre, luna de [moonBox] dp y etiqueta. */
    private fun topRow(frame: WidgetFrame, left: Float, right: Float, cy: Float, moonBox: Float) {
        val moonSpace = if (frame.moon.alpha > 0f) moonBox + 6f else 0f
        val badge = frame.badge
        val name = frame.place.orEmpty()
        val pinSpace = 14f + 3f

        // Anchuras naturales (dp) de la etiqueta (píldora o la hora a secas) y del nombre.
        val chipText = when (badge) {
            is WidgetBadge.Warning -> badge.text
            is WidgetBadge.Offline -> badge.time
            is WidgetBadge.Updated -> badge.time
            null -> null
        }
        val isPill = badge is WidgetBadge.Warning || badge is WidgetBadge.Offline
        val chipPad = if (isPill) 6f + 13f + 3f + 8f else 0f
        val chipTextW = chipText?.let { width(it, ChipRole) / s } ?: 0f
        var chipW = if (chipText == null) 0f else chipPad + chipTextW
        var nameW = width(name, PlaceRole) / s
        val gap = 6f
        val avail = right - left - moonSpace - pinSpace - (if (chipText != null) gap else 0f)
        val overflow = chipW + nameW - avail
        if (overflow > 0f) {
            // Como la maqueta (la etiqueta se encoge mucho antes que el nombre): primero cede la etiqueta, hasta 58 dp;
            // si aún no cabe, el nombre.
            // Pero nunca por debajo de su primera palabra entera («Temperaturas…»): el aviso pesa más que el resto del nombre.
            val firstWord = chipText?.substringBefore(' ')?.let { chipPad + width("$it…", ChipRole) / s } ?: 0f
            val chipFloor = max(CHIP_MIN, min(firstWord, chipW))
            val chipCut = if (badge is WidgetBadge.Warning) min(chipW - chipFloor, overflow).coerceAtLeast(0f) else 0f
            chipW -= chipCut
            val sixLetters = width(name.take(6) + "…", PlaceRole) / s
            nameW = max(min(nameW, max(NAME_MIN, sixLetters)), nameW - (overflow - chipCut))
            if (chipW + nameW > avail) chipW = max(0f, avail - nameW)
        }
        val shownName = fit(name, PlaceRole, nameW)
        icon(R.drawable.ic_wx_location, left, cy, 14f)
        add(shownName, PlaceRole, left + pinSpace, cy - PlaceRole.lineDp / 2, TEXT)

        var x = right
        if (chipText != null) {
            val chipLeft = x - chipW
            when (badge) {
                is WidgetBadge.Warning, is WidgetBadge.Offline -> {
                    val (bg, ink) = if (badge is WidgetBadge.Warning) {
                        warningColor(badge.level) to warningTextColor(badge.level)
                    } else {
                        Color.argb(128, VEIL_R, VEIL_G, VEIL_B) to Color.WHITE
                    }
                    pills.add(PillOp(RectF(chipLeft * s, (cy - 10f) * s, x * s, (cy + 10f) * s), bg))
                    icon(
                        if (badge is WidgetBadge.Warning) R.drawable.ic_wx_warning else R.drawable.ic_wx_cloud_off,
                        chipLeft + 6f, cy, 13f, tint = ink,
                    )
                    val text = fit(chipText, ChipRole, chipW - chipPad)
                    add(text, ChipRole, chipLeft + 6f + 13f + 3f, cy - ChipRole.lineDp / 2, 1f, color = ink, measured = badge is WidgetBadge.Offline)
                }
                else -> add(chipText, ChipRole, x, cy - ChipRole.lineDp / 2, MUTED, align = Paint.Align.RIGHT)
            }
            x = chipLeft - gap
        }
        if (frame.moon.alpha > 0f) {
            moonDiameter = moonBox * (MOON_DISC / MOON_BOX) * s
            moonCenter = PointF2((x - moonBox / 2) * s, cy * s)
        }
    }

    /** 4×1: a la izquierda sitio (y etiqueta), temperatura y cielo con máx/mín; a la derecha, tres horas. */
    fun medium(frame: WidgetFrame) {
        val hoursW = M_HOURS_W
        val hoursLeft = widthDp - M_PAD_R - hoursW
        val leftRight = hoursLeft - M_GAP
        val tempH = TempCompactRole.lineDp
        val top = (heightDp - (TOP_ROW + tempH)) / 2
        topRow(frame, M_PAD_L, leftRight, top + TOP_ROW / 2, M_MOON_BOX)
        val cy = top + TOP_ROW + tempH / 2
        val digits = digitsHeightDp(frame.temp, TempCompactRole)
        val tempRect = add(frame.temp, TempCompactRole, M_PAD_L, 0f, TEXT, baselineDp = cy + digits / 2)
        val condLeft = tempRect.right / s + M_GAP
        val availW = leftRight - condLeft
        val range = if (frame.max.isNotEmpty() && frame.min.isNotEmpty()) "${frame.max} / ${frame.min}" else ""
        val iconSize = 15f
        val rowGap = 4f
        // El cielo manda sobre la cifra secundaria (017 H3): puede usar el hueco de antes de las horas; el máx / mín se recorta primero.
        val sky = fit(frame.skyText, SkyRole, availW + M_GAP - iconSize - rowGap)
        val rows = if (range.isEmpty()) 1 else 2
        val blockTop = cy - rows * SkyRole.lineDp / 2
        icon(frame.skyIcon, condLeft, blockTop + SkyRole.lineDp / 2, iconSize)
        add(sky, SkyRole, condLeft + iconSize + rowGap, blockTop, TEXT)
        if (range.isNotEmpty()) add(fit(range, RangeRole, availW), RangeRole, condLeft, blockTop + SkyRole.lineDp, MUTED)
        hours(frame, (heightDp - hoursHeight()) / 2, hoursLeft, hoursW / 3, 3)
    }

    /** 2×1: icono y temperatura (y la luna); debajo «Córdoba · 26°/13°» (el nombre se corta antes que las cifras), «Aviso» o la nube tachada. */
    fun small(frame: WidgetFrame) {
        val rowH = TempCompactRole.lineDp
        val top = (heightDp - (rowH + LINE_GAP + PILL_H)) / 2
        val cy = top + rowH / 2
        val iconSize = 24f
        icon(frame.skyIcon, M_PAD_L, cy, iconSize)
        val digits = digitsHeightDp(frame.temp, TempCompactRole)
        val tempRect = add(frame.temp, TempCompactRole, M_PAD_L + iconSize + 6f, 0f, TEXT, baselineDp = cy + digits / 2)
        if (frame.moon.alpha > 0f) {
            val right = widthDp - M_PAD_R
            if (right - S_MOON_BOX >= tempRect.right / s + 4f) {
                moonDiameter = S_MOON_BOX * (MOON_DISC / MOON_BOX) * s
                moonCenter = PointF2((right - S_MOON_BOX / 2) * s, cy * s)
            }
        }
        val lineCy = top + rowH + LINE_GAP + PILL_H / 2
        val availW = widthDp - M_PAD_L - M_PAD_R
        val badge = frame.badge
        if (badge is WidgetBadge.Warning || badge is WidgetBadge.Offline) {
            val text: String
            val bg: Int
            val ink: Int
            val res: Int
            if (badge is WidgetBadge.Warning) {
                text = badge.text; bg = warningColor(badge.level); ink = warningTextColor(badge.level); res = R.drawable.ic_wx_warning
            } else {
                text = (badge as WidgetBadge.Offline).time
                bg = Color.argb(128, VEIL_R, VEIL_G, VEIL_B); ink = Color.WHITE; res = R.drawable.ic_wx_cloud_off
            }
            val chipPad = 6f + 13f + 3f + 8f
            val shown = fit(text, ChipRole, availW - chipPad)
            val pillW = chipPad + width(shown, ChipRole) / s
            pills.add(PillOp(RectF(M_PAD_L * s, (lineCy - PILL_H / 2) * s, (M_PAD_L + pillW) * s, (lineCy + PILL_H / 2) * s), bg))
            icon(res, M_PAD_L + 6f, lineCy, 13f, tint = ink)
            add(shown, ChipRole, M_PAD_L + 6f + 13f + 3f, lineCy - ChipRole.lineDp / 2, 1f, color = ink, measured = badge is WidgetBadge.Offline)
        } else {
            val range = if (frame.max.isNotEmpty() && frame.min.isNotEmpty()) " · ${frame.max}/${frame.min}" else ""
            val rangeW = width(range, LineRole) / s
            val name = fit(frame.place.orEmpty(), LineRole, (availW - rangeW).coerceAtLeast(NAME_MIN))
            add(name + range, LineRole, M_PAD_L, lineCy - LineRole.lineDp / 2, MUTED)
        }
    }

    private fun hoursHeight() = HourLabelRole.lineDp + 1f + 18f + 1f + HourLabelRole.lineDp

    private fun middle(frame: WidgetFrame, zoneTop: Float, zoneBottom: Float) {
        val cy = (zoneTop + zoneBottom) / 2
        val zoneH = zoneBottom - zoneTop
        // La cifra de 57 sp si sus dígitos caben entre la fila de arriba y las horas; si no, la de 45.
        val big = digitsHeightDp(frame.temp, TempRole) + 4f <= zoneH
        val r = if (big) TempRole else TempSmallRole
        val digits = digitsHeightDp(frame.temp, r)
        val tempRect = add(frame.temp, r, PAD_X, 0f, TEXT, baselineDp = cy + digits / 2)
        val tempRight = tempRect.right / s

        val range = if (frame.max.isNotEmpty() && frame.min.isNotEmpty()) "Máx ${frame.max} · Mín ${frame.min}" else ""
        val iconSize = 18f
        val rowGap = 4f
        val availW = widthDp - PAD_X - (tempRight + 10f)

        // 018 P3: con frase del parte, en lugar de la palabra del cielo, en una o dos líneas; si no cabe entera, el bloque de hoy.
        val lines = frame.phrase?.let { wrapPhrase(it, availW - (iconSize + rowGap)) }
        if (lines != null) {
            phraseShown = true
            val rightEdge = widthDp - PAD_X
            val blockH = lines.size * PhraseRole.lineDp + if (range.isEmpty()) 0f else RangeRole.lineDp
            val top = cy - blockH / 2
            lines.forEachIndexed { i, line -> add(line, PhraseRole, rightEdge, top + i * PhraseRole.lineDp, TEXT, align = Paint.Align.RIGHT) }
            val firstW = width(lines.first(), PhraseRole) / s
            icon(frame.skyIcon, rightEdge - firstW - rowGap - iconSize, top + PhraseRole.lineDp / 2, iconSize)
            if (range.isNotEmpty()) {
                add(fit(range, RangeRole, availW), RangeRole, rightEdge, top + lines.size * PhraseRole.lineDp, MUTED, align = Paint.Align.RIGHT)
            }
            return
        }

        var sky = frame.skyText
        var rangeText = range
        var skyW = iconSize + rowGap + width(sky, SkyRole) / s
        var rangeW = if (range.isEmpty()) 0f else width(range, RangeRole) / s
        if (max(skyW, rangeW) > availW) {
            if (rangeW > availW) {
                rangeText = fit(range, RangeRole, availW)
                rangeW = width(rangeText, RangeRole) / s
            }
            if (skyW > availW) {
                sky = fit(sky, SkyRole, availW - iconSize - rowGap)
                skyW = iconSize + rowGap + width(sky, SkyRole) / s
            }
        }
        val right = widthDp - PAD_X
        val rows = if (range.isEmpty()) 1 else 2
        val blockH = rows * SkyRole.lineDp
        val top = cy - blockH / 2
        add(sky, SkyRole, right, top, TEXT, align = Paint.Align.RIGHT)
        icon(frame.skyIcon, right - skyW, top + SkyRole.lineDp / 2, iconSize)
        if (range.isNotEmpty()) add(rangeText, RangeRole, right, top + SkyRole.lineDp, MUTED, align = Paint.Align.RIGHT)
    }

    /**
     * [text] partido por palabras en una o dos líneas de [maxDp] como mucho (la maqueta de 018: cada palabra a la línea de
     * antes si cabe), o `null` si no cabe entera en dos: nunca se recorta ni se pone «…». Una palabra suelta más ancha que la línea tampoco cabe.
     */
    private fun wrapPhrase(text: String, maxDp: Float): List<String>? {
        val words = text.trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || maxDp <= 0f) return null
        val lines = mutableListOf("")
        for (word in words) {
            val joined = if (lines.last().isEmpty()) word else lines.last() + " " + word
            if (width(joined, PhraseRole) / s <= maxDp) lines[lines.lastIndex] = joined else lines.add(word)
        }
        return lines.takeIf { it.size <= 2 && it.all { line -> width(line, PhraseRole) / s <= maxDp } }
    }

    private fun hours(
        frame: WidgetFrame, top: Float, left: Float = PAD_X, cellW: Float = (widthDp - 2 * PAD_X) / HOURS_CELLS, count: Int = HOURS_CELLS,
    ) {
        val cells = frame.hours.take(count)
        // Con menos horas que celdas (017 H4), el ancho de todas se reparte entre las que hay: sin hueco a la derecha.
        val shareW = if (cells.isEmpty()) cellW else cellW * count / cells.size
        cells.forEachIndexed { i, hour ->
            val cx = left + shareW * (i + 0.5f)
            add(hour.label, HourLabelRole, cx, top, MUTED, align = Paint.Align.CENTER)
            val iconTop = top + HourLabelRole.lineDp + 1f
            icon(hour.icon, cx - 9f, iconTop + 9f, 18f)
            add(hour.temp, HourLabelRole, cx, iconTop + 18f + 1f, TEXT, align = Paint.Align.CENTER)
        }
    }

    /** Lo que miden los dígitos de [text] (sin la caja de línea), en dp. */
    private fun digitsHeightDp(text: String, r: Role): Float {
        val bounds = Rect()
        paintOf(r).getTextBounds(text, 0, text.length, bounds)
        val h = bounds.height() / s
        return if (h > 0f) h else r.sizeDp * 0.73f // sin medida (Robolectric heredado): la altura de un dígito
    }
}

private class PointF2(val x: Float, val y: Float)

private fun drawText(canvas: Canvas, op: TextOp) {
    val p = op.painted
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = p.typeface
        textSize = p.sizePx
        fontFeatureSettings = "tnum"
        color = op.color
        alpha = (p.alpha * 255).roundToInt()
    }
    canvas.drawText(p.text, p.rect.left, op.baseline, paint)
}

private fun drawIcon(context: Context, canvas: Canvas, op: IconOp) {
    val d = ContextCompat.getDrawable(context, op.res)?.mutate() ?: return
    op.tint?.let { d.setTint(it) }
    d.setBounds(op.rect.left.roundToInt(), op.rect.top.roundToInt(), op.rect.right.roundToInt(), op.rect.bottom.roundToInt())
    d.draw(canvas)
}

/** La luna de la NASA (ya iluminada, con 4 px de margen) escalada al disco de [diameter] px, con su opacidad. */
private fun drawMoon(canvas: Canvas, center: PointF2, diameter: Float, moon: MoonLook, alpha: Float) {
    val photo = moon.lit ?: return
    val k = diameter / (2 * moonRadius(photo.width))
    val matrix = Matrix().apply {
        setScale(k, k)
        postTranslate(center.x - photo.width / 2f * k, center.y - photo.height / 2f * k)
    }
    canvas.drawBitmap(photo, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = (alpha * 255).roundToInt() })
}

/** Las paradas del degradado de una capa: pleno hasta [fullAt] (0 a 1) y luego la rampa de [veilFade] en [FADE_STOPS] pasos. */
internal fun veilStops(fullAt: Float, alpha: Float): Pair<FloatArray, IntArray> {
    val n = FADE_STOPS
    val stops = FloatArray(n + 2)
    val colors = IntArray(n + 2)
    stops[0] = 0f
    colors[0] = Color.argb((alpha * 255).roundToInt(), VEIL_R, VEIL_G, VEIL_B)
    for (i in 0..n) {
        val t = i / n.toFloat()
        stops[i + 1] = fullAt + (1f - fullAt) * t
        colors[i + 1] = Color.argb((alpha * veilFade(t) * 255).roundToInt(), VEIL_R, VEIL_G, VEIL_B)
    }
    return stops to colors
}

private const val FADE_STOPS = 12

/** Las capas del velo, una encima de otra: cada una, tinta que se desvanece desde su lado (o plana, `ALL`). */
private fun drawVeil(canvas: Canvas, layers: List<VeilLayer>, k: Float, s: Float, w: Int, h: Int) {
    if (k <= 0.001f) return
    for (l in layers) {
        val a = min(VEIL_MAX, k * l.weight)
        val solid = Color.argb((a * 255).roundToInt(), VEIL_R, VEIL_G, VEIL_B)
        val paint = Paint()
        if (l.side == VeilSide.ALL) {
            paint.color = solid
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            continue
        }
        val full = l.fullDp * s
        val total = (l.fullDp + l.fadeDp) * s
        val (stops, colors) = veilStops(full / total, a)
        when (l.side) {
            VeilSide.TOP -> {
                paint.shader = LinearGradient(0f, 0f, 0f, total, colors, stops, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), min(total, h.toFloat()), paint)
            }
            VeilSide.BOTTOM -> {
                paint.shader = LinearGradient(0f, h.toFloat(), 0f, h - total, colors, stops, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, max(0f, h - total), w.toFloat(), h.toFloat(), paint)
            }
            VeilSide.LEFT -> {
                paint.shader = LinearGradient(0f, 0f, total, 0f, colors, stops, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, min(total, w.toFloat()), h.toFloat(), paint)
            }
            VeilSide.RIGHT -> {
                paint.shader = LinearGradient(w.toFloat(), 0f, w - total, 0f, colors, stops, Shader.TileMode.CLAMP)
                canvas.drawRect(max(0f, w - total), 0f, w.toFloat(), h.toFloat(), paint)
            }
            VeilSide.ALL -> Unit
        }
    }
}

/** Deja transparente lo que queda fuera de las esquinas redondeadas del widget. */
private fun clearCorners(canvas: Canvas, w: Float, h: Float, radius: Float) {
    val outside = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        addRect(0f, 0f, w, h, Path.Direction.CW)
        addRoundRect(0f, 0f, w, h, radius, radius, Path.Direction.CW)
    }
    canvas.drawPath(outside, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
}
