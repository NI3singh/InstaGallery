// InstaGallery v2.0
package com.example.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.OnboardingPreferences
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProfilePhotoFilter {
    data object All : ProfilePhotoFilter
    data object SoloOnly : ProfilePhotoFilter
    data class TogetherWith(val otherPerson: PersonEntity) : ProfilePhotoFilter
}

data class ProfileUiState(
    val personId: Long = -1L,
    val activePerson: PersonEntity? = null,
    val isMyProfile: Boolean = false,
    val needsMyProfileSelection: Boolean = false,
    val allVisiblePeople: List<PersonEntity> = emptyList(),
    val otherPeople: List<PersonEntity> = emptyList(),
    val photos: List<MediaWithAuthors> = emptyList(),
    val totalPostsCount: Int = 0,
    val soloPostsCount: Int = 0,
    val togetherPostsCount: Int = 0,
    val customBio: String = OnboardingPreferences.DEFAULT_PROFILE_BIO,
    val selectedFilter: ProfilePhotoFilter = ProfilePhotoFilter.All,
    val coOccurringPeople: List<CoOccurringPerson> = emptyList(),
    val faces: List<FaceEntity> = emptyList()
) {
    val person: PersonEntity?
        get() = activePerson
    val allPeople: List<PersonEntity>
        get() = allVisiblePeople
    val photoCount: Int
        get() = totalPostsCount
    val totalPhotoCount: Int
        get() = totalPostsCount
    val soloPhotoCount: Int
        get() = soloPostsCount
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    application: Application,
    private val initialPersonId: Long,
    val isRootMyProfileTab: Boolean = (initialPersonId == -2L)
) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)

    init {
        OnboardingPreferences.init(application)
    }

    private val _selectedFilter = MutableStateFlow<ProfilePhotoFilter>(ProfilePhotoFilter.All)
    val selectedFilter: StateFlow<ProfilePhotoFilter> = _selectedFilter.asStateFlow()

    private data class ResolvedTarget(
        val resolvedPersonId: Long,
        val activePerson: PersonEntity?,
        val isMyProfile: Boolean,
        val needsMyProfileSelection: Boolean,
        val allVisiblePeople: List<PersonEntity>
    )

    private val resolvedTargetFlow = combine(
        repository.allPeople,
        repository.allPeopleIncludeHidden,
        OnboardingPreferences.myProfilePersonIdFlow
    ) { visiblePeople, allIncludeHidden, savedMyId ->
        if (isRootMyProfileTab || initialPersonId == -2L) {
            // 1. Check if savedMyId exists in visiblePeople
            val existingSaved = if (savedMyId > 0L) {
                visiblePeople.firstOrNull { it.personId == savedMyId }
            } else {
                null
            }

            if (existingSaved != null) {
                ResolvedTarget(
                    resolvedPersonId = existingSaved.personId,
                    activePerson = existingSaved,
                    isMyProfile = true,
                    needsMyProfileSelection = false,
                    allVisiblePeople = visiblePeople
                )
            } else {
                // 2. Check if any visible PersonEntity is named "Me"
                val mePerson = visiblePeople.firstOrNull {
                    it.name.removePrefix("@").trim().equals("Me", ignoreCase = true)
                }
                if (mePerson != null) {
                    OnboardingPreferences.setMyProfilePersonId(getApplication(), mePerson.personId)
                    ResolvedTarget(
                        resolvedPersonId = mePerson.personId,
                        activePerson = mePerson,
                        isMyProfile = true,
                        needsMyProfileSelection = false,
                        allVisiblePeople = visiblePeople
                    )
                } else if (visiblePeople.isNotEmpty()) {
                    // 3. Fallback to first visible person and prompt user to confirm/select "Me"
                    val fallback = visiblePeople.first()
                    ResolvedTarget(
                        resolvedPersonId = fallback.personId,
                        activePerson = fallback,
                        isMyProfile = true,
                        needsMyProfileSelection = true,
                        allVisiblePeople = visiblePeople
                    )
                } else {
                    ResolvedTarget(
                        resolvedPersonId = -1L,
                        activePerson = null,
                        isMyProfile = true,
                        needsMyProfileSelection = false,
                        allVisiblePeople = visiblePeople
                    )
                }
            }
        } else if (initialPersonId == -1L) {
            ResolvedTarget(
                resolvedPersonId = -1L,
                activePerson = null,
                isMyProfile = false,
                needsMyProfileSelection = false,
                allVisiblePeople = visiblePeople
            )
        } else {
            val found = visiblePeople.firstOrNull { it.personId == initialPersonId }
                ?: allIncludeHidden.firstOrNull { it.personId == initialPersonId }
            val isMe = (savedMyId > 0L && initialPersonId == savedMyId)
            ResolvedTarget(
                resolvedPersonId = initialPersonId,
                activePerson = found,
                isMyProfile = isMe,
                needsMyProfileSelection = false,
                allVisiblePeople = visiblePeople
            )
        }
    }

    private val activePersonIdFlow = resolvedTargetFlow
        .map { it.resolvedPersonId }
        .distinctUntilChanged()

    private val allPersonPhotosFlow = activePersonIdFlow.flatMapLatest { pid ->
        if (pid <= 0L) {
            repository.getUnassignedPhotosFlow()
        } else {
            repository.getMediaForPersonFlow(pid)
        }
    }

    private val soloPhotosFlow = activePersonIdFlow.flatMapLatest { pid ->
        if (pid <= 0L) {
            flowOf(emptyList())
        } else {
            repository.getSoloMediaForPersonFlow(pid)
        }
    }

    private val coOccurringPeopleFlow = activePersonIdFlow.flatMapLatest { pid ->
        if (pid <= 0L) {
            flowOf(emptyList())
        } else {
            repository.getCoOccurringPeopleFlow(pid)
        }
    }

    private val facesFlow = activePersonIdFlow.flatMapLatest { pid ->
        if (pid <= 0L) {
            flowOf(emptyList())
        } else {
            repository.getFacesForPerson(pid)
        }
    }

    private val filteredPhotosFlow = combine(
        activePersonIdFlow,
        _selectedFilter
    ) { pid, filter -> pid to filter }
        .flatMapLatest { (pid, filter) ->
            if (pid <= 0L) {
                repository.getUnassignedPhotosFlow()
            } else {
                when (filter) {
                    is ProfilePhotoFilter.All -> repository.getMediaForPersonFlow(pid)
                    is ProfilePhotoFilter.SoloOnly -> repository.getSoloMediaForPersonFlow(pid)
                    is ProfilePhotoFilter.TogetherWith -> repository.getSharedMediaBetweenPeopleFlow(
                        personId = pid,
                        otherPersonId = filter.otherPerson.personId
                    )
                }
            }
        }

    private data class PhotoAndFilterBundle(
        val filter: ProfilePhotoFilter,
        val filteredPhotos: List<MediaWithAuthors>,
        val allPhotos: List<MediaWithAuthors>,
        val soloPhotos: List<MediaWithAuthors>,
        val coOccurring: List<CoOccurringPerson>
    )

    private val photoBundleFlow = combine(
        _selectedFilter,
        filteredPhotosFlow,
        allPersonPhotosFlow,
        soloPhotosFlow,
        coOccurringPeopleFlow
    ) { filter, filtered, allPhotos, soloPhotos, coOccurring ->
        PhotoAndFilterBundle(
            filter = filter,
            filteredPhotos = filtered.filter { !it.media.isVideo },
            allPhotos = allPhotos.filter { !it.media.isVideo },
            soloPhotos = soloPhotos.filter { !it.media.isVideo },
            coOccurring = coOccurring
        )
    }

    private data class BioBundle(
        val defaultBio: String,
        val personBios: Map<Long, String>
    )

    private val bioBundleFlow = combine(
        OnboardingPreferences.customProfileBioFlow,
        OnboardingPreferences.personBiosFlow
    ) { defaultBio, map -> BioBundle(defaultBio, map) }

    val uiState: StateFlow<ProfileUiState> = combine(
        resolvedTargetFlow,
        photoBundleFlow,
        facesFlow,
        bioBundleFlow
    ) { target, bundle, faces, bioBundle ->
        val pid = target.resolvedPersonId
        val totalPosts = bundle.allPhotos.size
        val soloPosts = bundle.soloPhotos.size
        val togetherPosts = (totalPosts - soloPosts).coerceAtLeast(0)
        val otherPeople = target.allVisiblePeople.filter { it.personId != pid }

        val customBio = when {
            pid == -1L -> "Photos without assigned face profiles"
            bioBundle.personBios.containsKey(pid) -> bioBundle.personBios[pid] ?: OnboardingPreferences.DEFAULT_PROFILE_BIO
            target.isMyProfile -> bioBundle.defaultBio
            else -> "Capturing moments on device • $totalPosts memories"
        }

        ProfileUiState(
            personId = pid,
            activePerson = target.activePerson,
            isMyProfile = target.isMyProfile,
            needsMyProfileSelection = target.needsMyProfileSelection,
            allVisiblePeople = target.allVisiblePeople,
            otherPeople = otherPeople,
            photos = bundle.filteredPhotos,
            totalPostsCount = totalPosts,
            soloPostsCount = soloPosts,
            togetherPostsCount = togetherPosts,
            customBio = customBio,
            selectedFilter = bundle.filter,
            coOccurringPeople = bundle.coOccurring,
            faces = faces
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState(personId = initialPersonId)
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

    fun selectMyProfilePerson(personId: Long) {
        if (personId <= 0L) return
        OnboardingPreferences.setMyProfilePersonId(getApplication(), personId)
        _selectedFilter.value = ProfilePhotoFilter.All
    }

    fun updateProfileAndBio(newName: String, newBio: String) {
        val pid = uiState.value.personId
        if (pid <= 0L) return
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                repository.updatePersonName(pid, newName.trim())
            }
            OnboardingPreferences.setCustomProfileBio(getApplication(), pid, newBio.trim())
        }
    }

    fun updateProfileName(newName: String) {
        val pid = uiState.value.personId
        if (pid <= 0L || newName.isBlank()) return
        viewModelScope.launch {
            repository.updatePersonName(pid, newName.trim())
        }
    }

    fun updateCustomBio(newBio: String) {
        val pid = uiState.value.personId
        OnboardingPreferences.setCustomProfileBio(getApplication(), pid, newBio)
    }

    fun changeProfileAvatar(cropPath: String) {
        val pid = uiState.value.personId
        if (pid <= 0L || cropPath.isBlank()) return
        viewModelScope.launch {
            repository.updateAvatar(pid, cropPath)
        }
    }

    fun removePhotoFromPerson(mediaId: Long, targetPersonId: Long = uiState.value.personId) {
        if (targetPersonId <= 0L) return
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
        val pid = uiState.value.personId
        if (pid <= 0L || targetPersonId == pid) return
        val currentPerson = uiState.value.activePerson ?: return
        viewModelScope.launch {
            val targetPerson = uiState.value.allVisiblePeople.firstOrNull { it.personId == targetPersonId }
            val mergedName = if (currentPerson.isNamedByUser) {
                currentPerson.name
            } else {
                targetPerson?.name ?: currentPerson.name
            }
            repository.mergePeople(listOf(pid), targetPersonId, mergedName)
            if (OnboardingPreferences.getMyProfilePersonId(getApplication()) == pid) {
                OnboardingPreferences.setMyProfilePersonId(getApplication(), targetPersonId)
            }
            onComplete(targetPersonId)
        }
    }

    fun moveProfileToOther(onComplete: () -> Unit = {}) {
        val pid = uiState.value.personId
        if (pid <= 0L) return
        viewModelScope.launch {
            repository.movePersonToOther(pid)
            onComplete()
        }
    }

    fun restoreProfileFromOther(onComplete: () -> Unit = {}) {
        val pid = uiState.value.personId
        if (pid <= 0L) return
        viewModelScope.launch {
            repository.restorePersonFromOtherSql(pid)
            onComplete()
        }
    }

    fun updateCaption(mediaId: Long, caption: String?) {
        viewModelScope.launch {
            repository.updateMediaCaption(mediaId, caption)
        }
    }
}
