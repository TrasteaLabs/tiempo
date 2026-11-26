package com.trastealabs.tiempo.ui

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.OnScreen
import com.trastealabs.tiempo.ui.theme.CristalType
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.data.PlaceHit
import com.trastealabs.tiempo.ui.glass.translucent
import com.trastealabs.tiempo.ui.glass.sceneText

/** Dorado del cursor y de la franja del tope (diseño B). */
private val Gold = Color(0xFFFFD27A)

/** La franja del tope de la maqueta (`.limit`): dorado casi opaco con tinta oscura. */
private val LimitGold = Color(244, 194, 13).copy(alpha = 0.95f)
private val LimitInk = Color(0xFF2A1804)

/**
 * El campo del buscador (`SearchField`): píldora de cristal de 50 dp (la `searchbox` de la maqueta) con la flecha de
 * volver ([onBack]), lo escrito, el cursor dorado y una ✕ que borra lo escrito.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Row(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .translucent(shape)
            .clip(shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconCircle(R.drawable.ic_wx_back, stringResource(R.string.back), onBack, iconSize = 22)
        Box(Modifier.weight(1f).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
            val style = CristalType.bodyLarge.copy(color = Color.White)
            if (value.isEmpty()) {
                Text(stringResource(R.string.search_place), color = OnScreen.Hint, style = style)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(Gold),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                // «Buscar» cierra el teclado y deja ver los resultados (la búsqueda ya va escribiendo).
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (value.isNotEmpty()) {
            IconCircle(R.drawable.ic_wx_close, stringResource(R.string.search_clear), { onValueChange("") }, iconSize = 20)
        }
    }
}

/** Botón redondo de 38 dp con un icono (flecha y ✕ del campo). */
@Composable
private fun IconCircle(icon: Int, description: String, onClick: () -> Unit, iconSize: Int) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier.minimumInteractiveComponentSize().size(38.dp).clip(CircleShape).clickable(interactionSource = press, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), description, Modifier.size(iconSize.dp), colorFilter = ColorFilter.tint(Color.White))
    }
}

/** Título de grupo («En España», «Guardados · 5 de 10»): sin versalitas ni espaciado (Cristal 1.2), al 72 %. */
@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = OnScreen.Secondary,
        style = sceneText(CristalType.titleSmall),
        modifier = modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
    )
}

/** Lo escrito en negrita dentro del nombre (los tramos que da la búsqueda). */
private fun boldName(name: String, bold: List<IntRange>): AnnotatedString = buildAnnotatedString {
    append(name)
    for (r in bold) {
        val start = r.first.coerceIn(0, name.length)
        val end = (r.last + 1).coerceIn(start, name.length)
        if (end > start) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
    }
}

/**
 * Un resultado (`PlaceResultRow`): el nombre con lo escrito en negrita y, debajo, provincia o región y país. A la
 * derecha, «+» o «✓ Guardado». Con 10 guardados ([full]) los no guardados se apagan y no hacen nada.
 */
@Composable
fun PlaceResultRow(hit: PlaceHit, saved: Boolean, full: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val where = listOf(hit.region, hit.country).filter { it.isNotEmpty() }.joinToString(", ")
    val full2 = listOf(hit.name, where).filter { it.isNotEmpty() }.joinToString(", ")
    val enabled = saved || !full
    val said = if (saved) stringResource(R.string.search_open_saved, full2) else stringResource(R.string.search_save_open, full2)
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(interactionSource = press, role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = said }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = boldName(hit.name, hit.bold),
                color = Color.White,
                style = sceneText(CristalType.bodyLarge),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = where,
                color = OnScreen.Tertiary,
                style = sceneText(CristalType.bodySmall),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        if (saved) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Image(painterResource(R.drawable.ic_wx_check), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(OnScreen.Secondary))
                Text(stringResource(R.string.search_saved), color = OnScreen.Secondary, style = sceneText(CristalType.labelMedium))
            }
        } else {
            Box(
                Modifier
                    .size(34.dp)
                    .alpha(if (full) 0.3f else 1f)
                    .border(1.5.dp, OnSky.Control, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_wx_add), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Color.White))
            }
        }
    }
}

/** Franja dorada del tope: «Ya tienes 10 sitios guardados, el máximo…» (H6). */
@Composable
fun LimitBanner(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        text = stringResource(R.string.search_full),
        color = LimitInk,
        style = sceneText(CristalType.bodySmall),
        modifier = modifier
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 2.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(LimitGold)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/** Mensaje centrado del buscador: la pista de 2 letras o «No encuentro…», con su línea pequeña. */
@Composable
fun SearchMessage(title: String, body: String, modifier: Modifier = Modifier, top: Int = 30) {
    Column(
        modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = top.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color.White, style = sceneText(CristalType.titleMedium.copy(textAlign = TextAlign.Center)))
        Text(
            body,
            color = OnScreen.Tertiary,
            style = sceneText(CristalType.bodyMedium.copy(textAlign = TextAlign.Center)),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** La primera búsqueda lenta (H4): un círculo que gira con «Buscando…» y tres filas grises. */
@Composable
fun SearchLoading(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                strokeWidth = 2.5.dp,
            )
            Text(stringResource(R.string.search_loading), color = Color.White, style = sceneText(CristalType.bodyMedium))
        }
        listOf(0.70f, 0.55f, 0.62f).forEach { w ->
            Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SkeletonBar(w)
                SkeletonBar(w - 0.25f)
            }
        }
    }
}

@Composable
private fun SkeletonBar(width: Float) {
    Box(
        Modifier
            .fillMaxWidth(width)
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.13f)),
    )
}
