package com.example.ui.screens.reels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.MediaWithAuthors
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
    val isMuted: Boolean = false
)

class ReelsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)
    private var sessionSeed = System.currentTimeMillis()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    private val _seedTrigger = MutableStateFlow(sessionSeed)

    val uiState: StateFlow<ReelsUiState> = combine(
        repository.videosWithAuthors,
        _isMuted,
        _seedTrigger
    ) { videos, isMuted, seed ->
        val shuffled = videos.shuffled(Random(seed))
        ReelsUiState(
            videos = shuffled,
            isMuted = isMuted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReelsUiState()
    )

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun toggleFavorite(mediaId: Long, current: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(mediaId, current)
        }
    }

    fun reshuffle() {
        sessionSeed = System.currentTimeMillis()
        _seedTrigger.value = sessionSeed
    }
}
