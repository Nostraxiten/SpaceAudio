package com.spaceaudio.app.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spaceaudio.app.data.local.entity.TrackEntity
import com.spaceaudio.app.player.PlayerState
import com.spaceaudio.app.ui.components.NeonButton
import com.spaceaudio.app.ui.components.TrackItem
import com.spaceaudio.app.ui.theme.ElectricEmerald
import com.spaceaudio.app.ui.theme.LaserPink
import com.spaceaudio.app.ui.theme.NebulaViolet
import com.spaceaudio.app.ui.theme.NeonCyan
import com.spaceaudio.app.ui.theme.SpaceCardBg
import com.spaceaudio.app.ui.theme.SpaceCardBorder
import com.spaceaudio.app.ui.theme.SpaceDarkBg
import com.spaceaudio.app.ui.theme.SpaceSurfaceSubtle
import com.spaceaudio.app.ui.theme.TextMuted
import com.spaceaudio.app.ui.theme.TextPrimary
import com.spaceaudio.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    playerState: PlayerState,
    onTrackClick: (TrackEntity, List<TrackEntity>) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onMoveToFolder: (TrackEntity) -> Unit,
    onNavigateToImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val tracks by viewModel.tracks.collectAsState()

    // 1-second ticker to re-evaluate the 5-minute [NEW] badge expiration dynamically
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceDarkBg)
            .padding(top = 20.dp, start = 16.dp, end = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Space Library",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "${tracks.size} tracks available offline",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholder = { Text("Search songs, artists, albums...", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = NeonCyan
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SpaceSurfaceSubtle,
                unfocusedContainerColor = SpaceSurfaceSubtle,
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = SpaceCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Chips (All, Downloads, Favorites)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            item {
                FilterChip(
                    selected = uiState.selectedFilter == LibraryFilter.ALL,
                    onClick = { viewModel.onFilterSelected(LibraryFilter.ALL) },
                    label = { Text("All Tracks") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                        selectedLabelColor = NeonCyan,
                        selectedLeadingIconColor = NeonCyan,
                        containerColor = SpaceCardBg,
                        labelColor = TextSecondary,
                        iconColor = TextMuted
                    )
                )
            }
            item {
                FilterChip(
                    selected = uiState.selectedFilter == LibraryFilter.DOWNLOADS,
                    onClick = { viewModel.onFilterSelected(LibraryFilter.DOWNLOADS) },
                    label = { Text("Downloads") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricEmerald.copy(alpha = 0.2f),
                        selectedLabelColor = ElectricEmerald,
                        selectedLeadingIconColor = ElectricEmerald,
                        containerColor = SpaceCardBg,
                        labelColor = TextSecondary,
                        iconColor = TextMuted
                    )
                )
            }
            item {
                FilterChip(
                    selected = uiState.selectedFilter == LibraryFilter.FAVORITES,
                    onClick = { viewModel.onFilterSelected(LibraryFilter.FAVORITES) },
                    label = { Text("Favorites") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LaserPink.copy(alpha = 0.2f),
                        selectedLabelColor = LaserPink,
                        selectedLeadingIconColor = LaserPink,
                        containerColor = SpaceCardBg,
                        labelColor = TextSecondary,
                        iconColor = TextMuted
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Track List / Empty State
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicOff,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (uiState.searchQuery.isNotEmpty()) "No matching tracks found" else "Your library is empty",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (uiState.searchQuery.isNotEmpty()) "Try searching for a different title or artist"
                        else "Paste a YouTube or Audio link to download your first song",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (uiState.searchQuery.isEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        NeonButton(
                            text = "Import Audio Now",
                            onClick = onNavigateToImport,
                            icon = Icons.Default.Download,
                            accentColor = NeonCyan,
                            modifier = Modifier.width(220.dp)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    val isCurrent = playerState.currentTrack?.id == track.id
                    TrackItem(
                        track = track,
                        isCurrentTrack = isCurrent,
                        isPlaying = isCurrent && playerState.isPlaying,
                        onClick = { onTrackClick(track, tracks) },
                        onToggleFavorite = { viewModel.toggleFavorite(track) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onMoveToFolder = { onMoveToFolder(track) },
                        onDelete = { viewModel.deleteTrack(track) }
                    )
                }
            }
        }
    }
}
