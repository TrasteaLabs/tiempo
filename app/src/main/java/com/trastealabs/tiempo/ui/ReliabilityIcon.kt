package com.trastealabs.tiempo.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.ReliabilityLevel

/** Anillo de cuatro trozos (H3, diseño B): muy alta 4 encendidos … baja 1. */
@DrawableRes
fun reliabilityIconRes(level: ReliabilityLevel): Int = when (level) {
    ReliabilityLevel.VERY_HIGH -> R.drawable.ic_rel_4
    ReliabilityLevel.HIGH -> R.drawable.ic_rel_3
    ReliabilityLevel.MEDIUM -> R.drawable.ic_rel_2
    ReliabilityLevel.LOW -> R.drawable.ic_rel_1
}

fun reliabilityLabel(level: ReliabilityLevel): String = when (level) {
    ReliabilityLevel.VERY_HIGH -> "Fiabilidad muy alta"
    ReliabilityLevel.HIGH -> "Fiabilidad alta"
    ReliabilityLevel.MEDIUM -> "Fiabilidad media"
    ReliabilityLevel.LOW -> "Fiabilidad baja"
}

/**
 * El anillo, sin texto. TalkBack lo lee con [contentDescription] (por defecto, «Fiabilidad alta»); en la fila
 * del día lleva la fecha delante. Aparece con un fundido corto: la previsión ya estaba y la fiabilidad llega después.
 */
@Composable
fun ReliabilityIcon(
    level: ReliabilityLevel,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = reliabilityLabel(level),
) {
    val alpha = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(Unit) { alpha.animateTo(1f, spec) }
    Image(
        painter = painterResource(reliabilityIconRes(level)),
        contentDescription = contentDescription,
        modifier = modifier.size(size).graphicsLayer { this.alpha = alpha.value },
    )
}
