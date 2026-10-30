// InstaGallery v2.0
package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.InstaGalleryApp
import com.example.data.local.AppDatabase
import com.example.data.local.OnboardingPreferences
import com.example.data.local.entity.CoOccurringPerson
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.CollectionMediaCrossRef
import com.example.data.local.entity.FaceEntity
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.MediaWithAuthors
import com.example.data.local.entity.PersonEntity
import com.example.data.media.MediaStoreScanner
import com.example.data.media.SyncDiffResult
import com.example.ml.FaceClusterManager
import com.example.ml.FaceDetectorProcessor
import com.example.ml.FaceEmbeddingExtractor
import com.example.ml.FaceIndexingWorker
import com.example.ml.SimilarProfileCandidate
import com.example.ui.screens.permission.hasAnyMediaPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GalleryRepository(
    private val context: Context,
    private val clusterManager: FaceClusterManager =
        (context.applicationContext as? InstaGalleryApp)?.faceClusterManager
            ?: FaceClusterManager.getInstance(context)
) {

    private val appContext = context.applicationContext
    private val database = (appContext as? InstaGalleryApp)?.database
        ?: AppDatabase.getInstance(appContext)
    private val mediaDao = database.mediaDao()
    private val personDao = database.personDao()
    private val collectionDao = database.collectionDao()
    private val mediaStoreScanner = MediaStoreScanner(appContext, mediaDao, personDao)
    private val scanner: MediaStoreScanner
        get() = mediaStoreScanner

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repositoryScope: CoroutineScope
        get() = syncScope

    private val singleIdSyncJobs = ConcurrentHashMap<Long, Job>()
    private var observerDebounceJob: Job? = null
    private var mediaStoreObserver: ContentObserver? = null

    private val fastLaneIndexingMutex = Mutex()
    private var fastLaneDetector: FaceDetectorProcessor? = null
    private var fastLaneEmbeddingExtractor: FaceEmbeddingExtractor? = null

    init {
        syncScope.launch {
            ensureDefaultCollectionsSeeded()
            if (hasAnyMediaPermission(appContext)) {
                scheduleMediaUriTriggerWorker()
            }
        }
    }

    /**
     * Primary people Flow observed by HomeFeedScreen and PeopleDirectoryScreen.
     * Uses `personDao.getVisiblePeopleFlow()`.
     */
    val allPeople: Flow<List<PersonEntity>> = personDao.getVisiblePeopleFlow()

    fun getVisiblePeopleFlow(): Flow<List<PersonEntity>> = personDao.getVisiblePeopleFlow()

    /**
     * Newly detected undecided people with >= 3 distinct photos (for the "New People Found" review banner).
     */
    val newUndecidedPeople: Flow<List<PersonEntity>> = personDao.getNewUndecidedPeopleFlow()

    fun getNewUndecidedPeopleFlow(): Flow<List<PersonEntity>> = personDao.getNewUndecidedPeopleFlow()

    /**
     * Dedicated setup Flow that returns all profiles (including hidden) ordered by faceCount DESC, personId ASC
     * without sorting by isNamedByUser or decisionState.
     */
    val allPeopleForSetup: Flow<List<PersonEntity>> = personDao.getAllPeopleForSetupFlow()

    fun getAllPeopleForSetupFlow(): Flow<List<PersonEntity>> = personDao.getAllPeopleForSetupFlow()

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

    val unindexedCount: Flow<Int> = mediaDao.getUnindexedPhotoCountFlow()
    val unindexedPhotoCount: Flow<Int> = mediaDao.getUnindexedPhotoCountFlow()
    fun getUnindexedPhotoCountFlow(): Flow<Int> = mediaDao.getUnindexedPhotoCountFlow()

    val processedCount: Flow<Int> = mediaDao.getProcessedCount()
    val totalMediaCount: Flow<Int> = mediaDao.getTotalMediaCount()
    val unassociatedCount: Flow<Int> = mediaDao.getUnassociatedPhotoCount()

    // Work progress
    val indexingWorkInfo: Flow<WorkInfo?> = WorkManager.getInstance(appContext)
        .getWorkInfosForUniqueWorkFlow(MediaStoreScanner.FACE_INDEXING_WORK_NAME)
        .map { it.firstOrNull() }

    fun enqueueFaceIndexingWorker(
        policy: ExistingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE
    ) {
        mediaStoreScanner.enqueueFaceIndexingWorker(policy)
    }

    /**
     * 3.1 Surgical ContentObserver with Exact URI Coalescing (150ms) + Fast Set-Diff Debounce (400ms).
     * Syncs individual camera captures, WhatsApp arrivals, and external deletions in < 50ms.
     */
    @Synchronized
    fun startMediaStoreObserver() {
        if (mediaStoreObserver != null) return

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                handleMediaUriChange(uri)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
                handleMediaUriChange(uri)
            }

            private fun handleMediaUriChange(uri: Uri?) {
                if (!hasAnyMediaPermission(appContext)) return

                val changedId = uri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() }
                if (changedId != null && changedId > 0L) {
                    singleIdSyncJobs[changedId]?.cancel()
                    val job = syncScope.launch {
                        try {
                            delay(150L)
                            val diff = mediaStoreScanner.syncSingleMediaId(changedId)
                            if (diff.newlyInsertedPhotos.isNotEmpty()) {
                                indexPhotosFastLane(diff.newlyInsertedPhotos)
                            }
                        } finally {
                            singleIdSyncJobs.remove(changedId)
                        }
                    }
                    singleIdSyncJobs[changedId] = job
                }

                observerDebounceJob?.cancel()
                observerDebounceJob = syncScope.launch {
                    delay(400L)
                    runFastDeltaAndIndex()
                }
            }
        }

        runCatching {
            val resolver = appContext.contentResolver
            resolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            resolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            resolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"),
                true,
                observer
            )
            mediaStoreObserver = observer
        }

        if (hasAnyMediaPermission(appContext)) {
            scheduleMediaUriTriggerWorker()
        }
    }

    @Synchronized
    fun stopMediaStoreObserver() {
        observerDebounceJob?.cancel()
        observerDebounceJob = null
        singleIdSyncJobs.values.forEach { it.cancel() }
        singleIdSyncJobs.clear()
        mediaStoreObserver?.let { observer ->
            runCatching {
                appContext.contentResolver.unregisterContentObserver(observer)
            }
        }
        mediaStoreObserver = null
    }

    /**
     * Runs Zero-I/O HashSet ID-Diff Sync, fast-lanes up to 5 newly inserted photos in-process,
     * and enqueues FaceIndexingWorker if a larger batch remains.
     */
    suspend fun runFastDeltaAndIndex() = withContext(Dispatchers.IO) {
        if (!hasAnyMediaPermission(appContext)) return@withContext
        val diff = mediaStoreScanner.syncFastIdDiff()
        if (diff.newlyInsertedPhotos.isNotEmpty()) {
            val fastBatch = diff.newlyInsertedPhotos.take(5)
            indexPhotosFastLane(fastBatch)
        } else {
            val unindexedCount = mediaDao.getUnprocessedMediaCount()
            if (unindexedCount in 1..5) {
                val newestUnindexed = mediaDao.getNewestUnindexedPhotosSync(5)
                if (newestUnindexed.isNotEmpty()) {
                    indexPhotosFastLane(newestUnindexed)
                }
            } else if (unindexedCount > 5) {
                enqueueFaceIndexingWorker(ExistingWorkPolicy.APPEND_OR_REPLACE)
            }
        }
    }

    suspend fun performQuickRefreshSync(): SyncDiffResult = withContext(Dispatchers.IO) {
        if (!hasAnyMediaPermission(appContext)) {
            return@withContext SyncDiffResult(0, 0, 0, emptyList())
        }
        val diff = mediaStoreScanner.syncFastIdDiff()
        if (diff.newlyInsertedPhotos.isNotEmpty()) {
            if (diff.newlyInsertedPhotos.size <= 5) {
                indexPhotosFastLane(diff.newlyInsertedPhotos)
            } else {
                enqueueFaceIndexingWorker(ExistingWorkPolicy.APPEND_OR_REPLACE)
            }
        } else if (mediaDao.getUnprocessedMediaCount() > 0) {
            enqueueFaceIndexingWorker(ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        personDao.reconcilePeopleAfterMediaDeletion()
        diff
    }

    /**
     * 3.2 Fast-Lane In-Process Face Indexing.
     * Immediately runs ML Kit + MobileFaceNet 192-D clustering in-memory for 1..5 newly arrived photos
     * without waiting for WorkManager startup overhead.
     */
    suspend fun indexPhotosFastLane(photos: List<MediaEntity>) = withContext(Dispatchers.Default) {
        val candidatePhotos = photos.filter { !it.isVideo }.take(5)
        if (candidatePhotos.isEmpty()) return@withContext

        fastLaneIndexingMutex.withLock {
            val detector = fastLaneDetector ?: FaceDetectorProcessor(appContext).also {
                fastLaneDetector = it
            }
            val extractor = fastLaneEmbeddingExtractor ?: FaceEmbeddingExtractor(appContext).also {
                fastLaneEmbeddingExtractor = it
            }

            var anyPhotoProcessed = false
            for (photo in candidatePhotos) {
                // Skip if already processed by a concurrent pass
                val currentInDb = mediaDao.getMediaByIdSync(photo.mediaId) ?: continue
                if (currentInDb.isProcessedForFaces) continue

                try {
                    val detectedFaces = detector.processMedia(currentInDb)
                    val hasAssignedFaces = try {
                        if (detectedFaces.isNotEmpty()) {
                            clusterManager.processDetectedFacesForMedia(
                                mediaId = currentInDb.mediaId,
                                detectedFaces = detectedFaces,
                                embeddingExtractor = extractor,
                                isVideo = false
                            )
                        } else {
                            false
                        }
                    } finally {
                        for (face in detectedFaces) {
                            if (!face.alignedFaceBitmap.isRecycled) {
                                face.alignedFaceBitmap.recycle()
                            }
                            if (face.avatarCropBitmap != face.alignedFaceBitmap && !face.avatarCropBitmap.isRecycled) {
                                face.avatarCropBitmap.recycle()
                            }
                        }
                    }

                    mediaDao.markMediaAsProcessed(currentInDb.mediaId, hasFaces = hasAssignedFaces)
                    anyPhotoProcessed = true
                } catch (t: Throwable) {
                    Log.e("GalleryRepository", "Fast-lane indexing error for photo ${photo.mediaId}", t)
                    mediaDao.markMediaAsProcessed(currentInDb.mediaId, hasFaces = false)
                }
            }

            if (anyPhotoProcessed) {
                personDao.syncAllPeoplePhotoCountsSql()
            }
        }

        if (mediaDao.getUnprocessedMediaCount() > 5) {
            enqueueFaceIndexingWorker(ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
    }

    /**
     * 4. Schedules an OS-level ContentUriTrigger job so background camera captures or gallery deletions
     * wake up FaceIndexingWorker even when InstaGallery is closed.
     */
    fun scheduleMediaUriTriggerWorker() {
        runCatching {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
                .addContentUriTrigger(MediaStore.Files.getContentUri("external"), true)
                .setTriggerContentUpdateDelay(300, TimeUnit.MILLISECONDS)
                .setTriggerContentMaxDelay(1500, TimeUnit.MILLISECONDS)
                .build()

            val triggerWork = OneTimeWorkRequestBuilder<FaceIndexingWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "MediaStoreUriTriggerSync",
                ExistingWorkPolicy.REPLACE,
                triggerWork
            )
        }
    }

    /**
     * Runs a zero-I/O HashSet ID-diff scan on ON_RESUME to ingest new camera/WhatsApp photos
     * and remove deleted photos in < 20ms, fast-laning 1..5 new photos immediately.
     */
    suspend fun syncOnAppResume() = withContext(Dispatchers.IO) {
        if (!hasAnyMediaPermission(appContext)) return@withContext
        scheduleMediaUriTriggerWorker()
        runFastDeltaAndIndex()
    }

    suspend fun scanDeviceMedia(): Int {
        mediaDao.deleteAllVideoPersonLinks()
        val count = mediaStoreScanner.scanFullAndReconcile()
        scheduleMediaUriTriggerWorker()
        return count
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

    fun getMediaForPersonFlow(personId: Long): Flow<List<MediaWithAuthors>> {
        return getMediaWithAuthorsForPersonFlow(personId)
    }

    fun getUnassignedPhotosFlow(): Flow<List<MediaWithAuthors>> {
        return filterMediaAuthorsToVisible(mediaDao.getUnassignedPhotosWithAuthorsFlow())
    }

    fun getMediaForPerson(personId: Long): Flow<List<MediaWithAuthors>> {
        return getMediaWithAuthorsForPersonFlow(personId)
    }

    fun getPhotosForPerson(personId: Long): Flow<List<MediaWithAuthors>> {
        return getMediaWithAuthorsForPersonFlow(personId)
    }

    fun getCoOccurringPeopleFlow(personId: Long): Flow<List<CoOccurringPerson>> {
        return personDao.getCoOccurringPeopleFlow(personId)
    }

    fun getSharedMediaBetweenPeopleFlow(personId: Long, otherPersonId: Long): Flow<List<MediaWithAuthors>> {
        return filterMediaAuthorsToVisible(mediaDao.getSharedMediaBetweenPeopleFlow(personId, otherPersonId))
    }

    fun getSoloMediaForPersonFlow(personId: Long): Flow<List<MediaWithAuthors>> {
        return filterMediaAuthorsToVisible(mediaDao.getSoloMediaForPersonFlow(personId))
    }

    suspend fun getSamplePhotosForPersonSync(personId: Long, limit: Int = 3): List<MediaEntity> {
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

    suspend fun toggleBookmarked(mediaId: Long, currentBookmarked: Boolean) = withContext(Dispatchers.IO) {
        val newBookmarkedState = !currentBookmarked
        mediaDao.setBookmarked(mediaId, newBookmarkedState)
        if (!newBookmarkedState) {
            collectionDao.removeMediaFromAllCollections(mediaId)
        }
    }

    suspend fun toggleBookmark(media: MediaEntity) = toggleBookmarked(media.mediaId, media.isBookmarked)

    suspend fun setBookmarked(mediaId: Long, bookmarked: Boolean) = withContext(Dispatchers.IO) {
        mediaDao.setBookmarked(mediaId, bookmarked)
        if (!bookmarked) {
            collectionDao.removeMediaFromAllCollections(mediaId)
        }
    }

    val allCollectionsFlow: Flow<List<CollectionEntity>> = collectionDao.getAllCollectionsFlow()

    fun getCollectionIdsForMediaFlow(mediaId: Long): Flow<List<Long>> =
        collectionDao.getCollectionIdsForMediaFlow(mediaId)

    fun getAllCollectionCrossRefsFlow(): Flow<List<CollectionMediaCrossRef>> =
        collectionDao.getAllCollectionCrossRefsFlow()

    fun getMediaForCollectionFlow(collectionId: Long): Flow<List<MediaEntity>> =
        collectionDao.getMediaForCollectionFlow(collectionId)

    fun getMediaWithAuthorsForCollectionFlow(collectionId: Long): Flow<List<MediaWithAuthors>> =
        filterMediaAuthorsToVisible(collectionDao.getMediaWithAuthorsForCollectionFlow(collectionId))

    suspend fun ensureDefaultCollectionsSeeded() = withContext(Dispatchers.IO) {
        if (collectionDao.getCollectionCount() == 0) {
            val now = System.currentTimeMillis()
            val defaults = listOf(
                "Favorites & Highlights",
                "Trips & Outings",
                "Family & Friends",
                "Wallpapers"
            )
            defaults.forEachIndexed { idx, title ->
                collectionDao.insertCollection(
                    CollectionEntity(
                        name = title,
                        createdAt = now + idx
                    )
                )
            }
        }
    }

    suspend fun createCollection(rawName: String): Long = withContext(Dispatchers.IO) {
        val clean = rawName.trim()
        if (clean.isEmpty()) return@withContext -1L
        collectionDao.insertCollection(CollectionEntity(name = clean))
    }

    suspend fun deleteCollection(collectionId: Long) = withContext(Dispatchers.IO) {
        collectionDao.deleteCollection(collectionId)
    }

    suspend fun addMediaToCollection(collectionId: Long, mediaId: Long) = withContext(Dispatchers.IO) {
        collectionDao.addMediaToCollection(
            CollectionMediaCrossRef(
                collectionId = collectionId,
                mediaId = mediaId
            )
        )
        mediaDao.setBookmarked(mediaId, true)
    }

    suspend fun removeMediaFromCollection(collectionId: Long, mediaId: Long) = withContext(Dispatchers.IO) {
        collectionDao.removeMediaFromCollection(collectionId, mediaId)
        if (!collectionDao.isMediaInAnyCollection(mediaId)) {
            mediaDao.setBookmarked(mediaId, false)
        }
    }

    suspend fun toggleMediaInCollection(
        collectionId: Long,
        mediaId: Long,
        isCurrentlyInCollection: Boolean
    ) = withContext(Dispatchers.IO) {
        if (isCurrentlyInCollection) {
            removeMediaFromCollection(collectionId, mediaId)
        } else {
            addMediaToCollection(collectionId, mediaId)
        }
    }

    suspend fun updateMediaCaption(mediaId: Long, caption: String?) = withContext(Dispatchers.IO) {
        mediaDao.updateCustomCaption(mediaId, caption?.trim()?.takeIf { it.isNotEmpty() })
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

    suspend fun keepAndNamePerson(personId: Long, rawName: String) = withContext(Dispatchers.IO) {
        val cleanName = rawName.trim().removePrefix("@").trim()
        if (cleanName.isBlank()) {
            personDao.hidePersonToOther(personId)
            clusterManager.invalidateCache()
            return@withContext
        }
        personDao.markPersonKeptSql(personId, cleanName)
        val affectedMediaIds = personDao.getMediaIdsForPerson(personId)
        if (affectedMediaIds.isNotEmpty()) {
            personDao.syncHasFacesForMediaIds(affectedMediaIds)
        }
        clusterManager.invalidateCache()
    }

    suspend fun movePersonToOtherSql(personId: Long) = withContext(Dispatchers.IO) {
        personDao.hidePersonToOther(personId)
        clusterManager.invalidateCache()
    }

    suspend fun restorePersonFromOtherSql(personId: Long) = withContext(Dispatchers.IO) {
        personDao.unhidePersonFromOther(personId)
        clusterManager.invalidateCache()
    }

    suspend fun getPersonByIdSync(personId: Long): PersonEntity? = withContext(Dispatchers.IO) {
        personDao.getPersonByIdSync(personId)
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

    suspend fun repairAndSyncDatabaseCounts() = withContext(Dispatchers.IO) {
        personDao.reconcilePeopleAfterMediaDeletion()
        val allPeopleInDb = personDao.getAllPeopleIncludeHiddenSync()
        for (person in allPeopleInDb) {
            val cropValid = person.avatarCropPath.isNotBlank() && File(person.avatarCropPath).exists()
            if (!cropValid) {
                val bestFace = personDao.getHighestQualityFaceForPersonSync(person.personId)
                if (bestFace != null && bestFace.cropPath.isNotBlank() && File(bestFace.cropPath).exists()) {
                    personDao.updateAvatar(person.personId, bestFace.cropPath)
                }
            }
        }
        mediaDao.syncAllMediaHasFaces()
        clusterManager.invalidateCache()
    }

    fun findPossibleDuplicatePair(
        people: List<PersonEntity>,
        ignoredPairKeys: Set<String> = emptySet()
    ): Pair<PersonEntity, PersonEntity>? {
        return clusterManager.findPossibleDuplicatePair(people, ignoredPairKeys)
    }

    fun pairKey(id1: Long, id2: Long): String = clusterManager.pairKey(id1, id2)
}

