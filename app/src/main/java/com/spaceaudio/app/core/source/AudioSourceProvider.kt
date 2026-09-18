package com.spaceaudio.app.core.source

import java.io.File

/**
 * Core interface for extensible audio source providers.
 * Any current or future provider (YouTube, direct HTTP audio files, podcast RSS, etc.)
 * implements this contract without coupling to the rest of the application.
 */
interface AudioSourceProvider {
    /**
     * Unique identifier for this provider (e.g. "youtube", "direct_audio").
     */
    val providerId: String

    /**
     * User-facing display name.
     */
    val displayName: String

    /**
     * Determines whether this provider can handle the supplied URL.
     */
    fun canHandle(url: String): Boolean

    /**
     * Cleans and normalizes the URL (strips tracking query params, normalizes host, etc.).
     */
    fun normalizeUrl(url: String): String

    /**
     * Analyzes the given URL and retrieves available metadata (title, author, duration, thumbnail)
     * without downloading the full audio stream.
     */
    suspend fun analyze(url: String): Result<AudioMetadata>

    /**
     * Obtains/imports the audio track and writes it to a local file in the designated destination folder.
     *
     * @param url The validated URL.
     * @param customTitle Optional custom title supplied by the user.
     * @param destinationDirectory The directory where the file will be saved (e.g., app Downloads directory).
     * @param onProgress Callback receiving progress updates.
     */
    suspend fun obtainAudio(
        url: String,
        customTitle: String? = null,
        destinationDirectory: File,
        onProgress: (DownloadProgress) -> Unit = {}
    ): Result<AudioResult>
}
