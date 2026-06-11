package ai.telegram.android

import android.Manifest
import android.content.pm.PackageManager
import ai.telegram.android.billing.DonationBillingState
import ai.telegram.android.billing.DonationProduct
import ai.telegram.android.core.ContentNormalizer
import ai.telegram.android.data.HiddenContent
import ai.telegram.android.data.MediaDownloadPolicy
import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.NetworkMode
import ai.telegram.android.data.TelegramChat
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TelegramSender
import ai.telegram.android.data.TranslationStatus
import ai.telegram.android.data.translation.MlKitModelDownloadProgress
import ai.telegram.android.data.translation.MlKitTranslationModelInfo
import ai.telegram.android.data.translation.TranslationLanguagePair
import ai.telegram.android.data.translation.VietnameseTranslationPairs
import ai.telegram.android.data.translation.VideoSubtitleCue
import ai.telegram.android.data.translation.VideoSubtitleGenerator
import ai.telegram.android.data.translation.VideoSubtitleResult
import ai.telegram.android.data.telegram.TelegramBotCommand
import ai.telegram.android.data.telegram.TelegramBotMenuButton
import ai.telegram.android.data.telegram.TelegramActiveSession
import ai.telegram.android.data.telegram.TelegramBusinessChatLink
import ai.telegram.android.data.telegram.TelegramChatPermissionPreset
import ai.telegram.android.data.telegram.TelegramCall
import ai.telegram.android.data.telegram.TelegramCallState
import ai.telegram.android.data.telegram.TelegramInlineBotResult
import ai.telegram.android.data.telegram.TelegramInlineBotResults
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
import ai.telegram.android.data.telegram.TdLibStatus
import ai.telegram.android.ui.AiTelegramTheme
import ai.telegram.android.ui.AiThemeTokens
import ai.telegram.android.ui.MessagePrivacyPolicy
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
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
import java.io.File

internal const val MUTE_ONE_HOUR_SECONDS = 60 * 60
internal const val MUTE_EIGHT_HOURS_SECONDS = 8 * 60 * 60
internal const val MUTE_TWO_DAYS_SECONDS = 2 * 24 * 60 * 60
internal const val MUTE_FOREVER_SECONDS = 31_536_000
internal const val AUTO_DELETE_OFF_SECONDS = 0
internal const val AUTO_DELETE_ONE_DAY_SECONDS = 24 * 60 * 60
internal const val AUTO_DELETE_ONE_WEEK_SECONDS = 7 * 24 * 60 * 60
internal const val AUTO_DELETE_ONE_MONTH_SECONDS = 31 * 24 * 60 * 60
internal const val SLOW_MODE_OFF_SECONDS = 0
internal const val SLOW_MODE_TEN_SECONDS = 10
internal const val SLOW_MODE_THIRTY_SECONDS = 30
internal const val SLOW_MODE_ONE_MINUTE_SECONDS = 60
internal const val SLOW_MODE_FIVE_MINUTES_SECONDS = 5 * 60
internal const val SLOW_MODE_FIFTEEN_MINUTES_SECONDS = 15 * 60
internal const val SLOW_MODE_ONE_HOUR_SECONDS = 60 * 60
internal val DirectoryBottomInset = AiThemeTokens.ScreenBottomPadding

private enum class ChannelTool {
    Search,
    Web,
    Join,
    Create,
    Manage
}

data class ChatManagementActions(
    val onSetChatTitle: (Long, String) -> Unit,
    val onSetChatDescription: (Long, String) -> Unit,
    val onSetChatAutoDeleteTime: (Long, Int) -> Unit,
    val onSetChatSlowMode: (Long, Int) -> Unit,
    val onSetChatPermissions: (Long, TelegramChatPermissionPreset) -> Unit,
    val onCreateChatInviteLink: (Long, String) -> Unit,
    val onRevokeChatInviteLink: (Long, String) -> Unit,
    val onAddChatMember: (Long, Long) -> Unit,
    val onRemoveChatMember: (Long, Long) -> Unit,
    val onBanChatMember: (Long, Long) -> Unit,
    val onUnbanChatMember: (Long, Long) -> Unit,
    val onPromoteChatMember: (Long, Long, String) -> Unit,
    val onDemoteChatMember: (Long, Long) -> Unit,
    val onPinChat: (Long) -> Unit,
    val onUnpinChat: (Long) -> Unit,
    val onUnpinAllMessages: (Long) -> Unit,
    val onMuteChat: (Long, Int) -> Unit,
    val onUnmuteChat: (Long) -> Unit,
    val onArchiveChat: (Long) -> Unit,
    val onUnarchiveChat: (Long) -> Unit,
    val onMarkChatRead: (Long) -> Unit,
    val onMarkChatUnread: (Long) -> Unit,
    val onClearChatUnreadMark: (Long) -> Unit,
    val onClearChatHistory: (Long) -> Unit,
    val onLeaveChat: (Long) -> Unit
)

@Composable
fun MenuScreen(onSelect: (AppTab) -> Unit) {
    val telegramItems = listOf(AppTab.AccountPrivacy, AppTab.PremiumBusiness, AppTab.Stories, AppTab.Bots, AppTab.Calls, AppTab.SecretChats, AppTab.Groups, AppTab.Discover)
    val dataItems = listOf(AppTab.Blacklist, AppTab.Cache)
    val appItems = listOf(AppTab.Donate)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("menu_list")
            .padding(horizontal = AiThemeTokens.ScreenPadding),
        contentPadding = PaddingValues(top = AiThemeTokens.ScreenTopPadding),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
    ) {
        item {
            SectionHeader(
                title = "Telegram"
            )
        }
        items(telegramItems, key = { it.name }) { tab ->
            AppListItem(
                title = stringResource(tab.labelRes),
                subtitle = tab.menuSubtitle(),
                iconRes = tab.navigationIconRes(),
                modifier = Modifier.testTag("menu_item_${tab.name}"),
                onClick = { onSelect(tab) }
            )
        }
        item {
            SectionHeader(
                title = "Du lieu",
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        items(dataItems, key = { it.name }) { tab ->
            AppListItem(
                title = stringResource(tab.labelRes),
                subtitle = tab.menuSubtitle(),
                iconRes = tab.navigationIconRes(),
                modifier = Modifier.testTag("menu_item_${tab.name}"),
                onClick = { onSelect(tab) }
            )
        }
        item {
            SectionHeader(
                title = "Ung dung",
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        items(appItems, key = { it.name }) { tab ->
            AppListItem(
                title = stringResource(tab.labelRes),
                subtitle = tab.menuSubtitle(),
                iconRes = tab.navigationIconRes(),
                modifier = Modifier.testTag("menu_item_${tab.name}"),
                onClick = { onSelect(tab) }
            )
        }
        item { Spacer(Modifier.height(DirectoryBottomInset)) }
    }
}

@Composable
private fun AppTab.menuSubtitle(): String {
    return when (this) {
        AppTab.Groups -> stringResource(R.string.groups_title)
        AppTab.AccountPrivacy -> stringResource(R.string.account_privacy_title)
        AppTab.PremiumBusiness -> stringResource(R.string.premium_business_title)
        AppTab.Stories -> stringResource(R.string.stories_title)
        AppTab.Bots -> stringResource(R.string.bots_title)
        AppTab.Calls -> stringResource(R.string.calls_title)
        AppTab.SecretChats -> stringResource(R.string.secret_chats_title)
        AppTab.Discover -> stringResource(R.string.discover_title)
        AppTab.Blacklist -> stringResource(R.string.blacklist_title)
        AppTab.Cache -> stringResource(R.string.cache_title)
        AppTab.Donate -> stringResource(R.string.donate_title)
        AppTab.Settings -> stringResource(R.string.settings_title)
        AppTab.Chats,
        AppTab.Channels,
        AppTab.Contacts,
        AppTab.Menu -> stringResource(labelRes)
    }
}

@Composable
fun AccountPrivacyScreen(
    privacyRules: List<TelegramPrivacyRuleSummary>,
    activeSessions: List<TelegramActiveSession>,
    operationMessage: String?,
    onUpdateProfile: (String, String, String, String) -> Unit,
    onSetProfilePhoto: (String) -> Unit,
    onLoadPrivacy: (TelegramPrivacySetting) -> Unit,
    onSetPrivacy: (TelegramPrivacySetting, TelegramPrivacyPreset) -> Unit,
    onLoadSessions: () -> Unit,
    onTerminateSession: (Long) -> Unit,
    onTerminateAllOtherSessions: () -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var profilePhotoPath by remember { mutableStateOf("") }
    var profilePhotoPrepareError by remember { mutableStateOf<String?>(null) }
    var selectedPrivacySetting by rememberSaveable { mutableStateOf(TelegramPrivacySetting.LastSeen.name) }
    var selectedPrivacyPreset by rememberSaveable { mutableStateOf(TelegramPrivacyPreset.Contacts.name) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val profilePhotoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = runCatching { context.prepareOutgoingTelegramMedia(uri) }
        result.onSuccess { media ->
            if (media.kind == MessageKind.Image) {
                profilePhotoPath = media.localPath
                profilePhotoPrepareError = null
            } else {
                profilePhotoPrepareError = resources.getString(
                    R.string.profile_photo_prepare_failed,
                    resources.getString(R.string.media_image)
                )
            }
        }.onFailure { error ->
            profilePhotoPrepareError = resources.getString(
                R.string.profile_photo_prepare_failed,
                error.localizedMessage ?: error.message ?: error::class.java.simpleName
            )
        }
    }
    val privacySetting = TelegramPrivacySetting.entries.firstOrNull { it.name == selectedPrivacySetting }
        ?: TelegramPrivacySetting.LastSeen
    val privacyPreset = TelegramPrivacyPreset.entries.firstOrNull { it.name == selectedPrivacyPreset }
        ?: TelegramPrivacyPreset.Contacts

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AiThemeTokens.ScreenPadding),
        contentPadding = PaddingValues(top = AiThemeTokens.ScreenTopPadding),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
    ) {
        item {
            AccountPrivacyHero(
                privacyRules = privacyRules.size,
                sessions = activeSessions.size,
                onRefresh = {
                    onLoadPrivacy(privacySetting)
                    onLoadSessions()
                }
            )
        }
        operationNoticeItem(profilePhotoPrepareError ?: operationMessage)
        item {
            TelegramActionCard(
                title = stringResource(R.string.profile_edit_title),
                iconRes = R.drawable.ic_ai_contacts
            ) {
                Text(
                    text = stringResource(R.string.account_privacy_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ActionTextField(firstName, { firstName = it }, stringResource(R.string.profile_first_name_hint))
                ActionTextField(lastName, { lastName = it }, stringResource(R.string.profile_last_name_hint))
                ActionTextField(bio, { bio = it }, stringResource(R.string.profile_bio_hint))
                ActionTextField(username, { username = it }, stringResource(R.string.profile_username_hint))
                CompactActionButton(
                    label = stringResource(R.string.update_profile),
                    iconRes = android.R.drawable.ic_menu_save,
                    onClick = { onUpdateProfile(firstName, lastName, bio, username) },
                    enabled = firstName.isNotBlank() || bio.isNotBlank() || username.isNotBlank(),
                    primary = true
                )
                ActionTextField(profilePhotoPath, { profilePhotoPath = it }, stringResource(R.string.profile_photo_path_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    CompactActionButton(
                        label = stringResource(R.string.profile_pick_photo),
                        iconRes = R.drawable.ic_ai_paperclip,
                        onClick = { profilePhotoPickerLauncher.launch(arrayOf("image/*")) }
                    )
                    CompactActionButton(
                        label = stringResource(R.string.set_profile_photo),
                        iconRes = android.R.drawable.ic_menu_camera,
                        onClick = { onSetProfilePhoto(profilePhotoPath) },
                        enabled = profilePhotoPath.isNotBlank()
                    )
                }
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.privacy_settings_title),
                iconRes = R.drawable.ic_ai_shield
            ) {
                Text(
                    text = stringResource(R.string.privacy_settings_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    TelegramPrivacySetting.entries.forEach { setting ->
                        FilterChip(
                            selected = privacySetting == setting,
                            onClick = { selectedPrivacySetting = setting.name },
                            label = { Text(setting.label()) }
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    TelegramPrivacyPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = privacyPreset == preset,
                            onClick = { selectedPrivacyPreset = preset.name },
                            label = { Text(preset.label()) }
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    CompactActionButton(
                        label = stringResource(R.string.load_privacy_setting),
                        iconRes = R.drawable.ic_ai_search,
                        onClick = { onLoadPrivacy(privacySetting) },
                        primary = true
                    )
                    CompactActionButton(
                        label = stringResource(R.string.set_privacy_setting),
                        iconRes = R.drawable.ic_ai_shield,
                        onClick = { onSetPrivacy(privacySetting, privacyPreset) }
                    )
                }
            }
        }
        if (privacyRules.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.privacy_rules_title)) }
            items(privacyRules, key = { it.setting.name }) { rule ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                ) {
                    Row(
                        modifier = Modifier.padding(AiThemeTokens.CardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)) {
                            Text(rule.setting.label(), fontWeight = FontWeight.SemiBold)
                            Text(
                                text = rule.rawRule.ifBlank { rule.preset.label() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssistChip(onClick = {}, label = { Text(rule.preset.label()) })
                    }
                }
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.active_sessions_title),
                iconRes = R.drawable.ic_ai_settings
            ) {
                Text(
                    text = stringResource(R.string.active_sessions_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    CompactActionButton(
                        label = stringResource(R.string.load_active_sessions),
                        iconRes = R.drawable.ic_ai_search,
                        onClick = onLoadSessions,
                        primary = true
                    )
                    CompactActionButton(
                        label = stringResource(R.string.terminate_other_sessions),
                        iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                        onClick = onTerminateAllOtherSessions
                    )
                }
            }
        }
        if (activeSessions.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.active_sessions_title)) }
            items(activeSessions, key = { it.id }) { session ->
                ActiveSessionCard(
                    session = session,
                    onTerminate = { onTerminateSession(session.id) }
                )
            }
        }
        item { Spacer(Modifier.height(DirectoryBottomInset)) }
    }
}

@Composable
private fun AccountPrivacyHero(
    privacyRules: Int,
    sessions: Int,
    onRefresh: () -> Unit
) {
    StatsSummaryCard(
        title = stringResource(R.string.account_privacy_title),
        subtitle = stringResource(R.string.account_privacy_summary, privacyRules, sessions),
        iconRes = R.drawable.ic_ai_shield,
        onRefresh = onRefresh
    )
}

@Composable
private fun ActiveSessionCard(
    session: TelegramActiveSession,
    onTerminate: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier.padding(AiThemeTokens.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)
            ) {
                Text(
                    text = session.applicationName.ifBlank { session.deviceModel.ifBlank { session.platform.ifBlank { "Telegram" } } },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(session.deviceModel, session.platform, session.country, session.ip)
                        .filter { it.isNotBlank() }
                        .joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (session.isCurrent) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.current_session)) })
            } else {
                TextButton(onClick = onTerminate) {
                    Text(stringResource(R.string.terminate_session))
                }
            }
        }
    }
}

@Composable
private fun TelegramPrivacySetting.label(): String {
    return when (this) {
        TelegramPrivacySetting.LastSeen -> stringResource(R.string.privacy_last_seen)
        TelegramPrivacySetting.ProfilePhoto -> stringResource(R.string.privacy_profile_photo)
        TelegramPrivacySetting.PhoneNumber -> stringResource(R.string.privacy_phone_number)
        TelegramPrivacySetting.ForwardedMessages -> stringResource(R.string.privacy_forwarded_messages)
        TelegramPrivacySetting.Calls -> stringResource(R.string.privacy_calls)
        TelegramPrivacySetting.ChatInvites -> stringResource(R.string.privacy_chat_invites)
        TelegramPrivacySetting.Bio -> stringResource(R.string.privacy_bio)
    }
}

@Composable
private fun TelegramPrivacyPreset.label(): String {
    return when (this) {
        TelegramPrivacyPreset.Everybody -> stringResource(R.string.privacy_everybody)
        TelegramPrivacyPreset.Contacts -> stringResource(R.string.privacy_contacts)
        TelegramPrivacyPreset.Nobody -> stringResource(R.string.privacy_nobody)
    }
}

@Composable
fun PremiumBusinessScreen(
    premiumFeatures: List<TelegramPremiumFeatureInfo>,
    premiumLimits: List<TelegramPremiumLimitSummary>,
    starBalance: TelegramStarBalance?,
    starTransactions: List<TelegramStarTransaction>,
    businessLinks: List<TelegramBusinessChatLink>,
    operationMessage: String?,
    optionalActionsEnabled: Boolean = false,
    onLoadPremiumFeatures: () -> Unit,
    onViewPremiumFeature: (TelegramPremiumFeature) -> Unit,
    onLoadPremiumLimit: (TelegramPremiumLimitType) -> Unit,
    onLoadStarBalance: () -> Unit,
    onLoadStarTransactions: (String, Int) -> Unit,
    onLoadBusinessLinks: () -> Unit,
    onCreateBusinessLink: (String) -> Unit,
    onDeleteBusinessLink: (String) -> Unit
) {
    var selectedFeature by rememberSaveable { mutableStateOf(TelegramPremiumFeature.Business.name) }
    var selectedLimit by rememberSaveable { mutableStateOf(TelegramPremiumLimitType.ChatFolderCount.name) }
    var starOffset by remember { mutableStateOf("") }
    var businessMessage by remember { mutableStateOf("") }
    var businessLinkToDelete by remember { mutableStateOf("") }
    val premiumFeature = TelegramPremiumFeature.entries.firstOrNull { it.name == selectedFeature }
        ?: TelegramPremiumFeature.Business
    val premiumLimit = TelegramPremiumLimitType.entries.firstOrNull { it.name == selectedLimit }
        ?: TelegramPremiumLimitType.ChatFolderCount

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AiThemeTokens.ScreenPadding),
        contentPadding = PaddingValues(top = AiThemeTokens.ScreenTopPadding),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
    ) {
        item {
            PremiumBusinessHero(
                features = premiumFeatures.size,
                transactions = starTransactions.size,
                links = businessLinks.size,
                onRefresh = if (optionalActionsEnabled) {
                    {
                        onLoadPremiumFeatures()
                        onLoadStarBalance()
                        onLoadBusinessLinks()
                    }
                } else {
                    null
                }
            )
        }
        operationNoticeItem(operationMessage)
        item {
            TelegramActionCard(
                title = stringResource(R.string.premium_tools_title),
                iconRes = R.drawable.ic_ai_palette
            ) {
                Text(
                    text = stringResource(R.string.premium_business_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!optionalActionsEnabled) {
                    Text(
                        text = stringResource(R.string.optional_telegram_api_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    TelegramPremiumFeature.entries.forEach { feature ->
                        FilterChip(
                            selected = premiumFeature == feature,
                            onClick = { selectedFeature = feature.name },
                            label = { Text(feature.label()) }
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    CompactActionButton(
                        label = stringResource(R.string.load_premium_features),
                        iconRes = R.drawable.ic_ai_search,
                        onClick = onLoadPremiumFeatures,
                        enabled = optionalActionsEnabled,
                        primary = true
                    )
                    CompactActionButton(
                        label = stringResource(R.string.view_premium_feature),
                        iconRes = R.drawable.ic_ai_palette,
                        onClick = { onViewPremiumFeature(premiumFeature) },
                        enabled = optionalActionsEnabled
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    TelegramPremiumLimitType.entries.forEach { type ->
                        FilterChip(
                            selected = premiumLimit == type,
                            onClick = { selectedLimit = type.name },
                            label = { Text(type.label()) }
                        )
                    }
                }
                CompactActionButton(
                    label = stringResource(R.string.load_premium_limit),
                    iconRes = R.drawable.ic_ai_search,
                    onClick = { onLoadPremiumLimit(premiumLimit) },
                    enabled = optionalActionsEnabled
                )
            }
        }
        if (premiumLimits.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.premium_limits_title)) }
            items(premiumLimits, key = { it.type.name }) { limit ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                ) {
                    Row(
                        modifier = Modifier.padding(AiThemeTokens.CardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(limit.type.label(), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("${limit.defaultValue} / ${limit.premiumValue}")
                    }
                }
            }
        }
        if (premiumFeatures.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.premium_features_title)) }
            items(premiumFeatures, key = { "${it.type}:${it.title}" }) { feature ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                ) {
                    Column(Modifier.padding(AiThemeTokens.CardPadding), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.TinySpacing)) {
                        Text(feature.title.ifBlank { feature.type }, fontWeight = FontWeight.SemiBold)
                        Text(feature.description.ifBlank { feature.type }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.stars_monetization_title),
                iconRes = R.drawable.ic_ai_donate
            ) {
                Text(
                    text = starBalance?.let {
                        val quantity = it.amount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                        pluralStringResource(R.plurals.star_balance_value, quantity, it.amount)
                    }
                        ?: stringResource(R.string.star_balance_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ActionTextField(starOffset, { starOffset = it }, stringResource(R.string.star_offset_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    CompactActionButton(
                        label = stringResource(R.string.load_star_balance),
                        iconRes = R.drawable.ic_ai_search,
                        onClick = onLoadStarBalance,
                        enabled = optionalActionsEnabled,
                        primary = true
                    )
                    CompactActionButton(
                        label = stringResource(R.string.load_star_transactions),
                        iconRes = R.drawable.ic_ai_donate,
                        onClick = { onLoadStarTransactions(starOffset, 50) },
                        enabled = optionalActionsEnabled
                    )
                }
            }
        }
        if (starTransactions.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.star_transactions_title)) }
            items(starTransactions, key = { it.id }) { transaction ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                ) {
                    Row(Modifier.padding(AiThemeTokens.CardPadding), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)) {
                            Text(transaction.title.ifBlank { transaction.id }, fontWeight = FontWeight.SemiBold)
                            Text(transaction.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(transaction.amount.toString(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.business_tools_title),
                iconRes = R.drawable.ic_ai_channel
            ) {
                ActionTextField(businessMessage, { businessMessage = it }, stringResource(R.string.business_link_message_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    CompactActionButton(
                        label = stringResource(R.string.load_business_links),
                        iconRes = R.drawable.ic_ai_search,
                        onClick = onLoadBusinessLinks,
                        enabled = optionalActionsEnabled,
                        primary = true
                    )
                    CompactActionButton(
                        label = stringResource(R.string.create_business_link),
                        iconRes = android.R.drawable.ic_menu_share,
                        onClick = { onCreateBusinessLink(businessMessage) },
                        enabled = optionalActionsEnabled && businessMessage.isNotBlank()
                    )
                }
                ActionTextField(businessLinkToDelete, { businessLinkToDelete = it }, stringResource(R.string.business_link_delete_hint))
                CompactActionButton(
                    label = stringResource(R.string.delete_business_link),
                    iconRes = android.R.drawable.ic_menu_delete,
                    onClick = { onDeleteBusinessLink(businessLinkToDelete) },
                    enabled = optionalActionsEnabled && businessLinkToDelete.isNotBlank()
                )
            }
        }
        if (businessLinks.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.business_links_title)) }
            items(businessLinks, key = { it.link }) { link ->
                AppListItem(
                    title = link.title.ifBlank { link.link },
                    subtitle = link.text.ifBlank { link.link },
                    iconRes = R.drawable.ic_ai_channel,
                    onClick = { businessLinkToDelete = link.link }
                )
            }
        }
        item { Spacer(Modifier.height(DirectoryBottomInset)) }
    }
}

@Composable
private fun PremiumBusinessHero(
    features: Int,
    transactions: Int,
    links: Int,
    onRefresh: (() -> Unit)?
) {
    StatsSummaryCard(
        title = stringResource(R.string.premium_business_title),
        subtitle = stringResource(R.string.premium_business_summary, features, transactions, links),
        iconRes = R.drawable.ic_ai_donate,
        onRefresh = onRefresh
    )
}

@Composable
private fun TelegramPremiumFeature.label(): String {
    return when (this) {
        TelegramPremiumFeature.Stories -> stringResource(R.string.premium_feature_stories)
        TelegramPremiumFeature.Reactions -> stringResource(R.string.premium_feature_reactions)
        TelegramPremiumFeature.Stickers -> stringResource(R.string.premium_feature_stickers)
        TelegramPremiumFeature.Downloads -> stringResource(R.string.premium_feature_downloads)
        TelegramPremiumFeature.Business -> stringResource(R.string.premium_feature_business)
        TelegramPremiumFeature.VoiceToText -> stringResource(R.string.premium_feature_voice_to_text)
        TelegramPremiumFeature.ProfileBadge -> stringResource(R.string.premium_feature_profile_badge)
        TelegramPremiumFeature.EmojiStatus -> stringResource(R.string.premium_feature_emoji_status)
    }
}

@Composable
private fun TelegramPremiumLimitType.label(): String {
    return when (this) {
        TelegramPremiumLimitType.ChatFolderCount -> stringResource(R.string.premium_limit_folders)
        TelegramPremiumLimitType.PinnedChatCount -> stringResource(R.string.premium_limit_pinned)
        TelegramPremiumLimitType.CaptionLength -> stringResource(R.string.premium_limit_caption)
        TelegramPremiumLimitType.BioLength -> stringResource(R.string.premium_limit_bio)
        TelegramPremiumLimitType.ChatFilterChosenChatCount -> stringResource(R.string.premium_limit_filter_chats)
    }
}

@Composable
fun StoriesScreen(
    stories: List<TelegramStory>,
    chats: List<TelegramChat>,
    operationMessage: String?,
    onLoadActiveStories: (Long) -> Unit,
    onOpenStory: (Long, Int) -> Unit,
    onPostStory: (Long, String, MessageKind, String) -> Unit,
    onDeleteStory: (Long, Int) -> Unit,
    onRefreshStories: () -> Unit,
    onDownloadStoryMedia: (Int, MessageKind) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var chatIdInput by remember { mutableStateOf("") }
    var storyIdInput by remember { mutableStateOf("") }
    var localPath by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var postAsVideo by rememberSaveable { mutableStateOf(false) }
    var mediaPrepareError by remember { mutableStateOf<String?>(null) }
    var previewStory by remember { mutableStateOf<TelegramStory?>(null) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val storyMediaPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = runCatching { context.prepareOutgoingTelegramMedia(uri) }
        result.onSuccess { media ->
            localPath = media.localPath
            postAsVideo = media.kind == MessageKind.Video
            mediaPrepareError = null
        }.onFailure { error ->
            mediaPrepareError = resources.getString(
                R.string.story_media_prepare_failed,
                error.localizedMessage ?: error.message ?: error::class.java.simpleName
            )
        }
    }
    val filteredStories = remember(stories, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            stories
        } else {
            stories.filter { story ->
                story.caption.contains(normalizedQuery, ignoreCase = true) ||
                    story.chatId.toString().contains(normalizedQuery) ||
                    story.id.toString().contains(normalizedQuery)
            }
        }
    }
    val storyChats = remember(chats, query) {
        val normalizedQuery = query.trim()
        chats
            .filterNot { it.isSecretChat() }
            .filter { chat ->
                normalizedQuery.isBlank() ||
                    chat.title.contains(normalizedQuery, ignoreCase = true) ||
                    chat.id.toString().contains(normalizedQuery)
            }
            .take(24)
    }
    fun openStoryWithPreview(chatId: Long, storyId: Int) {
        onOpenStory(chatId, storyId)
        stories.firstOrNull { it.chatId == chatId && it.id == storyId }?.let { story ->
            if (story.mediaLocalPath.isNotBlank()) {
                previewStory = story
            } else if (story.mediaFileId > 0) {
                onDownloadStoryMedia(story.mediaFileId, story.kind)
            }
        }
    }

    previewStory?.let { story ->
        StoryPreviewDialog(
            story = story,
            chatTitle = chats.firstOrNull { it.id == story.chatId }?.title,
            onDismiss = { previewStory = null }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AiThemeTokens.ScreenPadding),
        contentPadding = PaddingValues(top = AiThemeTokens.ScreenTopPadding),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
    ) {
        item {
            StoriesHero(
                totalStories = stories.size,
                activeStories = stories.count { it.state == TelegramStoryState.Active || it.state == TelegramStoryState.Sending },
                onRefresh = onRefreshStories
            )
        }
        operationNoticeItem(mediaPrepareError ?: operationMessage)
        item {
            TelegramActionCard(
                title = stringResource(R.string.stories_load_title),
                iconRes = R.drawable.ic_ai_story
            ) {
                ActionTextField(chatIdInput, { chatIdInput = it }, stringResource(R.string.chat_id_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    CompactActionButton(
                        label = stringResource(R.string.load_active_stories),
                        iconRes = R.drawable.ic_ai_story,
                        onClick = { chatIdInput.toLongOrNull()?.let(onLoadActiveStories) },
                        enabled = chatIdInput.toLongOrNull() != null,
                        primary = true
                    )
                    CompactActionButton(
                        label = stringResource(R.string.open_story),
                        iconRes = R.drawable.ic_ai_chevron_right,
                        onClick = {
                            val chatId = chatIdInput.toLongOrNull()
                            val storyId = storyIdInput.toIntOrNull()
                            if (chatId != null && storyId != null) openStoryWithPreview(chatId, storyId)
                        },
                        enabled = chatIdInput.toLongOrNull() != null && storyIdInput.toIntOrNull() != null
                    )
                }
                ActionTextField(storyIdInput, { storyIdInput = it }, stringResource(R.string.story_id_hint))
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.post_story),
                iconRes = R.drawable.ic_ai_story
            ) {
                Text(
                    text = stringResource(R.string.stories_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ActionTextField(localPath, { localPath = it }, stringResource(R.string.story_local_path_hint))
                ActionTextField(caption, { caption = it }, stringResource(R.string.story_caption_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    CompactActionButton(
                        label = stringResource(R.string.story_pick_media),
                        iconRes = R.drawable.ic_ai_paperclip,
                        onClick = { storyMediaPickerLauncher.launch(arrayOf("image/*", "video/*")) }
                    )
                    FilterChip(
                        selected = !postAsVideo,
                        onClick = { postAsVideo = false },
                        label = { Text(stringResource(R.string.media_image)) }
                    )
                    FilterChip(
                        selected = postAsVideo,
                        onClick = { postAsVideo = true },
                        label = { Text(stringResource(R.string.media_video)) }
                    )
                    CompactActionButton(
                        label = stringResource(R.string.post_story),
                        iconRes = R.drawable.ic_ai_story,
                        onClick = {
                            val chatId = chatIdInput.toLongOrNull()
                            if (chatId != null) {
                                onPostStory(
                                    chatId,
                                    localPath,
                                    if (postAsVideo) MessageKind.Video else MessageKind.Image,
                                    caption
                                )
                            }
                        },
                        enabled = chatIdInput.toLongOrNull() != null && localPath.isNotBlank(),
                        primary = true
                    )
                }
            }
        }
        item {
            DirectorySearchCard(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.stories_search_hint)
            )
        }
        if (storyChats.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.stories_chat_sources_title)) }
            items(storyChats, key = { it.id }) { chat ->
                AppListItem(
                    title = chat.title,
                    subtitle = chat.type.ifBlank { stringResource(R.string.nav_chats) },
                    iconRes = R.drawable.ic_ai_story,
                    onClick = {
                        chatIdInput = chat.id.toString()
                        onLoadActiveStories(chat.id)
                    },
                    trailingText = stringResource(R.string.load_active_stories)
                )
            }
        }
        item { SectionHeader(title = stringResource(R.string.stories_title)) }
        if (filteredStories.isEmpty()) {
            item { EmptyState(stringResource(R.string.stories_empty)) }
        } else {
            items(filteredStories, key = { "${it.chatId}:${it.id}" }) { story ->
                StoryCard(
                    story = story,
                    chatTitle = chats.firstOrNull { it.id == story.chatId }?.title,
                    onOpenStory = ::openStoryWithPreview,
                    onDeleteStory = onDeleteStory,
                    onDownloadStoryMedia = onDownloadStoryMedia
                )
            }
        }
        item { Spacer(Modifier.height(DirectoryBottomInset)) }
    }
}

@Composable
fun BotPlatformScreen(
    chats: List<TelegramChat>,
    commands: List<TelegramBotCommand>,
    menuButtons: List<TelegramBotMenuButton>,
    inlineResults: TelegramInlineBotResults?,
    webAppUrl: TelegramWebAppUrl?,
    operationMessage: String?,
    webAppDataActionsEnabled: Boolean = true,
    onSearchBot: (String) -> Unit,
    onRefreshBots: () -> Unit,
    onStartBot: (Long, Long, String) -> Unit,
    onLoadCommands: (Long) -> Unit,
    onLoadMenuButton: (Long) -> Unit,
    onInlineQuery: (Long, Long, String, String) -> Unit,
    onSendInlineResult: (Long, Long, String, Boolean) -> Unit,
    onClickCallback: (Long, Long, String) -> Unit,
    onOpenWebApp: (Long, Long, String) -> Unit,
    onSendWebAppData: (Long, String, String) -> Unit
) {
    var botInput by remember { mutableStateOf("") }
    var botUserIdInput by remember { mutableStateOf("") }
    var chatIdInput by remember { mutableStateOf("") }
    var startParameter by remember { mutableStateOf("") }
    var inlineQuery by remember { mutableStateOf("") }
    var inlineOffset by remember { mutableStateOf("") }
    var inlineResultId by remember { mutableStateOf("") }
    var hideViaBot by rememberSaveable { mutableStateOf(false) }
    var callbackMessageId by remember { mutableStateOf("") }
    var callbackPayload by remember { mutableStateOf("") }
    var webAppRequestUrl by remember { mutableStateOf("") }
    var webAppButtonText by remember { mutableStateOf("") }
    var webAppData by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    val botUserId = botUserIdInput.toLongOrNull()
    val chatId = chatIdInput.toLongOrNull()
    val callbackMessageIdLong = callbackMessageId.toLongOrNull()
    val botLikeChats = remember(chats) {
        chats
            .filterNot { it.isSecretChat() }
            .filter { it.type == "private" || it.title.contains("bot", ignoreCase = true) }
            .take(24)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AiThemeTokens.ScreenPadding),
        contentPadding = PaddingValues(top = AiThemeTokens.ScreenTopPadding),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
    ) {
        item {
            BotPlatformHero(
                bots = botLikeChats.size,
                commands = commands.size,
                inlineResults = inlineResults?.results?.size ?: 0,
                onRefresh = onRefreshBots
            )
        }
        operationNoticeItem(operationMessage)
        item {
            TelegramActionCard(
                title = stringResource(R.string.bot_open_title),
                iconRes = R.drawable.ic_ai_bot
            ) {
                Text(
                    text = stringResource(R.string.bots_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppSearchField(
                    value = botInput,
                    onValueChange = { botInput = it },
                    placeholder = stringResource(R.string.bot_username_hint)
                )
                CompactActionButton(
                    label = stringResource(R.string.open_bot),
                    iconRes = R.drawable.ic_ai_bot,
                    onClick = { onSearchBot(botInput) },
                    enabled = botInput.isNotBlank(),
                    primary = true
                )
                ActionTextField(botUserIdInput, { botUserIdInput = it }, stringResource(R.string.bot_user_id_hint))
                ActionTextField(chatIdInput, { chatIdInput = it }, stringResource(R.string.chat_id_hint))
                ActionTextField(startParameter, { startParameter = it }, stringResource(R.string.bot_start_parameter_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    CompactActionButton(
                        label = stringResource(R.string.start_bot),
                        iconRes = android.R.drawable.ic_media_play,
                        onClick = { if (botUserId != null && chatId != null) onStartBot(botUserId, chatId, startParameter) },
                        enabled = botUserId != null && chatId != null,
                        primary = true
                    )
                    CompactActionButton(
                        label = stringResource(R.string.load_bot_commands),
                        iconRes = android.R.drawable.ic_menu_sort_by_size,
                        onClick = { botUserId?.let(onLoadCommands) },
                        enabled = botUserId != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.load_bot_menu),
                        iconRes = android.R.drawable.ic_menu_manage,
                        onClick = { botUserId?.let(onLoadMenuButton) },
                        enabled = botUserId != null
                    )
                }
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.bot_inline_title),
                iconRes = R.drawable.ic_ai_search
            ) {
                ActionTextField(inlineQuery, { inlineQuery = it }, stringResource(R.string.inline_query_hint))
                ActionTextField(inlineOffset, { inlineOffset = it }, stringResource(R.string.inline_offset_hint))
                CompactActionButton(
                    label = stringResource(R.string.query_inline_bot),
                    iconRes = R.drawable.ic_ai_search,
                    onClick = {
                        if (botUserId != null && chatId != null) {
                            onInlineQuery(botUserId, chatId, inlineQuery, inlineOffset)
                        }
                    },
                    enabled = botUserId != null && chatId != null,
                    primary = true
                )
                ActionTextField(inlineResultId, { inlineResultId = it }, stringResource(R.string.inline_result_id_hint))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.hide_via_bot), modifier = Modifier.weight(1f))
                    Switch(checked = hideViaBot, onCheckedChange = { hideViaBot = it })
                }
                CompactActionButton(
                    label = stringResource(R.string.send_inline_result),
                    iconRes = R.drawable.ic_ai_channel,
                    onClick = {
                        val queryId = inlineResults?.queryId ?: 0L
                        if (chatId != null) onSendInlineResult(chatId, queryId, inlineResultId, hideViaBot)
                    },
                    enabled = chatId != null && inlineResults != null && inlineResults.queryId != 0L && inlineResultId.isNotBlank()
                )
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.bot_callback_title),
                iconRes = R.drawable.ic_ai_more_horiz
            ) {
                ActionTextField(callbackMessageId, { callbackMessageId = it }, stringResource(R.string.message_id_hint))
                ActionTextField(callbackPayload, { callbackPayload = it }, stringResource(R.string.callback_payload_hint))
                CompactActionButton(
                    label = stringResource(R.string.click_callback_button),
                    iconRes = R.drawable.ic_ai_more_horiz,
                    onClick = {
                        if (chatId != null && callbackMessageIdLong != null) {
                            onClickCallback(chatId, callbackMessageIdLong, callbackPayload)
                        }
                    },
                    enabled = chatId != null && callbackMessageIdLong != null && callbackPayload.isNotBlank()
                )
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.bot_web_app_title),
                iconRes = android.R.drawable.ic_menu_view
            ) {
                if (!webAppDataActionsEnabled) {
                    Text(
                        text = stringResource(R.string.bot_web_app_data_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ActionTextField(webAppRequestUrl, { webAppRequestUrl = it }, stringResource(R.string.web_app_url_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    CompactActionButton(
                        label = stringResource(R.string.request_web_app_url),
                        iconRes = android.R.drawable.ic_menu_view,
                        onClick = {
                            if (botUserId != null && chatId != null) {
                                onOpenWebApp(botUserId, chatId, webAppRequestUrl)
                            }
                        },
                        enabled = botUserId != null && chatId != null && webAppRequestUrl.isNotBlank(),
                        primary = true
                    )
                    CompactActionButton(
                        label = stringResource(R.string.open_web_app),
                        iconRes = android.R.drawable.ic_menu_upload,
                        onClick = { webAppUrl?.url?.takeIf { it.isNotBlank() }?.let(uriHandler::openUri) },
                        enabled = !webAppUrl?.url.isNullOrBlank()
                    )
                }
                ActionTextField(webAppButtonText, { webAppButtonText = it }, stringResource(R.string.web_app_button_text_hint))
                ActionTextField(webAppData, { webAppData = it }, stringResource(R.string.web_app_data_hint))
                CompactActionButton(
                    label = stringResource(R.string.send_web_app_data),
                    iconRes = R.drawable.ic_ai_channel,
                    onClick = { botUserId?.let { onSendWebAppData(it, webAppButtonText, webAppData) } },
                    enabled = webAppDataActionsEnabled &&
                        botUserId != null &&
                        webAppButtonText.isNotBlank() &&
                        webAppData.isNotBlank()
                )
                webAppUrl?.url?.takeIf { it.isNotBlank() }?.let { url ->
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        if (commands.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.bot_commands_title)) }
            items(commands, key = { "${it.botUserId}:${it.command}" }) { command ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                ) {
                    Column(Modifier.padding(AiThemeTokens.CardPadding), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.TinySpacing)) {
                        Text("/${command.command}", fontWeight = FontWeight.SemiBold)
                        Text(command.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (menuButtons.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.bot_menu_title)) }
            items(menuButtons, key = { "${it.botUserId}:${it.type}:${it.url}:${it.text}" }) { menu ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                ) {
                    Column(Modifier.padding(AiThemeTokens.CardPadding), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.TinySpacing)) {
                        Text(menu.text.ifBlank { menu.type }, fontWeight = FontWeight.SemiBold)
                        Text(menu.url.ifBlank { menu.type }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        inlineResults?.takeIf { it.results.isNotEmpty() }?.let { results ->
            item {
                SectionHeader(
                    title = pluralStringResource(
                        R.plurals.inline_results_title,
                        results.results.size,
                        results.results.size
                    )
                )
            }
            items(results.results, key = { it.id }) { result ->
                InlineBotResultCard(
                    result = result,
                    onUse = { inlineResultId = result.id }
                )
            }
        }
        if (botLikeChats.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.bot_chats_title)) }
            items(botLikeChats, key = { it.id }) { chat ->
                AppListItem(
                    title = chat.title,
                    subtitle = stringResource(R.string.story_chat_title, chat.id),
                    iconRes = R.drawable.ic_ai_bot,
                    onClick = { chatIdInput = chat.id.toString() }
                )
            }
        }
        item { Spacer(Modifier.height(DirectoryBottomInset)) }
    }
}

@Composable
private fun BotPlatformHero(
    bots: Int,
    commands: Int,
    inlineResults: Int,
    onRefresh: () -> Unit
) {
    StatsSummaryCard(
        title = stringResource(R.string.bots_title),
        subtitle = stringResource(R.string.bots_summary, bots, commands, inlineResults),
        iconRes = R.drawable.ic_ai_bot,
        onRefresh = onRefresh
    )
}

@Composable
private fun InlineBotResultCard(
    result: TelegramInlineBotResult,
    onUse: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier.padding(AiThemeTokens.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(AiThemeTokens.IconButtonSize),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ai_bot),
                        contentDescription = result.type,
                        modifier = Modifier.size(AiThemeTokens.IconSize),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(AiThemeTokens.ListSpacing))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)
            ) {
                Text(
                    text = result.title.ifBlank { result.id },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = result.description.ifBlank { result.type },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onUse) {
                Text(stringResource(R.string.use_inline_result))
            }
        }
    }
}

@Composable
private fun StoriesHero(
    totalStories: Int,
    activeStories: Int,
    onRefresh: () -> Unit
) {
    StatsSummaryCard(
        title = stringResource(R.string.stories_title),
        iconRes = R.drawable.ic_ai_story,
        onRefresh = onRefresh
    ) {
        MetaChip(text = pluralStringResource(R.plurals.stories_total_metric, totalStories, totalStories))
        MetaChip(text = pluralStringResource(R.plurals.stories_active_metric, activeStories, activeStories))
    }
}

@Composable
private fun StoryCard(
    story: TelegramStory,
    chatTitle: String?,
    onOpenStory: (Long, Int) -> Unit,
    onDeleteStory: (Long, Int) -> Unit,
    onDownloadStoryMedia: (Int, MessageKind) -> Unit
) {
    TelegramActionCard(
        title = chatTitle ?: stringResource(R.string.story_chat_title, story.chatId),
        iconRes = R.drawable.ic_ai_story
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.story_id_label, story.id)) })
            AssistChip(onClick = {}, label = { Text(story.kind.storyKindLabel()) })
            AssistChip(onClick = {}, label = { Text(story.state.storyStateLabel()) })
            if (story.isViewed) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.story_viewed)) })
            }
            if (story.isPinned) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.message_pinned_short)) })
            }
        }
        Text(
            text = story.caption.ifBlank { stringResource(R.string.no_message_preview) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
            CompactActionButton(
                label = stringResource(R.string.open_story),
                iconRes = R.drawable.ic_ai_chevron_right,
                onClick = { onOpenStory(story.chatId, story.id) },
                primary = true
            )
            CompactActionButton(
                label = stringResource(R.string.download_story_media),
                iconRes = R.drawable.ic_action_download,
                onClick = { onDownloadStoryMedia(story.mediaFileId, story.kind) },
                enabled = story.mediaFileId > 0
            )
            CompactActionButton(
                label = stringResource(R.string.delete_story),
                iconRes = android.R.drawable.ic_menu_delete,
                onClick = { onDeleteStory(story.chatId, story.id) },
                enabled = story.isOutgoing || story.state == TelegramStoryState.Sending
            )
        }
    }
}

@Composable
private fun StoryPreviewDialog(
    story: TelegramStory,
    chatTitle: String?,
    onDismiss: () -> Unit
) {
    val localPath = story.mediaLocalPath
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(AiThemeTokens.InsetPadding),
                verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chatTitle ?: stringResource(R.string.story_chat_title, story.chatId),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.story_id_label, story.id),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when (story.kind) {
                        MessageKind.Image -> {
                            val bitmap = remember(localPath) { BitmapFactory.decodeFile(localPath) }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = story.caption,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(stringResource(R.string.story_media_not_ready))
                            }
                        }
                        MessageKind.Video -> {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { viewContext ->
                                    VideoView(viewContext).apply {
                                        setMediaController(MediaController(viewContext))
                                        setVideoURI(Uri.fromFile(File(localPath)))
                                        start()
                                    }
                                },
                                update = { videoView ->
                                    videoView.setVideoURI(Uri.fromFile(File(localPath)))
                                }
                            )
                        }
                        MessageKind.File,
                        MessageKind.Voice,
                        MessageKind.VideoNote,
                        MessageKind.Audio,
                        MessageKind.Sticker,
                        MessageKind.Text -> Text(stringResource(R.string.story_media_not_ready))
                    }
                }
                if (story.caption.isNotBlank()) {
                    Text(
                        text = story.caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageKind.storyKindLabel(): String {
    return when (this) {
        MessageKind.Image -> stringResource(R.string.media_image)
        MessageKind.Video -> stringResource(R.string.media_video)
        MessageKind.File -> stringResource(R.string.media_file)
        MessageKind.Voice -> stringResource(R.string.media_voice)
        MessageKind.VideoNote -> stringResource(R.string.media_video_message)
        MessageKind.Audio -> stringResource(R.string.media_audio)
        MessageKind.Sticker -> stringResource(R.string.media_sticker)
        MessageKind.Text -> stringResource(R.string.story_unsupported)
    }
}

@Composable
private fun TelegramStoryState.storyStateLabel(): String {
    return when (this) {
        TelegramStoryState.Active -> stringResource(R.string.story_state_active)
        TelegramStoryState.Sending -> stringResource(R.string.story_state_sending)
        TelegramStoryState.Expired -> stringResource(R.string.story_state_expired)
        TelegramStoryState.Deleted -> stringResource(R.string.story_state_deleted)
        TelegramStoryState.Failed -> stringResource(R.string.story_state_failed)
        TelegramStoryState.Unknown -> stringResource(R.string.story_state_unknown)
    }
}

@Composable
fun SecretChatDirectoryScreen(
    secretChats: List<TelegramChat>,
    contacts: List<TelegramSender>,
    operationMessage: String?,
    onOpenChat: (TelegramChat) -> Unit,
    onCreateSecretChat: (Long) -> Unit,
    onRefreshContacts: () -> Unit,
    onRefreshSecretChats: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var userIdInput by remember { mutableStateOf("") }
    val filteredSecretChats = remember(secretChats, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            secretChats
        } else {
            secretChats.filter { chat ->
                chat.title.contains(normalizedQuery, ignoreCase = true) ||
                    chat.lastMessagePreview.contains(normalizedQuery, ignoreCase = true) ||
                    chat.id.toString().contains(normalizedQuery)
            }
        }
    }
    val filteredContacts = remember(contacts, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            contacts.take(12)
        } else {
            contacts.filter { contact ->
                contact.displayName.contains(normalizedQuery, ignoreCase = true) ||
                    contact.id.contains(normalizedQuery, ignoreCase = true)
            }.take(20)
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
            SecretChatHero(
                totalSecretChats = secretChats.size,
                unreadSecretChats = secretChats.count { it.unreadCount > 0 },
                onRefresh = onRefreshSecretChats
            )
        }
        operationNoticeItem(operationMessage)
        item {
            TelegramActionCard(
                title = stringResource(R.string.secret_chat_create_title),
                iconRes = R.drawable.ic_ai_secret
            ) {
                Text(
                    text = stringResource(R.string.secret_chat_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ActionTextField(userIdInput, { userIdInput = it }, stringResource(R.string.user_id_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    CompactActionButton(
                        label = stringResource(R.string.create_secret_chat),
                        iconRes = R.drawable.ic_ai_secret,
                        onClick = { userIdInput.toLongOrNull()?.let(onCreateSecretChat) },
                        enabled = userIdInput.toLongOrNull() != null,
                        primary = true
                    )
                    CompactActionButton(
                        label = stringResource(R.string.sync_contacts),
                        iconRes = R.drawable.ic_ai_contacts,
                        onClick = onRefreshContacts
                    )
                }
            }
        }
        item {
            DirectorySearchCard(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.secret_chat_search_hint)
            )
        }
        if (filteredContacts.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.secret_chat_contacts_title)) }
            items(filteredContacts, key = { it.id }) { contact ->
                val userId = contact.telegramUserIdOrNull()
                AppListItem(
                    title = contact.displayName,
                    subtitle = contact.id,
                    iconRes = R.drawable.ic_ai_contacts,
                    onClick = { userId?.let(onCreateSecretChat) },
                    trailingText = stringResource(R.string.create_secret_chat)
                )
            }
        }
        item { SectionHeader(title = stringResource(R.string.secret_chats_title)) }
        if (filteredSecretChats.isEmpty()) {
            item { EmptyState(stringResource(R.string.secret_chats_empty)) }
        } else {
            items(filteredSecretChats, key = { it.id }) { chat ->
                AppListItem(
                    title = chat.title,
                    subtitle = chat.lastMessagePreview.ifBlank { stringResource(R.string.secret_chat_label) },
                    iconRes = R.drawable.ic_ai_secret,
                    trailingText = chat.unreadCount.takeIf { it > 0 }?.toString(),
                    onClick = { onOpenChat(chat) }
                )
            }
        }
        item { Spacer(Modifier.height(DirectoryBottomInset)) }
    }
}

@Composable
private fun SecretChatHero(
    totalSecretChats: Int,
    unreadSecretChats: Int,
    onRefresh: () -> Unit
) {
    StatsSummaryCard(
        title = stringResource(R.string.secret_chats_title),
        iconRes = R.drawable.ic_ai_secret,
        onRefresh = onRefresh
    ) {
        MetaChip(text = stringResource(R.string.chat_filter_secret) + " $totalSecretChats")
        MetaChip(text = stringResource(R.string.chat_filter_unread) + " $unreadSecretChats")
    }
}

@Composable
fun ChannelDirectoryScreen(
    channels: List<TelegramChat>,
    operationMessage: String?,
    onOpenChat: (TelegramChat) -> Unit,
    onRefreshChannels: () -> Unit,
    onSearchChannels: (String) -> Unit,
    onJoinChannel: (String) -> Unit,
    onCreateChannel: (String, String) -> Unit,
    managementActions: ChatManagementActions
) {
    var query by remember { mutableStateOf("") }
    var joinInput by remember { mutableStateOf("") }
    var channelTitle by remember { mutableStateOf("") }
    var channelDescription by remember { mutableStateOf("") }
    var activeTool by rememberSaveable { mutableStateOf<ChannelTool?>(null) }
    var webSearchTargetName by rememberSaveable { mutableStateOf(PublicSearchTarget.Channels.name) }
    val uriHandler = LocalUriHandler.current
    val webSearchTarget = webSearchTargetName.publicSearchTargetOrDefault()
    val publicTelegramLink = remember(query) { PublicWebSearch.telegramPublicLinkOrNull(query) }
    val filteredChannels = remember(channels, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            channels
        } else {
            channels.filter { channel ->
                channel.title.contains(normalizedQuery, ignoreCase = true) ||
                    channel.lastMessagePreview.contains(normalizedQuery, ignoreCase = true)
            }
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
            ChannelHero(
                totalChannels = channels.size,
                unreadChannels = channels.count { it.unreadCount > 0 },
                filteredChannels = filteredChannels.size,
                onRefresh = onRefreshChannels
            )
        }
        operationNoticeItem(operationMessage)
        item {
            DirectorySearchCard(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.global_search_hint)
            )
        }
        item {
            ChannelToolRow(
                activeTool = activeTool,
                onToolSelected = { tool -> activeTool = if (activeTool == tool) null else tool }
            )
        }
        activeTool?.let { tool ->
            item {
                when (tool) {
                    ChannelTool.Search -> TelegramActionCard(
                        title = stringResource(R.string.global_search),
                        iconRes = R.drawable.ic_ai_search
                    ) {
                        CompactActionButton(
                            label = stringResource(R.string.global_search),
                            iconRes = R.drawable.ic_ai_search,
                            onClick = { onSearchChannels(query) },
                            enabled = query.isNotBlank(),
                            primary = true
                        )
                    }
                    ChannelTool.Web -> TelegramActionCard(
                        title = stringResource(R.string.internet_search_title),
                        iconRes = R.drawable.ic_ai_search
                    ) {
                        Text(
                            text = stringResource(R.string.internet_search_privacy_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PublicSearchTargetRow(
                            selectedTarget = webSearchTarget,
                            onTargetChange = { webSearchTargetName = it.name }
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
                            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
                        ) {
                            CompactActionButton(
                                label = stringResource(R.string.internet_search_open),
                                iconRes = R.drawable.ic_ai_search,
                                onClick = {
                                    uriHandler.openUri(PublicWebSearch.buildTelegramDiscoveryUrl(query, webSearchTarget))
                                },
                                enabled = query.isNotBlank(),
                                primary = true
                            )
                            publicTelegramLink?.let { link ->
                                CompactActionButton(
                                    label = stringResource(R.string.internet_search_open_tme),
                                    iconRes = R.drawable.ic_ai_channel,
                                    onClick = { uriHandler.openUri(link) },
                                    enabled = true
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.internet_search_safety_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    ChannelTool.Join -> TelegramActionCard(
                        title = stringResource(R.string.join_channel),
                        iconRes = R.drawable.ic_ai_channel
                    ) {
                        ActionTextField(joinInput, { joinInput = it }, stringResource(R.string.join_channel_hint))
                        CompactActionButton(
                            label = stringResource(R.string.join_channel),
                            iconRes = R.drawable.ic_ai_channel,
                            onClick = { onJoinChannel(joinInput) },
                            enabled = joinInput.isNotBlank(),
                            primary = true
                        )
                    }
                    ChannelTool.Create -> TelegramActionCard(
                        title = stringResource(R.string.create_channel),
                        iconRes = R.drawable.ic_ai_channel
                    ) {
                        ActionTextField(channelTitle, { channelTitle = it }, stringResource(R.string.channel_title_hint))
                        ActionTextField(
                            channelDescription,
                            { channelDescription = it },
                            stringResource(R.string.channel_description_hint)
                        )
                        CompactActionButton(
                            label = stringResource(R.string.create_channel),
                            iconRes = R.drawable.ic_ai_channel,
                            onClick = {
                                onCreateChannel(channelTitle, channelDescription)
                                channelTitle = ""
                                channelDescription = ""
                            },
                            enabled = channelTitle.isNotBlank(),
                            primary = true
                        )
                    }
                    ChannelTool.Manage -> ChatManagementPanel(
                        chats = filteredChannels,
                        title = stringResource(R.string.channel_management_title),
                        emptyText = stringResource(R.string.channels_empty),
                        includeMembers = false,
                        includePermissions = false,
                        includeSlowMode = false,
                        actions = managementActions
                    )
                }
            }
        }
        if (filteredChannels.isEmpty()) {
            item {
                if (channels.isEmpty()) {
                    ChannelEmptyState()
                } else {
                    EmptyState(stringResource(R.string.no_chats_found))
                }
            }
        } else {
            items(filteredChannels, key = { it.id }) { chat ->
                ChatListRow(chat = chat, onClick = { onOpenChat(chat) })
            }
        }
        item { Spacer(Modifier.height(DirectoryBottomInset)) }
    }
}

@Composable
private fun ChannelToolRow(
    activeTool: ChannelTool?,
    onToolSelected: (ChannelTool) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
        contentPadding = PaddingValues(end = 12.dp)
    ) {
        item {
            FilterChip(
                selected = activeTool == ChannelTool.Search,
                onClick = { onToolSelected(ChannelTool.Search) },
                label = { Text(stringResource(R.string.channel_tool_search), maxLines = 1) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_ai_search),
                        contentDescription = null,
                        modifier = Modifier.size(AiThemeTokens.ControlIconSize)
                    )
                }
            )
        }
        item {
            FilterChip(
                selected = activeTool == ChannelTool.Web,
                onClick = { onToolSelected(ChannelTool.Web) },
                label = { Text(stringResource(R.string.channel_tool_web), maxLines = 1) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_ai_search),
                        contentDescription = null,
                        modifier = Modifier.size(AiThemeTokens.ControlIconSize)
                    )
                }
            )
        }
        item {
            FilterChip(
                selected = activeTool == ChannelTool.Join,
                onClick = { onToolSelected(ChannelTool.Join) },
                label = { Text(stringResource(R.string.channel_tool_join), maxLines = 1) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_ai_channel),
                        contentDescription = null,
                        modifier = Modifier.size(AiThemeTokens.ControlIconSize)
                    )
                }
            )
        }
        item {
            FilterChip(
                selected = activeTool == ChannelTool.Create,
                onClick = { onToolSelected(ChannelTool.Create) },
                label = { Text(stringResource(R.string.channel_tool_create), maxLines = 1) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_add),
                        contentDescription = null,
                        modifier = Modifier.size(AiThemeTokens.ControlIconSize)
                    )
                }
            )
        }
        item {
            FilterChip(
                selected = activeTool == ChannelTool.Manage,
                onClick = { onToolSelected(ChannelTool.Manage) },
                label = { Text(stringResource(R.string.channel_tool_manage), maxLines = 1) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_ai_settings),
                        contentDescription = null,
                        modifier = Modifier.size(AiThemeTokens.ControlIconSize)
                    )
                }
            )
        }
    }
}

@Composable
private fun PublicSearchTargetRow(
    selectedTarget: PublicSearchTarget,
    onTargetChange: (PublicSearchTarget) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
        contentPadding = PaddingValues(end = 12.dp)
    ) {
        items(PublicSearchTarget.entries, key = { it.name }) { target ->
            FilterChip(
                selected = selectedTarget == target,
                onClick = { onTargetChange(target) },
                label = { Text(stringResource(target.labelRes()), maxLines = 1) }
            )
        }
    }
}

private fun PublicSearchTarget.labelRes(): Int {
    return when (this) {
        PublicSearchTarget.All -> R.string.internet_filter_all
        PublicSearchTarget.Channels -> R.string.internet_filter_channels
        PublicSearchTarget.Groups -> R.string.internet_filter_groups
        PublicSearchTarget.Bots -> R.string.internet_filter_bots
    }
}

@Composable
private fun ChannelHero(
    totalChannels: Int,
    unreadChannels: Int,
    filteredChannels: Int,
    onRefresh: () -> Unit
) {
    StatsSummaryCard(
        title = stringResource(R.string.channels_title),
        iconRes = R.drawable.ic_ai_channel,
        onRefresh = onRefresh
    ) {
        MetaChip(text = "$filteredChannels/$totalChannels")
        MetaChip(text = stringResource(R.string.chat_filter_unread) + " $unreadChannels")
    }
}

@Composable
private fun ChannelEmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(AiThemeTokens.EmptyStatePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ai_channel),
                        contentDescription = null,
                        modifier = Modifier.size(AiThemeTokens.LargeIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = stringResource(R.string.channels_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TelegramToolsScreen(
    chats: List<TelegramChat>,
    contacts: List<TelegramSender>,
    operationMessage: String?,
    onGlobalSearch: (String) -> Unit,
    onJoinChannel: (String) -> Unit,
    onSendMessage: (Long, String) -> Unit,
    onCreatePrivateChat: (Long) -> Unit,
    onCreateSecretChat: (Long) -> Unit,
    onBlockUser: (Long) -> Unit,
    onUnblockUser: (Long) -> Unit,
    onAddContact: (Long, String, String, String) -> Unit,
    onDeleteContact: (Long) -> Unit,
    onCreateGroup: (List<Long>, String) -> Unit,
    onCreateChannel: (String, String) -> Unit,
    onCreateForumGroup: (String, String) -> Unit,
    onSetChatTitle: (Long, String) -> Unit,
    onSetChatDescription: (Long, String) -> Unit,
    onSetChatAutoDeleteTime: (Long, Int) -> Unit,
    onSetChatSlowMode: (Long, Int) -> Unit,
    onSetChatPermissions: (Long, TelegramChatPermissionPreset) -> Unit,
    onCreateChatInviteLink: (Long, String) -> Unit,
    onRevokeChatInviteLink: (Long, String) -> Unit,
    onAddChatMember: (Long, Long) -> Unit,
    onRemoveChatMember: (Long, Long) -> Unit,
    onBanChatMember: (Long, Long) -> Unit,
    onUnbanChatMember: (Long, Long) -> Unit,
    onSetChatDraft: (Long, String) -> Unit,
    onClearChatDraft: (Long) -> Unit,
    onPinChat: (Long) -> Unit,
    onUnpinChat: (Long) -> Unit,
    onUnpinAllMessages: (Long) -> Unit,
    onMuteChat: (Long, Int) -> Unit,
    onUnmuteChat: (Long) -> Unit,
    onArchiveChat: (Long) -> Unit,
    onUnarchiveChat: (Long) -> Unit,
    onMarkChatRead: (Long) -> Unit,
    onMarkChatUnread: (Long) -> Unit,
    onClearChatUnreadMark: (Long) -> Unit,
    onClearChatHistory: (Long) -> Unit,
    onLeaveChat: (Long) -> Unit,
    onOpenCache: () -> Unit,
    onRefreshContacts: () -> Unit
) {
    var globalQuery by remember { mutableStateOf("") }
    var webSearchTargetName by rememberSaveable { mutableStateOf(PublicSearchTarget.All.name) }
    var joinInput by remember { mutableStateOf("") }
    var sendChatId by remember { mutableStateOf("") }
    var sendText by remember { mutableStateOf("") }
    var userIdInput by remember { mutableStateOf("") }
    var contactFirstName by remember { mutableStateOf("") }
    var contactLastName by remember { mutableStateOf("") }
    var contactPhoneNumber by remember { mutableStateOf("") }
    var groupTitle by remember { mutableStateOf("") }
    var groupUserIds by remember { mutableStateOf("") }
    var channelTitle by remember { mutableStateOf("") }
    var channelDescription by remember { mutableStateOf("") }
    var forumTitle by remember { mutableStateOf("") }
    var forumDescription by remember { mutableStateOf("") }
    var manageChatId by remember { mutableStateOf("") }
    var manageChatTitle by remember { mutableStateOf("") }
    var manageChatDescription by remember { mutableStateOf("") }
    var manageChatDraft by remember { mutableStateOf("") }
    var manageInviteLinkName by remember { mutableStateOf("") }
    var manageInviteLinkToRevoke by remember { mutableStateOf("") }
    var manageMemberUserId by remember { mutableStateOf("") }
    var showAdvancedChatTools by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val webSearchTarget = webSearchTargetName.publicSearchTargetOrDefault()
    val publicTelegramLink = remember(globalQuery) { PublicWebSearch.telegramPublicLinkOrNull(globalQuery) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AiThemeTokens.ScreenPadding),
        contentPadding = PaddingValues(top = AiThemeTokens.ScreenTopPadding),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
    ) {
        item {
            DiscoverHero(
                chats = chats.size,
                channels = chats.count { it.isChannelChat() },
                groups = chats.count { it.isGroupChat() },
                contacts = contacts.size,
                onRefresh = onRefreshContacts
            )
        }
        operationNoticeItem(operationMessage)
        item {
            TelegramActionCard(
                title = stringResource(R.string.global_search),
                iconRes = R.drawable.ic_ai_search
            ) {
                AppSearchField(
                    value = globalQuery,
                    onValueChange = { globalQuery = it },
                    placeholder = stringResource(R.string.global_search_hint)
                )
                CompactActionButton(
                    label = stringResource(R.string.global_search),
                    iconRes = R.drawable.ic_ai_search,
                    onClick = { onGlobalSearch(globalQuery) },
                    enabled = globalQuery.isNotBlank(),
                    primary = true
                )
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.internet_search_title),
                iconRes = R.drawable.ic_ai_search
            ) {
                Text(
                    text = stringResource(R.string.internet_search_privacy_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PublicSearchTargetRow(
                    selectedTarget = webSearchTarget,
                    onTargetChange = { webSearchTargetName = it.name }
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
                    verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
                ) {
                    CompactActionButton(
                        label = stringResource(R.string.internet_search_open),
                        iconRes = R.drawable.ic_ai_search,
                        onClick = {
                            uriHandler.openUri(PublicWebSearch.buildTelegramDiscoveryUrl(globalQuery, webSearchTarget))
                        },
                        enabled = globalQuery.isNotBlank(),
                        primary = true
                    )
                    publicTelegramLink?.let { link ->
                        CompactActionButton(
                            label = stringResource(R.string.internet_search_open_tme),
                            iconRes = R.drawable.ic_ai_channel,
                            onClick = { uriHandler.openUri(link) },
                            enabled = true
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.internet_search_safety_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.channel_discovery_title),
                iconRes = R.drawable.ic_ai_channel
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    listOf("AI", "News", "Vietnam", "Android", "Crypto").forEach { topic ->
                        AssistChip(
                            onClick = { onGlobalSearch(topic) },
                            label = { Text(topic) }
                        )
                    }
                }
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.join_channel),
                iconRes = R.drawable.ic_ai_channel
            ) {
                ActionTextField(joinInput, { joinInput = it }, stringResource(R.string.join_channel_hint))
                CompactActionButton(
                    label = stringResource(R.string.join_channel),
                    iconRes = R.drawable.ic_ai_channel,
                    onClick = { onJoinChannel(joinInput) },
                    enabled = joinInput.isNotBlank(),
                    primary = true
                )
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.send_message),
                iconRes = R.drawable.ic_ai_chat
            ) {
                ActionTextField(sendChatId, { sendChatId = it }, stringResource(R.string.chat_id_hint))
                ActionTextField(sendText, { sendText = it }, stringResource(R.string.message_composer_hint))
                CompactActionButton(
                    label = stringResource(R.string.send_message),
                    iconRes = R.drawable.ic_ai_channel,
                    onClick = { sendChatId.toLongOrNull()?.let { onSendMessage(it, sendText) } },
                    enabled = sendChatId.toLongOrNull() != null && sendText.isNotBlank(),
                    primary = true
                )
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.create_chat),
                iconRes = R.drawable.ic_ai_groups
            ) {
                ActionTextField(userIdInput, { userIdInput = it }, stringResource(R.string.user_id_hint))
                CompactActionButton(
                    label = stringResource(R.string.create_private_chat),
                    iconRes = android.R.drawable.ic_dialog_email,
                    onClick = { userIdInput.toLongOrNull()?.let(onCreatePrivateChat) },
                    enabled = userIdInput.toLongOrNull() != null,
                    primary = true
                )
                CompactActionButton(
                    label = stringResource(R.string.create_secret_chat),
                    iconRes = R.drawable.ic_ai_secret,
                    onClick = { userIdInput.toLongOrNull()?.let(onCreateSecretChat) },
                    enabled = userIdInput.toLongOrNull() != null
                )
                ActionTextField(contactFirstName, { contactFirstName = it }, stringResource(R.string.contact_first_name_hint))
                ActionTextField(contactLastName, { contactLastName = it }, stringResource(R.string.contact_last_name_hint))
                ActionTextField(contactPhoneNumber, { contactPhoneNumber = it }, stringResource(R.string.contact_phone_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    val userIdLong = userIdInput.toLongOrNull()
                    CompactActionButton(
                        label = stringResource(R.string.add_contact),
                        iconRes = android.R.drawable.ic_menu_add,
                        onClick = {
                            userIdLong?.let {
                                onAddContact(it, contactFirstName, contactLastName, contactPhoneNumber)
                            }
                        },
                        enabled = userIdLong != null && contactFirstName.isNotBlank()
                    )
                    CompactActionButton(
                        label = stringResource(R.string.delete_contact),
                        iconRes = android.R.drawable.ic_menu_delete,
                        onClick = { userIdLong?.let(onDeleteContact) },
                        enabled = userIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.block_user),
                        iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                        onClick = { userIdLong?.let(onBlockUser) },
                        enabled = userIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.unblock_user),
                        iconRes = android.R.drawable.ic_menu_revert,
                        onClick = { userIdLong?.let(onUnblockUser) },
                        enabled = userIdLong != null
                    )
                }
                ActionTextField(groupTitle, { groupTitle = it }, stringResource(R.string.group_title_hint))
                ActionTextField(groupUserIds, { groupUserIds = it }, stringResource(R.string.group_user_ids_hint))
                CompactActionButton(
                    label = stringResource(R.string.create_group),
                    iconRes = android.R.drawable.ic_menu_add,
                    onClick = { onCreateGroup(parseUserIds(groupUserIds), groupTitle) },
                    enabled = groupTitle.isNotBlank() && parseUserIds(groupUserIds).isNotEmpty(),
                    primary = true
                )
                ActionTextField(channelTitle, { channelTitle = it }, stringResource(R.string.channel_title_hint))
                ActionTextField(
                    channelDescription,
                    { channelDescription = it },
                    stringResource(R.string.channel_description_hint)
                )
                CompactActionButton(
                    label = stringResource(R.string.create_channel),
                    iconRes = android.R.drawable.ic_menu_share,
                    onClick = { onCreateChannel(channelTitle, channelDescription) },
                    enabled = channelTitle.isNotBlank(),
                    primary = true
                )
                ActionTextField(forumTitle, { forumTitle = it }, stringResource(R.string.forum_title_hint))
                ActionTextField(
                    forumDescription,
                    { forumDescription = it },
                    stringResource(R.string.forum_description_hint)
                )
                CompactActionButton(
                    label = stringResource(R.string.create_forum_group),
                    iconRes = android.R.drawable.ic_menu_sort_by_size,
                    onClick = { onCreateForumGroup(forumTitle, forumDescription) },
                    enabled = forumTitle.isNotBlank(),
                    primary = true
                )
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.folder_pin_mute_title),
                iconRes = R.drawable.ic_ai_settings
            ) {
                Text(
                    text = stringResource(R.string.profile_members_notifications_status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CompactActionButton(
                    label = if (showAdvancedChatTools) stringResource(android.R.string.cancel) else stringResource(R.string.folder_pin_mute_title),
                    iconRes = if (showAdvancedChatTools) R.drawable.ic_ai_hidden else R.drawable.ic_ai_chevron_right,
                    onClick = { showAdvancedChatTools = !showAdvancedChatTools },
                    primary = !showAdvancedChatTools
                )
                if (showAdvancedChatTools) {
                    ActionTextField(manageChatId, { manageChatId = it }, stringResource(R.string.chat_id_hint))
                    ActionTextField(manageChatTitle, { manageChatTitle = it }, stringResource(R.string.chat_title_hint))
                    ActionTextField(
                        manageChatDescription,
                        { manageChatDescription = it },
                        stringResource(R.string.chat_description_hint)
                    )
                    ActionTextField(manageChatDraft, { manageChatDraft = it }, stringResource(R.string.chat_draft_hint))
                    ActionTextField(
                        manageInviteLinkName,
                        { manageInviteLinkName = it },
                        stringResource(R.string.chat_invite_link_name_hint)
                    )
                    ActionTextField(
                        manageInviteLinkToRevoke,
                        { manageInviteLinkToRevoke = it },
                        stringResource(R.string.chat_invite_link_revoke_hint)
                    )
                    ActionTextField(
                        manageMemberUserId,
                        { manageMemberUserId = it },
                        stringResource(R.string.chat_member_user_id_hint)
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    val manageChatIdLong = manageChatId.toLongOrNull()
                    val manageMemberUserIdLong = manageMemberUserId.toLongOrNull()
                    CompactActionButton(
                        label = stringResource(R.string.rename_chat),
                        iconRes = android.R.drawable.ic_menu_edit,
                        onClick = { manageChatIdLong?.let { onSetChatTitle(it, manageChatTitle) } },
                        enabled = manageChatIdLong != null && manageChatTitle.isNotBlank()
                    )
                    CompactActionButton(
                        label = stringResource(R.string.update_chat_description),
                        iconRes = android.R.drawable.ic_menu_info_details,
                        onClick = { manageChatIdLong?.let { onSetChatDescription(it, manageChatDescription) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.set_chat_draft),
                        iconRes = android.R.drawable.ic_menu_edit,
                        onClick = { manageChatIdLong?.let { onSetChatDraft(it, manageChatDraft) } },
                        enabled = manageChatIdLong != null && manageChatDraft.isNotBlank()
                    )
                    CompactActionButton(
                        label = stringResource(R.string.clear_chat_draft),
                        iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                        onClick = { manageChatIdLong?.let(onClearChatDraft) },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.create_invite_link),
                        iconRes = android.R.drawable.ic_menu_share,
                        onClick = { manageChatIdLong?.let { onCreateChatInviteLink(it, manageInviteLinkName) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.revoke_invite_link),
                        iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                        onClick = { manageChatIdLong?.let { onRevokeChatInviteLink(it, manageInviteLinkToRevoke) } },
                        enabled = manageChatIdLong != null && manageInviteLinkToRevoke.isNotBlank()
                    )
                    CompactActionButton(
                        label = stringResource(R.string.add_chat_member),
                        iconRes = android.R.drawable.ic_menu_add,
                        onClick = {
                            if (manageChatIdLong != null && manageMemberUserIdLong != null) {
                                onAddChatMember(manageChatIdLong, manageMemberUserIdLong)
                            }
                        },
                        enabled = manageChatIdLong != null && manageMemberUserIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.remove_chat_member),
                        iconRes = android.R.drawable.ic_menu_delete,
                        onClick = {
                            if (manageChatIdLong != null && manageMemberUserIdLong != null) {
                                onRemoveChatMember(manageChatIdLong, manageMemberUserIdLong)
                            }
                        },
                        enabled = manageChatIdLong != null && manageMemberUserIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.ban_chat_member),
                        iconRes = android.R.drawable.ic_lock_lock,
                        onClick = {
                            if (manageChatIdLong != null && manageMemberUserIdLong != null) {
                                onBanChatMember(manageChatIdLong, manageMemberUserIdLong)
                            }
                        },
                        enabled = manageChatIdLong != null && manageMemberUserIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.unban_chat_member),
                        iconRes = android.R.drawable.ic_menu_revert,
                        onClick = {
                            if (manageChatIdLong != null && manageMemberUserIdLong != null) {
                                onUnbanChatMember(manageChatIdLong, manageMemberUserIdLong)
                            }
                        },
                        enabled = manageChatIdLong != null && manageMemberUserIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.auto_delete_off),
                        iconRes = android.R.drawable.ic_menu_delete,
                        onClick = { manageChatIdLong?.let { onSetChatAutoDeleteTime(it, AUTO_DELETE_OFF_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.auto_delete_1d),
                        iconRes = android.R.drawable.ic_menu_delete,
                        onClick = { manageChatIdLong?.let { onSetChatAutoDeleteTime(it, AUTO_DELETE_ONE_DAY_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.auto_delete_1w),
                        iconRes = android.R.drawable.ic_menu_delete,
                        onClick = { manageChatIdLong?.let { onSetChatAutoDeleteTime(it, AUTO_DELETE_ONE_WEEK_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.auto_delete_1m),
                        iconRes = android.R.drawable.ic_menu_delete,
                        onClick = { manageChatIdLong?.let { onSetChatAutoDeleteTime(it, AUTO_DELETE_ONE_MONTH_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.slow_mode_off),
                        iconRes = android.R.drawable.ic_menu_recent_history,
                        onClick = { manageChatIdLong?.let { onSetChatSlowMode(it, SLOW_MODE_OFF_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.slow_mode_10s),
                        iconRes = android.R.drawable.ic_menu_recent_history,
                        onClick = { manageChatIdLong?.let { onSetChatSlowMode(it, SLOW_MODE_TEN_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.slow_mode_30s),
                        iconRes = android.R.drawable.ic_menu_recent_history,
                        onClick = { manageChatIdLong?.let { onSetChatSlowMode(it, SLOW_MODE_THIRTY_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.slow_mode_1m),
                        iconRes = android.R.drawable.ic_menu_recent_history,
                        onClick = { manageChatIdLong?.let { onSetChatSlowMode(it, SLOW_MODE_ONE_MINUTE_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.slow_mode_5m),
                        iconRes = android.R.drawable.ic_menu_recent_history,
                        onClick = { manageChatIdLong?.let { onSetChatSlowMode(it, SLOW_MODE_FIVE_MINUTES_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.slow_mode_15m),
                        iconRes = android.R.drawable.ic_menu_recent_history,
                        onClick = { manageChatIdLong?.let { onSetChatSlowMode(it, SLOW_MODE_FIFTEEN_MINUTES_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.slow_mode_1h),
                        iconRes = android.R.drawable.ic_menu_recent_history,
                        onClick = { manageChatIdLong?.let { onSetChatSlowMode(it, SLOW_MODE_ONE_HOUR_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.chat_permissions_read_only),
                        iconRes = android.R.drawable.ic_lock_lock,
                        onClick = { manageChatIdLong?.let { onSetChatPermissions(it, TelegramChatPermissionPreset.READ_ONLY) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.chat_permissions_text_only),
                        iconRes = android.R.drawable.ic_dialog_email,
                        onClick = { manageChatIdLong?.let { onSetChatPermissions(it, TelegramChatPermissionPreset.TEXT_ONLY) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.chat_permissions_media_allowed),
                        iconRes = android.R.drawable.ic_menu_gallery,
                        onClick = { manageChatIdLong?.let { onSetChatPermissions(it, TelegramChatPermissionPreset.MEDIA_ALLOWED) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.chat_permissions_full),
                        iconRes = android.R.drawable.ic_menu_manage,
                        onClick = { manageChatIdLong?.let { onSetChatPermissions(it, TelegramChatPermissionPreset.FULL) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.pin_chat),
                        iconRes = android.R.drawable.ic_menu_upload,
                        onClick = { manageChatIdLong?.let(onPinChat) },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.unpin_chat),
                        iconRes = android.R.drawable.ic_menu_revert,
                        onClick = { manageChatIdLong?.let(onUnpinChat) },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.unpin_all_messages),
                        iconRes = android.R.drawable.ic_menu_revert,
                        onClick = { manageChatIdLong?.let(onUnpinAllMessages) },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.mute_chat_1h),
                        iconRes = android.R.drawable.ic_lock_silent_mode,
                        onClick = { manageChatIdLong?.let { onMuteChat(it, MUTE_ONE_HOUR_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.mute_chat_8h),
                        iconRes = android.R.drawable.ic_lock_silent_mode,
                        onClick = { manageChatIdLong?.let { onMuteChat(it, MUTE_EIGHT_HOURS_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.mute_chat_2d),
                        iconRes = android.R.drawable.ic_lock_silent_mode,
                        onClick = { manageChatIdLong?.let { onMuteChat(it, MUTE_TWO_DAYS_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.mute_chat_forever),
                        iconRes = android.R.drawable.ic_lock_silent_mode,
                        onClick = { manageChatIdLong?.let { onMuteChat(it, MUTE_FOREVER_SECONDS) } },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.unmute_chat),
                        iconRes = android.R.drawable.ic_lock_silent_mode_off,
                        onClick = { manageChatIdLong?.let(onUnmuteChat) },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.archive_chat),
                        iconRes = android.R.drawable.ic_menu_save,
                        onClick = { manageChatIdLong?.let(onArchiveChat) },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.unarchive_chat),
                        iconRes = android.R.drawable.ic_menu_upload,
                        onClick = { manageChatIdLong?.let(onUnarchiveChat) },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.mark_chat_read),
                        iconRes = android.R.drawable.ic_menu_view,
                        onClick = { manageChatIdLong?.let(onMarkChatRead) },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.mark_chat_unread),
                        iconRes = android.R.drawable.ic_dialog_email,
                        onClick = { manageChatIdLong?.let(onMarkChatUnread) },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.clear_chat_unread_mark),
                        iconRes = android.R.drawable.ic_menu_view,
                        onClick = { manageChatIdLong?.let(onClearChatUnreadMark) },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.clear_chat_history),
                        iconRes = android.R.drawable.ic_menu_delete,
                        onClick = { manageChatIdLong?.let(onClearChatHistory) },
                        enabled = manageChatIdLong != null
                    )
                    CompactActionButton(
                        label = stringResource(R.string.leave_chat),
                        iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                        onClick = { manageChatIdLong?.let(onLeaveChat) },
                        enabled = manageChatIdLong != null
                    )
                }
                }
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.media_management_title),
                iconRes = R.drawable.ic_ai_paperclip
            ) {
                Text(stringResource(R.string.media_management_status), color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompactActionButton(
                    label = stringResource(R.string.open_cache),
                    iconRes = R.drawable.ic_ai_cache,
                    onClick = onOpenCache
                )
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.profile_members_notifications_title),
                iconRes = R.drawable.ic_ai_contacts
            ) {
                Text(stringResource(R.string.profile_members_notifications_status), color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompactActionButton(
                    label = stringResource(R.string.sync_contacts),
                    iconRes = R.drawable.ic_ai_contacts,
                    onClick = onRefreshContacts
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.directory_summary, chats.size, chats.count { it.isChannelChat() }, chats.count { it.isGroupChat() }, contacts.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(DirectoryBottomInset))
        }
    }
}

@Composable
private fun DiscoverHero(
    chats: Int,
    channels: Int,
    groups: Int,
    contacts: Int,
    onRefresh: () -> Unit
) {
    FeatureHero(
        title = stringResource(R.string.discover_title),
        subtitle = stringResource(R.string.directory_summary, chats, channels, groups, contacts),
        iconRes = R.drawable.ic_ai_search,
        onRefresh = onRefresh
    )
}

@Composable
private fun DirectoryScreen(
    searchValue: String,
    onSearchValueChange: (String) -> Unit,
    searchButtonLabel: String,
    onSearch: () -> Unit,
    operationMessage: String?,
    actionIconRes: Int = R.drawable.ic_ai_search,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = AiThemeTokens.ScreenPadding, vertical = AiThemeTokens.CompactSpacing),
                verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
            ) {
                OperationNotice(operationMessage)
                Row(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalAlignment = Alignment.CenterVertically) {
                    AppSearchField(
                        value = searchValue,
                        onValueChange = onSearchValueChange,
                        modifier = Modifier.weight(1f),
                        placeholder = stringResource(R.string.search_hint)
                    )
                    IconActionButton(
                        iconRes = actionIconRes,
                        contentDescription = searchButtonLabel,
                        onClick = onSearch,
                        primary = true
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = AiThemeTokens.ScreenPadding),
            contentPadding = PaddingValues(top = AiThemeTokens.ScreenTopPadding),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
        ) {
            content()
            item { Spacer(Modifier.height(DirectoryBottomInset)) }
        }
    }
}

@Composable
internal fun TelegramActionCard(
    title: String,
    iconRes: Int = R.drawable.ic_ai_settings,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LeadingIconTile(
                    iconRes = iconRes,
                    contentDescription = title,
                    size = 36.dp,
                    iconSize = 19.dp
                )
                Spacer(Modifier.width(AiThemeTokens.ListSpacing))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
    }
}

@Composable
internal fun ActionTextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        placeholder = {
            Text(
                text = placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun OperationNotice(message: String?) {
    if (message.isNullOrBlank() || message.isTelegramOperationNotice()) return
    StatusBanner(text = message)
}

internal fun LazyListScope.operationNoticeItem(message: String?) {
    message
        ?.takeIf { it.isNotBlank() && !it.isTelegramOperationNotice() }
        ?.let { visibleMessage ->
            item {
                StatusBanner(text = visibleMessage)
            }
        }
}

private fun String.isTelegramOperationNotice(): Boolean {
    val cleanMessage = trim()
    return cleanMessage.startsWith("Dang xu ly:", ignoreCase = true) ||
        cleanMessage.startsWith("Da hoan tat:", ignoreCase = true) ||
        cleanMessage.startsWith("Telegram khong thuc hien duoc:", ignoreCase = true) ||
        cleanMessage.startsWith("Working on:", ignoreCase = true) ||
        cleanMessage.startsWith("Completed:", ignoreCase = true) ||
        cleanMessage.startsWith("Telegram could not complete the action:", ignoreCase = true)
}

internal fun parseUserIds(input: String): List<Long> {
    return input
        .split(',', ' ', ';', '\n', '\t')
        .mapNotNull { it.trim().removePrefix("user:").toLongOrNull() }
        .distinct()
}

@Preview(name = "Menu", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun MenuScreenPreview() {
    AiTelegramTheme {
        MenuScreen(onSelect = {})
    }
}

@Preview(name = "Account privacy", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun AccountPrivacyScreenPreview() {
    AiTelegramTheme {
        AccountPrivacyScreen(
            privacyRules = previewPrivacyRules,
            activeSessions = previewSessions,
            operationMessage = "Privacy settings loaded",
            onUpdateProfile = { _, _, _, _ -> },
            onSetProfilePhoto = {},
            onLoadPrivacy = {},
            onSetPrivacy = { _, _ -> },
            onLoadSessions = {},
            onTerminateSession = {},
            onTerminateAllOtherSessions = {}
        )
    }
}

@Preview(name = "Premium business", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun PremiumBusinessScreenPreview() {
    AiTelegramTheme {
        PremiumBusinessScreen(
            premiumFeatures = previewPremiumFeatures,
            premiumLimits = previewPremiumLimits,
            starBalance = TelegramStarBalance(amount = 2400),
            starTransactions = previewStarTransactions,
            businessLinks = previewBusinessLinks,
            operationMessage = "Premium data loaded",
            optionalActionsEnabled = false,
            onLoadPremiumFeatures = {},
            onViewPremiumFeature = {},
            onLoadPremiumLimit = {},
            onLoadStarBalance = {},
            onLoadStarTransactions = { _, _ -> },
            onLoadBusinessLinks = {},
            onCreateBusinessLink = {},
            onDeleteBusinessLink = {}
        )
    }
}

@Preview(name = "Stories", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun StoriesScreenPreview() {
    AiTelegramTheme {
        StoriesScreen(
            stories = previewStories,
            chats = previewDirectoryChats,
            operationMessage = "Active stories loaded",
            onLoadActiveStories = {},
            onOpenStory = { _, _ -> },
            onPostStory = { _, _, _, _ -> },
            onDeleteStory = { _, _ -> },
            onRefreshStories = {},
            onDownloadStoryMedia = { _, _ -> }
        )
    }
}

@Preview(name = "Bots", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun BotPlatformScreenPreview() {
    AiTelegramTheme {
        BotPlatformScreen(
            chats = previewDirectoryChats,
            commands = previewBotCommands,
            menuButtons = previewBotMenuButtons,
            inlineResults = previewInlineResults,
            webAppUrl = TelegramWebAppUrl(botUserId = 501L, url = "https://example.com/app"),
            operationMessage = "Bot tools are available",
            webAppDataActionsEnabled = false,
            onSearchBot = {},
            onRefreshBots = {},
            onStartBot = { _, _, _ -> },
            onLoadCommands = {},
            onLoadMenuButton = {},
            onInlineQuery = { _, _, _, _ -> },
            onSendInlineResult = { _, _, _, _ -> },
            onClickCallback = { _, _, _ -> },
            onOpenWebApp = { _, _, _ -> },
            onSendWebAppData = { _, _, _ -> }
        )
    }
}

@Preview(name = "Secret chats", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun SecretChatDirectoryScreenPreview() {
    AiTelegramTheme {
        SecretChatDirectoryScreen(
            secretChats = previewDirectoryChats.filter { it.type.contains("secret", ignoreCase = true) },
            contacts = previewDirectoryContacts,
            operationMessage = "Secret chat preview",
            onOpenChat = {},
            onCreateSecretChat = {},
            onRefreshContacts = {},
            onRefreshSecretChats = {}
        )
    }
}

@Preview(name = "Channels", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun ChannelDirectoryScreenPreview() {
    AiTelegramTheme {
        ChannelDirectoryScreen(
            channels = previewDirectoryChats.filter { it.type.contains("channel", ignoreCase = true) },
            operationMessage = "Channel list refreshed",
            onOpenChat = {},
            onRefreshChannels = {},
            onSearchChannels = {},
            onJoinChannel = {},
            onCreateChannel = { _, _ -> },
            managementActions = previewDirectoryManagementActions
        )
    }
}

@Preview(name = "Tools", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun TelegramToolsScreenPreview() {
    AiTelegramTheme {
        TelegramToolsScreen(
            chats = previewDirectoryChats,
            contacts = previewDirectoryContacts,
            operationMessage = "Discovery tools ready",
            onGlobalSearch = {},
            onJoinChannel = {},
            onSendMessage = { _, _ -> },
            onCreatePrivateChat = {},
            onCreateSecretChat = {},
            onBlockUser = {},
            onUnblockUser = {},
            onAddContact = { _, _, _, _ -> },
            onDeleteContact = {},
            onCreateGroup = { _, _ -> },
            onCreateChannel = { _, _ -> },
            onCreateForumGroup = { _, _ -> },
            onSetChatTitle = { _, _ -> },
            onSetChatDescription = { _, _ -> },
            onSetChatAutoDeleteTime = { _, _ -> },
            onSetChatSlowMode = { _, _ -> },
            onSetChatPermissions = { _, _ -> },
            onCreateChatInviteLink = { _, _ -> },
            onRevokeChatInviteLink = { _, _ -> },
            onAddChatMember = { _, _ -> },
            onRemoveChatMember = { _, _ -> },
            onBanChatMember = { _, _ -> },
            onUnbanChatMember = { _, _ -> },
            onSetChatDraft = { _, _ -> },
            onClearChatDraft = {},
            onPinChat = {},
            onUnpinChat = {},
            onUnpinAllMessages = {},
            onMuteChat = { _, _ -> },
            onUnmuteChat = {},
            onArchiveChat = {},
            onUnarchiveChat = {},
            onMarkChatRead = {},
            onMarkChatUnread = {},
            onClearChatUnreadMark = {},
            onClearChatHistory = {},
            onLeaveChat = {},
            onOpenCache = {},
            onRefreshContacts = {}
        )
    }
}

private val previewDirectoryChats = listOf(
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
        id = 1004L,
        title = "Secret design review",
        type = "secret",
        unreadCount = 1,
        lastMessagePreview = "Encrypted preview conversation.",
        updatedAtMillis = 1_780_000_360_000L
    )
)

private val previewDirectoryContacts = listOf(
    TelegramSender("sample:linh", "Linh Nguyen", "user", 1_780_000_000_000L),
    TelegramSender("sample:minh", "Minh Tran", "user", 1_780_000_100_000L),
    TelegramSender("sample:bot", "Preview Bot", "bot", 1_780_000_200_000L)
)

private val previewPrivacyRules = listOf(
    TelegramPrivacyRuleSummary(TelegramPrivacySetting.LastSeen, TelegramPrivacyPreset.Contacts),
    TelegramPrivacyRuleSummary(TelegramPrivacySetting.ProfilePhoto, TelegramPrivacyPreset.Everybody),
    TelegramPrivacyRuleSummary(TelegramPrivacySetting.PhoneNumber, TelegramPrivacyPreset.Nobody)
)

private val previewSessions = listOf(
    TelegramActiveSession(
        id = 2001L,
        applicationName = "AiTelegramAndroid",
        applicationVersion = "0.1.0",
        deviceModel = "Pixel Preview",
        platform = "Android",
        ip = "192.0.2.12",
        country = "VN",
        isCurrent = true,
        lastActiveDate = 1_780_000_000
    ),
    TelegramActiveSession(
        id = 2002L,
        applicationName = "Telegram Desktop",
        applicationVersion = "5.0",
        deviceModel = "Windows",
        platform = "Desktop",
        ip = "192.0.2.24",
        country = "US",
        lastActiveDate = 1_779_990_000
    )
)

private val previewPremiumFeatures = listOf(
    TelegramPremiumFeatureInfo(
        type = TelegramPremiumFeature.Stories.name,
        title = "Stories",
        description = "Priority story tools and longer visibility."
    ),
    TelegramPremiumFeatureInfo(
        type = TelegramPremiumFeature.Business.name,
        title = "Business",
        description = "Business links and profile automation."
    )
)

private val previewPremiumLimits = listOf(
    TelegramPremiumLimitSummary(TelegramPremiumLimitType.PinnedChatCount, defaultValue = 5, premiumValue = 10),
    TelegramPremiumLimitSummary(TelegramPremiumLimitType.CaptionLength, defaultValue = 1024, premiumValue = 4096)
)

private val previewStarTransactions = listOf(
    TelegramStarTransaction("stars-1", amount = 120, title = "Story boost", description = "Preview transaction"),
    TelegramStarTransaction("stars-2", amount = -50, title = "Gift", description = "Sent to a contact")
)

private val previewBusinessLinks = listOf(
    TelegramBusinessChatLink(
        link = "https://t.me/example?start=preview",
        text = "Open preview support",
        title = "Support"
    )
)

private val previewStories = listOf(
    TelegramStory(
        chatId = 1001L,
        id = 11,
        caption = "Preview story with a translated caption.",
        kind = MessageKind.Text,
        dateMillis = 1_780_000_000_000L,
        state = TelegramStoryState.Active
    ),
    TelegramStory(
        chatId = 1002L,
        id = 12,
        caption = "Pinned group update.",
        kind = MessageKind.Image,
        dateMillis = 1_780_000_200_000L,
        isPinned = true,
        state = TelegramStoryState.Active
    )
)

private val previewBotCommands = listOf(
    TelegramBotCommand(botUserId = 501L, command = "start", description = "Start the bot"),
    TelegramBotCommand(botUserId = 501L, command = "help", description = "Show help")
)

private val previewBotMenuButtons = listOf(
    TelegramBotMenuButton(botUserId = 501L, type = "web_app", text = "Open app", url = "https://example.com/app")
)

private val previewInlineResults = TelegramInlineBotResults(
    botUserId = 501L,
    queryId = 9001L,
    nextOffset = "",
    results = listOf(
        TelegramInlineBotResult(
            id = "result-1",
            type = "article",
            title = "Preview result",
            description = "Inline result content"
        )
    )
)

private val previewDirectoryManagementActions = ChatManagementActions(
    onSetChatTitle = { _, _ -> },
    onSetChatDescription = { _, _ -> },
    onSetChatAutoDeleteTime = { _, _ -> },
    onSetChatSlowMode = { _, _ -> },
    onSetChatPermissions = { _, _ -> },
    onCreateChatInviteLink = { _, _ -> },
    onRevokeChatInviteLink = { _, _ -> },
    onAddChatMember = { _, _ -> },
    onRemoveChatMember = { _, _ -> },
    onBanChatMember = { _, _ -> },
    onUnbanChatMember = { _, _ -> },
    onPromoteChatMember = { _, _, _ -> },
    onDemoteChatMember = { _, _ -> },
    onPinChat = {},
    onUnpinChat = {},
    onUnpinAllMessages = {},
    onMuteChat = { _, _ -> },
    onUnmuteChat = {},
    onArchiveChat = {},
    onUnarchiveChat = {},
    onMarkChatRead = {},
    onMarkChatUnread = {},
    onClearChatUnreadMark = {},
    onClearChatHistory = {},
    onLeaveChat = {}
)

