package com.trastealabs.tiempo.ui.scene

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** H12: la caché de lunas con el mapa de verdad (`assets/moon_map.jpg`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoonCacheTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun empty() = MoonCache.clear()

    @After fun clean() = MoonCache.clear()

    @Test fun `el mapa se decodifica a 1024 x 512`() {
        val map = MoonCache.loadMap(context)
        assertEquals(1024, map.width)
        assertEquals(512, map.height)
    }

    @Test fun `la luna llena mide 512 x 512 y la segunda vez es la misma`() {
        val first = MoonCache.litNow(context, 0.5, south = false)
        assertEquals(512, first.width)
        assertEquals(512, first.height)
        assertSame(first, MoonCache.litNow(context, 0.5, south = false))
        assertSame(first, MoonCache.peek(0.5, south = false))
        // El sur es otra.
        assertNotSame(first, MoonCache.litNow(context, 0.5, south = true))
    }

    @Test fun `con 5 fases distintas se queda con 4`() {
        listOf(0.1, 0.2, 0.3, 0.4, 0.6).forEach { MoonCache.litNow(context, it, south = false) }
        assertEquals(4, MoonCache.count)
        assertEquals(null, MoonCache.peek(0.1, south = false))
    }
}
