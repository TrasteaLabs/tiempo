package com.trastealabs.tiempo.ui

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.PlaceCardModel
import com.trastealabs.tiempo.domain.PlaceRef
import com.trastealabs.tiempo.ui.glass.translucent
import com.trastealabs.tiempo.ui.scene.PhotoBands
import com.trastealabs.tiempo.ui.scene.SCREEN_LOOKS
import com.trastealabs.tiempo.ui.theme.OnScreen
import com.trastealabs.tiempo.ui.theme.AppFigures
import com.trastealabs.tiempo.ui.theme.CristalType
import com.trastealabs.tiempo.ui.theme.Tabular
import com.trastealabs.tiempo.ui.theme.skyLabel

private val CardShape = RoundedCornerShape(28.dp)

/** Alto de la postal de un guardado y de la de «Donde estoy» (`.pcA` de la maqueta). */
val PostalHeight = 112.dp
val HerePostalHeight = 148.dp

/** La tinta del velo (`INK` de la maqueta, #0A1028). */
private val Ink = Color(10, 16, 40)

/** Márgenes del texto: 20 dp, y 66 dp a la izquierda con la papelera (`.editing .pcA .in`). */
private val TextStart = 20.dp
private val TextStartEditing = 66.dp

/**
 * La postal de un sitio en la pantalla Sitios (Cristal, variante A): el trozo de la foto que le toca ([PlaceCardModel.photo],
 * la misma regla que la principal), escalado a su ancho y con su centro en la tabla `FRANJA`, con un velo uniforme medido por
 * foto, y encima el nombre, el cielo, de cuándo es el dato, y la temperatura con la máxima y la mínima. Sin foto (sin datos o
 * sin permiso), de cristal con «Aún sin datos» (o «Sin permiso de ubicación»). La del sitio que se ve lleva un borde de
 * 2 dp. Con [onDelete] (modo «Editar») el texto se aparta con el muelle rápido, sale la papelera a la izquierda y la
 * postal no se abre. La franja llega con un fundido corto: hasta entonces, la postal sale ya con su velo.
 */
@Composable
fun PlaceCard(
    card: PlaceCardModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    bands: PhotoBands,
    /** Solo la demo: sin textos, solo la foto y su velo, para medir el contraste. */
    bare: Boolean = false,
) {
    val here = card.ref == PlaceRef.Here
    val photo = card.photo.takeIf { !card.noPermission }
    val name = if (here && photo == null) stringResource(R.string.place_here) else card.name
    val skyText = card.sky?.let { stringResource(skyLabel(it)) }
    val said = when {
        photo == null || skyText == null || card.temp == null -> listOfNotNull(
            name,
            stringResource(if (card.noPermission) R.string.card_no_permission else R.string.card_no_data).lowercase(),
        ).joinToString(", ")
        else -> buildList {
            val max = card.max ?: card.temp
            val min = card.min ?: card.temp
            add(
                if (here) {
                    stringResource(R.string.card_talkback_here, name, skyText.lowercase(), card.temp, max, min)
                } else {
                    stringResource(R.string.card_talkback, name, skyText.lowercase(), card.temp, max, min)
                },
            )
            card.localTime?.let { add(stringResource(R.string.card_talkback_local, it)) }
            card.ageLabel?.let { add(it.replaceFirstChar(Char::lowercase)) }
        }.joinToString(", ")
    }
    val openLabel = stringResource(R.string.card_open, name)
    val height = if (here) HerePostalHeight else PostalHeight
    val editing = onDelete != null
    // El texto se aparta en su capa (011, H9): la caja ya está en su sitio final y solo se mueve el dibujo, sin volver a medir
    // la postal en cada fotograma.
    val textStart = if (editing && !here) TextStartEditing else TextStart
    val trash by animateFloatAsState(if (editing && !here) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "trash")
    val border = when {
        card.isShown -> Modifier.border(2.dp, OnScreen.Text, CardShape)
        photo != null -> Modifier.border(1.dp, Color.White.copy(alpha = 0.28f), CardShape)
        else -> Modifier
    }
    val press = remember { MutableInteractionSource() }
    Box(
        modifier
            .testTag("place_card")
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(height)
            // Sin foto, translúcida; con foto, su franja y un borde fino: sin sombra (012 P4, D24).
            .then(if (photo == null) Modifier.translucent(CardShape) else Modifier)
            .clip(CardShape)
            .then(if (photo != null) Modifier.photoBand(photo, here, bands) else Modifier)
            .then(border)
            // Una sola frase para TalkBack; en «Editar», sin acción (no se abre).
            .then(if (!editing) Modifier.clickable(interactionSource = press, role = Role.Button, onClickLabel = openLabel, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = said },
    ) {
        // Sobre la foto el velo ya da la lectura: sin la sombra de la escena.
        val style = if (photo != null) LocalTextStyle.current.copy(shadow = null) else LocalTextStyle.current
        if (!bare) CompositionLocalProvider(LocalTextStyle provides style) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .graphicsLayer { translationX = (TextStart + (TextStartEditing - TextStart) * trash - textStart).toPx() }
                    .padding(start = textStart, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    if (here && photo != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Image(painterResource(R.drawable.ic_wx_location), null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(Color.White))
                            Text(
                                stringResource(R.string.place_here),
                                color = OnScreen.Text,
                                style = LocalTextStyle.current.merge(CristalType.labelMedium),
                            )
                        }
                    }
                    Text(
                        text = name,
                        color = OnScreen.Text,
                        style = LocalTextStyle.current.merge(if (here) CristalType.headlineSmall else CristalType.titleLarge),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val line = when {
                        card.noPermission -> stringResource(R.string.card_no_permission)
                        photo == null || skyText == null -> stringResource(R.string.card_no_data)
                        card.localTime != null -> stringResource(R.string.card_local_time, skyText, card.localTime)
                        else -> skyText
                    }
                    Text(
                        text = line,
                        color = OnScreen.Text,
                        style = LocalTextStyle.current.merge(CristalType.bodyMedium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (photo != null && card.ageLabel != null) {
                        Text(
                            text = card.ageLabel,
                            color = OnScreen.Secondary,
                            style = LocalTextStyle.current.merge(CristalType.bodySmall),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                if (photo != null && card.temp != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${card.temp}°",
                            color = OnScreen.Text,
                            style = LocalTextStyle.current.merge(if (here) AppFigures.hereTemp else CristalType.displayMedium).merge(Tabular),
                            maxLines = 1,
                        )
                        if (card.max != null && card.min != null) {
                            Text(
                                text = stringResource(R.string.max_min, card.max.toString(), card.min.toString()),
                                color = OnScreen.Secondary,
                                style = LocalTextStyle.current.merge(CristalType.bodySmall).merge(Tabular),
                                maxLines = 1,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        if (!bare && !here && (onDelete != null || trash > 0.01f)) {
            val deletePress = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp)
                    .graphicsLayer {
                        alpha = trash
                        scaleX = 0.6f + 0.4f * trash
                        scaleY = 0.6f + 0.4f * trash
                    }
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(10, 14, 30).copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                    .then(if (onDelete != null) Modifier.clickable(interactionSource = deletePress, role = Role.Button, onClick = onDelete) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.ic_wx_delete),
                    contentDescription = stringResource(R.string.delete_place, name),
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }
        }
    }
}

/**
 * Pinta detrás la franja de [photo] para el hueco de este elemento, y encima el velo de tinta uniforme de
 * [com.trastealabs.tiempo.ui.scene.ScreenLook] (el de «Donde estoy» si [here]). El velo está desde el primer fotograma; la
 * franja llega con un fundido de 150 ms cuando está decodificada (fuera del hilo principal).
 */
@Composable
private fun Modifier.photoBand(photo: com.trastealabs.tiempo.domain.SkyPhoto, here: Boolean, bands: PhotoBands): Modifier {
    val look = SCREEN_LOOKS.getValue(photo)
    val veil = if (here) look.herePostalVeil else look.postalVeil
    var size by remember { mutableStateOf(IntSize.Zero) }
    val band by produceState(initialValue = null as ImageBitmap?, photo, size) {
        value = if (size.width > 0 && size.height > 0) bands.getOrNull(photo, size.width, size.height) else null
    }
    val fade = remember(photo) { Animatable(0f) }
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(band) { if (band != null) fade.animateTo(1f, fadeSpec) }
    return this
        .onSizeChanged { size = it }
        .drawBehind {
            drawRect(Color(0xFF26324F))
            band?.let {
                drawImage(
                    it,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    alpha = fade.value,
                )
            }
            drawRect(Ink, alpha = veil)
        }
}
