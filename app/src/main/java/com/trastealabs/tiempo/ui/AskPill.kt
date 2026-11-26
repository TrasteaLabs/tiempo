package com.trastealabs.tiempo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.CristalType

/**
 * La entrada de la pregunta (018 P4, H1 y H5): una píldora al pie del Resumen, «Pregunta por el tiempo de {sitio}». Blanco al
 * 12 % con borde al 18 %: translúcida, sin desenfoque. Con el tope del día ([AskEntry.Capped]) sigue ahí, apagada y sin toque.
 * Con [AskEntry.Hidden] no se pinta nada.
 */
@Composable
fun AskPill(entry: AskEntry, place: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (entry == AskEntry.Hidden) return
    val capped = entry as? AskEntry.Capped
    val shape = RoundedCornerShape(percent = 50)
    val ink = if (capped == null) OnSky.Text else OnSky.Tertiary
    Row(
        modifier
            .testTag("ask_pill")
            .semantics(mergeDescendants = true) {}
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .then(if (capped == null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (capped == null) R.drawable.ic_wx_chat else R.drawable.ic_wx_block),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = capped?.let { stringResource(R.string.ask_pill_capped, it.used) } ?: stringResource(R.string.ask_pill, place),
            color = ink,
            style = sceneText(CristalType.bodyLarge),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp).testTag("ask_pill_text"),
        )
    }
}
