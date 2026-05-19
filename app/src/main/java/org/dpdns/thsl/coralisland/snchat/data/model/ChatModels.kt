package org.dpdns.thsl.coralisland.snchat.data.model

import com.google.gson.annotations.SerializedName

data class ChatRoom(
    val id: String? = null,
    val name: String? = null,
    val picture: ChatPicture? = null,
    val members: List<ChatMember>? = null,
    @SerializedName("last_message")
    val lastMessage: Message? = null,
    @SerializedName("unread_count")
    val unreadCount: Int = 0,
    @SerializedName("created_at")
    val createdAt: String? = null,
    val type: String? = null
)

data class ChatPicture(
    val id: String? = null,
    val url: String? = null
)

data class ChatMember(
    val id: String? = null,
    val nick: String? = null,
    @SerializedName("realm_nick")
    val realmNick: String? = null,
    val account: Account? = null
)

data class Account(
    val id: String? = null,
    val name: String? = null,
    val nick: String? = null,
    val profile: Profile? = null
)

data class Profile(
    @SerializedName("first_name")
    val firstName: String? = null,
    val picture: ChatPicture? = null
)

data class Message(
    val id: String? = null,
    @SerializedName("sender_id")
    val senderId: String? = null,
    val sender: MessageSender? = null,
    val content: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    val attachments: List<ChatAttachment>? = null
)

data class ChatAttachment(
    val id: String? = null,
    val name: String? = null,
    @SerializedName("mime_type")
    val mimeType: String? = null,
    val size: Long? = null,
    val blurhash: String? = null,
    val url: String? = null,
    val ratio: Float? = null
)

data class MessageSender(
    val id: String? = null,
    val account: Account? = null
)

data class ChatRoomDetail(
    val id: String? = null,
    val name: String? = null,
    val picture: ChatPicture? = null,
    val members: List<ChatMember>? = null,
    val realm: Realm? = null
)

data class Realm(
    val id: String? = null,
    val name: String? = null,
    val picture: ChatPicture? = null
)

data class UserProfile(
    val id: String? = null,
    val name: String? = null,
    val nick: String? = null,
    val profile: ProfileDetail? = null,
    val bio: String? = null
)

data class ProfileDetail(
    @SerializedName("first_name")
    val firstName: String? = null,
    val bio: String? = null,
    val picture: ChatPicture? = null,
    val background: ChatPicture? = null,
    val links: List<UserLink>? = null
)

data class UserLink(
    val name: String? = null,
    val url: String? = null
)