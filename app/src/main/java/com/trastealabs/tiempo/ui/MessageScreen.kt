package com.trastealabs.tiempo.ui

import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.ui.glass.WhiteButton
import com.trastealabs.tiempo.ui.glass.translucent
import com.trastealabs.tiempo.ui.glass.sceneText

/**
 * Pantalla de mensaje sobre la escena (H8 del 002, `.msg` de la maqueta): círculo de cristal con el
 * icono, título, texto y botón blanco (primera vez sin red, sin permiso, sin ubicación).
 */
@Composable
fun MessageScreen(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    buttonText: String,
    onButton: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(104.dp).translucent(CircleShape), contentAlignment = Alignment.Center) {
            Image(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
        Text(
            text = title,
            color = Color.White,
            style = sceneText(CristalType.headlineSmall.copy(textAlign = TextAlign.Center)),
            modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
        )
        Text(
            text = body,
            color = OnSky.Secondary,
            style = sceneText(CristalType.bodyMedium.copy(textAlign = TextAlign.Center)),
        )
        WhiteButton(buttonText, onButton, Modifier.padding(top = 28.dp))
    }
}
