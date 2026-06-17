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
        ChatContentFilter.Media -> isSharedMediaKind()
        ChatContentFilter.PhotosVideos -> isPhotoOrVideoKind()
        ChatContentFilter.Audio -> kind == MessageKind.Audio
        ChatContentFilter.Voice -> kind == MessageKind.Voice
        ChatContentFilter.Stickers -> kind == MessageKind.Sticker
        ChatContentFilter.Files -> kind == MessageKind.File
        ChatContentFilter.Links -> containsVisibleLink()
    }
}

internal fun TelegramMessage.matchesSharedGalleryFilter(filter: SharedGalleryFilter): Boolean {
    return when (filter) {
        SharedGalleryFilter.Media -> isSharedMediaKind()
        SharedGalleryFilter.PhotosVideos -> isPhotoOrVideoKind()
        SharedGalleryFilter.Audio -> kind == MessageKind.Audio
        SharedGalleryFilter.Voice -> kind == MessageKind.Voice
        SharedGalleryFilter.Stickers -> kind == MessageKind.Sticker
        SharedGalleryFilter.Files -> kind == MessageKind.File
        SharedGalleryFilter.Links -> containsVisibleLink()
    }
}

internal fun TelegramMessage.isPhotoOrVideoKind(): Boolean {
    return kind == MessageKind.Image ||
        kind == MessageKind.Video ||
        kind == MessageKind.VideoNote
}

internal fun TelegramMessage.isSharedMediaKind(): Boolean {
    return isPhotoOrVideoKind() ||
        kind == MessageKind.Audio ||
        kind == MessageKind.Voice ||
        kind == MessageKind.Sticker
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

internal fun mergeDisplayedMessages(
    current: List<TelegramMessage>,
    incoming: List<TelegramMessage>
): List<TelegramMessage> {
    if (incoming.isEmpty() || current.isEmpty() || incoming.size >= current.size) return incoming
    if (incoming.size > MAX_PARTIAL_MESSAGE_SNAPSHOT_SIZE) return incoming

    val mergedByKey = current.associateBy { it.displayMergeKey() }.toMutableMap()
    incoming.forEach { message -> mergedByKey[message.displayMergeKey()] = message }
    return mergedByKey.values.sortedWith(MessageDisplayComparator)
}

internal fun shouldRequestLatestMessages(
    visibleMessageCount: Int,
    lastVisibleItemIndex: Int?
): Boolean {
    if (visibleMessageCount <= 0 || lastVisibleItemIndex == null) return false
    val firstLatestLoadIndex = (visibleMessageCount - MESSAGE_EDGE_LOAD_THRESHOLD + 1).coerceAtLeast(1)
    return lastVisibleItemIndex >= firstLatestLoadIndex
}

internal fun shouldRequestOlderMessages(
    visibleMessageCount: Int,
    firstVisibleItemIndex: Int?
): Boolean {
    return visibleMessageCount > 0 &&
        firstVisibleItemIndex != null &&
        firstVisibleItemIndex <= MESSAGE_EDGE_LOAD_THRESHOLD
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
private const val MAX_PARTIAL_MESSAGE_SNAPSHOT_SIZE = 3
private const val MESSAGE_EDGE_LOAD_THRESHOLD = 3

private val MessageDisplayComparator = compareByDescending<TelegramMessage> {
    it.receivedAtMillis.takeIf { timestamp -> timestamp > 0L } ?: it.id
}.thenByDescending { it.id }

private fun TelegramMessage.displayMergeKey(): String = "$chatId:$id"
