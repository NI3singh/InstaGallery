// InstaGallery v2.0
package com.example.ml

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.InstaGalleryApp
import com.example.data.local.AppDatabase
import com.example.data.local.OnboardingPreferences
import com.example.data.media.MediaStoreScanner
import com.example.ui.screens.permission.hasAnyMediaPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class FaceIndexingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "FaceIndexingWorker"
        const val PROGRESS_PROCESSED = "processed"
        const val PROGRESS_TOTAL = "total"
        const val PROGRESS_PROFILES_COUNT = "profilesCount"
        const val PROGRESS_FACES_FOUND = "faces_found"
        private const val BATCH_CHUNK_SIZE = 15

        fun scheduleContentUriTriggerWatcher(context: Context) {
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

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    "MediaStoreUriTriggerSync",
                    ExistingWorkPolicy.REPLACE,
                    triggerWork
                )
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? InstaGalleryApp
        val database = app?.database ?: AppDatabase.getInstance(applicationContext)
        val mediaDao = database.mediaDao()
        val personDao = database.personDao()
        val mediaStoreScanner = MediaStoreScanner(applicationContext, mediaDao, personDao)

        val detectorProcessor = FaceDetectorProcessor(applicationContext)
        val embeddingExtractor = FaceEmbeddingExtractor(applicationContext)
        val faceClusterManager = (applicationContext as? InstaGalleryApp)?.faceClusterManager
            ?: FaceClusterManager.getInstance(applicationContext)

        try {
            // 1. Reconcile any photos/videos added or deleted while the app was closed
            if (hasAnyMediaPermission(applicationContext)) {
                mediaStoreScanner.syncFastIdDiff()
            }

            // 2. Photos-only human face detection: immediately mark all videos as processed
            mediaDao.markAllVideosProcessed()

            // Self-heal if all photos were previously marked processed but 0 people exist in DB
            val totalPhotosInitial = mediaDao.getTotalMediaCountSync()
            val unindexedInitial = mediaDao.getUnindexedCountSync()
            if (totalPhotosInitial > 0 && unindexedInitial == 0 && personDao.getAllPeopleIncludeHiddenSync().isEmpty()) {
                Log.i(TAG, "Self-healing: resetting processed flags for $totalPhotosInitial photos because 0 people exist")
                mediaDao.resetAllMediaProcessed()
                mediaDao.markAllVideosProcessed()
            }

            val alreadyProcessed = mediaDao.getProcessedCount().firstOrNull() ?: 0
            val totalPhotos = mediaDao.getTotalMediaCount().firstOrNull() ?: 0
            var processedCount = alreadyProcessed
            var processedAnyNewInThisRun = false

            suspend fun countVisibleProfiles(): Int {
                return personDao.getVisiblePeopleSync().size
            }

            if (totalPhotos > 0) {
                val initialVisibleCount = countVisibleProfiles()
                setProgress(
                    workDataOf(
                        PROGRESS_PROCESSED to processedCount,
                        PROGRESS_TOTAL to totalPhotos,
                        PROGRESS_PROFILES_COUNT to initialVisibleCount,
                        PROGRESS_FACES_FOUND to initialVisibleCount
                    )
                )
            }

            while (!isStopped) {
                // Queries unindexed photos only (isVideo = 0 AND isProcessedForFaces = 0)
                val unindexedBatch = mediaDao.getUnindexedPhotos(limit = BATCH_CHUNK_SIZE)
                if (unindexedBatch.isEmpty()) break

                for (media in unindexedBatch) {
                    if (isStopped) break

                    if (media.isVideo) {
                        mediaDao.updateMediaProcessed(
                            mediaId = media.mediaId,
                            isProcessed = true,
                            hasFaces = false
                        )
                        continue
                    }

                    try {
                        val detectedFaces = detectorProcessor.processMedia(media)

                        val facesAssigned = try {
                            if (detectedFaces.isNotEmpty()) {
                                faceClusterManager.processDetectedFacesForMedia(
                                    mediaId = media.mediaId,
                                    detectedFaces = detectedFaces,
                                    embeddingExtractor = embeddingExtractor,
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

                        mediaDao.updateMediaProcessed(
                            mediaId = media.mediaId,
                            isProcessed = true,
                            hasFaces = facesAssigned
                        )
                        processedAnyNewInThisRun = true
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error processing photo ${media.mediaId}", t)
                        mediaDao.updateMediaProcessed(
                            mediaId = media.mediaId,
                            isProcessed = true,
                            hasFaces = false
                        )
                    }

                    processedCount++

                    val totalCount = (mediaDao.getTotalMediaCount().firstOrNull() ?: totalPhotos)
                        .coerceAtLeast(processedCount)
                    val visiblePeopleCount = countVisibleProfiles()

                    setProgress(
                        workDataOf(
                            PROGRESS_PROCESSED to processedCount,
                            PROGRESS_TOTAL to totalCount,
                            PROGRESS_PROFILES_COUNT to visiblePeopleCount,
                            PROGRESS_FACES_FOUND to visiblePeopleCount
                        )
                    )
                }
            }

            // Run Pass-2 Consolidation & Anchor Sweep after the photo loop finishes
            if (!isStopped) {
                if (processedAnyNewInThisRun) {
                    faceClusterManager.runPass2ConsolidationAndAnchorSweep()
                }
                personDao.syncAllPeoplePhotoCountsSql()
                faceClusterManager.reloadClustersFromDb()
            }

            val finalTotal = mediaDao.getTotalMediaCount().firstOrNull() ?: processedCount
            val finalVisibleCount = countVisibleProfiles()
            setProgress(
                workDataOf(
                    PROGRESS_PROCESSED to finalTotal,
                    PROGRESS_TOTAL to finalTotal,
                    PROGRESS_PROFILES_COUNT to finalVisibleCount,
                    PROGRESS_FACES_FOUND to finalVisibleCount
                )
            )

            OnboardingPreferences.markInitialScanCompleted(applicationContext, true)
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "FaceIndexingWorker failed", t)
            Result.retry()
        } finally {
            embeddingExtractor.close()
            // Re-arm OS ContentUriTrigger watcher for future background camera/gallery changes
            if (hasAnyMediaPermission(applicationContext)) {
                scheduleContentUriTriggerWatcher(applicationContext)
            }
        }
    }
}
