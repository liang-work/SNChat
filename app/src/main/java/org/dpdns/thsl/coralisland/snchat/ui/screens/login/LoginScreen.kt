package org.dpdns.thsl.coralisland.snchat.ui.screens.login

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dpdns.thsl.coralisland.snchat.data.model.ServerConfig
import org.dpdns.thsl.coralisland.snchat.service.OAuthManager
import org.dpdns.thsl.coralisland.snchat.ui.theme.SNChatTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    oauthLauncher: ActivityResultLauncher<Intent>?,
    oauthManager: OAuthManager?,
    onTokenLogin: (String) -> Unit
) {
    val context = LocalContext.current
    var serverUrl by remember { mutableStateOf(ServerConfig.DEFAULT.serverUrl) }
    var clientId by remember { mutableStateOf(ServerConfig.DEFAULT.clientId) }
    var clientSecret by remember { mutableStateOf(ServerConfig.DEFAULT.clientSecret) }
    var authUrl by remember { mutableStateOf(ServerConfig.DEFAULT.authUrl) }
    var tokenUrl by remember { mutableStateOf(ServerConfig.DEFAULT.tokenUrl) }
    var userInfoUrl by remember { mutableStateOf(ServerConfig.DEFAULT.userInfoUrl) }
    var tokenInput by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    SNChatTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(60.dp))

                Icon(
                    imageVector = Icons.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SNChat",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "连接 Solar Network",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(48.dp))

                val currentConfig = ServerConfig(
                    serverUrl = serverUrl,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    authUrl = authUrl,
                    tokenUrl = tokenUrl,
                    userInfoUrl = userInfoUrl
                )

                Button(
                    onClick = {
                        if (oauthLauncher != null && oauthManager != null) {
                            isLoading = true
                            val intent = oauthManager.getAuthorizationIntent(currentConfig)
                            if (intent != null) {
                                oauthLauncher.launch(intent)
                            } else {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && oauthLauncher != null && oauthManager != null && currentConfig.isConfigured()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("使用 OAuth 登录", fontSize = 16.sp)
                    }
                }

                if (!currentConfig.isConfigured()) {
                    Text(
                        text = "请先在高级设置中配置服务器信息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Divider(modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "高级选项",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("服务器地址") },
                            placeholder = { Text("https://api.solian.app") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            label = { Text("Client ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = clientSecret,
                            onValueChange = { clientSecret = it },
                            label = { Text("Client Secret (可选)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = null
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = authUrl,
                            onValueChange = { authUrl = it },
                            label = { Text("Auth URL 路径") },
                            placeholder = { Text("https://id.solian.app/auth/authorize") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = tokenUrl,
                            onValueChange = { tokenUrl = it },
                            label = { Text("Token URL 路径") },
                            placeholder = { Text("https://api.solian.app/padlock/auth/open/token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = userInfoUrl,
                            onValueChange = { userInfoUrl = it },
                            label = { Text("UserInfo URL 路径") },
                            placeholder = { Text("https://api.solian.app/padlock/auth/open/userinfo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    serverUrl = ServerConfig.DEFAULT.serverUrl
                                    clientId = ServerConfig.DEFAULT.clientId
                                    clientSecret = ServerConfig.DEFAULT.clientSecret
                                    authUrl = ServerConfig.DEFAULT.authUrl
                                    tokenUrl = ServerConfig.DEFAULT.tokenUrl
                                    userInfoUrl = ServerConfig.DEFAULT.userInfoUrl
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("恢复默认")
                            }

                            Button(
                                onClick = { /* 配置已保存到变量中 */ },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("保存配置")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Divider(modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "或使用 Token 登录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Access Token") },
                    placeholder = { Text("请输入你的 Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    visualTransformation = PasswordVisualTransformation()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (tokenInput.isNotBlank()) {
                            onTokenLogin(tokenInput)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = tokenInput.isNotBlank() && !isLoading
                ) {
                    Text("使用 Token 登录")
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}