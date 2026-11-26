package com.trastealabs.tiempo.demo

import com.trastealabs.tiempo.update.ApkProblem
import com.trastealabs.tiempo.update.AppRelease
import com.trastealabs.tiempo.update.CheckFailure
import com.trastealabs.tiempo.update.DownloadState
import com.trastealabs.tiempo.update.UpdateState
import java.io.File

/**
 * 021: cada estado del actualizador con datos inventados, para verlo en la versión de desarrollo (que no se actualiza
 * nunca): `--es update current|never|offline|available|beta|downloading|verifying|ready|permission|installing|error|invalid|install-failed|dev`.
 * Con versión nueva también sale la línea de la principal; `--es screen update` abre Ajustes en «Versión de la app».
 */
val demoUpdateKinds = listOf(
    "current", "never", "offline", "available", "beta", "downloading", "verifying", "ready", "permission",
    "installing", "error", "invalid", "install-failed", "dev",
)

fun demoUpdate(kind: String?, now: Long): UpdateState? {
    if (kind == null || kind !in demoUpdateKinds) return null
    val release = AppRelease(
        versionName = if (kind == "beta") "1.2.0" else "1.1.0",
        versionCode = if (kind == "beta") 530 else 512,
        tag = if (kind == "beta") "v1.2.0-beta.1" else "v1.1.0",
        notes = "## Novedades\n- La ventana del día enseña la lluvia hora a hora.\n- **Arreglado:** el widget ya no se queda en blanco al girar.\n- Textos más claros en Ajustes.",
        prerelease = kind == "beta",
        apkName = "tiempo-1.1.0-512.apk",
        apkUrl = "https://github.com/trastealabs/tiempo/releases/download/v1.1.0/tiempo-1.1.0-512.apk",
        apkSize = 9_412_608,
        sha256 = "0".repeat(64),
        pageUrl = "https://github.com/trastealabs/tiempo/releases/tag/v1.1.0",
    )
    val apk = File("/demo/tiempo-1.1.0-512.apk")
    val base = UpdateState(
        enabled = kind != "dev",
        installedName = "1.0.0",
        installedCode = 500,
        beta = kind == "beta",
        checking = false,
        lastCheck = now - 2 * 3600,
        lastFailure = null,
        available = release,
        dismissedCode = 0,
        download = DownloadState.Idle,
    )
    return when (kind) {
        "current" -> base.copy(available = null)
        "never" -> base.copy(available = null, lastCheck = 0)
        "offline" -> base.copy(available = null, lastFailure = CheckFailure.OFFLINE, lastCheck = now - 26 * 3600)
        "dev" -> base.copy(available = null)
        "downloading" -> base.copy(download = DownloadState.Downloading(3_870_000, release.apkSize))
        "verifying" -> base.copy(download = DownloadState.Verifying)
        "ready" -> base.copy(download = DownloadState.Ready(apk))
        "permission" -> base.copy(download = DownloadState.NeedsPermission(apk))
        "installing" -> base.copy(download = DownloadState.Installing(apk))
        "error" -> base.copy(download = DownloadState.Failed(ApkProblem.DOWNLOAD))
        "invalid" -> base.copy(download = DownloadState.Failed(ApkProblem.SIGNATURE))
        "install-failed" -> base.copy(download = DownloadState.InstallFailed)
        else -> base
    }
}
