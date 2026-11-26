package com.trastealabs.tiempo.refresh

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.data.MorningSettings
import com.trastealabs.tiempo.domain.nextMorning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefreshSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test fun `queda un unico trabajo periodico de 30 minutos con red (H11)`() {
        RefreshScheduler.schedule(context)
        RefreshScheduler.schedule(context)

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(RefreshScheduler.PERIODIC_WORK).get()
        assertEquals(1, infos.size)
        val info = infos.single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(TimeUnit.MINUTES.toMillis(30), info.periodicityInfo!!.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private fun scheduledActions(): Set<String?> =
        shadowOf(alarmManager).scheduledAlarms.map { shadowOf(it.operation).savedIntent.action }.toSet()

    @Test fun `programa la alarma de respaldo y la de medianoche`() {
        RefreshScheduler.schedule(context)
        assertEquals(
            setOf(RefreshAlarmReceiver.ACTION_REFRESH, RefreshAlarmReceiver.ACTION_MIDNIGHT),
            scheduledActions(),
        )
    }

    @Test fun `cada alarma de respaldo vuelve a armar tambien la de medianoche`() {
        // ColorOS deja a la alarma no exacta un margen proporcional a lo que falta: rearmarla cada
        // media hora acorta el retraso del cambio de día de hasta 1 h a unos 20 min.
        alarmManager.cancel(RefreshScheduler.pendingIntent(context, RefreshAlarmReceiver.ACTION_REFRESH))
        alarmManager.cancel(RefreshScheduler.pendingIntent(context, RefreshAlarmReceiver.ACTION_MIDNIGHT))
        assertTrue(scheduledActions().isEmpty())

        RefreshAlarmReceiver().onReceive(context, Intent(RefreshAlarmReceiver.ACTION_REFRESH))

        assertEquals(
            setOf(RefreshAlarmReceiver.ACTION_REFRESH, RefreshAlarmReceiver.ACTION_MIDNIGHT),
            scheduledActions(),
        )
    }

    @Test fun `la alarma de respaldo encola un refresco unico`() {
        RefreshScheduler.enqueueNow(context)
        RefreshScheduler.enqueueNow(context)
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(RefreshScheduler.ONE_TIME_WORK).get()
        assertEquals(1, infos.size)
    }

    @Test fun `el trabajo que arma enqueueNow lleva el origen pedido y por defecto WORKER`() {
        assertEquals(RefreshOrigin.ALARM, originOf(RefreshScheduler.refreshNowRequest(RefreshOrigin.ALARM).workSpec.input))
        assertEquals(RefreshOrigin.WORKER, originOf(RefreshScheduler.refreshNowRequest().workSpec.input))
    }

    @Test fun `originOf sin dato o con un dato que no es un origen da WORKER`() {
        assertEquals(RefreshOrigin.WORKER, originOf(androidx.work.Data.EMPTY))
        assertEquals(RefreshOrigin.WORKER, originOf(androidx.work.workDataOf(RefreshScheduler.KEY_ORIGIN to "OTRA_COSA")))
        assertEquals(RefreshOrigin.APP, originOf(androidx.work.workDataOf(RefreshScheduler.KEY_ORIGIN to "APP")))
    }

    @Test fun `el repintado de medianoche cae a las 00_01 del dia siguiente`() {
        val zone = TestData.zone
        assertEquals(TestData.at(2025, 6, 11, 0, 1), nextMidnightRepaint(TestData.at(2025, 6, 10, 23, 59), zone))
        assertEquals(TestData.at(2025, 6, 11, 0, 1), nextMidnightRepaint(TestData.at(2025, 6, 10, 0, 1), zone))
        assertEquals(TestData.at(2025, 6, 10, 0, 1), nextMidnightRepaint(TestData.at(2025, 6, 10, 0, 0), zone))
    }

    @Test fun `el repintado de medianoche respeta el cambio de hora`() {
        val zone = TestData.zone
        // 26-oct-2025: a las 03:00 vuelve a ser las 02:00; el día dura 25 h.
        assertEquals(TestData.at(2025, 10, 27, 0, 1), nextMidnightRepaint(TestData.at(2025, 10, 26, 12, 0), zone))
        assertEquals(TestData.at(2025, 3, 31, 0, 1), nextMidnightRepaint(TestData.at(2025, 3, 30, 12, 0), zone))
    }

    // --- 018 P2: la alarma de la notificación de la mañana ---

    private fun morningSettings(): MorningSettings {
        val prefs = context.getSharedPreferences("morning", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return MorningSettings(prefs)
    }

    private val morningNow = TestData.at(2026, 11, 16, 6, 0)

    @Test fun `la alarma de la manana se pone con el interruptor encendido a la siguiente hora y se cancela apagado`() {
        val settings = morningSettings()
        settings.setEnabled(true, morningNow, TestData.zone)

        RefreshScheduler.scheduleMorningAlarm(context, settings, morningNow, TestData.zone)

        val alarm = shadowOf(alarmManager).scheduledAlarms.single {
            shadowOf(it.operation).savedIntent.action == RefreshAlarmReceiver.ACTION_MORNING
        }
        assertEquals(TimeUnit.SECONDS.toMillis(TestData.at(2026, 11, 16, 7, 30)), alarm.triggerAtMs)
        assertEquals(TimeUnit.SECONDS.toMillis(nextMorning(morningNow, TestData.zone, 450)), alarm.triggerAtMs)

        settings.setEnabled(false, morningNow, TestData.zone)
        RefreshScheduler.scheduleMorningAlarm(context, settings, morningNow, TestData.zone)
        assertTrue(RefreshAlarmReceiver.ACTION_MORNING !in scheduledActions())
    }

    @Test fun `con la hora ya pasada la alarma cae a manana`() {
        val settings = morningSettings()
        val late = TestData.at(2026, 11, 16, 9, 0)
        settings.setEnabled(true, late, TestData.zone)
        RefreshScheduler.scheduleMorningAlarm(context, settings, late, TestData.zone)
        val alarm = shadowOf(alarmManager).scheduledAlarms.single {
            shadowOf(it.operation).savedIntent.action == RefreshAlarmReceiver.ACTION_MORNING
        }
        assertEquals(TimeUnit.SECONDS.toMillis(TestData.at(2026, 11, 17, 7, 30)), alarm.triggerAtMs)
    }

    @Test fun `schedule y la alarma de respaldo vuelven a armar la de la manana si esta encendida`() {
        MorningSettings.of(context).also { it.setEnabled(true, System.currentTimeMillis() / 1000, java.time.ZoneId.systemDefault()) }
        RefreshScheduler.schedule(context)
        assertTrue(RefreshAlarmReceiver.ACTION_MORNING in scheduledActions())

        alarmManager.cancel(RefreshScheduler.pendingIntent(context, RefreshAlarmReceiver.ACTION_MORNING))
        assertTrue(RefreshAlarmReceiver.ACTION_MORNING !in scheduledActions())
        RefreshAlarmReceiver().onReceive(context, Intent(RefreshAlarmReceiver.ACTION_REFRESH))
        assertTrue(RefreshAlarmReceiver.ACTION_MORNING in scheduledActions())
    }

    @Test fun `la alarma de la manana encola un trabajo unico sin exigir red y se rearma para el dia siguiente`() {
        MorningSettings.of(context).also { it.setEnabled(true, System.currentTimeMillis() / 1000, java.time.ZoneId.systemDefault()) }
        RefreshAlarmReceiver().onReceive(context, Intent(RefreshAlarmReceiver.ACTION_MORNING))
        RefreshAlarmReceiver().onReceive(context, Intent(RefreshAlarmReceiver.ACTION_MORNING))

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(RefreshScheduler.MORNING_WORK).get()
        assertEquals(1, infos.size)
        assertEquals(NetworkType.NOT_REQUIRED, infos.single().constraints.requiredNetworkType)
        assertTrue(RefreshAlarmReceiver.ACTION_MORNING in scheduledActions())
    }

    @Test fun `un cambio de huso rearma la alarma de la manana`() {
        MorningSettings.of(context).also { it.setEnabled(true, System.currentTimeMillis() / 1000, java.time.ZoneId.systemDefault()) }
        RefreshAlarmReceiver().onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        assertTrue(RefreshAlarmReceiver.ACTION_MORNING in scheduledActions())
    }

    @Test fun `el origen MORNING se lee del inputData`() {
        assertEquals(RefreshOrigin.MORNING, originOf(androidx.work.workDataOf(RefreshScheduler.KEY_ORIGIN to "MORNING")))
    }
}
