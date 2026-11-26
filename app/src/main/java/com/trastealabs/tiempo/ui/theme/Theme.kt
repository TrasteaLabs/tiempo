package com.trastealabs.tiempo.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * El tema de toda la app (012, P8): `MaterialExpressiveTheme` con el esquema oscuro estático, las formas, la escala de
 * letra de Cristal 1.2 y el movimiento estándar de M3. La pone `MainScreen`, raíz de todas las pantallas.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeatherTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = cristalColorScheme,
        typography = cristalTypography,
        shapes = cristalShapes,
        motionScheme = MotionScheme.standard(),
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides CristalType.bodyLarge,
            // La onda de Material con el color de la casa en lugar de la luz de contacto (012, P3).
            LocalRippleConfiguration provides CristalRipple,
            content = content,
        )
    }
}
