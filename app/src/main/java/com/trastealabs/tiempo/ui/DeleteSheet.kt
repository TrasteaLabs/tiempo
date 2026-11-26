package com.trastealabs.tiempo.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import com.trastealabs.tiempo.ui.scene.LocalBackdrop
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.OnSheet
import com.trastealabs.tiempo.ui.theme.CristalColors
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.PlaceCardModel
import com.trastealabs.tiempo.ui.glass.WhiteButton
import com.trastealabs.tiempo.ui.scene.PhotoBands
import com.trastealabs.tiempo.ui.theme.CristalType
import com.trastealabs.tiempo.ui.theme.Tabular

private val MiniShape = RoundedCornerShape(20.dp)

/**
 * La hoja «Borrar Sevilla» (010, P2; sustituye al diálogo; 012, P5: sobre `ModalBottomSheet`, misma firma): lleva el
 * título, qué pasa, la tira de la foto del sitio con su nombre y su temperatura, y la píldora «Borrar». Se baja y se cierra
 * como todas (dedo, asa, velo, «atrás»): [onDismiss] llega al acabar de bajar, sin borrar. «Borrar» llama a [onConfirm]
 * al momento y la hoja baja; [onDismiss] llega al acabar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteSheet(card: PlaceCardModel, bands: PhotoBands, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val latestConfirm by rememberUpdatedState(onConfirm)
    val latestDismiss by rememberUpdatedState(onDismiss)
    var confirmed by remember(card.ref) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("delete_sheet"),
        containerColor = CristalColors.SheetSurface,
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OnSky.Control) },
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(shadow = null),
            LocalBackdrop provides null,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.delete_sheet_title, card.name),
                    color = Color.White,
                    style = CristalType.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp),
                )
                Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 26.dp)) {
                    Text(
                        stringResource(R.string.delete_body),
                        color = OnSheet.Secondary,
                        style = CristalType.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Mini(card, bands)
                    WhiteButton(
                        stringResource(R.string.delete),
                        {
                            // Una sola vez: tocarlo otra vez mientras baja no vuelve a borrar.
                            if (!confirmed) {
                                confirmed = true
                                latestConfirm()
                                scope.launch { sheetState.hide() }.invokeOnCompletion { latestDismiss() }
                            }
                        },
                        Modifier.padding(top = 20.dp),
                    )
                }
            }
        }
    }
}

/** La tira de 72 dp con la foto del sitio, su nombre y su temperatura (`.mini` de la maqueta). */
@Composable
private fun Mini(card: PlaceCardModel, bands: PhotoBands) {
    val photo = card.photo
    var width by remember { mutableFloatStateOf(0f) }
    val heightPx = with(LocalDensity.current) { 72.dp.toPx() }
    val band by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null, photo, width,
    ) {
        value = if (photo != null && width > 0f) bands.getOrNull(photo, width.toInt(), heightPx.toInt()) else null
    }
    Row(
        Modifier
            .testTag("delete_mini")
            .padding(top = 16.dp)
            .fillMaxWidth()
            .height(72.dp)
            .clip(MiniShape)
            .onSizeChanged { width = it.width.toFloat() }
            .drawBehind {
                val image = band
                if (image == null) {
                    drawRect(Color.White.copy(alpha = 0.10f))
                } else {
                    drawIntoCanvas {
                        drawImage(
                            image,
                            dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                        )
                    }
                    // La tira de la maqueta: tinta de 45 % a 15 % de izquierda a derecha.
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color(10, 16, 40).copy(alpha = 0.45f), Color(10, 16, 40).copy(alpha = 0.15f)),
                            startX = 0f, endX = size.width,
                        ),
                    )
                }
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            card.name,
            color = Color.White,
            style = CristalType.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (card.temp != null) {
            Text("${card.temp}°", color = Color.White, style = CristalType.displaySmall.merge(Tabular), maxLines = 1)
        }
    }
}
