package pl.local.liberateus.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedCredentials(
    val username: String,
    val password: String
)

class SecureLoginStore(context: Context) {

    companion object {
        private const val PREFS = "librus_secure_login_v1"
        private const val KEY_ALIAS = "librus_login_key_v1"

        private const val USERNAME = "username"
        private const val PASSWORD = "password"
        private const val COOKIES = "cookies"
    }

    private val prefs = context.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE
    )

    fun saveCredentials(
        username: String,
        password: String
    ) {
        prefs.edit()
            .putString(USERNAME, encrypt(username))
            .putString(PASSWORD, encrypt(password))
            .apply()
    }

    fun loadCredentials(): SavedCredentials? {
        val username = decrypt(
            prefs.getString(USERNAME, null)
        ) ?: return null

        val password = decrypt(
            prefs.getString(PASSWORD, null)
        ) ?: return null

        if (username.isBlank() || password.isBlank()) {
            return null
        }

        return SavedCredentials(
            username = username,
            password = password
        )
    }

    fun saveCookies(json: String) {
        prefs.edit()
            .putString(COOKIES, encrypt(json))
            .apply()
    }

    fun loadCookies(): String? =
        decrypt(prefs.getString(COOKIES, null))

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(
            "AES/GCM/NoPadding"
        )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateKey()
        )

        val encrypted = cipher.doFinal(
            value.toByteArray(Charsets.UTF_8)
        )

        val json = JSONObject()
            .put(
                "iv",
                Base64.encodeToString(
                    cipher.iv,
                    Base64.NO_WRAP
                )
            )
            .put(
                "data",
                Base64.encodeToString(
                    encrypted,
                    Base64.NO_WRAP
                )
            )

        return json.toString()
    }

    private fun decrypt(value: String?): String? {
        if (value.isNullOrBlank()) return null

        return try {
            val json = JSONObject(value)

            val iv = Base64.decode(
                json.getString("iv"),
                Base64.NO_WRAP
            )

            val data = Base64.decode(
                json.getString("data"),
                Base64.NO_WRAP
            )

            val cipher = Cipher.getInstance(
                "AES/GCM/NoPadding"
            )

            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, iv)
            )

            String(
                cipher.doFinal(data),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(
            "AndroidKeyStore"
        ).apply {
            load(null)
        }

        val existing = keyStore.getKey(
            KEY_ALIAS,
            null
        ) as? SecretKey

        if (existing != null) {
            return existing
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setKeySize(256)
                .build()
        )

        return generator.generateKey()
    }
}
