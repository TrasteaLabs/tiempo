package com.trastealabs.tiempo.refresh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.trastealabs.tiempo.widget.WeatherWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Alarma de respaldo (encola un refresco), alarma de medianoche (solo repinta los widgets), alarma de la hora de la
 * notificación de la mañana (encola `MorningWorker`, 018 P2) y vuelta a poner las alarmas tras reiniciar el móvil o actualizar la app.
 */
class RefreshAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                RefreshScheduler.enqueueNow(context, RefreshOrigin.ALARM)
                RefreshScheduler.scheduleBackupAlarm(context)
                // El móvil da a una alarma no exacta un margen según lo que falta (en el OPPO, hasta 1 h);
                // rearmarla cada media hora deja el cambio de día con unos 20 min de retraso como mucho.
                RefreshScheduler.scheduleMidnightAlarm(context)
                // Y la de la mañana, por si el móvil la perdió o el huso cambió sin avisar (018 P2, H16).
                RefreshScheduler.scheduleMorningAlarm(context)
            }
            ACTION_MORNING -> {
                // Primero la de mañana, para que un fallo al encolar no deje al día siguiente sin alarma.
                RefreshScheduler.scheduleMorningAlarm(context)
                RefreshScheduler.enqueueMorning(context)
            }
            Intent.ACTION_TIMEZONE_CHANGED -> RefreshScheduler.scheduleMorningAlarm(context)
            ACTION_MIDNIGHT -> {
                RefreshScheduler.scheduleMidnightAlarm(context)
                val pending = goAsync()
                scope.launch {
                    try {
                        WeatherWidget().updateAll(context)
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudieron repintar los widgets", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> RefreshScheduler.schedule(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.trastealabs.tiempo.action.REFRESH"
        const val ACTION_MIDNIGHT = "com.trastealabs.tiempo.action.MIDNIGHT"
        const val ACTION_MORNING = "com.trastealabs.tiempo.action.MORNING"
        private const val TAG = "RefreshAlarmReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
