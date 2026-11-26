package com.trastealabs.tiempo.widget

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.ui.Header
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.domain.AiReport
import com.trastealabs.tiempo.domain.LOADING_PHOTO
import com.trastealabs.tiempo.domain.reportFacts
import com.trastealabs.tiempo.domain.skyPhotoNow
import com.trastealabs.tiempo.domain.visibleReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** H10 y H7: con el mismo snapshot, la cabecera de la app y el widget dan la misma temperatura, la misma hora y la misma foto. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetMatchesAppTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `widget y cabecera dan la misma temperatura y la misma hora`() {
        val now = TestData.at(2025, 6, 10, 10, 20)
        val snapshot = TestData.snapshot(now, currentC = 21.6, fetchedAt = TestData.at(2025, 6, 10, 9, 47))
        val temperature = "22°"
        val time = "09:47"

        // El móvil en la zona del sitio: si no, la cabecera añade «, hora de allí» (en GitHub el aparato está en UTC).
        rule.setContent { Header(snapshot, now, phoneZone = java.time.ZoneId.of(snapshot.zoneId)) }
        rule.onNode(androidx.compose.ui.test.hasText(temperature), useUnmergedTree = true).assertExists()
        rule.onNode(androidx.compose.ui.test.hasText("Martes, 10 de junio · $time")).assertExists()

        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext<Context>())
            setAppWidgetSize(DpSize(328.dp, 146.dp))
            provideComposable { WidgetContent(StoredState(snapshot, false), now) }
            // El grande es una imagen (010, P3): lo que dice, la descripción que lee TalkBack.
            val description = widgetDescription(widgetFrame(StoredState(snapshot, false), WidgetLayout.LARGE, now, ApplicationProvider.getApplicationContext()), ApplicationProvider.getApplicationContext<android.content.Context>())
            onNode(hasContentDescriptionEqualTo(description)).assertExists()
            assertTrue(description, description.contains(", $temperature,") && description.contains("Datos de las $time"))
        }
    }

    /** 018 P3: el widget enseña la frase del parte justo cuando la app enseña ese parte (`visibleReport`), y con la misma regla. */
    @Test fun `el widget ve la frase del parte cuando la app ve el parte y no cuando no`() {
        val now = TestData.at(2025, 6, 10, 10, 20)
        val snapshot = TestData.snapshot(now, currentC = 12.0)
        val state = StoredState(snapshot, false)
        val zone = java.time.ZoneId.of(snapshot.zoneId)
        val basis = reportFacts(snapshot, now, zone)!!.basis
        val phrase = "Sol toda la tarde; mañana, nubes"
        val cases = listOf(
            "de hoy" to AiReport(now, AiProvider.CLAUDE, basis, today = "Hoy…", widget = phrase),
            "cambiado de verdad" to AiReport(now, AiProvider.CLAUDE, basis.copy(maxC = basis.maxC + 5), today = "Hoy…", widget = phrase),
            "de otro día" to AiReport(now, AiProvider.CLAUDE, basis.copy(date = "2025-06-09"), today = "Hoy…", widget = phrase),
            "sin frase" to AiReport(now, AiProvider.CLAUDE, basis, today = "Hoy…"),
        )
        for ((label, report) in cases) {
            val appShows = visibleReport(report, basis) != null
            val widgetPhrase = widgetPhrase(state, report, now)
            assertEquals(label, appShows && report.widget != null, widgetPhrase != null)
            if (widgetPhrase != null) assertEquals(label, report.widget, widgetPhrase)
        }
    }

    @Test fun `widget y principal usan la misma foto`() {
        // La principal elige su foto con skyPhotoNow (MainScreen); el widget, con widgetPhoto.
        val cases = listOf(
            TestData.at(2025, 6, 10, 10, 20) to 21.6, // día
            TestData.at(2025, 6, 10, 22, 30) to 14.0, // noche
            TestData.at(2025, 6, 10, 10, 20) to -5.0,
            TestData.at(2025, 6, 10, 15, 0) to 44.0,
        )
        for ((now, c) in cases) {
            val snapshot = TestData.snapshot(now, currentC = c)
            assertEquals("$now $c", skyPhotoNow(snapshot, now), widgetPhoto(StoredState(snapshot, false), now))
        }
        assertEquals(LOADING_PHOTO, widgetPhoto(StoredState(null, false), cases[0].first))
    }
}
