package com.spaceaudio.app.core.source.youtube

import java.net.URI

object YouTubeUrlParser {

    private val YOUTUBE_VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

    // Patterns for YouTube URLs
    private val WATCH_REGEX = Regex("(?:https?://)?(?:www\\.|m\\.|music\\.)?youtube\\.com/watch\\?.*v=([a-zA-Z0-9_-]{11})")
    private val SHORT_LINK_REGEX = Regex("(?:https?://)?youtu\\.be/([a-zA-Z0-9_-]{11})")
    private val SHORTS_REGEX = Regex("(?:https?://)?(?:www\\.|m\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11})")
    private val EMBED_REGEX = Regex("(?:https?://)?(?:www\\.|m\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11})")
    private val LIVE_REGEX = Regex("(?:https?://)?(?:www\\.|m\\.)?youtube\\.com/live/([a-zA-Z0-9_-]{11})")

    /**
     * Extracts video ID if the given string is a valid YouTube URL.
     */
    fun extractVideoId(url: String): String? {
        val trimmed = url.trim()

        // Direct video ID check (11 chars)
        if (YOUTUBE_VIDEO_ID_REGEX.matches(trimmed)) {
            return trimmed
        }

        // Try standard watch URL
        WATCH_REGEX.find(trimmed)?.let {
            return it.groupValues[1]
        }

        // Try youtu.be short link
        SHORT_LINK_REGEX.find(trimmed)?.let {
            return it.groupValues[1]
        }

        // Try youtube.com/shorts link
        SHORTS_REGEX.find(trimmed)?.let {
            return it.groupValues[1]
        }

        // Try youtube.com/embed link
        EMBED_REGEX.find(trimmed)?.let {
            return it.groupValues[1]
        }

        // Try youtube.com/live link
        LIVE_REGEX.find(trimmed)?.let {
            return it.groupValues[1]
        }

        // Fallback URI parsing for complex query params
        return try {
            val uri = URI(if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) "https://$trimmed" else trimmed)
            val host = uri.host?.lowercase() ?: return null
            if (host.contains("youtube.com") || host.contains("youtu.be")) {
                if (host.contains("youtu.be")) {
                    uri.path.trim('/').takeIf { YOUTUBE_VIDEO_ID_REGEX.matches(it) }
                } else {
                    val query = uri.query ?: return null
                    query.split('&')
                        .map { it.split('=') }
                        .firstOrNull { it.size == 2 && it[0] == "v" && YOUTUBE_VIDEO_ID_REGEX.matches(it[1]) }
                        ?.get(1)
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if the given URL corresponds to a recognized YouTube URL.
     */
    fun isYouTubeUrl(url: String): Boolean {
        return extractVideoId(url) != null
    }

    /**
     * Normalizes any YouTube URL format to standard https://www.youtube.com/watch?v=VIDEO_ID
     */
    fun normalize(url: String): String {
        val videoId = extractVideoId(url)
        return if (videoId != null) {
            "https://www.youtube.com/watch?v=$videoId"
        } else {
            url.trim()
        }
    }

    /**
     * Generates a high quality thumbnail URL for a given YouTube Video ID.
     */
    fun getThumbnailUrl(videoId: String): String {
        return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }
}
