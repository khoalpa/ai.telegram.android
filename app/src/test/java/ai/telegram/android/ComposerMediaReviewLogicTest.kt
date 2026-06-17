package ai.telegram.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerMediaReviewLogicTest {
    @Test
    fun withComposerFallbackCaption_usesDraftOnlyForFirstMediaWithoutOwnCaption() {
        assertEquals("Draft caption", composerCaptionWithDraftFallback(0, "", " Draft caption "))
        assertEquals("Own caption", composerCaptionWithDraftFallback(0, "Own caption", "Draft caption"))
        assertEquals("", composerCaptionWithDraftFallback(1, "", "Draft caption"))
    }

    @Test
    fun effectiveComposerCaption_prefersPerMediaCaption() {
        assertEquals("Per media", effectiveComposerCaption("Per media", "Draft"))
        assertEquals("Draft", effectiveComposerCaption("", " Draft "))
    }

    @Test
    fun movePendingMedia_reordersWithinBounds() {
        val media = listOf("a", "b", "c")

        assertEquals(listOf("b", "a", "c"), media.moveItem(1, -1))
        assertEquals(listOf("a", "c", "b"), media.moveItem(1, 1))
        assertEquals(listOf("a", "b", "c"), media.moveItem(0, -1))
        assertEquals(listOf("a", "b", "c"), media.moveItem(99, 1))
    }
}
