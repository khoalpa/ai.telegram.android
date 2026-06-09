package ai.telegram.android.data.telegram

import ai.telegram.android.data.MessageKind

internal data class TdLibParsedMedia(
    val kind: MessageKind,
    val fileId: Int = 0,
    val localPath: String = "",
    val mimeType: String = "",
    val fileName: String = "",
    val thumbnailFileId: Int = 0,
    val thumbnailLocalPath: String = "",
    val downloadedPrefixBytes: Long = 0L,
    val sizeMb: Int = 0
)

internal fun tdLibBytesToMb(bytes: Long): Int {
    return if (bytes <= 0L) 0 else ((bytes + 1_048_575L) / 1_048_576L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}
