package com.trastealabs.tiempo.ui.scene

import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** El permiso para animar la escena (H8) y que la escena quieta no pide fotogramas (012 P6, H9). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkyClockTest {
    @get:Rule val rule = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private val owner = Owner()

    private fun moveTo(state: Lifecycle.State) = rule.runOnIdle {
        owner.registry.currentState = state
        // El reloj del test va a mano: que Compose vea ya el cambio de estado que hace el observador.
        Snapshot.sendApplyNotifications()
    }

    private var clock: SkyClock? = null

    private fun show(settings: MotionSettings) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                clock = rememberSkyClock(settings)
            }
        }
    }

    /** «Quitar animaciones»: la escala de duración de los animadores a 0 (API oculta; Robolectric la sombrea). */
    private fun setDurationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    @After fun tidy() = setDurationScale(1f)

    @Test fun `H9 con la app delante y sin dato nuevo la composicion queda en reposo, sin pedir fotogramas`() {
        moveTo(Lifecycle.State.RESUMED)
        show { true }
        rule.mainClock.advanceTimeBy(500)
        assertNotNull(clock)
        // Con un bucle de fotogramas (withFrameNanos) esta espera no terminaría: Compose nunca estaría en reposo.
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test fun `el permiso ya no cuenta fotogramas ni segundos`() {
        val source = java.io.File("src/main/java/com/trastealabs/tiempo/ui/scene/SkyClock.kt").readText()
        for (gone in listOf("withFrameNanos", "withFrameMillis", "onFrame", "SkyCover", "SkyConfig", "GLASS_FPS", "CALM_FPS", "maxFps")) {
            assertFalse("SkyClock.kt aún tiene $gone", source.contains(gone))
        }
    }

    @Test fun `H8 sin permiso para animar no hay SkyClock, y el cambio se nota al volver`() {
        var allowed = false
        moveTo(Lifecycle.State.RESUMED)
        show { allowed }
        rule.mainClock.advanceTimeBy(100)
        assertNull(clock)

        allowed = true
        rule.mainClock.advanceTimeBy(1_000)
        assertNull("cambió sin volver a la app", clock)

        moveTo(Lifecycle.State.CREATED)
        moveTo(Lifecycle.State.RESUMED)
        rule.mainClock.advanceTimeBy(100)
        assertNotNull("al volver no hay permiso", clock)

        allowed = false
        rule.mainClock.advanceTimeBy(1_000)
        assertNotNull("se quitó sin volver a la app", clock)

        moveTo(Lifecycle.State.CREATED)
        moveTo(Lifecycle.State.RESUMED)
        rule.mainClock.advanceTimeBy(100)
        assertNull("al volver sigue el permiso", clock)
    }

    @Test fun `AndroidMotionSettings mira el ahorro de bateria y Quitar animaciones`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = AndroidMotionSettings(context)
        assertTrue(settings.allowed())

        val power = shadowOf(context.getSystemService(PowerManager::class.java))
        power.setIsPowerSaveMode(true)
        assertFalse("con el ahorro de batería", settings.allowed())
        power.setIsPowerSaveMode(false)
        assertTrue(settings.allowed())

        setDurationScale(0f)
        assertFalse("con Quitar animaciones", ValueAnimator.areAnimatorsEnabled() && settings.allowed())
        assertFalse(settings.allowed())
    }
}
