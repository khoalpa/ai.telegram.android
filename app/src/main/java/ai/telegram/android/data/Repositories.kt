package ai.telegram.android.data

import ai.telegram.android.core.ContentNormalizer
import ai.telegram.android.data.local.MediaCacheEntity
import ai.telegram.android.data.local.AppDatabase
import ai.telegram.android.data.local.ChatEntity
import ai.telegram.android.data.local.ChatHistoryStateEntity
import ai.telegram.android.data.local.HiddenContentEntity
import ai.telegram.android.data.local.MessageEntity
import ai.telegram.android.data.local.TranslationJobEntity
import ai.telegram.android.data.local.SenderEntity
import ai.telegram.android.data.local.TranslationCacheEntity
import ai.telegram.android.data.local.VideoSubtitleCacheEntity
import ai.telegram.android.data.translation.TranslationPostProcessor
import ai.telegram.android.data.translation.TranslationResult
import ai.telegram.android.data.translation.VideoSubtitleCue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(
    private val database: AppDatabase
) {
    private companion object {
        const val MaxChatPreviewLength = 500
    }

    fun observeChats(): Flow<List<TelegramChat>> {
        return database.chatDao().observeChats().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun seedIfEmpty() {
        database.chatDao().insertAll(SampleTelegramRepository.chats().map { it.toEntity() })
    }

    suspend fun removeSampleData() {
        database.messageDao().deleteByChatIds(SampleTelegramRepository.chatIds)
        database.chatDao().deleteByIds(SampleTelegramRepository.chatIds)
        database.senderDao().deleteByIds(SampleTelegramRepository.senderIds)
    }

    suspend fun upsert(chat: TelegramChat) {
        val existing = database.chatDao().find(chat.id)
        if (existing != null && chat.title.isBlank()) {
            database.chatDao().upsert(
                existing.copy(
                    unreadCount = chat.unreadCount,
                    lastMessagePreview = chat.lastMessagePreview.chatPreviewForStorage(),
                    updatedAtMillis = chat.updatedAtMillis,
                    activeAction = chat.activeAction.ifBlank { existing.activeAction },
                    lastReadInboxMessageId = maxOf(existing.lastReadInboxMessageId, chat.lastReadInboxMessageId),
                    lastReadOutboxMessageId = maxOf(existing.lastReadOutboxMessageId, chat.lastReadOutboxMessageId),
                    pinnedMessageId = chat.pinnedMessageId.takeIf { it > 0L } ?: existing.pinnedMessageId,
                    isMainList = existing.isMainList || chat.isMainList
                )
            )
        } else {
            database.chatDao().upsert(chat.withStorageSafePreview().toEntity())
        }
    }

    suspend fun touchLastMessage(chatId: Long, preview: String) {
        val existing = database.chatDao().find(chatId)
        if (existing != null) {
            database.chatDao().upsert(
                existing.copy(
                    lastMessagePreview = preview.chatPreviewForStorage(),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        } else {
            database.chatDao().upsert(
                TelegramChat(
                    id = chatId,
                    title = "Telegram",
                    type = "chat",
                    unreadCount = 0,
                    lastMessagePreview = preview.chatPreviewForStorage(),
                    updatedAtMillis = System.currentTimeMillis(),
                    isMainList = false
                ).toEntity()
            )
        }
    }

    suspend fun updateReadInbox(chatId: Long, lastReadInboxMessageId: Long, unreadCount: Int) {
        if (chatId == 0L) return
        database.chatDao().updateReadInbox(
            chatId = chatId,
            lastReadInboxMessageId = lastReadInboxMessageId,
            unreadCount = unreadCount.coerceAtLeast(0),
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun updateReadOutbox(chatId: Long, lastReadOutboxMessageId: Long) {
        if (chatId == 0L) return
        database.chatDao().updateReadOutbox(
            chatId = chatId,
            lastReadOutboxMessageId = lastReadOutboxMessageId,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun updateActiveAction(chatId: Long, activeAction: String) {
        if (chatId == 0L) return
        database.chatDao().updateActiveAction(
            chatId = chatId,
            activeAction = activeAction,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun updatePinnedMessage(chatId: Long, messageId: Long) {
        if (chatId == 0L) return
        database.chatDao().updatePinnedMessage(
            chatId = chatId,
            messageId = messageId,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun clear() {
        database.chatDao().clear()
    }

    private fun TelegramChat.withStorageSafePreview(): TelegramChat {
        return copy(lastMessagePreview = lastMessagePreview.chatPreviewForStorage())
    }

    private fun String.chatPreviewForStorage(): String {
        return if (length <= MaxChatPreviewLength) this else take(MaxChatPreviewLength)
    }
}

class SenderRepository(
    private val database: AppDatabase
) {
    fun observeSenders(): Flow<List<TelegramSender>> {
        return database.senderDao().observeSenders().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun observeContacts(): Flow<List<TelegramSender>> {
        return database.senderDao().observeContacts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun seedIfEmpty() {
        SampleTelegramRepository.senders().forEach { upsert(it) }
    }

    suspend fun upsert(sender: TelegramSender) {
        val existing = database.senderDao().find(sender.id)
        val mergedSender = if (existing != null) {
            sender.copy(isContact = existing.isContact || sender.isContact)
        } else {
            sender
        }
        database.senderDao().upsert(mergedSender.toEntity())
    }

    suspend fun clear() {
        database.senderDao().clear()
    }
}

class MessageRepository(
    private val database: AppDatabase
) {
    fun observeMessages(): Flow<List<TelegramMessage>> {
        return database.messageDao().observeMessagesWithMetadata().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun observeMessagesForChat(chatId: Long): Flow<List<TelegramMessage>> {
        return database.messageDao().observeMessagesForChatWithMetadata(chatId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun observeMessagesForChat(chatId: Long, limit: Int): Flow<List<TelegramMessage>> {
        return database.messageDao()
            .observeMessagesForChatWithMetadata(chatId, limit.coerceAtLeast(1))
            .map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun recentMessagesNeedingTranslation(limit: Int): List<TelegramMessage> {
        return database.messageDao().recentMessagesNeedingTranslation(
            pendingStatus = TranslationStatus.Pending.name,
            failedStatus = TranslationStatus.Failed.name,
            retryableFailureReasons = retryableTranslationFailureNames(),
            limit = limit
        ).map { it.toDomain() }
    }

    suspend fun seedIfEmpty() {
        database.messageDao().insertAll(SampleTelegramRepository.messages().mapIndexed { index, message ->
            message.toEntity(receivedAtMillis = System.currentTimeMillis() - index * 60_000L)
        })
    }

    suspend fun upsert(message: TelegramMessage) {
        val existing = database.messageDao().findMessageWithMetadata(message.uid())?.toDomain()
        val mergedMessage = existing?.mergeIncoming(message) ?: message
        val receivedAtMillis = existing?.receivedAtMillis?.takeIf { it > 0L } ?: System.currentTimeMillis()
        database.messageDao().upsert(mergedMessage.toEntity(receivedAtMillis))
        ChatHistoryStateRepository(database).recordMessage(mergedMessage)
    }

    suspend fun updateMediaFile(file: TelegramMediaFile) {
        if (file.id <= 0) return
        database.messageDao().updateMediaFile(
            fileId = file.id,
            localPath = file.localPath,
            sizeMb = file.sizeMb,
            downloadedPrefixBytes = file.downloadedPrefixBytes
        )
    }

    suspend fun find(uid: String): TelegramMessage? {
        return database.messageDao().findMessageWithMetadata(uid)?.toDomain()
    }

    suspend fun deleteMessages(chatId: Long, messageIds: List<Long>) {
        val cleanMessageIds = messageIds.distinct().filter { it > 0L }
        if (chatId == 0L || cleanMessageIds.isEmpty()) return
        database.messageDao().deleteMessages(chatId, cleanMessageIds)
    }

    suspend fun markInboxRead(chatId: Long, lastReadMessageId: Long) {
        if (chatId == 0L || lastReadMessageId <= 0L) return
        database.messageDao().markReadUpTo(chatId, lastReadMessageId, isOutgoing = false)
    }

    suspend fun markOutboxRead(chatId: Long, lastReadMessageId: Long) {
        if (chatId == 0L || lastReadMessageId <= 0L) return
        database.messageDao().markReadUpTo(chatId, lastReadMessageId, isOutgoing = true)
    }

    suspend fun updateSyncState(chatId: Long, messageId: Long, state: MessageSyncState, isRead: Boolean) {
        if (chatId == 0L || messageId == 0L) return
        database.messageDao().updateSyncState(chatId, messageId, state.name, isRead)
    }

    suspend fun updateTextContent(chatId: Long, messageId: Long, text: String) {
        if (chatId == 0L || messageId == 0L || text.isBlank()) return
        database.messageDao().updateTextContent(
            chatId = chatId,
            messageId = messageId,
            originalText = text,
            translationStatus = TranslationStatus.Pending.name,
            contentHash = ContentNormalizer.contentHash(text)
        )
    }

    suspend fun updatePinnedMessage(chatId: Long, messageId: Long) {
        if (chatId == 0L) return
        database.messageDao().updatePinnedMessage(chatId, messageId)
    }

    suspend fun updateTranslation(
        message: TelegramMessage,
        translatedText: String,
        status: TranslationStatus,
        detectedLanguage: String? = null,
        failureReason: TranslationFailureReason = if (status == TranslationStatus.Failed) {
            TranslationFailureReason.TransientError
        } else {
            TranslationFailureReason.None
        },
        targetLanguage: String = message.translationTargetLanguage.ifBlank { "vi" }
    ) {
        val detected = detectedLanguage?.takeIf { it.isNotBlank() }
        if (detected == null) {
            database.messageDao().updateTranslation(
                uid = message.uid(),
                translatedText = translatedText,
                translationStatus = status.name,
                translationFailureReason = failureReason.name,
                translationTargetLanguage = targetLanguage
            )
        } else {
            database.messageDao().updateTranslationWithDetectedLanguage(
                uid = message.uid(),
                translatedText = translatedText,
                translationStatus = status.name,
                detectedLanguage = detected,
                translationFailureReason = failureReason.name,
                translationTargetLanguage = targetLanguage
            )
        }
    }

    suspend fun clear() {
        database.messageDao().clear()
        database.chatHistoryStateDao().clear()
    }
}

class ChatHistoryStateRepository(
    private val database: AppDatabase
) {
    private val dao = database.chatHistoryStateDao()
    private val messageDao = database.messageDao()

    suspend fun shouldLoadInitialHistory(chatId: Long): Boolean {
        if (chatId == 0L) return false
        val messageCount = messageDao.countForChat(chatId)
        val state = dao.find(chatId)
        if (messageCount < MIN_INITIAL_HISTORY_MESSAGE_COUNT) {
            val storedOldestMessageId = messageDao.oldestMessageIdForChat(chatId) ?: 0L
            val historyStateMismatch = state?.oldestMessageId?.takeIf { it > 0L }?.let { stateOldest ->
                storedOldestMessageId <= 0L || stateOldest < storedOldestMessageId
            } ?: false
            if (historyStateMismatch) return true
            return state == null || state.isStale() || messageCount > 0
        }
        return false
    }

    suspend fun shouldLoadOlderHistory(chatId: Long, fromMessageId: Long): Boolean {
        if (chatId == 0L || fromMessageId <= 0L) return false
        return dao.find(chatId)?.olderHistoryExhausted != true
    }

    suspend fun recordMessage(message: TelegramMessage) {
        if (message.chatId == 0L || message.id <= 0L) return
        val now = System.currentTimeMillis()
        val existing = dao.find(message.chatId)
        dao.upsert(
            ChatHistoryStateEntity(
                chatId = message.chatId,
                newestMessageId = maxOf(existing?.newestMessageId ?: 0L, message.id),
                oldestMessageId = minPositive(existing?.oldestMessageId ?: 0L, message.id),
                lastSyncedAtMillis = now,
                olderHistoryExhausted = existing?.olderHistoryExhausted ?: false
            )
        )
    }

    suspend fun recordHistoryLoaded(
        chatId: Long,
        fromMessageId: Long,
        messageIds: List<Long>,
        requestedLimit: Int
    ) {
        if (chatId == 0L) return
        val now = System.currentTimeMillis()
        val cleanIds = messageIds.filter { it > 0L }
        val existing = dao.find(chatId)
        val newest = listOfNotNull(
            existing?.newestMessageId?.takeIf { it > 0L },
            messageDao.newestMessageIdForChat(chatId),
            cleanIds.maxOrNull()
        ).maxOrNull() ?: 0L
        val oldest = listOfNotNull(
            existing?.oldestMessageId?.takeIf { it > 0L },
            messageDao.oldestMessageIdForChat(chatId),
            cleanIds.minOrNull()
        ).minOrNull() ?: 0L
        val olderExhausted = existing?.olderHistoryExhausted == true ||
            cleanIds.isEmpty() ||
            (fromMessageId > 0L && cleanIds.size < requestedLimit.coerceAtLeast(1))
        dao.upsert(
            ChatHistoryStateEntity(
                chatId = chatId,
                newestMessageId = newest,
                oldestMessageId = oldest,
                lastSyncedAtMillis = now,
                olderHistoryExhausted = olderExhausted
            )
        )
    }

    suspend fun clear() {
        dao.clear()
    }

    private fun ChatHistoryStateEntity.isStale(): Boolean {
        return lastSyncedAtMillis <= 0L ||
            lastSyncedAtMillis < System.currentTimeMillis() - INITIAL_HISTORY_REFRESH_MILLIS
    }

    private fun minPositive(first: Long, second: Long): Long {
        return listOf(first, second).filter { it > 0L }.minOrNull() ?: 0L
    }

    private companion object {
        const val INITIAL_HISTORY_REFRESH_MILLIS = 10L * 60L * 1000L
        const val MIN_INITIAL_HISTORY_MESSAGE_COUNT = 20
    }
}

class MediaCacheRepository(
    private val database: AppDatabase
) {
    private val dao = database.mediaCacheDao()

    suspend fun shouldRequest(fileId: Int): Boolean {
        if (fileId <= 0) return false
        val existing = dao.find(fileId) ?: return true
        if (existing.hasReadableLocalFile()) return false
        val staleRequest = existing.stateEnum() == MediaCacheState.Requested &&
            existing.updatedAtMillis < System.currentTimeMillis() - REQUEST_RETRY_MILLIS
        return staleRequest ||
            existing.stateEnum() == MediaCacheState.Corrupt ||
            existing.stateEnum() == MediaCacheState.FullDownloaded
    }

    suspend fun shouldRequestFullDownload(fileId: Int, kind: MessageKind): Boolean {
        if (fileId <= 0) return false
        val existing = dao.find(fileId) ?: return true
        if (!existing.matchesKind(kind)) return true
        if (existing.hasReadableLocalFile()) return false
        val staleRequest = existing.stateEnum() == MediaCacheState.Requested &&
            existing.updatedAtMillis < System.currentTimeMillis() - REQUEST_RETRY_MILLIS
        return when (existing.stateEnum()) {
            MediaCacheState.FullDownloaded -> true
            MediaCacheState.PrefixReady,
            MediaCacheState.ThumbnailReady,
            MediaCacheState.Corrupt -> true
            MediaCacheState.Requested -> staleRequest
        }
    }

    suspend fun markRequested(fileId: Int, kind: MessageKind) {
        if (fileId <= 0) return
        val now = System.currentTimeMillis()
        val existing = dao.find(fileId)
        val reusableExisting = existing?.takeIf { it.matchesKind(kind) }
        dao.upsert(
            MediaCacheEntity(
                fileId = fileId,
                kind = kind.name,
                localPath = reusableExisting?.localPath.orEmpty(),
                sizeMb = reusableExisting?.sizeMb ?: 0,
                actualSizeBytes = reusableExisting?.actualSizeBytes ?: 0L,
                downloadedPrefixBytes = reusableExisting?.downloadedPrefixBytes ?: 0L,
                state = MediaCacheState.Requested.name,
                requestedAtMillis = reusableExisting?.requestedAtMillis ?: now,
                updatedAtMillis = now,
                lastAccessedAtMillis = now
            )
        )
    }

    suspend fun markFullDownloadRequested(fileId: Int, kind: MessageKind) {
        if (fileId <= 0) return
        val now = System.currentTimeMillis()
        val existing = dao.find(fileId)
        val reusableExisting = existing?.takeIf { it.matchesKind(kind) }
        dao.upsert(
            MediaCacheEntity(
                fileId = fileId,
                kind = kind.name,
                localPath = reusableExisting?.localPath.orEmpty(),
                sizeMb = reusableExisting?.sizeMb ?: 0,
                actualSizeBytes = reusableExisting?.actualSizeBytes ?: 0L,
                downloadedPrefixBytes = reusableExisting?.downloadedPrefixBytes ?: 0L,
                state = MediaCacheState.Requested.name,
                requestedAtMillis = now,
                updatedAtMillis = now,
                lastAccessedAtMillis = now
            )
        )
    }

    suspend fun update(file: TelegramMediaFile, fallbackKind: MessageKind = MessageKind.File) {
        if (file.id <= 0) return
        val now = System.currentTimeMillis()
        val existing = dao.find(file.id)
        val effectiveKind = existing
            ?.kind
            ?.takeIf { it == fallbackKind.name || fallbackKind == MessageKind.File }
            ?: fallbackKind.name
        val reusableExisting = existing?.takeIf { it.kind == effectiveKind }
        val localPath = file.localPath.ifBlank { reusableExisting?.localPath.orEmpty() }
        val state = file.cacheState(localPath, reusableExisting?.stateEnum())
        dao.upsert(
            MediaCacheEntity(
                fileId = file.id,
                kind = effectiveKind,
                localPath = localPath,
                sizeMb = file.sizeMb,
                actualSizeBytes = file.actualSizeBytes(localPath),
                downloadedPrefixBytes = file.downloadedPrefixBytes,
                state = state.name,
                requestedAtMillis = reusableExisting?.requestedAtMillis ?: now,
                updatedAtMillis = now,
                lastAccessedAtMillis = now
            )
        )
    }

    suspend fun touch(fileId: Int) {
        if (fileId > 0) {
            dao.touch(fileId, System.currentTimeMillis())
        }
    }

    suspend fun totalSizeMb(): Int {
        return bytesToDisplayMb(dao.totalSizeBytes())
    }

    suspend fun count(): Int {
        return dao.count()
    }

    suspend fun stats(): MediaCacheStats {
        val requested = dao.countByState(MediaCacheState.Requested.name)
        val partial = dao.countByState(MediaCacheState.ThumbnailReady.name) +
            dao.countByState(MediaCacheState.PrefixReady.name)
        val full = dao.countByState(MediaCacheState.FullDownloaded.name)
        val corrupt = dao.countByState(MediaCacheState.Corrupt.name)
        return MediaCacheStats(
            totalItems = dao.count(),
            requestedItems = requested,
            partialItems = partial,
            fullItems = full,
            corruptItems = corrupt,
            sizeMb = totalSizeMb()
        )
    }

    suspend fun cleanup(cutoffMillis: Long, maxEntries: Int, maxBytes: Long): Int {
        var deleted = dao.deleteOlderThan(cutoffMillis) + dao.trimToMostRecent(maxEntries)
        var totalBytes = dao.totalSizeBytes()
        if (totalBytes <= maxBytes) return deleted
        for (entry in dao.oldestEntries()) {
            if (totalBytes <= maxBytes) break
            deleted += dao.delete(entry.fileId)
            totalBytes -= entry.actualSizeBytes
        }
        return deleted
    }

    suspend fun clear() {
        dao.clear()
    }

    private fun TelegramMediaFile.cacheState(localPath: String, existing: MediaCacheState?): MediaCacheState {
        return when {
            localPath.isNotBlank() -> MediaCacheState.FullDownloaded
            downloadedPrefixBytes > 0L -> MediaCacheState.PrefixReady
            else -> existing ?: MediaCacheState.Requested
        }
    }

    private fun TelegramMediaFile.actualSizeBytes(localPath: String): Long {
        val fileBytes = localPath
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() }
            ?.length()
            ?: 0L
        return when {
            fileBytes > 0L -> fileBytes
            downloadedPrefixBytes > 0L -> downloadedPrefixBytes
            else -> 0L
        }
    }

    private fun MediaCacheEntity.hasReadableLocalFile(): Boolean {
        return localPath
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.let { it.isFile && it.canRead() && it.length() > 0L }
            ?: false
    }

    private fun MediaCacheEntity.matchesKind(kind: MessageKind): Boolean {
        return this.kind == kind.name
    }

    private companion object {
        const val REQUEST_RETRY_MILLIS = 15L * 60L * 1000L
        const val BYTES_PER_MB = 1024L * 1024L
    }
}

class TranslationJobRepository(
    private val database: AppDatabase
) {
    private val dao = database.translationJobDao()

    suspend fun enqueue(message: TelegramMessage, targetLanguage: String = "vi") {
        val now = System.currentTimeMillis()
        val existing = dao.find(message.uid())
        if (existing != null && existing.status != TranslationJobStatus.Failed.name) return
        dao.upsert(
            (existing ?: TranslationJobEntity(
                messageUid = message.uid(),
                targetLanguage = targetLanguage,
                status = TranslationJobStatus.Pending.name,
                attempts = 0,
                createdAtMillis = now,
                updatedAtMillis = now
            )).copy(
                targetLanguage = targetLanguage,
                status = TranslationJobStatus.Pending.name,
                attempts = 0,
                updatedAtMillis = now
            )
        )
    }

    suspend fun pending(limit: Int): List<TranslationJobEntity> {
        return dao.findByStatus(TranslationJobStatus.Pending.name, limit)
    }

    suspend fun markRunning(messageUid: String) {
        dao.updateStatus(
            messageUid = messageUid,
            status = TranslationJobStatus.Running.name,
            updatedAtMillis = System.currentTimeMillis(),
            attemptIncrement = 1
        )
    }

    suspend fun markPending(messageUid: String) {
        dao.updateStatus(
            messageUid = messageUid,
            status = TranslationJobStatus.Pending.name,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun markFailed(messageUid: String) {
        dao.updateStatus(
            messageUid = messageUid,
            status = TranslationJobStatus.Failed.name,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun complete(messageUid: String) {
        dao.delete(messageUid)
    }

    suspend fun pendingCount(): Int {
        return dao.countByStatus(TranslationJobStatus.Pending.name)
    }

    suspend fun cleanupStaleFailed(cutoffMillis: Long): Int {
        return dao.deleteStale(TranslationJobStatus.Failed.name, cutoffMillis)
    }

    suspend fun recoverStaleRunning(cutoffMillis: Long): Int {
        return dao.resetStale(
            sourceStatus = TranslationJobStatus.Running.name,
            targetStatus = TranslationJobStatus.Pending.name,
            cutoffMillis = cutoffMillis,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun clear() {
        dao.clear()
    }
}

class TranslationCacheRepository(
    private val database: AppDatabase
) {
    private val dao = database.translationCacheDao()

    suspend fun getOrPut(
        originalText: String,
        targetLanguage: String,
        providerVersion: String = "on-device-v1",
        translate: suspend () -> String
    ): String {
        val contentHash = ContentNormalizer.contentHash(originalText)
        val existing = dao.find(contentHash, targetLanguage, providerVersion)
        if (existing != null && existing.failureReasonEnum() == TranslationFailureReason.None) {
            dao.upsert(existing.copy(hitCount = existing.hitCount + 1, updatedAtMillis = System.currentTimeMillis()))
            return existing.translatedText
        }

        val translated = translate()
        if (translated.isNotBlank()) {
            dao.upsert(
                TranslationCacheEntity(
                    contentHash = contentHash,
                    targetLanguage = targetLanguage,
                    providerVersion = providerVersion,
                    translatedText = translated,
                    sourceLanguage = "",
                    failureReason = TranslationFailureReason.None.name,
                    failureMessage = "",
                    expiresAtMillis = 0L,
                    updatedAtMillis = System.currentTimeMillis(),
                    hitCount = 1
                )
            )
        }
        return translated
    }

    suspend fun getOrTranslate(
        originalText: String,
        targetLanguage: String,
        providerVersion: String,
        translate: suspend () -> TranslationResult
    ): CachedTranslation {
        val now = System.currentTimeMillis()
        val contentHash = ContentNormalizer.contentHash(originalText)
        val existing = dao.find(contentHash, targetLanguage, providerVersion)
        if (existing != null && !existing.isExpiredFailure(now) && !existing.isRetryableFailure()) {
            dao.upsert(existing.copy(hitCount = existing.hitCount + 1, updatedAtMillis = now))
            return existing.toCachedTranslation()
        }

        return when (val result = translate()) {
            is TranslationResult.Success -> {
                val translated = TranslationPostProcessor.polish(originalText, result.text)
                if (translated.isNotBlank()) {
                    dao.upsert(
                        TranslationCacheEntity(
                            contentHash = contentHash,
                            targetLanguage = targetLanguage,
                            providerVersion = providerVersion,
                            translatedText = translated,
                            sourceLanguage = result.sourceLanguageCode.orEmpty(),
                            failureReason = TranslationFailureReason.None.name,
                            failureMessage = "",
                            expiresAtMillis = 0L,
                            updatedAtMillis = now,
                            hitCount = 1
                        )
                    )
                }
                CachedTranslation(
                    translatedText = translated,
                    sourceLanguage = result.sourceLanguageCode.orEmpty(),
                    failureReason = TranslationFailureReason.None,
                    failureMessage = ""
                )
            }
            is TranslationResult.Unavailable -> {
                val failure = result.failureReason
                val failureMessage = result.reason
                if (!failure.isRetryableFailure()) {
                    dao.upsert(
                        TranslationCacheEntity(
                            contentHash = contentHash,
                            targetLanguage = targetLanguage,
                            providerVersion = providerVersion,
                            translatedText = "",
                            sourceLanguage = result.sourceLanguageCode.orEmpty(),
                            failureReason = failure.name,
                            failureMessage = failureMessage,
                            expiresAtMillis = now + failure.negativeCacheTtlMillis(),
                            updatedAtMillis = now,
                            hitCount = 1
                        )
                    )
                }
                CachedTranslation(
                    translatedText = "",
                    sourceLanguage = result.sourceLanguageCode.orEmpty(),
                    failureReason = failure,
                    failureMessage = failureMessage
                )
            }
        }
    }

    suspend fun stats(languagePackSizeMb: Int): TranslationCacheStats {
        val translationItems = dao.count()
        val mediaStats = MediaCacheRepository(database).stats()
        val subtitleStats = VideoSubtitleCacheRepository(database).stats()
        return TranslationCacheStats(
            translationItems = translationItems,
            translationSizeMb = totalSizeMb(),
            mediaSizeMb = mediaStats.sizeMb,
            languagePackSizeMb = languagePackSizeMb,
            mediaItems = mediaStats.totalItems,
            mediaRequestedItems = mediaStats.requestedItems,
            mediaPartialItems = mediaStats.partialItems,
            mediaFullItems = mediaStats.fullItems,
            mediaCorruptItems = mediaStats.corruptItems,
            videoSubtitleItems = subtitleStats.items,
            videoSubtitleSizeMb = subtitleStats.sizeMb
        )
    }

    suspend fun count(): Int {
        return dao.count()
    }

    suspend fun totalSizeMb(): Int {
        return bytesToDisplayMb(dao.totalSizeBytes())
    }

    suspend fun clear() {
        dao.clear()
    }

    suspend fun cleanup(cutoffMillis: Long, maxEntries: Int): Int {
        val deletedExpiredFailures = dao.deleteExpiredFailures(System.currentTimeMillis())
        val deletedOld = dao.deleteOlderThan(cutoffMillis)
        val deletedOverflow = dao.trimToMostRecent(maxEntries)
        return deletedExpiredFailures + deletedOld + deletedOverflow
    }

    private fun TranslationCacheEntity.isExpiredFailure(nowMillis: Long): Boolean {
        return failureReasonEnum() != TranslationFailureReason.None &&
            expiresAtMillis > 0L &&
            expiresAtMillis < nowMillis
    }

    private fun TranslationCacheEntity.isRetryableFailure(): Boolean {
        return failureReasonEnum().isRetryableFailure()
    }

    private fun TranslationCacheEntity.toCachedTranslation(): CachedTranslation {
        return CachedTranslation(
            translatedText = translatedText,
            sourceLanguage = sourceLanguage,
            failureReason = failureReasonEnum(),
            failureMessage = failureMessage
        )
    }

    private fun TranslationFailureReason.negativeCacheTtlMillis(): Long {
        val hour = 60L * 60L * 1000L
        val day = 24L * hour
        return when (this) {
            TranslationFailureReason.MissingModel -> 6L * hour
            TranslationFailureReason.NetworkRequired -> 0L
            TranslationFailureReason.UndetectedLanguage -> day
            TranslationFailureReason.NonTranslatable -> 30L * day
            TranslationFailureReason.UnsupportedLanguage -> 30L * day
            TranslationFailureReason.TransientError -> 0L
            TranslationFailureReason.None -> 0L
        }
    }
}

fun TranslationFailureReason.isRetryableFailure(): Boolean {
    return this == TranslationFailureReason.NetworkRequired ||
        this == TranslationFailureReason.TransientError
}

fun retryableTranslationFailureNames(): List<String> {
    return listOf(
        TranslationFailureReason.NetworkRequired.name,
        TranslationFailureReason.TransientError.name
    )
}

class VideoSubtitleCacheRepository(
    private val database: AppDatabase
) {
    private val dao = database.videoSubtitleCacheDao()

    suspend fun get(
        fileId: Int,
        videoFile: File,
        targetLanguage: String,
        providerVersion: String
    ): VideoSubtitleCacheEntry? {
        if (fileId <= 0) return null
        val existing = dao.find(fileId, targetLanguage, providerVersion) ?: return null
        if (
            existing.videoPath != videoFile.absolutePath ||
            existing.videoSizeBytes != videoFile.length() ||
            existing.videoModifiedAtMillis != videoFile.lastModified()
        ) {
            return null
        }
        dao.upsert(existing.copy(lastAccessedAtMillis = System.currentTimeMillis()))
        return VideoSubtitleCacheEntry(
            sourceText = existing.sourceText,
            sourceLanguageCode = existing.sourceLanguageCode,
            cues = existing.cuesJson.toVideoSubtitleCues()
        )
    }

    suspend fun put(
        fileId: Int,
        videoFile: File,
        targetLanguage: String,
        providerVersion: String,
        sourceText: String,
        sourceLanguageCode: String,
        cues: List<VideoSubtitleCue>
    ) {
        if (fileId <= 0 || cues.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsert(
            VideoSubtitleCacheEntity(
                fileId = fileId,
                targetLanguage = targetLanguage,
                providerVersion = providerVersion,
                videoPath = videoFile.absolutePath,
                videoSizeBytes = videoFile.length(),
                videoModifiedAtMillis = videoFile.lastModified(),
                sourceText = sourceText,
                sourceLanguageCode = sourceLanguageCode,
                cuesJson = cues.toJson(),
                updatedAtMillis = now,
                lastAccessedAtMillis = now
            )
        )
    }

    suspend fun cleanup(cutoffMillis: Long, maxEntries: Int): Int {
        return dao.deleteOlderThan(cutoffMillis) + dao.trimToMostRecent(maxEntries)
    }

    suspend fun stats(): VideoSubtitleCacheStats {
        return VideoSubtitleCacheStats(
            items = dao.count(),
            sizeMb = bytesToDisplayMb(dao.totalSizeBytes())
        )
    }

    suspend fun clear() {
        dao.clear()
    }

    private fun String.toVideoSubtitleCues(): List<VideoSubtitleCue> {
        val array = JSONArray(this)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            VideoSubtitleCue(
                startMs = item.optLong("startMs"),
                endMs = item.optLong("endMs"),
                text = item.optString("text")
            ).takeIf { it.text.isNotBlank() && it.endMs > it.startMs }
        }
    }

    private fun List<VideoSubtitleCue>.toJson(): String {
        val array = JSONArray()
        forEach { cue ->
            array.put(
                JSONObject()
                    .put("startMs", cue.startMs)
                    .put("endMs", cue.endMs)
                    .put("text", cue.text)
            )
        }
        return array.toString()
    }
}

data class VideoSubtitleCacheEntry(
    val sourceText: String,
    val sourceLanguageCode: String,
    val cues: List<VideoSubtitleCue>
)

private const val CACHE_BYTES_PER_MB = 1024L * 1024L

private fun bytesToDisplayMb(bytes: Long): Int {
    if (bytes <= 0L) return 0
    return ((bytes + CACHE_BYTES_PER_MB - 1L) / CACHE_BYTES_PER_MB)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

class BlacklistRepository(
    private val database: AppDatabase
) {
    private val dao = database.hiddenContentDao()

    fun observeHiddenContent(): Flow<List<HiddenContent>> {
        return dao.observeHiddenContent().map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun isHidden(originalText: String): Boolean {
        return dao.find(ContentNormalizer.contentHash(originalText)) != null
    }

    suspend fun add(message: TelegramMessage) {
        if (message.originalText.isBlank()) return
        val hash = ContentNormalizer.contentHash(message.originalText)
        val existing = dao.find(hash)
        dao.upsert(HiddenContentEntity(
            contentHash = hash,
            preview = message.translatedText.ifBlank { message.originalText }.take(120),
            hiddenCount = (existing?.hiddenCount ?: 0) + 1,
            createdAt = "Hôm nay",
            createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis()
        ))
    }

    suspend fun remove(contentHash: String) {
        dao.delete(contentHash)
    }
}

class MediaDownloadPolicy {
    fun decide(networkMode: NetworkMode, kind: MessageKind, sizeMb: Int): DownloadDecision {
        return when (networkMode) {
            NetworkMode.Roaming -> DownloadDecision(false, "Roaming: không tự tải")
            NetworkMode.MobileData -> DownloadDecision(
                autoDownload = kind == MessageKind.Image && sizeMb <= 1,
                reason = "Dữ liệu di động: chỉ tải thumbnail"
            )
            NetworkMode.Wifi -> DownloadDecision(
                autoDownload = kind == MessageKind.Image && sizeMb <= 8,
                reason = "Wi-Fi: tải thumbnail và ảnh nhỏ"
            )
        }
    }
}

object SampleTelegramRepository {
    val chatIds: List<Long> = listOf(1001L, 1002L, 1003L)
    val senderIds: List<String> = listOf("sample:ai-news", "sample:crypto", "sample:minh", "sample:mirror")

    fun senders(): List<TelegramSender> = listOf(
        TelegramSender(
            id = "sample:ai-news",
            displayName = "AI News Global",
            type = "channel",
            updatedAtMillis = System.currentTimeMillis()
        ),
        TelegramSender(
            id = "sample:crypto",
            displayName = "Crypto Signals",
            type = "channel",
            updatedAtMillis = System.currentTimeMillis()
        ),
        TelegramSender(
            id = "sample:minh",
            displayName = "Minh",
            type = "user",
            updatedAtMillis = System.currentTimeMillis()
        ),
        TelegramSender(
            id = "sample:mirror",
            displayName = "Mirror",
            type = "channel",
            updatedAtMillis = System.currentTimeMillis()
        )
    )

    fun chats(): List<TelegramChat> = listOf(
        TelegramChat(
            id = 1001,
            title = "AI News Global",
            type = "channel",
            unreadCount = 2,
            lastMessagePreview = "Các mô hình dịch mã nguồn mở đang nhỏ hơn và nhanh hơn.",
            updatedAtMillis = System.currentTimeMillis() - 60_000L
        ),
        TelegramChat(
            id = 1002,
            title = "Crypto Signals",
            type = "channel",
            unreadCount = 8,
            lastMessagePreview = "Ưu đãi có hạn. Tham gia nhóm riêng này ngay.",
            updatedAtMillis = System.currentTimeMillis() - 120_000L
        ),
        TelegramChat(
            id = 1003,
            title = "Android Dev",
            type = "group",
            unreadCount = 0,
            lastMessagePreview = "Compose Preview giúp tinh chỉnh bố cục đáp ứng dễ hơn.",
            updatedAtMillis = System.currentTimeMillis() - 180_000L
        )
    )

    fun messages(): List<TelegramMessage> = listOf(
        TelegramMessage(
            chatId = 1001,
            id = 1,
            senderId = "sample:ai-news",
            chatTitle = "AI News Global",
            author = "Channel",
            originalText = "Open-source translation models are becoming smaller and faster for mobile devices.",
            translatedText = "Các mô hình dịch mã nguồn mở đang nhỏ hơn và nhanh hơn cho thiết bị di động.",
            translationStatus = TranslationStatus.Ready,
            detectedLanguage = "en",
            kind = MessageKind.Text,
            mediaSizeMb = 0,
            timestamp = "09:12"
        ),
        TelegramMessage(
            chatId = 1002,
            id = 2,
            senderId = "sample:crypto",
            chatTitle = "Crypto Signals",
            author = "Channel",
            originalText = "Limited offer. Join this private group now and double your portfolio.",
            translatedText = "Ưu đãi có hạn. Tham gia nhóm riêng này ngay và nhân đôi danh mục của bạn.",
            translationStatus = TranslationStatus.Ready,
            detectedLanguage = "en",
            kind = MessageKind.Image,
            mediaSizeMb = 3,
            timestamp = "09:31"
        ),
        TelegramMessage(
            chatId = 1003,
            id = 3,
            senderId = "sample:minh",
            chatTitle = "Android Dev",
            author = "Minh",
            originalText = "Compose previews make it easier to tune responsive layouts before wiring data.",
            translatedText = "Compose Preview giúp tinh chỉnh bố cục đáp ứng dễ hơn trước khi nối dữ liệu.",
            translationStatus = TranslationStatus.Ready,
            detectedLanguage = "en",
            kind = MessageKind.Text,
            mediaSizeMb = 0,
            timestamp = "10:04"
        ),
        TelegramMessage(
            chatId = 1002,
            id = 4,
            senderId = "sample:mirror",
            chatTitle = "Crypto Signals Mirror",
            author = "Mirror",
            originalText = "Limited offer. Join this private group now and double your portfolio.",
            translatedText = "Ưu đãi có hạn. Tham gia nhóm riêng này ngay và nhân đôi danh mục của bạn.",
            translationStatus = TranslationStatus.Ready,
            detectedLanguage = "en",
            kind = MessageKind.Image,
            mediaSizeMb = 3,
            timestamp = "10:40"
        )
    )
}

private fun TelegramSender.toEntity(): SenderEntity {
    return SenderEntity(
        id = id,
        displayName = displayName,
        type = type,
        updatedAtMillis = updatedAtMillis,
        isContact = isContact
    )
}

private fun TelegramChat.toEntity(): ChatEntity {
    return ChatEntity(
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

private fun TelegramMessage.toEntity(receivedAtMillis: Long): MessageEntity {
    return MessageEntity(
        uid = "$chatId:$id",
        id = id,
        chatId = chatId,
        senderId = senderId,
        chatTitle = chatTitle,
        author = author,
        originalText = originalText,
        translatedText = translatedText,
        translationStatus = translationStatus.name,
        detectedLanguage = detectedLanguage,
        translationFailureReason = translationFailureReason.name,
        translationTargetLanguage = translationTargetLanguage,
        kind = kind.name,
        mediaSizeMb = mediaSizeMb,
        mediaFileId = mediaFileId,
        mediaLocalPath = mediaLocalPath,
        mediaMimeType = mediaMimeType,
        mediaFileName = mediaFileName,
        mediaThumbnailFileId = mediaThumbnailFileId,
        mediaThumbnailLocalPath = mediaThumbnailLocalPath,
        mediaDownloadedPrefixBytes = mediaDownloadedPrefixBytes,
        timestamp = timestamp,
        syncState = syncState.name,
        isOutgoing = isOutgoing,
        isRead = isRead,
        isEdited = isEdited,
        isPinned = isPinned,
        contentHash = ContentNormalizer.contentHash(originalText),
        receivedAtMillis = receivedAtMillis.takeIf { it > 0L } ?: this.receivedAtMillis
    )
}

fun TelegramMessage.uid(): String = "$chatId:$id"

private fun TelegramMessage.mergeIncoming(incoming: TelegramMessage): TelegramMessage {
    val existingHasMedia = kind != MessageKind.Text ||
        mediaFileId > 0 ||
        mediaThumbnailFileId > 0 ||
        mediaLocalPath.isNotBlank() ||
        mediaThumbnailLocalPath.isNotBlank()
    val incomingHasMedia = incoming.kind != MessageKind.Text ||
        incoming.mediaFileId > 0 ||
        incoming.mediaThumbnailFileId > 0 ||
        incoming.mediaLocalPath.isNotBlank() ||
        incoming.mediaThumbnailLocalPath.isNotBlank()
    val incomingMediaFileChanged = incoming.mediaFileId > 0 && incoming.mediaFileId != mediaFileId
    val mergedOriginalText = incoming.originalText.ifBlank { originalText }
    val originalTextUnchanged = ContentNormalizer.contentHash(mergedOriginalText) == ContentNormalizer.contentHash(originalText)
    val keepExistingTranslation = incoming.translatedText.isBlank() && translatedText.isNotBlank() && originalTextUnchanged

    return incoming.copy(
        senderId = incoming.senderId.ifBlank { senderId },
        chatTitle = incoming.chatTitle.takeUnless { it.isBlank() || it == "Telegram" }
            ?: chatTitle.ifBlank { incoming.chatTitle },
        author = incoming.author.takeUnless { it.isBlank() || it == "Telegram user" }
            ?: author.ifBlank { incoming.author },
        originalText = mergedOriginalText,
        translatedText = when {
            incoming.translatedText.isNotBlank() -> incoming.translatedText
            keepExistingTranslation -> translatedText
            else -> ""
        },
        translationStatus = if (keepExistingTranslation) translationStatus else incoming.translationStatus,
        detectedLanguage = if (keepExistingTranslation) {
            incoming.detectedLanguage.ifBlank { detectedLanguage }
        } else {
            incoming.detectedLanguage
        },
        translationFailureReason = if (keepExistingTranslation) {
            translationFailureReason
        } else {
            incoming.translationFailureReason
        },
        translationTargetLanguage = incoming.translationTargetLanguage.ifBlank { translationTargetLanguage },
        kind = when {
            incoming.kind != MessageKind.Text -> incoming.kind
            existingHasMedia && !incomingHasMedia -> kind
            else -> incoming.kind
        },
        mediaSizeMb = incoming.mediaSizeMb.takeIf { it > 0 } ?: mediaSizeMb,
        mediaFileId = incoming.mediaFileId.takeIf { it > 0 } ?: mediaFileId,
        mediaLocalPath = incoming.mediaLocalPath.ifBlank {
            if (incomingMediaFileChanged) "" else mediaLocalPath
        },
        mediaMimeType = incoming.mediaMimeType.ifBlank { mediaMimeType },
        mediaFileName = incoming.mediaFileName.ifBlank { mediaFileName },
        mediaThumbnailFileId = incoming.mediaThumbnailFileId.takeIf { it > 0 } ?: mediaThumbnailFileId,
        mediaThumbnailLocalPath = incoming.mediaThumbnailLocalPath.ifBlank { mediaThumbnailLocalPath },
        mediaDownloadedPrefixBytes = if (incomingMediaFileChanged) {
            incoming.mediaDownloadedPrefixBytes
        } else {
            maxOf(mediaDownloadedPrefixBytes, incoming.mediaDownloadedPrefixBytes)
        },
        timestamp = incoming.timestamp.ifBlank { timestamp },
        isRead = isRead || incoming.isRead,
        isPinned = isPinned || incoming.isPinned,
        receivedAtMillis = receivedAtMillis
    )
}
