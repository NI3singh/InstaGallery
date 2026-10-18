package com.example.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.local.entity.PersonEntity
import com.example.data.repository.GalleryRepository
import com.example.ml.FaceIndexingWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

data class HomeUiState(
    val photos: List<MediaWithAuthors> = emptyList(),
    val people: List<PersonEntity> = emptyList(),
    val unassociatedCount: Int = 0,
    val isFilteringFavorites: Boolean = false,
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalToScan: Int = 0,
    val isRefreshing: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)
    private var sessionSeed = System.currentTimeMillis()

    private val _isFilteringFavorites = MutableStateFlow(false)
    val isFilteringFavorites: StateFlow<Boolean> = _isFilteringFavorites

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _seedTrigger = MutableStateFlow(sessionSeed)

    private val rawPhotos = combine(
        repository.photosWithAuthors,
        repository.favoritePhotosWithAuthors,
        _isFilteringFavorites
    ) { photos, favs, isFiltering ->
        if (isFiltering) favs else photos
    }

    private val scanState = combine(
        repository.indexingWorkInfo,
        repository.totalMediaCount
    ) { workInfo, totalCount ->
        val isRunning = workInfo?.state == WorkInfo.State.RUNNING
        val progress = workInfo?.progress
        val current = progress?.getInt(FaceIndexingWorker.PROGRESS_PROCESSED, 0) ?: 0
        val total = progress?.getInt(FaceIndexingWorker.PROGRESS_TOTAL, totalCount) ?: totalCount
        Triple(isRunning, current, total)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        rawPhotos,
        repository.allPeople,
        repository.unassociatedCount,
        _seedTrigger,
        scanState
    ) { photos, people, unassociatedCount, seed, scanInfo ->
        val (isRunning, current, total) = scanInfo
        HomeUiState(
            photos = photos.shuffled(Random(seed)),
            people = people,
            unassociatedCount = unassociatedCount,
            isFilteringFavorites = _isFilteringFavorites.value,
            isScanning = isRunning && total > 0,
            scannedCount = current,
            totalToScan = total,
            isRefreshing = _isRefreshing.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        scanMedia()
    }

    fun scanMedia() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.scanDeviceMedia()
            _isRefreshing.value = false
        }
    }

    fun reshuffle() {
        sessionSeed = System.currentTimeMillis()
        _seedTrigger.value = sessionSeed
        scanMedia()
    }

    fun toggleFavoritesFilter() {
        _isFilteringFavorites.value = !_isFilteringFavorites.value
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
}
