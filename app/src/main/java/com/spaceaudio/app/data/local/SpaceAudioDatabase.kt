package com.spaceaudio.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.spaceaudio.app.data.local.dao.FolderDao
import com.spaceaudio.app.data.local.dao.PlaylistDao
import com.spaceaudio.app.data.local.dao.TrackDao
import com.spaceaudio.app.data.local.entity.FolderEntity
import com.spaceaudio.app.data.local.entity.PlaylistEntity
import com.spaceaudio.app.data.local.entity.PlaylistTrackCrossRef
import com.spaceaudio.app.data.local.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        FolderEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SpaceAudioDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun folderDao(): FolderDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: SpaceAudioDatabase? = null

        fun getInstance(context: Context): SpaceAudioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpaceAudioDatabase::class.java,
                    "space_audio.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
