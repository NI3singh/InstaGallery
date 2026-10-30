// InstaGallery v2.0
package com.example.ui.screens.people

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.OnboardingPreferences
import com.example.data.local.entity.PersonEntity
import com.example.data.repository.GalleryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PeopleSortOrder(val label: String) {
    MOST_POSTS("Most Posts"),
    NAME_ASC("Name (A–Z)"),
    NEWEST("Recently Added")
}

enum class PeopleFilterTab(val label: String) {
    ALL("All"),
    NAMED("Named"),
    UNNAMED("Unnamed"),
    HIDDEN_OTHER("Hidden (@Other)")
}

data class PeopleUiState(
    val people: List<PersonEntity> = emptyList(),
    val hiddenPeople: List<PersonEntity> = emptyList(),
    val filteredPeople: List<PersonEntity> = emptyList(),
    val newUndecidedPeople: List<PersonEntity> = emptyList(),
    val unindexedPhotoCount: Int = 0,
    val searchQuery: String = "",
    val filterTab: PeopleFilterTab = PeopleFilterTab.ALL,
    val sortOrder: PeopleSortOrder = PeopleSortOrder.MOST_POSTS,
    val isSelectionMode: Boolean = false,
    val selectedPersonIds: Set<Long> = emptySet(),
    val duplicatePair: Pair<PersonEntity, PersonEntity>? = null,
    val unassociatedCount: Int = 0,
    val myProfilePersonId: Long = -1L,
    val toastMessage: String? = null
)

class PeopleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)

    init {
        OnboardingPreferences.init(application)
        viewModelScope.launch {
            repository.runAutoDeduplication()
        }
    }

    fun runAutoDeduplication() {
        viewModelScope.launch {
            repository.runAutoDeduplication()
        }
    }

    private val _searchQuery = MutableStateFlow("")
    private val _filterTab = MutableStateFlow(PeopleFilterTab.ALL)
    private val _sortOrder = MutableStateFlow(PeopleSortOrder.MOST_POSTS)
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedPersonIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _ignoredDuplicateKeys = MutableStateFlow(
        OnboardingPreferences.getDismissedDuplicatePairs(application)
    )
    private val _toastMessage = MutableStateFlow<String?>(null)

    private data class FilterSelectionTuple(
        val query: String,
        val filterTab: PeopleFilterTab,
        val sort: PeopleSortOrder,
        val isSelecting: Boolean,
        val selected: Set<Long>,
        val ignoredKeys: Set<String>
    )

    private val filterAndSelectionState = combine(
        combine(_searchQuery, _filterTab, _sortOrder) { q, tab, s -> Triple(q, tab, s) },
        combine(
            _isSelectionMode,
            _selectedPersonIds,
            combine(_ignoredDuplicateKeys, OnboardingPreferences.dismissedDuplicatePairsFlow) { a, b -> a + b }
        ) { selMode, selIds, ignored ->
            Triple(selMode, selIds, ignored)
        }
    ) { (query, tab, sort), (isSelecting, selected, ignored) ->
        FilterSelectionTuple(query, tab, sort, isSelecting, selected, ignored)
    }

    private data class CountsAndToast(
        val unassociatedCount: Int,
        val unindexedPhotoCount: Int,
        val newUndecidedPeople: List<PersonEntity>,
        val myProfilePersonId: Long,
        val toastMessage: String?
    )

    private val countsAndToastFlow = combine(
        repository.unassociatedCount,
        repository.unindexedPhotoCount,
        repository.newUndecidedPeople,
        OnboardingPreferences.myProfilePersonIdFlow,
        _toastMessage
    ) { unassociated, unindexed, newUndecided, myId, toast ->
        CountsAndToast(
            unassociatedCount = unassociated,
            unindexedPhotoCount = unindexed,
            newUndecidedPeople = newUndecided,
            myProfilePersonId = myId,
            toastMessage = toast
        )
    }

    val uiState: StateFlow<PeopleUiState> = combine(
        repository.allPeople,
        repository.allPeopleIncludeHidden,
        filterAndSelectionState,
        countsAndToastFlow
    ) { visiblePeople, allPeopleWithHidden, state, extras ->
        val hiddenList = allPeopleWithHidden.filter { it.isHiddenToOther && it.faceCount > 0 }

        val tabSource = when (state.filterTab) {
            PeopleFilterTab.ALL -> visiblePeople
            PeopleFilterTab.NAMED -> visiblePeople.filter { person ->
                person.isNamedByUser || !person.name.matches(Regex("^Person\\s+\\d+$", RegexOption.IGNORE_CASE))
            }
            PeopleFilterTab.UNNAMED -> visiblePeople.filter { person ->
                !person.isNamedByUser && person.name.matches(Regex("^Person\\s+\\d+$", RegexOption.IGNORE_CASE))
            }
            PeopleFilterTab.HIDDEN_OTHER -> hiddenList
        }

        val searched = if (state.query.isBlank()) {
            tabSource
        } else {
            tabSource.filter { it.name.contains(state.query.trim(), ignoreCase = true) }
        }

        val sorted = when (state.sort) {
            PeopleSortOrder.MOST_POSTS -> searched.sortedByDescending { it.faceCount }
            PeopleSortOrder.NAME_ASC -> searched.sortedBy { it.name.lowercase() }
            PeopleSortOrder.NEWEST -> searched.sortedByDescending { it.createdAt }
        }

        val duplicate = repository.findPossibleDuplicatePair(visiblePeople, state.ignoredKeys)

        PeopleUiState(
            people = visiblePeople,
            hiddenPeople = hiddenList,
            filteredPeople = sorted,
            newUndecidedPeople = extras.newUndecidedPeople,
            unindexedPhotoCount = extras.unindexedPhotoCount,
            searchQuery = state.query,
            filterTab = state.filterTab,
            sortOrder = state.sort,
            isSelectionMode = state.isSelecting,
            selectedPersonIds = state.selected,
            duplicatePair = duplicate,
            unassociatedCount = extras.unassociatedCount,
            myProfilePersonId = extras.myProfilePersonId,
            toastMessage = extras.toastMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PeopleUiState()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun setFilterTab(tab: PeopleFilterTab) {
        _filterTab.value = tab
    }

    fun cycleSortOrder() {
        val next = when (_sortOrder.value) {
            PeopleSortOrder.MOST_POSTS -> PeopleSortOrder.NAME_ASC
            PeopleSortOrder.NAME_ASC -> PeopleSortOrder.NEWEST
            PeopleSortOrder.NEWEST -> PeopleSortOrder.MOST_POSTS
        }
        _sortOrder.value = next
        _toastMessage.value = "Sorted by: ${next.label}"
    }

    fun toggleSelectionMode() {
        val willBeSelecting = !_isSelectionMode.value
        _isSelectionMode.value = willBeSelecting
        if (!willBeSelecting) {
            _selectedPersonIds.value = emptySet()
        }
    }

    fun togglePersonSelection(personId: Long) {
        val current = _selectedPersonIds.value.toMutableSet()
        if (current.contains(personId)) {
            current.remove(personId)
        } else {
            current.add(personId)
        }
        _selectedPersonIds.value = current
        if (current.isNotEmpty() && !_isSelectionMode.value) {
            _isSelectionMode.value = true
        } else if (current.isEmpty() && _isSelectionMode.value) {
            _isSelectionMode.value = false
        }
    }

    fun reviewDuplicate(pair: Pair<PersonEntity, PersonEntity>) {
        _isSelectionMode.value = true
        _selectedPersonIds.value = setOf(pair.first.personId, pair.second.personId)
    }

    fun mergeDuplicatePairDirectly(pair: Pair<PersonEntity, PersonEntity>) {
        val target = if (pair.first.isNamedByUser || pair.first.faceCount >= pair.second.faceCount) {
            pair.first
        } else {
            pair.second
        }
        val source = if (target.personId == pair.first.personId) pair.second else pair.first
        viewModelScope.launch {
            repository.mergePeople(
                sourceIds = listOf(source.personId),
                targetId = target.personId,
                newName = target.name
            )
            if (OnboardingPreferences.getMyProfilePersonId(getApplication()) == source.personId) {
                OnboardingPreferences.setMyProfilePersonId(getApplication(), target.personId)
            }
            _toastMessage.value = "Merged into @${target.name.removePrefix("@")}"
        }
    }

    fun ignoreDuplicatePair(pair: Pair<PersonEntity, PersonEntity>) {
        val key = repository.pairKey(pair.first.personId, pair.second.personId)
        _ignoredDuplicateKeys.value = _ignoredDuplicateKeys.value + key
        OnboardingPreferences.addDismissedDuplicatePair(getApplication(), key)
    }

    fun mergeSelectedPeople(targetName: String) {
        val selected = _selectedPersonIds.value.toList()
        if (selected.size < 2) return
        mergePeopleIds(selected, targetName)
    }

    fun mergePeopleIds(personIds: List<Long>, targetName: String) {
        if (personIds.size < 2) return
        val primaryTargetId = personIds.first()
        val sourceIds = personIds.drop(1)

        viewModelScope.launch {
            repository.mergePeople(sourceIds, primaryTargetId, targetName)
            val currentMyId = OnboardingPreferences.getMyProfilePersonId(getApplication())
            if (currentMyId in sourceIds) {
                OnboardingPreferences.setMyProfilePersonId(getApplication(), primaryTargetId)
            }
            _isSelectionMode.value = false
            _selectedPersonIds.value = emptySet()
            _toastMessage.value = "Merged into @${targetName.removePrefix("@")} successfully!"
        }
    }

    fun setAsMyProfile(person: PersonEntity) {
        OnboardingPreferences.setMyProfilePersonId(getApplication(), person.personId)
        _toastMessage.value = "@${person.name.removePrefix("@")} set as your primary profile"
    }

    fun renamePerson(personId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renamePerson(personId, newName.trim())
            _toastMessage.value = "Renamed to @${newName.trim().removePrefix("@")}"
        }
    }

    fun movePersonToOther(person: PersonEntity) {
        viewModelScope.launch {
            repository.movePersonToOther(person.personId)
            _toastMessage.value = "Moved @${person.name.removePrefix("@")} to @Other"
        }
    }

    fun restorePersonFromOther(person: PersonEntity) {
        viewModelScope.launch {
            repository.restorePersonFromOtherSql(person.personId)
            _toastMessage.value = "Restored @${person.name.removePrefix("@")} from @Other"
        }
    }

    fun dismissToast() {
        _toastMessage.value = null
    }
}
