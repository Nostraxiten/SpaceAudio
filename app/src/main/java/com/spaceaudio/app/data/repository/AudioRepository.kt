package com.spaceaudio.app.data.repository

import com.spaceaudio.app.core.source.AudioMetadata
import com.spaceaudio.app.core.source.DownloadProgress
import com.spaceaudio.app.data.local.entity.FolderEntity
import com.spaceaudio.app.data.local.entity.PlaylistEntity
import com.spaceaudio.app.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

interface AudioRepository {

    /**
     * Analyzes any supported URL (YouTube, Direct Audio, etc.) and returns preview metadata.
     */
    suspend fun analyzeUrl(url: String): Result<AudioMetadata>

    /**
     * Imports audio from the URL, writes it to the local Downloads directory,
     * extracts exact audio duration, and records the entity into Room with a 5-minute NEW badge.
     */
    suspend fun importAudio(
        url: String,
        customTitle: String? = null,
        targetFolder: String = "Downloads",
        onProgress: (DownloadProgress) -> Unit = {}
    ): Result<TrackEntity>

    /**
     * Imports an existing local audio file into the SpaceAudio library.
     */
    suspend fun importLocalFile(file: File, folderName: String = "Downloads"): Result<TrackEntity>

    fun getAllTracks(): Flow<List<TrackEntity>>
    fun getTracksByFolder(folderName: String): Flow<List<TrackEntity>>
    fun getFavoriteTracks(): Flow<List<TrackEntity>>
    fun getRecentlyPlayedTracks(): Flow<List<TrackEntity>>
    fun searchTracks(query: String): Flow<List<TrackEntity>>
    fun getAllFolderNames(): Flow<List<String>>
    fun getAllFolders(): Flow<List<FolderEntity>>
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    fun getTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>>

    suspend fun setFavorite(trackId: Long, isFavorite: Boolean)
    suspend fun recordTrackPlayed(trackId: Long)
    suspend fun updateTrackDuration(trackId: Long, durationMs: Long)
    suspend fun deleteTrack(track: TrackEntity, deleteLocalFile: Boolean = true)
    suspend fun createPlaylist(name: String): Long
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long)
    suspend fun createFolder(name: String): Result<FolderEntity>
    suspend fun moveTrackToFolder(trackId: Long, folderName: String)
    suspend fun deleteFolder(folderName: String)
}
