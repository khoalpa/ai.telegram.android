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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import java.io.File


@Composable
fun BlacklistScreen(
    items: List<HiddenContent>,
    version: Int,
    onRemove: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AiThemeTokens.ScreenPadding),
        contentPadding = PaddingValues(
            top = AiThemeTokens.ScreenTopPadding,
            bottom = AiThemeTokens.ScreenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
    ) {
        item {
            HiddenContentHero(hiddenCount = items.size)
        }
        if (items.isEmpty()) {
            item {
                HiddenContentEmptyState()
            }
        } else {
            items(items, key = { "${it.contentHash}-$version" }) { item ->
                HiddenContentRow(
                    item = item,
                    onRemove = { onRemove(item.contentHash) }
                )
            }
        }
    }
}

@Composable
private fun HiddenContentHero(hiddenCount: Int) {
    FeatureHero(
        title = stringResource(R.string.blacklist_title),
        subtitle = stringResource(R.string.privacy_note),
        iconRes = R.drawable.ic_ai_hidden
    ) {
        Spacer(Modifier.width(AiThemeTokens.CompactSpacing))
        AssistChip(
            onClick = {},
            label = { Text(hiddenCount.toString()) }
        )
    }
}

@Composable
private fun HiddenContentEmptyState() {
    AppCard(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LeadingIconTile(
                iconRes = R.drawable.ic_ai_shield,
                contentDescription = stringResource(R.string.blacklist_empty),
                size = AiThemeTokens.HeroIconTileSize,
                iconSize = AiThemeTokens.LargeIconSize,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.blacklist_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HiddenContentRow(
    item: HiddenContent,
    onRemove: () -> Unit
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HiddenContentAvatar(seed = item.contentHash)
            Spacer(Modifier.width(AiThemeTokens.ListSpacing))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing)
            ) {
                Text(
                    text = item.preview.ifBlank { stringResource(R.string.hidden_content) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing),
                    verticalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing)
                ) {
                    MetaChip(text = item.createdAt)
                    MetaChip(text = "${item.hiddenCount}x")
                    MetaChip(text = item.contentHash.take(10))
                }
            }
            Spacer(Modifier.width(AiThemeTokens.ListSpacing))
            CompactActionButton(
                label = stringResource(R.string.remove),
                iconRes = R.drawable.ic_ai_hidden,
                onClick = onRemove
            )
        }
    }
}

@Composable
private fun HiddenContentAvatar(seed: String) {
    val color = rememberStableAvatarColor(seed)
    Surface(
        modifier = Modifier.size(AiThemeTokens.IconButtonSize),
        color = color,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_ai_hidden),
                contentDescription = null,
                modifier = Modifier.size(AiThemeTokens.IconSize),
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}

@Preview(name = "Blacklist", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun BlacklistScreenPreview() {
    AiTelegramTheme {
        BlacklistScreen(
            items = listOf(
                HiddenContent(
                    contentHash = "hash-preview-spam-001",
                    preview = "Limited offer message hidden from chat.",
                    hiddenCount = 3,
                    createdAt = "Today"
                ),
                HiddenContent(
                    contentHash = "hash-preview-spam-002",
                    preview = "Repeated promotional message.",
                    hiddenCount = 1,
                    createdAt = "Yesterday"
                )
            ),
            version = 1,
            onRemove = {}
        )
    }
}
