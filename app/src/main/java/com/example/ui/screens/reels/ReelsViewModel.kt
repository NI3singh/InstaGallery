// InstaGallery v1.0
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

data class ReelsUiState(
    val videos: List<MediaWithAuthors> = emptyList(),
    val isMuted: Boolean = false,
    val onlyFavorites: Boolean = false,
    val isFitMode: Boolean = false
)

class ReelsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)
    private var sessionSeed = System.currentTimeMillis()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    private val _onlyFavorites = MutableStateFlow(false)
    private val _isFitMode = MutableStateFlow(false)
    private val _seedTrigger = MutableStateFlow(sessionSeed)
    private val _brokenMediaIds = MutableStateFlow<Set<Long>>(emptySet())

    private data class FilterConfig(
        val isMuted: Boolean,
        val onlyFavs: Boolean,
        val isFitMode: Boolean,
        val seed: Long,
        val brokenIds: Set<Long>
    )

    private val filterConfigFlow = combine(
        _isMuted,
        _onlyFavorites,
        _isFitMode,
        _seedTrigger,
        _brokenMediaIds
    ) { isMuted, onlyFavs, isFitMode, seed, broken ->
        FilterConfig(isMuted, onlyFavs, isFitMode, seed, broken)
    }

    val uiState: StateFlow<ReelsUiState> = combine(
        repository.videosWithAuthors,
        filterConfigFlow
    ) { videos, config ->
        val validVideos = videos.filter { item ->
            !config.brokenIds.contains(item.media.mediaId)
        }

        val favFiltered = if (config.onlyFavs) {
            validVideos.filter { it.media.isFavorite }
        } else {
            validVideos
        }

        // Deterministic per-mediaId shuffle so liking/bookmarking/muting NEVER reorders the active pager
        val stableShuffled = favFiltered.sortedBy { item ->
            mixHash(item.media.mediaId, config.seed)
        }

        ReelsUiState(
            videos = stableShuffled,
            isMuted = config.isMuted,
            onlyFavorites = config.onlyFavs,
            isFitMode = config.isFitMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReelsUiState()
    )

    private fun mixHash(id: Long, seed: Long): Long {
        var z = (id xor seed) + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun toggleFitMode() {
        _isFitMode.value = !_isFitMode.value
    }

    fun toggleFavoritesFilter() {
        _onlyFavorites.value = !_onlyFavorites.value
    }

    fun markVideoUnplayable(mediaId: Long) {
        _brokenMediaIds.value = _brokenMediaIds.value + mediaId
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

    fun reshuffle() {
        sessionSeed = System.currentTimeMillis()
        _seedTrigger.value = sessionSeed
    }
}
