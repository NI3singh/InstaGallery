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

data class PeopleUiState(
    val people: List<PersonEntity> = emptyList(),
    val filteredPeople: List<PersonEntity> = emptyList(),
    val searchQuery: String = "",
    val isSelectionMode: Boolean = false,
    val selectedPersonIds: Set<Long> = emptySet(),
    val duplicatePair: Pair<PersonEntity, PersonEntity>? = null,
    val unassociatedCount: Int = 0,
    val toastMessage: String? = null
)

class PeopleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)

    private val _searchQuery = MutableStateFlow("")
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedPersonIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _toastMessage = MutableStateFlow<String?>(null)

    private val selectionState = combine(
        _isSelectionMode,
        _selectedPersonIds
    ) { isSelecting, selected ->
        Pair(isSelecting, selected)
    }

    val uiState: StateFlow<PeopleUiState> = combine(
        repository.allPeople,
        _searchQuery,
        selectionState,
        repository.unassociatedCount,
        _toastMessage
    ) { people, query, sel, unassociated, toast ->
        val isSelecting = sel.first
        val selected = sel.second

        val filtered = if (query.isBlank()) {
            people
        } else {
            people.filter { it.name.contains(query, ignoreCase = true) }
        }

        val duplicate = repository.findPossibleDuplicatePair(people)

        PeopleUiState(
            people = people,
            filteredPeople = filtered,
            searchQuery = query,
            isSelectionMode = isSelecting,
            selectedPersonIds = selected,
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
        }
    }

    fun reviewDuplicate(pair: Pair<PersonEntity, PersonEntity>) {
        _isSelectionMode.value = true
        _selectedPersonIds.value = setOf(pair.first.personId, pair.second.personId)
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
