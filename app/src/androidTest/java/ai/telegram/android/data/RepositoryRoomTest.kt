package ai.telegram.android.data

import ai.telegram.android.core.ContentNormalizer
import ai.telegram.android.DeviceSafeConnectedTest
import ai.telegram.android.data.local.AppDatabase
import ai.telegram.android.data.local.MediaCacheEntity
import ai.telegram.android.data.translation.TranslationPreflight
import ai.telegram.android.data.translation.TranslationProcessor
import ai.telegram.android.data.translation.TranslationProvider
import ai.telegram.android.data.translation.TranslationQueue
import ai.telegram.android.data.translation.TranslationQueueResult
import ai.telegram.android.data.translation.TranslationResult
import ai.telegram.android.data.translation.TranslationSkipReason
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@DeviceSafeConnectedTest
class RepositoryRoomTest {
    private lateinit var database: AppDatabase
    private lateinit var blacklistRepository: BlacklistRepository
    private lateinit var translationCacheRepository: TranslationCacheRepository
    private lateinit var translationJobRepository: TranslationJobRepository
    private lateinit var mediaCacheRepository: MediaCacheRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var chatHistoryStateRepository: ChatHistoryStateRepository
    private lateinit var messageRepository: MessageRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        chatRepository = ChatRepository(database)
        messageRepository = MessageRepository(database)
        chatHistoryStateRepository = ChatHistoryStateRepository(database)
        blacklistRepository = BlacklistRepository(database)
        translationCacheRepository = TranslationCacheRepository(database)
        translationJobRepository = TranslationJobRepository(database)
        mediaCacheRepository = MediaCacheRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun blacklist_appliesToMessagesWithEquivalentContent() = runTest {
        val first = message(
            id = 1,
            originalText = "Limited offer. Join this private group now.",
            translatedText = "Uu dai co han. Tham gia nhom rieng nay."
        )
        val equivalent = "  limited   OFFER. join this private group now. "

        blacklistRepository.add(first)

        assertTrue(blacklistRepository.isHidden(equivalent))
        assertEquals(ContentNormalizer.contentHash(first.originalText), ContentNormalizer.contentHash(equivalent))
    }

    @Test
    fun blacklist_removeUnhidesMatchingContent() = runTest {
        val hidden = message(id = 2, originalText = "Same content")
        blacklistRepository.add(hidden)

        blacklistRepository.remove(ContentNormalizer.contentHash(hidden.originalText))

        assertFalse(blacklistRepository.isHidden("same   CONTENT"))
    }

    @Test
    fun translationCache_reusesCachedTranslationAndIncrementsHitCount() = runTest {
        var translateCalls = 0

        val first = translationCacheRepository.getOrPut(
            originalText = "Open-source translation models are smaller.",
            targetLanguage = "vi"
        ) {
            translateCalls += 1
            "Mo hinh dich nguon mo nho hon."
        }
        val second = translationCacheRepository.getOrPut(
            originalText = " open-source   TRANSLATION models are smaller. ",
            targetLanguage = "vi"
        ) {
            translateCalls += 1
            "Khong nen duoc goi."
        }
        val cached = database.translationCacheDao().find(
            contentHash = ContentNormalizer.contentHash("Open-source translation models are smaller."),
            targetLanguage = "vi",
            providerVersion = "on-device-v1"
        )

        assertEquals("Mo hinh dich nguon mo nho hon.", first)
        assertEquals(first, second)
        assertEquals(1, translateCalls)
        assertEquals(2, cached?.hitCount)
    }

    @Test
    fun translationCache_separatesTargetLanguagesAndProviderVersions() = runTest {
        val originalText = "Model management is ready."

        val vi = translationCacheRepository.getOrPut(originalText, targetLanguage = "vi") { "Quan ly model da san sang." }
        val en = translationCacheRepository.getOrPut(originalText, targetLanguage = "en") { "Model management is ready." }
        val providerV2 = translationCacheRepository.getOrPut(
            originalText = originalText,
            targetLanguage = "vi",
            providerVersion = "on-device-v2"
        ) { "Quan ly model da san sang v2." }

        assertEquals("Quan ly model da san sang.", vi)
        assertEquals("Model management is ready.", en)
        assertEquals("Quan ly model da san sang v2.", providerV2)
        assertEquals(3, database.translationCacheDao().count())
    }

    @Test
    fun translationProcessor_failedTranslationDoesNotStoreOriginalTextButPreviewsSource() = runTest {
        val failedPreview = "No translation available"
        val sourceText = "Original source text must stay out of translated UI."
        val chat = TelegramChat(
            id = 200,
            title = "Source chat",
            type = "channel",
            unreadCount = 0,
            lastMessagePreview = "",
            updatedAtMillis = 1L
        )
        val sourceMessage = message(
            id = 5,
            originalText = sourceText,
            translatedText = "",
        ).copy(
            chatId = chat.id,
            chatTitle = chat.title,
            translationStatus = TranslationStatus.Pending,
            detectedLanguage = ""
        )
        val processor = TranslationProcessor(
            messageRepository = messageRepository,
            chatRepository = chatRepository,
            blacklistRepository = blacklistRepository,
            translationCacheRepository = translationCacheRepository,
            translationProvider = AlwaysUnavailableTranslationProvider,
            failedPreview = failedPreview
        )

        chatRepository.upsert(chat)
        messageRepository.upsert(sourceMessage)

        val result = processor.process(sourceMessage)
        val storedMessage = messageRepository.find(sourceMessage.uid())
        val storedChat = database.chatDao().find(chat.id)

        assertTrue(result.completed)
        assertEquals(TranslationFailureReason.MissingModel, result.failureReason)
        assertEquals(TranslationStatus.Failed, storedMessage?.translationStatus)
        assertEquals("", storedMessage?.translatedText)
        assertEquals("en", storedMessage?.detectedLanguage)
        assertEquals(sourceText, storedChat?.lastMessagePreview)
    }

    @Test
    fun chatRepository_doesNotPromotePublicLinkChatsIntoMainList() = runTest {
        val mainChat = TelegramChat(
            id = 101,
            title = "Main channel",
            type = "channel",
            unreadCount = 0,
            lastMessagePreview = "",
            updatedAtMillis = 1L,
            isMainList = true
        )
        val publicLinkChat = TelegramChat(
            id = 202,
            title = "Public link result",
            type = "channel",
            unreadCount = 0,
            lastMessagePreview = "",
            updatedAtMillis = 2L,
            isMainList = false
        )
        val publicLastMessageUpdate = publicLinkChat.copy(
            title = "",
            lastMessagePreview = "Latest public post",
            updatedAtMillis = 3L,
            isMainList = false
        )
        val mainLastMessageUpdate = mainChat.copy(
            title = "",
            lastMessagePreview = "Latest main post",
            updatedAtMillis = 4L,
            isMainList = false
        )
        val previouslyPromotedPublicChat = TelegramChat(
            id = 303,
            title = "Previously promoted public result",
            type = "channel",
            unreadCount = 0,
            lastMessagePreview = "",
            updatedAtMillis = 5L,
            isMainList = true
        )
        val authoritativePublicUpdate = previouslyPromotedPublicChat.copy(
            updatedAtMillis = 6L,
            isMainList = false
        )

        chatRepository.upsert(mainChat)
        chatRepository.upsert(publicLinkChat)
        chatRepository.upsert(publicLastMessageUpdate)
        chatRepository.upsert(mainLastMessageUpdate)
        chatRepository.upsert(previouslyPromotedPublicChat)
        chatRepository.upsert(authoritativePublicUpdate)

        val visibleChats = chatRepository.observeChats().first()

        assertEquals(1, visibleChats.size)
        assertEquals(mainChat.id, visibleChats.single().id)
        assertEquals("Latest main post", visibleChats.single().lastMessagePreview)
        assertEquals(1, database.chatDao().count())
        assertEquals(false, database.chatDao().find(publicLinkChat.id)?.isMainList)
        assertEquals(false, database.chatDao().find(previouslyPromotedPublicChat.id)?.isMainList)
    }

    @Test
    fun chatRepository_truncatesLongChatPreviewsBeforeStorage() = runTest {
        val longPreview = "A".repeat(2_000)
        val chat = TelegramChat(
            id = 404,
            title = "Large preview chat",
            type = "channel",
            unreadCount = 0,
            lastMessagePreview = longPreview,
            updatedAtMillis = 1L,
            isMainList = true
        )

        chatRepository.upsert(chat)
        chatRepository.touchLastMessage(chat.id, longPreview)

        val stored = database.chatDao().find(chat.id)
        val visible = chatRepository.observeChats().first().single { it.id == chat.id }

        assertEquals(500, stored?.lastMessagePreview?.length)
        assertEquals(500, visible.lastMessagePreview.length)
    }

    @Test
    fun translationCache_doesNotCacheRetryableFailures() = runTest {
        var translateCalls = 0

        repeat(2) {
            val result = translationCacheRepository.getOrTranslate(
                originalText = "Retry this translation later.",
                targetLanguage = "vi",
                providerVersion = "test-retryable"
            ) {
                translateCalls += 1
                TranslationResult.Unavailable(
                    reason = "Temporary outage",
                    failureReason = TranslationFailureReason.TransientError
                )
            }

            assertEquals(TranslationFailureReason.TransientError, result.failureReason)
        }

        assertEquals(2, translateCalls)
        assertEquals(0, database.translationCacheDao().count())
    }

    @Test
    fun recentMessagesNeedingTranslation_returnsPendingAndRetryableFailuresOnly() = runTest {
        val pending = message(id = 10, originalText = "Pending text").copy(
            translatedText = "",
            translationStatus = TranslationStatus.Pending
        )
        val retryableFailed = message(id = 11, originalText = "Temporary failed text").copy(
            translatedText = "",
            translationStatus = TranslationStatus.Failed,
            translationFailureReason = TranslationFailureReason.NetworkRequired
        )
        val missingModelFailed = message(id = 12, originalText = "Missing model text").copy(
            translatedText = "",
            translationStatus = TranslationStatus.Failed,
            translationFailureReason = TranslationFailureReason.MissingModel
        )
        val ready = message(id = 13, originalText = "Ready text")

        listOf(pending, retryableFailed, missingModelFailed, ready).forEach {
            messageRepository.upsert(it)
        }

        val ids = messageRepository.recentMessagesNeedingTranslation(limit = 10).map { it.id }.toSet()

        assertEquals(setOf(10L, 11L), ids)
    }

    @Test
    fun translationQueue_skipsUrlOnlyContentAsNonTranslatableWithoutJob() = runTest {
        val source = message(
            id = 900,
            originalText = "https://t.me/example",
            translatedText = ""
        ).copy(
            translationStatus = TranslationStatus.Pending,
            detectedLanguage = ""
        )
        messageRepository.upsert(source)
        val queue = TranslationQueue(
            context = ApplicationProvider.getApplicationContext(),
            translationJobRepository = translationJobRepository,
            messageRepository = messageRepository,
            preflight = TranslationPreflight(
                downloadedLanguageCodes = { emptySet() },
                detectSourceLanguage = { error("URL-only content should be skipped before language detection.") }
            ),
            networkModeProvider = { NetworkMode.Wifi }
        )

        val result = queue.enqueueOnly(source)
        val updated = messageRepository.find(source.uid())!!

        assertEquals(TranslationQueueResult.Skipped(TranslationSkipReason.UrlOnly), result)
        assertEquals(TranslationStatus.Failed, updated.translationStatus)
        assertEquals(TranslationFailureReason.NonTranslatable, updated.translationFailureReason)
        assertEquals("", updated.translatedText)
        assertEquals(0, translationJobRepository.pendingCount())
    }

    @Test
    fun translationQueue_reportsMissingModelAsUpdatedOnlyWithoutJob() = runTest {
        val source = message(
            id = 901,
            originalText = "Open source models are getting smaller.",
            translatedText = ""
        ).copy(
            translationStatus = TranslationStatus.Pending,
            detectedLanguage = ""
        )
        messageRepository.upsert(source)
        val queue = TranslationQueue(
            context = ApplicationProvider.getApplicationContext(),
            translationJobRepository = translationJobRepository,
            messageRepository = messageRepository,
            preflight = TranslationPreflight(
                downloadedLanguageCodes = { emptySet() },
                detectSourceLanguage = { "en" }
            ),
            networkModeProvider = { NetworkMode.Wifi }
        )

        val result = queue.enqueueOnly(source)
        val updated = messageRepository.find(source.uid())!!

        assertEquals(TranslationQueueResult.UpdatedOnly(TranslationFailureReason.MissingModel), result)
        assertEquals(TranslationStatus.Failed, updated.translationStatus)
        assertEquals(TranslationFailureReason.MissingModel, updated.translationFailureReason)
        assertEquals("en", updated.detectedLanguage)
        assertEquals(0, translationJobRepository.pendingCount())
    }

    @Test
    fun messageUpsert_dropsStaleTranslationWhenOriginalTextChanges() = runTest {
        val original = message(
            id = 14,
            originalText = "Old source text",
            translatedText = "Ban dich cu"
        )
        val changed = original.copy(
            originalText = "New source text",
            translatedText = "",
            translationStatus = TranslationStatus.Pending,
            detectedLanguage = ""
        )

        messageRepository.upsert(original)
        messageRepository.upsert(changed)

        val stored = messageRepository.find(original.uid())
        assertEquals("New source text", stored?.originalText)
        assertEquals("", stored?.translatedText)
        assertEquals(TranslationStatus.Pending, stored?.translationStatus)
        assertEquals("", stored?.detectedLanguage)
    }

    @Test
    fun messageUpsert_keepsTranslationWhenIncomingMetadataHasSameOriginalText() = runTest {
        val original = message(
            id = 15,
            originalText = "Same source text",
            translatedText = "Cung noi dung"
        )
        val metadataUpdate = original.copy(
            translatedText = "",
            translationStatus = TranslationStatus.Pending,
            detectedLanguage = "",
            isRead = true
        )

        messageRepository.upsert(original)
        messageRepository.upsert(metadataUpdate)

        val stored = messageRepository.find(original.uid())
        assertEquals("Cung noi dung", stored?.translatedText)
        assertEquals(TranslationStatus.Ready, stored?.translationStatus)
        assertEquals("en", stored?.detectedLanguage)
        assertEquals(true, stored?.isRead)
    }

    @Test
    fun chatHistory_initialLoadStillRunsWhenOnlyLastMessageIsCached() = runTest {
        val lastMessage = message(id = 30, originalText = "Latest channel post")

        messageRepository.upsert(lastMessage)

        assertTrue(chatHistoryStateRepository.shouldLoadInitialHistory(lastMessage.chatId))
    }

    @Test
    fun chatHistory_initialLoadRetriesWhenInitialHistoryPageIsShort() = runTest {
        val onlyMessage = message(id = 31, originalText = "Only cached post")

        messageRepository.upsert(onlyMessage)
        chatHistoryStateRepository.recordHistoryLoaded(
            chatId = onlyMessage.chatId,
            fromMessageId = 0L,
            messageIds = listOf(onlyMessage.id),
            requestedLimit = 100
        )

        assertTrue(chatHistoryStateRepository.shouldLoadInitialHistory(onlyMessage.chatId))
    }

    @Test
    fun chatHistory_olderLoadStopsWhenOlderHistoryPageIsExhausted() = runTest {
        val olderMessage = message(id = 32, originalText = "Older cached post")

        messageRepository.upsert(olderMessage)
        chatHistoryStateRepository.recordHistoryLoaded(
            chatId = olderMessage.chatId,
            fromMessageId = olderMessage.id,
            messageIds = listOf(olderMessage.id),
            requestedLimit = 100
        )

        assertFalse(chatHistoryStateRepository.shouldLoadOlderHistory(olderMessage.chatId, olderMessage.id))
    }

    @Test
    fun chatHistory_initialLoadRecoversWhenHistoryStateReferencesMissingOlderMessages() = runTest {
        val newerMessage = message(id = 73400320, originalText = "Latest cached post")

        chatHistoryStateRepository.recordHistoryLoaded(
            chatId = newerMessage.chatId,
            fromMessageId = 0L,
            messageIds = listOf(73400320L, 4194304L),
            requestedLimit = 100
        )
        messageRepository.upsert(newerMessage)

        assertTrue(chatHistoryStateRepository.shouldLoadInitialHistory(newerMessage.chatId))
    }

    @Test
    fun translationJob_enqueueReopensFailedJobForRetry() = runTest {
        val sourceMessage = message(id = 20, originalText = "Retry me when the network comes back.").copy(
            translatedText = "",
            translationStatus = TranslationStatus.Failed,
            translationFailureReason = TranslationFailureReason.NetworkRequired
        )

        translationJobRepository.enqueue(sourceMessage)
        val viJobKey = translationJobKey(sourceMessage)
        translationJobRepository.markRunning(viJobKey)
        translationJobRepository.markFailed(viJobKey)

        translationJobRepository.enqueue(sourceMessage, targetLanguage = "en")

        val job = database.translationJobDao().find(translationJobKey(sourceMessage, "en"))
        assertEquals(TranslationJobStatus.Pending.name, job?.status)
        assertEquals(0, job?.attempts)
        assertEquals("en", job?.targetLanguage)
    }

    @Test
    fun translationJob_enqueueDoesNotResetPendingJob() = runTest {
        val sourceMessage = message(id = 21, originalText = "Already queued.")

        translationJobRepository.enqueue(sourceMessage)
        val viJobKey = translationJobKey(sourceMessage)
        translationJobRepository.markRunning(viJobKey)

        translationJobRepository.enqueue(sourceMessage, targetLanguage = "en")

        val job = database.translationJobDao().find(viJobKey)
        assertEquals(TranslationJobStatus.Running.name, job?.status)
        assertEquals(1, job?.attempts)
        assertEquals("vi", job?.targetLanguage)
    }

    @Test
    fun mediaCache_markRequestedResetsCorruptState() = runTest {
        database.mediaCacheDao().upsert(
            MediaCacheEntity(
                fileId = 77,
                kind = MessageKind.Image.name,
                localPath = "",
                sizeMb = 1,
                actualSizeBytes = 0L,
                downloadedPrefixBytes = 0L,
                state = MediaCacheState.Corrupt.name,
                requestedAtMillis = 1L,
                updatedAtMillis = 1L,
                lastAccessedAtMillis = 1L
            )
        )

        assertTrue(mediaCacheRepository.shouldRequest(77))

        mediaCacheRepository.markRequested(77, MessageKind.Image)

        assertEquals(MediaCacheState.Requested.name, database.mediaCacheDao().find(77)?.state)
    }

    @Test
    fun mediaCache_fullDownloadIgnoresReadableFileWhenKindChanged() = runTest {
        val staleImage = File(
            ApplicationProvider.getApplicationContext<Context>().cacheDir,
            "stale-image-cache.jpg"
        ).apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
        database.mediaCacheDao().upsert(
            MediaCacheEntity(
                fileId = 1938,
                kind = MessageKind.Image.name,
                localPath = staleImage.absolutePath,
                sizeMb = 1,
                actualSizeBytes = staleImage.length(),
                downloadedPrefixBytes = staleImage.length(),
                state = MediaCacheState.FullDownloaded.name,
                requestedAtMillis = 1L,
                updatedAtMillis = 1L,
                lastAccessedAtMillis = 1L
            )
        )

        assertTrue(mediaCacheRepository.shouldRequestFullDownload(1938, MessageKind.Video))
        assertFalse(mediaCacheRepository.shouldRequestFullDownload(1938, MessageKind.Image))

        mediaCacheRepository.markFullDownloadRequested(1938, MessageKind.Video)

        val refreshed = database.mediaCacheDao().find(1938)
        assertEquals(MessageKind.Video.name, refreshed?.kind)
        assertEquals("", refreshed?.localPath)
        assertEquals(0L, refreshed?.actualSizeBytes)
        assertEquals(0L, refreshed?.downloadedPrefixBytes)
        assertEquals(MediaCacheState.Requested.name, refreshed?.state)
    }

    @Test
    fun mediaCache_fullDownloadKeepsSameKindMetadataWhenRequeued() = runTest {
        database.mediaCacheDao().upsert(
            MediaCacheEntity(
                fileId = 1940,
                kind = MessageKind.Video.name,
                localPath = "",
                sizeMb = 5,
                actualSizeBytes = 5_000_000L,
                downloadedPrefixBytes = 256_000L,
                state = MediaCacheState.PrefixReady.name,
                requestedAtMillis = 1L,
                updatedAtMillis = 1L,
                lastAccessedAtMillis = 1L
            )
        )

        mediaCacheRepository.markFullDownloadRequested(1940, MessageKind.Video)

        val refreshed = database.mediaCacheDao().find(1940)
        assertEquals(MessageKind.Video.name, refreshed?.kind)
        assertEquals(5_000_000L, refreshed?.actualSizeBytes)
        assertEquals(256_000L, refreshed?.downloadedPrefixBytes)
        assertEquals(MediaCacheState.Requested.name, refreshed?.state)
    }

    @Test
    fun mediaCache_updateResetsMetadataWhenExplicitKindChanges() = runTest {
        val staleImage = File(
            ApplicationProvider.getApplicationContext<Context>().cacheDir,
            "stale-update-image-cache.jpg"
        ).apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
        database.mediaCacheDao().upsert(
            MediaCacheEntity(
                fileId = 1942,
                kind = MessageKind.Image.name,
                localPath = staleImage.absolutePath,
                sizeMb = 1,
                actualSizeBytes = staleImage.length(),
                downloadedPrefixBytes = staleImage.length(),
                state = MediaCacheState.FullDownloaded.name,
                requestedAtMillis = 1L,
                updatedAtMillis = 1L,
                lastAccessedAtMillis = 1L
            )
        )

        mediaCacheRepository.update(
            TelegramMediaFile(
                id = 1942,
                localPath = "",
                sizeMb = 8,
                downloadedPrefixBytes = 0L
            ),
            fallbackKind = MessageKind.Video
        )

        val refreshed = database.mediaCacheDao().find(1942)
        assertEquals(MessageKind.Video.name, refreshed?.kind)
        assertEquals("", refreshed?.localPath)
        assertEquals(0L, refreshed?.actualSizeBytes)
        assertEquals(0L, refreshed?.downloadedPrefixBytes)
        assertEquals(MediaCacheState.Requested.name, refreshed?.state)
    }

    private fun translationJobKey(message: TelegramMessage, targetLanguage: String = "vi"): String {
        return TranslationJobIdentity.key(
            message.uid(),
            ContentNormalizer.contentHash(message.originalText),
            targetLanguage
        )
    }

    private fun message(
        id: Long,
        originalText: String,
        translatedText: String = originalText
    ): TelegramMessage {
        return TelegramMessage(
            chatId = 100,
            id = id,
            senderId = "test:sender",
            chatTitle = "Test",
            author = "Tester",
            originalText = originalText,
            translatedText = translatedText,
            translationStatus = TranslationStatus.Ready,
            detectedLanguage = "en",
            kind = MessageKind.Text,
            mediaSizeMb = 0,
            timestamp = "10:00"
        )
    }

    private object AlwaysUnavailableTranslationProvider : TranslationProvider {
        override val providerVersion: String = "test-unavailable"

        override suspend fun translate(text: String, targetLanguage: String): TranslationResult {
            return TranslationResult.Unavailable(
                reason = "No model",
                sourceLanguageCode = "en",
                targetLanguageCode = targetLanguage,
                failureReason = TranslationFailureReason.MissingModel
            )
        }
    }
}
