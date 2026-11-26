package com.trastealabs.tiempo.data.ai

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.trastealabs.tiempo.domain.AiProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 016 P2 (H15). La clave de cada proveedor, cifrada. Nada de esto sale a ningún log ni a ningún `toString()`.
 */
interface KeyVault {
    fun get(p: AiProvider): String?
    fun put(p: AiProvider, key: String)
    fun clear(p: AiProvider)

    /** Los últimos caracteres de la clave (hasta 4) para reconocerla en Ajustes, o `null` sin clave. */
    fun hint(p: AiProvider): String?
}

/** La costura del cifrado: en el móvil, el Android Keystore; en los tests, un falso reversible. */
interface KeyCipher {
    /** Devuelve IV + texto cifrado. */
    fun encrypt(plain: ByteArray): ByteArray

    /** Lanza si el contenido no se puede descifrar (por ejemplo, la clave del Keystore se perdió). */
    fun decrypt(data: ByteArray): ByteArray
}

/** AES/GCM con una clave no exportable del Android Keystore (alias `ai_keys`). */
class AndroidKeyCipher(private val alias: String = ALIAS) : KeyCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        require(data.size > IV_SIZE) { "Contenido cifrado demasiado corto" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, data, 0, IV_SIZE))
        return cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val ALIAS = "ai_keys"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
    }
}

/** Lo cifrado va en [prefs] (`"ai"`) como Base64; la clave en claro no se guarda nunca. */
class KeystoreKeyVault(
    private val prefs: SharedPreferences,
    private val cipher: KeyCipher = AndroidKeyCipher(),
) : KeyVault {
    override fun get(p: AiProvider): String? {
        val stored = prefs.getString(name(p), null) ?: return null
        return try {
            String(cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP)), Charsets.UTF_8).ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    override fun put(p: AiProvider, key: String) {
        val clean = key.trim()
        if (clean.isEmpty()) {
            clear(p)
            return
        }
        val encoded = Base64.encodeToString(cipher.encrypt(clean.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit { putString(name(p), encoded) }
    }

    override fun clear(p: AiProvider) {
        prefs.edit { remove(name(p)) }
    }

    override fun hint(p: AiProvider): String? {
        val key = get(p) ?: return null
        // Una clave corta no se enseña entera: como mucho la mitad de sus caracteres.
        val shown = minOf(HINT_MAX, key.length / 2)
        return if (shown == 0) null else key.takeLast(shown)
    }

    override fun toString() = "KeystoreKeyVault"

    private fun name(p: AiProvider) = "key_${p.name.lowercase()}"

    private companion object {
        const val HINT_MAX = 4
    }
}
