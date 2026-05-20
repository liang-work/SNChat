package org.dpdns.thsl.coralisland.snchat.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.dpdns.thsl.coralisland.snchat.data.model.ServerConfig
import org.dpdns.thsl.coralisland.snchat.storage.AppStorage
import org.dpdns.thsl.coralisland.snchat.ui.theme.SNChatTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChange: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    hasBackgroundImage: Boolean = false,
    onBackgroundImageChange: ((Boolean) -> Unit)? = null,
    onClearBackgroundImage: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val storage = remember { AppStorage(context) }
    val scope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showApiDialog by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    var apiUrl by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                storage.saveBackgroundImageFromUri(it)
                onBackgroundImageChange?.invoke(true)
            }
        }
    }

    LaunchedEffect(Unit) {
        apiUrl = storage.serverConfig.first().serverUrl
    }

    SNChatTheme(darkTheme = isDarkTheme, showBackgroundImage = hasBackgroundImage) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("设置") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // 外观设置
                SettingsSection(title = "外观") {
                    SettingsItem(
                        icon = Icons.Default.DarkMode,
                        title = "深色主题",
                        subtitle = if (isDarkTheme) "已开启" else "已关闭",
                        onClick = { showThemeDialog = true }
                    )
                    SettingsItem(
                        icon = Icons.Default.Wallpaper,
                        title = "背景图片",
                        subtitle = if (hasBackgroundImage) "已启用" else "未启用",
                        onClick = { imagePickerLauncher.launch("image/*") }
                    )
                    if (hasBackgroundImage) {
                        SettingsItem(
                            icon = Icons.Default.DeleteOutline,
                            title = "清除背景图片",
                            subtitle = "移除已设置的背景图片",
                            onClick = {
                                onClearBackgroundImage?.invoke()
                                onBackgroundImageChange?.invoke(false)
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 服务器设置
                SettingsSection(title = "服务器") {
                    SettingsItem(
                        icon = Icons.Default.Cloud,
                        title = "API 服务器",
                        subtitle = apiUrl,
                        onClick = { showApiDialog = true }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 数据管理
                SettingsSection(title = "数据管理") {
                    SettingsItem(
                        icon = Icons.Default.DeleteSweep,
                        title = "清除缓存",
                        subtitle = "清除图片缓存等临时文件",
                        onClick = { showClearCacheConfirm = true }
                    )
                    SettingsItem(
                        icon = Icons.Default.RestartAlt,
                        title = "重置数据库",
                        subtitle = "清除所有本地聊天记录",
                        onClick = { showResetConfirm = true },
                        dangerous = true
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 关于
                SettingsSection(title = "关于") {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "版本",
                        subtitle = "1.0.0",
                        onClick = { }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Theme Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("选择主题") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("浅色模式") },
                        leadingContent = { RadioButton(selected = !isDarkTheme, onClick = {
                            onThemeChange(false)
                            scope.launch { storage.saveDarkTheme(false) }
                            showThemeDialog = false
                        }) },
                        modifier = Modifier.clickable {
                            onThemeChange(false)
                            scope.launch { storage.saveDarkTheme(false) }
                            showThemeDialog = false
                        }
                    )
                    ListItem(
                        headlineContent = { Text("深色模式") },
                        leadingContent = { RadioButton(selected = isDarkTheme, onClick = {
                            onThemeChange(true)
                            scope.launch { storage.saveDarkTheme(true) }
                            showThemeDialog = false
                        }) },
                        modifier = Modifier.clickable {
                            onThemeChange(true)
                            scope.launch { storage.saveDarkTheme(true) }
                            showThemeDialog = false
                        }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("取消") } }
        )
    }

    // API URL Dialog
    if (showApiDialog) {
        var tempUrl by remember { mutableStateOf(apiUrl) }
        AlertDialog(
            onDismissRequest = { showApiDialog = false },
            title = { Text("API 服务器地址") },
            text = {
                OutlinedTextField(
                    value = tempUrl,
                    onValueChange = { tempUrl = it },
                    label = { Text("服务器 URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    apiUrl = tempUrl
                    scope.launch {
                        val config = storage.serverConfig.first()
                        storage.saveServerConfig(config.copy(serverUrl = tempUrl))
                    }
                    showApiDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showApiDialog = false }) { Text("取消") }
            }
        )
    }

    // Clear Cache Confirm
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("清除缓存") },
            text = { Text("确定要清除所有缓存吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheConfirm = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text("取消") }
            }
        )
    }

    // Reset Database Confirm
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置数据库") },
            text = { Text("警告：此操作将清除所有本地聊天记录，且无法恢复！") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                }) { Text("确定重置") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    dangerous: Boolean = false
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = if (dangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (dangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}