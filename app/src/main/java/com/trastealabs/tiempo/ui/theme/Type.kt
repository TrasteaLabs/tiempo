package com.trastealabs.tiempo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Colores que van encima de cualquier cielo (diseño B): la principal, su cabecera y sus tarjetas, y la franja de la foto
 * de la ventana del día.
 *
 * Niveles de texto (019, Cristal 1.4): medidos con `tools/medir_niveles.py` sobre el punto más claro de las 14 fotos con
 * su velo, y vigilados por `TextLevelsTest`. Aquí no hay sitio para bajar: la cabecera pide 0,97 y las tarjetas 0,98, así
 * que los tres son blanco pleno y la jerarquía sale del tamaño y del peso de la letra, no del gris. Ningún texto lleva un
 * alfa escrito a mano fuera de este fichero (`TextLevelsTest`).
 */
object OnSky {
    /** Principal: cifras, títulos, el texto que se lee primero. */
    val Text = Color.White

    /** Secundario: lo que acompaña (sensación, máximas y mínimas, etiquetas de las horas). Medido: blanco pleno. */
    val Secondary = Color.White

    /** Terciario: lo que se lee el último (horas pasadas, notas). Medido: blanco pleno; lo atenuado se dibuja, no se escribe. */
    val Tertiary = Color.White

    /** Asa de las hojas, bordes de casillas y pistas de interruptores: no es texto (3:1 basta). */
    val Control = Color.White.copy(alpha = 0.4f)
    val Line = Color.White.copy(alpha = 0.25f)
    val Card = Color.White.copy(alpha = 0.15f)
    val CardBorder = Color.White.copy(alpha = 0.18f)
    val Shade = Color.Black.copy(alpha = 0.18f)
    val Temperature = Color(0xFFFFD27A)
    val Rain = Color(0xFFA8D4FF)
    val RainText = Color(0xFFD4E8FF)
    val Cold = Color(0xFF5AA0FF)
    val Warm = Color(0xFFFFB347)
}

/**
 * Texto de Ajustes y Sitios (019): sobre la foto desenfocada con su velo (`ScreenLook.sceneVeil`), sobre las tarjetas y
 * campos de encima y sobre las postales. Medido: el peor punto pide 0,81 (postal), así que el secundario va al 85 %.
 */
object OnScreen {
    val Text = Color.White
    val Secondary = Color.White.copy(alpha = 0.85f)

    /** Lo que se lee el último y el texto de ayuda de los campos: el mismo 85 %, no hay sitio para más. */
    val Tertiary = Color.White.copy(alpha = 0.85f)
    val Hint = Color.White.copy(alpha = 0.85f)
}

/**
 * Texto de las superficies opacas (019): hojas y ventana del día (`CristalColors.SheetSurface`), sus tarjetas y paneles
 * (blanco del 7 al 14 % encima) y el diálogo. Medido contra el panel más claro (blanco al 14 %): 0,62 da 5,4:1.
 */
object OnSheet {
    val Text = Color.White
    val Secondary = Color.White.copy(alpha = 0.78f)
    val Tertiary = Color.White.copy(alpha = 0.62f)
    val Hint = Color.White.copy(alpha = 0.62f)
}

/** Cifras de anchura fija, para que no bailen al cambiar. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** Google Sans Flex: cinco ficheros fijos, uno por peso de Cristal 1.2 (§4: 300, 400, 500, 600 y 700). */
val SansFlex = FontFamily(
    Font(R.font.google_sans_flex_300, FontWeight.Light),
    Font(R.font.google_sans_flex_400, FontWeight.Normal),
    Font(R.font.google_sans_flex_500, FontWeight.Medium),
    Font(R.font.google_sans_flex_600, FontWeight.SemiBold),
    Font(R.font.google_sans_flex_700, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = SansFlex,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = 0.sp,
)

/** Cristal 1.2 §4: la escala de Material 3. Todo texto de la app sale de aquí. */
object CristalType {
    val displayLarge = style(57, 64, FontWeight.Light)
    val displayMedium = style(45, 52, FontWeight.Normal)
    val displaySmall = style(36, 44, FontWeight.Normal)
    val headlineLarge = style(32, 40, FontWeight.Bold)
    val headlineMedium = style(28, 36, FontWeight.Bold)
    val headlineSmall = style(24, 32, FontWeight.Medium)
    val titleLarge = style(22, 28, FontWeight.Medium)
    val titleMedium = style(16, 24, FontWeight.SemiBold)
    val titleSmall = style(14, 20, FontWeight.Medium)
    val bodyLarge = style(16, 24, FontWeight.Normal)
    val bodyMedium = style(14, 20, FontWeight.Normal)
    val bodySmall = style(12, 16, FontWeight.Normal)
    val labelLarge = style(14, 20, FontWeight.Medium)
    val labelMedium = style(12, 16, FontWeight.Medium)
    val labelSmall = style(11, 16, FontWeight.Medium)

    /** El botón grande de §4: labelLarge a 16. */
    val labelLargeBig = style(16, 24, FontWeight.Medium)
}

/** Cifras propias de El Tiempo, fuera de la escala (decisión de diseño, 2026-11-10). */
object AppFigures {
    val mainTemp = style(112, 112, FontWeight.Light)
    val hereTemp = style(68, 70, FontWeight.Light)
    val usageFigure = style(64, 68, FontWeight.Light)
}

val cristalTypography: Typography by lazy {
    Typography(
        displayLarge = CristalType.displayLarge,
        displayMedium = CristalType.displayMedium,
        displaySmall = CristalType.displaySmall,
        headlineLarge = CristalType.headlineLarge,
        headlineMedium = CristalType.headlineMedium,
        headlineSmall = CristalType.headlineSmall,
        titleLarge = CristalType.titleLarge,
        titleMedium = CristalType.titleMedium,
        titleSmall = CristalType.titleSmall,
        bodyLarge = CristalType.bodyLarge,
        bodyMedium = CristalType.bodyMedium,
        bodySmall = CristalType.bodySmall,
        labelLarge = CristalType.labelLarge,
        labelMedium = CristalType.labelMedium,
        labelSmall = CristalType.labelSmall,
    )
}

private val spanish = Locale.forLanguageTag("es-ES")

fun degrees(celsius: Double): String = "${celsius.roundToInt()}°"

fun percent(probability: Int?): String = if (probability == null) "–" else "$probability%"

/** "0 mm", "2 mm", "1,2 mm". */
fun millimetres(mm: Double): String {
    val whole = mm.roundToInt()
    val value = if (abs(mm - whole) < 0.05) whole.toString() else String.format(spanish, "%.1f", mm)
    return "$value mm"
}
