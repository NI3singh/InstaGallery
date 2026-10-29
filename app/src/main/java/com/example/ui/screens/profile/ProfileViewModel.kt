// InstaGallery v1.0
package com.example.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.CoOccurringPerson
import com.example.data.local.entity.FaceEntity
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.local.entity.PersonEntity
import com.example.data.repository.GalleryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProfilePhotoFilter {
    data object All : ProfilePhotoFilter
    data object SoloOnly : ProfilePhotoFilter
    data class TogetherWith(val otherPerson: PersonEntity) : ProfilePhotoFilter
}

data class ProfileUiState(
    val personId: Long = -1L,
    val person: PersonEntity? = null,
    val photos: List<MediaWithAuthors> = emptyList(),
    val photoCount: Int = 0,
    val totalPhotoCount: Int = 0,
    val soloPhotoCount: Int = 0,
    val selectedFilter: ProfilePhotoFilter = ProfilePhotoFilter.All,
    val coOccurringPeople: List<CoOccurringPerson> = emptyList(),
    val faces: List<FaceEntity> = emptyList(),
    val allPeople: List<PersonEntity> = emptyList(),
    val otherPeople: List<PersonEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    application: Application,
    private val personId: Long
) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)

    private val _selectedFilter = MutableStateFlow<ProfilePhotoFilter>(ProfilePhotoFilter.All)
    val selectedFilter: StateFlow<ProfilePhotoFilter> = _selectedFilter.asStateFlow()

    private val personFlow = if (personId == -1L) {
        MutableStateFlow<PersonEntity?>(null)
    } else {
        repository.getPersonById(personId)
    }

    private val allPersonPhotosFlow = if (personId == -1L) {
        repository.getUnassignedPhotosFlow()
    } else {
        repository.getMediaForPersonFlow(personId)
    }

    private val soloPhotosFlow = if (personId == -1L) {
        flowOf(emptyList())
    } else {
        repository.getSoloMediaForPersonFlow(personId)
    }

    private val coOccurringPeopleFlow = if (personId == -1L) {
        flowOf(emptyList())
    } else {
        repository.getCoOccurringPeopleFlow(personId)
    }

    private val filteredPhotosFlow = _selectedFilter.flatMapLatest { filter ->
        if (personId == -1L) {
            allPersonPhotosFlow
        } else {
            when (filter) {
                is ProfilePhotoFilter.All -> allPersonPhotosFlow
                is ProfilePhotoFilter.SoloOnly -> soloPhotosFlow
                is ProfilePhotoFilter.TogetherWith -> repository.getSharedMediaBetweenPeopleFlow(
                    personId = personId,
                    otherPersonId = filter.otherPerson.personId
                )
            }
        }
    }

    private data class FilterMeta(
        val filter: ProfilePhotoFilter,
        val allPhotos: List<MediaWithAuthors>,
        val soloPhotos: List<MediaWithAuthors>,
        val coOccurring: List<CoOccurringPerson>
    )

    private val filterMetaFlow = combine(
        _selectedFilter,
        allPersonPhotosFlow,
        soloPhotosFlow,
        coOccurringPeopleFlow
    ) { filter, allPhotos, soloPhotos, coOccurring ->
        FilterMeta(
            filter = filter,
            allPhotos = allPhotos.filter { !it.media.isVideo },
            soloPhotos = soloPhotos.filter { !it.media.isVideo },
            coOccurring = coOccurring
        )
    }

    val uiState: StateFlow<ProfileUiState> = combine(
        personFlow,
        filteredPhotosFlow,
        filterMetaFlow,
        repository.getFacesForPerson(personId),
        repository.allPeople
    ) { person, filteredPhotos, meta, faces, allPeople ->
        val stillPhotos = filteredPhotos.filter { !it.media.isVideo }
        val candidateOthers = allPeople.filter { it.personId != personId }

        ProfileUiState(
            personId = personId,
            person = person,
            photos = stillPhotos,
            photoCount = meta.allPhotos.size,
            totalPhotoCount = meta.allPhotos.size,
            soloPhotoCount = meta.soloPhotos.size,
            selectedFilter = meta.filter,
            coOccurringPeople = meta.coOccurring,
            faces = faces,
            allPeople = allPeople,
            otherPeople = candidateOthers
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState(personId = personId)
    )

    fun setFilter(filter: ProfilePhotoFilter) {
        _selectedFilter.value = filter
    }

    fun toggleFilter(target: ProfilePhotoFilter) {
        val current = _selectedFilter.value
        val isSame = when {
            current is ProfilePhotoFilter.All && target is ProfilePhotoFilter.All -> true
            current is ProfilePhotoFilter.SoloOnly && target is ProfilePhotoFilter.SoloOnly -> true
            current is ProfilePhotoFilter.TogetherWith && target is ProfilePhotoFilter.TogetherWith ->
                current.otherPerson.personId == target.otherPerson.personId
            else -> false
        }
        _selectedFilter.value = if (isSame) ProfilePhotoFilter.All else target
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

    fun moveProfileToOther(onComplete: () -> Unit = {}) {
        if (personId == -1L) return
        viewModelScope.launch {
            repository.movePersonToOther(personId)
            onComplete()
        }
    }

    fun updateCaption(mediaId: Long, caption: String?) {
        viewModelScope.launch {
            repository.updateMediaCaption(mediaId, caption)
        }
    }
}
