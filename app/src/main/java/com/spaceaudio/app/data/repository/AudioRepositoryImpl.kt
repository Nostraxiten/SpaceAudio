package com.spaceaudio.app.data.repository

import com.spaceaudio.app.core.source.AudioMetadata
import com.spaceaudio.app.core.source.AudioSourceManager
import com.spaceaudio.app.core.source.DownloadProgress
import com.spaceaudio.app.core.source.DownloadStage
import com.spaceaudio.app.data.local.dao.FolderDao
import com.spaceaudio.app.data.local.dao.PlaylistDao
import com.spaceaudio.app.data.local.dao.TrackDao
import com.spaceaudio.app.data.local.entity.FolderEntity
import com.spaceaudio.app.data.local.entity.PlaylistEntity
import com.spaceaudio.app.data.local.entity.PlaylistTrackCrossRef
import com.spaceaudio.app.data.local.entity.TrackEntity
import com.spaceaudio.app.data.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class AudioRepositoryImpl(
    private val audioSourceManager: AudioSourceManager,
    private val localStorageManager: LocalStorageManager,
    private val trackDao: TrackDao,
    private val folderDao: FolderDao,
    private val playlistDao: PlaylistDao
) : AudioRepository {

    override suspend fun analyzeUrl(url: String): Result<AudioMetadata> {
        return audioSourceManager.analyzeUrl(url)
    }

    override suspend fun importAudio(
        url: String,
        customTitle: String?,
        targetFolder: String,
        onProgress: (DownloadProgress) -> Unit
    ): Result<TrackEntity> = withContext(Dispatchers.IO) {
        val targetDir = if (targetFolder == "Downloads") {
            localStorageManager.getDownloadsDirectory()
        } else {
            localStorageManager.getOrCreateFolder(targetFolder)
        }

        val result = audioSourceManager.obtainAudio(
            url = url,
            customTitle = customTitle,
            destinationDirectory = targetDir,
            onProgress = onProgress
        )

        val audioResult = result.getOrElse {
            return@withContext Result.failure(it)
        }

        onProgress(
            DownloadProgress(
                stage = DownloadStage.FINALIZING,
                progressPercent = 0.95f,
                message = "Indexing track in SpaceAudio..."
            )
        )

        // Extract accurate duration from downloaded local file
        val mediaInfo = localStorageManager.extractMediaInfo(audioResult.file)
        val finalDuration = if (mediaInfo.durationMs > 0) mediaInfo.durationMs else audioResult.durationMs
        val now = System.currentTimeMillis()

        // Create Room Track Entity with 5-minute NEW badge
        val trackEntity = TrackEntity(
            title = audioResult.metadata.title,
            artist = if (audioResult.metadata.author.isNotBlank()) audioResult.metadata.author else (mediaInfo.artist ?: "Unknown Artist"),
            album = mediaInfo.album ?: "SpaceAudio",
            durationMs = finalDuration,
            filePath = audioResult.file.absolutePath,
            fileSize = audioResult.file.length(),
            mimeType = audioResult.mimeType,
            originalUrl = audioResult.metadata.originalUrl,
            sourceProvider = audioResult.metadata.sourceProvider,
            thumbnailUri = audioResult.metadata.thumbnailUrl,
            folderName = targetFolder,
            addedTimestamp = now,
            isNewUntilTimestamp = now + (5 * 60 * 1000L), // 5 minutes NEW badge
            isFavorite = false,
            playCount = 0
        )

        val insertedId = trackDao.insertTrack(trackEntity)

        // Register / update folder entity
        folderDao.insertFolder(
            FolderEntity(
                name = targetFolder,
                path = targetDir.absolutePath,
                trackCount = 1,
                lastUpdated = now
            )
        )

        onProgress(
            DownloadProgress(
                stage = DownloadStage.SAVED_TO_DOWNLOADS,
                progressPercent = 1.0f,
                message = "Import complete! Added to $targetFolder"
            )
        )

        Result.success(trackEntity.copy(id = insertedId))
    }

    override suspend fun importLocalFile(file: File, folderName: String): Result<TrackEntity> = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext Result.failure(IllegalArgumentException("File does not exist: ${file.absolutePath}"))
        }

        val mediaInfo = localStorageManager.extractMediaInfo(file)
        val now = System.currentTimeMillis()
        val title = mediaInfo.title ?: file.nameWithoutExtension
        val artist = mediaInfo.artist ?: "Local Audio"

        val trackEntity = TrackEntity(
            title = title,
            artist = artist,
            album = mediaInfo.album ?: "Local Storage",
            durationMs = mediaInfo.durationMs,
            filePath = file.absolutePath,
            fileSize = file.length(),
            mimeType = "audio/mpeg",
            sourceProvider = "local_file",
            folderName = folderName,
            addedTimestamp = now,
            isNewUntilTimestamp = now + (5 * 60 * 1000L), // 5 minutes NEW badge
            isFavorite = false,
            playCount = 0
        )

        val insertedId = trackDao.insertTrack(trackEntity)
        Result.success(trackEntity.copy(id = insertedId))
    }

    override fun getAllTracks(): Flow<List<TrackEntity>> = trackDao.getAllTracks()

    override fun getTracksByFolder(folderName: String): Flow<List<TrackEntity>> =
        trackDao.getTracksByFolder(folderName)

    override fun getFavoriteTracks(): Flow<List<TrackEntity>> = trackDao.getFavoriteTracks()

    override fun getRecentlyPlayedTracks(): Flow<List<TrackEntity>> =
        trackDao.getRecentlyPlayedTracks()

    override fun searchTracks(query: String): Flow<List<TrackEntity>> =
        trackDao.searchTracks(query)

    override fun getAllFolderNames(): Flow<List<String>> = trackDao.getAllFolderNames()

    override fun getAllFolders(): Flow<List<FolderEntity>> = folderDao.getAllFolders()

    override fun getAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    override fun getTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>> =
        playlistDao.getTracksForPlaylist(playlistId)

    override suspend fun setFavorite(trackId: Long, isFavorite: Boolean) {
        trackDao.setFavorite(trackId, isFavorite)
    }

    override suspend fun recordTrackPlayed(trackId: Long) {
        trackDao.incrementPlayCount(trackId)
    }

    override suspend fun updateTrackDuration(trackId: Long, durationMs: Long) {
        if (durationMs > 0) {
            trackDao.updateTrackDuration(trackId, durationMs)
        }
    }

    override suspend fun deleteTrack(track: TrackEntity, deleteLocalFile: Boolean) {
        trackDao.deleteTrack(track)
        if (deleteLocalFile) {
            localStorageManager.deleteAudioFile(track.filePath)
        }
    }

    override suspend fun createPlaylist(name: String): Long {
        return playlistDao.insertPlaylist(PlaylistEntity(name = name))
    }

    override suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.insertTrackToPlaylist(PlaylistTrackCrossRef(playlistId, trackId))
    }

    override suspend fun createFolder(name: String): Result<FolderEntity> = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Folder name cannot be empty"))
        }
        val folderDir = localStorageManager.getOrCreateFolder(trimmed)
        val entity = FolderEntity(
            name = trimmed,
            path = folderDir.absolutePath,
            trackCount = 0,
            lastUpdated = System.currentTimeMillis()
        )
        folderDao.insertFolder(entity)
        Result.success(entity)
    }

    override suspend fun moveTrackToFolder(trackId: Long, folderName: String) = withContext(Dispatchers.IO) {
        trackDao.updateTrackFolder(trackId, folderName)
    }

    override suspend fun deleteFolder(folderName: String) = withContext(Dispatchers.IO) {
        val folder = folderDao.getFolderByName(folderName)
        if (folder != null) {
            folderDao.deleteFolder(folder)
        }
    }
}

