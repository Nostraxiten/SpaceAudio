package com.spaceaudio.app.ui.screens.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.spaceaudio.app.data.local.entity.TrackEntity
import com.spaceaudio.app.player.PlayerState
import com.spaceaudio.app.player.RepeatMode
import com.spaceaudio.app.ui.components.NewBadge
import com.spaceaudio.app.ui.components.formatDuration
import com.spaceaudio.app.ui.theme.LaserPink
import com.spaceaudio.app.ui.theme.NebulaViolet
import com.spaceaudio.app.ui.theme.NeonCyan
import com.spaceaudio.app.ui.theme.SpaceBlack
import com.spaceaudio.app.ui.theme.SpaceCardBg
import com.spaceaudio.app.ui.theme.SpaceDarkBg
import com.spaceaudio.app.ui.theme.SpaceSurfaceSubtle
import com.spaceaudio.app.ui.theme.TextMuted
import com.spaceaudio.app.ui.theme.TextPrimary
import com.spaceaudio.app.ui.theme.TextSecondary

@Composable
fun FullPlayerScreen(
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onTrackSelect: (TrackEntity) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack ?: return
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var showQueueSheet by remember { mutableStateOf(false) }

    // Vinyl rotation animation when playing
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotate")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "angle"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SpaceCardBg,
                        SpaceDarkBg,
                        SpaceBlack
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimize",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PLAYING FROM",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = track.folderName.uppercase(),
                    color = NeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(onClick = { showQueueSheet = true }) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "Queue",
                    tint = TextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Center Artwork / Vinyl pulsing animation
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .shadow(
                    elevation = 28.dp,
                    shape = CircleShape,
                    ambientColor = NeonCyan.copy(alpha = 0.4f),
                    spotColor = NebulaViolet.copy(alpha = 0.6f)
                )
                .clip(CircleShape)
                .background(SpaceDarkBg)
                .border(2.dp, NeonCyan.copy(alpha = 0.3f), CircleShape)
                .rotate(if (playerState.isPlaying) rotationAngle else 0f),
            contentAlignment = Alignment.Center
        ) {
            if (!track.thumbnailUri.isNullOrBlank()) {
                AsyncImage(
                    model = track.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(80.dp)
                )
            }

            // Center Vinyl Spindle Hole
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SpaceBlack)
                    .border(2.dp, NeonCyan, CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title, Artist and NEW Badge
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = track.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (track.isNew) {
                    NewBadge()
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = track.artist,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Progress Slider
        val currentPos = if (isDraggingSlider) (dragPosition * playerState.durationMs).toLong() else playerState.currentPositionMs
        val sliderValue = if (playerState.durationMs > 0) {
            (currentPos.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        Slider(
            value = sliderValue,
            onValueChange = {
                isDraggingSlider = true
                dragPosition = it
            },
            onValueChangeFinished = {
                isDraggingSlider = false
                val targetMs = (dragPosition * playerState.durationMs).toLong()
                onSeek(targetMs)
            },
            colors = SliderDefaults.colors(
                thumbColor = NeonCyan,
                activeTrackColor = NeonCyan,
                inactiveTrackColor = SpaceSurfaceSubtle
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPos),
                color = TextMuted,
                fontSize = 12.sp
            )
            Text(
                text = formatDuration(playerState.durationMs),
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Main Controls Row (Shuffle, Prev, Play/Pause, Next, Repeat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle Button
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playerState.isShuffleEnabled) NeonCyan else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Previous Button
            IconButton(
                onClick = onSkipPrevious,
                enabled = playerState.hasPrevious
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = if (playerState.hasPrevious) TextPrimary else TextMuted,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Play / Pause Neon Button
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .shadow(16.dp, CircleShape, spotColor = NeonCyan)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NeonCyan, Color(0xFF00B0FF))
                        )
                    )
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    tint = SpaceBlack,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Next Button
            IconButton(
                onClick = onSkipNext,
                enabled = playerState.hasNext
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = if (playerState.hasNext) TextPrimary else TextMuted,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Repeat Mode Button
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    imageVector = when (playerState.repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        RepeatMode.ALL -> Icons.Default.Repeat
                        RepeatMode.OFF -> Icons.Default.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (playerState.repeatMode != RepeatMode.OFF) NeonCyan else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Secondary Row (Playback Speed)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SpaceSurfaceSubtle)
                    .clickable {
                        val nextSpeed = when (playerState.playbackSpeed) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2.0f
                            2.0f -> 0.75f
                            else -> 1.0f
                        }
                        onSpeedChange(nextSpeed)
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${playerState.playbackSpeed}x",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            playerState = playerState,
            onTrackSelect = { selectedTrack ->
                onTrackSelect(selectedTrack)
                showQueueSheet = false
            },
            onRemoveFromQueue = onRemoveFromQueue,
            onDismiss = { showQueueSheet = false }
        )
    }
}
