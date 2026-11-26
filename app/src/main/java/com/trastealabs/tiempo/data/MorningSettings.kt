package com.trastealabs.tiempo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.trastealabs.tiempo.domain.DEFAULT_MORNING_MINUTE
import com.trastealabs.tiempo.domain.morningAt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId

/**
 * 018 P2 (C8): la notificación de la mañana. Interruptor, hora (minutos desde medianoche) y el último día avisado, en
 * `SharedPreferences("morning")`. Apagada de salida.
 *
 * [lastDay] guarda dos cosas con una sola fecha: el día del último aviso enviado y el día que se salta porque se encendió
 * o se cambió la hora cuando la de hoy ya había pasado (empieza mañana, H13/H16). Se guardan aparte para que mover la hora
 * a una futura levante el segundo sin borrar el primero: nunca dos avisos el mismo día.
 */
class MorningSettings(private val prefs: SharedPreferences) {
    private val enabledState = MutableStateFlow(prefs.getBoolean(ENABLED, false))
    private val minuteState = MutableStateFlow(prefs.getInt(MINUTE, DEFAULT_MORNING_MINUTE).coerceIn(0, MINUTES_PER_DAY - 1))

    val enabled: StateFlow<Boolean> = enabledState.asStateFlow()
    val minuteOfDay: StateFlow<Int> = minuteState.asStateFlow()

    /** La fecha local `YYYY-MM-DD` más reciente entre el último aviso enviado y el día saltado; vacía si no hay ninguna. */
    val lastDay: String
        get() = maxOf(prefs.getString(SENT_DAY, "").orEmpty(), prefs.getString(SKIP_DAY, "").orEmpty())

    /** Enciende o apaga. Al encender con la hora de hoy ya pasada, hoy no se avisa: empieza mañana. */
    fun setEnabled(on: Boolean, now: Long, zone: ZoneId) {
        prefs.edit {
            putBoolean(ENABLED, on)
            if (on) putString(SKIP_DAY, skipFor(now, zone, minuteState.value))
        }
        enabledState.value = on
    }

    /** Cambia la hora. Una ya pasada hoy empieza mañana; una por delante vuelve a contar hoy (si hoy no se avisó ya). */
    fun setMinuteOfDay(minute: Int, now: Long, zone: ZoneId) {
        val m = minute.coerceIn(0, MINUTES_PER_DAY - 1)
        prefs.edit {
            putInt(MINUTE, m)
            putString(SKIP_DAY, skipFor(now, zone, m))
        }
        minuteState.value = m
    }

    /** Apunta que hoy ([day], `YYYY-MM-DD`) ya se avisó. */
    fun markSent(day: String) {
        // commit y no apply: si ColorOS mata el proceso justo tras avisar, el día ya está en disco y no se repite el aviso.
        prefs.edit(commit = true) { putString(SENT_DAY, day) }
    }

    private fun skipFor(now: Long, zone: ZoneId, minute: Int): String {
        val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
        return if (morningAt(today, zone, minute) <= now) today.toString() else ""
    }

    companion object {
        private const val FILE = "morning"
        private const val ENABLED = "enabled"
        private const val MINUTE = "minute"
        private const val SENT_DAY = "sent_day"
        private const val SKIP_DAY = "skip_day"
        private const val MINUTES_PER_DAY = 24 * 60

        /** Los mismos ajustes desde cualquier sitio con un [Context] (la alarma, el receptor): `SharedPreferences` es una sola por fichero. */
        fun of(context: Context): MorningSettings =
            MorningSettings(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
