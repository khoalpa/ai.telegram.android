package ai.telegram.android

import org.junit.Assert.assertFalse
import org.junit.Test

class OptionalTelegramFeatureGatesTest {
    @Test
    fun optionalTelegramApisStayDisabledUntilDeviceEvidenceExists() {
        OptionalTelegramFeature.entries.forEach { feature ->
            assertFalse("$feature should remain feature-gated", OptionalTelegramFeatureGates.isEnabled(feature))
        }
    }
}
