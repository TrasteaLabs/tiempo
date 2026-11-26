package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.Place
import kotlin.math.cos

interface PlaceLookup {
    fun nearest(position: LatLon): Place
}

/**
 * Lugar más cercano en la lista de lugares ([PlaceIndex]). Se carga la primera vez que se pide; el nombre se calcula
 * sin red (C9) y con el nombre principal de GeoNames, no con el español (H1 del 006).
 */
class AssetPlaceLookup(private val index: PlaceIndex) : PlaceLookup {
    override fun nearest(position: LatLon): Place {
        val n = index.size
        check(n > 0) { "La lista de lugares está vacía" }
        val lats = index.lat
        val lons = index.lon
        val cosLat = cos(Math.toRadians(position.lat))
        var best = 0
        var bestDist = Double.MAX_VALUE
        for (i in 0 until n) {
            val dLat = lats[i] - position.lat
            var dLon = Math.abs(lons[i] - position.lon)
            if (dLon > 180.0) dLon = 360.0 - dLon
            val dLonScaled = dLon * cosLat
            val d = dLat * dLat + dLonScaled * dLonScaled
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return Place(index.names[best], index.countryOf(best), position)
    }
}
