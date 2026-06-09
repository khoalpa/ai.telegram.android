package ai.telegram.android.data.telegram

import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.MessageSyncState
import ai.telegram.android.data.TelegramChat
import ai.telegram.android.data.telegram.TelegramBotCommand
import ai.telegram.android.data.telegram.TelegramBotMenuButton
import ai.telegram.android.data.telegram.TelegramCall
import ai.telegram.android.data.telegram.TelegramInlineBotResults
import ai.telegram.android.data.telegram.TelegramActiveSession
import ai.telegram.android.data.telegram.TelegramBusinessChatLink
import ai.telegram.android.data.telegram.TelegramPremiumFeature
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
import android.util.Log
import java.io.File
import java.lang.reflect.Proxy
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

private const val MIN_POLL_OPTIONS = 2
private const val MAX_POLL_OPTIONS = 10
private const val DOWNLOAD_MESSAGE_MEDIA_EXTRA_PREFIX = "download_message_media:"
private const val CALL_LOG_TAG = "TelegramTdCall"
private val jsonReceiveLock = Any()

class TdLibReflectionClient(
    private val config: TdLibConfig,
    private val onStatus: (TdLibStatus) -> Unit,
    private val onChat: (TelegramChat) -> Unit,
    private val onSender: (TelegramSender) -> Unit,
    private val onMessage: (TelegramMessage) -> Unit,
    private val onContactsLoaded: (Int) -> Unit = {},
    private val onMessagesDeleted: (Long, List<Long>) -> Unit = { _, _ -> },
    private val onReadState: (TelegramReadStateUpdate) -> Unit = {},
    private val onMessageSync: (TelegramMessageSyncUpdate) -> Unit = {},
    private val onMessageContent: (TelegramMessageContentUpdate) -> Unit = {},
    private val onChatHistoryLoaded: (TelegramChatHistoryLoad) -> Unit = {},
    private val onChatAction: (TelegramChatActionUpdate) -> Unit = {},
    private val onPinnedMessage: (TelegramPinnedMessageUpdate) -> Unit = {},
    private val onFile: (TelegramMediaFile) -> Unit = {},
    private val onCall: (TelegramCall) -> Unit = {},
    private val onCallSignalingData: (TelegramCallSignalingData) -> Unit = {},
    private val onStory: (TelegramStory) -> Unit = {},
    private val onBotCommands: (Long, List<TelegramBotCommand>) -> Unit = { _, _ -> },
    private val onBotMenuButton: (TelegramBotMenuButton) -> Unit = {},
    private val onInlineBotResults: (TelegramInlineBotResults) -> Unit = {},
    private val onWebAppUrl: (TelegramWebAppUrl) -> Unit = {},
    private val onPrivacyRule: (TelegramPrivacyRuleSummary) -> Unit = {},
    private val onActiveSessions: (List<TelegramActiveSession>) -> Unit = {},
    private val onPremiumFeatures: (List<TelegramPremiumFeatureInfo>) -> Unit = {},
    private val onPremiumLimit: (TelegramPremiumLimitSummary) -> Unit = {},
    private val onStarBalance: (TelegramStarBalance) -> Unit = {},
    private val onStarTransactions: (List<TelegramStarTransaction>) -> Unit = {},
    private val onBusinessChatLinks: (List<TelegramBusinessChatLink>) -> Unit = {},
    private val onOpenChat: (Long) -> Unit = {},
    private val onChatInviteLink: (Long, String) -> Unit = { _, _ -> },
    private val callProtocolProvider: () -> TelegramCallProtocol = { TelegramCallProtocol() },
    private val onOperationError: (String) -> Unit = {}
) : TelegramClient {
    private val started = AtomicBoolean(false)
    private val tdlibParametersSent = AtomicBoolean(false)
    private var client: Any? = null
    private var clientClass: Class<*>? = null
    private var tdApiClass: Class<*>? = null
    private var resultHandlerClass: Class<*>? = null
    private var jsonClientId: Int? = null
    private var jsonTdLib: Any? = null
    private var jsonTdLibClass: Class<*>? = null
    private var jsonReceiveThread: Thread? = null
    private val jsonParser = TdLibJsonParser(onSender = onSender)
    private val objectParser = TdLibObjectParser(onSender = onSender)


    override fun start() {
        if (!config.isConfigured) {
            onStatus(TdLibStatus.NotConfigured)
            return
        }
        if (!started.compareAndSet(false, true)) return

        onStatus(TdLibStatus.Starting)
        try {
            clientClass = Class.forName("org.drinkless.tdlib.Client")
            tdApiClass = Class.forName("org.drinkless.tdlib.TdApi")
            resultHandlerClass = Class.forName("org.drinkless.tdlib.Client\$ResultHandler")
            val exceptionHandlerClass = Class.forName("org.drinkless.tdlib.Client\$ExceptionHandler")
            if (!hasRequiredTdApiClasses()) throw ClassNotFoundException("Incomplete org.drinkless.tdlib.TdApi binding")
            val updatesHandler = proxy(resultHandlerClass!!) { update -> handleUpdate(update) }
            val exceptionHandler = proxy(exceptionHandlerClass) { throwable -> onStatus(TdLibStatus.Error(throwable.toString())) }
            client = clientClass!!.getMethod("create", resultHandlerClass, exceptionHandlerClass, exceptionHandlerClass)
                .invoke(null, updatesHandler, exceptionHandler, exceptionHandler)
            newTdApiOrNull("GetAuthorizationState")?.let(::send)
        } catch (_: ClassNotFoundException) {
            startJsonClient()
        } catch (error: Throwable) {
            onStatus(TdLibStatus.Error(error.message ?: error::class.java.simpleName))
        }
    }

    override fun setPhoneNumber(phoneNumber: String) {
        if (sendJsonIfAvailable(
                json("setAuthenticationPhoneNumber")
                    .put("phone_number", phoneNumber)
                    .put("@extra", "auth:phone")
            )
        ) return
        val function = newTdApiOrNull("SetAuthenticationPhoneNumber") ?: return
        send(function.apply {
            setFieldIfPresent("phoneNumber", phoneNumber)
            setFieldIfPresent("settings", null)
        })
    }

    override fun checkCode(code: String) {
        if (sendJsonIfAvailable(
                json("checkAuthenticationCode")
                    .put("code", code)
                    .put("@extra", "auth:code")
            )
        ) return
        val function = newTdApiOrNull("CheckAuthenticationCode") ?: return
        send(function.apply {
            setFieldIfPresent("code", code)
        })
    }

    override fun checkPassword(password: String) {
        if (sendJsonIfAvailable(
                json("checkAuthenticationPassword")
                    .put("password", password)
                    .put("@extra", "auth:password")
            )
        ) return
        val function = newTdApiOrNull("CheckAuthenticationPassword") ?: return
        send(function.apply {
            setFieldIfPresent("password", password)
        })
    }

    override fun resendAuthenticationCode() {
        if (sendJsonIfAvailable(json("resendAuthenticationCode").put("@extra", "auth:resend"))) return
        val function = newTdApiOrNull("ResendAuthenticationCode") ?: return
        send(function)
    }

    override fun logOut() {
        if (sendJsonIfAvailable(json("logOut").put("@extra", "auth:logout"))) return
        val function = newTdApiOrNull("LogOut") ?: return
        send(function)
    }

    override fun loadMainChatList(limit: Int) {
        if (sendJsonIfAvailable(
                json("loadChats")
                    .put("chat_list", json("chatListMain"))
                    .put("limit", limit)
            )
        ) return
        val chatList = newTdApiOrNull("ChatListMain") ?: return
        val function = newTdApiOrNull("LoadChats") ?: return
        send(function.apply {
            setFieldIfPresent("chatList", chatList)
            setFieldIfPresent("limit", limit)
        })
    }

    override fun loadChat(chatId: Long) {
        if (chatId == 0L) return
        if (sendJsonIfAvailable(json("getChat").put("chat_id", chatId))) return
        val function = newTdApiOrNull("GetChat") ?: return
        send(
            function.apply { setFieldIfPresent("chatId", chatId) },
            onResult = { result -> objectParser.parseChat(result)?.let(onChat) }
        )
    }

    override fun loadChatHistory(chatId: Long, fromMessageId: Long, limit: Int) {
        val extra = "history:$chatId:$fromMessageId:$limit"
        if (sendJsonIfAvailable(
                json("getChatHistory")
                    .put("chat_id", chatId)
                    .put("from_message_id", fromMessageId)
                    .put("offset", 0)
                    .put("limit", limit)
                    .put("only_local", false)
                    .put("@extra", extra)
            )
        ) return
        val function = newTdApiOrNull("GetChatHistory") ?: return
        send(
            function = function.apply {
                setFieldIfPresent("chatId", chatId)
                setFieldIfPresent("fromMessageId", fromMessageId)
                setFieldIfPresent("offset", 0)
                setFieldIfPresent("limit", limit)
                setFieldIfPresent("onlyLocal", false)
            },
            onResult = { result ->
                val messages = objectParser.parseMessagesResult(result)
                messages.forEach(onMessage)
                onChatHistoryLoaded(
                    TelegramChatHistoryLoad(
                        chatId = chatId,
                        fromMessageId = fromMessageId,
                        messageIds = messages.map { it.id },
                        requestedLimit = limit
                    )
                )
            }
        )
    }

    override fun loadContacts(limit: Int) {
        if (sendJsonIfAvailable(json("getContacts").put("@extra", "contacts:$limit"))) return
        val function = newTdApiOrNull("GetContacts") ?: return
        send(function, onResult = { result ->
            val requestedCount = requestUsersFromResult(result, limit)
            onContactsLoaded(requestedCount)
        })
    }

    override fun searchChats(query: String, limit: Int) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        if (sendJsonIfAvailable(
                json("searchChats")
                    .put("query", trimmed)
                    .put("limit", limit)
            )
        ) return
        val function = newTdApiOrNull("SearchChats") ?: return
        send(
            function.apply {
                setFieldIfPresent("query", trimmed)
                setFieldIfPresent("limit", limit)
            },
            onResult = { result -> requestChatsFromResult(result, limit) }
        )
    }

    override fun searchPublicChats(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        if (sendJsonIfAvailable(json("searchPublicChats").put("query", trimmed))) return
        val function = newTdApiOrNull("SearchPublicChats") ?: return
        send(
            function.apply { setFieldIfPresent("query", trimmed) },
            onResult = { result -> requestChatsFromResult(result, 50) }
        )
    }

    override fun joinChannel(usernameOrLink: String) {
        val input = usernameOrLink.trim()
        if (input.isBlank()) return
        val inviteLink = input.takeIf(TdLibCommandMapper::isInviteLink)
        if (inviteLink != null) {
            joinChatByInviteLink(inviteLink)
            return
        }

        val username = TdLibCommandMapper.usernameForJoin(input)
            ?: return
        if (sendJsonIfAvailable(json("searchPublicChat").put("username", username).put("@extra", "join:$username"))) return
        val function = newTdApiOrNull("SearchPublicChat") ?: return
        send(
            function.apply { setFieldIfPresent("username", username) },
            onResult = { result ->
                objectParser.parseChat(result)?.let { chat ->
                    onChat(chat)
                    joinChat(chat.id)
                }
            }
        )
    }

    override fun openTelegramLink(link: String) {
        val input = link.trim()
        if (input.isBlank()) return
        val inviteLink = input.takeIf(TdLibCommandMapper::isInviteLink)
        if (inviteLink != null) {
            joinChatByInviteLink(inviteLink, openAfterJoin = true)
            return
        }

        val username = TdLibCommandMapper.extractTelegramUsername(input) ?: return
        if (sendJsonIfAvailable(json("searchPublicChat").put("username", username).put("@extra", "open:$username"))) return
        val function = newTdApiOrNull("SearchPublicChat") ?: return
        send(
            function.apply { setFieldIfPresent("username", username) },
            onResult = { result ->
                objectParser.parseChat(result)?.let { chat ->
                    onChat(chat)
                    onOpenChat(chat.id)
                    loadChatHistory(chat.id)
                }
            }
        )
    }

    fun joinChat(chatId: Long) {
        if (sendJsonIfAvailable(json("joinChat").put("chat_id", chatId))) return
        val function = newTdApiOrNull("JoinChat") ?: return
        send(function.apply { setFieldIfPresent("chatId", chatId) })
    }

    override fun leaveChat(chatId: Long) {
        if (sendJsonIfAvailable(json("leaveChat").put("chat_id", chatId))) return
        val function = newTdApiOrNull("LeaveChat") ?: return
        send(function.apply { setFieldIfPresent("chatId", chatId) })
    }

    override fun sendTextMessage(chatId: Long, text: String, options: MessageSendOptions) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (sendJsonIfAvailable(
                json("sendMessage")
                    .put("chat_id", chatId)
                    .put("options", messageSendOptionsJson(options))
                    .put("input_message_content", json("inputMessageText")
                        .put("text", json("formattedText")
                            .put("text", trimmed)
                            .put("entities", JSONArray())))
            )
        ) return
        val function = newTdApiOrNull("SendMessage") ?: return
        val inputMessageText = newTdApiOrNull("InputMessageText") ?: return
        val formattedText = newTdApiOrNull("FormattedText") ?: return
        formattedText.setFieldIfPresent("text", trimmed)
        formattedText.setFieldIfPresent("entities", emptyArray<Any>())
        inputMessageText.setFieldIfPresent("text", formattedText)
        function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("options", messageSendOptionsObject(options))
            setFieldIfPresent("inputMessageContent", inputMessageText)
        }
        send(function, onResult = { result -> objectParser.parseMessage(result)?.let(onMessage) })
    }

    override fun sendReplyTextMessage(
        chatId: Long,
        replyToMessageId: Long,
        text: String,
        options: MessageSendOptions
    ) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || replyToMessageId <= 0L) return
        if (sendJsonIfAvailable(
                json("sendMessage")
                    .put("chat_id", chatId)
                    .put("message_thread_id", 0)
                    .put("reply_to", json("inputMessageReplyToMessage")
                        .put("message_id", replyToMessageId))
                    .put("reply_to_message_id", replyToMessageId)
                    .put("options", messageSendOptionsJson(options))
                    .put("input_message_content", json("inputMessageText")
                        .put("text", json("formattedText")
                            .put("text", trimmed)
                            .put("entities", JSONArray())))
            )
        ) return
        val function = newTdApiOrNull("SendMessage") ?: return
        val inputMessageText = newTdApiOrNull("InputMessageText") ?: return
        val formattedText = newTdApiOrNull("FormattedText") ?: return
        formattedText.setFieldIfPresent("text", trimmed)
        formattedText.setFieldIfPresent("entities", emptyArray<Any>())
        inputMessageText.setFieldIfPresent("text", formattedText)
        function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("messageThreadId", 0L)
            setFieldIfPresent("replyToMessageId", replyToMessageId)
            setFieldIfPresent("replyTo", newInputMessageReplyToMessageOrNull(replyToMessageId))
            setFieldIfPresent("options", messageSendOptionsObject(options))
            setFieldIfPresent("inputMessageContent", inputMessageText)
        }
        send(function, onResult = { result -> objectParser.parseMessage(result)?.let(onMessage) })
    }

    override fun sendMediaMessage(
        chatId: Long,
        localPath: String,
        kind: MessageKind,
        caption: String,
        options: MessageSendOptions
    ) {
        val cleanPath = readableLocalPathOrReport(localPath) ?: return
        if (cleanPath.isBlank() || kind == MessageKind.Text) return
        val trimmedCaption = caption.trim()
        val jsonContent = when (kind) {
            MessageKind.Image -> json("inputMessagePhoto")
                .put("photo", json("inputFileLocal").put("path", cleanPath))
                .put("thumbnail", JSONObject.NULL)
                .put("added_sticker_file_ids", JSONArray())
                .put("width", 0)
                .put("height", 0)
                .put("caption", formattedTextJson(trimmedCaption))
                .put("show_caption_above_media", false)
                .put("has_spoiler", false)
                .put("self_destruct_type", JSONObject.NULL)
            MessageKind.Video -> json("inputMessageVideo")
                .put("video", json("inputFileLocal").put("path", cleanPath))
                .put("thumbnail", JSONObject.NULL)
                .put("added_sticker_file_ids", JSONArray())
                .put("duration", 0)
                .put("width", 0)
                .put("height", 0)
                .put("supports_streaming", true)
                .put("caption", formattedTextJson(trimmedCaption))
                .put("show_caption_above_media", false)
                .put("has_spoiler", false)
                .put("self_destruct_type", JSONObject.NULL)
            MessageKind.File -> json("inputMessageDocument")
                .put("document", json("inputFileLocal").put("path", cleanPath))
                .put("thumbnail", JSONObject.NULL)
                .put("disable_content_type_detection", false)
                .put("caption", formattedTextJson(trimmedCaption))
            MessageKind.Text -> return
        }
        if (sendJsonIfAvailable(
                json("sendMessage")
                    .put("chat_id", chatId)
                    .put("options", messageSendOptionsJson(options))
                    .put("input_message_content", jsonContent)
            )
        ) return

        val function = newTdApiOrNull("SendMessage") ?: return
        val inputContent = when (kind) {
            MessageKind.Image -> newTdApiOrNull("InputMessagePhoto")?.apply {
                setFieldIfPresent("photo", inputFileLocalOrNull(cleanPath))
                setFieldIfPresent("thumbnail", null)
                setFieldIfPresent("addedStickerFileIds", intArrayOf())
                setFieldIfPresent("width", 0)
                setFieldIfPresent("height", 0)
                setFieldIfPresent("caption", formattedTextObject(trimmedCaption))
                setFieldIfPresent("showCaptionAboveMedia", false)
                setFieldIfPresent("hasSpoiler", false)
                setFieldIfPresent("selfDestructType", null)
            }
            MessageKind.Video -> newTdApiOrNull("InputMessageVideo")?.apply {
                setFieldIfPresent("video", inputFileLocalOrNull(cleanPath))
                setFieldIfPresent("thumbnail", null)
                setFieldIfPresent("addedStickerFileIds", intArrayOf())
                setFieldIfPresent("duration", 0)
                setFieldIfPresent("width", 0)
                setFieldIfPresent("height", 0)
                setFieldIfPresent("supportsStreaming", true)
                setFieldIfPresent("caption", formattedTextObject(trimmedCaption))
                setFieldIfPresent("showCaptionAboveMedia", false)
                setFieldIfPresent("hasSpoiler", false)
                setFieldIfPresent("selfDestructType", null)
            }
            MessageKind.File -> newTdApiOrNull("InputMessageDocument")?.apply {
                setFieldIfPresent("document", inputFileLocalOrNull(cleanPath))
                setFieldIfPresent("thumbnail", null)
                setFieldIfPresent("disableContentTypeDetection", false)
                setFieldIfPresent("caption", formattedTextObject(trimmedCaption))
            }
            MessageKind.Text -> null
        } ?: return
        function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("options", messageSendOptionsObject(options))
            setFieldIfPresent("inputMessageContent", inputContent)
        }
        send(function, onResult = { result -> objectParser.parseMessage(result)?.let(onMessage) })
    }

    override fun sendPollMessage(
        chatId: Long,
        question: String,
        options: List<String>,
        isAnonymous: Boolean,
        allowMultipleAnswers: Boolean,
        sendOptions: MessageSendOptions
    ) {
        val cleanQuestion = question.trim()
        val cleanOptions = options
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_POLL_OPTIONS)
        if (chatId == 0L || cleanQuestion.isBlank() || cleanOptions.size < MIN_POLL_OPTIONS) return

        val jsonContent = json("inputMessagePoll")
            .put("question", formattedTextJson(cleanQuestion))
            .put("options", JSONArray(cleanOptions))
            .put("is_anonymous", isAnonymous)
            .put(
                "type",
                json("pollTypeRegular")
                    .put("allow_multiple_answers", allowMultipleAnswers)
            )
            .put("open_period", 0)
            .put("close_date", 0)
            .put("is_closed", false)
        if (sendJsonIfAvailable(
                json("sendMessage")
                    .put("chat_id", chatId)
                    .put("options", messageSendOptionsJson(sendOptions))
                    .put("input_message_content", jsonContent)
            )
        ) return

        val function = newTdApiOrNull("SendMessage") ?: return
        val inputContent = newTdApiOrNull("InputMessagePoll")?.apply {
            setFieldIfPresent("question", formattedTextObject(cleanQuestion))
            setFieldIfPresent("options", cleanOptions.toTypedArray())
            setFieldIfPresent("isAnonymous", isAnonymous)
            setFieldIfPresent(
                "type",
                newTdApiOrNull("PollTypeRegular")?.apply {
                    setFieldIfPresent("allowMultipleAnswers", allowMultipleAnswers)
                }
            )
            setFieldIfPresent("openPeriod", 0)
            setFieldIfPresent("closeDate", 0)
            setFieldIfPresent("isClosed", false)
        } ?: return
        function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("options", messageSendOptionsObject(sendOptions))
            setFieldIfPresent("inputMessageContent", inputContent)
        }
        send(function, onResult = { result -> objectParser.parseMessage(result)?.let(onMessage) })
    }

    override fun sendContactMessage(
        chatId: Long,
        firstName: String,
        lastName: String,
        phoneNumber: String,
        userId: Long,
        options: MessageSendOptions
    ) {
        val cleanFirstName = firstName.trim()
        val cleanLastName = lastName.trim()
        val cleanPhoneNumber = phoneNumber.trim()
        if (chatId == 0L || cleanFirstName.isBlank() || cleanPhoneNumber.isBlank()) return

        val jsonContent = json("inputMessageContact")
            .put("contact", contactJson(userId, cleanFirstName, cleanLastName, cleanPhoneNumber))
        if (sendJsonIfAvailable(
                json("sendMessage")
                    .put("chat_id", chatId)
                    .put("options", messageSendOptionsJson(options))
                    .put("input_message_content", jsonContent)
            )
        ) return

        val function = newTdApiOrNull("SendMessage") ?: return
        val inputContent = newTdApiOrNull("InputMessageContact")?.apply {
            setFieldIfPresent("contact", contactObject(userId, cleanFirstName, cleanLastName, cleanPhoneNumber))
        } ?: return
        function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("options", messageSendOptionsObject(options))
            setFieldIfPresent("inputMessageContent", inputContent)
        }
        send(function, onResult = { result -> objectParser.parseMessage(result)?.let(onMessage) })
    }

    override fun editTextMessage(chatId: Long, messageId: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || messageId <= 0L) return
        if (sendJsonIfAvailable(
                json("editMessageText")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
                    .put("reply_markup", JSONObject.NULL)
                    .put("input_message_content", json("inputMessageText")
                        .put("text", json("formattedText")
                            .put("text", trimmed)
                            .put("entities", JSONArray())))
            )
        ) return
        val function = newTdApiOrNull("EditMessageText") ?: return
        val inputMessageText = newTdApiOrNull("InputMessageText") ?: return
        val formattedText = newTdApiOrNull("FormattedText") ?: return
        formattedText.setFieldIfPresent("text", trimmed)
        formattedText.setFieldIfPresent("entities", emptyArray<Any>())
        inputMessageText.setFieldIfPresent("text", formattedText)
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("messageId", messageId)
            setFieldIfPresent("replyMarkup", null)
            setFieldIfPresent("inputMessageContent", inputMessageText)
        })
    }

    override fun deleteMessages(chatId: Long, messageIds: List<Long>, revoke: Boolean) {
        val cleanMessageIds = messageIds.distinct().filter { it > 0L }
        if (cleanMessageIds.isEmpty()) return
        if (sendJsonIfAvailable(
                json("deleteMessages")
                    .put("chat_id", chatId)
                    .put("message_ids", JSONArray(cleanMessageIds))
                    .put("revoke", revoke)
            )
        ) return
        val function = newTdApiOrNull("DeleteMessages") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("messageIds", cleanMessageIds.toLongArray())
            setFieldIfPresent("revoke", revoke)
        })
    }

    override fun forwardMessages(toChatId: Long, fromChatId: Long, messageIds: List<Long>) {
        val cleanMessageIds = messageIds.distinct().filter { it > 0L }
        if (toChatId == 0L || fromChatId == 0L || cleanMessageIds.isEmpty()) return
        if (sendJsonIfAvailable(
                json("forwardMessages")
                    .put("chat_id", toChatId)
                    .put("message_thread_id", 0)
                    .put("from_chat_id", fromChatId)
                    .put("message_ids", JSONArray(cleanMessageIds))
                    .put("send_copy", false)
                    .put("remove_caption", false)
            )
        ) return
        val function = newTdApiOrNull("ForwardMessages") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", toChatId)
            setFieldIfPresent("messageThreadId", 0L)
            setFieldIfPresent("fromChatId", fromChatId)
            setFieldIfPresent("messageIds", cleanMessageIds.toLongArray())
            setFieldIfPresent("sendCopy", false)
            setFieldIfPresent("removeCaption", false)
        })
    }

    override fun pinMessage(
        chatId: Long,
        messageId: Long,
        disableNotification: Boolean,
        onlyForSelf: Boolean
    ) {
        if (chatId == 0L || messageId <= 0L) return
        if (sendJsonIfAvailable(
                json("pinChatMessage")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
                    .put("disable_notification", disableNotification)
                    .put("only_for_self", onlyForSelf)
            )
        ) return
        val function = newTdApiOptional("PinChatMessage") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("messageId", messageId)
            setFieldIfPresent("disableNotification", disableNotification)
            setFieldIfPresent("onlyForSelf", onlyForSelf)
        })
    }

    override fun unpinMessage(chatId: Long, messageId: Long) {
        if (chatId == 0L || messageId <= 0L) return
        if (sendJsonIfAvailable(
                json("unpinChatMessage")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
            )
        ) return
        val function = newTdApiOptional("UnpinChatMessage") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("messageId", messageId)
        })
    }

    override fun unpinAllChatMessages(chatId: Long) {
        if (chatId == 0L) return
        if (sendJsonIfAvailable(json("unpinAllChatMessages").put("chat_id", chatId))) return
        val function = newTdApiOptional("UnpinAllChatMessages") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
        })
    }

    override fun addMessageReaction(chatId: Long, messageId: Long, emoji: String, isBig: Boolean) {
        val cleanEmoji = emoji.trim()
        if (chatId == 0L || messageId <= 0L || cleanEmoji.isBlank()) return
        if (sendJsonIfAvailable(
                json("addMessageReaction")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
                    .put("reaction_type", reactionTypeEmojiJson(cleanEmoji))
                    .put("is_big", isBig)
                    .put("update_recent_reactions", true)
            )
        ) return
        val function = newTdApiOptional("AddMessageReaction") ?: return
        val reactionType = reactionTypeEmojiObject(cleanEmoji) ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("messageId", messageId)
            setFieldIfPresent("reactionType", reactionType)
            setFieldIfPresent("isBig", isBig)
            setFieldIfPresent("updateRecentReactions", true)
        })
    }

    override fun createPrivateChat(userId: Long) {
        if (sendJsonIfAvailable(json("createPrivateChat").put("user_id", userId).put("force", false))) return
        val function = newTdApiOrNull("CreatePrivateChat") ?: return
        send(
            function.apply {
                setFieldIfPresent("userId", userId)
                setFieldIfPresent("force", false)
            },
            onResult = { result -> objectParser.parseChat(result)?.let(onChat) }
        )
    }

    override fun createSecretChat(userId: Long) {
        if (userId == 0L) return
        if (sendJsonIfAvailable(json("createNewSecretChat").put("user_id", userId))) return
        val function = newTdApiOrNull("CreateNewSecretChat") ?: return
        send(
            function.apply {
                setFieldIfPresent("userId", userId)
            },
            onResult = { result -> objectParser.parseChat(result)?.let(onChat) }
        )
    }

    override fun createCall(userId: Long, isVideo: Boolean) {
        if (userId == 0L) return
        Log.d(CALL_LOG_TAG, "createCall request userId=$userId video=$isVideo")
        val extra = "call:create:$userId:${if (isVideo) "video" else "voice"}"
        if (
            sendJsonIfAvailable(
                json("createCall")
                    .put("user_id", userId)
                    .put("protocol", callProtocolJson())
                    .put("is_video", isVideo)
                    .put("@extra", extra)
            )
        ) return
        val function = newTdApiOptional("CreateCall") ?: run {
            onOperationError("TDLib call API is unavailable in this binding.")
            return
        }
        send(
            function.apply {
                setFieldIfPresent("userId", userId)
                setFieldIfPresent("protocol", callProtocolObject())
                setFieldIfPresent("isVideo", isVideo)
            },
            onResult = { result ->
                Log.d(CALL_LOG_TAG, "createCall result=${result?.javaClass?.simpleName ?: "null"}")
                objectParser.parseCall(result)?.let(onCall)
            }
        )
    }

    override fun acceptCall(callId: Int) {
        if (callId == 0) return
        if (
            sendJsonIfAvailable(
                json("acceptCall")
                    .put("call_id", callId)
                    .put("protocol", callProtocolJson())
            )
        ) return
        val function = newTdApiOptional("AcceptCall") ?: run {
            onOperationError("TDLib call API is unavailable in this binding.")
            return
        }
        send(function.apply {
            setFieldIfPresent("callId", callId)
            setFieldIfPresent("protocol", callProtocolObject())
        })
    }

    override fun discardCall(callId: Int, isDisconnected: Boolean) {
        if (callId == 0) return
        if (
            sendJsonIfAvailable(
                json("discardCall")
                    .put("call_id", callId)
                    .put("is_disconnected", isDisconnected)
                    .put("duration", 0)
                    .put("connection_id", 0L)
            )
        ) return
        val function = newTdApiOptional("DiscardCall") ?: run {
            onOperationError("TDLib call API is unavailable in this binding.")
            return
        }
        send(function.apply {
            setFieldIfPresent("callId", callId)
            setFieldIfPresent("isDisconnected", isDisconnected)
            setFieldIfPresent("duration", 0)
            setFieldIfPresent("connectionId", 0L)
        })
    }

    override fun sendCallSignalingData(callId: Int, data: ByteArray) {
        if (callId == 0 || data.isEmpty()) return
        if (
            sendJsonIfAvailable(
                json("sendCallSignalingData")
                    .put("call_id", callId)
                    .put("data", Base64.getEncoder().encodeToString(data))
            )
        ) return
        val function = newTdApiOptional("SendCallSignalingData") ?: return
        send(function.apply {
            setFieldIfPresent("callId", callId)
            setFieldIfPresent("data", data)
        })
    }

    override fun loadActiveStories(chatId: Long) {
        if (chatId == 0L) return
        if (
            sendJsonIfAvailable(
                json("getChatActiveStories")
                    .put("chat_id", chatId)
                    .put("@extra", "stories:$chatId")
            )
        ) return
        val function = newTdApiOptional("GetChatActiveStories") ?: run {
            onOperationError("TDLib stories API is unavailable in this binding.")
            return
        }
        send(
            function.apply {
                setFieldIfPresent("chatId", chatId)
            },
            onResult = { result -> objectParser.parseStoriesResult(result).forEach(onStory) }
        )
    }

    override fun loadStory(chatId: Long, storyId: Int) {
        if (chatId == 0L || storyId == 0) return
        if (
            sendJsonIfAvailable(
                json("getStory")
                    .put("story_sender_chat_id", chatId)
                    .put("story_id", storyId)
                    .put("only_local", false)
                    .put("@extra", "story:$chatId:$storyId")
            )
        ) return
        val function = newTdApiOptional("GetStory") ?: run {
            onOperationError("TDLib stories API is unavailable in this binding.")
            return
        }
        send(
            function.apply {
                setFieldIfPresent("storySenderChatId", chatId)
                setFieldIfPresent("storyId", storyId)
                setFieldIfPresent("onlyLocal", false)
            },
            onResult = { result -> objectParser.parseStory(result, defaultChatId = chatId)?.let(onStory) }
        )
    }

    override fun viewStory(chatId: Long, storyId: Int) {
        if (chatId == 0L || storyId == 0) return
        if (
            sendJsonIfAvailable(
                json("viewStory")
                    .put("story_sender_chat_id", chatId)
                    .put("story_id", storyId)
            )
        ) return
        val function = newTdApiOptional("ViewStory") ?: return
        send(function.apply {
            setFieldIfPresent("storySenderChatId", chatId)
            setFieldIfPresent("storyId", storyId)
        })
    }

    override fun postStory(chatId: Long, localPath: String, kind: MessageKind, caption: String) {
        val cleanPath = readableLocalPathOrReport(localPath) ?: return
        if (chatId == 0L || cleanPath.isBlank()) return
        val trimmedCaption = caption.trim()
        val contentJson = inputStoryContentJson(cleanPath, kind) ?: return
        if (
            sendJsonIfAvailable(
                json("postStory")
                    .put("chat_id", chatId)
                    .put("content", contentJson)
                    .put("areas", JSONArray())
                    .put("caption", formattedTextJson(trimmedCaption))
                    .put("privacy_settings", json("storyPrivacySettingsContacts"))
                    .put("active_period", 24 * 60 * 60)
                    .put("from_story_full_id", JSONObject.NULL)
                    .put("is_posted_to_chat_page", false)
                    .put("protect_content", false)
            )
        ) return
        val function = newTdApiOptional("PostStory") ?: run {
            onOperationError("TDLib stories API is unavailable in this binding.")
            return
        }
        val content = inputStoryContentObject(cleanPath, kind) ?: return
        send(
            function.apply {
                setFieldIfPresent("chatId", chatId)
                setFieldIfPresent("content", content)
                setFieldIfPresent("areas", emptyArray<Any>())
                setFieldIfPresent("caption", formattedTextObject(trimmedCaption))
                setFieldIfPresent("privacySettings", newTdApiOptional("StoryPrivacySettingsContacts"))
                setFieldIfPresent("activePeriod", 24 * 60 * 60)
                setFieldIfPresent("fromStoryFullId", null)
                setFieldIfPresent("isPostedToChatPage", false)
                setFieldIfPresent("protectContent", false)
            },
            onResult = { result -> objectParser.parseStory(result, defaultChatId = chatId)?.let(onStory) }
        )
    }

    override fun deleteStory(chatId: Long, storyId: Int) {
        if (chatId == 0L || storyId == 0) return
        if (
            sendJsonIfAvailable(
                json("deleteStory")
                    .put("story_sender_chat_id", chatId)
                    .put("story_id", storyId)
            )
        ) return
        val function = newTdApiOptional("DeleteStory") ?: run {
            onOperationError("TDLib stories API is unavailable in this binding.")
            return
        }
        send(function.apply {
            setFieldIfPresent("storySenderChatId", chatId)
            setFieldIfPresent("storyId", storyId)
        })
        onStory(TelegramStory(chatId = chatId, id = storyId, state = TelegramStoryState.Deleted))
    }

    override fun searchBot(usernameOrLink: String) {
        val input = usernameOrLink.trim()
        val username = TdLibCommandMapper.extractTelegramUsername(input) ?: input.removePrefix("@")
        if (username.isBlank()) return
        if (sendJsonIfAvailable(json("searchPublicChat").put("username", username).put("@extra", "open_bot:$username"))) return
        val function = newTdApiOrNull("SearchPublicChat") ?: return
        send(
            function.apply { setFieldIfPresent("username", username) },
            onResult = { result ->
                objectParser.parseChat(result)?.let { chat ->
                    onChat(chat)
                    onOpenChat(chat.id)
                    loadChatHistory(chat.id)
                }
            }
        )
    }

    override fun startBot(botUserId: Long, chatId: Long, parameter: String) {
        if (botUserId <= 0L || chatId == 0L) return
        if (sendJsonIfAvailable(
                json("sendBotStartMessage")
                    .put("bot_user_id", botUserId)
                    .put("chat_id", chatId)
                    .put("parameter", parameter.trim())
            )
        ) return
        val function = newTdApiOrNull("SendBotStartMessage") ?: return
        send(
            function.apply {
                setFieldIfPresent("botUserId", botUserId)
                setFieldIfPresent("chatId", chatId)
                setFieldIfPresent("parameter", parameter.trim())
            },
            onResult = { result -> objectParser.parseMessage(result)?.let(onMessage) }
        )
    }

    override fun loadBotCommands(botUserId: Long) {
        if (botUserId <= 0L) return
        val extra = "bot_commands:$botUserId"
        if (sendJsonIfAvailable(
                json("getCommands")
                    .put("scope", json("botCommandScopeChat").put("chat_id", botUserId))
                    .put("language_code", "")
                    .put("@extra", extra)
            )
        ) return
        val function = newTdApiOrNull("GetCommands") ?: return
        val scope = newTdApiOptional("BotCommandScopeChat") ?: newTdApiOptional("BotCommandScopeDefault")
        send(
            function.apply {
                scope?.setFieldIfPresent("chatId", botUserId)
                setFieldIfPresent("scope", scope)
                setFieldIfPresent("languageCode", "")
            },
            onResult = { result -> onBotCommands(botUserId, objectParser.parseBotCommandsResult(result, botUserId)) }
        )
    }

    override fun loadBotMenuButton(botUserId: Long) {
        if (botUserId <= 0L) return
        val extra = "bot_menu:$botUserId"
        if (sendJsonIfAvailable(json("getBotMenuButton").put("user_id", botUserId).put("@extra", extra))) return
        val function = newTdApiOrNull("GetBotMenuButton") ?: return
        send(
            function.apply { setFieldIfPresent("userId", botUserId) },
            onResult = { result -> objectParser.parseBotMenuButton(result, botUserId)?.let(onBotMenuButton) }
        )
    }

    override fun requestInlineBotResults(botUserId: Long, chatId: Long, query: String, offset: String) {
        if (botUserId <= 0L || chatId == 0L) return
        val extra = "inline_results:$botUserId"
        if (sendJsonIfAvailable(
                json("getInlineQueryResults")
                    .put("bot_user_id", botUserId)
                    .put("chat_id", chatId)
                    .put("user_location", JSONObject.NULL)
                    .put("query", query)
                    .put("offset", offset)
                    .put("@extra", extra)
            )
        ) return
        val function = newTdApiOrNull("GetInlineQueryResults") ?: return
        send(
            function.apply {
                setFieldIfPresent("botUserId", botUserId)
                setFieldIfPresent("chatId", chatId)
                setFieldIfPresent("userLocation", null)
                setFieldIfPresent("query", query)
                setFieldIfPresent("offset", offset)
            },
            onResult = { result -> onInlineBotResults(objectParser.parseInlineBotResults(result, botUserId)) }
        )
    }

    override fun sendInlineBotResult(chatId: Long, queryId: Long, resultId: String, hideViaBot: Boolean) {
        val cleanResultId = resultId.trim()
        if (chatId == 0L || queryId == 0L || cleanResultId.isBlank()) return
        if (sendJsonIfAvailable(
                json("sendInlineQueryResultMessage")
                    .put("chat_id", chatId)
                    .put("message_thread_id", 0L)
                    .put("reply_to", JSONObject.NULL)
                    .put("options", messageSendOptionsJson(MessageSendOptions()))
                    .put("query_id", queryId)
                    .put("result_id", cleanResultId)
                    .put("hide_via_bot", hideViaBot)
            )
        ) return
        val function = newTdApiOrNull("SendInlineQueryResultMessage") ?: return
        send(
            function.apply {
                setFieldIfPresent("chatId", chatId)
                setFieldIfPresent("messageThreadId", 0L)
                setFieldIfPresent("replyTo", null)
                setFieldIfPresent("options", messageSendOptionsObject(MessageSendOptions()))
                setFieldIfPresent("queryId", queryId)
                setFieldIfPresent("resultId", cleanResultId)
                setFieldIfPresent("hideViaBot", hideViaBot)
            },
            onResult = { result -> objectParser.parseMessage(result)?.let(onMessage) }
        )
    }

    override fun clickBotCallbackButton(chatId: Long, messageId: Long, payload: String) {
        val cleanPayload = payload.trim()
        if (chatId == 0L || messageId == 0L || cleanPayload.isBlank()) return
        if (sendJsonIfAvailable(
                json("getCallbackQueryAnswer")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
                    .put("payload", json("callbackQueryPayloadData").put("data", cleanPayload))
            )
        ) return
        val function = newTdApiOrNull("GetCallbackQueryAnswer") ?: return
        val callbackPayload = newTdApiOrNull("CallbackQueryPayloadData") ?: return
        send(function.apply {
            callbackPayload.setFieldIfPresent("data", cleanPayload.toByteArray(Charsets.UTF_8))
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("messageId", messageId)
            setFieldIfPresent("payload", callbackPayload)
        })
    }

    override fun openBotWebApp(botUserId: Long, chatId: Long, url: String) {
        val cleanUrl = url.trim()
        if (botUserId <= 0L || chatId == 0L || cleanUrl.isBlank()) return
        val extra = "web_app:$botUserId"
        if (sendJsonIfAvailable(
                json("getWebAppUrl")
                    .put("bot_user_id", botUserId)
                    .put("chat_id", chatId)
                    .put("url", cleanUrl)
                    .put("theme", JSONObject.NULL)
                    .put("application_name", "ai.telegram.android")
                    .put("@extra", extra)
            )
        ) return
        val function = newTdApiOrNull("GetWebAppUrl") ?: return
        send(
            function.apply {
                setFieldIfPresent("botUserId", botUserId)
                setFieldIfPresent("chatId", chatId)
                setFieldIfPresent("url", cleanUrl)
                setFieldIfPresent("theme", null)
                setFieldIfPresent("applicationName", "ai.telegram.android")
            },
            onResult = { result -> objectParser.parseWebAppUrl(result, botUserId)?.let(onWebAppUrl) }
        )
    }

    override fun sendWebAppData(botUserId: Long, buttonText: String, data: String) {
        val cleanText = buttonText.trim()
        val cleanData = data.trim()
        if (botUserId <= 0L || cleanText.isBlank() || cleanData.isBlank()) return
        if (sendJsonIfAvailable(
                json("sendWebAppData")
                    .put("bot_user_id", botUserId)
                    .put("button_text", cleanText)
                    .put("data", cleanData)
            )
        ) return
        val function = newTdApiOrNull("SendWebAppData") ?: return
        send(function.apply {
            setFieldIfPresent("botUserId", botUserId)
            setFieldIfPresent("buttonText", cleanText)
            setFieldIfPresent("data", cleanData)
        })
    }

    override fun updateProfile(firstName: String, lastName: String, bio: String, username: String) {
        val cleanFirstName = firstName.trim()
        val cleanLastName = lastName.trim()
        val cleanBio = bio.trim()
        val cleanUsername = username.trim().removePrefix("@")
        if (cleanFirstName.isNotBlank()) {
            if (!sendJsonIfAvailable(
                    json("setName")
                        .put("first_name", cleanFirstName)
                        .put("last_name", cleanLastName)
                )
            ) {
                newTdApiOrNull("SetName")?.let { function ->
                    send(function.apply {
                        setFieldIfPresent("firstName", cleanFirstName)
                        setFieldIfPresent("lastName", cleanLastName)
                    })
                }
            }
        }
        if (cleanBio.isNotBlank()) {
            if (!sendJsonIfAvailable(json("setBio").put("bio", cleanBio))) {
                newTdApiOrNull("SetBio")?.let { function ->
                    send(function.apply { setFieldIfPresent("bio", cleanBio) })
                }
            }
        }
        if (cleanUsername.isNotBlank()) {
            if (!sendJsonIfAvailable(json("setUsername").put("username", cleanUsername))) {
                newTdApiOrNull("SetUsername")?.let { function ->
                    send(function.apply { setFieldIfPresent("username", cleanUsername) })
                }
            }
        }
    }

    override fun setProfilePhoto(localPath: String) {
        val cleanPath = readableLocalPathOrReport(localPath) ?: return
        if (cleanPath.isBlank()) return
        if (sendJsonIfAvailable(
                json("setProfilePhoto")
                    .put("photo", json("inputChatPhotoStatic").put("photo", inputFileLocalJson(cleanPath)))
                    .put("is_public", false)
            )
        ) return
        val function = newTdApiOrNull("SetProfilePhoto") ?: return
        send(function.apply {
            setFieldIfPresent("photo", inputChatPhotoStaticObject(cleanPath))
            setFieldIfPresent("isPublic", false)
        })
    }

    override fun loadPrivacySetting(setting: TelegramPrivacySetting) {
        val extra = "privacy:${setting.name}"
        if (sendJsonIfAvailable(
                json("getUserPrivacySettingRules")
                    .put("setting", privacySettingJson(setting))
                    .put("@extra", extra)
            )
        ) return
        val function = newTdApiOrNull("GetUserPrivacySettingRules") ?: return
        send(
            function.apply { setFieldIfPresent("setting", privacySettingObject(setting)) },
            onResult = { result -> onPrivacyRule(objectParser.parsePrivacyRulesResult(result, setting)) }
        )
    }

    override fun setPrivacySetting(setting: TelegramPrivacySetting, preset: TelegramPrivacyPreset) {
        if (sendJsonIfAvailable(
                json("setUserPrivacySettingRules")
                    .put("setting", privacySettingJson(setting))
                    .put("rules", privacyRulesJson(preset))
            )
        ) return
        val function = newTdApiOrNull("SetUserPrivacySettingRules") ?: return
        send(function.apply {
            setFieldIfPresent("setting", privacySettingObject(setting))
            setFieldIfPresent("rules", privacyRulesObject(preset))
        })
        onPrivacyRule(TelegramPrivacyRuleSummary(setting = setting, preset = preset))
    }

    override fun loadActiveSessions() {
        if (sendJsonIfAvailable(json("getActiveSessions"))) return
        val function = newTdApiOrNull("GetActiveSessions") ?: return
        send(function, onResult = { result -> onActiveSessions(objectParser.parseActiveSessions(result)) })
    }

    override fun terminateSession(sessionId: Long) {
        if (sessionId == 0L) return
        if (sendJsonIfAvailable(json("terminateSession").put("session_id", sessionId))) return
        val function = newTdApiOrNull("TerminateSession") ?: return
        send(function.apply { setFieldIfPresent("sessionId", sessionId) })
    }

    override fun terminateAllOtherSessions() {
        if (sendJsonIfAvailable(json("terminateAllOtherSessions"))) return
        newTdApiOrNull("TerminateAllOtherSessions")?.let(::send)
    }

    override fun loadPremiumFeatures() {
        if (sendJsonIfAvailable(json("getPremiumFeatures").put("source", json("premiumSourceSettings")))) return
        val function = newTdApiOptional("GetPremiumFeatures") ?: run {
            onOperationError("TDLib premium API is unavailable in this binding.")
            return
        }
        send(
            function.apply {
                setFieldIfPresent("source", newTdApiOptional("PremiumSourceSettings"))
            },
            onResult = { result -> onPremiumFeatures(objectParser.parsePremiumFeatures(result)) }
        )
    }

    override fun viewPremiumFeature(feature: TelegramPremiumFeature) {
        if (sendJsonIfAvailable(json("viewPremiumFeature").put("feature", premiumFeatureJson(feature)))) return
        val function = newTdApiOptional("ViewPremiumFeature") ?: return
        send(function.apply {
            setFieldIfPresent("feature", premiumFeatureObject(feature))
        })
    }

    override fun loadPremiumLimit(limitType: TelegramPremiumLimitType) {
        val extra = "premium_limit:${limitType.name}"
        if (sendJsonIfAvailable(
                json("getPremiumLimit")
                    .put("limit_type", premiumLimitTypeJson(limitType))
                    .put("@extra", extra)
            )
        ) return
        val function = newTdApiOptional("GetPremiumLimit") ?: run {
            onOperationError("TDLib premium API is unavailable in this binding.")
            return
        }
        send(
            function.apply { setFieldIfPresent("limitType", premiumLimitTypeObject(limitType)) },
            onResult = { result -> onPremiumLimit(objectParser.parsePremiumLimit(result, limitType)) }
        )
    }

    override fun loadStarBalance() {
        if (sendJsonIfAvailable(json("getStarBalance"))) return
        val function = newTdApiOptional("GetStarBalance") ?: run {
            onOperationError("TDLib Stars API is unavailable in this binding.")
            return
        }
        send(function, onResult = { result -> objectParser.parseStarBalance(result)?.let(onStarBalance) })
    }

    override fun loadStarTransactions(offset: String, limit: Int) {
        if (sendJsonIfAvailable(
                json("getStarTransactions")
                    .put("owner_id", JSONObject.NULL)
                    .put("subscription_id", "")
                    .put("direction", JSONObject.NULL)
                    .put("offset", offset)
                    .put("limit", limit)
            )
        ) return
        val function = newTdApiOptional("GetStarTransactions") ?: run {
            onOperationError("TDLib Stars API is unavailable in this binding.")
            return
        }
        send(
            function.apply {
                setFieldIfPresent("ownerId", null)
                setFieldIfPresent("subscriptionId", "")
                setFieldIfPresent("direction", null)
                setFieldIfPresent("offset", offset)
                setFieldIfPresent("limit", limit)
            },
            onResult = { result -> onStarTransactions(objectParser.parseStarTransactions(result)) }
        )
    }

    override fun loadBusinessChatLinks() {
        if (sendJsonIfAvailable(json("getBusinessChatLinks"))) return
        val function = newTdApiOptional("GetBusinessChatLinks") ?: run {
            onOperationError("TDLib Business API is unavailable in this binding.")
            return
        }
        send(function, onResult = { result -> onBusinessChatLinks(objectParser.parseBusinessChatLinks(result)) })
    }

    override fun createBusinessChatLink(message: String) {
        val cleanMessage = message.trim()
        if (cleanMessage.isBlank()) return
        if (sendJsonIfAvailable(
                json("createBusinessChatLink")
                    .put("link_info", json("inputBusinessChatLink")
                        .put("message", formattedTextJson(cleanMessage))
                        .put("title", ""))
            )
        ) return
        val function = newTdApiOptional("CreateBusinessChatLink") ?: run {
            onOperationError("TDLib Business API is unavailable in this binding.")
            return
        }
        send(
            function.apply {
                setFieldIfPresent("linkInfo", inputBusinessChatLinkObject(cleanMessage))
            },
            onResult = { result -> objectParser.parseBusinessChatLink(result)?.let { onBusinessChatLinks(listOf(it)) } }
        )
    }

    override fun deleteBusinessChatLink(link: String) {
        val cleanLink = link.trim()
        if (cleanLink.isBlank()) return
        if (sendJsonIfAvailable(json("deleteBusinessChatLink").put("link", cleanLink))) return
        val function = newTdApiOptional("DeleteBusinessChatLink") ?: return
        send(function.apply { setFieldIfPresent("link", cleanLink) })
    }

    override fun setUserBlocked(userId: Long, blocked: Boolean) {
        if (userId == 0L) return
        if (sendJsonIfAvailable(
                json("setMessageSenderBlockList")
                    .put("sender_id", messageSenderUserJson(userId))
                    .put("block_list", if (blocked) json("blockListMain") else JSONObject.NULL)
            )
        ) return
        val function = newTdApiOrNull("SetMessageSenderBlockList") ?: return
        send(function.apply {
            setFieldIfPresent("senderId", messageSenderUserObject(userId))
            setFieldIfPresent("blockList", if (blocked) newTdApiOrNull("BlockListMain") else null)
        })
    }

    override fun addContact(userId: Long, firstName: String, lastName: String, phoneNumber: String) {
        val cleanFirstName = firstName.trim()
        val cleanLastName = lastName.trim()
        val cleanPhoneNumber = phoneNumber.trim()
        if (userId == 0L || cleanFirstName.isBlank()) return
        if (sendJsonIfAvailable(
                json("addContact")
                    .put("contact", contactJson(userId, cleanFirstName, cleanLastName, cleanPhoneNumber))
                    .put("share_phone_number", false)
            )
        ) return
        val function = newTdApiOrNull("AddContact") ?: return
        send(function.apply {
            setFieldIfPresent("contact", contactObject(userId, cleanFirstName, cleanLastName, cleanPhoneNumber))
            setFieldIfPresent("sharePhoneNumber", false)
        })
    }

    override fun deleteContact(userId: Long) {
        if (userId == 0L) return
        if (sendJsonIfAvailable(json("deleteContacts").put("user_ids", JSONArray(listOf(userId))))) return
        val function = newTdApiOrNull("DeleteContacts") ?: return
        send(function.apply {
            setFieldIfPresent("userIds", longArrayOf(userId))
        })
    }

    override fun createBasicGroupChat(userIds: List<Long>, title: String) {
        val cleanTitle = title.trim()
        val cleanUserIds = userIds.distinct().filter { it > 0L }
        if (cleanTitle.isBlank() || cleanUserIds.isEmpty()) return
        if (sendJsonIfAvailable(
                json("createNewBasicGroupChat")
                    .put("user_ids", JSONArray(cleanUserIds))
                    .put("title", cleanTitle)
                    .put("message_auto_delete_time", 0)
            )
        ) return
        val function = newTdApiOrNull("CreateNewBasicGroupChat") ?: return
        send(
            function.apply {
                setFieldIfPresent("userIds", cleanUserIds.toLongArray())
                setFieldIfPresent("title", cleanTitle)
                setFieldIfPresent("messageAutoDeleteTime", 0)
            },
            onResult = { result -> objectParser.parseChat(result)?.let(onChat) }
        )
    }

    override fun createChannel(title: String, description: String) {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim().take(255)
        if (cleanTitle.isBlank()) return
        if (sendJsonIfAvailable(
                json("createNewSupergroupChat")
                    .put("title", cleanTitle)
                    .put("is_forum", false)
                    .put("is_channel", true)
                    .put("description", cleanDescription)
                    .put("location", JSONObject.NULL)
                    .put("message_auto_delete_time", 0)
                    .put("for_import", false)
            )
        ) return
        val function = newTdApiOrNull("CreateNewSupergroupChat") ?: return
        send(
            function.apply {
                setFieldIfPresent("title", cleanTitle)
                setFieldIfPresent("isForum", false)
                setFieldIfPresent("isChannel", true)
                setFieldIfPresent("description", cleanDescription)
                setFieldIfPresent("location", null)
                setFieldIfPresent("messageAutoDeleteTime", 0)
                setFieldIfPresent("forImport", false)
            },
            onResult = { result -> objectParser.parseChat(result)?.let(onChat) }
        )
    }

    override fun createForumGroup(title: String, description: String) {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim().take(255)
        if (cleanTitle.isBlank()) return
        if (sendJsonIfAvailable(
                json("createNewSupergroupChat")
                    .put("title", cleanTitle)
                    .put("is_forum", true)
                    .put("is_channel", false)
                    .put("description", cleanDescription)
                    .put("location", JSONObject.NULL)
                    .put("message_auto_delete_time", 0)
                    .put("for_import", false)
            )
        ) return
        val function = newTdApiOrNull("CreateNewSupergroupChat") ?: return
        send(
            function.apply {
                setFieldIfPresent("title", cleanTitle)
                setFieldIfPresent("isForum", true)
                setFieldIfPresent("isChannel", false)
                setFieldIfPresent("description", cleanDescription)
                setFieldIfPresent("location", null)
                setFieldIfPresent("messageAutoDeleteTime", 0)
                setFieldIfPresent("forImport", false)
            },
            onResult = { result -> objectParser.parseChat(result)?.let(onChat) }
        )
    }

    override fun setChatTitle(chatId: Long, title: String) {
        val cleanTitle = title.trim()
        if (chatId == 0L || cleanTitle.isBlank()) return
        if (sendJsonIfAvailable(
                json("setChatTitle")
                    .put("chat_id", chatId)
                    .put("title", cleanTitle)
            )
        ) return
        val function = newTdApiOrNull("SetChatTitle") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("title", cleanTitle)
        })
    }

    override fun setChatDescription(chatId: Long, description: String) {
        if (chatId == 0L) return
        if (sendJsonIfAvailable(
                json("setChatDescription")
                    .put("chat_id", chatId)
                    .put("description", description.trim())
            )
        ) return
        val function = newTdApiOrNull("SetChatDescription") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("description", description.trim())
        })
    }

    override fun setChatMessageAutoDeleteTime(chatId: Long, seconds: Int) {
        if (chatId == 0L || seconds < 0) return
        if (sendJsonIfAvailable(
                json("setChatMessageAutoDeleteTime")
                    .put("chat_id", chatId)
                    .put("message_auto_delete_time", seconds)
            )
        ) return
        val function = newTdApiOrNull("SetChatMessageAutoDeleteTime") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("messageAutoDeleteTime", seconds)
        })
    }

    override fun setChatSlowModeDelay(chatId: Long, seconds: Int) {
        val allowedDelays = setOf(0, 5, 10, 30, 60, 300, 900, 3600)
        if (chatId == 0L || seconds !in allowedDelays) return
        if (sendJsonIfAvailable(
                json("setChatSlowModeDelay")
                    .put("chat_id", chatId)
                    .put("slow_mode_delay", seconds)
            )
        ) return
        val function = newTdApiOrNull("SetChatSlowModeDelay") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("slowModeDelay", seconds)
        })
    }

    override fun setChatPermissions(chatId: Long, preset: TelegramChatPermissionPreset) {
        if (chatId == 0L) return
        if (sendJsonIfAvailable(
                json("setChatPermissions")
                    .put("chat_id", chatId)
                    .put("permissions", chatPermissionsJson(preset))
            )
        ) return
        val function = newTdApiOrNull("SetChatPermissions") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("permissions", chatPermissionsObject(preset))
        })
    }

    override fun createChatInviteLink(chatId: Long, name: String) {
        val cleanName = name.trim().take(32)
        if (chatId == 0L) return
        if (sendJsonIfAvailable(
                json("createChatInviteLink")
                    .put("chat_id", chatId)
                    .put("name", cleanName)
                    .put("expiration_date", 0)
                    .put("member_limit", 0)
                    .put("creates_join_request", false)
                    .put("@extra", "invite_link:$chatId")
            )
        ) return
        val function = newTdApiOrNull("CreateChatInviteLink") ?: return
        send(
            function.apply {
                setFieldIfPresent("chatId", chatId)
                setFieldIfPresent("name", cleanName)
                setFieldIfPresent("expirationDate", 0)
                setFieldIfPresent("memberLimit", 0)
                setFieldIfPresent("createsJoinRequest", false)
            },
            onResult = { result ->
                (result?.field("inviteLink") as? String)?.takeIf { it.isNotBlank() }?.let { link ->
                    onChatInviteLink(chatId, link)
                }
            }
        )
    }

    override fun revokeChatInviteLink(chatId: Long, inviteLink: String) {
        val cleanInviteLink = inviteLink.trim()
        if (chatId == 0L || cleanInviteLink.isBlank()) return
        if (sendJsonIfAvailable(
                json("revokeChatInviteLink")
                    .put("chat_id", chatId)
                    .put("invite_link", cleanInviteLink)
            )
        ) return
        val function = newTdApiOrNull("RevokeChatInviteLink") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("inviteLink", cleanInviteLink)
        })
    }

    override fun addChatMember(chatId: Long, userId: Long, forwardLimit: Int) {
        if (chatId == 0L || userId == 0L) return
        val cleanForwardLimit = forwardLimit.coerceIn(0, 100)
        if (sendJsonIfAvailable(
                json("addChatMember")
                    .put("chat_id", chatId)
                    .put("user_id", userId)
                    .put("forward_limit", cleanForwardLimit)
            )
        ) return
        val function = newTdApiOrNull("AddChatMember") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("userId", userId)
            setFieldIfPresent("forwardLimit", cleanForwardLimit)
        })
    }

    override fun removeChatMember(chatId: Long, userId: Long) {
        if (chatId == 0L || userId == 0L) return
        if (sendJsonIfAvailable(
                json("setChatMemberStatus")
                    .put("chat_id", chatId)
                    .put("member_id", messageSenderUserJson(userId))
                    .put("status", json("chatMemberStatusLeft"))
            )
        ) return
        val function = newTdApiOrNull("SetChatMemberStatus") ?: return
        val status = newTdApiOrNull("ChatMemberStatusLeft") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("memberId", messageSenderUserObject(userId))
            setFieldIfPresent("status", status)
        })
    }

    override fun banChatMember(chatId: Long, userId: Long, bannedUntilDate: Int) {
        if (chatId == 0L || userId == 0L) return
        val cleanBannedUntilDate = bannedUntilDate.coerceAtLeast(0)
        if (sendJsonIfAvailable(
                json("setChatMemberStatus")
                    .put("chat_id", chatId)
                    .put("member_id", messageSenderUserJson(userId))
                    .put(
                        "status",
                        json("chatMemberStatusBanned")
                            .put("banned_until_date", cleanBannedUntilDate)
                    )
            )
        ) return
        val function = newTdApiOrNull("SetChatMemberStatus") ?: return
        val status = newTdApiOrNull("ChatMemberStatusBanned") ?: return
        status.setFieldIfPresent("bannedUntilDate", cleanBannedUntilDate)
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("memberId", messageSenderUserObject(userId))
            setFieldIfPresent("status", status)
        })
    }

    override fun unbanChatMember(chatId: Long, userId: Long) {
        if (chatId == 0L || userId == 0L) return
        if (sendJsonIfAvailable(
                json("setChatMemberStatus")
                    .put("chat_id", chatId)
                    .put("member_id", messageSenderUserJson(userId))
                    .put(
                        "status",
                        json("chatMemberStatusMember")
                            .put("member_until_date", 0)
                    )
            )
        ) return
        val function = newTdApiOrNull("SetChatMemberStatus") ?: return
        val status = newTdApiOrNull("ChatMemberStatusMember") ?: return
        status.setFieldIfPresent("memberUntilDate", 0)
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("memberId", messageSenderUserObject(userId))
            setFieldIfPresent("status", status)
        })
    }

    override fun promoteChatMember(chatId: Long, userId: Long, customTitle: String) {
        if (chatId == 0L || userId == 0L) return
        val cleanTitle = customTitle.trim().take(16)
        if (sendJsonIfAvailable(
                json("setChatMemberStatus")
                    .put("chat_id", chatId)
                    .put("member_id", messageSenderUserJson(userId))
                    .put(
                        "status",
                        json("chatMemberStatusAdministrator")
                            .put("custom_title", cleanTitle)
                            .put("rights", chatAdministratorRightsJson())
                    )
            )
        ) return
        val function = newTdApiOrNull("SetChatMemberStatus") ?: return
        val status = newTdApiOrNull("ChatMemberStatusAdministrator") ?: return
        status.setFieldIfPresent("customTitle", cleanTitle)
        status.setFieldIfPresent("rights", chatAdministratorRightsObject())
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("memberId", messageSenderUserObject(userId))
            setFieldIfPresent("status", status)
        })
    }

    override fun demoteChatMember(chatId: Long, userId: Long) {
        if (chatId == 0L || userId == 0L) return
        if (sendJsonIfAvailable(
                json("setChatMemberStatus")
                    .put("chat_id", chatId)
                    .put("member_id", messageSenderUserJson(userId))
                    .put(
                        "status",
                        json("chatMemberStatusMember")
                            .put("member_until_date", 0)
                    )
            )
        ) return
        val function = newTdApiOrNull("SetChatMemberStatus") ?: return
        val status = newTdApiOrNull("ChatMemberStatusMember") ?: return
        status.setFieldIfPresent("memberUntilDate", 0)
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("memberId", messageSenderUserObject(userId))
            setFieldIfPresent("status", status)
        })
    }

    override fun setChatDraftMessage(chatId: Long, text: String) {
        val cleanText = text.trim()
        if (chatId == 0L || cleanText.isBlank()) return
        val dateSeconds = (System.currentTimeMillis() / 1000L).toInt()
        if (sendJsonIfAvailable(
                json("setChatDraftMessage")
                    .put("chat_id", chatId)
                    .put("message_thread_id", 0L)
                    .put(
                        "draft_message",
                        json("draftMessage")
                            .put("reply_to", JSONObject.NULL)
                            .put("date", dateSeconds)
                            .put(
                                "input_message_text",
                                json("inputMessageText")
                                    .put("text", formattedTextJson(cleanText))
                                    .put("link_preview_options", JSONObject.NULL)
                                    .put("clear_draft", false)
                            )
                    )
            )
        ) return
        val function = newTdApiOrNull("SetChatDraftMessage") ?: return
        val draft = newTdApiOrNull("DraftMessage") ?: return
        val inputMessageText = newTdApiOrNull("InputMessageText") ?: return
        val formattedText = newTdApiOrNull("FormattedText") ?: return
        formattedText.setFieldIfPresent("text", cleanText)
        formattedText.setFieldIfPresent("entities", emptyArray<Any>())
        inputMessageText.setFieldIfPresent("text", formattedText)
        inputMessageText.setFieldIfPresent("clearDraft", false)
        draft.setFieldIfPresent("date", dateSeconds)
        draft.setFieldIfPresent("replyTo", null)
        draft.setFieldIfPresent("inputMessageText", inputMessageText)
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("messageThreadId", 0L)
            setFieldIfPresent("draftMessage", draft)
        })
    }

    override fun clearChatDraftMessage(chatId: Long) {
        if (chatId == 0L) return
        if (sendJsonIfAvailable(
                json("setChatDraftMessage")
                    .put("chat_id", chatId)
                    .put("message_thread_id", 0L)
                    .put("draft_message", JSONObject.NULL)
            )
        ) return
        val function = newTdApiOrNull("SetChatDraftMessage") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("messageThreadId", 0L)
            setFieldIfPresent("draftMessage", null)
        })
    }

    override fun pinChat(chatId: Long, pinned: Boolean) {
        if (sendJsonIfAvailable(json("toggleChatIsPinned").put("chat_id", chatId).put("is_pinned", pinned))) return
        val function = newTdApiOrNull("ToggleChatIsPinned") ?: return
        send(function.apply {
            setFieldIfPresent("chatList", newTdApiOrNull("ChatListMain"))
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("isPinned", pinned)
        })
    }

    override fun muteChat(chatId: Long, muteForSeconds: Int) {
        if (sendJsonIfAvailable(
                json("setChatNotificationSettings")
                    .put("chat_id", chatId)
                    .put("notification_settings", json("chatNotificationSettings")
                        .put("mute_for", muteForSeconds))
            )
        ) return
        val function = newTdApiOrNull("SetChatNotificationSettings") ?: return
        val settings = newTdApiOrNull("ChatNotificationSettings") ?: return
        settings.setFieldIfPresent("muteFor", muteForSeconds)
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("notificationSettings", settings)
        })
    }

    override fun unmuteChat(chatId: Long) {
        muteChat(chatId, muteForSeconds = 0)
    }

    override fun archiveChat(chatId: Long, archived: Boolean) {
        val chatListType = if (archived) "chatListArchive" else "chatListMain"
        val chatListClass = if (archived) "ChatListArchive" else "ChatListMain"
        if (sendJsonIfAvailable(
                json("addChatToList")
                    .put("chat_id", chatId)
                    .put("chat_list", json(chatListType))
            )
        ) return
        val function = newTdApiOrNull("AddChatToList") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("chatList", newTdApiOrNull(chatListClass))
        })
    }

    override fun markChatRead(chatId: Long, limit: Int) {
        if (chatId == 0L) return
        val cleanLimit = limit.coerceIn(1, 100)
        if (sendJsonIfAvailable(
                json("getChatHistory")
                    .put("chat_id", chatId)
                    .put("from_message_id", 0L)
                    .put("offset", 0)
                    .put("limit", cleanLimit)
                    .put("only_local", false)
                    .put("@extra", "mark_read:$chatId")
            )
        ) return
        val function = newTdApiOrNull("GetChatHistory") ?: return
        send(
            function = function.apply {
                setFieldIfPresent("chatId", chatId)
                setFieldIfPresent("fromMessageId", 0L)
                setFieldIfPresent("offset", 0)
                setFieldIfPresent("limit", cleanLimit)
                setFieldIfPresent("onlyLocal", false)
            },
            onResult = { result ->
                val messageIds = objectParser.parseMessagesResult(result).map { it.id }
                viewMessageIds(chatId, messageIds)
                markChatUnread(chatId, markedUnread = false)
            }
        )
    }

    override fun markChatUnread(chatId: Long, markedUnread: Boolean) {
        if (sendJsonIfAvailable(
                json("toggleChatIsMarkedAsUnread")
                    .put("chat_id", chatId)
                    .put("is_marked_as_unread", markedUnread)
            )
        ) return
        val function = newTdApiOrNull("ToggleChatIsMarkedAsUnread") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("isMarkedAsUnread", markedUnread)
        })
    }

    override fun clearChatHistory(chatId: Long, revoke: Boolean) {
        if (chatId == 0L) return
        if (sendJsonIfAvailable(
                json("deleteChatHistory")
                    .put("chat_id", chatId)
                    .put("remove_from_chat_list", false)
                    .put("revoke", revoke)
            )
        ) return
        val function = newTdApiOrNull("DeleteChatHistory") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("removeFromChatList", false)
            setFieldIfPresent("revoke", revoke)
        })
    }

    override fun downloadFile(fileId: Int, priority: Int, limitBytes: Int) {
        if (fileId <= 0) return
        val safePriority = priority.coerceIn(1, 32)
        if (sendJsonIfAvailable(
                json("downloadFile")
                    .put("file_id", fileId)
                    .put("priority", safePriority)
                    .put("offset", 0)
                    .put("limit", limitBytes)
                    .put("synchronous", false)
            )
        ) return
        val function = newTdApiOrNull("DownloadFile") ?: return
        send(
            function.apply {
                setFieldIfPresent("fileId", fileId)
                setFieldIfPresent("priority", safePriority)
                setFieldIfPresent("offset", 0)
                setFieldIfPresent("limit", limitBytes)
                setFieldIfPresent("synchronous", false)
            },
            onResult = { result -> objectParser.parseFileUpdate(result)?.let(onFile) }
        )
    }

    override fun loadMessage(chatId: Long, messageId: Long) {
        if (chatId == 0L || messageId <= 0L) return
        if (sendJsonIfAvailable(
                json("getMessage")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
            )
        ) return
        val function = newTdApiOrNull("GetMessage") ?: return
        send(
            function.apply {
                setFieldIfPresent("chatId", chatId)
                setFieldIfPresent("messageId", messageId)
            },
            onResult = { result -> objectParser.parseMessage(result)?.let(onMessage) }
        )
    }

    override fun downloadMessageMedia(chatId: Long, messageId: Long, priority: Int, limitBytes: Int) {
        if (chatId == 0L || messageId <= 0L) return
        val extra = "download_message_media:$priority:$limitBytes"
        if (sendJsonIfAvailable(
                json("getMessage")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
                    .put("@extra", extra)
            )
        ) return
        val function = newTdApiOrNull("GetMessage") ?: return
        send(
            function.apply {
                setFieldIfPresent("chatId", chatId)
                setFieldIfPresent("messageId", messageId)
            },
            onResult = { result ->
                objectParser.parseMessage(result)?.let { message ->
                    onMessage(message)
                    downloadMessageMediaFile(message, priority, limitBytes)
                }
            }
        )
    }

    override fun cancelDownloadFile(fileId: Int, onlyIfPending: Boolean) {
        if (fileId <= 0) return
        if (sendJsonIfAvailable(
                json("cancelDownloadFile")
                    .put("file_id", fileId)
                    .put("only_if_pending", onlyIfPending)
            )
        ) return
        val function = newTdApiOrNull("CancelDownloadFile") ?: return
        send(function.apply {
            setFieldIfPresent("fileId", fileId)
            setFieldIfPresent("onlyIfPending", onlyIfPending)
        })
    }

    override fun close() {
        started.set(false)
        sendJsonIfAvailable(json("close"))
        jsonReceiveThread?.join(500)
        jsonReceiveThread = null
        jsonClientId = null
        jsonTdLib = null
        jsonTdLibClass = null
        newTdApiOrNull("Close")?.let { send(it) }
        tdlibParametersSent.set(false)
    }

    private fun handleUpdate(update: Any?) {
        if (update == null) return
        when (update.javaClass.simpleName) {
            "UpdateAuthorizationState" -> handleAuthorizationState(update.field("authorizationState"))
            "UpdateNewChat" -> objectParser.parseChat(update.field("chat"))?.let(onChat)
            "UpdateChatLastMessage" -> objectParser.parseChatLastMessage(update)?.let(onChat)
            "UpdateNewMessage" -> objectParser.parseMessage(update.field("message"))?.also {
                requestSenderIfNeeded(it.senderId)
            }?.let(onMessage)
            "UpdateDeleteMessages" -> handleDeleteMessagesUpdate(
                chatId = update.field("chatId") as? Long ?: 0L,
                messageIds = update.messageIdsField()
            )
            "UpdateChatReadInbox" -> onReadState(
                TelegramReadStateUpdate(
                    chatId = (update.field("chatId") as? Number)?.toLong() ?: 0L,
                    lastReadMessageId = (update.field("lastReadInboxMessageId") as? Number)?.toLong() ?: 0L,
                    unreadCount = (update.field("unreadCount") as? Number)?.toInt() ?: 0,
                    outgoing = false
                )
            )
            "UpdateChatReadOutbox" -> onReadState(
                TelegramReadStateUpdate(
                    chatId = (update.field("chatId") as? Number)?.toLong() ?: 0L,
                    lastReadMessageId = (update.field("lastReadOutboxMessageId") as? Number)?.toLong() ?: 0L,
                    outgoing = true
                )
            )
            "UpdateMessageSendSucceeded" -> handleMessageSendSucceededUpdate(update)
            "UpdateMessageSendFailed" -> handleMessageSendFailedUpdate(update)
            "UpdateMessageSendingState" -> handleMessageSendingStateUpdate(update)
            "UpdateMessageContent" -> handleMessageContentUpdate(update)
            "UpdateUserChatAction" -> handleUserChatActionUpdate(update)
            "UpdateChatAction" -> handleUserChatActionUpdate(update)
            "UpdateChatPinnedMessage" -> handlePinnedMessageUpdate(update)
            "UpdateMessageIsPinned" -> handleMessageIsPinnedUpdate(update)
            "UpdateCall" -> objectParser.parseCall(update.field("call"))?.let(onCall)
            "UpdateCallSignalingData" -> {
                val callId = (update.field("callId") as? Number)?.toInt() ?: 0
                val data = decodeTdLibBytes(update.field("data"))
                if (callId != 0 && data.isNotEmpty()) onCallSignalingData(TelegramCallSignalingData(callId, data))
            }
            "UpdateStory" -> objectParser.parseStory(update.field("story"))?.let(onStory)
            "UpdateStoryDeleted" -> {
                val chatId = (update.fieldAny("storySenderChatId", "senderChatId") as? Number)?.toLong() ?: 0L
                val storyId = (update.field("storyId") as? Number)?.toInt() ?: 0
                objectParser.deletedStory(chatId, storyId)?.let(onStory)
            }
            "UpdateChatActiveStories" -> objectParser.parseStoriesResult(update.field("activeStories")).forEach(onStory)
            "UpdateUser" -> objectParser.parseUser(update.field("user"))?.let(onSender)
            "UpdateBasicGroup" -> objectParser.parseGroup(update.field("basicGroup"), "basic_group")?.let(onSender)
            "UpdateSupergroup" -> objectParser.parseGroup(update.field("supergroup"), "supergroup")?.let(onSender)
            "UpdateFile" -> objectParser.parseFileUpdate(update.field("file"))?.let(onFile)
        }
    }

    private fun handleAuthorizationState(state: Any?) {
        when (state?.javaClass?.simpleName) {
            "AuthorizationStateWaitTdlibParameters" -> sendTdlibParameters()
            "AuthorizationStateWaitPhoneNumber" -> onStatus(TdLibStatus.WaitingForPhoneNumber)
            "AuthorizationStateWaitCode" -> onStatus(TdLibStatus.WaitingForCode(state.canResendAuthenticationCode()))
            "AuthorizationStateWaitPassword" -> onStatus(TdLibStatus.WaitingForPassword(state.passwordHint()))
            "AuthorizationStateReady" -> {
                onStatus(TdLibStatus.Ready)
                loadMainChatList()
            }
            "AuthorizationStateClosed" -> onStatus(TdLibStatus.Closed)
        }
    }

    private fun sendTdlibParameters() {
        if (!tdlibParametersSent.compareAndSet(false, true)) return
        val parameters = newTdApiOrNull("SetTdlibParameters") ?: return
        parameters.apply {
            setFieldIfPresent("useTestDc", config.useTestDc)
            setFieldIfPresent("databaseDirectory", config.databaseDirectory)
            setFieldIfPresent("filesDirectory", config.filesDirectory)
            setFieldIfPresent("databaseEncryptionKey", config.databaseEncryptionKey)
            setFieldIfPresent("useFileDatabase", true)
            setFieldIfPresent("useChatInfoDatabase", true)
            setFieldIfPresent("useMessageDatabase", true)
            setFieldIfPresent("useSecretChats", true)
            setFieldIfPresent("apiId", config.apiId)
            setFieldIfPresent("apiHash", config.apiHash)
            setFieldIfPresent("systemLanguageCode", config.systemLanguageCode)
            setFieldIfPresent("deviceModel", config.deviceModel)
            setFieldIfPresent("systemVersion", config.systemVersion)
            setFieldIfPresent("applicationVersion", config.applicationVersion)
            setFieldIfPresent("enableStorageOptimizer", true)
            setFieldIfPresent("ignoreFileNames", false)
        }
        send(parameters)
    }

    private fun startJsonClient() {
        try {
            loadJsonTdLibNative()
            jsonTdLibClass = Class.forName("io.xbot.tdlib.TdLib")
            jsonTdLib = jsonTdLibClass!!.getField("INSTANCE").get(null)
            jsonClientId = jsonTdLibClass!!.getMethod("createClientId").invoke(jsonTdLib) as Int
            onStatus(TdLibStatus.Starting)
            startJsonReceiveLoop()
            sendJsonIfAvailable(json("getAuthorizationState"))
        } catch (_: ClassNotFoundException) {
            started.set(false)
            onStatus(TdLibStatus.BindingMissing)
        } catch (error: Throwable) {
            started.set(false)
            onStatus(TdLibStatus.Error(error.tdLibLoadMessage()))
        }
    }

    private fun loadJsonTdLibNative() {
        val loaderClass = Class.forName("io.xbot.tdlib.TdLibLoader")
        val loader = loaderClass.getField("INSTANCE").get(null)
        val loaderError = runCatching {
            loaderClass.getMethod("load").invoke(loader)
        }.exceptionOrNull()
        if (loaderError == null) return

        runCatching {
            System.loadLibrary("tdjsonjava")
        }.getOrElse { directLoadError ->
            throw IllegalStateException(
                "TDLib JSON native library load failed. Loader: ${loaderError.tdLibLoadMessage()}; " +
                    "System.loadLibrary(tdjsonjava): ${directLoadError.tdLibLoadMessage()}",
                directLoadError
            )
        }
    }

    private fun startJsonReceiveLoop() {
        val thread = Thread({
            while (started.get() && jsonClientId != null) {
                runCatching {
                    val payload = receiveJson(0.2) ?: return@runCatching
                    handleJsonObject(JSONObject(payload))
                }.onFailure { error ->
                    onStatus(TdLibStatus.Error(error.message ?: error::class.java.simpleName))
                }
            }
        }, "tdlib-json-receiver")
        thread.isDaemon = true
        jsonReceiveThread = thread
        thread.start()
    }

    private fun handleJsonObject(json: JSONObject) {
        when (json.optString("@type")) {
            "updateAuthorizationState" -> handleJsonAuthorizationState(json.optJSONObject("authorization_state"))
            "updateNewChat" -> jsonParser.parseChat(json.optJSONObject("chat"))?.let(onChat)
            "updateChatLastMessage" -> jsonParser.parseChatLastMessage(json)?.let(onChat)
            "updateNewMessage" -> jsonParser.parseMessage(json.optJSONObject("message"))?.also {
                requestJsonSenderIfNeeded(it.senderId)
            }?.let(onMessage)
            "updateDeleteMessages" -> handleDeleteMessagesUpdate(
                chatId = json.optLong("chat_id", 0L),
                messageIds = json.deletedMessageIds()
            )
            "updateChatReadInbox" -> onReadState(
                TelegramReadStateUpdate(
                    chatId = json.optLong("chat_id", 0L),
                    lastReadMessageId = json.optLong("last_read_inbox_message_id", 0L),
                    unreadCount = json.optInt("unread_count", 0),
                    outgoing = false
                )
            )
            "updateChatReadOutbox" -> onReadState(
                TelegramReadStateUpdate(
                    chatId = json.optLong("chat_id", 0L),
                    lastReadMessageId = json.optLong("last_read_outbox_message_id", 0L),
                    outgoing = true
                )
            )
            "updateMessageSendSucceeded" -> handleJsonMessageSendSucceededUpdate(json)
            "updateMessageSendFailed" -> handleJsonMessageSendFailedUpdate(json)
            "updateMessageSendingState" -> handleJsonMessageSendingStateUpdate(json)
            "updateMessageContent" -> handleJsonMessageContentUpdate(json)
            "updateUserChatAction",
            "updateChatAction" -> handleJsonUserChatActionUpdate(json)
            "updateChatPinnedMessage" -> handleJsonPinnedMessageUpdate(json)
            "updateMessageIsPinned" -> handleJsonMessageIsPinnedUpdate(json)
            "updateCall" -> {
                logJsonCall("updateCall", json.optJSONObject("call"))
                jsonParser.parseCall(json.optJSONObject("call"))?.let(onCall)
            }
            "call" -> {
                logJsonCall("callResult", json)
                jsonParser.parseCall(json)?.let(onCall)
            }
            "updateCallSignalingData" -> {
                val callId = json.optInt("call_id", 0)
                val data = decodeTdLibBytes(json.opt("data"))
                if (callId != 0 && data.isNotEmpty()) onCallSignalingData(TelegramCallSignalingData(callId, data))
            }
            "updateStory" -> jsonParser.parseStory(json.optJSONObject("story"))?.let(onStory)
            "updateStoryDeleted" -> jsonParser.deletedStory(
                chatId = json.optLongAny("story_sender_chat_id", "sender_chat_id"),
                storyId = json.optInt("story_id", 0)
            )?.let(onStory)
            "updateChatActiveStories" -> jsonParser.parseStoriesResult(json.optJSONObject("active_stories") ?: json).forEach(onStory)
            "story" -> jsonParser.parseStory(json)?.let(onStory)
            "message" -> jsonParser.parseMessage(json)?.also { message ->
                requestJsonSenderIfNeeded(message.senderId)
                handleJsonMessageExtra(json.optString("@extra"), message)
            }?.let(onMessage)
            "updateUser" -> jsonParser.parseUser(json.optJSONObject("user"))?.let(onSender)
            "updateBasicGroup" -> jsonParser.parseGroup(json.optJSONObject("basic_group"), "basic_group")?.let(onSender)
            "updateSupergroup" -> jsonParser.parseGroup(json.optJSONObject("supergroup"), "supergroup")?.let(onSender)
            "updateFile" -> jsonParser.parseFileUpdate(json.optJSONObject("file"))?.let(onFile)
            "file" -> jsonParser.parseFileUpdate(json)?.let(onFile)
            "chatInviteLink" -> {
                val chatId = json.optString("@extra")
                    .takeIf { it.startsWith("invite_link:") }
                    ?.substringAfter("invite_link:")
                    ?.toLongOrNull()
                    ?: 0L
                val inviteLink = json.optString("invite_link")
                if (chatId != 0L && inviteLink.isNotBlank()) {
                    onChatInviteLink(chatId, inviteLink)
                }
            }
            "messages" -> {
                val extra = json.optString("@extra")
                if (extra.startsWith("mark_read:")) {
                    val chatId = extra.substringAfter("mark_read:").toLongOrNull()
                    val messageIds = json.messageIds()
                    if (chatId != null) {
                        viewMessageIdsJson(chatId, messageIds)
                        markChatUnread(chatId, markedUnread = false)
                    }
                } else {
                    val messages = jsonParser.parseMessagesResult(json)
                    messages.forEach(onMessage)
                    extra.historyLoadOrNull(messages.map { it.id })?.let(onChatHistoryLoaded)
                }
            }
            "chatActiveStories" -> jsonParser.parseStoriesResult(json).forEach(onStory)
            "botCommands" -> {
                val botUserId = json.extraLong("bot_commands:")
                onBotCommands(botUserId, jsonParser.parseBotCommandsResult(json, botUserId))
            }
            "botMenuButtonCommands",
            "botMenuButtonDefault",
            "botMenuButtonWebApp" -> {
                val botUserId = json.extraLong("bot_menu:")
                onBotMenuButton(jsonParser.parseBotMenuButton(json, botUserId))
            }
            "inlineQueryResults" -> {
                val botUserId = json.extraLong("inline_results:")
                onInlineBotResults(jsonParser.parseInlineBotResults(json, botUserId))
            }
            "webAppUrl" -> {
                val botUserId = json.extraLong("web_app:")
                jsonParser.parseWebAppUrl(json, botUserId)?.let(onWebAppUrl)
            }
            "userPrivacySettingRules" -> {
                val setting = json.extraPrivacySetting()
                onPrivacyRule(jsonParser.parsePrivacyRulesResult(json, setting))
            }
            "sessions" -> onActiveSessions(jsonParser.parseActiveSessions(json))
            "premiumFeatures" -> onPremiumFeatures(jsonParser.parsePremiumFeatures(json))
            "premiumLimit" -> onPremiumLimit(
                jsonParser.parsePremiumLimit(
                    json,
                    json.extraPremiumLimitType()
                )
            )
            "starAmount",
            "starsAmount" -> jsonParser.parseStarBalance(json)?.let(onStarBalance)
            "starTransactions" -> onStarTransactions(jsonParser.parseStarTransactions(json))
            "businessChatLinks" -> onBusinessChatLinks(jsonParser.parseBusinessChatLinks(json))
            "businessChatLink" -> jsonParser.parseBusinessChatLink(json)?.let { onBusinessChatLinks(listOf(it)) }
            "chats" -> requestJsonChats(json.optJSONArray("chat_ids"))
            "users" -> {
                val extra = json.optString("@extra")
                val limit = extra
                    .takeIf { it.startsWith("contacts:") }
                    ?.substringAfter("contacts:")
                    ?.toIntOrNull()
                    ?: Int.MAX_VALUE
                val requestedCount = requestJsonUsers(json.optJSONArray("user_ids"), limit)
                if (extra.startsWith("contacts:")) {
                    onContactsLoaded(requestedCount)
                }
            }
            "user" -> jsonParser.parseUser(json)?.let(onSender)
            "chat" -> {
                val chat = jsonParser.parseChat(json)
                chat?.let(onChat)
                val extra = json.optString("@extra")
                if (extra.startsWith("join:") && chat != null) {
                    joinChat(chat.id)
                } else if ((extra.startsWith("open:") || extra.startsWith("open_invite:") || extra.startsWith("open_bot:")) && chat != null) {
                    onOpenChat(chat.id)
                    loadChatHistory(chat.id)
                }
            }
            "error" -> handleJsonError(json)
            "authorizationStateWaitTdlibParameters",
            "authorizationStateWaitPhoneNumber",
            "authorizationStateWaitCode",
            "authorizationStateWaitPassword",
            "authorizationStateReady",
            "authorizationStateClosed" -> handleJsonAuthorizationState(json)
        }
    }

    private fun handleDeleteMessagesUpdate(chatId: Long, messageIds: List<Long>) {
        val cleanMessageIds = messageIds.distinct().filter { it > 0L }
        if (chatId != 0L && cleanMessageIds.isNotEmpty()) {
            onMessagesDeleted(chatId, cleanMessageIds)
        }
    }

    private fun handleMessageSendSucceededUpdate(update: Any) {
        val message = objectParser.parseMessage(update.field("message"))
        val oldMessageId = (update.field("oldMessageId") as? Number)?.toLong() ?: 0L
        if (message != null) {
            if (oldMessageId > 0L && oldMessageId != message.id) {
                onMessagesDeleted(message.chatId, listOf(oldMessageId))
            }
            onMessage(message.copy(syncState = MessageSyncState.Synced, isRead = true))
        }
    }

    private fun handleMessageSendFailedUpdate(update: Any) {
        val message = objectParser.parseMessage(update.field("message"))
        val oldMessageId = (update.field("oldMessageId") as? Number)?.toLong() ?: 0L
        if (message != null) {
            if (oldMessageId > 0L && oldMessageId != message.id) {
                onMessagesDeleted(message.chatId, listOf(oldMessageId))
            }
            onMessage(message.copy(syncState = MessageSyncState.Failed, isOutgoing = true, isRead = false))
        }
    }

    private fun handleMessageSendingStateUpdate(update: Any) {
        val chatId = (update.field("chatId") as? Number)?.toLong() ?: 0L
        val messageId = (update.field("messageId") as? Number)?.toLong() ?: 0L
        val state = update.field("sendingState").sendState()
        if (chatId != 0L && messageId != 0L) {
            onMessageSync(TelegramMessageSyncUpdate(chatId, messageId, state, isRead = state == MessageSyncState.Synced))
        }
    }

    private fun handleMessageContentUpdate(update: Any) {
        val chatId = (update.field("chatId") as? Number)?.toLong() ?: 0L
        val messageId = (update.field("messageId") as? Number)?.toLong() ?: 0L
        val text = objectParser.parseContentText(update.field("newContent"))
        if (chatId != 0L && messageId != 0L && text.isNotBlank()) {
            onMessageContent(TelegramMessageContentUpdate(chatId, messageId, text))
        }
    }

    private fun handleUserChatActionUpdate(update: Any) {
        val chatId = (update.field("chatId") as? Number)?.toLong() ?: 0L
        val senderId = update.field("senderId")?.let { objectParserSenderId(it) }
            ?: (update.field("userId") as? Number)?.toLong()?.let { "user:$it" }
            ?: ""
        val action = update.field("action").chatActionLabel()
        if (chatId != 0L) {
            onChatAction(TelegramChatActionUpdate(chatId, senderId, action))
        }
    }

    private fun handlePinnedMessageUpdate(update: Any) {
        val chatId = (update.field("chatId") as? Number)?.toLong() ?: 0L
        val messageId = (update.field("pinnedMessageId") as? Number)?.toLong() ?: 0L
        if (chatId != 0L) {
            onPinnedMessage(TelegramPinnedMessageUpdate(chatId, messageId))
        }
    }

    private fun handleMessageIsPinnedUpdate(update: Any) {
        val chatId = (update.field("chatId") as? Number)?.toLong() ?: 0L
        val messageId = (update.field("messageId") as? Number)?.toLong() ?: 0L
        val isPinned = update.field("isPinned") as? Boolean ?: false
        if (chatId != 0L) {
            onPinnedMessage(TelegramPinnedMessageUpdate(chatId, if (isPinned) messageId else 0L))
        }
    }

    private fun handleJsonMessageSendSucceededUpdate(json: JSONObject) {
        val message = jsonParser.parseMessage(json.optJSONObject("message"))
        val oldMessageId = json.optLong("old_message_id", 0L)
        if (message != null) {
            if (oldMessageId > 0L && oldMessageId != message.id) {
                onMessagesDeleted(message.chatId, listOf(oldMessageId))
            }
            onMessage(message.copy(syncState = MessageSyncState.Synced, isRead = true))
        }
    }

    private fun handleJsonMessageSendFailedUpdate(json: JSONObject) {
        val message = jsonParser.parseMessage(json.optJSONObject("message"))
        val oldMessageId = json.optLong("old_message_id", 0L)
        if (message != null) {
            if (oldMessageId > 0L && oldMessageId != message.id) {
                onMessagesDeleted(message.chatId, listOf(oldMessageId))
            }
            onMessage(message.copy(syncState = MessageSyncState.Failed, isOutgoing = true, isRead = false))
        }
    }

    private fun handleJsonMessageSendingStateUpdate(json: JSONObject) {
        val chatId = json.optLong("chat_id", 0L)
        val messageId = json.optLong("message_id", 0L)
        val state = json.optJSONObject("sending_state").sendState()
        if (chatId != 0L && messageId != 0L) {
            onMessageSync(TelegramMessageSyncUpdate(chatId, messageId, state, isRead = state == MessageSyncState.Synced))
        }
    }

    private fun handleJsonMessageContentUpdate(json: JSONObject) {
        val chatId = json.optLong("chat_id", 0L)
        val messageId = json.optLong("message_id", 0L)
        val text = jsonParser.parseContentText(json.optJSONObject("new_content"))
        if (chatId != 0L && messageId != 0L && text.isNotBlank()) {
            onMessageContent(TelegramMessageContentUpdate(chatId, messageId, text))
        }
    }

    private fun handleJsonMessageExtra(extra: String, message: TelegramMessage) {
        if (!extra.startsWith(DOWNLOAD_MESSAGE_MEDIA_EXTRA_PREFIX)) return
        val parts = extra.split(":")
        val priority = parts.getOrNull(1)?.toIntOrNull() ?: 16
        val limitBytes = parts.getOrNull(2)?.toIntOrNull() ?: 0
        downloadMessageMediaFile(message, priority, limitBytes)
    }

    private fun readableLocalPathOrReport(path: String): String? {
        val cleanPath = path.trim()
        val file = cleanPath.takeIf { it.isNotBlank() }?.let(::File)
        if (file != null && file.isFile && file.canRead()) {
            return file.absolutePath
        }
        onOperationError("Local file does not exist or cannot be read: ${cleanPath.ifBlank { "-" }}")
        return null
    }

    private fun downloadMessageMediaFile(message: TelegramMessage, priority: Int, limitBytes: Int) {
        val fileId = message.mediaFileId
        if (fileId > 0) {
            downloadFile(fileId, priority = priority, limitBytes = limitBytes)
        }
    }

    private fun handleJsonUserChatActionUpdate(json: JSONObject) {
        val chatId = json.optLong("chat_id", 0L)
        val senderId = json.optJSONObject("sender_id").jsonSenderId()
            .ifBlank { json.optLong("user_id", 0L).takeIf { it > 0L }?.let { "user:$it" }.orEmpty() }
        val action = json.optJSONObject("action").chatActionLabel()
        if (chatId != 0L) {
            onChatAction(TelegramChatActionUpdate(chatId, senderId, action))
        }
    }

    private fun handleJsonPinnedMessageUpdate(json: JSONObject) {
        val chatId = json.optLong("chat_id", 0L)
        val messageId = json.optLong("pinned_message_id", 0L)
        if (chatId != 0L) {
            onPinnedMessage(TelegramPinnedMessageUpdate(chatId, messageId))
        }
    }

    private fun handleJsonMessageIsPinnedUpdate(json: JSONObject) {
        val chatId = json.optLong("chat_id", 0L)
        val messageId = json.optLong("message_id", 0L)
        val isPinned = json.optBoolean("is_pinned", false)
        if (chatId != 0L) {
            onPinnedMessage(TelegramPinnedMessageUpdate(chatId, if (isPinned) messageId else 0L))
        }
    }

    private fun handleJsonError(json: JSONObject) {
        val message = json.optString("message", "TDLib error")
        val extra = json.optString("@extra")
        if (extra.startsWith("call:")) {
            Log.w(CALL_LOG_TAG, "tdlib error extra=$extra message=$message")
            onOperationError(message)
        }
        val isAuthenticationError = extra.startsWith("auth:") ||
            message.contains("Wrong database encryption key", ignoreCase = true) ||
            message.contains("PHONE_CODE", ignoreCase = true) ||
            message.contains("PASSWORD", ignoreCase = true) ||
            message.contains("Authentication code", ignoreCase = true)
        if (isAuthenticationError) {
            onStatus(TdLibStatus.Error(message))
        }
    }

    private fun handleJsonAuthorizationState(state: JSONObject?) {
        when (state?.optString("@type")) {
            "authorizationStateWaitTdlibParameters" -> sendJsonTdlibParameters()
            "authorizationStateWaitPhoneNumber" -> onStatus(TdLibStatus.WaitingForPhoneNumber)
            "authorizationStateWaitCode" -> onStatus(TdLibStatus.WaitingForCode(state.canResendAuthenticationCode()))
            "authorizationStateWaitPassword" -> onStatus(TdLibStatus.WaitingForPassword(state.optString("password_hint")))
            "authorizationStateReady" -> {
                onStatus(TdLibStatus.Ready)
                loadMainChatList()
            }
            "authorizationStateClosed" -> onStatus(TdLibStatus.Closed)
        }
    }

    private fun sendJsonTdlibParameters() {
        if (!tdlibParametersSent.compareAndSet(false, true)) return
        sendJsonIfAvailable(
            json("setTdlibParameters")
                .put("use_test_dc", config.useTestDc)
                .put("database_directory", config.databaseDirectory)
                .put("files_directory", config.filesDirectory)
                .put("database_encryption_key", TdLibDatabaseKeyStore.encodeForJson(config.databaseEncryptionKey))
                .put("use_file_database", true)
                .put("use_chat_info_database", true)
                .put("use_message_database", true)
                .put("use_secret_chats", true)
                .put("api_id", config.apiId)
                .put("api_hash", config.apiHash)
                .put("system_language_code", config.systemLanguageCode)
                .put("device_model", config.deviceModel)
                .put("system_version", config.systemVersion)
                .put("application_version", config.applicationVersion)
                .put("enable_storage_optimizer", true)
                .put("ignore_file_names", false)
                .put("@extra", "auth:parameters")
        )
    }

    private fun requestJsonSenderIfNeeded(senderId: String) {
        when {
            senderId.startsWith("user:") -> {
                val userId = senderId.removePrefix("user:").toLongOrNull() ?: return
                sendJsonIfAvailable(json("getUser").put("user_id", userId))
            }
            senderId.startsWith("chat:") -> {
                val chatId = senderId.removePrefix("chat:").toLongOrNull() ?: return
                sendJsonIfAvailable(json("getChat").put("chat_id", chatId))
            }
        }
    }

    private fun requestSenderIfNeeded(senderId: String) {
        when {
            senderId.startsWith("user:") -> {
                val userId = senderId.removePrefix("user:").toLongOrNull() ?: return
                val function = newTdApiOrNull("GetUser") ?: return
                send(function.apply { setFieldIfPresent("userId", userId) }) { result ->
                    objectParser.parseUser(result)?.let(onSender)
                }
            }
            senderId.startsWith("chat:") -> {
                val chatId = senderId.removePrefix("chat:").toLongOrNull() ?: return
                val function = newTdApiOrNull("GetChat") ?: return
                send(function.apply { setFieldIfPresent("chatId", chatId) }) { result ->
                    objectParser.parseChat(result)?.let(onChat)
                }
            }
        }
    }

    private fun requestJsonChats(chatIds: JSONArray?) {
        if (chatIds == null) return
        for (index in 0 until chatIds.length()) {
            sendJsonIfAvailable(json("getChat").put("chat_id", chatIds.optLong(index)))
        }
    }

    private fun requestJsonUsers(userIds: JSONArray?, limit: Int = Int.MAX_VALUE): Int {
        if (userIds == null) return 0
        val count = minOf(userIds.length(), limit.coerceAtLeast(0))
        for (index in 0 until count) {
            sendJsonIfAvailable(json("getUser").put("user_id", userIds.optLong(index)))
        }
        return count
    }

    private fun requestChatsFromResult(result: Any?, limit: Int) {
        val chatIds = objectParser.chatIdsFromResult(result) ?: return
        chatIds.take(limit).forEach { chatId ->
            val function = newTdApiOrNull("GetChat") ?: return@forEach
            send(function.apply { setFieldIfPresent("chatId", chatId) }) { chat ->
                objectParser.parseChat(chat)?.let(onChat)
            }
        }
    }

    private fun requestUsersFromResult(result: Any?, limit: Int): Int {
        val userIds = objectParser.userIdsFromResult(result) ?: return 0
        val requestedUserIds = userIds.take(limit)
        requestedUserIds.forEach { userId ->
            val function = newTdApiOrNull("GetUser") ?: return@forEach
            send(function.apply { setFieldIfPresent("userId", userId) }) { user ->
                objectParser.parseUser(user)?.let(onSender)
            }
        }
        return requestedUserIds.size
    }

    private fun joinChatByInviteLink(inviteLink: String, openAfterJoin: Boolean = false) {
        val functionJson = json("joinChatByInviteLink").put("invite_link", inviteLink)
        if (openAfterJoin) functionJson.put("@extra", "open_invite:$inviteLink")
        if (sendJsonIfAvailable(functionJson)) return
        val function = newTdApiOrNull("JoinChatByInviteLink") ?: return
        send(
            function.apply { setFieldIfPresent("inviteLink", inviteLink) },
            onResult = { result ->
                if (!openAfterJoin) return@send
                objectParser.parseChat(result)?.let { chat ->
                    onChat(chat)
                    onOpenChat(chat.id)
                    loadChatHistory(chat.id)
                }
            }
        )
    }

    private fun viewMessageIdsJson(chatId: Long, messageIds: List<Long>) {
        if (chatId == 0L || messageIds.isEmpty()) return
        sendJsonIfAvailable(
            json("viewMessages")
                .put("chat_id", chatId)
                .put("message_ids", JSONArray(messageIds))
                .put("force_read", true)
        )
    }

    private fun viewMessageIds(chatId: Long, messageIds: List<Long>) {
        val cleanMessageIds = messageIds.distinct().filter { it > 0L }
        if (chatId == 0L || cleanMessageIds.isEmpty()) return
        val function = newTdApiOrNull("ViewMessages") ?: return
        send(function.apply {
            setFieldIfPresent("chatId", chatId)
            setFieldIfPresent("messageIds", cleanMessageIds.toLongArray())
            setFieldIfPresent("source", null)
            setFieldIfPresent("forceRead", true)
        })
    }

    private fun send(function: Any, onResult: ((Any?) -> Unit)? = null) {
        val targetClient = client ?: return
        val handler = proxy(resultHandlerClass ?: return) { result ->
            if (result?.javaClass?.simpleName == "Error") {
                val message = result.field("message") as? String ?: "TDLib error"
                onOperationError(message)
                onStatus(TdLibStatus.Error(message))
            } else {
                onResult?.invoke(result)
            }
        }
        runCatching {
            val functionClass = Class.forName("org.drinkless.tdlib.TdApi\$Function")
            clientClass!!.getMethod("send", functionClass, resultHandlerClass).invoke(targetClient, function, handler)
        }.onFailure { error ->
            val message = error.message ?: error::class.java.simpleName
            onOperationError(message)
            onStatus(TdLibStatus.Error(message))
        }
    }

    private fun sendJsonIfAvailable(function: JSONObject): Boolean {
        val id = jsonClientId ?: return false
        val tdLib = jsonTdLib ?: return false
        val tdLibClass = jsonTdLibClass ?: return false
        val type = function.optString("@type")
        if (type in setOf("createCall", "acceptCall", "discardCall", "sendCallSignalingData")) {
            Log.d(
                CALL_LOG_TAG,
                "sendJson type=$type extra=${function.optString("@extra")} user=${function.optLong("user_id", 0L)} " +
                    "call=${function.optInt("call_id", 0)} video=${function.optBoolean("is_video", false)}"
            )
        }
        runCatching {
            tdLibClass.getMethod("send", Int::class.javaPrimitiveType, String::class.java)
                .invoke(tdLib, id, function.toString())
        }.onFailure { error ->
            onStatus(TdLibStatus.Error(error.cause?.message ?: error.message ?: error::class.java.simpleName))
        }
        return true
    }

    private fun receiveJson(timeoutSeconds: Double): String? {
        val tdLib = jsonTdLib ?: return null
        val tdLibClass = jsonTdLibClass ?: return null
        return runCatching {
            synchronized(jsonReceiveLock) {
                tdLibClass.getMethod("receive", Double::class.javaPrimitiveType)
                    .invoke(tdLib, timeoutSeconds) as? String
            }
        }.getOrElse { error ->
            onStatus(TdLibStatus.Error(error.cause?.message ?: error.message ?: error::class.java.simpleName))
            null
        }
    }

    private fun logJsonCall(source: String, call: JSONObject?) {
        if (call == null) {
            Log.w(CALL_LOG_TAG, "$source call=null")
            return
        }
        val state = call.optJSONObject("state")
        Log.d(
            CALL_LOG_TAG,
            "$source id=${call.optInt("id", 0)} user=${call.optLong("user_id", 0L)} " +
                "out=${call.optBoolean("is_outgoing", false)} video=${call.optBoolean("is_video", false)} " +
                "state=${state?.optString("@type").orEmpty()} created=${state?.optBoolean("is_created", false) ?: false} " +
                "received=${state?.optBoolean("is_received", false) ?: false} extra=${call.optString("@extra")}"
        )
    }

    private fun json(type: String): JSONObject = JSONObject().put("@type", type)

    private fun formattedTextJson(text: String): JSONObject {
        return json("formattedText")
            .put("text", text)
            .put("entities", JSONArray())
    }

    private fun messageSendOptionsJson(options: MessageSendOptions): JSONObject {
        return json("messageSendOptions")
            .put("disable_notification", options.disableNotification)
            .put("protect_content", false)
            .put("allow_paid_broadcast", false)
            .put("effect_id", 0L)
            .put(
                "scheduling_state",
                if (options.hasSchedule) {
                    json("messageSchedulingStateSendAtDate")
                        .put("send_date", options.scheduledAtEpochSeconds)
                } else {
                    JSONObject.NULL
                }
            )
    }

    private fun reactionTypeEmojiJson(emoji: String): JSONObject {
        return json("reactionTypeEmoji").put("emoji", emoji)
    }

    private fun Throwable.tdLibLoadMessage(): String {
        return generateSequence(this as Throwable?) { it.cause }
            .map { error ->
                error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
            }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" <- ")
            .ifBlank { javaClass.simpleName }
    }

    private fun newTdApi(name: String): Any {
        return Class.forName("org.drinkless.tdlib.TdApi\$$name").getDeclaredConstructor().newInstance()
    }

    private fun newTdApiOrNull(name: String): Any? {
        return runCatching { newTdApi(name) }.getOrElse { error ->
            onStatus(
                if (error is ClassNotFoundException) {
                    TdLibStatus.BindingMissing
                } else {
                    TdLibStatus.Error(error.message ?: error::class.java.simpleName)
                }
            )
            null
        }
    }

    private fun newTdApiOptional(name: String): Any? {
        return runCatching { newTdApi(name) }.getOrNull()
    }

    private fun newInputMessageReplyToMessageOrNull(messageId: Long): Any? {
        return newTdApiOrNull("InputMessageReplyToMessage")?.apply {
            setFieldIfPresent("messageId", messageId)
            setFieldIfPresent("quote", null)
        }
    }

    private fun inputFileLocalOrNull(path: String): Any? {
        return newTdApiOrNull("InputFileLocal")?.apply {
            setFieldIfPresent("path", path)
        }
    }

    private fun formattedTextObject(text: String): Any? {
        return newTdApiOrNull("FormattedText")?.apply {
            setFieldIfPresent("text", text)
            setFieldIfPresent("entities", emptyArray<Any>())
        }
    }

    private fun messageSendOptionsObject(options: MessageSendOptions): Any? {
        return newTdApiOptional("MessageSendOptions")?.apply {
            setFieldIfPresent("disableNotification", options.disableNotification)
            setFieldIfPresent("protectContent", false)
            setFieldIfPresent("allowPaidBroadcast", false)
            setFieldIfPresent("effectId", 0L)
            setFieldIfPresent("schedulingState", messageSchedulingStateObject(options))
        }
    }

    private fun messageSchedulingStateObject(options: MessageSendOptions): Any? {
        if (!options.hasSchedule) return null
        return newTdApiOptional("MessageSchedulingStateSendAtDate")?.apply {
            setFieldIfPresent("sendDate", options.scheduledAtEpochSeconds)
        }
    }

    private fun reactionTypeEmojiObject(emoji: String): Any? {
        return newTdApiOptional("ReactionTypeEmoji")?.apply {
            setFieldIfPresent("emoji", emoji)
        }
    }

    private fun callProtocolJson(): JSONObject {
        val protocol = callProtocolProvider()
        Log.d(
            CALL_LOG_TAG,
            "protocol min=${protocol.minLayer} max=${protocol.maxLayer} " +
                "p2p=${protocol.udpP2p} reflector=${protocol.udpReflector} " +
                "versions=${protocol.libraryVersions.joinToString(",")}"
        )
        return json("callProtocol")
            .put("udp_p2p", protocol.udpP2p)
            .put("udp_reflector", protocol.udpReflector)
            .put("min_layer", protocol.minLayer)
            .put("max_layer", protocol.maxLayer)
            .put("library_versions", JSONArray().apply {
                protocol.libraryVersions.ifEmpty { listOf("tdlib") }.forEach(::put)
            })
    }

    private fun callProtocolObject(): Any? {
        val protocol = callProtocolProvider()
        Log.d(
            CALL_LOG_TAG,
            "protocol min=${protocol.minLayer} max=${protocol.maxLayer} " +
                "p2p=${protocol.udpP2p} reflector=${protocol.udpReflector} " +
                "versions=${protocol.libraryVersions.joinToString(",")}"
        )
        return newTdApiOptional("CallProtocol")?.apply {
            setFieldIfPresent("udpP2p", protocol.udpP2p)
            setFieldIfPresent("udpReflector", protocol.udpReflector)
            setFieldIfPresent("minLayer", protocol.minLayer)
            setFieldIfPresent("maxLayer", protocol.maxLayer)
            setFieldIfPresent("libraryVersions", protocol.libraryVersions.ifEmpty { listOf("tdlib") }.toTypedArray())
        }
    }

    private fun inputStoryContentJson(localPath: String, kind: MessageKind): JSONObject? {
        return when (kind) {
            MessageKind.Image -> json("inputStoryContentPhoto")
                .put("photo", inputFileLocalJson(localPath))
                .put("added_sticker_file_ids", JSONArray())
            MessageKind.Video -> json("inputStoryContentVideo")
                .put("video", inputFileLocalJson(localPath))
                .put("duration", 0.0)
                .put("width", 0)
                .put("height", 0)
                .put("added_sticker_file_ids", JSONArray())
            MessageKind.File,
            MessageKind.Text -> null
        }
    }

    private fun inputStoryContentObject(localPath: String, kind: MessageKind): Any? {
        return when (kind) {
            MessageKind.Image -> newTdApiOptional("InputStoryContentPhoto")?.apply {
                setFieldIfPresent("photo", inputFileLocalOrNull(localPath))
                setFieldIfPresent("addedStickerFileIds", intArrayOf())
            }
            MessageKind.Video -> newTdApiOptional("InputStoryContentVideo")?.apply {
                setFieldIfPresent("video", inputFileLocalOrNull(localPath))
                setFieldIfPresent("duration", 0.0)
                setFieldIfPresent("width", 0)
                setFieldIfPresent("height", 0)
                setFieldIfPresent("addedStickerFileIds", intArrayOf())
            }
            MessageKind.File,
            MessageKind.Text -> null
        }
    }

    private fun inputFileLocalJson(path: String): JSONObject {
        return json("inputFileLocal").put("path", path)
    }

    private fun messageSenderUserJson(userId: Long): JSONObject {
        return json("messageSenderUser").put("user_id", userId)
    }

    private fun messageSenderUserObject(userId: Long): Any? {
        return newTdApiOrNull("MessageSenderUser")?.apply {
            setFieldIfPresent("userId", userId)
        }
    }

    private fun inputChatPhotoStaticObject(path: String): Any? {
        return newTdApiOrNull("InputChatPhotoStatic")?.apply {
            setFieldIfPresent("photo", inputFileLocalOrNull(path))
        }
    }

    private fun premiumFeatureJson(feature: TelegramPremiumFeature): JSONObject {
        return json(feature.tdJsonName())
    }

    private fun premiumFeatureObject(feature: TelegramPremiumFeature): Any? {
        return newTdApiOptional(feature.tdObjectName())
    }

    private fun TelegramPremiumFeature.tdJsonName(): String {
        return when (this) {
            TelegramPremiumFeature.Stories -> "premiumFeatureStories"
            TelegramPremiumFeature.Reactions -> "premiumFeatureReactions"
            TelegramPremiumFeature.Stickers -> "premiumFeatureStickers"
            TelegramPremiumFeature.Downloads -> "premiumFeatureDownloadSpeed"
            TelegramPremiumFeature.Business -> "premiumFeatureBusiness"
            TelegramPremiumFeature.VoiceToText -> "premiumFeatureVoiceRecognition"
            TelegramPremiumFeature.ProfileBadge -> "premiumFeatureProfileBadge"
            TelegramPremiumFeature.EmojiStatus -> "premiumFeatureEmojiStatus"
        }
    }

    private fun TelegramPremiumFeature.tdObjectName(): String {
        return when (this) {
            TelegramPremiumFeature.Stories -> "PremiumFeatureStories"
            TelegramPremiumFeature.Reactions -> "PremiumFeatureReactions"
            TelegramPremiumFeature.Stickers -> "PremiumFeatureStickers"
            TelegramPremiumFeature.Downloads -> "PremiumFeatureDownloadSpeed"
            TelegramPremiumFeature.Business -> "PremiumFeatureBusiness"
            TelegramPremiumFeature.VoiceToText -> "PremiumFeatureVoiceRecognition"
            TelegramPremiumFeature.ProfileBadge -> "PremiumFeatureProfileBadge"
            TelegramPremiumFeature.EmojiStatus -> "PremiumFeatureEmojiStatus"
        }
    }

    private fun premiumLimitTypeJson(type: TelegramPremiumLimitType): JSONObject {
        return json(type.tdJsonName())
    }

    private fun premiumLimitTypeObject(type: TelegramPremiumLimitType): Any? {
        return newTdApiOptional(type.tdObjectName())
    }

    private fun TelegramPremiumLimitType.tdJsonName(): String {
        return when (this) {
            TelegramPremiumLimitType.ChatFolderCount -> "premiumLimitTypeChatFolderCount"
            TelegramPremiumLimitType.PinnedChatCount -> "premiumLimitTypePinnedChatCount"
            TelegramPremiumLimitType.CaptionLength -> "premiumLimitTypeCaptionLength"
            TelegramPremiumLimitType.BioLength -> "premiumLimitTypeBioLength"
            TelegramPremiumLimitType.ChatFilterChosenChatCount -> "premiumLimitTypeChatFolderChosenChatCount"
        }
    }

    private fun TelegramPremiumLimitType.tdObjectName(): String {
        return when (this) {
            TelegramPremiumLimitType.ChatFolderCount -> "PremiumLimitTypeChatFolderCount"
            TelegramPremiumLimitType.PinnedChatCount -> "PremiumLimitTypePinnedChatCount"
            TelegramPremiumLimitType.CaptionLength -> "PremiumLimitTypeCaptionLength"
            TelegramPremiumLimitType.BioLength -> "PremiumLimitTypeBioLength"
            TelegramPremiumLimitType.ChatFilterChosenChatCount -> "PremiumLimitTypeChatFolderChosenChatCount"
        }
    }

    private fun inputBusinessChatLinkObject(message: String): Any? {
        return newTdApiOptional("InputBusinessChatLink")?.apply {
            setFieldIfPresent("message", formattedTextObject(message))
            setFieldIfPresent("title", "")
        }
    }

    private fun privacySettingJson(setting: TelegramPrivacySetting): JSONObject {
        return json(
            when (setting) {
                TelegramPrivacySetting.LastSeen -> "userPrivacySettingShowStatus"
                TelegramPrivacySetting.ProfilePhoto -> "userPrivacySettingShowProfilePhoto"
                TelegramPrivacySetting.PhoneNumber -> "userPrivacySettingShowPhoneNumber"
                TelegramPrivacySetting.ForwardedMessages -> "userPrivacySettingShowLinkInForwardedMessages"
                TelegramPrivacySetting.Calls -> "userPrivacySettingAllowCalls"
                TelegramPrivacySetting.ChatInvites -> "userPrivacySettingAllowChatInvites"
                TelegramPrivacySetting.Bio -> "userPrivacySettingShowBio"
            }
        )
    }

    private fun privacySettingObject(setting: TelegramPrivacySetting): Any? {
        return newTdApiOrNull(
            when (setting) {
                TelegramPrivacySetting.LastSeen -> "UserPrivacySettingShowStatus"
                TelegramPrivacySetting.ProfilePhoto -> "UserPrivacySettingShowProfilePhoto"
                TelegramPrivacySetting.PhoneNumber -> "UserPrivacySettingShowPhoneNumber"
                TelegramPrivacySetting.ForwardedMessages -> "UserPrivacySettingShowLinkInForwardedMessages"
                TelegramPrivacySetting.Calls -> "UserPrivacySettingAllowCalls"
                TelegramPrivacySetting.ChatInvites -> "UserPrivacySettingAllowChatInvites"
                TelegramPrivacySetting.Bio -> "UserPrivacySettingShowBio"
            }
        )
    }

    private fun privacyRulesJson(preset: TelegramPrivacyPreset): JSONObject {
        return json("userPrivacySettingRules")
            .put("rules", JSONArray().put(privacyRuleJson(preset)))
    }

    private fun privacyRuleJson(preset: TelegramPrivacyPreset): JSONObject {
        return json(
            when (preset) {
                TelegramPrivacyPreset.Everybody -> "userPrivacySettingRuleAllowAll"
                TelegramPrivacyPreset.Contacts -> "userPrivacySettingRuleAllowContacts"
                TelegramPrivacyPreset.Nobody -> "userPrivacySettingRuleRestrictAll"
            }
        )
    }

    private fun privacyRulesObject(preset: TelegramPrivacyPreset): Any? {
        return newTdApiOrNull("UserPrivacySettingRules")?.apply {
            val rule = privacyRuleObject(preset)
            setFieldIfPresent("rules", if (rule == null) emptyArray<Any>() else arrayOf(rule))
        }
    }

    private fun privacyRuleObject(preset: TelegramPrivacyPreset): Any? {
        return newTdApiOrNull(
            when (preset) {
                TelegramPrivacyPreset.Everybody -> "UserPrivacySettingRuleAllowAll"
                TelegramPrivacyPreset.Contacts -> "UserPrivacySettingRuleAllowContacts"
                TelegramPrivacyPreset.Nobody -> "UserPrivacySettingRuleRestrictAll"
            }
        )
    }

    private fun contactJson(userId: Long, firstName: String, lastName: String, phoneNumber: String): JSONObject {
        return json("contact")
            .put("phone_number", phoneNumber)
            .put("first_name", firstName)
            .put("last_name", lastName)
            .put("vcard", "")
            .put("user_id", userId)
    }

    private fun contactObject(userId: Long, firstName: String, lastName: String, phoneNumber: String): Any? {
        return newTdApiOrNull("Contact")?.apply {
            setFieldIfPresent("phoneNumber", phoneNumber)
            setFieldIfPresent("firstName", firstName)
            setFieldIfPresent("lastName", lastName)
            setFieldIfPresent("vcard", "")
            setFieldIfPresent("userId", userId)
        }
    }

    private fun chatPermissionsJson(preset: TelegramChatPermissionPreset): JSONObject {
        val values = preset.toPermissionValues()
        return json("chatPermissions")
            .put("can_send_basic_messages", values.basic)
            .put("can_send_audios", values.media)
            .put("can_send_documents", values.media)
            .put("can_send_photos", values.media)
            .put("can_send_videos", values.media)
            .put("can_send_video_notes", values.media)
            .put("can_send_voice_notes", values.media)
            .put("can_send_polls", values.polls)
            .put("can_send_other_messages", values.other)
            .put("can_add_link_previews", values.links)
            .put("can_change_info", values.changeInfo)
            .put("can_invite_users", values.inviteUsers)
            .put("can_pin_messages", values.pinMessages)
            .put("can_create_topics", values.createTopics)
    }

    private fun chatPermissionsObject(preset: TelegramChatPermissionPreset): Any? {
        val values = preset.toPermissionValues()
        return newTdApiOrNull("ChatPermissions")?.apply {
            setFieldIfPresent("canSendBasicMessages", values.basic)
            setFieldIfPresent("canSendAudios", values.media)
            setFieldIfPresent("canSendDocuments", values.media)
            setFieldIfPresent("canSendPhotos", values.media)
            setFieldIfPresent("canSendVideos", values.media)
            setFieldIfPresent("canSendVideoNotes", values.media)
            setFieldIfPresent("canSendVoiceNotes", values.media)
            setFieldIfPresent("canSendPolls", values.polls)
            setFieldIfPresent("canSendOtherMessages", values.other)
            setFieldIfPresent("canAddLinkPreviews", values.links)
            setFieldIfPresent("canChangeInfo", values.changeInfo)
            setFieldIfPresent("canInviteUsers", values.inviteUsers)
            setFieldIfPresent("canPinMessages", values.pinMessages)
            setFieldIfPresent("canCreateTopics", values.createTopics)
        }
    }

    private fun chatAdministratorRightsJson(): JSONObject {
        return json("chatAdministratorRights")
            .put("can_manage_chat", true)
            .put("can_change_info", true)
            .put("can_post_messages", true)
            .put("can_edit_messages", true)
            .put("can_delete_messages", true)
            .put("can_invite_users", true)
            .put("can_restrict_members", true)
            .put("can_pin_messages", true)
            .put("can_manage_topics", true)
            .put("can_promote_members", false)
            .put("can_manage_video_chats", true)
            .put("can_post_stories", true)
            .put("can_edit_stories", true)
            .put("can_delete_stories", true)
            .put("is_anonymous", false)
    }

    private fun chatAdministratorRightsObject(): Any? {
        return newTdApiOrNull("ChatAdministratorRights")?.apply {
            setFieldIfPresent("canManageChat", true)
            setFieldIfPresent("canChangeInfo", true)
            setFieldIfPresent("canPostMessages", true)
            setFieldIfPresent("canEditMessages", true)
            setFieldIfPresent("canDeleteMessages", true)
            setFieldIfPresent("canInviteUsers", true)
            setFieldIfPresent("canRestrictMembers", true)
            setFieldIfPresent("canPinMessages", true)
            setFieldIfPresent("canManageTopics", true)
            setFieldIfPresent("canPromoteMembers", false)
            setFieldIfPresent("canManageVideoChats", true)
            setFieldIfPresent("canPostStories", true)
            setFieldIfPresent("canEditStories", true)
            setFieldIfPresent("canDeleteStories", true)
            setFieldIfPresent("isAnonymous", false)
        }
    }

    private fun TelegramChatPermissionPreset.toPermissionValues(): ChatPermissionValues {
        return when (this) {
            TelegramChatPermissionPreset.READ_ONLY -> ChatPermissionValues()
            TelegramChatPermissionPreset.TEXT_ONLY -> ChatPermissionValues(
                basic = true,
                inviteUsers = true
            )
            TelegramChatPermissionPreset.MEDIA_ALLOWED -> ChatPermissionValues(
                basic = true,
                media = true,
                links = true,
                inviteUsers = true
            )
            TelegramChatPermissionPreset.FULL -> ChatPermissionValues(
                basic = true,
                media = true,
                polls = true,
                other = true,
                links = true,
                inviteUsers = true,
                pinMessages = true,
                createTopics = true
            )
        }
    }

    private data class ChatPermissionValues(
        val basic: Boolean = false,
        val media: Boolean = false,
        val polls: Boolean = false,
        val other: Boolean = false,
        val links: Boolean = false,
        val changeInfo: Boolean = false,
        val inviteUsers: Boolean = false,
        val pinMessages: Boolean = false,
        val createTopics: Boolean = false
    )

    private fun hasRequiredTdApiClasses(): Boolean {
        val requiredClassNames = listOf(
            "org.drinkless.tdlib.TdApi\$Function",
            "org.drinkless.tdlib.TdApi\$SetTdlibParameters",
            "org.drinkless.tdlib.TdApi\$GetAuthorizationState",
            "org.drinkless.tdlib.TdApi\$SetAuthenticationPhoneNumber",
            "org.drinkless.tdlib.TdApi\$CheckAuthenticationCode",
            "org.drinkless.tdlib.TdApi\$CheckAuthenticationPassword",
            "org.drinkless.tdlib.TdApi\$ResendAuthenticationCode",
            "org.drinkless.tdlib.TdApi\$LoadChats",
            "org.drinkless.tdlib.TdApi\$ChatListMain",
            "org.drinkless.tdlib.TdApi\$GetChatHistory",
            "org.drinkless.tdlib.TdApi\$GetUser",
            "org.drinkless.tdlib.TdApi\$GetChat",
            "org.drinkless.tdlib.TdApi\$LogOut",
            "org.drinkless.tdlib.TdApi\$Close"
        )
        return requiredClassNames.all { className ->
            runCatching { Class.forName(className) }.isSuccess
        }
    }

    private fun proxy(interfaceClass: Class<*>, block: (Any?) -> Unit): Any {
        return Proxy.newProxyInstance(interfaceClass.classLoader, arrayOf(interfaceClass)) { _, _, args ->
            block(args?.firstOrNull())
            null
        }
    }

    private fun Any.field(name: String): Any? {
        return runCatching {
            javaClass.getField(name).get(this)
        }.getOrNull()
    }

    private fun Any.setFieldIfPresent(name: String, value: Any?) {
        runCatching { javaClass.getField(name).set(this, value) }
    }

    private fun Any.messageIdsField(): List<Long> {
        return when (val value = field("messageIds")) {
            is LongArray -> value.toList()
            is Array<*> -> value.mapNotNull { (it as? Number)?.toLong() }
            is Iterable<*> -> value.mapNotNull { (it as? Number)?.toLong() }
            else -> emptyList()
        }
    }

    private fun JSONArray.firstString(): String? {
        for (index in 0 until length()) {
            val value = optString(index)
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun Any?.canResendAuthenticationCode(): Boolean {
        if (this == null) return false
        val codeInfo = field("codeInfo") ?: return false
        return codeInfo.field("nextType") != null
    }

    private fun Any?.passwordHint(): String {
        return (this?.field("passwordHint") as? String).orEmpty()
    }

    private fun JSONObject?.canResendAuthenticationCode(): Boolean {
        if (this == null) return false
        val codeInfo = optJSONObject("code_info") ?: return false
        return !codeInfo.isNull("next_type")
    }

    private fun JSONObject.messageIds(): List<Long> {
        val messages = optJSONArray("messages") ?: return emptyList()
        return (0 until messages.length())
            .mapNotNull { index ->
                messages.optJSONObject(index)
                    ?.optLong("id", Long.MIN_VALUE)
                    ?.takeIf { it != Long.MIN_VALUE }
            }
    }

    private fun JSONObject.deletedMessageIds(): List<Long> {
        val ids = optJSONArray("message_ids") ?: return emptyList()
        return (0 until ids.length())
            .mapNotNull { index ->
                ids.optLong(index, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
            }
    }

    private fun JSONObject.extraLong(prefix: String): Long {
        return optString("@extra")
            .takeIf { it.startsWith(prefix) }
            ?.substringAfter(prefix)
            ?.toLongOrNull()
            ?: 0L
    }

    private fun String.historyLoadOrNull(messageIds: List<Long>): TelegramChatHistoryLoad? {
        val parts = split(":")
        if (parts.size != 4 || parts[0] != "history") return null
        val chatId = parts[1].toLongOrNull() ?: return null
        val fromMessageId = parts[2].toLongOrNull() ?: return null
        val requestedLimit = parts[3].toIntOrNull() ?: return null
        return TelegramChatHistoryLoad(
            chatId = chatId,
            fromMessageId = fromMessageId,
            messageIds = messageIds,
            requestedLimit = requestedLimit
        )
    }

    private fun JSONObject.extraPrivacySetting(): TelegramPrivacySetting {
        return optString("@extra")
            .takeIf { it.startsWith("privacy:") }
            ?.substringAfter("privacy:")
            ?.let { name -> TelegramPrivacySetting.entries.firstOrNull { it.name == name } }
            ?: TelegramPrivacySetting.LastSeen
    }

    private fun JSONObject.extraPremiumLimitType(): TelegramPremiumLimitType {
        return optString("@extra")
            .takeIf { it.startsWith("premium_limit:") }
            ?.substringAfter("premium_limit:")
            ?.let { name -> TelegramPremiumLimitType.entries.firstOrNull { it.name == name } }
            ?: TelegramPremiumLimitType.ChatFolderCount
    }

    private fun Any?.sendState(): MessageSyncState {
        return when (this?.javaClass?.simpleName) {
            "MessageSendingStatePending" -> MessageSyncState.Sending
            "MessageSendingStateFailed" -> MessageSyncState.Failed
            else -> MessageSyncState.Synced
        }
    }

    private fun JSONObject?.sendState(): MessageSyncState {
        return when (this?.optString("@type")) {
            "messageSendingStatePending" -> MessageSyncState.Sending
            "messageSendingStateFailed" -> MessageSyncState.Failed
            else -> MessageSyncState.Synced
        }
    }

    private fun objectParserSenderId(sender: Any): String {
        return when (sender.javaClass.simpleName) {
            "MessageSenderUser" -> (sender.field("userId") as? Number)?.toLong()?.let { "user:$it" }.orEmpty()
            "MessageSenderChat" -> (sender.field("chatId") as? Number)?.toLong()?.let { "chat:$it" }.orEmpty()
            else -> ""
        }
    }

    private fun JSONObject?.jsonSenderId(): String {
        return when (this?.optString("@type")) {
            "messageSenderUser" -> "user:${optLong("user_id")}"
            "messageSenderChat" -> "chat:${optLong("chat_id")}"
            else -> ""
        }
    }

    private fun Any?.chatActionLabel(): String {
        return when (this?.javaClass?.simpleName) {
            null, "ChatActionCancel" -> ""
            "ChatActionTyping" -> "dang nhap..."
            "ChatActionRecordingVideo" -> "dang quay video..."
            "ChatActionUploadingVideo" -> "dang tai video..."
            "ChatActionRecordingVoiceNote" -> "dang ghi am..."
            "ChatActionUploadingVoiceNote" -> "dang tai audio..."
            "ChatActionUploadingPhoto" -> "dang tai anh..."
            "ChatActionUploadingDocument" -> "dang tai tep..."
            "ChatActionChoosingSticker" -> "dang chon sticker..."
            else -> "dang thao tac..."
        }
    }

    private fun JSONObject?.chatActionLabel(): String {
        return when (this?.optString("@type")) {
            null, "chatActionCancel" -> ""
            "chatActionTyping" -> "dang nhap..."
            "chatActionRecordingVideo" -> "dang quay video..."
            "chatActionUploadingVideo" -> "dang tai video..."
            "chatActionRecordingVoiceNote" -> "dang ghi am..."
            "chatActionUploadingVoiceNote" -> "dang tai audio..."
            "chatActionUploadingPhoto" -> "dang tai anh..."
            "chatActionUploadingDocument" -> "dang tai tep..."
            "chatActionChoosingSticker" -> "dang chon sticker..."
            else -> "dang thao tac..."
        }
    }
}

