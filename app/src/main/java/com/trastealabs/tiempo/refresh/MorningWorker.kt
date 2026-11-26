package com.trastealabs.tiempo.refresh

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trastealabs.tiempo.WeatherApp
import com.trastealabs.tiempo.data.MorningMark
import com.trastealabs.tiempo.data.MorningSettings
import com.trastealabs.tiempo.data.RefreshLog
import com.trastealabs.tiempo.data.RefreshLogEntry
import com.trastealabs.tiempo.data.RefreshOutcome
import com.trastealabs.tiempo.data.ReportStore
import com.trastealabs.tiempo.domain.MorningStrings
import com.trastealabs.tiempo.domain.MorningText
import com.trastealabs.tiempo.domain.WeatherSnapshot
import com.trastealabs.tiempo.domain.morningAt
import com.trastealabs.tiempo.domain.morningDue
import com.trastealabs.tiempo.domain.morningText
import com.trastealabs.tiempo.domain.reportFacts
import com.trastealabs.tiempo.domain.summaryLines
import com.trastealabs.tiempo.domain.visibleReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId

/** Lo que se espera al refresco de antes de avisar (H15): pasado esto, se avisa con lo guardado. */
const val MORNING_REFRESH_TIMEOUT_MS = 25_000L

/** Y lo que se espera, después, a un parte nuevo de la IA si toca; el parte de antes (o las reglas) vale si no llega. */
const val MORNING_REPORT_TIMEOUT_MS = 20_000L

/**
 * 018 P2 (H14–H16). Todo lo que hace falta para avisar una mañana, con lo que toca a la red y al sistema detrás de
 * funciones para poder probarlo con el reloj fijado. Lo llaman [MorningWorker] (la alarma de la hora) y `RefreshWorker`
 * (la red de seguridad: si la alarma se perdió, el siguiente refresco avisa dentro de las 2 h de margen).
 *
 * Un [Mutex] y el día del último aviso ([MorningSettings.lastDay]) hacen que dos entradas a la vez, un reinicio o una
 * alarma repetida den una sola notificación al día.
 */
class MorningNotice(
    private val settings: MorningSettings,
    /** El refresco de «donde estoy» que lanza el aviso (`RefreshOrigin.MORNING`). */
    private val refresh: suspend () -> RefreshResult,
    /** La previsión guardada de «donde estoy». */
    private val snapshot: suspend () -> WeatherSnapshot?,
    /** Pide el parte de la IA si toca (con su tope y su espera; nunca lanza). */
    private val requestReport: suspend (WeatherSnapshot) -> Unit,
    private val reportStore: ReportStore,
    private val log: RefreshLog,
    private val canPost: () -> Boolean,
    private val post: (MorningText) -> Boolean,
    private val strings: () -> MorningStrings,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val phoneZone: () -> ZoneId = { ZoneId.systemDefault() },
    private val refreshTimeoutMs: Long = MORNING_REFRESH_TIMEOUT_MS,
    private val reportTimeoutMs: Long = MORNING_REPORT_TIMEOUT_MS,
) {
    private val mutex = Mutex()

    /**
     * Avisa si toca. [refreshFirst] = false lo usa la red de seguridad de `RefreshWorker`, que acaba de refrescar y
     * de pedir el parte. Devuelve true si publicó. Nunca lanza (salvo la cancelación).
     */
    suspend fun runIfDue(refreshFirst: Boolean = true): Boolean = try {
        mutex.withLock { runLocked(refreshFirst) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo avisar de la mañana", e)
        false
    }

    private suspend fun runLocked(refreshFirst: Boolean): Boolean {
        val zone = phoneZone()
        if (!due(zone)) return false
        // Sin permiso no se publica ni se marca: si lo da luego (dentro de las 2 h), avisará.
        if (!canPost()) return false

        if (refreshFirst) {
            val result = withTimeoutOrNull(refreshTimeoutMs) { refresh() }
            if (result is RefreshResult.Ok) {
                snapshot()?.let { fresh -> withTimeoutOrNull(reportTimeoutMs) { requestReport(fresh) } }
            }
        }
        // Dentro del Mutex se vuelve a mirar: mientras esperaba al refresco, el reloj ha corrido.
        val now = nowSeconds()
        if (!due(zone)) return false

        val saved = snapshot() ?: return false
        val dataZone = ZoneId.of(saved.zoneId)
        val facts = reportFacts(saved, now, dataZone)
        val report = visibleReport(reportStore.read(), facts?.basis)
        val rules = summaryLines(saved, now, dataZone).map { it.text }
        val text = morningText(saved, report, rules, now, dataZone, strings()) ?: return false
        if (!post(text)) return false

        val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
        settings.markSent(today.toString())
        writeLog(now, morningAt(today, zone, settings.minuteOfDay.value), text.withAi)
        return true
    }

    private fun due(zone: ZoneId): Boolean =
        morningDue(nowSeconds(), zone, settings.enabled.value, settings.minuteOfDay.value, settings.lastDay)

    /** Una línea aparte por aviso enviado (H16): hora real, hora pedida y con qué texto. Un fallo del disco no deshace el aviso. */
    private suspend fun writeLog(at: Long, askedAt: Long, withAi: Boolean) {
        try {
            log.append(
                RefreshLogEntry(
                    at = at,
                    origin = RefreshOrigin.MORNING,
                    outcome = RefreshOutcome.OK,
                    aemet = AemetOutcome.NOT_APPLICABLE,
                    morning = MorningMark(askedAt, withAi),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo escribir el aviso en el registro", e)
        }
    }

    private companion object {
        const val TAG = "MorningNotice"
    }
}

/** El trabajo único (`morning-notice`) que arma la alarma de la hora: refresca, pide el parte y avisa. Sin exigir red. */
class MorningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as WeatherApp).morningNotice.runIfDue(refreshFirst = true)
        return Result.success()
    }
}
