package org.dpdns.thsl.coralisland.snchat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.dpdns.thsl.coralisland.snchat.data.model.ServerConfig
import org.dpdns.thsl.coralisland.snchat.service.OAuthManager
import org.dpdns.thsl.coralisland.snchat.storage.AppStorage
import org.dpdns.thsl.coralisland.snchat.ui.screens.chat.ChatScreen
import org.dpdns.thsl.coralisland.snchat.ui.screens.login.LoginScreen
import org.dpdns.thsl.coralisland.snchat.ui.theme.SNChatTheme

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private var oauthManager: OAuthManager? = null
    private var pendingOAuthLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        oauthManager = OAuthManager(this)
        oauthManager?.loadAuthState()

        pendingOAuthLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d(TAG, "OAuth launcher result: data=${result.data != null}")
            result.data?.let { data ->
                oauthManager?.handleAuthorizationResult(data)
            }
            
            lifecycleScope.launch {
                kotlinx.coroutines.delay(1500)
                Log.d(TAG, "After OAuth result: isAuthorized=${oauthManager?.isAuthorized()}")
                recreate()
            }
        }

        setContent {
            val storage = remember { AppStorage(this) }
            var isAuthenticated by remember { mutableStateOf(false) }
            var isLoading by remember { mutableStateOf(true) }
            var isDarkTheme by remember { mutableStateOf(false) }
            var hasBackgroundImage by remember { mutableStateOf(false) }
            var fontFamily by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                isLoading = true
                val authorized = oauthManager?.isAuthorized() ?: false
                Log.d(TAG, "Initial auth check: isAuthorized=$authorized")
                isDarkTheme = storage.isDarkTheme.first()
                hasBackgroundImage = storage.hasBackgroundImage.first()
                fontFamily = storage.fontFamily.first()
                isAuthenticated = authorized
                isLoading = false
            }

            SNChatTheme(
                darkTheme = isDarkTheme,
                showBackgroundImage = hasBackgroundImage
            ) {
                if (!isLoading) {
                    if (isAuthenticated) {
                        ChatScreen(
                            onLogout = {
                                oauthManager?.signOut()
                                isAuthenticated = false
                            },
                            isDarkTheme = isDarkTheme,
                            onThemeChange = { newTheme ->
                                isDarkTheme = newTheme
                                lifecycleScope.launch {
                                    storage.saveDarkTheme(newTheme)
                                }
                            },
                            onBackgroundImageChange = { enabled ->
                                hasBackgroundImage = enabled
                            },
                            onClearBackgroundImage = {
                                lifecycleScope.launch {
                                    storage.clearBackgroundImage()
                                    hasBackgroundImage = false
                                }
                            }
                        )
                    } else {
                        LoginScreen(
                            oauthLauncher = pendingOAuthLauncher,
                            oauthManager = oauthManager,
                            onTokenLogin = { token ->
                                lifecycleScope.launch {
                                    storage.saveAuthToken(
                                        org.dpdns.thsl.coralisland.snchat.data.model.AuthToken(
                                            accessToken = token
                                        )
                                    )
                                    isAuthenticated = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        oauthManager?.dispose()
    }
}