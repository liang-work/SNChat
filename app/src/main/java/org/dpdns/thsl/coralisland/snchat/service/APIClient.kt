package org.dpdns.thsl.coralisland.snchat.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.dpdns.thsl.coralisland.snchat.data.model.*
import org.dpdns.thsl.coralisland.snchat.storage.AppStorage
import java.util.concurrent.TimeUnit

private const val TAG = "APIClient"

class APIClient(context: Context) {

    private val storage = AppStorage(context)
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    // Avatar cache: username -> avatarUrl
    private val avatarCache = mutableMapOf<String, String?>()

    fun getCachedAvatar(username: String): String? = avatarCache[username]

    fun cacheAvatar(username: String, url: String?) {
        avatarCache[username] = url
    }

    fun clearAvatarCache() {
        avatarCache.clear()
    }

    suspend fun getUserInfo(token: String): UserInfo? {
        return try {
            val config = storage.getServerConfigSync()
            val request = Request.Builder()
                .url("${config.serverUrl}/passport/accounts/me")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    gson.fromJson(body, UserInfo::class.java)
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getUserAvatar(token: String, username: String): String? {
        // Check cache first
        avatarCache[username]?.let { return it }

        return try {
            val config = storage.getServerConfigSync()
            val request = Request.Builder()
                .url("${config.serverUrl}/passport/accounts/$username/picture")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val url = response.header("Location") ?: response.request.url.toString()
                    avatarCache[username] = url
                    url
                } else {
                    avatarCache[username] = null
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUserAvatar error", e)
            avatarCache[username] = null
            null
        }
    }

    suspend fun getUserBackground(token: String, username: String): String? {
        return try {
            val config = storage.getServerConfigSync()
            val request = Request.Builder()
                .url("${config.serverUrl}/passport/accounts/$username/background")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.header("Location") ?: response.request.url.toString()
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUserBackground error", e)
            null
        }
    }

    suspend fun getUserProfile(token: String, username: String): Map<String, Any>? {
        return try {
            val config = storage.getServerConfigSync()
            val request = Request.Builder()
                .url("${config.serverUrl}/passport/accounts/$username")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    gson.fromJson(body, type)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUserProfile error", e)
            null
        }
    }

    suspend fun getChatRooms(token: String, offset: Int = 0, limit: Int = 20): List<ChatRoom>? {
        return try {
            val config = storage.getServerConfigSync()

            // 1. Get summary
            val summaryRequest = Request.Builder()
                .url("${config.serverUrl}/messager/chat/summary")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val summaryResponse = httpClient.newCall(summaryRequest).execute()
            if (!summaryResponse.isSuccessful) {
                Log.e(TAG, "getChatRooms failed: ${summaryResponse.code}")
                return null
            }

            val body = summaryResponse.body?.string()
            val summaryType = object : TypeToken<Map<String, Any>>() {}.type
            val summaryData: Map<String, Any> = gson.fromJson(body, summaryType)

            // Get all room IDs and sort for consistent ordering
            val allRoomIds = summaryData.keys.sorted()

            // Apply pagination
            val paginatedRoomIds = if (offset >= allRoomIds.size) {
                emptyList()
            } else {
                allRoomIds.subList(offset, minOf(offset + limit, allRoomIds.size))
            }

            // 2. For each room in pagination range, get detail
            val rooms = mutableListOf<ChatRoom>()
            for (roomId in paginatedRoomIds) {
                val detailRequest = Request.Builder()
                    .url("${config.serverUrl}/messager/chat/$roomId")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                try {
                    val detailResponse = httpClient.newCall(detailRequest).execute()
                    if (detailResponse.isSuccessful) {
                        val detailBody = detailResponse.body?.string()
                        val detailType = object : TypeToken<Map<String, Any>>() {}.type
                        val detailData: Map<String, Any> = gson.fromJson(detailBody, detailType)

                        val roomType = detailData["type"] as? String
                        val roomName = detailData["name"] as? String
                        val roomPicture = parsePicture(detailData["picture"] as? Map<String, Any>)
                        val account = detailData["account"] as? Map<String, Any>

                        // For direct chat, show the other person's name
                        val displayName = if (roomType == "direct" && account != null) {
                            val accName = account["nick"] as? String
                                ?: account["name"] as? String
                                ?: account["profile"]?.let {
                                    (it as Map<String, Any>)["first_name"] as? String
                                }
                            accName
                        } else {
                            roomName
                        }

                        // Get last_message from summary
                        val roomData = summaryData[roomId] as? Map<String, Any>
                        val lastMessage = roomData?.get("last_message")?.let {
                            parseMessage(it as Map<String, Any>)
                        }
                        val unreadCount = (roomData?.get("unread_count") as? Number)?.toInt() ?: 0

                        rooms.add(
                            ChatRoom(
                                id = roomId,
                                name = displayName,
                                picture = roomPicture,
                                unreadCount = unreadCount,
                                lastMessage = lastMessage,
                                type = roomType
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "getChatRoomDetail failed for $roomId", e)
                }
            }

            rooms.sortedByDescending { it.lastMessage?.createdAt }
        } catch (e: Exception) {
            Log.e(TAG, "getChatRooms error", e)
            null
        }
    }

    private fun parseChatRooms(data: Map<String, Any>): List<ChatRoom> {
        val rooms = mutableListOf<ChatRoom>()
        data.forEach { (key, value) ->
            if (value is Map<*, *>) {
                val roomData = value as Map<String, Any>
                val chatRoom = roomData["chat_room"] as? Map<String, Any>
                val lastMessage = roomData["last_message"] as? Map<String, Any>

                rooms.add(
                    ChatRoom(
                        id = key,
                        name = chatRoom?.get("name") as? String,
                        picture = parsePicture(chatRoom?.get("picture") as? Map<String, Any>),
                        unreadCount = (roomData["unread_count"] as? Number)?.toInt() ?: 0,
                        lastMessage = lastMessage?.let { parseMessage(it) }
                    )
                )
            }
        }
        return rooms.sortedByDescending { it.lastMessage?.createdAt }
    }

    private fun parsePicture(data: Map<String, Any>?): ChatPicture? {
        return data?.let {
            ChatPicture(
                id = it["id"] as? String,
                url = it["url"] as? String
            )
        }
    }

    private fun parseMessage(data: Map<String, Any>): Message {
        val senderData = data["sender"] as? Map<String, Any>
        val accountData = senderData?.get("account") as? Map<String, Any>
        val profileData = accountData?.get("profile") as? Map<String, Any>
        val attachmentsData = data["attachments"] as? List<Map<String, Any>>

        val createdAtValue = data["created_at"]
        val createdAt = when (createdAtValue) {
            is String -> createdAtValue
            is Map<*, *> -> (createdAtValue["date"] as? String)
            else -> null
        }

        return Message(
            id = data["id"] as? String,
            content = data["content"] as? String,
            createdAt = createdAt,
            senderId = (data["sender_id"] as? String) ?: (senderData?.get("id") as? String),
            sender = senderData?.let {
                MessageSender(
                    id = it["id"] as? String,
                    account = accountData?.let { acc ->
                        Account(
                            id = acc["id"] as? String,
                            name = acc["name"] as? String,
                            nick = acc["nick"] as? String,
                            profile = profileData?.let { prof ->
                                Profile(
                                    firstName = prof["first_name"] as? String,
                                    picture = parsePicture(prof["picture"] as? Map<String, Any>)
                                )
                            }
                        )
                    }
                )
            },
            attachments = attachmentsData?.mapNotNull { parseAttachment(it) }
        )
    }

    private fun parseAttachment(data: Map<String, Any>): ChatAttachment? {
        return try {
            ChatAttachment(
                id = data["id"] as? String,
                name = data["name"] as? String,
                mimeType = data["mime_type"] as? String,
                size = (data["size"] as? Number)?.toLong(),
                blurhash = data["blurhash"] as? String,
                url = data["url"] as? String,
                ratio = (data["ratio"] as? Number)?.toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getMessages(token: String, roomId: String, offset: Int = 0, take: Int = 50): List<Message>? {
        return try {
            val config = storage.getServerConfigSync()
            val url = buildString {
                append("${config.serverUrl}/messager/chat/$roomId/messages?offset=$offset&take=$take")
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                    val data: List<Map<String, Any>> = gson.fromJson(body, type)
                    data.map { parseMessage(it) }
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun sendMessage(token: String, roomId: String, content: String): Message? {
        return try {
            val config = storage.getServerConfigSync()
            val requestBody = """{"content": "$content"}""".toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("${config.serverUrl}/messager/chat/$roomId/messages")
                .header("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    gson.fromJson(body, Message::class.java)
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getChatRoomDetail(token: String, roomId: String): ChatRoomDetail? {
        return try {
            val config = storage.getServerConfigSync()
            val request = Request.Builder()
                .url("${config.serverUrl}/messager/chat/$roomId")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    gson.fromJson(body, ChatRoomDetail::class.java)
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getChatRoomMembers(token: String, roomId: String, offset: Int = 0, take: Int = 100): List<ChatMember>? {
        return try {
            val config = storage.getServerConfigSync()
            val request = Request.Builder()
                .url("${config.serverUrl}/messager/chat/$roomId/members?offset=$offset&take=$take")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                    val data: List<Map<String, Any>> = gson.fromJson(body, type)
                    data.mapNotNull { parseChatMember(it) }
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getChatRoomMembers error", e)
            null
        }
    }

    private fun parseChatMember(data: Map<String, Any>): ChatMember? {
        val accountData = data["account"] as? Map<String, Any>
        val profileData = accountData?.get("profile") as? Map<String, Any>

        return ChatMember(
            id = data["account_id"] as? String,
            nick = data["nick"] as? String,
            realmNick = data["realm_nick"] as? String,
            account = accountData?.let {
                Account(
                    id = it["id"] as? String,
                    name = it["name"] as? String,
                    nick = it["nick"] as? String,
                    profile = profileData?.let { prof ->
                        Profile(
                            firstName = prof["first_name"] as? String,
                            picture = parsePicture(prof["picture"] as? Map<String, Any>)
                        )
                    }
                )
            }
        )
    }
}