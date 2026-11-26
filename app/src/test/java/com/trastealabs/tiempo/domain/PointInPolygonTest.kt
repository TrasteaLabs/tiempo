package com.trastealabs.tiempo.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointInPolygonTest {
    private val square = listOf(LatLon(39.0, -4.0), LatLon(41.0, -4.0), LatLon(41.0, -2.0), LatLon(39.0, -2.0), LatLon(39.0, -4.0))

    /** Una "U": el hueco del centro queda fuera. */
    private val concave = listOf(
        LatLon(0.0, 0.0), LatLon(0.0, 3.0), LatLon(3.0, 3.0), LatLon(3.0, 2.0),
        LatLon(1.0, 2.0), LatLon(1.0, 1.0), LatLon(3.0, 1.0), LatLon(3.0, 0.0),
    )

    @Test fun `un punto dentro`() = assertTrue(pointInPolygon(LatLon(40.0, -3.0), square))

    @Test fun `un punto fuera`() {
        assertFalse(pointInPolygon(LatLon(42.0, -3.0), square))
        assertFalse(pointInPolygon(LatLon(40.0, -5.0), square))
    }

    @Test fun `en un poligono concavo el hueco queda fuera y los brazos dentro`() {
        assertFalse(pointInPolygon(LatLon(2.0, 1.5), concave))
        assertTrue(pointInPolygon(LatLon(2.0, 0.5), concave))
        assertTrue(pointInPolygon(LatLon(2.0, 2.5), concave))
        assertTrue(pointInPolygon(LatLon(0.5, 1.5), concave))
    }

    @Test fun `un poligono de menos de tres puntos no contiene nada`() =
        assertFalse(pointInPolygon(LatLon(0.0, 0.0), listOf(LatLon(0.0, 0.0), LatLon(1.0, 1.0))))
}
