package dev.dogetime.ui.player

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioTrackTest {
    @Test
    fun labelsNameLanguageCodecAndChannels() {
        assertEquals("Japanese (JPN) · AAC Stereo", AudioTrack(1, "Japanese", "jpn", "aac", 2).label)
        assertEquals("ENG · EAC3 5.1", AudioTrack(2, language = "eng", codec = "eac3", channels = 6).label)
        // The title already names the language.
        assertEquals("English (eng) commentary", AudioTrack(3, "English (eng) commentary", "eng").label)
        assertEquals("Track 4", AudioTrack(4).label)
        assertEquals("JPN", AudioTrack(1, "Japanese", "jpn").shortLabel)
    }
}
