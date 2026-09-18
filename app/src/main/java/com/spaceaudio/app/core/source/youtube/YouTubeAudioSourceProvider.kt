package com.spaceaudio.app.core.source.youtube

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.spaceaudio.app.core.source.AudioMetadata
import com.spaceaudio.app.core.source.AudioResult
import com.spaceaudio.app.core.source.AudioSourceProvider
import com.spaceaudio.app.core.source.DownloadProgress
import com.spaceaudio.app.core.source.DownloadStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Isolated YouTube audio source provider.
 *
 * Download pipeline:
 *  1. yt-dlp via Python (Chaquopy) — iOS/mweb multi-client cascade, direct m4a → FFmpeg MP3
 *  2. Stream fallback (YouTubeStreamExtractor: Invidious/Cobalt/Piped/Innertube)
 *     → OkHttp byte stream download → FFmpeg MP3 conversion
 *
 * Guarantees a valid .mp3 output regardless of which strategy succeeds.
 */
class YouTubeAudioSourceProvider(
    private val context: Context? = null,
    private val httpClient: OkHttpClient = defaultHttpClient()
) : AudioSourceProvider {

    override val providerId: String = "youtube"
    override val displayName: String = "YouTube"

    companion object {
        private const val TAG = "YouTubeAudioSourceProvider"
        private const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

        private fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    override fun canHandle(url: String): Boolean {
        return YouTubeUrlParser.isYouTubeUrl(url)
    }

    override fun normalizeUrl(url: String): String {
        return YouTubeUrlParser.normalize(url)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANALYZE — metadata only, no download
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun analyze(url: String): Result<AudioMetadata> = withContext(Dispatchers.IO) {
        val videoId = YouTubeUrlParser.extractVideoId(url)
            ?: return@withContext Result.failure(IllegalArgumentException("Not a valid YouTube URL"))

        val normalized = normalizeUrl(url)

        // Strategy 1: YouTube Innertube — exact title, author, lengthSeconds
        try {
            val jsonPayload = JSONObject().apply {
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", "20.10.38")
                        put("androidSdkVersion", 35)
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=$INNERTUBE_API_KEY")
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "com.google.android.youtube/20.10.38 (Linux; U; Android 15; US) gzip")
                .header("X-YouTube-Client-Name", "3")
                .header("X-YouTube-Client-Version", "20.10.38")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val videoDetails = root.optJSONObject("videoDetails")
                    if (videoDetails != null) {
                        val title = videoDetails.optString("title", "YouTube Audio ($videoId)")
                        val author = videoDetails.optString("author", "YouTube Creator")
                        val lengthSec = videoDetails.optString("lengthSeconds", "0").toLongOrNull() ?: 0L
                        val durationMs = (lengthSec * 1000L)
                            .coerceAtMost(YouTubeStreamExtractor.MAX_ALLOWED_DURATION_MS)

                        return@withContext Result.success(
                            AudioMetadata(
                                title = title,
                                author = author,
                                durationMs = durationMs,
                                thumbnailUrl = YouTubeUrlParser.getThumbnailUrl(videoId),
                                originalUrl = normalized,
                                sourceProvider = providerId,
                                mimeType = "audio/mpeg"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Innertube metadata failed: ${e.message}")
        }

        // Strategy 2: oEmbed fallback (no duration but has title/author/thumbnail)
        try {
            val oembedUrl = "https://www.youtube.com/oembed?url=$normalized&format=json"
            val request = Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SpaceAudio/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val title = json.optString("title", "YouTube Audio ($videoId)")
                    val author = json.optString("author_name", "YouTube Creator")
                    val thumbnailUrl = json.optString("thumbnail_url", YouTubeUrlParser.getThumbnailUrl(videoId))

                    return@withContext Result.success(
                        AudioMetadata(
                            title = title,
                            author = author,
                            durationMs = 0L,
                            thumbnailUrl = thumbnailUrl,
                            originalUrl = normalized,
                            sourceProvider = providerId,
                            mimeType = "audio/mpeg"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "oEmbed fallback failed: ${e.message}")
        }

        // Last resort: bare stub
        Result.success(
            AudioMetadata(
                title = "YouTube Audio ($videoId)",
                author = "YouTube Creator",
                durationMs = 0L,
                thumbnailUrl = YouTubeUrlParser.getThumbnailUrl(videoId),
                originalUrl = normalized,
                sourceProvider = providerId,
                mimeType = "audio/mpeg"
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OBTAIN AUDIO — dual-redundant download pipeline
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun obtainAudio(
        url: String,
        customTitle: String?,
        destinationDirectory: File,
        onProgress: (DownloadProgress) -> Unit
    ): Result<AudioResult> = withContext(Dispatchers.IO) {
        val videoId = YouTubeUrlParser.extractVideoId(url)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Not a valid YouTube URL: $url")
            )

        val normalized = normalizeUrl(url)

        if (!destinationDirectory.exists()) destinationDirectory.mkdirs()

        // Fetch metadata (best-effort; won't block download if it fails)
        onProgress(DownloadProgress(stage = DownloadStage.FETCHING_METADATA, progressPercent = 0.05f, message = "Fetching metadata..."))
        val metadata = analyze(normalized).getOrElse {
            AudioMetadata(
                title = customTitle ?: "YouTube Track ($videoId)",
                author = "YouTube Creator",
                durationMs = 0L,
                thumbnailUrl = YouTubeUrlParser.getThumbnailUrl(videoId),
                originalUrl = normalized,
                sourceProvider = providerId
            )
        }
        val finalTitle = customTitle?.takeIf { it.isNotBlank() } ?: metadata.title

        // ── PRIMARY: yt-dlp via Python (iOS/mweb multi-client cascade) ────────
        val ctx = context
        if (ctx != null) {
            Log.d(TAG, "🎯 Trying primary pipeline: yt-dlp (iOS multi-client)")
            val ytDlpResult = YtDlpAudioDownloader(ctx).download(
                url = normalized,
                customTitle = customTitle,
                destinationDirectory = destinationDirectory,
                onProgress = onProgress
            )

            ytDlpResult.onSuccess { downloaded ->
                Log.d(TAG, "✅ yt-dlp succeeded: ${downloaded.file.name}")
                onProgress(DownloadProgress(stage = DownloadStage.SAVED_TO_DOWNLOADS, progressPercent = 1f, message = "Saved to Downloads"))
                val finalMeta = metadata.copy(
                    title = downloaded.title,
                    author = downloaded.author,
                    durationMs = downloaded.durationMs.takeIf { it > 0 } ?: metadata.durationMs,
                    mimeType = "audio/mpeg"
                )
                return@withContext Result.success(
                    AudioResult(
                        file = downloaded.file,
                        mimeType = "audio/mpeg",
                        durationMs = finalMeta.durationMs,
                        sizeBytes = downloaded.file.length(),
                        metadata = finalMeta
                    )
                )
            }

            val ytDlpError = ytDlpResult.exceptionOrNull()
            Log.w(TAG, "⚠️ yt-dlp failed: ${ytDlpError?.message} — switching to stream fallback")
        } else {
            Log.w(TAG, "⚠️ No Android context — skipping yt-dlp, using stream fallback")
        }

        // ── FALLBACK: YouTubeStreamExtractor → OkHttp byte stream → FFmpeg MP3 ─
        Log.d(TAG, "🎯 Trying fallback pipeline: stream extractor")
        onProgress(DownloadProgress(stage = DownloadStage.DOWNLOADING_AUDIO, progressPercent = 0.25f, message = "Trying alternate download method..."))

        val streamResult = YouTubeStreamExtractor.extractAudioStream(videoId)
        val stream = streamResult.getOrElse { streamError ->
            return@withContext Result.failure(
                IllegalStateException(
                    "Could not download YouTube audio.\n" +
                    "Please check your internet connection and try again.\n" +
                    "(Both yt-dlp and stream extraction failed)",
                    streamError
                )
            )
        }

        // Download raw stream bytes with live progress reporting
        val rawFile = File(destinationDirectory, ".yt-dlp/${videoId}.${stream.mimeType.substringAfter('/').substringBefore(';')}")
        rawFile.parentFile?.mkdirs()

        try {
            val streamRequest = Request.Builder()
                .url(stream.streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) SpaceAudio/1.0")
                .header("Referer", "https://www.youtube.com/")
                .build()

            httpClient.newCall(streamRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Stream HTTP ${response.code}: ${response.message}")
                }
                val body = response.body ?: throw IllegalStateException("Empty stream response")
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: stream.durationMs * 16L
                var bytesRead = 0L

                FileOutputStream(rawFile).use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(65536)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            bytesRead += read
                            val pct = if (totalBytes > 0) (0.25f + (bytesRead.toFloat() / totalBytes) * 0.5f).coerceIn(0.25f, 0.75f) else 0.5f
                            onProgress(DownloadProgress(stage = DownloadStage.DOWNLOADING_AUDIO, progressPercent = pct, bytesRead = bytesRead, totalBytes = totalBytes, message = "Downloading audio stream..."))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            rawFile.delete()
            return@withContext Result.failure(
                IllegalStateException("Failed to download audio stream: ${e.message}", e)
            )
        }

        if (!rawFile.isFile || rawFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Downloaded stream file is empty"))
        }

        // Convert to MP3
        onProgress(DownloadProgress(stage = DownloadStage.FINALIZING, progressPercent = 0.8f, message = "Converting to MP3..."))
        val outputFile = File(
            destinationDirectory,
            AudioMetadata.sanitizeFilename("${metadata.author} - $finalTitle.mp3")
        )

        val ffmpegCmd = buildString {
            append("-y ")
            append("-i '${rawFile.absolutePath.replace("'", "'\\''")}' ")
            append("-vn -codec:a libmp3lame -qscale:a 2 -id3v2_version 3 ")
            append("-metadata title='${finalTitle.replace("'", "'\\''")}' ")
            append("-metadata artist='${metadata.author.replace("'", "'\\''")}' ")
            append("-metadata album=SpaceAudio ")
            append("'${outputFile.absolutePath.replace("'", "'\\''")}' ")
        }

        val session = FFmpegKit.execute(ffmpegCmd)
        rawFile.delete()

        if (!ReturnCode.isSuccess(session.returnCode) || !outputFile.isFile || outputFile.length() == 0L) {
            return@withContext Result.failure(
                IllegalStateException("FFmpeg MP3 conversion failed. Code: ${session.returnCode}")
            )
        }

        Log.d(TAG, "✅ Fallback pipeline succeeded: ${outputFile.name}")
        onProgress(DownloadProgress(stage = DownloadStage.SAVED_TO_DOWNLOADS, progressPercent = 1f, message = "Saved to Downloads"))

        val finalMeta = metadata.copy(
            title = finalTitle,
            durationMs = if (stream.durationMs > 0) stream.durationMs else metadata.durationMs,
            mimeType = "audio/mpeg"
        )

        Result.success(
            AudioResult(
                file = outputFile,
                mimeType = "audio/mpeg",
                durationMs = finalMeta.durationMs,
                sizeBytes = outputFile.length(),
                metadata = finalMeta
            )
        )
    }
}

