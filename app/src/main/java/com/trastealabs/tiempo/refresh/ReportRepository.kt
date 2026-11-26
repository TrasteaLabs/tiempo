package com.trastealabs.tiempo.refresh

import android.util.Log
import com.trastealabs.tiempo.data.ReportStore
import com.trastealabs.tiempo.data.ai.AI_DAILY_ASK
import com.trastealabs.tiempo.data.ai.AI_DAILY_HERE
import com.trastealabs.tiempo.data.ai.AI_DAILY_SAVED
import com.trastealabs.tiempo.data.ai.AiException
import com.trastealabs.tiempo.data.ai.AiFailure
import com.trastealabs.tiempo.data.ai.AiSettings
import com.trastealabs.tiempo.data.ai.KeyVault
import com.trastealabs.tiempo.data.ai.ASK_MAX_CHARS
import com.trastealabs.tiempo.data.ai.ReportReply
import com.trastealabs.tiempo.data.ai.ReportScope
import com.trastealabs.tiempo.data.ai.ReportWriter
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.domain.AiReport
import com.trastealabs.tiempo.domain.AskFailure
import com.trastealabs.tiempo.domain.AskResult
import com.trastealabs.tiempo.domain.askPrompt
import com.trastealabs.tiempo.domain.checkAnswer
import com.trastealabs.tiempo.domain.ReportFacts
import com.trastealabs.tiempo.domain.WeatherSnapshot
import com.trastealabs.tiempo.domain.checkReply
import com.trastealabs.tiempo.domain.reportDue
import com.trastealabs.tiempo.domain.reportFacts
import com.trastealabs.tiempo.domain.reportPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId

/** 018: lo que se espera a una respuesta a una pregunta antes de darla por perdida (H6). */
const val ASK_TIMEOUT_MS = 20_000L

/**
 * 016 P3 (C6). Pide el parte del día a la IA después de un refresco bueno, solo cuando toca (H10), con tope por día
 * (H11), una sola llamada, comprobación y guardado. Va fuera de `WeatherRepository`: quien llama ya ha guardado y
 * pintado la previsión, y nada de aquí la retrasa ni cambia su resultado (H12). Nunca lanza.
 */
class ReportRepository(
    private val writerFor: (AiProvider, String) -> ReportWriter,
    private val vault: KeyVault,
    private val settings: AiSettings,
    private val nowSeconds: () -> Long,
    private val timeoutMs: Long = 45_000,
    /** 018: se llama tras guardar un parte de «donde estoy» (el widget se repinta con la frase nueva). Nunca lanza hacia fuera. */
    private val onSaved: suspend () -> Unit = {},
) {
    private val mutex = Mutex()

    /** La pregunta libre va aparte del parte: no espera a un parte en curso ni lo frena (018). */
    private val askMutex = Mutex()

    /** Pide un parte nuevo si toca y se puede. Devuelve true si guardó uno. */
    suspend fun refreshIfDue(snapshot: WeatherSnapshot, store: ReportStore, scope: ReportScope): Boolean = try {
        val now = nowSeconds()
        val provider = settings.provider.value
        val facts = factsOf(snapshot, now)
        if (facts == null || vault.get(provider) == null) {
            false
        } else {
            mutex.withLock {
                // Dentro del Mutex se vuelve a mirar todo: la llamada que acaba de terminar puede haber guardado ya.
                val at = nowSeconds()
                val key = vault.get(provider)
                if (key == null || !allowed(scope, at) || !reportDue(store.read(), facts.basis, at)) {
                    false
                } else {
                    call(provider, key, facts, store, scope, at).isSuccess
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo pedir el parte", e)
        false
    }

    /** «Probar ahora» (H16): sin tope ni espera ni mirar si toca; cuenta y guarda. Nunca lanza: el fallo va en el [Result]. */
    suspend fun testNow(snapshot: WeatherSnapshot, store: ReportStore): Result<AiReport> = try {
        mutex.withLock {
            val now = nowSeconds()
            val provider = settings.provider.value
            val key = vault.get(provider)
            val facts = factsOf(snapshot, now)
            when {
                key == null -> fail(now, AiFailure.NO_KEY)
                facts == null -> fail(now, AiFailure.BAD_REPLY)
                else -> call(provider, key, facts, store, ReportScope.HERE, now)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Fallo al probar el parte", e)
        Result.failure(AiException(AiFailure.PROVIDER, "inesperado"))
    }

    /**
     * 018 (H2–H8). Una pregunta sobre la previsión guardada: no descarga nada. Cuenta el intento (también si falla o se
     * cancela), no toca `settings.status` y no escribe ni la pregunta ni la respuesta en disco ni en `Log` (H7).
     * Nunca lanza, salvo la cancelación.
     */
    suspend fun ask(snapshot: WeatherSnapshot, question: String): AskResult = try {
        val q = question.take(ASK_MAX_CHARS)
        val provider = settings.provider.value
        val key = vault.get(provider)
        when {
            q.isBlank() -> AskResult.Failed(AskFailure.OTHER)
            key == null -> AskResult.Failed(AskFailure.NO_KEY)
            else -> askMutex.withLock {
                val at = nowSeconds()
                val facts = factsOf(snapshot, at)
                when {
                    settings.usageAt(at).dayAsk >= AI_DAILY_ASK -> AskResult.Failed(AskFailure.CAP)
                    // Datos tan viejos que no queda ninguna hora por delante: no hay nada con que contestar, y no se llama.
                    facts == null -> AskResult.Failed(AskFailure.UNSURE)
                    else -> askCall(provider, key, facts, q, snapshot.fetchedAt, at)
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Sin Log: aquí podría arrastrarse la pregunta (H7).
        AskResult.Failed(AskFailure.OTHER)
    }

    private suspend fun askCall(
        provider: AiProvider,
        key: String,
        facts: ReportFacts,
        question: String,
        dataAt: Long,
        at: Long,
    ): AskResult {
        val reply: ReportReply = try {
            withTimeoutOrNull(ASK_TIMEOUT_MS) { writerFor(provider, key).write(askPrompt(facts, question)) }
                ?: return askFailed(at, AskFailure.TIMEOUT)
        } catch (e: CancellationException) {
            settings.recordAttempt(ReportScope.ASK, at) // cancelar no devuelve el intento
            throw e
        } catch (e: AiException) {
            return askFailed(at, askFailureOf(e.failure))
        } catch (e: Exception) {
            return askFailed(at, AskFailure.OTHER)
        }
        settings.record(ReportScope.ASK, reply, at)
        val answer = checkAnswer(reply.json, facts, question, at, dataAt)
        return if (answer == null) AskResult.Failed(AskFailure.UNSURE) else AskResult.Ok(answer)
    }

    private fun askFailed(at: Long, failure: AskFailure): AskResult {
        settings.recordAttempt(ReportScope.ASK, at)
        return AskResult.Failed(failure)
    }

    private fun askFailureOf(failure: AiFailure): AskFailure = when (failure) {
        AiFailure.NO_KEY -> AskFailure.NO_KEY
        AiFailure.BAD_KEY -> AskFailure.BAD_KEY
        AiFailure.NO_CREDIT -> AskFailure.NO_CREDIT
        AiFailure.OFFLINE -> AskFailure.OFFLINE
        AiFailure.TIMEOUT -> AskFailure.TIMEOUT
        AiFailure.BAD_REPLY -> AskFailure.CUT
        AiFailure.RATE_LIMIT, AiFailure.PROVIDER -> AskFailure.OTHER
    }

    private fun factsOf(snapshot: WeatherSnapshot, now: Long): ReportFacts? =
        reportFacts(snapshot, now, ZoneId.of(snapshot.zoneId))

    /** Tope del día para ese ámbito y espera de 25 min tras un fallo. */
    private fun allowed(scope: ReportScope, now: Long): Boolean {
        val used = settings.usageAt(now)
        val underCap = when (scope) {
            ReportScope.HERE -> used.dayHere < AI_DAILY_HERE
            ReportScope.SAVED -> used.daySaved < AI_DAILY_SAVED
            ReportScope.ASK -> used.dayAsk < AI_DAILY_ASK
        }
        val status = settings.status.value
        val waiting = status?.failure != null && now - status.at < RETRY_AFTER_S
        return underCap && !waiting
    }

    /** Una llamada: escribe, cuenta, comprueba y guarda. Apunta el resultado en [AiSettings]. */
    private suspend fun call(
        provider: AiProvider,
        key: String,
        facts: ReportFacts,
        store: ReportStore,
        scope: ReportScope,
        now: Long,
    ): Result<AiReport> {
        val reply: ReportReply = try {
            withTimeoutOrNull(timeoutMs) { writerFor(provider, key).write(reportPrompt(facts)) }
                ?: return failed(scope, now, AiFailure.TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            return failed(scope, now, e.failure)
        } catch (e: Exception) {
            return failed(scope, now, AiFailure.PROVIDER)
        }
        settings.record(scope, reply, now)
        val report = checkReply(reply.json, facts, provider, now)
        if (report == null || (report.today == null && report.days.isEmpty() && report.warnings.isEmpty())) {
            return fail(now, AiFailure.BAD_REPLY)
        }
        try {
            store.write(report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo guardar el parte", e)
            return fail(now, AiFailure.PROVIDER)
        }
        settings.setStatus(now, null)
        // 018: el widget enseña la frase del parte de «donde estoy»; que se repinte con la nueva.
        if (scope == ReportScope.HERE) {
            try {
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo avisar de que se guardó el parte", e)
            }
        }
        return Result.success(report)
    }

    /** Un intento sin respuesta: también cuenta para el tope del día (H11), sin tokens. */
    private fun failed(scope: ReportScope, now: Long, failure: AiFailure): Result<AiReport> {
        settings.recordAttempt(scope, now)
        return fail(now, failure)
    }

    private fun fail(now: Long, failure: AiFailure): Result<AiReport> {
        settings.setStatus(now, failure)
        return Result.failure(AiException(failure, failure.name))
    }

    private companion object {
        const val TAG = "ReportRepository"
        const val RETRY_AFTER_S = 25 * 60L
    }
}
