package com.spaceaudio.app.core.source

import java.io.File

/**
 * Result of audio acquisition and storage.
 */
data class AudioResult(
    val file: File,
    val mimeType: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val metadata: AudioMetadata
)
