package com.trastealabs.tiempo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** Colores de la casa que no son de la escena (012, P3). */
object CristalColors {
    /** La onda sobre cristal y sobre la escena: blanco (el alfa lo pone M3). */
    val Ripple: Color = Color.White

    /** La onda sobre blanco (botón blanco, chip elegido): la tinta de los botones. */
    val RippleInk: Color = Color(0xFF1D2433)

    /** Hojas y ventana del día (012 P4): los tonos de `GlassTone.DARK` sin alfa, opacos. Nada de desenfoque detrás. */
    val SheetSurface: Color = Color(20, 26, 50)

    /** El diálogo de la batería: la misma superficie, un punto más clara para separarse del velo. */
    val DialogSurface: Color = Color(24, 30, 56)
}

/**
 * El esquema de color de la app (012, P8): oscuro y estático, sin color dinámico. Lo que la app ya pintaba a mano
 * (superficie de hojas y diálogo, texto blanco, botón blanco con tinta) sale ahora del tema; el resto se queda
 * como el oscuro de Material 3, que es lo que las piezas de M3 (asa de las hojas, velo) ya usaban.
 */
val cristalColorScheme: ColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = CristalColors.RippleInk,
    surface = CristalColors.SheetSurface,
    onSurface = Color.White,
    surfaceContainerHigh = CristalColors.DialogSurface,
)

/** La onda de Material con el color de la casa: la que da `WeatherTheme` a todo lo pulsable. */
val CristalRipple = RippleConfiguration(color = CristalColors.Ripple)

/** La onda de tinta, para lo pulsable que es blanco. */
val CristalRippleInk = RippleConfiguration(color = CristalColors.RippleInk)
