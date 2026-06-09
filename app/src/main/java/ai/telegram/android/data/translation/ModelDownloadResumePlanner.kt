package ai.telegram.android.data.translation

data class ModelDownloadResumeStep(
    val languageCode: String,
    val currentStep: Int,
    val totalSteps: Int,
    val skippedAlreadyDownloadedCount: Int,
    val isAlreadyDownloaded: Boolean
)

object ModelDownloadResumePlanner {
    fun plan(
        languageCodes: List<String>,
        downloadedLanguageCodes: Set<String>
    ): List<ModelDownloadResumeStep> {
        var skippedAlreadyDownloadedCount = 0
        val distinctLanguageCodes = languageCodes.distinct()
        return distinctLanguageCodes.mapIndexed { index, languageCode ->
            val alreadyDownloaded = languageCode in downloadedLanguageCodes
            if (alreadyDownloaded) {
                skippedAlreadyDownloadedCount += 1
            }
            ModelDownloadResumeStep(
                languageCode = languageCode,
                currentStep = index + 1,
                totalSteps = distinctLanguageCodes.size,
                skippedAlreadyDownloadedCount = skippedAlreadyDownloadedCount,
                isAlreadyDownloaded = alreadyDownloaded
            )
        }
    }
}
