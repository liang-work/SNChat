package org.dpdns.thsl.coralisland.snchat.ui.screens.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.dpdns.thsl.coralisland.snchat.data.model.AuthToken
import org.dpdns.thsl.coralisland.snchat.data.model.ServerConfig
import org.dpdns.thsl.coralisland.snchat.data.model.UserInfo
import org.dpdns.thsl.coralisland.snchat.service.APIClient
import org.dpdns.thsl.coralisland.snchat.service.OAuthService
import org.dpdns.thsl.coralisland.snchat.storage.AppStorage

data class LoginUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val user: UserInfo? = null,
    val error: String? = null,
    val oauthUrl: String? = null,
    val showAdvanced: Boolean = false,
    val serverConfig: ServerConfig = ServerConfig()
)

class LoginViewModel(private val context: Context) : ViewModel() {

    private val storage = AppStorage(context)
    private val oauthService = OAuthService(context)
    private val apiClient = APIClient(context)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        loadStoredConfig()
    }

    private fun loadStoredConfig() {
        viewModelScope.launch {
            val config = storage.serverConfig.first()
            _uiState.value = _uiState.value.copy(serverConfig = config)
            checkAuthStatus()
        }
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            val token = storage.authToken.first()
            if (token != null) {
                val config = storage.getServerConfigSync()
                val isValid = oauthService.validateToken(config, token.accessToken)
                if (isValid) {
                    val user = apiClient.getUserInfo(token.accessToken)
                    _uiState.value = _uiState.value.copy(
                        isAuthenticated = true,
                        user = user
                    )
                } else if (token.refreshToken != null) {
                    val newToken = oauthService.refreshToken(config, token.refreshToken)
                    if (newToken != null) {
                        storage.saveAuthToken(newToken)
                        val user = apiClient.getUserInfo(newToken.accessToken)
                        _uiState.value = _uiState.value.copy(
                            isAuthenticated = true,
                            user = user
                        )
                    }
                }
            }
        }
    }

    fun startOAuthLogin() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val config = _uiState.value.serverConfig
                if (!config.isConfigured()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "请先配置服务器信息"
                    )
                    return@launch
                }

                val url = oauthService.getAuthorizationUrl(config)
                if (url != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        oauthUrl = url
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "OAuth 配置不完整"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "OAuth 登录失败"
                )
            }
        }
    }

    fun onOAuthCodeReceived(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val config = _uiState.value.serverConfig
                val token = oauthService.exchangeCodeForToken(config, code)
                if (token != null) {
                    storage.saveAuthToken(token)
                    val user = apiClient.getUserInfo(token.accessToken)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        user = user,
                        oauthUrl = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Token 交换失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "登录失败"
                )
            }
        }
    }

    fun tokenLogin(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val config = _uiState.value.serverConfig
                val isValid = oauthService.validateToken(config, token)
                if (isValid) {
                    val authToken = AuthToken(accessToken = token)
                    storage.saveAuthToken(authToken)
                    val user = apiClient.getUserInfo(token)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        user = user
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Token 无效"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Token 登录失败"
                )
            }
        }
    }

    fun saveServerConfig(config: ServerConfig) {
        viewModelScope.launch {
            storage.saveServerConfig(config)
            _uiState.value = _uiState.value.copy(serverConfig = config)
        }
    }

    fun toggleAdvanced() {
        _uiState.value = _uiState.value.copy(
            showAdvanced = !_uiState.value.showAdvanced
        )
    }

    fun clearOAuthUrl() {
        _uiState.value = _uiState.value.copy(oauthUrl = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun logout() {
        viewModelScope.launch {
            storage.clearAuthToken()
            _uiState.value = _uiState.value.copy(
                isAuthenticated = false,
                user = null
            )
        }
    }
}

class LoginViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LoginViewModel(context) as T
    }
}