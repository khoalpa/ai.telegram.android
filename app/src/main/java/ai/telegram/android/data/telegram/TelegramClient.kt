package ai.telegram.android.data.telegram

import ai.telegram.android.data.MessageSyncState
import ai.telegram.android.data.MessageKind

enum class TelegramChatPermissionPreset {
    READ_ONLY,
    TEXT_ONLY,
    MEDIA_ALLOWED,
    FULL
}

data class MessageSendOptions(
    val disableNotification: Boolean = false,
    val scheduledAtEpochSeconds: Int = 0
) {
    val hasSchedule: Boolean
        get() = scheduledAtEpochSeconds > 0
}

data class TelegramReadStateUpdate(
    val chatId: Long,
    val lastReadMessageId: Long,
    val unreadCount: Int = 0,
    val outgoing: Boolean = false
)

data class TelegramMessageSyncUpdate(
    val chatId: Long,
    val messageId: Long,
    val state: MessageSyncState,
    val isRead: Boolean = false
)

data class TelegramMessageContentUpdate(
    val chatId: Long,
    val messageId: Long,
    val text: String
)

data class TelegramChatHistoryLoad(
    val chatId: Long,
    val fromMessageId: Long,
    val messageIds: List<Long>,
    val requestedLimit: Int
)

data class TelegramChatActionUpdate(
    val chatId: Long,
    val senderId: String,
    val action: String
)

data class TelegramPinnedMessageUpdate(
    val chatId: Long,
    val messageId: Long
)

data class TelegramCallProtocol(
    val minLayer: Int = 65,
    val maxLayer: Int = 200,
    val udpP2p: Boolean = true,
    val udpReflector: Boolean = true,
    val libraryVersions: List<String> = listOf("tdlib")
)

data class TelegramCallServer(
    val id: Long,
    val ipv4: String = "",
    val ipv6: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
    val supportsTurn: Boolean = true,
    val supportsStun: Boolean = false,
    val isTcp: Boolean = false,
    val peerTag: ByteArray = byteArrayOf()
)

data class TelegramCallSignalingData(
    val callId: Int,
    val data: ByteArray
)

enum class TelegramCallState {
    Pending,
    ExchangingKeys,
    Ready,
    HangingUp,
    Discarded,
    Error,
    Unknown
}

data class TelegramCall(
    val id: Int,
    val userId: Long,
    val isOutgoing: Boolean,
    val isVideo: Boolean,
    val state: TelegramCallState,
    val isCreated: Boolean = false,
    val isReceived: Boolean = false,
    val durationSeconds: Int = 0,
    val stateLabel: String = "",
    val errorMessage: String = "",
    val protocol: TelegramCallProtocol = TelegramCallProtocol(),
    val servers: List<TelegramCallServer> = emptyList(),
    val config: String = "",
    val encryptionKey: ByteArray = byteArrayOf(),
    val emojis: List<String> = emptyList(),
    val allowP2p: Boolean = true,
    val customParameters: String = ""
)

enum class TelegramStoryState {
    Active,
    Sending,
    Expired,
    Deleted,
    Failed,
    Unknown
}

data class TelegramStory(
    val chatId: Long,
    val id: Int,
    val senderChatId: Long = chatId,
    val dateMillis: Long = 0L,
    val caption: String = "",
    val kind: MessageKind = MessageKind.Text,
    val mediaFileId: Int = 0,
    val mediaLocalPath: String = "",
    val mediaThumbnailFileId: Int = 0,
    val mediaThumbnailLocalPath: String = "",
    val isOutgoing: Boolean = false,
    val isPinned: Boolean = false,
    val isEdited: Boolean = false,
    val isViewed: Boolean = false,
    val state: TelegramStoryState = TelegramStoryState.Active,
    val stateLabel: String = ""
)

data class TelegramBotCommand(
    val botUserId: Long,
    val command: String,
    val description: String
)

data class TelegramBotMenuButton(
    val botUserId: Long,
    val type: String,
    val text: String = "",
    val url: String = ""
)

data class TelegramInlineBotResult(
    val id: String,
    val type: String,
    val title: String = "",
    val description: String = "",
    val thumbnailFileId: Int = 0,
    val thumbnailLocalPath: String = ""
)

data class TelegramInlineBotResults(
    val botUserId: Long,
    val queryId: Long,
    val nextOffset: String,
    val results: List<TelegramInlineBotResult>
)

data class TelegramWebAppUrl(
    val botUserId: Long,
    val url: String
)

enum class TelegramPrivacySetting {
    LastSeen,
    ProfilePhoto,
    PhoneNumber,
    ForwardedMessages,
    Calls,
    ChatInvites,
    Bio
}

enum class TelegramPrivacyPreset {
    Everybody,
    Contacts,
    Nobody
}

data class TelegramPrivacyRuleSummary(
    val setting: TelegramPrivacySetting,
    val preset: TelegramPrivacyPreset,
    val rawRule: String = ""
)

data class TelegramActiveSession(
    val id: Long,
    val applicationName: String,
    val applicationVersion: String = "",
    val deviceModel: String = "",
    val platform: String = "",
    val systemVersion: String = "",
    val ip: String = "",
    val country: String = "",
    val region: String = "",
    val isCurrent: Boolean = false,
    val lastActiveDate: Int = 0
)

enum class TelegramPremiumFeature {
    Stories,
    Reactions,
    Stickers,
    Downloads,
    Business,
    VoiceToText,
    ProfileBadge,
    EmojiStatus
}

enum class TelegramPremiumLimitType {
    ChatFolderCount,
    PinnedChatCount,
    CaptionLength,
    BioLength,
    ChatFilterChosenChatCount
}

data class TelegramPremiumFeatureInfo(
    val type: String,
    val title: String = "",
    val description: String = ""
)

data class TelegramPremiumLimitSummary(
    val type: TelegramPremiumLimitType,
    val defaultValue: Int = 0,
    val premiumValue: Int = 0
)

data class TelegramStarBalance(
    val amount: Long,
    val nanoAmount: Int = 0
)

data class TelegramStarTransaction(
    val id: String,
    val amount: Long,
    val title: String = "",
    val description: String = "",
    val date: Int = 0
)

data class TelegramBusinessChatLink(
    val link: String,
    val text: String = "",
    val title: String = ""
)

interface TelegramClient {
    fun start()
    fun close()
    fun setPhoneNumber(phoneNumber: String)
    fun checkCode(code: String)
    fun checkPassword(password: String)
    fun resendAuthenticationCode()
    fun logOut()
    fun loadMainChatList(limit: Int = 100)
    fun loadChat(chatId: Long)
    fun loadChatHistory(chatId: Long, fromMessageId: Long = 0, limit: Int = 100)
    fun loadContacts(limit: Int = 200)
    fun searchChats(query: String, limit: Int = 50)
    fun searchPublicChats(query: String)
    fun joinChannel(usernameOrLink: String)
    fun openTelegramLink(link: String)
    fun sendTextMessage(chatId: Long, text: String, options: MessageSendOptions = MessageSendOptions())
    fun sendReplyTextMessage(
        chatId: Long,
        replyToMessageId: Long,
        text: String,
        options: MessageSendOptions = MessageSendOptions()
    )
    fun sendMediaMessage(
        chatId: Long,
        localPath: String,
        kind: MessageKind,
        caption: String = "",
        options: MessageSendOptions = MessageSendOptions()
    )
    fun sendPollMessage(
        chatId: Long,
        question: String,
        options: List<String>,
        isAnonymous: Boolean = true,
        allowMultipleAnswers: Boolean = false,
        sendOptions: MessageSendOptions = MessageSendOptions()
    )
    fun sendContactMessage(
        chatId: Long,
        firstName: String,
        lastName: String = "",
        phoneNumber: String,
        userId: Long = 0L,
        options: MessageSendOptions = MessageSendOptions()
    )
    fun editTextMessage(chatId: Long, messageId: Long, text: String)
    fun deleteMessages(chatId: Long, messageIds: List<Long>, revoke: Boolean = true)
    fun forwardMessages(toChatId: Long, fromChatId: Long, messageIds: List<Long>)
    fun pinMessage(chatId: Long, messageId: Long, disableNotification: Boolean = true, onlyForSelf: Boolean = false)
    fun unpinMessage(chatId: Long, messageId: Long)
    fun unpinAllChatMessages(chatId: Long)
    fun addMessageReaction(chatId: Long, messageId: Long, emoji: String, isBig: Boolean = false)
    fun createPrivateChat(userId: Long)
    fun createSecretChat(userId: Long)
    fun createCall(userId: Long, isVideo: Boolean = false)
    fun acceptCall(callId: Int)
    fun discardCall(callId: Int, isDisconnected: Boolean = true)
    fun sendCallSignalingData(callId: Int, data: ByteArray)
    fun loadActiveStories(chatId: Long)
    fun loadStory(chatId: Long, storyId: Int)
    fun viewStory(chatId: Long, storyId: Int)
    fun postStory(chatId: Long, localPath: String, kind: MessageKind, caption: String = "")
    fun deleteStory(chatId: Long, storyId: Int)
    fun searchBot(usernameOrLink: String)
    fun startBot(botUserId: Long, chatId: Long, parameter: String = "")
    fun loadBotCommands(botUserId: Long)
    fun loadBotMenuButton(botUserId: Long)
    fun requestInlineBotResults(botUserId: Long, chatId: Long, query: String, offset: String = "")
    fun sendInlineBotResult(chatId: Long, queryId: Long, resultId: String, hideViaBot: Boolean = false)
    fun clickBotCallbackButton(chatId: Long, messageId: Long, payload: String)
    fun openBotWebApp(botUserId: Long, chatId: Long, url: String)
    fun sendWebAppData(botUserId: Long, buttonText: String, data: String)
    fun updateProfile(firstName: String, lastName: String = "", bio: String = "", username: String = "")
    fun setProfilePhoto(localPath: String)
    fun loadPrivacySetting(setting: TelegramPrivacySetting)
    fun setPrivacySetting(setting: TelegramPrivacySetting, preset: TelegramPrivacyPreset)
    fun loadActiveSessions()
    fun terminateSession(sessionId: Long)
    fun terminateAllOtherSessions()
    fun loadPremiumFeatures()
    fun viewPremiumFeature(feature: TelegramPremiumFeature)
    fun loadPremiumLimit(limitType: TelegramPremiumLimitType)
    fun loadStarBalance()
    fun loadStarTransactions(offset: String = "", limit: Int = 50)
    fun loadBusinessChatLinks()
    fun createBusinessChatLink(message: String)
    fun deleteBusinessChatLink(link: String)
    fun setUserBlocked(userId: Long, blocked: Boolean)
    fun addContact(userId: Long, firstName: String, lastName: String = "", phoneNumber: String = "")
    fun deleteContact(userId: Long)
    fun createBasicGroupChat(userIds: List<Long>, title: String)
    fun createChannel(title: String, description: String = "")
    fun createForumGroup(title: String, description: String = "")
    fun setChatTitle(chatId: Long, title: String)
    fun setChatDescription(chatId: Long, description: String)
    fun setChatMessageAutoDeleteTime(chatId: Long, seconds: Int)
    fun setChatSlowModeDelay(chatId: Long, seconds: Int)
    fun setChatPermissions(chatId: Long, preset: TelegramChatPermissionPreset)
    fun createChatInviteLink(chatId: Long, name: String = "")
    fun revokeChatInviteLink(chatId: Long, inviteLink: String)
    fun addChatMember(chatId: Long, userId: Long, forwardLimit: Int = 50)
    fun removeChatMember(chatId: Long, userId: Long)
    fun banChatMember(chatId: Long, userId: Long, bannedUntilDate: Int = 0)
    fun unbanChatMember(chatId: Long, userId: Long)
    fun promoteChatMember(chatId: Long, userId: Long, customTitle: String = "")
    fun demoteChatMember(chatId: Long, userId: Long)
    fun setChatDraftMessage(chatId: Long, text: String)
    fun clearChatDraftMessage(chatId: Long)
    fun pinChat(chatId: Long, pinned: Boolean)
    fun muteChat(chatId: Long, muteForSeconds: Int = 31_536_000)
    fun unmuteChat(chatId: Long)
    fun archiveChat(chatId: Long, archived: Boolean = true)
    fun markChatRead(chatId: Long, limit: Int = 100)
    fun markChatUnread(chatId: Long, markedUnread: Boolean)
    fun clearChatHistory(chatId: Long, revoke: Boolean = false)
    fun leaveChat(chatId: Long)
    fun loadMessage(chatId: Long, messageId: Long)
    fun downloadMessageMedia(chatId: Long, messageId: Long, priority: Int = 16, limitBytes: Int = 0)
    fun downloadFile(fileId: Int, priority: Int = 16, limitBytes: Int = 0)
    fun cancelDownloadFile(fileId: Int, onlyIfPending: Boolean = false)
}
