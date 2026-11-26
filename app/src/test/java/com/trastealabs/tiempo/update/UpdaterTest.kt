package com.trastealabs.tiempo.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/** 021: cuándo pregunta, qué guarda, qué ofrece y qué hace con un APK que no cuadra. Reloj inyectado, GitHub de mentira. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdaterTest {
    @get:Rule val tmp = TemporaryFolder()

    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("update_test", Context.MODE_PRIVATE)
    private val settings = UpdateSettings(prefs)
    private val day = 24 * 3600L
    private var now = 1_790_000_000L
    private var calls = 0
    private var next: ReleasesResult = ReleasesResult.Ok(emptyList())
    private val pkg = "com.trastealabs.tiempo"
    private val ours = setOf("1".repeat(64))
    private var facts: ApkFacts? = null
    private var installResult = InstallStart.STARTED
    private val installed = mutableListOf<File>()
    private lateinit var downloads: File
    private var downloader: ApkDownloader = ApkDownloader { _, dest, _, onProgress ->
        dest.writeBytes(APK_BYTES)
        onProgress(APK_BYTES.size.toLong(), APK_BYTES.size.toLong())
        sha256Hex(APK_BYTES)
    }

    private val inspector = object : ApkInspector {
        override fun inspect(apk: File) = facts
        override fun installedSigners() = ours
        override val packageName = pkg
    }

    private fun updater(enabled: Boolean = true, installedCode: Int = 480) = Updater(
        source = { calls++; next },
        settings = settings,
        downloader = { url, dest, size, progress -> downloader.download(url, dest, size, progress) },
        inspector = inspector,
        installer = { installed += it; installResult },
        downloadDir = downloads,
        installedCode = installedCode,
        installedName = "1.0.0",
        enabled = enabled,
        nowSeconds = { now },
    )

    private fun release(code: Int, prerelease: Boolean = false, sha: String? = sha256Hex(APK_BYTES)) = GithubRelease(
        tag = "v$code",
        prerelease = prerelease,
        body = "Notas",
        assets = listOf(GithubAsset("tiempo-1.0.0-$code.apk", "https://github.com/trastealabs/tiempo/releases/download/v$code/tiempo-1.0.0-$code.apk", APK_BYTES.size.toLong(), sha?.let { "sha256:$it" })),
    )

    @Before fun setUp() {
        prefs.edit().clear().commit()
        downloads = tmp.newFolder("updates")
        facts = ApkFacts(pkg, 500, ours)
    }

    @After fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test fun `la primera vez pregunta y despues no hasta pasadas 24 h`() = runTest {
        val u = updater()
        assertTrue(u.checkIfDue())
        assertEquals(1, calls)
        now += day - 1
        assertFalse(u.checkIfDue())
        assertEquals(1, calls)
        now += 1
        assertTrue(u.checkIfDue())
        assertEquals(2, calls)
    }

    @Test fun `las 24 h cuentan entre arranques porque se guardan`() = runTest {
        updater().checkIfDue()
        now += 3600
        assertFalse(updater().checkIfDue())
        assertEquals(1, calls)
    }

    @Test fun `con el reloj hacia atras vuelve a preguntar`() = runTest {
        updater().checkIfDue()
        now -= 3600
        assertTrue(updater().checkIfDue())
    }

    @Test fun `buscar ahora pregunta aunque no hayan pasado 24 h`() = runTest {
        val u = updater()
        u.checkIfDue()
        assertTrue(u.checkNow())
        assertEquals(2, calls)
    }

    @Test fun `la version dev no pregunta nunca`() = runTest {
        val u = updater(enabled = false)
        assertFalse(u.checkIfDue())
        assertFalse(u.checkNow())
        assertEquals(0, calls)
        assertFalse(u.state.value.enabled)
    }

    @Test fun `sin red, con limite o sin repo apunta el fallo, no ofrece nada y espera al dia siguiente`() = runTest {
        for (reason in CheckFailure.entries) {
            prefs.edit().clear().commit()
            next = ReleasesResult.Failed(reason)
            val u = updater()
            assertTrue(u.checkIfDue())
            assertEquals(reason, u.state.value.lastFailure)
            assertNull(u.state.value.available)
            assertFalse(u.state.value.showNotice)
            assertEquals(now, settings.lastCheck)
            assertFalse(u.checkIfDue())
        }
    }

    @Test fun `un fallo no borra la version que ya se habia encontrado`() = runTest {
        next = ReleasesResult.Ok(listOf(release(500)))
        val u = updater()
        u.checkIfDue()
        next = ReleasesResult.Failed(CheckFailure.OFFLINE)
        u.checkNow()
        assertEquals(500, u.state.value.available!!.versionCode)
        assertEquals(CheckFailure.OFFLINE, u.state.value.lastFailure)
    }

    @Test fun `una consulta buena tras un fallo quita el fallo`() = runTest {
        next = ReleasesResult.Failed(CheckFailure.NOT_FOUND)
        val u = updater()
        u.checkIfDue()
        next = ReleasesResult.Ok(emptyList())
        u.checkNow()
        assertNull(u.state.value.lastFailure)
        assertNull(settings.lastFailure)
    }

    @Test fun `la version encontrada se guarda y se ofrece al volver a abrir sin preguntar`() = runTest {
        next = ReleasesResult.Ok(listOf(release(500)))
        updater().checkIfDue()
        val again = updater()
        assertEquals(500, again.state.value.available!!.versionCode)
        assertTrue(again.state.value.showNotice)
        assertFalse(again.checkIfDue())
    }

    @Test fun `ya instalada, deja de ofrecerse`() = runTest {
        next = ReleasesResult.Ok(listOf(release(500)))
        updater().checkIfDue()
        assertNull(updater(installedCode = 500).state.value.available)
    }

    @Test fun `descartar quita el aviso de la principal pero no la oferta`() = runTest {
        next = ReleasesResult.Ok(listOf(release(500)))
        val u = updater()
        u.checkIfDue()
        u.dismiss()
        assertFalse(u.state.value.showNotice)
        assertEquals(500, u.state.value.available!!.versionCode)
        assertFalse(updater().state.value.showNotice)
        // Una más nueva vuelve a avisar.
        next = ReleasesResult.Ok(listOf(release(510)))
        u.checkNow()
        assertTrue(u.state.value.showNotice)
    }

    @Test fun `apagar las betas deja de ofrecer la beta encontrada`() = runTest {
        settings.beta = true
        next = ReleasesResult.Ok(listOf(release(520, prerelease = true), release(500)))
        val u = updater()
        u.checkIfDue()
        assertEquals(520, u.state.value.available!!.versionCode)
        u.setBeta(false)
        assertNull(u.state.value.available)
        assertFalse(settings.beta)
        u.checkNow()
        assertEquals(500, u.state.value.available!!.versionCode)
    }

    @Test fun `bajar, comprobar e instalar`() = runTest {
        next = ReleasesResult.Ok(listOf(release(500)))
        val u = updater()
        u.checkIfDue()
        u.download()
        val ready = u.state.value.download as DownloadState.Ready
        assertTrue(ready.apk.isFile)
        u.install()
        assertEquals(listOf(ready.apk), installed)
        assertTrue(u.state.value.download is DownloadState.Installing)
        u.installAborted()
        assertTrue(u.state.value.download is DownloadState.Ready)
    }

    @Test fun `si Android no puede instalarla se dice, no vuelve a Instalar en silencio`() = runTest {
        next = ReleasesResult.Ok(listOf(release(500)))
        val u = updater()
        u.checkIfDue()
        u.download()
        u.install()
        u.installFailed()
        assertEquals(DownloadState.InstallFailed, u.state.value.download)
    }

    @Test fun `con la 500 bajada, encontrar la 510 descarta el fichero y no instala la 500`() = runTest {
        next = ReleasesResult.Ok(listOf(release(500)))
        val u = updater()
        u.checkIfDue()
        u.download()
        assertTrue(u.state.value.download is DownloadState.Ready)
        next = ReleasesResult.Ok(listOf(release(510), release(500)))
        u.checkNow()
        assertEquals(510, u.state.value.available!!.versionCode)
        assertEquals(DownloadState.Idle, u.state.value.download)
        assertEquals(0, downloads.listFiles()!!.size)
        u.install()
        assertTrue(installed.isEmpty())
    }

    @Test fun `la misma version encontrada otra vez conserva lo bajado`() = runTest {
        next = ReleasesResult.Ok(listOf(release(500)))
        val u = updater()
        u.checkIfDue()
        u.download()
        u.checkNow()
        assertTrue(u.state.value.download is DownloadState.Ready)
        assertEquals(1, downloads.listFiles()!!.size)
    }

    @Test fun `apagar las betas con la beta bajada no deja instalarla`() = runTest {
        settings.beta = true
        facts = ApkFacts(pkg, 520, ours)
        next = ReleasesResult.Ok(listOf(release(520, prerelease = true)))
        val u = updater()
        u.checkIfDue()
        u.download()
        assertTrue(u.state.value.download is DownloadState.Ready)
        u.setBeta(false)
        assertEquals(DownloadState.Idle, u.state.value.download)
        u.install()
        assertTrue(installed.isEmpty())
    }

    @Test fun `sin permiso de instalar, lo pide y se puede volver a pulsar`() = runTest {
        next = ReleasesResult.Ok(listOf(release(500)))
        installResult = InstallStart.NEEDS_PERMISSION
        val u = updater()
        u.checkIfDue()
        u.download()
        u.install()
        assertTrue(u.state.value.download is DownloadState.NeedsPermission)
        installResult = InstallStart.STARTED
        u.install()
        assertTrue(u.state.value.download is DownloadState.Installing)
        assertEquals(2, installed.size)
    }

    @Test fun `un APK que no cuadra con el digest se borra y no se instala`() = runTest {
        next = ReleasesResult.Ok(listOf(release(500, sha = "f".repeat(64))))
        val u = updater()
        u.checkIfDue()
        u.download()
        assertEquals(DownloadState.Failed(ApkProblem.DIGEST), u.state.value.download)
        assertEquals(0, downloads.listFiles()!!.size)
        u.install()
        assertTrue(installed.isEmpty())
    }

    @Test fun `un APK de otro paquete se borra y no se instala`() = runTest {
        next = ReleasesResult.Ok(listOf(release(500)))
        facts = ApkFacts("com.otra.app", 500, ours)
        val u = updater()
        u.checkIfDue()
        u.download()
        assertEquals(DownloadState.Failed(ApkProblem.PACKAGE), u.state.value.download)
        assertEquals(0, downloads.listFiles()!!.size)
    }

    @Test fun `un APK con otra firma se borra y no se instala`() = runTest {
        next = ReleasesResult.Ok(listOf(release(500)))
        facts = ApkFacts(pkg, 500, setOf("9".repeat(64)))
        val u = updater()
        u.checkIfDue()
        u.download()
        assertEquals(DownloadState.Failed(ApkProblem.SIGNATURE), u.state.value.download)
        assertEquals(0, downloads.listFiles()!!.size)
    }

    @Test fun `si la bajada se corta lo dice y no deja el fichero`() = runTest {
        next = ReleasesResult.Ok(listOf(release(500)))
        downloader = ApkDownloader { _, dest, _, _ -> dest.writeBytes(byteArrayOf(1)); throw IOException("cortado") }
        val u = updater()
        u.checkIfDue()
        u.download()
        assertEquals(DownloadState.Failed(ApkProblem.DOWNLOAD), u.state.value.download)
        assertEquals(0, downloads.listFiles()!!.size)
    }

    @Test fun `al dia, los APK viejos bajados se borran`() = runTest {
        File(downloads, "tiempo-1.0.0-470.apk").writeBytes(APK_BYTES)
        next = ReleasesResult.Ok(emptyList())
        updater().checkIfDue()
        assertEquals(0, downloads.listFiles()!!.size)
    }

    @Test fun `un JSON guardado roto se lee como nada`() {
        prefs.edit().putString("found", "{roto").commit()
        assertNull(updater().state.value.available)
    }

    @Test fun `el downloader de verdad calcula el sha y corta si llega a medias`() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setBody(Buffer().write(APK_BYTES)))
            val dest = File(downloads, "a.apk")
            var last = 0L
            val sha = HttpApkDownloader(OkHttpClient()).download(server.url("/a.apk").toString(), dest, 0) { done, _ -> last = done }
            assertEquals(sha256Hex(APK_BYTES), sha)
            assertEquals(APK_BYTES.size.toLong(), last)
            assertTrue(dest.isFile)

            server.enqueue(
                MockResponse().setBody(Buffer().write(APK_BYTES)).setHeader("Content-Length", APK_BYTES.size * 2)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )
            val half = File(downloads, "b.apk")
            val thrown = runCatching {
                HttpApkDownloader(OkHttpClient()).download(server.url("/b.apk").toString(), half, 0) { _, _ -> }
            }.exceptionOrNull()
            assertTrue(thrown is IOException)
            assertFalse(half.exists())
            assertFalse(File(half.path + ".part").exists())

            server.enqueue(MockResponse().setResponseCode(404))
            assertTrue(runCatching { HttpApkDownloader(OkHttpClient()).download(server.url("/c.apk").toString(), File(downloads, "c.apk"), 0) { _, _ -> } }.exceptionOrNull() is IOException)
        } finally {
            server.shutdown()
        }
    }

    private companion object {
        val APK_BYTES = ByteArray(200_000) { (it % 251).toByte() }
    }
}
