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
    val profilesCreated: Int = 0,
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

    private var maxProfilesSeen = 0

    // Immutable slot order so cards NEVER re-sort or jump positions when Kept, Renamed, or Moved to @Other
    private val orderLock = Any()
    private val lockedOrderIds = mutableListOf<Long>()
    private val initialPhotoCountMap = mutableMapOf<Long, Int>()
    private val initialUndecidedSet = mutableSetOf<Long>()
    private val mergedDeletedIds = mutableSetOf<Long>()

    private val _editedNames = MutableStateFlow<Map<Long, String>>(emptyMap())
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
     * Observes all setup profiles (both `isHiddenToOther = 0` and `isHiddenToOther = 1`) from
     * `personDao.getAllPeopleForSetupFlow()` and locks their slot order in `lockedOrderIds`.
     */
    private val allPeopleCardsFlow: Flow<List<PersonPreviewCard>> =
        galleryRepository.getAllPeopleForSetupFlow().mapLatest { people ->
            if (people.isEmpty()) {
                emptyList()
            } else {
                withContext(Dispatchers.IO) {
                    val rawCards = people.map { person ->
                        val samplePhotos = galleryRepository.getSamplePhotosForPersonSync(person.personId, limit = 3)
                        val count = person.faceCount.coerceAtLeast(samplePhotos.size)
                        PersonPreviewCard(
                            person = person,
                            sampleMedia = samplePhotos,
                            photoCount = count,
                            reelCount = 0
                        )
                    }

                    synchronized(orderLock) {
                        val isPostOnboarding = OnboardingPreferences.isOnboardingCompleted(getApplication())
                        val currentIds = rawCards.asSequence().map { it.person.personId }.toHashSet()
                        // Remove any personId that was deleted via a profile merge (or no longer exists in DB)
                        lockedOrderIds.removeAll { id -> id in mergedDeletedIds || id !in currentIds }

                        val newlyAddedPriorityIds = mutableListOf<Long>()
                        // Append any newly discovered personId in its initial (faceCount DESC, personId ASC) order
                        for (card in rawCards) {
                            val id = card.person.personId
                            val prevInitial = initialPhotoCountMap[id] ?: 0
                            if (card.photoCount > prevInitial) {
                                initialPhotoCountMap[id] = card.photoCount
                            }
                            if (id !in lockedOrderIds) {
                                val isUndecidedNew = card.person.decisionState == "UNDECIDED" &&
                                    !card.person.isNamedByUser &&
                                    !card.person.isHiddenToOther
                                if (isUndecidedNew) {
                                    initialUndecidedSet.add(id)
                                }
                                if (isPostOnboarding && isUndecidedNew && card.photoCount >= 2) {
                                    newlyAddedPriorityIds.add(id)
                                } else {
                                    lockedOrderIds.add(id)
                                }
                            }
                        }

                        if (newlyAddedPriorityIds.isNotEmpty()) {
                            // Insert post-onboarding undecided profiles (>= 2 photos) at the front of lockedOrderIds
                            val insertIndex = lockedOrderIds.indexOfFirst { existingId ->
                                existingId !in initialUndecidedSet || (initialPhotoCountMap[existingId] ?: 0) < 2
                            }.let { if (it < 0) lockedOrderIds.size else it }
                            lockedOrderIds.addAll(insertIndex, newlyAddedPriorityIds)
                        }

                        rawCards.sortedBy { card ->
                            val idx = lockedOrderIds.indexOf(card.person.personId)
                            if (idx >= 0) idx else Int.MAX_VALUE
                        }
                    }
                }
            }
        }

    private data class SetupConfigState(
        val editedNames: Map<Long, String>,
        val userFilterMode: SetupFilterMode?,
        val autoHideStrangers: Boolean,
        val quickChips: List<String>
    )

    private val setupConfigFlow = combine(
        _editedNames,
        _userSelectedFilterMode,
        _autoHideSinglePhotoStrangers,
        _quickNameChips
    ) { editedNames, userFilterMode, autoHideStrangers, quickChips ->
        SetupConfigState(
            editedNames = editedNames,
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

    private fun resolveDecisionFromEntity(person: PersonEntity): ProfileDecisionState {
        return when (person.decisionState) {
            "KEPT" -> ProfileDecisionState.KEPT
            "MOVED_TO_OTHER" -> ProfileDecisionState.MOVED_TO_OTHER
            else -> if (person.isHiddenToOther) ProfileDecisionState.MOVED_TO_OTHER
            else if (person.isNamedByUser) ProfileDecisionState.KEPT
            else ProfileDecisionState.UNDECIDED
        }
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
        val workerProfilesCount = metrics.workInfo?.progress?.getInt(FaceIndexingWorker.PROGRESS_PROFILES_COUNT, -1) ?: -1

        val effectiveTotal = if (metrics.total > 0) metrics.total else if (workerTotal > 0) workerTotal else 0
        val effectiveProcessed = maxOf(metrics.processed, if (workerProcessed >= 0) workerProcessed else 0)
            .coerceAtMost(effectiveTotal.coerceAtLeast(1))

        val isWorkerActive = workState == WorkInfo.State.RUNNING || workState == WorkInfo.State.ENQUEUED

        val isScanComplete = metrics.isMediaStoreReady && (
            (effectiveTotal > 0 && metrics.unindexed == 0 && !isWorkerActive) ||
                (effectiveTotal > 0 && metrics.unindexed == 0 && effectiveProcessed >= effectiveTotal) ||
                (effectiveTotal == 0 && !isWorkerActive)
            )

        // Single SQLite source of truth for decisions
        val resolvedDecisions = buildMap {
            for (card in allCards) {
                val p = card.person
                put(p.personId, resolveDecisionFromEntity(p))
            }
        }

        // Determine active FilterMode (default: TOP_PEOPLE when total profiles > 20, otherwise ALL_DETECTED)
        val effectiveFilterMode = config.userFilterMode ?: if (allCards.size > 20) {
            SetupFilterMode.TOP_PEOPLE
        } else {
            SetupFilterMode.ALL_DETECTED
        }

        val isPostOnboarding = OnboardingPreferences.isOnboardingCompleted(getApplication())

        val (allDetectedCards, topPeopleCards) = synchronized(orderLock) {
            val sortedAll = allCards
                .filter { it.photoCount >= 1 }
                .sortedBy { lockedOrderIds.indexOf(it.person.personId) }

            val hasThreePlusProfiles = sortedAll.count { card ->
                (initialPhotoCountMap[card.person.personId] ?: card.photoCount) >= 3
            } >= 3
            val minTopPhotos = if (hasThreePlusProfiles) 3 else 2

            val sortedTop = sortedAll
                .filter { card ->
                    val id = card.person.personId
                    val initialPhotos = initialPhotoCountMap[id] ?: card.photoCount
                    initialPhotos >= minTopPhotos ||
                        (isPostOnboarding && id in initialUndecidedSet && initialPhotos >= 2)
                }
                .sortedBy { lockedOrderIds.indexOf(it.person.personId) }

            sortedAll to sortedTop
        }

        val displayedCards = when (effectiveFilterMode) {
            SetupFilterMode.TOP_PEOPLE -> if (topPeopleCards.isNotEmpty()) topPeopleCards else allDetectedCards
            SetupFilterMode.ALL_DETECTED -> allDetectedCards
        }

        // Monotonic counter guard during active scanning so profilesCreated never flickers downward
        val rawVisibleDetectedCount = maxOf(
            allDetectedCards.size,
            if (workerProfilesCount >= 0) workerProfilesCount else 0
        )
        if (!isScanComplete) {
            if (rawVisibleDetectedCount > maxProfilesSeen) {
                maxProfilesSeen = rawVisibleDetectedCount
            }
        } else if (rawVisibleDetectedCount > 0 && maxProfilesSeen == 0) {
            maxProfilesSeen = rawVisibleDetectedCount
        }
        val monotonicProfilesCreated = if (!isScanComplete) {
            maxOf(maxProfilesSeen, rawVisibleDetectedCount)
        } else {
            rawVisibleDetectedCount.coerceAtLeast(allDetectedCards.size)
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
            profilesCreated = monotonicProfilesCreated,
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

            synchronized(orderLock) {
                mergedDeletedIds.add(sourcePersonId)
                lockedOrderIds.remove(sourcePersonId)
                initialPhotoCountMap.remove(sourcePersonId)
            }

            galleryRepository.mergeProfiles(
                targetId = targetPerson.personId,
                sourceIds = listOf(sourcePersonId),
                finalName = resolvedTargetName
            )
            galleryRepository.keepAndNamePerson(targetPerson.personId, resolvedTargetName)

            val updatedNames = _editedNames.value.toMutableMap()
            updatedNames.remove(sourcePersonId)
            updatedNames[targetPerson.personId] = resolvedTargetName
            _editedNames.value = updatedNames

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

            synchronized(orderLock) {
                mergedDeletedIds.add(lookalikePersonId)
                lockedOrderIds.remove(lookalikePersonId)
                initialPhotoCountMap.remove(lookalikePersonId)
            }

            galleryRepository.mergeProfiles(
                targetId = currentPerson.personId,
                sourceIds = listOf(lookalikePersonId),
                finalName = resolvedCurrentName
            )

            if (isCustomPersonName(resolvedCurrentName) ||
                currentPerson.isNamedByUser ||
                currentPerson.decisionState == "KEPT"
            ) {
                galleryRepository.keepAndNamePerson(currentPerson.personId, resolvedCurrentName)
            }

            val updatedNames = _editedNames.value.toMutableMap()
            updatedNames.remove(lookalikePersonId)
            updatedNames[currentPerson.personId] = resolvedCurrentName
            _editedNames.value = updatedNames

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

            synchronized(orderLock) {
                for (id in sourceIds) {
                    mergedDeletedIds.add(id)
                    lockedOrderIds.remove(id)
                    initialPhotoCountMap.remove(id)
                }
            }

            if (sourceIds.isNotEmpty()) {
                galleryRepository.mergeProfiles(
                    targetId = primaryId,
                    sourceIds = sourceIds,
                    finalName = cleanName
                )
            }
            galleryRepository.keepAndNamePerson(primaryId, cleanName)

            val updatedNames = _editedNames.value.toMutableMap()
            for (id in sourceIds) {
                updatedNames.remove(id)
            }
            updatedNames[primaryId] = cleanName
            _editedNames.value = updatedNames

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

        prependCustomQuickChip(cleanName)

        viewModelScope.launch {
            galleryRepository.keepAndNamePerson(personId, cleanName)
            if (_similarCandidatesForPersonId.value == personId) {
                _similarCandidates.value = galleryRepository.getSimilarProfilesFor(personId, limit = 8)
            }
        }
    }

    fun toggleMoveToOther(personId: Long) {
        viewModelScope.launch {
            val dbPerson = galleryRepository.getPersonByIdSync(personId)
                ?: uiState.value.allDetectedPeople.firstOrNull { it.person.personId == personId }?.person

            val isCurrentlyMovedToOther = dbPerson?.decisionState == "MOVED_TO_OTHER" ||
                (dbPerson?.decisionState != "KEPT" && dbPerson?.isHiddenToOther == true)

            if (isCurrentlyMovedToOther) {
                galleryRepository.restorePersonFromOtherSql(personId)
            } else {
                galleryRepository.movePersonToOtherSql(personId)
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

    /**
     * Called when opening ProfileSetupScreen after initial onboarding is already completed.
     * Places currently UNDECIDED profiles with photoCount >= 2 at the front of lockedOrderIds once
     * before the review session begins, while keeping slot order locked during the session.
     */
    fun prepareForPostOnboardingReview() {
        if (!OnboardingPreferences.isOnboardingCompleted(getApplication())) return
        val currentCards = uiState.value.allDetectedPeople
        synchronized(orderLock) {
            initialUndecidedSet.clear()
            for (card in currentCards) {
                val p = card.person
                if (p.decisionState == "UNDECIDED" && !p.isNamedByUser && !p.isHiddenToOther) {
                    initialUndecidedSet.add(p.personId)
                }
                initialPhotoCountMap[p.personId] = maxOf(initialPhotoCountMap[p.personId] ?: 0, card.photoCount)
            }
            if (lockedOrderIds.isNotEmpty()) {
                val priorityUndecided = lockedOrderIds.filter { id ->
                    id in initialUndecidedSet && (initialPhotoCountMap[id] ?: 0) >= 2
                }
                val remaining = lockedOrderIds.filterNot { id ->
                    id in initialUndecidedSet && (initialPhotoCountMap[id] ?: 0) >= 2
                }
                lockedOrderIds.clear()
                lockedOrderIds.addAll(priorityUndecided)
                lockedOrderIds.addAll(remaining)
            }
        }
    }

    fun triggerFullRescan() {
        maxProfilesSeen = 0
        synchronized(orderLock) {
            lockedOrderIds.clear()
            initialPhotoCountMap.clear()
            initialUndecidedSet.clear()
            mergedDeletedIds.clear()
        }
        _hasStartedScan.value = true
        _isMediaStoreScanFinished.value = false
        _editedNames.value = emptyMap()
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

        val currentDecision = uiState.value.decisionMap[personId]
        if (newName.isNotBlank() && currentDecision == ProfileDecisionState.MOVED_TO_OTHER) {
            viewModelScope.launch {
                galleryRepository.keepAndNamePerson(personId, newName)
            }
        } else if (isCustomPersonName(newName)) {
            prependCustomQuickChip(newName)
        }
    }

    fun toggleUnknownStatus(personId: Long) {
        toggleMoveToOther(personId)
    }

    fun setPersonUnknownStatus(personId: Long, isUnknown: Boolean, defaultName: String = "") {
        viewModelScope.launch {
            if (isUnknown) {
                galleryRepository.movePersonToOtherSql(personId)
            } else {
                val existingName = _editedNames.value[personId]?.takeIf { it.isNotBlank() }
                    ?: defaultName.takeIf { it.isNotBlank() }
                    ?: galleryRepository.getPersonByIdSync(personId)?.name
                    ?: "Person $personId"
                val names = _editedNames.value.toMutableMap()
                names[personId] = existingName
                _editedNames.value = names
                galleryRepository.keepAndNamePerson(personId, existingName)
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

        viewModelScope.launch {
            if (allAlreadyMoved) {
                for (id in singlePhotoIds) {
                    galleryRepository.restorePersonFromOtherSql(id)
                }
            } else {
                for (id in singlePhotoIds) {
                    galleryRepository.movePersonToOtherSql(id)
                }
            }
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
        // profiles aren't falsely marked as user-named unless the user typed a custom name or kept them.
        val activeNamesToApply = buildMap {
            for (card in state.allDetectedPeople) {
                val id = card.person.personId
                if (unknowns.contains(id)) continue
                val decision = decisions[id] ?: ProfileDecisionState.UNDECIDED
                val editedName = (namesToSave[id] ?: card.person.name).trim()
                if (decision == ProfileDecisionState.KEPT || isCustomPersonName(editedName) || card.person.isNamedByUser) {
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
