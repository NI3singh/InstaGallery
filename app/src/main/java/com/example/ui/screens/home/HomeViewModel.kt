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

enum class HomeFeedFilter {
    ALL,
    FAVORITES,
    BOOKMARKED
}

data class HomeUiState(
    val photos: List<MediaWithAuthors> = emptyList(),
    val people: List<PersonEntity> = emptyList(),
    val unassociatedCount: Int = 0,
    val activeFilter: HomeFeedFilter = HomeFeedFilter.ALL,
    val isFilteringFavorites: Boolean = false,
    val isFilteringBookmarks: Boolean = false,
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalToScan: Int = 0,
    val isRefreshing: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)
    private var sessionSeed = System.currentTimeMillis()

    private val _activeFilter = MutableStateFlow(HomeFeedFilter.ALL)
    private val _isRefreshing = MutableStateFlow(false)
    private val _seedTrigger = MutableStateFlow(sessionSeed)

    private val rawPhotos = combine(
        repository.photosWithAuthors,
        repository.favoritePhotosWithAuthors,
        repository.bookmarkedPhotosWithAuthors,
        _activeFilter
    ) { allPhotos, favPhotos, savedPhotos, filter ->
        when (filter) {
            HomeFeedFilter.ALL -> allPhotos
            HomeFeedFilter.FAVORITES -> favPhotos
            HomeFeedFilter.BOOKMARKED -> savedPhotos
        }
    }

    private val scanState = combine(
        repository.indexingWorkInfo,
        repository.unindexedCount,
        repository.totalMediaCount
    ) { workInfo, unindexed, totalCount ->
        val isRunning = workInfo?.state == WorkInfo.State.RUNNING ||
            (workInfo?.state == WorkInfo.State.ENQUEUED && unindexed > 0)
        val progress = workInfo?.progress
        val processedInRun = progress?.getInt(FaceIndexingWorker.PROGRESS_PROCESSED, 0) ?: 0
        val totalInRun = progress?.getInt(FaceIndexingWorker.PROGRESS_TOTAL, 0) ?: 0

        val current = if (totalInRun > 0) processedInRun else (totalCount - unindexed).coerceAtLeast(0)
        val total = if (totalInRun > 0) totalInRun else totalCount
        Triple(isRunning && unindexed > 0, current, total)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        rawPhotos,
        repository.allPeople,
        repository.unassociatedCount,
        _seedTrigger,
        scanState
    ) { photos, people, unassociatedCount, seed, scanInfo ->
        val (isRunning, current, total) = scanInfo
        val filter = _activeFilter.value
        HomeUiState(
            photos = photos.shuffled(Random(seed)),
            people = people,
            unassociatedCount = unassociatedCount,
            activeFilter = filter,
            isFilteringFavorites = filter == HomeFeedFilter.FAVORITES,
            isFilteringBookmarks = filter == HomeFeedFilter.BOOKMARKED,
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
        _activeFilter.value = if (_activeFilter.value == HomeFeedFilter.FAVORITES) {
            HomeFeedFilter.ALL
        } else {
            HomeFeedFilter.FAVORITES
        }
    }

    fun toggleBookmarksFilter() {
        _activeFilter.value = if (_activeFilter.value == HomeFeedFilter.BOOKMARKED) {
            HomeFeedFilter.ALL
        } else {
            HomeFeedFilter.BOOKMARKED
        }
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
}
