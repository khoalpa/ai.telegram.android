package ai.telegram.android.data

enum class MessageKind {
    Text,
    Image,
    Video,
    File
}

enum class TranslationStatus {
    Pending,
    Translating,
    Ready,
    Hidden,
    Failed
}

enum class TranslationJobStatus {
    Pending,
    Running,
    Failed
}

enum class TranslationFailureReason {
    None,
    MissingModel,
    UnsupportedLanguage,
    UndetectedLanguage,
    NonTranslatable,
    NetworkRequired,
    TransientError
}

enum class MediaCacheState {
    Requested,
    ThumbnailReady,
    PrefixReady,
    FullDownloaded,
    Corrupt
}

enum class MessageSyncState {
    Synced,
    Sending,
    Failed,
    Deleted
}

data class TelegramChat(
    val id: Long,
    val title: String,
    val type: String,
    val unreadCount: Int,
    val lastMessagePreview: String,
    val updatedAtMillis: Long,
    val activeAction: String = "",
    val lastReadInboxMessageId: Long = 0L,
    val lastReadOutboxMessageId: Long = 0L,
    val pinnedMessageId: Long = 0L
)

data class TelegramMessage(
    val chatId: Long,
    val id: Long,
    val senderId: String,
    val chatTitle: String,
    val author: String,
    val originalText: String,
    val translatedText: String,
    val translationStatus: TranslationStatus,
    val detectedLanguage: String,
    val translationFailureReason: TranslationFailureReason = TranslationFailureReason.None,
    val translationTargetLanguage: String = "vi",
    val kind: MessageKind,
    val mediaSizeMb: Int,
    val mediaFileId: Int = 0,
    val mediaLocalPath: String = "",
    val mediaMimeType: String = "",
    val mediaFileName: String = "",
    val mediaThumbnailFileId: Int = 0,
    val mediaThumbnailLocalPath: String = "",
    val mediaDownloadedPrefixBytes: Long = 0L,
    val timestamp: String,
    val syncState: MessageSyncState = MessageSyncState.Synced,
    val isOutgoing: Boolean = false,
    val isRead: Boolean = false,
    val isEdited: Boolean = false,
    val isPinned: Boolean = false,
    val receivedAtMillis: Long = 0L
)

data class TelegramMediaFile(
    val id: Int,
    val localPath: String,
    val sizeMb: Int,
    val downloadedPrefixBytes: Long = 0L
)

data class TelegramSender(
    val id: String,
    val displayName: String,
    val type: String,
    val updatedAtMillis: Long
)

data class HiddenContent(
    val contentHash: String,
    val preview: String,
    val hiddenCount: Int,
    val createdAt: String
)

data class TranslationCacheKey(
    val contentHash: String,
    val targetLanguage: String,
    val providerVersion: String
)

data class CachedTranslation(
    val translatedText: String,
    val sourceLanguage: String,
    val failureReason: TranslationFailureReason,
    val failureMessage: String
) {
    val isFailure: Boolean
        get() = failureReason != TranslationFailureReason.None
}

data class TranslationCacheStats(
    val translationItems: Int,
    val translationSizeMb: Int,
    val mediaSizeMb: Int,
    val languagePackSizeMb: Int,
    val mediaItems: Int = 0,
    val mediaRequestedItems: Int = 0,
    val mediaPartialItems: Int = 0,
    val mediaFullItems: Int = 0,
    val mediaCorruptItems: Int = 0,
    val videoSubtitleItems: Int = 0,
    val videoSubtitleSizeMb: Int = 0
) {
    val totalCacheSizeMb: Int
        get() = translationSizeMb + mediaSizeMb + languagePackSizeMb + videoSubtitleSizeMb
}

data class MediaCacheStats(
    val totalItems: Int,
    val requestedItems: Int,
    val partialItems: Int,
    val fullItems: Int,
    val corruptItems: Int,
    val sizeMb: Int
)

data class VideoSubtitleCacheStats(
    val items: Int,
    val sizeMb: Int
)

data class CacheClearResult(
    val translationItems: Int = 0,
    val mediaItems: Int = 0,
    val videoSubtitleItems: Int = 0,
    val temporaryFiles: Int = 0,
    val freedSizeMb: Int = 0
) {
    val totalItems: Int
        get() = translationItems + mediaItems + videoSubtitleItems + temporaryFiles
}

enum class NetworkMode {
    Wifi,
    MobileData,
    Roaming
}

data class DownloadDecision(
    val autoDownload: Boolean,
    val reason: String
)
