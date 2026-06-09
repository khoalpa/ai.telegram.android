package ai.telegram.android

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

enum class VideoSubtitleColor(@field:StringRes val labelRes: Int, val color: Color) {
    White(R.string.video_subtitle_color_white, Color.White),
    Yellow(R.string.video_subtitle_color_yellow, Color(0xFFFFD54F)),
    Cyan(R.string.video_subtitle_color_cyan, Color(0xFF80DEEA)),
    Green(R.string.video_subtitle_color_green, Color(0xFFA5D6A7)),
    Pink(R.string.video_subtitle_color_pink, Color(0xFFF8BBD0))
}
