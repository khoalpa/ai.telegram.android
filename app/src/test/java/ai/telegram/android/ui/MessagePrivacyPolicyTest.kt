package ai.telegram.android.ui

import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TranslationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePrivacyPolicyTest {
    @Test
    fun readableSearch_doesNotMatchOriginalSourceText() {
        val message = message(
            originalText = "Secret source phrase",
            translatedText = "Ban dich tieng Viet"
        )

        assertFalse(MessagePrivacyPolicy.matchesReadableQuery(message, "source phrase"))
        assertTrue(MessagePrivacyPolicy.matchesReadableQuery(message, "tieng Viet"))
    }

    @Test
    fun sourceText_isRenderableWhenTranslationIsNotAvailable() {
        assertTrue(MessagePrivacyPolicy.shouldRenderSourceText(TranslationStatus.Pending))
        assertTrue(MessagePrivacyPolicy.shouldRenderSourceText(TranslationStatus.Translating))
        assertTrue(MessagePrivacyPolicy.shouldRenderSourceText(TranslationStatus.Failed))
        assertFalse(MessagePrivacyPolicy.shouldRenderSourceText(TranslationStatus.Ready))
        assertFalse(MessagePrivacyPolicy.shouldRenderSourceText(TranslationStatus.Hidden))
    }

    @Test
    fun readableSearch_matchesOriginalSourceTextWhenTranslationFailed() {
        val message = message(
            originalText = "Original source phrase",
            translatedText = "",
            status = TranslationStatus.Failed
        )

        assertEquals("Original source phrase", MessagePrivacyPolicy.readableText(message))
        assertTrue(MessagePrivacyPolicy.matchesReadableQuery(message, "source phrase"))
    }

    @Test
    fun readableText_fallsBackToSourceWhenReadyTranslationMatchesForeignSource() {
        val message = message(
            originalText = "Original English",
            translatedText = "Original English",
            detectedLanguage = "en"
        )

        assertEquals("", MessagePrivacyPolicy.readableTranslatedText(message))
        assertEquals("Original English", MessagePrivacyPolicy.readableText(message))
        assertTrue(MessagePrivacyPolicy.matchesReadableQuery(message, "Original English"))
    }

    @Test
    fun readableTranslatedText_allowsAlreadyTargetLanguageText() {
        val message = message(
            originalText = "Noi dung tieng Viet",
            translatedText = "Noi dung tieng Viet",
            detectedLanguage = "vi"
        )

        assertEquals("Noi dung tieng Viet", MessagePrivacyPolicy.readableTranslatedText(message))
        assertTrue(MessagePrivacyPolicy.matchesReadableQuery(message, "tieng Viet"))
    }

    @Test
    fun hiddenMessages_doNotExposeOriginalOrTranslatedTextAsReadableText() {
        val message = message(
            originalText = "Sensitive original source",
            translatedText = "Sensitive translated text",
            status = TranslationStatus.Hidden
        )

        assertEquals("", MessagePrivacyPolicy.readableTranslatedText(message))
        assertEquals("", MessagePrivacyPolicy.readableText(message))
        assertFalse(MessagePrivacyPolicy.matchesReadableQuery(message, "Sensitive original"))
        assertFalse(MessagePrivacyPolicy.matchesReadableQuery(message, "Sensitive translated"))
    }

    @Test
    fun telegramRestrictedNotice_matchesOnlyWhenTelegramAndViolationContextArePresent() {
        val restricted = message(
            originalText = "This message can't be displayed because it violated Telegram Terms of Service.",
            status = TranslationStatus.Hidden
        )
        val genericCannotDisplay = message(
            originalText = "This file can't be displayed on this device.",
            status = TranslationStatus.Hidden
        )

        assertTrue(MessagePrivacyPolicy.isTelegramRestrictedNotice(restricted))
        assertFalse(MessagePrivacyPolicy.isTelegramRestrictedNotice(genericCannotDisplay))
    }

    private fun message(
        originalText: String = "Original English",
        translatedText: String = "",
        status: TranslationStatus = TranslationStatus.Ready,
        detectedLanguage: String = "en"
    ): TelegramMessage {
        return TelegramMessage(
            chatId = 1L,
            id = 1L,
            senderId = "user:1",
            chatTitle = "Test chat",
            author = "Tester",
            originalText = originalText,
            translatedText = translatedText,
            translationStatus = status,
            detectedLanguage = detectedLanguage,
            kind = MessageKind.Video,
            mediaSizeMb = 1,
            timestamp = "10:00"
        )
    }
}
