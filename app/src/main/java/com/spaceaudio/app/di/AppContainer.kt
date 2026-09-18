package com.spaceaudio.app.di

import android.content.Context
import com.spaceaudio.app.core.source.AudioSourceManager
import com.spaceaudio.app.core.source.direct.DirectAudioUrlSourceProvider
import com.spaceaudio.app.core.source.youtube.YouTubeAudioSourceProvider
import com.spaceaudio.app.data.local.SpaceAudioDatabase
import com.spaceaudio.app.data.repository.AudioRepository
import com.spaceaudio.app.data.repository.AudioRepositoryImpl
import com.spaceaudio.app.data.storage.LocalStorageManager
import com.spaceaudio.app.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppContainer(private val context: Context) {

    val database: SpaceAudioDatabase by lazy {
        SpaceAudioDatabase.getInstance(context)
    }

    val localStorageManager: LocalStorageManager by lazy {
        LocalStorageManager(context)
    }

    val audioSourceManager: AudioSourceManager by lazy {
        AudioSourceManager(
            providers = listOf(
                YouTubeAudioSourceProvider(),
                DirectAudioUrlSourceProvider()
            )
        )
    }

    val audioRepository: AudioRepository by lazy {
        AudioRepositoryImpl(
            audioSourceManager = audioSourceManager,
            localStorageManager = localStorageManager,
            trackDao = database.trackDao(),
            folderDao = database.folderDao(),
            playlistDao = database.playlistDao()
        )
    }

    val playerController: PlayerController by lazy {
        PlayerController(context).apply {
            onTrackDurationDiscovered = { trackId, durationMs ->
                CoroutineScope(Dispatchers.IO).launch {
                    audioRepository.updateTrackDuration(trackId, durationMs)
                }
            }
        }
    }
}
