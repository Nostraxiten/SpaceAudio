package com.spaceaudio.app.ui.screens.folders

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spaceaudio.app.data.local.entity.FolderEntity
import com.spaceaudio.app.data.local.entity.TrackEntity
import com.spaceaudio.app.data.repository.AudioRepository
import com.spaceaudio.app.player.PlayerState
import com.spaceaudio.app.ui.components.NeonButton
import com.spaceaudio.app.ui.components.TrackItem
import com.spaceaudio.app.ui.theme.ElectricEmerald
import com.spaceaudio.app.ui.theme.LaserPink
import com.spaceaudio.app.ui.theme.NebulaViolet
import com.spaceaudio.app.ui.theme.NeonCyan
import com.spaceaudio.app.ui.theme.SpaceBlack
import com.spaceaudio.app.ui.theme.SpaceCardBg
import com.spaceaudio.app.ui.theme.SpaceCardBorder
import com.spaceaudio.app.ui.theme.SpaceDarkBg
import com.spaceaudio.app.ui.theme.SpaceSurfaceSubtle
import com.spaceaudio.app.ui.theme.TextMuted
import com.spaceaudio.app.ui.theme.TextPrimary
import com.spaceaudio.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun FoldersScreen(
    audioRepository: AudioRepository,
    playerState: PlayerState,
    onTrackClick: (TrackEntity, List<TrackEntity>) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val folders by audioRepository.getAllFolders().collectAsState(initial = emptyList())
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val displayFolders = if (folders.isNotEmpty()) {
        val hasDownloads = folders.any { it.name.equals("Downloads", ignoreCase = true) }
        if (hasDownloads) folders else listOf(
            FolderEntity(name = "Downloads", path = "Default Download Storage")
        ) + folders
    } else {
        listOf(
            FolderEntity(name = "Downloads", path = "Default Download Storage")
        )
    }

    if (selectedFolder != null) {
        // Folder Detail View
        val currentFolder = selectedFolder!!
        val folderTracks by audioRepository.getTracksByFolder(currentFolder).collectAsState(initial = emptyList())

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(SpaceDarkBg)
                .padding(top = 20.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { selectedFolder = null }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = currentFolder,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "${folderTracks.size} canciones en esta carpeta",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (folderTracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.MusicOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No hay canciones en esta carpeta", color = TextSecondary, fontSize = 15.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(folderTracks, key = { it.id }) { track ->
                        val isCurrent = playerState.currentTrack?.id == track.id
                        TrackItem(
                            track = track,
                            isCurrentTrack = isCurrent,
                            isPlaying = isCurrent && playerState.isPlaying,
                            onClick = { onTrackClick(track, folderTracks) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onDelete = { onDeleteTrack(track) }
                        )
                    }
                }
            }
        }
    } else {
        // Main Folders View
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(SpaceDarkBg)
                .padding(top = 20.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Carpetas",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Organiza tu música local en carpetas",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                }

                ElevatedButton(
                    onClick = {
                        newFolderName = ""
                        showCreateDialog = true
                    },
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = NeonCyan,
                        contentColor = SpaceBlack
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(displayFolders) { folder ->
                    FolderCard(
                        folder = folder,
                        onClick = { selectedFolder = folder.name }
                    )
                }
            }
        }
    }

    // Create Folder Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = SpaceCardBg,
            title = {
                Text("Crear Nueva Carpeta", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Introduce el nombre para la carpeta de música:", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("Ej: Rock, Gym, Favoritos 2026...", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SpaceSurfaceSubtle,
                            unfocusedContainerColor = SpaceSurfaceSubtle,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SpaceCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            scope.launch {
                                audioRepository.createFolder(newFolderName.trim())
                                showCreateDialog = false
                            }
                        }
                    }
                ) {
                    Text("Crear", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun FolderCard(
    folder: FolderEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDownloads = folder.name.equals("Downloads", ignoreCase = true)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SpaceCardBg)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDownloads) ElectricEmerald.copy(alpha = 0.2f) else NebulaViolet.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDownloads) Icons.Default.Download else Icons.Default.Folder,
                contentDescription = null,
                tint = if (isDownloads) ElectricEmerald else NebulaViolet,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isDownloads) "Carpeta principal de descargas" else folder.path,
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextMuted
        )
    }
}
