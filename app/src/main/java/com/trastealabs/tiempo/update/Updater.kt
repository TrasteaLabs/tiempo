package com.trastealabs.tiempo.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Cómo va la bajada de una versión nueva. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val done: Long, val total: Long) : DownloadState {
        /** De 0 a 1; null si no se sabe el total. */
        val fraction: Float? get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
    }
    data object Verifying : DownloadState
    data class Ready(val apk: File) : DownloadState

    /** Android pide permitir «instalar apps desconocidas» para esta app; al volver, se pulsa otra vez. */
    data class NeedsPermission(val apk: File) : DownloadState

    /** Se ha pasado el APK al instalador de Android: falta que el usuario confirme. */
    data class Installing(val apk: File) : DownloadState
    data class Failed(val problem: ApkProblem) : DownloadState
    data object InstallFailed : DownloadState
}

/** Lo que pintan el aviso de la principal y la sección «Versión de la app» de Ajustes. */
data class UpdateState(
    /** False en la versión `.dev`: no se comprueba nunca. */
    val enabled: Boolean,
    val installedName: String,
    val installedCode: Int,
    val beta: Boolean,
    val checking: Boolean,
    /** Segundos epoch; 0 si nunca. */
    val lastCheck: Long,
    val lastFailure: CheckFailure?,
    /** La versión nueva que se ofrece; null si está al día (o no se sabe). */
    val available: AppRelease?,
    val dismissedCode: Int,
    val download: DownloadState,
) {
    /** La línea de la principal: hay versión nueva y no se ha descartado esa. */
    val showNotice: Boolean get() = enabled && available != null && available.versionCode != dismissedCode
}

/** Ponerle el APK delante al instalador de Android. */
fun interface ApkInstaller {
    fun install(apk: File): InstallStart
}

enum class InstallStart {
    /** Sesión enviada: Android enseña su confirmación. */
    STARTED,

    /** Falta el permiso de instalar apps desconocidas: se ha abierto su pantalla de Ajustes. */
    NEEDS_PERMISSION,
    FAILED,
}

/** ¿Toca preguntar a GitHub? Pasadas 24 h desde la última consulta, o si el reloj ha ido hacia atrás. */
fun updateCheckDue(now: Long, lastCheck: Long, interval: Long = UpdateConfig.CHECK_INTERVAL_SECONDS): Boolean =
    lastCheck <= 0 || now < lastCheck || now - lastCheck >= interval

/**
 * 021: el actualizador. Pregunta a GitHub al abrir la app (como mucho una vez al día) o con «Buscar ahora», nunca
 * desde el widget ni desde `refresh/`. Baja el APK solo cuando el usuario lo pide, lo comprueba y se lo pasa a
 * Android. Sin red, con límite o sin repositorio: no dice nada fuera de Ajustes y lo intenta otro día.
 */
class Updater(
    private val source: ReleaseSource,
    private val settings: UpdateSettings,
    private val downloader: ApkDownloader,
    private val inspector: ApkInspector,
    private val installer: ApkInstaller,
    private val downloadDir: File,
    private val installedCode: Int,
    private val installedName: String,
    private val enabled: Boolean,
    private val nowSeconds: () -> Long,
) {
    private val checkLock = Mutex()
    private val downloadLock = Mutex()

    private val mutable = MutableStateFlow(
        UpdateState(
            enabled = enabled,
            installedName = installedName,
            installedCode = installedCode,
            beta = settings.beta,
            checking = false,
            lastCheck = settings.lastCheck,
            lastFailure = settings.lastFailure,
            available = offerable(settings.found, settings.beta),
            dismissedCode = settings.dismissedCode,
            download = DownloadState.Idle,
        ),
    )
    val state: StateFlow<UpdateState> = mutable.asStateFlow()

    /** Al abrir la app. Devuelve true si ha preguntado a GitHub. */
    suspend fun checkIfDue(): Boolean {
        if (!enabled) return false
        if (!updateCheckDue(nowSeconds(), settings.lastCheck)) {
            cleanDownloads()
            return false
        }
        return check()
    }

    /** «Buscar ahora» de Ajustes: pregunta aunque no hayan pasado 24 h. */
    suspend fun checkNow(): Boolean = enabled && check()

    private suspend fun check(): Boolean {
        if (checkLock.isLocked) return false
        return checkLock.withLock {
            mutable.update { it.copy(checking = true) }
            val result = source.fetch()
            val now = nowSeconds()
            settings.lastCheck = now
            when (result) {
                is ReleasesResult.Ok -> {
                    val beta = settings.beta
                    val found = pickUpdate(result.releases, beta, installedCode)
                    settings.found = found
                    settings.lastFailure = null
                    mutable.update {
                        it.copy(checking = false, lastCheck = now, lastFailure = null, available = found, download = keptDownload(it, found))
                    }
                }
                // Lo encontrado antes se sigue ofreciendo: un fallo de red no dice que ya no haya versión nueva.
                is ReleasesResult.Failed -> {
                    settings.lastFailure = result.reason
                    mutable.update { it.copy(checking = false, lastCheck = now, lastFailure = result.reason) }
                }
            }
            cleanDownloads()
            true
        }
    }

    /** Enciende o apaga las betas. Al apagarlas deja de ofrecerse una beta ya encontrada; quien llama vuelve a buscar. */
    fun setBeta(on: Boolean) {
        settings.beta = on
        val found = offerable(settings.found, on)
        if (found == null) settings.found = null
        mutable.update { it.copy(beta = on, available = found, download = keptDownload(it, found)) }
    }

    /** «No avisar de esta versión» en la principal. Ajustes la sigue enseñando. */
    fun dismiss() {
        val code = mutable.value.available?.versionCode ?: return
        settings.dismissedCode = code
        mutable.update { it.copy(dismissedCode = code) }
    }

    /** Baja el APK de la versión ofrecida y lo comprueba. Solo cuando el usuario pulsa. */
    suspend fun download() {
        val release = mutable.value.available ?: return
        if (downloadLock.isLocked) return
        downloadLock.withLock {
            val dest = File(downloadDir, release.apkName)
            mutable.update { it.copy(download = DownloadState.Downloading(0, release.apkSize)) }
            val sha = try {
                withContext(Dispatchers.IO) { downloadDir.listFiles()?.forEach { it.delete() } }
                downloader.download(release.apkUrl, dest, release.apkSize) { done, total ->
                    mutable.update { it.copy(download = DownloadState.Downloading(done, total)) }
                }
            } catch (_: IOException) {
                dest.delete()
                mutable.update { it.copy(download = DownloadState.Failed(ApkProblem.DOWNLOAD)) }
                return
            }
            mutable.update { it.copy(download = DownloadState.Verifying) }
            val problem = withContext(Dispatchers.IO) {
                checkApk(sha, release, inspector.inspect(dest), inspector.packageName, inspector.installedSigners())
            }
            if (problem != null) {
                dest.delete()
                mutable.update { it.copy(download = DownloadState.Failed(problem)) }
            } else if (mutable.value.available?.versionCode != release.versionCode) {
                // Mientras bajaba, una comprobación ofreció otra versión: este fichero ya no es el que se enseña.
                dest.delete()
                mutable.update { it.copy(download = DownloadState.Idle) }
            } else {
                mutable.update { it.copy(download = DownloadState.Ready(dest)) }
            }
        }
    }

    /** Pasa el APK comprobado al instalador de Android. Desde «Instalando» también: el usuario cerró la ventana sin decidir. */
    suspend fun install() {
        val apk = when (val d = mutable.value.download) {
            is DownloadState.Ready -> d.apk
            is DownloadState.NeedsPermission -> d.apk
            is DownloadState.Installing -> d.apk
            else -> return
        }
        if (!apk.isFile) {
            mutable.update { it.copy(download = DownloadState.Idle) }
            return
        }
        val next = when (withContext(Dispatchers.IO) { installer.install(apk) }) {
            InstallStart.STARTED -> DownloadState.Installing(apk)
            InstallStart.NEEDS_PERMISSION -> DownloadState.NeedsPermission(apk)
            InstallStart.FAILED -> DownloadState.InstallFailed
        }
        mutable.update { it.copy(download = next) }
    }

    /**
     * Lo dice el receptor de la sesión de instalación: Android no ha podido instalarla (sin espacio, incompatible…).
     * Se dice en Ajustes; «Volver a intentarlo» la baja de nuevo.
     */
    fun installFailed() {
        if (mutable.value.download !is DownloadState.Installing) return
        mutable.update { it.copy(download = DownloadState.InstallFailed) }
    }

    /** Lo dice el receptor de la sesión de instalación: el usuario la canceló. El APK sigue para volver a intentarlo. */
    fun installAborted() {
        val apk = (mutable.value.download as? DownloadState.Installing)?.apk
        mutable.update { it.copy(download = if (apk != null && apk.isFile) DownloadState.Ready(apk) else DownloadState.InstallFailed) }
    }

    /** Tras un error: vuelve a bajar (si falló la bajada o el fichero) o a instalar (si falló Android). */
    suspend fun retry() {
        mutable.update { it.copy(download = DownloadState.Idle) }
        downloadAndInstall()
    }

    /** «Bajar e instalar»: si el fichero cuadra, abre directamente el instalador; si no, se queda en el error. */
    suspend fun downloadAndInstall() {
        download()
        if (mutable.value.download is DownloadState.Ready) install()
    }

    /** Con la versión ya instalada (o sin nada que ofrecer), los APK bajados sobran. */
    /** Borra lo bajado cuando ya no hace falta: nada en curso ni esperando a instalarse. */
    private suspend fun cleanDownloads() {
        if (downloadLock.isLocked || mutable.value.download != DownloadState.Idle) return
        withContext(Dispatchers.IO) { downloadDir.listFiles()?.forEach { it.delete() } }
    }

    /**
     * Si la versión ofrecida cambia (otra más nueva, o ninguna al apagar las betas), lo bajado o fallado de la anterior
     * deja de valer: «Instalar» no puede instalar una versión distinta de la que se enseña. Lo que está en marcha
     * (bajando, comprobando, en la ventana de Android) sigue; la bajada lo descarta al acabar.
     */
    private fun keptDownload(state: UpdateState, found: AppRelease?): DownloadState {
        if (found?.versionCode == state.available?.versionCode) return state.download
        return when (state.download) {
            is DownloadState.Ready, is DownloadState.NeedsPermission, is DownloadState.Failed, DownloadState.InstallFailed -> DownloadState.Idle
            else -> state.download
        }
    }

    private fun offerable(found: AppRelease?, beta: Boolean): AppRelease? =
        found?.takeIf { it.versionCode > installedCode && (beta || !it.prerelease) }
}
