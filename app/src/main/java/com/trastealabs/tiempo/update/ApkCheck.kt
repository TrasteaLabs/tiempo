package com.trastealabs.tiempo.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** Por qué un APK bajado no se instala. */
enum class ApkProblem {
    /** No se pudo bajar entero (red, espacio). */
    DOWNLOAD,

    /** El SHA-256 no cuadra con el que da GitHub: llegó roto o lo cambiaron por el camino. */
    DIGEST,

    /** Android no lo reconoce como APK. */
    UNREADABLE,

    /** Es de otra app. */
    PACKAGE,

    /** No lo firmó la misma clave que la app instalada: Android no lo dejaría instalar encima. */
    SIGNATURE,

    /** Su número de versión no es el anunciado. */
    VERSION,
}

/** Lo que Android dice de un APK: paquete, número de versión y huellas SHA-256 de sus certificados de firma. */
data class ApkFacts(val packageName: String, val versionCode: Long, val signers: Set<String>)

/**
 * Las comprobaciones antes de instalar, sin Android: el fichero cuadra con el `digest` (si GitHub lo da), es de este
 * paquete, lo firmó el mismo certificado que la app instalada y trae el número anunciado. Null si todo está bien.
 */
fun checkApk(
    fileSha256: String,
    release: AppRelease,
    facts: ApkFacts?,
    expectedPackage: String,
    installedSigners: Set<String>,
): ApkProblem? = when {
    release.sha256 != null && !release.sha256.equals(fileSha256, ignoreCase = true) -> ApkProblem.DIGEST
    facts == null -> ApkProblem.UNREADABLE
    facts.packageName != expectedPackage -> ApkProblem.PACKAGE
    facts.signers.isEmpty() || installedSigners.isEmpty() || facts.signers != installedSigners -> ApkProblem.SIGNATURE
    facts.versionCode != release.versionCode.toLong() -> ApkProblem.VERSION
    else -> null
}

/** Lo que hace falta de Android para [checkApk]: separado para probar la decisión sin un APK de verdad. */
interface ApkInspector {
    /** Paquete, versión y firmas de un APK en disco; null si Android no lo puede leer. */
    fun inspect(apk: File): ApkFacts?

    /** Huellas de los certificados con que está firmada la app instalada. */
    fun installedSigners(): Set<String>

    val packageName: String
}

class AndroidApkInspector(private val context: Context) : ApkInspector {
    override val packageName: String get() = context.packageName

    override fun inspect(apk: File): ApkFacts? {
        val pm = context.packageManager
        val info: PackageInfo = (
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            ) ?: return null
        return ApkFacts(info.packageName, info.longVersionCode, signersOf(info))
    }

    override fun installedSigners(): Set<String> = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        signersOf(info)
    }.getOrDefault(emptySet())

    private fun signersOf(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        val certs: Array<Signature> = signing.apkContentsSigners ?: return emptySet()
        return certs.map { sha256Hex(it.toByteArray()) }.toSet()
    }
}

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
