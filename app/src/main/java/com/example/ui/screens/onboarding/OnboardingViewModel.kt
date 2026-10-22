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
import com.example.ml.SimilarProfileCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ProfileDecisionState {
    UNDECIDED,
    KEPT,
    MOVED_TO_OTHER
}

enum class SetupFilterMode {
    TOP_PEOPLE,
    ALL_DETECTED
}

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
    val allDetectedPeople: List<PersonPreviewCard> = emptyList(),
    val topPeopleCount: Int = 0,
    val allDetectedCount: Int = 0,
    val unassociatedMediaCount: Int = 0,
    val editedNames: Map<Long, String> = emptyMap(),
    val markedUnknownIds: Set<Long> = emptySet(),
    val decisionMap: Map<Long, ProfileDecisionState> = emptyMap(),
    val filterMode: SetupFilterMode = SetupFilterMode.ALL_DETECTED,
    val autoHideSinglePhotoStrangers: Boolean = true,
    val quickNameChips: List<String> = OnboardingViewModel.DEFAULT_QUICK_NAME_CHIPS,
    val similarCandidates: List<SimilarProfileCandidate> = emptyList(),
    val similarCandidatesForPersonId: Long? = null,
    val feedbackMessage: String? = null,
    val isSavingSetup: Boolean = false
) {
    val progressFraction: Float
        get() = if (totalMediaCount > 0) {
            (processedCount.toFloat() / totalMediaCount.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt().coerceIn(0, 100)

    val decidedCount: Int
        get() = detectedPeople.count { card ->
            val state = decisionMap[card.person.personId] ?: ProfileDecisionState.UNDECIDED
            state == ProfileDecisionState.KEPT || state == ProfileDecisionState.MOVED_TO_OTHER
        }

    val keptCount: Int
        get() = allDetectedPeople.count { card ->
            val state = decisionMap[card.person.personId] ?: ProfileDecisionState.UNDECIDED
            state == ProfileDecisionState.KEPT
        }

    val movedToOtherCount: Int
        get() = allDetectedPeople.count { card ->
            val state = decisionMap[card.person.personId] ?: ProfileDecisionState.UNDECIDED
            state == ProfileDecisionState.MOVED_TO_OTHER
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        val DEFAULT_QUICK_NAME_CHIPS = listOf("Me", "Papa", "Maa", "Bro", "Sis", "Bestie", "Partner")
        private val DEFAULT_PERSON_NAME_REGEX = Regex("^Person\\s+\\d+$", RegexOption.IGNORE_CASE)
    }

    private val galleryRepository = GalleryRepository(application)

    private val _editedNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _decisionMap = MutableStateFlow<Map<Long, ProfileDecisionState>>(emptyMap())
    private val _userSelectedFilterMode = MutableStateFlow<SetupFilterMode?>(null)
    private val _autoHideSinglePhotoStrangers = MutableStateFlow(true)
    private val _quickNameChips = MutableStateFlow(DEFAULT_QUICK_NAME_CHIPS)
    val quickNameChips: StateFlow<List<String>> = _quickNameChips.asStateFlow()

    private val _similarCandidates = MutableStateFlow<List<SimilarProfileCandidate>>(emptyList())
    private val _similarCandidatesForPersonId = MutableStateFlow<Long?>(null)
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    private val _isSavingSetup = MutableStateFlow(false)
    private val _hasStartedScan = MutableStateFlow(false)
    private val _isMediaStoreScanFinished = MutableStateFlow(false)

    // Keep track of profiles touched during the current session so cards don't vanish mid-session
    private val _sessionTouchedIds = MutableStateFlow<Set<Long>>(emptySet())

    private data class ScanMetrics(
        val processed: Int,
        val total: Int,
        val unindexed: Int,
        val workInfo: WorkInfo?,
        val isMediaStoreReady: Boolean
    )

    private val scanMetricsFlow = combine(
        galleryRepository.processedCount,
        galleryRepository.totalMediaCount,
        galleryRepository.unindexedCount,
        galleryRepository.indexingWorkInfo,
        _isMediaStoreScanFinished
    ) { processed, total, unindexed, workInfo, isMediaStoreReady ->
        ScanMetrics(processed, total, unindexed, workInfo, isMediaStoreReady)
    }

    /**
     * Observes all detected profiles (including ones marked `isHiddenToOther = true` during this session)
     * so `ALL_DETECTED` and session cards marked `MOVED_TO_OTHER` can be undone without vanishing.
     */
    private val allPeopleCardsFlow: Flow<List<PersonPreviewCard>> = combine(
        galleryRepository.allPeopleIncludeHidden,
        _sessionTouchedIds
    ) { people, sessionTouched ->
        // Include all non-hidden profiles plus any hidden profiles that were moved to @Other or present
        people.filter { person ->
            !person.isHiddenToOther || sessionTouched.contains(person.personId) || person.faceCount >= 1
        }
    }.mapLatest { people ->
        if (people.isEmpty()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                people.map { person ->
                    val samplePhotos = galleryRepository.getSamplePhotosForPersonSync(person.personId, limit = 3)
                    PersonPreviewCard(
                        person = person,
                        sampleMedia = samplePhotos,
                        photoCount = person.faceCount.coerceAtLeast(samplePhotos.size),
                        reelCount = 0
                    )
                }
            }
        }
    }

    private data class SetupConfigState(
        val editedNames: Map<Long, String>,
        val explicitDecisions: Map<Long, ProfileDecisionState>,
        val userFilterMode: SetupFilterMode?,
        val autoHideStrangers: Boolean,
        val quickChips: List<String>
    )

    private val setupConfigFlow = combine(
        _editedNames,
        _decisionMap,
        _userSelectedFilterMode,
        _autoHideSinglePhotoStrangers,
        _quickNameChips
    ) { editedNames, explicitDecisions, userFilterMode, autoHideStrangers, quickChips ->
        SetupConfigState(
            editedNames = editedNames,
            explicitDecisions = explicitDecisions,
            userFilterMode = userFilterMode,
            autoHideStrangers = autoHideStrangers,
            quickChips = quickChips
        )
    }

    private data class SimilarityAndUiExtras(
        val unassociatedCount: Int,
        val similarCandidates: List<SimilarProfileCandidate>,
        val similarForPersonId: Long?,
        val feedbackMessage: String?,
        val isSaving: Boolean
    )

    private val extrasFlow = combine(
        galleryRepository.unassociatedCount,
        _similarCandidates,
        _similarCandidatesForPersonId,
        _feedbackMessage,
        _isSavingSetup
    ) { unassociatedCount, similarCandidates, similarForId, feedbackMessage, isSaving ->
        SimilarityAndUiExtras(
            unassociatedCount = unassociatedCount,
            similarCandidates = similarCandidates,
            similarForPersonId = similarForId,
            feedbackMessage = feedbackMessage,
            isSaving = isSaving
        )
    }

    val uiState: StateFlow<OnboardingUiState> = combine(
        scanMetricsFlow,
        allPeopleCardsFlow,
        setupConfigFlow,
        extrasFlow
    ) { metrics, allCards, config, extras ->
        val workState = metrics.workInfo?.state
        val workerProcessed = metrics.workInfo?.progress?.getInt(FaceIndexingWorker.PROGRESS_PROCESSED, -1) ?: -1
        val workerTotal = metrics.workInfo?.progress?.getInt(FaceIndexingWorker.PROGRESS_TOTAL, -1) ?: -1

        val effectiveTotal = if (metrics.total > 0) metrics.total else if (workerTotal > 0) workerTotal else 0
        val effectiveProcessed = maxOf(metrics.processed, if (workerProcessed >= 0) workerProcessed else 0)
            .coerceAtMost(effectiveTotal.coerceAtLeast(1))

        val isWorkerActive = workState == WorkInfo.State.RUNNING || workState == WorkInfo.State.ENQUEUED

        val isScanComplete = metrics.isMediaStoreReady && (
            (effectiveTotal > 0 && metrics.unindexed == 0) ||
                (effectiveTotal == 0 && !isWorkerActive)
            )

        // Build resolved decisionMap for every detected card:
        // Default state: if isNamedByUser == true -> KEPT; if isHiddenToOther == true -> MOVED_TO_OTHER; otherwise UNDECIDED
        val resolvedDecisions = buildMap {
            for (card in allCards) {
                val p = card.person
                val explicit = config.explicitDecisions[p.personId]
                val resolved = explicit ?: when {
                    p.isHiddenToOther -> ProfileDecisionState.MOVED_TO_OTHER
                    p.isNamedByUser -> ProfileDecisionState.KEPT
                    else -> ProfileDecisionState.UNDECIDED
                }
                put(p.personId, resolved)
            }
        }

        // Determine active FilterMode (default: TOP_PEOPLE when total profiles > 20, otherwise ALL_DETECTED)
        val effectiveFilterMode = config.userFilterMode ?: if (allCards.size > 20) {
            SetupFilterMode.TOP_PEOPLE
        } else {
            SetupFilterMode.ALL_DETECTED
        }

        // Compute TOP_PEOPLE photo threshold: >= 3 by default, lowered to >= 2 if fewer than 10 profiles have photoCount >= 3
        val countWith3Plus = allCards.count { it.photoCount >= 3 && !it.person.isHiddenToOther }
        val topMinPhotoThreshold = if (countWith3Plus < 10) 2 else 3

        val topPeopleCards = allCards.filter { card ->
            val p = card.person
            val hasExplicitDecision = config.explicitDecisions[p.personId] != null
            val isVisibleOrSessionTouched = !p.isHiddenToOther || hasExplicitDecision
            isVisibleOrSessionTouched && (
                card.photoCount >= topMinPhotoThreshold ||
                    p.isNamedByUser ||
                    hasExplicitDecision
                )
        }

        val allDetectedCards = allCards.filter { card ->
            card.photoCount >= 1
        }

        val displayedCards = when (effectiveFilterMode) {
            SetupFilterMode.TOP_PEOPLE -> if (topPeopleCards.isNotEmpty()) topPeopleCards else allDetectedCards
            SetupFilterMode.ALL_DETECTED -> allDetectedCards
        }

        val mergedNames = buildMap {
            for (card in allCards) {
                put(card.person.personId, config.editedNames[card.person.personId] ?: card.person.name)
            }
        }

        val markedUnknownSet = resolvedDecisions.entries
            .filter { it.value == ProfileDecisionState.MOVED_TO_OTHER }
            .map { it.key }
            .toSet()

        OnboardingUiState(
            isScanning = !isScanComplete,
            isScanFinished = isScanComplete,
            processedCount = if (isScanComplete) effectiveTotal else effectiveProcessed,
            totalMediaCount = effectiveTotal,
            detectedPeople = displayedCards,
            allDetectedPeople = allDetectedCards,
            topPeopleCount = topPeopleCards.size,
            allDetectedCount = allDetectedCards.size,
            unassociatedMediaCount = extras.unassociatedCount,
            editedNames = mergedNames,
            markedUnknownIds = markedUnknownSet,
            decisionMap = resolvedDecisions,
            filterMode = effectiveFilterMode,
            autoHideSinglePhotoStrangers = config.autoHideStrangers,
            quickNameChips = config.quickChips,
            similarCandidates = extras.similarCandidates,
            similarCandidatesForPersonId = extras.similarForPersonId,
            feedbackMessage = extras.feedbackMessage,
            isSavingSetup = extras.isSaving
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OnboardingUiState()
    )

    private fun isCustomPersonName(rawName: String): Boolean {
        val clean = rawName.trim().removePrefix("@").trim()
        return clean.isNotEmpty() && !DEFAULT_PERSON_NAME_REGEX.matches(clean)
    }

    private fun prependCustomQuickChip(rawName: String) {
        val clean = rawName.trim().removePrefix("@").trim()
        if (!isCustomPersonName(clean)) return
        val existing = _quickNameChips.value
        val deduplicated = listOf(clean) + existing.filterNot { it.equals(clean, ignoreCase = true) }
        _quickNameChips.value = deduplicated
    }

    fun setFilterMode(mode: SetupFilterMode) {
        _userSelectedFilterMode.value = mode
    }

    fun setAutoHideSinglePhotoStrangers(enabled: Boolean) {
        _autoHideSinglePhotoStrangers.value = enabled
    }

    fun toggleAutoHideSinglePhotoStrangers() {
        _autoHideSinglePhotoStrangers.value = !_autoHideSinglePhotoStrangers.value
    }

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }

    fun loadSimilarCandidatesFor(personId: Long) {
        _similarCandidatesForPersonId.value = personId
        viewModelScope.launch {
            val candidates = galleryRepository.getSimilarProfilesFor(personId, limit = 8)
            if (_similarCandidatesForPersonId.value == personId) {
                _similarCandidates.value = candidates
            }
        }
    }

    fun mergeCurrentIntoTarget(
        sourcePersonId: Long,
        targetPerson: PersonEntity,
        onMerged: () -> Unit = {}
    ) {
        if (sourcePersonId == targetPerson.personId) return
        viewModelScope.launch {
            val resolvedTargetName = _editedNames.value[targetPerson.personId]
                ?.trim()
                ?.removePrefix("@")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: targetPerson.name

            galleryRepository.mergeProfiles(
                targetId = targetPerson.personId,
                sourceIds = listOf(sourcePersonId),
                finalName = resolvedTargetName
            )

            val updatedDecisions = _decisionMap.value.toMutableMap()
            updatedDecisions.remove(sourcePersonId)
            updatedDecisions[targetPerson.personId] = ProfileDecisionState.KEPT
            _decisionMap.value = updatedDecisions

            val updatedNames = _editedNames.value.toMutableMap()
            updatedNames.remove(sourcePersonId)
            updatedNames[targetPerson.personId] = resolvedTargetName
            _editedNames.value = updatedNames

            _sessionTouchedIds.value = _sessionTouchedIds.value + targetPerson.personId
            prependCustomQuickChip(resolvedTargetName)

            val cleanDisplay = resolvedTargetName.removePrefix("@").trim()
            _feedbackMessage.value = "Merged into @$cleanDisplay"
            onMerged()
        }
    }

    fun mergeLookalikeIntoCurrent(
        currentPerson: PersonEntity,
        lookalikePersonId: Long
    ) {
        if (currentPerson.personId == lookalikePersonId) return
        viewModelScope.launch {
            val resolvedCurrentName = _editedNames.value[currentPerson.personId]
                ?.trim()
                ?.removePrefix("@")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: currentPerson.name

            galleryRepository.mergeProfiles(
                targetId = currentPerson.personId,
                sourceIds = listOf(lookalikePersonId),
                finalName = resolvedCurrentName
            )

            val updatedDecisions = _decisionMap.value.toMutableMap()
            updatedDecisions.remove(lookalikePersonId)
            if (isCustomPersonName(resolvedCurrentName) || currentPerson.isNamedByUser) {
                updatedDecisions[currentPerson.personId] = ProfileDecisionState.KEPT
            }
            _decisionMap.value = updatedDecisions

            val updatedNames = _editedNames.value.toMutableMap()
            updatedNames.remove(lookalikePersonId)
            updatedNames[currentPerson.personId] = resolvedCurrentName
            _editedNames.value = updatedNames

            _sessionTouchedIds.value = _sessionTouchedIds.value + currentPerson.personId
            prependCustomQuickChip(resolvedCurrentName)

            val refreshed = galleryRepository.getSimilarProfilesFor(currentPerson.personId, limit = 8)
            if (_similarCandidatesForPersonId.value == currentPerson.personId) {
                _similarCandidates.value = refreshed
            }
            val cleanDisplay = resolvedCurrentName.removePrefix("@").trim()
            _feedbackMessage.value = "Absorbed lookalike into @$cleanDisplay"
        }
    }

    fun mergeMultipleProfiles(personIds: List<Long>, targetName: String) {
        val distinctIds = personIds.distinct()
        if (distinctIds.isEmpty()) return
        viewModelScope.launch {
            val primaryId = distinctIds.first()
            val sourceIds = distinctIds.drop(1)
            val cleanName = targetName.trim().removePrefix("@").trim().ifBlank {
                _editedNames.value[primaryId] ?: "Person $primaryId"
            }

            if (sourceIds.isNotEmpty()) {
                galleryRepository.mergeProfiles(
                    targetId = primaryId,
                    sourceIds = sourceIds,
                    finalName = cleanName
                )
            } else {
                galleryRepository.renamePerson(primaryId, cleanName)
            }

            val updatedDecisions = _decisionMap.value.toMutableMap()
            for (id in sourceIds) {
                updatedDecisions.remove(id)
            }
            updatedDecisions[primaryId] = ProfileDecisionState.KEPT
            _decisionMap.value = updatedDecisions

            val updatedNames = _editedNames.value.toMutableMap()
            for (id in sourceIds) {
                updatedNames.remove(id)
            }
            updatedNames[primaryId] = cleanName
            _editedNames.value = updatedNames

            _sessionTouchedIds.value = _sessionTouchedIds.value + primaryId
            prependCustomQuickChip(cleanName)

            _feedbackMessage.value = "Merged ${distinctIds.size} profiles into @$cleanName"
            if (_similarCandidatesForPersonId.value != null) {
                loadSimilarCandidatesFor(primaryId)
            }
        }
    }

    fun markKeepProfile(personId: Long, currentName: String) {
        val cleanName = currentName.trim().removePrefix("@").trim()
        if (cleanName.isBlank()) {
            toggleMoveToOther(personId)
            return
        }

        val updatedNames = _editedNames.value.toMutableMap()
        updatedNames[personId] = cleanName
        _editedNames.value = updatedNames

        val updatedDecisions = _decisionMap.value.toMutableMap()
        updatedDecisions[personId] = ProfileDecisionState.KEPT
        _decisionMap.value = updatedDecisions

        _sessionTouchedIds.value = _sessionTouchedIds.value + personId
        prependCustomQuickChip(cleanName)

        viewModelScope.launch {
            galleryRepository.renamePerson(personId, cleanName)
            if (_similarCandidatesForPersonId.value == personId) {
                _similarCandidates.value = galleryRepository.getSimilarProfilesFor(personId, limit = 8)
            }
        }
    }

    fun toggleMoveToOther(personId: Long) {
        val currentDecision = uiState.value.decisionMap[personId] ?: ProfileDecisionState.UNDECIDED
        val updatedDecisions = _decisionMap.value.toMutableMap()
        _sessionTouchedIds.value = _sessionTouchedIds.value + personId

        if (currentDecision == ProfileDecisionState.MOVED_TO_OTHER) {
            updatedDecisions[personId] = ProfileDecisionState.UNDECIDED
            _decisionMap.value = updatedDecisions
            viewModelScope.launch {
                galleryRepository.unhidePersonFromOther(personId)
            }
        } else {
            updatedDecisions[personId] = ProfileDecisionState.MOVED_TO_OTHER
            _decisionMap.value = updatedDecisions
            viewModelScope.launch {
                galleryRepository.hidePersonToOther(personId)
            }
        }
    }

    fun startInitialScanIfNeeded() {
        if (_hasStartedScan.value) return
        _hasStartedScan.value = true
        _isMediaStoreScanFinished.value = false
        viewModelScope.launch {
            val count = galleryRepository.scanDeviceMedia()
            _isMediaStoreScanFinished.value = true
            if (count == 0) {
                OnboardingPreferences.markInitialScanCompleted(getApplication(), true)
            }
        }
    }

    fun triggerFullRescan() {
        _hasStartedScan.value = true
        _isMediaStoreScanFinished.value = false
        _editedNames.value = emptyMap()
        _decisionMap.value = emptyMap()
        _sessionTouchedIds.value = emptySet()
        _similarCandidates.value = emptyList()
        _similarCandidatesForPersonId.value = null
        viewModelScope.launch {
            galleryRepository.resetAndRescanAllFaces()
            _isMediaStoreScanFinished.value = true
        }
    }

    fun onNameChanged(personId: Long, newName: String) {
        val current = _editedNames.value.toMutableMap()
        current[personId] = newName
        _editedNames.value = current

        val currentDecision = _decisionMap.value[personId]
        if (newName.isNotBlank() && currentDecision == ProfileDecisionState.MOVED_TO_OTHER) {
            val updated = _decisionMap.value.toMutableMap()
            updated[personId] = ProfileDecisionState.KEPT
            _decisionMap.value = updated
            viewModelScope.launch {
                galleryRepository.unhidePersonFromOther(personId)
            }
        } else if (isCustomPersonName(newName)) {
            prependCustomQuickChip(newName)
        }
    }

    fun toggleUnknownStatus(personId: Long) {
        toggleMoveToOther(personId)
    }

    fun setPersonUnknownStatus(personId: Long, isUnknown: Boolean, defaultName: String = "") {
        val updatedDecisions = _decisionMap.value.toMutableMap()
        _sessionTouchedIds.value = _sessionTouchedIds.value + personId
        if (isUnknown) {
            updatedDecisions[personId] = ProfileDecisionState.MOVED_TO_OTHER
            _decisionMap.value = updatedDecisions
            viewModelScope.launch {
                galleryRepository.hidePersonToOther(personId)
            }
        } else {
            updatedDecisions[personId] = ProfileDecisionState.KEPT
            _decisionMap.value = updatedDecisions
            val existingName = _editedNames.value[personId]
            if (existingName != null && existingName.isBlank() && defaultName.isNotBlank()) {
                val names = _editedNames.value.toMutableMap()
                names[personId] = defaultName
                _editedNames.value = names
            }
            viewModelScope.launch {
                galleryRepository.unhidePersonFromOther(personId)
            }
        }
    }

    fun markSinglePhotoFacesAsUnknown() {
        val cards = uiState.value.allDetectedPeople
        val singlePhotoIds = cards
            .filter { it.photoCount <= 1 && !it.person.isNamedByUser }
            .map { it.person.personId }
            .toSet()
        if (singlePhotoIds.isEmpty()) return

        val currentDecisions = uiState.value.decisionMap
        val allAlreadyMoved = singlePhotoIds.all { currentDecisions[it] == ProfileDecisionState.MOVED_TO_OTHER }
        val updated = _decisionMap.value.toMutableMap()
        _sessionTouchedIds.value = _sessionTouchedIds.value + singlePhotoIds

        viewModelScope.launch {
            if (allAlreadyMoved) {
                for (id in singlePhotoIds) {
                    updated[id] = ProfileDecisionState.UNDECIDED
                    galleryRepository.unhidePersonFromOther(id)
                }
            } else {
                for (id in singlePhotoIds) {
                    updated[id] = ProfileDecisionState.MOVED_TO_OTHER
                    galleryRepository.hidePersonToOther(id)
                }
            }
            _decisionMap.value = updated
        }
    }

    fun finishEarlyAndGoToSetup() {
        OnboardingPreferences.markInitialScanCompleted(getApplication(), true)
    }

    fun finalizeOnboarding(onComplete: () -> Unit) {
        if (_isSavingSetup.value) return
        _isSavingSetup.value = true

        val state = uiState.value
        val namesToSave = state.editedNames
        val decisions = state.decisionMap
        val unknowns = mutableSetOf<Long>()

        for (card in state.allDetectedPeople) {
            val id = card.person.personId
            val decision = decisions[id] ?: ProfileDecisionState.UNDECIDED
            val editedName = (namesToSave[id] ?: card.person.name).trim()

            if (decision == ProfileDecisionState.MOVED_TO_OTHER || editedName.isBlank()) {
                unknowns.add(id)
            } else if (
                state.autoHideSinglePhotoStrangers &&
                decision == ProfileDecisionState.UNDECIDED &&
                card.photoCount <= 1 &&
                !card.person.isNamedByUser &&
                !isCustomPersonName(editedName)
            ) {
                unknowns.add(id)
            }
        }

        // Only pass custom-renamed or explicitly kept profiles into namedProfiles so unnamed "Person X"
        // profiles aren't falsely marked as user-named unless the user typed a custom name.
        val activeNamesToApply = buildMap {
            for (card in state.allDetectedPeople) {
                val id = card.person.personId
                if (unknowns.contains(id)) continue
                val editedName = (namesToSave[id] ?: card.person.name).trim()
                if (isCustomPersonName(editedName) || card.person.isNamedByUser) {
                    put(id, editedName)
                }
            }
        }

        viewModelScope.launch {
            galleryRepository.applyProfileSetup(
                namedProfiles = activeNamesToApply,
                unknownPersonIds = unknowns
            )
            _isSavingSetup.value = false
            onComplete()
        }
    }

    fun completeProfileSetup(onComplete: () -> Unit) {
        finalizeOnboarding(onComplete)
    }
}
