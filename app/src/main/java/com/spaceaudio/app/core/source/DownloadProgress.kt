package com.spaceaudio.app.core.source

/**
 * Progression updates during audio acquisition and local saving.
 */
data class DownloadProgress(
    val stage: DownloadStage,
    val progressPercent: Float = 0f,
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String = ""
)

enum class DownloadStage {
    IDLE,
    VALIDATING,
    FETCHING_METADATA,
    DOWNLOADING_AUDIO,
    FINALIZING,
    SAVED_TO_DOWNLOADS,
    ERROR
}
