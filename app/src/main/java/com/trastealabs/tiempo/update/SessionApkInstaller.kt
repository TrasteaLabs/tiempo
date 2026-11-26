package com.trastealabs.tiempo.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.trastealabs.tiempo.WeatherApp
import java.io.File
import java.io.IOException

/**
 * 021: instala con una sesión de [PackageInstaller]. La primera vez Android pide permitir «instalar apps
 * desconocidas» para Tiempo (`REQUEST_INSTALL_PACKAGES`): se abre esa pantalla y, al volver, se pulsa otra vez.
 * Se llama fuera del hilo principal (copia el APK a la sesión).
 */
class SessionApkInstaller(private val context: Context) : ApkInstaller {
    override fun install(apk: File): InstallStart {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return InstallStart.NEEDS_PERMISSION
        }
        val installer = pm.packageInstaller
        var sessionId = -1
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
                // Mutable: el instalador añade el estado y, si hace falta, la confirmación del usuario.
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, callback, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
            InstallStart.STARTED
        } catch (_: IOException) {
            abandon(installer, sessionId)
            InstallStart.FAILED
        } catch (_: SecurityException) {
            abandon(installer, sessionId)
            InstallStart.FAILED
        } catch (_: IllegalStateException) {
            abandon(installer, sessionId)
            InstallStart.FAILED
        }
    }

    private fun abandon(installer: PackageInstaller, id: Int) {
        if (id >= 0) runCatching { installer.abandonSession(id) }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.trastealabs.tiempo.update.INSTALL_STATUS"
    }
}

/**
 * El instalador avisa aquí: si pide confirmación, se abre la suya (la app está delante: el usuario acaba de pulsar);
 * si acaba bien, Android reinicia la app ya actualizada; si se cancela, se vuelve a «Instalar»; si falla, se dice.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SessionApkInstaller.ACTION_INSTALL_STATUS) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null) {
                    updater(context)?.installAborted()
                    return
                }
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { updater(context)?.installAborted() }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            // Cancelada por el usuario: vuelve a «Instalar». Cualquier otro fallo se dice («Android no ha podido instalarla»).
            PackageInstaller.STATUS_FAILURE_ABORTED -> updater(context)?.installAborted()
            else -> updater(context)?.installFailed()
        }
    }

    private fun updater(context: Context): Updater? = (context.applicationContext as? WeatherApp)?.updater
}
