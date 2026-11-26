package com.trastealabs.tiempo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 021: qué release se ofrece según el canal. Datos inventados con la forma de la API de GitHub. */
class ReleasesTest {
    private val sha = "a".repeat(64)

    private fun release(
        tag: String,
        apk: String? = null,
        prerelease: Boolean = false,
        draft: Boolean = false,
        digest: String? = "sha256:$sha",
        extraAssets: List<GithubAsset> = emptyList(),
    ) = GithubRelease(
        tag = tag,
        body = "Notas de $tag",
        draft = draft,
        prerelease = prerelease,
        htmlUrl = "https://github.com/trastealabs/tiempo/releases/tag/$tag",
        assets = extraAssets + listOfNotNull(
            apk?.let { GithubAsset(it, "https://github.com/trastealabs/tiempo/releases/download/$tag/$it", 9_000_000, digest) },
        ),
    )

    @Test fun `solo estables con el interruptor apagado`() {
        val list = listOf(
            release("v1.1.0-beta.2", "tiempo-1.1.0-520.apk", prerelease = true),
            release("v1.0.0", "tiempo-1.0.0-500.apk"),
        )
        val picked = pickUpdate(list, beta = false, installedCode = 480)!!
        assertEquals(500, picked.versionCode)
        assertEquals("1.0.0", picked.versionName)
        assertEquals("v1.0.0", picked.tag)
        assertEquals(false, picked.prerelease)
    }

    @Test fun `con betas encendidas gana la beta mas nueva`() {
        val list = listOf(
            release("v1.1.0-beta.2", "tiempo-1.1.0-520.apk", prerelease = true),
            release("v1.0.0", "tiempo-1.0.0-500.apk"),
        )
        val picked = pickUpdate(list, beta = true, installedCode = 480)!!
        assertEquals(520, picked.versionCode)
        assertEquals(true, picked.prerelease)
    }

    @Test fun `la estable que promociona una beta empata y gana la estable, la primera de la lista`() {
        val list = listOf(
            release("v1.1.0", "tiempo-1.1.0-520.apk"),
            release("v1.1.0-beta.2", "tiempo-1.1.0-520.apk", prerelease = true),
        )
        assertEquals("v1.1.0", pickUpdate(list, beta = true, installedCode = 500)!!.tag)
    }

    @Test fun `los borradores no cuentan nunca`() {
        val list = listOf(
            release("v2.0.0", "tiempo-2.0.0-900.apk", draft = true),
            release("v1.0.0", "tiempo-1.0.0-500.apk"),
        )
        assertEquals(500, pickUpdate(list, beta = true, installedCode = 1)!!.versionCode)
    }

    @Test fun `una release sin apk se salta`() {
        val list = listOf(
            release("v1.2.0", extraAssets = listOf(GithubAsset("notas.txt", "https://github.com/x/notas.txt"))),
            release("v1.0.0", "tiempo-1.0.0-500.apk"),
        )
        assertEquals(500, pickUpdate(list, beta = false, installedCode = 1)!!.versionCode)
    }

    @Test fun `versionCode igual o menor al instalado es estar al dia`() {
        val list = listOf(release("v1.0.0", "tiempo-1.0.0-500.apk"))
        assertNull(pickUpdate(list, beta = false, installedCode = 500))
        assertNull(pickUpdate(list, beta = false, installedCode = 501))
        assertEquals(500, pickUpdate(list, beta = false, installedCode = 499)!!.versionCode)
    }

    @Test fun `nombres de fichero raros no valen`() {
        listOf(
            "tiempo.apk", "tiempo-1.0.0.apk", "tiempo-1.0-500.apk", "tiempo-1.0.0-abc.apk", "tiempo-1.0.0-0.apk",
            "otra-1.0.0-500.apk", "tiempo-1.0.0-500.apk.sha256", "Tiempo-1.0.0-500.apk", "tiempo-1.0.0-500.APK",
            "xtiempo-1.0.0-500.apk", "tiempo-1.0.0-beta.1-500.apk", "tiempo-1.0.0-99999999999.apk",
        ).forEach { name ->
            assertNull(name, pickUpdate(listOf(release("v9", name)), beta = true, installedCode = 1))
        }
    }

    @Test fun `entre varios ficheros elige el apk de la casa`() {
        val list = listOf(
            release(
                "v1.0.0", "tiempo-1.0.0-500.apk",
                extraAssets = listOf(GithubAsset("tiempo-1.0.0-500.apk.sha256", "https://github.com/x/sha"), GithubAsset("mapping.txt", "https://github.com/x/m")),
            ),
        )
        assertEquals("tiempo-1.0.0-500.apk", pickUpdate(list, beta = false, installedCode = 1)!!.apkName)
    }

    @Test fun `un enlace que no es https no se ofrece`() {
        val r = GithubRelease(tag = "v1", assets = listOf(GithubAsset("tiempo-1.0.0-500.apk", "http://github.com/x.apk")))
        assertNull(pickUpdate(listOf(r), beta = false, installedCode = 1))
    }

    @Test fun `el digest se lee si es sha256 y si no queda null`() {
        assertEquals(sha, sha256FromDigest("sha256:$sha"))
        assertEquals(sha, sha256FromDigest("SHA256:${sha.uppercase()}"))
        assertNull(sha256FromDigest(null))
        assertNull(sha256FromDigest("sha1:abc"))
        assertNull(sha256FromDigest("sha256:abc"))
        val picked = pickUpdate(listOf(release("v1", "tiempo-1.0.0-500.apk", digest = null)), beta = false, installedCode = 1)!!
        assertNull(picked.sha256)
    }

    @Test fun `lista vacia es estar al dia`() {
        assertNull(pickUpdate(emptyList(), beta = true, installedCode = 1))
    }

    @Test fun `la respuesta de GitHub se lee ignorando lo que sobra`() {
        val body = """
            [{"url":"https://api.github.com/x","tag_name":"v1.0.0","name":"1.0.0","body":"Primera","draft":false,
              "prerelease":false,"html_url":"https://github.com/trastealabs/tiempo/releases/tag/v1.0.0",
              "author":{"login":"alguien"},
              "assets":[{"name":"tiempo-1.0.0-500.apk","size":9000000,"digest":"sha256:$sha","content_type":"application/vnd.android.package-archive",
                "browser_download_url":"https://github.com/trastealabs/tiempo/releases/download/v1.0.0/tiempo-1.0.0-500.apk"}]},
             {"tag_name":"v0.9.0","body":null,"assets":[]}]
        """.trimIndent()
        val releases = parseReleases(body)
        assertEquals(2, releases.size)
        val picked = pickUpdate(releases, beta = false, installedCode = 1)!!
        assertEquals("Primera", picked.notes)
        assertEquals(9_000_000, picked.apkSize)
        assertEquals(sha, picked.sha256)
    }

    @Test fun `las notas largas se recortan`() {
        val r = release("v1", "tiempo-1.0.0-500.apk").copy(body = "x".repeat(5000))
        assertEquals(MAX_NOTES_CHARS, pickUpdate(listOf(r), beta = false, installedCode = 1)!!.notes.length)
    }
}
