package com.spaceaudio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.spaceaudio.app.data.local.entity.TrackEntity
import com.spaceaudio.app.ui.theme.LaserPink
import com.spaceaudio.app.ui.theme.NebulaViolet
import com.spaceaudio.app.ui.theme.NeonCyan
import com.spaceaudio.app.ui.theme.SpaceCardBg
import com.spaceaudio.app.ui.theme.SpaceCardBorder
import com.spaceaudio.app.ui.theme.SpaceSurfaceSubtle
import com.spaceaudio.app.ui.theme.TextMuted
import com.spaceaudio.app.ui.theme.TextPrimary
import com.spaceaudio.app.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun TrackItem(
    track: TrackEntity,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDelete: () -> Unit,
    onMoveToFolder: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentTrack) SpaceCardBorder.copy(alpha = 0.5f) else SpaceCardBg.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail or Music Icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpaceSurfaceSubtle),
            contentAlignment = Alignment.Center
        ) {
            if (!track.thumbnailUri.isNullOrBlank()) {
                AsyncImage(
                    model = track.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isCurrentTrack) NeonCyan else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (isCurrentTrack) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(SpaceCardBg.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    WaveformBar(isPlaying = isPlaying, color = NeonCyan)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title, Artist, Duration & NEW Badge
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.title,
                    color = if (isCurrentTrack) NeonCyan else TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // 5-minute NEW badge
                if (track.isNew) {
                    NewBadge()
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = track.artist,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (track.durationMs > 0) {
                    Text(
                        text = "•",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    Text(
                        text = formatDuration(track.durationMs),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)
                    )
                }
            }
        }

        // Favorite Toggle
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (track.isFavorite) LaserPink else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        // Context Menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SpaceCardBg)
            ) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = NeonCyan)
                    },
                    text = { Text("Reproducir ahora", color = TextPrimary) },
                    onClick = {
                        onClick()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = null, tint = NeonCyan)
                    },
                    text = { Text("Reproducir siguiente", color = TextPrimary) },
                    onClick = {
                        onPlayNext()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null, tint = NeonCyan)
                    },
                    text = { Text("Añadir a la cola", color = TextPrimary) },
                    onClick = {
                        onAddToQueue()
                        showMenu = false
                    }
                )
                if (onMoveToFolder != null) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = NebulaViolet)
                        },
                        text = { Text("Mover a carpeta...", color = TextPrimary) },
                        onClick = {
                            onMoveToFolder()
                            showMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, tint = LaserPink)
                    },
                    text = { Text("Eliminar canción", color = LaserPink) },
                    onClick = {
                        onDelete()
                        showMenu = false
                    }
                )
            }
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
