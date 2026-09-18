package com.spaceaudio.app.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.spaceaudio.app.data.local.entity.TrackEntity
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
        val currentQueue = if (_playerState.value.isShuffleEnabled) {
            val shuffled = trackList.filter { it.id != track.id }.shuffled().toMutableList()
            shuffled.add(0, track)
            shuffled
        } else {
            trackList
        }

        val targetIndex = currentQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        _playerState.update {
            it.copy(
                currentTrack = track,
                queue = currentQueue,
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

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .build()

        val uri = Uri.fromFile(file)
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(mediaMetadata)
            .build()

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
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

        val nextIndex = when (state.repeatMode) {
            RepeatMode.ONE -> state.currentIndex
            RepeatMode.ALL -> (state.currentIndex + 1) % state.queue.size
            RepeatMode.OFF -> {
                if (state.currentIndex < state.queue.size - 1) state.currentIndex + 1 else return
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

        val prevIndex = when (state.repeatMode) {
            RepeatMode.ONE -> state.currentIndex
            RepeatMode.ALL -> if (state.currentIndex > 0) state.currentIndex - 1 else state.queue.size - 1
            RepeatMode.OFF -> if (state.currentIndex > 0) state.currentIndex - 1 else 0
        }

        val prevTrack = state.queue.getOrNull(prevIndex) ?: return
        _playerState.update { it.copy(currentTrack = prevTrack, currentIndex = prevIndex) }
        playMediaItem(prevTrack)
    }

    fun toggleShuffle() {
        val newState = !_playerState.value.isShuffleEnabled
        val currentTrack = _playerState.value.currentTrack

        val newQueue = if (newState) {
            if (currentTrack != null) {
                val rest = originalQueue.filter { it.id != currentTrack.id }.shuffled().toMutableList()
                rest.add(0, currentTrack)
                rest
            } else {
                originalQueue.shuffled()
            }
        } else {
            originalQueue
        }

        val newIndex = currentTrack?.let { track -> newQueue.indexOfFirst { it.id == track.id } } ?: 0

        _playerState.update {
            it.copy(
                isShuffleEnabled = newState,
                queue = newQueue,
                currentIndex = newIndex.coerceAtLeast(0)
            )
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
                if (_playerState.value.hasNext) {
                    skipToNext()
                } else {
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
