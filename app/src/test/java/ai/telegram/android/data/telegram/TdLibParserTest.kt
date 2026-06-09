package ai.telegram.android.data.telegram

import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.MessageSyncState
import ai.telegram.android.data.TranslationStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TdLibParserTest {
    @Test
    fun jsonParser_parsesTextMessageAndSender() {
        val message = JSONObject(
            """
            {
              "id": 42,
              "chat_id": 100,
              "date": 1700000000,
              "sender_id": {"@type": "messageSenderUser", "user_id": 7},
              "content": {
                "@type": "messageText",
                "text": {"text": "Hello from Telegram"}
              }
            }
            """.trimIndent()
        )

        val parsed = TdLibJsonParser(nowMillis = { 1_700_000_000_000L }).parseMessage(message)

        assertNotNull(parsed)
        assertEquals("100:42", "${parsed!!.chatId}:${parsed.id}")
        assertEquals("user:7", parsed.senderId)
        assertEquals("Hello from Telegram", parsed.originalText)
        assertEquals(TranslationStatus.Pending, parsed.translationStatus)
    }

    @Test
    fun parsers_useSourceTextForChatPreviewBeforeTranslationReady() {
        val jsonChat = JSONObject(
            """
            {
              "id": 100,
              "title": "Preview chat",
              "type": {"@type": "chatTypePrivate"},
              "last_message": {
                "id": 42,
                "chat_id": 100,
                "date": 1700000000,
                "sender_id": {"@type": "messageSenderUser", "user_id": 7},
                "content": {
                  "@type": "messageText",
                  "text": {"text": "Hello preview"}
                }
              }
            }
            """.trimIndent()
        )

        val jsonParsed = TdLibJsonParser(nowMillis = { 1_700_000_000_000L }).parseChat(jsonChat)

        assertNotNull(jsonParsed)
        assertEquals("Hello preview", jsonParsed!!.lastMessagePreview)

        val objectParsed = TdLibObjectParser(nowMillis = { 1_700_000_000_000L }).parseChat(
            TdChat(
                id = 101,
                title = "Object preview",
                type = ChatTypePrivate(),
                unreadCount = 0,
                lastMessage = TdMessage(
                    id = 5,
                    chatId = 101,
                    date = 1700000000,
                    senderId = MessageSenderChat(chatId = 101),
                    content = MessageText(FormattedText("Object preview text"))
                )
            )
        )

        assertNotNull(objectParsed)
        assertEquals("Object preview text", objectParsed!!.lastMessagePreview)
    }

    @Test
    fun jsonParser_usesMediaKindForChatPreviewWhenMessageHasNoText() {
        val jsonChat = JSONObject(
            """
            {
              "id": 102,
              "title": "Media preview",
              "type": {"@type": "chatTypePrivate"},
              "last_message": {
                "id": 43,
                "chat_id": 102,
                "date": 1700000000,
                "sender_id": {"@type": "messageSenderUser", "user_id": 7},
                "content": {
                  "@type": "messagePhoto",
                  "photo": {
                    "sizes": [
                      {
                        "width": 100,
                        "height": 100,
                        "photo": {"id": 9, "size": 1024}
                      }
                    ]
                  }
                }
              }
            }
            """.trimIndent()
        )

        val parsed = TdLibJsonParser(nowMillis = { 1_700_000_000_000L }).parseChat(jsonChat)

        assertNotNull(parsed)
        assertEquals("Image", parsed!!.lastMessagePreview)
    }

    @Test
    fun jsonParser_parsesFileUpdateLocalPathAndPrefix() {
        val prefixFile = JSONObject(
            """
            {
              "@type": "file",
              "id": 99,
              "size": 2097153,
              "local": {
                "path": "/tmp/video.mp4",
                "downloaded_prefix_size": 1024,
                "is_downloading_completed": false
              }
            }
            """.trimIndent()
        )

        val parsed = TdLibJsonParser().parseFileUpdate(prefixFile)

        assertNotNull(parsed)
        assertEquals(99, parsed!!.id)
        assertEquals("/tmp/video.mp4", parsed.localPath)
        assertEquals(3, parsed.sizeMb)
        assertEquals(1024L, parsed.downloadedPrefixBytes)

        val completedFile = JSONObject(
            """
            {
              "@type": "file",
              "id": 99,
              "size": 2097153,
              "local": {
                "path": "/tmp/video.mp4",
                "downloaded_prefix_size": 2097153,
                "is_downloading_completed": true
              }
            }
            """.trimIndent()
        )

        val completed = TdLibJsonParser().parseFileUpdate(completedFile)

        assertNotNull(completed)
        assertEquals("/tmp/video.mp4", completed!!.localPath)
        assertEquals(2097153L, completed.downloadedPrefixBytes)
    }

    @Test
    fun jsonParser_parsesPollQuestionAsReadableText() {
        val message = JSONObject(
            """
            {
              "id": 77,
              "chat_id": 100,
              "date": 1700000000,
              "sender_id": {"@type": "messageSenderUser", "user_id": 7},
              "content": {
                "@type": "messagePoll",
                "poll": {
                  "question": {"text": "Which feature next?"}
                }
              }
            }
            """.trimIndent()
        )

        val parsed = TdLibJsonParser(nowMillis = { 1_700_000_000_000L }).parseMessage(message)

        assertNotNull(parsed)
        assertEquals("Poll: Which feature next?", parsed!!.originalText)
        assertEquals(MessageKind.Text, parsed.kind)
    }

    @Test
    fun jsonParser_parsesMessageSyncFlags() {
        val message = JSONObject(
            """
            {
              "id": 79,
              "chat_id": 100,
              "date": 1700000000,
              "is_outgoing": true,
              "is_pinned": true,
              "edit_date": 1700000010,
              "sending_state": {"@type": "messageSendingStateFailed"},
              "sender_id": {"@type": "messageSenderUser", "user_id": 7},
              "content": {
                "@type": "messageText",
                "text": {"text": "Failed outgoing"}
              }
            }
            """.trimIndent()
        )

        val parsed = TdLibJsonParser(nowMillis = { 1_700_000_000_000L }).parseMessage(message)

        assertNotNull(parsed)
        assertEquals(MessageSyncState.Failed, parsed!!.syncState)
        assertEquals(true, parsed.isOutgoing)
        assertEquals(false, parsed.isRead)
        assertEquals(true, parsed.isEdited)
        assertEquals(true, parsed.isPinned)
    }

    @Test
    fun jsonParser_parsesContactAsReadableText() {
        val message = JSONObject(
            """
            {
              "id": 78,
              "chat_id": 100,
              "date": 1700000000,
              "sender_id": {"@type": "messageSenderUser", "user_id": 7},
              "content": {
                "@type": "messageContact",
                "contact": {
                  "first_name": "Ada",
                  "last_name": "Lovelace",
                  "phone_number": "+441234"
                }
              }
            }
            """.trimIndent()
        )

        val parsed = TdLibJsonParser(nowMillis = { 1_700_000_000_000L }).parseMessage(message)

        assertNotNull(parsed)
        assertEquals("Contact: Ada Lovelace • +441234", parsed!!.originalText)
        assertEquals(MessageKind.Text, parsed.kind)
    }

    @Test
    fun objectParser_parsesTextMessageAndChatIds() {
        val parser = TdLibObjectParser(nowMillis = { 1_700_000_000_000L })
        val message = TdMessage(
            id = 5,
            chatId = 44,
            date = 1700000000,
            senderId = MessageSenderChat(chatId = 44),
            content = MessageText(FormattedText("Hi object binding"))
        )

        val parsed = parser.parseMessage(message)

        assertNotNull(parsed)
        assertEquals(44L, parsed!!.chatId)
        assertEquals(5L, parsed.id)
        assertEquals("chat:44", parsed.senderId)
        assertEquals("Hi object binding", parsed.originalText)
        assertEquals(MessageKind.Text, parsed.kind)
        assertEquals(listOf(1L, 2L), parser.chatIdsFromResult(Chats(longArrayOf(1L, 2L)))!!.toList())
    }

    @Test
    fun objectParser_parsesMessageSyncFlags() {
        val parser = TdLibObjectParser(nowMillis = { 1_700_000_000_000L })
        val message = TdMessage(
            id = 10,
            chatId = 44,
            date = 1700000000,
            senderId = MessageSenderChat(chatId = 44),
            content = MessageText(FormattedText("Pending object binding")),
            isOutgoing = true,
            sendingState = MessageSendingStatePending(),
            editDate = 1700000010,
            isPinned = true
        )

        val parsed = parser.parseMessage(message)

        assertNotNull(parsed)
        assertEquals(MessageSyncState.Sending, parsed!!.syncState)
        assertEquals(true, parsed.isOutgoing)
        assertEquals(false, parsed.isRead)
        assertEquals(true, parsed.isEdited)
        assertEquals(true, parsed.isPinned)
    }

    @Test
    fun objectParser_parsesPollQuestionAsReadableText() {
        val parser = TdLibObjectParser(nowMillis = { 1_700_000_000_000L })
        val message = TdMessage(
            id = 8,
            chatId = 44,
            date = 1700000000,
            senderId = MessageSenderChat(chatId = 44),
            content = MessagePoll(Poll(FormattedText("Lunch choice?")))
        )

        val parsed = parser.parseMessage(message)

        assertNotNull(parsed)
        assertEquals("Poll: Lunch choice?", parsed!!.originalText)
        assertEquals(MessageKind.Text, parsed.kind)
    }

    @Test
    fun objectParser_parsesContactAsReadableText() {
        val parser = TdLibObjectParser(nowMillis = { 1_700_000_000_000L })
        val message = TdMessage(
            id = 9,
            chatId = 44,
            date = 1700000000,
            senderId = MessageSenderChat(chatId = 44),
            content = MessageContact(Contact("Grace", "Hopper", "+12025550100"))
        )

        val parsed = parser.parseMessage(message)

        assertNotNull(parsed)
        assertEquals("Contact: Grace Hopper • +12025550100", parsed!!.originalText)
        assertEquals(MessageKind.Text, parsed.kind)
    }

    @Test
    fun commandMapper_extractsJoinAndOpenUsernames() {
        assertEquals("androiddev", TdLibCommandMapper.usernameForJoin("https://t.me/androiddev"))
        assertEquals("channel_name", TdLibCommandMapper.extractTelegramUsername("https://t.me/s/channel_name?before=1"))
        assertEquals(true, TdLibCommandMapper.isInviteLink("https://t.me/+abcdef"))
    }

    @Test
    fun parsers_markSecretChatsAsSecretType() {
        val jsonChat = JSONObject(
            """
            {
              "id": 700,
              "title": "Alice secret",
              "type": {"@type": "chatTypeSecret"},
              "unread_count": 2
            }
            """.trimIndent()
        )
        val jsonParsed = TdLibJsonParser(nowMillis = { 1_700_000_000_000L }).parseChat(jsonChat)

        assertNotNull(jsonParsed)
        assertEquals("secret", jsonParsed!!.type)
        assertEquals(2, jsonParsed.unreadCount)

        val objectParsed = TdLibObjectParser(nowMillis = { 1_700_000_000_000L }).parseChat(
            TdChat(
                id = 701,
                title = "Bob secret",
                type = ChatTypeSecret(),
                unreadCount = 1
            )
        )

        assertNotNull(objectParsed)
        assertEquals("secret", objectParsed!!.type)
        assertEquals(1, objectParsed.unreadCount)
    }

    @Test
    fun parsers_mapCallUpdatesToTelegramCallState() {
        val jsonCall = JSONObject(
            """
            {
              "id": 17,
              "user_id": 42,
              "is_outgoing": true,
              "is_video": false,
              "duration": 9,
              "state": {"@type": "callStateReady"}
            }
            """.trimIndent()
        )
        val jsonParsed = TdLibJsonParser().parseCall(jsonCall)

        assertNotNull(jsonParsed)
        assertEquals(17, jsonParsed!!.id)
        assertEquals(42L, jsonParsed.userId)
        assertEquals(true, jsonParsed.isOutgoing)
        assertEquals(false, jsonParsed.isVideo)
        assertEquals(TelegramCallState.Ready, jsonParsed.state)

        val objectParsed = TdLibObjectParser().parseCall(
            TdCall(
                id = 18,
                userId = 43,
                isOutgoing = false,
                isVideo = true,
                duration = 0,
                state = CallStatePending()
            )
        )

        assertNotNull(objectParsed)
        assertEquals(18, objectParsed!!.id)
        assertEquals(43L, objectParsed.userId)
        assertEquals(false, objectParsed.isOutgoing)
        assertEquals(true, objectParsed.isVideo)
        assertEquals(TelegramCallState.Pending, objectParsed.state)
    }

    @Test
    fun parsers_mapStoriesToTelegramStoryModel() {
        val jsonStories = JSONObject(
            """
            {
              "@type": "chatActiveStories",
              "chat_id": 500,
              "stories": [
                {
                  "id": 3,
                  "date": 1700000000,
                  "caption": {"text": "Story caption"},
                  "content": {
                    "@type": "storyContentPhoto",
                    "photo": {
                      "sizes": [
                        {
                          "width": 100,
                          "height": 100,
                          "photo": {
                            "id": 77,
                            "size": 1024,
                            "local": {"path": "/tmp/story-photo.jpg"}
                          }
                        }
                      ]
                    }
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val jsonParsed = TdLibJsonParser(nowMillis = { 1_700_000_000_000L }).parseStoriesResult(jsonStories)

        assertEquals(1, jsonParsed.size)
        assertEquals(500L, jsonParsed.single().chatId)
        assertEquals(3, jsonParsed.single().id)
        assertEquals("Story caption", jsonParsed.single().caption)
        assertEquals(MessageKind.Image, jsonParsed.single().kind)
        assertEquals(77, jsonParsed.single().mediaFileId)
        assertEquals("/tmp/story-photo.jpg", jsonParsed.single().mediaLocalPath)

        val objectParsed = TdLibObjectParser(nowMillis = { 1_700_000_000_000L }).parseStoriesResult(
            ActiveStories(
                chatId = 501,
                stories = arrayOf(
                    TdStory(
                        id = 4,
                        content = StoryContentVideo(
                            Video(
                                video = TdFile(88, local = FileLocal(path = "/tmp/story-video.mp4")),
                                thumbnail = Thumbnail(
                                    TdFile(
                                        89,
                                        local = FileLocal(
                                            path = "/tmp/story-thumb.jpg",
                                            isDownloadingCompleted = true
                                        )
                                    )
                                )
                            )
                        ),
                        caption = FormattedText("Video story")
                    )
                )
            )
        )

        assertEquals(1, objectParsed.size)
        assertEquals(501L, objectParsed.single().chatId)
        assertEquals(4, objectParsed.single().id)
        assertEquals("Video story", objectParsed.single().caption)
        assertEquals(MessageKind.Video, objectParsed.single().kind)
        assertEquals(88, objectParsed.single().mediaFileId)
        assertEquals("/tmp/story-video.mp4", objectParsed.single().mediaLocalPath)
        assertEquals(89, objectParsed.single().mediaThumbnailFileId)
        assertEquals("/tmp/story-thumb.jpg", objectParsed.single().mediaThumbnailLocalPath)
    }

    @Test
    fun parsers_mapBotPlatformResults() {
        val jsonParser = TdLibJsonParser()
        val jsonCommands = JSONObject(
            """
            {
              "@type": "botCommands",
              "commands": [
                {"command": "start", "description": "Start the bot"},
                {"command": "help", "description": "Show help"}
              ]
            }
            """.trimIndent()
        )
        val jsonInline = JSONObject(
            """
            {
              "@type": "inlineQueryResults",
              "query_id": 99,
              "next_offset": "next",
              "results": [
                {"@type": "inlineQueryResultArticle", "id": "a1", "title": "Article", "description": "Preview"}
              ]
            }
            """.trimIndent()
        )

        assertEquals("start", jsonParser.parseBotCommandsResult(jsonCommands, 7L).first().command)
        assertEquals("Open", jsonParser.parseBotMenuButton(JSONObject("""{"@type":"botMenuButtonWebApp","text":"Open","url":"https://t.me/app"}"""), 7L).text)
        val jsonInlineParsed = jsonParser.parseInlineBotResults(jsonInline, 7L)
        assertEquals(99L, jsonInlineParsed.queryId)
        assertEquals("a1", jsonInlineParsed.results.single().id)
        assertEquals("https://t.me/app", jsonParser.parseWebAppUrl(JSONObject("""{"@type":"webAppUrl","url":"https://t.me/app"}"""), 7L)?.url)

        val objectParser = TdLibObjectParser()
        assertEquals(
            "settings",
            objectParser.parseBotCommandsResult(
                BotCommands(arrayOf(BotCommand("settings", "Open settings"))),
                8L
            ).single().command
        )
        assertEquals("Menu", objectParser.parseBotMenuButton(BotMenuButtonWebApp("Menu", "https://t.me/menu"), 8L)?.text)
        val objectInlineParsed = objectParser.parseInlineBotResults(
            InlineQueryResults(100L, "", arrayOf(InlineQueryResultArticle("r1", "Result", "Desc"))),
            8L
        )
        assertEquals(100L, objectInlineParsed.queryId)
        assertEquals("r1", objectInlineParsed.results.single().id)
        assertEquals("https://t.me/web", objectParser.parseWebAppUrl(WebAppUrl("https://t.me/web"), 8L)?.url)
    }

    @Test
    fun parsers_mapAccountPrivacyResults() {
        val jsonParser = TdLibJsonParser()
        val jsonPrivacy = JSONObject(
            """
            {
              "@type": "userPrivacySettingRules",
              "rules": [{"@type": "userPrivacySettingRuleRestrictAll"}]
            }
            """.trimIndent()
        )
        val jsonSessions = JSONObject(
            """
            {
              "@type": "sessions",
              "sessions": [
                {
                  "id": 123,
                  "application_name": "AI Telegram",
                  "device_model": "Pixel",
                  "platform": "Android",
                  "ip": "127.0.0.1",
                  "country": "VN",
                  "is_current": true,
                  "last_active_date": 1700000000
                }
              ]
            }
            """.trimIndent()
        )

        val jsonPrivacyParsed = jsonParser.parsePrivacyRulesResult(jsonPrivacy, TelegramPrivacySetting.LastSeen)
        assertEquals(TelegramPrivacyPreset.Nobody, jsonPrivacyParsed.preset)
        val jsonSession = jsonParser.parseActiveSessions(jsonSessions).single()
        assertEquals(123L, jsonSession.id)
        assertEquals(true, jsonSession.isCurrent)
        assertEquals("Pixel", jsonSession.deviceModel)

        val objectParser = TdLibObjectParser()
        val objectPrivacyParsed = objectParser.parsePrivacyRulesResult(
            UserPrivacySettingRules(arrayOf(UserPrivacySettingRuleAllowAll())),
            TelegramPrivacySetting.ProfilePhoto
        )
        assertEquals(TelegramPrivacyPreset.Everybody, objectPrivacyParsed.preset)
        val objectSession = objectParser.parseActiveSessions(
            Sessions(arrayOf(Session(456L, "Telegram", "Android", isCurrent = false)))
        ).single()
        assertEquals(456L, objectSession.id)
        assertEquals("Telegram", objectSession.applicationName)
        assertEquals(false, objectSession.isCurrent)
    }

    @Test
    fun parsers_mapPremiumBusinessMonetizationResults() {
        val jsonParser = TdLibJsonParser()
        val jsonFeatures = JSONObject(
            """
            {
              "@type": "premiumFeatures",
              "features": [
                {"@type": "premiumFeatureBusiness", "title": "Business", "description": "Business tools"}
              ]
            }
            """.trimIndent()
        )
        val jsonTransactions = JSONObject(
            """
            {
              "@type": "starTransactions",
              "transactions": [
                {"id": "tx1", "star_amount": {"star_count": 42}, "title": "Sale", "description": "Paid media"}
              ]
            }
            """.trimIndent()
        )
        val jsonLinks = JSONObject(
            """
            {
              "@type": "businessChatLinks",
              "links": [
                {"link": "https://t.me/m/test", "message": {"text": "Hello"}, "title": "Lead"}
              ]
            }
            """.trimIndent()
        )

        assertEquals("Business", jsonParser.parsePremiumFeatures(jsonFeatures).single().title)
        assertEquals(
            10,
            jsonParser.parsePremiumLimit(
                JSONObject("""{"@type":"premiumLimit","default_value":10,"premium_value":20}"""),
                TelegramPremiumLimitType.BioLength
            ).defaultValue
        )
        assertEquals(99L, jsonParser.parseStarBalance(JSONObject("""{"@type":"starAmount","star_count":99}"""))?.amount)
        assertEquals(42L, jsonParser.parseStarTransactions(jsonTransactions).single().amount)
        assertEquals("Hello", jsonParser.parseBusinessChatLinks(jsonLinks).single().text)

        val objectParser = TdLibObjectParser()
        assertEquals(
            "PremiumFeatureBusiness",
            objectParser.parsePremiumFeatures(PremiumFeatures(arrayOf(PremiumFeatureBusiness("Business", "Tools")))).single().type
        )
        assertEquals(
            20,
            objectParser.parsePremiumLimit(PremiumLimit(10, 20), TelegramPremiumLimitType.BioLength).premiumValue
        )
        assertEquals(7L, objectParser.parseStarBalance(StarAmount(7L))?.amount)
        assertEquals(5L, objectParser.parseStarTransactions(StarTransactions(arrayOf(StarTransaction("tx2", StarAmount(5L))))).single().amount)
        assertEquals("https://t.me/m/link", objectParser.parseBusinessChatLinks(BusinessChatLinks(arrayOf(BusinessChatLink("https://t.me/m/link", FormattedText("Hi"))))).single().link)
    }

    private class TdMessage(
        @JvmField val id: Long,
        @JvmField val chatId: Long,
        @JvmField val date: Int,
        @JvmField val senderId: Any,
        @JvmField val content: Any,
        @JvmField val isOutgoing: Boolean = false,
        @JvmField val sendingState: Any? = null,
        @JvmField val editDate: Int = 0,
        @JvmField val isPinned: Boolean = false
    )

    private class MessageSenderChat(@JvmField val chatId: Long)
    private class MessageText(@JvmField val text: FormattedText)
    private class MessagePoll(@JvmField val poll: Poll)
    private class Poll(@JvmField val question: FormattedText)
    private class MessageContact(@JvmField val contact: Contact)
    private class Contact(
        @JvmField val firstName: String,
        @JvmField val lastName: String,
        @JvmField val phoneNumber: String
    )
    private class FormattedText(@JvmField val text: String)
    private class Chats(@JvmField val chatIds: LongArray)
    private class MessageSendingStatePending
    private class TdChat(
        @JvmField val id: Long,
        @JvmField val title: String,
        @JvmField val type: Any,
        @JvmField val unreadCount: Int,
        @JvmField val lastMessage: Any? = null
    )
    private class ChatTypeSecret
    private class ChatTypePrivate
    private class TdCall(
        @JvmField val id: Int,
        @JvmField val userId: Long,
        @JvmField val isOutgoing: Boolean,
        @JvmField val isVideo: Boolean,
        @JvmField val duration: Int,
        @JvmField val state: Any
    )
    private class CallStatePending
    private class ActiveStories(
        @JvmField val chatId: Long,
        @JvmField val stories: Array<Any>
    )
    private class TdStory(
        @JvmField val id: Int,
        @JvmField val content: Any,
        @JvmField val caption: FormattedText,
        @JvmField val date: Int = 1700000000,
        @JvmField val isOutgoing: Boolean = false,
        @JvmField val isPinned: Boolean = false,
        @JvmField val isEdited: Boolean = false,
        @JvmField val hasViewerViewed: Boolean = false,
        @JvmField val sendingState: Any? = null
    )
    private class StoryContentVideo(@JvmField val video: Video)
    private class Video(
        @JvmField val video: TdFile,
        @JvmField val thumbnail: Thumbnail? = null,
        @JvmField val mimeType: String = "video/mp4",
        @JvmField val fileName: String = "story.mp4"
    )
    private class Thumbnail(@JvmField val file: TdFile)
    private class TdFile(
        @JvmField val id: Int,
        @JvmField val size: Int = 1024,
        @JvmField val local: FileLocal = FileLocal()
    )
    private class FileLocal(
        @JvmField val path: String = "",
        @JvmField val downloadedPrefixSize: Long = 0L,
        @JvmField val isDownloadingCompleted: Boolean = false
    )
    private class BotCommands(@JvmField val commands: Array<Any>)
    private class BotCommand(
        @JvmField val command: String,
        @JvmField val description: String
    )
    private class BotMenuButtonWebApp(
        @JvmField val text: String,
        @JvmField val url: String
    )
    private class InlineQueryResults(
        @JvmField val inlineQueryId: Long,
        @JvmField val nextOffset: String,
        @JvmField val results: Array<Any>
    )
    private class InlineQueryResultArticle(
        @JvmField val id: String,
        @JvmField val title: String,
        @JvmField val description: String
    )
    private class WebAppUrl(@JvmField val url: String)
    private class UserPrivacySettingRules(@JvmField val rules: Array<Any>)
    private class UserPrivacySettingRuleAllowAll
    private class Sessions(@JvmField val sessions: Array<Any>)
    private class Session(
        @JvmField val id: Long,
        @JvmField val applicationName: String,
        @JvmField val platform: String,
        @JvmField val applicationVersion: String = "",
        @JvmField val deviceModel: String = "",
        @JvmField val systemVersion: String = "",
        @JvmField val ip: String = "",
        @JvmField val country: String = "",
        @JvmField val region: String = "",
        @JvmField val isCurrent: Boolean = false,
        @JvmField val lastActiveDate: Int = 0
    )
    private class PremiumFeatures(@JvmField val features: Array<Any>)
    private class PremiumFeatureBusiness(
        @JvmField val title: String,
        @JvmField val description: String
    )
    private class PremiumLimit(
        @JvmField val defaultValue: Int,
        @JvmField val premiumValue: Int
    )
    private class StarAmount(
        @JvmField val starCount: Long,
        @JvmField val nanostarCount: Int = 0
    )
    private class StarTransactions(@JvmField val transactions: Array<Any>)
    private class StarTransaction(
        @JvmField val id: String,
        @JvmField val starAmount: StarAmount,
        @JvmField val title: String = "",
        @JvmField val description: String = "",
        @JvmField val date: Int = 0
    )
    private class BusinessChatLinks(@JvmField val links: Array<Any>)
    private class BusinessChatLink(
        @JvmField val link: String,
        @JvmField val message: FormattedText,
        @JvmField val title: String = ""
    )
}
