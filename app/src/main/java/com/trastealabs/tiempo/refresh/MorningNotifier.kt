package com.trastealabs.tiempo.refresh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.MorningStrings
import com.trastealabs.tiempo.domain.MorningText
import com.trastealabs.tiempo.ui.MainActivity

/**
 * 018 P2: la notificación de la mañana. Un canal («Parte de la mañana», importancia por defecto) y un id fijo: si por lo
 * que sea se publicara dos veces, la segunda sustituiría a la primera en lugar de apilarse.
 */
object MorningNotifier {
    const val CHANNEL_ID = "morning"
    const val NOTIFICATION_ID = 7301

    /** Los textos con cifras, de `strings.xml`. */
    fun strings(context: Context): MorningStrings = MorningStrings(
        title = { min, max -> context.getString(R.string.morning_title, min, max) },
        warning = { level, phenomenon -> context.getString(R.string.morning_warning, level, phenomenon) },
        dataAt = { time -> context.getString(R.string.morning_data_at, time) },
    )

    /** ¿Se puede publicar? Falso sin el permiso (o con las notificaciones de la app apagadas en el sistema). */
    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Publica [text]. Falso, sin publicar nada, si no se puede ([canPost]). Tocarla abre la app en «donde estoy». */
    fun post(context: Context, text: MorningText): Boolean {
        if (!canPost(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.morning_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_HERE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }
}
