package com.trastealabs.tiempo.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.trastealabs.tiempo.ui.glass.GlassButton
import com.trastealabs.tiempo.ui.glass.WhiteButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 012 P8: `WeatherTheme` es `MaterialExpressiveTheme` con el color, la forma, la letra y el movimiento de la casa. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `WeatherTheme da el esquema de color, las formas y la letra de la casa`() {
        var colors: ColorScheme? = null
        var shapes: Shapes? = null
        var typography: Typography? = null
        var motion: MotionScheme? = null
        rule.setContent {
            WeatherTheme {
                colors = MaterialTheme.colorScheme
                shapes = MaterialTheme.shapes
                typography = MaterialTheme.typography
                motion = MaterialTheme.motionScheme
            }
        }
        rule.waitForIdle()
        assertEquals(cristalColorScheme, colors)
        assertEquals(cristalShapes, shapes)
        assertEquals(cristalTypography, typography)
        val expected = MotionScheme.standard()
        assertEquals(expected.javaClass, motion!!.javaClass)
        assertEquals(expected.defaultSpatialSpec<Float>(), motion!!.defaultSpatialSpec<Float>())
    }

    @Test fun `el esquema es el oscuro de la app, sin color dinamico`() {
        // La superficie y el texto de las hojas, y el blanco de los botones, salen del tema y no cambian.
        assertEquals(CristalColors.SheetSurface, cristalColorScheme.surface)
        assertEquals(CristalColors.DialogSurface, cristalColorScheme.surfaceContainerHigh)
        assertEquals(Color.White, cristalColorScheme.onSurface)
        assertEquals(Color.White, cristalColorScheme.primary)
        assertEquals(CristalColors.RippleInk, cristalColorScheme.onPrimary)
    }

    @Test fun `WhiteButton y GlassButton llaman a onClick`() {
        var white = 0
        var glass = 0
        rule.setContent {
            WeatherTheme {
                Column {
                    WhiteButton("Blanco", onClick = { white++ })
                    GlassButton("Cristal", onClick = { glass++ })
                }
            }
        }
        rule.onNodeWithTag("white_button").performClick()
        rule.onNodeWithTag("glass_button").performClick()
        assertEquals(1, white)
        assertEquals(1, glass)
        assertNotNull(cristalShapes)
    }
}
