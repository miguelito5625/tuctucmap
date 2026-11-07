package com.example.tuctucmap.data.service

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.tuctucmap.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import java.security.MessageDigest
import java.util.UUID

class GoogleAuthCancellationException : Exception("Inicio de sesión con Google cancelado.")

data class GoogleUserData(
    val idToken: String,
    val email: String,
    val displayName: String,
    val rawNonce: String? = null
)

object GoogleAuthHelper {

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.fold("") { str, it -> str + "%02x".format(it) }
    }

    suspend fun getGoogleCredentials(context: Context): Result<GoogleUserData> {
        return try {
            val rawNonce = UUID.randomUUID().toString()
            val hashedNonce = sha256(rawNonce)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setNonce(hashedNonce)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(request = request, context = context)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName ?: ""

                Result.success(
                    GoogleUserData(
                        idToken = idToken,
                        email = email,
                        displayName = displayName,
                        rawNonce = rawNonce
                    )
                )
            } else {
                Result.failure(Exception("Tipo de credencial no reconocido"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(GoogleAuthCancellationException())
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val exceptionName = e.javaClass.simpleName
            val friendlyError = when {
                exceptionName.contains("NoCredential", ignoreCase = true) ||
                msg.contains("No credentials", ignoreCase = true) ->
                    Exception("No se encontró una cuenta de Google activa en este dispositivo. Puedes ingresar con correo y contraseña.")
                exceptionName.contains("GetCredentialProviderConfiguration", ignoreCase = true) ||
                msg.contains("provider", ignoreCase = true) ->
                    Exception("Los servicios de Google no están disponibles o actualizados en este dispositivo.")
                else -> e
            }
            Result.failure(friendlyError)
        }
    }
}
