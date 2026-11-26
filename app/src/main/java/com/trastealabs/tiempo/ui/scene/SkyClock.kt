package com.trastealabs.tiempo.ui.scene

import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** ¿Deja Android mover la escena? «Quitar animaciones» y el ahorro de batería (H8). Se lee en cada `ON_START`. */
fun interface MotionSettings {
    fun allowed(): Boolean
}

/** Los ajustes de verdad: animadores encendidos (escala de duración ≠ 0) y sin ahorro de batería. */
class AndroidMotionSettings(context: Context) : MotionSettings {
    private val power = context.applicationContext.getSystemService(PowerManager::class.java)

    override fun allowed(): Boolean = ValueAnimator.areAnimatorsEnabled() && power?.isPowerSaveMode != true
}

/**
 * El permiso para animar la escena (012 P6, D24: escena quieta). Ya no es un reloj: la escena no pide fotogramas; se
 * pinta una vez y otra cuando cambia el dato (o el `now` de `MainViewModel`, que salta a medianoche), con un fundido.
 * Tener un [SkyClock] significa que Android deja animar esos fundidos y la entrada (el astro); no tenerlo (`null`,
 * «Quitar animaciones» o ahorro de batería), que todo cambia casi de golpe.
 */
@Stable
class SkyClock

/**
 * El permiso de animar, o `null` si [settings] no deja (H8). Los ajustes se vuelven a mirar en cada `ON_START`, así que
 * un cambio se nota al volver a la app. No hay bucle de fotogramas: con la app quieta no se pide ninguno (H9).
 */
@Composable
fun rememberSkyClock(settings: MotionSettings): SkyClock? {
    val owner = LocalLifecycleOwner.current
    var allowed by remember(settings) { mutableStateOf(settings.allowed()) }
    DisposableEffect(owner, settings) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) allowed = settings.allowed() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val clock = remember { SkyClock() }
    return clock.takeIf { allowed }
}
