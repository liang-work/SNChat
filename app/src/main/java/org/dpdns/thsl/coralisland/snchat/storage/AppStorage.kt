package org.dpdns.thsl.coralisland.snchat.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.dpdns.thsl.coralisland.snchat.data.model.AuthToken
import org.dpdns.thsl.coralisland.snchat.data.model.ServerConfig

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "snchat_prefs")

class AppStorage(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val CLIENT_ID = stringPreferencesKey("client_id")
        private val CLIENT_SECRET = stringPreferencesKey("client_secret")
        private val AUTH_URL = stringPreferencesKey("auth_url")
        private val TOKEN_URL = stringPreferencesKey("token_url")
        private val USER_INFO_URL = stringPreferencesKey("user_info_url")
        private val REDIRECT_URI = stringPreferencesKey("redirect_uri")
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val TOKEN_TYPE = stringPreferencesKey("token_type")
        private val ID_TOKEN = stringPreferencesKey("id_token")
        private val AUTH_STATE = stringPreferencesKey("auth_state")
        private val IS_DARK_THEME = stringPreferencesKey("is_dark_theme")
        private val HAS_BACKGROUND_IMAGE = stringPreferencesKey("has_background_image")
        private val FONT_FAMILY = stringPreferencesKey("font_family")
    }

    val serverConfig: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        ServerConfig(
            serverUrl = prefs[SERVER_URL] ?: "",
            clientId = prefs[CLIENT_ID] ?: "",
            clientSecret = prefs[CLIENT_SECRET] ?: "",
            authUrl = prefs[AUTH_URL] ?: "",
            tokenUrl = prefs[TOKEN_URL] ?: "",
            userInfoUrl = prefs[USER_INFO_URL] ?: "",
            redirectUri = prefs[REDIRECT_URI] ?: "snchat://callback"
        )
    }

    val authToken: Flow<AuthToken?> = context.dataStore.data.map { prefs ->
        val accessToken = prefs[ACCESS_TOKEN]
        if (accessToken.isNullOrEmpty()) null
        else AuthToken(
            accessToken = accessToken,
            refreshToken = prefs[REFRESH_TOKEN],
            tokenType = prefs[TOKEN_TYPE] ?: "Bearer",
            idToken = prefs[ID_TOKEN]
        )
    }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_DARK_THEME]?.toBoolean() ?: false
    }

    val hasBackgroundImage: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HAS_BACKGROUND_IMAGE]?.toBoolean() ?: false
    }

    val fontFamily: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[FONT_FAMILY]
    }

    private fun getBackgroundImageFile(): java.io.File {
        return java.io.File(context.filesDir, "background_image")
    }

    suspend fun saveBackgroundImageEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HAS_BACKGROUND_IMAGE] = enabled.toString()
        }
    }

    suspend fun saveBackgroundImageFromUri(uri: android.net.Uri) {
        try {
            val file = getBackgroundImageFile()
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            context.dataStore.edit { prefs ->
                prefs[HAS_BACKGROUND_IMAGE] = "true"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun clearBackgroundImage() {
        try {
            getBackgroundImageFile().delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        context.dataStore.edit { prefs ->
            prefs[HAS_BACKGROUND_IMAGE] = "false"
        }
    }

    fun getBackgroundImagePath(): String? {
        val file = getBackgroundImageFile()
        return if (file.exists()) file.absolutePath else null
    }

    suspend fun saveServerConfig(config: ServerConfig) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL] = config.serverUrl
            prefs[CLIENT_ID] = config.clientId
            prefs[CLIENT_SECRET] = config.clientSecret
            prefs[AUTH_URL] = config.authUrl
            prefs[TOKEN_URL] = config.tokenUrl
            prefs[USER_INFO_URL] = config.userInfoUrl
            prefs[REDIRECT_URI] = config.redirectUri
        }
    }

    suspend fun saveDarkTheme(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_DARK_THEME] = isDark.toString()
        }
    }

    suspend fun saveFontFamily(family: String?) {
        context.dataStore.edit { prefs ->
            if (family != null) {
                prefs[FONT_FAMILY] = family
            } else {
                prefs.remove(FONT_FAMILY)
            }
        }
    }

    suspend fun saveAuthToken(token: AuthToken) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = token.accessToken
            token.refreshToken?.let { prefs[REFRESH_TOKEN] = it }
            prefs[TOKEN_TYPE] = token.tokenType
            token.idToken?.let { prefs[ID_TOKEN] = it }
        }
    }

    suspend fun clearAuthToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN)
            prefs.remove(REFRESH_TOKEN)
            prefs.remove(TOKEN_TYPE)
            prefs.remove(ID_TOKEN)
        }
    }

    suspend fun getServerConfigSync(): ServerConfig {
        return serverConfig.first()
    }

    suspend fun getAuthTokenSync(): AuthToken? {
        return authToken.first()
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    suspend fun saveAuthState(stateJson: String) {
        context.dataStore.edit { prefs ->
            prefs[AUTH_STATE] = stateJson
        }
    }

    suspend fun clearAuthState() {
        context.dataStore.edit { prefs ->
            prefs.remove(AUTH_STATE)
        }
    }

    suspend fun getAuthStateSync(): String? {
        return context.dataStore.data.first()[AUTH_STATE]
    }
}