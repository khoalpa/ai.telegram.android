package ai.telegram.android

import ai.telegram.android.data.TelegramChat
import ai.telegram.android.ui.AiTelegramTheme
import ai.telegram.android.ui.AppThemeMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@ComposeUiConnectedTest
class ChatScreenTapTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chatRowTap_callsOnSelectChat() {
        val targetChat = chat(id = 101L, title = "Alpha QA")
        var selectedChatId: Long? = null

        composeRule.setContent {
            AiTelegramTheme(themeMode = AppThemeMode.Light) {
                ChatScreen(
                    chats = listOf(targetChat, chat(id = 202L, title = "Beta QA")),
                    selectedChatId = null,
                    messages = emptyList(),
                    translatedOnly = false,
                    allowAdultContent = true,
                    autoPlayVideo = false,
                    videoSubtitlesEnabled = false,
                    videoSourceLanguage = VideoSourceLanguage.Auto,
                    videoSubtitleColor = VideoSubtitleColor.Yellow,
                    onSelectChat = { selectedChatId = it.id },
                    onBackToChats = {},
                    hiddenHashes = emptySet(),
                    onCurrentMessageViewed = {},
                    onOpenCache = {},
                    onSendMessage = { _, _, _ -> },
                    onSendReplyMessage = { _, _, _, _ -> },
                    onSendMedia = { _, _, _, _ -> },
                    onSendPoll = { _, _, _, _, _, _ -> },
                    onSendContact = { _, _, _, _, _ -> },
                    onSetDraft = { _, _ -> },
                    onClearDraft = {},
                    onEditMessage = { _, _ -> },
                    onDeleteMessage = {},
                    onForwardMessage = { _, _ -> },
                    onDeleteMessages = { _, _ -> },
                    onForwardMessages = { _, _, _ -> },
                    onPinMessage = {},
                    onUnpinMessage = {},
                    onReactToMessage = { _, _ -> },
                    onLoadOlderMessages = { _, _ -> },
                    onRefreshChats = {},
                    onOpenTelegramLink = {},
                    onDownloadMedia = { _, _ -> },
                    onRetryTranslation = {},
                    onRestartMediaDownload = {},
                    onPendingShareConsumed = {},
                    onHide = {},
                    onUnhide = {}
                )
            }
        }

        composeRule.onNodeWithTag("chat_row_101")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(101L, selectedChatId)
        }
    }

    private fun chat(id: Long, title: String): TelegramChat {
        return TelegramChat(
            id = id,
            title = title,
            type = "private",
            unreadCount = 0,
            lastMessagePreview = "Preview",
            updatedAtMillis = id
        )
    }

}
