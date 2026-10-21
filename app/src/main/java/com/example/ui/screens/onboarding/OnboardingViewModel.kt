// InstaGallery v1.0
package com.example.ui.screens.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.data.local.OnboardingPreferences
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.PersonEntity
import com.example.data.repository.GalleryRepository
import com.example.ml.FaceIndexingWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PersonPreviewCard(
    val person: PersonEntity,
    val sampleMedia: List<MediaEntity>,
    val photoCount: Int,
    val reelCount: Int = 0
)

data class OnboardingUiState(
    val isScanning: Boolean = true,
    val isScanFinished: Boolean = false,
    val processedCount: Int = 0,
    val totalMediaCount: Int = 0,
    val detectedPeople: List<PersonPreviewCard> = emptyList(),
    val unassociatedMediaCount: Int = 0,
    val editedNames: Map<Long, String> = emptyMap(),
    val markedUnknownIds: Set<Long> = emptySet(),
    val isSavingSetup: Boolean = false
) {
    val progressFraction: Float
        get() = if (totalMediaCount > 0) {
            (processedCount.toFloat() / totalMediaCount.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt().coerceIn(0, 100)
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)

    private val _editedNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _markedUnknownIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isSavingSetup = MutableStateFlow(false)
    private val _hasStartedScan = MutableStateFlow(false)

    private data class ScanMetrics(
        val processed: Int,
        val total: Int,
        val unindexed: Int,
        val workInfo: WorkInfo?
    )

    private val scanMetricsFlow = combine(
        repository.processedCount,
        repository.totalMediaCount,
        repository.unindexedCount,
        repository.indexingWorkInfo
    ) { processed, total, unindexed, workInfo ->
        ScanMetrics(processed, total, unindexed, workInfo)
    }

    private val peopleCardsFlow: Flow<List<PersonPreviewCard>> = repository.allPeople.flatMapLatest { people ->
        if (people.isEmpty()) {
            flowOf(emptyList())
        } else {
            val perPersonFlows = people.map { person ->
                repository.getMediaWithAuthorsForPersonFlow(person.personId).map { mediaWithAuthors ->
                    val photosOnly = mediaWithAuthors
                        .map { it.media }
                        .filter { !it.isVideo }
                    PersonPreviewCard(
                        person = person,
                        sampleMedia = photosOnly.take(3),
                        photoCount = photosOnly.size,
                        reelCount = 0
                    )
                }
            }
            combine(perPersonFlows) { cardsArray ->
                cardsArray.toList()
            }
        }
    }

    private val peoplePreviewFlow = combine(
        peopleCardsFlow,
        repository.unassociatedCount
    ) { cards, unassociatedCount ->
        cards to unassociatedCount
    }

    val uiState: StateFlow<OnboardingUiState> = combine(
        scanMetricsFlow,
        peoplePreviewFlow,
        _editedNames,
        _markedUnknownIds,
        _isSavingSetup
    ) { metrics, previewPair, editedNames, markedUnknown, isSaving ->
        val (cards, unassociatedCount) = previewPair
        val workState = metrics.workInfo?.state
        val workerProcessed = metrics.workInfo?.progress?.getInt(FaceIndexingWorker.PROGRESS_PROCESSED, -1) ?: -1
        val workerTotal = metrics.workInfo?.progress?.getInt(FaceIndexingWorker.PROGRESS_TOTAL, -1) ?: -1

        val effectiveTotal = if (metrics.total > 0) metrics.total else if (workerTotal > 0) workerTotal else 0
        val effectiveProcessed = maxOf(metrics.processed, if (workerProcessed >= 0) workerProcessed else 0)
            .coerceAtMost(effectiveTotal.coerceAtLeast(1))

        val isScanComplete = _hasStartedScan.value && (
            (effectiveTotal > 0 && metrics.unindexed == 0) ||
                workState == WorkInfo.State.SUCCEEDED ||
                (effectiveTotal == 0 && workState != WorkInfo.State.RUNNING && workState != WorkInfo.State.ENQUEUED)
            )

        // Ensure default names are populated in editedNames map for any newly discovered person
        val mergedNames = buildMap {
            for (card in cards) {
                put(card.person.personId, editedNames[card.person.personId] ?: card.person.name)
            }
        }

        OnboardingUiState(
            isScanning = !isScanComplete,
            isScanFinished = isScanComplete,
            processedCount = if (isScanComplete) effectiveTotal else effectiveProcessed,
            totalMediaCount = effectiveTotal,
            detectedPeople = cards,
            unassociatedMediaCount = unassociatedCount,
            editedNames = mergedNames,
            markedUnknownIds = markedUnknown,
            isSavingSetup = isSaving
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OnboardingUiState()
    )

    fun startInitialScanIfNeeded() {
        if (_hasStartedScan.value) return
        _hasStartedScan.value = true
        viewModelScope.launch {
            val count = repository.scanDeviceMedia()
            if (count == 0) {
                OnboardingPreferences.markInitialScanCompleted(getApplication(), true)
            }
        }
    }

    fun triggerFullRescan() {
        _hasStartedScan.value = true
        _editedNames.value = emptyMap()
        _markedUnknownIds.value = emptySet()
        viewModelScope.launch {
            repository.resetAndRescanAllFaces()
        }
    }

    fun onNameChanged(personId: Long, newName: String) {
        val current = _editedNames.value.toMutableMap()
        current[personId] = newName
        _editedNames.value = current
        // If user starts typing a name for a person previously marked Unknown, automatically unmark Unknown
        if (newName.isNotBlank() && _markedUnknownIds.value.contains(personId)) {
            _markedUnknownIds.value = _markedUnknownIds.value - personId
        }
    }

    fun toggleUnknownStatus(personId: Long) {
        val current = _markedUnknownIds.value.toMutableSet()
        if (current.contains(personId)) {
            current.remove(personId)
        } else {
            current.add(personId)
        }
        _markedUnknownIds.value = current
    }

    fun setPersonUnknownStatus(personId: Long, isUnknown: Boolean, defaultName: String = "") {
        val current = _markedUnknownIds.value.toMutableSet()
        if (isUnknown) {
            current.add(personId)
        } else {
            current.remove(personId)
            val existingName = _editedNames.value[personId]
            if (existingName != null && existingName.isBlank() && defaultName.isNotBlank()) {
                val names = _editedNames.value.toMutableMap()
                names[personId] = defaultName
                _editedNames.value = names
            }
        }
        _markedUnknownIds.value = current
    }

    fun markSinglePhotoFacesAsUnknown() {
        val cards = uiState.value.detectedPeople
        val currentUnknown = _markedUnknownIds.value.toMutableSet()
        val singlePhotoIds = cards
            .filter { it.person.faceCount <= 1 && !it.person.isNamedByUser }
            .map { it.person.personId }
            .toSet()

        val allAlreadyMarked = singlePhotoIds.isNotEmpty() && singlePhotoIds.all { currentUnknown.contains(it) }
        if (allAlreadyMarked) {
            currentUnknown.removeAll(singlePhotoIds)
        } else {
            currentUnknown.addAll(singlePhotoIds)
        }
        _markedUnknownIds.value = currentUnknown
    }

    fun finishEarlyAndGoToSetup() {
        OnboardingPreferences.markInitialScanCompleted(getApplication(), true)
    }

    fun completeProfileSetup(onComplete: () -> Unit) {
        if (_isSavingSetup.value) return
        _isSavingSetup.value = true

        val state = uiState.value
        val namesToSave = state.editedNames
        val unknowns = state.markedUnknownIds.toMutableSet()

        // Any person whose name was cleared blank by the user is also treated as Unknown (@Other)
        for ((id, name) in namesToSave) {
            if (name.trim().isBlank()) {
                unknowns.add(id)
            }
        }

        viewModelScope.launch {
            repository.applyProfileSetup(
                namedProfiles = namesToSave,
                unknownPersonIds = unknowns
            )
            _isSavingSetup.value = false
            onComplete()
        }
    }
}
