package com.spaceaudio.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["folderName"]),
        Index(value = ["isFavorite"])
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String = "Unknown Artist",
    val album: String = "SpaceAudio",
    val durationMs: Long = 0L,
    val filePath: String,
    val fileSize: Long = 0L,
    val mimeType: String = "audio/mpeg",
    val originalUrl: String? = null,
    val sourceProvider: String = "local",
    val thumbnailUri: String? = null,
    val folderName: String = "Downloads",
    val addedTimestamp: Long = System.currentTimeMillis(),
    /**
     * Visual NEW badge expiration timestamp.
     * By default set to addedTimestamp + 5 minutes (5 * 60 * 1000 = 300,000 ms).
     */
    val isNewUntilTimestamp: Long = addedTimestamp + (5 * 60 * 1000L),
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long? = null
) {
    /**
     * Determines whether this track should display the glowing NEW badge.
     */
    val isNew: Boolean
        get() = System.currentTimeMillis() < isNewUntilTimestamp
}
