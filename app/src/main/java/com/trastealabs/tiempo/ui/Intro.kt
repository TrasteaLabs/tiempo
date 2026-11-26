package com.trastealabs.tiempo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import com.trastealabs.tiempo.ui.scene.MotionSettings
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

/*
 * La entrada al abrir (007, H15; quieta desde el 012 P6, H10): el velo del color de arranque se va y el astro sube a su
 * sitio. La foto y los paneles no se mueven. Reglas puras por milisegundo, las de la maqueta (CSS `inB` e `introState`).
 */

/** Lo que dura la entrada entera: el astro acaba aquí (H15: como mucho 1 s). */
const val INTRO_MS = 800L

private const val VEIL_MS = 420.0
private const val ASTRO_MS = 800.0
private const val ASTRO_RISE_DP = 36f
private const val ASTRO_SCALE_FROM = 0.92f

private fun easeOut(x: Double): Double = 1 - (1 - x.coerceIn(0.0, 1.0)).pow(3)

/** El velo (alfa): 1 al empezar y 0 desde los 420 ms. */
fun introVeil(ms: Long): Float = (1 - easeOut(ms / VEIL_MS)).toFloat()

/** El astro: cuánto le falta por subir (dp) y su escala; de 36 dp y 0,92 a 0 y 1 en 800 ms. */
fun introAstro(ms: Long): Pair<Float, Float> {
    val p = easeOut(ms / ASTRO_MS).toFloat()
    return ASTRO_RISE_DP * (1 - p) to 1 - (1 - ASTRO_SCALE_FROM) * (1 - p)
}

/**
 * Una entrada por proceso (H15, «en frío»): vive en `WeatherApp`, así que nace con cada proceso. La gasta la principal
 * con su primer estado que no sea la carga.
 */
class IntroGate {
    private val left = AtomicBoolean(true)

    /** `true` la primera vez; después, siempre `false`. */
    fun take(): Boolean = left.getAndSet(false)

    /** ¿Aún sin gastar? */
    val pending: Boolean get() = left.get()
}

/** La entrada en marcha: [ms] desde que empezó (leerlo solo al dibujar); [running] pasa a `false` al acabar. */
@Stable
class IntroState internal constructor() {
    private val msState = mutableLongStateOf(0L)
    val ms: Long get() = msState.longValue
    var running by mutableStateOf(true)
        private set

    internal fun advance(ms: Long) {
        msState.longValue = ms
        if (ms >= INTRO_MS) running = false
    }
}

/**
 * La entrada: con [play], corre por fotogramas hasta [INTRO_MS]; con [waiting] (aún sin decidir), quieta al principio
 * (el velo tapa del todo). `null` si no hay entrada o ya acabó.
 */
@Composable
fun rememberIntro(play: Boolean, waiting: Boolean = false): IntroState? {
    val intro = remember { IntroState() }
    LaunchedEffect(play) {
        if (!play || !intro.running) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (intro.running) {
            val now = withFrameMillis { it }
            intro.advance(now - start)
        }
    }
    return intro.takeIf { (play || waiting) && it.running }
}

/**
 * La entrada de la principal (H15). Con el primer estado que no sea [UiState.Loading] se gasta [gate]: si es
 * [UiState.Content] y Android deja animar ([settings]), sale; si es otro (primera vez, sin permiso, un guardado
 * descargando), no. Mientras carga y la entrada aún puede salir, el velo tapa del todo.
 */
@Composable
fun rememberMainIntro(state: UiState, gate: IntroGate, settings: MotionSettings): IntroState? {
    // La decisión se toma una vez y no cambia: no hace falta que sea estado (el cambio de [state] ya recompone).
    val decision = remember { IntroDecision() }
    val allowedAtStart = remember { settings.allowed() }
    if (decision.play == null && state !is UiState.Loading) {
        decision.play = gate.take() && state is UiState.Content && settings.allowed()
    }
    val waiting = decision.play == null && gate.pending && allowedAtStart
    return rememberIntro(play = decision.play == true, waiting = waiting)
}

private class IntroDecision {
    var play: Boolean? = null
}
