package com.spaceaudio.app.core.source

/**
 * Data model holding extracted metadata for an audio source before or after download.
 */
data class AudioMetadata(
    val title: String,
    val author: String,
    val durationMs: Long,
    val thumbnailUrl: String? = null,
    val originalUrl: String,
    val sourceProvider: String,
    val mimeType: String = "audio/mpeg",
    val suggestedFilename: String = sanitizeFilename("$author - $title.mp3")
) {
    companion object {
        fun sanitizeFilename(name: String): String {
            return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
                .take(120)
        }
    }
}
