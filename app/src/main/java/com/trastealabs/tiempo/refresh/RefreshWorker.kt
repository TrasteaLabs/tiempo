package com.trastealabs.tiempo.refresh

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.trastealabs.tiempo.WeatherApp
import com.trastealabs.tiempo.data.ai.ReportScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Refresco sin abrir la app (H11): lo lanzan el trabajo periódico y la alarma de respaldo.
 * Siempre acaba bien: si no hay red, el repositorio ya lo apunta y el siguiente turno lo reintenta.
 * Con la previsión ya guardada (y el widget repintado), la fiabilidad si toca (H8) y después el parte con IA si toca (016).
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WeatherApp
        if (app.repository.refresh(Trigger.BACKGROUND, originOf(inputData)) is RefreshResult.Ok) {
            app.reliability.refreshIfDue(System.currentTimeMillis() / 1000)
            // El parte con IA, con la previsión ya guardada y el widget repintado; no cambia el resultado (016 P3, H12).
            app.repository.state.first().snapshot?.let { app.reports.refreshIfDue(it, app.reportStore, ReportScope.HERE) }
        }
        // Red de seguridad de la notificación de la mañana (018 P2): si la alarma de la hora se perdió, avisa ahora, con
        // lo que acaba de guardar, mientras siga dentro de las 2 h de margen. Nunca cambia el resultado del refresco.
        try {
            app.morningNotice.runIfDue(refreshFirst = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("RefreshWorker", "La red de seguridad de la mañana falló", e)
        }
        return Result.success()
    }
}

/** Quién lo pidió (015 P2, registro de refrescos): la alarma de respaldo lo pone en el `inputData`; sin dato, el periódico. */
internal fun originOf(data: Data): RefreshOrigin =
    RefreshOrigin.entries.firstOrNull { it.name == data.getString(RefreshScheduler.KEY_ORIGIN) } ?: RefreshOrigin.WORKER
