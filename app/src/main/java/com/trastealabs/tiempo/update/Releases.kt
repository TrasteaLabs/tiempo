package com.trastealabs.tiempo.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lo que interesa de una release de la API de GitHub (`GET /repos/{owner}/{repo}/releases`). */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tag: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
    /** `sha256:<hex>`; GitHub lo da en los ficheros subidos desde 2025. Puede faltar. */
    val digest: String? = null,
)

/** Una versión de la app que se puede ofrecer: ya elegida, con su APK y su número. Se guarda tal cual en el móvil. */
@Serializable
data class AppRelease(
    val versionName: String,
    val versionCode: Int,
    val tag: String,
    val notes: String,
    val prerelease: Boolean,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    /** SHA-256 en hexadecimal (minúsculas) que da GitHub para el fichero; null si no lo da. */
    val sha256: String?,
    val pageUrl: String,
)

internal val releaseJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

/** Lee la respuesta de la lista de releases. Lanza [kotlinx.serialization.SerializationException] si no es una lista. */
fun parseReleases(body: String): List<GithubRelease> = releaseJson.decodeFromString(body)

private val SHA256 = Regex("[0-9a-f]{64}")

/** `sha256:ABC…` → `abc…`; cualquier otra cosa (otro algoritmo, longitud rara) → null. */
fun sha256FromDigest(digest: String?): String? {
    val value = digest?.trim()?.lowercase() ?: return null
    if (!value.startsWith("sha256:")) return null
    return value.removePrefix("sha256:").takeIf { SHA256.matches(it) }
}

/** Nombre de APK de la casa: `<slug>-X.Y.Z-N.apk`. Devuelve (versionName, versionCode) o null si no encaja. */
fun parseApkName(name: String, slug: String = UpdateConfig.APK_SLUG): Pair<String, Int>? {
    val match = Regex("""^${Regex.escape(slug)}-(\d+\.\d+\.\d+)-(\d+)\.apk$""").matchEntire(name) ?: return null
    val code = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
    return match.groupValues[1] to code
}

/** Tope de las notas que se guardan y se enseñan: las de GitHub pueden ser largas. */
const val MAX_NOTES_CHARS = 1500

/**
 * La release que se ofrecería con este canal, sin mirar la versión instalada: ni borradores, ni releases sin un APK
 * con nombre de la casa, ni «prerelease» salvo con [beta]. Si hay varias, la de mayor `versionCode` (la estable que
 * promociona una beta lleva el mismo número; con empate gana la primera de la lista, la más reciente).
 */
fun newestEligible(releases: List<GithubRelease>, beta: Boolean, slug: String = UpdateConfig.APK_SLUG): AppRelease? =
    releases.asSequence()
        .filter { !it.draft && (beta || !it.prerelease) }
        .mapNotNull { release ->
            val apk = release.assets.firstNotNullOfOrNull { asset ->
                parseApkName(asset.name, slug)?.takeIf { asset.downloadUrl.startsWith("https://") }?.let { asset to it }
            } ?: return@mapNotNull null
            val (asset, version) = apk
            AppRelease(
                versionName = version.first,
                versionCode = version.second,
                tag = release.tag,
                notes = release.body.orEmpty().trim().take(MAX_NOTES_CHARS),
                prerelease = release.prerelease,
                apkName = asset.name,
                apkUrl = asset.downloadUrl,
                apkSize = asset.size,
                sha256 = sha256FromDigest(asset.digest),
                pageUrl = release.htmlUrl,
            )
        }
        .fold(null as AppRelease?) { best, next -> if (best == null || next.versionCode > best.versionCode) next else best }

/** La versión que se ofrece: la [newestEligible] si es más nueva que la instalada; si no, null (al día). */
fun pickUpdate(releases: List<GithubRelease>, beta: Boolean, installedCode: Int, slug: String = UpdateConfig.APK_SLUG): AppRelease? =
    newestEligible(releases, beta, slug)?.takeIf { it.versionCode > installedCode }
