package com.spaceaudio.app.core.source.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun testStandardWatchUrl() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertTrue(YouTubeUrlParser.isYouTubeUrl(url))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId(url))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", YouTubeUrlParser.normalize(url))
    }

    @Test
    fun testShortUrlWithTrackingParams() {
        val url = "https://youtu.be/dQw4w9WgXcQ?si=abcdef123456"
        assertTrue(YouTubeUrlParser.isYouTubeUrl(url))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId(url))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", YouTubeUrlParser.normalize(url))
    }

    @Test
    fun testShortsUrl() {
        val url = "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        assertTrue(YouTubeUrlParser.isYouTubeUrl(url))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId(url))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", YouTubeUrlParser.normalize(url))
    }

    @Test
    fun testMobileUrl() {
        val url = "https://m.youtube.com/watch?v=dQw4w9WgXcQ&feature=share"
        assertTrue(YouTubeUrlParser.isYouTubeUrl(url))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId(url))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", YouTubeUrlParser.normalize(url))
    }

    @Test
    fun testDirectVideoId() {
        val videoId = "dQw4w9WgXcQ"
        assertTrue(YouTubeUrlParser.isYouTubeUrl(videoId))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId(videoId))
    }

    @Test
    fun testInvalidUrl() {
        assertFalse(YouTubeUrlParser.isYouTubeUrl("https://example.com/audio/song.mp3"))
        assertNull(YouTubeUrlParser.extractVideoId("https://example.com/audio/song.mp3"))
        assertFalse(YouTubeUrlParser.isYouTubeUrl("https://google.com"))
        assertFalse(YouTubeUrlParser.isYouTubeUrl(""))
    }

    @Test
    fun testThumbnailUrlGeneration() {
        val videoId = "dQw4w9WgXcQ"
        val thumb = YouTubeUrlParser.getThumbnailUrl(videoId)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", thumb)
    }
}
