package com.trastealabs.tiempo.ui

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Looper
import com.trastealabs.tiempo.WeatherApp
import com.trastealabs.tiempo.domain.PlaceRef
import org.junit.Assert.assertFalse
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** H9 del 006: la app arranca, y se abre desde el widget, siempre en «donde estoy». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {
    /**
     * Con «Quitar animaciones» (007, 012 P6): sin entrada ni fundidos, la actividad se queda quieta del todo. Que la
     * escena no pide fotogramas con animaciones se prueba en `SkyClockTest` y `MainScreenTest`.
     */
    @Before fun stillSky() = setDurationScale(0f)

    @After fun restore() = setDurationScale(1f)

    private fun setDurationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    @Test fun `creada de cero empieza en donde estoy (H9)`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(PlaceRef.Here, activity.viewModel.selected.value)
    }

    @Test fun `abierta desde el widget con un guardado en pantalla vuelve a donde estoy (H9)`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        activity.viewModel.select(PlaceRef.Saved("p1"))
        assertEquals(PlaceRef.Saved("p1"), activity.viewModel.selected.value)
        controller.newIntent(Intent(activity, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_HERE, true))
        assertEquals(PlaceRef.Here, activity.viewModel.selected.value)
    }

    @Test fun `otro intent sin el extra no cambia de sitio`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        activity.viewModel.select(PlaceRef.Saved("p1"))
        controller.newIntent(Intent(activity, MainActivity::class.java))
        assertEquals(PlaceRef.Saved("p1"), activity.viewModel.selected.value)
    }

    // ---------- 007 · H15: la entrada, una por proceso ----------

    @Test fun `la actividad gasta la entrada con su primer estado y al recrearla no vuelve (H15)`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val app = controller.get().application as WeatherApp
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(app.introGate.pending)
        controller.recreate()
        shadowOf(Looper.getMainLooper()).idle()
        // El mismo proceso: la puerta sigue gastada.
        assertFalse(app.introGate.take())
    }

    @Test fun `abrir desde el widget con la app abierta no la repite (H15)`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val app = activity.application as WeatherApp
        shadowOf(Looper.getMainLooper()).idle()
        controller.newIntent(Intent(activity, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_HERE, true))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(app.introGate.take())
    }

    // ---------- 012 P9 · H12 y C8: la última temperatura vista ----------

    private fun prefs(activity: MainActivity) = activity.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)

    // Se llama a `saveLastSeen()` (lo que hace `onStop`) y no a `controller.stop()`: parar la actividad deja a los tests H15
    // de después con la principal en la carga (efecto de Robolectric al parar, sin relación con la app).
    @Test fun `sin datos que enseñar, parar la actividad no inventa una temperatura (H12)`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.saveLastSeen()
        assertFalse(prefs(activity).contains("last_seen_temp_c"))
    }

    @Test fun `parar la actividad sin datos no pisa la temperatura que ya estaba guardada (H12)`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        prefs(activity).edit().putInt("last_seen_temp_c", 17).commit()
        activity.saveLastSeen()
        assertEquals(17, prefs(activity).getInt("last_seen_temp_c", Int.MIN_VALUE))
    }
}
