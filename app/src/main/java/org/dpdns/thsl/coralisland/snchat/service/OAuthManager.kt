package org.dpdns.thsl.coralisland.snchat.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthState
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientSecretPost
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import org.dpdns.thsl.coralisland.snchat.data.model.ServerConfig
import org.dpdns.thsl.coralisland.snchat.data.model.UserInfo
import org.dpdns.thsl.coralisland.snchat.storage.AppStorage
import kotlin.coroutines.resume

private const val TAG = "OAuthManager"

class OAuthManager(private val context: Context) {

    private val storage = AppStorage(context)
    private var authService: AuthorizationService? = null
    private var authState: AuthState = AuthState()
    private var currentClientSecret: String = ""

    fun getAuthorizationIntent(config: ServerConfig): Intent? {
        try {
            val authUri = Uri.parse(config.getFullAuthUrl())
            val tokenUri = Uri.parse(config.getFullTokenUrl())

            val serviceConfig = net.openid.appauth.AuthorizationServiceConfiguration(
                authUri,
                tokenUri
            )

            // IMPORTANT: Save AuthState with service config BEFORE starting OAuth
            // This is required for AppAuth to handle the callback properly
            authState = AuthState(serviceConfig)
            saveAuthState()

            authService = AuthorizationService(context)

            currentClientSecret = config.clientSecret

            val scopes = listOf("openid", "profile", "email").joinToString(" ")

            val request = AuthorizationRequest.Builder(
                serviceConfig,
                config.clientId,
                ResponseTypeValues.CODE,
                Uri.parse(config.redirectUri)
            )
                .setScopes(scopes)
                .build()

            return authService?.getAuthorizationRequestIntent(request)
        } catch (e: Exception) {
            Log.e(TAG, "getAuthorizationIntent error", e)
            return null
        }
    }

    fun handleAuthorizationResult(data: Intent?) {
        if (data == null) return

        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)

        Log.d(TAG, "handleAuthorizationResult: response=${response != null}, exception=${exception?.error}")

        if (response != null && exception == null) {
            authState.update(response, exception)
            exchangeAuthorizationCode(response)
        } else if (exception != null) {
            Log.e(TAG, "Authorization failed", exception)
        }
    }

    private fun exchangeAuthorizationCode(response: AuthorizationResponse) {
        val request = response.createTokenExchangeRequest()

        val callback = object : AuthorizationService.TokenResponseCallback {
            override fun onTokenRequestCompleted(tokenResponse: TokenResponse?, exception: AuthorizationException?) {
                if (exception != null) {
                    Log.e(TAG, "Token exchange failed", exception)
                } else if (tokenResponse != null) {
                    authState.update(tokenResponse, null)
                    Log.d(TAG, "Token exchange success, isAuthorized=${authState.isAuthorized}")
                    saveAuthState()
                }
            }
        }

        if (currentClientSecret.isNotBlank()) {
            val clientAuth = ClientSecretPost(currentClientSecret)
            authService?.performTokenRequest(request, clientAuth, callback)
        } else {
            authService?.performTokenRequest(request, callback)
        }
    }

    fun getAccessTokenSync(): String? {
        val service = authService ?: return null
        var result: String? = null
        
        val callback = object : AuthState.AuthStateAction {
            override fun execute(accessToken: String?, idToken: String?, exception: AuthorizationException?) {
                result = accessToken
            }
        }

        if (currentClientSecret.isNotBlank()) {
            val clientAuth = ClientSecretPost(currentClientSecret)
            authState.performActionWithFreshTokens(service, clientAuth, callback)
        } else {
            authState.performActionWithFreshTokens(service, callback)
        }

        return result
    }

    suspend fun getUserInfo(): UserInfo? {
        val token = getAccessTokenSync() ?: return null
        return try {
            val config = storage.getServerConfigSync()
            val httpClient = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(config.getFullUserInfoUrl())
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val gson = com.google.gson.Gson()
                        gson.fromJson(body, UserInfo::class.java)
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUserInfo error", e)
            null
        }
    }

    fun isAuthorized(): Boolean = authState.isAuthorized

    fun signOut() {
        authState = AuthState()
        currentClientSecret = ""
        clearAuthState()
    }

    private fun saveAuthState() {
        try {
            val stateJson = authState.jsonSerializeString()
            kotlinx.coroutines.runBlocking {
                storage.saveAuthState(stateJson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveAuthState error", e)
        }
    }

    private fun clearAuthState() {
        try {
            kotlinx.coroutines.runBlocking {
                storage.clearAuthState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "clearAuthState error", e)
        }
    }

    fun loadAuthState() {
        try {
            val stateJson = kotlinx.coroutines.runBlocking { storage.getAuthStateSync() }
            if (stateJson != null) {
                authState = AuthState.jsonDeserialize(stateJson)
                Log.d(TAG, "AuthState loaded, isAuthorized=${authState.isAuthorized}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadAuthState error", e)
        }
    }

    fun dispose() {
        authService?.dispose()
        authService = null
    }
}