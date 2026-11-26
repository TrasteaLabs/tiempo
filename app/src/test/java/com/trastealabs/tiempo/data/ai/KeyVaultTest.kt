package com.trastealabs.tiempo.data.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.domain.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Un cifrado falso reversible: invierte los bytes y pone un IV de relleno delante. */
private class FakeCipher(var broken: Boolean = false) : KeyCipher {
    override fun encrypt(plain: ByteArray) = ByteArray(12) { 7 } + plain.reversedArray()
    override fun decrypt(data: ByteArray): ByteArray {
        check(!broken) { "clave del Keystore perdida" }
        return data.copyOfRange(12, data.size).reversedArray()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyVaultTest {
    private val secret = "clave-de-prueba-1234"
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("ai_test", Context.MODE_PRIVATE)
    private val cipher = FakeCipher()
    private lateinit var vault: KeystoreKeyVault

    @Before fun setUp() {
        prefs.edit().clear().commit()
        vault = KeystoreKeyVault(prefs, cipher)
    }

    @Test fun putAndGet() {
        vault.put(AiProvider.CLAUDE, secret)
        assertEquals(secret, vault.get(AiProvider.CLAUDE))
        assertEquals(secret, KeystoreKeyVault(prefs, cipher).get(AiProvider.CLAUDE))
    }

    @Test fun hintIsTheLastFour() {
        assertNull(vault.hint(AiProvider.CLAUDE))
        vault.put(AiProvider.CLAUDE, secret)
        assertEquals("1234", vault.hint(AiProvider.CLAUDE))
    }

    @Test fun shortKeysAreNotShownWhole() {
        vault.put(AiProvider.GEMINI, "abcdef")
        assertEquals("def", vault.hint(AiProvider.GEMINI))
        vault.put(AiProvider.GEMINI, "ab")
        assertEquals("b", vault.hint(AiProvider.GEMINI))
        vault.put(AiProvider.GEMINI, "a")
        assertNull(vault.hint(AiProvider.GEMINI))
    }

    @Test fun clearRemovesIt() {
        vault.put(AiProvider.OPENAI, secret)
        vault.clear(AiProvider.OPENAI)
        assertNull(vault.get(AiProvider.OPENAI))
        assertNull(vault.hint(AiProvider.OPENAI))
    }

    @Test fun providersDoNotStepOnEachOther() {
        vault.put(AiProvider.CLAUDE, "clave-uno-1111")
        vault.put(AiProvider.GEMINI, "clave-dos-2222")
        assertEquals("clave-uno-1111", vault.get(AiProvider.CLAUDE))
        assertEquals("clave-dos-2222", vault.get(AiProvider.GEMINI))
        vault.clear(AiProvider.CLAUDE)
        assertEquals("clave-dos-2222", vault.get(AiProvider.GEMINI))
        assertNull(vault.get(AiProvider.OPENAI))
    }

    @Test fun nothingInPlainTextIsStored() {
        vault.put(AiProvider.CLAUDE, secret)
        val stored = prefs.all.values.joinToString("|")
        assertFalse(stored.contains(secret))
        assertFalse(stored.contains("1234"))
        assertTrue(prefs.all.isNotEmpty())
    }

    @Test fun toStringDoesNotShowTheKey() {
        vault.put(AiProvider.CLAUDE, secret)
        assertFalse(vault.toString().contains(secret))
    }

    @Test fun unreadableCipherTextGivesNullWithoutThrowing() {
        vault.put(AiProvider.CLAUDE, secret)
        cipher.broken = true
        assertNull(vault.get(AiProvider.CLAUDE))
        assertNull(vault.hint(AiProvider.CLAUDE))
    }

    @Test fun blankKeyClears() {
        vault.put(AiProvider.CLAUDE, secret)
        vault.put(AiProvider.CLAUDE, "   ")
        assertNull(vault.get(AiProvider.CLAUDE))
    }
}
