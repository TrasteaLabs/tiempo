package com.trastealabs.tiempo.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.PlaceRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** H12 y C8: la última temperatura vista de «donde estoy», en las preferencias de siempre. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LastSeenTempTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("prefs-test", Context.MODE_PRIVATE)

    @Before fun clean() = prefs.edit().clear().commit().let { }

    @Test fun `vacio no hay temperatura`() {
        assertNull(LastSeenTemp(prefs).get())
    }

    @Test fun `guarda y lee, también bajo cero y cero`() {
        val last = LastSeenTemp(prefs)
        last.put(21)
        assertEquals(21, last.get())
        last.put(-5)
        assertEquals(-5, LastSeenTemp(prefs).get())
        last.put(0)
        assertEquals(0, LastSeenTemp(prefs).get())
    }

    @Test fun `usa la clave last_seen_temp_c`() {
        LastSeenTemp(prefs).put(7)
        assertEquals(7, prefs.getInt("last_seen_temp_c", Int.MIN_VALUE))
    }

    private val now = TestData.at(2025, 6, 10, 10, 20)

    private fun content(place: PlaceRef = PlaceRef.Here, c: Double = 12.4) = UiState.Content(
        snapshot = TestData.snapshot(now, currentC = c), now = now, offline = false, refreshing = false, place = place,
    )

    @Test fun `se guarda la temperatura entera que enseña donde estoy`() {
        assertEquals(12, lastSeenToSave(content(c = 12.4)))
        assertEquals(13, lastSeenToSave(content(c = 12.6)))
        assertEquals(-5, lastSeenToSave(content(c = -5.2)))
    }

    @Test fun `un sitio guardado, la carga o un mensaje no guardan nada`() {
        assertNull(lastSeenToSave(content(place = PlaceRef.Saved("p1"))))
        assertNull(lastSeenToSave(UiState.Loading))
        assertNull(lastSeenToSave(UiState.NoLocation))
    }
}
