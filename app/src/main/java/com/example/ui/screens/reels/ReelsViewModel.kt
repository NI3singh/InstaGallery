package com.example.ui.screens.reels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.local.entity.PersonEntity
import com.example.data.repository.GalleryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ReelsUiState(
    val videos: List<MediaWithAuthors> = emptyList(),
    val allPeople: List<PersonEntity> = emptyList(),
    val isMuted: Boolean = false,
    val onlyFavorites: Boolean = false
)

class ReelsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)
    private var sessionSeed = System.currentTimeMillis()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    private val _onlyFavorites = MutableStateFlow(false)
    private val _seedTrigger = MutableStateFlow(sessionSeed)

    val uiState: StateFlow<ReelsUiState> = combine(
        repository.videosWithAuthors,
        repository.allPeople,
        _isMuted,
        _onlyFavorites,
        _seedTrigger
    ) { videos, people, isMuted, onlyFavs, seed ->
        val filtered = if (onlyFavs) videos.filter { it.media.isFavorite } else videos
        val shuffled = filtered.shuffled(Random(seed))
        ReelsUiState(
            videos = shuffled,
            allPeople = people,
            isMuted = isMuted,
            onlyFavorites = onlyFavs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReelsUiState()
    )

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun toggleFavoritesFilter() {
        _onlyFavorites.value = !_onlyFavorites.value
    }

    fun toggleFavorite(mediaId: Long, current: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(mediaId, current)
        }
    }

    fun toggleBookmark(mediaId: Long, current: Boolean) {
        viewModelScope.launch {
            repository.toggleBookmarked(mediaId, current)
        }
    }

    fun tagPersonInMedia(mediaId: Long, personId: Long) {
        viewModelScope.launch {
            repository.tagPersonInMedia(mediaId, personId)
        }
    }

    fun removePersonFromMedia(mediaId: Long, personId: Long) {
        viewModelScope.launch {
            repository.removeMediaFromPerson(mediaId, personId)
        }
    }

    fun reshuffle() {
        sessionSeed = System.currentTimeMillis()
        _seedTrigger.value = sessionSeed
    }
}
