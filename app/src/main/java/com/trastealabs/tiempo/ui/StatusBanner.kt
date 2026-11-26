package com.trastealabs.tiempo.ui

import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.ui.glass.translucent

/** Píldora de cristal en lugar de la fecha: "Sin conexión · datos de las 08:15" (H7 del 002) o "Actualizando…". */
@Composable
fun StatusBanner(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    Row(
        modifier = modifier
            .translucent(CircleShape)
            .padding(start = if (icon != null) 8.dp else 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Image(painterResource(icon), contentDescription = null, Modifier.size(14.dp))
        Text(text = text, color = Color.White, style = CristalType.labelMedium)
    }
}
