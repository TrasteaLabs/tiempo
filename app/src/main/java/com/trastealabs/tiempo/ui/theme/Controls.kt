package com.trastealabs.tiempo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** La tinta sobre lo blanco (botón blanco, chip o pestaña elegidos, icono del interruptor): el `onPrimary` del tema (020). */
val WhiteInk: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onPrimary

/** El interruptor de la casa (020): encendido blanco sobre el gris de los controles, apagado con el pulgar de ayuda. */
@Composable
fun cristalSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.primary,
    checkedIconColor = WhiteInk,
    checkedTrackColor = OnSky.Control,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = OnScreen.Hint,
    uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
    uncheckedBorderColor = OnSky.Control,
)
