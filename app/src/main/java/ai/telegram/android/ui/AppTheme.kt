package ai.telegram.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1677C2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9ECFF),
    onPrimaryContainer = Color(0xFF063B67),
    secondary = Color(0xFF14A38B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F4EE),
    onSecondaryContainer = Color(0xFF053F35),
    tertiary = Color(0xFFD88A1D),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF121826),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF3F7),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F5F9),
    outline = Color(0xFFDDE4EC),
    outlineVariant = Color(0xFFE8EEF4),
    onSurface = Color(0xFF121826),
    onSurfaceVariant = Color(0xFF647084),
    error = Color(0xFFD6455D),
    errorContainer = Color(0xFFFFD9DF),
    onErrorContainer = Color(0xFF6D1022)
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF69B7FF),
    onPrimary = Color(0xFF06243F),
    primaryContainer = Color(0xFF143A5C),
    onPrimaryContainer = Color(0xFFD9ECFF),
    secondary = Color(0xFF4FD1B6),
    onSecondary = Color(0xFF042D27),
    secondaryContainer = Color(0xFF123D37),
    onSecondaryContainer = Color(0xFFD7F4EE),
    tertiary = Color(0xFFF6C15D),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFF2F5F8),
    surface = Color(0xFF171B22),
    surfaceVariant = Color(0xFF202631),
    surfaceContainer = Color(0xFF171B22),
    surfaceContainerHigh = Color(0xFF202631),
    outline = Color(0xFF2B3441),
    outlineVariant = Color(0xFF222B36),
    onSurface = Color(0xFFF2F5F8),
    onSurfaceVariant = Color(0xFFA9B2C0),
    error = Color(0xFFFF7B92),
    errorContainer = Color(0xFF5A1D2A),
    onErrorContainer = Color(0xFFFFD9DF)
)

enum class AppThemeMode {
    System,
    Light,
    Dark
}

object AiThemeTokens {
    val ScreenPadding = 16.dp
    val ScreenTopPadding = 12.dp
    val ScreenBottomPadding = 24.dp
    val SectionSpacing = 12.dp
    val ListSpacing = 10.dp
    val ScreenContentSpacing = ListSpacing
    val CompactSpacing = 8.dp
    val DenseSpacing = 6.dp
    val SmallSpacing = 5.dp
    val TinySpacing = 4.dp
    val MicroSpacing = 3.dp
    val FineSpacing = 2.dp
    val HairlineSpacing = 1.dp
    val CardPadding = 12.dp
    val InsetPadding = 14.dp
    val EmptyStatePadding = 18.dp
    val RowPaddingHorizontal = 12.dp
    val RowPaddingVertical = 9.dp
    val CompactRowPaddingHorizontal = 10.dp
    val ChipPaddingHorizontal = 8.dp
    val ChipPaddingVertical = 4.dp
    val BadgePaddingHorizontal = 6.dp
    val BadgePaddingVertical = 1.dp
    val ControlHeight = 44.dp
    val CompactControlHeight = 38.dp
    val SearchHeight = 48.dp
    val IconButtonSize = 40.dp
    val CompactIconButtonSize = 34.dp
    val IconTileSize = 36.dp
    val HeroIconTileSize = 46.dp
    val ActionIconSize = IconButtonSize
    val AvatarSize = 40.dp
    val IconSize = 20.dp
    val ControlIconSize = 18.dp
    val SmallIconSize = 16.dp
    val LargeIconSize = 24.dp
    val StatusDotSize = 8.dp
    val ListItemMinHeight = 58.dp
    val ChatRowMinHeight = 56.dp
    const val OutlineAlpha = 0.72f
    const val SubtleContainerAlpha = 0.72f
    const val HeroContainerAlpha = 0.38f
    const val HeroOutlineAlpha = 0.16f

    val AvatarColors = listOf(
        Color(0xFF1677C2),
        Color(0xFF14A38B),
        Color(0xFFD88A1D),
        Color(0xFF7C3AED),
        Color(0xFFBE123C)
    )
}

private val AiShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp)
)

private fun aiTextStyle(
    fontWeight: FontWeight,
    fontSize: Int,
    lineHeight: Int
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = fontWeight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp
)

private val AiTypography = Typography(
    displayLarge = aiTextStyle(FontWeight.Bold, 30, 36),
    displayMedium = aiTextStyle(FontWeight.Bold, 26, 32),
    displaySmall = aiTextStyle(FontWeight.Bold, 23, 29),
    headlineLarge = aiTextStyle(FontWeight.SemiBold, 22, 28),
    headlineMedium = aiTextStyle(FontWeight.SemiBold, 20, 26),
    headlineSmall = aiTextStyle(FontWeight.SemiBold, 18, 24),
    titleLarge = aiTextStyle(FontWeight.SemiBold, 18, 24),
    titleMedium = aiTextStyle(FontWeight.SemiBold, 16, 22),
    titleSmall = aiTextStyle(FontWeight.SemiBold, 14, 19),
    bodyLarge = aiTextStyle(FontWeight.Normal, 16, 23),
    bodyMedium = aiTextStyle(FontWeight.Normal, 14, 20),
    bodySmall = aiTextStyle(FontWeight.Normal, 12, 17),
    labelLarge = aiTextStyle(FontWeight.SemiBold, 14, 18),
    labelMedium = aiTextStyle(FontWeight.SemiBold, 12, 16),
    labelSmall = aiTextStyle(FontWeight.Medium, 11, 14)
)

@Composable
fun AiTelegramTheme(
    themeMode: AppThemeMode = AppThemeMode.System,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AiTypography,
        shapes = AiShapes,
        content = content
    )
}
