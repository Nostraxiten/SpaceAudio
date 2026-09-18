package com.spaceaudio.app.core.source.youtube

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.spaceaudio.app.core.source.AudioMetadata
import com.spaceaudio.app.core.source.DownloadProgress
import com.spaceaudio.app.core.source.DownloadStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class YtDlpAudioDownloader(context: Context) {

    private val applicationContext = context.applicationContext

    suspend fun download(
        url: String,
        customTitle: String?,
        destinationDirectory: File,
        onProgress: (DownloadProgress) -> Unit
    ): Result<DownloadedYouTubeAudio> = withContext(Dispatchers.IO) {
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(applicationContext))
            }

            onProgress(
                DownloadProgress(
                    stage = DownloadStage.DOWNLOADING_AUDIO,
                    progressPercent = 0.2f,
                    message = "Downloading audio from YouTube..."
                )
            )

            val temporaryDirectory = File(destinationDirectory, ".yt-dlp")
            temporaryDirectory.mkdirs()

            // Invoke Python yt-dlp downloader — multi-client iOS/mweb cascade handles 403
            val resultJson = try {
                Python.getInstance()
                    .getModule("youtube_downloader")
                    .callAttr("download_audio", url, temporaryDirectory.absolutePath)
                    .toString()
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Failed to download YouTube audio. " +
                    "Check your internet connection or try again later.\n" +
                    "Detail: ${e.message?.take(200)}",
                    e
                )
            }

            val result = JSONObject(resultJson)
            val sourceFile = File(result.getString("path"))
            if (!sourceFile.isFile || sourceFile.length() == 0L) {
                throw IllegalStateException(
                    "yt-dlp downloaded an empty or missing audio file. " +
                    "The video may be unavailable or region-restricted."
                )
            }

            val title = customTitle?.takeIf { it.isNotBlank() }
                ?: result.optString("title", "YouTube Audio")
            val author = result.optString("author", "YouTube Creator")
            val durationMs = result.optLong("durationMs", 0L)

            val outputFile = File(
                destinationDirectory,
                AudioMetadata.sanitizeFilename("$author - $title.mp3")
            )

            onProgress(
                DownloadProgress(
                    stage = DownloadStage.FINALIZING,
                    progressPercent = 0.75f,
                    message = "Converting to MP3..."
                )
            )

            // Convert to high-quality MP3 with embedded ID3 metadata tags
            val ffmpegCmd = buildString {
                append("-y ")
                append("-i ${quote(sourceFile)} ")
                append("-vn ")                        // Strip video stream
                append("-codec:a libmp3lame ")        // MP3 encoder
                append("-qscale:a 2 ")               // VBR quality ~190 kbps
                append("-id3v2_version 3 ")          // ID3v2.3 compatibility
                append("-metadata title=${quoteValue(title)} ")
                append("-metadata artist=${quoteValue(author)} ")
                append("-metadata album=SpaceAudio ")
                append(quote(outputFile))
            }

            val session = FFmpegKit.execute(ffmpegCmd)
            if (!ReturnCode.isSuccess(session.returnCode) || !outputFile.isFile || outputFile.length() == 0L) {
                throw IllegalStateException(
                    "FFmpeg failed to convert the audio to MP3. " +
                    "Code: ${session.returnCode}"
                )
            }

            // Clean up raw downloaded file
            sourceFile.delete()

            Result.success(
                DownloadedYouTubeAudio(
                    file = outputFile,
                    title = title,
                    author = author,
                    durationMs = durationMs
                )
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun quote(file: File): String = "'${file.absolutePath.replace("'", "'\\''")}'"

    private fun quoteValue(value: String): String = "'${value.replace("'", "'\\''")}'"
}

data class DownloadedYouTubeAudio(
    val file: File,
    val title: String,
    val author: String,
    val durationMs: Long
)
