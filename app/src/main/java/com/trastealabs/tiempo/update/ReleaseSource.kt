package com.trastealabs.tiempo.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Por qué no se ha podido comprobar. En la app todas se dicen igual («No se ha podido comprobar»). */
enum class CheckFailure {
    /** Sin red o se cortó. */
    OFFLINE,

    /** 403 o 429: GitHub limita a 60 consultas por hora y por IP sin clave. */
    RATE_LIMITED,

    /** 404: el repositorio aún no existe o no es público. */
    NOT_FOUND,

    /** Cualquier otra respuesta, o una que no se entiende. */
    OTHER,
}

sealed interface ReleasesResult {
    data class Ok(val releases: List<GithubRelease>) : ReleasesResult
    data class Failed(val reason: CheckFailure) : ReleasesResult
}

fun interface ReleaseSource {
    suspend fun fetch(): ReleasesResult
}

/**
 * La lista de releases de GitHub, sin clave. Nunca lanza: todo fallo es un [ReleasesResult.Failed].
 *
 * 022: cada cambio de la app sale como beta, así que la estable puede quedar fuera de la primera página. Si la lista
 * no trae ninguna estable, se pide además `releases/latest` (la última estable) y se añade; si esa segunda consulta
 * falla o no hay estable (404), vale la lista tal cual.
 */
class GithubReleaseSource(
    private val http: OkHttpClient,
    private val url: String = UpdateConfig.RELEASES_API_URL,
) : ReleaseSource {
    private val latestUrl: String? = url.toHttpUrlOrNull()?.newBuilder()?.query(null)?.addPathSegment("latest")?.build()?.toString()

    override suspend fun fetch(): ReleasesResult = withContext(Dispatchers.IO) {
        when (val list = fetchList()) {
            is ReleasesResult.Ok ->
                if (list.releases.any { !it.draft && !it.prerelease }) list
                else ReleasesResult.Ok(list.releases + listOfNotNull(fetchLatest()?.takeIf { latest -> list.releases.none { it.tag == latest.tag } }))
            is ReleasesResult.Failed -> list
        }
    }

    private fun request(target: String) = Request.Builder()
        .url(target)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .build()

    /** La última estable, o null si no hay, no se entiende o falla: es solo un complemento de la lista. */
    private fun fetchLatest(): GithubRelease? {
        val target = latestUrl ?: return null
        return try {
            http.newCall(request(target)).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.let { releaseJson.decodeFromString<GithubRelease>(it) }
                    ?.takeIf { !it.draft && !it.prerelease }
            }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun fetchList(): ReleasesResult {
        val request = request(url)
        return try {
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 403 || response.code == 429 -> ReleasesResult.Failed(CheckFailure.RATE_LIMITED)
                    response.code == 404 -> ReleasesResult.Failed(CheckFailure.NOT_FOUND)
                    !response.isSuccessful -> ReleasesResult.Failed(CheckFailure.OTHER)
                    else -> {
                        val body = response.body?.string() ?: return@use ReleasesResult.Failed(CheckFailure.OTHER)
                        ReleasesResult.Ok(parseReleases(body))
                    }
                }
            }
        } catch (_: SerializationException) {
            ReleasesResult.Failed(CheckFailure.OTHER)
        } catch (_: IllegalArgumentException) {
            ReleasesResult.Failed(CheckFailure.OTHER)
        } catch (_: IOException) {
            ReleasesResult.Failed(CheckFailure.OFFLINE)
        }
    }
}
