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
        const val PROGRESS_FACES_FOUND = "faces_found"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getInstance(applicationContext)
        val mediaDao = database.mediaDao()
        val personDao = database.personDao()

        val detectorProcessor = FaceDetectorProcessor(applicationContext)
        val embeddingExtractor = FaceEmbeddingExtractor(applicationContext)
        val clusterManager = FaceClusterManager(applicationContext, personDao, mediaDao)

        try {
            val alreadyProcessed = mediaDao.getProcessedCount().firstOrNull() ?: 0
            val totalMedia = mediaDao.getTotalMediaCount().firstOrNull() ?: 0
            var processedCount = alreadyProcessed

            if (totalMedia > 0) {
                setProgress(
                    workDataOf(
                        PROGRESS_PROCESSED to processedCount,
                        PROGRESS_TOTAL to totalMedia,
                        PROGRESS_FACES_FOUND to personDao.getAllPeopleSync().size
                    )
                )
            }

            while (!isStopped) {
                val unindexedBatch = mediaDao.getUnindexedMedia(limit = 20)
                if (unindexedBatch.isEmpty()) break

                for (media in unindexedBatch) {
                    if (isStopped) break

                    try {
                        var facesFound = false

                        if (!media.isVideo) {
                            // Photo processing: high-precision detection + co-occurrence exclusion
                            val bitmap = detectorProcessor.decodePhotoBitmap(media.uri)
                            if (bitmap != null) {
                                val detectedFaces = detectorProcessor.detectFaces(bitmap, isVideo = false)
                                if (detectedFaces.isNotEmpty()) {
                                    val matchedInThisPhoto = mutableSetOf<Long>()
                                    for (face in detectedFaces) {
                                        val alignedCrop = detectorProcessor.alignFaceCanonical(bitmap, face)
                                        val avatarCrop = detectorProcessor.cropFaceAvatar(bitmap, face) ?: alignedCrop
                                        if (alignedCrop != null && avatarCrop != null) {
                                            val quality = detectorProcessor.computeFaceQuality(face, bitmap.width, bitmap.height)
                                            val embedding = embeddingExtractor.extractEmbedding(alignedCrop, face)
                                            val personId = clusterManager.processFace(
                                                mediaId = media.mediaId,
                                                face = face,
                                                avatarBitmap = avatarCrop,
                                                embedding = embedding,
                                                faceQuality = quality,
                                                isVideo = false,
                                                excludePersonIdsInSameMedia = matchedInThisPhoto
                                            )
                                            if (personId != null) {
                                                matchedInThisPhoto.add(personId)
                                                facesFound = true
                                            }
                                            if (alignedCrop != bitmap) alignedCrop.recycle()
                                            if (avatarCrop != bitmap && avatarCrop != alignedCrop) avatarCrop.recycle()
                                        }
                                    }
                                }
                                bitmap.recycle()
                            }
                        } else {
                            // Video processing: sample keyframes and match to established person clusters
                            val frames = detectorProcessor.decodeVideoKeyframes(media.uri, media.durationMs)
                            val matchedInThisVideo = mutableSetOf<Long>()
                            for (frame in frames) {
                                val detectedFaces = detectorProcessor.detectFaces(frame, isVideo = true)
                                for (face in detectedFaces) {
                                    val alignedCrop = detectorProcessor.alignFaceCanonical(frame, face)
                                    val avatarCrop = detectorProcessor.cropFaceAvatar(frame, face) ?: alignedCrop
                                    if (alignedCrop != null && avatarCrop != null) {
                                        val quality = detectorProcessor.computeFaceQuality(face, frame.width, frame.height)
                                        val embedding = embeddingExtractor.extractEmbedding(alignedCrop, face)
                                        val personId = clusterManager.processFace(
                                            mediaId = media.mediaId,
                                            face = face,
                                            avatarBitmap = avatarCrop,
                                            embedding = embedding,
                                            faceQuality = quality,
                                            isVideo = true,
                                            excludePersonIdsInSameMedia = emptySet()
                                        )
                                        if (personId != null) {
                                            matchedInThisVideo.add(personId)
                                            facesFound = true
                                        }
                                        if (alignedCrop != frame) alignedCrop.recycle()
                                        if (avatarCrop != frame && avatarCrop != alignedCrop) avatarCrop.recycle()
                                    }
                                }
                                frame.recycle()
                            }
                        }

                        mediaDao.updateMediaProcessed(
                            mediaId = media.mediaId,
                            isProcessed = true,
                            hasFaces = facesFound
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing media ${media.mediaId}", e)
                        mediaDao.updateMediaProcessed(media.mediaId, isProcessed = true, hasFaces = false)
                    }

                    processedCount++
                    val latestTotal = (mediaDao.getTotalMediaCount().firstOrNull() ?: totalMedia).coerceAtLeast(processedCount)
                    setProgress(
                        workDataOf(
                            PROGRESS_PROCESSED to processedCount,
                            PROGRESS_TOTAL to latestTotal
                        )
                    )
                }

                // Consolidate split clusters after each batch
                clusterManager.consolidateConvergedClusters()
            }

            // Final cluster consolidation pass & sequential default numbering cleanup
            clusterManager.consolidateConvergedClusters()
            OnboardingPreferences.markInitialScanCompleted(applicationContext, true)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.retry()
        }
    }
}
