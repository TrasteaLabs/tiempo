package com.trastealabs.tiempo.update

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.SerializationException

/**
 * 021: lo que el actualizador guarda en el móvil, en `SharedPreferences("update")`: la última comprobación (y si
 * falló), el interruptor de betas, la versión descartada y la última versión nueva encontrada (para seguir
 * ofreciéndola sin volver a preguntar a GitHub en 24 h). Nada del usuario.
 */
class UpdateSettings(private val prefs: SharedPreferences) {
    /** Segundos epoch de la última consulta a GitHub, saliera bien o mal; 0 si nunca. */
    var lastCheck: Long
        get() = prefs.getLong(LAST_CHECK, 0)
        set(value) = prefs.edit { putLong(LAST_CHECK, value) }

    /** Cómo fue la última consulta; null si salió bien o no hubo ninguna. */
    var lastFailure: CheckFailure?
        get() = prefs.getString(LAST_FAILURE, null)?.let { name -> CheckFailure.entries.firstOrNull { it.name == name } }
        set(value) = prefs.edit { if (value == null) remove(LAST_FAILURE) else putString(LAST_FAILURE, value.name) }

    /** «Recibir versiones beta». Apagado de serie. */
    var beta: Boolean
        get() = prefs.getBoolean(BETA, false)
        set(value) = prefs.edit { putBoolean(BETA, value) }

    /** `versionCode` cuyo aviso de la principal se ha descartado; 0 si ninguno. En Ajustes se sigue ofreciendo. */
    var dismissedCode: Int
        get() = prefs.getInt(DISMISSED, 0)
        set(value) = prefs.edit { putInt(DISMISSED, value) }

    /** La versión nueva que encontró la última consulta buena; null si estaba al día. Un JSON roto se lee como null. */
    var found: AppRelease?
        get() = prefs.getString(FOUND, null)?.let { text ->
            try {
                releaseJson.decodeFromString(AppRelease.serializer(), text)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        set(value) = prefs.edit {
            if (value == null) remove(FOUND) else putString(FOUND, releaseJson.encodeToString(AppRelease.serializer(), value))
        }

    companion object {
        private const val FILE = "update"
        private const val LAST_CHECK = "last_check"
        private const val LAST_FAILURE = "last_failure"
        private const val BETA = "beta"
        private const val DISMISSED = "dismissed_code"
        private const val FOUND = "found"

        fun of(context: Context): UpdateSettings =
            UpdateSettings(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
