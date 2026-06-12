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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import java.io.File


@Composable
fun IconActionButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false
) {
    val backgroundColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .size(AiThemeTokens.ActionIconSize)
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        SafeActionIcon(
            iconRes = iconRes,
            contentDescription = contentDescription,
            modifier = Modifier.size(AiThemeTokens.IconSize),
            tint = contentColor
        )
    }
}

@Composable
fun CompactActionButton(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false
) {
    val backgroundColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .heightIn(min = AiThemeTokens.ControlHeight)
            .widthIn(min = AiThemeTokens.ControlHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.RowPaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SafeActionIcon(
            iconRes = iconRes,
            contentDescription = label,
            modifier = Modifier.size(AiThemeTokens.ControlIconSize),
            tint = contentColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = AiThemeTokens.OutlineAlpha))
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = AiThemeTokens.SearchHeight)
                .padding(start = AiThemeTokens.RowPaddingHorizontal, end = AiThemeTokens.DenseSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SafeActionIcon(
                iconRes = R.drawable.ic_ai_search,
                contentDescription = placeholder,
                modifier = Modifier.size(AiThemeTokens.IconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(AiThemeTokens.CompactSpacing))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    errorBorderColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                placeholder = {
                    Text(
                        text = placeholder,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatusBanner(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.24f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.ListSpacing),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
fun AppListItem(
    title: String,
    subtitle: String?,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    primary: Boolean = false
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = AiThemeTokens.OutlineAlpha)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = AiThemeTokens.ListItemMinHeight)
                .padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.RowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LeadingIconTile(
                iconRes = iconRes,
                contentDescription = title,
                containerColor = if (primary) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (primary) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
            Spacer(Modifier.width(AiThemeTokens.SectionSpacing))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailingText?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.width(AiThemeTokens.CompactSpacing))
            }
            SafeActionIcon(
                iconRes = R.drawable.ic_ai_chevron_right,
                contentDescription = title,
                modifier = Modifier.size(AiThemeTokens.ControlIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SafeActionIcon(
    iconRes: Int,
    contentDescription: String,
    modifier: Modifier,
    tint: androidx.compose.ui.graphics.Color
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AiThemeTokens.OutlineAlpha),
    contentPadding: androidx.compose.ui.unit.Dp = AiThemeTokens.CardPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(AiThemeTokens.ListSpacing),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}

@Composable
fun LeadingIconTile(
    iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = AiThemeTokens.IconTileSize,
    iconSize: androidx.compose.ui.unit.Dp = AiThemeTokens.IconSize,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = AiThemeTokens.SubtleContainerAlpha),
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        modifier = modifier.size(size),
        color = containerColor,
        shape = MaterialTheme.shapes.medium
    ) {
        Box(contentAlignment = Alignment.Center) {
            SafeActionIcon(
                iconRes = iconRes,
                contentDescription = contentDescription ?: "",
                modifier = Modifier.size(iconSize),
                tint = contentColor
            )
        }
    }
}

@Composable
fun StatsSummaryCard(
    title: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onRefresh: (() -> Unit)? = null,
    refreshContentDescription: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = AiThemeTokens.OutlineAlpha))
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = AiThemeTokens.ListItemMinHeight)
                .padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.CompactSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LeadingIconTile(
                iconRes = iconRes,
                contentDescription = title,
                size = AiThemeTokens.CompactIconButtonSize,
                iconSize = AiThemeTokens.ControlIconSize,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(AiThemeTokens.ListSpacing))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null || onRefresh != null) {
                Spacer(Modifier.width(AiThemeTokens.CompactSpacing))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing)
                ) {
                    trailing?.invoke(this)
                    onRefresh?.let { refresh ->
                        IconActionButton(
                            iconRes = R.drawable.ic_action_refresh,
                            contentDescription = refreshContentDescription ?: stringResource(R.string.refresh),
                            onClick = refresh
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureHero(
    title: String,
    subtitle: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
    refreshContentDescription: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    StatsSummaryCard(
        title = title,
        subtitle = subtitle,
        iconRes = iconRes,
        modifier = modifier,
        onRefresh = onRefresh,
        refreshContentDescription = refreshContentDescription,
        trailing = trailing
    )
}

@Composable
fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AiThemeTokens.SubtleContainerAlpha),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = AiThemeTokens.ChipPaddingHorizontal,
                vertical = AiThemeTokens.ChipPaddingVertical
            ),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun rememberStableAvatarColor(seed: String): Color {
    return remember(seed) {
        AiThemeTokens.AvatarColors[Math.floorMod(seed.hashCode(), AiThemeTokens.AvatarColors.size)]
    }
}

@Composable
fun SenderListRow(sender: TelegramSender, actionLabel: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = AiThemeTokens.ListItemMinHeight)
                .padding(AiThemeTokens.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(sender.displayName)
            Spacer(Modifier.width(AiThemeTokens.SectionSpacing))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)) {
                Text(
                    sender.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    sender.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconActionButton(
                iconRes = android.R.drawable.ic_dialog_email,
                contentDescription = actionLabel,
                onClick = onClick,
                primary = true
            )
        }
    }
}

@Composable
fun ChatListRow(chat: TelegramChat, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_row_${chat.id}")
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = AiThemeTokens.ChatRowMinHeight)
                .padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.RowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(chat.title)
            Spacer(Modifier.width(AiThemeTokens.SectionSpacing))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.FineSpacing)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.title,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (chat.unreadCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = chat.unreadCount.toString(),
                                modifier = Modifier.padding(horizontal = AiThemeTokens.BadgePaddingHorizontal, vertical = AiThemeTokens.BadgePaddingVertical),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Text(
                    text = chat.activeAction.ifBlank {
                        chat.lastMessagePreview.ifBlank { stringResource(R.string.no_message_preview) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (chat.activeAction.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun TelegramChat.isChannelChat(): Boolean {
    return type.equals("channel", ignoreCase = true) ||
        type.contains("channel", ignoreCase = true)
}

fun TelegramChat.isGroupChat(): Boolean {
    return type.equals("basic_group", ignoreCase = true) ||
        type.equals("supergroup", ignoreCase = true) ||
        type.equals("group", ignoreCase = true) ||
        type.contains("group", ignoreCase = true)
}

fun TelegramChat.isSecretChat(): Boolean {
    return type.equals("secret", ignoreCase = true) ||
        type.contains("secret", ignoreCase = true)
}

fun TelegramSender.telegramUserIdOrNull(): Long? {
    return id.removePrefix("user:").toLongOrNull()
}

@Composable
fun EmptyState(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = AiThemeTokens.OutlineAlpha))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = AiThemeTokens.EmptyStatePadding,
                vertical = AiThemeTokens.ScreenBottomPadding
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

