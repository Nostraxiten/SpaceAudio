package com.spaceaudio.app.ui.screens.import_audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spaceaudio.app.core.source.AudioMetadata
import com.spaceaudio.app.core.source.DownloadProgress
import com.spaceaudio.app.core.source.DownloadStage
import com.spaceaudio.app.data.local.entity.TrackEntity
import com.spaceaudio.app.data.repository.AudioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportUiState(
    val urlInput: String = "",
    val isValidUrl: Boolean = false,
    val detectedProvider: String? = null,
    val isAnalyzing: Boolean = false,
    val previewMetadata: AudioMetadata? = null,
    val customTitle: String = "",
    val selectedFolder: String = "Downloads",
    val downloadProgress: DownloadProgress? = null,
    val isDownloading: Boolean = false,
    val importedTrack: TrackEntity? = null,
    val errorMessage: String? = null
)

class ImportAudioViewModel(
    private val audioRepository: AudioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun onUrlChanged(newUrl: String) {
        val trimmed = newUrl.trim()
        val isYouTube = trimmed.contains("youtube.com") || trimmed.contains("youtu.be")
        val isDirect = trimmed.endsWith(".mp3") || trimmed.endsWith(".m4a") || trimmed.endsWith(".aac")
        val providerName = when {
            isYouTube -> "YouTube"
            isDirect -> "Direct Audio"
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> "Web Audio Source"
            else -> null
        }

        _uiState.update {
            it.copy(
                urlInput = newUrl,
                isValidUrl = providerName != null,
                detectedProvider = providerName,
                errorMessage = null,
                previewMetadata = null,
                importedTrack = null
            )
        }
    }

    fun analyzeUrl() {
        val url = _uiState.value.urlInput.trim()
        if (url.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, errorMessage = null) }
            val result = audioRepository.analyzeUrl(url)
            result.onSuccess { metadata ->
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        previewMetadata = metadata,
                        customTitle = metadata.title,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = error.message ?: "Unsupported source"
                    )
                }
            }
        }
    }

    fun onCustomTitleChanged(newTitle: String) {
        _uiState.update { it.copy(customTitle = newTitle) }
    }

    fun onFolderChanged(folder: String) {
        _uiState.update { it.copy(selectedFolder = folder) }
    }

    fun importAudio(onSuccess: (TrackEntity) -> Unit = {}) {
        val state = _uiState.value
        val url = state.urlInput.trim()
        if (url.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = DownloadProgress(stage = DownloadStage.VALIDATING, message = "Starting import..."),
                    errorMessage = null
                )
            }

            val result = audioRepository.importAudio(
                url = url,
                customTitle = state.customTitle.takeIf { it.isNotBlank() },
                targetFolder = state.selectedFolder,
                onProgress = { progress ->
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
            )

            result.onSuccess { track ->
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        importedTrack = track,
                        errorMessage = null
                    )
                }
                onSuccess(track)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        errorMessage = error.message ?: "Failed to import audio"
                    )
                }
            }
        }
    }

    fun clearImport() {
        _uiState.update { ImportUiState() }
    }
}
