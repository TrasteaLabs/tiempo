package com.trastealabs.tiempo.ui.motion

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Lo que recorre la pantalla que entra por el eje X. */
private val AxisShift = 30.dp

/** Sin animaciones: fundido de 150 ms, sin moverse. */
private const val REDUCED_FADE_MS = 150

/** La de debajo se apaga en el primer 35 % del paso; la que entra aparece del 35 al 100 %. */
private const val FADE_SPLIT = 0.35f

/** Atrás predictivo: lo que encoge una pantalla (hasta el 90 %). */
private const val SCREEN_BACK_SCALE = 0.90f

/**
 * El paso entre pantallas anima un número de 0 a 1000, no de 0 a 1: con un muelle de umbral por defecto, un 30 % por
 * hacer se quedaría sin acabar (la regla del manual).
 */
private const val PROGRESS_MAX = 1000f

/** Las pantallas de la app: la principal y las dos que entran de lado (011, Cristal §3: el eje X de Material 3). */
enum class Screen { MAIN, SETTINGS, PLACES }

/**
 * Lo que valen las dos capas para un progreso [p] del paso (0 = solo la principal, 1 = la secundaria entera): la que entra
 * ([topShiftDp], [topAlpha]) y la de debajo ([underShiftDp], [underAlpha]). Sin velo en ninguna (H3).
 */
data class AxisFrame(val topShiftDp: Float, val topAlpha: Float, val underShiftDp: Float, val underAlpha: Float)

/**
 * El eje X de M3 (H3): la que entra recorre 30 dp desde la derecha y aparece entre el 35 y el 100 % del paso; la de debajo
 * se va 30 dp a la izquierda y se apaga en el primer 35 %.
 */
fun axisFrame(p: Float): AxisFrame {
    val q = p.coerceIn(0f, 1f)
    val shift = AxisShift.value
    return AxisFrame(
        topShiftDp = shift * (1 - q),
        topAlpha = ((q - FADE_SPLIT) / (1 - FADE_SPLIT)).coerceIn(0f, 1f),
        underShiftDp = -shift * q,
        underAlpha = 1 - (q / FADE_SPLIT).coerceIn(0f, 1f),
    )
}

/** Sin animaciones (H12): nada se mueve; la secundaria funde y la principal se queda debajo hasta quedar tapada. */
fun reducedFrame(p: Float): AxisFrame = AxisFrame(0f, p.coerceIn(0f, 1f), 0f, 1f)

/**
 * Atrás predictivo de una pantalla (H10) con el gesto [gesture] de 0 a 1: su escala (de 1 a 0,90) y su alfa (se apaga en
 * el primer 35 % del gesto). Cancelado, el gesto vuelve a 0 y esto, a (1, 1).
 */
fun backFrame(gesture: Float): Pair<Float, Float> {
    val g = gesture.coerceIn(0f, 1f)
    return (1 - (1 - SCREEN_BACK_SCALE) * g) to (1 - g / FADE_SPLIT).coerceIn(0f, 1f)
}

/** Lo que dibuja una capa del host en este instante (para los tests): desplazamiento en dp, alfa y escala. */
data class LayerFrame(val shiftDp: Float, val alpha: Float, val scale: Float)

/** El estado de una capa del host. Para los tests. */
val LayerFrameKey = SemanticsPropertyKey<() -> LayerFrame>("LayerFrame")
private var SemanticsPropertyReceiver.layerFrame by LayerFrameKey

/** El progreso del paso, de 0 a 1. Para los tests. */
val ScreenProgressKey = SemanticsPropertyKey<() -> Float>("ScreenProgress")
private var SemanticsPropertyReceiver.screenProgress by ScreenProgressKey

/**
 * Cómo se mueve el contenido de la pantalla secundaria: su desplazamiento (dp) y su escala. Lo lee [secondaryContent];
 * el fondo desenfocado no lo lee, así que solo se funde (H6, M14: lo que desenfoca no se desplaza).
 */
class SecondaryMotion(val shiftDp: () -> Float, val scale: () -> Float)

val LocalSecondaryMotion = staticCompositionLocalOf { SecondaryMotion({ 0f }, { 1f }) }

/** Mueve el contenido de la secundaria con el paso del host: va dentro del fondo, no en él. */
@Composable
fun Modifier.secondaryContent(): Modifier {
    val motion = LocalSecondaryMotion.current
    return graphicsLayer {
        translationX = motion.shiftDp() * density
        val s = motion.scale()
        scaleX = s
        scaleY = s
    }
}

private fun Screen.tag() = when (this) {
    Screen.MAIN -> "screen_main"
    Screen.SETTINGS -> "screen_settings"
    Screen.PLACES -> "screen_places"
}

/**
 * Ajustes y Sitios entran y salen de lado con un solo progreso (011, H3-H6, H10, H12): las dos capas (la principal y la
 * secundaria) salen del mismo número, que corre de 0 a [PROGRESS_MAX] con el muelle por defecto del tema al entrar y el rápido
 * al volver, así que atrás a medias sigue desde donde está y abrir-volver-abrir no salta.
 *
 * [target] es la verdad (las banderas de quien llama, que se guardan); el host anima hacia ella. No hay gesto propio de
 * cierre: solo [onBack], desde la flecha o desde atrás (H5). Con el atrás predictivo, la secundaria encoge hasta el 90 %
 * y se funde en el primer 35 % del gesto. En reposo solo hay una capa compuesta, como antes del paso.
 *
 * [onSecondaryActive] avisa de cuándo hay una secundaria compuesta (entrando, entera o saliendo): quien llama para el
 * reloj del cielo, para que la principal sea una imagen quieta que solo se mueve y se funde (si no, el cielo la invalida
 * en cada fotograma y se vuelve a pintar con todos sus cristales: el 30–70 % de fotogramas lentos del primer intento).
 */
@Composable
fun ScreenHost(
    target: Screen,
    onBack: () -> Unit,
    main: @Composable () -> Unit,
    secondary: @Composable (Screen) -> Unit,
    modifier: Modifier = Modifier,
    onSecondaryActive: (Boolean) -> Unit = {},
) {
    val motion = LocalWindowMotion.current
    val scheme = MaterialTheme.motionScheme
    val enterSpec = scheme.defaultSpatialSpec<Float>()
    val backSpec = scheme.fastSpatialSpec<Float>()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(if (target == Screen.MAIN) 0f else PROGRESS_MAX) }
    val gesture = remember { Animatable(0f) }
    // La secundaria que se está yendo sigue compuesta hasta que acaba de irse.
    var last by remember { mutableStateOf(target.takeIf { it != Screen.MAIN }) }
    val layerScreen = if (target != Screen.MAIN) target else last
    LaunchedEffect(target, motion) {
        if (target != Screen.MAIN) {
            last = target
            gesture.snapTo(0f)   // reabrir tras un «atrás» con gesto: la secundaria vuelve entera, no encogida y apagada
            if (motion) {
                progress.animateTo(PROGRESS_MAX, enterSpec)
            } else {
                progress.animateTo(PROGRESS_MAX, tween(REDUCED_FADE_MS))
            }
        } else {
            if (motion) {
                progress.animateTo(0f, backSpec)
            } else {
                progress.animateTo(0f, tween(REDUCED_FADE_MS))
            }
            last = null
            gesture.snapTo(0f)
        }
    }
    val settled by remember { derivedStateOf { progress.value >= PROGRESS_MAX } }
    // Con el gesto de atrás acabado la secundaria ya no se ve: que no se quede los toques mientras la principal acaba de volver.
    val gestureDone by remember { derivedStateOf { gesture.value >= 1f } }
    val active = layerScreen != null
    LaunchedEffect(active) { onSecondaryActive(active) }

    fun moving(): Boolean = progress.value > 0f && progress.value < PROGRESS_MAX || gesture.value > 0f

    fun axis(): AxisFrame {
        val p = progress.value / PROGRESS_MAX
        return if (motion) axisFrame(p) else reducedFrame(p)
    }

    PredictiveBackHandler(enabled = target != Screen.MAIN) { events ->
        var moved = false
        try {
            events.collect {
                moved = true
                gesture.snapTo(it.progress)
            }
        } catch (e: CancellationException) {
            scope.launch { gesture.animateTo(0f, enterSpec) }
            throw e
        }
        // Sin progreso (la flecha del sistema, tres botones): es un «atrás» normal y vuelve por el eje.
        if (moved) gesture.animateTo(1f, backSpec)
        onBack()
    }

    Box(
        modifier
            .fillMaxSize()
            .semantics { screenProgress = { progress.value / PROGRESS_MAX } },
    ) {
        // La principal sigue compuesta (y oculta, alfa 0, sin accesibilidad) con la secundaria entera: componerla de nuevo al
        // volver tiraba el primer fotograma (50–60 ms). Con la secundaria entera no se dibuja (alfa 0).
        Box(
            Modifier
                .fillMaxSize()
                .testTag(Screen.MAIN.tag())
                .semantics {
                    layerFrame = {
                        val f = axis()
                        LayerFrame(f.underShiftDp * (1 - gesture.value), maxOf(f.underAlpha, backReveal(gesture.value)), 1f)
                    }
                }
                .graphicsLayer {
                    val f = axis()
                    translationX = f.underShiftDp * (1 - gesture.value) * density
                    alpha = maxOf(f.underAlpha, backReveal(gesture.value))
                    compositingStrategy = if (moving()) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                },
        ) {
            Box(Modifier.fillMaxSize().then(if (settled && gesture.value <= 0f) Modifier.clearAndSetSemantics {} else Modifier)) {
                main()
            }
        }
        if (layerScreen != null) {
            val shown = layerScreen
            val contentMotion = remember {
                SecondaryMotion(
                    shiftDp = { axis().topShiftDp * (1 - gesture.value) },
                    scale = { backFrame(gesture.value).first },
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(shown.tag())
                    .semantics {
                        layerFrame = {
                            LayerFrame(axis().topShiftDp * (1 - gesture.value), axis().topAlpha * backFrame(gesture.value).second, backFrame(gesture.value).first)
                        }
                    }
                    // La capa entera solo se funde: el fondo no se desplaza (H6); el contenido se mueve por dentro.
                    .graphicsLayer {
                        alpha = axis().topAlpha * backFrame(gesture.value).second
                        // Una imagen aparte solo durante el paso: mover o fundir la capa no la vuelve a pintar (con el cielo
                        // parado). En reposo, nada de capa de más: con ella `desplazar` pasaba del 10 % al 24 % de lentos.
                        compositingStrategy = if (moving()) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                    }
                    // Mientras la principal sigue debajo, la secundaria se queda los toques.
                    .then(if (gestureDone) Modifier else Modifier.pointerInput(Unit) {}),
            ) {
                CompositionLocalProvider(LocalSecondaryMotion provides contentMotion) {
                    secondary(shown)
                }
            }
        }
    }
}

/** Lo que se ve de la principal mientras la secundaria se funde con el gesto de atrás: lo que ella deja ver. */
private fun backReveal(gesture: Float): Float = 1 - backFrame(gesture).second
