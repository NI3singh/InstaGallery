package com.example.data.repository

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.FaceEntity
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.local.entity.PersonEntity
import com.example.data.media.MediaStoreScanner
import com.example.ml.FaceClusterManager
import com.example.ml.FaceIndexingWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GalleryRepository(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val mediaDao = database.mediaDao()
    private val personDao = database.personDao()
    private val scanner = MediaStoreScanner(context, mediaDao)
    private val clusterManager = FaceClusterManager(context, personDao, mediaDao)

    // Observables
    val photosWithAuthors: Flow<List<MediaWithAuthors>> = mediaDao.getPhotosWithAuthors()
    val videosWithAuthors: Flow<List<MediaWithAuthors>> = mediaDao.getVideosWithAuthors()
    val favoritePhotosWithAuthors: Flow<List<MediaWithAuthors>> = mediaDao.getFavoritePhotosWithAuthors()
    val allPeople: Flow<List<PersonEntity>> = personDao.getAllPeople()
    val unindexedCount: Flow<Int> = mediaDao.getUnindexedCount()
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
            mediaDao.getUnassociatedCount()
        } else {
            personDao.getPhotoCountForPerson(personId)
        }
    }

    fun getReelCountForPerson(personId: Long): Flow<Int> {
        return if (personId == -1L) {
            mediaDao.getUnassociatedVideos().map { it.size }
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
        mediaDao.removeMediaFromPerson(mediaId, personId)
    }

    suspend fun mergePeople(sourceIds: List<Long>, targetId: Long, newName: String) {
        personDao.mergePeople(sourceIds, targetId, newName)
    }

    fun findPossibleDuplicatePair(people: List<PersonEntity>): Pair<PersonEntity, PersonEntity>? {
        return clusterManager.findPossibleDuplicatePair(people)
    }
}
