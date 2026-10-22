// InstaGallery v1.0
package com.example.data.repository

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.local.AppDatabase
import com.example.data.local.OnboardingPreferences
import com.example.data.local.entity.FaceEntity
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.local.entity.PersonEntity
import com.example.data.media.MediaStoreScanner
import com.example.ml.FaceClusterManager
import com.example.ml.SimilarProfileCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class GalleryRepository(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val mediaDao = database.mediaDao()
    private val personDao = database.personDao()
    private val scanner = MediaStoreScanner(context, mediaDao)
    private val clusterManager = FaceClusterManager(context, personDao, mediaDao)

    /**
     * Primary people Flow observed by HomeFeedScreen and PeopleDirectoryScreen.
     * Uses `personDao.getVisiblePeopleFlow()` (`isHiddenToOther = 0 AND (faceCount >= 2 OR isNamedByUser = 1)`),
     * falling back to `faceCount >= 1` ONLY if the entire device has 0 clusters with `faceCount >= 2 || isNamedByUser`.
     */
    val allPeople: Flow<List<PersonEntity>> = combine(
        personDao.getVisiblePeopleFlow(),
        personDao.getAllPeople()
    ) { visiblePeople, allActivePeople ->
        if (visiblePeople.isNotEmpty()) {
            visiblePeople
        } else {
            allActivePeople
        }
    }

    /**
     * All detected people with >= 1 photo (excluding hidden), used for tiered onboarding filtering.
     */
    val allDetectedPeople: Flow<List<PersonEntity>> = personDao.getAllPeople()

    /**
     * All detected people including those marked `isHiddenToOther = true` so onboarding `ALL_DETECTED`
     * mode can still show cards marked `MOVED_TO_OTHER` in the current session for 1-tap Undo.
     */
    val allPeopleIncludeHidden: Flow<List<PersonEntity>> = personDao.getAllPeopleIncludeHiddenFlow()

    private fun filterMediaAuthorsToVisible(
        mediaFlow: Flow<List<MediaWithAuthors>>
    ): Flow<List<MediaWithAuthors>> = combine(
        mediaFlow,
        allPeople
    ) { items, visiblePeople ->
        val visibleIds = visiblePeople.asSequence().map { it.personId }.toHashSet()
        items.map { item ->
            val filteredAuthors = item.authors.filter { visibleIds.contains(it.personId) }
            if (filteredAuthors.size == item.authors.size) {
                item
            } else {
                item.copy(authors = filteredAuthors)
            }
        }
    }

    // Observables
    val allMediaWithAuthors: Flow<List<MediaWithAuthors>> = filterMediaAuthorsToVisible(mediaDao.getAllMediaWithAuthors())
    val photosWithAuthors: Flow<List<MediaWithAuthors>> = filterMediaAuthorsToVisible(mediaDao.getPhotosWithAuthors())
    val videosWithAuthors: Flow<List<MediaWithAuthors>> = filterMediaAuthorsToVisible(mediaDao.getVideosWithAuthors())
    val favoritePhotosWithAuthors: Flow<List<MediaWithAuthors>> = filterMediaAuthorsToVisible(mediaDao.getFavoritePhotosWithAuthors())
    val bookmarkedPhotosWithAuthors: Flow<List<MediaWithAuthors>> = filterMediaAuthorsToVisible(mediaDao.getBookmarkedPhotosWithAuthors())

    val unindexedCount: Flow<Int> = mediaDao.getUnindexedCount()
    val processedCount: Flow<Int> = mediaDao.getProcessedCount()
    val totalMediaCount: Flow<Int> = mediaDao.getTotalMediaCount()
    val unassociatedCount: Flow<Int> = mediaDao.getUnassociatedPhotoCount()

    // Work progress
    val indexingWorkInfo: Flow<WorkInfo?> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(MediaStoreScanner.FACE_INDEXING_WORK_NAME)
        .map { it.firstOrNull() }

    suspend fun scanDeviceMedia(): Int {
        return scanner.scanAndReconcile()
    }

    fun getMediaById(mediaId: Long): Flow<MediaWithAuthors?> = combine(
        mediaDao.getMediaById(mediaId),
        allPeople
    ) { item, visiblePeople ->
        if (item == null) {
            null
        } else {
            val visibleIds = visiblePeople.asSequence().map { it.personId }.toHashSet()
            item.copy(authors = item.authors.filter { visibleIds.contains(it.personId) })
        }
    }

    fun getPersonById(personId: Long): Flow<PersonEntity?> = personDao.getPersonById(personId)

    /**
     * Strict photo-only query for a specific person (`personId > 0`) or `@Other` (`personId == -1L`).
     * Never falls back to random or unrelated gallery media.
     */
    fun getMediaWithAuthorsForPersonFlow(personId: Long): Flow<List<MediaWithAuthors>> {
        val rawFlow = if (personId == -1L) {
            mediaDao.getUnassignedPhotosWithAuthorsFlow()
        } else {
            mediaDao.getMediaWithAuthorsForPersonFlow(personId)
        }
        return filterMediaAuthorsToVisible(rawFlow)
    }

    fun getMediaForPerson(personId: Long): Flow<List<MediaWithAuthors>> {
        return getMediaWithAuthorsForPersonFlow(personId)
    }

    fun getPhotosForPerson(personId: Long): Flow<List<MediaWithAuthors>> {
        return getMediaWithAuthorsForPersonFlow(personId)
    }

    suspend fun getSamplePhotosForPersonSync(personId: Long, limit: Int = 3): List<com.example.data.local.entity.MediaEntity> {
        return mediaDao.getSamplePhotosForPersonSync(personId, limit)
    }

    fun getVideosForPerson(personId: Long): Flow<List<MediaWithAuthors>> {
        val rawFlow = if (personId == -1L) {
            mediaDao.getUnassociatedVideos()
        } else {
            mediaDao.getVideosForPerson(personId)
        }
        return filterMediaAuthorsToVisible(rawFlow)
    }

    fun getPhotoCountForPerson(personId: Long): Flow<Int> {
        return if (personId == -1L) {
            mediaDao.getUnassociatedPhotoCount()
        } else {
            personDao.getPhotoCountForPerson(personId)
        }
    }

    fun getReelCountForPerson(personId: Long): Flow<Int> {
        return if (personId == -1L) {
            mediaDao.getUnassociatedVideoCount()
        } else {
            personDao.getReelCountForPerson(personId)
        }
    }

    fun getTotalCountForPerson(personId: Long): Flow<Int> {
        return getPhotoCountForPerson(personId)
    }

    fun getFacesForPerson(personId: Long): Flow<List<FaceEntity>> = personDao.getFacesForPerson(personId)

    suspend fun toggleFavorite(mediaId: Long, currentFavorite: Boolean) {
        mediaDao.setFavorite(mediaId, !currentFavorite)
    }

    suspend fun setFavorite(mediaId: Long, isFavorite: Boolean) {
        mediaDao.setFavorite(mediaId, isFavorite)
    }

    suspend fun toggleBookmarked(mediaId: Long, currentBookmarked: Boolean) {
        mediaDao.setBookmarked(mediaId, !currentBookmarked)
    }

    suspend fun updatePersonName(personId: Long, rawName: String) = withContext(Dispatchers.IO) {
        val newName = rawName.trim().removePrefix("@").trim()
        if (newName.isBlank()) {
            personDao.hidePersonToOther(personId)
            clusterManager.invalidateCache()
            return@withContext
        }

        val allExisting = personDao.getAllPeopleIncludeHiddenSync()
        val matchingExisting = allExisting.firstOrNull {
            it.personId != personId &&
                !it.isHiddenToOther &&
                it.name.trim().removePrefix("@").trim().equals(newName, ignoreCase = true)
        }

        if (matchingExisting != null) {
            personDao.mergePeople(
                sourcePersonIds = listOf(personId),
                targetPersonId = matchingExisting.personId,
                newName = newName
            )
        } else {
            personDao.markPersonNamedByUser(personId, newName)
        }

        withContext(Dispatchers.Default) {
            clusterManager.runPass2ConsolidationAndAnchorSweep()
        }
    }

    suspend fun updateAvatar(personId: Long, cropPath: String) {
        personDao.updateAvatar(personId, cropPath)
    }

    suspend fun removeMediaFromPerson(mediaId: Long, personId: Long) {
        personDao.removeMediaFromPersonAndSyncCount(mediaId, personId)
    }

    suspend fun tagPersonInMedia(mediaId: Long, personId: Long) {
        personDao.tagPersonInMediaAndSyncCount(mediaId, personId)
    }

    suspend fun mergePeople(sourceIds: List<Long>, targetId: Long, newName: String) = withContext(Dispatchers.IO) {
        val cleanName = newName.trim().removePrefix("@").trim()
        personDao.mergePeople(sourceIds, targetId, cleanName)
        clusterManager.invalidateCache()
        withContext(Dispatchers.Default) {
            clusterManager.runPass2ConsolidationAndAnchorSweep()
        }
    }

    suspend fun mergeProfiles(
        targetId: Long,
        sourceIds: List<Long>,
        finalName: String
    ) = withContext(Dispatchers.IO) {
        val cleanName = finalName.trim().removePrefix("@").trim()
        personDao.mergePeople(
            sourcePersonIds = sourceIds.filter { it != targetId },
            targetPersonId = targetId,
            newName = cleanName
        )
        clusterManager.invalidateCache()
    }

    suspend fun renamePerson(personId: Long, rawName: String) = withContext(Dispatchers.IO) {
        val cleanName = rawName.trim().removePrefix("@").trim()
        if (cleanName.isBlank()) {
            personDao.hidePersonToOther(personId)
            clusterManager.invalidateCache()
            return@withContext
        }

        val allExisting = personDao.getAllPeopleIncludeHiddenSync()
        val matchingExisting = allExisting.firstOrNull {
            it.personId != personId &&
                !it.isHiddenToOther &&
                it.isNamedByUser &&
                it.name.trim().removePrefix("@").trim().equals(cleanName, ignoreCase = true)
        }

        if (matchingExisting != null) {
            personDao.mergePeople(
                sourcePersonIds = listOf(personId),
                targetPersonId = matchingExisting.personId,
                newName = cleanName
            )
        } else {
            val isDefaultPlaceholder = cleanName.matches(Regex("^Person\\s+\\d+$", RegexOption.IGNORE_CASE))
            if (isDefaultPlaceholder) {
                val existing = personDao.getPersonByIdSync(personId)
                if (existing != null) {
                    personDao.updatePerson(
                        existing.copy(
                            name = cleanName,
                            isHiddenToOther = false
                        )
                    )
                }
            } else {
                personDao.markPersonNamedByUser(personId, cleanName)
            }
        }
        clusterManager.invalidateCache()
    }

    suspend fun getSimilarProfilesFor(
        personId: Long,
        limit: Int = 8
    ): List<SimilarProfileCandidate> {
        return clusterManager.getSimilarProfilesFor(targetPersonId = personId, limit = limit)
    }

    suspend fun hidePersonToOther(personId: Long) = withContext(Dispatchers.IO) {
        personDao.hidePersonToOther(personId)
        clusterManager.invalidateCache()
    }

    suspend fun unhidePersonFromOther(personId: Long) = withContext(Dispatchers.IO) {
        personDao.unhidePersonFromOther(personId)
        clusterManager.invalidateCache()
    }

    suspend fun movePersonToOther(personId: Long) = withContext(Dispatchers.IO) {
        personDao.hidePersonToOther(personId)
        clusterManager.invalidateCache()
    }

    suspend fun applyProfileSetup(
        namedProfiles: Map<Long, String>,
        unknownPersonIds: Set<Long>
    ) = withContext(Dispatchers.IO) {
        for (unknownId in unknownPersonIds) {
            personDao.hidePersonToOther(unknownId)
        }

        val activeGroups = mutableMapOf<String, MutableList<Pair<Long, String>>>()
        for ((personId, rawName) in namedProfiles) {
            if (unknownPersonIds.contains(personId)) continue
            val cleanName = rawName.trim().removePrefix("@").trim()
            if (cleanName.isBlank()) {
                personDao.hidePersonToOther(personId)
                continue
            }
            val key = cleanName.lowercase()
            activeGroups.getOrPut(key) { mutableListOf() }.add(personId to cleanName)
        }

        val allExistingInDb = personDao.getAllPeopleIncludeHiddenSync().associateBy { it.personId }

        for ((normalizedKey, group) in activeGroups) {
            val externalMatch = allExistingInDb.values.firstOrNull { existing ->
                !existing.isHiddenToOther &&
                    !unknownPersonIds.contains(existing.personId) &&
                    group.none { it.first == existing.personId } &&
                    existing.name.trim().removePrefix("@").trim().lowercase() == normalizedKey
            }

            if (externalMatch != null) {
                val sourceIds = group.map { it.first }
                val targetName = group.first().second
                personDao.mergePeople(
                    sourcePersonIds = sourceIds,
                    targetPersonId = externalMatch.personId,
                    newName = targetName
                )
            } else if (group.size == 1) {
                val (personId, name) = group[0]
                personDao.markPersonNamedByUser(personId, name)
            } else if (group.size > 1) {
                val targetId = group[0].first
                val targetName = group[0].second
                val sourceIds = group.drop(1).map { it.first }
                personDao.mergePeople(sourceIds, targetId, targetName)
            }
        }

        withContext(Dispatchers.Default) {
            clusterManager.runPass2ConsolidationAndAnchorSweep()
        }

        OnboardingPreferences.markInitialScanCompleted(context, true)
        OnboardingPreferences.markProfileSetupCompleted(context, true)
    }

    suspend fun resetAndRescanAllFaces() = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork(MediaStoreScanner.FACE_INDEXING_WORK_NAME)
        mediaDao.clearAllCrossRefs()
        personDao.deleteAllFaces()
        personDao.deleteAllPeople()
        mediaDao.resetAllMediaProcessed()
        clusterManager.invalidateCache()

        val cropsDir = File(context.filesDir, "face_crops")
        if (cropsDir.exists()) {
            cropsDir.listFiles()?.forEach { it.delete() }
        }

        OnboardingPreferences.resetOnboardingForRescan(context)
        scanner.scanAndReconcile()
        scanner.enqueueFaceIndexingWorker(replace = true)
    }

    fun findPossibleDuplicatePair(
        people: List<PersonEntity>,
        ignoredPairKeys: Set<String> = emptySet()
    ): Pair<PersonEntity, PersonEntity>? {
        return clusterManager.findPossibleDuplicatePair(people, ignoredPairKeys)
    }

    fun pairKey(id1: Long, id2: Long): String = clusterManager.pairKey(id1, id2)
}
