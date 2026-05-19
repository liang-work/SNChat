package org.dpdns.thsl.coralisland.snchat.ui.screens.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.dpdns.thsl.coralisland.snchat.data.model.Account
import org.dpdns.thsl.coralisland.snchat.data.model.ChatMember
import org.dpdns.thsl.coralisland.snchat.data.model.ChatRoom
import org.dpdns.thsl.coralisland.snchat.data.model.Message
import org.dpdns.thsl.coralisland.snchat.data.model.MessageSender
import org.dpdns.thsl.coralisland.snchat.data.model.Profile
import org.dpdns.thsl.coralisland.snchat.data.model.UserInfo
import org.dpdns.thsl.coralisland.snchat.data.model.UserProfile
import org.dpdns.thsl.coralisland.snchat.service.APIClient
import org.dpdns.thsl.coralisland.snchat.storage.AppStorage

private const val TAG = "ChatViewModel"

data class ChatUiState(
    val isLoading: Boolean = false,
    val isLoadingUser: Boolean = true,
    val isLoadingMessages: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingMoreRooms: Boolean = false,
    val isLoadingProfile: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val hasMoreRooms: Boolean = true,
    val user: UserInfo? = null,
    val chatRooms: List<ChatRoom> = emptyList(),
    val selectedRoom: ChatRoom? = null,
    val messages: List<Message> = emptyList(),
    val selectedProfile: UserProfile? = null,
    val members: List<ChatMember> = emptyList(),
    val error: String? = null
)

class ChatViewModel(private val context: Context) : ViewModel() {

    private val storage = AppStorage(context)
    private val apiClient = APIClient(context)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private val messagesCache = mutableMapOf<String, List<Message>>()
    private val profilesCache = mutableMapOf<String, UserProfile>()
    private val memberAvatarsCache = mutableMapOf<String, String>()
    private val membersCache = mutableMapOf<String, List<ChatMember>>()

    init {
        loadUserInfo()
    }

    private fun loadUserInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingUser = true)
            try {
                val token = storage.authToken.first()
                Log.d(TAG, "loadUserInfo: token=${token != null}")
                if (token != null) {
                    var user = apiClient.getUserInfo(token.accessToken)
                    Log.d(TAG, "loadUserInfo: user=$user")

                    user?.name?.let { username ->
                        val avatarUrl = apiClient.getUserAvatar(token.accessToken, username)
                        if (avatarUrl != null) {
                            user = UserInfo(
                                id = user?.id,
                                sub = user?.sub,
                                name = user?.name,
                                displayName = user?.displayName,
                                nickname = user?.nickname,
                                email = user?.email,
                                picture = user?.picture,
                                avatarUrl = avatarUrl
                            )
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        user = user,
                        isLoadingUser = false
                    )
                    loadChatRooms()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingUser = false,
                        error = "未找到登录凭证"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadUserInfo error", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingUser = false,
                    error = e.message
                )
            }
        }
    }

    private fun loadData() {
        loadUserInfo()
    }

    fun loadChatRooms() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val token = storage.authToken.first() ?: return@launch
                Log.d(TAG, "loadChatRooms: fetching rooms...")
                val rooms = apiClient.getChatRooms(token.accessToken)
                Log.d(TAG, "loadChatRooms: rooms count=${rooms?.size ?: 0}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    chatRooms = rooms ?: emptyList(),
                    hasMoreRooms = rooms?.size ?: 0 >= 20
                )
            } catch (e: Exception) {
                Log.e(TAG, "loadChatRooms error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun loadMoreChatRooms() {
        if (_uiState.value.isLoadingMoreRooms || !_uiState.value.hasMoreRooms) return

        val currentRooms = _uiState.value.chatRooms
        if (currentRooms.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingMoreRooms = true)
            try {
                val token = storage.authToken.first() ?: return@launch
                val offset = currentRooms.size
                Log.d(TAG, "loadMoreChatRooms: offset=$offset")
                val moreRooms = apiClient.getChatRooms(token.accessToken, offset = offset)
                val newRooms = currentRooms + (moreRooms ?: emptyList())
                _uiState.value = _uiState.value.copy(
                    isLoadingMoreRooms = false,
                    chatRooms = newRooms,
                    hasMoreRooms = moreRooms?.size ?: 0 >= 20
                )
            } catch (e: Exception) {
                Log.e(TAG, "loadMoreChatRooms error", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingMoreRooms = false,
                    error = e.message
                )
            }
        }
    }

    fun selectRoom(room: ChatRoom) {
        val cachedMessages = messagesCache[room.id]
        val cachedMembers = membersCache[room.id] ?: emptyList()

        _uiState.value = _uiState.value.copy(
            selectedRoom = room,
            messages = cachedMessages ?: emptyList(),
            members = cachedMembers
        )

        loadMessages()
        room.id?.let { loadMembers(it) }
        startPolling()
    }

    fun clearSelectedRoom() {
        pollingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            selectedRoom = null,
            messages = emptyList(),
            members = emptyList()
        )
    }

    private fun loadMembers(roomId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = storage.authToken.first() ?: return@launch
                val members = apiClient.getChatRoomMembers(token.accessToken, roomId) ?: emptyList()

                membersCache[roomId] = members

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(members = members)
                }

                for (member in members) {
                    val accountId = member.id ?: member.account?.id
                    if (accountId != null && memberAvatarsCache[accountId] == null) {
                        member.account?.name?.let { name ->
                            val avatarUrl = apiClient.getUserAvatar(token.accessToken, name)
                            if (avatarUrl != null) {
                                memberAvatarsCache[accountId] = avatarUrl
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMembers error", e)
            }
        }
    }

    fun getMemberAvatar(senderId: String?): String? {
        if (senderId == null) return null
        return memberAvatarsCache[senderId]
    }

    fun getMemberNick(senderId: String?): String? {
        if (senderId == null) return null
        return membersCache[_uiState.value.selectedRoom?.id]?.find {
            it.id == senderId || it.account?.id == senderId
        }?.let { member ->
            member.nick ?: member.realmNick ?: member.account?.nick ?: member.account?.name
        }
    }

    fun loadProfile(username: String) {
        profilesCache[username]?.let { cached ->
            _uiState.value = _uiState.value.copy(
                isLoadingProfile = false,
                selectedProfile = cached
            )
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingProfile = true)
            try {
                val token = storage.authToken.first() ?: return@launch
                val profileData = apiClient.getUserProfile(token.accessToken, username)

                if (profileData != null) {
                    val avatarUrl = apiClient.getUserAvatar(token.accessToken, username)
                    val backgroundUrl = apiClient.getUserBackground(token.accessToken, username)

                    val profile = UserProfile(
                        id = profileData["id"] as? String,
                        name = profileData["name"] as? String,
                        nick = profileData["nick"] as? String,
                        profile = org.dpdns.thsl.coralisland.snchat.data.model.ProfileDetail(
                            firstName = (profileData["profile"] as? Map<String, Any>)?.get("first_name") as? String,
                            bio = (profileData["profile"] as? Map<String, Any>)?.get("bio") as? String,
                            picture = avatarUrl?.let { org.dpdns.thsl.coralisland.snchat.data.model.ChatPicture(url = it) },
                            background = backgroundUrl?.let { org.dpdns.thsl.coralisland.snchat.data.model.ChatPicture(url = it) }
                        ),
                        bio = (profileData["profile"] as? Map<String, Any>)?.get("bio") as? String
                    )

                    profilesCache[username] = profile

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isLoadingProfile = false,
                            selectedProfile = profile
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadProfile error", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingProfile = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun closeProfile() {
        _uiState.value = _uiState.value.copy(selectedProfile = null)
    }

    private fun loadMessages() {
        val roomId = _uiState.value.selectedRoom?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingMessages = true, hasMoreMessages = true)
            try {
                val token = storage.authToken.first() ?: return@launch
                val messages = apiClient.getMessages(token.accessToken, roomId, offset = 0)
                val reversedMessages = messages?.reversed() ?: emptyList()

                withContext(Dispatchers.Main) {
                    messagesCache[roomId] = reversedMessages
                    _uiState.value = _uiState.value.copy(
                        isLoadingMessages = false,
                        messages = reversedMessages,
                        hasMoreMessages = messages?.size ?: 0 >= 50
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMessages = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun loadMoreMessages() {
        val roomId = _uiState.value.selectedRoom?.id ?: return
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMoreMessages) return

        val currentCount = _uiState.value.messages.size

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val token = storage.authToken.first() ?: return@launch
                val moreMessages = apiClient.getMessages(token.accessToken, roomId, offset = currentCount, take = 50)
                val reversedMore = moreMessages?.reversed() ?: emptyList()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        messages = _uiState.value.messages + reversedMore,
                        hasMoreMessages = (moreMessages?.size ?: 0) >= 50
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        error = e.message
                    )
                }
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                if (_uiState.value.selectedRoom != null) {
                    loadMessages()
                }
            }
        }
    }

    fun sendMessage(content: String) {
        val roomId = _uiState.value.selectedRoom?.id ?: return
        val currentUser = _uiState.value.user ?: return
        val senderId = currentUser.id ?: return

        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        // Create local message immediately for instant feedback
        val tempMessage = Message(
            id = "temp_${System.currentTimeMillis()}",
            content = content,
            senderId = senderId,
            sender = MessageSender(
                id = senderId,
                account = Account(
                    id = currentUser.id,
                    name = currentUser.name,
                    nick = currentUser.nickname
                )
            ),
            createdAt = timestamp
        )

        // Add message immediately to show in UI
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + tempMessage
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = storage.authToken.first() ?: return@launch
                apiClient.sendMessage(token.accessToken, roomId, content)
                // Reload to get server confirmation and real message data
                loadMessages()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(error = "发送失败: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun refresh() {
        loadData()
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}

class ChatViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(context) as T
    }
}