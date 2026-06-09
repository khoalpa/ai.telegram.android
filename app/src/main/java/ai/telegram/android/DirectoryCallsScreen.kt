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
fun CallsScreen(
    calls: List<TelegramCall>,
    contacts: List<TelegramSender>,
    operationMessage: String?,
    callActionsEnabled: Boolean = false,
    onStartCall: (Long, Boolean) -> Unit,
    onAcceptCall: (Int) -> Unit,
    onDiscardCall: (Int) -> Unit,
    onRefreshContacts: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var query by remember { mutableStateOf("") }
    var userIdInput by remember { mutableStateOf("") }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var pendingCallRequest by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    var callStartInFlight by remember { mutableStateOf(false) }
    val activeCalls = remember(calls) {
        calls.filterNot { it.state == TelegramCallState.Discarded || it.state == TelegramCallState.Error }
    }
    val hasActiveCall = activeCalls.isNotEmpty()
    val hasActiveOrStartingCall = hasActiveCall || callStartInFlight
    LaunchedEffect(hasActiveCall) {
        if (!hasActiveCall) callStartInFlight = false
    }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val pending = pendingCallRequest
        pendingCallRequest = null
        if (pending == null) return@rememberLauncherForActivityResult
        val hasAudio = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCamera = !pending.second ||
            grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasAudio && hasCamera) {
            if (hasActiveOrStartingCall) {
                permissionMessage = resources.getString(R.string.calls_active_call_blocked)
            } else {
                permissionMessage = null
                callStartInFlight = true
                if (callActionsEnabled) onStartCall(pending.first, pending.second)
            }
        } else {
            permissionMessage = resources.getString(R.string.calls_permission_needed)
        }
    }
    fun startCallWithPermissions(userId: Long, isVideo: Boolean) {
        if (!callActionsEnabled) {
            permissionMessage = resources.getString(R.string.calls_actions_disabled)
            return
        }
        if (hasActiveOrStartingCall) {
            permissionMessage = resources.getString(R.string.calls_active_call_blocked)
            return
        }
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (isVideo) add(Manifest.permission.CAMERA)
        }
        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            permissionMessage = null
            callStartInFlight = true
            onStartCall(userId, isVideo)
        } else {
            permissionMessage = null
            pendingCallRequest = userId to isVideo
            callPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    val filteredContacts = remember(contacts, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            contacts.take(16)
        } else {
            contacts.filter { contact ->
                contact.displayName.contains(normalizedQuery, ignoreCase = true) ||
                    contact.id.contains(normalizedQuery, ignoreCase = true)
            }.take(24)
        }
    }
    val callsDisabledMessage = stringResource(R.string.calls_actions_disabled)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AiThemeTokens.ScreenPadding),
        contentPadding = PaddingValues(top = AiThemeTokens.ScreenTopPadding),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
    ) {
        item {
            CallsHero(
                totalCalls = calls.size,
                activeCalls = activeCalls.size,
                onRefresh = onRefreshContacts
            )
        }
        operationNoticeItem(
            permissionMessage ?: if (!callActionsEnabled) {
                callsDisabledMessage
            } else {
                operationMessage
            }
        )
        item {
            DirectorySearchCard(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.calls_search_hint)
            )
        }
        item { SectionHeader(title = stringResource(R.string.active_calls_title)) }
        if (activeCalls.isEmpty()) {
            item { EmptyState(stringResource(R.string.calls_empty)) }
        } else {
            items(activeCalls, key = { it.id }) { call ->
                CallStatusCard(
                    call = call,
                    contactName = contacts.firstOrNull { it.telegramUserIdOrNull() == call.userId }?.displayName,
                    onAcceptCall = onAcceptCall,
                    onDiscardCall = onDiscardCall,
                    actionsEnabled = callActionsEnabled
                )
            }
        }
        if (filteredContacts.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.calls_contacts_title)) }
            items(filteredContacts, key = { it.id }) { contact ->
                val userId = contact.telegramUserIdOrNull()
                CallContactCard(
                    contact = contact,
                    actionsEnabled = callActionsEnabled && userId != null && !hasActiveOrStartingCall,
                    onStartVoiceCall = { userId?.let { startCallWithPermissions(it, false) } },
                    onStartVideoCall = { userId?.let { startCallWithPermissions(it, true) } }
                )
            }
        }
        item {
            TelegramActionCard(
                title = stringResource(R.string.calls_start_title),
                iconRes = R.drawable.ic_ai_call
            ) {
                Text(
                    text = stringResource(R.string.calls_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!callActionsEnabled) {
                    Text(
                        text = stringResource(R.string.calls_actions_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                ActionTextField(userIdInput, { userIdInput = it }, stringResource(R.string.user_id_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
                    val userId = userIdInput.toLongOrNull()
                    CompactActionButton(
                        label = stringResource(R.string.start_voice_call),
                        iconRes = R.drawable.ic_ai_call,
                        onClick = { userId?.let { startCallWithPermissions(it, false) } },
                        enabled = callActionsEnabled && userId != null && !hasActiveOrStartingCall,
                        primary = true
                    )
                    CompactActionButton(
                        label = stringResource(R.string.start_video_call),
                        iconRes = R.drawable.ic_ai_video,
                        onClick = { userId?.let { startCallWithPermissions(it, true) } },
                        enabled = callActionsEnabled && userId != null && !hasActiveOrStartingCall
                    )
                    CompactActionButton(
                        label = stringResource(R.string.sync_contacts),
                        iconRes = R.drawable.ic_ai_contacts,
                        onClick = onRefreshContacts
                    )
                }
            }
        }
        item { Spacer(Modifier.height(DirectoryBottomInset)) }
    }
}

@Composable
private fun CallContactCard(
    contact: TelegramSender,
    actionsEnabled: Boolean,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit
) {
    TelegramActionCard(
        title = contact.displayName,
        iconRes = R.drawable.ic_ai_call
    ) {
        Text(
            text = contact.id,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            CompactActionButton(
                label = stringResource(R.string.start_voice_call),
                iconRes = R.drawable.ic_ai_call,
                onClick = onStartVoiceCall,
                enabled = actionsEnabled,
                primary = true
            )
            CompactActionButton(
                label = stringResource(R.string.start_video_call),
                iconRes = R.drawable.ic_ai_video,
                onClick = onStartVideoCall,
                enabled = actionsEnabled
            )
        }
    }
}

@Composable
private fun CallsHero(
    totalCalls: Int,
    activeCalls: Int,
    onRefresh: () -> Unit
) {
    StatsSummaryCard(
        title = stringResource(R.string.calls_title),
        iconRes = R.drawable.ic_ai_call,
        onRefresh = onRefresh
    ) {
        MetaChip(text = pluralStringResource(R.plurals.calls_total_metric, totalCalls, totalCalls))
        MetaChip(text = pluralStringResource(R.plurals.calls_active_metric, activeCalls, activeCalls))
    }
}

@Composable
private fun CallStatusCard(
    call: TelegramCall,
    contactName: String?,
    onAcceptCall: (Int) -> Unit,
    onDiscardCall: (Int) -> Unit,
    actionsEnabled: Boolean = true
) {
    TelegramActionCard(
        title = contactName ?: stringResource(R.string.call_user_title, call.userId),
        iconRes = if (call.isVideo) R.drawable.ic_ai_video else R.drawable.ic_ai_call
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
            AssistChip(
                onClick = {},
                label = { Text(if (call.isVideo) stringResource(R.string.video_call) else stringResource(R.string.voice_call)) }
            )
            AssistChip(
                onClick = {},
                label = { Text(if (call.isOutgoing) stringResource(R.string.outgoing_call) else stringResource(R.string.incoming_call)) }
            )
            AssistChip(
                onClick = {},
                label = { Text(call.state.label()) }
            )
            if (call.state == TelegramCallState.Pending && call.isOutgoing) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            stringResource(
                                when {
                                    call.isReceived -> R.string.call_pending_received
                                    call.isCreated -> R.string.call_pending_created
                                    else -> R.string.call_pending_not_received
                                }
                            )
                        )
                    }
                )
            }
        }
        if (call.errorMessage.isNotBlank()) {
            Text(
                text = call.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
            CompactActionButton(
                label = stringResource(R.string.accept_call),
                iconRes = R.drawable.ic_ai_call,
                onClick = { onAcceptCall(call.id) },
                enabled = actionsEnabled && !call.isOutgoing && call.state == TelegramCallState.Pending,
                primary = true
            )
            CompactActionButton(
                label = stringResource(R.string.end_call),
                iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                onClick = { onDiscardCall(call.id) },
                enabled = call.state != TelegramCallState.Discarded && call.state != TelegramCallState.HangingUp
            )
        }
    }
}

@Composable
private fun TelegramCallState.label(): String {
    return when (this) {
        TelegramCallState.Pending -> stringResource(R.string.call_state_pending)
        TelegramCallState.ExchangingKeys -> stringResource(R.string.call_state_exchanging_keys)
        TelegramCallState.Ready -> stringResource(R.string.call_state_ready)
        TelegramCallState.HangingUp -> stringResource(R.string.call_state_hanging_up)
        TelegramCallState.Discarded -> stringResource(R.string.call_state_discarded)
        TelegramCallState.Error -> stringResource(R.string.call_state_error)
        TelegramCallState.Unknown -> stringResource(R.string.call_state_unknown)
    }
}

@Preview(name = "Calls", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun CallsScreenPreview() {
    AiTelegramTheme {
        CallsScreen(
            calls = previewCalls,
            contacts = previewCallContacts,
            operationMessage = "Voice calls are ready",
            callActionsEnabled = false,
            onStartCall = { _, _ -> },
            onAcceptCall = {},
            onDiscardCall = {},
            onRefreshContacts = {}
        )
    }
}

private val previewCalls = listOf(
    TelegramCall(
        id = 71,
        userId = 1003L,
        isOutgoing = true,
        isVideo = false,
        state = TelegramCallState.Ready,
        durationSeconds = 185,
        stateLabel = "Connected"
    ),
    TelegramCall(
        id = 72,
        userId = 1002L,
        isOutgoing = false,
        isVideo = true,
        state = TelegramCallState.Discarded,
        durationSeconds = 48,
        stateLabel = "Ended"
    )
)

private val previewCallContacts = listOf(
    TelegramSender("sample:linh", "Linh Nguyen", "user", 1_780_000_000_000L),
    TelegramSender("sample:minh", "Minh Tran", "user", 1_780_000_100_000L)
)
