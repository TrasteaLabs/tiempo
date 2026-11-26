package com.trastealabs.tiempo.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
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

/** La lista de releases de GitHub, sin clave. Nunca lanza: todo fallo es un [ReleasesResult.Failed]. */
class GithubReleaseSource(
    private val http: OkHttpClient,
    private val url: String = UpdateConfig.RELEASES_API_URL,
) : ReleaseSource {
    override suspend fun fetch(): ReleasesResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        try {
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
