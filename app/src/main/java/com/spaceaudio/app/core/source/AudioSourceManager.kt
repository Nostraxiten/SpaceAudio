package com.spaceaudio.app.core.source

import com.spaceaudio.app.core.source.direct.DirectAudioUrlSourceProvider
import java.io.File

/**
 * Manager that holds all registered AudioSourceProviders and delegates analysis/download requests.
 */
class AudioSourceManager(
    private val providers: List<AudioSourceProvider> = listOf(
        DirectAudioUrlSourceProvider()
    )
) {

    /**
     * Resolves the matching provider for the given URL.
     */
    fun findProvider(url: String): AudioSourceProvider? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        return providers.firstOrNull { it.canHandle(trimmed) }
    }

    /**
     * Analyzes a URL using the appropriate provider.
     */
    suspend fun analyzeUrl(url: String): Result<AudioMetadata> {
        val provider = findProvider(url)
            ?: return Result.failure(IllegalArgumentException("Unsupported source"))

        val normalized = provider.normalizeUrl(url)
        return provider.analyze(normalized)
    }

    /**
     * Obtains audio and saves it locally.
     */
    suspend fun obtainAudio(
        url: String,
        customTitle: String? = null,
        destinationDirectory: File,
        onProgress: (DownloadProgress) -> Unit = {}
    ): Result<AudioResult> {
        val provider = findProvider(url)
            ?: return Result.failure(IllegalArgumentException("Unsupported source"))

        val normalized = provider.normalizeUrl(url)
        return provider.obtainAudio(
            url = normalized,
            customTitle = customTitle,
            destinationDirectory = destinationDirectory,
            onProgress = onProgress
        )
    }
}
