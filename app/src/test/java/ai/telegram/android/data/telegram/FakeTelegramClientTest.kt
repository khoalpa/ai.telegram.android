package ai.telegram.android.data.telegram

import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.TelegramChat
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TranslationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeTelegramClientTest {
    @Test
    fun fakeClient_drivesLoginChatAndMessageFlow() {
        val statuses = mutableListOf<TdLibStatus>()
        val chats = mutableListOf<TelegramChat>()
        val messages = mutableListOf<TelegramMessage>()
        val client = FakeTelegramClient(
            onStatus = statuses::add,
            onChat = chats::add,
            onMessage = messages::add
        )

        client.start()
        client.setPhoneNumber("+84900000000")
        client.checkCode("12345")

        assertEquals(
            listOf(
                TdLibStatus.WaitingForPhoneNumber,
                TdLibStatus.WaitingForCode(canResendCode = true),
                TdLibStatus.Ready
            ),
            statuses
        )
        assertEquals("AI News", chats.single().title)
        assertEquals("New model released today.", messages.single().originalText)
    }

    @Test
    fun fakeClient_recordsSendMessageRequestAndEmitsOutgoingMessage() {
        val messages = mutableListOf<TelegramMessage>()
        val client = FakeTelegramClient(onMessage = messages::add)

        client.sendTextMessage(chatId = 42L, text = "Hello")

        assertEquals(FakeTelegramClient.SentMessage(42L, "Hello"), client.sentMessages.single())
        assertEquals("Hello", messages.single().originalText)
    }

    @Test
    fun fakeClient_recordsMessageActions() {
        val client = FakeTelegramClient()

        client.sendReplyTextMessage(chatId = 42L, replyToMessageId = 1L, text = "Reply")
        client.sendMediaMessage(chatId = 42L, localPath = "D:\\tmp\\photo.jpg", kind = MessageKind.Image, caption = "Photo")
        client.editTextMessage(chatId = 42L, messageId = 1L, text = "Edited")
        client.deleteMessages(chatId = 42L, messageIds = listOf(1L), revoke = true)
        client.forwardMessages(toChatId = 99L, fromChatId = 42L, messageIds = listOf(1L))
        client.pinMessage(chatId = 42L, messageId = 1L)
        client.addMessageReaction(chatId = 42L, messageId = 1L, emoji = "\uD83D\uDC4D")
        client.startBot(botUserId = 7L, chatId = 42L, parameter = "hello")
        client.requestInlineBotResults(botUserId = 7L, chatId = 42L, query = "cat", offset = "")
        client.sendInlineBotResult(chatId = 42L, queryId = 99L, resultId = "r1")
        client.clickBotCallbackButton(chatId = 42L, messageId = 1L, payload = "next")
        client.updateProfile(firstName = "Ada", lastName = "Lovelace", bio = "Math", username = "ada")
        client.setPrivacySetting(TelegramPrivacySetting.LastSeen, TelegramPrivacyPreset.Contacts)
        client.terminateSession(123L)
        client.loadPremiumLimit(TelegramPremiumLimitType.BioLength)
        client.loadStarTransactions(offset = "next", limit = 25)
        client.createBusinessChatLink("Hello business")

        assertEquals(FakeTelegramClient.ReplyMessage(42L, 1L, "Reply"), client.replyMessages.single())
        assertEquals(
            FakeTelegramClient.MediaMessage(42L, "D:\\tmp\\photo.jpg", MessageKind.Image, "Photo"),
            client.mediaMessages.single()
        )
        assertEquals(FakeTelegramClient.EditMessage(42L, 1L, "Edited"), client.editedMessages.single())
        assertEquals(FakeTelegramClient.DeleteMessages(42L, listOf(1L), true), client.deletedMessages.single())
        assertEquals(FakeTelegramClient.ForwardMessages(99L, 42L, listOf(1L)), client.forwardedMessages.single())
        assertEquals(FakeTelegramClient.PinnedMessage(42L, 1L, true, false), client.pinnedMessages.single())
        assertEquals(FakeTelegramClient.MessageReaction(42L, 1L, "\uD83D\uDC4D", false), client.reactions.single())
        assertEquals(FakeTelegramClient.BotStart(7L, 42L, "hello"), client.botStarts.single())
        assertEquals(FakeTelegramClient.InlineQuery(7L, 42L, "cat", ""), client.inlineQueries.single())
        assertEquals(FakeTelegramClient.InlineResult(42L, 99L, "r1", false), client.inlineResults.single())
        assertEquals(FakeTelegramClient.CallbackClick(42L, 1L, "next"), client.callbackClicks.single())
        assertEquals(FakeTelegramClient.ProfileUpdate("Ada", "Lovelace", "Math", "ada"), client.profileUpdates.single())
        assertEquals(
            FakeTelegramClient.PrivacyUpdate(TelegramPrivacySetting.LastSeen, TelegramPrivacyPreset.Contacts),
            client.privacyUpdates.single()
        )
        assertEquals(123L, client.terminatedSessions.single())
        assertEquals(TelegramPremiumLimitType.BioLength, client.premiumLimitRequests.single())
        assertEquals(FakeTelegramClient.StarTransactionRequest("next", 25), client.starTransactionRequests.single())
        assertEquals("Hello business", client.businessLinkMessages.single())
    }

    private class FakeTelegramClient(
        private val onStatus: (TdLibStatus) -> Unit = {},
        private val onChat: (TelegramChat) -> Unit = {},
        private val onMessage: (TelegramMessage) -> Unit = {}
    ) : TelegramClient {
        data class SentMessage(val chatId: Long, val text: String)
        data class ReplyMessage(val chatId: Long, val replyToMessageId: Long, val text: String)
        data class MediaMessage(val chatId: Long, val localPath: String, val kind: MessageKind, val caption: String)
        data class PollMessage(
            val chatId: Long,
            val question: String,
            val options: List<String>,
            val isAnonymous: Boolean,
            val allowMultipleAnswers: Boolean
        )
        data class ContactMessage(
            val chatId: Long,
            val firstName: String,
            val lastName: String,
            val phoneNumber: String,
            val userId: Long
        )
        data class EditMessage(val chatId: Long, val messageId: Long, val text: String)
        data class DeleteMessages(val chatId: Long, val messageIds: List<Long>, val revoke: Boolean)
        data class ForwardMessages(val toChatId: Long, val fromChatId: Long, val messageIds: List<Long>)
        data class PinnedMessage(
            val chatId: Long,
            val messageId: Long,
            val disableNotification: Boolean,
            val onlyForSelf: Boolean
        )
        data class MessageReaction(val chatId: Long, val messageId: Long, val emoji: String, val isBig: Boolean)
        data class BotStart(val botUserId: Long, val chatId: Long, val parameter: String)
        data class InlineQuery(val botUserId: Long, val chatId: Long, val query: String, val offset: String)
        data class InlineResult(val chatId: Long, val queryId: Long, val resultId: String, val hideViaBot: Boolean)
        data class CallbackClick(val chatId: Long, val messageId: Long, val payload: String)
        data class ProfileUpdate(val firstName: String, val lastName: String, val bio: String, val username: String)
        data class PrivacyUpdate(val setting: TelegramPrivacySetting, val preset: TelegramPrivacyPreset)
        data class StarTransactionRequest(val offset: String, val limit: Int)

        val sentMessages = mutableListOf<SentMessage>()
        val replyMessages = mutableListOf<ReplyMessage>()
        val mediaMessages = mutableListOf<MediaMessage>()
        val pollMessages = mutableListOf<PollMessage>()
        val contactMessages = mutableListOf<ContactMessage>()
        val editedMessages = mutableListOf<EditMessage>()
        val deletedMessages = mutableListOf<DeleteMessages>()
        val forwardedMessages = mutableListOf<ForwardMessages>()
        val pinnedMessages = mutableListOf<PinnedMessage>()
        val reactions = mutableListOf<MessageReaction>()
        val botStarts = mutableListOf<BotStart>()
        val inlineQueries = mutableListOf<InlineQuery>()
        val inlineResults = mutableListOf<InlineResult>()
        val callbackClicks = mutableListOf<CallbackClick>()
        val profileUpdates = mutableListOf<ProfileUpdate>()
        val privacyUpdates = mutableListOf<PrivacyUpdate>()
        val terminatedSessions = mutableListOf<Long>()
        val premiumLimitRequests = mutableListOf<TelegramPremiumLimitType>()
        val starTransactionRequests = mutableListOf<StarTransactionRequest>()
        val businessLinkMessages = mutableListOf<String>()

        override fun start() {
            onStatus(TdLibStatus.WaitingForPhoneNumber)
        }

        override fun close() {
            onStatus(TdLibStatus.Closed)
        }

        override fun setPhoneNumber(phoneNumber: String) {
            onStatus(TdLibStatus.WaitingForCode(canResendCode = true))
        }

        override fun checkCode(code: String) {
            onStatus(TdLibStatus.Ready)
            onChat(sampleChat)
            onMessage(sampleMessage)
        }

        override fun checkPassword(password: String) {
            onStatus(TdLibStatus.Ready)
            onChat(sampleChat)
            onMessage(sampleMessage)
        }

        override fun resendAuthenticationCode() {
            onStatus(TdLibStatus.WaitingForCode(canResendCode = false))
        }

        override fun logOut() {
            onStatus(TdLibStatus.Closed)
        }

        override fun loadMainChatList(limit: Int) {
            onChat(sampleChat)
        }

        override fun loadChat(chatId: Long) {
            onChat(sampleChat.copy(id = chatId))
        }

        override fun loadChatHistory(chatId: Long, fromMessageId: Long, limit: Int) {
            onMessage(sampleMessage.copy(chatId = chatId))
        }

        override fun loadContacts(limit: Int) = Unit
        override fun searchChats(query: String, limit: Int) = Unit
        override fun searchPublicChats(query: String) = Unit
        override fun joinChannel(usernameOrLink: String) = Unit
        override fun openTelegramLink(link: String) = Unit

        override fun sendTextMessage(chatId: Long, text: String, options: MessageSendOptions) {
            sentMessages += SentMessage(chatId, text)
            onMessage(sampleMessage.copy(chatId = chatId, id = 2L, originalText = text))
        }

        override fun sendReplyTextMessage(
            chatId: Long,
            replyToMessageId: Long,
            text: String,
            options: MessageSendOptions
        ) {
            replyMessages += ReplyMessage(chatId, replyToMessageId, text)
        }

        override fun sendMediaMessage(
            chatId: Long,
            localPath: String,
            kind: MessageKind,
            caption: String,
            options: MessageSendOptions
        ) {
            mediaMessages += MediaMessage(chatId, localPath, kind, caption)
        }

        override fun sendPollMessage(
            chatId: Long,
            question: String,
            options: List<String>,
            isAnonymous: Boolean,
            allowMultipleAnswers: Boolean,
            sendOptions: MessageSendOptions
        ) {
            pollMessages += PollMessage(chatId, question, options, isAnonymous, allowMultipleAnswers)
        }

        override fun sendContactMessage(
            chatId: Long,
            firstName: String,
            lastName: String,
            phoneNumber: String,
            userId: Long,
            options: MessageSendOptions
        ) {
            contactMessages += ContactMessage(chatId, firstName, lastName, phoneNumber, userId)
        }

        override fun editTextMessage(chatId: Long, messageId: Long, text: String) {
            editedMessages += EditMessage(chatId, messageId, text)
        }

        override fun deleteMessages(chatId: Long, messageIds: List<Long>, revoke: Boolean) {
            deletedMessages += DeleteMessages(chatId, messageIds, revoke)
        }

        override fun forwardMessages(toChatId: Long, fromChatId: Long, messageIds: List<Long>) {
            forwardedMessages += ForwardMessages(toChatId, fromChatId, messageIds)
        }

        override fun pinMessage(
            chatId: Long,
            messageId: Long,
            disableNotification: Boolean,
            onlyForSelf: Boolean
        ) {
            pinnedMessages += PinnedMessage(chatId, messageId, disableNotification, onlyForSelf)
        }

        override fun unpinMessage(chatId: Long, messageId: Long) = Unit

        override fun unpinAllChatMessages(chatId: Long) = Unit

        override fun addMessageReaction(chatId: Long, messageId: Long, emoji: String, isBig: Boolean) {
            reactions += MessageReaction(chatId, messageId, emoji, isBig)
        }

        override fun createPrivateChat(userId: Long) = Unit
        override fun createSecretChat(userId: Long) = Unit
        override fun createCall(userId: Long, isVideo: Boolean) = Unit
        override fun acceptCall(callId: Int) = Unit
        override fun discardCall(callId: Int, isDisconnected: Boolean) = Unit
        override fun sendCallSignalingData(callId: Int, data: ByteArray) = Unit
        override fun loadActiveStories(chatId: Long) = Unit
        override fun loadStory(chatId: Long, storyId: Int) = Unit
        override fun viewStory(chatId: Long, storyId: Int) = Unit
        override fun postStory(chatId: Long, localPath: String, kind: MessageKind, caption: String) = Unit
        override fun deleteStory(chatId: Long, storyId: Int) = Unit
        override fun searchBot(usernameOrLink: String) = Unit
        override fun startBot(botUserId: Long, chatId: Long, parameter: String) {
            botStarts += BotStart(botUserId, chatId, parameter)
        }
        override fun loadBotCommands(botUserId: Long) = Unit
        override fun loadBotMenuButton(botUserId: Long) = Unit
        override fun requestInlineBotResults(botUserId: Long, chatId: Long, query: String, offset: String) {
            inlineQueries += InlineQuery(botUserId, chatId, query, offset)
        }
        override fun sendInlineBotResult(chatId: Long, queryId: Long, resultId: String, hideViaBot: Boolean) {
            inlineResults += InlineResult(chatId, queryId, resultId, hideViaBot)
        }
        override fun clickBotCallbackButton(chatId: Long, messageId: Long, payload: String) {
            callbackClicks += CallbackClick(chatId, messageId, payload)
        }
        override fun openBotWebApp(botUserId: Long, chatId: Long, url: String) = Unit
        override fun sendWebAppData(botUserId: Long, buttonText: String, data: String) = Unit
        override fun updateProfile(firstName: String, lastName: String, bio: String, username: String) {
            profileUpdates += ProfileUpdate(firstName, lastName, bio, username)
        }
        override fun setProfilePhoto(localPath: String) = Unit
        override fun loadPrivacySetting(setting: TelegramPrivacySetting) = Unit
        override fun setPrivacySetting(setting: TelegramPrivacySetting, preset: TelegramPrivacyPreset) {
            privacyUpdates += PrivacyUpdate(setting, preset)
        }
        override fun loadActiveSessions() = Unit
        override fun terminateSession(sessionId: Long) {
            terminatedSessions += sessionId
        }
        override fun terminateAllOtherSessions() = Unit
        override fun loadPremiumFeatures() = Unit
        override fun viewPremiumFeature(feature: TelegramPremiumFeature) = Unit
        override fun loadPremiumLimit(limitType: TelegramPremiumLimitType) {
            premiumLimitRequests += limitType
        }
        override fun loadStarBalance() = Unit
        override fun loadStarTransactions(offset: String, limit: Int) {
            starTransactionRequests += StarTransactionRequest(offset, limit)
        }
        override fun loadBusinessChatLinks() = Unit
        override fun createBusinessChatLink(message: String) {
            businessLinkMessages += message
        }
        override fun deleteBusinessChatLink(link: String) = Unit
        override fun setUserBlocked(userId: Long, blocked: Boolean) = Unit
        override fun addContact(userId: Long, firstName: String, lastName: String, phoneNumber: String) = Unit
        override fun deleteContact(userId: Long) = Unit
        override fun createBasicGroupChat(userIds: List<Long>, title: String) = Unit
        override fun createChannel(title: String, description: String) = Unit
        override fun createForumGroup(title: String, description: String) = Unit
        override fun setChatTitle(chatId: Long, title: String) = Unit
        override fun setChatDescription(chatId: Long, description: String) = Unit
        override fun setChatMessageAutoDeleteTime(chatId: Long, seconds: Int) = Unit
        override fun setChatSlowModeDelay(chatId: Long, seconds: Int) = Unit
        override fun setChatPermissions(chatId: Long, preset: TelegramChatPermissionPreset) = Unit
        override fun createChatInviteLink(chatId: Long, name: String) = Unit
        override fun revokeChatInviteLink(chatId: Long, inviteLink: String) = Unit
        override fun addChatMember(chatId: Long, userId: Long, forwardLimit: Int) = Unit
        override fun removeChatMember(chatId: Long, userId: Long) = Unit
        override fun banChatMember(chatId: Long, userId: Long, bannedUntilDate: Int) = Unit
        override fun unbanChatMember(chatId: Long, userId: Long) = Unit
        override fun promoteChatMember(chatId: Long, userId: Long, customTitle: String) = Unit
        override fun demoteChatMember(chatId: Long, userId: Long) = Unit
        override fun setChatDraftMessage(chatId: Long, text: String) = Unit
        override fun clearChatDraftMessage(chatId: Long) = Unit
        override fun pinChat(chatId: Long, pinned: Boolean) = Unit
        override fun muteChat(chatId: Long, muteForSeconds: Int) = Unit
        override fun unmuteChat(chatId: Long) = Unit
        override fun archiveChat(chatId: Long, archived: Boolean) = Unit
        override fun markChatRead(chatId: Long, limit: Int) = Unit
        override fun markChatUnread(chatId: Long, markedUnread: Boolean) = Unit
        override fun clearChatHistory(chatId: Long, revoke: Boolean) = Unit
        override fun leaveChat(chatId: Long) = Unit
        override fun loadMessage(chatId: Long, messageId: Long) = Unit
        override fun downloadMessageMedia(chatId: Long, messageId: Long, priority: Int, limitBytes: Int) = Unit
        override fun downloadFile(fileId: Int, priority: Int, limitBytes: Int) = Unit
        override fun cancelDownloadFile(fileId: Int, onlyIfPending: Boolean) = Unit

        private companion object {
            val sampleChat = TelegramChat(
                id = 42L,
                title = "AI News",
                type = "channel",
                unreadCount = 1,
                lastMessagePreview = "Pending translation",
                updatedAtMillis = 1L
            )
            val sampleMessage = TelegramMessage(
                chatId = 42L,
                id = 1L,
                senderId = "chat:42",
                chatTitle = "AI News",
                author = "AI News",
                originalText = "New model released today.",
                translatedText = "",
                translationStatus = TranslationStatus.Pending,
                detectedLanguage = "",
                kind = MessageKind.Text,
                mediaSizeMb = 0,
                timestamp = "10:00"
            )
        }
    }
}
