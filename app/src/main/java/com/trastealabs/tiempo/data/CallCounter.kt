package com.trastealabs.tiempo.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.trastealabs.tiempo.domain.ApiUsage
import com.trastealabs.tiempo.domain.plus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.time.ZoneId

/**
 * H12. Contador de llamadas a Open-Meteo de hoy y del mes, en el fichero `prefs` de siempre. Unos pocos números,
 * sin historial; en la zona del móvil, que es el «hoy» del usuario.
 */
class CallCounter(
    private val prefs: SharedPreferences,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val state = MutableStateFlow(
        ApiUsage(
            day = prefs.getString(DAY, "").orEmpty(),
            dayTenths = prefs.getLong(DAY_TENTHS, 0),
            month = prefs.getString(MONTH, "").orEmpty(),
            monthTenths = prefs.getLong(MONTH_TENTHS, 0),
        ),
    )
    val usage: StateFlow<ApiUsage> = state.asStateFlow()

    /** Synchronized: el trabajo de fondo y la app pueden pedir a la vez. */
    @Synchronized
    fun record(weightTenths: Long) {
        val next = state.value.plus(weightTenths, nowSeconds(), zone())
        prefs.edit {
            putString(DAY, next.day)
            putLong(DAY_TENTHS, next.dayTenths)
            putString(MONTH, next.month)
            putLong(MONTH_TENTHS, next.monthTenths)
        }
        state.value = next
    }

    private companion object {
        const val DAY = "api_day"
        const val DAY_TENTHS = "api_day_tenths"
        const val MONTH = "api_month"
        const val MONTH_TENTHS = "api_month_tenths"
    }
}

/**
 * Cuenta cada petición a `*.open-meteo.com` al salir hacia el servidor, antes de saber si sale bien o mal: así
 * cuenta Open-Meteo. Va como interceptor de red, así que suma cada intento (reintentos y redirecciones incluidos)
 * y no suma lo que no llega a salir (sin conexión).
 */
class CallCountingInterceptor(private val counter: CallCounter) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (url.host.endsWith("open-meteo.com")) counter.record(openMeteoWeightTenths(url))
        return chain.proceed(chain.request())
    }
}
