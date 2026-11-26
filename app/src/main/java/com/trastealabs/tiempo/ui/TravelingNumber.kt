package com.trastealabs.tiempo.ui

import com.trastealabs.tiempo.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/*
 * La cifra que viaja (012 P9, H12): al abrir en frío, la temperatura cuenta desde la última que se vio hasta la de ahora
 * en 700 ms. Reglas puras por milisegundo (como `Intro.kt`); el reloj es el de la entrada ([IntroState]), así que no
 * hay un segundo bucle de fotogramas.
 */

/** Lo que dura la cuenta (cabe en [INTRO_MS]). */
const val TRAVEL_MS = 700L

/** Como mucho, tantos cambios de cifra: un salto grande sube de varios en varios grados. */
const val TRAVEL_MAX_STEPS = 40

/** Los puntos del fundido de entrada (sin cifra anterior): el alfa se interpola entre ellos. */
private const val FADE_STEPS = 8

/** A [atMs] desde el principio de la cuenta se ve [value] con transparencia [alpha] (1 = opaco). */
data class TravelStep(val atMs: Long, val value: Int, val alpha: Float)

private fun easeOutQuad(x: Float): Float = 1 - (1 - x.coerceIn(0f, 1f)).let { it * it }

/**
 * Los pasos de la cuenta de [from] a [to]:
 * - sin animaciones ([motion] `false`) o con [from] igual a [to]: un solo paso, quieto, con el valor final;
 * - sin cifra anterior ([from] `null`): el valor final entra con un fundido de 700 ms;
 * - si no, un paso por grado (los cambios, sin contar la cifra de partida), como mucho [TRAVEL_MAX_STEPS], que frenan
 *   al acercarse al final: cada cifra aparece cuando una curva de salida suave (sobre [TRAVEL_MS]) pasa por ella. Con
 *   pocos grados la cuenta acaba antes de los 700 ms (un solo grado cambia a los ~200 ms, no al final).
 */
fun travelSteps(from: Int?, to: Int, motion: Boolean): List<TravelStep> {
    if (!motion || from == to) return listOf(TravelStep(0, to, 1f))
    if (from == null) {
        return List(FADE_STEPS + 1) { i ->
            TravelStep(TRAVEL_MS * i / FADE_STEPS, to, easeOutQuad(i / FADE_STEPS.toFloat()))
        }
    }
    val delta = to - from
    val n = minOf(abs(delta), TRAVEL_MAX_STEPS)
    // El instante (0..1) en que la curva 1 − (1 − t)² pasa por la mitad del escalón i, que es cuando el redondeo cambia de cifra.
    fun crossing(i: Int) = 1.0 - sqrt(1.0 - (i - 0.5) / n)
    return List(n) { k ->
        val i = k + 1
        val value = if (n == abs(delta)) from + delta.sign * i else from + delta.sign * (abs(delta) * i / n.toDouble()).roundToInt()
        TravelStep((crossing(i) * TRAVEL_MS).roundToInt().toLong(), value, 1f)
    }
}

/**
 * Lo que se ve a los [ms] de empezar: el último paso que ya ha llegado (antes del primero, la cifra de partida [from]);
 * en el fundido, con el alfa interpolado entre un punto y el siguiente.
 */
fun travelAt(from: Int?, steps: List<TravelStep>, ms: Long): TravelStep {
    val k = steps.indexOfLast { it.atMs <= ms }
    if (k < 0) return TravelStep(0, from ?: steps.last().value, if (from == null) 0f else 1f)
    val step = steps[k]
    val next = steps.getOrNull(k + 1)
    if (next == null || next.value != step.value || next.alpha == step.alpha) return step
    val f = (ms - step.atMs).toFloat() / (next.atMs - step.atMs)
    return step.copy(alpha = step.alpha + (next.alpha - step.alpha) * f)
}

/**
 * La temperatura de la cabecera. Con [travel] (la entrada en marcha: solo al abrir en frío y con animaciones) cuenta
 * desde [from] hasta [to] siguiendo su reloj; sin ella, la cifra final, quieta. Solo se recompone al cambiar de cifra:
 * el fundido va en la capa, sin recomponer.
 */
@Composable
fun TravelingTemp(
    to: Int,
    from: Int?,
    travel: IntroState?,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    // El lector oye solo el valor final, una vez: ni el «°» suelto ni cada número que pasa en la cuenta. El Text queda
    // debajo de un Box que borra su semántica (en el árbol sin fusionar sigue ahí, para los tests).
    val spoken = stringResource(R.string.temp_spoken, to)
    if (travel == null) {
        Box(modifier.clearAndSetSemantics { contentDescription = spoken }) {
            Text(text = "$to°", color = color, style = style)
        }
        return
    }
    val steps = remember(from, to) { travelSteps(from, to, motion = true) }
    val value by remember(steps, from) { derivedStateOf { travelAt(from, steps, travel.ms).value } }
    Box(
        modifier
            .clearAndSetSemantics { contentDescription = spoken }
            .graphicsLayer { alpha = travelAt(from, steps, travel.ms).alpha },
    ) {
        Text(text = "$value°", color = color, style = style)
    }
}
