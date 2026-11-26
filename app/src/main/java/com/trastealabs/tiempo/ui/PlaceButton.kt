package com.trastealabs.tiempo.ui

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.ui.glass.sceneText

/** Qué dice la línea de encima del nombre (diseño B, 006). */
enum class PlaceKind {
    /** «📍 Donde estoy». */
    HERE,

    /** «🔖 Sitio guardado». */
    SAVED,

    /** Sin línea: la pantalla de sin permiso, donde el nombre ya es «Donde estoy». */
    PLAIN,
}

/**
 * El nombre que se toca (diseño B): «Donde estoy» o «Sitio guardado» en versalitas, el nombre (con puntos
 * suspensivos) y la flecha. Todo el bloque abre la pantalla Sitios (H8). TalkBack: «Córdoba, donde estoy. Cambiar de
 * sitio».
 */
@Composable
fun PlaceButton(name: String, kind: PlaceKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val said = when (kind) {
        PlaceKind.HERE -> stringResource(R.string.place_button_here, name)
        PlaceKind.SAVED -> stringResource(R.string.place_button_saved, name)
        PlaceKind.PLAIN -> stringResource(R.string.place_button_plain, name)
    }
    val press = remember { MutableInteractionSource() }
    Column(
        modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = press,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = said },
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (kind != PlaceKind.PLAIN) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Image(
                    painterResource(if (kind == PlaceKind.HERE) R.drawable.ic_wx_location else R.drawable.ic_wx_bookmark),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    colorFilter = ColorFilter.tint(Color.White),
                )
                Text(
                    text = stringResource(if (kind == PlaceKind.HERE) R.string.place_here else R.string.place_saved),
                    color = OnSky.Secondary,
                    style = sceneText(CristalType.labelMedium),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                color = Color.White,
                style = sceneText(CristalType.titleLarge),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Image(
                painterResource(R.drawable.ic_wx_expand),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(OnSky.Text),
            )
        }
    }
}
