package com.example.tuctucmap.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.tuctucmap.TucTucApplication
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Gestor de almacenamiento seguro para tokens y sesiones de autenticación en Android.
 * Implementa [SessionManager] de Supabase-kt con cifrado por hardware AES-256 en modo GCM
 * respaldado por Android KeyStore.
 */
object SecurePrefs : SessionManager {
    private const val PREFS_NAME = "tuctuc_prefs"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "tuctuc_token_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SEPARATOR = "]"

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun getContext(context: Context? = null): Context {
        return context ?: try {
            TucTucApplication.appContext
        } catch (_: Exception) {
            throw IllegalStateException("Context no disponible para SecurePrefs")
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            "$ivBase64$IV_SEPARATOR$cipherBase64"
        } catch (_: Exception) {
            plainText
        }
    }

    fun decrypt(encryptedText: String): String? {
        return try {
            if (!encryptedText.contains(IV_SEPARATOR)) {
                return encryptedText
            }
            val parts = encryptedText.split(IV_SEPARATOR, limit = 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: Exception) {
            if (encryptedText.startsWith("eyJ") || encryptedText.startsWith("{")) encryptedText else null
        }
    }

    // --- Implementación oficial de SessionManager para Supabase-kt ---

    override suspend fun saveSession(session: UserSession) {
        try {
            saveUserSession(getContext(), session)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun loadSession(): UserSession? {
        return try {
            loadUserSession(getContext())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun deleteSession() {
        try {
            clearTokens(getContext())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveUserSession(context: Context, session: UserSession) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val sessionJsonStr = json.encodeToString(session)
            val encSession = encrypt(sessionJsonStr)
            val encAccess = encrypt(session.accessToken)
            val encRefresh = encrypt(session.refreshToken)

            prefs.edit()
                .putString("supabase_session", encSession)
                .putString("supabase_access", encAccess)
                .putString("supabase_refresh", encRefresh)
                .apply()
        } catch (_: Exception) {
            val sessionJsonStr = json.encodeToString(session)
            prefs.edit()
                .putString("supabase_session", sessionJsonStr)
                .putString("supabase_access", session.accessToken)
                .putString("supabase_refresh", session.refreshToken)
                .apply()
        }
    }

    fun loadUserSession(context: Context): UserSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawSession = prefs.getString("supabase_session", null)
        if (rawSession != null) {
            val decrypted = decrypt(rawSession)
            if (decrypted != null) {
                try {
                    return json.decodeFromString<UserSession>(decrypted)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Migración de respaldo si venía de tokens individuales previos
        val (access, refresh) = getTokens(context)
        if (!access.isNullOrBlank() && !refresh.isNullOrBlank()) {
            return UserSession(
                accessToken = access,
                refreshToken = refresh,
                expiresIn = 3600L,
                tokenType = "Bearer"
            )
        }
        return null
    }

    fun saveTokens(context: Context, accessToken: String, refreshToken: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val encAccess = encrypt(accessToken)
            val encRefresh = encrypt(refreshToken)
            prefs.edit()
                .putString("supabase_access", encAccess)
                .putString("supabase_refresh", encRefresh)
                .apply()
        } catch (_: Exception) {
            prefs.edit()
                .putString("supabase_access", accessToken)
                .putString("supabase_refresh", refreshToken)
                .apply()
        }
    }

    fun getTokens(context: Context): Pair<String?, String?> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawAccess = prefs.getString("supabase_access", null)
        val rawRefresh = prefs.getString("supabase_refresh", null)

        val access = rawAccess?.let { decrypt(it) }
        val refresh = rawRefresh?.let { decrypt(it) }
        return Pair(access, refresh)
    }

    fun clearTokens(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("supabase_session")
            .remove("supabase_access")
            .remove("supabase_refresh")
            .remove("user_role")
            .remove("driver_last_approval_status")
            .remove("driver_last_sub_end")
            .apply()
    }
}
