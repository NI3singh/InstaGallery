// InstaGallery v2.0
package com.example.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.data.local.OnboardingPreferences
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.local.entity.PersonEntity
import com.example.data.repository.GalleryRepository
import com.example.ml.FaceIndexingWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Random

enum class HomeFeedFilter {
    ALL,
    FAVORITES,
    BOOKMARKED
}

data class HomeUiState(
    val photos: List<MediaWithAuthors> = emptyList(),
    val people: List<PersonEntity> = emptyList(),
    val newUndecidedPeople: List<PersonEntity> = emptyList(),
    val unassociatedCount: Int = 0,
    val unindexedPhotoCount: Int = 0,
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
    private val galleryRepository: GalleryRepository
        get() = repository

    init {
        OnboardingPreferences.init(application)
    }

    private val _activeFilter = MutableStateFlow(HomeFeedFilter.ALL)
    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshSeed = MutableStateFlow(System.currentTimeMillis())

    private val rawPhotos = combine(
        repository.photosWithAuthors,
        repository.favoritePhotosWithAuthors,
        repository.bookmarkedPhotosWithAuthors,
        _activeFilter,
        OnboardingPreferences.hideOtherFromHomeFeedFlow
    ) { allPhotos, favPhotos, savedPhotos, filter, hideOther ->
        val base = when (filter) {
            HomeFeedFilter.ALL -> allPhotos
            HomeFeedFilter.FAVORITES -> favPhotos
            HomeFeedFilter.BOOKMARKED -> savedPhotos
        }
        if (hideOther && filter == HomeFeedFilter.ALL) {
            base.filter { it.authors.isNotEmpty() }
        } else {
            base
        }
    }

    private data class ScanAndUndecidedInfo(
        val isRunning: Boolean,
        val current: Int,
        val total: Int,
        val unindexedCount: Int,
        val newUndecidedPeople: List<PersonEntity>
    )

    private val scanState = combine(
        repository.indexingWorkInfo,
        repository.unindexedPhotoCount,
        repository.totalMediaCount,
        repository.newUndecidedPeople
    ) { workInfo, unindexed, totalCount, newUndecided ->
        val isRunning = workInfo?.state == WorkInfo.State.RUNNING ||
            (workInfo?.state == WorkInfo.State.ENQUEUED && unindexed > 0)
        val progress = workInfo?.progress
        val processedInRun = progress?.getInt(FaceIndexingWorker.PROGRESS_PROCESSED, 0) ?: 0
        val totalInRun = progress?.getInt(FaceIndexingWorker.PROGRESS_TOTAL, 0) ?: 0

        val current = if (totalInRun > 0) processedInRun else (totalCount - unindexed).coerceAtLeast(0)
        val total = if (totalInRun > 0) totalInRun else totalCount
        ScanAndUndecidedInfo(
            isRunning = isRunning && unindexed > 0,
            current = current,
            total = total,
            unindexedCount = unindexed,
            newUndecidedPeople = newUndecided
        )
    }

    private data class RefreshAndStoriesMeta(
        val seed: Long,
        val isRefreshing: Boolean,
        val myProfilePersonId: Long,
        val viewedStoryIds: Set<Long>
    )

    private val refreshAndStoriesMeta = combine(
        _refreshSeed,
        _isRefreshing,
        OnboardingPreferences.myProfilePersonIdFlow,
        OnboardingPreferences.viewedStoryPersonIdsFlow
    ) { seed, refreshing, myProfileId, viewedIds ->
        RefreshAndStoriesMeta(
            seed = seed,
            isRefreshing = refreshing,
            myProfilePersonId = myProfileId,
            viewedStoryIds = viewedIds
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        rawPhotos,
        repository.allPeople,
        repository.unassociatedCount,
        refreshAndStoriesMeta,
        scanState
    ) { photos, people, unassociatedCount, meta, scanInfo ->
        val filter = _activeFilter.value
        val seed = meta.seed

        val arrangedPhotos = if (filter == HomeFeedFilter.FAVORITES) {
            photos.sortedByDescending { it.media.dateAddedSeconds }
        } else {
            val nowSeconds = System.currentTimeMillis() / 1000L
            val justTaken = ArrayList<MediaWithAuthors>()
            val catalogPosts = ArrayList<MediaWithAuthors>(photos.size)
            for (post in photos) {
                if (nowSeconds - post.media.dateAddedSeconds <= 1800L) {
                    justTaken.add(post)
                } else {
                    catalogPosts.add(post)
                }
            }
            justTaken.sortByDescending { it.media.dateAddedSeconds }
            // Deterministic sort pre-pass before seeded Random shuffle so Room emissions don't alter order
            catalogPosts.sortByDescending { it.media.mediaId }
            val shuffledCatalog = catalogPosts.shuffled(Random(seed))
            justTaken + shuffledCatalog
        }

        // Lightly shuffle unviewed stories while pinning My Profile first and viewed stories after
        val myPerson = if (meta.myProfilePersonId > 0L) {
            people.firstOrNull { it.personId == meta.myProfilePersonId }
        } else {
            null
        }
        val remainingPeople = if (myPerson != null) {
            people.filter { it.personId != myPerson.personId }
        } else {
            people
        }
        val (viewedPeople, unviewedPeople) = remainingPeople.partition {
            it.personId in meta.viewedStoryIds
        }
        val shuffledUnviewed = unviewedPeople
            .sortedBy { it.personId }
            .shuffled(Random(seed xor 0x5DEECE66DL))
        val arrangedPeople = buildList(people.size) {
            if (myPerson != null) add(myPerson)
            addAll(shuffledUnviewed)
            addAll(viewedPeople)
        }

        HomeUiState(
            photos = arrangedPhotos,
            people = arrangedPeople,
            newUndecidedPeople = scanInfo.newUndecidedPeople,
            unassociatedCount = unassociatedCount,
            unindexedPhotoCount = scanInfo.unindexedCount,
            activeFilter = filter,
            isFilteringFavorites = filter == HomeFeedFilter.FAVORITES,
            isFilteringBookmarks = filter == HomeFeedFilter.BOOKMARKED,
            isScanning = scanInfo.isRunning && scanInfo.total > 0,
            scannedCount = scanInfo.current,
            totalToScan = scanInfo.total,
            isRefreshing = meta.isRefreshing
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
            repository.scanDeviceMedia()
        }
    }

    fun refreshFeed() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching { galleryRepository.performQuickRefreshSync() }
            _refreshSeed.value = System.nanoTime()
            delay(280)
            _isRefreshing.value = false
        }
    }

    fun reshuffle() {
        refreshFeed()
    }

    fun setFilter(filter: HomeFeedFilter) {
        _activeFilter.value = filter
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

    fun updateCaption(mediaId: Long, caption: String?) {
        viewModelScope.launch {
            repository.updateMediaCaption(mediaId, caption)
        }
    }
}
