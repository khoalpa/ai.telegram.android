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
import androidx.compose.material3.AlertDialog
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

enum class CacheClearScope {
    All,
    Translation,
    Media,
    VideoSubtitles,
    TemporaryFiles
}

private enum class CacheModelFilter {
    All,
    Ready,
    Missing
}

@Composable
fun CacheScreen(
    stats: ai.telegram.android.data.TranslationCacheStats,
    mediaPolicy: MediaDownloadPolicy,
    models: List<MlKitTranslationModelInfo>,
    commonPairs: List<TranslationLanguagePair>,
    modelOperationState: ModelOperationUiState,
    onRefreshModels: () -> Unit,
    onDownloadAllCommonPairs: () -> Unit,
    onDownloadPair: (TranslationLanguagePair) -> Unit,
    onDeleteModel: (String) -> Unit,
    onClear: (CacheClearScope) -> Unit
) {
    RedesignedCacheScreen(
        stats = stats,
        mediaPolicy = mediaPolicy,
        models = models,
        commonPairs = commonPairs,
        modelOperationState = modelOperationState,
        onRefreshModels = onRefreshModels,
        onDownloadAllCommonPairs = onDownloadAllCommonPairs,
        onDownloadPair = onDownloadPair,
        onDeleteModel = onDeleteModel,
        onClear = onClear
    )
}

@Composable
private fun RedesignedCacheScreen(
    stats: ai.telegram.android.data.TranslationCacheStats,
    mediaPolicy: MediaDownloadPolicy,
    models: List<MlKitTranslationModelInfo>,
    commonPairs: List<TranslationLanguagePair>,
    modelOperationState: ModelOperationUiState,
    onRefreshModels: () -> Unit,
    onDownloadAllCommonPairs: () -> Unit,
    onDownloadPair: (TranslationLanguagePair) -> Unit,
    onDeleteModel: (String) -> Unit,
    onClear: (CacheClearScope) -> Unit
) {
    val installedCodes = remember(models) { models.map { it.languageCode }.toSet() }
    val modelsByCode = remember(models) { models.associateBy { it.languageCode } }
    val commonPairModelCodes = remember(commonPairs) {
        commonPairs.flatMap { it.requiredModelLanguageCodes }.toSet()
    }
    val standaloneModels = remember(models, commonPairModelCodes) {
        models.filter { it.languageCode !in commonPairModelCodes }
    }
    val readyPairCount = commonPairs.count { pair ->
        pair.requiredModelLanguageCodes.all { it in installedCodes }
    }
    var pendingClearScope by remember { mutableStateOf<CacheClearScope?>(null) }
    var pendingDeleteModel by remember { mutableStateOf<MlKitTranslationModelInfo?>(null) }
    var selectedModelFilterName by rememberSaveable { mutableStateOf(CacheModelFilter.All.name) }
    val selectedModelFilter = remember(selectedModelFilterName) {
        runCatching { CacheModelFilter.valueOf(selectedModelFilterName) }
            .getOrDefault(CacheModelFilter.All)
    }
    val filteredPairs = remember(commonPairs, installedCodes, selectedModelFilter) {
        commonPairs.filter { pair ->
            val ready = pair.requiredModelLanguageCodes.all { it in installedCodes }
            when (selectedModelFilter) {
                CacheModelFilter.All -> true
                CacheModelFilter.Ready -> ready
                CacheModelFilter.Missing -> !ready
            }
        }
    }
    val filteredStandaloneModels = remember(standaloneModels, selectedModelFilter) {
        when (selectedModelFilter) {
            CacheModelFilter.All,
            CacheModelFilter.Ready -> standaloneModels
            CacheModelFilter.Missing -> emptyList()
        }
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
        CacheHero(
            readyPairCount = readyPairCount,
            totalPairCount = commonPairs.size,
            installedModelCount = models.size,
            busy = modelOperationState.busy,
            onRefreshModels = onRefreshModels
        )
        CacheSectionHeader(
            title = stringResource(R.string.cache_overview),
            subtitle = stringResource(R.string.cache_overview_hint)
        )
        StorageOverview(stats = stats)

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactActionButton(
                label = stringResource(R.string.download_common_vi_pairs),
                iconRes = R.drawable.ic_action_download,
                onClick = onDownloadAllCommonPairs,
                enabled = !modelOperationState.busy,
                primary = true
            )
            CompactActionButton(
                label = stringResource(R.string.clear_translation_cache),
                iconRes = R.drawable.ic_action_delete,
                onClick = { pendingClearScope = CacheClearScope.Translation },
                enabled = !modelOperationState.busy && stats.translationItems > 0
            )
            CompactActionButton(
                label = stringResource(R.string.clear_all_cache),
                iconRes = R.drawable.ic_action_delete,
                onClick = { pendingClearScope = CacheClearScope.All },
                enabled = !modelOperationState.busy
            )
        }

        CacheMediaSection(
            stats = stats,
            mediaPolicy = mediaPolicy,
            busy = modelOperationState.busy,
            onClearScope = { pendingClearScope = it }
        )

        CacheSectionHeader(
            title = stringResource(R.string.translation_models_and_pairs),
            subtitle = stringResource(R.string.common_vi_pairs_hint)
        )
        CacheModelFilterRow(
            selected = selectedModelFilter,
            onSelected = { selectedModelFilterName = it.name }
        )
        filteredPairs.forEach { pair ->
            val ready = pair.requiredModelLanguageCodes.all { it in installedCodes }
            val downloading = modelOperationState.busy &&
                modelOperationState.progress?.operationLabel == pair.label
            CacheTranslationPairRow(
                pair = pair,
                ready = ready,
                downloading = downloading,
                busy = modelOperationState.busy,
                modelsByCode = modelsByCode,
                onDeleteModel = { pendingDeleteModel = it },
                onDownload = { onDownloadPair(pair) }
            )
        }
        filteredStandaloneModels.forEach { model ->
            CacheDownloadedModelRow(
                model = model,
                relatedPairs = emptyList(),
                busy = modelOperationState.busy,
                onDelete = { pendingDeleteModel = model }
            )
        }
        if (filteredPairs.isEmpty() && filteredStandaloneModels.isEmpty()) {
            EmptyState(stringResource(R.string.no_models_match_filter))
        }

        ModelOperationStatus(modelOperationState)
    }

    pendingClearScope?.let { scope ->
        ConfirmCacheClearDialog(
            scope = scope,
            onDismiss = { pendingClearScope = null },
            onConfirm = {
                pendingClearScope = null
                onClear(scope)
            }
        )
    }

    pendingDeleteModel?.let { model ->
        ConfirmModelDeleteDialog(
            model = model,
            onDismiss = { pendingDeleteModel = null },
            onConfirm = {
                pendingDeleteModel = null
                onDeleteModel(model.languageCode)
            }
        )
    }
}

@Composable
private fun CacheHero(
    readyPairCount: Int,
    totalPairCount: Int,
    installedModelCount: Int,
    busy: Boolean,
    onRefreshModels: () -> Unit
) {
    FeatureHero(
        title = stringResource(R.string.cache_title),
        subtitle = stringResource(R.string.ready_pairs_count, readyPairCount, totalPairCount) +
            " - ${stringResource(R.string.language_packs)}: $installedModelCount",
        iconRes = R.drawable.ic_ai_cache,
        onRefresh = onRefreshModels.takeUnless { busy },
        refreshContentDescription = stringResource(R.string.refresh_models)
    )
}

@Composable
private fun StorageOverview(stats: ai.telegram.android.data.TranslationCacheStats) {
    val totalSize = stats.totalCacheSizeMb.coerceAtLeast(1)
    AppCard(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.SectionSpacing)) {
            CacheMetricRow(
                label = stringResource(R.string.translation_cache),
                value = stringResource(R.string.translation_cache_metric, stats.translationItems, stats.translationSizeMb),
                progress = stats.translationSizeMb.toFloat() / totalSize.toFloat(),
                iconRes = R.drawable.ic_ai_translate
            )
            CacheMetricRow(
                label = stringResource(R.string.media_cache),
                value = stringResource(R.string.cache_items_metric, stats.mediaItems, stats.mediaSizeMb),
                progress = stats.mediaSizeMb.toFloat() / totalSize.toFloat(),
                iconRes = R.drawable.ic_ai_paperclip
            )
            CacheMetricRow(
                label = stringResource(R.string.video_subtitle_cache),
                value = stringResource(R.string.cache_items_metric, stats.videoSubtitleItems, stats.videoSubtitleSizeMb),
                progress = stats.videoSubtitleSizeMb.toFloat() / totalSize.toFloat(),
                iconRes = R.drawable.ic_ai_video
            )
            CacheMetricRow(
                label = stringResource(R.string.language_packs),
                value = stringResource(R.string.cache_estimated_mb, stats.languagePackSizeMb),
                progress = stats.languagePackSizeMb.toFloat() / totalSize.toFloat(),
                iconRes = R.drawable.ic_ai_language
            )
    }
}

@Composable
private fun CacheSectionHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CacheMediaSection(
    stats: ai.telegram.android.data.TranslationCacheStats,
    mediaPolicy: MediaDownloadPolicy,
    busy: Boolean,
    onClearScope: (CacheClearScope) -> Unit
) {
    CacheSectionHeader(
        title = stringResource(R.string.media_cache),
        subtitle = stringResource(R.string.media_cache_hint)
    )
    AppCard(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.SectionSpacing)) {
        CacheBreakdownLine(
            label = stringResource(R.string.media_cache_downloaded),
            count = stats.mediaFullItems,
            selected = stats.mediaFullItems > 0
        )
        CacheBreakdownLine(
            label = stringResource(R.string.media_cache_partial),
            count = stats.mediaPartialItems + stats.mediaRequestedItems,
            selected = stats.mediaPartialItems + stats.mediaRequestedItems > 0
        )
        CacheBreakdownLine(
            label = stringResource(R.string.media_cache_corrupt),
            count = stats.mediaCorruptItems,
            selected = stats.mediaCorruptItems > 0,
            error = stats.mediaCorruptItems > 0
        )
        CacheBreakdownLine(
            label = stringResource(R.string.video_subtitle_cache),
            count = stats.videoSubtitleItems,
            selected = stats.videoSubtitleItems > 0
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactActionButton(
                label = stringResource(R.string.clear_media_cache),
                iconRes = R.drawable.ic_action_delete,
                onClick = { onClearScope(CacheClearScope.Media) },
                enabled = !busy && stats.mediaItems > 0
            )
            CompactActionButton(
                label = stringResource(R.string.clear_video_subtitle_cache),
                iconRes = R.drawable.ic_action_delete,
                onClick = { onClearScope(CacheClearScope.VideoSubtitles) },
                enabled = !busy && stats.videoSubtitleItems > 0
            )
            CompactActionButton(
                label = stringResource(R.string.clear_temporary_files),
                iconRes = R.drawable.ic_action_delete,
                onClick = { onClearScope(CacheClearScope.TemporaryFiles) },
                enabled = !busy
            )
        }
    }
    CachePolicyCard(mediaPolicy = mediaPolicy)
}

@Composable
private fun CacheBreakdownLine(
    label: String,
    count: Int,
    selected: Boolean,
    error: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavDot(selected = selected)
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.cache_item_count, count),
            style = MaterialTheme.typography.labelSmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CacheModelFilterRow(
    selected: CacheModelFilter,
    onSelected: (CacheModelFilter) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CacheModelFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = {
                    Text(
                        when (filter) {
                            CacheModelFilter.All -> stringResource(R.string.model_filter_all)
                            CacheModelFilter.Ready -> stringResource(R.string.model_filter_ready)
                            CacheModelFilter.Missing -> stringResource(R.string.model_filter_missing)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun CacheTranslationPairRow(
    pair: TranslationLanguagePair,
    ready: Boolean,
    downloading: Boolean,
    busy: Boolean,
    modelsByCode: Map<String, MlKitTranslationModelInfo>,
    onDeleteModel: (MlKitTranslationModelInfo) -> Unit,
    onDownload: () -> Unit
) {
    AppCard(
        containerColor = if (ready) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        borderColor = if (ready) {
            MaterialTheme.colorScheme.primary.copy(alpha = AiThemeTokens.HeroOutlineAlpha)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = AiThemeTokens.OutlineAlpha)
        }
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LeadingIconTile(iconRes = R.drawable.ic_ai_translate, contentDescription = pair.label)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(pair.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = pair.requiredModelLanguageCodes.joinToString(" + "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (ready) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.model_ready)) })
                } else {
                    CompactActionButton(
                        label = if (downloading) {
                            stringResource(R.string.model_downloading_pair_button)
                        } else {
                            stringResource(R.string.download_pair)
                        },
                        iconRes = R.drawable.ic_action_download,
                        onClick = onDownload,
                        enabled = !busy,
                        primary = true
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.requiredModelLanguageCodes.forEach { languageCode ->
                    CachePairModelChip(
                        languageCode = languageCode,
                        model = modelsByCode[languageCode],
                        busy = busy,
                        onDelete = { model -> onDeleteModel(model) }
                    )
                }
            }
    }
}

@Composable
private fun CachePairModelChip(
    languageCode: String,
    model: MlKitTranslationModelInfo?,
    busy: Boolean,
    onDelete: (MlKitTranslationModelInfo) -> Unit
) {
    AssistChip(
        onClick = {
            if (model != null && !busy) {
                onDelete(model)
            }
        },
        enabled = model != null && !busy,
        label = {
            Text(
                if (model == null) {
                    stringResource(R.string.model_missing_chip, languageCode)
                } else {
                    stringResource(R.string.model_installed_chip, model.displayName)
                }
            )
        }
    )
}

@Composable
private fun CacheDownloadedModelRow(
    model: MlKitTranslationModelInfo,
    relatedPairs: List<TranslationLanguagePair>,
    busy: Boolean,
    onDelete: () -> Unit
) {
    AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LeadingIconTile(iconRes = R.drawable.ic_ai_language, contentDescription = model.displayName)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(model.displayName, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = stringResource(R.string.model_compact_meta, model.languageCode, model.modelType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.model_ready)) })
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CachePairModelChip(
                    languageCode = model.languageCode,
                    model = model,
                    busy = busy,
                    onDelete = { onDelete() }
                )
                if (relatedPairs.isNotEmpty()) {
                    relatedPairs.forEach { pair ->
                        AssistChip(onClick = {}, label = { Text(pair.label) })
                    }
                }
            }
    }
}

@Composable
private fun ModelDetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModelOperationStatus(state: ModelOperationUiState) {
    if (state.messageRes == null && state.errorMessage == null) return

    AppCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.messageRes?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.progress?.let { progress ->
                Text(
                    text = stringResource(R.string.model_download_pair_progress, progress.operationLabel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (progress.skippedAlreadyDownloadedCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.model_download_resume_progress,
                            progress.skippedAlreadyDownloadedCount,
                            progress.totalSteps
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(
                        if (progress.isAlreadyDownloaded) {
                            R.string.model_download_step_skipped
                        } else {
                            R.string.model_download_step_progress
                        },
                        progress.currentStep,
                        progress.totalSteps,
                        progress.languageDisplayName,
                        progress.languageCode
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (state.busy) {
                val progress = state.progress
                if (progress != null && progress.totalSteps > 0) {
                    LinearProgressIndicator(
                        progress = { progress.currentStep.toFloat() / progress.totalSteps.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
    }
}

@Composable
private fun CacheMetricRow(label: String, value: String, progress: Float, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LeadingIconTile(
            iconRes = iconRes,
            contentDescription = label,
            size = 36.dp,
            iconSize = 19.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CachePolicyCard(mediaPolicy: MediaDownloadPolicy) {
    AppCard {
            CacheSectionHeader(title = stringResource(R.string.download_policy))
            PolicyLine(
                stringResource(R.string.wifi_thumbnails),
                mediaPolicy.decide(NetworkMode.Wifi, ai.telegram.android.data.MessageKind.Image, 3).autoDownload
            )
            PolicyLine(
                stringResource(R.string.mobile_thumbnails),
                mediaPolicy.decide(NetworkMode.MobileData, ai.telegram.android.data.MessageKind.Image, 3).autoDownload
            )
            PolicyLine(
                stringResource(R.string.roaming_off),
                mediaPolicy.decide(NetworkMode.Roaming, ai.telegram.android.data.MessageKind.Image, 1).autoDownload
            )
    }
}

@Composable
private fun PolicyLine(text: String, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavDot(selected = enabled)
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = if (enabled) "Auto" else "Off",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConfirmCacheClearDialog(
    scope: CacheClearScope,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (scope) {
                    CacheClearScope.All -> stringResource(R.string.cache_confirm_clear_all_title)
                    CacheClearScope.Translation -> stringResource(R.string.cache_confirm_clear_translation_title)
                    CacheClearScope.Media -> stringResource(R.string.cache_confirm_clear_media_title)
                    CacheClearScope.VideoSubtitles -> stringResource(R.string.cache_confirm_clear_subtitles_title)
                    CacheClearScope.TemporaryFiles -> stringResource(R.string.cache_confirm_clear_temporary_title)
                }
            )
        },
        text = {
            Text(
                when (scope) {
                    CacheClearScope.All -> stringResource(R.string.cache_confirm_clear_all_message)
                    CacheClearScope.Translation -> stringResource(R.string.cache_confirm_clear_translation_message)
                    CacheClearScope.Media -> stringResource(R.string.cache_confirm_clear_media_message)
                    CacheClearScope.VideoSubtitles -> stringResource(R.string.cache_confirm_clear_subtitles_message)
                    CacheClearScope.TemporaryFiles -> stringResource(R.string.cache_confirm_clear_temporary_message)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.clear_cache),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun ConfirmModelDeleteDialog(
    model: MlKitTranslationModelInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_confirm_delete_title)) },
        text = { Text(stringResource(R.string.model_confirm_delete_message, model.displayName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.delete_model),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Preview(
    name = "Cache screen",
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
private fun CacheScreenPreview() {
    AiTelegramTheme {
        CacheScreen(
            stats = previewTranslationCacheStats,
            mediaPolicy = MediaDownloadPolicy(),
            models = previewTranslationModels,
            commonPairs = VietnameseTranslationPairs.common.take(8),
            modelOperationState = ModelOperationUiState(),
            onRefreshModels = {},
            onDownloadAllCommonPairs = {},
            onDownloadPair = {},
            onDeleteModel = {},
            onClear = {}
        )
    }
}

private val previewTranslationCacheStats = ai.telegram.android.data.TranslationCacheStats(
    translationItems = 248,
    translationSizeMb = 18,
    mediaSizeMb = 164,
    languagePackSizeMb = 105,
    mediaItems = 42,
    mediaRequestedItems = 6,
    mediaPartialItems = 12,
    mediaFullItems = 24,
    mediaCorruptItems = 1,
    videoSubtitleItems = 9,
    videoSubtitleSizeMb = 28
)

private val previewTranslationModels = listOf(
    previewTranslationModel("en", "English"),
    previewTranslationModel("ja", "Japanese"),
    previewTranslationModel("ko", "Korean")
)

private fun previewTranslationModel(
    languageCode: String,
    displayName: String
) = MlKitTranslationModelInfo(
    languageCode = languageCode,
    displayName = displayName,
    providerName = "ML Kit",
    backendName = "On device",
    storageKey = "preview-$languageCode",
    modelName = "Preview $displayName",
    modelHash = null,
    modelType = "translation",
    isBaseModel = true
)

