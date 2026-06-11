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
import java.util.Base64
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal class TdLibJsonParser(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onSender: (TelegramSender) -> Unit = {}
) {
    fun parseMessagesResult(result: JSONObject): List<TelegramMessage> {
        val messages = result.optJSONArray("messages") ?: return emptyList()
        return (0 until messages.length()).mapNotNull { index ->
            parseMessage(messages.optJSONObject(index))
        }
    }

    fun parseChat(chat: JSONObject?): TelegramChat? {
        if (chat == null) return null
        val id = chat.optLong("id", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return null
        val title = chat.optString("title", "Telegram")
        val type = parseChatType(chat.optJSONObject("type"))
        val lastMessage = parseMessage(chat.optJSONObject("last_message"))
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
            unreadCount = chat.optInt("unread_count", 0),
            lastMessagePreview = lastMessage.previewText(),
            updatedAtMillis = nowMillis(),
            isMainList = chat.isInMainChatList()
        )
    }

    fun parseChatLastMessage(update: JSONObject): TelegramChat? {
        val chatId = update.optLong("chat_id", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return null
        val lastMessage = parseMessage(update.optJSONObject("last_message"))
        return TelegramChat(
            id = chatId,
            title = "",
            type = "chat",
            unreadCount = 0,
            lastMessagePreview = lastMessage.previewText(),
            updatedAtMillis = nowMillis(),
            isMainList = false
        )
    }

    fun parseMessage(message: JSONObject?): TelegramMessage? {
        if (message == null) return null
        val content = message.optJSONObject("content") ?: return null
        val media = parseMedia(content)
        val text = extractText(content)
        if (text.isBlank() && media.kind == MessageKind.Text) return null
        val id = message.optLong("id", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return null
        val chatId = message.optLong("chat_id", 0L)
        val senderId = parseSenderId(message.optJSONObject("sender_id")).ifBlank { "chat:$chatId" }
        val date = message.optLong("date", 0L).takeIf { it > 0 }?.times(1000) ?: nowMillis()
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
            isOutgoing = message.optBoolean("is_outgoing", false),
            isRead = message.optBoolean("is_outgoing", false) && message.optJSONObject("sending_state") == null,
            isEdited = message.optLong("edit_date", 0L) > 0L,
            isPinned = message.optBoolean("is_pinned", false),
            receivedAtMillis = date
        )
    }

    fun parseUser(user: JSONObject?): TelegramSender? {
        if (user == null) return null
        val id = user.optLong("id", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return null
        val firstName = user.optString("first_name", "")
        val lastName = user.optString("last_name", "")
        val username = user.optJSONObject("usernames")?.optJSONArray("active_usernames")?.firstString()
        val displayName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { username ?: "Telegram user" }
        return TelegramSender(
            id = "user:$id",
            displayName = displayName,
            type = "user",
            updatedAtMillis = nowMillis(),
            isContact = user.optBoolean("is_contact", user.optBoolean("isContact", false))
        )
    }

    fun parseGroup(group: JSONObject?, type: String): TelegramSender? {
        if (group == null) return null
        val id = group.optLong("id", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return null
        val title = group.optString("title").takeIf { it.isNotBlank() } ?: return null
        return TelegramSender(
            id = "$type:$id",
            displayName = title,
            type = type,
            updatedAtMillis = nowMillis()
        )
    }

    fun parseFileUpdate(file: JSONObject?): TelegramMediaFile? {
        if (file == null) return null
        val fileId = file.optIntAny("id", "file_id")
        if (fileId <= 0) return null
        return TelegramMediaFile(
            id = fileId,
            localPath = file.localPath(),
            sizeMb = tdLibBytesToMb(file.optLongAny("size", "expected_size")),
            downloadedPrefixBytes = file.transferProgressBytes()
        )
    }

    fun parseContentText(content: JSONObject?): String {
        return content?.let(::extractText).orEmpty()
    }

    fun parseCall(call: JSONObject?): TelegramCall? {
        if (call == null) return null
        val id = call.optInt("id", 0).takeIf { it != 0 } ?: return null
        val userId = call.optLong("user_id", 0L)
        val stateObject = call.optJSONObject("state")
        val stateLabel = stateObject?.optString("@type").orEmpty()
        val errorMessage = stateObject
            ?.optJSONObject("error")
            ?.optString("message")
            .orEmpty()
        val ready = parseCallReady(stateObject)
        return TelegramCall(
            id = id,
            userId = userId,
            isOutgoing = call.optBoolean("is_outgoing", false),
            isVideo = call.optBoolean("is_video", false),
            state = stateLabel.toTelegramCallState(),
            isCreated = stateObject?.optBoolean("is_created", false) ?: false,
            isReceived = stateObject?.optBoolean("is_received", false) ?: false,
            durationSeconds = call.optInt("duration", 0),
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

    fun parseStoriesResult(result: JSONObject): List<TelegramStory> {
        val chatId = result.optLongAny("chat_id", "story_sender_chat_id", "sender_chat_id")
        val stories = result.optJSONArray("stories") ?: return emptyList()
        return (0 until stories.length()).mapNotNull { index ->
            parseStory(stories.optJSONObject(index), defaultChatId = chatId)
        }
    }

    fun parseStory(story: JSONObject?, defaultChatId: Long = 0L): TelegramStory? {
        if (story == null) return null
        val id = story.optInt("id", 0).takeIf { it != 0 }
            ?: story.optInt("story_id", 0).takeIf { it != 0 }
            ?: return null
        val chatId = story.optLongAny("chat_id", "story_sender_chat_id", "sender_chat_id")
            .takeIf { it != 0L }
            ?: defaultChatId
        if (chatId == 0L) return null
        val content = story.optJSONObject("content")
        val media = parseStoryMedia(content)
        val date = story.optLong("date", 0L).takeIf { it > 0L }?.times(1000) ?: nowMillis()
        val stateLabel = story.optJSONObject("sending_state")?.optString("@type").orEmpty()
        return TelegramStory(
            chatId = chatId,
            id = id,
            senderChatId = story.optLongAny("sender_chat_id", "story_sender_chat_id").takeIf { it != 0L } ?: chatId,
            dateMillis = date,
            caption = extractStoryCaption(story),
            kind = media.kind,
            mediaFileId = media.fileId,
            mediaLocalPath = media.localPath,
            mediaThumbnailFileId = media.thumbnailFileId,
            mediaThumbnailLocalPath = media.thumbnailLocalPath,
            isOutgoing = story.optBoolean("is_outgoing", false),
            isPinned = story.optBoolean("is_pinned", false),
            isEdited = story.optBoolean("is_edited", false),
            isViewed = story.optBoolean("has_viewer_viewed", false),
            state = stateLabel.toTelegramStoryState(),
            stateLabel = stateLabel
        )
    }

    fun deletedStory(chatId: Long, storyId: Int): TelegramStory? {
        if (chatId == 0L || storyId == 0) return null
        return TelegramStory(chatId = chatId, id = storyId, state = TelegramStoryState.Deleted)
    }

    fun parseBotCommandsResult(result: JSONObject, botUserId: Long): List<TelegramBotCommand> {
        val commands = result.optJSONArray("commands") ?: return emptyList()
        return (0 until commands.length()).mapNotNull { index ->
            commands.optJSONObject(index)?.let { command ->
                val name = command.optString("command", "").trim()
                if (name.isBlank()) {
                    null
                } else {
                    TelegramBotCommand(
                        botUserId = botUserId,
                        command = name,
                        description = command.optString("description", "")
                    )
                }
            }
        }
    }

    fun parseBotMenuButton(result: JSONObject, botUserId: Long): TelegramBotMenuButton {
        val type = result.optString("@type", "")
        return TelegramBotMenuButton(
            botUserId = botUserId,
            type = type,
            text = result.optString("text", ""),
            url = result.optString("url", "")
                .ifBlank { result.optJSONObject("web_app")?.optString("url", "").orEmpty() }
        )
    }

    fun parseInlineBotResults(result: JSONObject, botUserId: Long): TelegramInlineBotResults {
        val results = result.optJSONArray("results") ?: JSONArray()
        return TelegramInlineBotResults(
            botUserId = botUserId,
            queryId = result.optLongAny("inline_query_id", "query_id"),
            nextOffset = result.optString("next_offset", ""),
            results = (0 until results.length()).mapNotNull { index ->
                parseInlineBotResult(results.optJSONObject(index))
            }
        )
    }

    fun parseWebAppUrl(result: JSONObject, botUserId: Long): TelegramWebAppUrl? {
        val url = result.optString("url", "").trim()
        if (url.isBlank()) return null
        return TelegramWebAppUrl(botUserId = botUserId, url = url)
    }

    fun parsePrivacyRulesResult(result: JSONObject, setting: TelegramPrivacySetting): TelegramPrivacyRuleSummary {
        val firstRule = result.optJSONArray("rules")?.optJSONObject(0)
        val rawRule = firstRule?.optString("@type", "").orEmpty()
        return TelegramPrivacyRuleSummary(
            setting = setting,
            preset = rawRule.toPrivacyPreset(),
            rawRule = rawRule
        )
    }

    fun parseActiveSessions(result: JSONObject): List<TelegramActiveSession> {
        val sessions = result.optJSONArray("sessions") ?: return emptyList()
        return (0 until sessions.length()).mapNotNull { index ->
            sessions.optJSONObject(index)?.let(::parseActiveSession)
        }
    }

    fun parsePremiumFeatures(result: JSONObject): List<TelegramPremiumFeatureInfo> {
        val features = result.optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { index ->
            features.optJSONObject(index)?.let { feature ->
                TelegramPremiumFeatureInfo(
                    type = feature.optString("@type", ""),
                    title = feature.optString("title", ""),
                    description = feature.optString("description", "")
                ).takeIf { it.type.isNotBlank() || it.title.isNotBlank() }
            }
        }
    }

    fun parsePremiumLimit(result: JSONObject, type: TelegramPremiumLimitType): TelegramPremiumLimitSummary {
        return TelegramPremiumLimitSummary(
            type = type,
            defaultValue = result.optIntAny("default_value", "defaultValue", "limit", "value"),
            premiumValue = result.optIntAny("premium_value", "premiumValue")
        )
    }

    fun parseStarBalance(result: JSONObject): TelegramStarBalance? {
        val amount = result.optLongAny("star_count", "starCount", "amount", "count")
        if (amount == 0L && !result.has("nanostar_count") && !result.has("nanostarCount")) return null
        return TelegramStarBalance(
            amount = amount,
            nanoAmount = result.optIntAny("nanostar_count", "nanostarCount")
        )
    }

    fun parseStarTransactions(result: JSONObject): List<TelegramStarTransaction> {
        val transactions = result.optJSONArray("transactions") ?: return emptyList()
        return (0 until transactions.length()).mapNotNull { index ->
            transactions.optJSONObject(index)?.let(::parseStarTransaction)
        }
    }

    fun parseBusinessChatLinks(result: JSONObject): List<TelegramBusinessChatLink> {
        val links = result.optJSONArray("links") ?: return emptyList()
        return (0 until links.length()).mapNotNull { index ->
            parseBusinessChatLink(links.optJSONObject(index))
        }
    }

    fun parseBusinessChatLink(result: JSONObject?): TelegramBusinessChatLink? {
        if (result == null) return null
        val link = result.optString("link", "").ifBlank { result.optString("url", "") }
        if (link.isBlank()) return null
        return TelegramBusinessChatLink(
            link = link,
            text = result.optJSONObject("message")?.optString("text", "").orEmpty()
                .ifBlank { result.optString("text", "") },
            title = result.optString("title", "")
        )
    }

    private fun parseChatType(type: JSONObject?): String {
        return when (type?.optString("@type")) {
            "chatTypePrivate" -> "private"
            "chatTypeBasicGroup" -> "basic_group"
            "chatTypeSupergroup" -> if (type.optBoolean("is_channel", false)) "channel" else "supergroup"
            "chatTypeSecret" -> "secret"
            else -> "chat"
        }
    }

    private fun String.toTelegramCallState(): TelegramCallState {
        return when (this) {
            "callStatePending" -> TelegramCallState.Pending
            "callStateExchangingKeys" -> TelegramCallState.ExchangingKeys
            "callStateReady" -> TelegramCallState.Ready
            "callStateHangingUp" -> TelegramCallState.HangingUp
            "callStateDiscarded" -> TelegramCallState.Discarded
            "callStateError" -> TelegramCallState.Error
            else -> TelegramCallState.Unknown
        }
    }

    private fun parseCallReady(state: JSONObject?): TdLibParsedCallReady {
        if (state?.optString("@type") != "callStateReady") return TdLibParsedCallReady()
        return TdLibParsedCallReady(
            protocol = parseCallProtocol(state.optJSONObject("protocol")),
            servers = state.optJSONArray("servers")
                .ifNull { state.optJSONArray("connections") }
                ?.objectItems()
                ?.mapNotNull(::parseCallServer)
                ?: emptyList(),
            config = state.optString("config", ""),
            encryptionKey = decodeTdLibBytes(state.opt("encryption_key")),
            emojis = state.optJSONArray("emojis").stringList(),
            allowP2p = state.optBoolean("allow_p2p", true),
            customParameters = state.optString("custom_parameters", "")
        )
    }

    private fun parseCallProtocol(protocol: JSONObject?): TelegramCallProtocol {
        if (protocol == null) return TelegramCallProtocol()
        return TelegramCallProtocol(
            minLayer = protocol.optInt("min_layer", 65),
            maxLayer = protocol.optInt("max_layer", 200),
            udpP2p = protocol.optBoolean("udp_p2p", true),
            udpReflector = protocol.optBoolean("udp_reflector", true),
            libraryVersions = protocol.optJSONArray("library_versions").stringList().ifEmpty { listOf("tdlib") }
        )
    }

    private fun parseCallServer(server: JSONObject?): TelegramCallServer? {
        if (server == null) return null
        val type = server.optJSONObject("type")
        val typeLabel = type?.optString("@type", "").orEmpty()
        return TelegramCallServer(
            id = server.optLongAny("id").takeIf { it != 0L } ?: return null,
            ipv4 = server.optString("ip_address", "").ifBlank { server.optString("ipv4", "") },
            ipv6 = server.optString("ipv6_address", "").ifBlank { server.optString("ipv6", "") },
            port = server.optInt("port", 0),
            username = type?.optString("username", "").orEmpty(),
            password = type?.optString("password", "").orEmpty(),
            supportsTurn = when (typeLabel) {
                "callServerTypeWebrtc" -> type?.optBoolean("supports_turn", false) ?: false
                else -> true
            },
            supportsStun = type?.optBoolean("supports_stun", false) ?: false,
            isTcp = type?.optBoolean("is_tcp", false) ?: false,
            peerTag = decodeTdLibBytes(type?.opt("peer_tag"))
        )
    }

    private fun String.toTelegramStoryState(): TelegramStoryState {
        return when (this) {
            "storySendingStatePending" -> TelegramStoryState.Sending
            "storySendingStateFailed" -> TelegramStoryState.Failed
            "" -> TelegramStoryState.Active
            else -> TelegramStoryState.Unknown
        }
    }

    private fun parseSenderId(sender: JSONObject?): String {
        return when (sender?.optString("@type")) {
            "messageSenderUser" -> "user:${sender.optLong("user_id")}"
            "messageSenderChat" -> "chat:${sender.optLong("chat_id")}"
            else -> ""
        }
    }

    private fun extractText(content: JSONObject): String {
        if (content.optString("@type") == "messagePoll") {
            val question = content.optJSONObject("poll")?.pollQuestionText().orEmpty()
            return question.takeIf { it.isNotBlank() }?.let { "Poll: $it" }.orEmpty()
        }
        if (content.optString("@type") == "messageContact") {
            return content.optJSONObject("contact")?.contactPreviewText().orEmpty()
        }
        val textObject = content.optJSONObject("text") ?: content.optJSONObject("caption")
        val formatted = textObject?.optString("text", "").orEmpty()
        return formatted.ifBlank { content.optString("text", "") }
    }

    private fun extractStoryCaption(story: JSONObject): String {
        val caption = story.optJSONObject("caption")
        return caption?.optString("text", "").orEmpty()
    }

    private fun parseStoryMedia(content: JSONObject?): TdLibParsedMedia {
        return when (content?.optString("@type")) {
            "storyContentPhoto" -> {
                val photo = content.optJSONObject("photo")
                val sizes = photo
                    ?.optJSONArray("sizes")
                    ?.let { sizes -> (0 until sizes.length()).mapNotNull { sizes.optJSONObject(it) } }
                val file = sizes
                    ?.maxByOrNull { it.optInt("width", 0) * it.optInt("height", 0) }
                    ?.optFileObject("photo", "file")
                    ?: photo.findJsonFile()
                val thumbnailFile = sizes
                    ?.minByOrNull { it.optInt("width", 0) * it.optInt("height", 0) }
                    ?.optFileObject("photo", "file")
                parsedFile(MessageKind.Image, file, thumbnailFile = thumbnailFile, mimeType = "image/jpeg")
            }
            "storyContentVideo" -> {
                val video = content.optJSONObject("video")
                val thumbnailFile = video
                    ?.optJSONObject("thumbnail")
                    ?.let { thumbnail -> thumbnail.optFileObject("file", "photo") ?: thumbnail.findJsonFile() }
                parsedFile(
                    kind = MessageKind.Video,
                    file = video?.optFileObject("video", "file") ?: video.findJsonFile(),
                    thumbnailFile = thumbnailFile,
                    mimeType = video?.optString("mime_type", "").orEmpty(),
                    fileName = video?.optString("file_name", "").orEmpty()
                )
            }
            else -> TdLibParsedMedia(MessageKind.Text)
        }
    }

    private fun parseInlineBotResult(result: JSONObject?): TelegramInlineBotResult? {
        if (result == null) return null
        val id = result.optString("id", "").takeIf { it.isNotBlank() } ?: return null
        val thumbnailFile = result.optJSONObject("thumbnail")
            ?.optFileObject("file", "photo")
            ?: result.optJSONObject("photo")?.findJsonFile()
        return TelegramInlineBotResult(
            id = id,
            type = result.optString("@type", ""),
            title = result.optString("title", ""),
            description = result.optString("description", ""),
            thumbnailFileId = thumbnailFile?.optIntAny("id", "file_id") ?: 0,
            thumbnailLocalPath = thumbnailFile?.localPath().orEmpty()
        )
    }

    private fun parseActiveSession(session: JSONObject): TelegramActiveSession? {
        val id = session.optLong("id", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return null
        return TelegramActiveSession(
            id = id,
            applicationName = session.optString("application_name", "Telegram"),
            applicationVersion = session.optString("application_version", ""),
            deviceModel = session.optString("device_model", ""),
            platform = session.optString("platform", ""),
            systemVersion = session.optString("system_version", ""),
            ip = session.optString("ip", ""),
            country = session.optString("country", ""),
            region = session.optString("region", ""),
            isCurrent = session.optBoolean("is_current", false),
            lastActiveDate = session.optInt("last_active_date", 0)
        )
    }

    private fun parseStarTransaction(transaction: JSONObject): TelegramStarTransaction? {
        val id = transaction.optString("id", "").ifBlank { transaction.optString("transaction_id", "") }
        if (id.isBlank()) return null
        val starAmount = transaction.optJSONObject("star_amount")
            ?: transaction.optJSONObject("amount")
        return TelegramStarTransaction(
            id = id,
            amount = starAmount?.optLongAny("star_count", "starCount", "amount")
                ?: transaction.optLongAny("star_count", "starCount", "amount"),
            title = transaction.optString("title", ""),
            description = transaction.optString("description", ""),
            date = transaction.optInt("date", 0)
        )
    }

    private fun String.toPrivacyPreset(): TelegramPrivacyPreset {
        return when (this) {
            "userPrivacySettingRuleAllowAll" -> TelegramPrivacyPreset.Everybody
            "userPrivacySettingRuleAllowContacts" -> TelegramPrivacyPreset.Contacts
            "userPrivacySettingRuleRestrictAll" -> TelegramPrivacyPreset.Nobody
            else -> TelegramPrivacyPreset.Contacts
        }
    }

    private fun parseMedia(content: JSONObject): TdLibParsedMedia {
        return when (content.optString("@type")) {
            "messagePhoto" -> {
                val photo = content.optJSONObject("photo")
                val sizes = photo
                    ?.optJSONArray("sizes")
                    ?.let { sizes -> (0 until sizes.length()).mapNotNull { sizes.optJSONObject(it) } }
                val file = sizes
                    ?.maxByOrNull { it.optInt("width", 0) * it.optInt("height", 0) }
                    ?.optFileObject("photo", "file")
                    ?: photo.findJsonFile()
                val thumbnailFile = sizes
                    ?.minByOrNull { it.optInt("width", 0) * it.optInt("height", 0) }
                    ?.optFileObject("photo", "file")
                parsedFile(MessageKind.Image, file, thumbnailFile = thumbnailFile, mimeType = "image/jpeg")
            }
            "messageVideo" -> {
                val video = content.optJSONObject("video")
                val thumbnailFile = video
                    ?.optJSONObject("thumbnail")
                    ?.let { thumbnail -> thumbnail.optFileObject("file", "photo") ?: thumbnail.findJsonFile() }
                parsedFile(
                    kind = MessageKind.Video,
                    file = video?.optFileObject("video", "document", "file") ?: video.findJsonFile(),
                    thumbnailFile = thumbnailFile,
                    mimeType = video?.optString("mime_type", "").orEmpty(),
                    fileName = video?.optString("file_name", "").orEmpty()
                )
            }
            "messageDocument" -> {
                val document = content.optJSONObject("document")
                parsedFile(
                    kind = MessageKind.File,
                    file = document?.optFileObject("document", "file") ?: document.findJsonFile(),
                    mimeType = document?.optString("mime_type", "").orEmpty(),
                    fileName = document?.optString("file_name", "").orEmpty()
                )
            }
            "messageAudio" -> {
                val audio = content.optJSONObject("audio")
                val thumbnailFile = audio
                    ?.optJSONObject("album_cover_thumbnail")
                    ?.let { thumbnail -> thumbnail.optFileObject("file", "photo") ?: thumbnail.findJsonFile() }
                parsedFile(
                    kind = MessageKind.Audio,
                    file = audio?.optFileObject("audio", "file") ?: audio.findJsonFile(),
                    thumbnailFile = thumbnailFile,
                    mimeType = audio?.optString("mime_type", "").orEmpty().ifBlank { "audio/mpeg" },
                    fileName = audio?.optString("file_name", "").orEmpty()
                )
            }
            "messageVoiceNote" -> {
                val voiceNote = content.optJSONObject("voice_note")
                parsedFile(
                    kind = MessageKind.Voice,
                    file = voiceNote?.optFileObject("voice", "voice_note", "file") ?: voiceNote.findJsonFile(),
                    mimeType = voiceNote?.optString("mime_type", "").orEmpty().ifBlank { "audio/ogg" },
                    fileName = voiceNote?.optString("file_name", "").orEmpty()
                )
            }
            "messageVideoNote" -> {
                val videoNote = content.optJSONObject("video_note")
                val thumbnailFile = videoNote
                    ?.optJSONObject("thumbnail")
                    ?.let { thumbnail -> thumbnail.optFileObject("file", "photo") ?: thumbnail.findJsonFile() }
                parsedFile(
                    kind = MessageKind.VideoNote,
                    file = videoNote?.optFileObject("video", "video_note", "file") ?: videoNote.findJsonFile(),
                    thumbnailFile = thumbnailFile,
                    mimeType = videoNote?.optString("mime_type", "").orEmpty().ifBlank { "video/mp4" },
                    fileName = videoNote?.optString("file_name", "").orEmpty()
                )
            }
            "messageSticker" -> {
                val sticker = content.optJSONObject("sticker")
                val thumbnailFile = sticker
                    ?.optJSONObject("thumbnail")
                    ?.let { thumbnail -> thumbnail.optFileObject("file", "photo") ?: thumbnail.findJsonFile() }
                parsedFile(
                    kind = MessageKind.Sticker,
                    file = sticker?.optFileObject("sticker", "file") ?: sticker.findJsonFile(),
                    thumbnailFile = thumbnailFile,
                    mimeType = sticker?.optString("mime_type", "").orEmpty(),
                    fileName = sticker?.optString("emoji", "").orEmpty().ifBlank {
                        sticker?.optString("set_id", "").orEmpty()
                    }
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
            MessageKind.Voice -> "Voice"
            MessageKind.VideoNote -> "Video message"
            MessageKind.Audio -> "Audio"
            MessageKind.Sticker -> "Sticker"
            MessageKind.Text -> ""
        }
    }

    private fun parsedFile(
        kind: MessageKind,
        file: JSONObject?,
        thumbnailFile: JSONObject? = null,
        mimeType: String = "",
        fileName: String = ""
    ): TdLibParsedMedia {
        val sizeBytes = file?.optLongAny("size", "expected_size") ?: 0L
        return TdLibParsedMedia(
            kind = kind,
            fileId = file?.optIntAny("id", "file_id") ?: 0,
            localPath = file?.localPath().orEmpty(),
            mimeType = mimeType,
            fileName = fileName,
            thumbnailFileId = thumbnailFile?.optIntAny("id", "file_id") ?: 0,
            thumbnailLocalPath = thumbnailFile?.localPath()?.takeIf { thumbnailFile.localDownloadCompleted() }.orEmpty(),
            downloadedPrefixBytes = file?.transferProgressBytes() ?: 0L,
            sizeMb = tdLibBytesToMb(sizeBytes)
        )
    }
}

private data class TdLibParsedCallReady(
    val protocol: TelegramCallProtocol = TelegramCallProtocol(),
    val servers: List<TelegramCallServer> = emptyList(),
    val config: String = "",
    val encryptionKey: ByteArray = byteArrayOf(),
    val emojis: List<String> = emptyList(),
    val allowP2p: Boolean = true,
    val customParameters: String = ""
)

private inline fun JSONArray?.ifNull(block: () -> JSONArray?): JSONArray? = this ?: block()

private fun JSONArray?.objectItems(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index) }
}

private fun JSONObject.isInMainChatList(): Boolean {
    val positions = optJSONArray("positions") ?: return false
    return positions.objectItems().any { position ->
        position.optJSONObject("list")?.optString("@type") == "chatListMain"
    }
}

private fun JSONArray?.stringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optString(index, "").takeIf { it.isNotBlank() }
    }
}

internal fun JSONObject?.tdLibFileObject(vararg keys: String): JSONObject? {
    if (this == null) return null
    for (key in keys) {
        optJSONObject(key)?.let { return it }
    }
    return null
}

private fun JSONObject?.optFileObject(vararg keys: String): JSONObject? = tdLibFileObject(*keys)

private fun JSONObject.pollQuestionText(): String {
    val question = opt("question")
    return when (question) {
        is JSONObject -> question.optString("text", "")
        is String -> question
        else -> ""
    }
}

private fun JSONObject.contactPreviewText(): String {
    val name = listOf(
        optString("first_name", ""),
        optString("last_name", "")
    ).filter { it.isNotBlank() }.joinToString(" ")
    val phone = optString("phone_number", "")
    return listOf(name.ifBlank { "Contact" }, phone)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
        .let { "Contact: $it" }
}

internal fun JSONObject?.findJsonFile(depth: Int = 0): JSONObject? {
    if (this == null || depth > 6) return null
    if (isJsonFileCandidate()) return this
    val names = keys()
    while (names.hasNext()) {
        when (val value = opt(names.next())) {
            is JSONObject -> value.findJsonFile(depth + 1)?.let { return it }
            is JSONArray -> value.findJsonFile(depth + 1)?.let { return it }
        }
    }
    return null
}

internal fun JSONArray.findJsonFile(depth: Int = 0): JSONObject? {
    if (depth > 6) return null
    for (index in 0 until length()) {
        when (val value = opt(index)) {
            is JSONObject -> value.findJsonFile(depth + 1)?.let { return it }
            is JSONArray -> value.findJsonFile(depth + 1)?.let { return it }
        }
    }
    return null
}

private fun JSONObject.isJsonFileCandidate(): Boolean {
    val hasFileId = optIntAny("id", "file_id") > 0
    val hasFileShape = has("local") || has("remote") || has("size") || has("expected_size") || has("local_path")
    return optString("@type") == "file" || (hasFileId && hasFileShape)
}

internal fun JSONObject.optIntAny(vararg keys: String): Int {
    for (key in keys) {
        when (val value = opt(key)) {
            is Number -> if (value.toInt() > 0) return value.toInt()
            is String -> value.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        }
    }
    return 0
}

internal fun JSONObject.optLongAny(vararg keys: String): Long {
    for (key in keys) {
        when (val value = opt(key)) {
            is Number -> if (value.toLong() > 0L) return value.toLong()
            is String -> value.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
        }
    }
    return 0L
}

internal fun JSONObject.localPath(): String {
    return optJSONObject("local")?.optString("path", "").orEmpty()
        .ifBlank { optString("local_path", "") }
}

internal fun JSONObject.localDownloadedPrefixBytes(): Long {
    return optJSONObject("local")?.optLongAny("downloaded_prefix_size", "downloaded_size") ?: 0L
}

internal fun JSONObject.remoteUploadedBytes(): Long {
    return optJSONObject("remote")?.optLongAny("uploaded_size", "uploadedSize") ?: 0L
}

internal fun JSONObject.transferProgressBytes(): Long {
    return maxOf(localDownloadedPrefixBytes(), remoteUploadedBytes())
}

internal fun JSONObject.localDownloadCompleted(): Boolean {
    return optJSONObject("local")?.optBoolean("is_downloading_completed", false) ?: false
}

internal fun JSONObject.syncState(): MessageSyncState {
    return when (optJSONObject("sending_state")?.optString("@type")) {
        "messageSendingStatePending" -> MessageSyncState.Sending
        "messageSendingStateFailed" -> MessageSyncState.Failed
        else -> MessageSyncState.Synced
    }
}

internal fun JSONArray.firstString(): String? {
    for (index in 0 until length()) {
        val value = optString(index, "")
        if (value.isNotBlank()) return value
    }
    return null
}

internal fun decodeTdLibBytes(value: Any?): ByteArray {
    return when (value) {
        is ByteArray -> value
        is String -> runCatching { Base64.getDecoder().decode(value) }
            .getOrElse { value.encodeToByteArray() }
        is JSONArray -> ByteArray(value.length()) { index -> value.optInt(index, 0).toByte() }
        else -> byteArrayOf()
    }
}
