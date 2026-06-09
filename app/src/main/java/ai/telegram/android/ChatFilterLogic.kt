package ai.telegram.android

import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.TelegramChat
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.ui.MessagePrivacyPolicy

internal fun TelegramChat.matchesFolderFilter(filter: ChatFolderFilter): Boolean {
    return when (filter) {
        ChatFolderFilter.All -> true
        ChatFolderFilter.Unread -> unreadCount > 0
        ChatFolderFilter.Private -> !isChannelChat() && !isGroupChat() && !isSecretChat()
        ChatFolderFilter.Secret -> isSecretChat()
        ChatFolderFilter.Groups -> isGroupChat()
        ChatFolderFilter.Channels -> isChannelChat()
    }
}

internal fun TelegramMessage.matchesContentFilter(filter: ChatContentFilter): Boolean {
    return when (filter) {
        ChatContentFilter.All -> true
        ChatContentFilter.Media -> kind == MessageKind.Image || kind == MessageKind.Video
        ChatContentFilter.Files -> kind == MessageKind.File
        ChatContentFilter.Links -> containsVisibleLink()
    }
}

internal fun TelegramMessage.matchesDateFilter(filter: ChatDateFilter): Boolean {
    if (filter == ChatDateFilter.All) return true
    val timestamp = receivedAtMillis.takeIf { it > 0L } ?: return false
    val ageMillis = System.currentTimeMillis() - timestamp
    return when (filter) {
        ChatDateFilter.All -> true
        ChatDateFilter.Today -> ageMillis in 0L..ONE_DAY_MILLIS
        ChatDateFilter.Last7Days -> ageMillis in 0L..(7L * ONE_DAY_MILLIS)
        ChatDateFilter.Last30Days -> ageMillis in 0L..(30L * ONE_DAY_MILLIS)
    }
}

internal fun TelegramMessage.containsVisibleLink(): Boolean {
    return visibleLinkSources().any { text -> UrlPattern.containsMatchIn(text) }
}

internal fun TelegramMessage.firstVisibleLink(): String? {
    return visibleLinkSources().firstNotNullOfOrNull { text ->
        UrlPattern.find(text)?.value?.let(::normalizeUrl)
    }
}

internal fun List<TelegramMessage>.senderFilterOptions(): List<ChatSenderFilterOption> {
    return groupBy { message -> message.senderId }
        .map { (senderId, senderMessages) ->
            ChatSenderFilterOption(
                senderId = senderId,
                label = senderMessages.firstOrNull()?.author?.takeIf { it.isNotBlank() } ?: senderId,
                count = senderMessages.size
            )
        }
        .sortedWith(
            compareByDescending<ChatSenderFilterOption> { it.count }
                .thenBy { it.label.lowercase() }
        )
        .take(MAX_SENDER_FILTER_OPTIONS)
}

private fun TelegramMessage.visibleLinkSources(): List<String> {
    return listOf(
        MessagePrivacyPolicy.readableText(this),
        originalText,
        mediaFileName
    )
}

private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
private const val MAX_SENDER_FILTER_OPTIONS = 12
