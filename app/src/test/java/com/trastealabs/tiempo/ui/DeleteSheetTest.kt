package com.trastealabs.tiempo.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.trastealabs.tiempo.domain.PlaceCardModel
import com.trastealabs.tiempo.domain.PlaceRef
import com.trastealabs.tiempo.domain.Sky
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.ui.scene.PhotoBands
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La hoja «Borrar Sevilla» (010, P2; 012, P5: `ModalBottomSheet`): se cierra por asa, velo y «atrás», y borra con «Borrar». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class DeleteSheetTest {
    @get:Rule val rule = createComposeRule()

    private val sevilla = PlaceCardModel(
        ref = PlaceRef.Saved("sev"), name = "Sevilla", photo = SkyPhoto.CLEAR_MILD, sky = Sky.PARTLY_CLOUDY,
        temp = 22, max = 24, min = 11, ageLabel = null, localTime = null, noPermission = false, isShown = false,
    )
    private var dismisses = 0
    private var confirms = 0

    private fun show(card: PlaceCardModel = sevilla) = rule.setContent {
        val context = LocalContext.current
        val bands = remember { PhotoBands(context) }
        DeleteSheet(card, bands, onDismiss = { dismisses++ }, onConfirm = { confirms++ })
    }

    @Test fun `abre con el nombre, el aviso de que se quita y la tira de la foto`() {
        show()
        rule.waitForIdle()
        rule.onNodeWithText("Borrar Sevilla").assertIsDisplayed()
        rule.onNodeWithText("Se quita de tus sitios", substring = true).assertExists()
        rule.onNodeWithTag("delete_mini").assertExists()
        rule.onNodeWithText("22°", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Borrar").assertExists()
    }

    @Test fun `tocar el velo cierra sin borrar`() {
        show()
        rule.waitForIdle()
        clickScrim(rule)
        rule.waitForIdle()
        assertEquals(1, dismisses)
        assertEquals(0, confirms)
    }

    @Test fun `atras cierra sin borrar`() {
        show()
        rule.waitForIdle()
        pressSystemBack(rule)
        rule.waitForIdle()
        assertEquals(1, dismisses)
        assertEquals(0, confirms)
    }

    @Test fun `el asa cierra sin borrar`() {
        show()
        rule.waitForIdle()
        rule.onNode(hasContentDescription("Drag handle")).performClick()
        rule.waitForIdle()
        assertEquals(1, dismisses)
        assertEquals(0, confirms)
    }

    @Test fun `Borrar confirma al momento y baja la hoja`() {
        show()
        rule.waitForIdle()
        rule.onNodeWithText("Borrar").performClick()
        assertEquals(1, confirms)
        rule.waitForIdle()
        assertEquals(1, dismisses)
    }

    @Test fun `dos toques seguidos en Borrar borran una sola vez`() {
        show()
        rule.waitForIdle()
        rule.onNodeWithText("Borrar").performClick()
        rule.onNodeWithText("Borrar").performClick()
        rule.waitForIdle()
        assertEquals(1, confirms)
        assertEquals(1, dismisses)
    }
}
