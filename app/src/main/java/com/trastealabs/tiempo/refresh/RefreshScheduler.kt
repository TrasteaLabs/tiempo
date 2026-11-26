package com.trastealabs.tiempo.refresh

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.trastealabs.tiempo.data.MorningSettings
import com.trastealabs.tiempo.domain.nextMorning
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Siguiente 00:01 local estrictamente después de [now]: cuando "hoy" ya es el día nuevo. */
fun nextMidnightRepaint(now: Long, zone: ZoneId): Long {
    val local = Instant.ofEpochSecond(now).atZone(zone)
    val today = local.toLocalDate().atTime(REPAINT_TIME).atZone(zone)
    val next = if (today.toEpochSecond() > now) today else local.toLocalDate().plusDays(1).atTime(REPAINT_TIME).atZone(zone)
    return next.toEpochSecond()
}

private val REPAINT_TIME: LocalTime = LocalTime.of(0, 1)

/**
 * Programa el refresco en segundo plano (H11) y el repintado de medianoche:
 * - trabajo periódico de 30 min con red (WorkManager lo conserva entre reinicios);
 * - alarma de respaldo cada 30 min, que salta también en reposo y encola un refresco urgente
 *   (ColorOS retrasa mucho los trabajos periódicos);
 * - alarma a las 00:01 que solo repinta los widgets, para que "hoy" cambie aunque no haya red;
 * - alarma exacta a la hora de la notificación de la mañana, si está encendida (018 P2).
 * Las alarmas se pierden al reiniciar el móvil: [RefreshAlarmReceiver] las vuelve a poner.
 */
object RefreshScheduler {
    const val PERIODIC_WORK = "refresh-periodic"
    const val ONE_TIME_WORK = "refresh-now"
    const val MORNING_WORK = "morning-notice"

    /** Clave del `inputData` con quién pidió el refresco (015 P2); el periódico no la lleva y es `WORKER`. */
    const val KEY_ORIGIN = "origin"
    private const val INTERVAL_MINUTES = 30L

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<RefreshWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic)
        scheduleBackupAlarm(context)
        scheduleMidnightAlarm(context)
        scheduleMorningAlarm(context)
    }

    /**
     * Refresco único y urgente; si ya hay uno pendiente, no se duplica (y cuenta como el que estaba).
     * [origin] solo lo lee el registro de refrescos.
     */
    fun enqueueNow(context: Context, origin: RefreshOrigin = RefreshOrigin.WORKER) {
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.KEEP, refreshNowRequest(origin))
    }

    internal fun refreshNowRequest(origin: RefreshOrigin = RefreshOrigin.WORKER): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(network)
            .setInputData(workDataOf(KEY_ORIGIN to origin.name))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

    fun scheduleBackupAlarm(context: Context) {
        alarms(context).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(INTERVAL_MINUTES),
            pendingIntent(context, RefreshAlarmReceiver.ACTION_REFRESH),
        )
    }

    /** La zona es la del móvil: la del snapshot podría no existir todavía y, viajando, coincide con ella. */
    fun scheduleMidnightAlarm(context: Context) {
        val at = nextMidnightRepaint(System.currentTimeMillis() / 1000, ZoneId.systemDefault())
        alarms(context).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            TimeUnit.SECONDS.toMillis(at),
            pendingIntent(context, RefreshAlarmReceiver.ACTION_MIDNIGHT),
        )
    }

    /**
     * La alarma de la hora de la notificación de la mañana (018 P2). Exacta (`USE_EXACT_ALARM`, que se concede al
     * instalar); si el sistema no la deja, no exacta. ColorOS la convierte en una con ventana (medido: 3 min 46 s tarde con
     * 5 min de margen), y por eso se rearma con cada alarma de respaldo y `RefreshWorker` avisa de red de seguridad. Apagada, se cancela. Se rearma desde `schedule`, la de respaldo,
     * la propia alarma, el cambio de huso y el cambio de ajuste. [now] y [zone] solo se pasan en los tests.
     */
    fun scheduleMorningAlarm(
        context: Context,
        settings: MorningSettings = MorningSettings.of(context),
        now: Long = System.currentTimeMillis() / 1000,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val manager = alarms(context)
        val intent = pendingIntent(context, RefreshAlarmReceiver.ACTION_MORNING)
        if (!settings.enabled.value) {
            manager.cancel(intent)
            return
        }
        val at = TimeUnit.SECONDS.toMillis(nextMorning(now, zone, settings.minuteOfDay.value))
        if (manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    /** El trabajo único que avisa: urgente y **sin** exigir red (sin conexión también avisa, con lo guardado). */
    fun enqueueMorning(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(MORNING_WORK, ExistingWorkPolicy.KEEP, morningRequest())
    }

    internal fun morningRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<MorningWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    internal fun pendingIntent(context: Context, action: String): PendingIntent =PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, RefreshAlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
