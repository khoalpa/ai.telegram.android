package ai.telegram.android

import ai.telegram.android.billing.DonationBillingManager
import ai.telegram.android.billing.DonationBillingState
import ai.telegram.android.billing.DonationProduct
import ai.telegram.android.data.BlacklistRepository
import ai.telegram.android.data.ChatRepository
import ai.telegram.android.data.HiddenContent
import ai.telegram.android.data.MediaDownloadPolicy
import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.MessageRepository
import ai.telegram.android.data.NetworkMode
import ai.telegram.android.data.SenderRepository
import ai.telegram.android.data.TelegramChat
import ai.telegram.android.data.TelegramMediaFile
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TelegramSender
import ai.telegram.android.data.TranslationCacheRepository
import ai.telegram.android.data.TranslationFailureReason
import ai.telegram.android.data.TranslationJobRepository
import ai.telegram.android.data.TranslationStatus
import ai.telegram.android.data.local.AppDatabase
import ai.telegram.android.data.uid
import ai.telegram.android.data.telegram.TdLibConfig
import ai.telegram.android.data.telegram.TdLibReflectionClient
import ai.telegram.android.data.telegram.TdLibStatus
import ai.telegram.android.data.telegram.TelegramBotCommand
import ai.telegram.android.data.telegram.TelegramBotMenuButton
import ai.telegram.android.data.telegram.MediaSendOptions
import ai.telegram.android.data.telegram.TelegramActiveSession
import ai.telegram.android.data.telegram.TelegramBusinessChatLink
import ai.telegram.android.data.telegram.TelegramChatPermissionPreset
import ai.telegram.android.data.telegram.TelegramClient
import ai.telegram.android.data.telegram.TelegramCall
import ai.telegram.android.data.telegram.TelegramCallMediaEngine
import ai.telegram.android.data.telegram.TelegramCallState
import ai.telegram.android.data.telegram.TelegramInlineBotResults
import ai.telegram.android.data.telegram.TelegramPremiumFeatureInfo
import ai.telegram.android.data.telegram.TelegramPremiumLimitSummary
import ai.telegram.android.data.telegram.TelegramPrivacyRuleSummary
import ai.telegram.android.data.telegram.TelegramStarBalance
import ai.telegram.android.data.telegram.TelegramStarTransaction
import ai.telegram.android.data.telegram.TelegramStory
import ai.telegram.android.data.telegram.TelegramStoryState
import ai.telegram.android.data.telegram.TelegramOutgoingMedia
import ai.telegram.android.data.telegram.TelegramWebAppUrl
import ai.telegram.android.data.translation.MlKitModelManager
import ai.telegram.android.data.translation.MlKitModelDownloadProgress
import ai.telegram.android.data.translation.MlKitTranslationModelInfo
import ai.telegram.android.data.translation.MlKitTranslationPreflight
import ai.telegram.android.data.translation.TranslationQueue
import ai.telegram.android.data.translation.TranslationQueueResult
import ai.telegram.android.data.translation.TranslationLanguagePair
import ai.telegram.android.data.translation.VietnameseTranslationPairs
import ai.telegram.android.notifications.TelegramNotificationManager
import ai.telegram.android.notifications.TelegramPushRegistrar
import ai.telegram.android.ui.AiThemeTokens
import ai.telegram.android.ui.AppThemeMode
import ai.telegram.android.ui.MessagePrivacyPolicy
import ai.telegram.android.work.MaintenanceWorkScheduler
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID


enum class AppTab(val labelRes: Int) {
    Chats(R.string.nav_chats),
    Channels(R.string.nav_channels),
    Contacts(R.string.nav_contacts),
    Menu(R.string.nav_menu),
    AccountPrivacy(R.string.nav_account_privacy),
    PremiumBusiness(R.string.nav_premium_business),
    Stories(R.string.nav_stories),
    Bots(R.string.nav_bots),
    Calls(R.string.nav_calls),
    SecretChats(R.string.nav_secret_chats),
    Groups(R.string.nav_groups),
    Discover(R.string.nav_discover),
    Blacklist(R.string.nav_blacklist),
    Cache(R.string.nav_cache),
    Donate(R.string.nav_donate),
    Settings(R.string.nav_settings)
}

private val primaryTabs = listOf(AppTab.Chats, AppTab.Channels, AppTab.Contacts, AppTab.Settings)
private const val VIDEO_READY_PREFIX_BYTES = 2 * 1024 * 1024
private const val VIDEO_WIFI_AUTO_DOWNLOAD_MAX_MB = 8
private const val MAX_OUTGOING_MEDIA_BYTES = 512L * 1024L * 1024L
private const val MAX_OUTGOING_UPLOAD_CACHE_BYTES = 1024L * 1024L * 1024L
private const val OUTGOING_UPLOAD_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1000L
private const val APP_SETTINGS_PREFS = "ai_telegram_settings"
private const val PREF_TRANSLATED_ONLY = "translated_only"
private const val PREF_ALLOW_ADULT_CONTENT = "allow_adult_content"
private const val PREF_NOTIFICATIONS_ENABLED = "notifications_enabled"
private const val PREF_AUTO_PLAY_VIDEO = "auto_play_video"
private const val PREF_VIDEO_SUBTITLES_ENABLED = "video_subtitles_enabled"
private const val PREF_VIDEO_SOURCE_LANGUAGE = "video_source_language"
private const val PREF_VIDEO_SUBTITLE_COLOR = "video_subtitle_color"
private val ENABLE_CALL_ACTIONS = OptionalTelegramFeatureGates.isEnabled(OptionalTelegramFeature.Calls)
private val ENABLE_OPTIONAL_TELEGRAM_APIS =
    OptionalTelegramFeatureGates.isEnabled(OptionalTelegramFeature.PremiumBusinessApis)
private val ENABLE_BOT_WEB_APP_DATA_ACTIONS =
    OptionalTelegramFeatureGates.isEnabled(OptionalTelegramFeature.BotWebAppData)
private const val MESSAGE_PAGE_SIZE = 80
private const val MAX_MESSAGE_PAGE_SIZE = 800
private const val ChatTapLogTag = "AiTelegramChatTap"

data class PreparedOutgoingMedia(
    val localPath: String,
    val kind: MessageKind
)

private fun Context.muteDurationLabel(seconds: Int): String {
    return when (seconds) {
        60 * 60 -> getString(R.string.mute_duration_1h)
        8 * 60 * 60 -> getString(R.string.mute_duration_8h)
        2 * 24 * 60 * 60 -> getString(R.string.mute_duration_2d)
        else -> getString(R.string.mute_duration_forever)
    }
}

private fun Context.autoDeleteDurationLabel(seconds: Int): String {
    return when (seconds) {
        0 -> getString(R.string.auto_delete_duration_off)
        24 * 60 * 60 -> getString(R.string.auto_delete_duration_1d)
        7 * 24 * 60 * 60 -> getString(R.string.auto_delete_duration_1w)
        31 * 24 * 60 * 60 -> getString(R.string.auto_delete_duration_1m)
        else -> resources.getQuantityString(R.plurals.auto_delete_duration_custom, seconds, seconds)
    }
}

private fun Context.slowModeDurationLabel(seconds: Int): String {
    return when (seconds) {
        0 -> getString(R.string.slow_mode_duration_off)
        10 -> getString(R.string.slow_mode_duration_10s)
        30 -> getString(R.string.slow_mode_duration_30s)
        60 -> getString(R.string.slow_mode_duration_1m)
        5 * 60 -> getString(R.string.slow_mode_duration_5m)
        15 * 60 -> getString(R.string.slow_mode_duration_15m)
        60 * 60 -> getString(R.string.slow_mode_duration_1h)
        else -> resources.getQuantityString(R.plurals.slow_mode_duration_custom, seconds, seconds)
    }
}

private fun Context.chatPermissionPresetLabel(preset: TelegramChatPermissionPreset): String {
    return when (preset) {
        TelegramChatPermissionPreset.READ_ONLY -> getString(R.string.chat_permissions_label_read_only)
        TelegramChatPermissionPreset.TEXT_ONLY -> getString(R.string.chat_permissions_label_text_only)
        TelegramChatPermissionPreset.MEDIA_ALLOWED -> getString(R.string.chat_permissions_label_media_allowed)
        TelegramChatPermissionPreset.FULL -> getString(R.string.chat_permissions_label_full)
    }
}

private fun String.toTelegramOperationLabel(): String {
    var label = trim().trimEnd('.')
    val prefixes = listOf(
        "Da gui yeu cau ",
        "Telegram "
    )
    prefixes.forEach { prefix ->
        if (label.startsWith(prefix, ignoreCase = true)) {
            label = label.substring(prefix.length).trim()
        }
    }
    val suffixes = listOf(" requested", " request")
    suffixes.forEach { suffix ->
        if (label.endsWith(suffix, ignoreCase = true)) {
            label = label.dropLast(suffix.length).trim()
        }
    }
    return label.ifBlank { this.trim() }
}

private fun Context.operationPending(messageRes: Int, vararg args: Any): String {
    return getString(R.string.telegram_operation_pending, getString(messageRes, *args).toTelegramOperationLabel())
}

private fun Context.operationSucceeded(messageRes: Int, vararg args: Any): String {
    return getString(R.string.telegram_operation_succeeded, getString(messageRes, *args).toTelegramOperationLabel())
}

fun Context.prepareOutgoingTelegramMedia(
    uri: Uri,
    forcedKind: MessageKind? = null
): PreparedOutgoingMedia {
    require(uri.scheme == "content" || uri.scheme == "file") {
        "Unsupported media source"
    }
    val mimeType = contentResolver.getType(uri).orEmpty()
    contentResolver.sizeBytes(uri)?.let { sizeBytes ->
        require(sizeBytes <= MAX_OUTGOING_MEDIA_BYTES) {
            "File is larger than ${MAX_OUTGOING_MEDIA_BYTES / (1024L * 1024L)} MB"
        }
    }
    val kind = forcedKind ?: when {
        mimeType.startsWith("image/") -> MessageKind.Image
        mimeType.startsWith("video/") -> MessageKind.Video
        mimeType.startsWith("audio/") -> MessageKind.Audio
        else -> MessageKind.File
    }
    val displayName = contentResolver.displayName(uri)
    val extension = displayName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() && it.length <= 12 }
        ?: MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.takeIf { it.isNotBlank() }
    val safeExtension = extension
        ?.filter { it.isLetterOrDigit() }
        ?.takeIf { it.isNotBlank() }
    val safeName = buildString {
        append("upload_")
        append(System.currentTimeMillis())
        append('_')
        append(UUID.randomUUID().toString().replace("-", ""))
        safeExtension?.let {
            append('.')
            append(it)
        }
    }
    val uploadDir = File(cacheDir, "telegram_uploads").apply { mkdirs() }
    uploadDir.cleanupOutgoingUploadCache(
        nowMillis = System.currentTimeMillis(),
        maxBytes = MAX_OUTGOING_UPLOAD_CACHE_BYTES - MAX_OUTGOING_MEDIA_BYTES
    )
    val target = File(uploadDir, safeName)
    try {
        val input = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open selected media")
        input.use { source ->
            target.outputStream().use { output ->
                source.copyToLimit(output, MAX_OUTGOING_MEDIA_BYTES)
            }
        }
    } catch (error: Throwable) {
        runCatching { target.delete() }
        throw error
    }
    return PreparedOutgoingMedia(localPath = target.absolutePath, kind = kind)
}

private fun Context.readableTelegramLocalFilePath(localPath: String): Result<String> {
    val cleanPath = localPath.trim()
    val file = cleanPath.takeIf { it.isNotBlank() }?.let(::File)
    return if (file != null && file.isFile && file.canRead()) {
        Result.success(file.absolutePath)
    } else {
        Result.failure(
            IllegalStateException(getString(R.string.telegram_local_file_missing, cleanPath.ifBlank { "-" }))
        )
    }
}

private fun TelegramMessage.matchesMediaFile(fileId: Int): Boolean {
    return mediaFileId == fileId || mediaThumbnailFileId == fileId
}

private fun TelegramMessage.hasReadableMediaLocalFile(): Boolean {
    return mediaLocalPath
        .takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.let { it.isFile && it.canRead() && it.length() > 0L }
        ?: false
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

private fun android.content.ContentResolver.sizeBytes(uri: Uri): Long? {
    return query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) {
            cursor.getLong(0).takeIf { it >= 0L }
        } else {
            null
        }
    }
}

private fun InputStream.copyToLimit(output: OutputStream, maxBytes: Long) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) return
        copied += read
        if (copied > maxBytes) {
            throw IllegalStateException("File is larger than ${maxBytes / (1024L * 1024L)} MB")
        }
        output.write(buffer, 0, read)
    }
}

private fun File.cleanupOutgoingUploadCache(nowMillis: Long, maxBytes: Long) {
    if (!isDirectory) return
    listFiles()
        ?.asSequence()
        ?.filter { it.isFile }
        ?.filter { file -> nowMillis - file.lastModified() > OUTGOING_UPLOAD_RETENTION_MILLIS }
        ?.forEach { file -> runCatching { file.delete() } }

    var retainedBytes = 0L
    val newestFirst = listFiles()
        ?.asSequence()
        ?.filter { it.isFile }
        ?.sortedByDescending { it.lastModified() }
        ?.toList()
        .orEmpty()
    newestFirst.forEach { file ->
        val fileBytes = file.length().coerceAtLeast(0L)
        if (retainedBytes + fileBytes <= maxBytes.coerceAtLeast(0L)) {
            retainedBytes += fileBytes
        } else {
            runCatching { file.delete() }
        }
    }
}

private fun Context.currentNetworkMode(): NetworkMode {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return NetworkMode.MobileData
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkMode.MobileData
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkMode.Wifi
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) -> NetworkMode.MobileData
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkMode.Roaming
        else -> NetworkMode.MobileData
    }
}

private fun Context.loadBooleanSetting(key: String, defaultValue: Boolean): Boolean {
    return getSharedPreferences(APP_SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getBoolean(key, defaultValue)
}

private fun Context.saveBooleanSetting(key: String, value: Boolean) {
    getSharedPreferences(APP_SETTINGS_PREFS, Context.MODE_PRIVATE).edit {
        putBoolean(key, value)
    }
}

private fun Context.loadStringSetting(key: String, defaultValue: String): String {
    return getSharedPreferences(APP_SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getString(key, defaultValue)
        ?: defaultValue
}

private fun Context.saveStringSetting(key: String, value: String) {
    getSharedPreferences(APP_SETTINGS_PREFS, Context.MODE_PRIVATE).edit {
        putString(key, value)
    }
}

private fun Map<String, TelegramStory>.updateStoriesWithMediaFile(
    file: TelegramMediaFile
): Map<String, TelegramStory> {
    var changed = false
    val updated = mapValues { (_, story) ->
        when (file.id) {
            story.mediaFileId -> {
                changed = true
                story.copy(
                    mediaLocalPath = file.localPath.ifBlank { story.mediaLocalPath }
                )
            }
            story.mediaThumbnailFileId -> {
                changed = true
                story.copy(
                    mediaThumbnailLocalPath = file.localPath.ifBlank { story.mediaThumbnailLocalPath }
                )
            }
            else -> story
        }
    }
    return if (changed) updated else this
}

data class ModelOperationUiState(
    val busy: Boolean = false,
    val messageRes: Int? = null,
    val progress: MlKitModelDownloadProgress? = null,
    val errorMessage: String? = null
)

private fun modelOperationFailureState(
    error: Throwable,
    fallbackMessage: String,
    previousState: ModelOperationUiState
): ModelOperationUiState {
    val message = error.localizedMessage
        ?.takeIf { it.isNotBlank() }
        ?: error.message?.takeIf { it.isNotBlank() }
        ?: previousState.progress?.let { progress ->
            "Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c ${progress.languageDisplayName} (${progress.languageCode}). Ki\u1ec3m tra k\u1ebft n\u1ed1i m\u1ea1ng, Google Play services v\u00e0 th\u1eed l\u1ea1i."
        }
        ?: fallbackMessage

    return previousState.copy(
        busy = false,
        messageRes = null,
        errorMessage = "$message\n${error.javaClass.name}"
    )
}

private fun estimatedLanguagePackSizeMb(models: List<MlKitTranslationModelInfo>): Int {
    return models.size * ESTIMATED_MLKIT_MODEL_SIZE_MB
}

private const val ESTIMATED_MLKIT_MODEL_SIZE_MB = 35

internal fun shouldNotifyIncomingTelegramMessage(
    notificationsEnabled: Boolean,
    notificationPermissionGranted: Boolean,
    isOutgoing: Boolean,
    messageChatId: Long,
    selectedChatId: Long?,
    selectedTab: AppTab,
    isAppForeground: Boolean
): Boolean {
    if (!notificationsEnabled || !notificationPermissionGranted || isOutgoing) return false
    val selectedChatIsVisible = isAppForeground &&
        selectedTab == AppTab.Chats &&
        selectedChatId == messageChatId
    return !selectedChatIsVisible
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramClientApp(
    pendingAndroidEntryIntent: AndroidEntryIntent = AndroidEntryIntent(),
    onAndroidEntryIntentHandled: () -> Unit = {},
    isAppForeground: Boolean = true,
    themeMode: AppThemeMode = AppThemeMode.System,
    onThemeModeChange: (AppThemeMode) -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val appViewModel: TelegramAppViewModel = viewModel()
    val chatRepository = appViewModel.chatRepository
    val senderRepository = appViewModel.senderRepository
    val messageRepository = appViewModel.messageRepository
    val blacklistRepository = appViewModel.blacklistRepository
    val translationCache = appViewModel.translationCacheRepository
    val translationJobRepository = appViewModel.translationJobRepository
    val mlKitModelManager = remember { runCatching { MlKitModelManager() } }
    val commonVietnamesePairs = remember { VietnameseTranslationPairs.common }
    val mediaPolicy = remember { MediaDownloadPolicy() }
    var networkMode by remember { mutableStateOf(context.currentNetworkMode()) }
    val currentNetworkMode by rememberUpdatedState(networkMode)
    val translationQueue = remember {
        TranslationQueue(
            context = context.applicationContext,
            messageRepository = messageRepository,
            translationJobRepository = translationJobRepository,
            preflight = mlKitModelManager.getOrNull()?.let { MlKitTranslationPreflight(it) },
            networkModeProvider = { currentNetworkMode },
            targetLanguage = "vi"
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var donationBillingState by remember { mutableStateOf(DonationBillingState()) }
    val donationBillingManager = remember {
        if (BuildConfig.ENABLE_PLAY_BILLING) {
            DonationBillingManager(context) { state -> donationBillingState = state }
        } else {
            null
        }
    }
    var selectedTab by remember { mutableStateOf(AppTab.Chats) }
    var selectedChatId by remember { mutableStateOf<Long?>(null) }
    var messagePageSize by rememberSaveable(selectedChatId) { mutableIntStateOf(MESSAGE_PAGE_SIZE) }
    var transientOpenedChats by remember { mutableStateOf<Map<Long, TelegramChat>>(emptyMap()) }
    var pendingPrivateChatOpen by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var pendingNotificationChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingSharedText by remember { mutableStateOf("") }
    var pendingSharedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showStatusDetails by remember { mutableStateOf(false) }
    var translatedOnly by rememberSaveable {
        mutableStateOf(context.loadBooleanSetting(PREF_TRANSLATED_ONLY, false))
    }
    var allowAdultContent by rememberSaveable {
        mutableStateOf(context.loadBooleanSetting(PREF_ALLOW_ADULT_CONTENT, true))
    }
    var notificationsEnabled by rememberSaveable {
        mutableStateOf(context.loadBooleanSetting(PREF_NOTIFICATIONS_ENABLED, true))
    }
    var autoPlayVideo by rememberSaveable {
        mutableStateOf(context.loadBooleanSetting(PREF_AUTO_PLAY_VIDEO, true))
    }
    var videoSubtitlesEnabled by rememberSaveable {
        mutableStateOf(context.loadBooleanSetting(PREF_VIDEO_SUBTITLES_ENABLED, true))
    }
    var videoSourceLanguageName by rememberSaveable {
        mutableStateOf(context.loadStringSetting(PREF_VIDEO_SOURCE_LANGUAGE, VideoSourceLanguage.Auto.name))
    }
    val videoSourceLanguage = remember(videoSourceLanguageName) {
        VideoSourceLanguage.fromName(videoSourceLanguageName)
    }
    var videoSubtitleColorName by rememberSaveable {
        mutableStateOf(context.loadStringSetting(PREF_VIDEO_SUBTITLE_COLOR, VideoSubtitleColor.Yellow.name))
    }
    val videoSubtitleColor = remember(videoSubtitleColorName) {
        VideoSubtitleColor.entries.firstOrNull { it.name == videoSubtitleColorName } ?: VideoSubtitleColor.Yellow
    }
    var tdLibStatus by remember { mutableStateOf<TdLibStatus>(TdLibStatus.Starting) }
    var tdLibRestartToken by remember { mutableIntStateOf(0) }
    var telegramOperationMessage by remember { mutableStateOf<String?>(null) }
    val requestedMediaFileIds = remember(tdLibRestartToken) { mutableSetOf<Int>() }
    val requestedThumbnailFileIds = remember(tdLibRestartToken) { mutableSetOf<Int>() }
    val requestedVideoPrefixFileIds = remember(tdLibRestartToken) { mutableSetOf<Int>() }
    val requestedHistoryPages = remember(tdLibRestartToken) { mutableSetOf<String>() }
    val requestedChatMetadataIds = remember(tdLibRestartToken) { mutableSetOf<Long>() }
    val requestedAutoHistoryChatIds = remember(tdLibRestartToken) { mutableSetOf<Long>() }
    var queuedViewedTranslationUid by remember(tdLibRestartToken) { mutableStateOf<String?>(null) }
    val cachedTranslationUids = remember(tdLibRestartToken) { mutableSetOf<String>() }
    val chats by appViewModel.chats.collectAsState(initial = emptyList())
    val senders by appViewModel.senders.collectAsState(initial = emptyList())
    val contacts by appViewModel.contacts.collectAsState(initial = emptyList())
    val selectedChatMessagesFlow = remember(selectedChatId) {
        selectedChatId?.let { appViewModel.observeMessagesForChat(it) } ?: flowOf(emptyList<TelegramMessage>())
    }
    val messages by selectedChatMessagesFlow.collectAsState(initial = emptyList())
    val hiddenItems by appViewModel.hiddenContent.collectAsState(initial = emptyList())
    val hiddenHashes = remember(hiddenItems) { hiddenItems.map { it.contentHash }.toSet() }
    var telegramCalls by remember { mutableStateOf<Map<Int, TelegramCall>>(emptyMap()) }
    var telegramStories by remember { mutableStateOf<Map<String, TelegramStory>>(emptyMap()) }
    var telegramBotCommands by remember { mutableStateOf<Map<Long, List<TelegramBotCommand>>>(emptyMap()) }
    var telegramBotMenuButtons by remember { mutableStateOf<Map<Long, TelegramBotMenuButton>>(emptyMap()) }
    var telegramInlineBotResults by remember { mutableStateOf<TelegramInlineBotResults?>(null) }
    var telegramWebAppUrl by remember { mutableStateOf<TelegramWebAppUrl?>(null) }
    var telegramPrivacyRules by remember { mutableStateOf<Map<String, TelegramPrivacyRuleSummary>>(emptyMap()) }
    var telegramActiveSessions by remember { mutableStateOf<List<TelegramActiveSession>>(emptyList()) }
    var telegramPremiumFeatures by remember { mutableStateOf<List<TelegramPremiumFeatureInfo>>(emptyList()) }
    var telegramPremiumLimits by remember { mutableStateOf<Map<String, TelegramPremiumLimitSummary>>(emptyMap()) }
    var telegramStarBalance by remember { mutableStateOf<TelegramStarBalance?>(null) }
    var telegramStarTransactions by remember { mutableStateOf<List<TelegramStarTransaction>>(emptyList()) }
    var telegramBusinessLinks by remember { mutableStateOf<Map<String, TelegramBusinessChatLink>>(emptyMap()) }
    val telegramNotifications = remember(context) { TelegramNotificationManager(context) }
    val telegramPushRegistrar = remember(context) { TelegramPushRegistrar(context) }
    var notificationPermissionGranted by remember {
        mutableStateOf(TelegramNotificationManager.hasPostNotificationsPermission(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
        if (!granted) {
            notificationsEnabled = false
            context.saveBooleanSetting(PREF_NOTIFICATIONS_ENABLED, false)
        }
    }
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationPermissionGranted = true
        }
    }
    var cacheStats by remember { mutableStateOf<ai.telegram.android.data.TranslationCacheStats?>(null) }
    var mlKitModels by remember { mutableStateOf<List<MlKitTranslationModelInfo>>(emptyList()) }
    var modelOperationState by remember { mutableStateOf(ModelOperationUiState()) }
    fun mlKitModelManagerOrNull(): MlKitModelManager? {
        val manager = mlKitModelManager.getOrNull()
        if (manager == null) {
            val error = mlKitModelManager.exceptionOrNull()
                ?: IllegalStateException(resources.getString(R.string.model_operation_failed))
            modelOperationState = modelOperationFailureState(
                error = error,
                fallbackMessage = resources.getString(R.string.model_operation_failed),
            previousState = modelOperationState
            )
        }
        return manager
    }
    val tdLibClientRef = remember { AtomicReference<TelegramClient?>(null) }
    fun requestChatMetadata(chatId: Long) {
        if (chatId == 0L) return
        val client = tdLibClientRef.get() ?: return
        if (requestedChatMetadataIds.add(chatId)) {
            client.loadChat(chatId)
        }
    }
    fun requestInitialChatHistory(chatId: Long, force: Boolean = false) {
        if (chatId == 0L) return
        coroutineScope.launch {
            val client = tdLibClientRef.get() ?: return@launch
            if (force || appViewModel.shouldLoadInitialHistory(chatId)) {
                client.loadChatHistory(chatId)
            }
        }
    }
    fun requestOlderChatHistory(chatId: Long, fromMessageId: Long) {
        if (chatId == 0L || fromMessageId <= 0L) return
        coroutineScope.launch {
            val client = tdLibClientRef.get() ?: return@launch
            if (appViewModel.shouldLoadOlderHistory(chatId, fromMessageId)) {
                client.loadChatHistory(chatId = chatId, fromMessageId = fromMessageId)
            }
        }
    }
    fun rememberTransientOpenedChat(chat: TelegramChat) {
        transientOpenedChats = if (chat.isMainList) {
            transientOpenedChats - chat.id
        } else {
            val existing = transientOpenedChats[chat.id]
            val displayChat = if (existing != null && chat.title.isBlank()) {
                existing.copy(
                    unreadCount = chat.unreadCount,
                    lastMessagePreview = chat.lastMessagePreview.ifBlank { existing.lastMessagePreview },
                    updatedAtMillis = chat.updatedAtMillis,
                    activeAction = chat.activeAction.ifBlank { existing.activeAction },
                    lastReadInboxMessageId = maxOf(existing.lastReadInboxMessageId, chat.lastReadInboxMessageId),
                    lastReadOutboxMessageId = maxOf(existing.lastReadOutboxMessageId, chat.lastReadOutboxMessageId),
                    pinnedMessageId = chat.pinnedMessageId.takeIf { it > 0L } ?: existing.pinnedMessageId
                )
            } else {
                chat
            }
            (transientOpenedChats + (chat.id to displayChat))
                .entries
                .sortedByDescending { it.value.updatedAtMillis }
                .take(20)
                .associate { it.key to it.value }
        }
    }
    val callMediaEngine = remember(context) {
        TelegramCallMediaEngine(
            context = context.applicationContext,
            onSignalingData = { signalingData ->
                tdLibClientRef.get()?.sendCallSignalingData(signalingData.callId, signalingData.data)
            },
            onError = { message ->
                coroutineScope.launch {
                    telegramOperationMessage = resources.getString(R.string.telegram_operation_failed, message)
                }
            }
        )
    }
    val tdLibConfig = remember(tdLibRestartToken) { TdLibConfig.from(context) }
    val tdLibClient: TelegramClient = remember(tdLibConfig, callMediaEngine) {
        TdLibReflectionClient(
            config = tdLibConfig,
            onStatus = { status -> coroutineScope.launch { tdLibStatus = status } },
            onChat = { chat ->
                coroutineScope.launch {
                    rememberTransientOpenedChat(chat)
                    chatRepository.upsert(chat)
                    if (chat.title.isBlank()) {
                        requestChatMetadata(chat.id)
                    }
                    val pendingOpen = pendingPrivateChatOpen
                    if (pendingOpen != null && chat.matchesPendingPrivateChat(pendingOpen)) {
                        pendingPrivateChatOpen = null
                        pendingNotificationChatId = chat.id
                        selectedTab = AppTab.Chats
                        selectedChatId = chat.id
                        requestInitialChatHistory(chat.id)
                    }
                }
            },
            onSender = { sender -> coroutineScope.launch { senderRepository.upsert(sender) } },
            onContactsLoaded = {
                coroutineScope.launch {
                    telegramOperationMessage = context.operationSucceeded(R.string.telegram_action_contacts_requested)
                }
            },
            onMessage = { message ->
                coroutineScope.launch {
                    appViewModel.upsertIncomingMessage(message, message.pendingPreview(context))
                    if (
                        shouldNotifyIncomingTelegramMessage(
                            notificationsEnabled = notificationsEnabled,
                            notificationPermissionGranted = notificationPermissionGranted,
                            isOutgoing = message.isOutgoing,
                            messageChatId = message.chatId,
                            selectedChatId = selectedChatId,
                            selectedTab = selectedTab,
                            isAppForeground = isAppForeground
                        )
                    ) {
                        telegramNotifications.notifyNewMessage(message)
                    }
                }
            },
            onMessagesDeleted = { chatId, messageIds ->
                coroutineScope.launch {
                    appViewModel.deleteMessages(chatId, messageIds)
                }
            },
            onReadState = { update ->
                coroutineScope.launch {
                    if (update.outgoing) {
                        appViewModel.markOutboxRead(update.chatId, update.lastReadMessageId)
                    } else {
                        appViewModel.markInboxRead(update.chatId, update.lastReadMessageId, update.unreadCount)
                        if (update.unreadCount <= 0) {
                            telegramNotifications.cancelChat(update.chatId)
                        }
                    }
                }
            },
            onMessageSync = { update ->
                coroutineScope.launch {
                    appViewModel.updateMessageSyncState(
                        chatId = update.chatId,
                        messageId = update.messageId,
                        state = update.state,
                        isRead = update.isRead
                    )
                }
            },
            onMessageContent = { update ->
                coroutineScope.launch {
                    appViewModel.updateMessageContent(update.chatId, update.messageId, update.text)
                }
            },
            onChatHistoryLoaded = { historyLoad ->
                coroutineScope.launch {
                    appViewModel.recordHistoryLoaded(
                        chatId = historyLoad.chatId,
                        fromMessageId = historyLoad.fromMessageId,
                        messageIds = historyLoad.messageIds,
                        requestedLimit = historyLoad.requestedLimit
                    )
                }
            },
            onChatAction = { update ->
                coroutineScope.launch {
                    appViewModel.updateChatAction(update.chatId, update.action)
                }
            },
            onPinnedMessage = { update ->
                coroutineScope.launch {
                    appViewModel.updatePinnedMessage(update.chatId, update.messageId)
                }
            },
            onFile = { file ->
                coroutineScope.launch {
                    appViewModel.updateMediaFile(file)
                    telegramStories = telegramStories.updateStoriesWithMediaFile(file)
                    cacheStats = appViewModel.cacheStats(languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels))
                }
            },
            onCall = { call ->
                coroutineScope.launch {
                    telegramCalls = telegramCalls + (call.id to call)
                    val callerName = senders.firstOrNull { it.telegramUserIdOrNull() == call.userId }?.displayName
                    if (!call.isOutgoing && call.state == TelegramCallState.Pending && ENABLE_CALL_ACTIONS) {
                        if (notificationsEnabled && notificationPermissionGranted) {
                            telegramNotifications.notifyIncomingCall(call, callerName)
                        } else {
                            telegramNotifications.cancelIncomingCall(call.id)
                        }
                        selectedTab = AppTab.Calls
                    } else {
                        telegramNotifications.cancelIncomingCall(call.id)
                    }
                    telegramOperationMessage = if (call.state == TelegramCallState.Error) {
                        resources.getString(
                            R.string.telegram_operation_failed,
                            call.errorMessage.ifBlank { call.stateLabel.ifBlank { call.state.name } }
                        )
                    } else {
                        null
                    }
                }
                coroutineScope.launch(Dispatchers.IO) {
                    if (ENABLE_CALL_ACTIONS) {
                        callMediaEngine.handleCallUpdate(call)
                    } else {
                        callMediaEngine.stop(call.id)
                    }
                }
            },
            onCallSignalingData = { signalingData ->
                coroutineScope.launch(Dispatchers.IO) {
                    callMediaEngine.handleSignalingData(signalingData)
                }
            },
            onStory = { story ->
                coroutineScope.launch {
                    val key = "${story.chatId}:${story.id}"
                    telegramStories = if (story.state == TelegramStoryState.Deleted) {
                        telegramStories - key
                    } else {
                        telegramStories + (key to story)
                    }
                    telegramOperationMessage = context.operationSucceeded(
                        if (story.state == TelegramStoryState.Deleted) {
                            R.string.telegram_action_delete_story_requested
                        } else {
                            R.string.telegram_action_load_stories_requested
                        }
                    )
                }
            },
            onBotCommands = { botUserId, commands ->
                coroutineScope.launch {
                    telegramBotCommands = telegramBotCommands + (botUserId to commands)
                    telegramOperationMessage = context.operationSucceeded(R.string.telegram_action_load_bot_commands_requested)
                }
            },
            onBotMenuButton = { menu ->
                coroutineScope.launch {
                    telegramBotMenuButtons = telegramBotMenuButtons + (menu.botUserId to menu)
                    telegramOperationMessage = context.operationSucceeded(R.string.telegram_action_load_bot_menu_requested)
                }
            },
            onInlineBotResults = { results ->
                coroutineScope.launch {
                    telegramInlineBotResults = results
                    telegramOperationMessage = context.operationSucceeded(R.string.telegram_action_inline_query_requested)
                }
            },
            onWebAppUrl = { webAppUrl ->
                coroutineScope.launch {
                    telegramWebAppUrl = webAppUrl
                    telegramOperationMessage = context.operationSucceeded(R.string.telegram_action_open_bot_web_app_requested)
                }
            },
            onPrivacyRule = { rule ->
                coroutineScope.launch {
                    telegramPrivacyRules = telegramPrivacyRules + (rule.setting.name to rule)
                    telegramOperationMessage = context.operationSucceeded(R.string.telegram_action_load_privacy_requested)
                }
            },
            onActiveSessions = { sessions ->
                coroutineScope.launch {
                    telegramActiveSessions = sessions
                    telegramOperationMessage = context.operationSucceeded(R.string.telegram_action_load_sessions_requested)
                }
            },
            onPremiumFeatures = { features ->
                coroutineScope.launch {
                    telegramPremiumFeatures = features
                }
            },
            onPremiumLimit = { limit ->
                coroutineScope.launch {
                    telegramPremiumLimits = telegramPremiumLimits + (limit.type.name to limit)
                }
            },
            onStarBalance = { balance ->
                coroutineScope.launch {
                    telegramStarBalance = balance
                }
            },
            onStarTransactions = { transactions ->
                coroutineScope.launch {
                    telegramStarTransactions = transactions
                }
            },
            onBusinessChatLinks = { links ->
                coroutineScope.launch {
                    telegramBusinessLinks = telegramBusinessLinks + links.associateBy { it.link }
                }
            },
            onOpenChat = { chatId ->
                coroutineScope.launch {
                    selectedTab = AppTab.Chats
                    selectedChatId = chatId
                }
            },
            onChatInviteLink = { _, inviteLink ->
                coroutineScope.launch {
                    telegramOperationMessage = context.operationSucceeded(
                        R.string.telegram_action_chat_invite_link_created,
                        inviteLink
                    )
                }
            },
            onOperationError = { message ->
                coroutineScope.launch {
                    telegramOperationMessage = resources.getString(R.string.telegram_operation_failed, message)
                }
            },
            callProtocolProvider = { callMediaEngine.protocol() }
        )
    }

    LaunchedEffect(Unit) {
        appViewModel.initializeSeedData(seedSamples = !tdLibConfig.isConfigured)
        mlKitModelManager.getOrNull()?.let { manager ->
            mlKitModels = runCatching { manager.downloadedTranslationModels() }
                .getOrDefault(emptyList())
        }
        cacheStats = appViewModel.cacheStats(languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels))
        appViewModel.scheduleMaintenance()
        telegramNotifications.ensureMessageChannel()
        donationBillingManager?.start()
    }

    LaunchedEffect(tdLibStatus, notificationsEnabled, notificationPermissionGranted) {
        if (
            tdLibStatus == TdLibStatus.Ready &&
            notificationsEnabled &&
            !notificationPermissionGranted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            requestNotificationPermission()
        }
    }

    LaunchedEffect(tdLibStatus, tdLibClient) {
        if (tdLibStatus == TdLibStatus.Ready) {
            telegramPushRegistrar.registerIfAvailable(tdLibClient)
            tdLibClient.loadContacts()
        }
    }

    LaunchedEffect(tdLibClient) {
        tdLibClientRef.set(tdLibClient)
        tdLibClient.start()
    }

    LaunchedEffect(selectedChatId, tdLibStatus) {
        if (tdLibStatus == TdLibStatus.Ready) {
            selectedChatId?.let { chatId -> requestInitialChatHistory(chatId) }
        }
    }

    LaunchedEffect(chats, tdLibStatus) {
        if (tdLibStatus != TdLibStatus.Ready) return@LaunchedEffect
        chats.asSequence()
            .filter { it.title.isBlank() }
            .take(100)
            .forEach { chat -> requestChatMetadata(chat.id) }
        chats.asSequence()
            .filter { it.title.isNotBlank() && it.isChannelChat() }
            .take(24)
            .forEach { chat ->
                if (requestedAutoHistoryChatIds.add(chat.id)) {
                    requestInitialChatHistory(chat.id)
                }
            }
    }

    DisposableEffect(donationBillingManager) {
        onDispose { donationBillingManager?.close() }
    }

    DisposableEffect(tdLibClient) {
        onDispose {
            tdLibClientRef.compareAndSet(tdLibClient, null)
            tdLibClient.close()
        }
    }

    DisposableEffect(callMediaEngine) {
        onDispose { callMediaEngine.close() }
    }

    val chatManagementActions = ChatManagementActions(
        onSetChatTitle = { chatId, title ->
            tdLibClient.setChatTitle(chatId, title)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_set_chat_title_requested)
        },
        onSetChatDescription = { chatId, description ->
            tdLibClient.setChatDescription(chatId, description)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_set_chat_description_requested)
        },
        onSetChatAutoDeleteTime = { chatId, seconds ->
            tdLibClient.setChatMessageAutoDeleteTime(chatId, seconds)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_set_auto_delete_requested, context.autoDeleteDurationLabel(seconds))
        },
        onSetChatSlowMode = { chatId, seconds ->
            tdLibClient.setChatSlowModeDelay(chatId, seconds)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_set_slow_mode_requested, context.slowModeDurationLabel(seconds))
        },
        onSetChatPermissions = { chatId, preset ->
            tdLibClient.setChatPermissions(chatId, preset)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_set_chat_permissions_requested, context.chatPermissionPresetLabel(preset))
        },
        onCreateChatInviteLink = { chatId, name ->
            tdLibClient.createChatInviteLink(chatId, name)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_create_invite_link_requested)
        },
        onRevokeChatInviteLink = { chatId, inviteLink ->
            tdLibClient.revokeChatInviteLink(chatId, inviteLink)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_revoke_invite_link_requested)
        },
        onAddChatMember = { chatId, userId ->
            tdLibClient.addChatMember(chatId, userId)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_add_chat_member_requested)
        },
        onRemoveChatMember = { chatId, userId ->
            tdLibClient.removeChatMember(chatId, userId)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_remove_chat_member_requested)
        },
        onBanChatMember = { chatId, userId ->
            tdLibClient.banChatMember(chatId, userId)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_ban_chat_member_requested)
        },
        onUnbanChatMember = { chatId, userId ->
            tdLibClient.unbanChatMember(chatId, userId)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_unban_chat_member_requested)
        },
        onPromoteChatMember = { chatId, userId, adminTitle ->
            tdLibClient.promoteChatMember(chatId, userId, adminTitle)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_promote_chat_member_requested)
        },
        onDemoteChatMember = { chatId, userId ->
            tdLibClient.demoteChatMember(chatId, userId)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_demote_chat_member_requested)
        },
        onPinChat = { chatId ->
            tdLibClient.pinChat(chatId, pinned = true)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_pin_requested)
        },
        onUnpinChat = { chatId ->
            tdLibClient.pinChat(chatId, pinned = false)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_unpin_requested)
        },
        onUnpinAllMessages = { chatId ->
            tdLibClient.unpinAllChatMessages(chatId)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_unpin_all_messages_requested)
        },
        onMuteChat = { chatId, muteForSeconds ->
            tdLibClient.muteChat(chatId, muteForSeconds)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_mute_duration_requested, context.muteDurationLabel(muteForSeconds))
        },
        onUnmuteChat = { chatId ->
            tdLibClient.unmuteChat(chatId)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_unmute_requested)
        },
        onArchiveChat = { chatId ->
            tdLibClient.archiveChat(chatId)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_archive_requested)
        },
        onUnarchiveChat = { chatId ->
            tdLibClient.archiveChat(chatId, archived = false)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_unarchive_requested)
        },
        onMarkChatRead = { chatId ->
            tdLibClient.markChatRead(chatId)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_mark_read_requested)
        },
        onMarkChatUnread = { chatId ->
            tdLibClient.markChatUnread(chatId, markedUnread = true)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_mark_unread_requested)
        },
        onClearChatUnreadMark = { chatId ->
            tdLibClient.markChatUnread(chatId, markedUnread = false)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_clear_unread_mark_requested)
        },
        onClearChatHistory = { chatId ->
            tdLibClient.clearChatHistory(chatId)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_clear_chat_history_requested)
        },
        onLeaveChat = { chatId ->
            tdLibClient.leaveChat(chatId)
            tdLibClient.loadChat(chatId)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_leave_chat_requested)
        }
    )

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkMode = context.currentNetworkMode()
            }

            override fun onLost(network: Network) {
                networkMode = context.currentNetworkMode()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                networkMode = context.currentNetworkMode()
            }
        }
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        onDispose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }

    LaunchedEffect(chats) {
        val currentSelectedChatId = selectedChatId
        if (
            currentSelectedChatId != null &&
            chats.none { it.id == currentSelectedChatId } &&
            pendingNotificationChatId != currentSelectedChatId
        ) {
            selectedChatId = null
        }
        val pendingChatId = pendingNotificationChatId
        if (pendingChatId != null && chats.any { it.id == pendingChatId }) {
            pendingNotificationChatId = null
        }
    }

    LaunchedEffect(pendingAndroidEntryIntent.id) {
        val entryIntent = pendingAndroidEntryIntent
        entryIntent.shortcut?.let { shortcut ->
            selectedChatId = null
            selectedTab = when (shortcut) {
                AndroidShortcutTarget.Chats -> AppTab.Chats
                AndroidShortcutTarget.Search -> AppTab.Discover
                AndroidShortcutTarget.Settings -> AppTab.Settings
                AndroidShortcutTarget.Cache -> AppTab.Cache
            }
        }
        entryIntent.openChatId?.let { chatId ->
            pendingNotificationChatId = chatId
            selectedTab = AppTab.Chats
            selectedChatId = chatId
            telegramNotifications.cancelChat(chatId)
            requestInitialChatHistory(chatId)
        }
        if (entryIntent.openCalls) {
            selectedChatId = null
            selectedTab = AppTab.Calls
        }
        if (entryIntent.telegramLink.isNotBlank()) {
            selectedTab = AppTab.Chats
            tdLibClient.openTelegramLink(entryIntent.telegramLink)
            telegramOperationMessage = context.operationPending(R.string.telegram_action_open_link_requested)
        }
        if (entryIntent.hasShare) {
            selectedTab = AppTab.Chats
            pendingSharedText = entryIntent.sharedText
            pendingSharedUris = entryIntent.sharedUris
            telegramOperationMessage = if (selectedChatId == null) {
                resources.getString(R.string.android_share_select_chat)
            } else {
                resources.getString(R.string.android_share_added_to_composer)
            }
        }
        if (
            entryIntent.openChatId != null ||
            entryIntent.openCalls ||
            entryIntent.telegramLink.isNotBlank() ||
            entryIntent.hasShare ||
            entryIntent.shortcut != null
        ) {
            onAndroidEntryIntentHandled()
        }
    }

    LaunchedEffect(selectedChatId) {
        selectedChatId?.let(telegramNotifications::cancelChat)
    }

    LaunchedEffect(messages) {
        messages.forEach { message ->
            val uid = message.uid()
            val readableText = MessagePrivacyPolicy.readableTranslatedText(message)
            if (
                message.translationStatus == TranslationStatus.Ready &&
                message.originalText.isNotBlank() &&
                readableText.isNotBlank() &&
                cachedTranslationUids.add(uid)
            ) {
                translationCache.getOrPut(message.originalText, "vi") { readableText }
            }
        }
        cacheStats = appViewModel.cacheStats(languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels))
    }

    LaunchedEffect(messages, tdLibStatus, networkMode) {
        if (tdLibStatus != TdLibStatus.Ready) return@LaunchedEffect
        messages.forEach { message ->
            val thumbnailFileId = message.mediaThumbnailFileId
            if (
                networkMode != NetworkMode.Roaming &&
                thumbnailFileId > 0 &&
                message.mediaThumbnailLocalPath.isBlank() &&
                requestedThumbnailFileIds.add(thumbnailFileId) &&
                appViewModel.shouldRequestMedia(thumbnailFileId)
            ) {
                appViewModel.markMediaRequested(thumbnailFileId, MessageKind.Image)
                tdLibClient.downloadFile(thumbnailFileId, priority = 32)
            }

            val fileId = message.mediaFileId
            if (
                fileId > 0 &&
                !message.hasReadableMediaLocalFile() &&
                message.kind != MessageKind.Text
            ) {
                when (message.kind) {
                    MessageKind.Image -> {
                        val decision = mediaPolicy.decide(networkMode, message.kind, message.mediaSizeMb)
                        if (
                            decision.autoDownload &&
                            requestedMediaFileIds.add(fileId) &&
                            appViewModel.shouldRequestMedia(fileId)
                        ) {
                            appViewModel.markMediaRequested(fileId, message.kind)
                            tdLibClient.downloadFile(fileId, priority = 24)
                        }
                    }
                    MessageKind.Video -> Unit
                    MessageKind.File,
                    MessageKind.Voice,
                    MessageKind.VideoNote,
                    MessageKind.Audio,
                    MessageKind.Sticker,
                    MessageKind.Text -> Unit
                }
            }
        }
    }

    var openSettingsFromStatusDialog by remember { mutableStateOf(false) }

    LaunchedEffect(openSettingsFromStatusDialog) {
        if (openSettingsFromStatusDialog) {
            selectedChatId = null
            selectedTab = AppTab.Settings
            openSettingsFromStatusDialog = false
        }
    }

    LaunchedEffect(telegramOperationMessage) {
        val message = telegramOperationMessage?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short
        )
    }

    if (showStatusDetails) {
        AlertDialog(
            onDismissRequest = { showStatusDetails = false },
            title = { Text(tdLibStatus.topBarLabel()) },
            text = { Text(tdLibStatus.statusDetailText()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStatusDetails = false
                        openSettingsFromStatusDialog = true
                    }
                ) {
                    Text(stringResource(R.string.nav_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStatusDetails = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AiTelegramHeader(
                tdLibStatus = tdLibStatus,
                selectedTab = selectedTab,
                unreadCount = chats.sumOf { it.unreadCount },
                unreadChannelCount = chats.count { it.isChannelChat() && it.unreadCount > 0 },
                contactCount = contacts.size,
                onMenuClick = {
                    selectedChatId = null
                    selectedTab = AppTab.Menu
                },
                onCreateClick = {
                    selectedChatId = null
                    selectedTab = AppTab.Groups
                },
                onSearchClick = {
                    selectedChatId = null
                    selectedTab = AppTab.Discover
                },
                onStatusClick = { showStatusDetails = true },
                onTabSelected = { tab ->
                    if (tab != AppTab.Chats) {
                        selectedChatId = null
                    }
                    selectedTab = tab
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clipToBounds(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (selectedTab) {
                AppTab.Chats -> ChatScreen(
                    chats = chats,
                    selectedChatId = selectedChatId,
                    selectedChatOverride = selectedChatId?.let(transientOpenedChats::get),
                    messages = messages,
                    pendingSharedText = pendingSharedText,
                    pendingSharedUris = pendingSharedUris,
                    translatedOnly = translatedOnly,
                    allowAdultContent = allowAdultContent,
                    autoPlayVideo = autoPlayVideo,
                    videoSubtitlesEnabled = videoSubtitlesEnabled,
                    videoSourceLanguage = videoSourceLanguage,
                    videoSubtitleColor = videoSubtitleColor,
                    onSelectChat = { chat ->
                        Log.d(
                            ChatTapLogTag,
                            "TelegramClientApp onSelectChat chatId=${chat.id} previousSelectedChatId=$selectedChatId"
                        )
                        selectedChatId = chat.id
                        Log.d(ChatTapLogTag, "TelegramClientApp selectedChatId updated to $selectedChatId")
                        requestInitialChatHistory(chat.id)
                    },
                    onBackToChats = { selectedChatId = null },
                    hiddenHashes = hiddenHashes,
                    onCurrentMessageViewed = { message ->
                        coroutineScope.launch {
                            val uid = message.uid()
                            var queuedWork = false
                            if (message.shouldTranslateWhenViewed() && queuedViewedTranslationUid != uid) {
                                when (translationQueue.enqueueOnly(message)) {
                                    TranslationQueueResult.Queued -> {
                                        queuedViewedTranslationUid = uid
                                        queuedWork = true
                                    }
                                    is TranslationQueueResult.UpdatedOnly,
                                    is TranslationQueueResult.Skipped -> {
                                        queuedViewedTranslationUid = uid
                                    }
                                    is TranslationQueueResult.Blocked -> Unit
                                }
                            }
                            val videoFileId = message.mediaFileId
                            if (
                                tdLibStatus == TdLibStatus.Ready &&
                                networkMode == NetworkMode.Wifi &&
                                message.kind == MessageKind.Video &&
                                videoFileId > 0 &&
                                !message.hasReadableMediaLocalFile()
                            ) {
                                val shouldWarmFullVideo = message.mediaSizeMb in 1..VIDEO_WIFI_AUTO_DOWNLOAD_MAX_MB
                                if (shouldWarmFullVideo && requestedMediaFileIds.add(videoFileId)) {
                                    appViewModel.markFullMediaDownloadRequested(videoFileId, message.kind)
                                    tdLibClient.downloadFile(fileId = videoFileId, priority = 30, limitBytes = 0)
                                    queuedWork = true
                                } else if (
                                    !shouldWarmFullVideo &&
                                    requestedVideoPrefixFileIds.add(videoFileId) &&
                                    appViewModel.shouldRequestMedia(videoFileId)
                                ) {
                                    appViewModel.markMediaRequested(videoFileId, message.kind)
                                    tdLibClient.downloadFile(
                                        fileId = videoFileId,
                                        priority = 32,
                                        limitBytes = VIDEO_READY_PREFIX_BYTES
                                    )
                                    queuedWork = true
                                }
                            }
                            if (queuedWork) {
                                cacheStats = appViewModel.cacheStats(languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels))
                            }
                        }
                    },
                    onOpenCache = { selectedTab = AppTab.Cache },
                    onSendMessage = { chatId, text, options ->
                        tdLibClient.sendTextMessage(chatId, text, options)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_send_message_requested)
                    },
                    onSendReplyMessage = { chatId, replyToMessageId, text, options ->
                        tdLibClient.sendReplyTextMessage(chatId, replyToMessageId, text, options)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_reply_requested)
                    },
                    onSendMedia = { chatId, composerMedia, caption, options ->
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    context.prepareOutgoingTelegramMedia(
                                        uri = composerMedia.uri,
                                        forcedKind = composerMedia.kind
                                    )
                                }
                            }
                            result.onSuccess { media ->
                                tdLibClient.sendMediaMessage(
                                    chatId = chatId,
                                    localPath = media.localPath,
                                    kind = media.kind,
                                    caption = caption,
                                    options = options,
                                    mediaOptions = MediaSendOptions(
                                        highQualityPhoto = composerMedia.highQualityPhoto && media.kind == MessageKind.Image
                                    )
                                )
                                telegramOperationMessage = context.operationPending(R.string.telegram_action_send_media_requested)
                            }.onFailure { error ->
                                telegramOperationMessage = resources.getString(
                                    R.string.telegram_action_send_media_failed,
                                    error.localizedMessage ?: error.javaClass.simpleName
                                )
                            }
                        }
                    },
                    onSendMediaAlbum = { chatId, composerMedia, caption, options ->
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    composerMedia.take(10).mapIndexed { index, item ->
                                        val prepared = context.prepareOutgoingTelegramMedia(
                                            uri = item.uri,
                                            forcedKind = item.kind
                                        )
                                        TelegramOutgoingMedia(
                                            localPath = prepared.localPath,
                                            kind = prepared.kind,
                                            caption = if (index == 0) caption else "",
                                            mediaOptions = MediaSendOptions(
                                                highQualityPhoto = item.highQualityPhoto && prepared.kind == MessageKind.Image
                                            )
                                        )
                                    }
                                }
                            }
                            result.onSuccess { media ->
                                tdLibClient.sendMediaAlbum(
                                    chatId = chatId,
                                    media = media,
                                    options = options
                                )
                                telegramOperationMessage = context.operationPending(R.string.telegram_action_send_media_requested)
                            }.onFailure { error ->
                                telegramOperationMessage = resources.getString(
                                    R.string.telegram_action_send_media_failed,
                                    error.localizedMessage ?: error.javaClass.simpleName
                                )
                            }
                        }
                    },
                    onSendPoll = { chatId, question, pollOptions, isAnonymous, allowMultipleAnswers, sendOptions ->
                        tdLibClient.sendPollMessage(
                            chatId = chatId,
                            question = question,
                            options = pollOptions,
                            isAnonymous = isAnonymous,
                            allowMultipleAnswers = allowMultipleAnswers,
                            sendOptions = sendOptions
                        )
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_send_poll_requested)
                    },
                    onSendContact = { chatId, firstName, lastName, phoneNumber, options ->
                        tdLibClient.sendContactMessage(
                            chatId = chatId,
                            firstName = firstName,
                            lastName = lastName,
                            phoneNumber = phoneNumber,
                            options = options
                        )
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_send_contact_requested)
                    },
                    onSetDraft = { chatId, text ->
                        tdLibClient.setChatDraftMessage(chatId, text)
                    },
                    onClearDraft = { chatId ->
                        tdLibClient.clearChatDraftMessage(chatId)
                    },
                    onEditMessage = { message, text ->
                        tdLibClient.editTextMessage(message.chatId, message.id, text)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_edit_requested)
                    },
                    onDeleteMessage = { message ->
                        tdLibClient.deleteMessages(message.chatId, listOf(message.id), revoke = true)
                        coroutineScope.launch {
                            appViewModel.deleteMessages(message.chatId, listOf(message.id))
                        }
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_delete_requested)
                    },
                    onForwardMessage = { message, targetChatId ->
                        tdLibClient.forwardMessages(
                            toChatId = targetChatId,
                            fromChatId = message.chatId,
                            messageIds = listOf(message.id)
                        )
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_forward_requested)
                    },
                    onDeleteMessages = { chatId, messageIds ->
                        tdLibClient.deleteMessages(chatId, messageIds, revoke = true)
                        coroutineScope.launch {
                            appViewModel.deleteMessages(chatId, messageIds)
                        }
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_delete_requested)
                    },
                    onForwardMessages = { fromChatId, messageIds, targetChatId ->
                        tdLibClient.forwardMessages(
                            toChatId = targetChatId,
                            fromChatId = fromChatId,
                            messageIds = messageIds
                        )
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_forward_requested)
                    },
                    onResendMessage = { message ->
                        tdLibClient.resendMessages(message.chatId, listOf(message.id))
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_resend_requested)
                    },
                    onPinMessage = { message ->
                        tdLibClient.pinMessage(message.chatId, message.id)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_pin_message_requested)
                    },
                    onUnpinMessage = { message ->
                        tdLibClient.unpinMessage(message.chatId, message.id)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_unpin_message_requested)
                    },
                    onReactToMessage = { message, emoji ->
                        tdLibClient.addMessageReaction(message.chatId, message.id, emoji)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_reaction_requested)
                    },
                    onLoadOlderMessages = { chatId, fromMessageId ->
                        messagePageSize = (messagePageSize + MESSAGE_PAGE_SIZE).coerceAtMost(MAX_MESSAGE_PAGE_SIZE)
                        val key = "$chatId:$fromMessageId"
                        if (fromMessageId > 0 && requestedHistoryPages.add(key)) {
                            requestOlderChatHistory(chatId = chatId, fromMessageId = fromMessageId)
                        }
                    },
                    onSearchChatMessages = { chatId, searchQuery, filter ->
                        tdLibClient.searchChatMessages(chatId, searchQuery, filter)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_search_messages_requested)
                    },
                    onSearchPublicPosts = { searchQuery, filter ->
                        tdLibClient.searchPublicPosts(searchQuery, filter)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_search_public_posts_requested)
                    },
                    onRefreshChats = {
                        tdLibClient.loadMainChatList()
                        chats.take(24).forEach { chat -> requestInitialChatHistory(chat.id, force = true) }
                        telegramOperationMessage = context.operationPending(R.string.refresh)
                    },
                    onOpenTelegramLink = { url ->
                        tdLibClient.openTelegramLink(url)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_open_link_requested)
                    },
                    onPendingShareConsumed = {
                        pendingSharedText = ""
                        pendingSharedUris = emptyList()
                        telegramOperationMessage = resources.getString(R.string.android_share_added_to_composer)
                    },
                    onDownloadMedia = { fileId, kind ->
                        coroutineScope.launch {
                            val messageForFile = messages.firstOrNull { message -> message.matchesMediaFile(fileId) }
                            if (!appViewModel.shouldRequestFullMediaDownload(fileId, kind)) return@launch
                            appViewModel.markFullMediaDownloadRequested(fileId, kind)
                            if (messageForFile != null) {
                                tdLibClient.downloadMessageMedia(
                                    chatId = messageForFile.chatId,
                                    messageId = messageForFile.id,
                                    priority = 32,
                                    limitBytes = 0
                                )
                            } else {
                                tdLibClient.downloadFile(fileId, priority = 32, limitBytes = 0)
                            }
                            telegramOperationMessage = context.operationPending(R.string.telegram_action_download_media_requested)
                        }
                    },
                    onRetryTranslation = { message ->
                        coroutineScope.launch {
                            when (translationQueue.enqueueOnly(message)) {
                                TranslationQueueResult.Queued -> {
                                    cacheStats = appViewModel.cacheStats(languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels))
                                    telegramOperationMessage = context.operationPending(R.string.translation_retry_requested)
                                }
                                is TranslationQueueResult.UpdatedOnly -> {
                                    cacheStats = appViewModel.cacheStats(languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels))
                                }
                                is TranslationQueueResult.Skipped,
                                is TranslationQueueResult.Blocked -> Unit
                            }
                        }
                    },
                    onRestartMediaDownload = { message ->
                        if (message.mediaFileId > 0) {
                            coroutineScope.launch {
                                appViewModel.markFullMediaDownloadRequested(message.mediaFileId, message.kind)
                                tdLibClient.cancelDownloadFile(message.mediaFileId, onlyIfPending = true)
                                delay(250L)
                                tdLibClient.downloadMessageMedia(
                                    chatId = message.chatId,
                                    messageId = message.id,
                                    priority = 32,
                                    limitBytes = 0
                                )
                            }
                            telegramOperationMessage = context.operationPending(R.string.telegram_action_download_media_requested)
                        }
                    },
                    onHide = {
                        coroutineScope.launch {
                            appViewModel.hideMessage(it)
                            cacheStats = appViewModel.cacheStats(languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels))
                        }
                    },
                    onUnhide = {
                        coroutineScope.launch {
                            appViewModel.unhideMessage(it)
                            cacheStats = appViewModel.cacheStats(languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels))
                        }
                    }
                )
                AppTab.Menu -> MenuScreen(
                    onSelect = { selectedTab = it }
                )
                AppTab.Channels -> ChannelDirectoryScreen(
                    channels = chats.filter { it.isChannelChat() },
                    operationMessage = telegramOperationMessage,
                    onOpenChat = { chat ->
                        selectedTab = AppTab.Chats
                        selectedChatId = chat.id
                        requestInitialChatHistory(chat.id)
                    },
                    onRefreshChannels = {
                        tdLibClient.loadMainChatList()
                        chats.filter { it.isChannelChat() }.take(24).forEach { chat -> requestInitialChatHistory(chat.id, force = true) }
                        telegramOperationMessage = context.operationPending(R.string.refresh)
                    },
                    onSearchChannels = {
                        tdLibClient.searchPublicChats(it)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_search_requested)
                    },
                    onJoinChannel = {
                        tdLibClient.joinChannel(it)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_join_requested)
                    },
                    onCreateChannel = { title, description ->
                        tdLibClient.createChannel(title, description)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_create_channel_requested)
                    },
                    managementActions = chatManagementActions
                )
                AppTab.Contacts -> ContactsScreen(
                    contacts = contacts,
                    operationMessage = telegramOperationMessage,
                    onRefreshContacts = {
                        tdLibClient.loadContacts()
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_contacts_requested)
                    },
                    onOpenPrivateChat = { sender ->
                        sender.telegramUserIdOrNull()?.let { userId ->
                            pendingPrivateChatOpen = userId to sender.displayName
                            pendingNotificationChatId = userId
                            selectedTab = AppTab.Chats
                            selectedChatId = userId
                            tdLibClient.createPrivateChat(userId)
                            tdLibClient.loadChat(userId)
                            requestInitialChatHistory(userId)
                            telegramOperationMessage = context.operationPending(R.string.telegram_action_create_chat_requested)
                        }
                    }
                )
                AppTab.AccountPrivacy -> AccountPrivacyScreen(
                    privacyRules = telegramPrivacyRules.values.sortedBy { it.setting.name },
                    activeSessions = telegramActiveSessions.sortedWith(
                        compareByDescending<TelegramActiveSession> { it.isCurrent }.thenByDescending { it.lastActiveDate }
                    ),
                    operationMessage = telegramOperationMessage,
                    onUpdateProfile = { firstName, lastName, bio, username ->
                        tdLibClient.updateProfile(firstName, lastName, bio, username)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_update_profile_requested)
                    },
                    onSetProfilePhoto = { localPath ->
                        context.readableTelegramLocalFilePath(localPath).onSuccess { path ->
                            tdLibClient.setProfilePhoto(path)
                            telegramOperationMessage = context.operationPending(R.string.telegram_action_set_profile_photo_requested)
                        }.onFailure { error ->
                            telegramOperationMessage = resources.getString(
                                R.string.telegram_operation_failed,
                                error.localizedMessage ?: error.javaClass.simpleName
                            )
                        }
                    },
                    onLoadPrivacy = { setting ->
                        tdLibClient.loadPrivacySetting(setting)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_load_privacy_requested)
                    },
                    onSetPrivacy = { setting, preset ->
                        tdLibClient.setPrivacySetting(setting, preset)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_set_privacy_requested)
                    },
                    onLoadSessions = {
                        tdLibClient.loadActiveSessions()
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_load_sessions_requested)
                    },
                    onTerminateSession = { sessionId ->
                        tdLibClient.terminateSession(sessionId)
                        tdLibClient.loadActiveSessions()
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_terminate_session_requested)
                    },
                    onTerminateAllOtherSessions = {
                        tdLibClient.terminateAllOtherSessions()
                        tdLibClient.loadActiveSessions()
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_terminate_other_sessions_requested)
                    }
                )
                AppTab.PremiumBusiness -> PremiumBusinessScreen(
                    premiumFeatures = telegramPremiumFeatures,
                    premiumLimits = telegramPremiumLimits.values.sortedBy { it.type.name },
                    starBalance = telegramStarBalance,
                    starTransactions = telegramStarTransactions,
                    businessLinks = telegramBusinessLinks.values.sortedBy { it.link },
                    operationMessage = telegramOperationMessage,
                    optionalActionsEnabled = ENABLE_OPTIONAL_TELEGRAM_APIS,
                    onLoadPremiumFeatures = {
                        tdLibClient.loadPremiumFeatures()
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_load_premium_features_requested)
                    },
                    onViewPremiumFeature = { feature ->
                        tdLibClient.viewPremiumFeature(feature)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_view_premium_feature_requested)
                    },
                    onLoadPremiumLimit = { limitType ->
                        tdLibClient.loadPremiumLimit(limitType)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_load_premium_limit_requested)
                    },
                    onLoadStarBalance = {
                        tdLibClient.loadStarBalance()
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_load_star_balance_requested)
                    },
                    onLoadStarTransactions = { offset, limit ->
                        tdLibClient.loadStarTransactions(offset, limit)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_load_star_transactions_requested)
                    },
                    onLoadBusinessLinks = {
                        tdLibClient.loadBusinessChatLinks()
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_load_business_links_requested)
                    },
                    onCreateBusinessLink = { message ->
                        tdLibClient.createBusinessChatLink(message)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_create_business_link_requested)
                    },
                    onDeleteBusinessLink = { link ->
                        tdLibClient.deleteBusinessChatLink(link)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_delete_business_link_requested)
                    }
                )
                AppTab.Stories -> StoriesScreen(
                    stories = telegramStories.values.sortedWith(
                        compareByDescending<TelegramStory> { it.dateMillis }.thenByDescending { it.id }
                    ),
                    chats = chats,
                    operationMessage = telegramOperationMessage,
                    onLoadActiveStories = { chatId ->
                        tdLibClient.loadActiveStories(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_load_stories_requested)
                    },
                    onOpenStory = { chatId, storyId ->
                        tdLibClient.loadStory(chatId, storyId)
                        tdLibClient.viewStory(chatId, storyId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_open_story_requested)
                    },
                    onPostStory = { chatId, localPath, kind, caption ->
                        context.readableTelegramLocalFilePath(localPath).onSuccess { path ->
                            tdLibClient.postStory(chatId, path, kind, caption)
                            telegramOperationMessage = context.operationPending(R.string.telegram_action_post_story_requested)
                        }.onFailure { error ->
                            telegramOperationMessage = resources.getString(
                                R.string.telegram_operation_failed,
                                error.localizedMessage ?: error.javaClass.simpleName
                            )
                        }
                    },
                    onDeleteStory = { chatId, storyId ->
                        tdLibClient.deleteStory(chatId, storyId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_delete_story_requested)
                    },
                    onRefreshStories = {
                        chats.filterNot { it.isSecretChat() }.take(24).forEach { chat -> tdLibClient.loadActiveStories(chat.id) }
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_load_stories_requested)
                    },
                    onDownloadStoryMedia = { fileId, kind ->
                        tdLibClient.downloadFile(fileId, priority = 32, limitBytes = 0)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_download_media_requested)
                    }
                )
                AppTab.Bots -> BotPlatformScreen(
                    chats = chats,
                    commands = telegramBotCommands.values.flatten().sortedBy { it.command },
                    menuButtons = telegramBotMenuButtons.values.sortedBy { it.botUserId },
                    inlineResults = telegramInlineBotResults,
                    webAppUrl = telegramWebAppUrl,
                    operationMessage = telegramOperationMessage,
                    webAppDataActionsEnabled = ENABLE_BOT_WEB_APP_DATA_ACTIONS,
                    onSearchBot = { username ->
                        tdLibClient.searchBot(username)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_search_bot_requested)
                    },
                    onRefreshBots = {
                        tdLibClient.loadMainChatList()
                        chats.filter { chat -> chat.type == "private" || chat.title.contains("bot", ignoreCase = true) }
                            .take(24)
                            .forEach { chat -> requestInitialChatHistory(chat.id, force = true) }
                        telegramOperationMessage = context.operationPending(R.string.refresh)
                    },
                    onStartBot = { botUserId, chatId, parameter ->
                        tdLibClient.startBot(botUserId, chatId, parameter)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_start_bot_requested)
                    },
                    onLoadCommands = { botUserId ->
                        tdLibClient.loadBotCommands(botUserId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_load_bot_commands_requested)
                    },
                    onLoadMenuButton = { botUserId ->
                        tdLibClient.loadBotMenuButton(botUserId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_load_bot_menu_requested)
                    },
                    onInlineQuery = { botUserId, chatId, query, offset ->
                        tdLibClient.requestInlineBotResults(botUserId, chatId, query, offset)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_inline_query_requested)
                    },
                    onSendInlineResult = { chatId, queryId, resultId, hideViaBot ->
                        tdLibClient.sendInlineBotResult(chatId, queryId, resultId, hideViaBot)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_send_inline_result_requested)
                    },
                    onClickCallback = { chatId, messageId, payload ->
                        tdLibClient.clickBotCallbackButton(chatId, messageId, payload)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_click_bot_callback_requested)
                    },
                    onOpenWebApp = { botUserId, chatId, url ->
                        tdLibClient.openBotWebApp(botUserId, chatId, url)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_open_bot_web_app_requested)
                    },
                    onSendWebAppData = { botUserId, buttonText, data ->
                        tdLibClient.sendWebAppData(botUserId, buttonText, data)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_send_web_app_data_requested)
                    }
                )
                AppTab.Calls -> CallsScreen(
                    calls = telegramCalls.values.sortedByDescending { it.id },
                    contacts = contacts,
                    operationMessage = telegramOperationMessage,
                    callActionsEnabled = ENABLE_CALL_ACTIONS && callMediaEngine.isAvailable,
                    onStartCall = { userId, isVideo ->
                        tdLibClient.createCall(userId, isVideo)
                        telegramOperationMessage = context.operationPending(
                            if (isVideo) {
                                R.string.telegram_action_create_video_call_requested
                            } else {
                                R.string.telegram_action_create_voice_call_requested
                            }
                        )
                    },
                    onAcceptCall = { callId ->
                        tdLibClient.acceptCall(callId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_accept_call_requested)
                    },
                    onDiscardCall = { callId ->
                        tdLibClient.discardCall(callId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_discard_call_requested)
                    },
                    onRefreshContacts = {
                        tdLibClient.loadContacts()
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_contacts_requested)
                    }
                )
                AppTab.SecretChats -> SecretChatDirectoryScreen(
                    secretChats = chats.filter { it.isSecretChat() },
                    contacts = contacts,
                    operationMessage = telegramOperationMessage,
                    onOpenChat = { chat ->
                        selectedTab = AppTab.Chats
                        selectedChatId = chat.id
                        requestInitialChatHistory(chat.id)
                    },
                    onCreateSecretChat = { userId ->
                        tdLibClient.createSecretChat(userId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_create_secret_chat_requested)
                    },
                    onRefreshContacts = {
                        tdLibClient.loadContacts()
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_contacts_requested)
                    },
                    onRefreshSecretChats = {
                        tdLibClient.loadMainChatList()
                        chats.filter { it.isSecretChat() }.take(24).forEach { chat -> requestInitialChatHistory(chat.id, force = true) }
                        telegramOperationMessage = context.operationPending(R.string.refresh)
                    }
                )
                AppTab.Groups -> GroupDirectoryScreen(
                    groups = chats.filter { it.isGroupChat() },
                    operationMessage = telegramOperationMessage,
                    onOpenChat = { chat ->
                        selectedTab = AppTab.Chats
                        selectedChatId = chat.id
                        requestInitialChatHistory(chat.id)
                    },
                    onRefreshGroups = {
                        tdLibClient.loadMainChatList()
                        chats.filter { it.isGroupChat() }.take(24).forEach { chat -> requestInitialChatHistory(chat.id, force = true) }
                        telegramOperationMessage = context.operationPending(R.string.refresh)
                    },
                    onCreateGroup = { userIds, title ->
                        tdLibClient.createBasicGroupChat(userIds, title)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_create_group_requested)
                    },
                    onCreateForumGroup = { title, description ->
                        tdLibClient.createForumGroup(title, description)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_create_forum_group_requested)
                    },
                    managementActions = chatManagementActions
                )
                AppTab.Discover -> TelegramToolsScreen(
                    chats = chats,
                    contacts = senders.filter { it.type == "user" },
                    operationMessage = telegramOperationMessage,
                    onGlobalSearch = {
                        tdLibClient.searchChats(it)
                        tdLibClient.searchPublicChats(it)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_search_requested)
                    },
                    onJoinChannel = {
                        tdLibClient.joinChannel(it)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_join_requested)
                    },
                    onSendMessage = { chatId, text ->
                        tdLibClient.sendTextMessage(chatId, text)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_send_message_requested)
                    },
                    onCreatePrivateChat = { userId ->
                        tdLibClient.createPrivateChat(userId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_create_chat_requested)
                    },
                    onCreateSecretChat = { userId ->
                        tdLibClient.createSecretChat(userId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_create_secret_chat_requested)
                    },
                    onBlockUser = { userId ->
                        tdLibClient.setUserBlocked(userId, blocked = true)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_block_user_requested)
                    },
                    onUnblockUser = { userId ->
                        tdLibClient.setUserBlocked(userId, blocked = false)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_unblock_user_requested)
                    },
                    onAddContact = { userId, firstName, lastName, phoneNumber ->
                        tdLibClient.addContact(userId, firstName, lastName, phoneNumber)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_add_contact_requested)
                    },
                    onDeleteContact = { userId ->
                        tdLibClient.deleteContact(userId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_delete_contact_requested)
                    },
                    onCreateGroup = { userIds, title ->
                        tdLibClient.createBasicGroupChat(userIds, title)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_create_group_requested)
                    },
                    onCreateChannel = { title, description ->
                        tdLibClient.createChannel(title, description)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_create_channel_requested)
                    },
                    onCreateForumGroup = { title, description ->
                        tdLibClient.createForumGroup(title, description)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_create_forum_group_requested)
                    },
                    onSetChatTitle = { chatId, title ->
                        tdLibClient.setChatTitle(chatId, title)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_set_chat_title_requested)
                    },
                    onSetChatDescription = { chatId, description ->
                        tdLibClient.setChatDescription(chatId, description)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_set_chat_description_requested)
                    },
                    onSetChatAutoDeleteTime = { chatId, seconds ->
                        tdLibClient.setChatMessageAutoDeleteTime(chatId, seconds)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(
                            R.string.telegram_action_set_auto_delete_requested,
                            context.autoDeleteDurationLabel(seconds)
                        )
                    },
                    onSetChatSlowMode = { chatId, seconds ->
                        tdLibClient.setChatSlowModeDelay(chatId, seconds)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(
                            R.string.telegram_action_set_slow_mode_requested,
                            context.slowModeDurationLabel(seconds)
                        )
                    },
                    onSetChatPermissions = { chatId, preset ->
                        tdLibClient.setChatPermissions(chatId, preset)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(
                            R.string.telegram_action_set_chat_permissions_requested,
                            context.chatPermissionPresetLabel(preset)
                        )
                    },
                    onCreateChatInviteLink = { chatId, name ->
                        tdLibClient.createChatInviteLink(chatId, name)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_create_invite_link_requested)
                    },
                    onRevokeChatInviteLink = { chatId, inviteLink ->
                        tdLibClient.revokeChatInviteLink(chatId, inviteLink)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_revoke_invite_link_requested)
                    },
                    onAddChatMember = { chatId, userId ->
                        tdLibClient.addChatMember(chatId, userId)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_add_chat_member_requested)
                    },
                    onRemoveChatMember = { chatId, userId ->
                        tdLibClient.removeChatMember(chatId, userId)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_remove_chat_member_requested)
                    },
                    onBanChatMember = { chatId, userId ->
                        tdLibClient.banChatMember(chatId, userId)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_ban_chat_member_requested)
                    },
                    onUnbanChatMember = { chatId, userId ->
                        tdLibClient.unbanChatMember(chatId, userId)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_unban_chat_member_requested)
                    },
                    onSetChatDraft = { chatId, text ->
                        tdLibClient.setChatDraftMessage(chatId, text)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_set_chat_draft_requested)
                    },
                    onClearChatDraft = { chatId ->
                        tdLibClient.clearChatDraftMessage(chatId)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_clear_chat_draft_requested)
                    },
                    onPinChat = { chatId ->
                        tdLibClient.pinChat(chatId, pinned = true)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_pin_requested)
                    },
                    onUnpinChat = { chatId ->
                        tdLibClient.pinChat(chatId, pinned = false)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_unpin_requested)
                    },
                    onUnpinAllMessages = { chatId ->
                        tdLibClient.unpinAllChatMessages(chatId)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_unpin_all_messages_requested)
                    },
                    onMuteChat = { chatId, muteForSeconds ->
                        tdLibClient.muteChat(chatId, muteForSeconds)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(
                            R.string.telegram_action_mute_duration_requested,
                            context.muteDurationLabel(muteForSeconds)
                        )
                    },
                    onUnmuteChat = { chatId ->
                        tdLibClient.unmuteChat(chatId)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_unmute_requested)
                    },
                    onArchiveChat = { chatId ->
                        tdLibClient.archiveChat(chatId)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_archive_requested)
                    },
                    onUnarchiveChat = { chatId ->
                        tdLibClient.archiveChat(chatId, archived = false)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_unarchive_requested)
                    },
                    onMarkChatRead = { chatId ->
                        tdLibClient.markChatRead(chatId)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_mark_read_requested)
                    },
                    onMarkChatUnread = { chatId ->
                        tdLibClient.markChatUnread(chatId, markedUnread = true)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_mark_unread_requested)
                    },
                    onClearChatUnreadMark = { chatId ->
                        tdLibClient.markChatUnread(chatId, markedUnread = false)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_clear_unread_mark_requested)
                    },
                    onClearChatHistory = { chatId ->
                        tdLibClient.clearChatHistory(chatId)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_clear_chat_history_requested)
                    },
                    onLeaveChat = { chatId ->
                        tdLibClient.leaveChat(chatId)
                        tdLibClient.loadChat(chatId)
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_leave_chat_requested)
                    },
                    onOpenCache = { selectedTab = AppTab.Cache },
                    onRefreshContacts = {
                        tdLibClient.loadContacts()
                        telegramOperationMessage = context.operationPending(R.string.telegram_action_contacts_requested)
                    }
                )
                AppTab.Blacklist -> BlacklistScreen(
                    items = hiddenItems,
                    version = hiddenItems.size,
                    onRemove = {
                        coroutineScope.launch { blacklistRepository.remove(it) }
                    }
                )
                AppTab.Cache -> CacheScreen(
                    stats = cacheStats ?: ai.telegram.android.data.TranslationCacheStats(
                        translationItems = 0,
                        translationSizeMb = 0,
                        mediaSizeMb = 0,
                        languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels)
                    ),
                    mediaPolicy = mediaPolicy,
                    models = mlKitModels,
                    commonPairs = commonVietnamesePairs,
                    modelOperationState = modelOperationState,
                    onRefreshModels = {
                        coroutineScope.launch {
                            val manager = mlKitModelManagerOrNull() ?: return@launch
                            modelOperationState = ModelOperationUiState(
                                busy = true,
                                messageRes = R.string.model_refreshing
                            )
                            val result = runCatching { manager.downloadedTranslationModels() }
                            mlKitModels = result.getOrDefault(emptyList())
                            cacheStats = appViewModel.cacheStats(languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels))
                            modelOperationState = result.fold(
                                onSuccess = { ModelOperationUiState() },
                                onFailure = {
                                    modelOperationFailureState(
                                        error = it,
                                        fallbackMessage = resources.getString(R.string.model_operation_failed),
                                        previousState = modelOperationState
                                    )
                                }
                            )
                        }
                    },
                    onDownloadAllCommonPairs = {
                        coroutineScope.launch {
                            val manager = mlKitModelManagerOrNull() ?: return@launch
                            modelOperationState = ModelOperationUiState(
                                busy = true,
                                messageRes = R.string.model_downloading
                            )
                            val result = runCatching {
                                manager.downloadTranslationPairs(commonVietnamesePairs) { progress ->
                                    modelOperationState = modelOperationState.copy(progress = progress)
                                }
                                manager.downloadedTranslationModels()
                            }
                            mlKitModels = result.getOrDefault(mlKitModels)
                            cacheStats = appViewModel.cacheStats(languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels))
                            modelOperationState = result.fold(
                                onSuccess = { ModelOperationUiState() },
                                onFailure = {
                                    modelOperationFailureState(
                                        error = it,
                                        fallbackMessage = resources.getString(R.string.model_operation_failed),
                                        previousState = modelOperationState
                                    )
                                }
                            )
                        }
                    },
                    onDownloadPair = { pair ->
                        coroutineScope.launch {
                            val manager = mlKitModelManagerOrNull() ?: return@launch
                            modelOperationState = ModelOperationUiState(
                                busy = true,
                                messageRes = R.string.model_downloading
                            )
                            val result = runCatching {
                                manager.downloadTranslationPair(pair) { progress ->
                                    modelOperationState = modelOperationState.copy(progress = progress)
                                }
                                manager.downloadedTranslationModels()
                            }
                            mlKitModels = result.getOrDefault(mlKitModels)
                            cacheStats = appViewModel.cacheStats(languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels))
                            modelOperationState = result.fold(
                                onSuccess = { ModelOperationUiState() },
                                onFailure = {
                                    modelOperationFailureState(
                                        error = it,
                                        fallbackMessage = resources.getString(R.string.model_operation_failed),
                                        previousState = modelOperationState
                                    )
                                }
                            )
                        }
                    },
                    onDeleteModel = { languageCode ->
                        coroutineScope.launch {
                            val manager = mlKitModelManagerOrNull() ?: return@launch
                            modelOperationState = ModelOperationUiState(
                                busy = true,
                                messageRes = R.string.model_deleting
                            )
                            val result = runCatching {
                                manager.deleteTranslationModel(languageCode)
                                manager.downloadedTranslationModels()
                            }
                            mlKitModels = result.getOrDefault(mlKitModels)
                            cacheStats = appViewModel.cacheStats(languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels))
                            modelOperationState = result.fold(
                                onSuccess = { ModelOperationUiState() },
                                onFailure = {
                                    modelOperationFailureState(
                                        error = it,
                                        fallbackMessage = resources.getString(R.string.model_operation_failed),
                                        previousState = modelOperationState
                                    )
                                }
                            )
                        }
                    },
                    onClear = { scope ->
                        coroutineScope.launch {
                            val result = when (scope) {
                                CacheClearScope.All -> appViewModel.clearCache()
                                CacheClearScope.Translation -> appViewModel.clearTranslationCache()
                                CacheClearScope.Media -> appViewModel.clearMediaCache()
                                CacheClearScope.VideoSubtitles -> appViewModel.clearVideoSubtitleCache()
                                CacheClearScope.TemporaryFiles -> appViewModel.clearTemporaryFiles()
                            }
                            cacheStats = appViewModel.cacheStats(languagePackSizeMb = estimatedLanguagePackSizeMb(mlKitModels))
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message = resources.getString(
                                    R.string.cache_clear_complete,
                                    result.totalItems,
                                    result.freedSizeMb
                                ),
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )
                AppTab.Donate -> DonateScreen(
                    billingState = donationBillingState,
                    showPlayBilling = BuildConfig.ENABLE_PLAY_BILLING,
                    showVietQrDonation = BuildConfig.ENABLE_VIETQR_DONATION,
                    onRefresh = {
                        coroutineScope.launch { donationBillingManager?.refreshProducts() }
                    },
                    onDonate = { product ->
                        (context as? Activity)?.let { donationBillingManager?.launchDonation(it, product) }
                    }
                )
                AppTab.Settings -> {
                    if (tdLibStatus != TdLibStatus.Ready) {
                        TelegramAuthScreen(
                            tdLibStatus = tdLibStatus,
                            onSendPhone = { tdLibClient.setPhoneNumber(it) },
                            onSendCode = { tdLibClient.checkCode(it) },
                            onSendPassword = { tdLibClient.checkPassword(it) },
                            onResendCode = { tdLibClient.resendAuthenticationCode() },
                            onResetTdLibData = {
                                tdLibStatus = TdLibStatus.Starting
                                tdLibClient.close()
                                TdLibConfig.resetLocalData(context)
                                tdLibRestartToken += 1
                            }
                        )
                    } else {
                        SettingsScreen(
                            tdLibStatus = tdLibStatus,
                            translatedOnly = translatedOnly,
                            onTranslatedOnlyChange = {
                                translatedOnly = it
                                context.saveBooleanSetting(PREF_TRANSLATED_ONLY, it)
                            },
                            allowAdultContent = allowAdultContent,
                            onAllowAdultContentChange = {
                                allowAdultContent = it
                                context.saveBooleanSetting(PREF_ALLOW_ADULT_CONTENT, it)
                            },
                            notificationsEnabled = notificationsEnabled,
                            onNotificationsEnabledChange = { enabled ->
                                notificationsEnabled = enabled
                                context.saveBooleanSetting(PREF_NOTIFICATIONS_ENABLED, enabled)
                                if (
                                    enabled &&
                                    !notificationPermissionGranted &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    requestNotificationPermission()
                                }
                            },
                            notificationPermissionGranted = notificationPermissionGranted,
                            onRequestNotificationPermission = { requestNotificationPermission() },
                            autoPlayVideo = autoPlayVideo,
                            onAutoPlayVideoChange = {
                                autoPlayVideo = it
                                context.saveBooleanSetting(PREF_AUTO_PLAY_VIDEO, it)
                            },
                            videoSubtitlesEnabled = videoSubtitlesEnabled,
                            onVideoSubtitlesEnabledChange = {
                                videoSubtitlesEnabled = it
                                context.saveBooleanSetting(PREF_VIDEO_SUBTITLES_ENABLED, it)
                            },
                            videoSourceLanguage = videoSourceLanguage,
                            onVideoSourceLanguageChange = {
                                videoSourceLanguageName = it.name
                                context.saveStringSetting(PREF_VIDEO_SOURCE_LANGUAGE, it.name)
                            },
                            videoSubtitleColor = videoSubtitleColor,
                            onVideoSubtitleColorChange = {
                                videoSubtitleColorName = it.name
                                context.saveStringSetting(PREF_VIDEO_SUBTITLE_COLOR, it.name)
                            },
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            onSendPhone = { tdLibClient.setPhoneNumber(it) },
                            onSendCode = { tdLibClient.checkCode(it) },
                            onSendPassword = { tdLibClient.checkPassword(it) },
                            onResendCode = { tdLibClient.resendAuthenticationCode() },
                            onSignOut = {
                                tdLibStatus = TdLibStatus.Starting
                                tdLibClient.logOut()
                                coroutineScope.launch {
                                    selectedChatId = null
                                    appViewModel.clearTelegramData()
                                    tdLibClient.close()
                                    TdLibConfig.resetLocalData(context)
                                    tdLibRestartToken += 1
                                }
                            },
                            onResetTdLibData = {
                                tdLibStatus = TdLibStatus.Starting
                                tdLibClient.close()
                                TdLibConfig.resetLocalData(context)
                                tdLibRestartToken += 1
                            },
                            chatCount = chats.size,
                            contactCount = contacts.size
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(if (selected) 10.dp else 7.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
    )
}

@Composable
fun AiTelegramHeader(
    tdLibStatus: TdLibStatus,
    selectedTab: AppTab,
    unreadCount: Int,
    unreadChannelCount: Int,
    contactCount: Int,
    onMenuClick: () -> Unit,
    onCreateClick: () -> Unit,
    onSearchClick: () -> Unit,
    onStatusClick: () -> Unit,
    onTabSelected: (AppTab) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 2.dp, end = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderIconButton(
                    iconRes = R.drawable.ic_ai_menu,
                    contentDescription = stringResource(R.string.nav_menu),
                    modifier = Modifier.testTag("header_menu"),
                    onClick = onMenuClick
                )
                Text(
                    text = "AI Telegram",
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                HeaderIconButton(
                    iconRes = R.drawable.ic_ai_plus,
                    contentDescription = stringResource(R.string.create_chat),
                    modifier = Modifier.testTag("header_create"),
                    onClick = onCreateClick
                )
                HeaderIconButton(
                    iconRes = R.drawable.ic_ai_search,
                    contentDescription = stringResource(R.string.global_search),
                    modifier = Modifier.testTag("header_search"),
                    onClick = onSearchClick
                )
                TopBarStatusPill(
                    tdLibStatus = tdLibStatus,
                    onClick = onStatusClick
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                primaryTabs.forEach { tab ->
                    HeaderTabButton(
                        tab = tab,
                        selected = selectedTab == tab,
                        badgeText = when (tab) {
                            AppTab.Chats -> unreadCount.takeIf { it > 0 }?.toCompactBadge()
                            AppTab.Channels -> unreadChannelCount.takeIf { it > 0 }?.toCompactBadge()
                            AppTab.Contacts -> contactCount.takeIf { it > 0 }?.toCompactBadge(limit = 999)
                            else -> null
                        },
                        badgeCritical = tab != AppTab.Contacts,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("header_tab_${tab.name}")
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(modifier = modifier, onClick = onClick) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(30.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HeaderTabButton(
    tab: AppTab,
    selected: Boolean,
    badgeText: String?,
    badgeCritical: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabLabel = stringResource(tab.labelRes)
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(50.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(tab.navigationIconRes()),
                contentDescription = tabLabel,
                modifier = Modifier.size(30.dp),
                tint = contentColor
            )
            if (!badgeText.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    color = if (badgeCritical) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = CircleShape
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = AiThemeTokens.BadgePaddingHorizontal, vertical = AiThemeTokens.BadgePaddingVertical),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (badgeCritical) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        maxLines = 1
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                    }
                )
        )
    }
}

private fun Int.toCompactBadge(limit: Int = 99): String {
    return if (this > limit) "$limit+" else toString()
}

private fun TelegramChat.matchesPendingPrivateChat(pending: Pair<Long, String>): Boolean {
    val (userId, displayName) = pending
    return type.equals("private", ignoreCase = true) &&
        (id == userId || (displayName.isNotBlank() && title.equals(displayName, ignoreCase = true)))
}

@Composable
private fun TopBarStatusPill(tdLibStatus: TdLibStatus, onClick: () -> Unit) {
    val active = tdLibStatus == TdLibStatus.Ready
    Surface(
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .padding(end = AiThemeTokens.SectionSpacing)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AiThemeTokens.CompactRowPaddingHorizontal, vertical = AiThemeTokens.SmallSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing)
        ) {
            Box(
                modifier = Modifier
                    .size(AiThemeTokens.StatusDotSize)
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
            )
            Text(
                text = tdLibStatus.topBarLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TdLibStatus.statusDetailText(): String {
    return when (this) {
        TdLibStatus.Ready -> stringResource(R.string.tdlib_status_ready)
        TdLibStatus.Starting -> stringResource(R.string.tdlib_status_starting)
        TdLibStatus.WaitingForPhoneNumber -> stringResource(R.string.tdlib_status_waiting_phone)
        is TdLibStatus.WaitingForCode -> if (canResendCode) {
            stringResource(R.string.tdlib_status_waiting_code_can_resend)
        } else {
            stringResource(R.string.tdlib_status_waiting_code)
        }
        is TdLibStatus.WaitingForPassword -> stringResource(R.string.tdlib_status_waiting_password)
        TdLibStatus.NotConfigured -> stringResource(R.string.tdlib_setup_missing_config)
        TdLibStatus.BindingMissing -> stringResource(R.string.tdlib_setup_binding_missing)
        TdLibStatus.Closed -> stringResource(R.string.tdlib_status_closed)
        is TdLibStatus.Error -> stringResource(R.string.tdlib_status_error, message)
    }
}

@Composable
private fun TdLibStatus.topBarLabel(): String {
    return when (this) {
        TdLibStatus.Ready -> stringResource(R.string.status_ready_short)
        TdLibStatus.Starting -> stringResource(R.string.status_connecting_short)
        TdLibStatus.WaitingForPhoneNumber,
        is TdLibStatus.WaitingForCode,
        is TdLibStatus.WaitingForPassword -> stringResource(R.string.status_login_short)
        TdLibStatus.NotConfigured,
        TdLibStatus.BindingMissing,
        TdLibStatus.Closed,
        is TdLibStatus.Error -> stringResource(R.string.status_attention_short)
    }
}

fun AppTab.navigationIconRes(): Int {
    return when (this) {
        AppTab.Chats -> R.drawable.ic_ai_chat
        AppTab.Channels -> R.drawable.ic_ai_channel
        AppTab.Contacts -> R.drawable.ic_ai_contacts
        AppTab.Menu -> R.drawable.ic_ai_menu
        AppTab.AccountPrivacy -> R.drawable.ic_ai_shield
        AppTab.PremiumBusiness -> R.drawable.ic_ai_donate
        AppTab.Stories -> R.drawable.ic_ai_story
        AppTab.Bots -> R.drawable.ic_ai_bot
        AppTab.Calls -> R.drawable.ic_ai_call
        AppTab.SecretChats -> R.drawable.ic_ai_secret
        AppTab.Groups -> R.drawable.ic_ai_groups
        AppTab.Discover -> R.drawable.ic_ai_search
        AppTab.Blacklist -> R.drawable.ic_ai_hidden
        AppTab.Cache -> R.drawable.ic_ai_cache
        AppTab.Donate -> R.drawable.ic_ai_donate
        AppTab.Settings -> R.drawable.ic_ai_settings
    }
}

private fun TelegramMessage.pendingPreview(context: android.content.Context): String {
    val readableText = MessagePrivacyPolicy.readableTranslatedText(this)
    if (readableText.isNotBlank()) return readableText
    if (originalText.isNotBlank()) {
        if (MessagePrivacyPolicy.shouldRenderSourceText(this)) return originalText
        return context.getString(R.string.translation_hidden)
    }
    return when (kind) {
        MessageKind.Image -> context.getString(R.string.media_image)
        MessageKind.Video -> context.getString(R.string.media_video)
        MessageKind.File -> context.getString(R.string.media_file)
        MessageKind.Voice -> context.getString(R.string.media_voice)
        MessageKind.VideoNote -> context.getString(R.string.media_video_message)
        MessageKind.Audio -> context.getString(R.string.media_audio)
        MessageKind.Sticker -> context.getString(R.string.media_sticker)
        MessageKind.Text -> context.getString(R.string.no_message_preview)
    }
}

private fun TelegramMessage.shouldTranslateWhenViewed(): Boolean {
    if (originalText.isBlank()) return false
    if (translationFailureReason == TranslationFailureReason.NonTranslatable) return false
    if (
        translationStatus == TranslationStatus.Ready &&
        MessagePrivacyPolicy.readableTranslatedText(this).isBlank()
    ) {
        return true
    }
    return translationStatus == TranslationStatus.Pending ||
        translationStatus == TranslationStatus.Translating ||
        translationStatus == TranslationStatus.Failed
}

