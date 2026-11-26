package com.trastealabs.tiempo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 021: un APK bajado solo se instala si cuadra con el digest, es de esta app, lo firmó la misma clave y trae su número. */
class ApkCheckTest {
    private val pkg = "com.trastealabs.tiempo"
    private val ours = setOf("1".repeat(64))
    private val sha = "b".repeat(64)
    private val release = AppRelease(
        versionName = "1.0.0", versionCode = 500, tag = "v1.0.0", notes = "", prerelease = false,
        apkName = "tiempo-1.0.0-500.apk", apkUrl = "https://github.com/x.apk", apkSize = 10, sha256 = sha, pageUrl = "",
    )
    private val good = ApkFacts(pkg, 500, ours)

    @Test fun `todo cuadra`() {
        assertNull(checkApk(sha, release, good, pkg, ours))
        assertNull(checkApk(sha.uppercase(), release, good, pkg, ours))
    }

    @Test fun `el digest no cuadra`() {
        assertEquals(ApkProblem.DIGEST, checkApk("c".repeat(64), release, good, pkg, ours))
    }

    @Test fun `sin digest de GitHub se fia del resto`() {
        assertNull(checkApk("c".repeat(64), release.copy(sha256 = null), good, pkg, ours))
    }

    @Test fun `Android no lo puede leer`() {
        assertEquals(ApkProblem.UNREADABLE, checkApk(sha, release, null, pkg, ours))
    }

    @Test fun `es de otro paquete`() {
        assertEquals(ApkProblem.PACKAGE, checkApk(sha, release, good.copy(packageName = "com.trastealabs.tiempo.dev"), pkg, ours))
        assertEquals(ApkProblem.PACKAGE, checkApk(sha, release, good.copy(packageName = "com.otra.app"), pkg, ours))
    }

    @Test fun `otra firma, sin firma o sin saber la nuestra`() {
        assertEquals(ApkProblem.SIGNATURE, checkApk(sha, release, good.copy(signers = setOf("2".repeat(64))), pkg, ours))
        assertEquals(ApkProblem.SIGNATURE, checkApk(sha, release, good.copy(signers = emptySet()), pkg, ours))
        assertEquals(ApkProblem.SIGNATURE, checkApk(sha, release, good, pkg, emptySet()))
        // Una firma de más (otro firmante añadido) tampoco vale.
        assertEquals(ApkProblem.SIGNATURE, checkApk(sha, release, good.copy(signers = ours + "2".repeat(64)), pkg, ours))
    }

    @Test fun `trae otro numero del anunciado`() {
        assertEquals(ApkProblem.VERSION, checkApk(sha, release, good.copy(versionCode = 499), pkg, ours))
    }

    @Test fun `sha256Hex es el de siempre`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(ByteArray(0)))
    }
}
