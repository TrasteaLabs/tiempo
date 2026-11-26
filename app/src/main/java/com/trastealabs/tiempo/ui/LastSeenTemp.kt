package com.trastealabs.tiempo.ui

import android.content.SharedPreferences
import androidx.core.content.edit
import com.trastealabs.tiempo.domain.PlaceRef
import kotlin.math.roundToInt

/**
 * La última temperatura vista de «donde estoy» (012 P9, H12; C8): el punto de partida de la cifra que viaja. Vive en las
 * preferencias de siempre de `MainActivity`; la escribe `onStop` y no toca `data/`.
 */
class LastSeenTemp(private val prefs: SharedPreferences) {
    /** La temperatura entera de la última vez, o `null` si nunca se guardó. */
    fun get(): Int? = if (prefs.contains(KEY)) prefs.getInt(KEY, 0) else null

    fun put(c: Int) = prefs.edit { putInt(KEY, c) }

    private companion object {
        const val KEY = "last_seen_temp_c"
    }
}

/** Lo que enseñaba la principal para «donde estoy» (redondeado como la cabecera), o `null` si no enseñaba eso. */
fun lastSeenToSave(state: UiState): Int? =
    (state as? UiState.Content)?.takeIf { it.place == PlaceRef.Here }?.snapshot?.current?.temperatureC?.roundToInt()
