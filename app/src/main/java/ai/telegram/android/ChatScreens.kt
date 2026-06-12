package ai.telegram.android

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import ai.telegram.android.billing.DonationBillingState
import ai.telegram.android.billing.DonationProduct
import ai.telegram.android.data.HiddenContent
import ai.telegram.android.data.MediaDownloadPolicy
import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.MessageSyncState
import ai.telegram.android.data.NetworkMode
import ai.telegram.android.data.TelegramChat
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TelegramSender
import ai.telegram.android.data.TranslationFailureReason
import ai.telegram.android.data.TranslationStatus
import ai.telegram.android.data.translation.MlKitModelDownloadProgress
import ai.telegram.android.data.translation.MlKitTranslationModelInfo
import ai.telegram.android.data.translation.TranslationLanguagePair
import ai.telegram.android.data.translation.VietnameseTranslationPairs
import ai.telegram.android.data.translation.VideoSubtitleCue
import ai.telegram.android.data.translation.VideoSubtitleGenerator
import ai.telegram.android.data.translation.VideoSubtitleMode
import ai.telegram.android.data.translation.VideoSubtitleResult
import ai.telegram.android.data.telegram.MessageSendOptions
import ai.telegram.android.data.telegram.TdLibStatus
import ai.telegram.android.data.telegram.TelegramMessageSearchFilter
import ai.telegram.android.ui.AiTelegramTheme
import ai.telegram.android.ui.AiThemeTokens
import ai.telegram.android.ui.ChatMessageVisibilityPolicy
import ai.telegram.android.ui.MessagePrivacyPolicy
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun ChatScreen(
    chats: List<TelegramChat>,
    selectedChatId: Long?,
    selectedChatOverride: TelegramChat? = null,
    messages: List<TelegramMessage>,
    pendingSharedText: String = "",
    pendingSharedUris: List<Uri> = emptyList(),
    translatedOnly: Boolean,
    allowAdultContent: Boolean,
    autoPlayVideo: Boolean,
    videoSubtitlesEnabled: Boolean,
    videoSourceLanguage: VideoSourceLanguage,
    videoSubtitleColor: VideoSubtitleColor,
    contentTranslationTargetLanguage: String = ContentTranslationLanguage.DefaultCode,
    onSelectChat: (TelegramChat) -> Unit,
    onBackToChats: () -> Unit,
    hiddenHashes: Set<String>,
    onCurrentMessageViewed: (TelegramMessage) -> Unit,
    onOpenCache: () -> Unit,
    onSendMessage: (Long, String, MessageSendOptions) -> Unit,
    onSendReplyMessage: (Long, Long, String, MessageSendOptions) -> Unit,
    onSendMedia: (Long, PendingComposerMedia, String, MessageSendOptions) -> Unit,
    onSendMediaAlbum: (Long, List<PendingComposerMedia>, String, MessageSendOptions) -> Unit,
    onSendPoll: (Long, String, List<String>, Boolean, Boolean, MessageSendOptions) -> Unit,
    onSendContact: (Long, String, String, String, MessageSendOptions) -> Unit,
    onSetDraft: (Long, String) -> Unit,
    onClearDraft: (Long) -> Unit,
    onEditMessage: (TelegramMessage, String) -> Unit,
    onDeleteMessage: (TelegramMessage) -> Unit,
    onForwardMessage: (TelegramMessage, Long) -> Unit,
    onDeleteMessages: (Long, List<Long>) -> Unit,
    onForwardMessages: (Long, List<Long>, Long) -> Unit,
    onResendMessage: (TelegramMessage) -> Unit,
    onPinMessage: (TelegramMessage) -> Unit,
    onUnpinMessage: (TelegramMessage) -> Unit,
    onReactToMessage: (TelegramMessage, String) -> Unit,
    onLoadOlderMessages: (Long, Long) -> Unit,
    onSearchChatMessages: (Long, String, TelegramMessageSearchFilter) -> Unit,
    onSearchPublicPosts: (String, TelegramMessageSearchFilter) -> Unit,
    onRefreshChats: () -> Unit,
    onOpenTelegramLink: (String) -> Unit,
    onDownloadMedia: (Int, MessageKind) -> Unit,
    onRetryTranslation: (TelegramMessage) -> Unit,
    onRestartMediaDownload: (TelegramMessage) -> Unit = { message -> onDownloadMedia(message.mediaFileId, message.kind) },
    onPendingShareConsumed: () -> Unit = {},
    onHide: (TelegramMessage) -> Unit,
    onUnhide: (TelegramMessage) -> Unit
) {
    TelegramLikeChatScreen(
        chats = chats,
        selectedChatId = selectedChatId,
        selectedChatOverride = selectedChatOverride,
        messages = messages,
        pendingSharedText = pendingSharedText,
        pendingSharedUris = pendingSharedUris,
        translatedOnly = translatedOnly,
        allowAdultContent = allowAdultContent,
        autoPlayVideo = autoPlayVideo,
        videoSubtitlesEnabled = videoSubtitlesEnabled,
        videoSourceLanguage = videoSourceLanguage,
        videoSubtitleColor = videoSubtitleColor,
        contentTranslationTargetLanguage = contentTranslationTargetLanguage,
        onSelectChat = onSelectChat,
        onBackToChats = onBackToChats,
        hiddenHashes = hiddenHashes,
        onCurrentMessageViewed = onCurrentMessageViewed,
        onOpenCache = onOpenCache,
        onSendMessage = onSendMessage,
        onSendReplyMessage = onSendReplyMessage,
        onSendMedia = onSendMedia,
        onSendMediaAlbum = onSendMediaAlbum,
        onSendPoll = onSendPoll,
        onSendContact = onSendContact,
        onSetDraft = onSetDraft,
        onClearDraft = onClearDraft,
        onEditMessage = onEditMessage,
        onDeleteMessage = onDeleteMessage,
        onForwardMessage = onForwardMessage,
        onDeleteMessages = onDeleteMessages,
        onForwardMessages = onForwardMessages,
        onResendMessage = onResendMessage,
        onPinMessage = onPinMessage,
        onUnpinMessage = onUnpinMessage,
        onReactToMessage = onReactToMessage,
        onLoadOlderMessages = onLoadOlderMessages,
        onSearchChatMessages = onSearchChatMessages,
        onSearchPublicPosts = onSearchPublicPosts,
        onRefreshChats = onRefreshChats,
        onOpenTelegramLink = onOpenTelegramLink,
        onDownloadMedia = onDownloadMedia,
        onRetryTranslation = onRetryTranslation,
        onRestartMediaDownload = onRestartMediaDownload,
        onPendingShareConsumed = onPendingShareConsumed,
        onHide = onHide,
        onUnhide = onUnhide
    )
}

@Composable
private fun TelegramLikeChatScreen(
    chats: List<TelegramChat>,
    selectedChatId: Long?,
    selectedChatOverride: TelegramChat?,
    messages: List<TelegramMessage>,
    pendingSharedText: String,
    pendingSharedUris: List<Uri>,
    translatedOnly: Boolean,
    allowAdultContent: Boolean,
    autoPlayVideo: Boolean,
    videoSubtitlesEnabled: Boolean,
    videoSourceLanguage: VideoSourceLanguage,
    videoSubtitleColor: VideoSubtitleColor,
    contentTranslationTargetLanguage: String,
    onSelectChat: (TelegramChat) -> Unit,
    onBackToChats: () -> Unit,
    hiddenHashes: Set<String>,
    onCurrentMessageViewed: (TelegramMessage) -> Unit,
    onOpenCache: () -> Unit,
    onSendMessage: (Long, String, MessageSendOptions) -> Unit,
    onSendReplyMessage: (Long, Long, String, MessageSendOptions) -> Unit,
    onSendMedia: (Long, PendingComposerMedia, String, MessageSendOptions) -> Unit,
    onSendMediaAlbum: (Long, List<PendingComposerMedia>, String, MessageSendOptions) -> Unit,
    onSendPoll: (Long, String, List<String>, Boolean, Boolean, MessageSendOptions) -> Unit,
    onSendContact: (Long, String, String, String, MessageSendOptions) -> Unit,
    onSetDraft: (Long, String) -> Unit,
    onClearDraft: (Long) -> Unit,
    onEditMessage: (TelegramMessage, String) -> Unit,
    onDeleteMessage: (TelegramMessage) -> Unit,
    onForwardMessage: (TelegramMessage, Long) -> Unit,
    onDeleteMessages: (Long, List<Long>) -> Unit,
    onForwardMessages: (Long, List<Long>, Long) -> Unit,
    onResendMessage: (TelegramMessage) -> Unit,
    onPinMessage: (TelegramMessage) -> Unit,
    onUnpinMessage: (TelegramMessage) -> Unit,
    onReactToMessage: (TelegramMessage, String) -> Unit,
    onLoadOlderMessages: (Long, Long) -> Unit,
    onSearchChatMessages: (Long, String, TelegramMessageSearchFilter) -> Unit,
    onSearchPublicPosts: (String, TelegramMessageSearchFilter) -> Unit,
    onRefreshChats: () -> Unit,
    onOpenTelegramLink: (String) -> Unit,
    onDownloadMedia: (Int, MessageKind) -> Unit,
    onRetryTranslation: (TelegramMessage) -> Unit,
    onRestartMediaDownload: (TelegramMessage) -> Unit,
    onPendingShareConsumed: () -> Unit,
    onHide: (TelegramMessage) -> Unit,
    onUnhide: (TelegramMessage) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var chatFolderFilter by rememberSaveable { mutableStateOf(ChatFolderFilter.All) }
    var chatContentFilter by rememberSaveable(selectedChatId) { mutableStateOf(ChatContentFilter.All) }
    var chatDateFilter by rememberSaveable(selectedChatId) { mutableStateOf(ChatDateFilter.All) }
    var sharedGalleryOpen by remember(selectedChatId) { mutableStateOf(false) }
    var galleryMediaViewerMessageKey by rememberSaveable(selectedChatId) { mutableStateOf<String?>(null) }
    var selectedSenderFilterId by rememberSaveable(selectedChatId) { mutableStateOf<String?>(null) }
    var draftMessage by remember(selectedChatId) { mutableStateOf("") }
    var replyToMessage by remember(selectedChatId) { mutableStateOf<TelegramMessage?>(null) }
    var silentSend by rememberSaveable(selectedChatId) { mutableStateOf(false) }
    var scheduledSend by rememberSaveable(selectedChatId) { mutableStateOf(false) }
    var scheduleDelayMinutes by rememberSaveable(selectedChatId) { mutableStateOf("30") }
    var highQualityPhotos by rememberSaveable(selectedChatId) { mutableStateOf(false) }
    var selectedMessageKeys by remember(selectedChatId) { mutableStateOf<Set<String>>(emptySet()) }
    var bulkForwardDialogOpen by remember(selectedChatId) { mutableStateOf(false) }
    var bulkForwardChatId by remember(selectedChatId) { mutableStateOf("") }
    var bulkDeleteDialogOpen by remember(selectedChatId) { mutableStateOf(false) }
    var pollDialogOpen by remember(selectedChatId) { mutableStateOf(false) }
    var pollQuestion by remember(selectedChatId) { mutableStateOf("") }
    var pollOptions by remember(selectedChatId) { mutableStateOf(listOf("", "")) }
    var pollAnonymous by remember(selectedChatId) { mutableStateOf(true) }
    var pollMultipleAnswers by remember(selectedChatId) { mutableStateOf(false) }
    var contactDialogOpen by remember(selectedChatId) { mutableStateOf(false) }
    var contactFirstName by remember(selectedChatId) { mutableStateOf("") }
    var contactLastName by remember(selectedChatId) { mutableStateOf("") }
    var contactPhoneNumber by remember(selectedChatId) { mutableStateOf("") }
    var pendingMedia by remember(selectedChatId) { mutableStateOf<List<PendingComposerMedia>>(emptyList()) }
    var pendingCameraUri by remember(selectedChatId) { mutableStateOf<Uri?>(null) }
    var activeVideoKey by rememberSaveable(selectedChatId) { mutableStateOf<String?>(null) }
    val selectedChat = chats.firstOrNull { it.id == selectedChatId }
        ?: selectedChatOverride?.takeIf { it.id == selectedChatId }
    val currentSendOptions = remember(silentSend, scheduledSend, scheduleDelayMinutes) {
        val cleanDelayMinutes = scheduleDelayMinutes.toIntOrNull()?.coerceIn(1, 365 * 24 * 60) ?: 0
        val scheduledAt = if (scheduledSend && cleanDelayMinutes > 0) {
            ((System.currentTimeMillis() / 1000L) + cleanDelayMinutes * 60L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        } else {
            0
        }
        MessageSendOptions(
            disableNotification = silentSend,
            scheduledAtEpochSeconds = scheduledAt
        )
    }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboardScope = rememberCoroutineScope()
    fun appendPendingMedia(
        uris: List<Uri>,
        forcedKind: MessageKind? = null,
        highQualityPhoto: Boolean = false
    ) {
        val remainingSlots = (MAX_COMPOSER_MEDIA - pendingMedia.size).coerceAtLeast(0)
        if (remainingSlots > 0) {
            pendingMedia = pendingMedia + uris
                .take(remainingSlots)
                .map { uri ->
                    context.pendingComposerMedia(
                        uri = uri,
                        forcedKind = forcedKind,
                        highQualityPhoto = highQualityPhoto
                    )
                }
        }
    }
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { appendPendingMedia(listOf(it), highQualityPhoto = highQualityPhotos) }
    }
    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { appendPendingMedia(listOf(it)) }
    }
    val voicePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { appendPendingMedia(listOf(it), forcedKind = MessageKind.Voice) }
    }
    val videoMessagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { appendPendingMedia(listOf(it), forcedKind = MessageKind.VideoNote) }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { appendPendingMedia(listOf(it)) }
    }
    val albumPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        appendPendingMedia(uris, highQualityPhoto = highQualityPhotos)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = pendingCameraUri
        if (captured && uri != null) {
            pendingMedia = pendingMedia + PendingComposerMedia(
                uri = uri,
                kind = MessageKind.Image,
                displayName = resources.getString(R.string.composer_camera_photo_name),
                highQualityPhoto = highQualityPhotos
            )
        }
        pendingCameraUri = null
    }

    if (selectedChat == null) {
        val filteredChats = remember(chats, query, chatFolderFilter) {
            val normalizedQuery = query.trim()
            chats
                .filter { chat -> chat.matchesFolderFilter(chatFolderFilter) }
                .filter { chat ->
                    normalizedQuery.isBlank() ||
                    chat.title.contains(normalizedQuery, ignoreCase = true) ||
                        chat.lastMessagePreview.contains(normalizedQuery, ignoreCase = true)
                }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AiThemeTokens.ScreenPadding),
            contentPadding = PaddingValues(top = AiThemeTokens.ScreenTopPadding),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
        ) {
            item {
                ChatStatsHero(
                    totalChats = chats.size,
                    unreadChats = chats.count { it.unreadCount > 0 },
                    filteredChats = filteredChats.size,
                    onRefresh = onRefreshChats
                )
            }
            item {
                ChatSearchAndFilters(
                    query = query,
                    onQueryChange = { query = it },
                    chats = chats,
                    selectedFilter = chatFolderFilter,
                    onFilterChange = { chatFolderFilter = it }
                )
            }
            if (filteredChats.isEmpty()) {
                item {
                    EmptyState(
                        if (query.isBlank() && chatFolderFilter != ChatFolderFilter.All) {
                            stringResource(R.string.no_chats_in_filter)
                        } else {
                            stringResource(R.string.no_chats_found)
                        }
                    )
                }
            } else {
                items(filteredChats, key = { it.id }) { chat ->
                    ChatListRow(
                        chat = chat,
                        onClick = {
                            if (BuildConfig.DEBUG) {
                                Log.d(ChatTapLogTag, "ChatScreen row tap chatId=${chat.id} title=${chat.title}")
                            }
                            onSelectChat(chat)
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(AiThemeTokens.ScreenBottomPadding)) }
        }
        return
    }

    val normalizedMessageQuery = query.trim()
    val activeMessageSearchFilter = remember(chatContentFilter) {
        chatContentFilter.toTelegramMessageSearchFilter()
    }
    var displayedMessages by remember(selectedChat.id) { mutableStateOf(messages) }
    var showMessageSkeleton by remember(selectedChat.id) { mutableStateOf(messages.isEmpty()) }
    LaunchedEffect(selectedChat.id, messages) {
        if (messages.isNotEmpty()) {
            if (showMessageSkeleton && displayedMessages.isEmpty()) {
                delay(MESSAGE_CONTENT_CROSSFADE_MS.toLong())
            }
            displayedMessages = mergeDisplayedMessages(displayedMessages, messages)
            showMessageSkeleton = false
        } else if (displayedMessages.isEmpty()) {
            showMessageSkeleton = true
            delay(MESSAGE_LOADING_DEBOUNCE_MS)
            showMessageSkeleton = false
        } else {
            showMessageSkeleton = false
            delay(MESSAGE_EMPTY_RELOAD_GRACE_MS)
            displayedMessages = emptyList()
        }
    }
    val baseVisibleMessages = remember(
        displayedMessages,
        translatedOnly,
        allowAdultContent,
        hiddenHashes,
        contentTranslationTargetLanguage
    ) {
        ChatMessageVisibilityPolicy.baseVisibleMessages(
            messages = displayedMessages,
            translatedOnly = translatedOnly,
            allowAdultContent = allowAdultContent,
            hiddenHashes = hiddenHashes,
            targetLanguage = contentTranslationTargetLanguage
        )
    }
    val senderFilterOptions = remember(baseVisibleMessages) {
        baseVisibleMessages.senderFilterOptions()
    }
    LaunchedEffect(selectedChat.id, senderFilterOptions) {
        if (
            selectedSenderFilterId != null &&
            senderFilterOptions.none { option -> option.senderId == selectedSenderFilterId }
        ) {
            selectedSenderFilterId = null
        }
    }
    val senderFilteredMessages = remember(baseVisibleMessages, selectedSenderFilterId) {
        selectedSenderFilterId?.let { senderId ->
            baseVisibleMessages.filter { message -> message.senderId == senderId }
        } ?: baseVisibleMessages
    }
    val dateFilteredMessages = remember(senderFilteredMessages, chatDateFilter) {
        senderFilteredMessages.filter { message -> message.matchesDateFilter(chatDateFilter) }
    }
    val visibleMessages = remember(dateFilteredMessages, chatContentFilter) {
        dateFilteredMessages.filter { message -> message.matchesContentFilter(chatContentFilter) }
    }
    val searchResultIndexes = remember(visibleMessages, normalizedMessageQuery) {
        if (normalizedMessageQuery.isBlank()) {
            emptyList()
        } else {
            visibleMessages.mapIndexedNotNull { index, message ->
                index.takeIf {
                    MessagePrivacyPolicy.matchesReadableQuery(
                        message,
                        normalizedMessageQuery,
                        targetLanguage = contentTranslationTargetLanguage
                    )
                }
            }
        }
    }
    var selectedSearchResult by remember(
        selectedChat.id,
        normalizedMessageQuery,
        chatContentFilter,
        chatDateFilter,
        selectedSenderFilterId
    ) { mutableIntStateOf(0) }
    val activeSearchResult = selectedSearchResult.coerceIn(0, (searchResultIndexes.size - 1).coerceAtLeast(0))
    val activeSearchMessageKey = searchResultIndexes
        .getOrNull(activeSearchResult)
        ?.let { index -> visibleMessages.getOrNull(index) }
        ?.let { message -> "${message.chatId}:${message.id}" }
    val visibleMediaMessages = remember(visibleMessages) {
        visibleMessages.filter { it.kind != MessageKind.Text }
    }
    val galleryMediaMessages = remember(dateFilteredMessages) {
        dateFilteredMessages.filter { it.isSharedMediaKind() }
    }
    val selectedMessages = remember(visibleMessages, selectedMessageKeys) {
        visibleMessages.filter { message -> message.selectionKey() in selectedMessageKeys }
    }
    val selectionMode = selectedMessageKeys.isNotEmpty()
    val oldestMessageId = remember(displayedMessages) { displayedMessages.minOfOrNull { it.id } ?: 0L }
    val messageListState = rememberLazyListState()
    val shouldLoadOlderMessages by remember(messageListState, visibleMessages) {
        derivedStateOf {
            val layoutInfo = messageListState.layoutInfo
            val firstVisibleIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: return@derivedStateOf false
            visibleMessages.isNotEmpty() && firstVisibleIndex <= 3
        }
    }
    val isNearLatestMessage by remember(messageListState, visibleMessages) {
        derivedStateOf {
            val lastVisibleIndex = messageListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            visibleMessages.isNotEmpty() && lastVisibleIndex >= visibleMessages.size
        }
    }
    val currentViewedMessage by remember(messageListState, visibleMessages) {
        derivedStateOf {
            val layoutInfo = messageListState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val messageItemIndex = layoutInfo.visibleItemsInfo
                .asSequence()
                .filter { item -> item.index > 0 }
                .minByOrNull { item ->
                    abs(item.offset + item.size / 2 - viewportCenter)
                }
                ?.index
                ?.minus(1)
            messageItemIndex?.let { index -> visibleMessages.getOrNull(index) }
        }
    }
    val canAutoHideControls = draftMessage.isBlank() &&
        replyToMessage == null &&
        !selectionMode &&
        normalizedMessageQuery.isBlank() &&
        chatContentFilter == ChatContentFilter.All &&
        chatDateFilter == ChatDateFilter.All &&
        selectedSenderFilterId == null &&
        !silentSend &&
        !scheduledSend
    var controlsHidden by remember(selectedChat.id) { mutableStateOf(false) }
    val showControls = !controlsHidden || !canAutoHideControls

    LaunchedEffect(selectedChat.id, pendingSharedText, pendingSharedUris) {
        if (pendingSharedText.isNotBlank() || pendingSharedUris.isNotEmpty()) {
            if (pendingSharedText.isNotBlank()) {
                draftMessage = listOf(draftMessage, pendingSharedText)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            }
            if (pendingSharedUris.isNotEmpty()) {
                appendPendingMedia(pendingSharedUris)
            }
            controlsHidden = false
            onPendingShareConsumed()
        }
    }

    LaunchedEffect(shouldLoadOlderMessages, selectedChat.id, oldestMessageId) {
        if (shouldLoadOlderMessages && oldestMessageId > 0L) {
            onLoadOlderMessages(selectedChat.id, oldestMessageId)
        }
    }

    LaunchedEffect(selectedChat.id, normalizedMessageQuery, activeMessageSearchFilter) {
        if (normalizedMessageQuery.isBlank()) return@LaunchedEffect
        delay(650)
        onSearchChatMessages(selectedChat.id, normalizedMessageQuery, activeMessageSearchFilter)
    }

    LaunchedEffect(
        messageListState.isScrollInProgress,
        canAutoHideControls,
        isNearLatestMessage,
        selectedChat.id
    ) {
        if (!canAutoHideControls) {
            controlsHidden = false
        } else if (messageListState.isScrollInProgress) {
            controlsHidden = true
        } else if (isNearLatestMessage) {
            controlsHidden = false
        }
    }

    LaunchedEffect(
        selectedChat.id,
        currentViewedMessage?.selectionKey(),
        currentViewedMessage?.translationStatus
    ) {
        currentViewedMessage?.let(onCurrentMessageViewed)
    }

    LaunchedEffect(selectedChat.id, draftMessage) {
        delay(700)
        if (draftMessage.isBlank()) {
            onClearDraft(selectedChat.id)
        } else {
            onSetDraft(selectedChat.id, draftMessage)
        }
    }

    LaunchedEffect(normalizedMessageQuery, activeSearchResult, searchResultIndexes) {
        val messageIndex = searchResultIndexes.getOrNull(activeSearchResult) ?: return@LaunchedEffect
        messageListState.animateScrollToItem((messageIndex + 1).coerceAtLeast(0))
    }

    val searchNavigationScope = rememberCoroutineScope()
    val messageListOverlayState = when {
        visibleMessages.isNotEmpty() -> MessageListOverlayState.None
        showMessageSkeleton && displayedMessages.isEmpty() -> MessageListOverlayState.Loading
        else -> MessageListOverlayState.Empty
    }

    if (bulkDeleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { bulkDeleteDialogOpen = false },
            title = { Text(stringResource(R.string.delete_selected_messages)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.delete_selected_messages_confirm,
                        selectedMessages.size,
                        selectedMessages.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = selectedMessages.map { it.id }
                        if (ids.isNotEmpty()) {
                            onDeleteMessages(selectedChat.id, ids)
                            selectedMessageKeys = emptySet()
                        }
                        bulkDeleteDialogOpen = false
                    }
                ) {
                    Text(stringResource(R.string.delete_message))
                }
            },
            dismissButton = {
                TextButton(onClick = { bulkDeleteDialogOpen = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (bulkForwardDialogOpen) {
        AlertDialog(
            onDismissRequest = { bulkForwardDialogOpen = false },
            title = { Text(stringResource(R.string.forward_selected_messages)) },
            text = {
                OutlinedTextField(
                    value = bulkForwardChatId,
                    onValueChange = { bulkForwardChatId = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.forward_chat_id_hint)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetChatId = bulkForwardChatId.toLongOrNull()
                        val ids = selectedMessages.map { it.id }
                        if (targetChatId != null && ids.isNotEmpty()) {
                            onForwardMessages(selectedChat.id, ids, targetChatId)
                            selectedMessageKeys = emptySet()
                        }
                        bulkForwardChatId = ""
                        bulkForwardDialogOpen = false
                    },
                    enabled = bulkForwardChatId.toLongOrNull() != null && selectedMessages.isNotEmpty()
                ) {
                    Text(stringResource(R.string.forward_message))
                }
            },
            dismissButton = {
                TextButton(onClick = { bulkForwardDialogOpen = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (pollDialogOpen) {
        val cleanPollOptions = pollOptions
            .map { it.trim() }
            .filter { it.isNotBlank() }
        AlertDialog(
            onDismissRequest = { pollDialogOpen = false },
            title = { Text(stringResource(R.string.create_poll)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
                ) {
                    OutlinedTextField(
                        value = pollQuestion,
                        onValueChange = { pollQuestion = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.poll_question_hint)) }
                    )
                    pollOptions.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = option,
                                onValueChange = { value ->
                                    pollOptions = pollOptions.toMutableList().also { options ->
                                        if (index in options.indices) {
                                            options[index] = value
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = {
                                    Text(stringResource(R.string.poll_option_hint, index + 1))
                                }
                            )
                            IconButton(
                                onClick = {
                                    if (pollOptions.size > MIN_POLL_OPTIONS) {
                                        pollOptions = pollOptions.toMutableList().also { options ->
                                            if (index in options.indices) {
                                                options.removeAt(index)
                                            }
                                        }
                                    }
                                },
                                enabled = pollOptions.size > MIN_POLL_OPTIONS
                            ) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_menu_delete),
                                    contentDescription = stringResource(R.string.remove)
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { pollOptions = pollOptions + "" },
                        enabled = pollOptions.size < MAX_POLL_OPTIONS
                    ) {
                        Text(stringResource(R.string.add_poll_option))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.poll_anonymous),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = pollAnonymous,
                            onCheckedChange = { pollAnonymous = it }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.poll_multiple_answers),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = pollMultipleAnswers,
                            onCheckedChange = { pollMultipleAnswers = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSendPoll(
                            selectedChat.id,
                            pollQuestion,
                            cleanPollOptions,
                            pollAnonymous,
                            pollMultipleAnswers,
                            currentSendOptions
                        )
                        pollQuestion = ""
                        pollOptions = listOf("", "")
                        pollAnonymous = true
                        pollMultipleAnswers = false
                        pollDialogOpen = false
                    },
                    enabled = pollQuestion.isNotBlank() && cleanPollOptions.distinct().size >= MIN_POLL_OPTIONS
                ) {
                    Text(stringResource(R.string.send_poll))
                }
            },
            dismissButton = {
                TextButton(onClick = { pollDialogOpen = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (contactDialogOpen) {
        AlertDialog(
            onDismissRequest = { contactDialogOpen = false },
            title = { Text(stringResource(R.string.send_contact)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
                ) {
                    OutlinedTextField(
                        value = contactFirstName,
                        onValueChange = { contactFirstName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.contact_first_name_hint)) }
                    )
                    OutlinedTextField(
                        value = contactLastName,
                        onValueChange = { contactLastName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.contact_last_name_hint)) }
                    )
                    OutlinedTextField(
                        value = contactPhoneNumber,
                        onValueChange = { contactPhoneNumber = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.contact_phone_hint)) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSendContact(
                            selectedChat.id,
                            contactFirstName,
                            contactLastName,
                            contactPhoneNumber,
                            currentSendOptions
                        )
                        contactFirstName = ""
                        contactLastName = ""
                        contactPhoneNumber = ""
                        contactDialogOpen = false
                    },
                    enabled = contactFirstName.isNotBlank() && contactPhoneNumber.isNotBlank()
                ) {
                    Text(stringResource(R.string.send_contact))
                }
            },
            dismissButton = {
                TextButton(onClick = { contactDialogOpen = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (sharedGalleryOpen) {
        SharedMediaGalleryDialog(
            chat = selectedChat,
            messages = dateFilteredMessages,
            mediaMessages = galleryMediaMessages,
            onClose = { sharedGalleryOpen = false },
            onOpenMedia = { message -> galleryMediaViewerMessageKey = message.mediaViewerKey() },
            onDownloadMedia = onDownloadMedia,
            onRestartMediaDownload = onRestartMediaDownload,
            onOpenTelegramLink = onOpenTelegramLink
        )
    }

    galleryMediaViewerMessageKey
        ?.let { key -> galleryMediaMessages.firstOrNull { it.mediaViewerKey() == key } }
        ?.let { message ->
            FullScreenMediaViewer(
                mediaMessages = galleryMediaMessages.ifEmpty { listOf(message) },
                initialMessage = message,
                autoPlayVideo = autoPlayVideo,
                videoSubtitlesEnabled = videoSubtitlesEnabled,
                videoSourceLanguage = videoSourceLanguage,
                videoSubtitleColor = videoSubtitleColor,
                contentTranslationTargetLanguage = contentTranslationTargetLanguage,
                activeVideoKey = activeVideoKey,
                onActiveVideoChange = { activeVideoKey = it },
                onDownloadMedia = onDownloadMedia,
                onRestartMediaDownload = onRestartMediaDownload,
                onClose = { galleryMediaViewerMessageKey = null }
            )
        }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = AiThemeTokens.ScreenPadding,
                            vertical = AiThemeTokens.CompactSpacing
                        ),
                        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
                    ) {
                        ConversationHeader(chat = selectedChat, onBack = onBackToChats)
                        ChatSearchField(value = query, onValueChange = { query = it })
                        ChatContentFilterRow(
                            messages = baseVisibleMessages,
                            selectedFilter = chatContentFilter,
                            onFilterChange = { chatContentFilter = it }
                        )
                        ChatDateFilterRow(
                            messages = senderFilteredMessages,
                            selectedFilter = chatDateFilter,
                            onFilterChange = { chatDateFilter = it }
                        )
                        SharedMediaOpenRow(
                            messages = dateFilteredMessages,
                            onOpen = { sharedGalleryOpen = true }
                        )
                        ChatSenderFilterRow(
                            options = senderFilterOptions,
                            selectedSenderId = selectedSenderFilterId,
                            onSenderChange = { selectedSenderFilterId = it }
                        )
                        if (normalizedMessageQuery.isNotBlank()) {
                            ChatSearchNavigationRow(
                                current = if (searchResultIndexes.isEmpty()) 0 else activeSearchResult + 1,
                                total = searchResultIndexes.size,
                                onSearchPublicPosts = {
                                    onSearchPublicPosts(normalizedMessageQuery, activeMessageSearchFilter)
                                },
                                onPrevious = {
                                    if (searchResultIndexes.isNotEmpty()) {
                                        val nextResult = if (activeSearchResult <= 0) {
                                            searchResultIndexes.lastIndex
                                        } else {
                                            activeSearchResult - 1
                                        }
                                        selectedSearchResult = nextResult
                                        searchNavigationScope.launch {
                                            val targetIndex = searchResultIndexes[nextResult]
                                            messageListState.animateScrollToItem(targetIndex + 1)
                                        }
                                    }
                                },
                                onNext = {
                                    if (searchResultIndexes.isNotEmpty()) {
                                        val nextResult = if (activeSearchResult >= searchResultIndexes.lastIndex) {
                                            0
                                        } else {
                                            activeSearchResult + 1
                                        }
                                        selectedSearchResult = nextResult
                                        searchNavigationScope.launch {
                                            val targetIndex = searchResultIndexes[nextResult]
                                            messageListState.animateScrollToItem(targetIndex + 1)
                                        }
                                    }
                                }
                            )
                        }
                        MessageJumpNavigationRow(
                            total = visibleMessages.size,
                            onFirst = {
                                if (visibleMessages.isNotEmpty()) {
                                    searchNavigationScope.launch {
                                        messageListState.animateScrollToItem(1)
                                    }
                                }
                            },
                            onLast = {
                                if (visibleMessages.isNotEmpty()) {
                                    searchNavigationScope.launch {
                                        messageListState.animateScrollToItem(visibleMessages.size)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        if (selectionMode) {
            BulkMessageSelectionBar(
                selectedCount = selectedMessages.size,
                onCopy = {
                    val copyText = selectedMessages
                        .joinToString("\n\n") { it.readableTextForCopy() }
                        .trim()
                    if (copyText.isNotBlank()) {
                        clipboardScope.copyPlainText(clipboard, copyText)
                    }
                    selectedMessageKeys = emptySet()
                },
                onForward = { bulkForwardDialogOpen = true },
                onDelete = { bulkDeleteDialogOpen = true },
                onClear = { selectedMessageKeys = emptySet() }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = messageListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AiThemeTokens.ScreenPadding),
                contentPadding = PaddingValues(top = AiThemeTokens.ScreenTopPadding),
                verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
            ) {
                if (visibleMessages.isNotEmpty()) {
                    item(key = "messages-top-sentinel") {
                        Spacer(Modifier.height(AiThemeTokens.HairlineSpacing))
                    }
                    items(visibleMessages, key = { "${it.chatId}:${it.id}" }) { message ->
                        val messageKey = message.selectionKey()
                        MessageCard(
                            message = message,
                            mediaMessages = visibleMediaMessages,
                            hidden = ChatMessageVisibilityPolicy.isHiddenByLocalPolicy(message, hiddenHashes),
                            allowAdultContent = allowAdultContent,
                            autoPlayVideo = autoPlayVideo,
                            videoSubtitlesEnabled = videoSubtitlesEnabled,
                            videoSourceLanguage = videoSourceLanguage,
                            videoSubtitleColor = videoSubtitleColor,
                            contentTranslationTargetLanguage = contentTranslationTargetLanguage,
                            activeVideoKey = activeVideoKey,
                            onActiveVideoChange = { activeVideoKey = it },
                            onOpenCache = onOpenCache,
                            onDownloadMedia = onDownloadMedia,
                            onRetryTranslation = onRetryTranslation,
                            onRestartMediaDownload = onRestartMediaDownload,
                            onOpenTelegramLink = onOpenTelegramLink,
                            onReply = { replyToMessage = message },
                            onEdit = { replacementText -> onEditMessage(message, replacementText) },
                            onDelete = { onDeleteMessage(message) },
                            onForward = { targetChatId -> onForwardMessage(message, targetChatId) },
                            onResend = { onResendMessage(message) },
                            onPin = { onPinMessage(message) },
                            onUnpin = { onUnpinMessage(message) },
                            onReact = { emoji -> onReactToMessage(message, emoji) },
                            onHide = { onHide(message) },
                            onUnhide = { onUnhide(message) },
                            highlighted = "${message.chatId}:${message.id}" == activeSearchMessageKey,
                            selectionMode = selectionMode,
                            selected = messageKey in selectedMessageKeys,
                            onToggleSelected = {
                                selectedMessageKeys = if (messageKey in selectedMessageKeys) {
                                    selectedMessageKeys - messageKey
                                } else {
                                    selectedMessageKeys + messageKey
                                }
                            }
                        )
                    }
                }
                item { Spacer(Modifier.height(AiThemeTokens.SectionSpacing)) }
            }
            Crossfade(
                targetState = messageListOverlayState,
                modifier = Modifier.matchParentSize(),
                animationSpec = tween(durationMillis = MESSAGE_CONTENT_CROSSFADE_MS),
                label = "message-list-overlay"
            ) { overlayState ->
                when (overlayState) {
                    MessageListOverlayState.None -> Box(Modifier.fillMaxSize())
                    MessageListOverlayState.Loading -> MessageLoadingSkeletonList()
                    MessageListOverlayState.Empty -> MessageEmptyOverlay(
                        text = if (displayedMessages.isEmpty()) {
                            stringResource(R.string.no_messages_in_chat)
                        } else {
                            stringResource(R.string.no_messages_match_filter)
                        }
                    )
                }
            }
        }

            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    MessageComposer(
                        value = draftMessage,
                        onValueChange = { draftMessage = it },
                        replyToMessage = replyToMessage,
                        onCancelReply = { replyToMessage = null },
                        silentSend = silentSend,
                        onSilentSendChange = { silentSend = it },
                        scheduledSend = scheduledSend,
                        onScheduledSendChange = { scheduledSend = it },
                        scheduleDelayMinutes = scheduleDelayMinutes,
                        onScheduleDelayMinutesChange = { value ->
                            scheduleDelayMinutes = value.filter { it.isDigit() }.take(6)
                        },
                        highQualityPhotos = highQualityPhotos,
                        onHighQualityPhotosChange = { highQualityPhotos = it },
                        pendingMedia = pendingMedia,
                        onRemovePendingMedia = { mediaId ->
                            pendingMedia = pendingMedia.filterNot { it.id == mediaId }
                        },
                        onClearPendingMedia = { pendingMedia = emptyList() },
                        onAttachPhoto = { photoPickerLauncher.launch("image/*") },
                        onAttachVideo = { videoPickerLauncher.launch("video/*") },
                        onAttachVoice = { voicePickerLauncher.launch("audio/*") },
                        onAttachVideoMessage = { videoMessagePickerLauncher.launch("video/*") },
                        onAttachFile = { filePickerLauncher.launch("*/*") },
                        onAttachAlbum = { albumPickerLauncher.launch("*/*") },
                        onCapturePhoto = {
                            if (pendingMedia.size < MAX_COMPOSER_MEDIA) {
                                runCatching { context.createComposerCameraUri() }
                                    .onSuccess { uri ->
                                        pendingCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    }
                            }
                        },
                        onCreatePoll = { pollDialogOpen = true },
                        onSendContact = { contactDialogOpen = true },
                        onSend = {
                            val caption = draftMessage
                            if (pendingMedia.isNotEmpty()) {
                                val mediaAlbum = pendingMedia.filter { it.kind == MessageKind.Image || it.kind == MessageKind.Video }
                                if (mediaAlbum.size == pendingMedia.size && pendingMedia.size > 1) {
                                    onSendMediaAlbum(selectedChat.id, pendingMedia, caption, currentSendOptions)
                                } else {
                                    pendingMedia.forEachIndexed { index, media ->
                                        onSendMedia(
                                            selectedChat.id,
                                            media,
                                            if (index == 0) caption else "",
                                            currentSendOptions
                                        )
                                    }
                                }
                            } else {
                                val replyTarget = replyToMessage
                                if (replyTarget != null) {
                                    onSendReplyMessage(selectedChat.id, replyTarget.id, draftMessage, currentSendOptions)
                                } else {
                                    onSendMessage(selectedChat.id, draftMessage, currentSendOptions)
                                }
                            }
                            draftMessage = ""
                            replyToMessage = null
                            pendingMedia = emptyList()
                        },
                        modifier = Modifier.padding(horizontal = AiThemeTokens.ScreenPadding, vertical = AiThemeTokens.ListSpacing)
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = !showControls,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .size(AiThemeTokens.HeroIconTileSize)
                    .clip(CircleShape)
                    .clickable { controlsHidden = false },
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_edit),
                        contentDescription = stringResource(R.string.message_composer_hint),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

private const val ChatTapLogTag = "AiTelegramChatTap"

@Composable
private fun ChatSearchAndFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    chats: List<TelegramChat>,
    selectedFilter: ChatFolderFilter,
    onFilterChange: (ChatFolderFilter) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.SmallSpacing)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatSearchField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f)
            )
            IconActionButton(
                iconRes = R.drawable.ic_ai_search,
                contentDescription = stringResource(R.string.internet_search_open),
                onClick = {
                    uriHandler.openUri(PublicWebSearch.buildTelegramDiscoveryUrl(query, PublicSearchTarget.All))
                },
                enabled = query.isNotBlank(),
                primary = query.isNotBlank()
            )
        }
        ChatFolderFilterRow(
            chats = chats,
            selectedFilter = selectedFilter,
            onFilterChange = onFilterChange
        )
    }
}

@Composable
private fun ChatStatsHero(
    totalChats: Int,
    unreadChats: Int,
    filteredChats: Int,
    onRefresh: () -> Unit
) {
    StatsSummaryCard(
        title = stringResource(R.string.nav_chats),
        iconRes = R.drawable.ic_ai_chat,
        onRefresh = onRefresh
    ) {
        MetaChip(text = "$filteredChats/$totalChats")
        MetaChip(text = stringResource(R.string.chat_filter_unread) + " $unreadChats")
    }
}

@Composable
private fun ChatSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    AppSearchField(
        value = value,
        onValueChange = onValueChange,
        placeholder = stringResource(R.string.search_hint),
        modifier = modifier
    )
}

@Composable
private fun BulkMessageSelectionBar(
    selectedCount: Int,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AiThemeTokens.ScreenPadding, vertical = AiThemeTokens.CompactSpacing),
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pluralStringResource(R.plurals.selected_messages_count, selectedCount, selectedCount),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconActionButton(
                iconRes = android.R.drawable.ic_menu_save,
                contentDescription = stringResource(R.string.copy_selected_messages),
                onClick = onCopy,
                enabled = selectedCount > 0
            )
            IconActionButton(
                iconRes = android.R.drawable.ic_menu_share,
                contentDescription = stringResource(R.string.forward_selected_messages),
                onClick = onForward,
                enabled = selectedCount > 0
            )
            IconActionButton(
                iconRes = android.R.drawable.ic_menu_delete,
                contentDescription = stringResource(R.string.delete_selected_messages),
                onClick = onDelete,
                enabled = selectedCount > 0
            )
            IconActionButton(
                iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                contentDescription = stringResource(R.string.clear_selection),
                onClick = onClear
            )
        }
    }
}

@Composable
private fun ChatSearchNavigationRow(
    current: Int,
    total: Int,
    onSearchPublicPosts: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.chat_search_result_count, current, total),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onSearchPublicPosts) {
            Icon(
                painter = painterResource(R.drawable.ic_ai_search),
                contentDescription = stringResource(R.string.chat_search_public_posts)
            )
        }
        IconButton(
            onClick = onPrevious,
            enabled = total > 0
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_media_previous),
                contentDescription = stringResource(R.string.chat_search_previous)
            )
        }
        IconButton(
            onClick = onNext,
            enabled = total > 0
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_media_next),
                contentDescription = stringResource(R.string.chat_search_next)
            )
        }
    }
}

@Composable
private fun MessageJumpNavigationRow(
    total: Int,
    onFirst: () -> Unit,
    onLast: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.chat_message_count, total),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = onFirst,
            enabled = total > 0
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_media_rew),
                contentDescription = stringResource(R.string.jump_to_first_message)
            )
        }
        IconButton(
            onClick = onLast,
            enabled = total > 0
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_media_ff),
                contentDescription = stringResource(R.string.jump_to_last_message)
            )
        }
    }
}

@Composable
private fun MessageLoadingSkeletonList() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AiThemeTokens.ScreenPadding),
        contentPadding = PaddingValues(top = AiThemeTokens.ScreenTopPadding),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
    ) {
        items(4) { index ->
            MessageSkeletonCard(index = index)
        }
        item { Spacer(Modifier.height(AiThemeTokens.SectionSpacing)) }
    }
}

@Composable
private fun MessageEmptyOverlay(text: String) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AiThemeTokens.ScreenPadding),
        contentPadding = PaddingValues(top = AiThemeTokens.ScreenTopPadding),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
    ) {
        item {
            EmptyState(text)
        }
    }
}

@Composable
private fun MessageSkeletonCard(index: Int) {
    val lineColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f)
    val widths = listOf(0.62f, 0.84f, 0.48f, 0.72f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = AiThemeTokens.OutlineAlpha))
    ) {
        Row(
            modifier = Modifier.padding(AiThemeTokens.CardPadding),
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(AiThemeTokens.AvatarSize)
                    .clip(CircleShape)
                    .background(lineColor)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(widths[index % widths.size])
                        .height(14.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(lineColor)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(lineColor.copy(alpha = 0.7f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(12.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(lineColor.copy(alpha = 0.58f))
                )
            }
        }
    }
}

@Composable
private fun ChatFolderFilterRow(
    chats: List<TelegramChat>,
    selectedFilter: ChatFolderFilter,
    onFilterChange: (ChatFolderFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
        contentPadding = PaddingValues(end = 16.dp)
    ) {
        items(ChatFolderFilter.entries, key = { it.name }) { filter ->
            val count = remember(chats, filter) {
                chats.count { chat -> chat.matchesFolderFilter(filter) }
            }
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        text = "${stringResource(filter.labelRes)} $count",
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
private fun ChatContentFilterRow(
    messages: List<TelegramMessage>,
    selectedFilter: ChatContentFilter,
    onFilterChange: (ChatContentFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
        contentPadding = PaddingValues(end = 16.dp)
    ) {
        items(ChatContentFilter.entries, key = { it.name }) { filter ->
            val count = remember(messages, filter) {
                messages.count { message -> message.matchesContentFilter(filter) }
            }
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        text = "${stringResource(filter.labelRes)} $count",
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
private fun ChatDateFilterRow(
    messages: List<TelegramMessage>,
    selectedFilter: ChatDateFilter,
    onFilterChange: (ChatDateFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
        contentPadding = PaddingValues(end = 16.dp)
    ) {
        items(ChatDateFilter.entries, key = { it.name }) { filter ->
            val count = remember(messages, filter) {
                messages.count { message -> message.matchesDateFilter(filter) }
            }
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        text = "${stringResource(filter.labelRes)} $count",
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
private fun SharedMediaOpenRow(
    messages: List<TelegramMessage>,
    onOpen: () -> Unit
) {
    val mediaCount = remember(messages) { messages.count { it.isSharedMediaKind() } }
    val fileCount = remember(messages) { messages.count { it.kind == MessageKind.File } }
    val linkCount = remember(messages) { messages.count { it.containsVisibleLink() } }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onOpen,
            modifier = Modifier.weight(1f),
            enabled = mediaCount + fileCount + linkCount > 0
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_gallery),
                contentDescription = null,
                modifier = Modifier.size(AiThemeTokens.ControlIconSize)
            )
            Spacer(Modifier.width(AiThemeTokens.CompactSpacing))
            Text(
                text = stringResource(R.string.shared_media_open, mediaCount, fileCount, linkCount),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatSenderFilterRow(
    options: List<ChatSenderFilterOption>,
    selectedSenderId: String?,
    onSenderChange: (String?) -> Unit
) {
    if (options.size <= 1) return

    val allCount = remember(options) { options.sumOf { it.count } }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
        contentPadding = PaddingValues(end = 16.dp)
    ) {
        item {
            FilterChip(
                selected = selectedSenderId == null,
                onClick = { onSenderChange(null) },
                label = {
                    Text(
                        text = "${stringResource(R.string.sender_filter_all)} $allCount",
                        maxLines = 1
                    )
                }
            )
        }
        items(options, key = { it.senderId }) { option ->
            FilterChip(
                selected = selectedSenderId == option.senderId,
                onClick = { onSenderChange(option.senderId) },
                label = {
                    Text(
                        text = "${option.label} ${option.count}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun SharedMediaGalleryDialog(
    chat: TelegramChat,
    messages: List<TelegramMessage>,
    mediaMessages: List<TelegramMessage>,
    onClose: () -> Unit,
    onOpenMedia: (TelegramMessage) -> Unit,
    onDownloadMedia: (Int, MessageKind) -> Unit,
    onRestartMediaDownload: (TelegramMessage) -> Unit,
    onOpenTelegramLink: (String) -> Unit
) {
    var selectedFilter by remember(chat.id) { mutableStateOf(SharedGalleryFilter.Media) }
    val filteredMessages = remember(messages, selectedFilter) {
        messages.filter { message -> message.matchesSharedGalleryFilter(selectedFilter) }
            .sortedByDescending { message -> message.receivedAtMillis.takeIf { it > 0L } ?: message.id }
    }
    val groupedMessages = remember(filteredMessages) {
        filteredMessages.groupBySharedMediaPeriod()
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(560.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
        ) {
            Column(
                modifier = Modifier.padding(AiThemeTokens.CardPadding),
                verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.shared_media_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = chat.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = stringResource(R.string.close_media_viewer)
                        )
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
                    contentPadding = PaddingValues(end = 12.dp)
                ) {
                    items(SharedGalleryFilter.entries, key = { it.name }) { filter ->
                        val count = remember(messages, filter) {
                            messages.count { message -> message.matchesSharedGalleryFilter(filter) }
                        }
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text("${stringResource(filter.labelRes)} $count") }
                        )
                    }
                }
                if (filteredMessages.isEmpty()) {
                    EmptyState(stringResource(R.string.shared_media_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
                    ) {
                        groupedMessages.forEach { group ->
                            item(key = "section:${group.label}") {
                                Text(
                                    text = group.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (selectedFilter == SharedGalleryFilter.Media ||
                                selectedFilter == SharedGalleryFilter.PhotosVideos ||
                                selectedFilter == SharedGalleryFilter.Stickers
                            ) {
                                item(key = "grid:${group.label}") {
                                    androidx.compose.foundation.layout.FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
                                        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
                                    ) {
                                        group.messages.forEach { message ->
                                            SharedMediaGridItem(
                                                message = message,
                                                onOpen = { onOpenMedia(message) },
                                                onDownload = {
                                                    if (message.mediaFileId > 0) {
                                                        onDownloadMedia(message.mediaFileId, message.kind)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            } else if (selectedFilter == SharedGalleryFilter.Files ||
                                selectedFilter == SharedGalleryFilter.Audio ||
                                selectedFilter == SharedGalleryFilter.Voice
                            ) {
                                items(group.messages, key = { it.selectionKey() }) { message ->
                                    SharedFileRow(
                                        message = message,
                                        onDownload = {
                                            if (message.mediaFileId > 0) {
                                                onDownloadMedia(message.mediaFileId, message.kind)
                                            }
                                        }
                                    )
                                }
                            } else {
                                items(group.messages, key = { it.selectionKey() }) { message ->
                                    SharedLinkRow(
                                        message = message,
                                        onOpen = {
                                            message.firstVisibleLink()?.let(onOpenTelegramLink)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedMediaGridItem(
    message: TelegramMessage,
    onOpen: () -> Unit,
    onDownload: () -> Unit
) {
    val renderState = rememberMediaRenderState(message)
    Surface(
        modifier = Modifier
            .width(112.dp)
            .height(126.dp)
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.58f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                renderState.bitmap != null -> Image(
                    bitmap = renderState.bitmap,
                    contentDescription = stringResource(R.string.media_image_content_description),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                renderState.thumbnailBitmap != null -> Image(
                    bitmap = renderState.thumbnailBitmap,
                    contentDescription = stringResource(R.string.media_image_content_description),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (message.kind == MessageKind.Video) android.R.drawable.ic_media_play else android.R.drawable.ic_menu_gallery
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(AiThemeTokens.LargeIconSize),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(AiThemeTokens.DenseSpacing))
                    Text(
                        text = mediaMetaText(message).ifBlank { mediaLabel(message) },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = message.timestamp,
                    modifier = Modifier.padding(horizontal = AiThemeTokens.DenseSpacing, vertical = AiThemeTokens.MicroSpacing),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (message.mediaFileId > 0 && !message.hasReadableChatMediaLocalFile()) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_download),
                        contentDescription = stringResource(R.string.download_media)
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedFileRow(
    message: TelegramMessage,
    onDownload: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.58f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
    ) {
        Row(
            modifier = Modifier.padding(AiThemeTokens.ListSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
        ) {
            Avatar(message.mediaFileName.ifBlank { "F" })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.FineSpacing)) {
                Text(
                    text = message.mediaFileName.ifBlank { stringResource(R.string.media_file) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(message.mediaMimeType, message.timestamp)
                        .filter { it.isNotBlank() }
                        .joinToString(stringResource(R.string.inline_separator)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconActionButton(
                iconRes = R.drawable.ic_action_download,
                contentDescription = stringResource(R.string.download_media),
                onClick = onDownload,
                enabled = message.mediaFileId > 0
            )
        }
    }
}

@Composable
private fun SharedLinkRow(
    message: TelegramMessage,
    onOpen: () -> Unit
) {
    val link = message.firstVisibleLink().orEmpty()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = link.isNotBlank(), onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.58f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
    ) {
        Row(
            modifier = Modifier.padding(AiThemeTokens.ListSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ai_search),
                contentDescription = null,
                modifier = Modifier.size(AiThemeTokens.IconSize),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.FineSpacing)) {
                Text(
                    text = link.ifBlank { stringResource(R.string.content_filter_links) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = message.replyPreview(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    replyToMessage: TelegramMessage?,
    onCancelReply: () -> Unit,
    silentSend: Boolean,
    onSilentSendChange: (Boolean) -> Unit,
    scheduledSend: Boolean,
    onScheduledSendChange: (Boolean) -> Unit,
    scheduleDelayMinutes: String,
    onScheduleDelayMinutesChange: (String) -> Unit,
    highQualityPhotos: Boolean,
    onHighQualityPhotosChange: (Boolean) -> Unit,
    pendingMedia: List<PendingComposerMedia>,
    onRemovePendingMedia: (String) -> Unit,
    onClearPendingMedia: () -> Unit,
    onAttachPhoto: () -> Unit,
    onAttachVideo: () -> Unit,
    onAttachVoice: () -> Unit,
    onAttachVideoMessage: () -> Unit,
    onAttachFile: () -> Unit,
    onAttachAlbum: () -> Unit,
    onCapturePhoto: () -> Unit,
    onCreatePoll: () -> Unit,
    onSendContact: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheduleIsValid = !scheduledSend || (scheduleDelayMinutes.toIntOrNull()?.let { it > 0 } == true)
    val canSend = (value.isNotBlank() || pendingMedia.isNotEmpty()) && scheduleIsValid
    var moreMenuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing)
    ) {
        replyToMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.CompactSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.FineSpacing)) {
                        Text(
                            text = stringResource(R.string.replying_to),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = message.replyPreview(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onCancelReply) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = stringResource(R.string.cancel_reply)
                        )
                    }
                }
            }
        }
        if (pendingMedia.isNotEmpty()) {
            ComposerMediaTray(
                media = pendingMedia,
                onRemove = onRemovePendingMedia,
                onClear = onClearPendingMedia
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing)
        ) {
            ComposerToggleChip(
                selected = silentSend,
                label = stringResource(R.string.silent_send),
                iconRes = R.drawable.ic_ai_silent,
                onClick = { onSilentSendChange(!silentSend) }
            )
            ComposerToggleChip(
                selected = scheduledSend,
                label = stringResource(R.string.scheduled_send),
                iconRes = R.drawable.ic_ai_schedule,
                onClick = { onScheduledSendChange(!scheduledSend) }
            )
            ComposerToggleChip(
                selected = highQualityPhotos,
                label = stringResource(R.string.composer_hd_photo),
                iconRes = android.R.drawable.ic_menu_upload,
                onClick = { onHighQualityPhotosChange(!highQualityPhotos) }
            )
            if (scheduledSend) {
                OutlinedTextField(
                    value = scheduleDelayMinutes,
                    onValueChange = onScheduleDelayMinutesChange,
                    modifier = Modifier
                        .width(104.dp)
                        .height(AiThemeTokens.ControlHeight),
                    singleLine = true,
                    isError = !scheduleIsValid,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = MaterialTheme.shapes.medium,
                    placeholder = {
                        Text(
                            stringResource(R.string.schedule_delay_minutes_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .height(AiThemeTokens.SearchHeight),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = {
                    Text(
                        stringResource(R.string.message_composer_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            )
            Box {
                ComposerIconButton(
                    iconRes = R.drawable.ic_ai_more_horiz,
                    contentDescription = stringResource(R.string.composer_more_actions),
                    onClick = { moreMenuOpen = true }
                )
                DropdownMenu(
                    expanded = moreMenuOpen,
                    onDismissRequest = { moreMenuOpen = false }
                ) {
                    ComposerAttachmentType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(stringResource(type.labelRes)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(type.iconRes),
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                moreMenuOpen = false
                                when (type) {
                                    ComposerAttachmentType.Photo -> onAttachPhoto()
                                    ComposerAttachmentType.Video -> onAttachVideo()
                                    ComposerAttachmentType.Voice -> onAttachVoice()
                                    ComposerAttachmentType.VideoMessage -> onAttachVideoMessage()
                                    ComposerAttachmentType.File -> onAttachFile()
                                    ComposerAttachmentType.Album -> onAttachAlbum()
                                    ComposerAttachmentType.Camera -> onCapturePhoto()
                                }
                            },
                            enabled = pendingMedia.size < MAX_COMPOSER_MEDIA
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.create_poll)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_ai_poll),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            moreMenuOpen = false
                            onCreatePoll()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.send_contact)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_ai_contacts),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            moreMenuOpen = false
                            onSendContact()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.composer_clear_media)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            moreMenuOpen = false
                            onClearPendingMedia()
                        },
                        enabled = pendingMedia.isNotEmpty()
                    )
                }
            }
            ComposerIconButton(
                iconRes = R.drawable.ic_ai_channel,
                contentDescription = stringResource(R.string.send_message),
                onClick = onSend,
                enabled = canSend,
                primary = true
            )
        }
    }
}

@Composable
private fun ComposerMediaTray(
    media: List<PendingComposerMedia>,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
    ) {
        Column(
            modifier = Modifier.padding(AiThemeTokens.CompactSpacing),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.composer_media_ready, media.size, MAX_COMPOSER_MEDIA),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.composer_clear_media))
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                items(media, key = { it.id }) { item ->
                    ComposerMediaPreview(
                        media = item,
                        onRemove = { onRemove(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerMediaPreview(
    media: PendingComposerMedia,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(122.dp)
            .height(100.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    ) {
        Box(Modifier.fillMaxSize()) {
            val bitmap = rememberComposerPreviewBitmap(media)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.composer_media_preview),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(media.kind.composerIconRes()),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(AiThemeTokens.DenseSpacing))
                    Text(
                        text = media.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shape = CircleShape
            ) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(AiThemeTokens.CompactIconButtonSize)
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = stringResource(R.string.remove),
                        modifier = Modifier.size(AiThemeTokens.SmallIconSize)
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = stringResource(media.kind.composerLabelRes()),
                    modifier = Modifier.padding(horizontal = AiThemeTokens.DenseSpacing, vertical = AiThemeTokens.MicroSpacing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun rememberComposerPreviewBitmap(media: PendingComposerMedia): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(media.id, media.uri) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(media.id, media.uri, media.kind) {
        bitmap = null
        if (media.kind != MessageKind.Image) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            context.contentResolver.decodeComposerPreview(media.uri)
        }
    }
    return bitmap
}

@Composable
private fun ComposerToggleChip(
    selected: Boolean,
    label: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AiThemeTokens.RowPaddingVertical, vertical = AiThemeTokens.DenseSpacing),
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.SmallSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ComposerIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false
) {
    val backgroundColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(AiThemeTokens.IconButtonSize)
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(AiThemeTokens.IconSize),
            tint = contentColor
        )
    }
}

@Composable
private fun ConversationHeader(chat: TelegramChat, onBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.CompactSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ComposerIconButton(
                iconRes = R.drawable.ic_ai_chevron_left,
                contentDescription = stringResource(R.string.back_to_chats),
                onClick = onBack
            )
            Spacer(Modifier.width(AiThemeTokens.CompactSpacing))
            Avatar(chat.title)
            Spacer(Modifier.width(AiThemeTokens.ListSpacing))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing),
                    verticalArrangement = Arrangement.spacedBy(AiThemeTokens.TinySpacing)
                ) {
                    ConversationMetaChip(
                        text = if (chat.isSecretChat()) {
                            stringResource(R.string.secret_chat_label)
                        } else {
                            chat.type.ifBlank { stringResource(R.string.nav_chats) }
                        }
                    )
                    if (chat.unreadCount > 0) {
                        ConversationMetaChip(
                            text = pluralStringResource(R.plurals.unread_count, chat.unreadCount, chat.unreadCount)
                        )
                    }
                    if (chat.activeAction.isNotBlank()) {
                        ConversationMetaChip(text = chat.activeAction)
                    }
                    if (chat.pinnedMessageId > 0L) {
                        ConversationMetaChip(text = stringResource(R.string.message_pinned_short))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationMetaChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = AiThemeTokens.DenseSpacing, vertical = AiThemeTokens.MicroSpacing),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChatSelector(
    chats: List<TelegramChat>,
    selectedChatId: Long?,
    onSelectChat: (TelegramChat) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
        items(chats, key = { it.id }) { chat ->
            FilterChip(
                selected = selectedChatId == chat.id,
                onClick = { onSelectChat(chat) },
                label = {
                    Column {
                        Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (chat.unreadCount > 0) {
                            Text(
                                pluralStringResource(R.plurals.unread_count, chat.unreadCount, chat.unreadCount),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SearchPill() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    ) {
        Text(
            text = stringResource(R.string.search_hint),
            modifier = Modifier.padding(horizontal = AiThemeTokens.ScreenPadding, vertical = AiThemeTokens.SectionSpacing),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LinkifiedMessageText(
    text: String,
    modifier: Modifier = Modifier,
    onOpenTelegramLink: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val normalColor = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedText = remember(text, linkColor, uriHandler, onOpenTelegramLink) {
        val linkStyles = TextLinkStyles(
            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
        )
        buildAnnotatedString {
            var cursor = 0
            UrlPattern.findAll(text).forEach { match ->
                val rawMatch = match.value
                val trimmed = rawMatch.trimEnd('.', ',', ';', ':', ')', ']', '}')
                val start = match.range.first
                val end = start + trimmed.length
                if (start > cursor) {
                    append(text.substring(cursor, start))
                }
                val url = normalizeUrl(trimmed)
                pushLink(
                    LinkAnnotation.Clickable(
                        tag = url,
                        styles = linkStyles
                    ) { annotation ->
                        val clickedUrl = (annotation as? LinkAnnotation.Clickable)?.tag ?: return@Clickable
                        if (isTelegramUrl(clickedUrl)) {
                            onOpenTelegramLink(clickedUrl)
                        } else {
                            runCatching { uriHandler.openUri(clickedUrl) }
                        }
                    }
                )
                append(text.substring(start, end))
                pop()
                cursor = end
            }
            if (cursor < text.length) {
                append(text.substring(cursor))
            }
        }
    }

    Text(
        text = annotatedText,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(color = normalColor)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageCard(
    message: TelegramMessage,
    mediaMessages: List<TelegramMessage>,
    hidden: Boolean,
    allowAdultContent: Boolean,
    autoPlayVideo: Boolean,
    videoSubtitlesEnabled: Boolean,
    videoSourceLanguage: VideoSourceLanguage,
    videoSubtitleColor: VideoSubtitleColor,
    contentTranslationTargetLanguage: String,
    activeVideoKey: String?,
    onActiveVideoChange: (String?) -> Unit,
    onOpenCache: () -> Unit,
    onDownloadMedia: (Int, MessageKind) -> Unit,
    onRetryTranslation: (TelegramMessage) -> Unit,
    onRestartMediaDownload: (TelegramMessage) -> Unit,
    onOpenTelegramLink: (String) -> Unit,
    onReply: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
    onForward: (Long) -> Unit,
    onResend: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onReact: (String) -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    highlighted: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {}
) {
    val isTelegramRestrictedNotice = MessagePrivacyPolicy.isTelegramRestrictedNotice(message)
    val readableText = MessagePrivacyPolicy.readableText(message, targetLanguage = contentTranslationTargetLanguage)
    val readableTranslatedText = MessagePrivacyPolicy.readableTranslatedText(
        message,
        targetLanguage = contentTranslationTargetLanguage
    )
    val sourceFallbackText = if (
        MessagePrivacyPolicy.shouldRenderSourceText(message, targetLanguage = contentTranslationTargetLanguage)
    ) {
        message.originalText.trim()
    } else {
        ""
    }
    val hasReadableMessageText = sourceFallbackText.isNotBlank() ||
        readableText.isNotBlank() ||
        readableTranslatedText.isNotBlank() ||
        message.originalText.isNotBlank()
    val isNonTranslatableMessage = message.translationFailureReason == TranslationFailureReason.NonTranslatable
    val effectiveTranslationStatus = if (
        message.translationStatus == TranslationStatus.Ready &&
        message.originalText.isNotBlank() &&
        readableTranslatedText.isBlank()
    ) {
        TranslationStatus.Failed
    } else {
        message.translationStatus
    }
    val isHiddenMessage = !isTelegramRestrictedNotice &&
        !allowAdultContent &&
        (hidden || message.translationStatus == TranslationStatus.Hidden)
    val canHideByContent = ChatMessageVisibilityPolicy.canHideByContent(message)
    val canResend = message.isOutgoing && message.syncState == MessageSyncState.Failed
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    var editDialogOpen by remember(message.chatId, message.id) { mutableStateOf(false) }
    var replacementText by remember(message.chatId, message.id) { mutableStateOf("") }
    var deleteDialogOpen by remember(message.chatId, message.id) { mutableStateOf(false) }
    var forwardDialogOpen by remember(message.chatId, message.id) { mutableStateOf(false) }
    var forwardChatId by remember(message.chatId, message.id) { mutableStateOf("") }
    var actionMenuOpen by remember(message.chatId, message.id) { mutableStateOf(false) }
    var actionFeedback by remember(message.chatId, message.id) { mutableStateOf<String?>(null) }
    val copiedFeedback = stringResource(R.string.message_action_copied)
    val replySelectedFeedback = stringResource(R.string.message_action_reply_selected)
    val forwardRequestedFeedback = stringResource(R.string.message_action_forward_requested)
    val pinRequestedFeedback = stringResource(R.string.message_action_pin_requested)
    val unpinRequestedFeedback = stringResource(R.string.message_action_unpin_requested)
    val editRequestedFeedback = stringResource(R.string.message_action_edit_requested)
    val deleteRequestedFeedback = stringResource(R.string.message_action_delete_requested)
    val reactionRequestedFeedback = stringResource(R.string.message_action_reaction_requested)
    val resendRequestedFeedback = stringResource(R.string.message_action_resend_requested)

    if (editDialogOpen) {
        AlertDialog(
            onDismissRequest = { editDialogOpen = false },
            title = { Text(stringResource(R.string.edit_message)) },
            text = {
                OutlinedTextField(
                    value = replacementText,
                    onValueChange = { replacementText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text(stringResource(R.string.edit_message_hint)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEdit(replacementText)
                        actionFeedback = editRequestedFeedback
                        replacementText = ""
                        editDialogOpen = false
                    },
                    enabled = replacementText.isNotBlank()
                ) {
                    Text(stringResource(R.string.edit_message))
                }
            },
            dismissButton = {
                TextButton(onClick = { editDialogOpen = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text(stringResource(R.string.delete_message)) },
            text = { Text(stringResource(R.string.delete_message_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        actionFeedback = deleteRequestedFeedback
                        deleteDialogOpen = false
                    }
                ) {
                    Text(stringResource(R.string.delete_message))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogOpen = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (forwardDialogOpen) {
        AlertDialog(
            onDismissRequest = { forwardDialogOpen = false },
            title = { Text(stringResource(R.string.forward_message)) },
            text = {
                OutlinedTextField(
                    value = forwardChatId,
                    onValueChange = { forwardChatId = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.forward_chat_id_hint)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        forwardChatId.toLongOrNull()?.let {
                            onForward(it)
                            actionFeedback = forwardRequestedFeedback
                        }
                        forwardChatId = ""
                        forwardDialogOpen = false
                    },
                    enabled = forwardChatId.toLongOrNull() != null
                ) {
                    Text(stringResource(R.string.forward_message))
                }
            },
            dismissButton = {
                TextButton(onClick = { forwardDialogOpen = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                isHiddenMessage -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
                highlighted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selectionMode) {
                    Modifier.clickable { onToggleSelected() }
                } else if (!isHiddenMessage && !isTelegramRestrictedNotice) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { actionMenuOpen = true }
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)) {
            Row(
                modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelected() }
                    )
                } else {
                    Avatar(message.chatTitle)
                }
                Spacer(Modifier.width(AiThemeTokens.ListSpacing))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = message.chatTitle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    MessageCompactMetaLine(message)
                }
                if (!isHiddenMessage) {
                    if (canHideByContent) {
                        Spacer(Modifier.width(AiThemeTokens.FineSpacing))
                        IconButton(onClick = onHide) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = stringResource(R.string.hide_same_content)
                            )
                        }
                    }
                }
            }

            if (isHiddenMessage) {
                Box(Modifier.padding(horizontal = AiThemeTokens.InsetPadding)) {
                    HiddenBlock(onUndo = onUnhide)
                }
            } else if (isTelegramRestrictedNotice) {
                Box(Modifier.padding(horizontal = AiThemeTokens.InsetPadding)) {
                    TelegramRestrictedBlock()
                }
            } else if (allowAdultContent && message.translationStatus == TranslationStatus.Hidden) {
                MediaBlock(
                    message = message,
                    mediaMessages = mediaMessages,
                    autoPlayVideo = autoPlayVideo,
                    videoSubtitlesEnabled = videoSubtitlesEnabled,
                    videoSourceLanguage = videoSourceLanguage,
                    videoSubtitleColor = videoSubtitleColor,
                    contentTranslationTargetLanguage = contentTranslationTargetLanguage,
                    activeVideoKey = activeVideoKey,
                    onActiveVideoChange = onActiveVideoChange,
                    onDownloadMedia = onDownloadMedia,
                    onRestartMediaDownload = onRestartMediaDownload
                )
                Column(
                    Modifier.padding(horizontal = AiThemeTokens.InsetPadding),
                    verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
                ) {
                    TranslationStateBlock(stringResource(R.string.adult_content_allowed_notice))
                    if (readableText.isNotBlank()) {
                        LinkifiedMessageText(
                            text = readableText,
                            modifier = Modifier.fillMaxWidth(),
                            onOpenTelegramLink = onOpenTelegramLink
                        )
                    }
                }
            } else if (effectiveTranslationStatus == TranslationStatus.Pending || effectiveTranslationStatus == TranslationStatus.Translating) {
                MediaBlock(
                    message = message,
                    mediaMessages = mediaMessages,
                    autoPlayVideo = autoPlayVideo,
                    videoSubtitlesEnabled = videoSubtitlesEnabled,
                    videoSourceLanguage = videoSourceLanguage,
                    videoSubtitleColor = videoSubtitleColor,
                    contentTranslationTargetLanguage = contentTranslationTargetLanguage,
                    activeVideoKey = activeVideoKey,
                    onActiveVideoChange = onActiveVideoChange,
                    onDownloadMedia = onDownloadMedia,
                    onRestartMediaDownload = onRestartMediaDownload
                )
                Column(
                    Modifier.padding(horizontal = AiThemeTokens.InsetPadding),
                    verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
                ) {
                    if (sourceFallbackText.isNotBlank()) {
                        LinkifiedMessageText(
                            text = sourceFallbackText,
                            modifier = Modifier.fillMaxWidth(),
                            onOpenTelegramLink = onOpenTelegramLink
                        )
                    } else if (hasReadableMessageText) {
                        TranslationStateBlock(stringResource(R.string.translation_pending))
                    }
                }
            } else if (effectiveTranslationStatus == TranslationStatus.Failed) {
                MediaBlock(
                    message = message,
                    mediaMessages = mediaMessages,
                    autoPlayVideo = autoPlayVideo,
                    videoSubtitlesEnabled = videoSubtitlesEnabled,
                    videoSourceLanguage = videoSourceLanguage,
                    videoSubtitleColor = videoSubtitleColor,
                    contentTranslationTargetLanguage = contentTranslationTargetLanguage,
                    activeVideoKey = activeVideoKey,
                    onActiveVideoChange = onActiveVideoChange,
                    onDownloadMedia = onDownloadMedia,
                    onRestartMediaDownload = onRestartMediaDownload
                )
                Column(
                    Modifier.padding(horizontal = AiThemeTokens.InsetPadding),
                    verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
                ) {
                    if (sourceFallbackText.isNotBlank()) {
                        LinkifiedMessageText(
                            text = sourceFallbackText,
                            modifier = Modifier.fillMaxWidth(),
                            onOpenTelegramLink = onOpenTelegramLink
                        )
                    } else if (hasReadableMessageText && readableText.isBlank()) {
                        TranslationStateBlock(stringResource(R.string.translation_failed))
                    } else if (readableText.isNotBlank()) {
                        LinkifiedMessageText(
                            text = readableText,
                            modifier = Modifier.fillMaxWidth(),
                            onOpenTelegramLink = onOpenTelegramLink
                        )
                    }
                    if (message.translationFailureReason == TranslationFailureReason.MissingModel) {
                        MissingTranslationModelBlock(
                            detectedLanguage = message.detectedLanguage,
                            targetLanguage = message.translationTargetLanguage,
                            onOpenCache = onOpenCache
                        )
                    } else if (message.originalText.isNotBlank() && !isNonTranslatableMessage) {
                        TranslationRetryBlock(
                            failureReason = message.translationFailureReason,
                            onRetry = { onRetryTranslation(message) }
                        )
                    }
                }
            } else {
                MediaBlock(
                    message = message,
                    mediaMessages = mediaMessages,
                    autoPlayVideo = autoPlayVideo,
                    videoSubtitlesEnabled = videoSubtitlesEnabled,
                    videoSourceLanguage = videoSourceLanguage,
                    videoSubtitleColor = videoSubtitleColor,
                    contentTranslationTargetLanguage = contentTranslationTargetLanguage,
                    activeVideoKey = activeVideoKey,
                    onActiveVideoChange = onActiveVideoChange,
                    onDownloadMedia = onDownloadMedia,
                    onRestartMediaDownload = onRestartMediaDownload
                )
                if (readableText.isNotBlank()) {
                    LinkifiedMessageText(
                        text = readableText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AiThemeTokens.InsetPadding),
                        onOpenTelegramLink = onOpenTelegramLink
                    )
                }
            }
            if (!selectionMode && !isHiddenMessage && !isTelegramRestrictedNotice) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AiThemeTokens.InsetPadding)
                ) {
                    MessageActionMenu(
                        expanded = actionMenuOpen,
                        canCopy = message.readableTextForCopy(contentTranslationTargetLanguage).isNotBlank(),
                        canResend = canResend,
                        onDismiss = { actionMenuOpen = false },
                        onSelect = {
                            actionMenuOpen = false
                            onToggleSelected()
                        },
                        onCopy = {
                            actionMenuOpen = false
                            val copyText = message.readableTextForCopy(contentTranslationTargetLanguage)
                            if (copyText.isNotBlank()) {
                                clipboardScope.copyPlainText(clipboard, copyText)
                                actionFeedback = copiedFeedback
                            }
                        },
                        onReply = {
                            actionMenuOpen = false
                            onReply()
                            actionFeedback = replySelectedFeedback
                        },
                        onForward = {
                            actionMenuOpen = false
                            forwardDialogOpen = true
                        },
                        onResend = {
                            actionMenuOpen = false
                            onResend()
                            actionFeedback = resendRequestedFeedback
                        },
                        onPin = {
                            actionMenuOpen = false
                            onPin()
                            actionFeedback = pinRequestedFeedback
                        },
                        onUnpin = {
                            actionMenuOpen = false
                            onUnpin()
                            actionFeedback = unpinRequestedFeedback
                        },
                        onEdit = {
                            actionMenuOpen = false
                            editDialogOpen = true
                        },
                        onDelete = {
                            actionMenuOpen = false
                            deleteDialogOpen = true
                        },
                        onReact = { emoji ->
                            actionMenuOpen = false
                            onReact(emoji)
                            actionFeedback = reactionRequestedFeedback
                        }
                    )
                }
            }
            if (!isHiddenMessage && !isTelegramRestrictedNotice && !selectionMode) {
                actionFeedback?.let { feedback ->
                    Text(
                        text = feedback,
                        modifier = Modifier.padding(horizontal = AiThemeTokens.InsetPadding),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(AiThemeTokens.TinySpacing))
        }
    }
}

@Composable
private fun MessageCompactMetaLine(message: TelegramMessage) {
    val uploadProgressPercent = remember(
        message.syncState,
        message.isOutgoing,
        message.kind,
        message.mediaSizeMb,
        message.mediaDownloadedPrefixBytes
    ) {
        val expectedBytes = message.mediaSizeMb.takeIf { it > 0 }?.toLong()?.times(BYTES_PER_MB) ?: 0L
        if (
            message.isOutgoing &&
            message.syncState == MessageSyncState.Sending &&
            message.kind != MessageKind.Text &&
            expectedBytes > 0L &&
            message.mediaDownloadedPrefixBytes > 0L
        ) {
            ((message.mediaDownloadedPrefixBytes * 100L) / expectedBytes).toInt().coerceIn(1, 99)
        } else {
            null
        }
    }
    val metaParts = mutableListOf<String>()
    if (message.author.isNotBlank() && !message.author.equals(message.chatTitle, ignoreCase = true)) {
        metaParts += message.author
    }
    if (message.timestamp.isNotBlank()) {
        metaParts += message.timestamp
    }
    when (message.syncState) {
        MessageSyncState.Sending -> metaParts += stringResource(R.string.message_sync_sending)
        MessageSyncState.Failed -> metaParts += stringResource(R.string.message_sync_failed)
        MessageSyncState.Deleted -> metaParts += stringResource(R.string.message_sync_deleted)
        MessageSyncState.Synced -> if (message.isOutgoing) {
            metaParts += stringResource(if (message.isRead) R.string.message_sync_read else R.string.message_sync_sent)
        }
    }
    if (message.isEdited) metaParts += stringResource(R.string.message_sync_edited)
    if (message.isPinned) metaParts += stringResource(R.string.message_sync_pinned)
    when (message.translationStatus) {
        TranslationStatus.Pending,
        TranslationStatus.Translating -> metaParts += stringResource(R.string.translation_pending)
        TranslationStatus.Failed -> metaParts += stringResource(R.string.translation_failed)
        TranslationStatus.Hidden -> metaParts += stringResource(R.string.translation_hidden)
        TranslationStatus.Ready -> Unit
    }
    uploadProgressPercent?.let { percent ->
        metaParts += stringResource(R.string.message_upload_progress, percent)
    }
    if (metaParts.isEmpty()) return
    Text(
        text = metaParts.joinToString("  •  "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun MessageActionMenu(
    expanded: Boolean,
    canCopy: Boolean,
    canResend: Boolean,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onResend: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.select_message)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(android.R.drawable.checkbox_on_background),
                    contentDescription = null
                )
            },
            onClick = onSelect
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy_message)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_save),
                    contentDescription = null
                )
            },
            enabled = canCopy,
            onClick = onCopy
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.reply_message)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_revert),
                    contentDescription = null
                )
            },
            onClick = onReply
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.forward_message)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_share),
                    contentDescription = null
                )
            },
            onClick = onForward
        )
        if (canResend) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.resend_message)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_revert),
                        contentDescription = null
                    )
                },
                onClick = onResend
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.pin_message)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_upload),
                    contentDescription = null
                )
            },
            onClick = onPin
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.unpin_message)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_revert),
                    contentDescription = null
                )
            },
            onClick = onUnpin
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.edit_message)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_edit),
                    contentDescription = null
                )
            },
            onClick = onEdit
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete_message)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = onDelete
        )
        DropdownMenuItem(
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)) {
                    QuickReactionEmojis.forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable {
                                onReact(emoji)
                            }
                        )
                    }
                }
            },
            onClick = {}
        )
    }
}

private val QuickReactionEmojis = listOf(
    "\uD83D\uDC4D",
    "\u2764\uFE0F",
    "\uD83D\uDE02"
)

private data class SharedMediaPeriodGroup(
    val label: String,
    val messages: List<TelegramMessage>
)

private fun List<TelegramMessage>.groupBySharedMediaPeriod(): List<SharedMediaPeriodGroup> {
    if (isEmpty()) return emptyList()
    val now = System.currentTimeMillis()
    val dayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    return groupBy { message ->
        val timestamp = message.receivedAtMillis.takeIf { it > 0L } ?: 0L
        val ageMillis = now - timestamp
        when {
            timestamp <= 0L -> "Unknown date"
            ageMillis in 0L until SHARED_MEDIA_DAY_MILLIS -> "Today"
            ageMillis in SHARED_MEDIA_DAY_MILLIS until (2L * SHARED_MEDIA_DAY_MILLIS) -> "Yesterday"
            ageMillis in 0L until (31L * SHARED_MEDIA_DAY_MILLIS) -> dayFormat.format(Date(timestamp))
            else -> monthFormat.format(Date(timestamp))
        }
    }.map { (label, groupMessages) ->
        SharedMediaPeriodGroup(label, groupMessages)
    }
}

private fun ChatContentFilter.toTelegramMessageSearchFilter(): TelegramMessageSearchFilter {
    return when (this) {
        ChatContentFilter.All,
        ChatContentFilter.Media -> TelegramMessageSearchFilter.Empty
        ChatContentFilter.PhotosVideos -> TelegramMessageSearchFilter.PhotoVideo
        ChatContentFilter.Audio -> TelegramMessageSearchFilter.Audio
        ChatContentFilter.Voice -> TelegramMessageSearchFilter.Voice
        ChatContentFilter.Stickers -> TelegramMessageSearchFilter.Sticker
        ChatContentFilter.Files -> TelegramMessageSearchFilter.Document
        ChatContentFilter.Links -> TelegramMessageSearchFilter.Url
    }
}

private const val SHARED_MEDIA_DAY_MILLIS = 24L * 60L * 60L * 1000L

private fun TelegramMessage.readableTextForCopy(targetLanguage: String = translationTargetLanguage): String {
    return MessagePrivacyPolicy.readableText(this, targetLanguage = targetLanguage)
}

private fun TelegramMessage.selectionKey(): String = "$chatId:$id"

private fun TelegramMessage.replyPreview(): String {
    return readableTextForCopy()
        .ifBlank {
            when (kind) {
                MessageKind.Image -> "Image"
                MessageKind.Video -> "Video"
                MessageKind.File -> mediaFileName.ifBlank { "File" }
                MessageKind.Voice -> mediaFileName.ifBlank { "Voice" }
                MessageKind.VideoNote -> mediaFileName.ifBlank { "Video message" }
                MessageKind.Audio -> mediaFileName.ifBlank { "Audio" }
                MessageKind.Sticker -> mediaFileName.ifBlank { "Sticker" }
                MessageKind.Text -> ""
            }
        }
        .ifBlank { timestamp }
}

@Composable
fun Avatar(seed: String) {
    val avatarColor = rememberStableAvatarColor(seed)
    Box(
        modifier = Modifier
            .size(AiThemeTokens.AvatarSize)
            .clip(CircleShape)
            .background(avatarColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = seed.take(1).uppercase(),
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TranslationStateBlock(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(AiThemeTokens.CardPadding),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HiddenBlock(onUndo: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(AiThemeTokens.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.hidden_content),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconActionButton(
                iconRes = android.R.drawable.ic_menu_revert,
                contentDescription = stringResource(R.string.undo),
                onClick = onUndo
            )
        }
    }
}

@Composable
private fun TelegramRestrictedBlock() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.34f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(AiThemeTokens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ai_shield),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(AiThemeTokens.IconSize)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)
            ) {
                Text(
                    text = stringResource(R.string.telegram_restricted_content_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.telegram_restricted_content_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Preview(name = "Chats", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun ChatScreenPreview() {
    AiTelegramTheme {
        ChatScreen(
            chats = previewChats,
            selectedChatId = null,
            messages = previewMessages,
            translatedOnly = false,
            allowAdultContent = true,
            autoPlayVideo = false,
            videoSubtitlesEnabled = true,
            videoSourceLanguage = VideoSourceLanguage.Auto,
            videoSubtitleColor = VideoSubtitleColor.Yellow,
            onSelectChat = {},
            onBackToChats = {},
            hiddenHashes = emptySet(),
            onCurrentMessageViewed = {},
            onOpenCache = {},
            onSendMessage = { _, _, _ -> },
            onSendReplyMessage = { _, _, _, _ -> },
            onSendMedia = { _, _, _, _ -> },
            onSendMediaAlbum = { _, _, _, _ -> },
            onSendPoll = { _, _, _, _, _, _ -> },
            onSendContact = { _, _, _, _, _ -> },
            onSetDraft = { _, _ -> },
            onClearDraft = {},
            onEditMessage = { _, _ -> },
            onDeleteMessage = {},
            onForwardMessage = { _, _ -> },
            onDeleteMessages = { _, _ -> },
            onForwardMessages = { _, _, _ -> },
            onResendMessage = {},
            onPinMessage = {},
            onUnpinMessage = {},
            onReactToMessage = { _, _ -> },
            onLoadOlderMessages = { _, _ -> },
            onSearchChatMessages = { _, _, _ -> },
            onSearchPublicPosts = { _, _ -> },
            onRefreshChats = {},
            onOpenTelegramLink = {},
            onDownloadMedia = { _, _ -> },
            onRetryTranslation = {},
            onHide = {},
            onUnhide = {}
        )
    }
}

private val previewChats = listOf(
    TelegramChat(
        id = 1001L,
        title = "AI News Global",
        type = "channel",
        unreadCount = 4,
        lastMessagePreview = "New model rollout notes are ready to review.",
        updatedAtMillis = 1_780_000_000_000L
    ),
    TelegramChat(
        id = 1002L,
        title = "Android Dev",
        type = "group",
        unreadCount = 2,
        lastMessagePreview = "Compose preview fixtures are now in place.",
        updatedAtMillis = 1_780_000_120_000L
    ),
    TelegramChat(
        id = 1003L,
        title = "Minh",
        type = "private",
        unreadCount = 0,
        lastMessagePreview = "Thanks, I will check the preview.",
        updatedAtMillis = 1_780_000_240_000L
    )
)

private val previewMessages = listOf(
    TelegramMessage(
        chatId = 1002L,
        id = 1L,
        senderId = "sample:linh",
        chatTitle = "Android Dev",
        author = "Linh",
        originalText = "Compose previews make screen work much faster.",
        translatedText = "Compose previews make screen work much faster.",
        translationStatus = TranslationStatus.Ready,
        detectedLanguage = "en",
        translationFailureReason = TranslationFailureReason.None,
        kind = MessageKind.Text,
        mediaSizeMb = 0,
        timestamp = "09:41",
        syncState = MessageSyncState.Synced,
        isRead = true
    ),
    TelegramMessage(
        chatId = 1002L,
        id = 2L,
        senderId = "sample:me",
        chatTitle = "Android Dev",
        author = "You",
        originalText = "Great, I added fixtures for every screen.",
        translatedText = "Great, I added fixtures for every screen.",
        translationStatus = TranslationStatus.Ready,
        detectedLanguage = "en",
        kind = MessageKind.Text,
        mediaSizeMb = 0,
        timestamp = "09:43",
        syncState = MessageSyncState.Synced,
        isOutgoing = true,
        isRead = true
    )
)
