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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class GalleryRepository(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val mediaDao = database.mediaDao()
    private val personDao = database.personDao()
    private val scanner = MediaStoreScanner(context, mediaDao)
    private val clusterManager = FaceClusterManager(context, personDao, mediaDao)

    // Observables
    val allMediaWithAuthors: Flow<List<MediaWithAuthors>> = mediaDao.getAllMediaWithAuthors()
    val photosWithAuthors: Flow<List<MediaWithAuthors>> = mediaDao.getPhotosWithAuthors()
    val videosWithAuthors: Flow<List<MediaWithAuthors>> = mediaDao.getVideosWithAuthors()
    val favoritePhotosWithAuthors: Flow<List<MediaWithAuthors>> = mediaDao.getFavoritePhotosWithAuthors()
    val bookmarkedPhotosWithAuthors: Flow<List<MediaWithAuthors>> = mediaDao.getBookmarkedPhotosWithAuthors()
    val allPeople: Flow<List<PersonEntity>> = personDao.getAllPeople()
    val unindexedCount: Flow<Int> = mediaDao.getUnindexedCount()
    val processedCount: Flow<Int> = mediaDao.getProcessedCount()
    val totalMediaCount: Flow<Int> = mediaDao.getTotalMediaCount()
    val unassociatedCount: Flow<Int> = mediaDao.getUnassociatedCount()

    // Work progress
    val indexingWorkInfo: Flow<WorkInfo?> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(MediaStoreScanner.FACE_INDEXING_WORK_NAME)
        .map { it.firstOrNull() }

    suspend fun scanDeviceMedia(): Int {
        return scanner.scanAndReconcile()
    }

    fun getMediaById(mediaId: Long): Flow<MediaWithAuthors?> = mediaDao.getMediaById(mediaId)

    fun getPersonById(personId: Long): Flow<PersonEntity?> = personDao.getPersonById(personId)

    fun getMediaForPerson(personId: Long): Flow<List<MediaWithAuthors>> {
        return if (personId == -1L) {
            mediaDao.getUnassociatedMedia()
        } else {
            mediaDao.getMediaForPerson(personId)
        }
    }

    fun getPhotosForPerson(personId: Long): Flow<List<MediaWithAuthors>> {
        return if (personId == -1L) {
            mediaDao.getUnassociatedPhotos()
        } else {
            mediaDao.getPhotosForPerson(personId)
        }
    }

    fun getVideosForPerson(personId: Long): Flow<List<MediaWithAuthors>> {
        return if (personId == -1L) {
            mediaDao.getUnassociatedVideos()
        } else {
            mediaDao.getVideosForPerson(personId)
        }
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
        return if (personId == -1L) {
            mediaDao.getUnassociatedCount()
        } else {
            personDao.getTotalCountForPerson(personId)
        }
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

    suspend fun updatePersonName(personId: Long, name: String) {
        personDao.updatePersonName(personId, name)
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

    suspend fun mergePeople(sourceIds: List<Long>, targetId: Long, newName: String) {
        personDao.mergePeople(sourceIds, targetId, newName)
    }

    suspend fun movePersonToOther(personId: Long) {
        personDao.movePersonToOther(personId)
    }

    /**
     * Completes the Profile Setup step:
     * 1. Moves any clusters marked as Unknown/Skip into @Other (unlinking their cross-refs).
     * 2. Saves user-assigned or default names for active profiles.
     * 3. Automatically merges any profiles given the exact same name by the user!
     */
    suspend fun applyProfileSetup(
        namedProfiles: Map<Long, String>,
        unknownPersonIds: Set<Long>
    ) = withContext(Dispatchers.IO) {
        // 1. Move unknown / skipped face clusters into @Other
        for (unknownId in unknownPersonIds) {
            personDao.movePersonToOther(unknownId)
        }

        // 2. Group remaining profiles by normalized name so duplicate names auto-merge
        val activeGroups = mutableMapOf<String, MutableList<Pair<Long, String>>>()
        for ((personId, rawName) in namedProfiles) {
            if (unknownPersonIds.contains(personId)) continue
            val cleanName = rawName.trim()
            if (cleanName.isBlank()) {
                personDao.movePersonToOther(personId)
                continue
            }
            val key = cleanName.lowercase()
            activeGroups.getOrPut(key) { mutableListOf() }.add(personId to cleanName)
        }

        for ((_, group) in activeGroups) {
            if (group.size == 1) {
                val (personId, name) = group[0]
                personDao.updatePersonName(personId, name)
            } else if (group.size > 1) {
                val targetId = group[0].first
                val targetName = group[0].second
                val sourceIds = group.drop(1).map { it.first }
                personDao.mergePeople(sourceIds, targetId, targetName)
            }
        }

        OnboardingPreferences.markInitialScanCompleted(context, true)
        OnboardingPreferences.markProfileSetupCompleted(context, true)
    }

    /**
     * Clears all existing face clusters and triggers a fresh AI face scan across the gallery.
     */
    suspend fun resetAndRescanAllFaces() = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork(MediaStoreScanner.FACE_INDEXING_WORK_NAME)
        mediaDao.clearAllCrossRefs()
        personDao.deleteAllFaces()
        personDao.deleteAllPeople()
        mediaDao.resetAllMediaProcessed()

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
