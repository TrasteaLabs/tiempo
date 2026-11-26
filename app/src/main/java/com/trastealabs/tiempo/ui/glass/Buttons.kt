package com.trastealabs.tiempo.ui.glass

import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import com.trastealabs.tiempo.ui.theme.CristalRippleInk
import com.trastealabs.tiempo.ui.theme.CristalType
import com.trastealabs.tiempo.ui.theme.WhiteInk
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Pill = RoundedCornerShape(percent = 50)

/** Texto de los botones (`.btn` de la maqueta), sin la sombra de la escena. */
private val ButtonText = CristalType.labelLargeBig

/** Botón blanco a todo el ancho («Dar permiso», «Reintentar», «Abrir ajustes»): `Button` de M3, cambia de forma al pulsar. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhiteButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Sobre blanco la onda es de tinta.
    CompositionLocalProvider(LocalRippleConfiguration provides CristalRippleInk) {
        Button(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().testTag("white_button"),
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
            contentPadding = ButtonPadding,
        ) {
            Text(text = text, style = ButtonText)
        }
    }
}

/**
 * Botón de cristal a todo el ancho («Cerrar», «Ahora no»). Va sobre el cristal oscuro de la hoja y del
 * diálogo, que viven en otra ventana: solo el relleno y el borde del cristal, sin difuminar la escena.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(GlassTone.CLEAR.top, GlassTone.CLEAR.bottom)), Pill)
            .testTag("glass_button"),
        // El relleno es un degradado de píldora fija: la forma no cambia al pulsar, para que borde y relleno coincidan.
        shapes = ButtonDefaults.shapes(shape = Pill, pressedShape = Pill),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
        contentPadding = ButtonPadding,
    ) {
        Text(text = text, style = ButtonText)
    }
}

private val ButtonPadding = PaddingValues(12.dp)
private val Ink: Color
    @Composable get() = WhiteInk
