package com.spaceaudio.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spaceaudio.app.data.local.entity.TrackEntity
import com.spaceaudio.app.di.AppContainer
import com.spaceaudio.app.ui.screens.folders.FoldersScreen
import com.spaceaudio.app.ui.screens.import_audio.ImportAudioScreen
import com.spaceaudio.app.ui.screens.import_audio.ImportAudioViewModel
import com.spaceaudio.app.ui.screens.library.LibraryFilter
import com.spaceaudio.app.ui.screens.library.LibraryScreen
import com.spaceaudio.app.ui.screens.library.LibraryViewModel
import com.spaceaudio.app.ui.screens.player.FullPlayerScreen
import com.spaceaudio.app.ui.screens.player.MiniPlayer
import com.spaceaudio.app.ui.theme.NebulaViolet
import com.spaceaudio.app.ui.theme.NeonCyan
import com.spaceaudio.app.ui.theme.SpaceBlack
import com.spaceaudio.app.ui.theme.SpaceCardBg
import com.spaceaudio.app.ui.theme.SpaceDarkBg
import com.spaceaudio.app.ui.theme.SpaceSurfaceSubtle
import com.spaceaudio.app.ui.theme.TextMuted
import com.spaceaudio.app.ui.theme.TextPrimary
import com.spaceaudio.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Library : Screen("library", "Biblioteca", Icons.Default.LibraryMusic)
    object Import : Screen("import", "Importar URL", Icons.Default.Download)
    object Folders : Screen("folders", "Carpetas", Icons.Default.Folder)
}

@Composable
fun MainAppScreen(
    appContainer: AppContainer,
    initialSharedUrl: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Library.route

    val playerController = appContainer.playerController
    val playerState by playerController.playerState.collectAsState()

    var isFullPlayerExpanded by remember { mutableStateOf(false) }
    var trackToMove by remember { mutableStateOf<TrackEntity?>(null) }

    val folders by appContainer.audioRepository.getAllFolders().collectAsState(initial = emptyList())

    val importViewModel = remember {
        ImportAudioViewModel(appContainer.audioRepository).also { vm ->
            if (!initialSharedUrl.isNullOrBlank()) {
                vm.onUrlChanged(initialSharedUrl)
            }
        }
    }

    val libraryViewModel = remember {
        LibraryViewModel(appContainer.audioRepository)
    }

    val items = listOf(
        Screen.Library,
        Screen.Import,
        Screen.Folders
    )

    Box(modifier = Modifier.fillMaxSize().background(SpaceDarkBg)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    // MiniPlayer positioned directly above navigation bar
                    if (playerState.currentTrack != null) {
                        MiniPlayer(
                            playerState = playerState,
                            onPlayPause = { playerController.togglePlayPause() },
                            onSkipNext = { playerController.skipToNext() },
                            onClick = { isFullPlayerExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    NavigationBar(
                        containerColor = SpaceCardBg,
                        tonalElevation = 8.dp
                    ) {
                        items.forEach { screen ->
                            val selected = currentRoute == screen.route
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.title
                                    )
                                },
                                label = { Text(screen.title) },
                                selected = selected,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(Screen.Library.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SpaceBlack,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonCyan,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = if (!initialSharedUrl.isNullOrBlank()) Screen.Import.route else Screen.Library.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Library.route) {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        playerState = playerState,
                        onTrackClick = { track, list ->
                            playerController.playTrack(track, list)
                        },
                        onPlayNext = { track ->
                            playerController.playNext(track)
                            Toast.makeText(context, "Se reproducirá a continuación: ${track.title}", Toast.LENGTH_SHORT).show()
                        },
                        onAddToQueue = { track ->
                            playerController.addToQueue(track)
                            Toast.makeText(context, "Añadida a la cola: ${track.title}", Toast.LENGTH_SHORT).show()
                        },
                        onMoveToFolder = { track ->
                            trackToMove = track
                        },
                        onNavigateToImport = {
                            navController.navigate(Screen.Import.route)
                        }
                    )
                }

                composable(Screen.Import.route) {
                    ImportAudioScreen(
                        viewModel = importViewModel,
                        onPlayTrack = { track ->
                            playerController.playTrack(track, listOf(track))
                            isFullPlayerExpanded = true
                        }
                    )
                }

                composable(Screen.Folders.route) {
                    FoldersScreen(
                        audioRepository = appContainer.audioRepository,
                        playerState = playerState,
                        onTrackClick = { track, list ->
                            playerController.playTrack(track, list)
                        },
                        onPlayNext = { track ->
                            playerController.playNext(track)
                            Toast.makeText(context, "Se reproducirá a continuación: ${track.title}", Toast.LENGTH_SHORT).show()
                        },
                        onAddToQueue = { track ->
                            playerController.addToQueue(track)
                            Toast.makeText(context, "Añadida a la cola: ${track.title}", Toast.LENGTH_SHORT).show()
                        },
                        onToggleFavorite = { track ->
                            libraryViewModel.toggleFavorite(track)
                        },
                        onDeleteTrack = { track ->
                            libraryViewModel.deleteTrack(track)
                        }
                    )
                }
            }
        }

        // Full Screen Player Modal
        AnimatedVisibility(
            visible = isFullPlayerExpanded,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            FullPlayerScreen(
                playerState = playerState,
                onPlayPause = { playerController.togglePlayPause() },
                onSeek = { pos -> playerController.seekTo(pos) },
                onSkipNext = { playerController.skipToNext() },
                onSkipPrevious = { playerController.skipToPrevious() },
                onToggleShuffle = { playerController.toggleShuffle() },
                onCycleRepeat = { playerController.cycleRepeatMode() },
                onSpeedChange = { speed -> playerController.setPlaybackSpeed(speed) },
                onTrackSelect = { track -> playerController.playTrack(track, playerState.queue) },
                onRemoveFromQueue = { index -> playerController.removeFromQueue(index) },
                onDismiss = { isFullPlayerExpanded = false }
            )
        }

        // Move to Folder Dialog
        trackToMove?.let { track ->
            val availableFolders = (listOf("Downloads") + folders.map { it.name }).distinct()
            AlertDialog(
                onDismissRequest = { trackToMove = null },
                containerColor = SpaceCardBg,
                title = {
                    Text("Mover a Carpeta", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text("Elige el destino para '${track.title}':", color = TextSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableFolders) { folderName ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (track.folderName == folderName) NeonCyan.copy(alpha = 0.2f) else SpaceSurfaceSubtle)
                                        .clickable {
                                            scope.launch {
                                                appContainer.audioRepository.moveTrackToFolder(track.id, folderName)
                                                Toast.makeText(context, "Movida a $folderName", Toast.LENGTH_SHORT).show()
                                                trackToMove = null
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (track.folderName == folderName) NeonCyan else NebulaViolet,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = folderName,
                                        color = if (track.folderName == folderName) NeonCyan else TextPrimary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { trackToMove = null }) {
                        Text("Cerrar", color = TextSecondary)
                    }
                }
            )
        }
    }
}
