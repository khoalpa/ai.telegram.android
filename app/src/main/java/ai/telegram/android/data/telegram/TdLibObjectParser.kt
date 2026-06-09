package ai.telegram.android.data.telegram

import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.MessageSyncState
import ai.telegram.android.data.TelegramChat
import ai.telegram.android.data.telegram.TelegramBotCommand
import ai.telegram.android.data.telegram.TelegramBotMenuButton
import ai.telegram.android.data.telegram.TelegramCall
import ai.telegram.android.data.telegram.TelegramCallProtocol
import ai.telegram.android.data.telegram.TelegramCallServer
import ai.telegram.android.data.telegram.TelegramCallState
import ai.telegram.android.data.telegram.TelegramInlineBotResult
import ai.telegram.android.data.telegram.TelegramInlineBotResults
import ai.telegram.android.data.telegram.TelegramActiveSession
import ai.telegram.android.data.telegram.TelegramBusinessChatLink
import ai.telegram.android.data.telegram.TelegramPremiumFeatureInfo
import ai.telegram.android.data.telegram.TelegramPremiumLimitSummary
import ai.telegram.android.data.telegram.TelegramPremiumLimitType
import ai.telegram.android.data.telegram.TelegramPrivacyPreset
import ai.telegram.android.data.telegram.TelegramPrivacyRuleSummary
import ai.telegram.android.data.telegram.TelegramPrivacySetting
import ai.telegram.android.data.telegram.TelegramStarBalance
import ai.telegram.android.data.telegram.TelegramStarTransaction
import ai.telegram.android.data.telegram.TelegramStory
import ai.telegram.android.data.telegram.TelegramStoryState
import ai.telegram.android.data.telegram.TelegramWebAppUrl
import ai.telegram.android.data.TelegramMediaFile
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TelegramSender
import ai.telegram.android.data.TranslationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class TdLibObjectParser(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onSender: (TelegramSender) -> Unit = {}
) {
    fun parseMessagesResult(result: Any?): List<TelegramMessage> {
        if (result?.javaClass?.simpleName != "Messages") return emptyList()
        val messages = result.field("messages") as? Array<*> ?: return emptyList()
        return messages.mapNotNull { parseMessage(it) }
    }

    fun parseChat(chat: Any?): TelegramChat? {
        if (chat == null) return null
        val id = (chat.field("id") as? Number)?.toLong() ?: return null
        val title = chat.field("title") as? String ?: "Telegram"
        val unreadCount = (chat.field("unreadCount") as? Number)?.toInt() ?: 0
        val type = parseChatType(chat.field("type"))
        val lastMessage = parseMessage(chat.field("lastMessage"))
        onSender(
            TelegramSender(
                id = "chat:$id",
                displayName = title,
                type = type,
                updatedAtMillis = nowMillis()
            )
        )
        return TelegramChat(
            id = id,
            title = title,
            type = type,
            unreadCount = unreadCount,
            lastMessagePreview = lastMessage.previewText(),
            updatedAtMillis = nowMillis()
        )
    }

    fun parseChatLastMessage(update: Any): TelegramChat? {
        val chatId = (update.field("chatId") as? Number)?.toLong() ?: return null
        val lastMessage = parseMessage(update.field("lastMessage"))
        return TelegramChat(
            id = chatId,
            title = "",
            type = "chat",
            unreadCount = 0,
            lastMessagePreview = lastMessage.previewText(),
            updatedAtMillis = nowMillis()
        )
    }

    fun parseMessage(message: Any?): TelegramMessage? {
        if (message == null) return null
        val content = message.field("content") ?: return null
        val media = parseMedia(content)
        val text = extractText(content)
        if (text.isBlank() && media.kind == MessageKind.Text) return null
        val id = (message.field("id") as? Number)?.toLong() ?: return null
        val chatId = (message.field("chatId") as? Number)?.toLong() ?: 0L
        val senderId = parseSenderId(message.field("senderId")).ifBlank { "chat:$chatId" }
        val date = (message.field("date") as? Number)?.toLong()?.times(1000) ?: nowMillis()
        return TelegramMessage(
            chatId = chatId,
            id = id,
            senderId = senderId,
            chatTitle = "Telegram",
            author = "Telegram user",
            originalText = text,
            translatedText = "",
            translationStatus = if (text.isBlank()) TranslationStatus.Ready else TranslationStatus.Pending,
            detectedLanguage = "",
            kind = media.kind,
            mediaSizeMb = media.sizeMb,
            mediaFileId = media.fileId,
            mediaLocalPath = media.localPath,
            mediaMimeType = media.mimeType,
            mediaFileName = media.fileName,
            mediaThumbnailFileId = media.thumbnailFileId,
            mediaThumbnailLocalPath = media.thumbnailLocalPath,
            mediaDownloadedPrefixBytes = media.downloadedPrefixBytes,
            timestamp = SimpleDateFormat("HH:mm", Locale.forLanguageTag("vi-VN")).format(Date(date)),
            syncState = message.syncState(),
            isOutgoing = message.field("isOutgoing") as? Boolean ?: false,
            isRead = (message.field("isOutgoing") as? Boolean ?: false) && message.field("sendingState") == null,
            isEdited = (message.field("editDate") as? Number)?.toLong()?.let { it > 0L } ?: false,
            isPinned = message.field("isPinned") as? Boolean ?: false,
            receivedAtMillis = date
        )
    }

    fun parseUser(user: Any?): TelegramSender? {
        if (user == null) return null
        val id = (user.field("id") as? Number)?.toLong() ?: return null
        val firstName = user.field("firstName") as? String ?: ""
        val lastName = user.field("lastName") as? String ?: ""
        val username = (user.field("usernames")?.field("activeUsernames") as? Array<*>)?.firstOrNull() as? String
        val displayName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { username ?: "Telegram user" }
        return TelegramSender(
            id = "user:$id",
            displayName = displayName,
            type = "user",
            updatedAtMillis = nowMillis()
        )
    }

    fun parseGroup(group: Any?, type: String): TelegramSender? {
        if (group == null) return null
        val id = (group.field("id") as? Number)?.toLong() ?: return null
        val title = group.field("title") as? String ?: return null
        return TelegramSender(
            id = "$type:$id",
            displayName = title,
            type = type,
            updatedAtMillis = nowMillis()
        )
    }

    fun parseFileUpdate(file: Any?): TelegramMediaFile? {
        val fileId = (file.fieldAny("id", "fileId") as? Number)?.toInt() ?: 0
        if (fileId <= 0) return null
        val sizeBytes = (file.fieldAny("size", "expectedSize") as? Number)?.toLong() ?: 0L
        return TelegramMediaFile(
            id = fileId,
            localPath = file.localPath(),
            sizeMb = tdLibBytesToMb(sizeBytes),
            downloadedPrefixBytes = file.localDownloadedPrefixBytes()
        )
    }

    fun parseContentText(content: Any?): String {
        return content?.let(::extractText).orEmpty()
    }

    fun parseCall(call: Any?): TelegramCall? {
        if (call == null) return null
        val id = (call.field("id") as? Number)?.toInt()?.takeIf { it != 0 } ?: return null
        val state = call.field("state")
        val stateLabel = state?.javaClass?.simpleName.orEmpty()
        val errorMessage = state
            ?.field("error")
            ?.field("message") as? String ?: ""
        val ready = parseCallReady(state)
        return TelegramCall(
            id = id,
            userId = (call.field("userId") as? Number)?.toLong() ?: 0L,
            isOutgoing = call.field("isOutgoing") as? Boolean ?: false,
            isVideo = call.field("isVideo") as? Boolean ?: false,
            state = stateLabel.toTelegramCallState(),
            isCreated = state.fieldAny("isCreated", "is_created") as? Boolean ?: false,
            isReceived = state.fieldAny("isReceived", "is_received") as? Boolean ?: false,
            durationSeconds = (call.field("duration") as? Number)?.toInt() ?: 0,
            stateLabel = stateLabel,
            errorMessage = errorMessage,
            protocol = ready.protocol,
            servers = ready.servers,
            config = ready.config,
            encryptionKey = ready.encryptionKey,
            emojis = ready.emojis,
            allowP2p = ready.allowP2p,
            customParameters = ready.customParameters
        )
    }

    fun parseStoriesResult(result: Any?): List<TelegramStory> {
        if (result == null) return emptyList()
        val chatId = (result.fieldAny("chatId", "storySenderChatId", "senderChatId") as? Number)?.toLong() ?: 0L
        val stories = result.field("stories").asObjectList()
        return stories.mapNotNull { parseStory(it, defaultChatId = chatId) }
    }

    fun parseStory(story: Any?, defaultChatId: Long = 0L): TelegramStory? {
        if (story == null) return null
        val id = (story.fieldAny("id", "storyId") as? Number)?.toInt()?.takeIf { it != 0 } ?: return null
        val chatId = ((story.fieldAny("chatId", "storySenderChatId", "senderChatId") as? Number)?.toLong() ?: 0L)
            .takeIf { it != 0L }
            ?: defaultChatId
        if (chatId == 0L) return null
        val media = parseStoryMedia(story.field("content"))
        val state = story.field("sendingState")
        val stateLabel = state?.javaClass?.simpleName.orEmpty()
        return TelegramStory(
            chatId = chatId,
            id = id,
            senderChatId = ((story.fieldAny("senderChatId", "storySenderChatId") as? Number)?.toLong() ?: 0L)
                .takeIf { it != 0L }
                ?: chatId,
            dateMillis = ((story.field("date") as? Number)?.toLong()?.takeIf { it > 0L }?.times(1000)) ?: nowMillis(),
            caption = (story.field("caption")?.field("text") as? String).orEmpty(),
            kind = media.kind,
            mediaFileId = media.fileId,
            mediaLocalPath = media.localPath,
            mediaThumbnailFileId = media.thumbnailFileId,
            mediaThumbnailLocalPath = media.thumbnailLocalPath,
            isOutgoing = story.field("isOutgoing") as? Boolean ?: false,
            isPinned = story.field("isPinned") as? Boolean ?: false,
            isEdited = story.field("isEdited") as? Boolean ?: false,
            isViewed = story.field("hasViewerViewed") as? Boolean ?: false,
            state = stateLabel.toTelegramStoryState(),
            stateLabel = stateLabel
        )
    }

    fun deletedStory(chatId: Long, storyId: Int): TelegramStory? {
        if (chatId == 0L || storyId == 0) return null
        return TelegramStory(chatId = chatId, id = storyId, state = TelegramStoryState.Deleted)
    }

    fun parseBotCommandsResult(result: Any?, botUserId: Long): List<TelegramBotCommand> {
        val commands = result.field("commands").asObjectList()
        return commands.mapNotNull { command ->
            val name = command.field("command") as? String ?: return@mapNotNull null
            if (name.isBlank()) {
                null
            } else {
                TelegramBotCommand(
                    botUserId = botUserId,
                    command = name,
                    description = command.field("description") as? String ?: ""
                )
            }
        }
    }

    fun parseBotMenuButton(result: Any?, botUserId: Long): TelegramBotMenuButton? {
        if (result == null) return null
        return TelegramBotMenuButton(
            botUserId = botUserId,
            type = result.javaClass.simpleName,
            text = result.field("text") as? String ?: "",
            url = result.field("url") as? String
                ?: result.field("webApp")?.field("url") as? String
                ?: ""
        )
    }

    fun parseInlineBotResults(result: Any?, botUserId: Long): TelegramInlineBotResults {
        return TelegramInlineBotResults(
            botUserId = botUserId,
            queryId = (result.fieldAny("inlineQueryId", "queryId") as? Number)?.toLong() ?: 0L,
            nextOffset = result.field("nextOffset") as? String ?: "",
            results = result.field("results").asObjectList().mapNotNull(::parseInlineBotResult)
        )
    }

    fun parseWebAppUrl(result: Any?, botUserId: Long): TelegramWebAppUrl? {
        val url = result.field("url") as? String ?: return null
        if (url.isBlank()) return null
        return TelegramWebAppUrl(botUserId = botUserId, url = url)
    }

    fun parsePrivacyRulesResult(result: Any?, setting: TelegramPrivacySetting): TelegramPrivacyRuleSummary {
        val firstRule = result.field("rules").asObjectList().firstOrNull()
        val rawRule = firstRule?.javaClass?.simpleName.orEmpty()
        return TelegramPrivacyRuleSummary(
            setting = setting,
            preset = rawRule.toPrivacyPreset(),
            rawRule = rawRule
        )
    }

    fun parseActiveSessions(result: Any?): List<TelegramActiveSession> {
        return result.field("sessions").asObjectList().mapNotNull(::parseActiveSession)
    }

    fun parsePremiumFeatures(result: Any?): List<TelegramPremiumFeatureInfo> {
        return result.field("features").asObjectList().mapNotNull { feature ->
            TelegramPremiumFeatureInfo(
                type = feature?.javaClass?.simpleName.orEmpty(),
                title = feature.field("title") as? String ?: "",
                description = feature.field("description") as? String ?: ""
            ).takeIf { it.type.isNotBlank() || it.title.isNotBlank() }
        }
    }

    fun parsePremiumLimit(result: Any?, type: TelegramPremiumLimitType): TelegramPremiumLimitSummary {
        return TelegramPremiumLimitSummary(
            type = type,
            defaultValue = (result.fieldAny("defaultValue", "limit", "value") as? Number)?.toInt() ?: 0,
            premiumValue = (result.field("premiumValue") as? Number)?.toInt() ?: 0
        )
    }

    fun parseStarBalance(result: Any?): TelegramStarBalance? {
        val amount = (result.fieldAny("starCount", "amount", "count") as? Number)?.toLong() ?: 0L
        val nanoAmount = (result.fieldAny("nanostarCount", "nanoAmount") as? Number)?.toInt() ?: 0
        if (amount == 0L && nanoAmount == 0) return null
        return TelegramStarBalance(amount = amount, nanoAmount = nanoAmount)
    }

    fun parseStarTransactions(result: Any?): List<TelegramStarTransaction> {
        return result.field("transactions").asObjectList().mapNotNull(::parseStarTransaction)
    }

    fun parseBusinessChatLinks(result: Any?): List<TelegramBusinessChatLink> {
        return result.field("links").asObjectList().mapNotNull(::parseBusinessChatLink)
    }

    fun parseBusinessChatLink(result: Any?): TelegramBusinessChatLink? {
        val link = result.field("link") as? String
            ?: result.field("url") as? String
            ?: return null
        if (link.isBlank()) return null
        return TelegramBusinessChatLink(
            link = link,
            text = result.field("message")?.field("text") as? String
                ?: result.field("text") as? String
                ?: "",
            title = result.field("title") as? String ?: ""
        )
    }

    fun chatIdsFromResult(result: Any?): LongArray? {
        return result?.field("chatIds") as? LongArray
            ?: (result?.field("chatIds") as? Array<*>)?.mapNotNull { (it as? Number)?.toLong() }?.toLongArray()
    }

    private fun String.toTelegramCallState(): TelegramCallState {
        return when (this) {
            "CallStatePending" -> TelegramCallState.Pending
            "CallStateExchangingKeys" -> TelegramCallState.ExchangingKeys
            "CallStateReady" -> TelegramCallState.Ready
            "CallStateHangingUp" -> TelegramCallState.HangingUp
            "CallStateDiscarded" -> TelegramCallState.Discarded
            "CallStateError" -> TelegramCallState.Error
            else -> TelegramCallState.Unknown
        }
    }

    private fun parseCallReady(state: Any?): TdLibObjectParsedCallReady {
        if (state?.javaClass?.simpleName != "CallStateReady") return TdLibObjectParsedCallReady()
        return TdLibObjectParsedCallReady(
            protocol = parseCallProtocol(state.field("protocol")),
            servers = state.fieldAny("servers", "connections").asObjectList().mapNotNull(::parseCallServer),
            config = state.field("config") as? String ?: "",
            encryptionKey = decodeTdLibBytes(state.fieldAny("encryptionKey", "encryption_key")),
            emojis = state.field("emojis").asObjectList().mapNotNull { it as? String },
            allowP2p = state.fieldAny("allowP2p", "allowP2p_", "allow_p2p") as? Boolean ?: true,
            customParameters = state.fieldAny("customParameters", "custom_parameters") as? String ?: ""
        )
    }

    private fun parseCallProtocol(protocol: Any?): TelegramCallProtocol {
        if (protocol == null) return TelegramCallProtocol()
        return TelegramCallProtocol(
            minLayer = (protocol.field("minLayer") as? Number)?.toInt() ?: 65,
            maxLayer = (protocol.field("maxLayer") as? Number)?.toInt() ?: 200,
            udpP2p = protocol.field("udpP2p") as? Boolean ?: true,
            udpReflector = protocol.field("udpReflector") as? Boolean ?: true,
            libraryVersions = protocol.field("libraryVersions")
                .asObjectList()
                .mapNotNull { it as? String }
                .ifEmpty { listOf("tdlib") }
        )
    }

    private fun parseCallServer(server: Any?): TelegramCallServer? {
        if (server == null) return null
        val type = server.field("type")
        val typeLabel = type?.javaClass?.simpleName.orEmpty()
        return TelegramCallServer(
            id = (server.field("id") as? Number)?.toLong()?.takeIf { it != 0L } ?: return null,
            ipv4 = server.fieldAny("ipAddress", "ip", "ipv4") as? String ?: "",
            ipv6 = server.fieldAny("ipv6Address", "ipv6") as? String ?: "",
            port = (server.field("port") as? Number)?.toInt() ?: 0,
            username = type.field("username") as? String ?: "",
            password = type.field("password") as? String ?: "",
            supportsTurn = when (typeLabel) {
                "CallServerTypeWebrtc" -> type.fieldAny("supportsTurn", "turn") as? Boolean ?: false
                else -> true
            },
            supportsStun = type.fieldAny("supportsStun", "stun") as? Boolean ?: false,
            isTcp = type.fieldAny("isTcp", "tcp") as? Boolean ?: false,
            peerTag = decodeTdLibBytes(type.fieldAny("peerTag", "peer_tag"))
        )
    }

    private fun String.toTelegramStoryState(): TelegramStoryState {
        return when (this) {
            "StorySendingStatePending" -> TelegramStoryState.Sending
            "StorySendingStateFailed" -> TelegramStoryState.Failed
            "" -> TelegramStoryState.Active
            else -> TelegramStoryState.Unknown
        }
    }

    fun userIdsFromResult(result: Any?): LongArray? {
        return result?.field("userIds") as? LongArray
            ?: (result?.field("userIds") as? Array<*>)?.mapNotNull { (it as? Number)?.toLong() }?.toLongArray()
    }

    private fun parseChatType(type: Any?): String {
        return when (type?.javaClass?.simpleName) {
            "ChatTypePrivate" -> "private"
            "ChatTypeBasicGroup" -> "basic_group"
            "ChatTypeSupergroup" -> if (type.field("isChannel") == true) "channel" else "supergroup"
            "ChatTypeSecret" -> "secret"
            else -> "chat"
        }
    }

    private fun parseSenderId(sender: Any?): String {
        return when (sender?.javaClass?.simpleName) {
            "MessageSenderUser" -> "user:${sender.field("userId")}"
            "MessageSenderChat" -> "chat:${sender.field("chatId")}"
            else -> ""
        }
    }

    private fun extractText(content: Any): String {
        if (content.javaClass.simpleName == "MessagePoll") {
            val question = content.field("poll").pollQuestionText()
            return question.takeIf { it.isNotBlank() }?.let { "Poll: $it" }.orEmpty()
        }
        if (content.javaClass.simpleName == "MessageContact") {
            return content.field("contact").contactPreviewText()
        }
        val textObject = content.field("text") ?: content.field("caption")
        val formatted = textObject?.field("text") ?: textObject
        return formatted as? String ?: ""
    }

    private fun parseMedia(content: Any): TdLibParsedMedia {
        return when (content.javaClass.simpleName) {
            "MessagePhoto" -> {
                val sizes = content.field("photo")?.field("sizes").asObjectList()
                val file = sizes
                    .maxByOrNull { size ->
                        ((size?.field("width") as? Number)?.toInt() ?: 0) *
                            ((size?.field("height") as? Number)?.toInt() ?: 0)
                    }
                    ?.let { it.fieldAny("photo", "file") ?: it.findFileObject() }
                    ?: content.field("photo").findFileObject()
                val thumbnailFile = sizes
                    .minByOrNull { size ->
                        ((size?.field("width") as? Number)?.toInt() ?: 0) *
                            ((size?.field("height") as? Number)?.toInt() ?: 0)
                    }
                    ?.let { it.fieldAny("photo", "file") ?: it.findFileObject() }
                parsedFile(MessageKind.Image, file, thumbnailFile = thumbnailFile, mimeType = "image/jpeg")
            }
            "MessageVideo" -> {
                val video = content.field("video")
                val thumbnailFile = video?.field("thumbnail")
                    ?.let { thumbnail -> thumbnail.fieldAny("file", "photo") ?: thumbnail.findFileObject() }
                parsedFile(
                    kind = MessageKind.Video,
                    file = video.fieldAny("video", "document", "file") ?: video.findFileObject(),
                    thumbnailFile = thumbnailFile,
                    mimeType = video?.field("mimeType") as? String ?: "",
                    fileName = video?.field("fileName") as? String ?: ""
                )
            }
            "MessageDocument" -> {
                val document = content.field("document")
                parsedFile(
                    kind = MessageKind.File,
                    file = document.fieldAny("document", "file") ?: document.findFileObject(),
                    mimeType = document?.field("mimeType") as? String ?: "",
                    fileName = document?.field("fileName") as? String ?: ""
                )
            }
            else -> TdLibParsedMedia(MessageKind.Text)
        }
    }

    private fun TelegramMessage?.previewText(): String {
        return this
            ?.translatedText
            ?.ifBlank { originalText }
            ?.ifBlank { kind.previewLabel() }
            .orEmpty()
    }

    private fun MessageKind.previewLabel(): String {
        return when (this) {
            MessageKind.Image -> "Image"
            MessageKind.Video -> "Video"
            MessageKind.File -> "File"
            MessageKind.Text -> ""
        }
    }

    private fun parseStoryMedia(content: Any?): TdLibParsedMedia {
        return when (content?.javaClass?.simpleName) {
            "StoryContentPhoto" -> {
                val sizes = content.field("photo")?.field("sizes").asObjectList()
                val file = sizes
                    .maxByOrNull { size ->
                        ((size?.field("width") as? Number)?.toInt() ?: 0) *
                            ((size?.field("height") as? Number)?.toInt() ?: 0)
                    }
                    ?.let { it.fieldAny("photo", "file") ?: it.findFileObject() }
                    ?: content.field("photo").findFileObject()
                val thumbnailFile = sizes
                    .minByOrNull { size ->
                        ((size?.field("width") as? Number)?.toInt() ?: 0) *
                            ((size?.field("height") as? Number)?.toInt() ?: 0)
                    }
                    ?.let { it.fieldAny("photo", "file") ?: it.findFileObject() }
                parsedFile(MessageKind.Image, file, thumbnailFile = thumbnailFile, mimeType = "image/jpeg")
            }
            "StoryContentVideo" -> {
                val video = content.field("video")
                val thumbnailFile = video?.field("thumbnail")
                    ?.let { thumbnail -> thumbnail.fieldAny("file", "photo") ?: thumbnail.findFileObject() }
                parsedFile(
                    kind = MessageKind.Video,
                    file = video.fieldAny("video", "file") ?: video.findFileObject(),
                    thumbnailFile = thumbnailFile,
                    mimeType = video?.field("mimeType") as? String ?: "",
                    fileName = video?.field("fileName") as? String ?: ""
                )
            }
            else -> TdLibParsedMedia(MessageKind.Text)
        }
    }

    private fun parseInlineBotResult(result: Any?): TelegramInlineBotResult? {
        if (result == null) return null
        val id = result.field("id") as? String ?: return null
        if (id.isBlank()) return null
        val thumbnailFile = result.field("thumbnail")?.fieldAny("file", "photo")
            ?: result.field("photo").findFileObject()
        return TelegramInlineBotResult(
            id = id,
            type = result.javaClass.simpleName,
            title = result.field("title") as? String ?: "",
            description = result.field("description") as? String ?: "",
            thumbnailFileId = (thumbnailFile.fieldAny("id", "fileId") as? Number)?.toInt() ?: 0,
            thumbnailLocalPath = thumbnailFile.localPath()
        )
    }

    private fun parseActiveSession(session: Any?): TelegramActiveSession? {
        val id = (session.field("id") as? Number)?.toLong() ?: return null
        return TelegramActiveSession(
            id = id,
            applicationName = session.field("applicationName") as? String ?: "Telegram",
            applicationVersion = session.field("applicationVersion") as? String ?: "",
            deviceModel = session.field("deviceModel") as? String ?: "",
            platform = session.field("platform") as? String ?: "",
            systemVersion = session.field("systemVersion") as? String ?: "",
            ip = session.field("ip") as? String ?: "",
            country = session.field("country") as? String ?: "",
            region = session.field("region") as? String ?: "",
            isCurrent = session.field("isCurrent") as? Boolean ?: false,
            lastActiveDate = (session.field("lastActiveDate") as? Number)?.toInt() ?: 0
        )
    }

    private fun parseStarTransaction(transaction: Any?): TelegramStarTransaction? {
        val id = transaction.fieldAny("id", "transactionId") as? String ?: return null
        if (id.isBlank()) return null
        val starAmount = transaction.fieldAny("starAmount", "amount")
        return TelegramStarTransaction(
            id = id,
            amount = (starAmount.fieldAny("starCount", "amount") as? Number)?.toLong()
                ?: (transaction.fieldAny("starCount", "amount") as? Number)?.toLong()
                ?: 0L,
            title = transaction.field("title") as? String ?: "",
            description = transaction.field("description") as? String ?: "",
            date = (transaction.field("date") as? Number)?.toInt() ?: 0
        )
    }

    private fun String.toPrivacyPreset(): TelegramPrivacyPreset {
        return when (this) {
            "UserPrivacySettingRuleAllowAll" -> TelegramPrivacyPreset.Everybody
            "UserPrivacySettingRuleAllowContacts" -> TelegramPrivacyPreset.Contacts
            "UserPrivacySettingRuleRestrictAll" -> TelegramPrivacyPreset.Nobody
            else -> TelegramPrivacyPreset.Contacts
        }
    }

    private fun parsedFile(
        kind: MessageKind,
        file: Any?,
        thumbnailFile: Any? = null,
        mimeType: String = "",
        fileName: String = ""
    ): TdLibParsedMedia {
        val sizeBytes = (file.fieldAny("size", "expectedSize") as? Number)?.toLong() ?: 0L
        return TdLibParsedMedia(
            kind = kind,
            fileId = (file.fieldAny("id", "fileId") as? Number)?.toInt() ?: 0,
            localPath = file.localPath(),
            mimeType = mimeType,
            fileName = fileName,
            thumbnailFileId = (thumbnailFile.fieldAny("id", "fileId") as? Number)?.toInt() ?: 0,
            thumbnailLocalPath = thumbnailFile.localPath().takeIf { thumbnailFile.localDownloadCompleted() }.orEmpty(),
            downloadedPrefixBytes = file.localDownloadedPrefixBytes(),
            sizeMb = tdLibBytesToMb(sizeBytes)
        )
    }
}

private data class TdLibObjectParsedCallReady(
    val protocol: TelegramCallProtocol = TelegramCallProtocol(),
    val servers: List<TelegramCallServer> = emptyList(),
    val config: String = "",
    val encryptionKey: ByteArray = byteArrayOf(),
    val emojis: List<String> = emptyList(),
    val allowP2p: Boolean = true,
    val customParameters: String = ""
)

internal fun Any?.fieldAny(vararg names: String): Any? {
    if (this == null) return null
    for (name in names) {
        field(name)?.let { return it }
    }
    return null
}

internal fun Any?.asObjectList(): List<Any?> {
    if (this == null) return emptyList()
    if (this is Array<*>) return toList()
    if (this is Iterable<*>) return toList()
    val valueClass = javaClass
    if (!valueClass.isArray) return emptyList()
    return (0 until java.lang.reflect.Array.getLength(this))
        .map { index -> java.lang.reflect.Array.get(this, index) }
}

private fun Any?.pollQuestionText(): String {
    val question = this?.field("question")
    return when (question) {
        is String -> question
        else -> question?.field("text") as? String ?: ""
    }
}

private fun Any?.contactPreviewText(): String {
    val firstName = this?.field("firstName") as? String ?: ""
    val lastName = this?.field("lastName") as? String ?: ""
    val phoneNumber = this?.field("phoneNumber") as? String ?: ""
    val name = listOf(firstName, lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    return listOf(name.ifBlank { "Contact" }, phoneNumber)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
        .let { "Contact: $it" }
}

internal fun Any?.findFileObject(depth: Int = 0): Any? {
    if (this == null || depth > 6) return null
    if (isFileCandidate()) return this
    val preferred = listOf("video", "document", "photo", "file", "thumbnail")
    for (name in preferred) {
        field(name).findFileObject(depth + 1)?.let { return it }
    }
    for (field in javaClass.fields) {
        val value = runCatching { field.get(this) }.getOrNull()
        if (value is String || value is Number || value is Boolean) continue
        value.asObjectList().takeIf { it.isNotEmpty() }?.forEach { item ->
            item.findFileObject(depth + 1)?.let { return it }
        }
        value.findFileObject(depth + 1)?.let { return it }
    }
    return null
}

private fun Any?.isFileCandidate(): Boolean {
    if (this == null) return false
    if (javaClass.simpleName == "File") return true
    val id = (fieldAny("id", "fileId") as? Number)?.toInt() ?: 0
    val hasFileShape = field("local") != null || field("remote") != null ||
        field("size") != null || field("expectedSize") != null
    return id > 0 && hasFileShape
}

internal fun Any?.localPath(): String {
    return field("local")?.field("path") as? String
        ?: field("localPath") as? String
        ?: ""
}

internal fun Any?.localDownloadedPrefixBytes(): Long {
    return (field("local")?.fieldAny("downloadedPrefixSize", "downloadedSize") as? Number)?.toLong() ?: 0L
}

internal fun Any?.localDownloadCompleted(): Boolean {
    return field("local")?.fieldAny("isDownloadingCompleted", "is_downloading_completed") as? Boolean ?: false
}

internal fun Any?.syncState(): MessageSyncState {
    return when (field("sendingState")?.javaClass?.simpleName) {
        "MessageSendingStatePending" -> MessageSyncState.Sending
        "MessageSendingStateFailed" -> MessageSyncState.Failed
        else -> MessageSyncState.Synced
    }
}

internal fun Any?.field(name: String): Any? {
    if (this == null) return null
    return runCatching { javaClass.getField(name).get(this) }.getOrNull()
}
