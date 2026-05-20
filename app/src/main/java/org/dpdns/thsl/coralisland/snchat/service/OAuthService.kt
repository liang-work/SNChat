package org.dpdns.thsl.coralisland.snchat.service

import android.content.Context
import android.util.Base64
import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.dpdns.thsl.coralisland.snchat.data.model.AuthToken
import org.dpdns.thsl.coralisland.snchat.data.model.ServerConfig
import org.dpdns.thsl.coralisland.snchat.storage.AppStorage
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets.UTF_8

private const val TAG = "OAuthService"

class OAuthService(private val context: Context) {

    private val storage = AppStorage(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private var codeVerifier: String = ""
    private var codeChallenge: String = ""
    private var state: String = ""

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING)
            .replace("+", "-")
            .replace("/", "_")
            .trimEnd('=')
    }

    fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP or Base64.NO_PADDING)
            .replace("+", "-")
            .replace("/", "_")
            .trimEnd('=')
    }

    fun generateState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    suspend fun getAuthorizationUrl(config: ServerConfig): String? {
        if (!config.isConfigured()) return null

        codeVerifier = generateCodeVerifier()
        codeChallenge = generateCodeChallenge(codeVerifier)
        state = generateState()

        val params = listOf(
            "client_id" to config.clientId,
            "redirect_uri" to config.redirectUri,
            "response_type" to "code",
            "scope" to "openid profile email",
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256"
        )

        val queryString = params.joinToString("&") { "${it.first}=${it.second}" }
        return "${config.getFullAuthUrl()}?$queryString"
    }

    suspend fun exchangeCodeForToken(config: ServerConfig, code: String): AuthToken? {
        return try {
            Log.d(TAG, "Token URL: ${config.getFullTokenUrl()}")
            Log.d(TAG, "Redirect URI: ${config.redirectUri}")
            Log.d(TAG, "Client ID: ${config.clientId}")
            Log.d(TAG, "Has client secret: ${config.clientSecret.isNotBlank()}")

            val requestBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", config.clientId)
                .add("code", code)
                .add("redirect_uri", config.redirectUri)
                .add("code_verifier", codeVerifier)
                .apply {
                    config.clientSecret.takeIf { it.isNotBlank() }?.let {
                        add("client_secret", it)
                    }
                }
                .build()

            val request = Request.Builder()
                .url(config.getFullTokenUrl())
                .post(requestBody)
                .header("Accept", "application/json")
                .build()

            Log.d(TAG, "Sending token exchange request...")
            httpClient.newCall(request).execute().use { response ->
                Log.d(TAG, "Response code: ${response.code}")
                Log.d(TAG, "Response message: ${response.message}")
                val body = response.body?.string()
                Log.d(TAG, "Response body: ${body?.take(200)}")

                if (response.isSuccessful) {
                    val gson = com.google.gson.Gson()
                    gson.fromJson(body, AuthToken::class.java)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "exchangeCodeForToken error", e)
            null
        }
    }

    suspend fun refreshToken(config: ServerConfig, refreshToken: String): AuthToken? {
        return try {
            val requestBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", config.clientId)
                .add("refresh_token", refreshToken)
                .apply {
                    config.clientSecret.takeIf { it.isNotBlank() }?.let {
                        add("client_secret", it)
                    }
                }
                .build()

            val request = Request.Builder()
                .url(config.getFullTokenUrl())
                .post(requestBody)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val gson = com.google.gson.Gson()
                    gson.fromJson(body, AuthToken::class.java)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun validateToken(config: ServerConfig, token: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("${config.serverUrl}/passport/accounts/me")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}