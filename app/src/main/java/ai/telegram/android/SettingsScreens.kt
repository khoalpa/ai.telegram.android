package ai.telegram.android

import ai.telegram.android.data.telegram.TdLibStatus
import ai.telegram.android.ui.AiTelegramTheme
import ai.telegram.android.ui.AiThemeTokens
import ai.telegram.android.ui.AppThemeMode
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    tdLibStatus: TdLibStatus,
    translatedOnly: Boolean,
    onTranslatedOnlyChange: (Boolean) -> Unit,
    contentTranslationLanguage: ContentTranslationLanguage,
    onContentTranslationLanguageChange: (ContentTranslationLanguage) -> Unit,
    allowAdultContent: Boolean,
    onAllowAdultContentChange: (Boolean) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    notificationPreviewsEnabled: Boolean,
    onNotificationPreviewsEnabledChange: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    autoPlayVideo: Boolean,
    onAutoPlayVideoChange: (Boolean) -> Unit,
    videoSubtitlesEnabled: Boolean,
    onVideoSubtitlesEnabledChange: (Boolean) -> Unit,
    videoSourceLanguage: VideoSourceLanguage,
    onVideoSourceLanguageChange: (VideoSourceLanguage) -> Unit,
    videoSubtitleColor: VideoSubtitleColor,
    onVideoSubtitleColorChange: (VideoSubtitleColor) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    interfaceLanguage: InterfaceLanguage,
    onInterfaceLanguageChange: (InterfaceLanguage) -> Unit,
    onSendPhone: (String) -> Unit,
    onSendCode: (String) -> Unit,
    onSendPassword: (String) -> Unit,
    onResendCode: () -> Unit,
    onSignOut: () -> Unit,
    onResetTdLibData: () -> Unit,
    chatCount: Int,
    contactCount: Int
) {
    TelegramLikeSettingsScreen(
        tdLibStatus = tdLibStatus,
        translatedOnly = translatedOnly,
        onTranslatedOnlyChange = onTranslatedOnlyChange,
        contentTranslationLanguage = contentTranslationLanguage,
        onContentTranslationLanguageChange = onContentTranslationLanguageChange,
        allowAdultContent = allowAdultContent,
        onAllowAdultContentChange = onAllowAdultContentChange,
        notificationsEnabled = notificationsEnabled,
        onNotificationsEnabledChange = onNotificationsEnabledChange,
        notificationPreviewsEnabled = notificationPreviewsEnabled,
        onNotificationPreviewsEnabledChange = onNotificationPreviewsEnabledChange,
        notificationPermissionGranted = notificationPermissionGranted,
        onRequestNotificationPermission = onRequestNotificationPermission,
        autoPlayVideo = autoPlayVideo,
        onAutoPlayVideoChange = onAutoPlayVideoChange,
        videoSubtitlesEnabled = videoSubtitlesEnabled,
        onVideoSubtitlesEnabledChange = onVideoSubtitlesEnabledChange,
        videoSourceLanguage = videoSourceLanguage,
        onVideoSourceLanguageChange = onVideoSourceLanguageChange,
        videoSubtitleColor = videoSubtitleColor,
        onVideoSubtitleColorChange = onVideoSubtitleColorChange,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        interfaceLanguage = interfaceLanguage,
        onInterfaceLanguageChange = onInterfaceLanguageChange,
        onSendPhone = onSendPhone,
        onSendCode = onSendCode,
        onSendPassword = onSendPassword,
        onResendCode = onResendCode,
        onSignOut = onSignOut,
        onResetTdLibData = onResetTdLibData,
        chatCount = chatCount,
        contactCount = contactCount
    )
}

@Composable
fun TelegramAuthScreen(
    tdLibStatus: TdLibStatus,
    onSendPhone: (String) -> Unit,
    onSendCode: (String) -> Unit,
    onSendPassword: (String) -> Unit,
    onResendCode: () -> Unit,
    onResetTdLibData: () -> Unit = {}
) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(tdLibStatus) {
        authBusy = false
    }

    val canEditPhone = tdLibStatus == TdLibStatus.WaitingForPhoneNumber ||
        tdLibStatus.hasPhoneNumberError()
    val canEditCode = tdLibStatus is TdLibStatus.WaitingForCode ||
        tdLibStatus.hasInvalidPhoneCode() ||
        tdLibStatus.hasCodeResendUnavailable()
    val canEditPassword = tdLibStatus is TdLibStatus.WaitingForPassword ||
        tdLibStatus.hasInvalidPassword()
    val canResendCode = (tdLibStatus as? TdLibStatus.WaitingForCode)?.canResendCode == true
    val canResetTdLibData = tdLibStatus.hasWrongDatabaseEncryptionKey()
    val activeStep = when {
        canEditPhone -> AuthStep.Phone
        canEditCode -> AuthStep.Code
        canEditPassword -> AuthStep.Password
        else -> null
    }
    val setupHint = when (tdLibStatus) {
        TdLibStatus.NotConfigured -> stringResource(R.string.tdlib_setup_missing_config)
        TdLibStatus.BindingMissing -> stringResource(R.string.tdlib_setup_binding_missing)
        is TdLibStatus.Error -> if (tdLibStatus.hasTdLibFileNotFound()) {
            stringResource(R.string.tdlib_setup_file_not_found)
        } else {
            null
        }
        else -> null
    }

    if (confirmReset) {
        ConfirmDangerDialog(
            title = stringResource(R.string.settings_confirm_reset_title),
            text = stringResource(R.string.settings_confirm_reset_message),
            confirmLabel = stringResource(R.string.tdlib_reset_local_data),
            onConfirm = {
                confirmReset = false
                onResetTdLibData()
            },
            onDismiss = { confirmReset = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = AiThemeTokens.ScreenPadding,
                top = AiThemeTokens.ScreenTopPadding,
                end = AiThemeTokens.ScreenPadding,
                bottom = AiThemeTokens.ScreenBottomPadding
            ),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ScreenContentSpacing)
    ) {
        TelegramAuthHero(tdLibStatus)
        AuthStepStrip(activeStep = activeStep)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(AiThemeTokens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(AiThemeTokens.InsetPadding)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.TinySpacing)) {
                    Text(
                        text = stringResource(R.string.telegram_login),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = tdLibStatus.accountDescription(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (setupHint == null) {
                    StatusBanner(
                        text = tdLibStatus.toDisplayText(context),
                        isError = tdLibStatus is TdLibStatus.Error
                    )
                } else {
                    StatusBanner(text = setupHint, isError = tdLibStatus is TdLibStatus.Error)
                }

                if (canResetTdLibData) {
                    StatusBanner(
                        text = stringResource(R.string.tdlib_wrong_database_key_hint),
                        isError = true
                    )
                    SettingsDangerButton(
                        label = stringResource(R.string.tdlib_reset_local_data),
                        iconRes = R.drawable.ic_action_delete,
                        onClick = { confirmReset = true }
                    )
                }

                when {
                    canEditPhone -> PhoneAuthFields(
                        phone = phone,
                        onPhoneChange = { phone = it },
                        enabled = true,
                        onSendPhone = {
                            authBusy = true
                            onSendPhone(phone.trim())
                        },
                        authBusy = authBusy
                    )
                    canEditCode -> CodeAuthFields(
                        tdLibStatus = tdLibStatus,
                        code = code,
                        onCodeChange = { code = it },
                        enabled = true,
                        onSendCode = {
                            authBusy = true
                            onSendCode(code.trim())
                        },
                        onResendCode = {
                            authBusy = true
                            onResendCode()
                        },
                        canResendCode = canResendCode,
                        authBusy = authBusy
                    )
                    canEditPassword -> PasswordAuthFields(
                        tdLibStatus = tdLibStatus,
                        password = password,
                        onPasswordChange = { password = it },
                        enabled = true,
                        onSendPassword = {
                            authBusy = true
                            onSendPassword(password)
                        },
                        authBusy = authBusy
                    )
                }
            }
        }
        Spacer(Modifier.height(AiThemeTokens.ScreenBottomPadding))
    }
}

private enum class AuthStep {
    Phone,
    Code,
    Password
}

@Composable
private fun TelegramAuthHero(tdLibStatus: TdLibStatus) {
    val context = LocalContext.current
    FeatureHero(
        title = stringResource(R.string.telegram_login),
        subtitle = tdLibStatus.accountDescription(context),
        iconRes = android.R.drawable.sym_action_chat
    ) {
        Spacer(Modifier.width(AiThemeTokens.CompactSpacing))
        AccountStatusChip(tdLibStatus)
    }
}

@Composable
private fun AuthStepStrip(activeStep: AuthStep?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(AiThemeTokens.ListSpacing),
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AuthStepPill(
                number = "1",
                label = stringResource(R.string.tdlib_phone_hint),
                active = activeStep == AuthStep.Phone,
                modifier = Modifier.weight(1f)
            )
            AuthStepPill(
                number = "2",
                label = stringResource(R.string.tdlib_code_hint),
                active = activeStep == AuthStep.Code,
                modifier = Modifier.weight(1f)
            )
            AuthStepPill(
                number = "3",
                label = stringResource(R.string.tdlib_password_hint),
                active = activeStep == AuthStep.Password,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AuthStepPill(
    number: String,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (active) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AiThemeTokens.CompactSpacing, vertical = AiThemeTokens.CompactSpacing),
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(AiThemeTokens.IconSize),
                color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.onSecondary else contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TelegramLikeSettingsScreen(
    tdLibStatus: TdLibStatus,
    translatedOnly: Boolean,
    onTranslatedOnlyChange: (Boolean) -> Unit,
    contentTranslationLanguage: ContentTranslationLanguage,
    onContentTranslationLanguageChange: (ContentTranslationLanguage) -> Unit,
    allowAdultContent: Boolean,
    onAllowAdultContentChange: (Boolean) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    notificationPreviewsEnabled: Boolean,
    onNotificationPreviewsEnabledChange: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    autoPlayVideo: Boolean,
    onAutoPlayVideoChange: (Boolean) -> Unit,
    videoSubtitlesEnabled: Boolean,
    onVideoSubtitlesEnabledChange: (Boolean) -> Unit,
    videoSourceLanguage: VideoSourceLanguage,
    onVideoSourceLanguageChange: (VideoSourceLanguage) -> Unit,
    videoSubtitleColor: VideoSubtitleColor,
    onVideoSubtitleColorChange: (VideoSubtitleColor) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    interfaceLanguage: InterfaceLanguage,
    onInterfaceLanguageChange: (InterfaceLanguage) -> Unit,
    onSendPhone: (String) -> Unit,
    onSendCode: (String) -> Unit,
    onSendPassword: (String) -> Unit,
    onResendCode: () -> Unit,
    onSignOut: () -> Unit,
    onResetTdLibData: () -> Unit,
    chatCount: Int,
    contactCount: Int
) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    LaunchedEffect(tdLibStatus) {
        authBusy = false
    }
    val canEditPhone = tdLibStatus == TdLibStatus.WaitingForPhoneNumber ||
        tdLibStatus.hasPhoneNumberError()
    val canEditCode = tdLibStatus is TdLibStatus.WaitingForCode ||
        tdLibStatus.hasInvalidPhoneCode() ||
        tdLibStatus.hasCodeResendUnavailable()
    val canEditPassword = tdLibStatus is TdLibStatus.WaitingForPassword ||
        tdLibStatus.hasInvalidPassword()
    val canResendCode = (tdLibStatus as? TdLibStatus.WaitingForCode)?.canResendCode == true
    val canResetTdLibData = tdLibStatus.hasWrongDatabaseEncryptionKey()
    val setupHint = when (tdLibStatus) {
        TdLibStatus.NotConfigured -> stringResource(R.string.tdlib_setup_missing_config)
        TdLibStatus.BindingMissing -> stringResource(R.string.tdlib_setup_binding_missing)
        is TdLibStatus.Error -> if (tdLibStatus.hasTdLibFileNotFound()) {
            stringResource(R.string.tdlib_setup_file_not_found)
        } else {
            null
        }
        else -> null
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
        SectionHeader(title = stringResource(R.string.settings_section_account))
        SettingsAccountCard(
            tdLibStatus = tdLibStatus,
            chatCount = chatCount,
            contactCount = contactCount,
            onSignOut = onSignOut
        )

        if (setupHint != null || canResetTdLibData || canEditPhone || canEditCode || canEditPassword) {
            TelegramLoginCard(
                tdLibStatus = tdLibStatus,
                setupHint = setupHint,
                canResetTdLibData = canResetTdLibData,
                phone = phone,
                onPhoneChange = { phone = it },
                canEditPhone = canEditPhone,
                onSendPhone = {
                    authBusy = true
                    onSendPhone(phone)
                },
                code = code,
                onCodeChange = { code = it },
                canEditCode = canEditCode,
                canResendCode = canResendCode,
                onSendCode = {
                    authBusy = true
                    onSendCode(code)
                },
                password = password,
                onPasswordChange = { password = it },
                canEditPassword = canEditPassword,
                onSendPassword = {
                    authBusy = true
                    onSendPassword(password)
                },
                onResendCode = {
                    authBusy = true
                    onResendCode()
                },
                onResetTdLibData = onResetTdLibData,
                authBusy = authBusy
            )
        }

        SectionHeader(title = stringResource(R.string.settings_section_appearance))
        SettingsGroup {
            SettingsThemeModeRow(
                selectedMode = themeMode,
                onModeChange = onThemeModeChange
            )
            SettingsInterfaceLanguageRow(
                selectedLanguage = interfaceLanguage,
                onLanguageChange = onInterfaceLanguageChange
            )
        }

        SectionHeader(title = stringResource(R.string.settings_section_notifications))
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.notifications),
                subtitle = if (notificationPermissionGranted) {
                    stringResource(R.string.settings_notifications_subtitle)
                } else {
                    stringResource(R.string.notifications_permission_needed)
                },
                checked = notificationsEnabled,
                onCheckedChange = onNotificationsEnabledChange,
                iconRes = R.drawable.ic_ai_notifications
            )
            SettingsSwitchRow(
                title = stringResource(R.string.notification_previews),
                subtitle = stringResource(R.string.settings_notification_previews_subtitle),
                checked = notificationPreviewsEnabled,
                onCheckedChange = onNotificationPreviewsEnabledChange,
                iconRes = R.drawable.ic_ai_shield
            )
            if (!notificationPermissionGranted) {
                SettingsActionRow(
                    title = stringResource(R.string.notifications_permission_action),
                    subtitle = stringResource(R.string.notifications_permission_needed),
                    iconRes = R.drawable.ic_ai_notifications,
                    onClick = onRequestNotificationPermission
                )
            }
        }

        SectionHeader(title = stringResource(R.string.settings_section_translation))
        SettingsGroup {
            SettingsContentTranslationLanguageRow(
                selectedLanguage = contentTranslationLanguage,
                onLanguageChange = onContentTranslationLanguageChange
            )
            SettingsSwitchRow(
                title = stringResource(R.string.translated_only),
                subtitle = stringResource(R.string.settings_translated_only_subtitle),
                checked = translatedOnly,
                onCheckedChange = onTranslatedOnlyChange,
                iconRes = R.drawable.ic_ai_hidden
            )
        }

        SectionHeader(title = stringResource(R.string.settings_section_media))
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.autoplay_video),
                subtitle = stringResource(R.string.settings_auto_play_video_subtitle),
                checked = autoPlayVideo,
                onCheckedChange = onAutoPlayVideoChange,
                iconRes = R.drawable.ic_ai_video
            )
            SettingsSwitchRow(
                title = stringResource(R.string.video_subtitles),
                subtitle = stringResource(R.string.settings_video_subtitles_subtitle),
                checked = videoSubtitlesEnabled,
                onCheckedChange = onVideoSubtitlesEnabledChange,
                iconRes = R.drawable.ic_ai_translate
            )
            SettingsVideoSourceLanguageRow(
                selectedLanguage = videoSourceLanguage,
                onLanguageChange = onVideoSourceLanguageChange
            )
            SettingsSubtitleColorRow(
                selectedColor = videoSubtitleColor,
                onColorChange = onVideoSubtitleColorChange
            )
        }

        SectionHeader(title = stringResource(R.string.settings_section_privacy))
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.allow_adult_content),
                subtitle = stringResource(R.string.allow_adult_content_subtitle),
                checked = allowAdultContent,
                onCheckedChange = onAllowAdultContentChange,
                iconRes = R.drawable.ic_ai_shield
            )
            SettingsTrustRow(text = stringResource(R.string.free_commitment))
            SettingsTrustRow(text = stringResource(R.string.on_device_translation))
            SettingsTrustRow(text = stringResource(R.string.privacy_note))
        }

        SectionHeader(title = stringResource(R.string.settings_section_about))
        SettingsGroup {
            SettingsRow(
                title = stringResource(R.string.app_version),
                subtitle = stringResource(R.string.app_version_subtitle),
                value = stringResource(
                    R.string.app_version_value,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                iconRes = R.drawable.ic_ai_settings
            )
        }
    }
}

@Composable
private fun SettingsAccountCard(
    tdLibStatus: TdLibStatus,
    chatCount: Int,
    contactCount: Int,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val active = tdLibStatus == TdLibStatus.Ready
    if (confirmSignOut) {
        ConfirmDangerDialog(
            title = stringResource(R.string.settings_confirm_sign_out_title),
            text = stringResource(R.string.settings_confirm_sign_out_message),
            confirmLabel = stringResource(R.string.sign_out),
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            1.dp,
            if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(AiThemeTokens.InsetPadding),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.SectionSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(AiThemeTokens.HeroIconTileSize),
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ai_channel),
                            contentDescription = stringResource(R.string.telegram_account),
                            modifier = Modifier.size(AiThemeTokens.LargeIconSize),
                            tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(AiThemeTokens.SectionSpacing))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.TinySpacing)) {
                    Text(
                        stringResource(R.string.telegram_account),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        tdLibStatus.accountDescription(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AccountStatusChip(tdLibStatus)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
            ) {
                AccountMetricTile(
                    label = stringResource(R.string.nav_chats),
                    value = chatCount.toString(),
                    iconRes = R.drawable.ic_ai_chat,
                    modifier = Modifier.weight(1f)
                )
                AccountMetricTile(
                    label = stringResource(R.string.nav_contacts),
                    value = contactCount.toString(),
                    iconRes = R.drawable.ic_ai_contacts,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing)) {
                AccountActionItem(
                    label = stringResource(R.string.settings_diagnostics),
                    iconRes = R.drawable.ic_ai_settings,
                    onClick = { showDiagnostics = !showDiagnostics }
                )
                if (tdLibStatus.canShowSignOut()) {
                    AccountActionItem(
                        label = stringResource(R.string.sign_out),
                        iconRes = R.drawable.ic_ai_logout,
                        onClick = { confirmSignOut = true },
                        danger = true
                    )
                }
            }

            if (showDiagnostics) {
                StatusBanner(text = tdLibStatus.toDisplayText(context), isError = tdLibStatus is TdLibStatus.Error)
            }
        }
    }
}

@Composable
private fun AccountMetricTile(
    label: String,
    value: String,
    iconRes: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AiThemeTokens.CompactRowPaddingHorizontal, vertical = AiThemeTokens.RowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                    modifier = Modifier.size(AiThemeTokens.ControlIconSize),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(AiThemeTokens.CompactSpacing))
            Column(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.HairlineSpacing)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AccountActionItem(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    primary: Boolean = false,
    danger: Boolean = false
) {
    val backgroundColor = when {
        danger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
        primary -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    }
    val contentColor = when {
        danger -> MaterialTheme.colorScheme.onErrorContainer
        primary -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AiThemeTokens.CompactRowPaddingHorizontal, vertical = AiThemeTokens.ListSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(AiThemeTokens.ControlIconSize),
                tint = contentColor
            )
            Spacer(Modifier.width(AiThemeTokens.CompactSpacing))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                painter = painterResource(R.drawable.ic_ai_chevron_right),
                contentDescription = label,
                modifier = Modifier.size(AiThemeTokens.ControlIconSize),
                tint = contentColor.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun AccountStatusChip(tdLibStatus: TdLibStatus) {
    val context = LocalContext.current
    val active = tdLibStatus == TdLibStatus.Ready
    val needsAttention = tdLibStatus is TdLibStatus.Error ||
        tdLibStatus == TdLibStatus.NotConfigured ||
        tdLibStatus == TdLibStatus.BindingMissing
    Surface(
        color = when {
            active -> MaterialTheme.colorScheme.primaryContainer
            needsAttention -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AiThemeTokens.RowPaddingVertical, vertical = AiThemeTokens.DenseSpacing),
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(active = active)
            Text(
                text = tdLibStatus.accountStatusLabel(context),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    active -> MaterialTheme.colorScheme.onPrimaryContainer
                    needsAttention -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TelegramLoginCard(
    tdLibStatus: TdLibStatus,
    setupHint: String?,
    canResetTdLibData: Boolean,
    phone: String,
    onPhoneChange: (String) -> Unit,
    canEditPhone: Boolean,
    onSendPhone: () -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    canEditCode: Boolean,
    canResendCode: Boolean,
    onSendCode: () -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    canEditPassword: Boolean,
    onSendPassword: () -> Unit,
    onResendCode: () -> Unit,
    onResetTdLibData: () -> Unit,
    authBusy: Boolean
) {
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    val activeStep = when {
        canEditPhone -> AuthStep.Phone
        canEditCode -> AuthStep.Code
        canEditPassword -> AuthStep.Password
        else -> null
    }
    if (confirmReset) {
        ConfirmDangerDialog(
            title = stringResource(R.string.settings_confirm_reset_title),
            text = stringResource(R.string.settings_confirm_reset_message),
            confirmLabel = stringResource(R.string.tdlib_reset_local_data),
            onConfirm = {
                confirmReset = false
                onResetTdLibData()
            },
            onDismiss = { confirmReset = false }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(AiThemeTokens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(AiThemeTokens.InsetPadding)) {
            Column(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.TinySpacing)) {
                Text(
                    stringResource(R.string.telegram_login),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    tdLibStatus.accountDescription(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AuthStepStrip(activeStep = activeStep)
            if (setupHint == null) {
                StatusBanner(text = tdLibStatus.toDisplayText(context), isError = tdLibStatus is TdLibStatus.Error)
            }

            setupHint?.let {
                StatusBanner(text = it, isError = tdLibStatus is TdLibStatus.Error)
            }

            if (canResetTdLibData) {
                StatusBanner(
                    text = stringResource(R.string.tdlib_wrong_database_key_hint),
                    isError = true
                )
                SettingsDangerButton(
                    label = stringResource(R.string.tdlib_reset_local_data),
                    iconRes = R.drawable.ic_action_delete,
                    onClick = { confirmReset = true }
                )
            } else if (canEditPhone) {
                PhoneAuthFields(
                    phone = phone,
                    onPhoneChange = onPhoneChange,
                    enabled = true,
                    onSendPhone = onSendPhone,
                    authBusy = authBusy
                )
            } else if (canEditCode) {
                CodeAuthFields(
                    tdLibStatus = tdLibStatus,
                    code = code,
                    onCodeChange = onCodeChange,
                    enabled = true,
                    onSendCode = onSendCode,
                    onResendCode = onResendCode,
                    canResendCode = canResendCode,
                    authBusy = authBusy
                )
            } else if (canEditPassword) {
                PasswordAuthFields(
                    tdLibStatus = tdLibStatus,
                    password = password,
                    onPasswordChange = onPasswordChange,
                    enabled = true,
                    onSendPassword = onSendPassword,
                    authBusy = authBusy
                )
            }
        }
    }
}

@Composable
private fun PhoneAuthFields(
    phone: String,
    onPhoneChange: (String) -> Unit,
    enabled: Boolean,
    onSendPhone: () -> Unit,
    authBusy: Boolean
) {
    val focusManager = LocalFocusManager.current
    AuthTextFieldFrame(enabled = enabled && !authBusy) {
        NativeAuthTextField(
            value = phone,
            onValueChange = onPhoneChange,
            hint = stringResource(R.string.tdlib_phone_hint),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && !authBusy,
            inputType = InputType.TYPE_CLASS_PHONE
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
    ) {
        CompactActionButton(
            label = if (authBusy) stringResource(R.string.settings_auth_sending) else stringResource(R.string.tdlib_send_phone),
            iconRes = android.R.drawable.ic_menu_send,
            onClick = {
                focusManager.clearFocus(force = true)
                onSendPhone()
            },
            enabled = enabled && phone.isNotBlank() && !authBusy,
            primary = true
        )
    }
}

@Composable
private fun AuthTextFieldFrame(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (enabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.36f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = AiThemeTokens.TinySpacing, vertical = AiThemeTokens.FineSpacing),
            contentAlignment = Alignment.CenterStart
        ) {
            content()
        }
    }
}

@Composable
private fun AuthActionRow(
    primaryLabel: String,
    primaryIconRes: Int,
    onPrimaryClick: () -> Unit,
    primaryEnabled: Boolean,
    secondaryContent: (@Composable () -> Unit)? = null
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
    ) {
        CompactActionButton(
            label = primaryLabel,
            iconRes = primaryIconRes,
            onClick = onPrimaryClick,
            enabled = primaryEnabled,
            primary = true
        )
        secondaryContent?.invoke()
    }
}

@Composable
private fun CodeAuthFields(
    tdLibStatus: TdLibStatus,
    code: String,
    onCodeChange: (String) -> Unit,
    enabled: Boolean,
    onSendCode: () -> Unit,
    onResendCode: () -> Unit,
    canResendCode: Boolean,
    authBusy: Boolean
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    if (tdLibStatus.hasInvalidPhoneCode() || tdLibStatus.hasCodeResendUnavailable()) {
        StatusBanner(text = tdLibStatus.toDisplayText(context), isError = tdLibStatus.hasInvalidPhoneCode())
    }
    AuthTextFieldFrame(enabled = enabled && !authBusy) {
        NativeAuthTextField(
            value = code,
            onValueChange = onCodeChange,
            hint = stringResource(R.string.tdlib_code_hint),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && !authBusy,
            inputType = InputType.TYPE_CLASS_NUMBER
        )
    }
    AuthActionRow(
        primaryLabel = if (authBusy) stringResource(R.string.settings_auth_sending) else stringResource(R.string.tdlib_send_code),
        primaryIconRes = android.R.drawable.ic_menu_send,
        onPrimaryClick = {
            focusManager.clearFocus(force = true)
            onSendCode()
        },
        primaryEnabled = enabled && code.isNotBlank() && !authBusy,
        secondaryContent = {
            IconActionButton(
                iconRes = android.R.drawable.ic_popup_sync,
                contentDescription = stringResource(R.string.tdlib_resend_code),
                onClick = {
                    focusManager.clearFocus(force = true)
                    onResendCode()
                },
                enabled = enabled && canResendCode && !authBusy
            )
        }
    )
}

@Composable
private fun PasswordAuthFields(
    tdLibStatus: TdLibStatus,
    password: String,
    onPasswordChange: (String) -> Unit,
    enabled: Boolean,
    onSendPassword: () -> Unit,
    authBusy: Boolean
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    if (tdLibStatus.hasInvalidPassword()) {
        StatusBanner(text = tdLibStatus.toDisplayText(context), isError = true)
    }
    val hint = (tdLibStatus as? TdLibStatus.WaitingForPassword)
        ?.hint
        ?.takeIf { it.isNotBlank() }
    AuthTextFieldFrame(enabled = enabled && !authBusy) {
        NativeAuthTextField(
            value = password,
            onValueChange = onPasswordChange,
            hint = stringResource(R.string.tdlib_password_hint),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && !authBusy,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
    }
    hint?.let { passwordHint ->
        Text(
            text = stringResource(R.string.tdlib_password_supporting_hint, passwordHint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    AuthActionRow(
        primaryLabel = if (authBusy) stringResource(R.string.settings_auth_sending) else stringResource(R.string.tdlib_send_password),
        primaryIconRes = android.R.drawable.ic_menu_send,
        onPrimaryClick = {
            focusManager.clearFocus(force = true)
            onSendPassword()
        },
        primaryEnabled = enabled && password.isNotBlank() && !authBusy
    )
}

@Composable
private fun NativeAuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    enabled: Boolean,
    inputType: Int,
    modifier: Modifier = Modifier
) {
    val onValueChangeState = rememberUpdatedState(onValueChange)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            EditText(context).apply {
                setSingleLine(true)
                setPadding(24, 0, 24, 0)
                setTextColor(textColor)
                setHintTextColor(hintColor)
                setBackgroundColor(backgroundColor)
                this.hint = hint
                this.inputType = inputType
                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(s: Editable?) {
                            val nextValue = s?.toString().orEmpty()
                            if (nextValue != value) {
                                onValueChangeState.value(nextValue)
                            }
                        }
                    }
                )
            }
        },
        update = { editText ->
            editText.hint = hint
            editText.inputType = inputType
            editText.isEnabled = enabled
            editText.setTextColor(if (enabled) textColor else disabledTextColor)
            editText.setHintTextColor(hintColor)
            editText.setBackgroundColor(backgroundColor)
            if (editText.text.toString() != value) {
                editText.setText(value)
                editText.setSelection(editText.text.length)
            }
        }
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    ) {
        Column(Modifier.padding(vertical = AiThemeTokens.MicroSpacing)) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    iconRes: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.ListSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        iconRes?.let {
            SettingsLeadingIcon(iconRes = it, contentDescription = title)
            Spacer(Modifier.width(AiThemeTokens.ListSpacing))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        value?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconRes: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically
    ) {
        iconRes?.let {
            SettingsLeadingIcon(iconRes = it, contentDescription = title)
            Spacer(Modifier.width(AiThemeTokens.ListSpacing))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsThemeModeRow(
    selectedMode: AppThemeMode,
    onModeChange: (AppThemeMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.ListSpacing),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsLeadingIcon(iconRes = R.drawable.ic_ai_palette, contentDescription = stringResource(R.string.theme_mode))
            Spacer(Modifier.width(AiThemeTokens.ListSpacing))
            Column(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)) {
                Text(
                    stringResource(R.string.theme_mode),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.theme_mode_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            AppThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { onModeChange(mode) },
                    label = { Text(mode.label()) }
                )
            }
        }
    }
}

@Composable
private fun SettingsContentTranslationLanguageRow(
    selectedLanguage: ContentTranslationLanguage,
    onLanguageChange: (ContentTranslationLanguage) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.ListSpacing),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsLeadingIcon(iconRes = R.drawable.ic_ai_translate, contentDescription = stringResource(R.string.target_language))
            Spacer(Modifier.width(AiThemeTokens.ListSpacing))
            Column(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)) {
                Text(
                    stringResource(R.string.target_language),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.settings_target_language_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            ContentTranslationLanguage.entries.forEach { language ->
                FilterChip(
                    selected = selectedLanguage == language,
                    onClick = { onLanguageChange(language) },
                    label = { Text(stringResource(language.labelRes)) }
                )
            }
        }
    }
}

@Composable
private fun SettingsInterfaceLanguageRow(
    selectedLanguage: InterfaceLanguage,
    onLanguageChange: (InterfaceLanguage) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.ListSpacing),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsLeadingIcon(iconRes = R.drawable.ic_ai_language, contentDescription = stringResource(R.string.interface_language))
            Spacer(Modifier.width(AiThemeTokens.ListSpacing))
            Column(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)) {
                Text(
                    stringResource(R.string.interface_language),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.settings_interface_language_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            InterfaceLanguage.entries.forEach { language ->
                FilterChip(
                    selected = selectedLanguage == language,
                    onClick = { onLanguageChange(language) },
                    label = { Text(stringResource(language.labelRes)) }
                )
            }
        }
    }
}

@Composable
private fun SettingsSubtitleColorRow(
    selectedColor: VideoSubtitleColor,
    onColorChange: (VideoSubtitleColor) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.ListSpacing),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsLeadingIcon(iconRes = R.drawable.ic_ai_palette, contentDescription = stringResource(R.string.video_subtitle_color))
            Spacer(Modifier.width(AiThemeTokens.ListSpacing))
            Column(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)) {
                Text(
                    stringResource(R.string.video_subtitle_color),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.settings_video_subtitle_color_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            VideoSubtitleColor.entries.forEach { color ->
                FilterChip(
                    selected = selectedColor == color,
                    onClick = { onColorChange(color) },
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.DenseSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(14.dp),
                                color = color.color,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                content = {}
                            )
                            Text(stringResource(color.labelRes))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsVideoSourceLanguageRow(
    selectedLanguage: VideoSourceLanguage,
    onLanguageChange: (VideoSourceLanguage) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.ListSpacing),
        verticalArrangement = Arrangement.spacedBy(AiThemeTokens.ListSpacing)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsLeadingIcon(iconRes = R.drawable.ic_ai_language, contentDescription = stringResource(R.string.video_source_language))
            Spacer(Modifier.width(AiThemeTokens.ListSpacing))
            Column(verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)) {
                Text(
                    stringResource(R.string.video_source_language),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.settings_video_source_language_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing)
        ) {
            VideoSourceLanguage.entries.forEach { language ->
                FilterChip(
                    selected = selectedLanguage == language,
                    onClick = { onLanguageChange(language) },
                    label = { Text(stringResource(language.labelRes)) }
                )
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String?,
    iconRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.ListSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsLeadingIcon(iconRes = iconRes, contentDescription = title)
        Spacer(Modifier.width(AiThemeTokens.ListSpacing))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AiThemeTokens.MicroSpacing)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_ai_chevron_right),
            contentDescription = title,
            modifier = Modifier.size(AiThemeTokens.IconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsLeadingIcon(
    iconRes: Int,
    contentDescription: String
) {
    LeadingIconTile(iconRes = iconRes, contentDescription = contentDescription)
}

@Composable
private fun SettingsTrustRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.ListSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsLeadingIcon(iconRes = R.drawable.ic_ai_shield, contentDescription = text)
        Spacer(Modifier.width(AiThemeTokens.SectionSpacing))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsDangerButton(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AiThemeTokens.RowPaddingHorizontal, vertical = AiThemeTokens.ListSpacing),
            horizontalArrangement = Arrangement.spacedBy(AiThemeTokens.CompactSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(AiThemeTokens.ControlIconSize),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConfirmDangerDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
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
private fun SettingsNotice(text: String, isError: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(AiThemeTokens.CardPadding),
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StatusDot(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(AiThemeTokens.StatusDotSize)
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
    )
}

private fun TdLibStatus.hasWrongDatabaseEncryptionKey(): Boolean {
    return this is TdLibStatus.Error &&
        message.contains("Wrong database encryption key", ignoreCase = true)
}

private fun TdLibStatus.hasTdLibFileNotFound(): Boolean {
    return this is TdLibStatus.Error &&
        (
            message.contains("TDLib JSON native library load failed", ignoreCase = true) ||
                message.contains("dlopen failed", ignoreCase = true) ||
                message.contains("couldn't find", ignoreCase = true) ||
                message.contains("tdjsonjava", ignoreCase = true)
            )
}

private fun TdLibStatus.canShowSignOut(): Boolean {
    return this == TdLibStatus.Ready ||
        this == TdLibStatus.Closed ||
        this is TdLibStatus.Error
}



private fun TdLibStatus.hasInvalidPhoneCode(): Boolean {
    return this is TdLibStatus.Error &&
        message.contains("PHONE_CODE_INVALID", ignoreCase = true)
}

private fun TdLibStatus.hasPhoneNumberError(): Boolean {
    return this is TdLibStatus.Error &&
        (
            message.contains("Phone number", ignoreCase = true) ||
                message.contains("PHONE_NUMBER", ignoreCase = true)
            )
}

private fun TdLibStatus.hasInvalidPassword(): Boolean {
    return this is TdLibStatus.Error &&
        message.contains("PASSWORD_HASH_INVALID", ignoreCase = true)
}

private fun TdLibStatus.hasCodeResendUnavailable(): Boolean {
    return this is TdLibStatus.Error &&
        message.contains("Authentication code can't be resend", ignoreCase = true)
}

private fun TdLibStatus.toDisplayText(context: Context): String {
    return when (this) {
        TdLibStatus.BindingMissing -> context.getString(R.string.tdlib_status_binding_missing)
        TdLibStatus.Closed -> context.getString(R.string.tdlib_status_closed)
        is TdLibStatus.Error -> if (hasInvalidPhoneCode()) {
            context.getString(R.string.tdlib_status_phone_code_invalid)
        } else if (hasInvalidPassword()) {
            context.getString(R.string.tdlib_status_password_invalid)
        } else if (hasCodeResendUnavailable()) {
            context.getString(R.string.tdlib_status_code_resend_unavailable)
        } else if (hasTdLibFileNotFound()) {
            context.getString(R.string.tdlib_status_file_not_found)
        } else {
            context.getString(R.string.tdlib_status_error, message)
        }
        TdLibStatus.NotConfigured -> context.getString(R.string.tdlib_status_not_configured)
        TdLibStatus.Ready -> context.getString(R.string.tdlib_status_ready)
        TdLibStatus.Starting -> context.getString(R.string.tdlib_status_starting)
        is TdLibStatus.WaitingForCode -> if (canResendCode) {
            context.getString(R.string.tdlib_status_waiting_code_can_resend)
        } else {
            context.getString(R.string.tdlib_status_waiting_code)
        }
        is TdLibStatus.WaitingForPassword -> context.getString(R.string.tdlib_status_waiting_password)
        TdLibStatus.WaitingForPhoneNumber -> context.getString(R.string.tdlib_status_waiting_phone)
    }
}

private fun TdLibStatus.accountStatusLabel(context: Context): String {
    return when (this) {
        TdLibStatus.Ready -> context.getString(R.string.settings_account_connected)
        TdLibStatus.Starting -> context.getString(R.string.settings_account_starting)
        TdLibStatus.WaitingForPhoneNumber,
        is TdLibStatus.WaitingForCode,
        is TdLibStatus.WaitingForPassword -> context.getString(R.string.settings_account_login_needed)
        TdLibStatus.NotConfigured,
        TdLibStatus.BindingMissing,
        TdLibStatus.Closed,
        is TdLibStatus.Error -> context.getString(R.string.settings_account_attention)
    }
}

private fun TdLibStatus.accountDescription(context: Context): String {
    return when (this) {
        TdLibStatus.Ready -> context.getString(R.string.settings_account_connected_description)
        TdLibStatus.Starting -> context.getString(R.string.settings_account_starting_description)
        TdLibStatus.WaitingForPhoneNumber -> context.getString(R.string.settings_account_phone_description)
        is TdLibStatus.WaitingForCode -> context.getString(R.string.settings_account_code_description)
        is TdLibStatus.WaitingForPassword -> context.getString(R.string.settings_account_password_description)
        TdLibStatus.NotConfigured -> context.getString(R.string.settings_account_config_description)
        TdLibStatus.BindingMissing -> context.getString(R.string.settings_account_binding_description)
        TdLibStatus.Closed -> context.getString(R.string.settings_account_closed_description)
        is TdLibStatus.Error -> if (hasWrongDatabaseEncryptionKey()) {
            context.getString(R.string.settings_account_reset_description)
        } else {
            context.getString(R.string.settings_account_error_description)
        }
    }
}

@Composable
private fun AppThemeMode.label(): String {
    return when (this) {
        AppThemeMode.System -> stringResource(R.string.theme_system)
        AppThemeMode.Light -> stringResource(R.string.theme_light)
        AppThemeMode.Dark -> stringResource(R.string.theme_dark)
    }
}

@Composable
private fun SettingsAvatar(seed: String) {
    val color = rememberStableAvatarColor(seed)
    Box(
        modifier = Modifier
            .size(AiThemeTokens.AvatarSize)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = seed.take(1).uppercase(),
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(name = "Settings", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun SettingsScreenPreview() {
    AiTelegramTheme {
        SettingsScreen(
            tdLibStatus = TdLibStatus.Ready,
            translatedOnly = true,
            onTranslatedOnlyChange = {},
            contentTranslationLanguage = ContentTranslationLanguage.Vietnamese,
            onContentTranslationLanguageChange = {},
            allowAdultContent = true,
            onAllowAdultContentChange = {},
            notificationsEnabled = true,
            onNotificationsEnabledChange = {},
            notificationPreviewsEnabled = false,
            onNotificationPreviewsEnabledChange = {},
            notificationPermissionGranted = true,
            onRequestNotificationPermission = {},
            autoPlayVideo = false,
            onAutoPlayVideoChange = {},
            videoSubtitlesEnabled = true,
            onVideoSubtitlesEnabledChange = {},
            videoSourceLanguage = VideoSourceLanguage.Auto,
            onVideoSourceLanguageChange = {},
            videoSubtitleColor = VideoSubtitleColor.Yellow,
            onVideoSubtitleColorChange = {},
            themeMode = AppThemeMode.System,
            onThemeModeChange = {},
            interfaceLanguage = InterfaceLanguage.Vietnamese,
            onInterfaceLanguageChange = {},
            onSendPhone = {},
            onSendCode = {},
            onSendPassword = {},
            onResendCode = {},
            onSignOut = {},
            onResetTdLibData = {},
            chatCount = 12,
            contactCount = 4
        )
    }
}

@Preview(name = "Telegram auth", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun TelegramAuthScreenPreview() {
    AiTelegramTheme {
        TelegramAuthScreen(
            tdLibStatus = TdLibStatus.WaitingForPhoneNumber,
            onSendPhone = {},
            onSendCode = {},
            onSendPassword = {},
            onResendCode = {},
            onResetTdLibData = {}
        )
    }
}
