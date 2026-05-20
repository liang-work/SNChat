package org.dpdns.thsl.coralisland.snchat.data.model

import com.google.gson.annotations.SerializedName

data class AuthToken(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    @SerializedName("token_type")
    val tokenType: String = "Bearer",
    @SerializedName("expires_in")
    val expiresIn: Long? = null,
    @SerializedName("scope")
    val scope: String? = null,
    @SerializedName("id_token")
    val idToken: String? = null
) {
    fun getAuthorizationHeader(): String = "$tokenType $accessToken"
}

data class UserInfo(
    val id: String? = null,
    @SerializedName("sub")
    val sub: String? = null,
    val name: String? = null,
    @SerializedName("display_name")
    val displayName: String? = null,
    val nickname: String? = null,
    val email: String? = null,
    val picture: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null
) {
    @JvmName("resolveDisplayName")
    fun getDisplayName(): String = displayName ?: nickname ?: name ?: "用户"
    @JvmName("resolveAvatarUrl")
    fun getAvatarUrl(): String? = avatarUrl ?: picture
}