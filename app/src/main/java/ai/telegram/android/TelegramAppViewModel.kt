package ai.telegram.android

import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.MessageSyncState
import ai.telegram.android.data.CacheClearResult
import ai.telegram.android.data.TelegramMediaFile
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TranslationCacheStats
import ai.telegram.android.core.ContentNormalizer
import ai.telegram.android.work.MaintenanceWorkScheduler
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TelegramAppViewModel internal constructor(
    application: Application,
    private val repositories: TelegramAppRepositories
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        TelegramAppRepositories.from(application.applicationContext)
    )

    private val appContext = application.applicationContext

    val chatRepository = repositories.chatRepository
    val chatHistoryStateRepository = repositories.chatHistoryStateRepository
    val senderRepository = repositories.senderRepository
    val messageRepository = repositories.messageRepository
    val blacklistRepository = repositories.blacklistRepository
    val translationCacheRepository = repositories.translationCacheRepository
    val translationJobRepository = repositories.translationJobRepository
    val mediaCacheRepository = repositories.mediaCacheRepository
    val videoSubtitleCacheRepository = repositories.videoSubtitleCacheRepository

    val chats = chatRepository.observeChats()
    val senders = senderRepository.observeSenders()
    val contacts = senderRepository.observeContacts()
    val messages = messageRepository.observeMessages()
    val hiddenContent = blacklistRepository.observeHiddenContent()

    fun observeMessagesForChat(chatId: Long) =
        messageRepository.observeMessagesForChat(chatId)

    fun observeMessagesForChat(chatId: Long, limit: Int) =
        messageRepository.observeMessagesForChat(chatId, limit)

    suspend fun shouldLoadInitialHistory(chatId: Long): Boolean {
        return chatHistoryStateRepository.shouldLoadInitialHistory(chatId)
    }

    suspend fun shouldLoadOlderHistory(chatId: Long, fromMessageId: Long): Boolean {
        return chatHistoryStateRepository.shouldLoadOlderHistory(chatId, fromMessageId)
    }

    suspend fun recordHistoryLoaded(
        chatId: Long,
        fromMessageId: Long,
        messageIds: List<Long>,
        requestedLimit: Int
    ) {
        chatHistoryStateRepository.recordHistoryLoaded(chatId, fromMessageId, messageIds, requestedLimit)
    }

    suspend fun initializeSeedData(seedSamples: Boolean) {
        if (seedSamples) {
            chatRepository.seedIfEmpty()
            senderRepository.seedIfEmpty()
            messageRepository.seedIfEmpty()
        } else {
            chatRepository.removeSampleData()
        }
    }

    fun scheduleMaintenance() {
        MaintenanceWorkScheduler.schedule(appContext)
    }

    suspend fun cacheStats(languagePackSizeMb: Int): TranslationCacheStats {
        return translationCacheRepository.stats(languagePackSizeMb)
    }

    suspend fun upsertIncomingMessage(message: TelegramMessage, pendingPreview: String) {
        messageRepository.upsert(message)
        chatRepository.touchLastMessage(message.chatId, pendingPreview)
    }

    suspend fun updateMediaFile(file: TelegramMediaFile) {
        messageRepository.updateMediaFile(file)
        mediaCacheRepository.update(file)
    }

    suspend fun shouldRequestMedia(fileId: Int): Boolean {
        return mediaCacheRepository.shouldRequest(fileId)
    }

    suspend fun shouldRequestFullMediaDownload(fileId: Int, kind: MessageKind): Boolean {
        return mediaCacheRepository.shouldRequestFullDownload(fileId, kind)
    }

    suspend fun markMediaRequested(fileId: Int, kind: MessageKind) {
        mediaCacheRepository.markRequested(fileId, kind)
    }

    suspend fun markFullMediaDownloadRequested(fileId: Int, kind: MessageKind) {
        mediaCacheRepository.markFullDownloadRequested(fileId, kind)
    }

    suspend fun deleteMessages(chatId: Long, messageIds: List<Long>) {
        messageRepository.deleteMessages(chatId, messageIds)
    }

    suspend fun markInboxRead(chatId: Long, lastReadMessageId: Long, unreadCount: Int) {
        chatRepository.updateReadInbox(chatId, lastReadMessageId, unreadCount)
        messageRepository.markInboxRead(chatId, lastReadMessageId)
    }

    suspend fun markOutboxRead(chatId: Long, lastReadMessageId: Long) {
        chatRepository.updateReadOutbox(chatId, lastReadMessageId)
        messageRepository.markOutboxRead(chatId, lastReadMessageId)
    }

    suspend fun updateMessageSyncState(chatId: Long, messageId: Long, state: MessageSyncState, isRead: Boolean) {
        messageRepository.updateSyncState(chatId, messageId, state, isRead)
    }

    suspend fun updateMessageContent(chatId: Long, messageId: Long, text: String) {
        messageRepository.updateTextContent(chatId, messageId, text)
        messageRepository.find("$chatId:$messageId")?.let { message ->
            translationJobRepository.enqueue(message)
        }
    }

    suspend fun updateChatAction(chatId: Long, activeAction: String) {
        chatRepository.updateActiveAction(chatId, activeAction)
    }

    suspend fun updatePinnedMessage(chatId: Long, messageId: Long) {
        chatRepository.updatePinnedMessage(chatId, messageId)
        messageRepository.updatePinnedMessage(chatId, messageId)
    }

    suspend fun hideMessage(message: TelegramMessage) {
        blacklistRepository.add(message)
    }

    suspend fun unhideMessage(message: TelegramMessage) {
        if (message.originalText.isBlank()) return
        blacklistRepository.remove(ContentNormalizer.contentHash(message.originalText))
    }

    suspend fun clearTelegramData() {
        translationJobRepository.clear()
        chatHistoryStateRepository.clear()
        messageRepository.clear()
        chatRepository.clear()
        senderRepository.clear()
    }

    suspend fun clearCache(): CacheClearResult {
        val result = cacheClearResult(
            includeTranslations = true,
            includeMedia = true,
            includeVideoSubtitles = true,
            includeTemporaryFiles = true
        )
        translationCacheRepository.clear()
        mediaCacheRepository.clear()
        videoSubtitleCacheRepository.clear()
        withContext(Dispatchers.IO) {
            clearTemporaryCacheDirectories()
        }
        return result
    }

    suspend fun clearTranslationCache(): CacheClearResult {
        val result = cacheClearResult(includeTranslations = true)
        translationCacheRepository.clear()
        return result
    }

    suspend fun clearMediaCache(): CacheClearResult {
        val result = cacheClearResult(includeMedia = true)
        mediaCacheRepository.clear()
        return result
    }

    suspend fun clearVideoSubtitleCache(): CacheClearResult {
        val result = cacheClearResult(includeVideoSubtitles = true)
        videoSubtitleCacheRepository.clear()
        return result
    }

    suspend fun clearTemporaryFiles(): CacheClearResult {
        val result = cacheClearResult(includeTemporaryFiles = true)
        withContext(Dispatchers.IO) {
            clearTemporaryCacheDirectories()
        }
        return result
    }

    private suspend fun cacheClearResult(
        includeTranslations: Boolean = false,
        includeMedia: Boolean = false,
        includeVideoSubtitles: Boolean = false,
        includeTemporaryFiles: Boolean = false
    ): CacheClearResult {
        val temporaryStats = if (includeTemporaryFiles) {
            withContext(Dispatchers.IO) { temporaryCacheStats() }
        } else {
            TemporaryCacheStats()
        }
        val translationItems = if (includeTranslations) translationCacheRepository.count() else 0
        val translationSizeMb = if (includeTranslations) translationCacheRepository.totalSizeMb() else 0
        val mediaStats = if (includeMedia) mediaCacheRepository.stats() else null
        val subtitleStats = if (includeVideoSubtitles) videoSubtitleCacheRepository.stats() else null
        return CacheClearResult(
            translationItems = translationItems,
            mediaItems = mediaStats?.totalItems ?: 0,
            videoSubtitleItems = subtitleStats?.items ?: 0,
            temporaryFiles = temporaryStats.files,
            freedSizeMb = translationSizeMb +
                (mediaStats?.sizeMb ?: 0) +
                (subtitleStats?.sizeMb ?: 0) +
                temporaryStats.sizeMb
        )
    }

    private fun clearTemporaryCacheDirectories() {
        TemporaryCacheDirectories.forEach { directoryName ->
            runCatching {
                File(appContext.cacheDir, directoryName).deleteRecursively()
            }
        }
    }

    private fun temporaryCacheStats(): TemporaryCacheStats {
        var files = 0
        var bytes = 0L
        TemporaryCacheDirectories.forEach { directoryName ->
            val directory = File(appContext.cacheDir, directoryName)
            if (!directory.exists()) return@forEach
            directory.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    files += 1
                    bytes += file.length()
                }
        }
        return TemporaryCacheStats(files = files, sizeMb = bytes.toDisplayMb())
    }

    private companion object {
        val TemporaryCacheDirectories = listOf(
            "telegram_uploads",
            "composer_camera",
            "video_review_frames",
            "video_review_reports"
        )
    }
}

private data class TemporaryCacheStats(
    val files: Int = 0,
    val sizeMb: Int = 0
)

private fun Long.toDisplayMb(): Int {
    if (this <= 0L) return 0
    return ((this + TEMP_CACHE_BYTES_PER_MB - 1L) / TEMP_CACHE_BYTES_PER_MB)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

private const val TEMP_CACHE_BYTES_PER_MB = 1024L * 1024L
