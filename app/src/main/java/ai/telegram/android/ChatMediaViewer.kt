package ai.telegram.android

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TranslationFailureReason
import ai.telegram.android.data.translation.VideoSubtitleCue
import ai.telegram.android.data.translation.VideoSubtitleGenerator
import ai.telegram.android.data.translation.VideoSubtitleMode
import ai.telegram.android.data.translation.VideoSubtitleResult
import ai.telegram.android.data.translation.VietnameseTranslationPairs
import ai.telegram.android.ui.AiThemeTokens
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
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
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
@Composable
internal fun MediaBlock(
    message: TelegramMessage,
    mediaMessages: List<TelegramMessage>,
    autoPlayVideo: Boolean,
    videoSubtitlesEnabled: Boolean,
    videoSourceLanguage: VideoSourceLanguage,
    videoSubtitleColor: VideoSubtitleColor,
    activeVideoKey: String?,
    onActiveVideoChange: (String?) -> Unit,
    onDownloadMedia: (Int, MessageKind) -> Unit,
    onRestartMediaDownload: (TelegramMessage) -> Unit
) {
    if (message.kind == MessageKind.Text) return

    val localFile = remember(message.mediaLocalPath) {
        message.mediaLocalPath.takeIf { it.isNotBlank() }?.let(::File)
    }
    val bitmap = remember(message.mediaLocalPath, message.kind) {
        if (message.kind == MessageKind.Image && localFile?.exists() == true) {
            runCatching { BitmapFactory.decodeFile(localFile.absolutePath)?.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }
    val videoFile = rememberPlayableVideoFile(message, localFile)
    val thumbnailFile = remember(message.mediaThumbnailLocalPath) {
        message.mediaThumbnailLocalPath.takeIf { it.isNotBlank() }?.let(::File)
    }
    val thumbnailBitmap = remember(message.mediaThumbnailLocalPath) {
        if (thumbnailFile?.exists() == true) {
            runCatching { BitmapFactory.decodeFile(thumbnailFile.absolutePath)?.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }

    MediaPreviewContent(
        message = message,
        bitmap = bitmap,
        thumbnailBitmap = thumbnailBitmap,
        videoFile = videoFile,
        mediaMessages = mediaMessages,
        autoPlayVideo = autoPlayVideo,
        videoSubtitlesEnabled = videoSubtitlesEnabled,
        videoSourceLanguage = videoSourceLanguage,
        videoSubtitleColor = videoSubtitleColor,
        activeVideoKey = activeVideoKey,
        onActiveVideoChange = onActiveVideoChange,
        onDownloadMedia = onDownloadMedia,
        onRestartMediaDownload = onRestartMediaDownload
    )
}


@Composable
private fun MediaPreviewContent(
    message: TelegramMessage,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    thumbnailBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    videoFile: File?,
    mediaMessages: List<TelegramMessage>,
    autoPlayVideo: Boolean,
    videoSubtitlesEnabled: Boolean,
    videoSourceLanguage: VideoSourceLanguage,
    videoSubtitleColor: VideoSubtitleColor,
    activeVideoKey: String?,
    onActiveVideoChange: (String?) -> Unit,
    onDownloadMedia: (Int, MessageKind) -> Unit,
    onRestartMediaDownload: (TelegramMessage) -> Unit
) {
    var fullScreenOpen by rememberSaveable(message.uidForMediaPreview()) { mutableStateOf(false) }
    var downloadRequested by remember(message.mediaViewerKey(), message.mediaFileId) { mutableStateOf(false) }
    var playWhenReady by rememberSaveable(message.mediaViewerKey(), message.mediaFileId) { mutableStateOf(false) }
    val videoPlaybackKey = message.videoPlaybackStateKey(videoFile)
    val inlineVideoKey = remember(message.mediaViewerKey()) { "inline:${message.mediaViewerKey()}" }
    val currentActiveVideoKey by rememberUpdatedState(activeVideoKey)
    var videoPlaybackFailed by remember(videoPlaybackKey) { mutableStateOf(false) }
    DisposableEffect(inlineVideoKey) {
        onDispose {
            if (currentActiveVideoKey == inlineVideoKey) {
                onActiveVideoChange(null)
            }
        }
    }
    LaunchedEffect(autoPlayVideo, fullScreenOpen, videoFile?.absolutePath, activeVideoKey, inlineVideoKey, message.kind) {
        if (
            message.kind == MessageKind.Video &&
            autoPlayVideo &&
            !fullScreenOpen &&
            videoFile != null &&
            activeVideoKey == null
        ) {
            onActiveVideoChange(inlineVideoKey)
        }
    }
    LaunchedEffect(videoFile?.absolutePath, message.kind) {
        if (message.kind == MessageKind.Video && downloadRequested && videoFile != null) {
            downloadRequested = false
        }
    }
    fun requestMediaDownload() {
        if (message.mediaFileId <= 0) return
        if (message.kind == MessageKind.Video) {
            downloadRequested = true
            playWhenReady = true
        }
        onDownloadMedia(message.mediaFileId, message.kind)
    }

    when {
        bitmap != null -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clickable { fullScreenOpen = true }
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.media_image_content_description),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                MediaPreviewOpenBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                )
            }
        }
        videoFile != null && !videoPlaybackFailed -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clickable {
                        if (message.kind == MessageKind.Video) {
                            playWhenReady = true
                            onActiveVideoChange("viewer:${message.mediaViewerKey()}")
                        }
                        fullScreenOpen = true
                    }
            ) {
                key(videoPlaybackKey) {
                    VideoPlayer(
                        file = videoFile,
                        modifier = Modifier.fillMaxSize(),
                        autoPlay = autoPlayVideo && !fullScreenOpen,
                        isActive = !fullScreenOpen && activeVideoKey == inlineVideoKey,
                        onPlaybackStarted = { onActiveVideoChange(inlineVideoKey) },
                        onPlaybackError = {
                            videoPlaybackFailed = true
                        }
                    )
                }
                MediaPreviewOpenBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                )
            }
        }
        videoPlaybackFailed && message.mediaFileId > 0 -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clickable { requestMediaDownload() },
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                MediaPreviewPlaceholder(
                    message = message,
                    hintRes = R.string.media_playback_failed
                )
            }
        }
        thumbnailBitmap != null -> {
            val shouldDownloadVideo = message.kind == MessageKind.Video && message.mediaFileId > 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clickable {
                        if (shouldDownloadVideo) {
                            requestMediaDownload()
                            fullScreenOpen = true
                        } else {
                            fullScreenOpen = true
                        }
                    }
            ) {
                Image(
                    bitmap = thumbnailBitmap,
                    contentDescription = stringResource(R.string.media_image_content_description),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (shouldDownloadVideo) {
                    MediaPreviewPlayBadge(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    MediaPreviewOpenBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }
                if (downloadRequested && shouldDownloadVideo) {
                    VideoDownloadOverlay(
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
        message.mediaFileId > 0 && (message.kind == MessageKind.Image || message.kind == MessageKind.Video) -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clickable {
                        requestMediaDownload()
                        if (message.kind == MessageKind.Video) {
                            fullScreenOpen = true
                        }
                    },
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                MediaPreviewPlaceholder(
                    message = message,
                    hintRes = if (message.kind == MessageKind.Video) {
                        if (downloadRequested) R.string.media_video_downloading else R.string.media_video_not_ready
                    } else {
                        R.string.media_tap_to_download
                    }
                )
            }
        }
        else -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AiThemeTokens.InsetPadding),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (message.mediaFileId > 0) {
                                Modifier.clickable { requestMediaDownload() }
                            } else {
                                Modifier
                            }
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(mediaLabel(message))
                    Spacer(Modifier.width(AiThemeTokens.ListSpacing))
                    Column(Modifier.weight(1f)) {
                        Text(mediaLabel(message), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (message.mediaFileId > 0) {
                                stringResource(R.string.media_tap_to_download)
                            } else {
                                stringResource(R.string.media_not_available)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = mediaMetaText(message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (fullScreenOpen) {
        FullScreenMediaViewer(
            mediaMessages = mediaMessages,
            initialMessage = message,
            videoSubtitlesEnabled = videoSubtitlesEnabled,
            videoSourceLanguage = videoSourceLanguage,
            videoSubtitleColor = videoSubtitleColor,
            autoPlayVideo = autoPlayVideo || playWhenReady,
            activeVideoKey = activeVideoKey,
            onActiveVideoChange = onActiveVideoChange,
            onDownloadMedia = onDownloadMedia,
            onRestartMediaDownload = onRestartMediaDownload,
            onClose = { fullScreenOpen = false }
        )
    }
}

@Composable
private fun MediaPreviewOpenBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(AiThemeTokens.IconButtonSize),
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.58f),
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_view),
                contentDescription = stringResource(R.string.open_media_fullscreen),
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(AiThemeTokens.ControlIconSize)
            )
        }
    }
}

@Composable
private fun MediaPreviewPlayBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(AiThemeTokens.HeroIconTileSize),
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.62f),
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(android.R.drawable.ic_media_play),
                contentDescription = stringResource(R.string.download_and_play_video),
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(AiThemeTokens.LargeIconSize)
            )
        }
    }
}

private fun TelegramMessage.uidForMediaPreview(): String {
    return "$chatId:$id:$mediaLocalPath:$mediaThumbnailLocalPath"
}

private fun TelegramMessage.videoPlaybackStateKey(videoFile: File?): String {
    val length = videoFile?.length() ?: 0L
    val modifiedAt = videoFile?.lastModified() ?: 0L
    return listOf(
        chatId,
        id,
        videoFile?.absolutePath.orEmpty(),
        mediaDownloadedPrefixBytes,
        mediaSizeMb,
        length,
        modifiedAt
    ).joinToString(":")
}

private fun TelegramMessage.videoReviewStorageKey(): String {
    return listOf(chatId, id, mediaFileId.takeIf { it > 0 } ?: mediaLocalPath).joinToString(":")
}

private fun String.toReviewBookmarks(): Set<Long> {
    return split(",")
        .mapNotNull { raw -> raw.trim().toLongOrNull()?.takeIf { it >= 0L } }
        .toSet()
}

private fun Set<Long>.toReviewBookmarksString(): String {
    return sorted().joinToString(",")
}

private fun String.toReviewNotes(): List<VideoReviewNote> {
    return lineSequence()
        .mapNotNull { line ->
            val separatorIndex = line.indexOf('\t')
            if (separatorIndex <= 0) return@mapNotNull null
            val positionMs = line.substring(0, separatorIndex).toLongOrNull() ?: return@mapNotNull null
            val text = Uri.decode(line.substring(separatorIndex + 1)).trim()
            if (text.isBlank()) null else VideoReviewNote(positionMs, text)
        }
        .sortedBy { it.positionMs }
        .toList()
}

private fun List<VideoReviewNote>.toReviewNotesString(): String {
    return sortedBy { it.positionMs }
        .joinToString(separator = "\n") { note ->
            "${note.positionMs}\t${Uri.encode(note.text)}"
        }
}

private fun Long.formatReviewTime(): String {
    val totalSeconds = (this.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun Float.reviewSpeedLabel(): String {
    return if (this == 1f) {
        "1x"
    } else {
        String.format(Locale.US, "%.1fx", this)
    }
}

@Composable
private fun String.sourceLanguageDisplayName(): String {
    val cleanCode = trim()
    if (cleanCode.isBlank()) return stringResource(R.string.video_subtitle_mode_source)
    val locale = LocalLocale.current.platformLocale
    val displayName = Locale.forLanguageTag(cleanCode)
        .getDisplayLanguage(locale)
        .takeIf { it.isNotBlank() && !it.equals(cleanCode, ignoreCase = true) }
        ?: return stringResource(R.string.video_subtitle_mode_source)
    return displayName.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(locale) else char.toString()
    }
}

private fun List<VideoSubtitleCue>.toReviewTranscript(): String {
    return joinToString(separator = "\n") { cue ->
        "${cue.startMs.formatReviewTime()} ${cue.text}"
    }
}

private fun List<VideoSubtitleCue>.toReviewReport(
    message: TelegramMessage,
    notes: List<VideoReviewNote>,
    bookmarks: Set<Long>
): String {
    return buildString {
        appendLine(message.mediaFileName.ifBlank { "Video ${message.id}" })
        appendLine("Chat: ${message.chatId}")
        appendLine("Message: ${message.id}")
        if (message.mediaMimeType.isNotBlank()) {
            appendLine("MIME: ${message.mediaMimeType}")
        }
        if (message.mediaSizeMb > 0) {
            appendLine("Size: ${message.mediaSizeMb} MB")
        }
        if (bookmarks.isNotEmpty()) {
            appendLine()
            appendLine("Bookmarks")
            bookmarks.sorted().forEach { positionMs ->
                appendLine("- ${positionMs.formatReviewTime()}")
            }
        }
        val noteText = notes.toReviewNotesText()
        if (noteText.isNotBlank()) {
            appendLine()
            appendLine("Review notes")
            appendLine(noteText)
        }
        val transcript = toReviewTranscript()
        if (transcript.isNotBlank()) {
            appendLine()
            appendLine("Transcript")
            appendLine(transcript)
        }
    }.trim()
}

private fun List<VideoSubtitleCue>.toReviewSummary(
    message: TelegramMessage,
    notes: List<VideoReviewNote> = emptyList()
): String {
    val title = message.mediaFileName.ifBlank { "Video ${message.id}" }
    val highlights = take(6)
        .joinToString(separator = "\n") { cue -> "- ${cue.startMs.formatReviewTime()}: ${cue.text}" }
    return buildString {
        appendLine(title)
        appendLine("${this@toReviewSummary.size} transcript segments")
        if (notes.isNotEmpty()) {
            appendLine("${notes.size} review notes")
        }
        if (highlights.isNotBlank()) {
            appendLine()
            append(highlights)
        }
        val noteText = notes.toReviewNotesText()
        if (noteText.isNotBlank()) {
            appendLine()
            appendLine()
            append(noteText)
        }
    }.trim()
}

private fun List<VideoReviewNote>.toReviewNotesText(): String {
    return sortedBy { it.positionMs }
        .joinToString(separator = "\n") { note ->
            "${note.positionMs.formatReviewTime()} ${note.text}"
        }
}

private suspend fun captureVideoReviewFrame(
    context: Context,
    videoFile: File,
    positionMs: Long,
    message: TelegramMessage
): File? = withContext(Dispatchers.IO) {
    runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(
                positionMs.coerceAtLeast(0L) * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@runCatching null
            val outputDir = File(context.cacheDir, "video_review_frames").apply { mkdirs() }
            val outputFile = File(
                outputDir,
                "frame_${message.chatId}_${message.id}_${positionMs.coerceAtLeast(0L)}.jpg"
            )
            outputFile.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            bitmap.recycle()
            outputFile
        } finally {
            retriever.release()
        }
    }.getOrNull()
}

private suspend fun exportVideoReviewReport(
    context: Context,
    message: TelegramMessage,
    cues: List<VideoSubtitleCue>,
    notes: List<VideoReviewNote>,
    bookmarks: Set<Long>
): File? = withContext(Dispatchers.IO) {
    runCatching {
        val outputDir = File(context.cacheDir, "video_review_reports").apply { mkdirs() }
        val outputFile = File(outputDir, "review_${message.chatId}_${message.id}.txt")
        outputFile.writeText(
            cues.toReviewReport(
                message = message,
                notes = notes,
                bookmarks = bookmarks
            )
        )
        outputFile
    }.getOrNull()
}

private fun File.hasReadableVideoMetadata(): Boolean {
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            durationMs > 0L && hasVideo != "no"
        } finally {
            retriever.release()
        }
    }.getOrDefault(false)
}

@Composable
private fun AllowFullscreenVideoOrientation(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val activity = context.findActivity()
        if (!enabled || activity == null) {
            return@DisposableEffect onDispose {}
        }

        val previousOrientation = activity.requestedOrientation
        val window = activity.window
        val decorView = window.decorView
        val insetsController = WindowCompat.getInsetsController(window, decorView)
        val previousSystemBarsBehavior = insetsController.systemBarsBehavior
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity.requestedOrientation = previousOrientation
            insetsController.systemBarsBehavior = previousSystemBarsBehavior
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Composable
internal fun FullScreenMediaViewer(
    mediaMessages: List<TelegramMessage>,
    initialMessage: TelegramMessage,
    autoPlayVideo: Boolean,
    videoSubtitlesEnabled: Boolean,
    videoSourceLanguage: VideoSourceLanguage,
    videoSubtitleColor: VideoSubtitleColor,
    activeVideoKey: String?,
    onActiveVideoChange: (String?) -> Unit,
    onDownloadMedia: (Int, MessageKind) -> Unit,
    onRestartMediaDownload: (TelegramMessage) -> Unit,
    onClose: () -> Unit
) {
    val viewerMessages = remember(mediaMessages, initialMessage) {
        mediaMessages.takeIf { it.isNotEmpty() } ?: listOf(initialMessage)
    }
    var selectedIndex by rememberSaveable(viewerMessages.map { it.mediaViewerKey() }, initialMessage.mediaViewerKey()) {
        mutableIntStateOf(
            viewerMessages.indexOfFirst { it.mediaViewerKey() == initialMessage.mediaViewerKey() }
                .takeIf { it >= 0 }
                ?: 0
        )
    }
    val safeSelectedIndex = selectedIndex.coerceIn(0, viewerMessages.lastIndex.coerceAtLeast(0))
    LaunchedEffect(viewerMessages, selectedIndex, safeSelectedIndex) {
        if (selectedIndex != safeSelectedIndex) {
            selectedIndex = safeSelectedIndex
        }
    }
    val selectedMessage = viewerMessages.getOrNull(safeSelectedIndex) ?: initialMessage
    val renderState = rememberMediaRenderState(selectedMessage)
    val videoPlaybackKey = selectedMessage.videoPlaybackStateKey(renderState.videoFile)
    val fullscreenVideoKey = remember(selectedMessage.mediaViewerKey()) {
        "viewer:${selectedMessage.mediaViewerKey()}"
    }
    val currentActiveVideoKey by rememberUpdatedState(activeVideoKey)
    var videoPlaybackFailed by remember(videoPlaybackKey) {
        mutableStateOf(false)
    }
    var videoControlsVisible by remember(videoPlaybackKey) { mutableStateOf(true) }
    var autoPlaySelectedVideo by rememberSaveable { mutableStateOf(autoPlayVideo) }
    DisposableEffect(fullscreenVideoKey) {
        onDispose {
            if (currentActiveVideoKey == fullscreenVideoKey) {
                onActiveVideoChange(null)
            }
        }
    }
    LaunchedEffect(
        autoPlayVideo,
        fullscreenVideoKey,
        renderState.videoFile?.absolutePath,
        selectedMessage.kind
    ) {
        if (autoPlayVideo && selectedMessage.kind == MessageKind.Video) {
            autoPlaySelectedVideo = true
        }
    }
    LaunchedEffect(
        autoPlaySelectedVideo,
        fullscreenVideoKey,
        renderState.videoFile?.absolutePath,
        selectedMessage.kind
    ) {
        if (autoPlaySelectedVideo && selectedMessage.kind == MessageKind.Video && renderState.videoFile != null) {
            onActiveVideoChange(fullscreenVideoKey)
        }
    }
    AllowFullscreenVideoOrientation(
        enabled = selectedMessage.kind == MessageKind.Video &&
            renderState.videoFile != null &&
            !videoPlaybackFailed
    )
    val context = LocalContext.current
    val resources = LocalResources.current
    val reviewPrefs = remember(context) {
        context.getSharedPreferences(VIDEO_REVIEW_PREFS, Context.MODE_PRIVATE)
    }
    val videoReviewKey = selectedMessage.videoReviewStorageKey()
    var videoDurationMs by remember(videoReviewKey, renderState.videoFile?.absolutePath) {
        mutableLongStateOf(0L)
    }
    var currentVideoPositionMs by rememberSaveable(videoReviewKey, renderState.videoFile?.absolutePath) {
        mutableLongStateOf(
            reviewPrefs.getLong(
                VIDEO_REVIEW_POSITION_PREFIX + videoReviewKey,
                0L
            )
        )
    }
    var playbackSpeed by rememberSaveable(videoReviewKey) {
        mutableFloatStateOf(1f)
    }
    var loopEnabled by rememberSaveable(videoReviewKey) {
        mutableStateOf(false)
    }
    var seekRequestId by remember { mutableLongStateOf(0L) }
    var seekRequest by remember { mutableStateOf<VideoSeekRequest?>(null) }
    var transcriptExpanded by rememberSaveable(videoReviewKey) { mutableStateOf(false) }
    var subtitleMode by rememberSaveable(videoReviewKey) { mutableStateOf(VideoSubtitleMode.Source) }
    var bookmarks by remember(videoReviewKey) {
        mutableStateOf(
            reviewPrefs
                .getString(VIDEO_REVIEW_BOOKMARKS_PREFIX + videoReviewKey, "")
                .orEmpty()
                .toReviewBookmarks()
        )
    }
    var reviewNotes by remember(videoReviewKey) {
        mutableStateOf(
            reviewPrefs
                .getString(VIDEO_REVIEW_NOTES_PREFIX + videoReviewKey, "")
                .orEmpty()
                .toReviewNotes()
        )
    }
    var reviewNoteDraft by rememberSaveable(videoReviewKey) { mutableStateOf("") }
    fun seekVideoTo(positionMs: Long) {
        seekRequestId += 1
        seekRequest = VideoSeekRequest(positionMs, seekRequestId)
        currentVideoPositionMs = positionMs.coerceAtLeast(0L)
    }
    fun persistBookmarks(nextBookmarks: Set<Long>) {
        bookmarks = nextBookmarks
        reviewPrefs.edit {
            putString(VIDEO_REVIEW_BOOKMARKS_PREFIX + videoReviewKey, nextBookmarks.toReviewBookmarksString())
        }
    }
    fun bookmarkCurrentVideoPosition() {
        val normalizedPosition = currentVideoPositionMs.coerceAtLeast(0L)
        persistBookmarks(bookmarks + normalizedPosition)
    }
    fun persistReviewNotes(nextNotes: List<VideoReviewNote>) {
        reviewNotes = nextNotes.sortedBy { it.positionMs }
        reviewPrefs.edit {
            putString(VIDEO_REVIEW_NOTES_PREFIX + videoReviewKey, reviewNotes.toReviewNotesString())
        }
    }
    LaunchedEffect(videoReviewKey, selectedMessage.kind, currentVideoPositionMs, videoDurationMs) {
        if (selectedMessage.kind != MessageKind.Video) return@LaunchedEffect
        delay(800L)
        val position = currentVideoPositionMs.coerceAtLeast(0L)
        val shouldClear = videoDurationMs > 0L && position >= videoDurationMs - 2_000L
        reviewPrefs.edit {
            if (shouldClear) {
                remove(VIDEO_REVIEW_POSITION_PREFIX + videoReviewKey)
            } else {
                putLong(VIDEO_REVIEW_POSITION_PREFIX + videoReviewKey, position)
            }
        }
    }
    val videoViewerIndexes = remember(viewerMessages) {
        viewerMessages.mapIndexedNotNull { index, message ->
            index.takeIf { message.kind == MessageKind.Video }
        }
    }
    val previousViewerIndex = remember(viewerMessages, videoViewerIndexes, safeSelectedIndex, selectedMessage.kind) {
        if (selectedMessage.kind == MessageKind.Video) {
            videoViewerIndexes.lastOrNull { it < safeSelectedIndex }
        } else {
            (safeSelectedIndex - 1).takeIf { it >= 0 }
        }
    }
    val nextViewerIndex = remember(viewerMessages, videoViewerIndexes, safeSelectedIndex, selectedMessage.kind) {
        if (selectedMessage.kind == MessageKind.Video) {
            videoViewerIndexes.firstOrNull { it > safeSelectedIndex }
        } else {
            (safeSelectedIndex + 1).takeIf { it <= viewerMessages.lastIndex }
        }
    }
    val viewerPosition = remember(videoViewerIndexes, safeSelectedIndex, selectedMessage.kind) {
        if (selectedMessage.kind == MessageKind.Video && videoViewerIndexes.isNotEmpty()) {
            val videoPosition = videoViewerIndexes.indexOf(safeSelectedIndex).takeIf { it >= 0 } ?: 0
            (videoPosition + 1) to videoViewerIndexes.size
        } else {
            (safeSelectedIndex + 1) to viewerMessages.size
        }
    }
    val canGoPrevious = previousViewerIndex != null
    val canGoNext = nextViewerIndex != null
    fun moveToViewerIndex(index: Int) {
        onActiveVideoChange(null)
        autoPlaySelectedVideo = true
        videoControlsVisible = true
        selectedIndex = index
    }
    var hasSpeechPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val speechPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasSpeechPermission = granted
    }
    val subtitleGenerator = remember(context) {
        VideoSubtitleGenerator(context.applicationContext)
    }
    var subtitleState by remember(
        selectedMessage.mediaViewerKey(),
        renderState.videoFile?.absolutePath,
        videoSubtitlesEnabled,
        videoSourceLanguage,
        subtitleMode
    ) {
        mutableStateOf<VideoSubtitleUiState>(VideoSubtitleUiState.Idle)
    }

    val selectedVideoNeedsDownload = selectedMessage.kind == MessageKind.Video &&
        selectedMessage.mediaFileId > 0 &&
        renderState.videoFile == null
    var requestedVideoDownloadKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var backgroundFullVideoDownloadKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadRequestCount by remember(selectedMessage.mediaViewerKey()) { mutableIntStateOf(0) }
    var lastManualRestartAtMillis by remember(selectedMessage.mediaViewerKey()) { mutableLongStateOf(0L) }
    fun requestSelectedVideoDownload() {
        if (selectedMessage.kind != MessageKind.Video || selectedMessage.mediaFileId <= 0) return
        if (selectedMessage.mediaViewerKey() in requestedVideoDownloadKeys) return
        requestedVideoDownloadKeys = requestedVideoDownloadKeys + selectedMessage.mediaViewerKey()
        downloadRequestCount += 1
        onDownloadMedia(selectedMessage.mediaFileId, selectedMessage.kind)
    }
    fun restartSelectedVideoDownload() {
        if (selectedMessage.kind != MessageKind.Video || selectedMessage.mediaFileId <= 0) return
        requestedVideoDownloadKeys = requestedVideoDownloadKeys + selectedMessage.mediaViewerKey()
        downloadRequestCount += 1
        onRestartMediaDownload(selectedMessage)
    }
    fun requestOrRestartSelectedVideoDownload() {
        if (downloadRequestCount > 0) {
            val now = System.currentTimeMillis()
            if (now - lastManualRestartAtMillis < 1_200L) return
            lastManualRestartAtMillis = now
            restartSelectedVideoDownload()
        } else {
            requestSelectedVideoDownload()
        }
    }
    val selectedVideoDownloadRequested = autoPlayVideo ||
        selectedMessage.mediaViewerKey() in requestedVideoDownloadKeys
    LaunchedEffect(
        selectedMessage.mediaViewerKey(),
        selectedMessage.mediaFileId,
        selectedVideoNeedsDownload,
        autoPlayVideo
    ) {
        if (autoPlayVideo && selectedVideoNeedsDownload) {
            requestSelectedVideoDownload()
        }
    }
    LaunchedEffect(
        selectedMessage.mediaViewerKey(),
        selectedMessage.mediaFileId,
        renderState.videoFile?.absolutePath
    ) {
        val videoFile = renderState.videoFile
        val key = selectedMessage.mediaViewerKey()
        if (
            selectedMessage.kind == MessageKind.Video &&
            selectedMessage.mediaFileId > 0 &&
            videoFile != null &&
            key !in backgroundFullVideoDownloadKeys
        ) {
            backgroundFullVideoDownloadKeys = backgroundFullVideoDownloadKeys + key
            onDownloadMedia(selectedMessage.mediaFileId, selectedMessage.kind)
        }
    }

    LaunchedEffect(
        selectedMessage.mediaViewerKey(),
        renderState.videoFile?.absolutePath,
        videoSubtitlesEnabled,
        videoSourceLanguage,
        subtitleMode,
        hasSpeechPermission
    ) {
        val videoFile = renderState.videoFile
        if (!videoSubtitlesEnabled || selectedMessage.kind != MessageKind.Video || videoFile == null) {
            subtitleState = VideoSubtitleUiState.Idle
            return@LaunchedEffect
        }
        if (!hasSpeechPermission) {
            speechPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            subtitleState = VideoSubtitleUiState.Unavailable(
                resources.getString(R.string.video_subtitles_permission_required)
            )
            return@LaunchedEffect
        }

        subtitleState = VideoSubtitleUiState.Loading
        subtitleState = when (
            val result = subtitleGenerator.generate(
                videoFile = videoFile,
                fileId = selectedMessage.mediaFileId,
                mode = subtitleMode,
                sourceLanguageCode = videoSourceLanguage.speechLanguageTag
            )
        ) {
            is VideoSubtitleResult.Success -> VideoSubtitleUiState.Ready(
                cues = result.cues,
                sourceLanguageCode = result.sourceLanguageCode
            )
            is VideoSubtitleResult.Unavailable -> VideoSubtitleUiState.Unavailable(result.reason)
        }
    }

    val subtitleSnapshot = subtitleState
    val subtitleText = when (val state = subtitleSnapshot) {
        VideoSubtitleUiState.Idle -> ""
        VideoSubtitleUiState.Loading -> stringResource(
            if (subtitleMode == VideoSubtitleMode.Vietnamese) {
                R.string.video_subtitles_translating
            } else {
                R.string.video_subtitles_generating
            }
        )
        is VideoSubtitleUiState.Ready -> state.cues
            .firstOrNull { cue -> currentVideoPositionMs in cue.startMs until cue.endMs }
            ?.text
            .orEmpty()
        is VideoSubtitleUiState.Unavailable -> ""
    }
    val fullscreenChromeVisible = selectedMessage.kind != MessageKind.Video ||
        renderState.videoFile == null ||
        videoPlaybackFailed ||
        videoControlsVisible
    val videoReviewControlsVisible = fullscreenChromeVisible &&
        selectedMessage.kind == MessageKind.Video &&
        renderState.videoFile != null &&
        !videoPlaybackFailed
    val reviewOverlayBottomPadding = if (videoReviewControlsVisible) 96.dp else 20.dp
    val subtitleVisible = subtitleText.isNotBlank() &&
        (subtitleSnapshot is VideoSubtitleUiState.Ready || fullscreenChromeVisible)
    val transcriptCues = (subtitleState as? VideoSubtitleUiState.Ready)?.cues.orEmpty()
    val activeTranscriptCue = transcriptCues.firstOrNull { cue ->
        currentVideoPositionMs in cue.startMs until cue.endMs
    }
    val loopRange = remember(loopEnabled, activeTranscriptCue, currentVideoPositionMs, videoDurationMs) {
        if (!loopEnabled) {
            null
        } else {
            activeTranscriptCue?.let { cue ->
                VideoLoopRange(
                    startMs = cue.startMs.coerceAtLeast(0L),
                    endMs = cue.endMs.coerceAtLeast(cue.startMs + 1_000L)
                )
            } ?: run {
                val startMs = (currentVideoPositionMs - 5_000L).coerceAtLeast(0L)
                val fallbackEnd = currentVideoPositionMs + 5_000L
                val endMs = if (videoDurationMs > 0L) {
                    fallbackEnd.coerceAtMost(videoDurationMs)
                } else {
                    fallbackEnd
                }.coerceAtLeast(startMs + 1_000L)
                VideoLoopRange(startMs = startMs, endMs = endMs)
            }
        }
    }
    LaunchedEffect(loopEnabled) {
        if (loopEnabled) {
            loopRange?.let { range -> seekVideoTo(range.startMs) }
        }
    }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var reviewNotice by remember(videoReviewKey) { mutableStateOf("") }
    var transcriptQuery by rememberSaveable(videoReviewKey) { mutableStateOf("") }
    val visibleTranscriptCues = remember(transcriptCues, transcriptQuery) {
        val query = transcriptQuery.trim()
        if (query.isBlank()) {
            transcriptCues
        } else {
            transcriptCues.filter { cue -> cue.text.contains(query, ignoreCase = true) }
        }
    }
    fun seekAdjacentTranscriptCue(next: Boolean) {
        val cue = if (next) {
            transcriptCues.firstOrNull { it.startMs > currentVideoPositionMs + 500L }
        } else {
            transcriptCues.lastOrNull { it.startMs < currentVideoPositionMs - 500L }
        } ?: return
        seekVideoTo(cue.startMs)
    }
    fun copyVideoReviewText(text: String) {
        if (text.isBlank()) return
        coroutineScope.copyPlainText(clipboard, text)
        reviewNotice = resources.getString(R.string.video_review_copied)
    }
    fun saveCurrentReviewNote() {
        val noteText = reviewNoteDraft.trim()
        if (noteText.isBlank()) return
        val positionMs = activeTranscriptCue?.startMs ?: currentVideoPositionMs.coerceAtLeast(0L)
        val nextNotes = reviewNotes
            .filterNot { it.positionMs == positionMs }
            .plus(VideoReviewNote(positionMs, noteText))
        persistReviewNotes(nextNotes)
        persistBookmarks(bookmarks + positionMs)
        reviewNoteDraft = ""
        reviewNotice = resources.getString(R.string.video_review_note_saved)
    }
    fun captureCurrentFrame() {
        val videoFile = renderState.videoFile ?: return
        coroutineScope.launch {
            val frameFile = captureVideoReviewFrame(
                context = context.applicationContext,
                videoFile = videoFile,
                positionMs = currentVideoPositionMs,
                message = selectedMessage
            )
            reviewNotice = if (frameFile != null) {
                resources.getString(R.string.video_review_frame_saved, frameFile.name)
            } else {
                resources.getString(R.string.video_review_frame_failed)
            }
        }
    }
    fun shareReviewReport() {
        coroutineScope.launch {
            val reportFile = exportVideoReviewReport(
                context = context.applicationContext,
                message = selectedMessage,
                cues = transcriptCues,
                notes = reviewNotes,
                bookmarks = bookmarks
            )
            reviewNotice = if (reportFile != null) {
                resources.getString(R.string.video_review_report_saved, reportFile.name)
            } else {
                resources.getString(R.string.video_review_report_failed)
            }
            if (reportFile != null) {
                val reportUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    reportFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, reportUri)
                    putExtra(Intent.EXTRA_TEXT, reportFile.readText())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, resources.getString(R.string.video_review_export_report)))
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
        ) {
            when {
                renderState.videoFile != null && !videoPlaybackFailed -> {
                    key(videoPlaybackKey) {
                        VideoPlayer(
                            file = renderState.videoFile,
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center),
                            autoPlay = autoPlayVideo,
                            isActive = activeVideoKey == fullscreenVideoKey,
                            startPositionMs = currentVideoPositionMs,
                            playbackSpeed = playbackSpeed,
                            seekRequest = seekRequest,
                            loopRange = loopRange,
                            onPlaybackStarted = { onActiveVideoChange(fullscreenVideoKey) },
                            onPlaybackError = {
                                videoPlaybackFailed = true
                            },
                            onPositionChanged = { currentVideoPositionMs = it },
                            onDurationChanged = { videoDurationMs = it },
                            onControllerVisibilityChanged = { videoControlsVisible = it }
                        )
                    }
                }
                selectedMessage.kind == MessageKind.Video && videoPlaybackFailed -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { requestOrRestartSelectedVideoDownload() },
                        contentAlignment = Alignment.Center
                    ) {
                        MediaPreviewPlaceholder(
                            message = selectedMessage,
                            hintRes = if (selectedVideoDownloadRequested) {
                                R.string.media_video_downloading
                            } else {
                                R.string.media_playback_failed
                            }
                        )
                    }
                }
                renderState.bitmap != null -> {
                    Image(
                        bitmap = renderState.bitmap,
                        contentDescription = stringResource(R.string.media_image_content_description),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                renderState.thumbnailBitmap != null -> {
                    val shouldDownloadVideo = selectedMessage.kind == MessageKind.Video &&
                        selectedMessage.mediaFileId > 0
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (shouldDownloadVideo) {
                                    Modifier.clickable { requestOrRestartSelectedVideoDownload() }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Image(
                            bitmap = renderState.thumbnailBitmap,
                            contentDescription = stringResource(R.string.media_image_content_description),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                if (selectedVideoNeedsDownload) {
                                    requestOrRestartSelectedVideoDownload()
                                } else {
                                    selectedMessage.mediaFileId
                                        .takeIf { it > 0 }
                                        ?.let { fileId -> onDownloadMedia(fileId, selectedMessage.kind) }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        MediaPreviewPlaceholder(
                            message = selectedMessage,
                            hintRes = if (selectedMessage.kind == MessageKind.Video) {
                                if (selectedVideoNeedsDownload && selectedVideoDownloadRequested) {
                                    R.string.media_video_downloading
                                } else {
                                    R.string.media_video_not_ready
                                }
                            } else {
                                R.string.media_tap_to_download
                            }
                        )
                    }
                }
            }

            if (subtitleVisible || videoReviewControlsVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = reviewOverlayBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (subtitleVisible) {
                        Text(
                            text = subtitleText,
                            color = videoSubtitleColor.color,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = androidx.compose.ui.graphics.Color.Black,
                                    blurRadius = 6f
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        )
                    }
                    if (videoReviewControlsVisible && transcriptExpanded) {
                        VideoReviewTranscriptPanel(
                            cues = visibleTranscriptCues,
                            query = transcriptQuery,
                            totalCueCount = transcriptCues.size,
                            bookmarks = bookmarks,
                            notes = reviewNotes,
                            noteDraft = reviewNoteDraft,
                            currentPositionMs = currentVideoPositionMs,
                            onQueryChange = { transcriptQuery = it },
                            onNoteDraftChange = { reviewNoteDraft = it },
                            onSaveNote = { saveCurrentReviewNote() },
                            onCueClick = { cue -> seekVideoTo(cue.startMs) },
                            onBookmarkClick = { positionMs -> seekVideoTo(positionMs) },
                            onNoteClick = { note -> seekVideoTo(note.positionMs) },
                            onDeleteNote = { note ->
                                persistReviewNotes(reviewNotes.filterNot { it == note })
                            },
                            onClose = { transcriptExpanded = false }
                        )
                    }
                    if (videoReviewControlsVisible) {
                        VideoReviewControls(
                            playbackSpeed = playbackSpeed,
                            bookmarksCount = bookmarks.size,
                            notesCount = reviewNotes.size,
                            loopEnabled = loopEnabled,
                            loopRange = loopRange,
                            panelAvailable = true,
                            transcriptAvailable = transcriptCues.isNotEmpty(),
                            transcriptExpanded = transcriptExpanded,
                            subtitleMode = subtitleMode,
                            sourceLanguageCode = (subtitleState as? VideoSubtitleUiState.Ready)?.sourceLanguageCode.orEmpty(),
                            reviewNotice = reviewNotice,
                            onCycleSpeed = {
                                playbackSpeed = when (playbackSpeed) {
                                    0.5f -> 1f
                                    1f -> 1.5f
                                    1.5f -> 2f
                                    else -> 0.5f
                                }
                            },
                            onBookmark = { bookmarkCurrentVideoPosition() },
                            onToggleLoop = { loopEnabled = !loopEnabled },
                            onPreviousSegment = { seekAdjacentTranscriptCue(next = false) },
                            onNextSegment = { seekAdjacentTranscriptCue(next = true) },
                            onCopyTranscript = { copyVideoReviewText(transcriptCues.toReviewTranscript()) },
                            onCopySummary = { copyVideoReviewText(transcriptCues.toReviewSummary(selectedMessage, reviewNotes)) },
                            onCopyNotes = { copyVideoReviewText(reviewNotes.toReviewNotesText()) },
                            onExportReport = { shareReviewReport() },
                            onCaptureFrame = { captureCurrentFrame() },
                            onToggleTranscript = { transcriptExpanded = !transcriptExpanded },
                            onSubtitleModeChange = { nextMode ->
                                subtitleMode = nextMode
                                transcriptQuery = ""
                                reviewNotice = ""
                            },
                            modifier = Modifier.align(Alignment.Start)
                        )
                    }
                }
            }

            if (fullscreenChromeVisible) {
                MediaViewerInfoPill(
                    message = selectedMessage,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 16.dp, start = 16.dp, end = 72.dp)
                )
            }

            if (fullscreenChromeVisible && (selectedVideoNeedsDownload || (selectedMessage.kind == MessageKind.Video && videoPlaybackFailed))) {
                MediaViewerDownloadAction(
                    downloading = selectedVideoDownloadRequested,
                    message = selectedMessage,
                    downloadRequestCount = downloadRequestCount,
                    onClick = { requestOrRestartSelectedVideoDownload() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = if (subtitleVisible) 92.dp else 24.dp
                        )
                )
            }

            if (fullscreenChromeVisible && viewerMessages.size > 1) {
                IconButton(
                    onClick = { previousViewerIndex?.let(::moveToViewerIndex) },
                    enabled = canGoPrevious,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(14.dp)
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_media_previous),
                        contentDescription = stringResource(R.string.previous_media),
                        tint = androidx.compose.ui.graphics.Color.White.copy(alpha = if (canGoPrevious) 1f else 0.35f)
                    )
                }
                IconButton(
                    onClick = { nextViewerIndex?.let(::moveToViewerIndex) },
                    enabled = canGoNext,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(14.dp)
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_media_next),
                        contentDescription = stringResource(R.string.next_media),
                        tint = androidx.compose.ui.graphics.Color.White.copy(alpha = if (canGoNext) 1f else 0.35f)
                    )
                }
                Surface(
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.56f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp, start = 72.dp, end = 72.dp)
                ) {
                    Text(
                        text = stringResource(R.string.media_viewer_position, viewerPosition.first, viewerPosition.second),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = AiThemeTokens.CompactRowPaddingHorizontal, vertical = AiThemeTokens.DenseSpacing)
                    )
                }
            }
            if (fullscreenChromeVisible) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = stringResource(R.string.close_media_viewer),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoReviewControls(
    playbackSpeed: Float,
    bookmarksCount: Int,
    notesCount: Int,
    loopEnabled: Boolean,
    loopRange: VideoLoopRange?,
    panelAvailable: Boolean,
    transcriptAvailable: Boolean,
    transcriptExpanded: Boolean,
    subtitleMode: VideoSubtitleMode,
    sourceLanguageCode: String,
    reviewNotice: String,
    onCycleSpeed: () -> Unit,
    onBookmark: () -> Unit,
    onToggleLoop: () -> Unit,
    onPreviousSegment: () -> Unit,
    onNextSegment: () -> Unit,
    onCopyTranscript: () -> Unit,
    onCopySummary: () -> Unit,
    onCopyNotes: () -> Unit,
    onExportReport: () -> Unit,
    onCaptureFrame: () -> Unit,
    onToggleTranscript: () -> Unit,
    onSubtitleModeChange: (VideoSubtitleMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.68f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f))
    ) {
        LazyRow(
            modifier = Modifier.padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.CompactSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.TinySpacing)
                ) {
                    VideoSubtitleMode.values().forEach { mode ->
                        val selected = subtitleMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onSubtitleModeChange(mode) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f),
                                labelColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f),
                                selectedContainerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.94f),
                                selectedLabelColor = androidx.compose.ui.graphics.Color(0xFF111827)
                            ),
                            label = {
                                VideoReviewChipLabel(
                                    text = when (mode) {
                                        VideoSubtitleMode.Source -> sourceLanguageCode.sourceLanguageDisplayName()
                                        VideoSubtitleMode.Vietnamese -> stringResource(R.string.video_subtitle_mode_vietnamese)
                                    },
                                    selected = selected
                                )
                            }
                        )
                    }
                }
            }
            item {
                AssistChip(
                    onClick = onCycleSpeed,
                    label = {
                        VideoReviewChipLabel(stringResource(R.string.video_review_speed, playbackSpeed.reviewSpeedLabel()))
                    }
                )
            }
            item {
                AssistChip(
                    onClick = onBookmark,
                    label = {
                        VideoReviewChipLabel(
                            if (bookmarksCount > 0) {
                                pluralStringResource(
                                    R.plurals.video_review_bookmarks_count,
                                    bookmarksCount,
                                    bookmarksCount
                                )
                            } else {
                                stringResource(R.string.video_review_bookmark)
                            }
                        )
                    }
                )
            }
            item {
                AssistChip(
                    onClick = onToggleLoop,
                    label = {
                        VideoReviewChipLabel(
                            if (loopEnabled && loopRange != null) {
                                stringResource(
                                    R.string.video_review_loop_range,
                                    loopRange.startMs.formatReviewTime(),
                                    loopRange.endMs.formatReviewTime()
                                )
                            } else if (loopEnabled) {
                                stringResource(R.string.video_review_loop_on)
                            } else {
                                stringResource(R.string.video_review_loop_off)
                            }
                        )
                    }
                )
            }
            if (notesCount > 0) {
                item {
                    AssistChip(
                        onClick = onCopyNotes,
                        label = {
                            VideoReviewChipLabel(
                                pluralStringResource(R.plurals.video_review_notes_count, notesCount, notesCount)
                            )
                        }
                    )
                }
            }
            item {
                AssistChip(
                    onClick = onPreviousSegment,
                    enabled = transcriptAvailable,
                    label = {
                        VideoReviewChipLabel(
                            stringResource(R.string.video_review_previous_segment),
                            enabled = transcriptAvailable
                        )
                    }
                )
            }
            item {
                AssistChip(
                    onClick = onNextSegment,
                    enabled = transcriptAvailable,
                    label = {
                        VideoReviewChipLabel(
                            stringResource(R.string.video_review_next_segment),
                            enabled = transcriptAvailable
                        )
                    }
                )
            }
            item {
                AssistChip(
                    onClick = onToggleTranscript,
                    enabled = panelAvailable,
                    label = {
                        VideoReviewChipLabel(
                            if (transcriptExpanded) {
                                stringResource(R.string.video_review_close)
                            } else {
                                stringResource(R.string.video_review_transcript)
                            },
                            enabled = panelAvailable
                        )
                    }
                )
            }
            item {
                AssistChip(
                    onClick = onCopyTranscript,
                    enabled = transcriptAvailable,
                    label = {
                        VideoReviewChipLabel(
                            stringResource(R.string.video_review_copy_transcript),
                            enabled = transcriptAvailable
                        )
                    }
                )
            }
            item {
                AssistChip(
                    onClick = onCopySummary,
                    enabled = transcriptAvailable,
                    label = {
                        VideoReviewChipLabel(
                            stringResource(R.string.video_review_copy_summary),
                            enabled = transcriptAvailable
                        )
                    }
                )
            }
            item {
                AssistChip(
                    onClick = onExportReport,
                    label = { VideoReviewChipLabel(stringResource(R.string.video_review_export_report)) }
                )
            }
            item {
                AssistChip(
                    onClick = onCaptureFrame,
                    label = { VideoReviewChipLabel(stringResource(R.string.video_review_capture_frame)) }
                )
            }
            if (reviewNotice.isNotBlank()) {
                item {
                    Text(
                        text = reviewNotice,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoReviewChipLabel(
    text: String,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    Text(
        text = text,
        color = when {
            selected -> androidx.compose.ui.graphics.Color(0xFF111827)
            enabled -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f)
            else -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.46f)
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun VideoReviewTranscriptPanel(
    cues: List<VideoSubtitleCue>,
    query: String,
    totalCueCount: Int,
    bookmarks: Set<Long>,
    notes: List<VideoReviewNote>,
    noteDraft: String,
    currentPositionMs: Long,
    onQueryChange: (String) -> Unit,
    onNoteDraftChange: (String) -> Unit,
    onSaveNote: () -> Unit,
    onCueClick: (VideoSubtitleCue) -> Unit,
    onBookmarkClick: (Long) -> Unit,
    onNoteClick: (VideoReviewNote) -> Unit,
    onDeleteNote: (VideoReviewNote) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 340.dp),
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.78f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(AiThemeTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.video_review_title),
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = stringResource(R.string.video_review_close),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { Text(stringResource(R.string.video_review_search_hint)) },
                supportingText = {
                    if (query.isNotBlank()) {
                        Text(pluralStringResource(R.plurals.video_review_matches, cues.size, cues.size))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = androidx.compose.ui.graphics.Color.White,
                    unfocusedTextColor = androidx.compose.ui.graphics.Color.White,
                    focusedLabelColor = androidx.compose.ui.graphics.Color.White,
                    unfocusedLabelColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f),
                    focusedBorderColor = androidx.compose.ui.graphics.Color.White,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.42f),
                    cursorColor = androidx.compose.ui.graphics.Color.White,
                    focusedSupportingTextColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f),
                    unfocusedSupportingTextColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
            ) {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = onNoteDraftChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.video_review_note_hint)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = androidx.compose.ui.graphics.Color.White,
                        unfocusedTextColor = androidx.compose.ui.graphics.Color.White,
                        focusedLabelColor = androidx.compose.ui.graphics.Color.White,
                        unfocusedLabelColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f),
                        focusedBorderColor = androidx.compose.ui.graphics.Color.White,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.42f),
                        cursorColor = androidx.compose.ui.graphics.Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onSaveNote,
                    enabled = noteDraft.isNotBlank()
                ) {
                    Text(stringResource(R.string.video_review_save_note))
                }
            }
            if (bookmarks.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    items(bookmarks.toList().sorted()) { positionMs ->
                        AssistChip(
                            onClick = { onBookmarkClick(positionMs) },
                            label = { VideoReviewChipLabel(positionMs.formatReviewTime()) }
                        )
                    }
                }
            }
            if (notes.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    items(notes.sortedBy { it.positionMs }) { note ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.TinySpacing)
                        ) {
                            AssistChip(
                                onClick = { onNoteClick(note) },
                                label = {
                                    VideoReviewChipLabel("${note.positionMs.formatReviewTime()} ${note.text}")
                                }
                            )
                            IconButton(
                                onClick = { onDeleteNote(note) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_menu_delete),
                                    contentDescription = stringResource(R.string.video_review_delete_note),
                                    tint = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                    }
                }
            }
            if (cues.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (totalCueCount > 0 && query.isNotBlank()) {
                            pluralStringResource(R.plurals.video_review_matches, 0, 0)
                        } else {
                            stringResource(R.string.video_review_no_transcript)
                        },
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing)
                ) {
                    items(cues) { cue ->
                        val active = currentPositionMs in cue.startMs until cue.endMs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (active) {
                                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f)
                                    } else {
                                        androidx.compose.ui.graphics.Color.Transparent
                                    }
                                )
                                .clickable { onCueClick(cue) }
                                .padding(horizontal = AiThemeTokens.CompactSpacing, vertical = AiThemeTokens.DenseSpacing),
                            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
                        ) {
                            Text(
                                text = cue.startMs.formatReviewTime(),
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(48.dp)
                            )
                            Text(
                                text = cue.text,
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaViewerInfoPill(
    message: TelegramMessage,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.56f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.CompactSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            Icon(
                painter = painterResource(
                    if (message.kind == MessageKind.Video) {
                        android.R.drawable.ic_media_play
                    } else {
                        android.R.drawable.ic_menu_gallery
                    }
                ),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(AiThemeTokens.ControlIconSize)
            )
            Column(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.HairlineSpacing)) {
                Text(
                    text = mediaLabel(message),
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = mediaMetaText(message),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MediaViewerDownloadAction(
    downloading: Boolean,
    message: TelegramMessage,
    downloadRequestCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadedBytes = message.downloadedMediaBytes()
    val totalBytes = message.mediaSizeMb
        .takeIf { it > 0 }
        ?.toLong()
        ?.times(BYTES_PER_MB)
        ?: 0L
    val progress = if (totalBytes > 0L) {
        (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    val progressPercent = progress?.let { (it * 100f).toInt().coerceIn(0, 100) }
    val readyProgress = (downloadedBytes.toFloat() / MIN_VIDEO_START_BYTES.toFloat()).coerceIn(0f, 1f)
    val progressText = if (totalBytes > 0L) {
        stringResource(
            R.string.media_download_progress_percent,
            downloadedBytes.formatMediaBytes(),
            totalBytes.formatMediaBytes(),
            progressPercent ?: 0
        )
    } else {
        stringResource(R.string.media_download_progress_bytes, downloadedBytes.formatMediaBytes())
    }
    Surface(
        modifier = modifier,
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.66f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = AiThemeTokens.InsetPadding, vertical = AiThemeTokens.ListSpacing),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_media_play),
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(AiThemeTokens.IconSize)
                )
                Text(
                    text = stringResource(
                        if (downloading) R.string.media_video_downloading else R.string.download_and_play_video
                    ),
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = androidx.compose.ui.graphics.Color.White,
                    trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.24f)
                )
            } else {
                LinearProgressIndicator(
                    progress = { readyProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = androidx.compose.ui.graphics.Color.White,
                    trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.24f)
                )
            }
            Text(
                text = progressText,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = stringResource(
                    R.string.media_download_ready_threshold,
                    MIN_VIDEO_START_BYTES.formatMediaBytes()
                ),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = stringResource(R.string.media_download_retry_hint),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
            if (downloadRequestCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.media_download_request_count,
                        downloadRequestCount,
                        downloadRequestCount
                    ),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private sealed interface VideoSubtitleUiState {
    data object Idle : VideoSubtitleUiState
    data object Loading : VideoSubtitleUiState
    data class Ready(
        val cues: List<VideoSubtitleCue>,
        val sourceLanguageCode: String = ""
    ) : VideoSubtitleUiState
    data class Unavailable(val reason: String) : VideoSubtitleUiState
}

private data class VideoSeekRequest(
    val positionMs: Long,
    val requestId: Long
)

private data class VideoLoopRange(
    val startMs: Long,
    val endMs: Long
)

private data class VideoReviewNote(
    val positionMs: Long,
    val text: String
)

internal data class MediaRenderState(
    val bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    val thumbnailBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    val videoFile: File?
)

@Composable
private fun rememberPlayableVideoFile(message: TelegramMessage, localFile: File?): File? {
    var videoFile by remember(
        message.mediaViewerKey(),
        message.mediaLocalPath,
        message.mediaDownloadedPrefixBytes,
        message.mediaSizeMb,
        message.kind
    ) {
        mutableStateOf<File?>(null)
    }
    LaunchedEffect(
        message.mediaViewerKey(),
        message.mediaLocalPath,
        message.mediaDownloadedPrefixBytes,
        message.mediaSizeMb,
        message.kind
    ) {
        videoFile = null
        if (message.kind != MessageKind.Video) return@LaunchedEffect
        repeat(VIDEO_FAST_START_POLL_ATTEMPTS) { attempt ->
            val playableFile = withContext(Dispatchers.IO) {
                localFile?.takeIf { file ->
                    file.exists() && file.length() > 0L &&
                        (message.hasEnoughVideoPrefix(file) || file.hasReadableVideoMetadata())
                }
            }
            if (playableFile != null) {
                videoFile = playableFile
                return@LaunchedEffect
            }
            if (attempt < VIDEO_FAST_START_POLL_ATTEMPTS - 1) {
                delay(VIDEO_FAST_START_POLL_DELAY_MS)
            }
        }
    }
    return videoFile
}

private fun TelegramMessage.hasEnoughVideoPrefix(file: File): Boolean {
    return mediaDownloadedPrefixBytes >= MIN_VIDEO_START_BYTES ||
        file.length() >= MIN_VIDEO_START_BYTES
}

@Composable
internal fun rememberMediaRenderState(message: TelegramMessage): MediaRenderState {
    val localFile = remember(message.mediaLocalPath) {
        message.mediaLocalPath.takeIf { it.isNotBlank() }?.let(::File)
    }
    val bitmap = remember(message.mediaLocalPath, message.kind) {
        if (message.kind == MessageKind.Image && localFile?.exists() == true) {
            runCatching { BitmapFactory.decodeFile(localFile.absolutePath)?.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }
    val videoFile = rememberPlayableVideoFile(message, localFile)
    val thumbnailFile = remember(message.mediaThumbnailLocalPath) {
        message.mediaThumbnailLocalPath.takeIf { it.isNotBlank() }?.let(::File)
    }
    val thumbnailBitmap = remember(message.mediaThumbnailLocalPath) {
        if (thumbnailFile?.exists() == true) {
            runCatching { BitmapFactory.decodeFile(thumbnailFile.absolutePath)?.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }
    return MediaRenderState(
        bitmap = bitmap,
        thumbnailBitmap = thumbnailBitmap,
        videoFile = videoFile
    )
}

internal fun TelegramMessage.mediaViewerKey(): String = "$chatId:$id"

internal fun TelegramMessage.hasReadableChatMediaLocalFile(): Boolean {
    return mediaLocalPath
        .takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.let { it.isFile && it.canRead() && it.length() > 0L }
        ?: false
}

internal fun Context.pendingComposerMedia(
    uri: Uri,
    forcedKind: MessageKind? = null,
    highQualityPhoto: Boolean = false
): PendingComposerMedia {
    val mimeType = contentResolver.getType(uri).orEmpty()
    val kind = forcedKind ?: when {
        mimeType.startsWith("image/") -> MessageKind.Image
        mimeType.startsWith("video/") -> MessageKind.Video
        mimeType.startsWith("audio/") -> MessageKind.Audio
        else -> MessageKind.File
    }
    return PendingComposerMedia(
        uri = uri,
        kind = kind,
        displayName = contentResolver.displayName(uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: getString(kind.composerLabelRes()),
        highQualityPhoto = highQualityPhoto && kind == MessageKind.Image
    )
}

internal fun Context.createComposerCameraUri(): Uri {
    val captureDir = File(cacheDir, "composer_camera").apply { mkdirs() }
    val captureFile = File(
        captureDir,
        "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "")}.jpg"
    )
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", captureFile)
}

private fun android.content.ContentResolver.displayName(uri: Uri): String? {
    return query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(0)
        } else {
            null
        }
    }
}

internal fun android.content.ContentResolver.decodeComposerPreview(
    uri: Uri,
    maxEdgePx: Int = 512
): androidx.compose.ui.graphics.ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    val largestEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (largestEdge <= 0) return null
    var sampleSize = 1
    while (largestEdge / sampleSize > maxEdgePx) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
    }
}

internal fun CoroutineScope.copyPlainText(clipboard: androidx.compose.ui.platform.Clipboard, text: String) {
    launch {
        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
    }
}

internal fun MessageKind.composerIconRes(): Int {
    return when (this) {
        MessageKind.Image -> android.R.drawable.ic_menu_gallery
        MessageKind.Video -> R.drawable.ic_ai_video
        MessageKind.File -> R.drawable.ic_ai_paperclip
        MessageKind.Voice -> android.R.drawable.ic_btn_speak_now
        MessageKind.VideoNote -> R.drawable.ic_ai_video
        MessageKind.Audio -> android.R.drawable.ic_media_play
        MessageKind.Sticker -> android.R.drawable.ic_menu_gallery
        MessageKind.Text -> R.drawable.ic_ai_chat
    }
}

internal fun MessageKind.composerLabelRes(): Int {
    return when (this) {
        MessageKind.Image -> R.string.media_image
        MessageKind.Video -> R.string.media_video
        MessageKind.File -> R.string.media_file
        MessageKind.Voice -> R.string.media_voice
        MessageKind.VideoNote -> R.string.media_video_message
        MessageKind.Audio -> R.string.media_audio
        MessageKind.Sticker -> R.string.media_sticker
        MessageKind.Text -> R.string.nav_chats
    }
}

@Composable
private fun VideoDownloadOverlay(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.68f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AiThemeTokens.ScreenPadding, vertical = AiThemeTokens.SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            Text(
                text = stringResource(R.string.media_video_downloading),
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}

@Composable
private fun MediaPreviewPlaceholder(
    message: TelegramMessage,
    hintRes: Int = R.string.media_tap_to_download
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing)
        ) {
            Avatar(mediaLabel(message))
            Text(mediaLabel(message), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                mediaMetaText(message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun VideoPlayer(
    file: File,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    isActive: Boolean = true,
    startPositionMs: Long = 0L,
    playbackSpeed: Float = 1f,
    seekRequest: VideoSeekRequest? = null,
    loopRange: VideoLoopRange? = null,
    onPlaybackStarted: () -> Unit = {},
    onPlaybackError: () -> Unit = {},
    onPositionChanged: (Long) -> Unit = {},
    onDurationChanged: (Long) -> Unit = {},
    onControllerVisibilityChanged: (Boolean) -> Unit = {}
) {
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    val currentOnPlaybackStarted by rememberUpdatedState(onPlaybackStarted)
    val currentOnPositionChanged by rememberUpdatedState(onPositionChanged)
    val currentOnDurationChanged by rememberUpdatedState(onDurationChanged)
    val currentOnControllerVisibilityChanged by rememberUpdatedState(onControllerVisibilityChanged)
    val currentLoopRange by rememberUpdatedState(loopRange)
    val currentIsActive by rememberUpdatedState(isActive)
    var handledSeekRequestId by remember(file.absolutePath) { mutableStateOf<Long?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                tag = file.absolutePath
                useController = true
                controllerAutoShow = true
                controllerShowTimeoutMs = 3000
                setControllerHideOnTouch(true)
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        currentOnControllerVisibilityChanged(visibility == android.view.View.VISIBLE)
                    }
                )
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setShowFastForwardButton(true)
                setShowRewindButton(true)
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        VIDEO_MIN_BUFFER_MS,
                        VIDEO_MAX_BUFFER_MS,
                        VIDEO_BUFFER_FOR_PLAYBACK_MS,
                        VIDEO_BUFFER_FOR_PLAYBACK_MS
                    )
                    .build()
                val createdPlayer = runCatching {
                    ExoPlayer.Builder(context)
                        .setLoadControl(loadControl)
                        .build()
                        .apply {
                            addListener(object : Player.Listener {
                                override fun onPlayerError(error: PlaybackException) {
                                    currentOnPlaybackError()
                                }

                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                    if (isPlaying) {
                                        currentOnPlaybackStarted()
                                    }
                                }

                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    if (playbackState == Player.STATE_READY) {
                                        currentOnDurationChanged(duration.coerceAtLeast(0L))
                                    }
                                }
                            })
                            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                            playWhenReady = autoPlay && isActive
                            setPlaybackSpeed(playbackSpeed)
                            if (startPositionMs > 0L) {
                                seekTo(startPositionMs)
                            }
                            prepare()
                        }
                }.onFailure {
                    post { currentOnPlaybackError() }
                }.getOrNull()
                this.player = createdPlayer
                val positionTicker = object : Runnable {
                    override fun run() {
                        val activePlayer = this@apply.player
                        if (activePlayer != null) {
                            val positionMs = activePlayer.currentPosition.coerceAtLeast(0L)
                            currentLoopRange?.let { range ->
                                if (positionMs >= range.endMs) {
                                    activePlayer.seekTo(range.startMs)
                                    if (currentIsActive) {
                                        activePlayer.playWhenReady = true
                                    }
                                } else if (positionMs < range.startMs) {
                                    activePlayer.seekTo(range.startMs)
                                }
                            }
                            currentOnPositionChanged(activePlayer.currentPosition.coerceAtLeast(0L))
                            currentOnDurationChanged(activePlayer.duration.coerceAtLeast(0L))
                        }
                        postDelayed(this, 250L)
                    }
                }
                addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: android.view.View) {
                        v.post(positionTicker)
                    }

                    override fun onViewDetachedFromWindow(v: android.view.View) {
                        v.removeCallbacks(positionTicker)
                        (this@apply.player as? ExoPlayer)?.pause()
                    }
                })
            }
        },
        update = { view ->
            val player = view.player as? ExoPlayer ?: return@AndroidView
            if (!isActive) {
                player.playWhenReady = false
            } else if (autoPlay && !player.playWhenReady) {
                player.playWhenReady = true
            }
            if (view.tag != file.absolutePath) {
                view.tag = file.absolutePath
                runCatching {
                    player.playWhenReady = false
                    player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    player.prepare()
                    player.seekTo(startPositionMs.coerceAtLeast(0L))
                    player.playWhenReady = autoPlay && isActive
                }.onFailure {
                    view.post { currentOnPlaybackError() }
                }
            }
            runCatching {
                player.setPlaybackSpeed(playbackSpeed)
            }.onFailure {
                view.post { currentOnPlaybackError() }
            }
            seekRequest?.takeIf { request -> request.requestId != handledSeekRequestId }?.let { request ->
                runCatching {
                    player.seekTo(request.positionMs.coerceAtLeast(0L))
                    if (isActive && !player.isPlaying) {
                        player.playWhenReady = true
                    }
                    handledSeekRequestId = request.requestId
                }.onFailure {
                    view.post { currentOnPlaybackError() }
                }
            }
        },
        onRelease = { view ->
            runCatching {
                view.setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?)
                view.player?.pause()
                view.player?.clearMediaItems()
                view.player?.release()
                view.player = null
            }
        }
    )
}

@Composable
internal fun mediaLabel(message: TelegramMessage): String {
    return when (message.kind) {
        MessageKind.Image -> stringResource(R.string.media_image)
        MessageKind.Video -> stringResource(R.string.media_video)
        MessageKind.File -> stringResource(R.string.media_file)
        MessageKind.Voice -> stringResource(R.string.media_voice)
        MessageKind.VideoNote -> stringResource(R.string.media_video_message)
        MessageKind.Audio -> stringResource(R.string.media_audio)
        MessageKind.Sticker -> stringResource(R.string.media_sticker)
        MessageKind.Text -> ""
    }
}

@Composable
internal fun mediaMetaText(message: TelegramMessage): String {
    return listOfNotNull(
        message.mediaFileName.takeIf { it.isNotBlank() },
        message.mediaMimeType.takeIf { it.isNotBlank() },
        message.mediaSizeMb.takeIf { it > 0 }?.let { "$it MB" }
    ).joinToString(stringResource(R.string.inline_separator)).ifBlank { stringResource(R.string.media_generic) }
}

private fun TelegramMessage.downloadedMediaBytes(): Long {
    val localFileBytes = mediaLocalPath
        .takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.exists() }
        ?.length()
        ?: 0L
    return maxOf(mediaDownloadedPrefixBytes, localFileBytes)
}

private fun Long.formatMediaBytes(): String {
    val bytes = coerceAtLeast(0L)
    return when {
        bytes >= BYTES_PER_MB -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / BYTES_PER_MB.toDouble())
        bytes >= BYTES_PER_KB -> String.format(Locale.US, "%.0f KB", bytes.toDouble() / BYTES_PER_KB.toDouble())
        else -> "$bytes B"
    }
}

@Composable
internal fun MissingTranslationModelBlock(
    detectedLanguage: String,
    targetLanguage: String,
    onOpenCache: () -> Unit
) {
    val pair = remember(detectedLanguage, targetLanguage) {
        VietnameseTranslationPairs.common.firstOrNull {
            it.sourceLanguageCode == detectedLanguage && it.targetLanguageCode == targetLanguage
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AiThemeTokens.CompactSpacing,
                vertical = AiThemeTokens.SmallSpacing
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            Text(
                text = if (pair != null) {
                    stringResource(R.string.translation_model_pair_hint, pair.label)
                } else {
                    stringResource(R.string.translation_model_hint)
                },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CompactActionButton(
                label = stringResource(R.string.open_cache),
                iconRes = android.R.drawable.ic_menu_save,
                onClick = onOpenCache
            )
        }
    }
}

@Composable
internal fun TranslationRetryBlock(
    failureReason: TranslationFailureReason,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AiThemeTokens.CompactSpacing,
                vertical = AiThemeTokens.SmallSpacing
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            Text(
                text = stringResource(failureReason.retryHintRes()),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CompactActionButton(
                label = stringResource(R.string.retry_translation),
                iconRes = R.drawable.ic_action_refresh,
                onClick = onRetry
            )
        }
    }
}

private fun TranslationFailureReason.retryHintRes(): Int {
    return when (this) {
        TranslationFailureReason.UnsupportedLanguage -> R.string.translation_retry_unsupported_language_hint
        TranslationFailureReason.UndetectedLanguage -> R.string.translation_retry_undetected_language_hint
        TranslationFailureReason.NetworkRequired -> R.string.translation_retry_network_required_hint
        TranslationFailureReason.TransientError -> R.string.translation_retry_transient_hint
        TranslationFailureReason.NonTranslatable,
        TranslationFailureReason.None,
        TranslationFailureReason.MissingModel -> R.string.translation_retry_generic_hint
    }
}
