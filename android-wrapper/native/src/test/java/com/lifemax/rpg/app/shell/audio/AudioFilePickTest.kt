package com.lifemax.rpg.app.shell.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFilePickTest {
    // the page's own list (its #audioFileInput accept attribute)
    private val accept =
        AudioAccept.of("audio/*,.m4b,.m4a,.mp3,.aac,.ogg,.wav,audio/mp4,audio/x-m4a,audio/mpeg")

    @Test
    fun anM4bThatTheSystemCallsAnythingIsAllowedByItsName() {
        assertTrue(accept.allows("Hail Mary.m4b", "application/octet-stream"))
        assertTrue(accept.allows("Hail Mary.M4B", null))
        assertTrue(accept.allows("book.m4b", "audio/x-m4b"))
    }

    @Test
    fun anAudioTypeIsAllowedWhateverTheName() {
        assertTrue(accept.allows("recording", "audio/flac"))
        assertTrue(accept.allows("x.bin", "audio/mpeg"))
    }

    @Test
    fun otherFilesAreRefused() {
        assertFalse(accept.allows("notes.pdf", "application/pdf"))
        assertFalse(accept.allows("movie.mp4", "video/mp4"))
        assertFalse(accept.allows("book", "application/octet-stream"))
        assertFalse(accept.allows("book", null))
    }

    @Test
    fun theListIsTheOnePassedIn() {
        val only = AudioAccept.of(".m4b")
        assertTrue(only.allows("a.m4b", null))
        assertFalse(only.allows("a.mp3", "audio/mpeg"))
    }

    @Test
    fun aContentDocumentIsReadThroughCapacitorsLocalServer() {
        assertEquals(
            "http://localhost/_capacitor_content_/com.android.providers.media.documents/document/audio%3A42",
            capacitorContentUrl(
                "http://localhost/",
                "content://com.android.providers.media.documents/document/audio%3A42",
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            capacitorContentUrl("http://localhost", "file:///sdcard/a.m4b")
        }
    }
}
