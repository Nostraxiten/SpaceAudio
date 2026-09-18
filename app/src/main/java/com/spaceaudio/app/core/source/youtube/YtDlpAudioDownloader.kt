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
                    progressPercent = 0.25f,
                    message = "Downloading audio from YouTube..."
                )
            )

            val temporaryDirectory = File(destinationDirectory, ".yt-dlp")
            temporaryDirectory.mkdirs()
            val resultJson = Python.getInstance()
                .getModule("youtube_downloader")
                .callAttr("download_audio", url, temporaryDirectory.absolutePath)
                .toString()
            val result = JSONObject(resultJson)
            val sourceFile = File(result.getString("path"))
            if (!sourceFile.isFile || sourceFile.length() == 0L) {
                throw IllegalStateException("yt-dlp no produjo un archivo de audio válido")
            }

            val title = customTitle?.takeIf { it.isNotBlank() }
                ?: result.optString("title", "YouTube Audio")
            val author = result.optString("author", "YouTube Creator")
            val outputFile = File(
                destinationDirectory,
                AudioMetadata.sanitizeFilename("$author - $title.mp3")
            )

            onProgress(
                DownloadProgress(
                    stage = DownloadStage.FINALIZING,
                    progressPercent = 0.8f,
                    message = "Converting audio to MP3..."
                )
            )

            val session = FFmpegKit.execute(
                "-y -i ${quote(sourceFile)} -vn -codec:a libmp3lame -qscale:a 2 " +
                    "-metadata title=${quoteValue(title)} -metadata artist=${quoteValue(author)} ${quote(outputFile)}"
            )
            if (!ReturnCode.isSuccess(session.returnCode) || !outputFile.isFile || outputFile.length() == 0L) {
                throw IllegalStateException("FFmpeg no pudo convertir el audio a MP3")
            }

            sourceFile.delete()
            DownloadedYouTubeAudio(
                file = outputFile,
                title = title,
                author = author,
                durationMs = result.optLong("durationMs", 0L)
            ).let { Result.success(it) }
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
