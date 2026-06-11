package ai.telegram.android.data.local

import ai.telegram.android.data.HiddenContent
import ai.telegram.android.data.MediaCacheState
import ai.telegram.android.data.MessageSyncState
import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.TelegramChat
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TelegramSender
import ai.telegram.android.data.TranslationFailureReason
import ai.telegram.android.data.TranslationCacheKey
import ai.telegram.android.data.TranslationCacheStats
import ai.telegram.android.data.TranslationStatus
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "chats",
    indices = [
        Index(value = ["updatedAtMillis"], name = "index_chats_updatedAtMillis"),
        Index(value = ["type"], name = "index_chats_type")
    ]
)
data class ChatEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val type: String,
    val unreadCount: Int,
    val lastMessagePreview: String,
    val updatedAtMillis: Long,
    val activeAction: String,
    val lastReadInboxMessageId: Long,
    val lastReadOutboxMessageId: Long,
    val pinnedMessageId: Long,
    val isMainList: Boolean
) {
    fun toDomain(): TelegramChat = TelegramChat(
        id = id,
        title = title,
        type = type,
        unreadCount = unreadCount,
        lastMessagePreview = lastMessagePreview,
        updatedAtMillis = updatedAtMillis,
        activeAction = activeAction,
        lastReadInboxMessageId = lastReadInboxMessageId,
        lastReadOutboxMessageId = lastReadOutboxMessageId,
        pinnedMessageId = pinnedMessageId,
        isMainList = isMainList
    )
}

@Entity(
    tableName = "senders",
    indices = [
        Index(value = ["displayName"], name = "index_senders_displayName")
    ]
)
data class SenderEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val type: String,
    val updatedAtMillis: Long,
    val isContact: Boolean
) {
    fun toDomain(): TelegramSender = TelegramSender(
        id = id,
        displayName = displayName,
        type = type,
        updatedAtMillis = updatedAtMillis,
        isContact = isContact
    )
}

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId", "receivedAtMillis"], name = "index_messages_chatId_receivedAtMillis"),
        Index(value = ["chatId", "id"], name = "index_messages_chatId_id"),
        Index(value = ["senderId"], name = "index_messages_senderId"),
        Index(value = ["translationStatus", "translationFailureReason", "receivedAtMillis"], name = "index_messages_translation_work"),
        Index(value = ["mediaFileId"], name = "index_messages_mediaFileId"),
        Index(value = ["mediaThumbnailFileId"], name = "index_messages_mediaThumbnailFileId")
    ]
)
data class MessageEntity(
    @PrimaryKey val uid: String,
    val id: Long,
    val chatId: Long,
    val senderId: String,
    val chatTitle: String,
    val author: String,
    val originalText: String,
    val translatedText: String,
    val translationStatus: String,
    val detectedLanguage: String,
    val translationFailureReason: String,
    val translationTargetLanguage: String,
    val kind: String,
    val mediaSizeMb: Int,
    val mediaFileId: Int,
    val mediaLocalPath: String,
    val mediaMimeType: String,
    val mediaFileName: String,
    val mediaThumbnailFileId: Int,
    val mediaThumbnailLocalPath: String,
    val mediaDownloadedPrefixBytes: Long,
    val timestamp: String,
    val syncState: String,
    val isOutgoing: Boolean,
    val isRead: Boolean,
    val isEdited: Boolean,
    val isPinned: Boolean,
    val contentHash: String,
    val receivedAtMillis: Long
) {
    fun toDomain(
        resolvedChatTitle: String? = null,
        resolvedAuthor: String? = null
    ): TelegramMessage = TelegramMessage(
        chatId = chatId,
        id = id,
        senderId = senderId,
        chatTitle = resolvedChatTitle ?: chatTitle,
        author = resolvedAuthor ?: author,
        originalText = originalText,
        translatedText = translatedText,
        translationStatus = TranslationStatus.valueOf(translationStatus),
        detectedLanguage = detectedLanguage,
        translationFailureReason = runCatching { TranslationFailureReason.valueOf(translationFailureReason) }
            .getOrDefault(TranslationFailureReason.None),
        translationTargetLanguage = translationTargetLanguage,
        kind = MessageKind.valueOf(kind),
        mediaSizeMb = mediaSizeMb,
        mediaFileId = mediaFileId,
        mediaLocalPath = mediaLocalPath,
        mediaMimeType = mediaMimeType,
        mediaFileName = mediaFileName,
        mediaThumbnailFileId = mediaThumbnailFileId,
        mediaThumbnailLocalPath = mediaThumbnailLocalPath,
        mediaDownloadedPrefixBytes = mediaDownloadedPrefixBytes,
        timestamp = timestamp,
        syncState = runCatching { MessageSyncState.valueOf(syncState) }
            .getOrDefault(MessageSyncState.Synced),
        isOutgoing = isOutgoing,
        isRead = isRead,
        isEdited = isEdited,
        isPinned = isPinned,
        receivedAtMillis = receivedAtMillis
    )
}

data class MessageWithMetadata(
    @Embedded val message: MessageEntity,
    @Relation(
        parentColumn = "chatId",
        entityColumn = "id"
    )
    val chat: ChatEntity?,
    @Relation(
        parentColumn = "senderId",
        entityColumn = "id"
    )
    val sender: SenderEntity?
) {
    fun toDomain(): TelegramMessage {
        return message.toDomain(
            resolvedChatTitle = chat?.title,
            resolvedAuthor = sender?.displayName
        )
    }
}

@Entity(
    tableName = "chat_history_state",
    indices = [
        Index(value = ["lastSyncedAtMillis"], name = "index_chat_history_state_lastSyncedAtMillis")
    ]
)
data class ChatHistoryStateEntity(
    @PrimaryKey val chatId: Long,
    val newestMessageId: Long,
    val oldestMessageId: Long,
    val lastSyncedAtMillis: Long,
    val olderHistoryExhausted: Boolean
)

@Entity(tableName = "hidden_content")
data class HiddenContentEntity(
    @PrimaryKey val contentHash: String,
    val preview: String,
    val hiddenCount: Int,
    val createdAt: String,
    val createdAtMillis: Long
) {
    fun toDomain(): HiddenContent = HiddenContent(
        contentHash = contentHash,
        preview = preview,
        hiddenCount = hiddenCount,
        createdAt = createdAt
    )
}

@Entity(tableName = "translation_cache", primaryKeys = ["contentHash", "targetLanguage", "providerVersion"])
data class TranslationCacheEntity(
    val contentHash: String,
    val targetLanguage: String,
    val providerVersion: String,
    val translatedText: String,
    val sourceLanguage: String,
    val failureReason: String,
    val failureMessage: String,
    val expiresAtMillis: Long,
    val updatedAtMillis: Long,
    val hitCount: Int
) {
    fun key(): TranslationCacheKey = TranslationCacheKey(contentHash, targetLanguage, providerVersion)

    fun failureReasonEnum(): TranslationFailureReason {
        return runCatching { TranslationFailureReason.valueOf(failureReason) }
            .getOrDefault(TranslationFailureReason.None)
    }
}

data class CacheTotals(
    val translationItems: Int,
    val mediaSizeMb: Int
) {
    fun toDomain(languagePackSizeMb: Int): TranslationCacheStats = TranslationCacheStats(
        translationItems = translationItems,
        translationSizeMb = 0,
        mediaSizeMb = mediaSizeMb,
        languagePackSizeMb = languagePackSizeMb
    )
}

@Entity(
    tableName = "translation_jobs",
    indices = [
        Index(value = ["status", "createdAtMillis"], name = "index_translation_jobs_status_createdAtMillis"),
        Index(value = ["status", "updatedAtMillis"], name = "index_translation_jobs_status_updatedAtMillis")
    ]
)
data class TranslationJobEntity(
    @PrimaryKey val messageUid: String,
    val targetLanguage: String,
    val status: String,
    val attempts: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "media_cache",
    indices = [
        Index(value = ["state", "updatedAtMillis"], name = "index_media_cache_state_updatedAtMillis"),
        Index(value = ["lastAccessedAtMillis"], name = "index_media_cache_lastAccessedAtMillis")
    ]
)
data class MediaCacheEntity(
    @PrimaryKey val fileId: Int,
    val kind: String,
    val localPath: String,
    val sizeMb: Int,
    val actualSizeBytes: Long,
    val downloadedPrefixBytes: Long,
    val state: String,
    val requestedAtMillis: Long,
    val updatedAtMillis: Long,
    val lastAccessedAtMillis: Long
) {
    fun stateEnum(): MediaCacheState {
        return runCatching { MediaCacheState.valueOf(state) }
            .getOrDefault(MediaCacheState.Requested)
    }
}

@Entity(tableName = "video_subtitle_cache", primaryKeys = ["fileId", "targetLanguage", "providerVersion"])
data class VideoSubtitleCacheEntity(
    val fileId: Int,
    val targetLanguage: String,
    val providerVersion: String,
    val videoPath: String,
    val videoSizeBytes: Long,
    val videoModifiedAtMillis: Long,
    val sourceText: String,
    val sourceLanguageCode: String,
    val cuesJson: String,
    val updatedAtMillis: Long,
    val lastAccessedAtMillis: Long
)
