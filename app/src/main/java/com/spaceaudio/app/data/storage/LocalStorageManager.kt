package com.spaceaudio.app.data.storage

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import java.io.File

/**
 * Manages physical audio files on the Android filesystem.
 * Handles the "Downloads" directory and extraction of local audio attributes.
 */
class LocalStorageManager(private val context: Context) {

    /**
     * Retrieves the SpaceAudio Downloads directory.
     * Ensures it exists on disk.
     */
    fun getDownloadsDirectory(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Retrieves a named sub-folder within the SpaceAudio storage.
     */
    fun getOrCreateFolder(folderName: String): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val folder = File(base, folderName)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }

    /**
     * Extracts precise duration and embedded tags using Android's MediaMetadataRetriever.
     */
    fun extractMediaInfo(file: File): ExtractedMediaInfo {
        if (!file.exists()) return ExtractedMediaInfo(0L, null, null, null)

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)

            ExtractedMediaInfo(
                durationMs = durationMs,
                title = title,
                artist = artist,
                album = album
            )
        } catch (_: Exception) {
            ExtractedMediaInfo(0L, null, null, null)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Safely deletes an audio file from disk.
     */
    fun deleteAudioFile(filePath: String): Boolean {
        val file = File(filePath)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}

data class ExtractedMediaInfo(
    val durationMs: Long,
    val title: String?,
    val artist: String?,
    val album: String?
)
