package com.trastealabs.tiempo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.update.ApkProblem
import com.trastealabs.tiempo.update.AppRelease
import com.trastealabs.tiempo.update.CheckFailure
import com.trastealabs.tiempo.update.DownloadState
import com.trastealabs.tiempo.update.UpdateState
import com.trastealabs.tiempo.ui.theme.WeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 021: «Versión de la app» en cada estado y la línea de la principal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1800dp")
class UpdateSectionTest {
    @get:Rule val rule = createComposeRule()

    private val now = TestData.at(2026, 11, 26, 18, 0)
    private val zone = TestData.zone
    private val release = AppRelease(
        versionName = "1.1.0", versionCode = 512, tag = "v1.1.0", notes = "## Novedades\n- Una cosa\n- **Otra**",
        prerelease = false, apkName = "tiempo-1.1.0-512.apk", apkUrl = "https://github.com/x.apk", apkSize = 9_412_608,
        sha256 = null, pageUrl = "https://github.com/trastealabs/tiempo/releases/tag/v1.1.0",
    )
    private val base = UpdateState(
        enabled = true, installedName = "1.0.0", installedCode = 500, beta = false, checking = false,
        lastCheck = now - 3600, lastFailure = null, available = null, dismissedCode = 0, download = DownloadState.Idle,
    )
    private val actions = mutableListOf<UpdateAction>()

    private fun show(state: UpdateState) = rule.setContent {
        WeatherTheme { Box { UpdateSection(state, now, zone, { actions += it }) } }
    }

    @Test fun `al dia dice cuando se comprobo y deja buscar`() {
        show(base)
        rule.onNodeWithText("Instalada: 1.0.0 (500)").assertIsDisplayed()
        rule.onNodeWithText("Está al día").assertIsDisplayed()
        rule.onNodeWithText("Comprobado hoy a las 17:00").assertIsDisplayed()
        rule.onNodeWithTag("update_check_now").performClick()
        assertEquals(listOf<UpdateAction>(UpdateAction.CheckNow), actions)
    }

    @Test fun `sin comprobar todavia`() {
        show(base.copy(lastCheck = 0))
        rule.onNodeWithTag("update_never").assertIsDisplayed()
    }

    @Test fun `sin red o sin repositorio dice no se ha podido comprobar, sin mas`() {
        show(base.copy(lastFailure = CheckFailure.NOT_FOUND, lastCheck = now - 26 * 3600))
        rule.onNodeWithText("No se ha podido comprobar").assertIsDisplayed()
        rule.onNodeWithText("ayer a las 16:00", substring = true).assertIsDisplayed()
    }

    @Test fun `version nueva con notas en texto y el boton de bajarla`() {
        show(base.copy(available = release))
        rule.onNodeWithText("Hay una versión nueva: 1.1.0").assertIsDisplayed()
        rule.onNodeWithText("Novedades\n• Una cosa\n• Otra").assertIsDisplayed()
        rule.onNodeWithText("Bajar e instalar · 9,4 MB").assertIsDisplayed()
        rule.onNodeWithTag("update_download").performClick()
        assertEquals(listOf<UpdateAction>(UpdateAction.Download), actions)
    }

    @Test fun `bajando enseña cuanto lleva`() {
        show(base.copy(available = release, download = DownloadState.Downloading(3_870_000, 9_412_608)))
        rule.onNodeWithText("Bajando 3,9 MB de 9,4 MB").assertIsDisplayed()
    }

    @Test fun `un fichero que no vale lo dice y deja volver a intentarlo`() {
        show(base.copy(available = release, download = DownloadState.Failed(ApkProblem.SIGNATURE)))
        rule.onNodeWithText("No se ha instalado nada", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("update_retry").performClick()
        assertEquals(listOf<UpdateAction>(UpdateAction.Retry), actions)
    }

    @Test fun `sin permiso de instalar explica que hacer`() {
        show(base.copy(available = release, download = DownloadState.NeedsPermission(File("x.apk"))))
        rule.onNodeWithTag("update_permission").assertIsDisplayed()
        rule.onNodeWithTag("update_install").performClick()
        assertEquals(listOf<UpdateAction>(UpdateAction.Install), actions)
    }

    @Test fun `el interruptor de betas esta apagado y se enciende`() {
        show(base)
        rule.onNodeWithText("Recibir versiones beta").assertIsDisplayed()
        rule.onNodeWithTag("update_beta").performClick()
        assertEquals(listOf<UpdateAction>(UpdateAction.SetBeta(true)), actions)
        rule.onNodeWithTag("update_beta").assertTouchAtLeast48("betas")
        rule.onNodeWithTag("update_check_now").assertTouchAtLeast48("buscar ahora")
    }

    @Test fun `la version dev solo dice que no se actualiza`() {
        show(base.copy(enabled = false))
        rule.onNodeWithTag("update_dev").assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasTestTag("update_check_now")).fetchSemanticsNodes().size)
    }

    @Test fun `la linea de la principal lleva a Ajustes y se puede descartar`() {
        var opened = 0
        var dismissed = 0
        rule.setContent {
            WeatherTheme { Column { UpdateNotice(release, onOpen = { opened++ }, onDismiss = { dismissed++ }) } }
        }
        rule.onNodeWithText("Hay una versión nueva de Tiempo: 1.1.0").performClick()
        rule.onNodeWithTag("update_notice_dismiss").assertTouchAtLeast48("descartar")
        rule.onNodeWithTag("update_notice_dismiss").performClick()
        assertEquals(1, opened)
        assertEquals(1, dismissed)
    }

    @Test fun `notas en markdown pasan a texto`() {
        assertEquals("Título\n• uno\n  • dos\nnegrita y código", plainNotes("# Título\n- uno\n  * dos\n**negrita** y `código`"))
        assertEquals("", plainNotes("   "))
        assertEquals("a\n\nb", plainNotes("a\n\n\n\nb"))
    }

    @Test fun `megas con coma`() {
        assertEquals("9,4 MB", megabytes(9_412_608))
        assertEquals("0,0 MB", megabytes(0))
    }
}
