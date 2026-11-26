package com.trastealabs.tiempo.data.ai

import android.content.SharedPreferences
import androidx.core.content.edit
import com.trastealabs.tiempo.domain.AiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId

/** Topes de llamadas de IA por día: uno para «donde estoy» y otro para los sitios guardados. */
const val AI_DAILY_HERE = 12
const val AI_DAILY_SAVED = 12

/** 018: preguntas libres a la IA por día (cuentan intentos) y largo máximo de una pregunta. Aparte de los topes del parte. */
const val AI_DAILY_ASK = 20
const val ASK_MAX_CHARS = 200

/** Para quién se pidió el parte. P3 lo importa de aquí en lugar de redefinirlo. */
enum class ReportScope { HERE, SAVED, ASK }

/** Llamadas y tokens de IA de hoy (por ámbito) y del mes. Las fechas son locales: `YYYY-MM-DD` y `YYYY-MM`. */
data class AiUsage(
    val day: String = "",
    val dayHere: Int = 0,
    val daySaved: Int = 0,
    /** 018: preguntas de hoy. */
    val dayAsk: Int = 0,
    val month: String = "",
    val monthCalls: Int = 0,
    val monthInputTokens: Long = 0,
    val monthOutputTokens: Long = 0,
) {
    /** Lo mismo visto desde [now]: si el día o el mes ya cambiaron, esa parte vuelve a cero. */
    fun at(now: Long, zone: ZoneId): AiUsage {
        val date = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
        val today = date.toString()
        val thisMonth = today.take(MONTH_LENGTH)
        val sameDay = day == today
        val sameMonth = month == thisMonth
        return AiUsage(
            day = today,
            dayHere = if (sameDay) dayHere else 0,
            daySaved = if (sameDay) daySaved else 0,
            dayAsk = if (sameDay) dayAsk else 0,
            month = thisMonth,
            monthCalls = if (sameMonth) monthCalls else 0,
            monthInputTokens = if (sameMonth) monthInputTokens else 0,
            monthOutputTokens = if (sameMonth) monthOutputTokens else 0,
        )
    }

    private companion object {
        const val MONTH_LENGTH = 7
    }
}

/** El último intento: cuándo (epoch s) y por qué falló, o `null` si salió bien. */
data class AiStatus(val at: Long, val failure: AiFailure? = null)

/**
 * 016 P2 (H13). Proveedor elegido, contador y último resultado, en `SharedPreferences("ai")`. Unos pocos números;
 * la clave vive en [KeyVault], nunca aquí. «Hoy» y «mes» con la zona del móvil, como `CallCounter`.
 */
class AiSettings(
    private val prefs: SharedPreferences,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val providerState = MutableStateFlow(
        runCatching { AiProvider.valueOf(prefs.getString(PROVIDER, null).orEmpty()) }.getOrDefault(AiProvider.CLAUDE),
    )
    private val usageState = MutableStateFlow(
        AiUsage(
            day = prefs.getString(DAY, "").orEmpty(),
            dayHere = prefs.getInt(DAY_HERE, 0),
            daySaved = prefs.getInt(DAY_SAVED, 0),
            dayAsk = prefs.getInt(DAY_ASK, 0),
            month = prefs.getString(MONTH, "").orEmpty(),
            monthCalls = prefs.getInt(MONTH_CALLS, 0),
            monthInputTokens = prefs.getLong(MONTH_IN, 0),
            monthOutputTokens = prefs.getLong(MONTH_OUT, 0),
        ),
    )
    private val statusState = MutableStateFlow(
        if (prefs.contains(STATUS_AT)) {
            AiStatus(
                at = prefs.getLong(STATUS_AT, 0),
                failure = runCatching { AiFailure.valueOf(prefs.getString(STATUS_FAILURE, null).orEmpty()) }.getOrNull(),
            )
        } else {
            null
        },
    )

    val provider: StateFlow<AiProvider> = providerState.asStateFlow()
    val usage: StateFlow<AiUsage> = usageState.asStateFlow()
    val status: StateFlow<AiStatus?> = statusState.asStateFlow()

    fun setProvider(p: AiProvider) {
        prefs.edit { putString(PROVIDER, p.name) }
        providerState.value = p
    }

    /** El contador visto desde [now] (para comprobar los topes pasada la medianoche sin esperar a otra llamada). */
    fun usageAt(now: Long): AiUsage = usageState.value.at(now, zone())

    /** Suma una llamada hecha: al ámbito de hoy y al mes, con los tokens de [reply]. */
    fun record(scope: ReportScope, reply: ReportReply, now: Long) = record(scope, reply.inputTokens, reply.outputTokens, now)

    /** Un intento que llegó a llamar al proveedor y no tuvo respuesta: cuenta para el tope, sin tokens. */
    fun recordAttempt(scope: ReportScope, now: Long) = record(scope, 0, 0, now)

    @Synchronized
    private fun record(scope: ReportScope, inputTokens: Int, outputTokens: Int, now: Long) {
        val base = usageState.value.at(now, zone())
        val next = base.copy(
            dayHere = base.dayHere + if (scope == ReportScope.HERE) 1 else 0,
            daySaved = base.daySaved + if (scope == ReportScope.SAVED) 1 else 0,
            dayAsk = base.dayAsk + if (scope == ReportScope.ASK) 1 else 0,
            monthCalls = base.monthCalls + 1,
            monthInputTokens = base.monthInputTokens + inputTokens,
            monthOutputTokens = base.monthOutputTokens + outputTokens,
        )
        prefs.edit {
            putString(DAY, next.day)
            putInt(DAY_HERE, next.dayHere)
            putInt(DAY_SAVED, next.daySaved)
            putInt(DAY_ASK, next.dayAsk)
            putString(MONTH, next.month)
            putInt(MONTH_CALLS, next.monthCalls)
            putLong(MONTH_IN, next.monthInputTokens)
            putLong(MONTH_OUT, next.monthOutputTokens)
        }
        usageState.value = next
    }

    /** El resultado del último intento: `failure == null` es que salió bien. */
    @Synchronized
    fun setStatus(at: Long, failure: AiFailure?) {
        prefs.edit {
            putLong(STATUS_AT, at)
            if (failure == null) remove(STATUS_FAILURE) else putString(STATUS_FAILURE, failure.name)
        }
        statusState.value = AiStatus(at, failure)
    }

    private companion object {
        const val PROVIDER = "provider"
        const val DAY = "usage_day"
        const val DAY_HERE = "usage_day_here"
        const val DAY_SAVED = "usage_day_saved"
        const val DAY_ASK = "usage_day_ask"
        const val MONTH = "usage_month"
        const val MONTH_CALLS = "usage_month_calls"
        const val MONTH_IN = "usage_month_in"
        const val MONTH_OUT = "usage_month_out"
        const val STATUS_AT = "status_at"
        const val STATUS_FAILURE = "status_failure"
    }
}
