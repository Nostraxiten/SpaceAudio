package com.spaceaudio.app.player

import com.spaceaudio.app.data.local.entity.TrackEntity

data class PlayerState(
    val currentTrack: TrackEntity? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<TrackEntity> = emptyList(),
    val currentIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffleEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val hasNext: Boolean
        get() = when (repeatMode) {
            RepeatMode.ONE, RepeatMode.ALL -> queue.isNotEmpty()
            RepeatMode.OFF -> currentIndex < queue.size - 1
        }

    val hasPrevious: Boolean
        get() = when (repeatMode) {
            RepeatMode.ONE, RepeatMode.ALL -> queue.isNotEmpty()
            RepeatMode.OFF -> currentIndex > 0 || currentPositionMs > 3000
        }
}
