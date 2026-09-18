package com.spaceaudio.app.core.source.youtube

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
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Isolated YouTube audio source provider.
 * Extracts accurate metadata, duration down to the second, and audio streams up to 1 hour (3600s).
 */
class YouTubeAudioSourceProvider(
    private val httpClient: OkHttpClient = defaultHttpClient()
) : AudioSourceProvider {

    override val providerId: String = "youtube"
    override val displayName: String = "YouTube"

    override fun canHandle(url: String): Boolean {
        return YouTubeUrlParser.isYouTubeUrl(url)
    }

    override fun normalizeUrl(url: String): String {
        return YouTubeUrlParser.normalize(url)
    }

    override suspend fun analyze(url: String): Result<AudioMetadata> = withContext(Dispatchers.IO) {
        val videoId = YouTubeUrlParser.extractVideoId(url)
            ?: return@withContext Result.failure(IllegalArgumentException("Unsupported source: Not a valid YouTube URL"))

        val normalized = normalizeUrl(url)

        // Try Strategy 1: YouTube Innertube to get exact title, author and lengthSeconds
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
                        val durationMs = (lengthSec * 1000L).coerceAtMost(YouTubeStreamExtractor.MAX_ALLOWED_DURATION_MS)

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
        } catch (_: Exception) {
            // Fallback to oEmbed
        }

        // Strategy 2: oEmbed fallback
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
        } catch (_: Exception) {
            // Fallback
        }

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

    override suspend fun obtainAudio(
        url: String,
        customTitle: String?,
        destinationDirectory: File,
        onProgress: (DownloadProgress) -> Unit
    ): Result<AudioResult> = withContext(Dispatchers.IO) {
        val videoId = YouTubeUrlParser.extractVideoId(url)
            ?: return@withContext Result.failure(IllegalArgumentException("Unsupported source: Not a valid YouTube URL"))

        try {
            onProgress(
                DownloadProgress(
                    stage = DownloadStage.FETCHING_METADATA,
                    progressPercent = 0.1f,
                    message = "Analyzing YouTube video metadata..."
                )
            )

            val metadataResult = analyze(url)
            val metadata = metadataResult.getOrNull() ?: AudioMetadata(
                title = customTitle ?: "YouTube Track ($videoId)",
                author = "YouTube Audio",
                durationMs = 0L,
                thumbnailUrl = YouTubeUrlParser.getThumbnailUrl(videoId),
                originalUrl = normalizeUrl(url),
                sourceProvider = providerId
            )

            val finalTitle = customTitle?.takeIf { it.isNotBlank() } ?: metadata.title

            if (!destinationDirectory.exists()) {
                destinationDirectory.mkdirs()
            }

            onProgress(
                DownloadProgress(
                    stage = DownloadStage.DOWNLOADING_AUDIO,
                    progressPercent = 0.2f,
                    message = "Resolving audio stream..."
                )
            )

            // Extract direct audio stream from YouTube
            val streamResult = YouTubeStreamExtractor.extractAudioStream(videoId)
            val stream = streamResult.getOrElse { error ->
                throw IllegalStateException("No se pudo obtener el audio de YouTube: ${error.message}")
            }

            if (stream.streamUrl.isBlank()) {
                throw IllegalStateException("La URL del stream de audio obtenida está vacía.")
            }

            val ext = when {
                stream.mimeType.contains("webm") -> "webm"
                stream.mimeType.contains("mp4") || stream.mimeType.contains("m4a") -> "m4a"
                else -> "mp3"
            }
            val filename = AudioMetadata.sanitizeFilename("${metadata.author} - $finalTitle.$ext")
            val outputFile = File(destinationDirectory, filename)

            val downloadRequest = Request.Builder()
                .url(stream.streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://www.youtube.com/")
                .header("Accept", "*/*")
                .build()

            httpClient.newCall(downloadRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Error al descargar el flujo de audio: HTTP ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("Cuerpo de respuesta vacío")
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
                        val percent = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes.toFloat()) * 0.75f + 0.2f else 0.6f

                        onProgress(
                            DownloadProgress(
                                stage = DownloadStage.DOWNLOADING_AUDIO,
                                progressPercent = percent.coerceIn(0f, 0.95f),
                                bytesRead = bytesRead,
                                totalBytes = totalBytes,
                                message = "Descargando pista de audio completa..."
                            )
                        )
                    }
                }
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw IllegalStateException("El archivo descargado está vacío o no se guardó correctamente.")
            }

            onProgress(
                DownloadProgress(
                    stage = DownloadStage.SAVED_TO_DOWNLOADS,
                    progressPercent = 1.0f,
                    bytesRead = outputFile.length(),
                    totalBytes = outputFile.length(),
                    message = "Guardado en la carpeta de descargas"
                )
            )

            val resolvedDuration = when {
                stream.durationMs > 0 -> stream.durationMs
                metadata.durationMs > 0 -> metadata.durationMs
                else -> 0L
            }

            val finalMetadata = metadata.copy(
                title = finalTitle,
                durationMs = resolvedDuration
            )

            Result.success(
                AudioResult(
                    file = outputFile,
                    mimeType = stream.mimeType,
                    durationMs = resolvedDuration,
                    sizeBytes = outputFile.length(),
                    metadata = finalMetadata
                )
            )
        } catch (e: Exception) {
            onProgress(
                DownloadProgress(
                    stage = DownloadStage.ERROR,
                    progressPercent = 0f,
                    message = "Error: ${e.message}"
                )
            )
            Result.failure(e)
        }
    }

    companion object {
        private const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

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
