package com.spaceaudio.app.core.source

import com.spaceaudio.app.core.source.direct.DirectAudioUrlSourceProvider
import com.spaceaudio.app.core.source.youtube.YouTubeAudioSourceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioSourceManagerTest {

    private lateinit var audioSourceManager: AudioSourceManager

    @Before
    fun setup() {
        audioSourceManager = AudioSourceManager(
            providers = listOf(
                YouTubeAudioSourceProvider(),
                DirectAudioUrlSourceProvider()
            )
        )
    }

    @Test
    fun testYouTubeProviderResolution() {
        val provider = audioSourceManager.findProvider("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNotNull(provider)
        assertEquals("youtube", provider?.providerId)
    }

    @Test
    fun testDirectAudioProviderResolution() {
        val providerMp3 = audioSourceManager.findProvider("https://example.com/audio/song.mp3")
        assertNotNull(providerMp3)
        assertEquals("direct_audio", providerMp3?.providerId)

        val providerM4a = audioSourceManager.findProvider("https://example.com/music/track.m4a?dl=1")
        assertNotNull(providerM4a)
        assertEquals("direct_audio", providerM4a?.providerId)
    }

    @Test
    fun testUnsupportedSourceRejection() {
        val provider = audioSourceManager.findProvider("https://random-unsupported-website.com/article/123")
        assertNull(provider)
    }
}
