package com.trastealabs.tiempo.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.trastealabs.tiempo.domain.LatLon
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

interface LocationSource {
    suspend fun current(): LatLon?
    suspend fun lastKnown(): LatLon?
    fun hasBackgroundPermission(): Boolean
}

/** Ubicación del móvil con FusedLocationProvider. Sin permiso o sin señal devuelve `null`, nunca lanza. */
class FusedLocationSource(private val context: Context) : LocationSource {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    private fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission")
    override suspend fun current(): LatLon? {
        if (!hasPermission()) return null
        return withTimeoutOrNull(CURRENT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val token = CancellationTokenSource()
                cont.invokeOnCancellation { token.cancel() }
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it?.toLatLon()) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): LatLon? {
        if (!hasPermission()) return null
        return suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { if (cont.isActive) cont.resume(it?.toLatLon()) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }

    override fun hasBackgroundPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun Location.toLatLon() = LatLon(latitude, longitude)

    private companion object {
        const val CURRENT_TIMEOUT_MS = 10_000L
    }
}
