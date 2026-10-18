package com.example.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.FaceEntity
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.local.entity.PersonEntity
import com.example.data.repository.GalleryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val personId: Long = -1L,
    val person: PersonEntity? = null,
    val photos: List<MediaWithAuthors> = emptyList(),
    val videos: List<MediaWithAuthors> = emptyList(),
    val photoCount: Int = 0,
    val reelCount: Int = 0,
    val totalCount: Int = 0,
    val faces: List<FaceEntity> = emptyList(),
    val allPeople: List<PersonEntity> = emptyList(),
    val otherPeople: List<PersonEntity> = emptyList(),
    val selectedTab: Int = 0 // 0 = Photos grid, 1 = Reels grid
)

data class MemoryCounts(val photos: Int, val reels: Int, val total: Int)

class ProfileViewModel(
    application: Application,
    private val personId: Long
) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)
    private val _selectedTab = MutableStateFlow(0)

    private val countsFlow = combine(
        repository.getPhotoCountForPerson(personId),
        repository.getReelCountForPerson(personId),
        repository.getTotalCountForPerson(personId)
    ) { p, r, t -> MemoryCounts(p, r, t) }

    private val mediaFlow = combine(
        repository.getPhotosForPerson(personId),
        repository.getVideosForPerson(personId),
        _selectedTab
    ) { photos, videos, tab -> Triple(photos, videos, tab) }

    private val personFlow = if (personId == -1L) MutableStateFlow<PersonEntity?>(null) else repository.getPersonById(personId)

    val uiState: StateFlow<ProfileUiState> = combine(
        personFlow,
        mediaFlow,
        countsFlow,
        repository.getFacesForPerson(personId),
        repository.allPeople
    ) { person, mediaTuple, counts, faces, allPeople ->
        val photos = mediaTuple.first
        val videos = mediaTuple.second
        val tab = mediaTuple.third
        val candidateOthers = allPeople.filter { it.personId != personId }

        ProfileUiState(
            personId = personId,
            person = person,
            photos = photos,
            videos = videos,
            photoCount = counts.photos,
            reelCount = counts.reels,
            totalCount = counts.total,
            faces = faces,
            allPeople = allPeople,
            otherPeople = candidateOthers,
            selectedTab = tab
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState(personId = personId)
    )

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun updateProfileName(newName: String) {
        if (personId == -1L || newName.isBlank()) return
        viewModelScope.launch {
            repository.updatePersonName(personId, newName.trim())
        }
    }

    fun changeProfileAvatar(cropPath: String) {
        if (personId == -1L || cropPath.isBlank()) return
        viewModelScope.launch {
            repository.updateAvatar(personId, cropPath)
        }
    }

    fun removePhotoFromPerson(mediaId: Long, targetPersonId: Long = personId) {
        if (targetPersonId == -1L) return
        viewModelScope.launch {
            repository.removeMediaFromPerson(mediaId, targetPersonId)
        }
    }

    fun tagPersonInMedia(mediaId: Long, targetPersonId: Long) {
        viewModelScope.launch {
            repository.tagPersonInMedia(mediaId, targetPersonId)
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

    fun mergeWithPerson(targetPersonId: Long, onComplete: (Long) -> Unit = {}) {
        if (personId == -1L || targetPersonId == personId) return
        val currentPerson = uiState.value.person ?: return
        viewModelScope.launch {
            repository.mergePeople(listOf(personId), targetPersonId, currentPerson.name)
            onComplete(targetPersonId)
        }
    }
}
