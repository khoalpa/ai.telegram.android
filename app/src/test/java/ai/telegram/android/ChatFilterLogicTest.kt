package ai.telegram.android

import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.TelegramChat
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TranslationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFilterLogicTest {
    @Test
    fun matchesFolderFilter_classifiesChatTypes() {
        assertTrue(chat(type = "channel").matchesFolderFilter(ChatFolderFilter.Channels))
        assertTrue(chat(type = "supergroup").matchesFolderFilter(ChatFolderFilter.Groups))
        assertTrue(chat(type = "secret").matchesFolderFilter(ChatFolderFilter.Secret))
        assertTrue(chat(type = "private").matchesFolderFilter(ChatFolderFilter.Private))
    }

    @Test
    fun visibleLinkHelpers_useReadableMessageSources() {
        val message = message(
            originalText = "Source text",
            translatedText = "Doc tiep tai https://t.me/example",
            translationStatus = TranslationStatus.Ready
        )

        assertTrue(message.matchesContentFilter(ChatContentFilter.Links))
        assertEquals("https://t.me/example", message.firstVisibleLink())
    }

    @Test
    fun matchesDateFilter_excludesOldMessages() {
        val oldMessage = message(
            receivedAtMillis = System.currentTimeMillis() - 31L * 24L * 60L * 60L * 1000L
        )

        assertFalse(oldMessage.matchesDateFilter(ChatDateFilter.Last30Days))
        assertTrue(oldMessage.matchesDateFilter(ChatDateFilter.All))
    }

    @Test
    fun matchesContentFilter_distinguishesSharedMediaKinds() {
        assertTrue(message(kind = MessageKind.Audio).matchesContentFilter(ChatContentFilter.Audio))
        assertTrue(message(kind = MessageKind.Voice).matchesContentFilter(ChatContentFilter.Voice))
        assertTrue(message(kind = MessageKind.Sticker).matchesContentFilter(ChatContentFilter.Stickers))
        assertTrue(message(kind = MessageKind.VideoNote).matchesContentFilter(ChatContentFilter.PhotosVideos))
        assertFalse(message(kind = MessageKind.File).matchesContentFilter(ChatContentFilter.Media))
        assertTrue(message(kind = MessageKind.File).matchesContentFilter(ChatContentFilter.Files))
    }

    private fun chat(type: String): TelegramChat {
        return TelegramChat(
            id = 1L,
            title = "Chat",
            type = type,
            unreadCount = 0,
            lastMessagePreview = "",
            updatedAtMillis = 0L
        )
    }

    private fun message(
        originalText: String = "",
        translatedText: String = "",
        translationStatus: TranslationStatus = TranslationStatus.Ready,
        receivedAtMillis: Long = System.currentTimeMillis(),
        kind: MessageKind = MessageKind.Text
    ): TelegramMessage {
        return TelegramMessage(
            chatId = 1L,
            id = 1L,
            senderId = "user:1",
            chatTitle = "Chat",
            author = "Sender",
            originalText = originalText,
            translatedText = translatedText,
            translationStatus = translationStatus,
            detectedLanguage = "en",
            kind = kind,
            mediaSizeMb = 0,
            timestamp = "",
            receivedAtMillis = receivedAtMillis
        )
    }
}
