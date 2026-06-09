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
import ai.telegram.android.data.telegram.TelegramActiveSession
import ai.telegram.android.data.telegram.TelegramBotCommand
import ai.telegram.android.data.telegram.TelegramBotMenuButton
import ai.telegram.android.data.telegram.TelegramBusinessChatLink
import ai.telegram.android.data.telegram.TelegramCall
import ai.telegram.android.data.telegram.TelegramCallState
import ai.telegram.android.data.telegram.TelegramChatPermissionPreset
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
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
@Composable
fun GroupDirectoryScreen(
    groups: List<TelegramChat>,
    operationMessage: String?,
    onOpenChat: (TelegramChat) -> Unit,
    onRefreshGroups: () -> Unit,
    onCreateGroup: (List<Long>, String) -> Unit,
    onCreateForumGroup: (String, String) -> Unit,
    managementActions: ChatManagementActions
) {
    var query by remember { mutableStateOf("") }
    var groupTitle by remember { mutableStateOf("") }
    var groupUserIds by remember { mutableStateOf("") }
    var forumTitle by remember { mutableStateOf("") }
    var forumDescription by remember { mutableStateOf("") }
    val filteredGroups = remember(groups, query) {
        val trimmed = query.trim()
        groups.filter { trimmed.isBlank() || it.title.contains(trimmed, ignoreCase = true) }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AiThemeTokens.ScreenPadding),
        contentPadding = PaddingValues(top = AiThemeTokens.ScreenTopPadding),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
    ) {
        item {
            GroupHero(
                totalGroups = groups.size,
                unreadGroups = groups.count { it.unreadCount > 0 },
                filteredGroups = filteredGroups.size,
                onRefresh = onRefreshGroups
            )
        }
        operationNoticeItem(operationMessage)
        item {
            DirectorySearchCard(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.search_hint)
            )
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.create_group),
                iconRes = R.drawable.ic_ai_groups
            ) {
                ActionTextField(groupTitle, { groupTitle = it }, stringResource(R.string.group_title_hint))
                ActionTextField(groupUserIds, { groupUserIds = it }, stringResource(R.string.group_user_ids_hint))
                CompactActionButton(
                    label = stringResource(R.string.create_group),
                    iconRes = R.drawable.ic_ai_groups,
                    onClick = {
                        onCreateGroup(parseUserIds(groupUserIds), groupTitle)
                        groupTitle = ""
                        groupUserIds = ""
                    },
                    enabled = groupTitle.isNotBlank() && parseUserIds(groupUserIds).isNotEmpty(),
                    primary = true
                )
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.create_forum_group),
                iconRes = R.drawable.ic_ai_groups
            ) {
                ActionTextField(forumTitle, { forumTitle = it }, stringResource(R.string.forum_title_hint))
                ActionTextField(
                    forumDescription,
                    { forumDescription = it },
                    stringResource(R.string.forum_description_hint)
                )
                CompactActionButton(
                    label = stringResource(R.string.create_forum_group),
                    iconRes = R.drawable.ic_ai_groups,
                    onClick = {
                        onCreateForumGroup(forumTitle, forumDescription)
                        forumTitle = ""
                        forumDescription = ""
                    },
                    enabled = forumTitle.isNotBlank(),
                    primary = true
                )
            }
        }
        item {
            ChatManagementPanel(
                chats = filteredGroups,
                title = stringResource(R.string.group_management_title),
                emptyText = stringResource(R.string.groups_empty),
                includeMembers = true,
                includePermissions = true,
                includeSlowMode = true,
                actions = managementActions
            )
        }
        if (filteredGroups.isEmpty()) {
            item {
                GroupEmptyState(query = query)
            }
        } else {
            items(filteredGroups, key = { it.id }) { chat ->
                ChatListRow(chat = chat, onClick = { onOpenChat(chat) })
            }
        }
        item { Spacer(Modifier.height(DirectoryBottomInset)) }
    }
}

@Composable
internal fun ChatManagementPanel(
    chats: List<TelegramChat>,
    title: String,
    emptyText: String,
    includeMembers: Boolean,
    includePermissions: Boolean,
    includeSlowMode: Boolean,
    actions: ChatManagementActions
) {
    var selectedChatId by rememberSaveable(chats.map { it.id }.joinToString(",")) {
        mutableStateOf(chats.firstOrNull()?.id?.toString().orEmpty())
    }
    var chatTitle by remember { mutableStateOf("") }
    var chatDescription by remember { mutableStateOf("") }
    var inviteLinkName by remember { mutableStateOf("") }
    var inviteLinkToRevoke by remember { mutableStateOf("") }
    var memberUserId by remember { mutableStateOf("") }
    var adminTitle by remember { mutableStateOf("") }
    val selectedChatIdLong = selectedChatId.toLongOrNull()
    val memberUserIdLong = memberUserId.toLongOrNull()
    val selectedChat = chats.firstOrNull { it.id == selectedChatIdLong }

    TelegramActionCard(
        title = title,
        iconRes = R.drawable.ic_ai_settings
    ) {
        if (chats.isEmpty()) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TelegramActionCard
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
            contentPadding = PaddingValues(end = 12.dp)
        ) {
            items(chats, key = { it.id }) { chat ->
                FilterChip(
                    selected = chat.id == selectedChatIdLong,
                    onClick = { selectedChatId = chat.id.toString() },
                    label = {
                        Text(
                            text = chat.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
        selectedChat?.let { chat ->
            Text(
                text = stringResource(R.string.selected_chat_management, chat.title, chat.id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ActionTextField(chatTitle, { chatTitle = it }, stringResource(R.string.chat_title_hint))
        ActionTextField(chatDescription, { chatDescription = it }, stringResource(R.string.chat_description_hint))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
            CompactActionButton(
                label = stringResource(R.string.rename_chat),
                iconRes = android.R.drawable.ic_menu_edit,
                onClick = {
                    selectedChatIdLong?.let { actions.onSetChatTitle(it, chatTitle) }
                    chatTitle = ""
                },
                enabled = selectedChatIdLong != null && chatTitle.isNotBlank()
            )
            CompactActionButton(
                label = stringResource(R.string.update_chat_description),
                iconRes = android.R.drawable.ic_menu_info_details,
                onClick = {
                    selectedChatIdLong?.let { actions.onSetChatDescription(it, chatDescription) }
                    chatDescription = ""
                },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.auto_delete_off),
                iconRes = android.R.drawable.ic_menu_delete,
                onClick = { selectedChatIdLong?.let { actions.onSetChatAutoDeleteTime(it, AUTO_DELETE_OFF_SECONDS) } },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.auto_delete_1d),
                iconRes = android.R.drawable.ic_menu_delete,
                onClick = { selectedChatIdLong?.let { actions.onSetChatAutoDeleteTime(it, AUTO_DELETE_ONE_DAY_SECONDS) } },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.auto_delete_1w),
                iconRes = android.R.drawable.ic_menu_delete,
                onClick = { selectedChatIdLong?.let { actions.onSetChatAutoDeleteTime(it, AUTO_DELETE_ONE_WEEK_SECONDS) } },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.auto_delete_1m),
                iconRes = android.R.drawable.ic_menu_delete,
                onClick = { selectedChatIdLong?.let { actions.onSetChatAutoDeleteTime(it, AUTO_DELETE_ONE_MONTH_SECONDS) } },
                enabled = selectedChatIdLong != null
            )
        }

        ActionTextField(inviteLinkName, { inviteLinkName = it }, stringResource(R.string.chat_invite_link_name_hint))
        ActionTextField(inviteLinkToRevoke, { inviteLinkToRevoke = it }, stringResource(R.string.chat_invite_link_revoke_hint))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
            CompactActionButton(
                label = stringResource(R.string.create_invite_link),
                iconRes = android.R.drawable.ic_menu_share,
                onClick = { selectedChatIdLong?.let { actions.onCreateChatInviteLink(it, inviteLinkName) } },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.revoke_invite_link),
                iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                onClick = {
                    selectedChatIdLong?.let { actions.onRevokeChatInviteLink(it, inviteLinkToRevoke) }
                    inviteLinkToRevoke = ""
                },
                enabled = selectedChatIdLong != null && inviteLinkToRevoke.isNotBlank()
            )
        }

        if (includeMembers) {
            ActionTextField(memberUserId, { memberUserId = it }, stringResource(R.string.chat_member_user_id_hint))
            ActionTextField(adminTitle, { adminTitle = it }, stringResource(R.string.admin_title_hint))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                CompactActionButton(
                    label = stringResource(R.string.add_chat_member),
                    iconRes = android.R.drawable.ic_menu_add,
                    onClick = {
                        if (selectedChatIdLong != null && memberUserIdLong != null) {
                            actions.onAddChatMember(selectedChatIdLong, memberUserIdLong)
                        }
                    },
                    enabled = selectedChatIdLong != null && memberUserIdLong != null
                )
                CompactActionButton(
                    label = stringResource(R.string.remove_chat_member),
                    iconRes = android.R.drawable.ic_menu_delete,
                    onClick = {
                        if (selectedChatIdLong != null && memberUserIdLong != null) {
                            actions.onRemoveChatMember(selectedChatIdLong, memberUserIdLong)
                        }
                    },
                    enabled = selectedChatIdLong != null && memberUserIdLong != null
                )
                CompactActionButton(
                    label = stringResource(R.string.ban_chat_member),
                    iconRes = android.R.drawable.ic_lock_lock,
                    onClick = {
                        if (selectedChatIdLong != null && memberUserIdLong != null) {
                            actions.onBanChatMember(selectedChatIdLong, memberUserIdLong)
                        }
                    },
                    enabled = selectedChatIdLong != null && memberUserIdLong != null
                )
                CompactActionButton(
                    label = stringResource(R.string.unban_chat_member),
                    iconRes = android.R.drawable.ic_menu_revert,
                    onClick = {
                        if (selectedChatIdLong != null && memberUserIdLong != null) {
                            actions.onUnbanChatMember(selectedChatIdLong, memberUserIdLong)
                        }
                    },
                    enabled = selectedChatIdLong != null && memberUserIdLong != null
                )
                CompactActionButton(
                    label = stringResource(R.string.promote_chat_member),
                    iconRes = android.R.drawable.ic_menu_manage,
                    onClick = {
                        if (selectedChatIdLong != null && memberUserIdLong != null) {
                            actions.onPromoteChatMember(selectedChatIdLong, memberUserIdLong, adminTitle)
                        }
                    },
                    enabled = selectedChatIdLong != null && memberUserIdLong != null
                )
                CompactActionButton(
                    label = stringResource(R.string.demote_chat_member),
                    iconRes = android.R.drawable.ic_menu_revert,
                    onClick = {
                        if (selectedChatIdLong != null && memberUserIdLong != null) {
                            actions.onDemoteChatMember(selectedChatIdLong, memberUserIdLong)
                        }
                    },
                    enabled = selectedChatIdLong != null && memberUserIdLong != null
                )
            }
        }

        if (includeSlowMode) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                SlowModeButton(R.string.slow_mode_off, SLOW_MODE_OFF_SECONDS, selectedChatIdLong, actions)
                SlowModeButton(R.string.slow_mode_10s, SLOW_MODE_TEN_SECONDS, selectedChatIdLong, actions)
                SlowModeButton(R.string.slow_mode_30s, SLOW_MODE_THIRTY_SECONDS, selectedChatIdLong, actions)
                SlowModeButton(R.string.slow_mode_1m, SLOW_MODE_ONE_MINUTE_SECONDS, selectedChatIdLong, actions)
                SlowModeButton(R.string.slow_mode_5m, SLOW_MODE_FIVE_MINUTES_SECONDS, selectedChatIdLong, actions)
                SlowModeButton(R.string.slow_mode_15m, SLOW_MODE_FIFTEEN_MINUTES_SECONDS, selectedChatIdLong, actions)
                SlowModeButton(R.string.slow_mode_1h, SLOW_MODE_ONE_HOUR_SECONDS, selectedChatIdLong, actions)
            }
        }

        if (includePermissions) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                PermissionButton(R.string.chat_permissions_read_only, TelegramChatPermissionPreset.READ_ONLY, selectedChatIdLong, actions)
                PermissionButton(R.string.chat_permissions_text_only, TelegramChatPermissionPreset.TEXT_ONLY, selectedChatIdLong, actions)
                PermissionButton(R.string.chat_permissions_media_allowed, TelegramChatPermissionPreset.MEDIA_ALLOWED, selectedChatIdLong, actions)
                PermissionButton(R.string.chat_permissions_full, TelegramChatPermissionPreset.FULL, selectedChatIdLong, actions)
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
            CompactActionButton(
                label = stringResource(R.string.pin_chat),
                iconRes = android.R.drawable.ic_menu_upload,
                onClick = { selectedChatIdLong?.let(actions.onPinChat) },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.unpin_chat),
                iconRes = android.R.drawable.ic_menu_revert,
                onClick = { selectedChatIdLong?.let(actions.onUnpinChat) },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.unpin_all_messages),
                iconRes = android.R.drawable.ic_menu_revert,
                onClick = { selectedChatIdLong?.let(actions.onUnpinAllMessages) },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.mute_chat_1h),
                iconRes = android.R.drawable.ic_lock_silent_mode,
                onClick = { selectedChatIdLong?.let { actions.onMuteChat(it, MUTE_ONE_HOUR_SECONDS) } },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.mute_chat_8h),
                iconRes = android.R.drawable.ic_lock_silent_mode,
                onClick = { selectedChatIdLong?.let { actions.onMuteChat(it, MUTE_EIGHT_HOURS_SECONDS) } },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.unmute_chat),
                iconRes = android.R.drawable.ic_lock_silent_mode_off,
                onClick = { selectedChatIdLong?.let(actions.onUnmuteChat) },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.archive_chat),
                iconRes = android.R.drawable.ic_menu_save,
                onClick = { selectedChatIdLong?.let(actions.onArchiveChat) },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.unarchive_chat),
                iconRes = android.R.drawable.ic_menu_upload,
                onClick = { selectedChatIdLong?.let(actions.onUnarchiveChat) },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.mark_chat_read),
                iconRes = android.R.drawable.ic_menu_view,
                onClick = { selectedChatIdLong?.let(actions.onMarkChatRead) },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.mark_chat_unread),
                iconRes = android.R.drawable.ic_dialog_email,
                onClick = { selectedChatIdLong?.let(actions.onMarkChatUnread) },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.clear_chat_unread_mark),
                iconRes = android.R.drawable.ic_menu_view,
                onClick = { selectedChatIdLong?.let(actions.onClearChatUnreadMark) },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.clear_chat_history),
                iconRes = android.R.drawable.ic_menu_delete,
                onClick = { selectedChatIdLong?.let(actions.onClearChatHistory) },
                enabled = selectedChatIdLong != null
            )
            CompactActionButton(
                label = stringResource(R.string.leave_chat),
                iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                onClick = { selectedChatIdLong?.let(actions.onLeaveChat) },
                enabled = selectedChatIdLong != null
            )
        }
    }
}

@Composable
private fun SlowModeButton(
    labelRes: Int,
    seconds: Int,
    chatId: Long?,
    actions: ChatManagementActions
) {
    CompactActionButton(
        label = stringResource(labelRes),
        iconRes = android.R.drawable.ic_menu_recent_history,
        onClick = { chatId?.let { actions.onSetChatSlowMode(it, seconds) } },
        enabled = chatId != null
    )
}

@Composable
private fun PermissionButton(
    labelRes: Int,
    preset: TelegramChatPermissionPreset,
    chatId: Long?,
    actions: ChatManagementActions
) {
    CompactActionButton(
        label = stringResource(labelRes),
        iconRes = android.R.drawable.ic_menu_manage,
        onClick = { chatId?.let { actions.onSetChatPermissions(it, preset) } },
        enabled = chatId != null
    )
}

@Composable
private fun GroupHero(
    totalGroups: Int,
    unreadGroups: Int,
    filteredGroups: Int,
    onRefresh: () -> Unit
) {
    StatsSummaryCard(
        title = stringResource(R.string.groups_title),
        iconRes = R.drawable.ic_ai_groups,
        onRefresh = onRefresh
    ) {
        MetaChip(text = stringResource(R.string.chat_filter_groups) + " $totalGroups")
        MetaChip(text = stringResource(R.string.chat_filter_unread) + " $unreadGroups")
        MetaChip(text = stringResource(R.string.filter) + " $filteredGroups")
    }
}

@Composable
internal fun DirectorySearchCard(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    AppSearchField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder
    )
}

@Composable
private fun GroupEmptyState(query: String) {
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
                        painter = painterResource(R.drawable.ic_ai_groups),
                        contentDescription = null,
                        modifier = Modifier.size(AiThemeTokens.LargeIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = if (query.isBlank()) {
                    stringResource(R.string.groups_empty)
                } else {
                    stringResource(R.string.no_chats_found)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "Groups", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun GroupDirectoryScreenPreview() {
    AiTelegramTheme {
        GroupDirectoryScreen(
            groups = previewGroupChats,
            operationMessage = "Group tools are available",
            onOpenChat = {},
            onRefreshGroups = {},
            onCreateGroup = { _, _ -> },
            onCreateForumGroup = { _, _ -> },
            managementActions = previewGroupManagementActions
        )
    }
}

private val previewGroupChats = listOf(
    TelegramChat(
        id = 1002L,
        title = "Android Dev",
        type = "group",
        unreadCount = 2,
        lastMessagePreview = "Compose preview fixtures are now in place.",
        updatedAtMillis = 1_780_000_120_000L
    ),
    TelegramChat(
        id = 1005L,
        title = "Mobile Design",
        type = "supergroup",
        unreadCount = 0,
        lastMessagePreview = "Reviewing compact rows for directory tools.",
        updatedAtMillis = 1_780_000_220_000L
    )
)

private val previewGroupManagementActions = ChatManagementActions(
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

