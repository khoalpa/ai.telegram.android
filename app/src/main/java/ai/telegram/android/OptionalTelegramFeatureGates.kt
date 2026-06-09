package ai.telegram.android

enum class OptionalTelegramFeature {
    Calls,
    PremiumBusinessApis,
    BotWebAppData
}

object OptionalTelegramFeatureGates {
    fun isEnabled(feature: OptionalTelegramFeature): Boolean {
        return when (feature) {
            OptionalTelegramFeature.Calls -> false
            OptionalTelegramFeature.PremiumBusinessApis -> false
            OptionalTelegramFeature.BotWebAppData -> false
        }
    }
}
