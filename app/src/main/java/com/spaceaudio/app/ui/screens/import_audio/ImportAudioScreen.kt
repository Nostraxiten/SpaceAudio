package com.spaceaudio.app.ui.screens.import_audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.spaceaudio.app.data.local.entity.TrackEntity
import com.spaceaudio.app.ui.components.GlassmorphicCard
import com.spaceaudio.app.ui.components.NeonButton
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

@Composable
fun ImportAudioScreen(
    viewModel: ImportAudioViewModel,
    onPlayTrack: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Import Audio",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Paste YouTube or Audio URL to save offline",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonCyan.copy(alpha = 0.15f))
                    .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "OFFLINE READY",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // URL Input Section
        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AUDIO SOURCE URL",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )

                    if (uiState.detectedProvider != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (uiState.detectedProvider == "YouTube") LaserPink.copy(alpha = 0.2f)
                                    else ElectricEmerald.copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = uiState.detectedProvider ?: "",
                                color = if (uiState.detectedProvider == "YouTube") LaserPink else ElectricEmerald,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.urlInput,
                    onValueChange = { viewModel.onUrlChanged(it) },
                    placeholder = {
                        Text(
                            "https://www.youtube.com/watch?v=...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = if (uiState.isValidUrl) NeonCyan else TextMuted
                        )
                    },
                    trailingIcon = {
                        Row {
                            if (uiState.urlInput.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onUrlChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = TextSecondary
                                    )
                                }
                            }
                            IconButton(onClick = {
                                clipboardManager.getText()?.text?.let { clipText ->
                                    viewModel.onUrlChanged(clipText)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = NeonCyan
                                )
                            }
                        }
                    },
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                        viewModel.analyzeUrl()
                    }),
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

                Spacer(modifier = Modifier.height(12.dp))

                NeonButton(
                    text = if (uiState.isAnalyzing) "Analyzing Source..." else "Analyze URL",
                    onClick = {
                        keyboardController?.hide()
                        viewModel.analyzeUrl()
                    },
                    icon = Icons.Default.Search,
                    isLoading = uiState.isAnalyzing,
                    enabled = uiState.urlInput.isNotBlank() && !uiState.isDownloading,
                    accentColor = NeonCyan
                )
            }
        }

        // Error message
        AnimatedVisibility(visible = uiState.errorMessage != null) {
            uiState.errorMessage?.let { errorMsg ->
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LaserPink.copy(alpha = 0.15f))
                        .border(1.dp, LaserPink.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = LaserPink,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMsg,
                            color = LaserPink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Preview Card
        AnimatedVisibility(
            visible = uiState.previewMetadata != null && !uiState.isDownloading && uiState.importedTrack == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            uiState.previewMetadata?.let { meta ->
                Column {
                    Spacer(modifier = Modifier.height(20.dp))
                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = NeonCyan.copy(alpha = 0.4f)
                    ) {
                        Column {
                            Text(
                                text = "METADATA PREVIEW",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                // Thumbnail
                                if (!meta.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = meta.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, SpaceCardBorder, RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = meta.author,
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = meta.title,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Editable Custom Title
                            Text(
                                text = "Custom Song Name (Optional):",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = uiState.customTitle,
                                onValueChange = { viewModel.onCustomTitleChanged(it) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
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

                            // Destination Info
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = NebulaViolet,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Destination: ",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Downloads Folder",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            NeonButton(
                                text = "Import & Save Locally",
                                onClick = { viewModel.importAudio() },
                                icon = Icons.Default.Download,
                                accentColor = ElectricEmerald
                            )
                        }
                    }
                }
            }
        }

        // Progress Section during download
        AnimatedVisibility(visible = uiState.isDownloading) {
            uiState.downloadProgress?.let { prog ->
                Column {
                    Spacer(modifier = Modifier.height(20.dp))
                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = NeonCyan
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = prog.message,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${(prog.progressPercent * 100).toInt()}%",
                                    color = NeonCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { prog.progressPercent },
                                color = NeonCyan,
                                trackColor = SpaceSurfaceSubtle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }

        // Success Card
        AnimatedVisibility(visible = uiState.importedTrack != null) {
            uiState.importedTrack?.let { track ->
                Column {
                    Spacer(modifier = Modifier.height(20.dp))
                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = ElectricEmerald
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ElectricEmerald,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Audio Saved Successfully!",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Added to Downloads with 5-minute [NEW] badge",
                                color = ElectricEmerald,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "${track.artist} - ${track.title}",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                NeonButton(
                                    text = "Play Now",
                                    onClick = { onPlayTrack(track) },
                                    icon = Icons.Default.PlayArrow,
                                    accentColor = NeonCyan,
                                    modifier = Modifier.weight(1f)
                                )
                                NeonButton(
                                    text = "Import Another",
                                    onClick = { viewModel.clearImport() },
                                    accentColor = NebulaViolet,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
