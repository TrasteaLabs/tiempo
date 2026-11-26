package com.trastealabs.tiempo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.glass.translucent
import com.trastealabs.tiempo.ui.theme.CristalType
import kotlinx.coroutines.delay

/** Lo que tarda en salir el esqueleto mientras se lee lo guardado: casi siempre llega antes y no se ve nada. */
internal const val LOADING_SKELETON_DELAY_MS = 400L

private val Bar = Color.White.copy(alpha = 0.13f)
private val Card = RoundedCornerShape(26.dp)

/**
 * La espera de la principal (020, Cristal §9.11): un esqueleto con la forma de lo que viene —el sitio, la cifra con su
 * cielo y su máxima y mínima, el resumen y las tarjetas de 24 horas y 10 días— y, donde va el resumen, la frase de qué se
 * está haciendo ([text]: «Buscando dónde estás…», «Descargando el tiempo de Granada…»). Sin frase (leer lo guardado),
 * solo la forma. No se desplaza: lo que no cabe queda fuera. Con [withPlace] falso, el hueco del nombre queda vacío:
 * ahí ya va el botón del sitio de verdad (`WithPlaceButton`).
 */
@Composable
fun MainSkeleton(text: String?, modifier: Modifier = Modifier, withPlace: Boolean = true) {
    Column(
        modifier
            .testTag("main_skeleton")
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = false)
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Cabecera: «Donde estoy» y el nombre, la fecha, la cifra de 112 sp, el cielo y la línea de máxima y mínima.
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (withPlace) {
                Block(width = 90.dp, height = 12.dp)
                Block(width = 170.dp, height = 28.dp)
            } else {
                Spacer(Modifier.height(48.dp))
            }
            Block(width = 150.dp, height = 12.dp)
            Spacer(Modifier.height(8.dp))
            Block(width = 150.dp, height = 96.dp, radius = 22.dp)
            Spacer(Modifier.height(4.dp))
            Block(width = 110.dp, height = 20.dp)
            Block(width = 200.dp, height = 14.dp)
        }
        // El resumen, con la frase de la espera en su sitio.
        Column(
            Modifier.padding(top = 12.dp).fillMaxWidth().translucent(Card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (text != null) {
                Text(
                    text = text,
                    color = Color.White,
                    style = sceneText(CristalType.titleMedium.copy(textAlign = TextAlign.Start)),
                    modifier = Modifier.testTag("main_skeleton_text"),
                )
            }
            Line(0.92f)
            Line(0.64f)
        }
        // «Próximas 24 horas»: título y la gráfica.
        SkeletonCard {
            Box(Modifier.padding(horizontal = 4.dp).fillMaxWidth().height(194.dp).clip(RoundedCornerShape(16.dp)).background(Bar))
        }
        // «Próximos 10 días»: título y filas de 48 dp.
        SkeletonCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                repeat(4) { Line(0.86f, height = 22.dp) }
            }
        }
    }
}

/**
 * [MainSkeleton] sin frase para `UiState.Loading`: solo si leer lo guardado pasa de [LOADING_SKELETON_DELAY_MS], y con
 * el fundido de efectos de M3, para que no parpadee en cada arranque.
 */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier) {
    var show by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_SKELETON_DELAY_MS)
        show = true
    }
    AnimatedVisibility(visible = show, enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()), modifier = modifier) {
        MainSkeleton(text = null)
    }
}

@Composable
private fun SkeletonCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().translucent(Card).padding(top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(horizontal = 16.dp).width(130.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Bar))
        Box(Modifier.padding(horizontal = 12.dp)) { content() }
    }
}

/** Las piezas de la cabecera van sobre la escena sin tarjeta: con el tono de la foto, o en las nubes claras no se ven. */
@Composable
private fun Block(width: Dp, height: Dp, radius: Dp = height / 2) {
    Box(Modifier.width(width).height(height).translucent(RoundedCornerShape(radius)))
}

@Composable
private fun Line(fraction: Float, height: Dp = 14.dp) {
    Box(Modifier.fillMaxWidth(fraction).height(height).clip(RoundedCornerShape(height / 2)).background(Bar))
}
