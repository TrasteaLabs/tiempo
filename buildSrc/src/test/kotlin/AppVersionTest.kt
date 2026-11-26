import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppVersionTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `la propiedad manda sobre la variable y sobre git`() {
        assertEquals(40, AppVersion.versionCode("40", "30", { error("no se llama a git") }))
    }

    @Test fun `sin propiedad la variable y sin las dos git`() {
        assertEquals(30, AppVersion.versionCode(null, "30", { error("no se llama a git") }))
        assertEquals(812, AppVersion.versionCode(null, null, { "812\n" }))
    }

    @Test fun `vacios se saltan`() {
        assertEquals(812, AppVersion.versionCode("", " ", { " 812 " }))
        assertEquals(812, AppVersion.versionCode(null, "", { "812" }))
    }

    @Test fun `un valor pedido que no es un entero positivo falla, no se cambia por otro en silencio`() {
        assertThrows(IllegalArgumentException::class.java) { AppVersion.versionCode("abc", null, { "812" }) }
        assertThrows(IllegalArgumentException::class.java) { AppVersion.versionCode(null, "0", { "812" }) }
        assertThrows(IllegalArgumentException::class.java) { AppVersion.versionCode("-3", "30", { "812" }) }
    }

    @Test fun `sin git ni nada, 1`() {
        assertEquals(1, AppVersion.versionCode(null, null, { null }))
        assertEquals(1, AppVersion.versionCode(null, null, { "fatal: not a git repository" }))
        assertEquals(1, AppVersion.versionCode(null, null, { throw java.io.IOException("git no está instalado") }))
    }

    @Test fun `versionName sale de version properties`() {
        assertEquals("1.0.0", AppVersion.versionName("# comentario\nversionName=1.0.0\n"))
        assertEquals("1.2.3", AppVersion.versionName("versionName = 1.2.3 "))
    }

    @Test fun `versionName sin fichero o mal escrito falla`() {
        assertThrows(IllegalArgumentException::class.java) { AppVersion.versionName(null) }
        assertThrows(IllegalArgumentException::class.java) { AppVersion.versionName("versionName=1.0") }
        assertThrows(IllegalArgumentException::class.java) { AppVersion.versionName("versionName=1.0.0-beta") }
    }

    @Test fun `el nombre del APK lleva version y codigo`() {
        assertEquals("tiempo-1.0.0-812.apk", AppVersion.apkName("tiempo", "1.0.0", 812))
    }

    @Test fun `con las cuatro variables firma con ellas aunque haya fichero`() {
        val props = tmp.newFile("keystore.properties").apply {
            writeText("storeFile=k.jks\nstorePassword=a\nkeyAlias=b\nkeyPassword=c\n")
        }
        val env = mapOf(
            "RELEASE_KEYSTORE_FILE" to "/ci/clave.jks", "RELEASE_KEYSTORE_PASSWORD" to "p1",
            "RELEASE_KEY_ALIAS" to "alias", "RELEASE_KEY_PASSWORD" to "p2",
        )
        val (signing, warning) = ReleaseSigning.resolve(env::get, props)
        assertEquals("/ci/clave.jks", signing!!.storeFile.path.replace('\\', '/'))
        assertEquals("alias", signing.keyAlias)
        assertNull(warning)
    }

    @Test fun `sin variables usa keystore properties con ruta relativa a su carpeta`() {
        val props = tmp.newFile("keystore.properties").apply {
            writeText("storeFile=k.jks\nstorePassword=a\nkeyAlias=b\nkeyPassword=c\n")
        }
        val (signing, warning) = ReleaseSigning.resolve({ null }, props)
        assertEquals(tmp.root.resolve("k.jks"), signing!!.storeFile)
        assertNull(warning)
    }

    @Test fun `variables a medias avisan, sin valores, y caen al fichero`() {
        val props = tmp.newFile("keystore.properties").apply {
            writeText("storeFile=k.jks\nstorePassword=a\nkeyAlias=b\nkeyPassword=c\n")
        }
        val (signing, warning) = ReleaseSigning.resolve(mapOf("RELEASE_KEY_ALIAS" to "secreto")::get, props)
        assertEquals("b", signing!!.keyAlias)
        assertTrue(warning!!.contains("RELEASE_KEYSTORE_FILE"))
        assertTrue(!warning.contains("secreto"))
    }

    @Test fun `sin variables ni fichero, sin firma y con aviso`() {
        val (signing, warning) = ReleaseSigning.resolve({ null }, tmp.root.resolve("no-existe.properties"))
        assertNull(signing)
        assertTrue(warning!!.contains("sin firmar"))
    }

    @Test fun `fichero incompleto, sin firma y con aviso`() {
        val props = tmp.newFile("keystore.properties").apply { writeText("storeFile=k.jks\n") }
        val (signing, warning) = ReleaseSigning.resolve({ null }, props)
        assertNull(signing)
        assertTrue(warning!!.contains("storePassword"))
    }
}
