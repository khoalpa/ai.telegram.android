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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import java.io.File


@Composable
fun DonateScreen(
    billingState: DonationBillingState,
    showPlayBilling: Boolean,
    showVietQrDonation: Boolean,
    onRefresh: () -> Unit,
    onDonate: (DonationProduct) -> Unit
) {
    var showVietQr by remember(showVietQrDonation) {
        mutableStateOf(false)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = AiThemeTokens.ScreenPadding,
                top = AiThemeTokens.ScreenTopPadding,
                end = AiThemeTokens.ScreenPadding,
                bottom = AiThemeTokens.ScreenBottomPadding
            ),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
    ) {
        DonateHero(
            onRefresh = onRefresh,
            refreshEnabled = !billingState.loading
        )

        if (showPlayBilling) {
            DonateSection(
                title = stringResource(R.string.donate_play_billing),
                iconRes = R.drawable.ic_ai_donate
            ) {
                billingState.message?.let {
                    StatusBanner(text = it)
                }
                if (billingState.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (billingState.products.isEmpty()) {
                    EmptyState(stringResource(R.string.donate_no_products))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        billingState.products.forEach { product ->
                            DonationProductRow(
                                product = product,
                                enabled = billingState.ready && !billingState.loading,
                                onClick = { onDonate(product) }
                            )
                        }
                    }
                }
                CompactActionButton(
                    label = stringResource(R.string.donate_refresh),
                    iconRes = R.drawable.ic_action_refresh,
                    onClick = onRefresh,
                    enabled = !billingState.loading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (showVietQrDonation) {
            DonateSection(
                title = stringResource(R.string.donate_qr),
                iconRes = R.drawable.ic_ai_cache
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { showVietQr = !showVietQr }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.donate_vietqr_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        painter = painterResource(
                            if (showVietQr) R.drawable.ic_ai_hidden else R.drawable.ic_ai_chevron_right
                        ),
                        contentDescription = stringResource(R.string.donate_qr),
                        modifier = Modifier.size(AiThemeTokens.IconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DonateAccountBlock()
                if (showVietQr) {
                    VietQrPreview()
                } else {
                    CompactActionButton(
                        label = stringResource(R.string.donate_qr),
                        iconRes = R.drawable.ic_ai_chevron_right,
                        onClick = { showVietQr = true },
                        modifier = Modifier.fillMaxWidth(),
                        primary = true
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun DonateHero(
    onRefresh: () -> Unit,
    refreshEnabled: Boolean
) {
    FeatureHero(
        title = stringResource(R.string.donate_title),
        subtitle = stringResource(R.string.donate_message),
        iconRes = R.drawable.ic_ai_donate,
        onRefresh = onRefresh.takeIf { refreshEnabled },
        refreshContentDescription = stringResource(R.string.donate_refresh)
    )
}

@Composable
private fun DonateSection(
    title: String,
    iconRes: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.SectionSpacing)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LeadingIconTile(
                    iconRes = iconRes,
                    contentDescription = title,
                    size = 36.dp,
                    iconSize = 19.dp
                )
                Spacer(Modifier.width(10.dp))
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
private fun DonationProductRow(
    product: DonationProduct,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.18f else 0.06f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = product.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = product.productId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = product.price.ifBlank { product.title },
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_ai_chevron_right),
                contentDescription = product.title,
                modifier = Modifier.size(AiThemeTokens.IconSize),
                tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DonateAccountBlock() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = stringResource(R.string.donate_vietqr_account),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VietQrPreview() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.58f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    ) {
        Image(
            painter = painterResource(R.drawable.vietqr_donation),
            contentDescription = stringResource(R.string.donate_vietqr_content_description),
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .padding(10.dp)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Fit
        )
    }
}

@Preview(name = "Donate", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun DonateScreenPreview() {
    AiTelegramTheme {
        DonateScreen(
            billingState = DonationBillingState(
                ready = false,
                loading = false,
                message = "VietQR donation preview"
            ),
            showPlayBilling = false,
            showVietQrDonation = true,
            onRefresh = {},
            onDonate = {}
        )
    }
}

