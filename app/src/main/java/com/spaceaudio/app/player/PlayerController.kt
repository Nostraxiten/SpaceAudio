package com.spaceaudio.app.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.spaceaudio.app.data.local.entity.TrackEntity
import com.spaceaudio.app.player.service.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class PlayerController(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job()),
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
) {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var originalQueue: List<TrackEntity> = emptyList()
    private val shuffleHistory = ArrayDeque<Int>()
    private var positionTickerJob: Job? = null
    var onTrackDurationDiscovered: ((trackId: Long, durationMs: Long) -> Unit)? = null

    init {
        setupPlayerListener()
    }

    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    checkAndUpdateDuration()
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                _playerState.update { it.copy(isBuffering = isBuffering) }
                checkAndUpdateDuration()

                if (playbackState == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                checkAndUpdateDuration()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                    events.contains(Player.EVENT_IS_PLAYING_CHANGED)
                ) {
                    checkAndUpdateDuration()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlayerController", "ExoPlayer error: ${error.message}", error)
                _playerState.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false
                    )
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                _playerState.update { it.copy(currentPositionMs = newPosition.positionMs) }
                checkAndUpdateDuration()
            }
        })
    }

    private fun checkAndUpdateDuration() {
        val actualDuration = exoPlayer.duration
        if (actualDuration > 0) {
            val state = _playerState.value
            val currentTrack = state.currentTrack
            if (currentTrack != null && (currentTrack.durationMs != actualDuration || state.durationMs != actualDuration)) {
                val updatedTrack = currentTrack.copy(durationMs = actualDuration)
                val updatedQueue = state.queue.map { if (it.id == currentTrack.id) updatedTrack else it }
                _playerState.update {
                    it.copy(
                        durationMs = actualDuration,
                        currentTrack = updatedTrack,
                        queue = updatedQueue
                    )
                }
                onTrackDurationDiscovered?.invoke(currentTrack.id, actualDuration)
            } else if (state.durationMs != actualDuration) {
                _playerState.update { it.copy(durationMs = actualDuration) }
            }
        }
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    checkAndUpdateDuration()
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration.takeIf { it > 0 } ?: _playerState.value.durationMs
                    _playerState.update { it.copy(currentPositionMs = pos, durationMs = dur) }
                }
                delay(300)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    fun playTrack(track: TrackEntity, trackList: List<TrackEntity> = listOf(track)) {
        originalQueue = trackList
        shuffleHistory.clear()
        val targetIndex = trackList.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        _playerState.update {
            it.copy(
                currentTrack = track,
                queue = trackList,
                currentIndex = targetIndex,
                durationMs = track.durationMs
            )
        }

        playMediaItem(track)
    }

    private fun playMediaItem(track: TrackEntity) {
        val file = File(track.filePath)
        if (!file.exists()) {
            Log.w("PlayerController", "Audio file not found: ${track.filePath}")
            return
        }

        val mediaMetadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)

        if (!track.thumbnailUri.isNullOrBlank()) {
            try {
                mediaMetadataBuilder.setArtworkUri(Uri.parse(track.thumbnailUri))
            } catch (e: Exception) {
                Log.w("PlayerController", "Could not parse artwork URI: ${track.thumbnailUri}")
            }
        }

        val uri = Uri.fromFile(file)
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(mediaMetadataBuilder.build())
            .build()

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        // Ensure background / lock-screen notification service is running
        try {
            val serviceIntent = Intent(context, MusicService::class.java)
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.w("PlayerController", "Could not start MusicService: ${e.message}")
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (_playerState.value.currentTrack != null) {
                exoPlayer.play()
            } else if (_playerState.value.queue.isNotEmpty()) {
                val firstTrack = _playerState.value.queue.first()
                playTrack(firstTrack, _playerState.value.queue)
            }
        }
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun play() {
        exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playerState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun skipToNext() {
        val state = _playerState.value
        if (state.queue.isEmpty()) return

        if (state.queue.size == 1) {
            seekTo(0)
            play()
            return
        }

        val nextIndex = if (state.isShuffleEnabled) {
            // Push current index to history for going back
            if (state.currentIndex in state.queue.indices) {
                shuffleHistory.addLast(state.currentIndex)
                if (shuffleHistory.size > 100) shuffleHistory.removeFirst()
            }
            // Pick randomly excluding current track
            val candidates = state.queue.indices.filter { it != state.currentIndex }
            if (candidates.isNotEmpty()) candidates.random() else 0
        } else {
            if (state.repeatMode == RepeatMode.ONE) {
                state.currentIndex
            } else {
                // Infinite sequential loop
                (state.currentIndex + 1) % state.queue.size
            }
        }

        val nextTrack = state.queue.getOrNull(nextIndex) ?: return
        _playerState.update { it.copy(currentTrack = nextTrack, currentIndex = nextIndex) }
        playMediaItem(nextTrack)
    }

    fun skipToPrevious() {
        val state = _playerState.value
        if (exoPlayer.currentPosition > 3000) {
            seekTo(0)
            return
        }
        if (state.queue.isEmpty()) return

        if (state.queue.size == 1) {
            seekTo(0)
            play()
            return
        }

        val prevIndex = if (state.isShuffleEnabled) {
            if (shuffleHistory.isNotEmpty()) {
                shuffleHistory.removeLast()
            } else {
                val candidates = state.queue.indices.filter { it != state.currentIndex }
                if (candidates.isNotEmpty()) candidates.random() else 0
            }
        } else {
            if (state.repeatMode == RepeatMode.ONE) {
                state.currentIndex
            } else {
                // Infinite backward loop
                if (state.currentIndex > 0) state.currentIndex - 1 else state.queue.size - 1
            }
        }

        val prevTrack = state.queue.getOrNull(prevIndex) ?: return
        _playerState.update { it.copy(currentTrack = prevTrack, currentIndex = prevIndex) }
        playMediaItem(prevTrack)
    }

    fun toggleShuffle() {
        val newState = !_playerState.value.isShuffleEnabled
        shuffleHistory.clear()
        _playerState.update {
            it.copy(isShuffleEnabled = newState)
        }
    }

    fun cycleRepeatMode() {
        val nextMode = _playerState.value.repeatMode.next()
        _playerState.update { it.copy(repeatMode = nextMode) }
    }

    fun addToQueue(track: TrackEntity) {
        _playerState.update { state ->
            val updated = state.queue + track
            state.copy(queue = updated)
        }
    }

    fun playNext(track: TrackEntity) {
        _playerState.update { state ->
            val mutable = state.queue.toMutableList()
            val insertPos = (state.currentIndex + 1).coerceAtMost(mutable.size)
            mutable.add(insertPos, track)
            state.copy(queue = mutable)
        }
    }

    fun removeFromQueue(index: Int) {
        _playerState.update { state ->
            if (index in state.queue.indices) {
                val mutable = state.queue.toMutableList()
                mutable.removeAt(index)
                val newIndex = when {
                    index < state.currentIndex -> state.currentIndex - 1
                    index == state.currentIndex -> state.currentIndex.coerceAtMost(mutable.size - 1)
                    else -> state.currentIndex
                }
                state.copy(queue = mutable, currentIndex = newIndex)
            } else state
        }
    }

    fun reorderQueue(from: Int, to: Int) {
        _playerState.update { state ->
            if (from in state.queue.indices && to in state.queue.indices) {
                val mutable = state.queue.toMutableList()
                val item = mutable.removeAt(from)
                mutable.add(to, item)
                val currentTrack = state.currentTrack
                val newIndex = currentTrack?.let { track -> mutable.indexOfFirst { it.id == track.id } } ?: state.currentIndex
                state.copy(queue = mutable, currentIndex = newIndex)
            } else state
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        exoPlayer.playbackParameters = PlaybackParameters(clamped)
        _playerState.update { it.copy(playbackSpeed = clamped) }
    }

    private fun handleTrackEnded() {
        when (_playerState.value.repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0)
                play()
            }
            RepeatMode.ALL -> skipToNext()
            RepeatMode.OFF -> {
                val state = _playerState.value
                if (state.isShuffleEnabled || state.currentIndex < state.queue.size - 1) {
                    skipToNext()
                } else {
                    _playerState.update { it.copy(currentIndex = 0, currentTrack = it.queue.firstOrNull()) }
                    pause()
                    seekTo(0)
                }
            }
        }
    }

    fun release() {
        stopPositionTicker()
        exoPlayer.release()
    }
}
