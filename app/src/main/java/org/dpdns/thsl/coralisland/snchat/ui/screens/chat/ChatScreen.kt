package org.dpdns.thsl.coralisland.snchat.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.noties.markwon.Markwon
import org.dpdns.thsl.coralisland.snchat.data.model.ChatRoom
import org.dpdns.thsl.coralisland.snchat.data.model.Message
import org.dpdns.thsl.coralisland.snchat.data.model.UserInfo
import org.dpdns.thsl.coralisland.snchat.data.model.UserProfile
import org.dpdns.thsl.coralisland.snchat.ui.screens.settings.SettingsScreen
import org.dpdns.thsl.coralisland.snchat.ui.theme.SNChatTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onLogout: () -> Unit,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onBackgroundImageChange: (Boolean) -> Unit = {},
    onClearBackgroundImage: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = remember { ChatViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()
    var showUserMenu by remember { mutableStateOf(false) }
    var messageInput by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var hasBackgroundImage by remember { mutableStateOf(false) }
    var avatarPressed by remember { mutableStateOf(false) }
    var previousMessageCount by remember { mutableIntStateOf(0) }
    val avatarScale by animateFloatAsState(
        targetValue = if (avatarPressed) 0.9f else 1f,
        animationSpec = tween(100),
        label = "avatarScale"
    )

    val listState = rememberLazyListState()
    var lastScrollIndex by remember { mutableIntStateOf(-1) }
    var lastScrollOffset by remember { mutableIntStateOf(0) }

    val roomListState = rememberLazyListState()
    var lastRoomScrollIndex by remember { mutableIntStateOf(-1) }
    var lastRoomScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isEmpty() || uiState.selectedRoom == null) return@LaunchedEffect
        val newCount = uiState.messages.size
        if (newCount > previousMessageCount && previousMessageCount > 0) {
            listState.animateScrollToItem(newCount - 1)
        }
        previousMessageCount = newCount
    }

    LaunchedEffect(roomListState.firstVisibleItemIndex, roomListState.firstVisibleItemScrollOffset) {
        val currentIndex = roomListState.firstVisibleItemIndex
        val currentOffset = roomListState.firstVisibleItemScrollOffset
        val isScrollingUp = lastRoomScrollIndex > currentIndex ||
            (lastRoomScrollIndex == currentIndex && lastRoomScrollOffset > currentOffset + 50)
        if (isScrollingUp && uiState.hasMoreRooms && !uiState.isLoadingMoreRooms) {
            if (currentIndex == 0 && (currentOffset > 0 || uiState.chatRooms.isNotEmpty())) {
                viewModel.loadMoreChatRooms()
            }
        }
        lastRoomScrollIndex = currentIndex
        lastRoomScrollOffset = currentOffset
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val currentIndex = listState.firstVisibleItemIndex
        val currentOffset = listState.firstVisibleItemScrollOffset
        val isScrollingUp = lastScrollIndex > currentIndex ||
            (lastScrollIndex == currentIndex && lastScrollOffset > currentOffset + 50)
        if (isScrollingUp && uiState.hasMoreMessages && !uiState.isLoadingMore) {
            if (currentIndex == 0 && (currentOffset > 0 || uiState.messages.isNotEmpty())) {
                viewModel.loadMoreMessages()
            }
        }
        lastScrollIndex = currentIndex
        lastScrollOffset = currentOffset
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.selectedRoom?.name ?: "消息",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (uiState.selectedRoom != null) {
                        IconButton(onClick = { viewModel.clearSelectedRoom() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (uiState.selectedRoom != null) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { showUserMenu = true },
                            modifier = Modifier.graphicsLayer {
                                scaleX = avatarScale
                                scaleY = avatarScale
                            }
                        ) {
                            val userAvatar = uiState.user?.getAvatarUrl()
                            if (userAvatar != null) {
                                AsyncImage(
                                    model = userAvatar,
                                    contentDescription = "用户头像",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .scale(avatarScale),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val initial = uiState.user?.getDisplayName()?.firstOrNull() ?: '?'
                                BadgedBox(
                                    badge = { Badge { Text(initial.toString()) } }
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = "用户")
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = showUserMenu,
                            onDismissRequest = { showUserMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("个人信息") },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                onClick = {
                                    showUserMenu = false
                                    uiState.user?.name?.let { username ->
                                        viewModel.loadProfile(username)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("设置") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = {
                                    showUserMenu = false
                                    showSettings = true
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("退出登录") },
                                leadingIcon = { Icon(Icons.Default.ExitToApp, null) },
                                onClick = {
                                    showUserMenu = false
                                    onLogout()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.selectedRoom == null) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading && uiState.chatRooms.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    ChatRoomList(
                        chatRooms = uiState.chatRooms,
                        onRoomClick = { viewModel.selectRoom(it) },
                        modifier = Modifier.padding(paddingValues),
                        state = roomListState,
                        isLoadingMore = uiState.isLoadingMoreRooms
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.isLoadingMessages) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.messages,
                        key = { it.id ?: "" }
                    ) { message ->
                        val senderId = message.senderId ?: message.sender?.account?.id
                        MessageItem(
                            message = message,
                            isOwn = message.senderId == uiState.user?.id,
                            senderAvatarUrl = message.sender?.account?.profile?.picture?.url,
                            senderNick = viewModel.getMemberNick(senderId),
                            onAvatarClick = { username ->
                                viewModel.loadProfile(username)
                            }
                        )
                    }
                }

                MessageInput(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    onSend = {
                        if (messageInput.isNotBlank()) {
                            viewModel.sendMessage(messageInput)
                            messageInput = ""
                        }
                    }
                )

                uiState.error?.let { error ->
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("关闭")
                            }
                        }
                    ) {
                        Text(error)
                    }
                }
            }
        }
    }

    if (uiState.selectedProfile != null) {
        ProfileScreen(
            profile = uiState.selectedProfile!!,
            isLoading = uiState.isLoadingProfile,
            isDarkTheme = isDarkTheme,
            showBackgroundImage = hasBackgroundImage,
            onBack = { viewModel.closeProfile() }
        )
    }

    if (showSettings) {
        SettingsScreen(
            onBack = { showSettings = false },
            onThemeChange = { newTheme -> onThemeChange(newTheme) },
            isDarkTheme = isDarkTheme,
            hasBackgroundImage = hasBackgroundImage,
            onBackgroundImageChange = { enabled ->
                hasBackgroundImage = enabled
                onBackgroundImageChange(enabled)
            },
            onClearBackgroundImage = onClearBackgroundImage
        )
    }
}

@Composable
fun ChatRoomList(
    chatRooms: List<ChatRoom>,
    onRoomClick: (ChatRoom) -> Unit,
    modifier: Modifier = Modifier,
    state: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    isLoadingMore: Boolean = false
) {
    if (chatRooms.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "暂无聊天室",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            state = state,
            modifier = modifier.fillMaxSize()
        ) {
            items(chatRooms) { room ->
                ChatRoomItem(
                    room = room,
                    onClick = { onRoomClick(room) }
                )
            }
            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChatRoomItem(
    room: ChatRoom,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                val pictureUrl = room.picture?.url
                if (pictureUrl != null) {
                    AsyncImage(
                        model = pictureUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val isPrivate = room.type == "1"
                    val initial = when {
                        isPrivate && !room.name.isNullOrBlank() -> room.name!!.firstOrNull() ?: '?'
                        !room.name.isNullOrBlank() -> room.name!!.firstOrNull() ?: '群'
                        else -> '?'
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPrivate)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else
                                    MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            initial.toString(),
                            color = if (isPrivate)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (room.type == "1")
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.primary
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (room.type == "1") Icons.Default.Person else Icons.Default.Group,
                        contentDescription = if (room.type == "1") "私聊" else "群聊",
                        modifier = Modifier.size(12.dp),
                        tint = if (room.type == "1")
                            MaterialTheme.colorScheme.onTertiary
                        else
                            MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.name ?: if (room.type == "1") "私聊" else "未命名",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                room.lastMessage?.content?.let { content ->
                    Text(
                        content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (room.unreadCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (room.unreadCount > 99) "99+" else room.unreadCount.toString(),
                        color = MaterialTheme.colorScheme.onError,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
}

@Composable
fun MessageItem(
    message: Message,
    isOwn: Boolean,
    senderAvatarUrl: String? = null,
    senderNick: String? = null,
    onAvatarClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val markwon = remember { Markwon.create(context) }

    val senderName = senderNick
        ?: message.sender?.account?.nick
        ?: message.sender?.account?.profile?.firstName
        ?: message.sender?.account?.name
        ?: "未知"

    val senderAccountName = message.sender?.account?.name

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isOwn) {
            if (senderAvatarUrl != null) {
                AsyncImage(
                    model = senderAvatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { senderAccountName?.let { onAvatarClick(it) } },
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { senderAccountName?.let { onAvatarClick(it) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        senderName.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
        ) {
            if (!isOwn && senderName.isNotBlank()) {
                Text(
                    senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isOwn) 16.dp else 4.dp,
                            bottomEnd = if (isOwn) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isOwn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(12.dp)
            ) {
                Column {
                    if (!message.content.isNullOrBlank()) {
                        MarkdownText(
                            text = message.content ?: "",
                            textColor = if (isOwn) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            markwon = markwon
                        )
                    }
                    message.attachments?.let { attachments ->
                        if (attachments.isNotEmpty()) {
                            if (!message.content.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            AttachmentList(
                                attachments = attachments,
                                isOwn = isOwn
                            )
                        }
                    }
                }
            }

            message.createdAt?.let { time ->
                Text(
                    formatMessageTime(time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息...") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "发送",
                    tint = if (value.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    markwon: Markwon? = null
) {
    val context = LocalContext.current
    val m = markwon ?: remember { Markwon.create(context) }
    val spanned = remember(text) {
        try {
            m.toMarkdown(text)
        } catch (e: Exception) {
            null
        }
    }
    if (spanned != null) {
        androidx.compose.foundation.text.BasicText(
            text = spanned.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(color = textColor)
        )
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )
    }
}

@Composable
fun AttachmentList(
    attachments: List<org.dpdns.thsl.coralisland.snchat.data.model.ChatAttachment>,
    isOwn: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        attachments.forEach { attachment ->
            val isImage = attachment.mimeType?.startsWith("image") == true
            if (isImage && attachment.url != null) {
                AsyncImage(
                    model = attachment.url,
                    contentDescription = attachment.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isOwn)
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isImage) Icons.Default.Person else Icons.Default.Chat,
                            contentDescription = null,
                            tint = if (isOwn)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachment.name ?: "附件",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOwn)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            attachment.size?.let { size ->
                                Text(
                                    text = formatFileSize(size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOwn)
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    profile: UserProfile,
    isLoading: Boolean,
    isDarkTheme: Boolean = false,
    showBackgroundImage: Boolean = false,
    onBack: () -> Unit
) {
    @OptIn(ExperimentalMaterial3Api::class)
    SNChatTheme(darkTheme = isDarkTheme, showBackgroundImage = showBackgroundImage) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("个人资料") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { paddingValues ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        profile.profile?.background?.url?.let { backgroundUrl ->
                            AsyncImage(
                                model = backgroundUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .offset(y = (-50).dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        val avatarUrl = profile.profile?.picture?.url
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "头像",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (profile.profile?.firstName ?: profile.name?.firstOrNull() ?: '?').toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-30).dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = profile.profile?.firstName ?: profile.nick ?: profile.name ?: "未知",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "@${profile.name ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatMessageTime(timestamp: String): String {
    return try {
        val parts = timestamp.split("T")
        if (parts.size >= 2) {
            val timePart = parts[1].split(".")[0]
            val parts2 = timePart.split(":")
            if (parts2.size >= 2) {
                "${parts2[0]}:${parts2[1]}"
            } else timePart
        } else timestamp
    } catch (e: Exception) {
        timestamp
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "${size}B"
        size < 1024 * 1024 -> "${size / 1024}KB"
        else -> "${size / (1024 * 1024)}MB"
    }
}