package ai.telegram.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query(
        """
        SELECT id,
               title,
               type,
               unreadCount,
               substr(lastMessagePreview, 1, 500) AS lastMessagePreview,
               updatedAtMillis,
               activeAction,
               lastReadInboxMessageId,
               lastReadOutboxMessageId,
               pinnedMessageId,
               isMainList
        FROM chats
        WHERE isMainList = 1
        ORDER BY updatedAtMillis DESC
        LIMIT 500
        """
    )
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun find(chatId: Long): ChatEntity?

    @Query("SELECT COUNT(*) FROM chats WHERE isMainList = 1")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(chats: List<ChatEntity>)

    @Query("DELETE FROM chats WHERE id IN (:chatIds)")
    suspend fun deleteByIds(chatIds: List<Long>): Int

    @Query("DELETE FROM chats")
    suspend fun clear()

    @Query(
        """
        UPDATE chats
        SET unreadCount = :unreadCount,
            lastReadInboxMessageId = CASE
                WHEN :lastReadInboxMessageId > lastReadInboxMessageId THEN :lastReadInboxMessageId
                ELSE lastReadInboxMessageId
            END,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :chatId
        """
    )
    suspend fun updateReadInbox(chatId: Long, lastReadInboxMessageId: Long, unreadCount: Int, updatedAtMillis: Long)

    @Query(
        """
        UPDATE chats
        SET lastReadOutboxMessageId = CASE
                WHEN :lastReadOutboxMessageId > lastReadOutboxMessageId THEN :lastReadOutboxMessageId
                ELSE lastReadOutboxMessageId
            END,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :chatId
        """
    )
    suspend fun updateReadOutbox(chatId: Long, lastReadOutboxMessageId: Long, updatedAtMillis: Long)

    @Query(
        """
        UPDATE chats
        SET activeAction = :activeAction,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :chatId
        """
    )
    suspend fun updateActiveAction(chatId: Long, activeAction: String, updatedAtMillis: Long)

    @Query(
        """
        UPDATE chats
        SET pinnedMessageId = :messageId,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :chatId
        """
    )
    suspend fun updatePinnedMessage(chatId: Long, messageId: Long, updatedAtMillis: Long)

    @Upsert
    suspend fun upsert(chat: ChatEntity)
}

@Dao
interface SenderDao {
    @Query("SELECT * FROM senders ORDER BY displayName COLLATE NOCASE ASC")
    fun observeSenders(): Flow<List<SenderEntity>>

    @Query("SELECT * FROM senders WHERE isContact = 1 ORDER BY displayName COLLATE NOCASE ASC")
    fun observeContacts(): Flow<List<SenderEntity>>

    @Query("SELECT * FROM senders WHERE id = :senderId LIMIT 1")
    suspend fun find(senderId: String): SenderEntity?

    @Query("DELETE FROM senders")
    suspend fun clear()

    @Query("DELETE FROM senders WHERE id IN (:senderIds)")
    suspend fun deleteByIds(senderIds: List<String>): Int

    @Upsert
    suspend fun upsert(sender: SenderEntity)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY receivedAtMillis DESC")
    fun observeMessages(): Flow<List<MessageEntity>>

    @Transaction
    @Query("SELECT * FROM messages ORDER BY receivedAtMillis DESC")
    fun observeMessagesWithMetadata(): Flow<List<MessageWithMetadata>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY receivedAtMillis DESC")
    fun observeMessagesForChat(chatId: Long): Flow<List<MessageEntity>>

    @Transaction
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY receivedAtMillis DESC")
    fun observeMessagesForChatWithMetadata(chatId: Long): Flow<List<MessageWithMetadata>>

    @Transaction
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY receivedAtMillis DESC LIMIT :limit")
    fun observeMessagesForChatWithMetadata(chatId: Long, limit: Int): Flow<List<MessageWithMetadata>>

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun countForChat(chatId: Long): Int

    @Query("SELECT MAX(id) FROM messages WHERE chatId = :chatId")
    suspend fun newestMessageIdForChat(chatId: Long): Long?

    @Query("SELECT MIN(id) FROM messages WHERE chatId = :chatId")
    suspend fun oldestMessageIdForChat(chatId: Long): Long?

    @Transaction
    @Query("SELECT * FROM messages WHERE uid = :uid LIMIT 1")
    suspend fun findMessageWithMetadata(uid: String): MessageWithMetadata?

    @Transaction
    @Query(
        """
        SELECT * FROM messages
        WHERE originalText != ''
          AND (
              translationStatus = :pendingStatus
              OR (
                  translationStatus = :failedStatus
                  AND translationFailureReason IN (:retryableFailureReasons)
              )
          )
        ORDER BY receivedAtMillis DESC
        LIMIT :limit
        """
    )
    suspend fun recentMessagesNeedingTranslation(
        pendingStatus: String,
        failedStatus: String,
        retryableFailureReasons: List<String>,
        limit: Int
    ): List<MessageWithMetadata>

    @Query("SELECT COALESCE(SUM(mediaSizeMb), 0) FROM messages")
    suspend fun mediaSizeMb(): Int

    @Query(
        """
        UPDATE messages
        SET mediaLocalPath = CASE
                WHEN mediaFileId = :fileId AND :localPath != '' THEN :localPath
                ELSE mediaLocalPath
            END,
            mediaThumbnailLocalPath = CASE
                WHEN mediaThumbnailFileId = :fileId AND :localPath != '' THEN :localPath
                ELSE mediaThumbnailLocalPath
            END,
            mediaDownloadedPrefixBytes = CASE
                WHEN mediaFileId = :fileId AND :downloadedPrefixBytes > 0 THEN :downloadedPrefixBytes
                ELSE mediaDownloadedPrefixBytes
            END,
            mediaSizeMb = CASE WHEN mediaFileId = :fileId AND :sizeMb > 0 THEN :sizeMb ELSE mediaSizeMb END
        WHERE mediaFileId = :fileId OR mediaThumbnailFileId = :fileId
        """
    )
    suspend fun updateMediaFile(fileId: Int, localPath: String, sizeMb: Int, downloadedPrefixBytes: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages")
    suspend fun clear()

    @Query("DELETE FROM messages WHERE chatId IN (:chatIds)")
    suspend fun deleteByChatIds(chatIds: List<Long>): Int

    @Query("DELETE FROM messages WHERE chatId = :chatId AND id IN (:messageIds)")
    suspend fun deleteMessages(chatId: Long, messageIds: List<Long>)

    @Query(
        """
        UPDATE messages
        SET isRead = 1
        WHERE chatId = :chatId
          AND id <= :lastReadMessageId
          AND isOutgoing = :isOutgoing
        """
    )
    suspend fun markReadUpTo(chatId: Long, lastReadMessageId: Long, isOutgoing: Boolean)

    @Query(
        """
        UPDATE messages
        SET syncState = :syncState,
            isRead = :isRead
        WHERE chatId = :chatId
          AND id = :messageId
        """
    )
    suspend fun updateSyncState(chatId: Long, messageId: Long, syncState: String, isRead: Boolean)

    @Query(
        """
        UPDATE messages
        SET originalText = :originalText,
            translatedText = '',
            translationStatus = :translationStatus,
            detectedLanguage = '',
            translationFailureReason = 'None',
            isEdited = 1,
            contentHash = :contentHash
        WHERE chatId = :chatId
          AND id = :messageId
        """
    )
    suspend fun updateTextContent(
        chatId: Long,
        messageId: Long,
        originalText: String,
        translationStatus: String,
        contentHash: String
    )

    @Query(
        """
        UPDATE messages
        SET isPinned = CASE WHEN id = :messageId THEN 1 ELSE 0 END
        WHERE chatId = :chatId
        """
    )
    suspend fun updatePinnedMessage(chatId: Long, messageId: Long)

    @Query(
        """
        UPDATE messages
        SET translatedText = :translatedText,
            translationStatus = :translationStatus,
            translationFailureReason = :translationFailureReason,
            translationTargetLanguage = :translationTargetLanguage
        WHERE uid = :uid
        """
    )
    suspend fun updateTranslation(
        uid: String,
        translatedText: String,
        translationStatus: String,
        translationFailureReason: String,
        translationTargetLanguage: String
    )

    @Query(
        """
        UPDATE messages
        SET translatedText = :translatedText,
            translationStatus = :translationStatus,
            detectedLanguage = :detectedLanguage,
            translationFailureReason = :translationFailureReason,
            translationTargetLanguage = :translationTargetLanguage
        WHERE uid = :uid
        """
    )
    suspend fun updateTranslationWithDetectedLanguage(
        uid: String,
        translatedText: String,
        translationStatus: String,
        detectedLanguage: String,
        translationFailureReason: String,
        translationTargetLanguage: String
    )

    @Upsert
    suspend fun upsert(message: MessageEntity)
}

@Dao
interface ChatHistoryStateDao {
    @Query("SELECT * FROM chat_history_state WHERE chatId = :chatId LIMIT 1")
    suspend fun find(chatId: Long): ChatHistoryStateEntity?

    @Query("DELETE FROM chat_history_state")
    suspend fun clear()

    @Upsert
    suspend fun upsert(state: ChatHistoryStateEntity)
}

@Dao
interface TranslationJobDao {
    @Query("SELECT * FROM translation_jobs WHERE messageUid = :messageUid LIMIT 1")
    suspend fun find(messageUid: String): TranslationJobEntity?

    @Query("SELECT * FROM translation_jobs WHERE status = :status ORDER BY createdAtMillis ASC LIMIT :limit")
    suspend fun findByStatus(status: String, limit: Int): List<TranslationJobEntity>

    @Query("SELECT COUNT(*) FROM translation_jobs WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM translation_jobs WHERE messageUid = :messageUid")
    suspend fun countByMessageUid(messageUid: String): Int

    @Query("DELETE FROM translation_jobs WHERE status = :status AND updatedAtMillis < :cutoffMillis")
    suspend fun deleteStale(status: String, cutoffMillis: Long): Int

    @Query(
        """
        UPDATE translation_jobs
        SET status = :targetStatus,
            updatedAtMillis = :updatedAtMillis
        WHERE status = :sourceStatus
          AND updatedAtMillis < :cutoffMillis
        """
    )
    suspend fun resetStale(
        sourceStatus: String,
        targetStatus: String,
        cutoffMillis: Long,
        updatedAtMillis: Long
    ): Int

    @Query(
        """
        UPDATE translation_jobs
        SET status = :status,
            attempts = attempts + :attemptIncrement,
            updatedAtMillis = :updatedAtMillis
        WHERE messageUid = :messageUid
        """
    )
    suspend fun updateStatus(
        messageUid: String,
        status: String,
        updatedAtMillis: Long,
        attemptIncrement: Int = 0
    )

    @Query("DELETE FROM translation_jobs WHERE messageUid = :messageUid")
    suspend fun delete(messageUid: String)

    @Query("DELETE FROM translation_jobs")
    suspend fun clear()

    @Upsert
    suspend fun upsert(job: TranslationJobEntity)
}

@Dao
interface HiddenContentDao {
    @Query("SELECT * FROM hidden_content ORDER BY createdAtMillis DESC")
    fun observeHiddenContent(): Flow<List<HiddenContentEntity>>

    @Query("SELECT * FROM hidden_content WHERE contentHash = :contentHash LIMIT 1")
    suspend fun find(contentHash: String): HiddenContentEntity?

    @Query("DELETE FROM hidden_content WHERE contentHash = :contentHash")
    suspend fun delete(contentHash: String)

    @Upsert
    suspend fun upsert(entity: HiddenContentEntity)
}

@Dao
interface TranslationCacheDao {
    @Query(
        """
        SELECT * FROM translation_cache
        WHERE contentHash = :contentHash
          AND targetLanguage = :targetLanguage
          AND providerVersion = :providerVersion
        LIMIT 1
        """
    )
    suspend fun find(contentHash: String, targetLanguage: String, providerVersion: String): TranslationCacheEntity?

    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun count(): Int

    @Query(
        """
        SELECT COALESCE(SUM(LENGTH(translatedText) + LENGTH(sourceLanguage) + LENGTH(failureMessage)), 0)
        FROM translation_cache
        """
    )
    suspend fun totalSizeBytes(): Long

    @Query("DELETE FROM translation_cache WHERE updatedAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM translation_cache WHERE expiresAtMillis > 0 AND expiresAtMillis < :nowMillis")
    suspend fun deleteExpiredFailures(nowMillis: Long): Int

    @Query(
        """
        DELETE FROM translation_cache
        WHERE rowid NOT IN (
            SELECT rowid FROM translation_cache
            ORDER BY updatedAtMillis DESC
            LIMIT :maxEntries
        )
        """
    )
    suspend fun trimToMostRecent(maxEntries: Int): Int

    @Query("DELETE FROM translation_cache")
    suspend fun clear()

    @Upsert
    suspend fun upsert(entity: TranslationCacheEntity)
}

@Dao
interface MediaCacheDao {
    @Query("SELECT * FROM media_cache WHERE fileId = :fileId LIMIT 1")
    suspend fun find(fileId: Int): MediaCacheEntity?

    @Query("SELECT COUNT(*) FROM media_cache WHERE fileId = :fileId")
    suspend fun count(fileId: Int): Int

    @Query("SELECT COUNT(*) FROM media_cache")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM media_cache WHERE state = :state")
    suspend fun countByState(state: String): Int

    @Query("SELECT COALESCE(SUM(actualSizeBytes), 0) FROM media_cache")
    suspend fun totalSizeBytes(): Long

    @Query("SELECT * FROM media_cache ORDER BY lastAccessedAtMillis ASC")
    suspend fun oldestEntries(): List<MediaCacheEntity>

    @Query("UPDATE media_cache SET lastAccessedAtMillis = :nowMillis WHERE fileId = :fileId")
    suspend fun touch(fileId: Int, nowMillis: Long)

    @Query(
        """
        DELETE FROM media_cache
        WHERE rowid NOT IN (
            SELECT rowid FROM media_cache
            ORDER BY lastAccessedAtMillis DESC
            LIMIT :maxEntries
        )
        """
    )
    suspend fun trimToMostRecent(maxEntries: Int): Int

    @Query("DELETE FROM media_cache WHERE updatedAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM media_cache WHERE fileId = :fileId")
    suspend fun delete(fileId: Int): Int

    @Query("DELETE FROM media_cache")
    suspend fun clear()

    @Upsert
    suspend fun upsert(entity: MediaCacheEntity)
}

@Dao
interface VideoSubtitleCacheDao {
    @Query(
        """
        SELECT * FROM video_subtitle_cache
        WHERE fileId = :fileId
          AND targetLanguage = :targetLanguage
          AND providerVersion = :providerVersion
        LIMIT 1
        """
    )
    suspend fun find(fileId: Int, targetLanguage: String, providerVersion: String): VideoSubtitleCacheEntity?

    @Query("SELECT COUNT(*) FROM video_subtitle_cache")
    suspend fun count(): Int

    @Query(
        """
        SELECT COALESCE(SUM(LENGTH(sourceText) + LENGTH(cuesJson)), 0)
        FROM video_subtitle_cache
        """
    )
    suspend fun totalSizeBytes(): Long

    @Query(
        """
        DELETE FROM video_subtitle_cache
        WHERE rowid NOT IN (
            SELECT rowid FROM video_subtitle_cache
            ORDER BY lastAccessedAtMillis DESC
            LIMIT :maxEntries
        )
        """
    )
    suspend fun trimToMostRecent(maxEntries: Int): Int

    @Query("DELETE FROM video_subtitle_cache WHERE updatedAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM video_subtitle_cache")
    suspend fun clear()

    @Upsert
    suspend fun upsert(entity: VideoSubtitleCacheEntity)
}
