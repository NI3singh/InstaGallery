// InstaGallery v1.0
package com.example.ui.screens.story

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.OnboardingPreferences
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.PersonEntity
import com.example.data.repository.GalleryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class StoryMemoryBadge(val label: String) {
    ON_THIS_DAY("On This Day"),
    THROWBACK("Throwback Memory"),
    FAVORITE("Favorite"),
    RECENT("Recent Capture"),
    FEATURED("Highlight")
}

data class StorySlide(
    val media: MediaEntity,
    val badge: StoryMemoryBadge,
    val formattedDate: String
)

data class PersonStoryDeck(
    val personId: Long,
    val handleName: String,
    val avatarPath: String?,
    val slides: List<StorySlide>
)

data class StoryUiState(
    val decks: List<PersonStoryDeck> = emptyList(),
    val currentDeckIndex: Int = 0,
    val currentSlideIndex: Int = 0,
    val isLoading: Boolean = true
) {
    val currentDeck: PersonStoryDeck?
        get() = decks.getOrNull(currentDeckIndex)

    val currentSlide: StorySlide?
        get() = currentDeck?.slides?.getOrNull(currentSlideIndex)
}

class StoryPlayerViewModel(
    application: Application,
    private val initialPersonId: Long
) : AndroidViewModel(application) {

    companion object {
        private const val MAX_SLIDES = 12
        private const val BURST_COOLDOWN_SECONDS = 15L
        private const val FOURTEEN_DAYS_SECONDS = 14L * 24L * 3600L
    }

    private val repository = GalleryRepository(application)

    private val _uiState = MutableStateFlow(StoryUiState(isLoading = true))
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()

    init {
        OnboardingPreferences.init(application)
        loadCuratedStoryDecks()
    }

    private fun loadCuratedStoryDecks() {
        viewModelScope.launch {
            val builtDecks = withContext(Dispatchers.IO) {
                val visiblePeople = repository.getVisiblePeopleFlow().first()
                val decks = mutableListOf<PersonStoryDeck>()

                // 1. Build story decks for each visible person
                for (person in visiblePeople) {
                    val mediaWithAuthors = repository.getMediaWithAuthorsForPersonFlow(person.personId).first()
                    val stillPhotos = mediaWithAuthors
                        .asSequence()
                        .map { it.media }
                        .filter { !it.isVideo }
                        .distinctBy { it.mediaId }
                        .toList()

                    if (stillPhotos.isNotEmpty()) {
                        val slides = curateSlidesForPhotos(stillPhotos)
                        if (slides.isNotEmpty()) {
                            val cleanName = person.name.trim().removePrefix("@").trim()
                            val handle = if (person.isNamedByUser && !cleanName.startsWith("Person ", ignoreCase = true)) {
                                "@$cleanName"
                            } else {
                                "@$cleanName"
                            }
                            decks.add(
                                PersonStoryDeck(
                                    personId = person.personId,
                                    handleName = handle,
                                    avatarPath = person.avatarCropPath,
                                    slides = slides
                                )
                            )
                        }
                    }
                }

                // 2. Append @Other (personId = -1L) deck at the end if unassigned photos exist
                val unassignedMedia = repository.getMediaWithAuthorsForPersonFlow(-1L).first()
                val unassignedPhotos = unassignedMedia
                    .asSequence()
                    .map { it.media }
                    .filter { !it.isVideo }
                    .distinctBy { it.mediaId }
                    .toList()

                if (unassignedPhotos.isNotEmpty()) {
                    val otherSlides = curateSlidesForPhotos(unassignedPhotos)
                    if (otherSlides.isNotEmpty()) {
                        decks.add(
                            PersonStoryDeck(
                                personId = -1L,
                                handleName = "@Other",
                                avatarPath = null,
                                slides = otherSlides
                            )
                        )
                    }
                }

                // If initialPersonId was requested and isn't in visiblePeople (e.g. opened directly), ensure it's included
                if (decks.none { it.personId == initialPersonId } && initialPersonId > 0L) {
                    val targetPerson: PersonEntity? = repository.getPersonByIdSync(initialPersonId)
                    val targetPhotos = repository.getMediaWithAuthorsForPersonFlow(initialPersonId).first()
                        .map { it.media }
                        .filter { !it.isVideo }
                        .distinctBy { it.mediaId }
                    if (targetPhotos.isNotEmpty()) {
                        val slides = curateSlidesForPhotos(targetPhotos)
                        if (slides.isNotEmpty()) {
                            val cleanName = targetPerson?.name?.trim()?.removePrefix("@")?.trim() ?: "Person $initialPersonId"
                            decks.add(
                                0,
                                PersonStoryDeck(
                                    personId = initialPersonId,
                                    handleName = "@$cleanName",
                                    avatarPath = targetPerson?.avatarCropPath,
                                    slides = slides
                                )
                            )
                        }
                    }
                }

                decks
            }

            val startDeckIndex = builtDecks.indexOfFirst { it.personId == initialPersonId }
                .takeIf { it >= 0 } ?: 0

            builtDecks.getOrNull(startDeckIndex)?.let { activeDeck ->
                OnboardingPreferences.markStoryViewed(activeDeck.personId)
            }

            _uiState.value = StoryUiState(
                decks = builtDecks,
                currentDeckIndex = startDeckIndex,
                currentSlideIndex = 0,
                isLoading = false
            )
        }
    }

    /**
     * Curates up to MAX_SLIDES (12) still photos in deterministic priority order:
     * 1. ON_THIS_DAY (previous year, same month, within ±5 days)
     * 2. THROWBACK (previous year, exact same month)
     * 3. FAVORITE (media.isFavorite == true)
     * 4. RECENT (captured within last 14 days)
     * 5. FEATURED (interleaved recent + older memories, avoiding burst duplicates within 15s unless total < 12)
     */
    private fun curateSlidesForPhotos(photos: List<MediaEntity>): List<StorySlide> {
        if (photos.isEmpty()) return emptyList()

        val sortedDesc = photos.sortedByDescending { it.dateAddedSeconds }
        val nowCal = Calendar.getInstance()
        val currentYear = nowCal.get(Calendar.YEAR)
        val currentMonth = nowCal.get(Calendar.MONTH)
        val currentDay = nowCal.get(Calendar.DAY_OF_MONTH)
        val nowSeconds = System.currentTimeMillis() / 1000L

        val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val photoCal = Calendar.getInstance()

        fun classifyPhoto(media: MediaEntity): StoryMemoryBadge {
            photoCal.timeInMillis = media.dateAddedSeconds * 1000L
            val y = photoCal.get(Calendar.YEAR)
            val m = photoCal.get(Calendar.MONTH)
            val d = photoCal.get(Calendar.DAY_OF_MONTH)

            if (y < currentYear && m == currentMonth) {
                if (abs(d - currentDay) <= 5) {
                    return StoryMemoryBadge.ON_THIS_DAY
                }
                return StoryMemoryBadge.THROWBACK
            }
            if (media.isFavorite) {
                return StoryMemoryBadge.FAVORITE
            }
            if (nowSeconds - media.dateAddedSeconds in 0..FOURTEEN_DAYS_SECONDS) {
                return StoryMemoryBadge.RECENT
            }
            return StoryMemoryBadge.FEATURED
        }

        val onThisDayBucket = mutableListOf<MediaEntity>()
        val throwbackBucket = mutableListOf<MediaEntity>()
        val favoriteBucket = mutableListOf<MediaEntity>()
        val recentBucket = mutableListOf<MediaEntity>()
        val featuredRawBucket = mutableListOf<MediaEntity>()

        for (photo in sortedDesc) {
            when (classifyPhoto(photo)) {
                StoryMemoryBadge.ON_THIS_DAY -> onThisDayBucket.add(photo)
                StoryMemoryBadge.THROWBACK -> throwbackBucket.add(photo)
                StoryMemoryBadge.FAVORITE -> favoriteBucket.add(photo)
                StoryMemoryBadge.RECENT -> recentBucket.add(photo)
                StoryMemoryBadge.FEATURED -> featuredRawBucket.add(photo)
            }
        }

        // Interleave featuredRawBucket (newer + older memories) so the story feels like a journey
        val interleavedFeatured = mutableListOf<MediaEntity>()
        var left = 0
        var right = featuredRawBucket.lastIndex
        var takeFromNewest = true
        while (left <= right) {
            if (takeFromNewest) {
                interleavedFeatured.add(featuredRawBucket[left])
                left++
            } else {
                interleavedFeatured.add(featuredRawBucket[right])
                right--
            }
            takeFromNewest = !takeFromNewest
        }

        val orderedCandidates = buildList {
            addAll(onThisDayBucket)
            addAll(throwbackBucket)
            addAll(favoriteBucket)
            addAll(recentBucket)
            addAll(interleavedFeatured)
        }

        val selected = mutableListOf<MediaEntity>()
        val selectedIds = HashSet<Long>()
        val enforceBurstSpacing = photos.size >= MAX_SLIDES

        // Pass 1: Respect 15-second burst spacing so we don't pick 12 identical burst shots
        for (candidate in orderedCandidates) {
            if (selected.size >= MAX_SLIDES) break
            if (selectedIds.contains(candidate.mediaId)) continue

            val tooCloseToExisting = enforceBurstSpacing && selected.any { existing ->
                abs(existing.dateAddedSeconds - candidate.dateAddedSeconds) < BURST_COOLDOWN_SECONDS
            }
            if (!tooCloseToExisting) {
                selected.add(candidate)
                selectedIds.add(candidate.mediaId)
            }
        }

        // Pass 2: If fewer than MAX_SLIDES were selected and more photos exist, fill remaining slots
        if (selected.size < MAX_SLIDES && selected.size < orderedCandidates.size) {
            for (candidate in orderedCandidates) {
                if (selected.size >= MAX_SLIDES) break
                if (selectedIds.add(candidate.mediaId)) {
                    selected.add(candidate)
                }
            }
        }

        return selected.map { media ->
            StorySlide(
                media = media,
                badge = classifyPhoto(media),
                formattedDate = dateFormatter.format(Date(media.dateAddedSeconds * 1000L))
            )
        }
    }

    fun nextSlide(onAllFinished: () -> Unit) {
        val state = _uiState.value
        val activeDeck = state.currentDeck
        if (activeDeck == null) {
            onAllFinished()
            return
        }

        if (state.currentSlideIndex + 1 < activeDeck.slides.size) {
            _uiState.update { it.copy(currentSlideIndex = it.currentSlideIndex + 1) }
        } else if (state.currentDeckIndex + 1 < state.decks.size) {
            val nextDeckIdx = state.currentDeckIndex + 1
            state.decks.getOrNull(nextDeckIdx)?.let { nextDeck ->
                OnboardingPreferences.markStoryViewed(nextDeck.personId)
            }
            _uiState.update {
                it.copy(
                    currentDeckIndex = nextDeckIdx,
                    currentSlideIndex = 0
                )
            }
        } else {
            onAllFinished()
        }
    }

    fun previousSlide() {
        val state = _uiState.value
        if (state.currentSlideIndex > 0) {
            _uiState.update { it.copy(currentSlideIndex = it.currentSlideIndex - 1) }
        } else if (state.currentDeckIndex > 0) {
            val prevDeckIdx = state.currentDeckIndex - 1
            val prevDeck = state.decks[prevDeckIdx]
            OnboardingPreferences.markStoryViewed(prevDeck.personId)
            _uiState.update {
                it.copy(
                    currentDeckIndex = prevDeckIdx,
                    currentSlideIndex = (prevDeck.slides.size - 1).coerceAtLeast(0)
                )
            }
        } else {
            // Restart first slide of first deck
            _uiState.update { it.copy(currentSlideIndex = 0) }
        }
    }

    fun jumpToDeck(index: Int) {
        val state = _uiState.value
        if (index in state.decks.indices) {
            OnboardingPreferences.markStoryViewed(state.decks[index].personId)
            _uiState.update {
                it.copy(
                    currentDeckIndex = index,
                    currentSlideIndex = 0
                )
            }
        }
    }

    fun toggleFavorite(media: MediaEntity) {
        val newFav = !media.isFavorite
        updateMediaInDecks(media.mediaId) { it.copy(isFavorite = newFav) }
        viewModelScope.launch {
            repository.setFavorite(media.mediaId, newFav)
        }
    }

    fun setFavoriteTrue(media: MediaEntity) {
        if (!media.isFavorite) {
            updateMediaInDecks(media.mediaId) { it.copy(isFavorite = true) }
            viewModelScope.launch {
                repository.setFavorite(media.mediaId, true)
            }
        }
    }

    fun updateCaption(mediaId: Long, caption: String?) {
        val cleanCaption = caption?.trim()?.takeIf { it.isNotEmpty() }
        updateMediaInDecks(mediaId) { it.copy(customCaption = cleanCaption) }
        viewModelScope.launch {
            repository.updateMediaCaption(mediaId, cleanCaption)
        }
    }

    private fun updateMediaInDecks(mediaId: Long, transform: (MediaEntity) -> MediaEntity) {
        _uiState.update { state ->
            val updatedDecks = state.decks.map { deck ->
                val updatedSlides = deck.slides.map { slide ->
                    if (slide.media.mediaId == mediaId) {
                        slide.copy(media = transform(slide.media))
                    } else {
                        slide
                    }
                }
                deck.copy(slides = updatedSlides)
            }
            state.copy(decks = updatedDecks)
        }
    }
}
