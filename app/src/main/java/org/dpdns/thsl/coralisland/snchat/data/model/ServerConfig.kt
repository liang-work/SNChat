package org.dpdns.thsl.coralisland.snchat.data.model

data class ServerConfig(
    val serverUrl: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val authUrl: String = "",
    val tokenUrl: String = "",
    val userInfoUrl: String = "",
    val redirectUri: String = "snchat://callback"
) {
    fun isConfigured(): Boolean {
        return serverUrl.isNotBlank() && clientId.isNotBlank()
    }

    fun getFullAuthUrl(): String = if (authUrl.startsWith("http")) authUrl else "$serverUrl$authUrl"
    fun getFullTokenUrl(): String = if (tokenUrl.startsWith("http")) tokenUrl else "$serverUrl$tokenUrl"
    fun getFullUserInfoUrl(): String = if (userInfoUrl.startsWith("http")) userInfoUrl else "$serverUrl$userInfoUrl"

    companion object {
        val DEFAULT = ServerConfig(
            serverUrl = "https://api.solian.app",
            clientId = "snchat",
            clientSecret = "",
            authUrl = "https://id.solian.app/auth/authorize",
            tokenUrl = "https://api.solian.app/padlock/auth/open/token",
            userInfoUrl = "https://api.solian.app/padlock/auth/open/userinfo",
            redirectUri = "snchat://oauth2redirect/callback"
        )
    }
}