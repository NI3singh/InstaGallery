// InstaGallery v1.0
package com.example.ml

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.AppDatabase
import com.example.data.local.OnboardingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

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
        private const val BATCH_CHUNK_SIZE = 20
        private const val PASS2_CONSOLIDATION_INTERVAL = 40
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getInstance(applicationContext)
        val mediaDao = database.mediaDao()
        val personDao = database.personDao()

        val detectorProcessor = FaceDetectorProcessor(applicationContext)
        val embeddingExtractor = FaceEmbeddingExtractor(applicationContext)
        val clusterManager = FaceClusterManager(applicationContext, personDao, mediaDao)

        try {
            // Photos-only human face detection: immediately mark all videos as processed
            mediaDao.markAllVideosProcessed()

            val alreadyProcessed = mediaDao.getProcessedCount().firstOrNull() ?: 0
            val totalPhotos = mediaDao.getTotalMediaCount().firstOrNull() ?: 0
            var processedCount = alreadyProcessed
            var itemsProcessedInThisRun = 0

            suspend fun countVisibleProfiles(): Int {
                val allActive = personDao.getAllPeopleSync()
                val multiOrNamed = allActive.count { !it.isHiddenToOther && (it.faceCount >= 2 || it.isNamedByUser) }
                return if (multiOrNamed > 0) multiOrNamed else allActive.count { !it.isHiddenToOther }
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
                                clusterManager.processDetectedFacesForMedia(
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
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing photo ${media.mediaId}", e)
                        mediaDao.updateMediaProcessed(
                            mediaId = media.mediaId,
                            isProcessed = true,
                            hasFaces = false
                        )
                    }

                    processedCount++
                    itemsProcessedInThisRun++

                    if (itemsProcessedInThisRun % PASS2_CONSOLIDATION_INTERVAL == 0) {
                        clusterManager.runPass2ConsolidationAndAnchorSweep()
                    }

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

            clusterManager.runPass2ConsolidationAndAnchorSweep()

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
        } catch (e: Exception) {
            Log.e(TAG, "FaceIndexingWorker failed", e)
            Result.retry()
        } finally {
            embeddingExtractor.close()
        }
    }
}
