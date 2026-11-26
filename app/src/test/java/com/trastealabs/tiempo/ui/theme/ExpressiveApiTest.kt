package com.trastealabs.tiempo.ui.theme

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 013 (H3): con material3 1.5 las API de Expressive que pide la 012 se pueden usar (en 1.4.0 eran inaccesibles).
 * Solo vive en el test: ninguna pantalla las usa todavía.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpressiveApiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun expressiveThemeMotionAndButtonShapesCompose() {
        rule.setContent {
            MaterialExpressiveTheme(motionScheme = MotionScheme.standard()) {
                Button(onClick = {}, shapes = ButtonDefaults.shapes()) { Text("Probar") }
                LargeFlexibleTopAppBar(title = { Text("Barra") })
            }
        }
        rule.onNodeWithText("Probar").assertIsDisplayed()
        rule.onNodeWithText("Barra").assertIsDisplayed()
    }
}
