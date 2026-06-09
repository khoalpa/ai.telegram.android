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
fun ContactsScreen(
    contacts: List<TelegramSender>,
    operationMessage: String?,
    onRefreshContacts: () -> Unit,
    onOpenPrivateChat: (TelegramSender) -> Unit
) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        onRefreshContacts()
    }
    val filteredContacts = remember(contacts, query) {
        val trimmedQuery = query.trim()
        contacts.filter {
            trimmedQuery.isBlank() || it.displayName.contains(trimmedQuery, ignoreCase = true)
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
            ContactsHero(
                totalContacts = contacts.size,
                filteredContacts = filteredContacts.size,
                onRefreshContacts = onRefreshContacts
            )
        }
        operationNoticeItem(operationMessage)
        item {
            DirectorySearchCard(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.contacts_search_hint)
            )
        }
        if (filteredContacts.isEmpty()) {
            item {
                ContactsEmptyState(query = query)
            }
        } else {
            items(filteredContacts, key = { it.id }) { sender ->
                SenderListRow(sender = sender, actionLabel = stringResource(R.string.create_private_chat)) {
                    onOpenPrivateChat(sender)
                }
            }
        }
        item { Spacer(Modifier.height(DirectoryBottomInset)) }
    }
}

@Composable
private fun ContactsHero(
    totalContacts: Int,
    filteredContacts: Int,
    onRefreshContacts: () -> Unit
) {
    StatsSummaryCard(
        title = stringResource(R.string.contacts_title),
        iconRes = R.drawable.ic_ai_contacts,
        onRefresh = onRefreshContacts,
        refreshContentDescription = stringResource(R.string.sync_contacts)
    ) {
        MetaChip(text = "$filteredContacts/$totalContacts")
    }
}

@Composable
private fun ContactsEmptyState(query: String) {
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
                        painter = painterResource(R.drawable.ic_ai_contacts),
                        contentDescription = null,
                        modifier = Modifier.size(AiThemeTokens.LargeIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = if (query.isBlank()) {
                    stringResource(R.string.contacts_empty)
                } else {
                    stringResource(R.string.no_chats_found)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (query.isBlank()) {
                Text(
                    text = stringResource(R.string.sync_contacts),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(name = "Contacts", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun ContactsScreenPreview() {
    AiTelegramTheme {
        ContactsScreen(
            contacts = previewContacts,
            operationMessage = "Contacts loaded",
            onRefreshContacts = {},
            onOpenPrivateChat = {}
        )
    }
}

private val previewContacts = listOf(
    TelegramSender("sample:linh", "Linh Nguyen", "user", 1_780_000_000_000L),
    TelegramSender("sample:minh", "Minh Tran", "user", 1_780_000_100_000L),
    TelegramSender("sample:bot", "Preview Bot", "bot", 1_780_000_200_000L)
)

