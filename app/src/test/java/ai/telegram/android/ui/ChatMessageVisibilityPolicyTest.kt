package ai.telegram.android.ui

import ai.telegram.android.core.ContentNormalizer
import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TranslationFailureReason
import ai.telegram.android.data.TranslationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageVisibilityPolicyTest {
    @Test
    fun baseVisibleMessages_hidesHiddenStatusUnlessAdultContentAllowed() {
        val hidden = message(id = 1L, originalText = "Hidden source", status = TranslationStatus.Hidden)
        val visible = message(id = 2L, originalText = "Visible source", status = TranslationStatus.Failed)

        val blocked = ChatMessageVisibilityPolicy.baseVisibleMessages(
            messages = listOf(hidden, visible),
            translatedOnly = false,
            allowAdultContent = false,
            hiddenHashes = emptySet()
        )
        val allowed = ChatMessageVisibilityPolicy.baseVisibleMessages(
            messages = listOf(hidden, visible),
            translatedOnly = false,
            allowAdultContent = true,
            hiddenHashes = emptySet()
        )

        assertEquals(listOf(2L), blocked.map { it.id })
        assertEquals(listOf(2L, 1L), allowed.map { it.id })
    }

    @Test
    fun baseVisibleMessages_hidesLocallyBlacklistedSourceHash() {
        val hiddenByHash = message(id = 1L, originalText = "Locally blacklisted source")
        val visible = message(id = 2L, originalText = "Different source")
        val hiddenHashes = setOf(ContentNormalizer.contentHash(hiddenByHash.originalText))

        val result = ChatMessageVisibilityPolicy.baseVisibleMessages(
            messages = listOf(hiddenByHash, visible),
            translatedOnly = false,
            allowAdultContent = false,
            hiddenHashes = hiddenHashes
        )

        assertTrue(ChatMessageVisibilityPolicy.isHiddenByLocalPolicy(hiddenByHash, hiddenHashes))
        assertFalse(ChatMessageVisibilityPolicy.isHiddenByLocalPolicy(visible, hiddenHashes))
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun baseVisibleMessages_keepsUserBlacklistHiddenWhenAdultContentAllowed() {
        val hiddenByHash = message(id = 1L, originalText = "Globally hidden source")
        val visible = message(id = 2L, originalText = "Different source")
        val hiddenHashes = setOf(ContentNormalizer.contentHash(hiddenByHash.originalText))

        val result = ChatMessageVisibilityPolicy.baseVisibleMessages(
            messages = listOf(hiddenByHash, visible),
            translatedOnly = false,
            allowAdultContent = true,
            hiddenHashes = hiddenHashes
        )

        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun canHideByContent_requiresSourceTextForGlobalSimilarity() {
        val text = message(id = 1L, originalText = "Hide similar content")
        val mediaOnly = message(id = 2L, originalText = "", kind = MessageKind.Video)

        assertTrue(ChatMessageVisibilityPolicy.canHideByContent(text))
        assertFalse(ChatMessageVisibilityPolicy.canHideByContent(mediaOnly))
    }

    @Test
    fun baseVisibleMessages_keepsTelegramRestrictedNoticeEvenWhenHidden() {
        val restrictedNotice = message(
            id = 1L,
            originalText = "This message can't be displayed because it violated Telegram Terms of Service.",
            status = TranslationStatus.Hidden
        )

        val result = ChatMessageVisibilityPolicy.baseVisibleMessages(
            messages = listOf(restrictedNotice),
            translatedOnly = false,
            allowAdultContent = false,
            hiddenHashes = emptySet()
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun baseVisibleMessages_translatedOnlyKeepsReadyAndSourceFallbackMessages() {
        val pending = message(id = 1L, status = TranslationStatus.Pending)
        val readyOlder = message(id = 2L, translatedText = "Ban dich cu", status = TranslationStatus.Ready)
        val readyNewer = message(id = 3L, translatedText = "Ban dich moi", status = TranslationStatus.Ready)
        val failedMissingModel = message(
            id = 4L,
            status = TranslationStatus.Failed,
            failureReason = TranslationFailureReason.MissingModel
        )
        val readyBlankTranslation = message(id = 5L, translatedText = "", status = TranslationStatus.Ready)
        val failedUndetectedLanguage = message(
            id = 6L,
            status = TranslationStatus.Failed,
            failureReason = TranslationFailureReason.UndetectedLanguage
        )
        val failedNonTranslatable = message(
            id = 7L,
            originalText = "https://t.me/example",
            status = TranslationStatus.Failed,
            failureReason = TranslationFailureReason.NonTranslatable
        )

        val result = ChatMessageVisibilityPolicy.baseVisibleMessages(
            messages = listOf(
                pending,
                readyOlder,
                readyNewer,
                failedMissingModel,
                readyBlankTranslation,
                failedUndetectedLanguage,
                failedNonTranslatable
            ),
            translatedOnly = true,
            allowAdultContent = false,
            hiddenHashes = emptySet()
        )

        assertEquals(listOf(7L, 6L, 5L, 4L, 3L, 2L, 1L), result.map { it.id })
        assertEquals("Original English", MessagePrivacyPolicy.readableText(failedMissingModel))
        assertEquals("Original English", MessagePrivacyPolicy.readableText(readyBlankTranslation))
        assertEquals("Original English", MessagePrivacyPolicy.readableText(failedUndetectedLanguage))
        assertEquals("https://t.me/example", MessagePrivacyPolicy.readableText(failedNonTranslatable))
    }

    private fun message(
        id: Long,
        originalText: String = "Original English",
        translatedText: String = "",
        status: TranslationStatus = TranslationStatus.Failed,
        failureReason: TranslationFailureReason = TranslationFailureReason.None,
        kind: MessageKind = MessageKind.Text
    ): TelegramMessage {
        return TelegramMessage(
            chatId = 1L,
            id = id,
            senderId = "user:$id",
            chatTitle = "Test chat",
            author = "Tester",
            originalText = originalText,
            translatedText = translatedText,
            translationStatus = status,
            translationFailureReason = failureReason,
            detectedLanguage = "en",
            kind = kind,
            mediaSizeMb = 0,
            timestamp = "10:00"
        )
    }
}
