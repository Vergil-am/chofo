package dev.dogetime.ui.player

import dev.dogetime.domain.Subtitle
import dev.dogetime.domain.SubtitleGroup
import dev.dogetime.domain.SubtitleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerViewModelTest {
    private val hourMs = 3_600_000L

    @Test
    fun resumesFromAPositionInTheMiddle() {
        assertEquals(1_200_000L, resumePositionFor(savedMs = 1_200_000L, durationMs = hourMs))
    }

    @Test
    fun startsOverWhenBarelyStarted() {
        assertNull(resumePositionFor(savedMs = 4_000L, durationMs = hourMs))
    }

    @Test
    fun startsOverWhenNearlyFinished() {
        assertNull(resumePositionFor(savedMs = (hourMs * 0.97).toLong(), durationMs = hourMs))
        // From 90% it counts as watched, so it plays from the start.
        assertNull(resumePositionFor(savedMs = (hourMs * 0.9).toLong(), durationMs = hourMs))
        assertEquals((hourMs * 0.89).toLong(), resumePositionFor(savedMs = (hourMs * 0.89).toLong(), durationMs = hourMs))
    }

    @Test
    fun mergesProvidersIntoOneGroupPerLanguageSortedByName() {
        fun sub(name: String, language: String) = Subtitle(name, language, author = "", provider = SubtitleProvider.Subdl, downloadUrl = name)
        val merged = mergeSubtitleGroups(
            listOf(
                SubtitleGroup("French", listOf(sub("a", "French"))),
                SubtitleGroup("English", listOf(sub("b", "English"))),
                SubtitleGroup("French", listOf(sub("c", "French"))),
            ),
        )
        assertEquals(listOf("English", "French"), merged.map { it.language })
        assertEquals(listOf("a", "c"), merged[1].subtitles.map { it.releaseName })
    }
}
