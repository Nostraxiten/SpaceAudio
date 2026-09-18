package com.spaceaudio.app.core.source.direct

import com.spaceaudio.app.core.source.AudioMetadata
import com.spaceaudio.app.core.source.AudioResult
import com.spaceaudio.app.core.source.AudioSourceProvider
import com.spaceaudio.app.core.source.DownloadProgress
import com.spaceaudio.app.core.source.DownloadStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Provider that handles direct audio file links (e.g. .mp3, .m4a, .aac, .ogg, .flac, .wav).
 */
class DirectAudioUrlSourceProvider(
    private val httpClient: OkHttpClient = defaultHttpClient()
) : AudioSourceProvider {

    override val providerId: String = "direct_audio"
    override val displayName: String = "Direct Audio URL"

    private val supportedExtensions = listOf(".mp3", ".m4a", ".aac", ".ogg", ".flac", ".wav", ".opus")

    override fun canHandle(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return false
        }
        return supportedExtensions.any { ext ->
            val path = try { URI(trimmed).path?.lowercase() ?: "" } catch (_: Exception) { trimmed }
            path.endsWith(ext) || path.contains("$ext?")
        }
    }

    override fun normalizeUrl(url: String): String {
        return url.trim()
    }

    override suspend fun analyze(url: String): Result<AudioMetadata> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(url)
        try {
            val uri = URI(normalized)
            val path = uri.path ?: ""
            val rawName = path.substringAfterLast('/').substringBefore('?')
            val cleanTitle = rawName.substringBeforeLast('.').ifBlank { "Direct Audio" }

            val headRequest = Request.Builder()
                .url(normalized)
                .head()
                .header("User-Agent", "SpaceAudio/1.0")
                .build()

            var mimeType = "audio/mpeg"
            try {
                httpClient.newCall(headRequest).execute().use { response ->
                    val contentType = response.header("Content-Type")
                    if (contentType != null && contentType.startsWith("audio/")) {
                        mimeType = contentType.substringBefore(';')
                    }
                }
            } catch (_: Exception) {
                // Ignore HEAD error and fall back to extension matching
            }

            Result.success(
                AudioMetadata(
                    title = cleanTitle,
                    author = "Direct Link",
                    durationMs = 0L,
                    thumbnailUrl = null,
                    originalUrl = normalized,
                    sourceProvider = providerId,
                    mimeType = mimeType,
                    suggestedFilename = AudioMetadata.sanitizeFilename("$cleanTitle.mp3")
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Unsupported URL: Unable to analyze direct audio source (${e.message})"))
        }
    }

    override suspend fun obtainAudio(
        url: String,
        customTitle: String?,
        destinationDirectory: File,
        onProgress: (DownloadProgress) -> Unit
    ): Result<AudioResult> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(url)
        try {
            onProgress(
                DownloadProgress(
                    stage = DownloadStage.FETCHING_METADATA,
                    progressPercent = 0.1f,
                    message = "Connecting to direct audio source..."
                )
            )

            val metadata = analyze(normalized).getOrNull() ?: AudioMetadata(
                title = customTitle ?: "Direct Audio Track",
                author = "Direct Audio",
                durationMs = 0L,
                originalUrl = normalized,
                sourceProvider = providerId
            )

            val finalTitle = customTitle?.takeIf { it.isNotBlank() } ?: metadata.title
            val ext = if (metadata.mimeType.contains("mp4") || metadata.mimeType.contains("m4a")) "m4a" else "mp3"
            val filename = AudioMetadata.sanitizeFilename("$finalTitle.$ext")

            if (!destinationDirectory.exists()) {
                destinationDirectory.mkdirs()
            }

            val outputFile = File(destinationDirectory, filename)

            val getRequest = Request.Builder()
                .url(normalized)
                .header("User-Agent", "SpaceAudio/1.0")
                .build()

            onProgress(
                DownloadProgress(
                    stage = DownloadStage.DOWNLOADING_AUDIO,
                    progressPercent = 0.2f,
                    message = "Downloading audio stream..."
                )
            )

            httpClient.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Failed to download: HTTP ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("Empty body response")
                val totalBytes = body.contentLength()
                var bytesRead = 0L

                val inputStream: InputStream = body.byteStream()
                val outputStream = FileOutputStream(outputFile)

                outputStream.use { out ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        bytesRead += read
                        val percent = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes.toFloat()) * 0.8f + 0.2f else 0.5f

                        onProgress(
                            DownloadProgress(
                                stage = DownloadStage.DOWNLOADING_AUDIO,
                                progressPercent = percent.coerceIn(0f, 0.99f),
                                bytesRead = bytesRead,
                                totalBytes = totalBytes,
                                message = "Saving audio locally..."
                            )
                        )
                    }
                }
            }

            onProgress(
                DownloadProgress(
                    stage = DownloadStage.SAVED_TO_DOWNLOADS,
                    progressPercent = 1.0f,
                    bytesRead = outputFile.length(),
                    totalBytes = outputFile.length(),
                    message = "Saved to Downloads folder"
                )
            )

            val finalMetadata = metadata.copy(title = finalTitle)
            Result.success(
                AudioResult(
                    file = outputFile,
                    mimeType = metadata.mimeType,
                    durationMs = metadata.durationMs,
                    sizeBytes = outputFile.length(),
                    metadata = finalMetadata
                )
            )
        } catch (e: Exception) {
            onProgress(
                DownloadProgress(
                    stage = DownloadStage.ERROR,
                    progressPercent = 0f,
                    message = "Download error: ${e.message}"
                )
            )
            Result.failure(e)
        }
    }

    companion object {
        private fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }
}
