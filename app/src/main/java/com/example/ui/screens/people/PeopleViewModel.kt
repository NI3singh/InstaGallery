package com.example.ui.screens.people

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

data class PeopleUiState(
    val people: List<PersonEntity> = emptyList(),
    val filteredPeople: List<PersonEntity> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: PeopleSortOrder = PeopleSortOrder.MOST_POSTS,
    val isSelectionMode: Boolean = false,
    val selectedPersonIds: Set<Long> = emptySet(),
    val duplicatePair: Pair<PersonEntity, PersonEntity>? = null,
    val unassociatedCount: Int = 0,
    val toastMessage: String? = null
)

class PeopleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)

    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(PeopleSortOrder.MOST_POSTS)
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedPersonIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _ignoredDuplicateKeys = MutableStateFlow<Set<String>>(emptySet())
    private val _toastMessage = MutableStateFlow<String?>(null)

    private val filterAndSelectionState = combine(
        _searchQuery,
        _sortOrder,
        _isSelectionMode,
        _selectedPersonIds,
        _ignoredDuplicateKeys
    ) { query, sort, isSelecting, selected, ignored ->
        FilterSelectionTuple(query, sort, isSelecting, selected, ignored)
    }

    private data class FilterSelectionTuple(
        val query: String,
        val sort: PeopleSortOrder,
        val isSelecting: Boolean,
        val selected: Set<Long>,
        val ignoredKeys: Set<String>
    )

    val uiState: StateFlow<PeopleUiState> = combine(
        repository.allPeople,
        filterAndSelectionState,
        repository.unassociatedCount,
        _toastMessage
    ) { people, state, unassociated, toast ->
        val searched = if (state.query.isBlank()) {
            people
        } else {
            people.filter { it.name.contains(state.query, ignoreCase = true) }
        }

        val sorted = when (state.sort) {
            PeopleSortOrder.MOST_POSTS -> searched.sortedByDescending { it.faceCount }
            PeopleSortOrder.NAME_ASC -> searched.sortedBy { it.name.lowercase() }
            PeopleSortOrder.NEWEST -> searched.sortedByDescending { it.createdAt }
        }

        val duplicate = repository.findPossibleDuplicatePair(people, state.ignoredKeys)

        PeopleUiState(
            people = people,
            filteredPeople = sorted,
            searchQuery = state.query,
            sortOrder = state.sort,
            isSelectionMode = state.isSelecting,
            selectedPersonIds = state.selected,
            duplicatePair = duplicate,
            unassociatedCount = unassociated,
            toastMessage = toast
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PeopleUiState()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
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

    fun ignoreDuplicatePair(pair: Pair<PersonEntity, PersonEntity>) {
        val key = repository.pairKey(pair.first.personId, pair.second.personId)
        _ignoredDuplicateKeys.value = _ignoredDuplicateKeys.value + key
    }

    fun mergeSelectedPeople(targetName: String) {
        val selected = _selectedPersonIds.value.toList()
        if (selected.size < 2) return

        val primaryTargetId = selected[0]
        val sourceIds = selected.drop(1)

        viewModelScope.launch {
            repository.mergePeople(sourceIds, primaryTargetId, targetName)
            _isSelectionMode.value = false
            _selectedPersonIds.value = emptySet()
            _toastMessage.value = "Merged into @$targetName successfully!"
        }
    }

    fun dismissToast() {
        _toastMessage.value = null
    }
}
