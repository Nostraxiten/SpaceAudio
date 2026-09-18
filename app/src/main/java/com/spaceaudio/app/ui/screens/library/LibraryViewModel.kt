package com.spaceaudio.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spaceaudio.app.data.local.entity.TrackEntity
import com.spaceaudio.app.data.repository.AudioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryFilter {
    ALL,
    DOWNLOADS,
    FAVORITES
}

data class LibraryUiState(
    val searchQuery: String = "",
    val selectedFilter: LibraryFilter = LibraryFilter.ALL,
    val folderList: List<String> = emptyList()
)

class LibraryViewModel(
    private val audioRepository: AudioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tracks: StateFlow<List<TrackEntity>> = _uiState.flatMapLatest { state ->
        when {
            state.searchQuery.isNotBlank() -> audioRepository.searchTracks(state.searchQuery)
            state.selectedFilter == LibraryFilter.DOWNLOADS -> audioRepository.getTracksByFolder("Downloads")
            state.selectedFilter == LibraryFilter.FAVORITES -> audioRepository.getFavoriteTracks()
            else -> audioRepository.getAllTracks()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onFilterSelected(filter: LibraryFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun toggleFavorite(track: TrackEntity) {
        viewModelScope.launch {
            audioRepository.setFavorite(track.id, !track.isFavorite)
        }
    }

    fun deleteTrack(track: TrackEntity) {
        viewModelScope.launch {
            audioRepository.deleteTrack(track, deleteLocalFile = true)
        }
    }
}
