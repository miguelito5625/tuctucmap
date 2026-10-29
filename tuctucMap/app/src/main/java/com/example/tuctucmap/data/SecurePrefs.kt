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
 * Gestor de almacenamiento seguro para tokens, sesiones y estado de suscripción en Android.
 * Implementa [SessionManager] de Supabase-kt con cifrado por hardware AES-256 en modo GCM
 * respaldado por Android KeyStore, sin fallback en texto plano.
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

    @Volatile
    private var cachedSecretKey: SecretKey? = null

    private fun getContext(context: Context? = null): Context {
        return context ?: try {
            TucTucApplication.appContext
        } catch (_: Exception) {
            throw IllegalStateException("Context no disponible para SecurePrefs")
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        cachedSecretKey?.let { return it }
        synchronized(this) {
            cachedSecretKey?.let { return it }
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
                cachedSecretKey = entry.secretKey
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
            val generated = keyGenerator.generateKey()
            cachedSecretKey = generated
            return generated
        }
    }

    fun encrypt(plainText: String): String? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            "$ivBase64$IV_SEPARATOR$cipherBase64"
        } catch (_: Exception) {
            null
        }
    }

    fun decrypt(encryptedText: String): String? {
        return try {
            if (!encryptedText.contains(IV_SEPARATOR)) {
                return null
            }
            val parts = encryptedText.split(IV_SEPARATOR, limit = 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: Exception) {
            null
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
        val sessionJsonStr = json.encodeToString(session)
        val encSession = encrypt(sessionJsonStr) ?: return

        prefs.edit()
            .putString("supabase_session", encSession)
            .remove("supabase_access")
            .remove("supabase_refresh")
            .apply()
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
        val encAccess = encrypt(accessToken) ?: return
        val encRefresh = encrypt(refreshToken) ?: return
        prefs.edit()
            .putString("supabase_access", encAccess)
            .putString("supabase_refresh", encRefresh)
            .apply()
    }

    fun getTokens(context: Context): Pair<String?, String?> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawAccess = prefs.getString("supabase_access", null)
        val rawRefresh = prefs.getString("supabase_refresh", null)

        val access = rawAccess?.let { decrypt(it) }
        val refresh = rawRefresh?.let { decrypt(it) }
        return Pair(access, refresh)
    }

    // --- Caché cifrada del estado de aprobación y suscripción del conductor ---

    fun saveDriverSubscriptionCache(context: Context, approvalStatus: String, subscriptionEndDate: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encStatus = encrypt(approvalStatus) ?: return
        val editor = prefs.edit().putString("driver_last_approval_status", encStatus)
        if (subscriptionEndDate != null) {
            encrypt(subscriptionEndDate)?.let { editor.putString("driver_last_sub_end", it) }
        } else {
            editor.remove("driver_last_sub_end")
        }
        editor.apply()
    }

    fun getDriverApprovalStatusCache(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("driver_last_approval_status", null) ?: return null
        return decrypt(raw)
    }

    fun getDriverSubEndCache(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("driver_last_sub_end", null) ?: return null
        return decrypt(raw)
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
