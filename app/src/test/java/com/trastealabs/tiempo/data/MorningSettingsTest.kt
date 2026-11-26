package com.trastealabs.tiempo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.DEFAULT_MORNING_MINUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 018 P2 (H13, H16, C8): interruptor, hora y último día avisado; encender o mover la hora a una ya pasada empieza mañana. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MorningSettingsTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("morning_settings_test", Context.MODE_PRIVATE)
    private val zone = TestData.zone
    private val early = TestData.at(2026, 11, 16, 6, 0)
    private val late = TestData.at(2026, 11, 16, 9, 0)

    @Before fun setUp() {
        prefs.edit().clear().commit()
    }

    @Test fun `de salida esta apagada, a las 7_30 y sin dia`() {
        val s = MorningSettings(prefs)
        assertFalse(s.enabled.value)
        assertEquals(DEFAULT_MORNING_MINUTE, s.minuteOfDay.value)
        assertEquals(450, s.minuteOfDay.value)
        assertEquals("", s.lastDay)
    }

    @Test fun `lo que se cambia se guarda y lo lee otro objeto`() {
        val s = MorningSettings(prefs)
        s.setEnabled(true, early, zone)
        s.setMinuteOfDay(8 * 60 + 5, early, zone)
        s.markSent("2026-11-15")
        val again = MorningSettings(prefs)
        assertTrue(again.enabled.value)
        assertEquals(8 * 60 + 5, again.minuteOfDay.value)
        assertEquals("2026-11-15", again.lastDay)
    }

    @Test fun `encender antes de la hora de hoy no salta hoy`() {
        val s = MorningSettings(prefs)
        s.setEnabled(true, early, zone)
        assertEquals("", s.lastDay)
    }

    @Test fun `encender con la hora de hoy ya pasada marca hoy y empieza manana`() {
        val s = MorningSettings(prefs)
        s.setEnabled(true, late, zone)
        assertEquals("2026-11-16", s.lastDay)
    }

    @Test fun `cambiar la hora a una ya pasada hoy marca hoy`() {
        val s = MorningSettings(prefs)
        s.setMinuteOfDay(12 * 60, early, zone)
        assertEquals("", s.lastDay)
        s.setMinuteOfDay(8 * 60, late, zone)
        assertEquals("2026-11-16", s.lastDay)
    }

    @Test fun `mover la hora a una futura levanta el salto, pero no un aviso ya enviado`() {
        val s = MorningSettings(prefs)
        // Se enciende tarde (hoy saltado) y se pone una hora que aún no ha llegado: hoy vuelve a contar.
        s.setEnabled(true, late, zone)
        s.setMinuteOfDay(20 * 60, late, zone)
        assertEquals("", s.lastDay)
        // Pero si hoy ya salió el aviso, mover la hora a una futura no permite un segundo.
        s.markSent("2026-11-16")
        s.setMinuteOfDay(21 * 60, late, zone)
        assertEquals("2026-11-16", s.lastDay)
    }

    @Test fun `una hora fuera de rango se recorta al dia`() {
        val s = MorningSettings(prefs)
        s.setMinuteOfDay(99_999, early, zone)
        assertEquals(24 * 60 - 1, s.minuteOfDay.value)
    }
}
